// Hydrogen power plant units at real scale (1 block = 1 m, 16 px = 1 m): the atmospheric alkaline electrolyser
// (Nel A485 class, about 2.2 MW), the two-stage diaphragm hydrogen compressor (PDC class), the 200 bar tube bank, the
// low-pressure receiver, and the 200 kW PEM fuel cell module (Ballard FCwave class) with its grid inverter.
// Sheet: docs/research/2026-11-24-hydrogen/hydrogen-dimensions.md (disk only); the port, pivot and partial list there
// is repeated per unit below, and this header is the final word where they differ.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for k in UNITS:
//   H2.build(repo, k); /* wait */ H2.save(repo, k)
//   for (const p of H2.parts(k)) { H2.buildPartial(repo, k, p); H2.savePartial(repo, k, p) }
//
// Every unit: controller (0, 0, 0), front north.
// electrolyser (h2_electrolyser_real), x -4..4, y 0..3, z -1..1: stack along x at (y 20, z 8); DC power on the west
//   face of (-4, 1, 0) at (-64, 20, 8); water in on the south face of (2, 0, 1) at (40, 8, 32); H2 out on the top face
//   of (4, 3, 0) at (72, 64, 8); O2 out on the top face of (3, 3, 0) at (56, 64, 8); cooling water on the south face
//   of (-1, 0, 1) at (-8, 8, 32). Partials h2_level (float in the H2 separator sight glass at (56, 20, -10), slides
//   y 0..20; stored -48 x) and o2_level (float in the O2 separator glass at (63, 20, 20), slides y 0..20; stored -48 x).
// compressor (h2_compressor_real), x -2..2, y 0..2, z -1..1: H2 in on the west face of (-2, 0, 0) at (-32, 12, 8);
//   H2 out on the east face of (2, 0, 0) at (48, 12, 8); power on the north face of (1, 0, -1) at (24, 8, -16);
//   cooling water on the south face of (0, 0, 1) at (8, 8, 32). Partials flywheel (crank sheave, turns about z at
//   (9, 14), no shift) and motor_sheave (turns about z at (-18, 10); stored +8 x); speed ratio 1 : 4.
// tube_bank (h2_tube_bank_real), x -3..3, y 0..2, z -1..1: H2 in/out on the east face of (3, 1, 0) at (64, 24, 8);
//   vent on the top face of (3, 2, 0) at (56, 48, 8). Partial pressure_needle: turns about x at (62.4, 30, 8),
//   0..270 deg over 0..250 bar; stored -48 x.
// gasholder (h2_gasholder_real), x -2..2, y 0..12, z -2..2: H2 in on the west face of (-2, 1, 0) at (-32, 24, 8); H2
//   out on the east face of (2, 1, 0) at (48, 24, 8); vent on the top face of (0, 12, 0) at (8, 208, 8); drain on the
//   bottom face of (0, 0, 0) at (8, 0, 8). Partial pressure_needle: turns about z at (16, 40, -18.2), 0..270 deg over
//   0..40 bar; stored (0, -24, +4).
// fuel_cell (h2_fuel_cell_real, running variant h2_fuel_cell_real_on with the status lamp lit), x -1..1, y 0..2,
//   z 0: H2 in on the west face of (-1, 0, 0) at (-16, 8, 8); AC out on the east face of (1, 0, 0) at (32, 8, 8);
//   product water on the bottom face of (-1, 0, 0) at (-8, 0, 8); exhaust air on the top face of (-1, 2, 0) at
//   (-8, 48, 8). Partials fan_0 and fan_1: radiator fans, turn about y at (-10.5, 8) and (-3.5, 8); stored -24 y.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, blue: { tex: 'plant/blue', P: 16 } });
  const mk = (p) => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: p || 'vver/paint' });

  // ------------------------------------------------------------------ electrolyser
  function electrolyser() {
    const m = mk();
    m.box('skid', [-64, 0, -14], [78, 4, 30], 'frame', { split: 'xz' });
    // filter-press stack: end plates, cell frames, tie rods
    for (const [x0, x1] of [[-60, -56], [32, 36]]) m.box(`end_plate_${x0}`, [x0, 4, -6], [x1, 36, 22], 'blue', { split: '' });
    m.box('cells', [-56, 6, -4.6], [32, 34, 20.6], 'dark', { split: 'x' });
    for (let x = -55; x < 32; x += 3) m.box(`cell_frame_${x}`, [x, 5.6, -5], [x + 1, 34.4, 21], 'ci', { split: '' });
    for (const [y, z] of [[7, -6.6], [33, -6.6], [7, 22.6], [33, 22.6], [20, -6.6], [20, 22.6]]) m.prism(`tie_${y}_${z}`, 'x', [y, z], 0.8, -62, 38, 'bright', { split: 'x', ends: ['west', 'east'] });
    for (const x of [-48, 20]) m.box(`stack_foot_${x}`, [x, 4, -8], [x + 6, 6, 24], 'blue', { split: '' });
    // DC busbars from the west face to the end plate
    for (const z of [3, 11]) m.box(`busbar_${z}`, [-64, 18, z], [-60, 22, z + 2], 'br', { split: '' });
    m.box('busbar_insulator', [-63, 16, 1], [-62, 24, 15], 'dark', { split: '' });
    // gas headers from the stack top to the separators: H2 (yellow) on the north, O2 (blue) on the south
    m.box('h2_header', [-50, 34, 0], [56, 37, 3], 'yel', { split: 'x' });
    m.box('o2_header', [-50, 34, 13], [56, 37, 16], 'blue', { split: 'x' });
    for (let x = -50; x <= 26; x += 19) { m.box(`h2_riser_${x}`, [x, 34, 0.5], [x + 1.6, 34.6, 2.5], 'yel', { split: '' }); }
    // separators
    m.cylY('h2_sep', [56, -2], 7.2, 6, 52, 'pnt');
    m.headUp('h2_sep_top', [56, -2], 7.2, 52, 4, 3, 'pnt');
    m.cylY('o2_sep', [56, 20], 6.4, 6, 50, 'pnt');
    m.headUp('o2_sep_top', [56, 20], 6.4, 50, 4, 3, 'pnt');
    m.box('h2_sep_in', [54, 34, 1], [56, 37, 3], 'yel', { split: '' });
    m.box('o2_sep_in', [54, 34, 14], [56, 37, 16], 'blue', { split: '' });
    // sight glasses (floats are partials)
    m.box('h2_glass', [55, 16, -10.6], [57, 44, -9.4], 'st', { split: '', face: { north: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    m.box('o2_glass', [62.6, 16, 19], [63.8, 44, 21], 'st', { split: '', face: { east: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    // H2 scrubber column and the H2 outlet to the top face; O2 vent to the top face
    m.cylY('scrubber', [72, 8], 4, 4, 58, 'pnt');
    m.headUp('scrubber_top', [72, 8], 4, 58, 2, 2, 'pnt');
    m.box('h2_to_scrubber', [56, 55, -0.8], [72, 57, 8.8], 'yel', { split: '' });
    m.cylY('h2_out', [72, 8], 1.4, 60, 63.9, 'yel');
    m.box('o2_to_vent', [55, 53, 7.4], [57, 55, 20], 'blue', { split: '' });
    m.cylY('o2_out', [56, 8], 1.2, 55, 63.9, 'blue');
    // lye loop on the south side: return lines, pump, cooler, filter; water and cooling water inlets
    m.box('lye_return', [-40, 6, 24], [56, 8, 26], 'pnt', { split: 'x' });
    m.box('lye_drop_h2', [55, 6, 4], [57, 8, 24], 'pnt', { split: '' });
    m.prism('lye_pump', 'z', [40, 12], 3, 22, 28, 'blue', { split: '', ends: ['north', 'south'] });
    m.box('lye_pump_motor', [36, 4, 28], [44, 12, 30], 'motor', { split: '' });
    m.box('lye_cooler', [16, 4, 22], [30, 20, 29], 'st', { split: '' });
    for (let x = 17; x < 30; x += 2) m.box(`cooler_plate_${x}`, [x, 5, 29], [x + 1, 19, 29.6], 'ci', { split: '' });
    m.box('lye_filter', [2, 4, 23], [8, 16, 29], 'pnt', { split: '' });
    m.box('water_in', [39, 4, 30], [41, 10, 31.9], 'gw', { split: '' });
    m.box('cw_in', [-9, 4, 26], [-7, 10, 31.9], 'gw', { split: '' });
    m.box('cw_line', [-9, 8, 26], [16, 10, 28], 'gw', { split: 'x' });
    m.decal('plate', [-30, 24, -6.4], [-24, 27.6, -5.8], 'blue', 'north', 'pl.maker');
    return m;
  }
  const floatAt = (x, z, d) => () => { const m = mk(); m.box('float', d === 'n' ? [x - 1.4, 20, z - 0.8] : [x - 0.8, 20, z - 1.4], d === 'n' ? [x + 1.4, 22, z + 0.8] : [x + 0.8, 22, z + 1.4], 'red', { split: '' }); return m; };

  // ------------------------------------------------------------------ compressor
  function compressor() {
    const m = mk();
    m.box('skid', [-32, 0, -13], [42, 3, 29], 'frame', { split: 'xz' });
    // motor along z and its base
    m.box('motor_base', [-26, 3, -12], [-10, 5, 4], 'frame', { split: '' });
    m.prism('motor', 'z', [-18, 10], 5.6, -11, 3, 'motor', { split: '', ends: ['south'] });
    m.box('motor_box', [-24, 15, -6], [-18, 19, 0], 'motor', { split: '' });
    // crankcase with the shaft stub out north to the flywheel, two diaphragm heads on top
    m.box('crankcase', [-2, 3, -4], [20, 24, 18], 'blue', { split: '' });
    m.prism('crank_stub', 'z', [9, 14], 2, -11, -4, 'bright', { split: '' });
    for (const [x, s] of [[2, 1], [16, 2]]) {
      m.cylY(`head_${s}`, [x, 7], s === 1 ? 7 : 5.6, 24, 29, 'ci', { cap: 'vver/flange' });
      m.nutsY(`head_bolt_${s}`, [x, 7], s === 1 ? 6.2 : 5, 12, 29, 1, 1, 'bright');
    }
    // belt guard over the drive, belts
    m.box('belt_top', [-18, 18.6, -14.6], [9, 19.2, -11.4], 'dark', { split: 'x' });
    m.box('belt_bot', [-18, 6.4, -14.6], [9, 7.2, -11.4], 'dark', { split: 'x' });
    m.box('belt_guard', [-26, 2, -16], [20, 26, -15.4], 'yel', { split: 'x' });
    // intercooler and aftercooler, oil skid, control panel
    m.prism('intercooler', 'x', [16, 24], 2.6, 22, 40, 'st', { split: '', ends: ['west', 'east'] });
    m.prism('aftercooler', 'x', [8, 24], 2.6, 22, 40, 'st', { split: '', ends: ['west', 'east'] });
    m.box('oil_unit', [-30, 3, 10], [-14, 13, 26], 'pnt', { split: '' });
    m.box('panel', [26, 3, -12], [40, 30, -6], 'pnt', { split: '' });
    m.plate('panel_gauge', [30, 22, -12.1], [34, 26, -12], 'st', 'north', 'material/gauge_dial');
    m.box('cable_out', [23, 6, -16], [26, 10, -12], 'dark', { split: '' });
    // gas piping: in from the west to the 1st stage head, between heads via the intercooler, out east via aftercooler
    m.box('h2_in', [-32, 11, 7], [-4, 13, 9], 'yel', { split: 'x' });
    m.box('h2_in_up', [-4, 11, 7], [-2, 27, 9], 'yel', { split: '' });
    m.box('h2_mid', [2, 29, 6], [22, 31, 8], 'yel', { split: '' });
    m.box('h2_out', [40, 7, 7], [47.9, 9, 9], 'yel', { split: '' });
    m.box('cw_in', [7, 3, 18], [9, 5, 31.9], 'gw', { split: 'z' });
    m.decal('plate', [4, 12, -4.6], [10, 15.6, -4], 'blue', 'north', 'pl.maker');
    return m;
  }
  function flywheel() { const m = mk(); m.prism('flywheel', 'z', [9, 14], 6, -14.6, -11.4, 'ci', { split: '', ends: ['north', 'south'] }); m.box('fly_spoke', [8.4, 8.4, -14.8], [9.6, 19.6, -14.6], 'st', { split: '' }); return m; }
  function motorSheave() { const m = mk(); m.prism('sheave', 'z', [-18, 10], 3.4, -14.6, -11.4, 'ci', { split: '', ends: ['north', 'south'] }); return m; }

  // ------------------------------------------------------------------ tube bank
  function tubeBank() {
    const m = mk();
    m.box('base', [-48, 0, -14], [62, 3, 30], 'frame', { split: 'xz' });
    for (const x of [-46, 52]) {
      m.box(`bulkhead_${x}`, [x, 3, -12], [x + 2, 42, 28], 'frame', { split: '' });
      for (const z of [-14, 28]) m.box(`post_${x}_${z}`, [x - 2, 0, z], [x + 4, 46, z + 2], 'frame', { split: 'y' });
    }
    m.box('top_rail_n', [-48, 44, -14], [56, 46, -12], 'frame', { split: 'x' });
    m.box('top_rail_s', [-48, 44, 28], [56, 46, 30], 'frame', { split: 'x' });
    for (const y of [10, 22, 34]) for (const z of [-4, 8, 20]) {
      m.prism(`tube_${y}_${z}`, 'x', [y, z], 4.5, -44, 52, 'green', { split: 'x' });
      for (const x of [-46, 52]) m.prism(`tube_end_${y}_${z}_${x}`, 'x', [y, z], 3.2, x, x + 2, 'ci', { split: '', ends: [x < 0 ? 'west' : 'east'] });
      m.box(`tube_valve_${y}_${z}`, [54, y - 1, z - 1], [56, y + 1, z + 1], 'red', { split: '' });
    }
    m.box('manifold', [56, 8, 6.8], [58, 38, 9.2], 'st', { split: '' });
    for (const y of [10, 22, 34]) m.box(`manifold_branch_${y}`, [55, y - 0.6, -4], [57, y + 0.6, 20], 'st', { split: '' });
    m.box('main_valve', [58, 20, 4], [62, 28, 12], 'red', { split: '' });
    m.box('out_pipe', [62, 23, 7], [63.9, 25, 9], 'yel', { split: '' });
    m.prism('gauge', 'x', [30, 8], 2.2, 61, 62, 'st', { split: '', ends: ['east'] });
    m.plate('gauge_face', [62, 27.8, 5.8], [62.1, 32.2, 10.2], 'st', 'east', 'material/gauge_dial');
    m.box('gauge_stem', [58, 29.4, 7.4], [61, 30.6, 8.6], 'st', { split: '' });
    m.cylY('vent_stack', [56, 8], 1, 38, 47.9, 'yel');
    m.box('burst_disc', [54.6, 38, 6.6], [57.4, 40, 9.4], 'red', { split: '' });
    return m;
  }
  function tbNeedle() { const m = mk(); m.box('needle', [62.15, 29.7, 7.7], [62.3, 31.9, 8.3], 'red', { split: '' }); return m; }

  // ------------------------------------------------------------------ low-pressure receiver
  function gasholder() {
    const m = mk();
    for (const a of [45, 135, 225, 315]) {
      m.postY(`leg_${a}`, [8, 8], 20, a, 3, 0, 26, 'frame', { split: '' });
      m.postY(`foot_${a}`, [8, 8], 20, a, 6, 0, 1, 'conc', { split: '' });
    }
    m.headDown('bot', [8, 8], 24, 28, 12, 4, 'pnt');
    m.cylY('shell', [8, 8], 24, 28, 172, 'pnt');
    for (const y of [64, 100, 136]) m.ringY(`seam_${y}`, [8, 8], 24.3, 0.6, y, y + 0.8, 'pnt');
    m.headUp('top', [8, 8], 24, 172, 12, 4, 'pnt');
    m.nozzle('h2_in', 'x', [24, 8], -10, -32, 3.6, 2.6, 'yel', { nuts: 8 });
    m.nozzle('h2_out', 'x', [24, 8], 26, 48, 3.6, 2.6, 'yel', { nuts: 8 });
    m.cylY('drain', [8, 8], 1.2, 0, 17, 'gw');
    m.box('drain_valve', [6, 4, 6], [10, 7, 10], 'red', { split: '' });
    // safety valve and the vent stack above the top
    m.cylY('psv_nz', [20, 8], 1.4, 180, 184, 'pnt');
    m.box('psv', [17.6, 184, 5.6], [22.4, 188, 10.4], 'red', { split: '' });
    m.cylY('vent', [8, 8], 1.2, 184, 207.9, 'yel');
    m.box('vent_link', [8, 186, 7.4], [18, 187.2, 8.6], 'yel', { split: '' });
    // top platform with rail, caged ladder up the north side
    m.cylY('platform', [8, 8], 14, 183, 184, 'grate', { cap: 'vver/grating_disc', bottom: true });
    m.railY('platform_rail', [8, 8], 13.6, 184, 'yel');
    m.ladderZ('ladder', 4, 12, -18, -1, 0, 184, 'yel', 3);
    // pressure gauge on the north side
    m.box('pg_stem', [15.4, 36, -18], [16.6, 38, -16], 'st', { split: '' });
    m.prism('pg', 'z', [16, 40], 2.2, -18, -17.4, 'st', { split: '', ends: ['north'] });
    m.plate('pg_face', [13.8, 37.8, -18.1], [18.2, 42.2, -18], 'st', 'north', 'material/gauge_dial');
    m.decal('plate', [12, 50, -16.6], [18, 53.6, -16], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function ghNeedle() { const m = mk(); m.box('needle', [15.7, 39.7, -18.3], [16.3, 41.9, -18.15], 'red', { split: '' }); return m; }

  // ------------------------------------------------------------------ fuel cell module and inverter
  function fuelCell(on) {
    const m = mk();
    m.box('skid', [-16, 0, 0.5], [32, 2, 15.5], 'frame', { split: 'x' });
    // module
    m.box('module', [-15.6, 2, 2], [3.4, 37, 14], 'blue', { split: '' });
    for (let y = 6; y < 34; y += 2) m.box(`louvre_${y}`, [-14, y, 1.6], [-4, y + 0.8, 2], 'dark', { split: '' });
    m.box('door_line', [-3.4, 4, 1.8], [-3, 35, 2], 'dark', { split: '' });
    m.box('h2_panel', [-16, 4, 4], [-15.6, 14, 12], 'yel', { split: '' });
    m.box('h2_in', [-16, 7, 7], [-15.9, 9, 9], 'yel', { split: '' });
    m.box('lamp_frame', [-1.6, 30, 1.6], [1.6, 33, 2], 'ci', { split: '' });
    m.box(on ? 'glow_lamp' : 'lamp', [-1, 30.6, 1.4], [1, 32.4, 1.6], on ? 'gw' : 'dark', { split: '' });
    m.decal('plate', [-12, 26, 1.4], [-6, 29.6, 2], 'blue', 'north', 'pl.maker');
    // radiator on top with two fan rings (fans are partials), exhaust hood
    m.box('radiator', [-15, 37, 3], [2, 42, 13], 'st', { split: '' });
    for (const x of [-10.5, -3.5]) m.ringY(`fan_ring_${x}`, [x, 8], 3.6, 0.6, 42, 44, 'dark');
    m.box('exhaust_hood', [-12, 44, 4], [-4, 47.9, 12], 'grate', { split: '' });
    m.cylY('drain', [-8, 8], 0.8, 0, 2, 'gw');
    // cable tray and the inverter cabinet with its door, louvres, display and AC gland
    m.box('tray', [3.4, 30, 5], [13, 32, 11], 'ci', { split: '' });
    m.box('inverter', [13, 2, 3], [31.4, 37, 15], 'pnt', { split: '' });
    for (const x of [21.8, 22.2]) m.box(`inv_door_${x}`, [x, 4, 2.8], [x + 0.2, 35, 3], 'dark', { split: '' });
    for (let y = 6; y < 14; y += 2) m.box(`inv_louvre_${y}`, [15, y, 2.6], [21, y + 0.8, 3], 'dark', { split: '' });
    m.box('inv_display', [24, 26, 2.6], [29, 30, 3], on ? 'gw' : 'dark', { split: '' });
    m.box('ac_gland', [31.4, 6, 6], [32, 10, 10], 'dark', { split: '' });
    return m;
  }
  const fan = (x) => () => {
    const m = mk();
    m.cylY(`hub_${x}`, [x, 8], 0.8, 42.4, 43.6, 'ci');
    m.box(`blade_a_${x}`, [x - 3, 42.8, 7.6], [x + 3, 43.2, 8.4], 'dark', { split: '' });
    m.box(`blade_b_${x}`, [x - 0.4, 42.8, 5], [x + 0.4, 43.2, 11], 'dark', { split: '' });
    return m;
  };

  const UNITS = {
    electrolyser: { id: 'h2_electrolyser_real', draw: electrolyser, parts: { h2_level: { draw: floatAt(56, -10, 'n'), shift: [-48, 0, 0] }, o2_level: { draw: floatAt(63.2, 20, 'e'), shift: [-48, 0, 0] } } },
    compressor: { id: 'h2_compressor_real', draw: compressor, parts: { flywheel: { draw: flywheel, shift: [0, 0, 0] }, motor_sheave: { draw: motorSheave, shift: [8, 0, 0] } } },
    tube_bank: { id: 'h2_tube_bank_real', draw: tubeBank, parts: { pressure_needle: { draw: tbNeedle, shift: [-48, 0, 0] } } },
    gasholder: { id: 'h2_gasholder_real', draw: gasholder, parts: { pressure_needle: { draw: ghNeedle, shift: [0, -24, 4] } } },
    fuel_cell: { id: 'h2_fuel_cell_real', draw: () => fuelCell(false), parts: { fan_0: { draw: fan(-10.5), shift: [0, -24, 0] }, fan_1: { draw: fan(-3.5), shift: [0, -24, 0] } } },
    fuel_cell_on: { id: 'h2_fuel_cell_real_on', draw: () => fuelCell(true), parts: {} },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return mk().merge(d.draw(), dx, dy, dz, ''); };
  window.H2 = {
    UNITS, partial,
    parts: (k) => Object.keys(UNITS[k].parts),
    model: (k) => UNITS[k].draw(),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'hydrogen'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'hydrogen'),
  };
})();
