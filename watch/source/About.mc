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
        var accent = Theme.accent();
        var mid = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(accent, Graphics.COLOR_BLACK);
        dc.clear();
        pageDots(dc, ABOUT_PAGE);
        // the colour button, outlined at the top as its own target, apart from the refresh tap (see AboutDelegate)
        dc.setPenWidth(2);
        dc.drawRoundedRectangle(w * 0.27, h * 0.07, w * 0.46, h * 0.10, h * 0.05);
        dc.drawText(w / 2, h * 0.12, Graphics.FONT_XTINY, "Colour: " + Theme.NAMES[Theme.index()], mid);
        row(dc, w / 2, h * 0.22, "Car data", s != null ? s["ts"] as Number? : null);
        row(dc, w / 2, h * 0.40, "Phone update", s != null ? s["pt"] as Number? : null);
        // which builds are on the watch and the phone, for spotting a mismatch and for bug reports
        dc.setColor(Theme.dim(), Graphics.COLOR_BLACK);
        var pv = s != null ? s["pv"] as String? : null;
        dc.drawText(w / 2, h * 0.80, Graphics.FONT_XTINY, "Orbit " + WatchUi.loadResource(Rez.Strings.Version), mid);
        dc.drawText(w / 2, h * 0.88, Graphics.FONT_XTINY, "Phone " + (pv != null ? pv : "--"), mid);
        // the way to refresh: what the app is doing, an error in red, or the hint
        var link = link();
        dc.setColor(link.err != null ? Graphics.COLOR_RED : accent, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.68, Graphics.FONT_TINY,
            link.pending != null ? link.pending + "..." : link.err != null ? link.err : "Tap to refresh", mid);
    }

    // a dim title over "10:02 (3m ago)", or "--" when nothing has arrived yet (see AboutDelegate for the refresh)
    private function row(dc as Dc, x as Number, y as Float, title as String, at as Number?) as Void {
        dc.setColor(Theme.dim(), Graphics.COLOR_BLACK);
        dc.drawText(x, y, Graphics.FONT_XTINY, title, Graphics.TEXT_JUSTIFY_CENTER);
        dc.setColor(Theme.accent(), Graphics.COLOR_BLACK);
        dc.drawText(x, y + dc.getFontHeight(Graphics.FONT_XTINY), Graphics.FONT_SMALL,
            at != null ? Units.clock(at) + " (" + ago(Time.now().value() - at) + ")" : "--", Graphics.TEXT_JUSTIFY_CENTER);
    }

    private function ago(secs as Number) as String {
        return secs < 60 ? "now" : secs < 3600 ? secs / 60 + "m ago" : secs / 3600 + "h ago";
    }
}

// A tap on the colour button at the top opens the accent picker, the way in on a watch with no menu button (the other
// watches also have a held button, see PageDelegate). A tap anywhere else, or the button, asks the phone for fresh
// status. Paging and back are PageDelegate's.
class AboutDelegate extends PageDelegate {
    function initialize() {
        PageDelegate.initialize(ABOUT_PAGE);
    }

    function onTap(evt as WatchUi.ClickEvent) as Boolean {
        // the whole top fifth, so the button is easy to hit; the first row starts below it
        if (evt.getCoordinates()[1] < System.getDeviceSettings().screenHeight * 0.2) {
            pushAccentMenu();
        } else {
            link().send("status", null);
        }
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
