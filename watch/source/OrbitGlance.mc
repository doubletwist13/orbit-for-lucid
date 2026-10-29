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
        var y = h * 0.70;
        if (s == null) {
            dc.drawText(0, y, Graphics.FONT_GLANCE, "No data", left);
            return;
        }
        // no age or dimming here: a glance is not redrawn between pushes, so an age would only go stale
        var soc = s["soc"] as Number?;
        var text = (soc == null || soc < 0 || soc > 100 ? "--" : soc) + "%";
        var size = dc.getFontHeight(Graphics.FONT_GLANCE);
        var gap = size / 3;
        var top = (y - size / 2).toNumber();
        var c = s["charge"] as String?;
        var plug = c == null || c.equals("NOT_CONNECTED") || c.equals("UNKNOWN") ? :plugoff
            : c.equals("CHARGING") ? :bolt : :plug;
        var x = dc.getTextWidthInPixels(text, Graphics.FONT_GLANCE);
        dc.drawText(0, y, Graphics.FONT_GLANCE, text, left);
        var ids = [:battery, s["locked"] ? :lock : :unlock, plug] as Array<Symbol>;
        for (var i = 0; i < ids.size(); i++) {
            x += gap;
            Icons.draw(dc, ids[i], x, top, size);
            x += size;
        }
    }
}
