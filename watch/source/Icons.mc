import Toybox.Graphics;
import Toybox.Lang;

// Original line icons drawn with Dc primitives so one set takes any colour and size. Each is designed on a 24-unit
// grid; (x, y) is the top-left corner and size the side in pixels. The caller sets the colour. The glance uses them
// too, so the module is (:glance) code.
(:glance)
module Icons {
    function draw(dc as Dc, id as Symbol, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth(size / 12 < 2 ? 2 : size / 12);
        if (id == :lock || id == :unlock || id == :lockunknown) {
            lock(dc, x, y, u, id != :unlock, id == :lockunknown);
        } else if (id == :plug) {
            plug(dc, x, y, u);
        } else if (id == :plugoff) {
            plug(dc, x, y, u);
            line(dc, x, y, u, 3, 3, 21, 21);
        } else if (id == :bolt) {
            bolt(dc, x, y, u);
        } else if (id == :hvac) {
            hvac(dc, x, y, u);
        } else if (id == :hvacoff) {
            hvac(dc, x, y, u);
            line(dc, x, y, u, 3, 3, 21, 21);
        } else if (id == :refresh) {
            refresh(dc, x, y, u);
        } else if (id == :battery) {
            battery(dc, x, y, u);
        } else if (id == :info) {
            info(dc, x, y, u);
        } else if (id == :frunk || id == :frunkopen || id == :trunk || id == :trunkopen) {
            lid(dc, x, y, u, id == :frunk || id == :frunkopen, id == :frunkopen || id == :trunkopen, false);
        } else if (id == :afrunk || id == :afrunkopen || id == :atrunk || id == :atrunkopen) {
            lid(dc, x, y, u, id == :afrunk || id == :afrunkopen, id == :afrunkopen || id == :atrunkopen, true);
        } else if (id == :horn) {
            horn(dc, x, y, u);
        } else if (id == :lights) {
            lights(dc, x, y, u);
        }
    }

    // grid point to pixel
    function px(o as Number, v as Numeric, u as Float) as Number {
        return o + (v * u).toNumber();
    }

    function line(dc as Dc, x as Number, y as Number, u as Float, x1 as Numeric, y1 as Numeric, x2 as Numeric,
            y2 as Numeric) as Void {
        dc.drawLine(px(x, x1, u), px(y, y1, u), px(x, x2, u), px(y, y2, u));
    }

    // unknown is a closed padlock with a question mark in the body; the caller colours it so it is not read as locked
    function lock(dc as Dc, x as Number, y as Number, u as Float, locked as Boolean, unknown as Boolean) as Void {
        dc.drawRoundedRectangle(px(x, 5, u), px(y, 11, u), (14 * u).toNumber(), (10 * u).toNumber(),
            (2 * u).toNumber());
        if (unknown) {
            dc.drawArc(px(x, 12, u), px(y, 14.3, u), (2 * u).toNumber(), Graphics.ARC_CLOCKWISE, 180, 270);
            line(dc, x, y, u, 12, 16.3, 12, 17.6);
            dc.fillCircle(px(x, 12, u), px(y, 19.4, u), (0.7 * u).toNumber() < 1 ? 1 : (0.7 * u).toNumber());
        }
        var r = (4 * u).toNumber();
        if (locked) {
            line(dc, x, y, u, 8, 8, 8, 11);
            dc.drawArc(px(x, 12, u), px(y, 8, u), r, Graphics.ARC_CLOCKWISE, 180, 0);
            line(dc, x, y, u, 16, 8, 16, 11);
        } else {
            // open: the whole shackle is lifted, joined only on the left, so the gap above the body shows at a glance
            line(dc, x, y, u, 8, 4.5, 8, 11);
            dc.drawArc(px(x, 12, u), px(y, 4.5, u), r, Graphics.ARC_CLOCKWISE, 180, 0);
        }
    }

    function plug(dc as Dc, x as Number, y as Number, u as Float) as Void {
        line(dc, x, y, u, 9, 2, 9, 7);
        line(dc, x, y, u, 15, 2, 15, 7);
        line(dc, x, y, u, 6, 7, 18, 7);
        line(dc, x, y, u, 6, 7, 6, 11);
        line(dc, x, y, u, 18, 7, 18, 11);
        dc.drawArc(px(x, 12, u), px(y, 11, u), (6 * u).toNumber(), Graphics.ARC_COUNTER_CLOCKWISE, 180, 360);
        line(dc, x, y, u, 12, 17, 12, 22);
    }

    function bolt(dc as Dc, x as Number, y as Number, u as Float) as Void {
        var g = [[13, 2], [5, 14], [11, 14], [10, 22], [18, 10], [12, 10]];
        var pts = [] as Array<[Number, Number]>;
        for (var i = 0; i < g.size(); i++) {
            pts.add([px(x, g[i][0], u), px(y, g[i][1], u)]);
        }
        dc.fillPolygon(pts);
    }

    // three wavy lines, drawn as zigzags
    function hvac(dc as Dc, x as Number, y as Number, u as Float) as Void {
        for (var cx = 7; cx <= 17; cx += 5) {
            for (var yy = 4; yy < 20; yy += 4) {
                var dir = ((yy - 4) / 4) % 2 == 0 ? 1 : -1;
                line(dc, x, y, u, cx + dir, yy, cx - dir, yy + 4);
            }
        }
    }

    // an almost full circle ending in an arrowhead
    function refresh(dc as Dc, x as Number, y as Number, u as Float) as Void {
        dc.drawArc(px(x, 12, u), px(y, 12, u), (8 * u).toNumber(), Graphics.ARC_CLOCKWISE, 0, 46);
        line(dc, x, y, u, 20, 3, 20, 8);
        line(dc, x, y, u, 20, 8, 15, 8);
    }

    // a lowercase "i": a dot over a stem
    function info(dc as Dc, x as Number, y as Number, u as Float) as Void {
        dc.fillCircle(px(x, 12, u), px(y, 6, u), (1.8 * u).toNumber());
        line(dc, x, y, u, 12, 10, 12, 19);
    }

    // The front and back of the car in line art, as x, y pairs on the grid: the nose and the tail, the window line, the
    // ground line and a letter (F or T) in the body. The shapes follow the icons of the WearOS companion app (see
    // NOTICE), traced and simplified; the wheel is a circle (WHEEL).
    const FRUNK = [
        [23.0, 4.2, 18.2, 5.4, 11.3, 8.9, 2.8, 10.1, 1.4, 11.5, 1.0, 15.3, 1.4, 17.1, 1.0, 17.8, 4.6, 18.6],
        [22.8, 5.0, 18.6, 6.4, 14.7, 8.7, 22.8, 8.7], [14.9, 18.8, 23.0, 18.8],
        [16.5, 16.0, 16.5, 11.0, 20.5, 11.0], [16.5, 13.4, 19.5, 13.4]] as Array<Array<Float> >;
    const TRUNK = [
        [15.9, 18.9, 19.9, 18.1, 21.6, 16.5, 21.6, 13.6, 21.0, 12.6, 21.4, 8.9, 19.1, 5.9, 20.4, 4.7, 13.2, 3.2,
            1.2, 2.4],
        [1.2, 7.9, 14.9, 7.7, 12.6, 5.9, 8.5, 4.1, 1.2, 3.4], [1.0, 19.3, 4.9, 19.3],
        [16.0, 11.0, 20.5, 11.0], [18.2, 11.0, 18.2, 16.0]] as Array<Array<Float> >;
    // an arrow over the lid, shown while it is open
    const FRUNK_ARROW = [[5.4, 8.9, 5.7, 6.4, 6.6, 4.4, 8.0, 3.0], [5.4, 3.5, 8.0, 3.0, 7.6, 5.6]] as
        Array<Array<Float> >;
    const TRUNK_ARROW = [[22.8, 8.0, 23.0, 5.2, 22.5, 3.4, 21.6, 2.0], [21.5, 4.6, 21.6, 2.0, 23.4, 2.8]] as
        Array<Array<Float> >;
    // The same for the Air, which is lower and sleeker than the Gravity: drawn by hand to match its proportions.
    const AIR_FRUNK = [
        [1.2, 17.6, 1.0, 14.8, 1.8, 12.6, 5.0, 12.0, 12.0, 10.8, 16.0, 10.0, 23.0, 5.2], [14.9, 18.8, 23.0, 18.8],
        [16.5, 16.5, 16.5, 12.5, 20.5, 12.5], [16.5, 14.5, 19.5, 14.5]] as Array<Array<Float> >;
    const AIR_TRUNK = [
        [1.2, 5.6, 8.0, 6.2, 12.5, 6.8, 17.2, 10.0, 21.8, 10.6, 22.8, 11.4, 22.8, 14.5, 21.8, 17.0, 19.9, 18.2,
            15.9, 18.9],
        [1.0, 19.3, 4.9, 19.3], [15.5, 12.4, 20.0, 12.4], [17.7, 12.4, 17.7, 16.8]] as Array<Array<Float> >;
    const AIR_FRUNK_ARROW = [[5.0, 9.0, 5.3, 6.4, 6.4, 4.4, 7.8, 3.0], [5.2, 3.5, 7.8, 3.0, 7.4, 5.5]] as
        Array<Array<Float> >;
    const AIR_TRUNK_ARROW = [[20.0, 9.6, 20.6, 7.0, 20.2, 4.6, 19.0, 3.2], [18.8, 5.4, 19.0, 3.2, 21.2, 3.8]] as
        Array<Array<Float> >;
    // wheel centre and radius: Gravity frunk, Gravity trunk, Air frunk, Air trunk
    const WHEEL = [[9.8, 17.1, 4.0], [10.4, 17.5, 4.4], [9.4, 17.4, 3.6], [10.4, 17.4, 3.8]] as Array<Array<Float> >;

    // the frunk (front) or trunk (back) end of the car; the arrow says the lid is open
    function lid(dc as Dc, x as Number, y as Number, u as Float, front as Boolean, open as Boolean, air as Boolean)
            as Void {
        var pen = (u * 1.2).toNumber();
        dc.setPenWidth(pen < 1 ? 1 : pen);
        var which = (air ? 2 : 0) + (front ? 0 : 1);
        var parts = [FRUNK, TRUNK, AIR_FRUNK, AIR_TRUNK][which];
        for (var i = 0; i < parts.size(); i++) {
            poly(dc, x, y, u, parts[i]);
        }
        var w = WHEEL[which];
        dc.drawCircle(px(x, w[0], u), px(y, w[1], u), (w[2] * u).toNumber());
        if (open) {
            var arrow = [FRUNK_ARROW, TRUNK_ARROW, AIR_FRUNK_ARROW, AIR_TRUNK_ARROW][which];
            for (var i = 0; i < arrow.size(); i++) {
                poly(dc, x, y, u, arrow[i]);
            }
        }
    }

    function poly(dc as Dc, x as Number, y as Number, u as Float, p as Array<Float>) as Void {
        for (var i = 0; i + 3 < p.size(); i += 2) {
            line(dc, x, y, u, p[i], p[i + 1], p[i + 2], p[i + 3]);
        }
    }

    // a speaker with two sound waves
    function horn(dc as Dc, x as Number, y as Number, u as Float) as Void {
        line(dc, x, y, u, 3, 9, 7, 9);
        line(dc, x, y, u, 3, 15, 7, 15);
        line(dc, x, y, u, 3, 9, 3, 15);
        line(dc, x, y, u, 7, 9, 12, 4);
        line(dc, x, y, u, 7, 15, 12, 20);
        line(dc, x, y, u, 12, 4, 12, 20);
        dc.drawArc(px(x, 12, u), px(y, 12, u), (5 * u).toNumber(), Graphics.ARC_COUNTER_CLOCKWISE, 320, 40);
        dc.drawArc(px(x, 12, u), px(y, 12, u), (9 * u).toNumber(), Graphics.ARC_COUNTER_CLOCKWISE, 320, 40);
    }

    // a headlight with three rays
    function lights(dc as Dc, x as Number, y as Number, u as Float) as Void {
        dc.drawArc(px(x, 10, u), px(y, 12, u), (6 * u).toNumber(), Graphics.ARC_COUNTER_CLOCKWISE, 90, 270);
        line(dc, x, y, u, 10, 6, 10, 18);
        line(dc, x, y, u, 14, 7, 21, 5);
        line(dc, x, y, u, 14, 12, 22, 12);
        line(dc, x, y, u, 14, 17, 21, 19);
    }

    // upright, so it uses only the middle half of its cell: a caller short of width can crop to that
    function battery(dc as Dc, x as Number, y as Number, u as Float) as Void {
        dc.drawRoundedRectangle(px(x, 6.5, u), px(y, 4.5, u), (11 * u).toNumber(), (18 * u).toNumber(),
            (2.5 * u).toNumber());
        line(dc, x, y, u, 10.5, 2, 13.5, 2);
    }
}
