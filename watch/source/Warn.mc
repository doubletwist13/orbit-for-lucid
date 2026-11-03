import Toybox.Graphics;
import Toybox.Lang;

// The warning icons of the main screen's state row, drawn like the ones in Icons.mc but kept apart from them: Icons is
// glance code, and the glance has no use for these.
module Warn {
    const DOOR_BODY = [1.2, 18.4, 1.0, 14.8, 1.8, 12.6, 5.0, 12.0, 9.0, 8.4, 15.5, 8.0, 19.5, 10.8, 22.0, 12.0, 23.0,
        13.2, 23.0, 18.4] as Array<Float>;
    const DOOR_D = [11.6, 11.0, 11.6, 16.0, 11.6, 11.0, 14.0, 11.0, 15.6, 12.6, 15.6, 14.4, 14.0, 16.0, 11.6, 16.0] as
        Array<Float>;

    // a car from the side, like the frunk and trunk icons, with a D in the body
    function door(dc as Dc, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth((u * 1.2).toNumber() < 1 ? 1 : (u * 1.2).toNumber());
        Icons.poly(dc, x, y, u, DOOR_BODY);
        Icons.poly(dc, x, y, u, DOOR_D);
        dc.drawCircle(Icons.px(x, 6.0, u), Icons.px(y, 18.6, u), (3.2 * u).toNumber());
        dc.drawCircle(Icons.px(x, 18.0, u), Icons.px(y, 18.6, u), (3.2 * u).toNumber());
    }

    // a door window with an arrow up: close all the windows
    function windows(dc as Dc, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth(size / 16 < 2 ? 2 : size / 16);
        Icons.poly(dc, x, y, u, [3.0, 20.0, 3.0, 11.0, 9.0, 4.0, 21.0, 4.0, 21.0, 20.0, 3.0, 20.0] as Array<Float>);
        Icons.poly(dc, x, y, u, [8.5, 14.0, 13.0, 9.5, 17.5, 14.0] as Array<Float>);
        Icons.line(dc, x, y, u, 13.0, 10.0, 13.0, 18.0);
    }

    // a paw: pet mode
    function pet(dc as Dc, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth(size / 16 < 2 ? 2 : size / 16);
        dc.drawCircle(Icons.px(x, 12, u), Icons.px(y, 16, u), (4.5 * u).toNumber());
        var toes = [[5.5, 11], [9.5, 6.5], [14.5, 6.5], [18.5, 11]];
        for (var i = 0; i < toes.size(); i++) {
            dc.drawCircle(Icons.px(x, toes[i][0], u), Icons.px(y, toes[i][1], u), (2 * u).toNumber());
        }
    }

    // a person with an exclamation mark beside them: something may be left inside the car
    function occupant(dc as Dc, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth(size / 16 < 2 ? 2 : size / 16);
        dc.drawCircle(Icons.px(x, 9, u), Icons.px(y, 6.5, u), (3.4 * u).toNumber());
        Icons.poly(dc, x, y, u, [3.5, 21.0, 4.5, 13.5, 9.0, 11.5, 13.5, 13.5, 14.5, 21.0] as Array<Float>);
        Icons.line(dc, x, y, u, 20.0, 4.0, 20.0, 12.0);
        dc.fillCircle(Icons.px(x, 20, u), Icons.px(y, 17, u), (1.5 * u).toNumber() < 1 ? 1 : (1.5 * u).toNumber());
    }

    // the usual tyre pressure warning: a flat-bottomed tyre section with an exclamation mark
    function tire(dc as Dc, x as Number, y as Number, size as Number) as Void {
        var u = size / 24.0;
        dc.setPenWidth(size / 16 < 2 ? 2 : size / 16);
        dc.drawArc(Icons.px(x, 12, u), Icons.px(y, 11, u), (9 * u).toNumber(), Graphics.ARC_CLOCKWISE, 220, 320);
        Icons.line(dc, x, y, u, 6.5, 17.5, 8.5, 21);
        Icons.line(dc, x, y, u, 17.5, 17.5, 15.5, 21);
        Icons.line(dc, x, y, u, 8.5, 21, 15.5, 21);
        Icons.line(dc, x, y, u, 12, 6.5, 12, 13);
        dc.fillCircle(Icons.px(x, 12, u), Icons.px(y, 16.9, u), (1.4 * u).toNumber() < 1 ? 1 : (1.4 * u).toNumber());
    }
}
