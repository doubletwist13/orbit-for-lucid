import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

// A screen of big round icon choices. Touch: tap one. Buttons: select picks the highlighted one; up/down moves the
// highlight on a choice screen, but on a strip page (stripIndex set) they change page, so only a touch screen can
// reach every icon there.
// Two big items sit side by side, three form a triangle and four a grid. Items marked :small are small buttons in a
// row under them. Any other count would need a new row in BIG or SMALL.
class IconMenu extends WatchUi.View {
    // centre of each item as a fraction of width and height, per count of big or small items
    private const BIG = {2 => [[0.30, 0.50], [0.70, 0.50]], 3 => [[0.31, 0.31], [0.69, 0.31], [0.50, 0.67]],
        4 => [[0.30, 0.30], [0.70, 0.30], [0.30, 0.62], [0.70, 0.62]]};
    private const SMALL = {1 => [[0.50, 0.89]], 2 => [[0.38, 0.89], [0.62, 0.89]]};
    var items as Array<Dictionary>;
    var sel as Number = 0;
    // The focus ring only matters to a button user, so a touch screen shows none until a button or vertical swipe
    // moves it; a watch without touch always shows it.
    var showSel as Boolean = !System.getDeviceSettings().isTouchScreen;
    // set by the delegate on the first choice so a second tap before the screen closes does nothing
    var picked as Boolean = false;
    // this screen's place in the main strip, for the page dots; -1 for a choice screen pushed over it
    var stripIndex as Number = -1;

    function initialize(items as Array<Dictionary>) {
        View.initialize();
        self.items = items;
    }

    // back from a sub-screen makes the menu usable again
    function onShow() as Void {
        picked = false;
    }

    private function small(i as Number) as Boolean {
        return items[i][:small] == true;
    }

    // how many of the page's items are small (or, with false, big)
    private function count(wantSmall as Boolean) as Number {
        var n = 0;
        for (var i = 0; i < items.size(); i++) {
            if (small(i) == wantSmall) { n++; }
        }
        return n;
    }

    private function radius(w as Number, i as Number) as Float {
        return w * (small(i) ? 0.065 : count(false) == 2 ? 0.15 : count(false) == 4 ? 0.12 : 0.14);
    }

    function onUpdate(dc as Dc) as Void {
        dc.setColor(GREEN, Graphics.COLOR_BLACK);
        dc.clear();
        if (stripIndex >= 0) { pageDots(dc, stripIndex); }
        for (var i = 0; i < items.size(); i++) {
            var r = radius(dc.getWidth(), i);
            var c = center(i, dc.getWidth(), dc.getHeight());
            var size = (r * 1.2).toNumber();
            // a thick ring marks the focus; a filled circle would read as an item that is switched on
            dc.setPenWidth(showSel && i == sel ? 8 : 3);
            dc.setColor(GREEN, Graphics.COLOR_BLACK);
            dc.drawCircle(c[0], c[1], r);
            dc.setPenWidth(3);
            Icons.draw(dc, items[i][:icon] as Symbol, c[0] - size / 2, c[1] - size / 2, size);
            var label = items[i][:label] as String?;
            if (label != null) {
                dc.setColor(GREEN, Graphics.COLOR_BLACK);
                dc.drawText(c[0], c[1] + r.toNumber() + 4, Graphics.FONT_SMALL, label, Graphics.TEXT_JUSTIFY_CENTER);
            }
        }
    }

    private function center(i as Number, w as Number, h as Number) as [Number, Number] {
        // this item's place among the others of its size
        var s = small(i);
        var k = 0;
        for (var j = 0; j < i; j++) {
            if (small(j) == s) { k++; }
        }
        var f = ((s ? SMALL : BIG)[count(s)] as Array)[k] as Array<Float>;
        return [(w * f[0]).toNumber(), (h * f[1]).toNumber()];
    }

    // index of the item under a tap, or -1; a little slack because a wrist tap is imprecise
    function hit(x as Number, y as Number, w as Number, h as Number) as Number {
        for (var i = 0; i < items.size(); i++) {
            // the small button gets more slack, since a wrist tap on something that size is hard to land
            var reach = radius(w, i) * (small(i) ? 1.8 : 1.25);
            var c = center(i, w, h);
            if ((x - c[0]) * (x - c[0]) + (y - c[1]) * (y - c[1]) <= reach * reach) { return i; }
        }
        return -1;
    }

    // step the highlight, wrapping round at either end
    function move(by as Number) as Void {
        showSel = true;
        sel = (sel + by + items.size()) % items.size();
        WatchUi.requestUpdate();
    }
}

// Subclasses say what a chosen id does. onTap does not call the base class: it would turn the tap into onSelect as
// well, which then picked the highlighted item as well as the one tapped. Paging and back (swipes, up/down on a strip
// page) are left to PageDelegate.
class IconMenuDelegate extends PageDelegate {
    protected var _menu as IconMenu;

    function initialize(menu as IconMenu) {
        PageDelegate.initialize(menu.stripIndex);
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
            // with no ring showing, the first press only shows where the focus is, so nothing happens unseen
            if (_menu.showSel) {
                pick(_menu.items[_menu.sel][:id] as String);
            } else {
                _menu.showSel = true;
                WatchUi.requestUpdate();
            }
        } else if (_menu.stripIndex < 0 && key == WatchUi.KEY_DOWN) {
            _menu.move(1);
        } else if (_menu.stripIndex < 0 && key == WatchUi.KEY_UP) {
            _menu.move(-1);
        } else {
            return PageDelegate.onKey(evt);
        }
        return true;
    }
}

// The actions pages. Lock, climate, frunk and trunk open a second screen so the choice never depends on the cached
// state; the rest act at once. The pages stay under it, so perform closes only the screens pushed above them.
class ActionsDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        if (id.equals("lock_menu")) {
            lockChoice();
        } else if (id.equals("frunk_menu") || id.equals("trunk_menu")) {
            var lid = id.equals("frunk_menu") ? "frunk" : "trunk";
            // a gas strut frunk can be unlatched but not closed by the car, so there is only one choice: skip the menu
            if (lid.equals("frunk") && link().state != null && link().state["fpower"] == false) {
                perform("frunk_open", 0, null);
                return;
            }
            var menu = new IconMenu([{:icon => lidIcon(lid, true), :id => lid + "_open"},
                {:icon => lidIcon(lid, false), :id => lid + "_close"}]);
            WatchUi.pushView(menu, new ChoiceDelegate(menu), WatchUi.SLIDE_LEFT);
        } else if (id.equals("climate_menu")) {
            // the on icon shows the car's current set point, which it keeps even while climate is off
            var tset = link().state != null ? link().state["tset"] as Numeric? : null;
            var f = Units.fahrenheit();
            var label = tset != null ? Units.label(tset.toFloat(), f) : null;
            var menu = new IconMenu([{:icon => :hvacoff, :id => "climate_off"},
                {:icon => :hvac, :id => "climate_on", :label => label}]);
            WatchUi.pushView(menu, new ClimateDelegate(menu), WatchUi.SLIDE_LEFT);
        } else {
            perform(id, 0, null);
        }
    }
}

// Lock or unlock, from the main screen's button or the actions page.
function lockChoice() as Void {
    var menu = new IconMenu([{:icon => :lock, :id => "lock"}, {:icon => :unlock, :id => "unlock"}]);
    WatchUi.pushView(menu, new ChoiceDelegate(menu), WatchUi.SLIDE_LEFT);
}

// The second screen of lock, frunk or trunk: either choice acts at once (the confirm tap comes from perform).
class ChoiceDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        perform(id, 1, null);
    }
}

// Climate off, or on with a set point picked next.
class ClimateDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        if (id.equals("climate_off")) {
            perform(id, 1, null);
            return;
        }
        var tset = link().state != null ? link().state["tset"] as Numeric? : null;
        var f = Units.fahrenheit();
        var start = Units.indexOf(tset != null ? tset.toFloat() : 21.0f, f);
        // a stock menu, so every watch draws it in its own theme; the title is the unit, the least text that says
        // what is being set
        var menu = new WatchUi.Menu2({:title => f ? "°F" : "°C"});
        for (var i = 0; i < Units.count(f); i++) {
            menu.addItem(new WatchUi.MenuItem(Units.label(Units.at(i, f), f), null, i, {}));
        }
        menu.setFocus(start);
        WatchUi.pushView(menu, new TempMenuDelegate(f), WatchUi.SLIDE_LEFT);
    }
}

// Choosing a set point sends climate_on; back closes only the menu.
class TempMenuDelegate extends WatchUi.Menu2InputDelegate {
    private var _f as Boolean;

    function initialize(f as Boolean) {
        Menu2InputDelegate.initialize();
        _f = f;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        perform("climate_on", 2, Units.at(item.getId() as Number, _f));
    }
}

// The frunk or trunk icon, drawn as the car's model (the Gravity's until the phone has said it is an Air).
function lidIcon(lid as String, open as Boolean) as Symbol {
    var s = link().state;
    var air = s != null && "AIR".equals(s["model"]);
    if (lid.equals("frunk")) {
        return air ? (open ? :afrunkopen : :afrunk) : (open ? :frunkopen : :frunk);
    }
    return air ? (open ? :atrunkopen : :atrunk) : (open ? :trunkopen : :trunk);
}

// Close `depth` menu screens, then send the command, asking first for the ones that change the car beyond locking it.
// temp is the climate_on set point in Celsius, otherwise null.
function perform(id as String, depth as Number, temp as Float?) as Void {
    for (var i = 0; i < depth; i++) {
        WatchUi.popView(WatchUi.SLIDE_IMMEDIATE);
    }
    // climate_on always comes from the set point menu, so it always has a set point to ask about
    var question = id.equals("climate_on") ? "Climate on " + Units.label(temp as Float, Units.fahrenheit()) + "?"
        : CONFIRM[id];
    if (question != null) {
        WatchUi.pushView(new WatchUi.Confirmation(question), new CommandConfirm(id, temp), WatchUi.SLIDE_UP);
    } else {
        link().send(id, null);
    }
}
