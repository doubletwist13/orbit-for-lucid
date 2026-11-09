import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The AC current limit picker: a few common values and "Other...", which opens a picker over the car's whole range.
// The range is the phone's MIN_AMPS and MAX_AMPS (Controller.kt), which clamps what is sent. A plugged-in car caps the
// limit at the charger's maximum, so a high pick may not be taken.
const AC_COMMON = [10, 16, 24, 32, 40, 48, 80];
const AC_MIN = 10;
const AC_MAX = 80;

// The last page of the strip: start or stop charging, the charge port, the charge limit, battery preconditioning and
// the AC current limit. The bolt and the battery heater are one icon each that follows the car (filled while on, like
// pet mode), so the choice never depends on a stale page.
function chargeItems() as Array<Dictionary> {
    var s = link().state;
    var charging = s != null && "CHARGING".equals(s["charge"]);
    var kw = charging && s["rate"] != null ? (s["rate"] as Numeric).toNumber() + " kW" : null;
    var lim = s != null && s["lim"] != null ? (s["lim"] as Numeric).toNumber() + "%" : null;
    // giving power out (V2X): nothing to start or stop from here, so the icon only says so
    var out = s != null && Charge.discharging(s["charge"] as String?);
    var power = out ? {:icon => :discharge, :id => "discharging", :label => "Discharging"}
        : {:icon => :bolt, :id => charging ? "charge_stop" : "charge_start", :on => charging, :label => kw};
    // the battery heater: filled while on, with the minutes left under it
    var heating = s != null && s["bp"] == true;
    var left = heating && s["bpm"] != null ? (s["bpm"] as Numeric).toNumber() + " min" : null;
    var amps = s != null && s["ac"] != null ? (s["ac"] as Numeric).toNumber() + " A" : null;
    // The bolt in the middle, as the lock is on the Access page, then clockwise from the top. The text under an icon
    // would run into the middle one above it, so the icons that always have text (the AC limit and the charge limit)
    // are at the sides, and the port, which has none, is on top. Preconditioning has text only while it is on.
    return [power,
        {:icon => s != null && s["port"] == true ? :portopen : :portclosed, :id => "port_menu"},
        {:icon => :acamps, :id => "ac_menu", :label => amps},
        {:icon => :precon, :id => heating ? "precon_off" : "precon_on", :on => heating, :label => left},
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
        } else if (id.equals("ac_menu")) {
            // the common values, with the car's own in focus, or "Other..." when it is none of them
            var s = link().state;
            var now = s != null && s["ac"] != null ? (s["ac"] as Numeric).toNumber() : 0;
            var menu = new WatchUi.Menu2({:title => "AC limit"});
            var focus = AC_COMMON.size();
            for (var i = 0; i < AC_COMMON.size(); i++) {
                menu.addItem(new WatchUi.MenuItem(AC_COMMON[i] + " A", null, AC_COMMON[i], {}));
                if (AC_COMMON[i] == now) { focus = i; }
            }
            menu.addItem(new WatchUi.MenuItem("Other...", null, 0, {}));
            menu.setFocus(focus);
            WatchUi.pushView(menu, new AcMenuDelegate(now), WatchUi.SLIDE_LEFT);
        } else {
            perform(id, 0, null);
        }
    }
}

// A common value sends the AC limit after a confirm; "Other..." (id 0) opens the picker over the whole range.
class AcMenuDelegate extends WatchUi.Menu2InputDelegate {
    private var _now as Number;

    function initialize(now as Number) {
        Menu2InputDelegate.initialize();
        _now = now;
    }

    function onSelect(item as WatchUi.MenuItem) as Void {
        var amps = item.getId() as Number;
        if (amps > 0) {
            perform("ac_limit", 1, amps.toFloat());
            return;
        }
        // start on the car's value, or a typical home charger's 32 A
        var start = _now >= AC_MIN && _now <= AC_MAX ? _now - AC_MIN : 32 - AC_MIN;
        var title = new WatchUi.Text({:text => "AC amps", :color => Theme.accent(),
            :locX => WatchUi.LAYOUT_HALIGN_CENTER, :locY => WatchUi.LAYOUT_VALIGN_BOTTOM});
        var picker = new WatchUi.Picker({:title => title, :pattern => [new AmpFactory()], :defaults => [start]});
        WatchUi.pushView(picker, new AmpPickerDelegate(), WatchUi.SLIDE_LEFT);
    }
}

// The numbers AC_MIN to AC_MAX, one per step of the picker.
class AmpFactory extends WatchUi.PickerFactory {
    function initialize() {
        PickerFactory.initialize();
    }

    function getSize() as Number {
        return AC_MAX - AC_MIN + 1;
    }

    function getValue(index as Number) as Object? {
        return AC_MIN + index;
    }

    function getDrawable(index as Number, selected as Boolean) as WatchUi.Drawable? {
        return new WatchUi.Text({:text => (AC_MIN + index).toString(), :color => Theme.accent(),
            :font => Graphics.FONT_NUMBER_MEDIUM, :locX => WatchUi.LAYOUT_HALIGN_CENTER,
            :locY => WatchUi.LAYOUT_VALIGN_CENTER});
    }
}

// Accepting sends the AC limit after a confirm, closing the picker and the menu under it; back closes only the picker.
class AmpPickerDelegate extends WatchUi.PickerDelegate {
    function initialize() {
        PickerDelegate.initialize();
    }

    function onAccept(values as Array) as Boolean {
        perform("ac_limit", 2, (values[0] as Number).toFloat());
        return true;
    }

    function onCancel() as Boolean {
        WatchUi.popView(WatchUi.SLIDE_RIGHT);
        return true;
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
