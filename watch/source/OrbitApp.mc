import Toybox.Application;
import Toybox.Background;
import Toybox.Lang;
import Toybox.System;
import Toybox.WatchUi;

(:glance :background)
class OrbitApp extends Application.AppBase {
    // created in getInitialView, not here or in onStart: the glance process runs both of those but has no Link code
    var link as Link? = null;

    function initialize() {
        AppBase.initialize();
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        var l = new Link();
        link = l;
        l.send("status", null);
        // the phone pushes status every few minutes; this wakes the background service when one arrives while the
        // app is closed
        Background.registerForPhoneAppMessageEvent();
        return [new MainView(), new MainDelegate()];
    }

    (:background)
    function getServiceDelegate() as [System.ServiceDelegate] {
        return [new PushDelegate()];
    }

    (:glance)
    function getGlanceView() as [GlanceView] or [GlanceView, GlanceViewDelegate] or Null {
        return [new OrbitGlance()];
    }
}

function link() as Link {
    return (Application.getApp() as OrbitApp).link as Link;
}
