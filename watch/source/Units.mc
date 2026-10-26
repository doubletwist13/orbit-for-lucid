import Toybox.Lang;
import Toybox.Math;
import Toybox.System;
import Toybox.Test;

// The phone talks Celsius only (docs/protocol.md); the watch shows and steps in whatever the watch is set to.
module Units {
    const MIN_C = 15.0f; // the car's range, the same clamp the phone applies
    const MAX_C = 30.0f;

    function fahrenheit() as Boolean {
        return System.getDeviceSettings().temperatureUnits == System.UNIT_STATUTE;
    }

    // The picker's list: 59..86 F in whole degrees, or 15.0..30.0 C in halves. Values are Celsius either way.
    function count(f as Boolean) as Number {
        return f ? 28 : 31;
    }

    function at(index as Number, f as Boolean) as Float {
        return f ? clamp((59 + index - 32) / 1.8f) : MIN_C + index * 0.5f;
    }

    // the list position nearest a Celsius value
    function indexOf(c as Float, f as Boolean) as Number {
        var i = (f ? Math.round(c * 1.8f + 32) - 59 : Math.round((c - MIN_C) * 2)).toNumber();
        return i < 0 ? 0 : i >= count(f) ? count(f) - 1 : i;
    }

    // the number as shown, without the degree sign (the number fonts have no glyph for it)
    function digits(c as Float, f as Boolean) as String {
        return f ? Math.round(c * 1.8f + 32).toNumber().toString() : (Math.round(c * 2) / 2.0f).format("%.1f");
    }

    // a reading in whole degrees, for places where half degrees are noise
    function whole(c as Float, f as Boolean) as String {
        return Math.round(f ? c * 1.8f + 32 : c).toNumber().toString();
    }

    function label(c as Float, f as Boolean) as String {
        return digits(c, f) + "°";
    }

    function clamp(c as Float) as Float {
        return c < MIN_C ? MIN_C : c > MAX_C ? MAX_C : c;
    }

    (:test)
    function listCoversTheCarsRangeAndRoundTrips(logger as Test.Logger) as Boolean {
        var ok = true;
        // the ends of both lists are the car's 15..30 C (59..86 F)
        ok = ok && at(0, true) == MIN_C && at(count(true) - 1, true) == MAX_C;
        ok = ok && at(0, false) == MIN_C && at(count(false) - 1, false) == MAX_C;
        // the car reads 22.972 C back for a 73 F setting, and 22.5 C is a half degree on a Celsius watch
        ok = ok && label(at(indexOf(22.972f, true), true), true).equals("73°");
        ok = ok && label(22.5f, false).equals("22.5°");
        ok = ok && indexOf(22.5f, false) == 15;
        // air temperatures round to whole degrees, below freezing included
        ok = ok && whole(21.4f, false).equals("21") && whole(21.4f, true).equals("71");
        ok = ok && whole(-2.6f, false).equals("-3") && whole(0.0f, true).equals("32");
        // a value outside the range lands on an end of the list
        ok = ok && indexOf(5.0f, true) == 0 && indexOf(99.0f, true) == count(true) - 1;
        // every list entry maps back to its own position
        for (var i = 0; i < count(true); i++) { ok = ok && indexOf(at(i, true), true) == i; }
        for (var i = 0; i < count(false); i++) { ok = ok && indexOf(at(i, false), false) == i; }
        if (!ok) { logger.error("unit conversion off"); }
        return ok;
    }
}
