// Era I3 plant at real scale (1 block = 1 m, 16 px = 1 m): the heavy gas turbine and the HRSG of the CCGT, the primary
// steam reformer, the ammonia converter, the Lurgi methanol converter, the ethylene pyrolysis furnace, the bipolar
// membrane chlor-alkali electrolyser and the suspension PVC reactor.
// Sheet: docs/research/2026-11-24-i3/i3-dimensions.md (disk only); this header is the final word where they differ.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then:
//   I3.paint(repo); for k in UNITS: I3.build(repo, k); /* wait */ I3.save(repo, k)
//   for (const p of I3.parts(k)) { I3.buildPartial(repo, k, p); I3.savePartial(repo, k, p) }
//
// Every unit: controller (0, 0, 0), front north.
// gt (gas_turbine_heavy_real), x -6..6, y 0..5, z -2..2, axis along x at (y 48, z 8): air in on the top face of
//   (-5, 5, 0) at (-76, 96, 8); exhaust on the east face of (6, 3, 0) at (112, 48, 8); generator shaft on the west face
//   of (-6, 3, 0) at (-96, 48, 8); fuel gas on the north face of (0, 0, -2) at (8, 8, -32); lube oil on the south face
//   of (0, 0, 2) at (8, 8, 48). Partial coupling: turns about x at (y 48, z 8); stored +80 x, -32 y.
// hrsg (hrsg_real), x -6..7, y 0..17, z -3..3: GT exhaust in on the west face of (-6, 3, 0) at (-96, 48, 8); steam out
//   on the north face of (0, 16, -3) at (8, 260, -48); feedwater on the south face of (2, 15, 3) at (40, 248, 64);
//   blowdown on the bottom face of (0, 0, 0) at (8, 0, 8); flue gas on the top face of (6, 17, 0) at (104, 288, 8).
//   Partial damper: turns about z at (104, 200); stored -96 x, -184 y.
// reformer (steam_reformer_real, lit variant steam_reformer_real_lit), x -4..4, y 0..13, z -2..2: feed on the west
//   face of (-4, 13, 0) at (-64, 214, 8); reformed gas on the west face of (-4, 0, 0) at (-64, 10, 8); fuel gas on the
//   north face of (0, 12, -2) at (8, 204, -32); flue gas on the top face of (4, 13, 0) at (64, 224, 8). Partial
//   fan_coupling: turns about z at (64, 10); stored -48 x, +8 z.
// ammonia (ammonia_converter_real), x -1..1, y 0..13, z -1..1: gas in on the west face of (-1, 1, 0) at (-16, 24, 8);
//   gas out on the east face of (1, 1, 0) at (32, 24, 8); quench gas on the top face of (0, 13, 0) at (8, 224, 8).
// methanol (methanol_converter_real), x -2..2, y 0..12, z -2..2: syngas in on the top face of (0, 12, 0) at
//   (8, 208, 8); product gas on the east face of (2, 0, 0) at (48, 12, 8); steam on the top face of (-1, 12, -1) at
//   (-8, 208, -16); boiler feedwater on the west face of (-2, 11, -1) at (-32, 180, -16).
// pyrolysis (pyrolysis_furnace_real, lit variant pyrolysis_furnace_real_lit), x -4..4, y 0..15, z -2..2: feed on the
//   east face of (4, 11, 0) at (80, 184, 8); cracked gas on the north face of (0, 13, -2) at (8, 218, -32); fuel gas on
//   the south face of (0, 0, 2) at (8, 6, 48); feedwater on the west face of (-4, 14, 0) at (-64, 236, 12); HP steam on
//   the top face of (3, 15, 1) at (56, 256, 20); flue gas on the top face of (0, 15, 0) at (8, 256, 8). Partial
//   damper: turns about x at (y 240, z 8); stored -224 y.
// chlor (chlor_alkali_electrolyser_real), x -4..5, y 0..2, z -1..1: DC + on the west face of (-4, 1, 0) at
//   (-64, 24, 8); DC - on the east face of (5, 1, 0) at (96, 24, 8); brine in on the south face of (0, 0, 1) at
//   (8, 9, 32); NaOH out on the north face of (0, 0, -1) at (8, 8, -16); water in on the north face of (2, 0, -1) at
//   (40, 8, -16); Cl2 out on the top face of (-3, 2, 0) at (-40, 48, -2); H2 out on the top face of (4, 2, 1) at
//   (72, 48, 18).
// pvc (pvc_reactor_real), x -2..2, y 0..7, z -2..2: VCM in on the top face of (-1, 7, 0) at (-8, 128, 8); water and
//   initiator on the west face of (-2, 6, 0) at (-32, 100, 8); slurry out on the bottom face of (0, 0, 0) at (8, 0, 8);
//   jacket water in on the south face of (0, 1, 2) at (8, 24, 48), out on the north face of (0, 4, -2) at (8, 72, -32);
//   condenser vent on the top face of (2, 7, -2) at (36, 128, -16). Partial agitator_coupling: turns about y at (8, 8);
//   stored -96 y.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    frame: { tex: 'plant/structure', P: 16 }, blue: { tex: 'plant/blue', P: 16 },
    pntL: { tex: 'vver/paint', P: 32 }, frameL: { tex: 'plant/structure', P: 32 }, hot: { tex: 'i3/hot', P: 16 },
  });
  const mk = (p) => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: p || 'vver/paint' });
  const S = { split: '' };

  function paint(root) {
    const { img, put, mix, rng, writePng } = PCART;
    const im = img(32, 32), r = rng(501);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = Math.sin(x * 0.45 + y * 0.3) * 0.5 + 0.5;
      put(im, x, y, mix([255, 140, 40], [255, 232, 170], t * 0.7 + r() * 0.3));
    }
    writePng(im, require('path').join(root, PCART.ASSETS, 'textures/block/i3/hot.png'));
    return 1;
  }
  const rails = (m, name, x0, x1, z0, z1, y, mat) => {
    for (const yy of [y + 4, y + 7.4]) {
      m.box(`${name}_n_${yy}`, [x0, yy, z0], [x1, yy + 0.6, z0 + 0.6], mat, { split: 'x' });
      m.box(`${name}_s_${yy}`, [x0, yy, z1 - 0.6], [x1, yy + 0.6, z1], mat, { split: 'x' });
    }
    for (let x = x0; x <= x1 - 0.6; x += Math.max(8, (x1 - x0 - 0.6) / Math.ceil((x1 - x0) / 24))) for (const z of [z0, z1 - 0.6]) m.box(`${name}_p_${x.toFixed(1)}_${z}`, [x, y, z], [x + 0.6, y + 8, z + 0.6], mat, S);
  };
  const ladder = (m, name, x, z, y0, y1) => {
    for (let y = y0 + 4; y < y1; y += 5) m.box(`ladder_${name}_${y}`, [x, y, z], [x + 4, y + 0.5, z + 0.5], 'st', S);
    for (const dx of [0, 3.6]) m.box(`ladder_${name}_side_${dx}`, [x + dx, y0, z], [x + dx + 0.4, y1, z + 0.5], 'st', { split: 'y' });
  };

  // ------------------------------------------------------------------ heavy gas turbine
  function gt() {
    const m = mk(), A = [48, 8];
    m.box('base', [-96, 0, -24], [112, 8, 40], 'frame');
    for (const x of [-60, 36, 64]) m.box(`pedestal_${x}`, [x, 8, -10], [x + 8, 22, 26], 'pnt', S);
    m.prism('shaft_stub', 'x', A, 3, -90, -88, 'bright', S);
    m.box('inlet_plenum', [-88, 16, -24], [-64, 95.9, 40], 'pntL');
    for (const y of [32, 56, 80]) m.box(`plenum_rib_${y}`, [-88.4, y, -24.4], [-63.6, y + 1.5, 40.4], 'frame', S);
    m.prism('compressor_1', 'x', A, 22, -64, -40, 'pnt', S);
    m.prism('compressor_2', 'x', A, 20, -40, -16, 'pnt', S);
    m.prism('compressor_3', 'x', A, 18, -16, -8, 'pnt', S);
    m.prism('combustor_casing', 'x', A, 26, -8, 24, 'pnt', S);
    for (let k = 0; k < 14; k++) {
      const a = k * 2 * Math.PI / 14, y = 48 + 28 * Math.cos(a), z = 8 + 28 * Math.sin(a);
      m.prism(`can_${k}`, 'x', [y, z], 3, -6, 20, 'st', { split: '', ends: ['west'] });
    }
    m.ring('fuel_ring', 'x', A, 31.5, 1.2, 21, 22.5, 'yel');
    m.prism('turbine_1', 'x', A, 28, 24, 44, 'pnt', S);
    m.prism('turbine_2', 'x', A, 30, 44, 56, 'pnt', S);
    m.box('diffuser', [56, 16, -24], [111.9, 80, 40], 'pntL');
    for (const x of [72, 92]) m.box(`diffuser_rib_${x}`, [x, 15.6, -24.4], [x + 1.5, 80.4, 40.4], 'frame', S);
    for (const x of [-56, 30]) m.box(`bearing_${x}`, [x, 20, -6], [x + 6, 28, 22], 'ci', S);
    m.box('fuel_in', [6, 6, -32], [10, 10, -20], 'yel', S);
    m.box('fuel_riser', [6, 10, -24], [10, 48, -20], 'yel', S);
    m.box('lube_in', [6, 6, 40], [10, 10, 47.9], 'oil', S);
    m.box('lube_line', [-58, 10, 40], [34, 12, 42], 'oil', { split: 'x' });
    m.decal('plate', [-80, 40, -24.6], [-72, 44, -24], 'pntL', 'north', 'pl.maker');
    return m;
  }
  function coupling() { const m = mk(); m.prism('coupling', 'x', [48, 8], 6, -96, -90, 'red', { split: '', ends: ['east', 'west'] }); return m; }

  // ------------------------------------------------------------------ HRSG
  function hrsg() {
    const m = mk();
    m.box('foundation', [-96, 0, -48], [128, 8, 64], 'conc', { split: 'xz' });
    m.box('duct_1', [-96, 16, -24], [-80, 80, 40], 'pntL');
    m.box('duct_2', [-80, 8, -32], [-64, 160, 48], 'pntL');
    m.box('duct_3', [-64, 8, -40], [-48, 220, 56], 'pntL');
    m.box('casing', [-48, 8, -40], [64, 240, 56], 'pntL');
    for (const x of [-48, -16, 16, 48]) for (const z of [-44, 56]) m.box(`column_${x}_${z}`, [x, 8, z], [x + 3, 240, z + 4], 'frameL', { split: 'y' });
    for (const x of [-40, -16, 8, 32]) m.box(`header_${x}`, [x, 240, -36], [x + 6, 248, 52], 'st', S);
    m.prism('drum', 'z', [8, 262], 12, -40, 56, 'pnt', { split: '', ends: ['north', 'south'] });
    for (const z of [-30, 42]) m.box(`drum_saddle_${z}`, [0, 248, z], [16, 252, z + 4], 'frame', S);
    for (const x of [-40, -16, 8, 32]) m.box(`riser_${x}`, [x + 1, 248, 6], [x + 5, 251, 10], 'st', S);
    for (const x of [-40, -16, 32]) m.box(`riser_up_${x}`, [x + 1, 251, 6], [x + 5, 254, 10], 'st', S);
    m.box('riser_link', [-39, 252, 6.5], [37, 254, 9.5], 'st', { split: 'x' });
    m.box('downcomer', [6, 12, -47], [10, 262, -43], 'st', { split: 'y' });
    m.box('downcomer_foot', [6, 12, -43], [10, 16, -40], 'st', S);
    m.box('downcomer_top', [6, 258, -43], [10, 262, -40], 'st', S);
    m.box('steam_out', [6, 258, -48], [10, 262, -43], 'gs', S);
    m.box('fw_line', [14, 246, 50], [42, 250, 54], 'gw', S);
    m.box('fw_in', [38, 246, 54], [42, 250, 63.9], 'gw', S);
    for (const [x, z] of [[2, -10], [14, 26]]) m.cylY(`safety_${x}`, [x, z], 1, 274, 282, 'red');
    m.box('platform', [-16, 248, -38], [32, 249, 54], 'grate', { split: 'xz' });
    rails(m, 'drum_rail', -16, 32, -38, 54, 249, 'yel');
    ladder(m, 'west', -46, -48, 8, 248);
    m.box('outlet_hood', [64, 16, -36], [88, 232, 52], 'pntL');
    m.cylY('stack', [104, 8], 20, 8, 287.9, 'st');
    m.ringY('stack_platform', [104, 8], 22, 2, 256, 257, 'grate');
    m.ringY('stack_rail', [104, 8], 22, 0.6, 262, 263, 'yel');
    m.box('damper_actuator', [124, 196, 6], [127.9, 204, 10], 'motor', S);
    m.box('blowdown', [6, 0, 6], [10, 8, 10], 'st', S);
    m.decal('plate', [-30, 30, -40.6], [-22, 34, -40], 'pntL', 'north', 'pl.maker');
    return m;
  }
  function hrsgDamper() { const m = mk(); m.box('blade', [84.5, 199.5, -11], [123.5, 200.5, 27], 'st', S); return m; }

  // ------------------------------------------------------------------ primary reformer
  const TUBE_X = Array.from({ length: 10 }, (_, i) => -58 + 10.5 * i);
  function reformer(lit) {
    const m = mk();
    for (const x of [-64, -22, 18, 44]) for (const z of [-24, 37]) m.box(`column_${x}_${z}`, [x, 0, z], [x + 3, 24, z + 3], 'frame', S);
    m.box('box', [-64, 24, -24], [48, 200, 40], 'pntL');
    for (const y of [60, 120, 170]) for (let x = -56; x < 44; x += 16) for (const [z0, z1] of [[-24.4, -24], [40, 40.4]]) {
      m.box(lit ? `glow_peep_${x}_${y}_${z0}` : `peep_${x}_${y}_${z0}`, [x, y, z0], [x + 3, y + 3, z1], lit ? 'hot' : 'dark', S);
    }
    for (let x = -56; x < 44; x += 12) for (const z of [-12, 28]) m.box(`burner_${x}_${z}`, [x, 200, z - 2], [x + 3, 206, z + 2], 'dark', S);
    m.box('air_duct', [-60, 200, 4], [44, 208, 12], 'pnt', { split: 'x' });
    for (const x of TUBE_X) for (const z of [0, 16]) {
      m.box(`tube_top_${x}_${z}`, [x - 1, 200, z - 1], [x + 1, 208, z + 1], 'forged', S);
      m.box(`pigtail_${x}_${z}`, [x - 0.3, 208, Math.min(z, 8)], [x + 0.3, 212, Math.max(z, 8) + 0.6], 'bright', S);
      m.box(`tube_bot_${x}_${z}`, [x - 1, 12, z - 1], [x + 1, 24, z + 1], 'forged', S);
    }
    m.prism('inlet_header', 'x', [214, 8], 2.5, -64, 40, 'gs', S);
    for (const z of [0, 16]) m.prism(`outlet_${z}`, 'x', [10, z], 3, -60, 40, 'clad', S);
    m.prism('outlet_cross', 'z', [-60, 10], 3, -3, 19, 'clad', S);
    m.box('outlet_port', [-64, 7, 5], [-60, 13, 11], 'clad', S);
    m.box('fuel_header', [-60, 202, -28], [44, 206, -24], 'yel', { split: 'x' });
    m.box('fuel_in', [6, 202, -32], [10, 206, -28], 'yel', S);
    m.box('flue_duct', [40, 24, -12], [56, 40, 28], 'pnt', S);
    m.box('convection', [48, 24, -16], [80, 160, 32], 'pntL');
    for (const y of [60, 100, 140]) m.box(`conv_header_${y}`, [80, y, -12], [80, y + 4, 28], 'st', { split: '' });
    m.cylY('stack', [64, 8], 6, 160, 223.9, 'st');
    m.box('fan_housing', [50, 0, -14], [78, 22, 2], 'pnt', S);
    m.box('fan_motor', [56, 4, -30], [72, 16, -22], 'motor', S);
    m.box('platform_n', [-64, 200, -32], [48, 201, -24], 'grate', { split: 'x' });
    m.box('platform_s', [-64, 200, 40], [48, 201, 48], 'grate', { split: 'x' });
    rails(m, 'roof_rail', -64, 48, -32, 48, 201, 'yel');
    ladder(m, 'nw', -63, -31.6, 0, 200);
    m.decal('plate', [-40, 30, -24.6], [-32, 34, -24], 'pntL', 'north', 'pl.maker');
    return m;
  }
  function fanCoupling() { const m = mk(); m.prism('coupling', 'z', [64, 10], 3, -21, -15, 'red', { split: '', ends: ['north', 'south'] }); return m; }

  // ------------------------------------------------------------------ ammonia converter
  function ammonia() {
    const m = mk();
    m.prismY('base_ring', [8, 8], 13, 0, 1, 'pnt', { ends: ['up'] });
    m.cylY('skirt', [8, 8], 12, 1, 16, 'pnt');
    m.cylY('shell', [8, 8], 12, 16, 196, 'forged');
    for (const y of [56, 96, 136]) m.ringY(`band_${y}`, [8, 8], 12.4, 0.6, y, y + 1, 'st');
    m.prismY('closure', [8, 8], 14, 196, 204, 'forged', { ends: ['up', 'down'] });
    for (let k = 0; k < 20; k++) {
      const a = k * Math.PI / 10, x = 8 + 13 * Math.cos(a), z = 8 + 13 * Math.sin(a);
      m.box(`stud_${k}`, [x - 0.5, 204, z - 0.5], [x + 0.5, 207, z + 0.5], 'st', S);
    }
    m.prismY('cover', [8, 8], 10, 204, 208, 'forged', { ends: ['up'] });
    m.cylY('quench_nozzle', [8, 8], 2, 208, 223.9, 'st');
    [[0, 140], [120, 100], [240, 60]].forEach(([deg, yEnd], i) => {
      const a = deg * Math.PI / 180, px = 8 + 14.5 * Math.cos(a), pz = 8 + 14.5 * Math.sin(a);
      m.box(`quench_${i}`, [px - 0.8, yEnd, pz - 0.8], [px + 0.8, 216, pz + 0.8], 'yel', { split: 'y' });
      m.box(`quench_in_${i}`, [Math.min(px, 8 + 11.8 * Math.cos(a)) - 0.8, yEnd, Math.min(pz, 8 + 11.8 * Math.sin(a)) - 0.8], [Math.max(px, 8 + 11.8 * Math.cos(a)) + 0.8, yEnd + 1.6, Math.max(pz, 8 + 11.8 * Math.sin(a)) + 0.8], 'yel', S);
      m.box(`quench_top_${i}`, [Math.min(px, 8) - 0.8, 214.4, Math.min(pz, 8) - 0.8], [Math.max(px, 8) + 0.8, 216, Math.max(pz, 8) + 0.8], 'yel', S);
    });
    m.prism('inlet', 'x', [24, 8], 3, -16, -3, 'forged', { split: '', ends: ['west'] });
    m.prism('outlet', 'x', [24, 8], 3, 19, 32, 'forged', { split: '', ends: ['east'] });
    m.box('platform', [-15, 188, -15], [31, 189, 31], 'grate', { split: 'xz' });
    rails(m, 'top_rail', -15, 31, -15, 31, 189, 'yel');
    ladder(m, 'n', 6, -15.6, 0, 188);
    m.decal('plate', [4, 30, -4.4], [12, 34, -4], 'forged', 'north', 'pl.maker');
    return m;
  }

  // ------------------------------------------------------------------ methanol converter
  function methanol() {
    const m = mk();
    m.cylY('skirt', [8, 8], 20, 0, 20, 'pnt');
    m.prismY('bottom_head', [8, 8], 22, 20, 24, 'clad', { ends: ['down'] });
    m.cylY('shell', [8, 8], 24, 24, 136, 'clad');
    for (const y of [56, 96]) m.ringY(`band_${y}`, [8, 8], 24.5, 0.6, y, y + 1, 'st');
    m.prismY('top_head_1', [8, 8], 20, 136, 144, 'clad', { ends: ['up'] });
    m.prismY('top_head_2', [8, 8], 14, 144, 152, 'clad', { ends: ['up'] });
    m.cylY('syngas_in', [8, 8], 3, 152, 207.9, 'yel');
    m.box('product_out', [8, 10, 6], [47.9, 14, 10], 'oil', S);
    m.prism('drum', 'x', [180, -16], 10, -30, 46, 'pnt', { split: '', ends: ['east', 'west'] });
    for (const x of [-30, 43]) for (const z of [-30, -12]) m.box(`column_${x}_${z}`, [x, 0, z], [x + 3, 170, z + 3], 'frame', { split: 'y' });
    for (const z of [-30, -12]) m.box(`beam_${z}`, [-30, 167, z], [46, 170, z + 3], 'frame', { split: 'x' });
    m.box('platform', [-31, 170, -32], [47, 171, 0], 'grate', { split: 'xz' });
    rails(m, 'drum_rail', -31, 47, -32, 0, 171, 'yel');
    for (const x of [-2, 18]) {
      m.box(`downcomer_${x}`, [x, 30, -19], [x + 2, 172, -17], 'st', { split: 'y' });
      m.box(`downcomer_in_${x}`, [x, 30, -17], [x + 2, 32, -14], 'st', S);
    }
    for (const x of [4, 12]) {
      m.box(`riser_${x}`, [x, 128, -21], [x + 2, 172, -19], 'gs', { split: 'y' });
      m.box(`riser_in_${x}`, [x, 128, -19], [x + 2, 130, -14], 'gs', S);
    }
    for (const x of [0, 20]) m.cylY(`safety_${x}`, [x, -16], 1, 190, 196, 'red');
    m.cylY('steam_out', [-8, -16], 1.5, 190, 207.9, 'gs');
    m.prism('bfw_in', 'x', [180, -16], 1.5, -32, -30, 'gw', S);
    ladder(m, 'w', -30, -32, 0, 170);
    m.decal('plate', [4, 40, -16.6], [12, 44, -16], 'clad', 'north', 'pl.maker');
    return m;
  }

  // ------------------------------------------------------------------ pyrolysis furnace
  const TLE_X = [-40, -8, 24, 56];
  function pyrolysis(lit) {
    const m = mk();
    for (const x of [-64, -16, 32, 77]) for (const z of [-24, 37]) m.box(`column_${x}_${z}`, [x, 0, z], [x + 3, 16, z + 3], 'frame', S);
    for (let x = -56; x < 76; x += 16) for (const z of [-16, 8, 32]) {
      m.box(`floor_burner_${x}_${z}`, [x, 6, z - 2], [x + 4, 16, z + 2], 'dark', S);
      if (lit) m.box(`glow_floor_burner_${x}_${z}`, [x + 0.5, 5.6, z - 1.5], [x + 3.5, 6, z + 1.5], 'hot', S);
    }
    m.box('box', [-64, 16, -24], [80, 160, 40], 'pntL');
    for (let y = 40; y < 150; y += 24) for (let x = -56; x < 76; x += 24) for (const [z0, z1] of [[-24.6, -24], [40, 40.6]]) {
      m.box(`wall_burner_${x}_${y}_${z0}`, [x, y, z0], [x + 4, y + 4, z1], 'dark', S);
    }
    for (const y of [70, 130]) for (let x = -44; x < 76; x += 24) for (const [z0, z1] of [[-24.4, -24], [40, 40.4]]) {
      m.box(lit ? `glow_peep_${x}_${y}_${z0}` : `peep_${x}_${y}_${z0}`, [x, y, z0], [x + 2, y + 2, z1], lit ? 'hot' : 'dark', S);
    }
    m.box('convection', [-48, 160, -8], [64, 224, 24], 'pntL');
    for (const y of [172, 196]) m.box(`conv_header_${y}`, [64, y, -6], [66, y + 4, 22], 'st', S);
    m.box('feed_in', [64, 182, 6], [79.9, 186, 10], 'oil', S);
    for (const x of TLE_X) {
      m.box(`transfer_${x}`, [x - 1.5, 160, -21.5], [x + 1.5, 168, -18.5], 'forged', S);
      m.cylY(`tle_${x}`, [x, -20], 3, 168, 214, 'pnt');
      m.box(`tle_out_${x}`, [x - 1.5, 214, -25.5], [x + 1.5, 217, -18.5], 'pnt', S);
    }
    m.prism('cracked_header', 'x', [218, -24], 3, -48, 64, 'pnt', S);
    m.box('cracked_out', [6, 215, -32], [10, 221, -27], 'pnt', S);
    m.prism('drum', 'x', [236, 16], 8, -56, 72, 'pnt', { split: '', ends: ['east', 'west'] });
    for (const x of [-40, 56]) m.box(`drum_saddle_${x}`, [x, 224, 10], [x + 4, 230, 22], 'frame', S);
    m.box('bfw_in', [-64, 234, 10], [-56, 238, 14], 'gw', S);
    m.cylY('steam_out', [56, 20], 1.5, 243, 255.9, 'gs');
    for (const x of TLE_X) m.box(`tle_steam_${x}`, [x - 0.5, 214, -17], [x + 0.5, 234, -16], 'gs', { split: 'y' });
    m.box('tle_steam_header', [-41, 233, -17], [57, 234, 10], 'gs', S);
    m.cylY('stack', [8, 8], 8, 224, 255.9, 'st');
    m.box('fuel_header', [-60, 4, 42], [76, 8, 46], 'yel', { split: 'x' });
    m.box('fuel_in', [6, 4, 46], [10, 8, 47.9], 'yel', S);
    rails(m, 'roof_rail', -64, 80, -24, 40, 160, 'yel');
    ladder(m, 'nw', -63, -31.6, 0, 160);
    m.decal('plate', [-30, 24, -24.6], [-22, 28, -24], 'pntL', 'north', 'pl.maker');
    return m;
  }
  function pyroDamper() { const m = mk(); m.box('blade', [0.6, 239.5, 0.6], [15.4, 240.5, 15.4], 'st', S); return m; }

  // ------------------------------------------------------------------ chlor-alkali electrolyser
  function chlor() {
    const m = mk();
    for (let x = -62; x < 94; x += 31) for (const z of [-12, 26]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 2, 8, z + 2], 'frame', S);
    for (const z of [-12, 26]) m.box(`rail_${z}`, [-62, 8, z], [94, 10, z + 2], 'frame', { split: 'x' });
    for (const z of [-12, 24]) m.box(`hanger_${z}`, [-60, 34, z], [92, 36, z + 4], 'frame', { split: 'x' });
    m.box('end_w', [-60, 8, -14], [-56, 38, 30], 'blue', S);
    m.box('end_e', [88, 8, -14], [92, 38, 30], 'blue', S);
    for (const [y, z] of [[12, -12], [12, 28], [34, -12], [34, 28]]) m.prism(`tie_${y}_${z}`, 'x', [y, z], 0.8, -60, 92, 'bright', S);
    let i = 0;
    for (let x = -56; x < 87.9; x += 1.2, i++) {
      m.box(`element_${i}`, [x, 12, -10], [x + 1, 34, 26], i % 2 ? 'st' : 'ci', S);
      m.box(`hose_a_${i}`, [x + 0.2, 34, -6.4], [x + 0.8, 36.5, -5.6], 'oil', S);
      m.box(`hose_c_${i}`, [x + 0.2, 34, 21.6], [x + 0.8, 36.5, 22.4], 'blue', S);
    }
    m.prism('brine_in', 'x', [9, 22], 1.5, -56, 88, 'gw', S);
    m.prism('catholyte_in', 'x', [9, -6], 1.5, -56, 88, 'blue', S);
    m.prism('anolyte_out', 'x', [38, -6], 2, -56, 88, 'oil', S);
    m.prism('catholyte_out', 'x', [38, 22], 2, -56, 88, 'blue', S);
    m.prism('cl2', 'x', [44, -2], 3, -56, 88, 'yel', S);
    m.prism('h2', 'x', [44, 18], 3, -56, 88, 'red', S);
    m.box('cl2_out', [-42, 44, -4], [-38, 47.9, 0], 'yel', S);
    m.box('h2_out', [70, 44, 16], [74, 47.9, 20], 'red', S);
    m.box('brine_port', [6, 7, 23], [10, 11, 31.9], 'gw', S);
    m.box('naoh_down', [6, 8, -10], [10, 38, -7], 'blue', S);
    m.box('naoh_port', [6, 6, -16], [10, 10, -10], 'blue', S);
    m.box('water_port', [38, 6, -16], [42, 10, -7], 'blue', S);
    m.box('busbar_w', [-64, 8, 0], [-60, 40, 16], 'br', S);
    m.box('busbar_e', [92, 8, 0], [95.9, 40, 16], 'br', S);
    m.decal('plate', [-59, 20, -14.6], [-57, 24, -14], 'blue', 'north', 'pl.maker');
    return m;
  }

  // ------------------------------------------------------------------ PVC reactor
  function pvc() {
    const m = mk();
    for (const [x, z] of [[-31, -31], [45, -31], [-31, 45], [45, 45]]) m.box(`column_${x}_${z}`, [x, 0, z], [x + 2, 96, z + 2], 'frame', { split: 'y' });
    m.box('floor', [-32, 96, -32], [48, 97, 48], 'grate', { split: 'xz' });
    rails(m, 'floor_rail', -32, 48, -32, 48, 97, 'yel');
    m.cylY('discharge', [8, 8], 2, 0, 12, 'st');
    m.box('discharge_valve', [4, 4, 4], [12, 10, 12], 'dark', S);
    m.prismY('bottom_head', [8, 8], 20, 12, 16, 'st', { ends: ['down'] });
    m.cylY('shell', [8, 8], 24, 16, 80, 'st');
    m.cylY('jacket', [8, 8], 25, 20, 76, 'pnt', { seed: 3 });
    m.prismY('top_head_1', [8, 8], 20, 80, 86, 'st', { ends: ['up'] });
    m.prismY('top_head_2', [8, 8], 14, 86, 92, 'st', { ends: ['up'] });
    m.prismY('top_head_3', [8, 8], 8, 92, 96, 'st', { ends: ['up'] });
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) m.box(`lug_${dx}_${dz}`, [8 + dx * 24 - 2 - (dx < 0 ? 4 : 0) + (dx > 0 ? 0 : 0), 90, 8 + dz * 24 - 2 - (dz < 0 ? 4 : 0)], [8 + dx * 24 + 2 + (dx > 0 ? 4 : 0), 96, 8 + dz * 24 + 2 + (dz > 0 ? 4 : 0)], 'frame', S);
    m.box('gearbox', [2, 97, 2], [14, 108, 14], 'pnt', S);
    m.box('motor', [4, 112, 4], [12, 127.9, 12], 'motor', S);
    m.cylY('condenser', [36, -16], 6, 100, 124, 'st');
    m.prismY('condenser_top', [36, -16], 6.4, 124, 125, 'st', { ends: ['up'] });
    m.cylY('vent', [36, -16], 1, 125, 127.9, 'st');
    for (const [x, z] of [[31, -21], [40, -21], [31, -12], [40, -12]]) m.box(`condenser_leg_${x}_${z}`, [x, 97, z], [x + 1, 100, z + 1], 'frame', S);
    m.cylY('vapour', [22, -4], 1.5, 90, 106, 'st');
    m.box('vapour_line', [20.5, 104, -5.5], [36, 107, -2.5], 'st', S);
    m.box('vapour_drop', [33, 104, -12], [36, 107, -2.5], 'st', S);
    m.cylY('vcm_in', [-8, 8], 1.5, 86, 127.9, 'yel');
    m.cylY('manhole', [8, 26], 4, 86, 100, 'st');
    m.prismY('manhole_cover', [8, 26], 4.5, 100, 101, 'forged', { ends: ['up'] });
    m.box('davit_post', [13, 97, 28], [14, 110, 29], 'yel', S);
    m.box('davit_arm', [8, 109, 28], [14, 110, 29], 'yel', S);
    m.box('init_line', [-32, 98, 6], [-2, 102, 10], 'gw', S);
    m.cylY('init_drop', [-2, 8], 1, 88, 100, 'gw');
    m.box('jacket_in', [6, 22, 32], [10, 26, 47.9], 'gw', S);
    m.box('jacket_out', [6, 70, -31.9], [10, 74, -17], 'gw', S);
    ladder(m, 'nw', -30, -31.6, 0, 96);
    m.decal('plate', [4, 50, -17.6], [12, 54, -17], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function agitatorCoupling() { const m = mk(); m.prismY('coupling', [8, 8], 3, 108, 112, 'red', { ends: ['up', 'down'] }); return m; }

  const UNITS = {
    gt: { id: 'gas_turbine_heavy_real', draw: gt, parts: { coupling: { draw: coupling, shift: [80, -32, 0] } } },
    hrsg: { id: 'hrsg_real', draw: hrsg, parts: { damper: { draw: hrsgDamper, shift: [-96, -184, 0] } } },
    reformer: { id: 'steam_reformer_real', draw: () => reformer(false), parts: { fan_coupling: { draw: fanCoupling, shift: [-48, 0, 8] } } },
    reformer_lit: { id: 'steam_reformer_real_lit', draw: () => reformer(true), parts: {} },
    ammonia: { id: 'ammonia_converter_real', draw: ammonia, parts: {} },
    pyrolysis: { id: 'pyrolysis_furnace_real', draw: () => pyrolysis(false), parts: { damper: { draw: pyroDamper, shift: [0, -224, 0] } } },
    pyrolysis_lit: { id: 'pyrolysis_furnace_real_lit', draw: () => pyrolysis(true), parts: {} },
    methanol: { id: 'methanol_converter_real', draw: methanol, parts: {} },
    chlor: { id: 'chlor_alkali_electrolyser_real', draw: chlor, parts: {} },
    pvc: { id: 'pvc_reactor_real', draw: pvc, parts: { agitator_coupling: { draw: agitatorCoupling, shift: [0, -96, 0] } } },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return mk().merge(d.draw(), dx, dy, dz, ''); };
  window.I3 = {
    UNITS, partial, paint, model: (k) => UNITS[k].draw(),
    parts: (k) => Object.keys(UNITS[k].parts),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'i3'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'i3'),
  };
})();
