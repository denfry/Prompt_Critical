// Moulding line of the I0 foundry, hand-authored for Blockbench (replaces the spec_visual.py placeholders):
// the sand mould in its flasks (empty, poured, cooling), the jolt-squeeze moulding machine and the shakeout
// grate, plus the panel boards of the two machines.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/foundry/molding.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); MLD.paint('<repo>'); MLD.paintGui('<repo>')
//   build + save each model in turn (MLD.build / MLD.save, ids in MLD.ids)
//
// Sand mould: 1x1x1. Cast-iron drag and cope with ribs, lugs with guide pins, handles, two clamps over the
// joint, a weight on the cope; on top the rammed sand with the pouring basin over the sprue (MachineFx wisp at
// x 10.5, z 10.5) and two risers (flame at x 4, z 11). Poured: metal glowing in the basin and risers; cooling:
// crusted dark metal.
// Moulding machine: 2x1x1 (x 0..32), Create shaft in from the west into the drive box. Base with the jolt
// cylinder, the table with pattern plate and flask (the table partial, which the renderer jolts), a swing
// yoke on two posts carrying the squeeze head, and the sand hopper with its gate and chute on the east.
// Shakeout grate: 2x1x2 (x 0..32, z 0..32). Base frame on four coil springs; the deck with its grate bars
// (the new deck partial, jolted by the renderer) and the unbalanced-mass vibrator on the west, driven by the
// Create shaft through the lower west cell.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, rivet, writePng } = PCART;
  const T = {};

  // Flask iron: dark cast iron dusted with moulding sand.
  T['sand_mold/flask'] = () => {
    const im = img(32, 32), r = grain(im, [58, 56, 54], 2101, 5, 6, 8);
    for (let k = 0; k < 24; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [96, 88, 74]);
    return im;
  };
  // Green moulding sand, rammed: dark brown-grey with bentonite and coal-dust pepper.
  T['sand_mold/sand'] = () => {
    const im = img(32, 32), r = grain(im, [96, 84, 70], 2111, 8, 6, 4);
    for (let k = 0; k < 90; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), r() < 0.5 ? [44, 40, 38] : [150, 132, 104]);
    return im;
  };
  // Metal: glowing (poured) and crusted (cooling).
  T['sand_mold/melt'] = () => {
    const im = img(16, 16), r = rng(2121);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([255, 226, 140], [230, 110, 30], r() * 0.5), (r() - 0.5) * 16));
    return im;
  };
  T['sand_mold/crust'] = () => {
    const im = img(16, 16), r = grain(im, [52, 48, 46], 2131, 6, 6, 4);
    for (let k = 0; k < 10; k++) put(im, Math.floor(r() * 16), Math.floor(r() * 16), [150, 60, 24]);
    return im;
  };
  // Machine paint: foundry green-grey, scuffed and sanded.
  T['molding_station/paint'] = () => {
    const im = img(32, 32), r = grain(im, [74, 96, 84], 2141, 3, 5, 8);
    for (let k = 0; k < 14; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [104, 110, 96]);
    return im;
  };
  // Pattern plate: wooden pattern painted red for cast iron, on a machined plate.
  T['molding_station/pattern'] = () => {
    const im = img(32, 32), r = grain(im, [150, 152, 154], 2151, 3, 3, 8);
    rect(im, 6, 8, 20, 16, [168, 40, 34]); rect(im, 6, 8, 20, 1, [210, 80, 70]); rect(im, 6, 23, 20, 1, [110, 24, 20]);
    rect(im, 13, 12, 6, 8, [190, 56, 46]);
    return im;
  };
  // Coil spring, side view: bright wire turns.
  T['shakeout_grate/spring'] = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, (y + Math.floor(x / 4)) % 4 < 2 ? [150, 152, 156] : [40, 40, 42]);
    return im;
  };

  const MAT = {
    fl: { tex: 'sand_mold/flask', P: 16 }, sand: { tex: 'sand_mold/sand', P: 16 }, melt: { tex: 'sand_mold/melt', P: 16 },
    crust: { tex: 'sand_mold/crust', P: 16 }, mp: { tex: 'molding_station/paint', P: 16 },
    pat: { tex: 'molding_station/pattern', P: 16 }, spring: { tex: 'shakeout_grate/spring', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Flask pair with sand top at y 12 between x0..x1, z0..z1 (shared by the mould and the machine's flask).
  function mold(state) {
    const m = mk('sand_mold/sand');
    m.box('drag', [1, 0, 1], [15, 6, 15], 'fl', { split: '', face: { up: 'sand' } });
    m.box('cope', [1, 6.2, 1], [15, 12, 15], 'fl', { split: '', face: { up: 'sand' } });
    for (const y of [2.6, 9]) {
      m.box(`rib_n_${y}`, [1.4, y, 0.6], [14.6, y + 0.8, 1], 'fl', { split: '' });
      m.box(`rib_s_${y}`, [1.4, y, 15], [14.6, y + 0.8, 15.4], 'fl', { split: '' });
    }
    // lugs with guide pins, handles, clamps over the joint, weight on the cope
    for (const [s, x0, x1] of [['w', 0, 1], ['e', 15, 16]]) {
      m.box(`lug_${s}`, [x0, 4.6, 6.4], [x1, 7.6, 9.6], 'fl', { split: '' });
      m.box(`pin_${s}`, [x0 + 0.3, 3.6, 7.6], [x1 - 0.3, 8.6, 8.4], 'bright', { split: '' });
    }
    for (const [s, z0, z1] of [['n', 0.2, 0.6], ['s', 15.4, 15.8]]) {
      m.box(`handle_${s}`, [6, 9.4, z0], [10, 10.2, z1], 'st', { split: '' });
      m.box(`clamp_${s}`, [11.6, 4.6, z0 - 0.2], [12.6, 7.8, z1 + 0.2], 'st', { split: '' });
    }
    m.box('weight', [1.6, 12, 1.6], [5.6, 13.6, 5.6], 'ci', { split: '' });
    // pouring basin over the sprue and two risers; their tops show the state of the metal
    m.box('basin', [8.6, 12, 8.6], [12.4, 12.8, 12.4], 'sand', { split: '' });
    const top = state === 'poured' ? 'melt' : state === 'cooling' ? 'crust' : 'sand';
    const g = state === 'poured' ? 'glow_' : '';
    m.box(`${g}sprue`, [9.4, 12.5, 9.4], [11.6, 12.9, 11.6], top, { split: '' });
    for (const [n, x, z] of [['riser_a', 3, 10], ['riser_b', 11, 3]]) {
      m.prismY(`${g}${n}`, [x + 1, z + 1], 1.1, 12, 12.4 + (state === 'empty' ? 0 : 0.2), state === 'empty' ? 'sand' : top, { ends: ['up'] });
    }
    // vent holes pricked in the cope
    for (const [x, z] of [[7, 4], [5, 7], [13, 7], [7, 13]]) m.box(`vent_${x}_${z}`, [x, 12, z], [x + 0.4, 12.05, z + 0.4], 'crust', { split: '' });
    return m;
  }

  function moldingStation() {
    const m = mk('molding_station/paint');
    m.box('base', [0.6, 0, 2], [19.4, 4.6, 14], 'mp', { split: 'x' });
    m.prismY('jolt_cylinder', [10, 8], 4.6, 4.6, 6, 'bright', { ends: ['up'] });
    m.box('drive_box', [0, 3, 4.6], [2.6, 12, 11.4], 'ci', { split: '' });
    // swing yoke: two posts, a beam, the squeeze head on its piston
    for (const [n, z0, z1] of [['n', 2, 4.4], ['s', 11.6, 14]]) m.box(`yoke_post_${n}`, [19.4, 0, z0], [22.4, 22, z1], 'mp', { split: 'y' });
    m.box('yoke_beam', [3, 19, 4.4], [22.4, 22, 11.6], 'mp', { split: 'x' });
    m.prismY('squeeze_ram', [10, 8], 1.6, 17.4, 19, 'bright');
    m.box('squeeze_head', [3.6, 15.8, 3.6], [16.4, 17.4, 12.4], 'mp', { split: '' });
    // sand hopper on legs with the gate and the chute to the flask
    for (const [x, z] of [[24, 3], [30, 3], [24, 11], [30, 11]]) m.box(`hopper_leg_${x}_${z}`, [x, 0, z], [x + 1.2, 10, z + 1.2], 'mp', { split: '' });
    m.box('hopper', [22.6, 10, 2], [32, 17.6, 14], 'mp', { split: '', face: { up: 'sand' } });
    m.box('hopper_gate', [21.6, 10.6, 5.6], [22.6, 13.6, 10.4], 'ci', { split: '' });
    m.box('chute', [16, 12.6, 6], [22.6, 13.4, 10], 'ci', { split: '', rot: { axis: 'z', angle: 22.5, origin: [22.6, 13, 8] } });
    // valve box with the jolt and squeeze levers on the front
    m.box('valve_box', [3, 5, 0.6], [9, 9, 2], 'ci', { split: '' });
    m.box('lever_jolt', [4, 9, 1], [4.8, 13, 1.6], 'red', { split: '' });
    m.box('lever_squeeze', [7, 9, 1], [7.8, 13, 1.6], 'green', { split: '' });
    m.box('air_pipe', [0.4, 1, 0.6], [3, 2, 1.6], 'st', { split: '' });
    return m;
  }

  // Table partial: T-slotted table, pattern plate and an empty flask being filled.
  function table() {
    const m = mk('molding_station/paint');
    m.box('table', [1.4, 6, 2.4], [18.6, 8, 13.6], 'mp', { split: '' });
    m.plate('pattern_plate', [3, 8, 4], [17, 8.6, 12], 'mp', 'up', 'molding_station/pattern');
    for (const [n, f, t] of [['n', [3, 8.6, 3.4], [17, 12, 4]], ['s', [3, 8.6, 12], [17, 12, 12.6]],
      ['w', [3, 8.6, 4], [3.6, 12, 12]], ['e', [16.4, 8.6, 4], [17, 12, 12]]]) m.box(`flask_${n}`, f, t, 'fl', { split: '' });
    m.box('sand_fill', [3.6, 8.6, 4], [16.4, 11.2, 12], 'sand', { split: '' });
    return m;
  }

  function shakeout() {
    const m = mk('sand_mold/flask');
    m.box('base', [0, 0, 0], [32, 1.6, 32], 'ci', { split: 'xz' });
    m.box('sand_pile', [6, 1.6, 6], [26, 2.6, 26], 'sand', { split: 'xz' });
    m.box('sand_pile_top', [10, 2.6, 10], [22, 3.2, 22], 'sand', { split: 'xz' });
    for (const [x, z] of [[1.4, 1.4], [27.6, 1.4], [1.4, 27.6], [27.6, 27.6]]) {
      m.prismY(`spring_${x}_${z}`, [x + 1.5, z + 1.5], 1.4, 1.6, 4.6, 'spring', { ends: [] });
    }
    m.box('vibrator_foot', [0, 1.6, 3], [5.4, 3.4, 13], 'ci', { split: '' });
    return m;
  }

  // Deck partial: frame on the springs, grate bars, the vibrator housing and its two guarded unbalanced masses.
  function deck() {
    const m = mk('sand_mold/flask');
    for (const [n, f, t] of [['n', [0, 4.6, 0], [32, 8, 2]], ['s', [0, 4.6, 30], [32, 8, 32]],
      ['w', [0, 4.6, 2], [2, 8, 30]], ['e', [30, 4.6, 2], [32, 8, 30]]]) m.box(`deck_${n}`, f, t, 'ci', { split: 'xz' });
    for (let i = 0; i < 7; i++) m.box(`bar_${i}`, [2, 6, 3 + i * 4], [30, 7.4, 4.6 + i * 4], 'fl', { split: 'x' });
    for (const x of [10, 21]) m.box(`cross_${x}`, [x, 5, 2], [x + 1.2, 6, 30], 'st', { split: 'z' });
    m.box('vibrator', [0.4, 3.4, 4.4], [5, 10.6, 11.6], 'ci', { split: '' });
    for (const z of [2.6, 11.6]) m.box(`unbalance_guard_${z}`, [0.6, 4.4, z], [4.8, 10, z + 1.8], 'red', { split: '' });
    return m;
  }

  const panel = (W, H, seed, plates, rings) => {
    const im = img(W, H), r = rng(seed);
    grain(im, [66, 70, 66], seed + 1, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const [cx, cy, R] of rings) for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, cx + R * Math.cos(t), cy + R * Math.sin(t), [36, 40, 36]); put(im, cx + (R + 1) * Math.cos(t) + 0.5, cy + (R + 1) * Math.sin(t) + 0.5, [118, 126, 114]);
    }
    for (const [x0, y0, x1, y1] of plates) for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
      put(im, x, y, add([54, 58, 54], edge + (r() - 0.5) * 4));
    }
    return im;
  };

  // FoundryPanels.MOLDING (200x150) and SHAKEOUT (200x150).
  function paintGui(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/gui/panel');
    writePng(panel(200, 150, 2161, [[62, 28, 194, 86], [8, 112, 194, 144]], [[32, 48, 26]]), path.join(dir, 'molding_station.png'));
    writePng(panel(200, 150, 2171, [[62, 28, 194, 62], [80, 68, 140, 128]], [[32, 48, 26]]), path.join(dir, 'shakeout_grate.png'));
    return 2;
  }

  const BUILDS = {
    sand_mold: { model: () => mold('empty') },
    sand_mold_poured: { model: () => mold('poured') },
    sand_mold_cooling: { model: () => mold('cooling') },
    molding_station: { model: moldingStation },
    'molding_station/table': { model: table, java: 'molding_station/partial/table' },
    shakeout_grate: { model: shakeout },
    'shakeout_grate/deck': { model: deck, java: 'shakeout_grate/partial/deck' },
  };

  window.MLD = {
    paint, paintGui, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'foundry', id, BUILDS[id].java),
    preview(root) {
      return PCART.realize(root, mold('poured').merge(mold('empty'), 0, 0, 20, 'e_').merge(moldingStation(), 20, 0, 0, 'm_')
        .merge(table(), 20, 0, 0, 't_').merge(shakeout(), 60, 0, 0, 's_').merge(deck(), 60, 0, 0, 'd_'), 'molding_preview');
    },
  };
})();
