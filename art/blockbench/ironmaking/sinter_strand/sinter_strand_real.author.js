// Dwight-Lloyd sinter strand at real scale (1 block = 1 m, 16 units = 1 m): the first machine's size (Dwight &
// Lloyd, 1911: 42 in wide, 26 ft between sprocket centres, 8 m). A steel frame on columns carries the upper and the
// return strand of grate pallets on rails between the tail sprockets (feed, north) and the head sprockets
// (discharge, south); the feed hopper with its drum feeder over the tail, the brick ignition hood with gas burners
// after it, four windboxes under the upper strand with downcomers into the wind main on the east, the spiked
// breaker and the hot-sinter chute at the head, the drive on the west, a walkway with rail along the west side.
// Sizes after Dwight & Lloyd US 882517 and Johannsen, "Sinterung" (1914); bed 0.15–0.3 m, sprocket ⌀1 m.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   SSR.build(repo); SSR.save(repo); SSR.buildPartial(repo, name); SSR.savePartial(repo, name)
//
// Controller block = (0, 0, 0) at the tail (feed) end, the strand running south. Footprint x -1..1, y 0..2,
// z 0..8 (model x -16..32, y 0..48, z 0..144). Pallets x 0..16, upper strand bed top y 26.6, return strand y 5..9.
// Ports: ore mix in at the hopper top y 46 over (0, 2, 0..1); ignition gas in from the east at (32, 31, 32) on
// (1, 1, 2); waste gas out of the wind main on the south face of (1, 0, 8) at (28, 6, 144); sinter out down the
// chute on the south face of (0, 0, 8), lip at (8, 4, 144); Create shaft into the drive on the west face of
// (-1, 0, 8) at (-16, 8, 136), axis x.
// Partials:
//   sprocket_tail — tail sprockets and shaft, turns about x, axis (y 15, z 8); no shift.
//   sprocket_head — head sprockets and shaft, turns about x, axis (y 15, z 136); saved shifted (0, 0, -112): draw it
//                   112 px south (axis then at z 24 in the file).
//   glow          — full-bright ignition: flame under the hood and the burning front past it; saved shifted
//                   (0, 0, -24): draw it 24 px south. Show while the hood is lit.
(function () {
  const ID = 'sinter_strand_real';
  const Z0 = 8, Z1 = 136, YS = 15, RS = 8;                 // sprocket centres and axis height, radius
  const MAT = Object.assign({}, PCVV.MAT, { crust: { tex: 'sand_mold/crust', P: 16 }, coals: { tex: 'smithy/coals', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/steel' });

  function frame(m) {
    for (const z of [8, 40, 72, 104, 136]) for (const x of [-10, 24]) {
      m.box(`column_${x}_${z}`, [x, 0, z - 1], [x + 2, 22, z + 1], 'st', { split: 'y' });
      m.box(`col_foot_${x}_${z}`, [x - 0.6, 0, z - 1.6], [x + 2.6, 0.6, z + 1.6], 'conc', { split: '' });
    }
    for (const x of [-10, 24]) m.box(`girder_${x}`, [x, 20, 2], [x + 2, 23, 142], 'st', { split: 'z' });
    for (const z of [8, 72, 136]) m.box(`cross_beam_${z}`, [-8, 20.6, z - 0.8], [24, 22, z + 0.8], 'st', { split: 'x' });
    // rails of the upper and return strands
    for (const x of [-1, 16]) {
      m.box(`rail_up_${x}`, [x, 22, Z0], [x + 1, 23, Z1], 'bright', { split: 'z' });
      m.box(`rail_ret_${x}`, [x, 9, Z0], [x + 1, 10, Z1], 'bright', { split: 'z' });
    }
    m.box('ret_bearer_w', [-8, 9, Z0], [-1, 10, Z1], 'st', { split: 'z' });
    m.box('ret_bearer_e', [17, 9, Z0], [24, 10, Z1], 'st', { split: 'z' });
  }

  function pallets(m) {
    for (let k = 0, z = Z0; z < Z1; k++, z += 8) {
      // upper strand: grate, side walls, bed (raw mix ahead of the hood, sinter after it)
      m.box(`pal_${k}`, [-0.6, 23, z + 0.2], [16.6, 24, z + 7.8], 'grate', { split: '' });
      m.box(`pal_wall_w_${k}`, [-1, 24, z + 0.2], [0, 27, z + 7.8], 'ci', { split: '' });
      m.box(`pal_wall_e_${k}`, [16, 24, z + 0.2], [17, 27, z + 7.8], 'ci', { split: '' });
      m.box(`bed_${k}`, [0, 24, z], [16, 26.6, z + 8], z < 40 ? 'soot' : 'crust', { split: '', hide: ['down'] });
      // return strand, upside down and empty
      m.box(`ret_${k}`, [-0.6, 6, z + 0.2], [16.6, 7, z + 7.8], 'grate', { split: '' });
      m.box(`ret_wall_w_${k}`, [-1, 4, z + 0.2], [0, 7, z + 7.8], 'ci', { split: '' });
      m.box(`ret_wall_e_${k}`, [16, 4, z + 0.2], [17, 7, z + 7.8], 'ci', { split: '' });
      m.box(`ret_wheel_${k}`, [-1.6, 7, z + 3], [17.6, 9, z + 5], 'ci', { split: '' });
    }
  }

  function feedAndHood(m) {
    // feed hopper on four legs with the drum feeder and the spout onto the pallets
    for (const x of [-6, 20]) for (const z of [4, 22]) m.box(`hopper_leg_${x}_${z}`, [x, 23, z], [x + 1.6, 34, z + 1.6], 'st', { split: 'y' });
    m.box('hopper_ring', [-6, 34, 4], [21.6, 35, 23.6], 'st', { split: '' });
    m.box('hopper', [-4, 35, 6], [20, 46, 22], 'st');
    m.box('hopper_neck', [0, 31, 9], [16, 35, 19], 'st', { split: '' });
    m.box('hopper_mix', [-3.6, 45.8, 6.4], [19.6, 46.2, 21.6], 'soot', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.prism('drum_feeder', 'x', [29.4, 18], 2.2, 0, 16, 'ci', { split: '', ends: ['east', 'west'] });
    m.box('feed_plate', [0, 27.2, 18], [16, 27.8, 24], 'st', { split: '' });
    // ignition hood: brick box on a steel frame over the bed, burners and the gas main on top
    m.box('hood', [-3, 27.4, 26], [19, 35, 40], 'brick');
    m.box('hood_frame_n', [-3.4, 27, 25.6], [19.4, 35.4, 26.4], 'st', { split: '' });
    m.box('hood_frame_s', [-3.4, 27, 39.6], [19.4, 35.4, 40.4], 'st', { split: '' });
    for (const x of [2, 8, 14]) {
      m.cylY(`burner_${x}`, [x, 33], 0.9, 35, 38, 'yel');
      m.box(`burner_valve_${x}`, [x - 0.8, 38, 32.2], [x + 0.8, 39.2, 33.8], 'ci', { split: '' });
    }
    m.prism('gas_main', 'x', [40, 33], 1.2, 0, 32, 'yel', { split: '', ends: ['east'] });
    m.box('gas_drop', [29.6, 31, 31.8], [32, 40, 34.2], 'yel', { split: '' });
    m.decal('hood_plate', [6, 30, 25.5], [10, 32.4, 25.6], 'st', 'north', 'pl.maker');
  }

  function windboxes(m) {
    for (let i = 0; i < 4; i++) {
      const z0 = 32 + i * 24, z1 = z0 + 24;
      m.box(`windbox_${i}`, [-0.6, 15, z0 + 0.4], [16.6, 22, z1 - 0.4], 'ci', { split: '' });
      m.box(`windbox_hopper_${i}`, [3, 11, z0 + 4], [13, 15, z1 - 4], 'ci', { split: '' });
      m.box(`downcomer_${i}`, [13, 11.6, z0 + 9.6], [28, 14.4, z1 - 9.6], 'ci', { split: 'x' });
      m.box(`downcomer_drop_${i}`, [26.6, 9, z0 + 9.6], [29.4, 11.6, z1 - 9.6], 'ci', { split: '' });
      m.box(`damper_${i}`, [20, 14.4, z0 + 11.4], [20.6, 16.4, z0 + 12.6], 'st', { split: '' });
    }
    m.prism('wind_main', 'z', [28, 6], 3.4, 28, 144, 'ci', { split: '', ends: ['south', 'north'] });
    for (const z of [30, 140]) m.box(`main_saddle_${z}`, [24, 0, z - 1], [32, 2.8, z + 1], 'conc', { split: '' });
  }

  function headEnd(m) {
    // breaker under the head, the hot-sinter chute out to the south
    m.box('breaker_frame_w', [-2, 0, 138], [0, 12, 144], 'st', { split: '' });
    m.box('breaker_frame_e', [16, 0, 138], [18, 12, 144], 'st', { split: '' });
    m.prism('breaker', 'x', [8, 141], 2.6, 0, 16, 'ci', { split: '', ends: ['east', 'west'] });
    m.box('chute', [1, 2.6, 139], [15, 4, 144], 'st', { split: '' });
    // drive on the west: gearbox and the reduction gear up to the head sprocket shaft
    m.box('drive_base', [-16, 0, 128], [-6, 1, 144], 'conc', { hide: ['down'] });
    m.box('gearbox', [-14, 1, 130], [-6, 13, 142], 'gw', { split: '' });
    m.prism('drive_boss', 'x', [8, 136], 2, -16, -14, 'ci', { split: '', ends: ['west'] });
    m.disc('gear_cover', 'x', [YS, Z1], 5.4, -6, -4.6, 'gw', 'vver/flange', 'west');
  }

  function walkway(m) {
    m.box('walk_floor', [-16, 22, 4], [-10, 23, 142], 'grate', { split: 'z', hide: ['down'] });
    for (const z of [4, 40, 72, 104, 141.4]) m.box(`walk_post_${z}`, [-16, 23, z], [-15.4, 38, z + 0.6], 'st', { split: '' });
    m.box('walk_rail_top', [-16, 37.4, 4], [-15.4, 38, 142], 'yel', { split: 'z' });
    m.box('walk_rail_knee', [-16, 30, 4], [-15.4, 30.6, 142], 'yel', { split: 'z' });
    m.box('ladder_l', [-15, 0, 2.4], [-14.4, 26, 3], 'st', { split: 'y' });
    m.box('ladder_r', [-11.6, 0, 2.4], [-11, 26, 3], 'st', { split: 'y' });
    for (let y = 3; y < 24; y += 3) m.box(`rung_${y}`, [-14.4, y, 2.5], [-11.6, y + 0.5, 2.9], 'st', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function sprockets(m, z) {
    m.prism('shaft', 'x', [YS, z], 1.2, -6, 20, 'bright', { split: '', ends: ['east', 'west'] });
    for (const x of [-3, 17]) m.disc(`sprocket_${x}`, 'x', [YS, z], RS, x, x + 2, 'ci', 'vver/flange', x < 0 ? 'west' : 'east');
    m.prism('drum', 'x', [YS, z], RS - 1, -1, 17, 'ci', { split: '' });
  }
  function glow(m) {
    m.box('glow_flame', [0.2, 26.7, 26.4], [15.8, 27.3, 39.6], 'coals', { split: '' });
    m.box('glow_front', [0, 26.6, 40], [16, 26.8, 52], 'coals', { split: '', hide: ['down'] });
  }
  const PART = {
    sprocket_tail: { draw: (m) => sprockets(m, Z0), shift: [0, 0, 0] },
    sprocket_head: { draw: (m) => sprockets(m, Z1), shift: [0, 0, -112] },
    glow: { draw: glow, shift: [0, 0, -24] },
  };

  function model() { const m = mk(); frame(m); pallets(m); feedAndHood(m); windboxes(m); headEnd(m); walkway(m); return m; }
  function partial(name) { const s = mk(); PART[name].draw(s); const [dx, dy, dz] = PART[name].shift; return mk().merge(s, dx, dy, dz, ''); }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.SSR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'ironmaking/sinter_strand'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'ironmaking/sinter_strand'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
