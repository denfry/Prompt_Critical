// Shaft top: steel headframe sections, the headgear sheave and the cage, hand-authored for Blockbench
// (replaces the spec_metallurgy.py placeholders).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/headframe.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); HFR.paint('<repo>')
//   await HFR.exportAll('<repo>')
//
// Headframe: 3x3x3 sections (model -16..32 on every axis, anchor in the middle) stacked 2 to 4 high. Each
// section has four built-up riveted legs, girts at the foot and at mid height, and cross bracing in two bays
// per face. The diagonals run at 22.5 degrees, the steepest a Java model allows for a bar this long, and
// their ends bury in the legs. A ladder runs up the west side. The top section stops at the sheave deck:
// chequer plate with a slot for the lower half of the sheave, the sheave girders and plummer blocks with the
// axle at y 16 (HoistRenderer pivots the sheave there), handrails, and a lifting beam over the sheave for
// changing it.
//
// Cage: 2x3x2, model x 0..32, y -16..32, z 0..32, the gate to the north. It has a chequer floor with tub
// rails, sheet sides with wire mesh above, corner channels, guide shoes on both sides, the crosshead with the
// rope capel on the roof, and a sliding gate of flat bars, raised when open.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/shaft';
  const T = {};

  // Red-oxide riveted steel, weathered: rust blooms, rain streaks, a rivet every 4 px along the plate seams.
  T.steel = () => {
    const im = img(32, 32), r = grain(img(32, 32), [0, 0, 0], 801, 0, 0, 8);
    const g = img(32, 32);
    grain(g, [128, 52, 36], 802, 5, 10, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4;
      let c = [g.d[i], g.d[i + 1], g.d[i + 2]];
      if (r() < 0.04) c = mix(c, [86, 46, 30], 0.6);
      if (x % 16 === 15) c = add(c, -26);
      put(im, x, y, c);
    }
    for (let y = 1; y < 32; y += 4) for (const x of [2, 13, 18, 29]) {
      put(im, x, y, [168, 82, 60]); put(im, x + 1, y, [140, 64, 46]); put(im, x, y + 1, [120, 52, 38]); put(im, x + 1, y + 1, [70, 30, 22]);
    }
    return im;
  };
  // Woven wire mesh of the cage sides, 4 px pitch, the holes clear.
  T.mesh = () => {
    const im = img(32, 32), r = rng(811);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const wx = x % 4 === 0, wy = y % 4 === 0;
      if (!wx && !wy) { put(im, x, y, [0, 0, 0, 0]); continue; }
      const over = wx && wy ? ((x + y) % 8 === 0 ? 14 : -14) : 0;
      put(im, x, y, add([118, 120, 118], over + (r() - 0.5) * 16));
    }
    return im;
  };
  // Cage gate: vertical flat bars with two cross straps, clear between the bars.
  T.gate = () => {
    const im = img(32, 32), r = rng(821);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const bar = x % 4 < 1, strap = y % 16 < 2;
      if (!bar && !strap) { put(im, x, y, [0, 0, 0, 0]); continue; }
      put(im, x, y, add(strap ? [150, 60, 40] : [96, 98, 100], (r() - 0.5) * 12 + (strap && y % 16 === 0 ? 16 : 0)));
    }
    return im;
  };
  // Cage sheet, painted grey-green, scuffed bright where tubs and boots hit it.
  T.sheet = () => {
    const im = img(32, 32), r = grain(im, [84, 98, 90], 831, 4, 6, 8);
    for (let k = 0; k < 14; k++) {
      const x0 = Math.floor(r() * 30), y0 = 20 + Math.floor(r() * 12);
      for (let i = 0; i < 3; i++) put(im, x0 + i, y0, [150, 150, 146]);
    }
    for (let y = 2; y < 32; y += 6) for (const x of [1, 30]) { put(im, x, y, [130, 140, 134]); put(im, x, y + 1, [50, 58, 54]); }
    return im;
  };
  const MAT = {
    hsteel: { tex: 'shaft/steel', P: 16 }, sheet: { tex: 'shaft/sheet', P: 16 }, mesh: { tex: 'shaft/mesh', P: 16 },
    gate: { tex: 'shaft/gate', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // ------------------------------------------------------------------ headframe
  const LEG = [-16, 29];                 // leg x (and z) starts, legs 3 wide
  const IN0 = -13, IN1 = 29;             // clear span between legs

  // Two crossed diagonals across a bay centred at (u, yc) on one face. Faces 'n'/'s' lie in x (rotation about z),
  // 'w'/'e' lie in z (rotation about x). The diagonals sit 1 deep just inside the face.
  function cross(m, n, face, yc) {
    const L = 46, h = 1.4, c = 7;
    for (const a of [22.5, -22.5]) {
      if (face === 'n' || face === 's') {
        const z0 = face === 'n' ? -15.5 : 30.5;
        m.box(`${n}_${a}`, [c - L / 2, yc - h / 2, z0], [c + L / 2, yc + h / 2, z0 + 1], 'hsteel',
          { split: '', rot: { axis: 'z', angle: a, origin: [c, yc, z0] } });
      } else {
        const x0 = face === 'w' ? -15.5 : 30.5;
        m.box(`${n}_${a}`, [x0, yc - h / 2, c - L / 2], [x0 + 1, yc + h / 2, c + L / 2], 'hsteel',
          { split: '', rot: { axis: 'x', angle: a, origin: [x0, yc, c] } });
      }
    }
  }

  function girts(m, n, y0, y1) {
    m.box(`${n}_n`, [IN0, y0, -16], [IN1, y1, -14], 'hsteel', { split: 'x' });
    m.box(`${n}_s`, [IN0, y0, 30], [IN1, y1, 32], 'hsteel', { split: 'x' });
    m.box(`${n}_w`, [-16, y0, IN0], [-14, y1, IN1], 'hsteel', { split: 'z' });
    m.box(`${n}_e`, [30, y0, IN0], [32, y1, IN1], 'hsteel', { split: 'z' });
  }

  function legs(m, y0, y1) {
    for (const x of LEG) for (const z of LEG) {
      m.box(`leg_${x}_${z}`, [x, y0, z], [x + 3, y1, z + 3], 'hsteel', { split: 'y' });
      m.box(`leg_${x}_${z}_flange`, [x - 0.3, y0, z + 1.2], [x + 3.3, y1, z + 1.8], 'hsteel', { split: 'y' });
    }
  }

  function ladder(m, y0, y1) {
    for (const z of [2, 6]) m.box(`ladder_stile_${z}`, [-13.6, y0, z], [-13, y1, z + 0.6], 'st', { split: 'y' });
    for (let y = y0 + 1.5; y < y1; y += 3) m.box(`ladder_rung_${y}`, [-13.5, y, 2.6], [-13.1, y + 0.4, 6], 'st', { split: '' });
  }

  function section() {
    const m = mk('shaft/steel');
    legs(m, -16, 32);
    girts(m, 'girt_foot', -16, -14);
    girts(m, 'girt_mid', 8, 10);
    for (const f of ['n', 's', 'w', 'e']) { cross(m, `brace_${f}_lo`, f, -3); cross(m, `brace_${f}_hi`, f, 21); }
    ladder(m, -16, 32);
    return m;
  }

  function top() {
    const m = mk('shaft/steel');
    const DECK = 4;
    legs(m, -16, DECK);
    girts(m, 'girt_foot', -16, -14);
    girts(m, 'girt_deck', DECK - 3, DECK);
    for (const f of ['n', 's', 'w', 'e']) cross(m, `brace_${f}_lo`, f, -6.5);
    ladder(m, -16, DECK + 0.5);
    // deck plate round the sheave slot (x 4..12, z -8..24)
    m.box('deck_w', [-16, DECK, -16], [4, DECK + 0.6, 32], 'chequer', { hide: ['down'] });
    m.box('deck_e', [12, DECK, -16], [32, DECK + 0.6, 32], 'chequer', { hide: ['down'] });
    m.box('deck_n', [4, DECK, -16], [12, DECK + 0.6, -8], 'chequer', { hide: ['down'] });
    m.box('deck_s', [4, DECK, 24], [12, DECK + 0.6, 32], 'chequer', { hide: ['down'] });
    // sheave girders along z either side of the slot, plummer blocks on them carrying the axle at y 16
    for (const [n, x0, x1] of [['w', 0.5, 3.5], ['e', 12.5, 15.5]]) {
      m.box(`sheave_girder_${n}`, [x0, DECK + 0.6, -14], [x1, 10, 30], 'hsteel', { split: 'z' });
      m.box(`plummer_${n}_sole`, [x0 - 0.5, 10, 4], [x1 + 0.5, 11, 12], 'ci', { split: '' });
      m.box(`plummer_${n}_body`, [x0, 11, 5], [x1, 15, 11], 'ci', { split: '' });
      m.box(`plummer_${n}_cap`, [x0 + 0.3, 15, 5.5], [x1 - 0.3, 17.4, 10.5], 'gm', { split: '' });
      for (const z of [5.4, 10]) m.box(`plummer_${n}_bolt_${z}`, [x0 + 1.2, 17.4, z], [x0 + 1.8, 18, z + 0.6], 'st', { split: '' });
    }
    // handrails round the deck
    for (const x of [-15.6, 31]) for (const z of [-15.6, 8, 31]) {
      m.box(`rail_post_${x}_${z}`, [x, DECK + 0.6, z], [x + 0.6, DECK + 11, z + 0.6], 'st', { split: '' });
    }
    for (const y of [DECK + 6, DECK + 10.4]) {
      m.box(`rail_w_${y}`, [-15.6, y, -15.6], [-15, y + 0.6, 31.6], 'st', { split: 'z' });
      m.box(`rail_e_${y}`, [31, y, -15.6], [31.6, y + 0.6, 31.6], 'st', { split: 'z' });
      m.box(`rail_n_${y}`, [-15.6, y, -15.6], [31.6, y + 0.6, -15], 'st', { split: 'x' });
      m.box(`rail_s_${y}`, [-15.6, y, 31], [31.6, y + 0.6, 31.6], 'st', { split: 'x' });
    }
    // lifting beam over the sheave on two A posts
    for (const x of [-12, 26]) {
      m.box(`gantry_post_${x}`, [x, DECK + 0.6, 7], [x + 2, 30, 9], 'hsteel', { split: 'y' });
      for (const a of [22.5, -22.5]) {
        // pivoted at the top so the pair splays out to the deck as an A
        m.box(`gantry_strut_${x}_${a}`, [x + 0.4, DECK - 1.2, 7.4], [x + 1.6, 26, 8.6], 'hsteel',
          { split: '', rot: { axis: 'x', angle: a, origin: [x + 1, 26, 8] } });
      }
    }
    m.box('gantry_beam', [-13, 30, 6.5], [29, 32, 9.5], 'hsteel', { split: 'x' });
    m.box('gantry_trolley', [6, 28.4, 6.8], [10, 30, 9.2], 'ci', { split: '' });
    m.box('gantry_hook', [7.6, 26.6, 7.6], [8.4, 28.4, 8.4], 'st', { split: '' });
    return m;
  }

  // Headgear sheave, turned by HoistRenderer about x through (y 16, z 8): grooved rim, eight spokes, hub, axle.
  function sheave() {
    const m = mk('material/cast_iron');
    const C = [16, 8], R = 15;
    m.ring('rim', 'x', C, R, 1.6, 6, 10, 'ci');
    m.ring('groove', 'x', C, R - 0.4, 0.6, 5.6, 6, 'bright');
    m.ring('groove_e', 'x', C, R - 0.4, 0.6, 10, 10.4, 'bright');
    for (const a of [0, 45, -45]) {
      m.box(`spoke_y_${a}`, [7.2, C[0] - R + 1.4, C[1] - 0.6], [8.8, C[0] + R - 1.4, C[1] + 0.6], 'ci',
        { split: '', rot: a ? { axis: 'x', angle: a, origin: [8, C[0], C[1]] } : undefined });
    }
    m.box('spoke_z', [7.2, C[0] - 0.6, C[1] - R + 1.4], [8.8, C[0] + 0.6, C[1] + R - 1.4], 'ci', { split: '' });
    m.prism('hub', 'x', C, 3, 4.5, 11.5, 'ci', { split: '', ends: ['east', 'west'] });
    m.prism('axle', 'x', C, 1.2, 0.5, 15.5, 'bright', { split: '', ends: ['east', 'west'] });
    return m;
  }

  // ------------------------------------------------------------------ cage
  function cage(open) {
    const m = mk('shaft/sheet');
    m.box('floor', [0, -16, 0], [32, -14.5, 32], 'chequer');
    for (const x of [9.5, 21.5]) m.box(`tub_rail_${x}`, [x, -14.5, 0.5], [x + 1, -13.8, 31.5], 'st', { split: 'z' });
    m.box('roof', [0, 28.5, 0], [32, 30, 32], 'sheet');
    for (const x of [0, 30]) for (const z of [0, 30]) m.box(`post_${x}_${z}`, [x, -14.5, z], [x + 2, 28.5, z + 2], 'hsteel', { split: 'y' });
    // sides and back: sheet to waist height, mesh above
    m.box('side_w_sheet', [0, -14.5, 2], [0.5, 2, 30], 'sheet', { split: 'yz' });
    m.box('side_e_sheet', [31.5, -14.5, 2], [32, 2, 30], 'sheet', { split: 'yz' });
    m.box('back_sheet', [2, -14.5, 31.5], [30, 2, 32], 'sheet', { split: 'xy' });
    m.box('side_w_mesh', [0.1, 2, 2], [0.4, 28.5, 30], 'mesh', { split: 'yz' });
    m.box('side_e_mesh', [31.6, 2, 2], [31.9, 28.5, 30], 'mesh', { split: 'yz' });
    m.box('back_mesh', [2, 2, 31.6], [30, 28.5, 31.9], 'mesh', { split: 'xy' });
    for (const [n, x0, x1] of [['w', 0.5, 2], ['e', 30, 31.5]]) m.box(`grab_rail_${n}`, [x0, 8, 3], [x1, 8.8, 29], 'bright', { split: 'z' });
    // guide shoes on both sides, top and bottom, running on the shaft guides
    for (const [n, x0, x1] of [['w', -1.5, 0], ['e', 32, 33.5]]) for (const y of [-14, 24]) {
      m.box(`shoe_${n}_${y}`, [x0, y, 13], [x1, y + 4, 19], 'ci', { split: '' });
      m.box(`shoe_${n}_${y}_jaw`, [x0 + (n === 'w' ? -0.5 : 1.5), y + 0.5, 13.5], [x0 + (n === 'w' ? 0 : 2), y + 3.5, 14.5], 'ci', { split: '' });
    }
    // crosshead with the capel over the centre
    m.box('crosshead', [4, 30, 14], [28, 31.4, 18], 'hsteel', { split: 'x' });
    m.box('capel', [14.5, 31.4, 14.5], [17.5, 32, 17.5], 'ci', { split: '' });
    for (const x of [4, 26]) m.box(`hanger_${x}`, [x, 28.5, 14.5], [x + 2, 30, 17.5], 'hsteel', { split: '' });
    // the gate: down across the opening, or raised into the roof frame
    const g0 = open ? 14 : -14.5, g1 = open ? 28.5 : 0;
    m.box('gate', [2, g0, 0.1], [30, g1, 0.4], 'gate', { split: 'xy' });
    for (const x of [2, 29]) m.box(`gate_runner_${x}`, [x, -14.5, 0], [x + 1, 28.5, 0.6], 'st', { split: 'y' });
    m.box('gate_handle', [14, g0 + 6, -0.6], [18, g0 + 7, 0.1], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    headframe: { model: section },
    headframe_top: { model: top },
    'headframe/sheave': { model: sheave, java: 'headframe/partial/sheave' },
    mine_cage: { model: () => cage(false) },
    mine_cage_open: { model: () => cage(true) },
  };

  window.HFR = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, top().merge(sheave(), 0, 0, 0, 'sh_'), 'headframe_preview');
    },
  };
})();
