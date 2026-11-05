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
        var accent = Theme.accent();
        // no dc.clear() and a transparent background throughout: the system draws the card's gradient behind this view
        dc.setColor(accent, Graphics.COLOR_TRANSPARENT);
        // an unattended occupant is urgent enough to take over the title, in red
        var occupant = s != null && s["occ"] == true;
        if (occupant) { dc.setColor(0xFF0000, Graphics.COLOR_TRANSPARENT); }
        dc.drawText(0, h * 0.25, Graphics.FONT_GLANCE, occupant ? "! Occupant in car" : "> Orbit", left);
        if (occupant) { dc.setColor(accent, Graphics.COLOR_TRANSPARENT); }
        var y = h * 0.70;
        if (s == null) {
            dc.drawText(0, y, Graphics.FONT_GLANCE, "No data", left);
            return;
        }
        // no age or dimming here: a glance is not redrawn between pushes, so an age would only go stale
        var soc = s["soc"] as Number?;
        if (soc != null && (soc < 0 || soc > 100)) { soc = null; }
        var text = (soc == null ? "--" : soc) + "%";
        var size = dc.getFontHeight(Graphics.FONT_GLANCE);
        var gap = size / 3;
        var top = (y - size / 2).toNumber();
        var x = dc.getTextWidthInPixels(text, Graphics.FONT_GLANCE);
        // the charge (number and battery) takes its colour from the level, like the main screen's ring
        dc.setColor(Charge.color(soc), Graphics.COLOR_TRANSPARENT);
        dc.drawText(0, y, Graphics.FONT_GLANCE, text, left);
        // the upright battery only uses the middle half of its cell, so it is drawn a quarter back and takes half
        x += gap;
        Icons.draw(dc, :battery, x - size / 4, top, size);
        x += size / 2;
        dc.setColor(accent, Graphics.COLOR_TRANSPARENT);
        // the padlock is a quarter bigger, so its open shackle is easier to tell from a closed one
        var big = size * 5 / 4;
        x += gap;
        var unknown = s["locked"] == null;
        if (unknown) { dc.setColor(0xFFAA33, Graphics.COLOR_TRANSPARENT); } // amber: not locked, not unlocked
        Icons.draw(dc, unknown ? :lockunknown : s["locked"] ? :lock : :unlock, x, top - (big - size) / 2, big);
        dc.setColor(accent, Graphics.COLOR_TRANSPARENT);
        x += big + gap;
        var rate = s["rate"] as Number?;
        if (rate == null) {
            Icons.draw(dc, Charge.plug(s["charge"] as String?), x, top, size);
            return;
        }
        // charging: the power in kW stands in for the plug, with a bolt; the unit only if there is room for it
        Icons.draw(dc, :bolt, x - size / 4, top, size);
        x += size / 2 + gap / 2;
        var kw = rate.toString();
        dc.drawText(x, y, Graphics.FONT_GLANCE, kw, left);
        x += dc.getTextWidthInPixels(kw, Graphics.FONT_GLANCE) + gap / 2;
        if (x + dc.getTextWidthInPixels("kW", Graphics.FONT_XTINY) <= dc.getWidth()) {
            dc.drawText(x, y, Graphics.FONT_XTINY, "kW", left);
        }
    }
}
