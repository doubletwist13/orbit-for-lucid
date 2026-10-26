import Toybox.Application;
import Toybox.Background;
import Toybox.Communications;
import Toybox.Lang;
import Toybox.Notifications;
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
            var old = Application.Storage.getValue("s") as Dictionary?;
            // read before merging: merge updates the old dictionary in place
            var was = old != null ? old["charge"] : null;
            var st = Status.merge(old, d);
            if (st != null) {
                Application.Storage.setValue("s", st);
                // only on a change from a state we knew, so the first push after an install stays quiet
                if (was != null && !"CHARGING".equals(was) && "CHARGING".equals(st["charge"])) {
                    Notifications.showNotification("Charging started", st["soc"] + "%", null);
                }
            }
        }
        Background.exit(null);
    }
}
