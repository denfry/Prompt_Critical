// Mechanical flotation cell at real scale (1 block = 1 m, 16 px = 1 m): one 6.3 m3 chamber of a Mekhanobr-type
// self-aerating machine, 1.9 m square and 1.5 m deep, with its impeller mechanism hung from a bridge (standpipe,
// bearing housing, V-belt drive from the motor on the bridge under a guard), the froth lip and the froth launder
// along the north side with the paddle froth scraper over it, the feed pipe into the west side, the chamber box
// with the sand gate and its handwheel on the east side, and the air pipe with its valve into the standpipe.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   FCR.build(repo); /* wait */ FCR.save(repo)
//   FCR.buildPartial(repo, p); FCR.savePartial(repo, p)    for impeller, paddles, froth
//   FCR.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x 0..1, y 0..2, z 0..1.
// Connections (px): pulp feed in on the west face of (0, 0, 1) at (0, 14, 24); tailings out on the east face of
// (1, 0, 1) at (32, 6, 24); froth concentrate out on the north face of (0, 0, 0) at (16, 10, 0) from the launder
// spout (between cells (0, 0, 0) and (1, 0, 0)).
// Partials (flotation_cell_real/partial/*):
// - impeller: shaft, impeller and stator ring at the bottom and the driven pulley on top; turns about y through
//   (x 16, z 18), drawn at y -16 (shaft y -16..32 in the partial, y 0..48 in the model), so draw it at +16 y.
// - paddles: froth scraper shaft and paddles; turns about x through (y 27, z 7), drawn in place.
// - froth: the froth bed over the pulp (y 21..24); shown while the cell runs.
(function () {
  const TX = [1, 31], TZ = [8, 31], TY = [0, 24], PULP = 21;   // chamber (outer), pulp level
  const SH = [16, 18];                                          // impeller shaft [x, z]
  const MAT = Object.assign({}, PCVV.MAT, {
    cell: { tex: 'material/paint_green', P: 16 }, frame: { tex: 'plant/structure', P: 16 }, warn: { tex: 'plant/yellow', P: 16 },
    pulp: { tex: 'm3met/pulp', P: 16 }, froth: { tex: 'm3met/froth', P: 16 }, air: { tex: 'plant/pipe_air', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/paint_green' });

  function chamber(m) {
    m.box('floor', [TX[0], 0, TZ[0]], [TX[1], 1, TZ[1]], 'cell', { split: '' });
    m.box('wall_s', [TX[0], 1, TZ[1] - 0.8], [TX[1], TY[1], TZ[1]], 'cell', { split: '' });
    m.box('wall_w', [TX[0], 1, TZ[0]], [TX[0] + 0.8, TY[1], TZ[1] - 0.8], 'cell', { split: '' });
    m.box('wall_e', [TX[1] - 0.8, 1, TZ[0]], [TX[1], TY[1], TZ[1] - 0.8], 'cell', { split: '' });
    // the north wall stops at the froth lip, lower than the others
    m.box('wall_n', [TX[0] + 0.8, 1, TZ[0]], [TX[1] - 0.8, PULP + 2.4, TZ[0] + 0.8], 'cell', { split: '' });
    m.box('lip', [TX[0] + 0.8, PULP + 2.4, TZ[0] - 0.4], [TX[1] - 0.8, PULP + 2.8, TZ[0] + 0.8], 'st', { split: '' });
    for (const y of [8, 16]) for (const [n, f, t] of [['s', [TX[0], y, TZ[1]], [TX[1], y + 0.8, TZ[1] + 0.4]], ['w', [TX[0] - 0.4, y, TZ[0]], [TX[0], y + 0.8, TZ[1]]], ['e', [TX[1], y, TZ[0]], [TX[1] + 0.4, y + 0.8, TZ[1]]]]) m.box(`stiff_${n}_${y}`, f, t, 'cell', { split: '' });
    m.box('pulp', [TX[0] + 0.8, PULP - 0.6, TZ[0] + 0.8], [TX[1] - 0.8, PULP, TZ[1] - 0.8], 'pulp', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.decal('plate', [TX[0] - 0.6, 10, 12], [TX[0] - 0.5, 13, 18], 'cell', 'west', 'pl.maker');
  }

  // Froth launder along the north side, sloping to the spout at the middle of the front.
  function launder(m) {
    m.box('launder_floor', [TX[0], 12, 1], [TX[1], 13, TZ[0]], 'cell', { split: '' });
    m.box('launder_front', [TX[0], 13, 0.4], [TX[1], PULP + 1, 1.2], 'cell', { split: '' });
    for (const x of [TX[0], TX[1] - 0.8]) m.box(`launder_end_${x}`, [x, 13, 1.2], [x + 0.8, PULP + 1, TZ[0]], 'cell', { split: '' });
    m.box('launder_froth', [TX[0] + 0.8, 13, 1.2], [TX[1] - 0.8, 14, TZ[0]], 'froth', { split: '' });
    m.box('spout', [12, 7, 0], [20, 12, 4], 'cell', { split: '' });
    m.box('spout_froth', [12.8, 11, 0], [19.2, 11.6, 3.2], 'froth', { split: '' });
    for (const x of [TX[0], TX[1] - 1.4]) m.box(`launder_leg_${x}`, [x, 0, 1], [x + 1.4, 12, 2.4], 'frame', { split: '' });
    // scraper bearings on the end walls
    for (const x of [TX[0] - 1, TX[1]]) m.box(`scraper_bearing_${x}`, [x, 25.4, 5.4], [x + 1, 28.6, 8.6], 'ci', { split: '' });
  }

  // Bridge across the chamber carrying the impeller mechanism and the motor.
  function mechanism(m) {
    for (const z of [12, 25]) m.box(`bridge_${z}`, [TX[0] - 0.4, TY[1], z], [TX[1] + 0.4, TY[1] + 2, z + 2], 'frame', { split: '' });
    m.box('deck', [8, TY[1] + 2, 12], [31, TY[1] + 2.6, 27], 'frame', { split: '' });
    m.cylY('standpipe', SH, 3.2, 4, TY[1] + 2, 'cell');
    m.cylY('bearing_housing', SH, 2.4, TY[1] + 2.6, 36, 'cell', { cap: 'vver/cap' });
    m.ringY('housing_flange', SH, 3.2, 0.8, TY[1] + 2.6, TY[1] + 3.6, 'ci');
    // motor on the deck, its pulley and the belt guard joining the two pulleys
    m.prism('motor', 'z', [26, 31], 3.6, 15, 25, 'cell', { split: '', ends: ['north', 'south'] });
    m.box('motor_feet', [22.6, TY[1] + 2.6, 15.6], [29.4, 28, 24.4], 'cell', { split: '' });
    m.box('motor_box', [24.6, 34, 19], [27.4, 36.4, 22], 'dark', { split: '' });
    m.cylY('motor_pulley', [26, 20], 2, 36.4, 39, 'ci');
    m.box('belt_guard_top', [12.6, 39.6, 14.6], [29.4, 40.2, 23.4], 'warn', { split: '' });
    m.box('belt_guard_n', [12.6, 37, 14.6], [29.4, 39.6, 15.2], 'warn', { split: '' });
    m.box('belt_guard_s', [12.6, 37, 22.8], [29.4, 39.6, 23.4], 'warn', { split: '' });
    // air pipe with its valve into the standpipe
    m.prism('air_pipe', 'x', [28, SH[1]], 0.9, 2, SH[0] - 2.4, 'air', { split: '' });
    m.cylY('air_drop', [2.4, SH[1]], 0.9, TY[1] + 2.6, 28.9, 'air');
    m.box('air_valve', [5, 26.6, 16.6], [7.4, 29.4, 19.4], 'gm', { split: '' });
    m.wheelY('air_wheel', 6.2, 31, 18, 1.2, 'red');
  }

  function feedAndTails(m) {
    // feed pipe into the west side below the pulp level
    m.prism('feed', 'x', [14, 24], 1.6, 0, TX[0], 'dark', { split: '' });
    m.disc('feed_flange', 'x', [14, 24], 2.6, 0.1, 0.8, 'ci', 'vver/flange', 'west');
    // chamber box with the sand gate on the east side, the tails pipe out at the bottom
    m.box('chamber_box', [TX[1], 2, 18], [32, 22, 30], 'cell', { split: '' });
    m.box('gate_spindle', [28.6, 22, 23.4], [29.4, 27, 24.2], 'bright', { split: '' });
    m.wheelY('gate_wheel', 29, 27.4, 23.8, 2, 'red');
    m.prism('tails', 'x', [6, 24], 1.8, 31.2, 31.9, 'ci', { split: '', ends: ['east'] });
    for (const z of [9, 29]) for (const x of [TX[0], TX[1] - 1.6]) m.box(`foot_${x}_${z}`, [x, 0, z], [x + 1.6, 1, z + 1.6], 'frame', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function impeller(m) {
    m.cylY('shaft', SH, 1, 3, 41.4, 'bright');
    m.cylY('impeller', SH, 4.4, 2, 4.2, 'dark', { cap: 'vver/cap' });
    for (const a of [0, 45, 90, 135]) m.radial(`vane_${a}`, SH, 1.2, 4.6, a, 0.6, 4.2, 6, 'dark');
    m.cylY('pulley', SH, 3.2, 37, 39.6, 'ci', { cap: 'vver/cap' });
    m.ringY('belt', SH, 3.3, 0.5, 37.8, 38.8, 'dark');
  }
  function paddles(m) {
    m.prism('scraper_shaft', 'x', [27, 7], 0.6, TX[0] - 1, TX[1] + 1, 'bright', { split: '' });
    for (const [a, d] of [[0, 'y'], [0, 'z'], [45, 'y'], [45, 'z']]) {
      const f = d === 'y' ? [TX[0] + 1.4, 23.6, 6.7] : [TX[0] + 1.4, 26.7, 3.6];
      const t = d === 'y' ? [TX[1] - 1.4, 30.4, 7.3] : [TX[1] - 1.4, 27.3, 10.4];
      m.box(`paddle_${a}_${d}`, f, t, 'frame', { split: '', rot: a ? { axis: 'x', angle: a, origin: [16, 27, 7] } : undefined });
    }
  }
  function froth(m) {
    m.box('froth', [TX[0] + 0.8, PULP, TZ[0] + 0.8], [TX[1] - 0.8, PULP + 3, TZ[1] - 0.8], 'froth', { split: '', hide: ['down'] });
    m.box('froth_over', [TX[0] + 0.8, PULP + 2.8, TZ[0] - 0.4], [TX[1] - 0.8, PULP + 3.2, TZ[0] + 0.8], 'froth', { split: '' });
  }

  const PART = {
    impeller: { draw: impeller, shift: [0, -16, 0], note: 'turns about y through (16, 18); draw at +16 y' },
    paddles: { draw: paddles, shift: [0, 0, 0], note: 'turns about x through (y 27, z 7)' },
    froth: { draw: froth, shift: [0, 0, 0], note: 'shown while running' },
  };
  function model() { const m = mk(); chamber(m); launder(m); mechanism(m); feedAndTails(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'flotation_cell_real';
  window.FCR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'ore_processing'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'ore_processing'),
    preview: (root) => { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
