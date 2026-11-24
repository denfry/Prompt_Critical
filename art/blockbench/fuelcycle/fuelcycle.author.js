// Nuclear fuel cycle units at real scale (1 block = 1 m, 16 px = 1 m), drawn from the outside at the level of public
// encyclopaedic descriptions: a single gas centrifuge and a cascade section; a UF6 flame tower fluorinator, an ANSI
// 30B cylinder and its steam autoclave; a rotary pellet press and a hydrogen pusher sintering furnace; a fuel rod end
// plug welding station, a VVER-440 assembly stand and the VVER-440 assembly as an item block.
// Sheet: docs/research/2026-11-24-fuelcycle/fuelcycle-dimensions.md (disk only); this header is the final word where
// they differ.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then:
//   FC.paint(repo); for k in UNITS: FC.build(repo, k); /* wait */ FC.save(repo, k)
//   for (const p of FC.parts(k)) { FC.buildPartial(repo, k, p); FC.savePartial(repo, k, p) }
//
// Every unit: controller (0, 0, 0), front north.
// centrifuge (gas_centrifuge_real, open variant gas_centrifuge_real_open without the casing), x 0, y 0..1, z 0:
//   feed/product/tails on the top face of (0, 1, 0) at (8, 32, 8); power on the bottom face of (0, 0, 0) at (8, 0, 8).
//   Partial rotor: turns about y at (8, 8); no shift.
// cascade (centrifuge_cascade_section_real), x 0..3, y 0..1, z 0, tiles along x: headers (feed yellow, product red,
//   tails blue) at y 30.4, z 6.6 / 8 / 9.4 on the west face of (0, 1, 0) at (0, 30.4, 8) and the east face of
//   (3, 1, 0) at (64, 30.4, 8); power on the bottom face of (0, 0, 0) at (8, 0, 8). No partials.
// fluorinator (fluorinator_real), x -1..1, y 0..5, z -1..1: UF4 in on the top face of (0, 5, 0) at (8, 96, 8); F2 in
//   on the west face of (-1, 5, 0) at (-16, 84, 8); UF6 out on the east face of (1, 2, 0) at (32, 36, 8); ash out on
//   the bottom face of (0, 0, 0) at (8, 0, 8); heater power on the west face of (-1, 0, 0) at (-16, 8, 8). Partial
//   feeder_coupling: turns about x at (y 88, z 8); stored -64 y.
// cylinder (uf6_cylinder_30b_real), x -1..1, y 0, z 0: valve on the east face of (1, 0, 0) at (32, 7, 8). Partial
//   valve_cap: lifted off; stored -16 x.
// autoclave (uf6_autoclave_real, running variant uf6_autoclave_real_on with the control lamp lit), x -2..2, y 0..1,
//   z -1..1: steam in on the top face of (0, 1, 0) at (8, 32, 8); condensate on the bottom face of (1, 0, 0) at
//   (24, 0, 8); UF6 pigtail on the east face of (2, 0, 0) at (48, 15, 8); cylinder through the door on the west face
//   of (-2, 0, 0) at (-32, 15, 8). Partial door: swings about y at (x -28, z -6) through 0..100 deg; stored +16 x.
// press (pellet_press_real), x -1..1, y 0..1, z -1..1: powder in on the top face of (0, 1, -1) at (8, 32, -6.5);
//   pellets out on the east face of (1, 0, 0) at (32, 14.5, 8); power on the west face of (-1, 0, 0) at (-16, 6, 8).
//   Partials turret, upper_punches, lower_punches: turn about y at (8, 8) together; no shifts.
// furnace (sintering_furnace_real, running variant sintering_furnace_real_on with the peepholes and flame curtains
//   glowing), x -4..4, y 0..2, z -1..1: boats in on the west face of (-4, 1, 0) at (-64, 20, 8); boats out on the east
//   face of (4, 1, 0) at (80, 20, 8); H2 in on the top face of (0, 2, 0) at (8, 48, 8); power on the north face of
//   (0, 1, -1) at (8, 24, -16); cooling water on the south face of (3, 0, 1) at (56, 8, 32); exhaust on the top face
//   of (-3, 2, 0) at (-40, 48, 8). Partials pusher_ram (slides x 0..16; stored +56 x), entry_door (slides y 0..8;
//   stored +48 x), exit_door (slides y 0..8; stored -64 x).
// welder (fuel_rod_welder_real), x -2..1, y 0..1, z 0: rods on the west face of (-2, 0, 0) at (-32, 14.9, 8); power
//   on the bottom face of (-1, 0, 0) at (-8, 0, 8). Partials rod (slides x -8..0; stored +14 x) and weld_head (turns about x at
//   (y 14.9, z 8)); weld_head not shifted.
// stand (fuel_assembly_stand_real), x -1..1, y 0..4, z -1..1: the assembly seats at (8, 6, 8); power on the west face
//   of (-1, 0, 0) at (-16, 8, 8). Partials turntable (turns about y at (8, 8)), hoist_hook (slides y 0..-24; stored
//   -48 y), gauge_carriage (slides y 0..48; no shift).
// assembly (vver440_fuel_assembly_real), x 0, y 0..3, z 0, y 0..51.2. Partial head: lifted off, slides y 0..8;
//   stored -32 y.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    frame: { tex: 'plant/structure', P: 16 }, blue: { tex: 'plant/blue', P: 16 },
    hot: { tex: 'fuelcycle/hot', P: 16 }, glass: { tex: 'fuelcycle/glass', P: 16 },
  });
  const mk = (p) => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: p || 'vver/paint' });
  const S = { split: '' };

  function paint(root) {
    const { img, put, add, mix, rng, grain, writePng } = PCART;
    const out = (n, im) => writePng(im, require('path').join(root, PCART.ASSETS, 'textures/block/fuelcycle', n + '.png'));
    let im = img(32, 32), r = rng(401);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = Math.sin(x * 0.4 + y * 0.25) * 0.5 + 0.5;
      put(im, x, y, add(mix([255, 150, 50], [255, 236, 180], t * 0.7 + r() * 0.3), 0));
    }
    out('hot', im);
    im = img(32, 32); r = rng(411);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.abs(x - y - 4), band = d < 3 ? 0.45 : d < 5 ? 0.2 : 0;
      put(im, x, y, add(mix([54, 66, 78], [170, 186, 196], band), (r() - 0.5) * 3));
    }
    out('glass', im);
    return 2;
  }
  // octagon column (rounded-off hexagon at this size) about a vertical axis, across flats d
  function oct(m, name, c, d, y0, y1, mat) {
    const a = d / 2, b = a * Math.tan(Math.PI / 8);
    m.box(`${name}_a`, [c[0] - a, y0, c[1] - b], [c[0] + a, y1, c[1] + b], mat, { split: 'y' });
    m.box(`${name}_b`, [c[0] - b, y0, c[1] - a], [c[0] + b, y1, c[1] + a], mat, { split: 'y' });
    for (const g of [45, -45]) m.box(`${name}_${g}`, [c[0] - a, y0, c[1] - b], [c[0] + a, y1, c[1] + b], mat, { split: '', rot: { axis: 'y', angle: g, origin: [c[0], 8, c[1]] } });
  }

  // ------------------------------------------------------------------ centrifuges
  function centrifuge(open) {
    const m = mk();
    m.box('anchor', [3, 0, 3], [13, 1, 13], 'ci', S);
    m.prismY('drive', [8, 8], 2.6, 1, 4, 'dark', { ends: ['up'] });
    if (open) {
      m.prismY('casing_flange', [8, 8], 2.4, 4, 5, 'pnt', { ends: ['up'] });
    } else {
      m.cylY('casing', [8, 8], 2, 4, 30, 'pnt');
      m.prismY('cap', [8, 8], 2.4, 30, 31, 'st', { ends: ['up'] });
      for (const [x, z, mat] of [[7, 7, 'yel'], [9, 7, 'red'], [8, 9.2, 'blue']]) m.box(`conn_${x}_${z}`, [x - 0.3, 31, z - 0.3], [x + 0.3, 32, z + 0.3], mat, S);
    }
    m.box('cable', [7.6, 0, 3], [8.4, 0.6, 8], 'dark', S);
    return m;
  }
  function rotor() { const m = mk(); m.cylY('rotor', [8, 8], 1.2, 5, 29, 'bright'); m.prismY('rotor_top', [8, 8], 1.2, 29, 29.5, 'bright', { ends: ['up'] }); return m; }
  function cascade() {
    const m = mk();
    for (const z of [1, 13]) m.box(`rail_${z}`, [0, 0, z], [64, 1, z + 2], 'frame', { split: 'x' });
    m.box('cable_tray', [0, 0, 7], [64, 0.6, 9], 'dark', { split: 'x' });
    for (let i = 0; i < 8; i++) for (const z of [4, 12]) {
      const x = 4 + 8 * i, n = `${i}_${z}`;
      m.box(`anchor_${n}`, [x - 2.6, 0, z - 2.6], [x + 2.6, 1, z + 2.6], 'ci', S);
      m.prismY(`drive_${n}`, [x, z], 2.6, 1, 3, 'dark', { ends: ['up'] });
      m.cylY(`casing_${n}`, [x, z], 2, 3, 26, 'pnt', { seed: i });
      m.prismY(`cap_${n}`, [x, z], 2.4, 26, 27, 'st', { ends: ['up'] });
      m.box(`riser_${n}`, [x - 0.3, 27, z - 0.3], [x + 0.3, 29.4, z + 0.3], 'st', S);
      m.box(`branch_${n}`, [x - 0.3, 29.4, z < 8 ? 4 : 6.6], [x + 0.3, 30, z < 8 ? 9.4 : 12], 'st', S);
    }
    for (const [z, mat] of [[6.6, 'yel'], [8, 'red'], [9.4, 'blue']]) m.prism(`header_${z}`, 'x', [30.4, z], 0.6, 0, 64, mat, { split: '' });
    for (const x of [0, 31.5]) m.box(`hanger_${x}`, [x, 1, 7.4], [x + 1, 29.8, 8.6], 'frame', S);
    return m;
  }

  // ------------------------------------------------------------------ fluorinator
  function fluorinator() {
    const m = mk();
    for (const [x, z] of [[-15, -15], [29, -15], [-15, 29], [29, 29]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 2, 76, z + 2], 'frame', { split: 'y' });
    m.box('platform', [-16, 76, -16], [32, 77, 32], 'grate', { split: 'xz' });
    for (const y of [80.5, 84.4]) {
      m.box(`rail_n_${y}`, [-16, y, -16], [32, y + 0.6, -15.4], 'yel', { split: 'x' });
      m.box(`rail_s_${y}`, [-16, y, 31.4], [32, y + 0.6, 32], 'yel', { split: 'x' });
      m.box(`rail_e_${y}`, [31.4, y, -16], [32, y + 0.6, 32], 'yel', { split: 'z' });
    }
    for (const x of [-4, 8, 20]) for (const z of [-16, 31.4]) m.box(`rail_post_${x}_${z}`, [x, 77, z], [x + 0.6, 85, z + 0.6], 'yel', S);
    for (const z of [-4, 8, 20]) m.box(`rail_post_e_${z}`, [31.4, 77, z], [32, 85, z + 0.6], 'yel', S);
    for (let y = 2; y < 76; y += 4) m.box(`ladder_rung_${y}`, [-15.6, y, -10], [-15, y + 0.5, -6], 'st', S);
    for (const z of [-10, -6.4]) m.box(`ladder_side_${z}`, [-15.6, 0, z], [-15, 77, z + 0.4], 'st', { split: 'y' });
    m.prismY('ash_can', [8, 8], 3, 0, 4, 'dark', { ends: ['up'] });
    m.cylY('cone', [8, 8], 3.6, 4, 12, 'pnt');
    m.cylY('tower', [8, 8], 4.8, 12, 80, 'bright');
    for (let y = 16; y < 76; y += 8) m.ringY(`heater_${y}`, [8, 8], 5.4, 0.8, y, y + 2, 'dark');
    m.prismY('head', [8, 8], 5.6, 80, 81, 'forged', { ends: ['up'] });
    m.box('hopper', [2, 81, 2], [14, 92, 14], 'bright', S);
    m.box('hopper_inlet', [6, 92, 6], [10, 95.9, 10], 'st', S);
    m.box('feeder_trough', [14, 86, 6], [21.5, 90, 10], 'st', S);
    m.box('feeder_motor', [23, 85, 5], [29, 91, 11], 'motor', S);
    m.box('f2_in', [-16, 83, 7], [4.6, 85, 9], 'yel', S);
    m.box('f2_drop', [2.6, 81, 7], [4.6, 83, 9], 'yel', S);
    m.box('offtake', [12.8, 17, 7], [22, 19, 9], 'bright', S);
    m.cylY('filter', [26, 8], 4, 4, 40, 'bright');
    m.prismY('filter_top', [26, 8], 4.4, 40, 42, 'forged', { ends: ['up'] });
    m.box('uf6_out', [30, 35, 7], [32, 37, 9], 'bright', S);
    m.box('heater_box', [-16, 4, 5], [-12, 12, 11], 'pnt', S);
    m.box('heater_conduit', [-13, 12, 7], [-12, 40, 8], 'dark', { split: 'y' });
    m.box('heater_feed', [-12, 39, 7], [3, 40, 8], 'dark', S);
    m.decal('plate', [-15.4, 14, 0], [-15, 18, 6], 'pnt', 'west', 'pl.maker');
    return m;
  }
  function feederCoupling() { const m = mk(); m.prism('coupling', 'x', [88, 8], 2, 21.5, 23, 'red', { split: '', ends: ['east', 'west'] }); return m; }

  // ------------------------------------------------------------------ 30B cylinder and autoclave
  function cylinder30b() {
    const m = mk(), c = [7, 8];
    m.prism('shell', 'x', c, 6.08, -8.5, 24.5, 'pnt', { split: '', ends: ['east', 'west'] });
    for (const x of [2, 14]) m.ring(`ring_${x}`, 'x', c, 6.7, 0.62, x, x + 0.8, 'pnt');
    m.box('valve', [24.5, 6.4, 7.4], [25.6, 7.6, 8.6], 'br', S);
    m.box('plug', [-9.2, 6.5, 7.5], [-8.5, 7.5, 8.5], 'br', S);
    for (const [x0, x1] of [[-4, 0], [16, 20]]) for (const [z0, z1] of [[0.5, 3], [13, 15.5]]) m.box(`chock_${x0}_${z0}`, [x0, 0, z0], [x1, 2, z1], 'wood', S);
    m.decal('plate', [6, 6, 1.6], [10, 8.4, 1.9], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function valveCap() {
    const m = mk();
    for (const [f, t] of [[[24.5, 5, 6], [27.5, 5.5, 10]], [[24.5, 8.5, 6], [27.5, 9, 10]], [[27, 5.5, 6], [27.5, 8.5, 10]], [[24.5, 5.5, 6], [27, 8.5, 6.5]], [[24.5, 5.5, 9.5], [27, 8.5, 10]]]) m.box(`cap_${f[0]}_${f[1]}_${f[2]}`, f, t, 'st', S);
    return m;
  }
  function autoclave(on) {
    const m = mk(), c = [15, 8];
    m.prism('shell', 'x', c, 12.8, -25, 40, 'st', { split: '' });
    m.prism('head_1', 'x', c, 10.5, 40, 42.5, 'st', { split: '', ends: ['east'] });
    m.prism('head_2', 'x', c, 6, 42.5, 44, 'st', { split: '', ends: ['east'] });
    m.ring('lock_ring', 'x', c, 14, 2, -27, -25, 'forged');
    m.box('hinge', [-29, 6, -8], [-27, 24, -5.6], 'forged', S);
    for (const x of [-16, 30]) m.box(`saddle_${x}`, [x - 3, 0, -2], [x + 3, 6, 18], 'pnt', S);
    m.cylY('steam_in', [8, 8], 1.2, 27, 31.9, 'gs');
    m.cylY('safety_valve', [24, 8], 0.8, 27, 31, 'red');
    m.cylY('condensate', [24, 8], 0.8, 0, 2.4, 'gw');
    m.prism('pigtail', 'x', c, 0.8, 44, 47.9, 'yel', { split: '' });
    m.box('pigtail_valve', [45, 13.5, 6.5], [47, 16.5, 9.5], 'br', S);
    m.box('control_box', [-8, 4, -15.9], [0, 14, -12], 'pnt', S);
    for (const x of [-7.5, -0.5]) m.box(`control_leg_${x}`, [x, 0, -15], [x + 0.5, 4, -14.5], 'st', S);
    m.box(on ? 'glow_lamp' : 'lamp', [-7, 11, -16], [-5, 13, -15.9], on ? 'gw' : 'dark', S);
    m.box('gauge', [-3.5, 9, -16], [-1, 11.5, -15.9], 'bright', S);
    m.box('cable', [-4, 14, -14], [-3, 18, -13], 'dark', S);
    m.decal('plate', [12, 4, -4.9], [18, 7, -4.6], 'st', 'north', 'pl.maker');
    return m;
  }
  function autoclaveDoor() {
    const m = mk(), c = [15, 8];
    m.prism('door', 'x', c, 13.4, -30, -27, 'forged', { split: '', ends: ['east', 'west'] });
    m.box('door_handle', [-30.6, 13, 6], [-30, 17, 10], 'st', S);
    m.box('door_arm', [-30, 14, -6], [-28, 16, 2], 'forged', S);
    return m;
  }

  // ------------------------------------------------------------------ pellet press and sintering furnace
  function press() {
    const m = mk();
    m.box('cabinet', [-12, 0, -12], [28, 14, 28], 'pnt');
    m.box('table_plate', [-8, 14, -8], [24, 14.5, 24], 'st', S);
    for (const [x, z] of [[-3, -3], [17, -3], [-3, 17], [17, 17]]) m.box(`column_${x}_${z}`, [x, 14.5, z], [x + 2, 26, z + 2], 'bright', S);
    m.box('head', [-4, 26, -4], [20, 29, 20], 'pnt', S);
    m.box('roller_housing', [5, 24.5, 1], [11, 26, 5], 'ci', S);
    m.box('hopper', [2, 22, -10], [14, 31.9, -3], 'bright', S);
    m.box('chute_in', [7, 18.5, -5], [9, 22, -3], 'bright', S);
    m.box('feed_shoe', [5, 17.5, -3], [11, 18.5, 2], 'st', S);
    m.box('pellet_chute', [15, 14, 7], [32, 15, 9], 'bright', S);
    m.box('chute_lip', [15, 15, 6.6], [32, 15.6, 7], 'bright', S);
    m.box('motor', [-16, 2, 4], [-12, 10, 12], 'motor', S);
    m.box('panel', [-4, 4, -12.4], [6, 11, -12], 'dark', S);
    m.decal('plate', [10, 6, -12.4], [16, 9, -12], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function turret() {
    const m = mk();
    m.prismY('die_table', [8, 8], 7, 15, 17.5, 'forged', { ends: ['up', 'down'] });
    m.prismY('punch_guide', [8, 8], 6.5, 21, 23, 'forged', { ends: ['up', 'down'] });
    m.prismY('shaft', [8, 8], 1, 17.5, 21, 'st');
    return m;
  }
  const punches = (y0, y1) => () => {
    const m = mk();
    for (let k = 0; k < 16; k++) {
      const a = k * Math.PI / 8, x = 8 + 5.5 * Math.cos(a), z = 8 + 5.5 * Math.sin(a);
      m.box(`punch_${k}`, [x - 0.3, y0, z - 0.3], [x + 0.3, y1, z + 0.3], 'bright', S);
    }
    return m;
  };
  function furnace(on) {
    const m = mk();
    for (const x of [-60, -40, -14, 10, 30, 46, 66]) for (const z of [-2, 16]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 2, 8, z + 2], 'frame', S);
    m.box('pusher_cyl', [-64, 17, 5], [-58, 23, 11], 'pnt', S);
    m.box('boat_table', [-64, 11, 4], [-57, 12, 12], 'bright', S);
    m.box('entry_vestibule', [-56, 8, -2], [-48, 32, 18], 'pnt', S);
    m.box('preheat', [-48, 8, -4], [-16, 32, 20], 'pnt');
    m.box('hot_zone', [-16, 4, -10], [32, 38, 26], 'pnt');
    m.box('cooling', [32, 8, -4], [64, 32, 20], 'pnt');
    for (let x = 34; x < 63; x += 6) m.box(`jacket_${x}`, [x, 7.6, -4.4], [x + 1, 32.4, 20.4], 'gw', S);
    m.box('exit_vestibule', [64, 8, -2], [76, 32, 18], 'pnt', S);
    m.box('out_table', [76, 11, 4], [79.9, 12, 12], 'bright', S);
    for (const x of [-12, -4, 4, 12, 20, 28]) for (const z of [2, 12]) m.box(`terminal_${x}_${z}`, [x, 38, z], [x + 2, 41, z + 2], 'br', S);
    m.box('busbar_n', [-12, 40, 2], [30, 41, 4], 'br', { split: 'x' });
    m.box('busbar_down', [4, 28, -10.5], [12, 41, -9.8], 'br', S);
    m.box('busbar_in', [4, 20, -16], [12, 28, -10], 'br', S);
    for (const x of [-8, 4, 16]) m.box(on ? `glow_peep_${x}` : `peep_${x}`, [x, 18, -10.4], [x + 2, 20, -10], on ? 'hot' : 'dark', S);
    m.box('water_line', [-16, 6, -11], [32, 7, -10], 'gw', { split: 'x' });
    m.box('water_in', [55, 6, 20], [57, 10, 31.9], 'gw', S);
    m.cylY('stack', [-40, 8], 1.5, 32, 47.9, 'st');
    m.cylY('h2_in', [8, 8], 0.8, 38, 47.9, 'yel');
    for (const [x0, x1, tag] of [[-57.5, -56, 'in'], [76, 77.5, 'out']]) {
      m.box(`burner_${tag}`, [x0, 28, 0], [x1, 29, 16], 'yel', S);
      if (on) m.box(`glow_flame_${tag}`, tag === 'in' ? [-57.6, 25, 1] : [77.2, 25, 1], tag === 'in' ? [-57.2, 28, 15] : [77.6, 28, 15], 'hot', S);
    }
    m.decal('plate', [-4, 28, -10.4], [2, 31, -10], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function pusherRam() { const m = mk(); m.box('ram', [-58, 19, 7], [-50, 21, 9], 'bright', S); m.box('ram_plate', [-50.5, 17, 5], [-50, 23, 11], 'st', S); return m; }
  const furnaceDoor = (x0) => () => { const m = mk(); m.box('door', [x0, 12, 1], [x0 + 1, 28, 15], 'st', S); m.box('door_rib', [x0 - 0.2, 19, 2], [x0 + 1.2, 21, 14], 'forged', S); return m; };

  // ------------------------------------------------------------------ fuel rods and assemblies
  function welder() {
    const m = mk();
    for (const x of [-28, -18, -8, 2]) {
      m.box(`stand_${x}`, [x, 0, 7], [x + 1, 13, 9], 'frame', S);
      m.box(`roller_${x}`, [x - 0.5, 13, 6.5], [x + 1.5, 14.6, 9.5], 'dark', S);
    }
    m.box('stand_rail', [-29, 6, 7.4], [4, 7, 8.6], 'frame', { split: 'x' });
    m.box('cabinet', [-12, 0, 1], [2, 12, 6], 'pnt', S);
    m.box('screen', [-10, 7, 0.6], [-4, 11, 1], 'dark', S);
    m.box('chamber', [15, 8, 2], [28, 26, 14], 'bright', S);
    m.box('window', [17, 14, 1.6], [26, 22, 2], 'glass', S);
    for (const [x, z] of [[16, 3], [26, 3], [16, 12], [26, 12]]) m.box(`chamber_leg_${x}_${z}`, [x, 0, z], [x + 1, 8, z + 1], 'st', S);
    m.prism('collar', 'x', [14.9, 8], 2, 13.5, 15, 'st', { split: '', ends: ['west'] });
    m.cylY('helium', [30, 12], 1.6, 0, 22, 'oil');
    m.prismY('helium_valve', [30, 12], 0.8, 22, 24, 'br', { ends: ['up'] });
    m.box('helium_line', [28, 22.5, 11.5], [30, 23.3, 12.3], 'br', S);
    m.decal('plate', [19, 9, 1.6], [24, 12, 2], 'bright', 'north', 'pl.maker');
    return m;
  }
  function rod() { const m = mk(); m.box('rod', [-30, 14.6, 7.7], [15, 15.2, 8.3], 'clad', { split: '' }); m.box('end_plug', [15, 14.6, 7.7], [15.4, 15.2, 8.3], 'st', S); return m; }
  function weldHead() { const m = mk(); m.ring('collet', 'x', [14.9, 8], 1.6, 0.6, 12.5, 13.5, 'br'); m.box('collet_jaw', [12.5, 14.3, 7.4], [13.5, 15.5, 8.6], 'br', S); return m; }
  function stand() {
    const m = mk();
    m.box('base', [-14, 0, -14], [30, 2, 30], 'frame', { split: 'xz' });
    for (const x of [-12, 24]) m.box(`column_${x}`, [x, 2, 6], [x + 4, 72, 10], 'pnt', { split: 'y' });
    m.box('top_beam', [-12, 72, 5], [28, 76, 11], 'pnt', { split: 'x' });
    m.box('hoist', [4, 68, 5], [12, 72, 11], 'motor', S);
    m.box('control', [-15.6, 2, 4], [-13.6, 14, 12], 'pnt', S);
    m.box('control_screen', [-16, 9, 5], [-15.6, 13, 11], 'dark', S);
    for (let y = 4; y < 70; y += 4) m.box(`ladder_rung_${y}`, [-12, y, 10], [-8, y + 0.5, 10.6], 'st', S);
    m.decal('plate', [-11, 20, 5.6], [-9, 24, 6], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function turntable() { const m = mk(); m.prismY('turntable', [8, 8], 5, 2, 5, 'st', { ends: ['up'] }); m.prismY('socket', [8, 8], 2, 5, 6, 'dark', { ends: ['up'] }); return m; }
  function hook() { const m = mk(); m.box('cable', [7.7, 60, 7.7], [8.3, 68, 8.3], 'dark', S); m.box('hook', [6.5, 58, 7], [9.5, 60, 9], 'yel', S); return m; }
  function gauge() {
    const m = mk();
    m.box('carriage', [20, 10, 4], [30, 14, 12], 'yel', S);
    m.box('arm', [10, 11, 7.5], [20, 12, 8.5], 'bright', S);
    m.box('probe', [9.6, 10.5, 7.2], [10.2, 12.5, 8.8], 'red', S);
    return m;
  }
  function assembly() {
    const m = mk();
    m.prismY('tail', [8, 8], 0.9, 0, 4, 'st', { ends: ['down'] });
    oct(m, 'shroud', [8, 8], 2.3, 4, 47, 'clad');
    return m;
  }
  function head() {
    const m = mk();
    oct(m, 'head', [8, 8], 2.7, 47, 51.2, 'st');
    for (const s of [1, -1]) m.box(`slot_${s}`, [8 + s * 1.35 - 0.1, 49, 7.5], [8 + s * 1.35 + 0.1, 50.4, 8.5], 'dark', S);
    return m;
  }

  const UNITS = {
    centrifuge: { id: 'gas_centrifuge_real', draw: () => centrifuge(false), parts: { rotor: { draw: rotor, shift: [0, 0, 0] } } },
    centrifuge_open: { id: 'gas_centrifuge_real_open', draw: () => centrifuge(true), parts: {} },
    cascade: { id: 'centrifuge_cascade_section_real', draw: cascade, parts: {} },
    fluorinator: { id: 'fluorinator_real', draw: fluorinator, parts: { feeder_coupling: { draw: feederCoupling, shift: [0, -64, 0] } } },
    cylinder: { id: 'uf6_cylinder_30b_real', draw: cylinder30b, parts: { valve_cap: { draw: valveCap, shift: [-16, 0, 0] } } },
    autoclave: { id: 'uf6_autoclave_real', draw: () => autoclave(false), parts: { door: { draw: autoclaveDoor, shift: [16, 0, 0] } } },
    autoclave_on: { id: 'uf6_autoclave_real_on', draw: () => autoclave(true), parts: {} },
    press: { id: 'pellet_press_real', draw: press, parts: {
      turret: { draw: turret, shift: [0, 0, 0] }, upper_punches: { draw: punches(17.5, 21), shift: [0, 0, 0] },
      lower_punches: { draw: punches(13.5, 15), shift: [0, 0, 0] } } },
    furnace: { id: 'sintering_furnace_real', draw: () => furnace(false), parts: {
      pusher_ram: { draw: pusherRam, shift: [56, 0, 0] }, entry_door: { draw: furnaceDoor(-57), shift: [48, 0, 0] },
      exit_door: { draw: furnaceDoor(76), shift: [-64, 0, 0] } } },
    furnace_on: { id: 'sintering_furnace_real_on', draw: () => furnace(true), parts: {} },
    welder: { id: 'fuel_rod_welder_real', draw: welder, parts: { rod: { draw: rod, shift: [14, 0, 0] }, weld_head: { draw: weldHead, shift: [0, 0, 0] } } },
    stand: { id: 'fuel_assembly_stand_real', draw: stand, parts: {
      turntable: { draw: turntable, shift: [0, 0, 0] }, hoist_hook: { draw: hook, shift: [0, -48, 0] }, gauge_carriage: { draw: gauge, shift: [0, 0, 0] } } },
    assembly: { id: 'vver440_fuel_assembly_real', draw: assembly, parts: { head: { draw: head, shift: [0, -32, 0] } } },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return mk().merge(d.draw(), dx, dy, dz, ''); };
  window.FC = {
    UNITS, partial, paint, model: (k) => UNITS[k].draw(),
    parts: (k) => Object.keys(UNITS[k].parts),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'fuelcycle'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'fuelcycle'),
  };
})();
