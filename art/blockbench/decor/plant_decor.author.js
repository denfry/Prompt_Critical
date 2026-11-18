// Shaped plant decor, hand-authored for Blockbench (replaces the spec_decor.py / spec_zdecor.py placeholders):
// cable trays (straight, with cables, corner, tee, vertical), cage ladder, curb, floor grating and stair step,
// handrails (yellow and galvanised), room signs, safety posters, wall clock; plus the LRW evaporator and the oil
// seep.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/decor/plant_decor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); DEC.paint('<repo>')
//   DEC.exportAll('<repo>')
//
// Front to the north; wall pieces sit against the south face. The signs and posters keep their lettered
// textures (textures/block/decor/room_sign_*.png, safety_poster*.png) and get frames and fixings; the posters
// span two blocks to the east as before.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'decor_hand/';
  const T = {};

  // Cable sheaths in a tray: black, grey and orange runs.
  T.cables = () => { const im = img(16, 16), r = rng(15101), c = [[30, 30, 32], [96, 98, 100], [30, 30, 32], [200, 110, 40], [30, 30, 32]]; for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(c[Math.floor(x / 3.2)], (x % 3 === 0 ? -14 : 6) + (r() - 0.5) * 6)); return im; };
  // Perforated tray steel.
  T.tray = () => { const im = img(16, 16), r = grain(im, [170, 174, 172], 15111, 3, 3, 4); for (let y = 2; y < 16; y += 4) for (let x = 1; x < 16; x += 3) { put(im, x, y, [90, 92, 92]); put(im, x + 1, y, [90, 92, 92]); } return im; };
  // Oil seep: black oil with a rainbow sheen on wet sand.
  T.oil = () => { const im = img(32, 32), r = rng(15121), n = vnoise(r, 32, 32, 8); for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) { const t = n(x, y); put(im, x, y, t < 0.55 ? add([20, 18, 16], (r() - 0.5) * 6 + (t > 0.5 ? 30 : 0)) : add([150, 132, 96], (r() - 0.5) * 16)); } for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [80, 60, 120]); return im; };
  // Clock face: white dial, hour marks, hands at ten past ten.
  T.clock = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, Math.hypot(x - 15.5, y - 15.5) < 15.6 ? [240, 238, 230] : [30, 30, 30]);
    for (let k = 0; k < 12; k++) { const a = k * 30 * Math.PI / 180; for (let s = 0; s < (k % 3 ? 2 : 4); s++) put(im, 15.5 + Math.cos(a) * (13 - s), 15.5 - Math.sin(a) * (13 - s), [20, 20, 20]); }
    for (let s = 0; s < 8; s++) put(im, 15.5 + Math.cos(Math.PI * 5 / 6) * s, 15.5 - Math.sin(Math.PI * 5 / 6) * s, [20, 20, 20]);
    for (let s = 0; s < 11; s++) put(im, 15.5 + Math.cos(Math.PI / 6) * s, 15.5 - Math.sin(Math.PI / 6) * s, [20, 20, 20]);
    for (let s = 0; s < 12; s++) put(im, 15.5, 15.5 + s, [200, 30, 24]);
    return im;
  };

  const MAT = Object.assign({}, PLANT.MAT, {
    cab: { tex: TEX + 'cables', P: 16 }, tray: { tex: TEX + 'tray', P: 16 }, oil: { tex: TEX + 'oil', P: 16 },
    gal: { tex: 'disconnector/galvanised', P: 16 }, grat: { tex: 'steelmaking/grating', P: 8 }, en: { tex: 'switchgear_cell/enamel', P: 16 },
    ss: { tex: 'electronics/ss', P: 16 }, glass: { tex: 'electronics/glass', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });
  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  const B = (m, n, f, t, mat, o) => m.box(n, f, t, mat, Object.assign({ split: '' }, o || {}));

  // ---------------------------------------------------------------- cable trays
  // Ladder-type tray along z: two side rails, rungs every 4, with or without cables lying in it.
  function trayRun(m, n, x0, x1, z0, z1, along, cables) {
    if (along === 'z') {
      B(m, `${n}_rail_w`, [x0, 0, z0], [x0 + 0.6, 3, z1], 'tray');
      B(m, `${n}_rail_e`, [x1 - 0.6, 0, z0], [x1, 3, z1], 'tray');
      for (let z = z0 + 1; z < z1; z += 4) B(m, `${n}_rung_${z}`, [x0 + 0.6, 0, z], [x1 - 0.6, 0.6, z + 1], 'gal');
      if (cables) B(m, `${n}_cables`, [x0 + 0.6, 0.6, z0], [x1 - 0.6, 2, z1], 'cab');
    } else {
      B(m, `${n}_rail_n`, [x0, 0, z0], [x1, 3, z0 + 0.6], 'tray');
      B(m, `${n}_rail_s`, [x0, 0, z1 - 0.6], [x1, 3, z1], 'tray');
      for (let x = x0 + 1; x < x1; x += 4) B(m, `${n}_rung_${x}`, [x, 0, z0 + 0.6], [x + 1, 0.6, z1 - 0.6], 'gal');
      if (cables) B(m, `${n}_cables`, [x0, 0.6, z0 + 0.6], [x1, 2, z1 - 0.6], 'cab', { face: { up: { tex: TEX + 'cables', uv: [0, 0, 16, 16] } } });
    }
  }
  function tray(cables) { const m = mk(TEX + 'tray'); trayRun(m, 't', 3, 13, 0, 16, 'z', cables); return m; }
  function trayCorner() {
    const m = mk(TEX + 'tray');
    B(m, 'rail_w', [3, 0, 0], [3.6, 3, 13], 'tray');
    B(m, 'rail_n', [3.6, 0, 12.4], [16, 3, 13], 'tray');
    B(m, 'rail_in_e', [12.4, 0, 0], [13, 3, 3.6], 'tray');
    B(m, 'rail_in_s', [12.4, 0, 3], [16, 3, 3.6], 'tray');
    B(m, 'floor', [3.6, 0, 0], [12.4, 0.6, 12.4], 'gal');
    B(m, 'floor_e', [12.4, 0, 3.6], [16, 0.6, 12.4], 'gal');
    B(m, 'cables_n', [3.6, 0.6, 0], [12.4, 2, 12.4], 'cab');
    B(m, 'cables_e', [12.4, 0.6, 3.6], [16, 2, 12.4], 'cab');
    return m;
  }
  function trayTee() {
    const m = mk(TEX + 'tray');
    B(m, 'rail_w', [3, 0, 0], [3.6, 3, 16], 'tray');
    B(m, 'rail_e_n', [12.4, 0, 0], [13, 3, 3.6], 'tray');
    B(m, 'rail_e_s', [12.4, 0, 12.4], [13, 3, 16], 'tray');
    B(m, 'rail_branch_n', [13, 0, 3], [16, 3, 3.6], 'tray');
    B(m, 'rail_branch_s', [13, 0, 12.4], [16, 3, 13], 'tray');
    B(m, 'floor', [3.6, 0, 0], [12.4, 0.6, 16], 'gal');
    B(m, 'floor_e', [12.4, 0, 3.6], [16, 0.6, 12.4], 'gal');
    B(m, 'cables', [3.6, 0.6, 0], [12.4, 2, 16], 'cab');
    B(m, 'cables_e', [12.4, 0.6, 3.6], [16, 2, 12.4], 'cab');
    return m;
  }
  function trayVertical() {
    const m = mk(TEX + 'tray');
    B(m, 'rail_w', [3, 0, 12.5], [3.6, 16, 16], 'tray');
    B(m, 'rail_e', [12.4, 0, 12.5], [13, 16, 16], 'tray');
    for (let y = 1; y < 16; y += 4) B(m, `rung_${y}`, [3.6, y, 15.4], [12.4, y + 1, 16], 'gal');
    B(m, 'cables', [3.6, 0, 13.4], [12.4, 16, 15.4], 'cab');
    for (let y = 3; y < 16; y += 6) B(m, `tie_${y}`, [3.6, y, 13.2], [12.4, y + 0.4, 13.4], 'rubber');
    return m;
  }

  // ---------------------------------------------------------------- access
  // Cage ladder against the south: stringers and rungs, the safety hoops tied by vertical bars.
  function cageLadder() {
    const m = mk('disconnector/galvanised');
    for (const x of [4, 11.4]) B(m, `stringer_${x}`, [x, 0, 13.6], [x + 0.6, 16, 14.4], 'gal');
    for (let y = 1; y < 16; y += 3) B(m, `rung_${y}`, [4.6, y, 13.8], [11.4, y + 0.4, 14.2], 'gal');
    B(m, 'hoop_n', [1, 8, 1], [15, 8.6, 1.6], 'gal');
    B(m, 'hoop_w', [1, 8, 1.6], [1.6, 8.6, 14], 'gal');
    B(m, 'hoop_e', [14.4, 8, 1.6], [15, 8.6, 14], 'gal');
    for (const [x, z] of [[1, 1], [14.4, 1], [7.7, 1], [1, 7.5], [14.4, 7.5]]) B(m, `bar_${x}_${z}`, [x, 0, z], [x + 0.6, 16, z + 0.6], 'gal');
    return m;
  }
  // Curb stone: a concrete kerb with a chamfered top edge and painted black-and-white bands.
  function curb() {
    const m = mk('plant/concrete');
    B(m, 'kerb', [0, 0, 5], [16, 5, 11], 'conc');
    B(m, 'chamfer', [0, 5, 6], [16, 6, 11], 'conc');
    B(m, 'band_w', [0, 0, 4.95], [8, 5, 5], 'light', { hide: ['south'] });
    B(m, 'band_b', [8, 0, 4.95], [16, 5, 5], 'rubber', { hide: ['south'] });
    return m;
  }
  // Floor grating at the top of the block on its bearer bars, and the stair step with its yellow nosing.
  function grating() {
    const m = mk('steelmaking/grating');
    B(m, 'grating', [0, 15, 0], [16, 16, 16], 'grat');
    for (const x of [0.4, 15]) B(m, `bearer_${x}`, [x, 12, 0], [x + 0.6, 15, 16], 'gal');
    return m;
  }
  function gratingStep() {
    const m = mk('steelmaking/grating');
    B(m, 'tread_lo', [0, 7, 0], [16, 8, 8], 'grat');
    B(m, 'tread_hi', [0, 15, 8], [16, 16, 16], 'grat');
    B(m, 'nosing_lo', [0, 7, -0.01], [16, 8, 1], 'yel');
    B(m, 'nosing_hi', [0, 15, 7.99], [16, 16, 9], 'yel');
    for (const x of [0, 15.4]) B(m, `stringer_${x}`, [x, 0, 0], [x + 0.6, 16, 16], 'gal', { rot: undefined });
    return m;
  }
  // Handrail: posts, top rail and knee rail, toe board.
  function handrail(mat) {
    const m = mk(mat === 'yel' ? 'plant/yellow' : 'disconnector/galvanised');
    for (const x of [0.6, 14.8]) B(m, `post_${x}`, [x, 0, 7.2], [x + 0.6, 16, 7.8], mat);
    B(m, 'top_rail', [0, 15.4, 7], [16, 16.4, 8], mat);
    B(m, 'knee_rail', [0, 8, 7.2], [16, 8.6, 7.8], mat);
    B(m, 'toe_board', [0, 0, 7.3], [16, 1.6, 7.7], mat);
    return m;
  }

  // ---------------------------------------------------------------- signs and wall pieces
  function sign(id) {
    const m = mk('decor/' + id);
    B(m, 'plate', [2, 5, 15.4], [14, 11, 16], 'en', { face: { north: { tex: 'decor/' + id, uv: [0, 0, 16, 16] } } });
    B(m, 'rim_t', [1.8, 10.8, 15.3], [14.2, 11.2, 16], 'frame');
    B(m, 'rim_b', [1.8, 4.8, 15.3], [14.2, 5.2, 16], 'frame');
    for (const x of [2.6, 13]) B(m, `screw_${x}`, [x, 7.6, 15.3], [x + 0.4, 8, 15.4], 'bright');
    return m;
  }
  function poster(large) {
    const id = large ? 'safety_poster_large' : 'safety_poster', h = large ? 32 : 16;
    const m = mk('decor/' + id);
    B(m, 'sheet', [0.4, 0.4, 15.75], [31.6, h - 0.4, 16], 'light', { face: { north: { tex: 'decor/' + id, uv: [0, 0, 16, 16] } } });
    B(m, 'rail_t', [0, h - 0.8, 15.5], [32, h, 16], 'wood');
    B(m, 'rail_b', [0, 0, 15.5], [32, 0.8, 16], 'wood');
    return m;
  }
  function clock() {
    const m = mk(TEX + 'clock');
    B(m, 'case', [2, 2, 15], [14, 14, 16], 'rubber');
    B(m, 'bezel', [1.6, 1.6, 14.6], [14.4, 14.4, 15], 'bright');
    m.plate('face', [2.4, 2.4, 14.5], [13.6, 13.6, 14.6], 'rubber', 'north', TEX + 'clock', { hide: ['south'] });
    return m;
  }

  // ---------------------------------------------------------------- process odds
  // LRW evaporator: a stainless natural-circulation evaporator with its heating calandria (steam red), the
  // separator with a sight glass on top, the condensate and concentrate lines, on a frame.
  function evaporator() {
    const m = mk('electronics/ss');
    B(m, 'frame', [0.4, 0, 2], [15.6, 1.4, 14], 'frame');
    m.prismY('calandria', [6, 8], 3.4, 1.4, 9, 'ss', {});
    m.prismY('steam_jacket', [6, 8], 3.7, 3, 7, 'p_steam', {});
    m.prismY('separator', [6, 8], 4.6, 9, 16, 'ss', { ends: ['up'] });
    B(m, 'sight', [5.2, 11, 3.2], [6.8, 14, 3.5], 'glass');
    B(m, 'vapour', [5.4, 16, 7.4], [6.6, 17.5, 8.6], 'p_steam');
    B(m, 'condenser', [11, 9, 4], [15, 15, 12], 'ss');
    B(m, 'vapour_line', [6.6, 14, 7.4], [11, 15.2, 8.6], 'p_steam');
    B(m, 'concentrate', [9.4, 1.4, 7.4], [15, 2.6, 8.6], 'p_acid');
    B(m, 'condensate', [12.4, 1.4, 4.6], [13.6, 9, 5.8], 'p_water');
    return m;
  }
  // Oil seep: a low mound of oil-soaked sand with a black pool and a sheen.
  function oilSeep() {
    const m = mk(TEX + 'oil');
    B(m, 'mound', [0, 0, 0], [16, 2, 16], 'oil', { face: { up: { tex: TEX + 'oil', uv: [0, 0, 16, 16] } } });
    B(m, 'pool', [4, 2, 5], [12, 2.4, 12], 'rubber');
    B(m, 'lip', [2, 2, 2], [6, 3, 6], 'oil');
    B(m, 'lip2', [11, 2, 10], [15, 3.4, 14], 'oil');
    return m;
  }

  const BUILDS = {
    cable_tray: { model: () => tray(false) }, cable_tray_cables: { model: () => tray(true) },
    cable_tray_corner: { model: trayCorner }, cable_tray_tee: { model: trayTee }, cable_tray_vertical: { model: trayVertical },
    cage_ladder: { model: cageLadder }, curb: { model: curb }, grating: { model: grating }, grating_step: { model: gratingStep },
    handrail: { model: () => handrail('yel') }, handrail_galvanized: { model: () => handrail('gal') },
    room_sign: { model: () => sign('room_sign') }, room_sign_control: { model: () => sign('room_sign_control') },
    room_sign_electrical: { model: () => sign('room_sign_electrical') }, room_sign_evac: { model: () => sign('room_sign_evac') },
    room_sign_pump: { model: () => sign('room_sign_pump') },
    safety_poster: { model: () => poster(false) }, safety_poster_large: { model: () => poster(true) },
    wall_clock: { model: clock },
    evaporator: { model: evaporator, cat: 'conversion' }, oil_seep: { model: oilSeep, cat: 'oil_geology' },
  };

  window.DEC = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    // Synchronous: Blockbench throttles timers while its window is hidden.
    exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        PCART.realize(root, BUILDS[id].model(), id);
        out[id] = PCART.save(root, BUILDS[id].cat || 'decor', id, BUILDS[id].java);
        while (Dialog.open) Dialog.open.close();
      }
      return out;
    },
  };
})();
