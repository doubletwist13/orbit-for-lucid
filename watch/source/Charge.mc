import Toybox.Lang;

// The colour of the charge by level, shared by the main screen and the glance (which sees only (:glance) code, so the
// values are literals rather than MainView's consts).
(:glance)
module Charge {
    // green from 50, yellow from 25, orange from 10, red below; no reading keeps the normal green
    function color(soc as Number?) as Number {
        if (soc == null || soc >= 50) { return 0x00FF66; }
        return soc >= 25 ? 0xFFDD00 : soc >= 10 ? 0xFF7700 : 0xFF3333;
    }
}
