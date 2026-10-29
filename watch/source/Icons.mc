import Toybox.Graphics;
import Toybox.Lang;

// Original line icons drawn with Dc primitives so one set takes any colour and size. Each is designed on a 24-unit
// grid; (x, y) is the top-left corner and size the side in pixels. The caller sets the colour. The glance uses them too,
// so the module is (:glance) code.
(:glance)
module Icons {
    function draw(dc as Dc, id as Symbol, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth(size / 12 < 2 ? 2 : size / 12);
        if (id == :lock || id == :unlock) {
            lock(dc, x, y, u, id == :lock);
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

    function lock(dc as Dc, x as Number, y as Number, u as Float, locked as Boolean) as Void {
        dc.drawRoundedRectangle(px(x, 5, u), px(y, 11, u), (14 * u).toNumber(), (10 * u).toNumber(), (2 * u).toNumber());
        var r = (4 * u).toNumber();
        line(dc, x, y, u, 8, 8, 8, 11);
        if (locked) {
            dc.drawArc(px(x, 12, u), px(y, 8, u), r, Graphics.ARC_CLOCKWISE, 180, 0);
            line(dc, x, y, u, 16, 8, 16, 11);
        } else {
            dc.drawArc(px(x, 12, u), px(y, 8, u), r, Graphics.ARC_CLOCKWISE, 180, 30);
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

    function battery(dc as Dc, x as Number, y as Number, u as Float) as Void {
        dc.drawRoundedRectangle(px(x, 1.5, u), px(y, 6.5, u), (18 * u).toNumber(), (11 * u).toNumber(),
            (2.5 * u).toNumber());
        line(dc, x, y, u, 22, 10.5, 22, 13.5);
    }
}
