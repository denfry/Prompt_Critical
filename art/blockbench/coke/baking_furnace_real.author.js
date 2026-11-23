// Carbon baking furnace at real scale (1 block = 1 m, 16 px = 1 m): one section of a ring (Hoffman/Riedhammer-type)
// baking furnace for green electrodes and graphite blanks, built as a free-standing chamber: a steel-cased firebrick
// block 5 m long, 4 m wide and 3.3 m high with three sagger pits packed in coke breeze between hollow flue walls, the
// removable cover with lifting lugs and thermocouple heads, three gas burners with peepholes and the gas header on
// the front, buckstays and tie rods, and the flue duct with its damper out of the back to the chimney.
// baking_furnace_real_lit is the same chamber firing (LIT): flames in the peepholes and burner throats and the cover
// joint glowing.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for v in cold, lit:
//   BKR.build(repo, v); /* wait */ BKR.save(repo, v)
//
// Controller block = (0, 0, 0), front north. Footprint x -2..2, y 0..3, z 0..3 (the cover reaches into y 3).
// Connections (px): fuel gas in on the west face of (-2, 2, 0) at (-32, 38, 6), into the header; flue gas out on the
// south face of (0, 1, 3) at (8, 24, 64), to the chimney; electrodes in and out through the top under the cover.
// No partials (the lit look is the _lit whole model).
(function () {
  const X = [-30, 46], Z = [4, 62], YT = 52;             // brick body, body top
  const PITS = [[8, 22], [26, 40], [44, 58]];            // sagger pits along z
  const MAT = Object.assign({}, PCVV.MAT, {
    frame: { tex: 'plant/structure', P: 16 }, soot: { tex: 'material/soot', P: 16 }, duct: { tex: 'plant/structure', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/brick' });

  function body(m) {
    m.box('footing', [X[0] - 2, 0, Z[0] - 2], [X[1] + 2, 3, Z[1]], 'conc', { split: 'xz' });
    m.box('body', [X[0], 3, Z[0]], [X[1], YT, Z[1]], 'brick', { split: 'xyz', hide: ['up'] });
    // top: coke breeze in the pits, the flue walls between them, the edge course
    PITS.forEach(([z0, z1], i) => m.box(`pit_${i}`, [X[0] + 6, YT - 1, z0], [X[1] - 6, YT - 0.6, z1], 'soot', { split: 'x' }));
    for (const [z0, z1] of [[Z[0], 8], [22, 26], [40, 44], [58, Z[1]]]) m.box(`flue_wall_${z0}`, [X[0], YT - 1, z0], [X[1], YT, z1], 'brick', { split: 'x' });
    for (const [x0, x1] of [[X[0], X[0] + 6], [X[1] - 6, X[1]]]) m.box(`head_${x0}`, [x0, YT - 1, 8], [x1, YT, 58], 'brick', { split: 'z' });
    // steel casing: buckstays on the long sides joined by tie rods over the top
    for (let x = X[0] + 2; x < X[1]; x += 18) {
      for (const [z0, z1] of [[Z[0] - 1.4, Z[0]], [Z[1], Z[1] + 1.4]]) m.box(`buckstay_${x}_${z0}`, [x - 1, 3, z0], [x + 1, YT + 1, z1], 'frame', { split: 'y' });
      m.box(`tie_${x}`, [x - 0.4, YT + 0.6, Z[0] - 1.4], [x + 0.4, YT + 1.2, Z[1] + 1.4], 'st', { split: 'z' });
    }
    for (const [x0, x1] of [[X[0] - 1.4, X[0]], [X[1], X[1] + 1.4]]) for (const z of [8, 32, 56]) m.box(`end_stay_${x0}_${z}`, [x0, 3, z - 1], [x1, YT + 1, z + 1], 'frame', { split: 'y' });
  }

  // Domed steel cover over the chamber in three steps, lifting lugs, thermocouple heads; the joint glows when lit.
  function cover(m, lit) {
    m.box('cover_skirt', [X[0] + 1, YT + 1.2, Z[0] + 1], [X[1] - 1, YT + 4, Z[1] - 1], 'pnt', { split: 'xz' });
    m.box('cover_mid', [X[0] + 4, YT + 4, Z[0] + 4], [X[1] - 4, YT + 7, Z[1] - 4], 'pnt', { split: 'xz' });
    m.box('cover_top', [X[0] + 10, YT + 7, Z[0] + 10], [X[1] - 10, YT + 9, Z[1] - 10], 'pnt', { split: 'xz' });
    m.box('cover_seal', [X[0] + 0.6, YT + 1, Z[0] + 0.6], [X[1] - 0.6, YT + 1.4, Z[1] - 0.6], lit ? 'gs' : 'soot', { split: 'xz' });
    if (lit) m.box('glow_cover_joint', [X[0] + 0.5, YT + 1.05, Z[0] + 0.5], [X[1] - 0.5, YT + 1.2, Z[1] - 0.5], 'yel', { split: 'xz' });
    for (const [x, z] of [[X[0] + 12, Z[0] + 12], [X[1] - 12, Z[0] + 12], [X[0] + 12, Z[1] - 12], [X[1] - 12, Z[1] - 12]]) {
      m.box(`lug_${x}_${z}`, [x - 0.6, YT + 9, z - 2], [x + 0.6, YT + 12, z + 2], 'st', { split: '' });
    }
    for (const z of [15, 33, 51]) {
      m.cylY(`tc_${z}`, [8, z], 0.8, YT + 9, YT + 10.4, 'bright');
      m.box(`tc_head_${z}`, [7, YT + 10.4, z - 1], [9, YT + 11.8, z + 1], 'ci', { split: '' });
    }
  }

  // Front: three burners into the flue walls' fire channels, peepholes, the gas header along the front.
  function burners(m, lit) {
    for (const x of [-14, 8, 30]) {
      m.box(`burner_plate_${x}`, [x - 4, 22, Z[0] - 0.6], [x + 4, 32, Z[0]], 'ci', { split: '' });
      m.prism(`burner_${x}`, 'z', [x, 27], 1.6, Z[0] - 3.4, Z[0] - 0.6, 'ci', { split: '', ends: ['north'] });
      m.box(`burner_air_${x}`, [x - 2.4, 26, Z[0] - 4], [x + 2.4, 28, Z[0] - 3.4], 'ci', { split: '' });
      m.box(`gas_drop_${x}`, [x - 0.6, 28, Z[0] - 2.4], [x + 0.6, 38, Z[0] - 1.2], 'yel', { split: '' });
      m.box(`gas_cock_${x}`, [x - 1.2, 33, Z[0] - 2.8], [x + 1.2, 35, Z[0] - 0.8], 'gm', { split: '' });
      // peephole over the burner
      m.box(`peep_${x}`, [x - 1.6, 38, Z[0] - 0.8], [x + 1.6, 41, Z[0]], 'ci', { split: '' });
      m.box(`peep_glass_${x}`, [x - 1, 38.6, Z[0] - 0.85], [x + 1, 40.4, Z[0] - 0.8], lit ? 'gs' : 'dark', { split: '' });
      if (lit) {
        m.box(`glow_peep_${x}`, [x - 0.8, 38.8, Z[0] - 0.9], [x + 0.8, 40.2, Z[0] - 0.85], 'yel', { split: '' });
        m.box(`glow_throat_${x}`, [x - 1, 26, Z[0] - 0.65], [x + 1, 28, Z[0] - 0.6], 'yel', { split: '' });
      }
    }
    m.prism('gas_header', 'x', [38, Z[0] - 1.8], 1.2, -32, 34, 'yel', { split: 'x' });
    m.disc('gas_flange', 'x', [38, Z[0] - 1.8], 2, -31.9, -31.2, 'ci', 'vver/flange', 'west');
    for (const x of [-26, 26]) m.box(`header_bracket_${x}`, [x - 0.4, 34, Z[0] - 3.4], [x + 0.4, 38, Z[0] - 0.6], 'frame', { split: '' });
    m.decal('plate', [-4, 12, Z[0] - 0.1], [4, 16, Z[0]], 'brick', 'north', 'pl.maker');
  }

  // Flue duct out of the back to the chimney, its damper and lever.
  function flue(m) {
    m.box('flue_duct', [0, 14, Z[1]], [16, 32, 64], 'duct', { split: '' });
    m.box('flue_flange', [-1, 13, 63.4], [17, 33, 64], 'ci', { split: '', hide: ['south'] });
    m.box('flue_dark', [1, 15, 63.95], [15, 31, 64], 'soot', { split: '' });
    m.box('damper_spindle', [16, 23, 62.4], [19, 24, 63.4], 'bright', { split: '' });
    m.box('damper_lever', [19, 18, 62.4], [20, 29, 63.4], 'red', { split: '' });
  }

  function model(lit) { const m = mk(); body(m); cover(m, lit); burners(m, lit); flue(m); return m; }
  const ID = { cold: 'baking_furnace_real', lit: 'baking_furnace_real_lit' };
  window.BKR = {
    ID, model,
    build: (root, v) => { const m = PCVV.cut(model(v === 'lit')); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, ID[v], 'coke'),
  };
})();
