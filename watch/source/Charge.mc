import Toybox.Lang;

// The colour of the charge by level, shared by the main screen and the glance (which sees only (:glance) code, so the
// values are literals rather than MainView's consts).
(:glance)
module Charge {
    // the accent from 50, yellow from 25, orange from 10, red below; no reading keeps the accent
    function color(soc as Number?) as Number {
        if (soc == null || soc >= 50) { return Theme.accent(); }
        return soc >= 25 ? 0xFFDD00 : soc >= 10 ? 0xFF7700 : 0xFF3333;
    }

    // The car is giving power out (V2X). The watch only names it: the field that carries the power then is not known.
    function discharging(charge as String?) as Boolean {
        return "DISCHARGING".equals(charge) || "PREPARING_DISCHARGE".equals(charge);
    }

    // the plug icon for a charge state: slashed when unplugged (or unknown), a bolt in place of the plug while
    // charging, an outlined one while discharging
    function plug(charge as String?) as Symbol {
        if (charge == null || charge.equals("NOT_CONNECTED") || charge.equals("UNKNOWN")) { return :plugoff; }
        if (discharging(charge)) { return :discharge; }
        return charge.equals("CHARGING") ? :bolt : :plug;
    }
}
