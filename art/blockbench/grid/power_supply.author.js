// Power supply of machines on the 0.4 kV grid, hand-authored: a household socket 16 A, a three-phase industrial
// socket 32 A and a power board with its incomer. The UPS cabinet is the hand-drawn M6 model (automation).
// Run headless: node art/tools/pcart_headless.js <repo> grid/power_supply.author.js PSU --paint
//
// Front to the north. Sockets and the board hang on the wall behind them (south face, z 16), like the lighting
// board. "_off" variants show the dropped-out contactor (no lamp).
(function () {
  const { rng, img, put, add, mix, grain } = PCART;
  const { P, lamp, cabinet } = FUTK;
  const D = 'm3psu/', T = {};

  T.white = FUTK.enamel([226, 224, 214], 32101, 0);      // RAL 9010 socket body
  T.blue = FUTK.enamel([40, 92, 160], 32102, 1);          // CEE 230 V blue
  T.red = FUTK.enamel([176, 40, 36], 32103, 1);           // CEE 400 V red
  T.holes = () => {                                        // socket face: two pins with an earthing clip
    const im = img(16, 16);
    grain(im, [214, 212, 202], 32111, 3, 4, 4);
    for (const [cx, cy] of [[5, 8], [11, 8]]) for (let y = -1; y <= 1; y++) for (let x = -1; x <= 1; x++) put(im, cx + x, cy + y, [30, 30, 30]);
    for (let x = 6; x <= 10; x++) { put(im, x, 2, [150, 150, 150]); put(im, x, 13, [150, 150, 150]); }
    return im;
  };
  T.cee = () => {                                          // CEE five-pin face
    const im = img(16, 16), r = rng(32121);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 34, 30], (r() - 0.5) * 8));
    for (let a = 0; a < 5; a++) {
      const cx = Math.round(8 + 4.5 * Math.cos(a * 2 * Math.PI / 5 - Math.PI / 2)), cy = Math.round(8 + 4.5 * Math.sin(a * 2 * Math.PI / 5 - Math.PI / 2));
      put(im, cx, cy, [20, 20, 20]); put(im, cx + 1, cy, [20, 20, 20]); put(im, cx, cy + 1, [20, 20, 20]); put(im, cx + 1, cy + 1, [20, 20, 20]);
    }
    return im;
  };
  T.breakers = () => {                                     // row of DIN rail breakers with their toggles
    const im = img(16, 16), r = rng(32131);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const toggle = x % 4 === 1 && y >= 6 && y <= 9;
      put(im, x, y, toggle ? [40, 40, 44] : add(x % 4 === 3 ? [170, 170, 166] : [222, 220, 212], (r() - 0.5) * 6));
    }
    return im;
  };
  const MATX = {
    pw: { tex: D + 'white', P: 16 }, pb: { tex: D + 'blue', P: 16 }, pr: { tex: D + 'red', P: 16 },
    holes: { tex: D + 'holes', P: 16 }, cee: { tex: D + 'cee', P: 16 }, brk: { tex: D + 'breakers', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- 16 A socket
  // A white surface box with two sockets, the cord gland at the bottom and a small "on" lamp.
  function socket(on) {
    const m = mk(D + 'white');
    P(m, 'box', [4, 3, 14.4], [12, 13, 16], 'pw');
    for (const y of [4.2, 8.6]) {
      P(m, 'face' + y, [5, y, 14], [11, y + 3.6, 14.4], 'holes');
      P(m, 'ring' + y, [4.8, y - 0.2, 14.2], [11.2, y, 14.4], 'pw');
    }
    P(m, 'gland', [7, 2, 14.8], [9, 3, 15.8], 'rubber');
    lamp(m, 'on', 10.6, 12.2, 14.4, on ? 'l_g' : 'l_off', on, 0.35);
    return m;
  }

  // ---------------------------------------------------------------- 32 A CEE socket
  // Red three-phase socket with a hinged cover and a switch interlock on a blue backplate.
  function socket3p(on) {
    const m = mk(D + 'red');
    P(m, 'plate', [3, 2, 15.2], [13, 14, 16], 'pb');
    P(m, 'switch_box', [4, 9, 13.4], [12, 13.4, 15.2], 'pr');
    P(m, 'knob', [7, 10.4, 12.8], [9, 12.4, 13.4], 'bk');
    P(m, 'knob_bar', [6.2, 11.1, 12.6], [9.8, 11.7, 12.8], 'bk');
    m.cylZ ? m.cylZ('socket', [8, 5.6], 3, 12.6, 15.2, 'pr') : P(m, 'socket', [5, 2.8, 12.6], [11, 8.4, 15.2], 'pr');
    P(m, 'face', [5.6, 3.4, 12.4], [10.4, 7.8, 12.6], 'cee');
    P(m, 'cover', [4.8, 8, 12.2], [11.2, 8.6, 15], 'pr');
    lamp(m, 'on', 11.6, 3.4, 15.2, on ? 'l_g' : 'l_off', on, 0.35);
    return m;
  }

  // ---------------------------------------------------------------- power board
  // Wall cabinet: grey enamel, a window over the DIN rail of breakers, the incomer handle, start and stop buttons
  // and a lamp for the contactor.
  function board(on) {
    const m = mk('switchgear_cell/enamel');
    cabinet(m, 'cab', [1.5, 0.5, 12.2], [14.5, 15.5, 16], 'en', { plate: true });
    P(m, 'window', [3, 7.6, 11.8], [13, 11.4, 12], 'glass');
    P(m, 'rail', [3.4, 8, 11.7], [12.6, 11, 11.8], 'brk');
    P(m, 'incomer', [3.4, 3, 11.4], [6.4, 6.6, 12], 'bk');
    P(m, 'incomer_handle', [4.4, 4, 10.8], [5.4, 6, 11.4], 'yel');
    P(m, 'btn_start', [8, 4.4, 11.6], [9.4, 5.8, 12], 'l_g');
    P(m, 'btn_stop', [10, 4.4, 11.6], [11.4, 5.8, 12], 'l_r');
    lamp(m, 'run', 12.6, 13.6, 12, on ? 'l_g' : 'l_off', on, 0.45);
    for (const x of [4, 7, 10]) P(m, 'cable' + x, [x, 0, 13.6], [x + 1.2, 0.5, 14.8], 'cable');
    m.decal('hv', [2.6, 12.8, 11.8], [4.4, 14.6, 12], 'en', 'north', 'parts.warning');
    return m;
  }

  const B = (model) => ({ model, cat: 'grid' });
  const BUILDS = {
    power_socket: B(() => socket(true)), power_socket_off: B(() => socket(false)),
    power_socket_3p: B(() => socket3p(true)), power_socket_3p_off: B(() => socket3p(false)),
    power_board: B(() => board(true)), power_board_off: B(() => board(false)),
  };
  window.PSU = FUTK.machine(BUILDS, paint);
})();
