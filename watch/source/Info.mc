import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The page above the main screen: the car's numbers as text, label and value in two columns. A value the car did not
// report shows "--" so every row stays where it is.
class InfoView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var s = link().state;
        var w = dc.getWidth();
        var h = dc.getHeight();
        var accent = Theme.accent();
        var dim = Theme.dim();
        dc.setColor(accent, Graphics.COLOR_BLACK);
        dc.clear();
        pageDots(dc, INFO_PAGE);
        var f = Units.fahrenheit();
        var imperial = Units.imperial();
        var unit = imperial ? "PSI" : "Bar";
        var rows = [
            ["HVAC", s == null || s["hvac"] == null ? "--" : (s["hvac"] ? "On " : "Off ") + temp(s, "tset", f)],
            ["In/Out", s != null && s["tin"] != null && s["tout"] != null
                ? temp(s, "tin", f) + " / " + temp(s, "tout", f) : "--"],
            ["Cells", s != null && s["cmin"] != null && s["cmax"] != null
                ? Units.whole((s["cmin"] as Numeric).toFloat(), f) + "-" + temp(s, "cmax", f) : "--"],
            ["SoH", s != null && s["soh"] != null ? (s["soh"] as Numeric).toFloat().format("%.1f") + "%" : "--"],
            ["Odo", s != null && s["odo"] != null ? Units.distance(s["odo"] as Numeric, imperial) : "--"],
            [unit + " F", tires(s, 0, imperial)],
            [unit + " R", tires(s, 2, imperial)]] as Array<Array<String> >;
        var pitch = h * 0.095;
        var top = h * 0.5 - pitch * (rows.size() - 1) / 2;
        for (var i = 0; i < rows.size(); i++) {
            var y = top + i * pitch;
            dc.setColor(dim, Graphics.COLOR_BLACK);
            dc.drawText(w * 0.42, y, Graphics.FONT_SMALL, rows[i][0],
                Graphics.TEXT_JUSTIFY_RIGHT | Graphics.TEXT_JUSTIFY_VCENTER);
            dc.setColor(accent, Graphics.COLOR_BLACK);
            dc.drawText(w * 0.46, y, Graphics.FONT_SMALL, rows[i][1],
                Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
        }
    }

    private function temp(s as Dictionary?, key as String, f as Boolean) as String {
        return s != null && s[key] != null ? Units.whole((s[key] as Numeric).toFloat(), f) + "°" : "--";
    }

    // two wheels of the phone's list (front left, front right, rear left, rear right), "41 / 41"; the label has the
    // unit
    private function tires(s as Dictionary?, first as Number, imperial as Boolean) as String {
        var tp = s != null ? s["tp"] as Array<Numeric>? : null;
        if (tp == null || tp.size() < first + 2) { return "--"; }
        return Units.pressure(tp[first], imperial) + " / " + Units.pressure(tp[first + 1], imperial);
    }
}
