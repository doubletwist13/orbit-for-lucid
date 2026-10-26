import Toybox.Application;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// Glance (carousel) view: draws the state the main app cached and never talks to the phone, so it costs no battery.
// Glances run with a small memory limit and only see (:glance) code, hence the literals instead of MainView's consts.
(:glance)
class OrbitGlance extends WatchUi.GlanceView {
    function initialize() {
        GlanceView.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var s = Application.Storage.getValue("s") as Dictionary?;
        var h = dc.getHeight();
        var left = Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.setColor(0x00FF66, Graphics.COLOR_BLACK);
        dc.clear();
        dc.drawText(0, h * 0.25, Graphics.FONT_GLANCE, "> Orbit", left);
        var line = "No data";
        if (s != null) {
            // no age or dimming here: a glance is not redrawn between pushes, so an age would only go stale
            var soc = s["soc"] as Number?;
            line = (soc == null || soc < 0 || soc > 100 ? "--" : soc) + "% " + (s["locked"] ? "Locked" : "Unlocked");
        }
        dc.drawText(0, h * 0.70, Graphics.FONT_GLANCE, line, left);
    }
}
