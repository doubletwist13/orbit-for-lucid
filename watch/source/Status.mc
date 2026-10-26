import Toybox.Lang;
import Toybox.Time;

// How a status message from the phone becomes the saved state. Shared by the app and its background service, which
// runs with very little memory and sees only (:background) code.
(:background)
module Status {
    const KEYS = ["soc", "locked", "charge", "hvac", "tset", "tin", "tout", "awake"];
    // fields the phone leaves out when the car reports none, which must not linger from an earlier reply
    const OPTIONAL = ["tset", "tin", "tout"];

    // The old state with the message's status fields on top, or null if the message carries none (a pending-only
    // reply). Merged, not replaced, because replies carry only what changed in the car's report.
    function merge(old as Dictionary?, d as Dictionary) as Dictionary? {
        var st = old != null ? old : {};
        var fresh = false;
        for (var i = 0; i < KEYS.size(); i++) {
            if (d[KEYS[i]] != null) {
                st[KEYS[i]] = d[KEYS[i]];
                fresh = true;
            }
        }
        if (!fresh) { return null; }
        for (var i = 0; i < OPTIONAL.size(); i++) {
            if (d[OPTIONAL[i]] == null) { st.remove(OPTIONAL[i]); }
        }
        // age is how old the car's data already was when the phone sent it
        st["ts"] = Time.now().value() - (d["age"] != null ? d["age"] as Number : 0);
        return st;
    }
}
