import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.System;

// Wakes when the phone pushes a status (docs/protocol.md, "Pushed status") while the app is not running, and saves it
// where the glance and the app read it. It must stay tiny: background services get a small memory pool.
(:background)
class PushDelegate extends System.ServiceDelegate {
    function initialize() {
        ServiceDelegate.initialize();
    }

    function onPhoneAppMessage(msg as Communications.PhoneAppMessage) as Void {
        var d = msg.data as Dictionary;
        if (d["v"] == 1 && d["push"] == true) {
            var st = Status.merge(Application.Storage.getValue("s") as Dictionary?, d);
            if (st != null) { Application.Storage.setValue("s", st); }
        }
        Background.exit(null);
    }
}
