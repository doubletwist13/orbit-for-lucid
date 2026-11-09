import Toybox.Application;
import Toybox.Attention;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// Fake the phone in the simulator and for UI work; set to false to talk to the real phone.
const USE_STUB = false;

const LABELS = {"status" => "Updating", "lock" => "Locking", "unlock" => "Unlocking",
    "climate_on" => "Climate on", "climate_off" => "Climate off", "frunk_open" => "Opening frunk",
    "frunk_close" => "Closing frunk", "trunk_open" => "Opening trunk", "trunk_close" => "Closing trunk",
    "horn" => "Sending horn", "flash" => "Sending lights", "windows_close" => "Closing windows",
    "pet_on" => "Pet mode on", "pet_off" => "Pet mode off", "charge_start" => "Starting charge",
    "charge_stop" => "Stopping charge", "limit" => "Setting limit", "port_open" => "Opening port",
    "port_close" => "Closing port", "precon_on" => "Starting precon", "precon_off" => "Stopping precon",
    "ac_limit" => "Setting AC amps"};
// What a result toast calls each command, in front of its outcome ("Unlock: done", "Frunk close: not confirmed")
const NAMES = {"lock" => "Lock", "unlock" => "Unlock", "climate_on" => "Climate on", "climate_off" => "Climate off",
    "frunk_open" => "Frunk open", "frunk_close" => "Frunk close", "trunk_open" => "Trunk open",
    "trunk_close" => "Trunk close", "horn" => "Horn", "flash" => "Lights", "windows_close" => "Windows close",
    "pet_on" => "Pet mode on", "pet_off" => "Pet mode off", "charge_start" => "Charge start",
    "charge_stop" => "Charge stop", "limit" => "Charge limit", "port_open" => "Port open", "port_close" => "Port close",
    "precon_on" => "Precon on", "precon_off" => "Precon off", "ac_limit" => "AC limit"};
// short on purpose: the main screen shows these inside a round bezel, where about 15 characters fit on every watch
const ERRORS = {"auth" => "Open phone app", "offline" => "No connection", "rate" => "Try again soon",
    "timeout" => "Not confirmed", "wake" => "Car didn't wake", "unsupported" => "Can't do that",
    "novehicle" => "No car found", "badcmd" => "Update phone", "nocable" => "No cable", "atlimit" => "At limit",
    "unavail" => "Not available", "plugged" => "Cable in",
    "unknown" => "Try again later", "nolink" => "No phone link"};

// Phone link per docs/protocol.md: caches the last reply, drops replies to superseded requests.
class Link {
    var state as Dictionary? = null;
    var pending as String? = null;
    var err as String? = null;
    private var _id as Number = 0;
    private var _cmd as String = "";
    // whether the last command's result has been told (toast and vibration), so a late second failure stays quiet
    private var _told as Boolean = false;
    private var _timer as Timer.Timer = new Timer.Timer();
    private var _silence as Timer.Timer = new Timer.Timer();

    function initialize() {
        state = cached();
        Communications.registerForPhoneAppMessages(method(:onMessage));
    }

    // a stored soc outside 0..100 (seen once as 255) is dropped so the screen shows "--" until fresh data arrives
    private function cached() as Dictionary? {
        var s = Application.Storage.getValue("s") as Dictionary?;
        if (s != null && s["soc"] != null && (s["soc"] < 0 || s["soc"] > 100)) { s.remove("soc"); }
        return s;
    }

    // arg is the climate set point in Celsius for climate_on, the charge limit in percent for limit or the AC limit in
    // amps for ac_limit, otherwise null
    function send(cmd as String, arg as Float?) as Void {
        _id += 1;
        _cmd = cmd;
        _told = false;
        pending = LABELS[cmd];
        err = null;
        if (USE_STUB) {
            _timer.start(method(:stubReply), 2000, false);
        } else {
            // wv: this app's version, so the phone app can show it and a mismatch is easy to spot
            var msg = {"v" => 1, "id" => _id, "cmd" => cmd, "wv" => WatchUi.loadResource(Rez.Strings.Version)};
            if (arg != null) {
                if (cmd.equals("limit")) {
                    msg["lim"] = arg.toNumber();
                } else if (cmd.equals("ac_limit")) {
                    msg["amp"] = arg.toNumber();
                } else {
                    msg["temp"] = arg;
                }
            }
            Communications.transmit(msg, null, new LinkListener(self));
            // the phone's first reply (even "pending") arrives within seconds; silence means no service is listening
            _silence.stop();
            _silence.start(method(:onSilence), 8000, false);
        }
        WatchUi.requestUpdate();
    }

    function onMessage(msg as Communications.PhoneAppMessage) as Void {
        handle(msg.data as Dictionary);
    }

    function handle(d as Dictionary) as Void {
        if (d["v"] != 1) { return; }
        // a push is not an answer to anything we asked, so it has no request id to match; it just refreshes the state
        if (d["push"] == true) {
            var fresh = Status.merge(state, d);
            if (fresh != null) {
                // a message from the phone proves the link works, so an old "no phone link" no longer applies
                err = null;
                state = fresh;
                Application.Storage.setValue("s", fresh);
                WatchUi.requestUpdate();
            }
            return;
        }
        if (d["id"] != _id) { return; }
        _silence.stop();
        if (d["err"] != null) {
            fail(d["err"] as String);
            return;
        }
        err = null;
        var st = Status.merge(state, d);
        if (st != null) { state = st; }
        pending = d["pending"] as String?;
        // flash writes wear it and cost power: save once a command settles, not on every progress reply
        if (st != null && pending == null) { Application.Storage.setValue("s", st); }
        if (pending == null) { tell(true, null); }
        WatchUi.requestUpdate();
    }

    // Say how the command ended, so the user need not go to the main screen: a toast naming the action and the outcome,
    // and a vibration (one short buzz for done, two for failed). The refresh is left out, as it also runs at launch.
    private function tell(ok as Boolean, why as String?) as Void {
        if (_told || !NAMES.hasKey(_cmd)) { return; }
        _told = true;
        // nothing reads horn or lights back, so the most they can say is that Lucid took them
        var done = _cmd.equals("horn") || _cmd.equals("flash") ? "sent" : "done";
        WatchUi.showToast(NAMES[_cmd] + ": " + (ok ? done : lower(why as String)), null);
        // the watch's own vibration setting is respected, and not every watch can vibrate
        if (!(Attention has :vibrate) || !System.getDeviceSettings().vibrateOn) { return; }
        var buzz = new Attention.VibeProfile(ok ? 50 : 100, 200);
        Attention.vibrate(ok ? [buzz] : [buzz, new Attention.VibeProfile(0, 120), buzz]);
    }

    // the error text starts a sentence on the main screen, but follows "Action: " in a toast
    private function lower(text as String) as String {
        return text.substring(0, 1).toLower() + text.substring(1, text.length());
    }

    function onSilence() as Void {
        fail("nolink");
    }

    function fail(code as String) as Void {
        _silence.stop();
        err = ERRORS.hasKey(code) ? ERRORS[code] : ERRORS["unknown"];
        pending = null;
        tell(false, err);
        WatchUi.requestUpdate();
    }

    function stubReply() as Void {
        var s = state != null ? state : {"soc" => 75, "locked" => true, "charge" => "NOT_CONNECTED", "hvac" => false};
        if (_cmd.equals("lock")) { s["locked"] = true; }
        if (_cmd.equals("unlock")) { s["locked"] = false; }
        if (_cmd.equals("climate_on")) { s["hvac"] = true; }
        if (_cmd.equals("climate_off")) { s["hvac"] = false; }
        s["v"] = 1;
        s["id"] = _id;
        s["ok"] = true;
        s["age"] = 0;
        s.remove("pending");
        handle(s);
    }
}

class LinkListener extends Communications.ConnectionListener {
    private var _link as Link;

    function initialize(link as Link) {
        ConnectionListener.initialize();
        _link = link;
    }

    function onComplete() as Void {}

    function onError() as Void {
        _link.fail("nolink");
    }
}
