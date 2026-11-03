import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Time;
import Toybox.Time.Gregorian;
import Toybox.WatchUi;

// When the car last reported and when the phone last sent status, so you can tell whether the 5 minute pushes arrive,
// and which builds are on the watch and the phone. The age is not on the main screen. A tap or the button refreshes.
class AboutView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var s = link().state;
        var w = dc.getWidth();
        var h = dc.getHeight();
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        dc.clear();
        pageDots(dc, ABOUT_PAGE);
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        row(dc, w / 2, h * 0.11, "Car data", s != null ? s["ts"] as Number? : null);
        row(dc, w / 2, h * 0.36, "Phone update", s != null ? s["pt"] as Number? : null);
        // which builds are on the watch and the phone, for spotting a mismatch and for bug reports
        dc.setColor(DIM, Graphics.COLOR_BLACK);
        var pv = s != null ? s["pv"] as String? : null;
        var mid = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.drawText(w / 2, h * 0.80, Graphics.FONT_XTINY, "Orbit " + WatchUi.loadResource(Rez.Strings.Version), mid);
        dc.drawText(w / 2, h * 0.88, Graphics.FONT_XTINY, "Phone " + (pv != null ? pv : "--"), mid);
        // the way to refresh: what the app is doing, an error in red, or the hint
        var link = link();
        dc.setColor(link.err != null ? Graphics.COLOR_RED : GREEN, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.70, Graphics.FONT_TINY,
            link.pending != null ? link.pending + "..." : link.err != null ? link.err : "Tap to refresh", mid);
    }

    // a dim title over "10:02 (3m ago)", or "--" when nothing has arrived yet (see AboutDelegate for the refresh)
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

// A tap anywhere on the page, or the button, asks the phone for fresh status. Paging and back are PageDelegate's.
class AboutDelegate extends PageDelegate {
    function initialize() {
        PageDelegate.initialize(ABOUT_PAGE);
    }

    function onTap(evt as WatchUi.ClickEvent) as Boolean {
        link().send("status", null);
        return true;
    }

    function onKey(evt as WatchUi.KeyEvent) as Boolean {
        if (evt.getKey() == WatchUi.KEY_ENTER) {
            link().send("status", null);
            return true;
        }
        return PageDelegate.onKey(evt);
    }
}
