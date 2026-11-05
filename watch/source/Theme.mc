import Toybox.Application;
import Toybox.Lang;

// The accent colour: one colour for the icons, text and page dots, picked on the watch (Accent.mc) and kept in
// Storage. The charge follows it only while it is high; its low colours and the amber and red warnings are not themed,
// so a warning never looks like decoration. The glance reads it too, so this is (:glance) code and the values are
// literals.
(:glance)
module Theme {
    // readable on black and clear of the yellow, orange, amber and red the warnings use; green is the default
    const NAMES = ["Green", "Mint", "Teal", "Cyan", "Sky", "Blue", "Violet", "Purple", "Pink", "White", "Silver"];
    const COLORS = [0x00FF66, 0x66FFCC, 0x22DDAA, 0x00DDFF, 0x66AAFF, 0x5577FF, 0xAA77FF, 0xCC66FF, 0xFF66CC, 0xFFFFFF,
        0xBBBBBB];

    // an unset or stale value (a list that later got shorter) falls back to the default
    function index() as Number {
        var i = Application.Storage.getValue("accent");
        return i instanceof Number && i >= 0 && i < COLORS.size() ? i : 0;
    }

    function set(i as Number) as Void {
        Application.Storage.setValue("accent", i);
    }

    function accent() as Number {
        return COLORS[index()];
    }

    // The other shades are each channel of the accent divided down and lifted by a base, which gives the original
    // greens (0x338855, 0x0B3D22, 0x12331F, 0x12251A) from 0x00FF66 and a matching one for any other accent.
    function dim() as Number {
        return shade(accent(), 3, 51);
    }

    // the inside of an icon that is switched on
    function fill() as Number {
        return shade(accent(), 5, 11);
    }

    // the unfilled part of the charge ring, and the same for old data
    function track() as Number {
        return shade(accent(), 8, 18);
    }

    function trackDim() as Number {
        return shade(accent(), 13, 18);
    }

    function shade(c as Number, div as Number, base as Number) as Number {
        return ((((c >> 16) & 0xFF) / div + base) << 16) | ((((c >> 8) & 0xFF) / div + base) << 8)
            | ((c & 0xFF) / div + base);
    }
}
