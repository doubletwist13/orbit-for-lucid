import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The last page of the strip: start or stop charging, the charge port and the charge limit. The bolt is one icon that
// follows the car (filled while it charges, like pet mode), so the choice never depends on a stale page.
function chargeItems() as Array<Dictionary> {
    var s = link().state;
    var charging = s != null && "CHARGING".equals(s["charge"]);
    var kw = charging && s["rate"] != null ? (s["rate"] as Numeric).toNumber() + " kW" : null;
    var lim = s != null && s["lim"] != null ? (s["lim"] as Numeric).toNumber() + "%" : null;
    // giving power out (V2X): nothing to start or stop from here, so the icon only says so
    var out = s != null && Charge.discharging(s["charge"] as String?);
    var power = out ? {:icon => :discharge, :id => "discharging", :label => "Discharging"}
        : {:icon => :bolt, :id => charging ? "charge_stop" : "charge_start", :on => charging, :label => kw};
    return [power,
        {:icon => s != null && s["port"] == true ? :portopen : :portclosed, :id => "port_menu"},
        {:icon => :limit, :id => "limit_menu", :label => lim}] as Array<Dictionary>;
}

// Rebuilds its items on every draw, so the power, limit and port follow the car while the page is open.
class ChargeMenu extends IconMenu {
    function initialize() {
        IconMenu.initialize(chargeItems());
    }

    function onUpdate(dc as Dc) as Void {
        items = chargeItems();
        IconMenu.onUpdate(dc);
    }
}

// The port and the limit open a second screen, like the frunk does; the bolt acts at once (after its confirm tap).
class ChargeDelegate extends IconMenuDelegate {
    function initialize(menu as IconMenu) {
        IconMenuDelegate.initialize(menu);
    }

    function choose(id as String) as Void {
        if (id.equals("discharging")) {
            return;
        } else if (id.equals("port_menu")) {
            var menu = new IconMenu([{:icon => :portopen, :id => "port_open"},
                {:icon => :portclosed, :id => "port_close"}]);
            WatchUi.pushView(menu, new ChoiceDelegate(menu), WatchUi.SLIDE_LEFT);
        } else if (id.equals("limit_menu")) {
            // a stock menu like the climate set point, the car's current limit (or 80) in focus
            var s = link().state;
            var now = s != null && s["lim"] != null ? (s["lim"] as Numeric).toNumber() : 80;
            var menu = new WatchUi.Menu2({:title => "Limit"});
            // the range is the phone's MIN_LIMIT and MAX_LIMIT (Controller.kt), which clamps what is sent
            for (var p = 50; p <= 100; p += 5) {
                menu.addItem(new WatchUi.MenuItem(p + "%", null, p, {}));
            }
            var at = (now - 48) / 5; // the nearest step, rounding 52 down to 50 and 53 up to 55
            menu.setFocus(at < 0 ? 0 : at > 10 ? 10 : at);
            WatchUi.pushView(menu, new LimitMenuDelegate(), WatchUi.SLIDE_LEFT);
        } else {
            perform(id, 0, null);
        }
    }
}

// Choosing a limit sends it after a confirm; back closes only the menu.
class LimitMenuDelegate extends WatchUi.Menu2InputDelegate {
    function initialize() {
        Menu2InputDelegate.initialize();
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        perform("limit", 1, (item.getId() as Number).toFloat());
    }
}
