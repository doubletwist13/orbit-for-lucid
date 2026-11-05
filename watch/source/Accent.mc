import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The accent picker, opened by holding any page (PageDelegate.onMenu). A stock menu, so every watch draws it in its own
// theme; each item has a dot in its colour, and choosing one saves it and closes the menu.
function pushAccentMenu() as Void {
    var menu = new WatchUi.Menu2({:title => "Colour"});
    for (var i = 0; i < Theme.COLORS.size(); i++) {
        menu.addItem(new WatchUi.IconMenuItem(Theme.NAMES[i], null, i, new Swatch(Theme.COLORS[i]), {}));
    }
    menu.setFocus(Theme.index());
    WatchUi.pushView(menu, new AccentDelegate(), WatchUi.SLIDE_LEFT);
}

class Swatch extends WatchUi.Drawable {
    private var _color as Number;

    function initialize(color as Number) {
        Drawable.initialize({});
        _color = color;
    }

    function draw(dc as Dc) as Void {
        var r = dc.getHeight() / 2;
        dc.setColor(_color, Graphics.COLOR_BLACK);
        dc.fillCircle(dc.getWidth() / 2, r, r * 0.6);
    }
}

class AccentDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        Theme.set(item.getId() as Number);
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
    }
}
