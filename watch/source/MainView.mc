import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Time;
import Toybox.WatchUi;

const AMBER = 0xFFAA33;
const STALE_SECS = 1800;
const CONFIRM = {"unlock" => "Unlock?", "climate_off" => "Climate off?", "horn" => "Honk horn?",
    "frunk_open" => "Open frunk?", "frunk_close" => "Close frunk?", "trunk_open" => "Open trunk?",
    "trunk_close" => "Close trunk?", "windows_close" => "Close windows?", "charge_start" => "Start charging?",
    "charge_stop" => "Stop charging?", "port_open" => "Open charge port?", "port_close" => "Close charge port?",
    "precon_on" => "Precondition battery?", "precon_off" => "Stop preconditioning?"};

class MainView extends WatchUi.View {
    function initialize() {
        View.initialize();
    }

    function onUpdate(dc as Dc) as Void {
        var link = link();
        var s = link.state;
        var w = dc.getWidth();
        var h = dc.getHeight();
        var cx = w / 2;
        // old data is dimmed; the car only reports while awake, so this is normal, not an error
        var stale = s != null && Time.now().value() - (s["ts"] as Number) >= STALE_SECS;
        var base = stale ? Theme.dim() : Theme.accent();
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var soc = s != null ? s["soc"] as Number? : null;
        // the charge (ring and number) is the accent until it runs low, then yellow, orange or red (see Charge)
        var fg = stale ? Theme.dim() : Charge.color(soc);
        var rx = (w * 0.33).toNumber();
        var ry = (h * 0.34).toNumber();
        var rr = (w * 0.19).toNumber();
        ring(dc, soc, fg, stale, rx, ry, rr);
        // a transparent background, or the number's box cuts a corner out of the ring
        dc.setColor(fg, Graphics.COLOR_TRANSPARENT);
        socText(dc, soc, rx, ry, rr, h);
        dc.setColor(base, Graphics.COLOR_BLACK);
        air(dc, s, w, h, base);
        rate(dc, s, w, h, base);
        icons(dc, s, w, h, base);
        hints(dc, s, w, h, base);
        tabs(dc, w, h, base);
        // what the app is doing: the age of the data is on the info page, and a stale screen is dimmed
        var note = link.pending != null ? link.pending + "..." : link.err != null ? link.err : "";
        if (note.length() == 0) { return; }
        // a transparent background, or the text's box cuts a notch out of the ring behind it
        dc.setColor(link.err != null ? Graphics.COLOR_RED : Theme.dim(), Graphics.COLOR_TRANSPARENT);
        // a long message must stay inside the round bezel: the usable chord at this height, less the ring
        var y = h * 0.87;
        var dy = y - h / 2;
        var room = 2 * Math.sqrt(cx * cx - dy * dy) - w * 0.08;
        var font = dc.getTextWidthInPixels(note, Graphics.FONT_TINY) <= room ? Graphics.FONT_TINY : Graphics.FONT_XTINY;
        dc.drawText(cx, y, font, note, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // charge as an arc round the number, clockwise from the top
    private function ring(dc as Dc, soc as Number?, fg as Number, stale as Boolean, x as Number, y as Number,
            r as Number) as Void {
        dc.setPenWidth(dc.getWidth() * 0.026);
        dc.setColor(stale ? Theme.trackDim() : Theme.track(), Graphics.COLOR_BLACK);
        dc.drawCircle(x, y, r);
        if (soc == null || soc <= 0) { return; }
        dc.setColor(fg, Graphics.COLOR_BLACK);
        if (soc >= 100) {
            dc.drawCircle(x, y, r);
        } else {
            dc.drawArc(x, y, r, Graphics.ARC_CLOCKWISE, 90, 90 - 360 * soc / 100);
        }
    }

    // the number centred in the ring with a small % under it; the smaller font if three digits would crowd the ring
    private function socText(dc as Dc, soc as Number?, x as Number, y as Number, r as Number, h as Number) as Void {
        var text = soc == null ? "--" : soc.toString();
        var font = dc.getTextWidthInPixels(text, Graphics.FONT_NUMBER_MEDIUM) <= r * 1.5 ? Graphics.FONT_NUMBER_MEDIUM
            : Graphics.FONT_NUMBER_MILD;
        var mid = Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER;
        var ny = y - h * 0.03;
        dc.drawText(x, ny, font, text, mid);
        if (soc != null) { dc.drawText(x, ny + dc.getFontHeight(font) / 2 + h * 0.025, Graphics.FONT_XTINY, "%", mid); }
    }

    // interior and exterior air temperature, one above the other, below the lock button's mark
    private function air(dc as Dc, s as Dictionary?, w as Number, h as Number, base as Number) as Void {
        if (s == null) { return; }
        var f = Units.fahrenheit();
        var keys = ["tin", "tout"];
        var labels = ["int", "ext"];
        var left = Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER;
        for (var i = 0; i < 2; i++) {
            if (s[keys[i]] == null) { continue; }
            var t = Units.whole((s[keys[i]] as Numeric).toFloat(), f) + "°";
            var y = h * (0.42 + 0.08 * i);
            dc.setColor(base, Graphics.COLOR_BLACK);
            dc.drawText(w * 0.58, y, Graphics.FONT_SMALL, t, left);
            dc.setColor(Theme.dim(), Graphics.COLOR_BLACK);
            dc.drawText(w * 0.58 + dc.getTextWidthInPixels(t, Graphics.FONT_SMALL) + w * 0.012, y + h * 0.01,
                Graphics.FONT_XTINY, labels[i], left);
        }
    }

    // charge power in kW with a bolt, between the ring and the lock button's mark; the phone sends it only while
    // charging
    private function rate(dc as Dc, s as Dictionary?, w as Number, h as Number, base as Number) as Void {
        if (s == null || s["rate"] == null) { return; }
        dc.setColor(base, Graphics.COLOR_BLACK);
        var left = Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER;
        var bolt = (w * 0.08).toNumber();
        var y = h * 0.175;
        var x = w * 0.46;
        Icons.draw(dc, :bolt, x.toNumber(), (y - bolt / 2).toNumber(), bolt);
        x += bolt + w * 0.01;
        var n = (s["rate"] as Numeric).toNumber().toString();
        dc.drawText(x, y, Graphics.FONT_SMALL, n, left);
        dc.drawText(x + dc.getTextWidthInPixels(n, Graphics.FONT_SMALL) + w * 0.008, y + h * 0.012, Graphics.FONT_XTINY,
            "kW", left);
    }

    // small filled slices at the top and bottom edge: tap for info or actions, like a swipe down or up. The bottom one
    // has three dots for "more".
    private function tabs(dc as Dc, w as Number, h as Number, base as Number) as Void {
        var cx = w / 2;
        var depth = (h * 0.07).toNumber();
        var r = (w * 0.17).toNumber();
        dc.setColor(base, Graphics.COLOR_BLACK);
        dc.fillCircle(cx, depth - r, r);
        dc.fillCircle(cx, h - depth + r, r);
        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_TRANSPARENT);
        dc.drawText(cx, depth * 0.45, Graphics.FONT_XTINY, "i",
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        var dot = (w * 0.008).toNumber() + 1;
        for (var i = -1; i <= 1; i++) {
            dc.fillCircle(cx + i * dot * 4, h - depth * 0.5, dot);
        }
    }

    // state icons: lock and plug always (like the glance), climate only while it is on, and in amber an open frunk,
    // trunk or door and a tyre warning, only while they apply
    private function icons(dc as Dc, s as Dictionary?, w as Number, h as Number, base as Number) as Void {
        if (s == null) { return; }
        var lockId = s["locked"] == null ? :lockunknown : s["locked"] ? :lock : :unlock;
        // an occupant warning comes first, in red; the other warnings follow the lock, plug and climate in amber
        var ids = [] as Array<Symbol>;
        if (s["occ"] == true) { ids.add(:occupant); }
        ids.add(lockId);
        ids.add(Charge.plug(s["charge"] as String?));
        var plain = ids.size();
        if (s["hvac"] == true) { ids.add(:hvac); plain++; }
        if (s["frunk"] == true) { ids.add(lidIcon("frunk", true)); }
        if (s["trunk"] == true) { ids.add(lidIcon("trunk", true)); }
        if (s["door"] == true) { ids.add(:door); }
        if (s["tire"] == true) { ids.add(:tire); }
        // smaller icons if they would not all fit the width of the screen at this height
        var size = (w * 0.105).toNumber();
        var fit = (w * 0.8 / (ids.size() + (ids.size() - 1) / 5.0)).toNumber();
        if (fit < size) { size = fit; }
        var gap = size / 5;
        var x = (w - (ids.size() * size + (ids.size() - 1) * gap)) / 2;
        for (var i = 0; i < ids.size(); i++) {
            var id = ids[i];
            // an unknown lock is amber, so it is not read as locked or unlocked
            var warn = id == :lockunknown || i >= plain;
            dc.setColor(id == :occupant ? Graphics.COLOR_RED : warn ? AMBER : base, Graphics.COLOR_BLACK);
            var ix = x + i * (size + gap);
            var iy = (h * 0.66).toNumber();
            if (id == :occupant) {
                Warn.occupant(dc, ix, iy, size);
            } else if (id == :door) {
                Warn.door(dc, ix, iy, size);
            } else if (id == :tire) {
                Warn.tire(dc, ix, iy, size);
            } else {
                Icons.draw(dc, id, ix, iy, size);
            }
        }
    }

    // page dots at the left edge, and a bezel mark by the top right button with a locked and an unlocked padlock beside
    // it, since it opens the choice between them; the colour follows the lock state, like the lock icon
    private function hints(dc as Dc, s as Dictionary?, w as Number, h as Number, base as Number) as Void {
        pageDots(dc, MAIN_PAGE);
        dc.setColor(s == null || s["locked"] != true ? AMBER : base, Graphics.COLOR_BLACK);
        dc.setPenWidth((w * 0.025).toNumber());
        dc.drawArc(w / 2, h / 2, w * 0.455, Graphics.ARC_COUNTER_CLOCKWISE, 22, 42);
        var size = (w * 0.075).toNumber();
        var x = (w * 0.70).toNumber();
        var y = (h * 0.30).toNumber() - size / 2;
        Icons.draw(dc, :lock, x, y, size);
        Icons.draw(dc, :unlock, x + (size * 1.3).toNumber(), y, size);
        dc.setPenWidth(2);
        dc.drawLine(x + size, y + size, x + (size * 1.35).toNumber(), y);
    }
}

// The main screen is for reading: the button opens lock/unlock, and the strip pages to the others. A tap does nothing
// except on the top and bottom slices, which page like a swipe. The base class is not called for a tap because it would
// turn it into onSelect.
class MainDelegate extends PageDelegate {
    function initialize() {
        PageDelegate.initialize(MAIN_PAGE);
    }

    function onKey(evt as WatchUi.KeyEvent) as Boolean {
        if (evt.getKey() == WatchUi.KEY_ENTER) {
            lockChoice();
            return true;
        }
        return PageDelegate.onKey(evt);
    }

    // the zones are bigger than what is drawn, since a wrist tap on something that small is hard to land
    function onTap(evt as WatchUi.ClickEvent) as Boolean {
        var xy = evt.getCoordinates();
        var ds = System.getDeviceSettings();
        // the lock button's mark and padlocks open the same choice as the button
        var dx = xy[0] - ds.screenWidth * 0.77;
        var dy = xy[1] - ds.screenHeight * 0.30;
        if (dx * dx + dy * dy < ds.screenWidth * 0.12 * ds.screenWidth * 0.12) {
            lockChoice();
            return true;
        }
        if ((xy[0] - ds.screenWidth / 2).abs() < ds.screenWidth * 0.22) {
            if (xy[1] < ds.screenHeight * 0.14) {
                onPreviousPage();
            } else if (xy[1] > ds.screenHeight * 0.86) {
                onNextPage();
            }
        }
        return true;
    }
}

class CommandConfirm extends WatchUi.ConfirmationDelegate {
    private var _cmd as String;
    private var _arg as Float?;

    function initialize(cmd as String, arg as Float?) {
        ConfirmationDelegate.initialize();
        _cmd = cmd;
        _arg = arg;
    }

    function onResponse(response as WatchUi.Confirm) as Boolean {
        if (response == WatchUi.CONFIRM_YES) { sendNow(_cmd, _arg); }
        return true;
    }
}
