import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;

(:glance)
class OrbitApp extends Application.AppBase {
    // created in getInitialView, not here or in onStart: the glance process runs both of those but has no Link code
    var link as Link? = null;

    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        var l = new Link();
        link = l;
        l.send("status");
        return [new MainView(), new MainDelegate()];
    }

    (:glance)
    function getGlanceView() as [GlanceView] or [GlanceView, GlanceViewDelegate] or Null {
        return [new OrbitGlance()];
    }
}

function link() as Link {
    return (Application.getApp() as OrbitApp).link as Link;
}
