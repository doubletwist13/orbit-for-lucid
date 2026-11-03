import Toybox.Graphics;
import Toybox.Lang;
import Toybox.WatchUi;

// The app's pages in one vertical strip, docs/watch-design.md: info above, the main screen (where it opens), then the
// actions pages. Swipe or the up/down buttons move along it and it stops at both ends. Not a WatchUi.ViewLoop: that
// draws its own page indicator, which cannot be turned off, and the stock screens show only dots on the left.
const ABOUT_PAGE = 0;
const INFO_PAGE = 1;
const MAIN_PAGE = 2;
const STRIP_PAGES = 5;

// The view and delegate for a page of the strip
function stripView(page as Number) as [WatchUi.View, WatchUi.BehaviorDelegate] {
    if (page == ABOUT_PAGE) {
        return [new AboutView(), new PageDelegate(ABOUT_PAGE)];
    } else if (page == INFO_PAGE) {
        return [new InfoView(), new PageDelegate(INFO_PAGE)];
    } else if (page == MAIN_PAGE) {
        return [new MainView(), new MainDelegate()];
    }
    var s = link().state;
    var menu = new IconMenu(page == MAIN_PAGE + 1 ? [
        {:icon => s != null && s["locked"] == false ? :unlock : :lock, :id => "lock_menu"},
        {:icon => s != null && s["hvac"] == false ? :hvacoff : :hvac, :id => "climate_menu"},
        {:icon => lidIcon("frunk", s != null && s["frunk"] == true), :id => "frunk_menu"},
        {:icon => lidIcon("trunk", s != null && s["trunk"] == true), :id => "trunk_menu"}] : [
        {:icon => :horn, :id => "horn"},
        {:icon => :lights, :id => "flash"},
        {:icon => :refresh, :id => "status", :small => true}]);
    // set before the delegate is made, which reads it
    menu.stripIndex = page;
    return [menu, new ActionsDelegate(menu)];
}

// Swipe up or the down button goes to the next page, swipe down or the up button to the previous one. A page of -1 is a
// screen pushed over the strip, which has no neighbours.
class PageDelegate extends WatchUi.BehaviorDelegate {
    private var _page as Number;

    function initialize(page as Number) {
        BehaviorDelegate.initialize();
        _page = page;
    }

    function onNextPage() as Boolean {
        return turn(1);
    }

    function onPreviousPage() as Boolean {
        return turn(-1);
    }

    private function turn(by as Number) as Boolean {
        var p = _page + by;
        if (_page < 0 || p < 0 || p >= STRIP_PAGES) { return false; }
        var v = stripView(p);
        WatchUi.switchToView(v[0], v[1], by > 0 ? WatchUi.SLIDE_UP : WatchUi.SLIDE_DOWN);
        return true;
    }
}

// One dot per page down the left edge, the current one filled, where the stock Garmin screens put theirs
function pageDots(dc as Dc, page as Number) as Void {
    var gap = (dc.getHeight() * 0.05).toNumber();
    var x = (dc.getWidth() * 0.07).toNumber();
    var y0 = dc.getHeight() / 2 - (STRIP_PAGES - 1) * gap / 2;
    dc.setColor(DIM, Graphics.COLOR_BLACK);
    dc.setPenWidth(2);
    for (var p = 0; p < STRIP_PAGES; p++) {
        if (p == page) { dc.fillCircle(x, y0 + p * gap, gap / 4); } else { dc.drawCircle(x, y0 + p * gap, gap / 4); }
    }
}
