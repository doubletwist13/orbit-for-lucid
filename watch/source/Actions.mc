import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// A screen of big round icon choices. Touch: tap one. Buttons: up/down moves the highlight, select picks it.
// Two items sit side by side, three form a triangle; a fourth slot would need a new row in SLOTS.
class IconMenu extends WatchUi.View {
    // centre of each item as a fraction of width and height, per item count
    private const SLOTS = {2 => [[0.30, 0.50], [0.70, 0.50]], 3 => [[0.31, 0.31], [0.69, 0.31], [0.50, 0.67]]};
    var items as Array<Dictionary>;
    var sel as Number = 0;
    // set by the delegate on the first choice so a second tap before the screen closes does nothing
    var picked as Boolean = false;

    function initialize(items as Array<Dictionary>) {
        View.initialize();
        self.items = items;
    }

    // back from a sub-screen makes the menu usable again
    function onShow() as Void {
        picked = false;
    }

    private function radius(w as Number) as Float {
        return w * (items.size() == 2 ? 0.15 : 0.14);
    }

    function onUpdate(dc as Dc) as Void {
        var r = radius(dc.getWidth());
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        dc.clear();
        for (var i = 0; i < items.size(); i++) {
            var c = center(i, dc.getWidth(), dc.getHeight());
            var size = (r * 1.2).toNumber();
            dc.setPenWidth(3);
            dc.setColor(GREEN, Graphics.COLOR_BLACK);
            if (i == sel) {
                dc.fillCircle(c[0], c[1], r);
                dc.setColor(Graphics.COLOR_BLACK, GREEN);
            } else {
                dc.drawCircle(c[0], c[1], r);
            }
            Icons.draw(dc, items[i][:icon] as Symbol, c[0] - size / 2, c[1] - size / 2, size);
            var label = items[i][:label] as String?;
            if (label != null) {
                dc.setColor(GREEN, Graphics.COLOR_BLACK);
                dc.drawText(c[0], c[1] + r.toNumber() + 4, Graphics.FONT_SMALL, label, Graphics.TEXT_JUSTIFY_CENTER);
            }
        }
    }

    private function center(i as Number, w as Number, h as Number) as [Number, Number] {
        var f = (SLOTS[items.size()] as Array)[i] as Array<Float>;
        return [(w * f[0]).toNumber(), (h * f[1]).toNumber()];
    }

    // index of the item under a tap, or -1; a little slack because a wrist tap is imprecise
    function hit(x as Number, y as Number, w as Number, h as Number) as Number {
        var reach = radius(w) * 1.25;
        for (var i = 0; i < items.size(); i++) {
            var c = center(i, w, h);
            if ((x - c[0]) * (x - c[0]) + (y - c[1]) * (y - c[1]) <= reach * reach) { return i; }
        }
        return -1;
    }

    function move(by as Number) as Void {
        sel = (sel + by + items.size()) % items.size();
        WatchUi.requestUpdate();
    }
}

// Subclasses say what a chosen id does. A raw InputDelegate, not a BehaviorDelegate: that one turns a tap into both
// onTap and onSelect, and the select then picked the highlighted item as well as the one tapped.
class IconMenuDelegate extends WatchUi.InputDelegate {
    protected var _menu as IconMenu;

    function initialize(menu as IconMenu) {
        InputDelegate.initialize();
        _menu = menu;
    }

    function choose(id as String) as Void {}

    // Each choice closes screens, so a second one before that happens would close too many and could exit the app.
    private function pick(id as String) as Void {
        if (_menu.picked) { return; }
        _menu.picked = true;
        choose(id);
    }

    function onTap(evt as WatchUi.ClickEvent) as Boolean {
        var xy = evt.getCoordinates();
        var ds = System.getDeviceSettings();
        var i = _menu.hit(xy[0], xy[1], ds.screenWidth, ds.screenHeight);
        if (i >= 0) { pick(_menu.items[i][:id] as String); }
        return true;
    }

    // buttons: select picks the highlighted item; anything else (back) falls through to the system
    function onKey(evt as WatchUi.KeyEvent) as Boolean {
        var key = evt.getKey();
        if (key == WatchUi.KEY_ENTER) {
            pick(_menu.items[_menu.sel][:id] as String);
        } else if (key == WatchUi.KEY_DOWN) {
            _menu.move(1);
        } else if (key == WatchUi.KEY_UP) {
            _menu.move(-1);
        } else {
            return false;
        }
        return true;
    }

    // a vertical swipe steps the highlight too; a swipe right stays the system's back gesture
    function onSwipe(evt as WatchUi.SwipeEvent) as Boolean {
        var dir = evt.getDirection();
        if (dir == WatchUi.SWIPE_UP) {
            _menu.move(1);
        } else if (dir == WatchUi.SWIPE_DOWN) {
            _menu.move(-1);
        } else {
            return false;
        }
        return true;
    }
}

// Lock, climate, refresh. Lock opens a second screen so the choice never depends on the cached lock state.
class ActionsDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        if (id.equals("lock_menu")) {
            var menu = new IconMenu([{:icon => :lock, :id => "lock"}, {:icon => :unlock, :id => "unlock"}]);
            WatchUi.pushView(menu, new LockDelegate(menu), WatchUi.SLIDE_LEFT);
        } else if (id.equals("climate_menu")) {
            // the on icon shows the car's current set point, which it keeps even while climate is off
            var tset = link().state != null ? link().state["tset"] as Numeric? : null;
            var f = Units.fahrenheit();
            var menu = new IconMenu([{:icon => :hvacoff, :id => "climate_off"},
                {:icon => :hvac, :id => "climate_on", :label => tset != null ? Units.label(tset.toFloat(), f) : null}]);
            WatchUi.pushView(menu, new ClimateDelegate(menu), WatchUi.SLIDE_LEFT);
        } else {
            perform(id, 1, null);
        }
    }
}

class LockDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        perform(id, 2, null);
    }
}

// Climate off, or on with a set point picked next.
class ClimateDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        if (id.equals("climate_off")) {
            perform(id, 2, null);
            return;
        }
        var tset = link().state != null ? link().state["tset"] as Numeric? : null;
        var f = Units.fahrenheit();
        var start = Units.indexOf(tset != null ? tset.toFloat() : 21.0f, f);
        // the title is the unit, the least text that tells you what is being set
        var title = new WatchUi.Text({:text => f ? "°F" : "°C", :color => GREEN, :font => Graphics.FONT_SMALL,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER, :locY => WatchUi.LAYOUT_VALIGN_BOTTOM});
        WatchUi.pushView(new WatchUi.Picker({:title => title, :pattern => [new TempFactory(f)],
            :defaults => [start]}), new TempPickerDelegate(), WatchUi.SLIDE_LEFT);
    }
}

// The values the picker scrolls through: Units.count(f) set points across the car's range.
class TempFactory extends WatchUi.PickerFactory {
    private var _f as Boolean;

    function initialize(f as Boolean) {
        PickerFactory.initialize();
        _f = f;
    }

    function getSize() as Number {
        return Units.count(_f);
    }

    function getValue(index as Number) as Object? {
        return Units.at(index, _f);
    }

    function getDrawable(index as Number, selected as Boolean) as WatchUi.Drawable? {
        return new WatchUi.Text({:text => Units.label(Units.at(index, _f), _f), :font => Graphics.FONT_LARGE,
            :color => selected ? GREEN : DIM, :justification => Graphics.TEXT_JUSTIFY_CENTER,
            :locX => WatchUi.LAYOUT_HALIGN_CENTER, :locY => WatchUi.LAYOUT_VALIGN_CENTER});
    }
}

// The picker's own check button accepts; back or cancel closes only the picker.
class TempPickerDelegate extends WatchUi.PickerDelegate {
    function initialize() {
        PickerDelegate.initialize();
    }

    function onAccept(values as Array) as Boolean {
        perform("climate_on", 3, values[0] as Float);
        return true;
    }

    function onCancel() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
        return true;
    }
}

// Close `depth` menu screens, then send the command, asking first for the ones that change the car beyond locking it.
// temp is the climate_on set point in Celsius, otherwise null.
function perform(id as String, depth as Number, temp as Float?) as Void {
    for (var i = 0; i < depth; i++) {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }
    // climate_on always comes from the picker, so it always has a set point to ask about
    var question = id.equals("climate_on") ? "Climate on " + Units.label(temp as Float, Units.fahrenheit()) + "?"
        : CONFIRM[id];
    if (question != null) {
        WatchUi.pushView(new WatchUi.Confirmation(question), new CommandConfirm(id, temp), WatchUi.SLIDE_UP);
    } else {
        link().send(id, null);
    }
}
