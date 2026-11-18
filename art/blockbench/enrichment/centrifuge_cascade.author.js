// Cascade control desk with its frequency converter (one cell), hand-authored for Blockbench; replaces the borrowed
// cascade_header. The block is the cascade's console, not a centrifuge: the gas_centrifuge sections stand round it.
// Front: a sloped Soviet-style operator desk with the cascade mimic (stages as a ladder of feed, product and tails
// lines), UF6 pressure meters, a key switch and the lamp row. Back: the frequency converter cabinet that turns the
// Create shaft into the high-frequency supply of the centrifuge motors, with louvres, and the bearing housing
// where the shaft comes in from the south face (FoundryMachineRenderer draws the half shaft there).
// Load in Blockbench desktop after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/enrichment/centrifuge_cascade.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CASCADE.paint('<repo>')
//   CASCADE.build('<repo>', 'centrifuge_cascade'); PCART.save('<repo>', 'enrichment', 'centrifuge_cascade')
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/centrifuge_cascade';
  const T = {};

  // Cabinet enamel: the pale grey-green of Soviet control rooms, hammer finish, scuffs round the edges.
  T.enamel = () => {
    const im = img(32, 32), r = rng(901), n = vnoise(r, 32, 32, 4);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([150, 164, 154], (n(x, y) - 0.5) * 10 + (r() - 0.5) * 5));
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [104, 112, 106]);
    return im;
  };
  // Plinth and frame: dark grey.
  T.dark = () => {
    const im = img(32, 32);
    grain(im, [56, 60, 60], 911, 4, 4, 8);
    return im;
  };
  // Louvres of the converter cabinet: pressed slats every 2 px.
  T.louvre = () => {
    const im = img(32, 32), r = rng(921);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const k = y % 4;
      put(im, x, y, add(k === 0 ? [176, 188, 180] : k === 3 ? [36, 38, 38] : [138, 150, 142], (r() - 0.5) * 6));
    }
    return im;
  };
  // Atlas (64 px, 4 px per uv unit): desk top with mimic and meters, lamp rows off/on, a converter door, a plate.
  const A = { desk: [0, 0, 56, 24], lamps_off: [0, 24, 56, 6], lamps_on: [0, 30, 56, 6], door: [0, 36, 28, 28], plate: [28, 36, 20, 8] };
  T.parts = () => {
    const im = img(64, 64), r = rng(931);
    for (let y = 0; y < 24; y++) for (let x = 0; x < 56; x++) {
      let c = add([150, 164, 154], (r() - 0.5) * 5);
      if (x === 0 || y === 0) c = [184, 196, 188];
      if (x === 55 || y === 23) c = [96, 104, 98];
      put(im, x, y, c);
    }
    // the cascade mimic: stages as boxes on a ladder; feed (blue-grey), product up (red), tails down (green)
    const line = (x0, y0, x1, y1, c) => { for (let t = 0; t <= 1; t += 0.01) put(im, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c); };
    for (let k = 0; k < 7; k++) {
      const x = 4 + k * 5, y = 16 - k * 1.6;
      rect(im, x, y, 3, 3, [44, 48, 52]); put(im, x + 1, y + 1, [200, 200, 190]);
      if (k < 6) { line(x + 3, y + 1, x + 5, y - 0.6, [180, 40, 34]); line(x + 3, y + 2, x + 5, y + 3.6, [40, 130, 70]); }
    }
    line(2, 13, 18, 13, [90, 110, 150]);
    line(4, 21, 40, 21, [40, 130, 70]); line(38, 5, 40, 5, [180, 40, 34]);
    // UF6 header pressure meters and the separative power meter
    for (let k = 0; k < 3; k++) {
      const x0 = 42, y0 = 2 + k * 7;
      rect(im, x0, y0, 11, 6, [26, 26, 24]); rect(im, x0 + 1, y0 + 1, 9, 4, [228, 222, 200]);
      for (let i = 0; i < 9; i += 2) put(im, x0 + 1 + i, y0 + 1, [40, 40, 40]);
      line(x0 + 5, y0 + 5, x0 + 3 + k * 2, y0 + 2, [30, 30, 30]);
    }
    // key switch and two push buttons
    rect(im, 30, 15, 3, 3, [40, 40, 40]); put(im, 31, 16, [200, 190, 120]);
    rect(im, 34, 15, 2, 2, [40, 160, 70]); rect(im, 37, 15, 2, 2, [190, 40, 34]);
    const lamps = (oy, on) => {
      for (let y = 0; y < 6; y++) for (let x = 0; x < 56; x++) put(im, x, oy + y, add([132, 144, 136], (r() - 0.5) * 5));
      const cols = [[80, 230, 100], [80, 230, 100], [80, 230, 100], [255, 190, 50], [255, 190, 50], [250, 70, 50], [240, 240, 220]];
      cols.forEach((c, k) => {
        const x0 = 3 + k * 7.5;
        rect(im, x0, oy + 1, 4, 4, [30, 30, 30]);
        const lit = on && (k < 3 || k === 6);
        rect(im, x0 + 1, oy + 2, 2, 2, lit ? c : mix(c, [30, 30, 30], 0.75));
        if (lit) put(im, x0 + 1, oy + 2, [255, 255, 240]);
      });
    };
    lamps(24, false); lamps(30, true);
    // converter cabinet door: louvre panel, a handle, warning triangle
    for (let y = 0; y < 28; y++) for (let x = 0; x < 28; x++) {
      let c = add([150, 164, 154], (r() - 0.5) * 5);
      if (x === 0 || y === 0) c = [184, 196, 188];
      if (x === 27 || y === 27) c = [92, 100, 94];
      if (y > 16 && y < 25 && x > 3 && x < 24) c = (y % 2) ? [44, 48, 46] : [168, 180, 172];
      put(im, x, 36 + y, c);
    }
    rect(im, 23, 44, 2, 6, [50, 52, 52]);
    for (let y = 0; y < 7; y++) for (let x = -y; x <= y; x++) put(im, 13 + x * 0.6, 40 + y, y === 6 || Math.abs(x) === y ? [30, 30, 30] : [230, 190, 30]);
    put(im, 13, 43, [30, 30, 30]); put(im, 13, 44, [30, 30, 30]);
    for (let y = 0; y < 8; y++) for (let x = 0; x < 20; x++) {
      let c = add([190, 190, 184], (r() - 0.5) * 5);
      if (x === 0 || y === 0 || x === 19 || y === 7) c = [96, 98, 96];
      if (y > 1 && y < 6 && x > 2 && x < 17 && r() < 0.45) c = [40, 42, 44];
      put(im, 28 + x, 36 + y, c);
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    enamel: { tex: 'centrifuge_cascade/enamel', P: 16 }, dark: { tex: 'centrifuge_cascade/dark', P: 16 },
    louvre: { tex: 'centrifuge_cascade/louvre', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'centrifuge_cascade/parts', size: 64, rects: A } }),
    { particle: 'centrifuge_cascade/enamel' });

  function cascade(running) {
    const m = mk();
    m.box('plinth', [0, 0, 0], [16, 1, 16], 'dark');
    // operator desk: body, the sloped top with the mimic, the lamp strip on the riser
    m.box('desk_body', [0.5, 1, 0.5], [15.5, 7, 8], 'enamel');
    m.decal('desk_top', [0.5, 7, 1], [15.5, 7.6, 8], 'enamel', 'up', 'p.desk', { hide: ['down'], rot: { axis: 'x', angle: -22.5, origin: [8, 7, 1] } });
    m.box('desk_riser', [0.5, 7, 7.6], [15.5, 12, 9], 'enamel');
    m.decal(running ? 'glow_lamps' : 'lamps', [1, 10.2, 7.4], [15, 11.7, 7.6], 'enamel', 'north', running ? 'p.lamps_on' : 'p.lamps_off', { hide: ['south'] });
    m.box('kick_plate', [0.5, 1, 0.2], [15.5, 2, 0.5], 'dark', { split: '' });
    // frequency converter cabinet at the back: nameplate over the riser, door to the south, louvres east and west
    m.box('cabinet', [0.5, 1, 9], [15.5, 16, 15.5], 'enamel');
    m.decal('nameplate', [4, 12.6, 8.8], [12, 15.2, 9], 'enamel', 'north', 'p.plate', { hide: ['south'] });
    m.decal('cabinet_door', [1, 1.5, 15.5], [15, 15.5, 15.6], 'enamel', 'south', 'p.door', { hide: ['north'] });
    for (const [x, d] of [[0.3, 'west'], [15.5, 'east']]) {
      m.box(`louvre_${d}`, [x, 4, 10.5], [x + 0.2, 13, 14], 'louvre', { split: '', hide: [d === 'west' ? 'east' : 'west'] });
    }
    m.box('cabinet_cap', [0.2, 15.6, 8.8], [15.8, 16, 15.8], 'dark', { split: '' });
    // bearing housing and coupling guard round the shaft entry on the south face
    m.box('bearing', [5, 5, 15.5], [11, 11, 16], 'dark', { split: '' });
    m.box('bearing_cap', [6.5, 6.5, 15.4], [9.5, 9.5, 15.5], 'dark', { split: '' });
    // cable trough down the west side into the floor
    m.box('cable_tray', [0, 1, 9.5], [0.5, 15, 11], 'dark', { split: '' });
    return m;
  }

  const BUILDS = {
    centrifuge_cascade: { model: () => cascade(false) },
    centrifuge_cascade_running: { model: () => cascade(true) },
  };
  window.CASCADE = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
