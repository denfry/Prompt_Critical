// Reverberatory matte smelting furnace at real scale (1 block = 1 m, 16 px = 1 m): a small copper reverberatory of
// the early twentieth century, 16 m long and 6 m wide over the buckstays, 3 m to the crown of its sprung silica
// arch: the brick side walls and end walls on a concrete foundation, the arch stepped in three courses, buckstays
// with tie rods over the roof, three pulverised-coal burners through the west end wall, the charge pipes through the
// crown along both sides, the matte tap hole with its launder on the north side, the slag skimming door at the east
// end, and the uptake at the east end into the flue to the waste heat boiler.
// matte_smelter_real_lit is the same furnace firing (LIT): burner flames, the glow in the skimming door, the tap
// hole and the peepholes.
//
// Load after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for v in cold, lit:
//   MSMR.build(repo, v); /* wait */ MSMR.save(repo, v)
//
// Controller block = (0, 0, 0). Footprint x -8..7, y 0..3, z -3..2; furnace along x, firing end west.
// Connections (px): fuel and air in on the west face of (-8, 1, 0) at (-128, 24, 0), the burner header; charge in
// through the top of the crown (the charge pipes, cells (-4..4, 3, -2) and (-4..4, 3, 1)); matte out on the north
// face of (2, 0, -3) at (40, 8, -48), the tap launder; slag out on the north face of (6, 1, -3) at (104, 20, -48),
// the skimming door; flue gas out on the east face of (7, 3, 0) at (128, 56, 0). No partials.
(function () {
  const X = [-120, 120], Z = [-44, 44], YW = 36;        // inner end walls, outer side walls, wall top
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, soot: { tex: 'material/soot', P: 16 }, fire: { tex: 'plant/yellow', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/brick' });

  function body(m) {
    m.box('foundation', [-128, 0, -48], [128, 3, 48], 'conc', { split: 'xz' });
    m.box('wall_n', [X[0], 3, Z[0]], [X[1], YW, Z[0] + 8], 'brick', { split: 'xy' });
    m.box('wall_s', [X[0], 3, Z[1] - 8], [X[1], YW, Z[1]], 'brick', { split: 'xy' });
    m.box('end_w', [-124, 3, Z[0]], [X[0], YW + 6, Z[1]], 'brick', { split: 'zy' });
    m.box('end_e', [X[1], 3, Z[0]], [128, YW + 6, Z[1]], 'brick', { split: 'zy' });
    // sprung arch in three courses, the skewbacks along the wall tops
    m.box('skew_n', [X[0], YW, Z[0]], [X[1], YW + 3, Z[0] + 10], 'ci', { split: 'x' });
    m.box('skew_s', [X[0], YW, Z[1] - 10], [X[1], YW + 3, Z[1]], 'ci', { split: 'x' });
    [[Z[0] + 8, Z[1] - 8, YW + 3, YW + 6], [Z[0] + 14, Z[1] - 14, YW + 6, YW + 9], [Z[0] + 24, Z[1] - 24, YW + 9, YW + 11]].forEach(([z0, z1, y0, y1], i) => m.box(`arch_${i}`, [X[0], y0, z0], [X[1], y1, z1], 'brick', { split: 'xz' }));
    // buckstays and tie rods
    for (let x = X[0] + 8; x < X[1]; x += 16) {
      for (const [z0, z1] of [[Z[0] - 3, Z[0]], [Z[1], Z[1] + 3]]) m.box(`buckstay_${x}_${z0}`, [x - 1, 3, z0], [x + 1, YW + 10, z1], 'frame', { split: 'y' });
      m.box(`tie_${x}`, [x - 0.4, YW + 11, Z[0] - 3], [x + 0.4, YW + 11.8, Z[1] + 3], 'st', { split: 'z' });
    }
  }

  function firing(m, lit) {
    // burner header across the west end and three burners through the end wall
    m.prism('header', 'z', [-126.4, 24], 1.6, -24, 24, 'frame', { split: 'z' });
    m.disc('header_inlet', 'x', [24, 0], 2.6, -127.9, -127.2, 'ci', 'vver/flange', 'west');
    m.prism('header_feed', 'x', [24, 0], 1.6, -128, -126.4, 'frame', { split: '' });
    for (const z of [-20, 0, 20]) {
      m.prism(`burner_${z}`, 'x', [24, z], 1.4, -126.4, X[0], 'ci', { split: '' });
      m.box(`burner_plate_${z}`, [-124.6, 20, z - 4], [-124, 28, z + 4], 'ci', { split: '' });
      if (lit) m.box(`glow_flame_${z}`, [X[0], 20, z - 3], [X[0] + 20, 28, z + 3], 'fire', { split: '' });
    }
  }

  function charging(m) {
    for (let x = -64; x <= 64; x += 32) for (const z of [-24, 24]) {
      m.cylY(`charge_pipe_${x}_${z}`, [x, z], 2.6, YW + 6, 64, 'frame');
      m.box(`charge_gate_${x}_${z}`, [x - 3, 60, z - 3], [x + 3, 61, z + 3], 'ci', { split: '' });
    }
  }

  function taps(m, lit) {
    // matte tap hole and launder on the north side
    m.box('tap_block', [34, 3, Z[0] - 2], [46, 14, Z[0]], 'ci', { split: '' });
    m.box(lit ? 'glow_tap' : 'tap_plug', [39, 6, Z[0] - 2.05], [41, 9, Z[0] - 2], lit ? 'fire' : 'soot', { split: '' });
    m.box('launder', [37, 3, -48], [43, 6, Z[0] - 2], 'ci', { split: '' });
    // skimming door at the east end of the north wall, its counterweight frame
    m.box('door_frame', [96, 12, Z[0] - 2], [112, 30, Z[0]], 'ci', { split: '' });
    m.box(lit ? 'glow_door' : 'door', [99, 14, Z[0] - 2.05], [109, 26, Z[0] - 2], lit ? 'fire' : 'soot', { split: '' });
    m.box('slag_spout', [100, 12, -48], [108, 14, Z[0] - 2], 'ci', { split: '' });
    for (const x of [-96, -48, 0, 64]) {
      m.box(`peep_${x}`, [x - 1.6, 22, Z[0] - 0.6], [x + 1.6, 26, Z[0]], 'ci', { split: '' });
      if (lit) m.box(`glow_peep_${x}`, [x - 0.8, 23, Z[0] - 0.65], [x + 0.8, 25, Z[0] - 0.6], 'fire', { split: '' });
    }
    m.decal('plate', [-8, 6, Z[0] - 0.1], [0, 10, Z[0]], 'brick', 'north', 'pl.maker');
  }

  // Uptake at the east end and the flue to the east face.
  function flue(m) {
    m.box('uptake', [104, YW + 6, -16], [128, 52, 16], 'brick', { split: 'xz' });
    m.box('flue', [112, 48, -10], [128, 64, 10], 'frame', { split: '' });
    m.box('flue_flange', [127.2, 47, -11], [128, 64, 11], 'ci', { split: '', hide: ['east'] });
    m.box('flue_dark', [127.95, 50, -8], [128, 62, 8], 'soot', { split: '' });
  }

  function model(lit) { const m = mk(); body(m); firing(m, lit); charging(m); taps(m, lit); flue(m); return m; }
  const ID = { cold: 'matte_smelter_real', lit: 'matte_smelter_real_lit' };
  window.MSMR = {
    ID, model,
    build: (root, v) => { const m = PCVV.cut(model(v === 'lit')); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, ID[v], 'nonferrous'),
  };
})();
