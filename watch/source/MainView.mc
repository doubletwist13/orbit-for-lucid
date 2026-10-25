import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Time;
import Toybox.WatchUi;

const GREEN = 0x00FF66;
const DIM = 0x338855;
const STALE_SECS = 1800;

class MainView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var link = link();
        var s = link.state;
        var cx = dc.getWidth() / 2;
        var h = dc.getHeight();
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        dc.clear();
        var mid = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.drawText(cx, h * 0.18, Graphics.FONT_SMALL, "> Orbit", mid);
        // old data is dimmed; the car only reports while awake, so this is normal, not an error
        if (s != null && Time.now().value() - (s["ts"] as Number) >= STALE_SECS) {
            dc.setColor(DIM, Graphics.COLOR_BLACK);
        }
        dc.drawText(cx, h * 0.42, Graphics.FONT_NUMBER_HOT, s == null ? "--" : s["soc"] + "%", mid);
        if (s != null) {
            dc.drawText(cx, h * 0.62, Graphics.FONT_SMALL, (s["locked"] ? "Locked" : "Unlocked") + " | " + charge(s),
                mid);
        }
        var note = link.pending != null ? link.pending + "..." : link.err != null ? link.err : age(s);
        dc.setColor(link.err != null ? Graphics.COLOR_RED : DIM, Graphics.COLOR_BLACK);
        dc.drawText(cx, h * 0.80, Graphics.FONT_TINY, note, mid);
    }

    private function charge(s as Dictionary) as String {
        var c = s["charge"] as String?;
        if (c == null || c.equals("NOT_CONNECTED") || c.equals("UNKNOWN")) { return "Unplugged"; }
        return c.equals("CHARGING") ? "Charging" : "Plugged";
    }

    private function age(s as Dictionary?) as String {
        if (s == null) { return "No data"; }
        var secs = Time.now().value() - (s["ts"] as Number);
        return secs < 60 ? "Updated now"
            : secs < 3600 ? "Updated " + secs / 60 + "m ago" : "Updated " + secs / 3600 + "h ago";
    }
}

class MainDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        var s = link().state;
        var hvac = s != null && s["hvac"] == true;
        var menu = new WatchUi.Menu2({:title => "Orbit"});
        menu.addItem(new WatchUi.MenuItem("Lock", null, "lock", null));
        menu.addItem(new WatchUi.MenuItem("Unlock", null, "unlock", null));
        var climate = hvac ? "climate_off" : "climate_on";
        menu.addItem(new WatchUi.MenuItem(hvac ? "Climate off" : "Climate on", null, climate, null));
        menu.addItem(new WatchUi.MenuItem("Refresh", null, "status", null));
        WatchUi.pushView(menu, new ActionDelegate(), WatchUi.SLIDE_UP);
        return true;
    }
}

class ActionDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        var id = item.getId() as String;
        if (id.equals("unlock")) {
            WatchUi.pushView(new WatchUi.Confirmation("Unlock?"), new UnlockConfirm(), WatchUi.SLIDE_UP);
        } else {
            link().send(id);
        }
    }
}

class UnlockConfirm extends WatchUi.ConfirmationDelegate {
    function initialize() {
        ConfirmationDelegate.initialize();
    }

    function onResponse(response as WatchUi.Confirm) as Boolean {
        if (response == WatchUi.CONFIRM_YES) { link().send("unlock"); }
        return true;
    }
}
