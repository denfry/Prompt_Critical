// Wet drum magnetic separator for magnetite at real scale (1 block = 1 m, 16 px = 1 m), a PBM-90/250 type unit:
// a 0.9 m by 2.4 m non-magnetic drum over a counter-current tank, the fixed magnet system inside it, the feed box
// over the north side of the tank, the concentrate washed off the top of the drum into the launder on the south
// side, the tailings out of the tank bottom through the spigot at the east end, the wash water spray pipe along
// the drum, and the worm reducer at the west end driven from a Create shaft in place of the motor.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   MSR.build(repo); /* wait */ MSR.save(repo)
//   MSR.buildPartial(repo, 'drum'); MSR.savePartial(repo, 'drum')
//   MSR.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x -2..1, y 0..1, z 0..1. The drum axis runs along x
// through (y 24, z 8). Connections (px):
// - Create shaft in on the west face of (-2, 1, 0) at (-32, 24, 8);
// - ore feed in on the top face of (0, 1, 0) at (8, 32, 2), into the feed box;
// - concentrate out on the south face of (0, 1, 1) at (8, 18, 32), off the launder lip;
// - tailings out on the east face of (1, 0, 0) at (32, 4, 8);
// - wash water in on the east face of (1, 1, 1) at (32, 30, 20), GOST green.
// Partial `drum` (magnetic_separator_real/partial/drum): drum shell, end discs and shaft; turns about x through
// (y 24, z 8). It is stored shifted +8 x (shell x -11..27), so draw it at -8 x.
(function () {
  const AX = [24, 8], RD = 7.2, DX = [-19, 19];           // drum axis [y, z], radius, shell x span
  const TX = [-21, 21], TY = [2, 22], TZ = [-0.0, 18];    // tank
  const MAT = Object.assign({}, PCVV.MAT, {
    sep: { tex: 'plant/blue', P: 16 }, frame: { tex: 'plant/structure', P: 16 }, warn: { tex: 'plant/yellow', P: 16 },
    pulp: { tex: 'm3met/pulp', P: 16 }, conc: { tex: 'vver/concrete', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'plant/blue' });

  function frame(m) {
    for (const x of [-22, 20.4]) for (const z of [0.2, 16.4]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1.6, TY[0] + 2, z + 1.6], 'frame', { split: '' });
    m.box('base_n', [-22, 0, 0.2], [22, 1.2, 1.8], 'frame', { split: 'x' });
    m.box('base_s', [-22, 0, 16.4], [22, 1.2, 18], 'frame', { split: 'x' });
  }

  function tank(m) {
    // counter-current tank: sloping sides stepped down to the tailings trough along the bottom
    m.box('tank_floor', [TX[0], TY[0], 4], [TX[1], TY[0] + 1, 14], 'sep', { split: 'x' });
    m.box('tank_step_n', [TX[0], TY[0] + 1, 1], [TX[1], TY[0] + 6, 4], 'sep', { split: 'x' });
    m.box('tank_step_s', [TX[0], TY[0] + 1, 14], [TX[1], TY[0] + 6, 17], 'sep', { split: 'x' });
    m.box('tank_wall_n', [TX[0], TY[0] + 6, 0.4], [TX[1], TY[1], 1.4], 'sep', { split: 'x' });
    m.box('tank_wall_s', [TX[0], TY[0] + 6, 16.6], [TX[1], AX[0] - 4, 17.6], 'sep', { split: 'x' });
    for (const [n, x0, x1] of [['w', TX[0], TX[0] + 1], ['e', TX[1] - 1, TX[1]]]) m.box(`tank_end_${n}`, [x0, TY[0] + 1, 0.4], [x1, TY[1], 17.6], 'sep', { split: '' });
    m.box('pulp', [TX[0] + 1, TY[0] + 1, 1.4], [TX[1] - 1, AX[0] - RD + 2.4, 16.6], 'pulp', { split: 'x', hide: ['down', 'north', 'south', 'east', 'west'] });
    for (const x of [-12, 0, 12]) m.box(`rib_${x}`, [x - 0.4, TY[0] + 6, 0], [x + 0.4, TY[1], 0.4], 'frame', { split: '' });
    // tailings spigot through the east end, a pinch valve on it
    m.prism('spigot', 'x', [4, 8], 1.4, TX[1], 32, 'sep', { split: '' });
    m.box('pinch_valve', [24, 2, 6], [27, 6.4, 10], 'dark', { split: '' });
    m.disc('spigot_flange', 'x', [4, 8], 2.2, 31.2, 31.9, 'ci', 'vver/flange', 'east');
    m.decal('plate', [-6, 10, 0.3], [2, 14, 0.4], 'sep', 'north', 'pl.maker');
  }

  // Feed box over the north side, its splash plate guiding the pulp under the drum.
  function feed(m) {
    m.box('feed_n', [-16, AX[0] - 2, 0], [16, 32, 0.8], 'sep', { split: 'x' });
    m.box('feed_floor', [-16, AX[0] - 2, 0.8], [16, AX[0] - 1, 3], 'sep', { split: 'x' });
    for (const x of [-16, 15.2]) m.box(`feed_end_${x}`, [x, AX[0] - 1, 0.8], [x + 0.8, 32, 3], 'sep', { split: '' });
    m.box('feed_rim', [-16.4, 31.4, 0], [16.4, 32, 1.2], 'ci', { split: 'x' });
    m.box('splash', [-15, TY[1] - 6, 1.4], [15, AX[0] - 2, 2], 'st', { split: 'x' });
  }

  // Concentrate launder on the south side: the scraper lip at the drum top, the launder sloping to its spout.
  function launder(m) {
    m.box('scraper', [DX[0] + 1, AX[0] + RD - 2.2, AX[1] + RD - 0.4], [DX[1] - 1, AX[0] + RD - 1.4, AX[1] + RD + 3], 'st', { split: 'x' });
    m.box('launder_floor', [TX[0], 17, 17.6], [TX[1], 18, 30], 'sep', { split: 'x' });
    m.box('launder_back', [TX[0], 18, 29.4], [TX[1], 23, 30], 'sep', { split: 'x' });
    for (const x of [TX[0], TX[1] - 0.6]) m.box(`launder_end_${x}`, [x, 18, 17.6], [x + 0.6, 23, 29.4], 'sep', { split: '' });
    m.box('launder_conc', [TX[0] + 0.6, 18, 17.6], [TX[1] - 0.6, 18.6, 29.4], 'dark', { split: 'x' });
    m.box('spout', [4, 15, 29.4], [12, 18, 32], 'sep', { split: '' });
    for (const x of [-20, 18.4]) m.box(`launder_strut_${x}`, [x, 0, 27], [x + 1.6, 17, 28.6], 'frame', { split: '' });
  }

  // Wash water spray pipe over the drum from the east face, and the magnet system lever on the west end cover.
  function water(m) {
    m.prism('spray', 'x', [30, 13], 0.8, DX[0] + 2, 30, 'gw', { split: 'x' });
    m.prism('spray_feed', 'z', [30, 30], 0.8, 13, 20, 'gw', { split: '' });
    m.disc('water_flange', 'x', [30, 20], 1.6, 31.2, 31.9, 'ci', 'vver/flange', 'east');
    m.prism('water_in', 'x', [30, 20], 0.8, 30, 31.2, 'gw', { split: '' });
    m.box('water_valve', [26, 28.4, 18.6], [28, 31.6, 21.4], 'gm', { split: '' });
  }

  // Bearings on the tank ends, the worm reducer at the west end with the input shaft stub, guards.
  function drive(m) {
    for (const [n, x0, x1] of [['w', -23, -20.6], ['e', 20.6, 23]]) {
      m.box(`bearing_stand_${n}`, [x0, TY[1] - 2, AX[1] - 3], [x1, AX[0] - 2, AX[1] + 3], 'ci', { split: '' });
      m.prism(`bearing_${n}`, 'x', AX, 2.6, x0, x1, 'ci', { split: '', ends: ['west', 'east'] });
    }
    m.box('magnet_lever', [23, AX[0] - 0.5, AX[1] - 0.5], [24, AX[0] + 5.6, AX[1] + 0.5], 'red', { split: '' });
    m.box('magnet_quadrant', [23, AX[0] + 2, AX[1] - 3], [23.4, AX[0] + 5.4, AX[1] + 3], 'ci', { split: '' });
    // worm reducer: the input from the Create shaft on the west face, wheel case up to the drum shaft
    m.box('reducer', [-31, TY[1] - 4, AX[1] - 5], [-24, AX[0] + 5, AX[1] + 5], 'sep', { split: '' });
    m.box('reducer_fins', [-31.4, TY[1] - 2, AX[1] - 4], [-31, AX[0] + 3, AX[1] + 4], 'sep', { split: '' });
    m.box('reducer_stand', [-31, 0, AX[1] - 5], [-24, TY[1] - 4, AX[1] + 5], 'frame', { split: '' });
    m.prism('input_boss', 'x', AX, 2.2, -32, -31, 'ci', { split: '', ends: ['west'] });
    m.box('drum_coupling_guard', [-24, AX[0] - 3.6, AX[1] - 3.6], [-23, AX[0] + 3.6, AX[1] + 3.6], 'warn', { split: '' });
  }

  // ------------------------------------------------------------------ partial: the drum
  function drum(m) {
    m.prism('shell', 'x', AX, RD, DX[0], DX[1], 'st', { split: '' });
    for (const [n, x0, x1, d] of [['w', DX[0] - 0.8, DX[0], 'west'], ['e', DX[1], DX[1] + 0.8, 'east']]) m.disc(`end_${n}`, 'x', AX, RD + 0.4, x0, x1, 'sep', 'vver/flange', d);
    for (const x of [-10, 0, 10]) m.ring(`band_${x}`, 'x', AX, RD + 0.15, 0.3, x - 0.4, x + 0.4, 'dark');
    m.prism('shaft', 'x', AX, 1.2, -24, 23, 'bright', { split: '' });
  }

  const PART = { drum: { draw: drum, shift: [8, 0, 0], note: 'turns about x through (y 24, z 8); draw at -8 x' } };
  function model() { const m = mk(); frame(m); tank(m); feed(m); launder(m); water(m); drive(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'magnetic_separator_real';
  window.MSR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'ore_processing'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'ore_processing'),
    preview: (root) => { const m = model(); const s = mk(); drum(s); m.merge(s, 0, 0, 0, 'drum_'); PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
