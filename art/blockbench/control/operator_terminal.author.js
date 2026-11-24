// Operator terminal of GDD 16 (R58 §16.3.4): a non-programmable operator workstation, hand-authored. A floor desk
// console in grey enamel with a putty-plastic CRT monitor on a swivel plinth, a membrane keypad and a trackball on the
// desk top and one status lamp on the monitor bezel; the cable goes out at the back. Real scale (16 units = 1 m):
// desk 0.94 x 0.75 m high, 14-inch monitor, top of the case at 1.2 m.
//   operator_terminal        unpowered: dark glass, lamp off
//   operator_terminal_on     green-phosphor screen (glow_), green lamp
//   operator_terminal_alarm  screen lit, red lamp lit (blink by alternating _on and _alarm)
// Plus the panel board textures/gui/panel/operator_terminal.png (324 x 230), a VDU in a putty bezel.
// Run headless: node art/tools/pcart_headless.js <repo> control/operator_terminal.author.js OPT --paint
// Front to the north (the operator stands on the north side), no moving parts.
(function () {
  const { rng, img, put, add, mix, rect, grain } = PCART;
  const { P, lamp } = FUTK;
  const D = 'opterm/', T = {};

  T.case = FUTK.enamel([198, 190, 168], 62101, 0);           // putty ABS of a 1980s VDU
  T.desk = FUTK.enamel([116, 124, 130], 62102, 1);
  T.vent = () => { const im = img(16, 16); grain(im, [104, 110, 116], 62103, 2, 2, 8); for (let y = 2; y < 16; y += 3) rect(im, 2, y, 12, 1, [36, 38, 42]); return im; };
  // Membrane keypad: grey field, a grid of raised keys, a red and a green function row.
  T.keypad = () => {
    const im = img(16, 16); grain(im, [70, 72, 76], 62111, 2, 2, 8);
    for (let j = 0; j < 4; j++) for (let i = 0; i < 6; i++) {
      const c = j === 0 ? (i < 3 ? [180, 60, 50] : [70, 150, 80]) : [196, 196, 188];
      rect(im, 1 + i * 2.5, 2 + j * 3.4, 2, 2.4, c); rect(im, 1 + i * 2.5, 4 + j * 3.4, 2, 0.6, add(c, -60));
    }
    return im;
  };
  // Screens: rounded corners darker, the dead glass with a reflection; lit, green text lines on scanlines.
  const glass = (fn) => () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const cx = Math.abs(x + 0.5 - 16) / 16, cy = Math.abs(y + 0.5 - 16) / 16, v = Math.pow(cx, 4) + Math.pow(cy, 4);
      put(im, x, y, fn(x, y, v));
    }
    return im;
  };
  T.screen_off = glass((x, y, v) => add(mix([34, 40, 38], [12, 14, 14], Math.min(1, v * 1.4)), x + y < 14 && x + y > 8 ? 18 : 0));
  T.screen_on = (() => {
    const r = rng(62121), rows = [];
    for (let k = 0; k < 9; k++) rows.push([3 + Math.floor(r() * 3), 10 + Math.floor(r() * 16)]);
    return glass((x, y, v) => {
      let c = y % 2 ? [10, 40, 18] : [14, 52, 24];
      const row = Math.floor((y - 3) / 3), inRow = (y - 3) % 3 !== 2 && row >= 0 && row < 9;
      if (inRow && x >= rows[row][0] && x < rows[row][1] && (x * 7 + row * 5) % 5 !== 0) c = [120, 250, 140];
      if (row === 8 && inRow && x >= 22 && x < 28) c = [250, 210, 90];          // the alarm line, amber
      return mix(c, [4, 10, 6], Math.min(1, v * 1.2));
    });
  })();

  const MATX = {
    kg: { tex: 'ggs/key_grey', P: 16 }, case: { tex: D + 'case', P: 16 }, desk: { tex: D + 'desk', P: 16 }, vent: { tex: D + 'vent', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);

  function terminal(state) {
    const m = mk(D + 'desk');
    // desk: cabinet with a kick recess and a vented front, the top overhanging the front
    P(m, 'cabinet', [1, 1, 4.4], [15, 11, 15], 'desk');
    P(m, 'plinth', [1.4, 0, 5], [14.6, 1, 14.6], 'bk');
    m.box('front_vent', [3, 3, 4.3], [13, 9, 4.4], 'desk', { split: '', face: { north: { tex: D + 'vent', uv: [0, 0, 16, 16] } } });
    P(m, 'top', [0.5, 11, 1.8], [15.5, 12, 15.5], 'desk');
    P(m, 'top_edge', [0.5, 10.6, 1.8], [15.5, 11, 2.4], 'bk');
    // membrane keypad and trackball at the front of the desk
    m.box('keypad', [2.6, 12, 2.6], [9.8, 12.4, 6.2], 'bk', { split: '', face: { up: { tex: D + 'keypad', uv: [0, 0, 16, 16] } } });
    P(m, 'trackball_housing', [10.8, 12, 2.8], [13.6, 12.5, 5.8], 'case');
    P(m, 'trackball', [11.7, 12.5, 3.7], [12.7, 13.1, 4.7], 'bk');
    for (const x of [10.9, 12.5]) P(m, `trackball_key_${x}`, [x, 12.5, 5], [x + 1.1, 12.7, 5.6], 'kg');
    // CRT monitor on its swivel plinth: bezel, tapering tube housing, the screen, status lamp, cable
    P(m, 'swivel', [5.6, 12, 8.4], [10.4, 12.6, 13], 'case');
    P(m, 'bezel', [3.4, 12.6, 7.2], [12.6, 19.4, 9.2], 'case');
    P(m, 'tube', [4.2, 13.2, 9.2], [11.8, 18.8, 12.4], 'case');
    P(m, 'tube_back', [5.2, 13.8, 12.4], [10.8, 18, 14.6], 'case');
    m.box('vent_top', [5.4, 18.8, 9.6], [10.6, 18.81, 12], 'case', { split: '', face: { up: { tex: D + 'vent', uv: [0, 0, 16, 16] } } });
    const lit = state !== 'off';
    m.box(lit ? 'glow_screen' : 'screen', [4.3, 13.6, 7.05], [11.7, 18.6, 7.2], 'bk',
      { split: '', face: { north: { tex: D + (lit ? 'screen_on' : 'screen_off'), uv: [0, 0, 16, 16] } } });
    P(m, 'brand_strip', [4.6, 12.9, 7.1], [8, 13.2, 7.2], 'kg');
    lamp(m, 'status', 11.2, 13.05, 7.2, state === 'alarm' ? 'l_r' : 'l_g', lit, 0.25);
    P(m, 'power_key', [9.6, 12.85, 7.05], [10.4, 13.25, 7.2], 'kg');
    P(m, 'cable', [7.6, 12, 14.6], [8.4, 12.6, 16], 'bk');
    return m;
  }

  // ---------------------------------------------------------------- panel board, 324 x 230
  // Putty bezel round two dark CRT fields: the device list on the left (4,20,100,146 with the page keys, the alarm
  // summary 4,184,100,24 and the trend keys), the faceplate on the right (104,20,216,206) with the header line, the
  // tag rows, the trend window 108,156,208x36 (graticule) and the command row. PanelLayout draws every element.
  function gui() {
    const W = 324, H = 230, im = img(W, H), r = rng(62201), putty = [198, 190, 168];
    grain(im, putty, 62202, 2, 4, 16);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 3) put(im, x, y, add(putty, d === 0 ? -70 : d === 1 ? 26 : -18));
    }
    // CRT glass: dark green with scanlines, darkening into the rounded corners, a recessed rim
    const crt = (x0, y0, w, h, tint) => {
      for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
        const cx = Math.abs(x + 0.5 - x0 - w / 2) / (w / 2), cy = Math.abs(y + 0.5 - y0 - h / 2) / (h / 2);
        const v = Math.pow(cx, 6) + Math.pow(cy, 6);
        let c = (y - y0) % 2 ? tint : add(tint, 5);
        c = mix(c, [4, 8, 6], Math.min(1, v * 0.9));
        const edge = x === x0 || y === y0 ? -30 : x === x0 + w - 1 || y === y0 + h - 1 ? 30 : 0;
        put(im, x, y, edge ? add(putty, edge - 40) : add(c, (r() - 0.5) * 3));
      }
    };
    crt(4, 20, 100, 206, [14, 30, 20]);
    crt(104, 20, 216, 206, [12, 34, 20]);
    // separators on the left: under the device list, round the alarm summary (amber tint), over the trend keys
    for (let x = 6; x < 102; x++) { put(im, x, 182, [40, 70, 48]); put(im, x, 208, [40, 70, 48]); }
    for (let y = 184; y < 208; y++) for (let x = 6; x < 102; x++) put(im, x, y, mix([24, 20, 8], [14, 30, 20], ((y + x) % 2) * 0.3));
    // right: header underline, the trend window with a graticule, the command row rule
    for (let x = 108; x < 316; x++) { put(im, x, 36, [50, 110, 64]); put(im, x, 193, [40, 80, 50]); }
    for (let y = 156; y < 192; y++) for (let x = 108; x < 316; x++) {
      let c = [6, 20, 10];
      if ((x - 108) % 26 === 0 || (y - 156) % 9 === 0) c = [24, 60, 32];
      if (x === 108 || y === 191) c = [60, 140, 80];
      put(im, x, y, c);
    }
    return im;
  }

  function paint(root) {
    const path = require('path');
    PCART.writePng(gui(), path.join(root, PCART.ASSETS, 'textures/gui/panel', 'operator_terminal.png'));
    return FUTK.paintSet(root, D, T) + 1;
  }

  const B = (model) => ({ model, cat: 'control' });
  window.OPT = FUTK.machine({
    operator_terminal: B(() => terminal('off')), operator_terminal_on: B(() => terminal('on')), operator_terminal_alarm: B(() => terminal('alarm')),
  }, paint);
})();
