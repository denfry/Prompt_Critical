// Plant public address (GDD 13 §13.3.4, ГГС), hand-authored, replacing the M10 drafts of m10_safety.author.js:
//   intercom_station[_talking|_fault]  wall station in a light-grey IP54 steel box (RAL 7035): round speaker grille,
//                                      power / talk / fault lamps, six zone keys, the channel selector, four
//                                      pre-recorded message keys (evacuation, fire, gas, all clear), the hand
//                                      microphone with its press-to-talk bar on a hook at the side with a coiled cord,
//                                      conduit down the wall from the cable gland.
//   intercom_station_desk              desk console: sloped key field, gooseneck microphone, lamps, message keys.
//   plant_loudspeaker[_active|_pole|_ceiling]  pressure-chamber horn (25 W, 10GRD class): flared bell of grey
//                                      enamel with a rolled lip, the dark phase plug seen in the throat, the driver
//                                      unit behind, a U yoke on pivot bolts and the wall plate, pole clamp or ceiling
//                                      rod. Active: the green LED on the driver.
// Real scale (16 units = 1 m): station 0.45 x 0.56 m, horn bell 0.62 m across.
// Run headless: node art/tools/pcart_headless.js <repo> ops/ggs.author.js GGS --paint
// Front to the north; wall pieces hang on the south face (backs at z 16), as the drafts they replace.
(function () {
  const { rng, img, put, add, grain } = PCART;
  const { P, lamp } = FUTK;
  const D = 'ggs/', T = {};

  T.body = FUTK.enamel([192, 194, 188], 61101, 0);
  T.horn = FUTK.enamel([148, 152, 150], 61102, 1);
  // Perforated speaker grille, radial: rows of holes on rings, a pressed rim.
  T.grille = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy);
      let c = d > 7 ? [150, 152, 148] : [176, 178, 172];
      if (d <= 6.4 && (x + y) % 2 === 0 && (x % 2 === 0)) c = [36, 38, 40];
      if (d > 6.4 && d <= 7) c = [204, 206, 200];
      put(im, x, y, c);
    }
    return im;
  };
  // Key caps: a little lighter at the top, a shadow at the bottom.
  const cap = (base, seed) => () => { const im = img(16, 16); grain(im, base, seed, 3, 2, 8); for (let x = 0; x < 16; x++) { put(im, x, 0, add(base, 40)); put(im, x, 15, add(base, -40)); } return im; };
  T.key_grey = cap([70, 74, 78], 61111);
  T.key_white = cap([214, 214, 206], 61112);
  // Hand microphone: black bakelite with the moulded grip ribs.
  T.bakelite = () => { const im = img(16, 16), r = grain(im, [30, 30, 32], 61121, 3, 3, 8); for (let y = 2; y < 16; y += 3) for (let x = 0; x < 16; x++) put(im, x, y, [48, 48, 52]); return im; };

  const MATX = {
    body: { tex: D + 'body', P: 16 }, horn: { tex: D + 'horn', P: 16 }, kg: { tex: D + 'key_grey', P: 16 }, kw: { tex: D + 'key_white', P: 16 },
    bak: { tex: D + 'bakelite', P: 16 }, orng: { tex: 'm10ops/orange', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  // ---------------------------------------------------------------- panel boards (PanelLayout draws every element)
  // Light-grey enamel board of the station box in a brass frame, recessed fields behind each group of elements.
  function board(W, H, seed) {
    const im = img(W, H), r = rng(seed), base = [176, 178, 172];
    grain(im, base, seed + 1, 2, 4, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    const inset = (x0, y0, x1, y1, k = 20) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const e = x === x0 || y === y0 ? -k : x === x1 - 1 || y === y1 - 1 ? k * 0.8 : 0;
        put(im, x, y, add(add(base, -16), e + (r() - 0.5) * 3));
      }
    };
    const screw = (x, y) => { put(im, x, y, [120, 122, 118]); put(im, x + 1, y, [210, 212, 206]); put(im, x, y + 1, [210, 212, 206]); put(im, x + 1, y + 1, [96, 98, 94]); };
    for (const [x, y] of [[6, 6], [W - 8, 6], [6, H - 8], [W - 8, H - 8]]) screw(x, y);
    return { im, inset };
  }
  // Channel colours, EVAC .. CHEM_RAD, as tabs over the six channel keys.
  const CH = [[200, 40, 34], [232, 110, 30], [230, 230, 222], [60, 110, 180], [220, 180, 40], [130, 70, 150]];
  function guiIntercom() {
    const { im, inset } = board(232, 122, 61201);
    inset(5, 20, 227, 43);
    CH.forEach((c, i) => { for (let x = 9 + 36 * i; x < 41 + 36 * i; x++) { put(im, x, 21, c); put(im, x, 22, add(c, -40)); } });
    inset(5, 40, 227, 79);
    inset(5, 79, 227, 100);
    for (let i = 0; i < 4; i++) for (let x = 9 + 56 * i; x < 59 + 56 * i; x++) put(im, x, 99, [[200, 40, 34], [232, 110, 30], [220, 180, 40], [60, 150, 70]][i]);
    inset(5, 101, 227, 118);
    return im;
  }
  function guiLoudspeaker() {
    const { im, inset } = board(232, 60, 61211);
    inset(5, 18, 227, 56);
    return im;
  }
  function paint(root) {
    const path = require('path'), dir = path.join(root, PCART.ASSETS, 'textures/gui/panel');
    PCART.writePng(guiIntercom(), path.join(dir, 'intercom_station.png'));
    PCART.writePng(guiLoudspeaker(), path.join(dir, 'plant_loudspeaker.png'));
    return FUTK.paintSet(root, D, T) + 2;
  }
  const MSG = [['red', 'evac'], ['orng', 'fire'], ['yel', 'gas'], ['green', 'clear']];

  // ---------------------------------------------------------------- wall station
  function station(state) {
    const m = mk(D + 'body');
    P(m, 'box', [4.4, 5, 13.4], [11.6, 14, 16], 'body');
    P(m, 'lid_seam', [4.3, 13.4, 13.3], [11.7, 13.6, 16], 'body');
    m.disc('grille', 'z', [8, 11.5], 1.7, 13.2, 13.4, 'body', D + 'grille', 'north');
    // lamps: power (white), talk (green), fault (red)
    lamp(m, 'power', 6, 9.2, 13.4, 'l_w', state !== 'fault', 0.3);
    lamp(m, 'talk', 8, 9.2, 13.4, 'l_g', state === 'talking', 0.3);
    lamp(m, 'fault', 10, 9.2, 13.4, 'l_r', state === 'fault', 0.3);
    // six zone keys in two rows, the channel selector beside them
    for (let j = 0; j < 2; j++) for (let i = 0; i < 3; i++) {
      const x = 5.2 + i * 1.1, y = 7.4 - j * 1.1;
      P(m, `zone_${i}_${j}`, [x, y, 13.15], [x + 0.8, y + 0.8, 13.4], 'kw');
    }
    m.prism('selector', 'z', [10.2, 7.2], 0.7, 12.9, 13.4, 'bk', { split: '', ends: ['north'] });
    P(m, 'selector_pointer', [10.1, 7.2, 12.85], [10.3, 7.8, 12.9], 'wh');
    // pre-recorded message keys along the bottom
    MSG.forEach(([mat, n], i) => P(m, `msg_${n}`, [5.1 + i * 1.5, 5.5, 13.1], [6.1 + i * 1.5, 6.1, 13.4], mat));
    // hand microphone on its hook at the east side, coiled cord to the bottom
    P(m, 'mic_hook', [11.6, 11, 14.2], [12.2, 11.6, 15.2], 'st');
    P(m, 'mic', [11.7, 8.2, 13.6], [12.9, 11.2, 14.8], 'bak');
    P(m, 'mic_head', [11.6, 10.6, 13.5], [13, 11.6, 14.9], 'bak');
    P(m, 'mic_ptt', [11.9, 9, 13.4], [12.7, 10.2, 13.6], state === 'talking' ? 'red' : 'kg');
    for (let k = 0; k < 5; k++) P(m, `cord_${k}`, [12 + (k % 2) * 0.2, 7.4 - k * 0.6, 14], [12.6 + (k % 2) * 0.2, 7.8 - k * 0.6, 14.6], 'bk');
    P(m, 'cord_in', [11.6, 4.8, 14.1], [12.2, 5.2, 14.5], 'bk');
    // cable gland and the conduit down the wall
    P(m, 'gland', [7.4, 4.4, 14.4], [8.6, 5, 15.6], 'st');
    P(m, 'conduit', [7.6, 0, 14.6], [8.4, 4.4, 15.4], 'galv');
    P(m, 'conduit_saddle', [7.2, 2, 15.4], [8.8, 2.6, 16], 'galv');
    return m;
  }

  // ---------------------------------------------------------------- desk console
  function desk() {
    const m = mk(D + 'body');
    // base, the key field sloping up 22.5 degrees from the front edge, the raised back with lamps, grille and mic
    P(m, 'base', [3, 0, 4], [13, 1.8, 12.5], 'body');
    P(m, 'back', [3, 1.8, 10.2], [13, 4.4, 12.5], 'body');
    const s = { rot: { axis: 'x', angle: -22.5, origin: [8, 1.8, 4.2] } };
    P(m, 'slope', [3, 1.4, 4.2], [13, 1.8, 10.6], 'body', s);
    for (let i = 0; i < 6; i++) P(m, `zone_${i}`, [3.8 + i * 1.3, 1.8, 8.2], [4.7 + i * 1.3, 2.05, 9.1], 'kw', s);
    MSG.forEach(([mat, n], i) => P(m, `msg_${n}`, [3.8 + i * 1.6, 1.8, 6.4], [5 + i * 1.6, 2.05, 7.2], mat, s));
    P(m, 'ptt', [10.6, 1.8, 4.8], [12.2, 2.1, 5.8], 'red', s);
    lamp(m, 'power', 4.4, 4.4, 11.4, 'l_w', true, 0.3, 'up');
    lamp(m, 'talk', 5.6, 4.4, 11.4, 'l_g', false, 0.3, 'up');
    lamp(m, 'fault', 6.8, 4.4, 11.4, 'l_r', false, 0.3, 'up');
    m.box('grille', [8, 4.4, 10.8], [10.4, 4.5, 12.1], 'body', { split: '', face: { up: { tex: D + 'grille', uv: [2, 2, 14, 14] } } });
    // gooseneck microphone from the back right
    P(m, 'neck_base', [11.4, 4.4, 10.8], [12.6, 4.8, 12], 'bk');
    for (let k = 0; k < 6; k++) P(m, `neck_${k}`, [11.7, 4.8 + k * 0.6, 11.1 - k * 0.5], [12.3, 5.5 + k * 0.6, 11.7 - k * 0.5], 'bk');
    P(m, 'mic_capsule', [11.5, 8.2, 7.6], [12.5, 9, 9], 'bak');
    return m;
  }

  // ---------------------------------------------------------------- horn loudspeaker
  function horn(m) {
    m.ring('lip', 'z', [8, 8], 5, 0.5, 2.8, 3.3, 'horn');
    for (const [n, r, z0, z1] of [['bell_0', 4.6, 3.3, 4.4], ['bell_1', 3.8, 4.4, 5.6], ['bell_2', 3, 5.6, 6.8], ['bell_3', 2.2, 6.8, 8], ['throat', 1.6, 8, 9.4]])
      m.prism(n, 'z', [8, 8], r, z0, z1, 'horn', { split: '', ends: ['south'] });  // back faces only: the inside stays open
    m.prism('phase_plug', 'z', [8, 8], 2.8, 6, 6.2, 'dk', { split: '', ends: ['north'] });
    m.prism('driver', 'z', [8, 8], 2.1, 9.4, 11.8, 'dk', { split: '', ends: ['north'] });
    m.prism('driver_cap', 'z', [8, 8], 1.6, 11.8, 12.2, 'dk', { split: '', ends: ['south'] });
    P(m, 'gland', [7.5, 5.4, 10.4], [8.5, 5.9, 11.4], 'st');
  }
  function yoke(m, z1) {
    for (const [n, x0] of [['w', 5.3], ['e', 10.2]]) {
      P(m, `yoke_${n}`, [x0, 7.4, 9.8], [x0 + 0.5, 8.6, z1], 'horn');
      P(m, `pivot_${n}`, [n === 'w' ? 5 : 10.7, 7.6, 10.4], [n === 'w' ? 5.3 : 11, 8.4, 11.2], 'bright');
    }
    P(m, 'yoke_bar', [5.3, 7.4, z1 - 0.6], [10.7, 8.6, z1], 'horn');
  }
  function loudspeaker(mount, active) {
    const m = mk(D + 'horn');
    horn(m);
    if (mount === 'wall') {
      yoke(m, 13.8);
      P(m, 'stem', [7.4, 7.4, 13.8], [8.6, 8.6, 15.2], 'horn');
      P(m, 'wall_plate', [6, 6, 15.2], [10, 10, 16], 'horn');
      for (const [x, y] of [[6.5, 6.5], [9, 6.5], [6.5, 9], [9, 9]]) P(m, `bolt_${x}_${y}`, [x, y, 15], [x + 0.5, y + 0.5, 15.2], 'bright');
    }
    if (mount === 'pole') {
      yoke(m, 12.6);
      P(m, 'pole', [6.6, -0.03, 12.6], [9.4, 16.03, 15.4], 'galv', { split: 'y' });
      for (const y of [6.6, 8.6]) P(m, `clamp_${y}`, [6.3, y, 12.4], [9.7, y + 0.8, 15.6], 'horn');
    }
    if (mount === 'ceiling') {
      for (const [n, x0] of [['w', 5.3], ['e', 10.2]]) {
        P(m, `stirrup_${n}`, [x0, 8, 10.2], [x0 + 0.5, 10.8, 11.2], 'horn');
        P(m, `pivot_${n}`, [n === 'w' ? 5 : 10.7, 7.6, 10.3], [n === 'w' ? 5.3 : 11, 8.4, 11.1], 'bright');
      }
      P(m, 'stirrup_bar', [5.3, 10.8, 10.2], [10.7, 11.4, 11.2], 'horn');
      P(m, 'rod', [7.6, 11.4, 10.3], [8.4, 15.4, 11.1], 'galv');
      P(m, 'ceiling_rose', [6.4, 15.4, 9.2], [9.6, 16, 12.2], 'horn');
    }
    lamp(m, 'active', 8, 10.1, 11, 'l_g', active, 0.3, 'up');
    return m;
  }

  const B = (model, java) => ({ model, cat: 'ops', java });
  const BUILDS = {
    intercom_station: B(() => station('idle')), intercom_station_talking: B(() => station('talking')),
    intercom_station_fault: B(() => station('fault')), intercom_station_desk: B(desk),
    plant_loudspeaker: B(() => loudspeaker('wall', false)), plant_loudspeaker_active: B(() => loudspeaker('wall', true)),
    plant_loudspeaker_pole: B(() => loudspeaker('pole', false)), plant_loudspeaker_ceiling: B(() => loudspeaker('ceiling', false)),
  };
  window.GGS = FUTK.machine(BUILDS, paint);
})();
