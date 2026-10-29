import Toybox.Graphics;
import Toybox.Lang;
import Toybox.Math;
import Toybox.Time;
import Toybox.WatchUi;

const GREEN = 0x00FF66;
const DIM = 0x338855;
const AMBER = 0xFFAA33;
const YELLOW = 0xFFDD00;
const ORANGE = 0xFF7700;
const RED = 0xFF3333;
const TRACK = 0x12331F;
const TRACK_DIM = 0x12251A;
const STALE_SECS = 1800;
const CONFIRM = {"unlock" => "Unlock?", "climate_off" => "Climate off?"};

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
        var base = stale ? DIM : GREEN;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();
        var soc = s != null ? s["soc"] as Number? : null;
        // the charge (ring, number, battery icon) takes its colour from the level; everything else stays green
        var fg = stale ? DIM : socColor(soc);
        ring(dc, soc, fg, stale);
        dc.setColor(fg, Graphics.COLOR_BLACK);
        socText(dc, soc, w, h);
        dc.setColor(base, Graphics.COLOR_BLACK);
        air(dc, s, w, h);
        icons(dc, s, w, h);
        var note = link.pending != null ? link.pending + "..." : link.err != null ? link.err : age(s);
        // a transparent background, or the text's box cuts a notch out of the ring behind it
        dc.setColor(link.err != null ? Graphics.COLOR_RED : stale ? AMBER : DIM, Graphics.COLOR_TRANSPARENT);
        // a long message must stay inside the round bezel: the usable chord at this height, less the ring
        var y = h * 0.87;
        var dy = y - h / 2;
        var room = 2 * Math.sqrt(cx * cx - dy * dy) - w * 0.08;
        var font = dc.getTextWidthInPixels(note, Graphics.FONT_TINY) <= room ? Graphics.FONT_TINY : Graphics.FONT_XTINY;
        dc.drawText(cx, y, font, note, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // green from 50, yellow from 25, orange from 10, red below; no reading keeps the normal green
    private function socColor(soc as Number?) as Number {
        if (soc == null || soc >= 50) { return GREEN; }
        return soc >= 25 ? YELLOW : soc >= 10 ? ORANGE : RED;
    }

    // charge as an arc round the bezel, clockwise from the top
    private function ring(dc as Dc, soc as Number?, fg as Number, stale as Boolean) as Void {
        var pen = dc.getWidth() * 0.03;
        var r = dc.getWidth() / 2 - pen / 2 - 1;
        dc.setPenWidth(pen);
        dc.setColor(stale ? TRACK_DIM : TRACK, Graphics.COLOR_BLACK);
        dc.drawCircle(dc.getWidth() / 2, dc.getHeight() / 2, r);
        if (soc == null || soc <= 0) { return; }
        dc.setColor(fg, Graphics.COLOR_BLACK);
        if (soc >= 100) {
            dc.drawCircle(dc.getWidth() / 2, dc.getHeight() / 2, r);
        } else {
            dc.drawArc(dc.getWidth() / 2, dc.getHeight() / 2, r, Graphics.ARC_CLOCKWISE, 90, 90 - 360 * soc / 100);
        }
    }

    // the number, then a small % and battery icon at its top right, the group centred on the screen
    private function socText(dc as Dc, soc as Number?, w as Number, h as Number) as Void {
        var font = Graphics.FONT_NUMBER_MEDIUM;
        var text = soc == null ? "--" : soc.toString();
        var numW = dc.getTextWidthInPixels(text, font);
        var pctW = soc == null ? 0 : dc.getTextWidthInPixels("%", Graphics.FONT_SMALL);
        var bat = (w * 0.075).toNumber();
        var gap = (w * 0.012).toNumber();
        // the upright battery only uses the middle half of its cell, so it takes half the width, drawn a quarter back
        var x = (w - (numW + gap + pctW + (soc == null ? 0 : gap + bat / 2))) / 2;
        var left = Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER;
        dc.drawText(x, h * 0.37, font, text, left);
        if (soc == null) { return; }
        x += numW + gap;
        dc.drawText(x, h * 0.31, Graphics.FONT_SMALL, "%", left);
        Icons.draw(dc, :battery, x + pctW + gap - bat / 4, (h * 0.31).toNumber() - bat / 2, bat);
    }

    // inside/outside air temperature as "21°/14°", left aligned so there is room beside it for more later
    private function air(dc as Dc, s as Dictionary?, w as Number, h as Number) as Void {
        if (s == null || (s["tin"] == null && s["tout"] == null)) { return; }
        var f = Units.fahrenheit();
        var inside = s["tin"] != null ? Units.whole((s["tin"] as Numeric).toFloat(), f) : "--";
        var outside = s["tout"] != null ? Units.whole((s["tout"] as Numeric).toFloat(), f) : "--";
        dc.drawText(w * 0.22, h * 0.54, Graphics.FONT_SMALL, inside + "°/" + outside + "°",
            Graphics.TEXT_JUSTIFY_LEFT | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // state icons, shown only when they apply: lock, plug, charging bolt, climate
    private function icons(dc as Dc, s as Dictionary?, w as Number, h as Number) as Void {
        if (s == null) { return; }
        var ids = [s["locked"] ? :lock : :unlock] as Array<Symbol>;
        var c = s["charge"] as String?;
        if (c != null && !c.equals("NOT_CONNECTED") && !c.equals("UNKNOWN")) {
            ids.add(:plug);
            if (c.equals("CHARGING")) { ids.add(:bolt); }
        }
        if (s["hvac"] == true) { ids.add(:hvac); }
        var size = (w * 0.105).toNumber();
        var gap = size / 5;
        var x = (w - (ids.size() * size + (ids.size() - 1) * gap)) / 2;
        for (var i = 0; i < ids.size(); i++) {
            Icons.draw(dc, ids[i], x + i * (size + gap), (h * 0.66).toNumber(), size);
        }
    }

    private function age(s as Dictionary?) as String {
        if (s == null) { return ""; }
        var secs = Time.now().value() - (s["ts"] as Number);
        return secs < 60 ? "now" : secs < 3600 ? secs / 60 + "m" : secs / 3600 + "h";
    }
}

class MainDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() as Boolean {
        // the icon shows the cached lock state; the choice itself is made on the next screen
        var s = link().state;
        var menu = new IconMenu([
            {:icon => s != null && s["locked"] == false ? :unlock : :lock, :id => "lock_menu"},
            {:icon => :hvac, :id => "climate_menu"},
            {:icon => :refresh, :id => "status"},
            {:icon => :info, :id => "info"}]);
        WatchUi.pushView(menu, new ActionsDelegate(menu), WatchUi.SLIDE_UP);
        return true;
    }
}

class CommandConfirm extends WatchUi.ConfirmationDelegate {
    private var _cmd as String;
    private var _temp as Float?;

    function initialize(cmd as String, temp as Float?) {
        ConfirmationDelegate.initialize();
        _cmd = cmd;
        _temp = temp;
    }

    function onResponse(response as WatchUi.Confirm) as Boolean {
        if (response == WatchUi.CONFIRM_YES) { link().send(_cmd, _temp); }
        return true;
    }
}
