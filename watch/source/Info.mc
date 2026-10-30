import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.WatchUi;

// When the car last reported and when the phone last sent status, so you can tell whether the 5 minute pushes arrive.
// The age line on the main screen is only the car's.
class InfoView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var s = link().state;
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        dc.clear();
        row(dc, w / 2, h * 0.17, "Car data", s != null ? s["ts"] as Number? : null);
        row(dc, w / 2, h * 0.46, "Phone update", s != null ? s["pt"] as Number? : null);
        // which builds are on the watch and the phone, for spotting a mismatch and for bug reports
        dc.setColor(DIM, Graphics.COLOR_BLACK);
        var pv = s != null ? s["pv"] as String? : null;
        dc.drawText(w / 2, h * 0.80, Graphics.FONT_XTINY, "Orbit " + WatchUi.loadResource(Rez.Strings.Version),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        dc.drawText(w / 2, h * 0.89, Graphics.FONT_XTINY, "Phone " + (pv != null ? pv : "--"),
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // a dim title over "10:02 (3m ago)", or "--" when nothing has arrived yet
    private function row(dc as Dc, x as Number, y as Float, title as String, at as Number?) as Void {
        dc.setColor(DIM, Graphics.COLOR_BLACK);
        dc.drawText(x, y, Graphics.FONT_SMALL, title, Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        dc.drawText(x, y + dc.getFontHeight(Graphics.FONT_SMALL), Graphics.FONT_MEDIUM,
            at != null ? clock(at) + " (" + ago(Time.now().value() - at) + ")" : "--", Graphics.TEXT_JUSTIFY_CENTER);
    }

    // local time of an epoch second, in the watch's 12 or 24 hour style
    private function clock(epoch as Number) as String {
        var t = Gregorian.info(new Time.Moment(epoch), Time.FORMAT_SHORT);
        var min = t.min.format("%02d");
        if (System.getDeviceSettings().is24Hour) { return t.hour.format("%02d") + ":" + min; }
        return (t.hour % 12 == 0 ? 12 : t.hour % 12) + ":" + min + (t.hour < 12 ? "a" : "p");
    }

    private function ago(secs as Number) as String {
        return secs < 60 ? "now" : secs < 3600 ? secs / 60 + "m ago" : secs / 3600 + "h ago";
    }
}
