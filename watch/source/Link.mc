import Toybox.Application;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Time;
import Toybox.Timer;
import Toybox.WatchUi;

// Fake the phone in the simulator and for UI work; set to false to talk to the real phone.
const USE_STUB = false;

const LABELS = {"status" => "Updating", "lock" => "Locking", "unlock" => "Unlocking",
    "climate_on" => "Climate on", "climate_off" => "Climate off"};
const STATUS_KEYS = ["soc", "locked", "charge", "hvac", "tset", "awake"];
const ERRORS = {"auth" => "Open the phone app", "offline" => "No connection", "rate" => "Try again in a minute",
    "timeout" => "No confirmation yet", "wake" => "Car did not wake", "unknown" => "Something went wrong",
    "nolink" => "Phone not reachable"};

// Phone link per docs/protocol.md: caches the last reply, drops replies to superseded requests.
class Link {
    var state as Dictionary? = null;
    var pending as String? = null;
    var err as String? = null;
    private var _id as Number = 0;
    private var _cmd as String = "";
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

    // temp is the climate set point in Celsius for climate_on, otherwise null
    function send(cmd as String, temp as Float?) as Void {
        _id += 1;
        _cmd = cmd;
        pending = LABELS[cmd];
        err = null;
        if (USE_STUB) {
            _timer.start(method(:stubReply), 2000, false);
        } else {
            var msg = {"v" => 1, "id" => _id, "cmd" => cmd};
            if (temp != null) { msg["temp"] = temp; }
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
        if (d["v"] != 1 || d["id"] != _id) { return; }
        _silence.stop();
        if (d["err"] != null) {
            fail(d["err"] as String);
            return;
        }
        err = null;
        // pending-only replies carry no status, so merge rather than replace
        var st = state != null ? state : {};
        var fresh = false;
        for (var i = 0; i < STATUS_KEYS.size(); i++) {
            if (d[STATUS_KEYS[i]] != null) {
                st[STATUS_KEYS[i]] = d[STATUS_KEYS[i]];
                fresh = true;
            }
        }
        if (fresh) {
            st["ts"] = Time.now().value() - (d["age"] != null ? d["age"] as Number : 0);
            state = st;
        }
        pending = d["pending"] as String?;
        // flash writes wear it and cost power: save once a command settles, not on every progress reply
        if (fresh && pending == null) { Application.Storage.setValue("s", st); }
        WatchUi.requestUpdate();
    }

    function onSilence() as Void {
        fail("nolink");
    }

    function fail(code as String) as Void {
        _silence.stop();
        err = ERRORS.hasKey(code) ? ERRORS[code] : ERRORS["unknown"];
        pending = null;
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
