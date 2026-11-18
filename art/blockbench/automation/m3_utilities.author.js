// M3 plant utilities not yet in the mod, hand-authored for Blockbench (replaces the spec_automation.py,
// spec_metallurgy2.py and spec_grid2.py placeholders): instrument air (compressor and its flywheel, receiver,
// adsorption dryer), water treatment (chemical dosing skid and its pump head, decarbonizer), the process heater
// (electric, and the fired heater's controller front) and the ball-tube coal mill with its drum.
// Load inside Blockbench desktop after pcart.js, pcround.js, plantkit.js and futurekit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/automation/m3_utilities.author.js', 'utf8'))
//   M3U.paint('<repo>'); M3U.exportAll('<repo>')
//
// Front to the north. Footprints and pivots as the GDD rows and the placeholders: compressor 2x1x1 along x with
// the flywheel partial about x through (y 8, z 8) at the west end; receiver and dryer 1x2x1; dosing skid 2x1x1
// with the plunger head partial at x 22..26; decarbonizer 1x3x1 about the middle block (y -16..32); coal mill
// 3x3x3 about the middle block with the drum partial about x through (y 8, z 8).
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, vessel, flange, motor, gauge, ladder, skid } = FUTK;
  const D = 'm3util/', T = {};

  // Finned air-cooled cylinder: horizontal ribs 2 px apart, compressor blue with oily dark roots.
  T.cyl_fins = () => {
    const im = img(32, 32), r = rng(30101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(y % 4 < 2 ? [74, 100, 140] : [34, 44, 60], (r() - 0.5) * 8 + (y % 4 === 0 ? 14 : 0)));
    return im;
  };
  // Polyethylene chemical tank: milky white, a darker liquid band below the level line, moulded ribs.
  T.pe_tank = () => {
    const im = img(32, 32), r = grain(im, [214, 210, 196], 30111, 3, 4, 8);
    for (let y = 14; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, mix(get(im, x, y), [176, 168, 120], 0.35));
    for (let x = 0; x < 32; x++) put(im, x, 13, [150, 140, 100]);
    for (const y of [4, 24]) for (let x = 0; x < 32; x++) { put(im, x, y, add(get(im, x, y), 14)); put(im, x, y + 1, add(get(im, x, y + 1), -16)); }
    return im;
  };
  // Mill shell: dark steel with rows of liner bolt heads and a dust bloom.
  T.mill_shell = () => {
    const im = img(32, 32), r = grain(im, [70, 72, 74], 30121, 4, 6, 8);
    for (let y = 2; y < 32; y += 8) for (let x = 2; x < 32; x += 6) { rect(im, x, y, 2, 2, [118, 120, 120]); put(im, x + 1, y + 1, [40, 40, 40]); }
    for (let k = 0; k < 40; k++) put(im, r() * 32, r() * 32, [48, 46, 44]);
    return im;
  };
  // Girth gear: teeth across the face.
  T.gear = () => {
    const im = img(16, 16), r = rng(30131);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 3 === 0 ? [60, 60, 58] : [130, 128, 120], (r() - 0.5) * 8));
    return im;
  };
  // Heater coil seen through the electric heater's mesh: dull or glowing.
  T.coil_cold = () => { const im = img(16, 16), r = rng(30141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 < 2 ? add([90, 80, 72], (r() - 0.5) * 8) : [24, 24, 26]); return im; };
  T.coil_hot = () => { const im = img(16, 16), r = rng(30142); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 < 2 ? add([255, 136, 40], (r() - 0.5) * 30) : [60, 20, 10]); return im; };
  // Dew point indicator: silica gel window, blue when dry, pink when wet.
  T.gel_dry = () => { const im = img(16, 16), r = rng(30151); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([60, 110, 220], (r() - 0.5) * 50)); return im; };
  T.gel_wet = () => { const im = img(16, 16), r = rng(30152); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([230, 120, 170], (r() - 0.5) * 50)); return im; };
  T.air_blue = FUTK.enamel([72, 110, 170], 30161, 3);

  const MATX = {
    cfin: { tex: D + 'cyl_fins', P: 16 }, pe: { tex: D + 'pe_tank', P: 16 }, mshell: { tex: D + 'mill_shell', P: 16 },
    gear: { tex: D + 'gear', P: 8 }, coil0: { tex: D + 'coil_cold', P: 8 }, coil1: { tex: D + 'coil_hot', P: 8 },
    gel0: { tex: D + 'gel_dry', P: 8 }, gel1: { tex: D + 'gel_wet', P: 8 }, abl: { tex: D + 'air_blue', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- instrument air
  // Two-stage vertical air-cooled compressor on a skid: crankcase, finned LP and HP cylinders, intercooler coil,
  // the motor at the east end on the same shaft, the flywheel (partial) at the west end.
  function compressor() {
    const m = mk('plant/blue');
    skid(m, 'skid', [0, 1], [32, 15]);
    P(m, 'crankcase', [4, 1.4, 4], [18, 8.6, 12], 'blue');
    P(m, 'sump', [5, 1.4, 3.4], [17, 4, 12.6], 'blue');
    P(m, 'bearing_w', [3.2, 5, 5.6], [4.4, 11, 10.4], 'ci');
    m.prism('crank_out', 'x', [8, 8], 1, 3.6, 4.4, 'bright', { split: '' });
    // cylinders: LP big, HP small, finned barrels with heads and valve covers
    for (const [x, r, n] of [[8, 3.2, 'lp'], [15, 2.4, 'hp']]) {
      m.cylY(n + '_barrel', [x, 8], r, 8.6, 14.6, 'cfin');
      P(m, n + '_head', [x - r - 0.4, 14.6, 8 - r - 0.4], [x + r + 0.4, 16.2, 8 + r + 0.4], 'blue');
      P(m, n + '_valves', [x - r + 0.4, 16.2, 8 - r + 0.6], [x + r - 0.4, 17, 8 + r - 0.6], 'ci');
    }
    // intercooler: finned tube from LP head down and over to HP head; delivery pipe to the east
    P(m, 'ic_up', [10.6, 15, 11.6], [11.6, 18.6, 12.6], 'p_air');
    P(m, 'ic_run', [10.6, 17.6, 11.6], [15.4, 18.6, 12.6], 'cfin');
    P(m, 'ic_down', [14.4, 15.6, 11.6], [15.4, 18.6, 12.6], 'p_air');
    P(m, 'delivery', [16.4, 15, 3.6], [17.4, 16, 4.6], 'p_air');
    P(m, 'delivery_run', [16.4, 13, 0.4], [17.4, 16, 3.6], 'p_air');
    P(m, 'unloader', [12, 14, 1.4], [14, 16, 3.4], 'ci');
    gauge(m, 'gauge_lp', 8, 12, 4.4, 1);
    gauge(m, 'gauge_hp', 15, 12, 5.2, 1);
    // coupling guard and the motor on the same shaft
    P(m, 'guard', [18, 4.4, 4.6], [20.4, 11.4, 11.4], 'yel');
    motor(m, 'motor', 'x', [8, 8], 4.6, 20.4, 31.4, 'blue');
    P(m, 'motor_base', [20.4, 1.4, 3], [31.4, 3.4, 13], 'frame');
    m.decal('plate', [10, 4, 3.8], [14, 6, 4], 'blue', 'north', 'parts.plate');
    return m;
  }
  // Flywheel at the west end: rim, six spokes, hub, about x through (y 8, z 8).
  function flywheel() {
    const m = mk('material/steel');
    m.ring('rim', 'x', [8, 8], 5.5, 1.2, 0.4, 3.6, 'st');
    for (const a of [0, 45, -45]) P(m, 'spoke' + a, [1.4, 3, 7.4], [2.6, 13, 8.6], 'st', { rot: a ? { axis: 'x', angle: a, origin: [8, 8, 8] } : undefined });
    P(m, 'spoke_h', [1.4, 7.4, 3], [2.6, 8.6, 13], 'st');
    m.prism('hub', 'x', [8, 8], 1.5, 0, 4, 'red', { split: '', ends: ['west', 'east'] });
    return m;
  }
  // Vertical air receiver, 1x2 blocks: blue shell on three legs, manhole, safety valve, gauge, drain.
  function receiver() {
    const m = mk('m3util/air_blue');
    for (const [x, z] of [[3, 3.4], [12.2, 3.4], [7.6, 12.4]]) P(m, `leg_${x}`, [x, 0, z], [x + 1.2, 6, z + 1.2], 'frame');
    vessel(m, 'shell', [8, 8], 6.2, 6, 27, 'abl', { bottom: true });
    m.cylY('manhole', [8, 1.2], 2.2, 10, 14, 'abl');
    P(m, 'manhole_cover', [5.6, 9.6, 0.2], [10.4, 14.4, 1], 'ci');
    P(m, 'inlet', [-0.5, 20, 7.4], [2, 21.2, 8.6], 'p_air'); flange(m, 'inlet_fl', 'x', [20.6, 8], 0.6, 1.6, 'ci');
    P(m, 'outlet', [14, 24, 7.4], [16.5, 25.2, 8.6], 'p_air'); flange(m, 'outlet_fl', 'x', [24.6, 8], 0.6, 14.2, 'ci');
    P(m, 'sv_stem', [7.6, 30.4, 7.6], [8.4, 31.2, 8.4], 'br');
    P(m, 'sv_body', [7, 31, 7], [9, 32, 9], 'br');
    P(m, 'gauge_pipe', [9.6, 22, 1.8], [10.4, 26.6, 2.6], 'br');
    gauge(m, 'gauge', 10, 28, 1.6, 1.4);
    P(m, 'drain', [7.6, 2, 7.6], [8.4, 4.6, 8.4], 'br');
    P(m, 'drain_valve', [7, 2.4, 6.6], [9, 3.6, 8.6], 'red');
    m.decal('plate', [5.6, 17, 1.7], [9.4, 19, 1.9], 'abl', 'north', 'parts.plate');
    return m;
  }
  // Twin-tower heatless adsorption dryer: two columns, switching valve block below, purge manifold above,
  // control box with the silica gel dew point window and a lamp (green dry, red wet).
  function dryer(wet) {
    const m = mk('plant/light');
    P(m, 'base', [1, 0, 3], [15, 2, 15], 'frame');
    for (const [x, n] of [[4.4, 'a'], [11.6, 'b']]) {
      vessel(m, 'tower_' + n, [x, 9], 2.8, 5, 25, 'abl');
      P(m, 'foot_' + n, [x - 2, 2, 7], [x + 2, 5, 11], 'frame');
    }
    P(m, 'valve_block', [2, 2, 5.2], [14, 4.6, 7.4], 'ci');
    for (const x of [4, 8, 12]) P(m, 'actuator' + x, [x - 1, 4.6, 5.4], [x + 1, 6.4, 7.2], 'p_air');
    P(m, 'top_manifold', [3.6, 27, 8.4], [12.4, 28, 9.6], 'p_air');
    for (const x of [4, 11.2]) P(m, 'riser' + x, [x, 25.8, 8.6], [x + 0.8, 27, 9.4], 'p_air');
    P(m, 'purge_orifice', [7.4, 26.6, 8], [8.6, 28.4, 10], 'br');
    P(m, 'inlet', [0, 3, 6], [2, 4.2, 7], 'p_air'); P(m, 'outlet', [12.4, 27, 8.4], [16, 28, 9.6], 'p_air');
    cabinet(m, 'box', [5, 8, 1.6], [11, 16, 4], 'light', { plate: false });
    P(m, 'gel_frame', [6.2, 12.4, 1.1], [8.4, 15, 1.4], 'ci');
    P(m, 'gel', [6.6, 12.8, 0.9], [8, 14.6, 1.1], wet ? 'gel1' : 'gel0');
    lamp(m, 'dew', 9.6, 13.6, 1.4, wet ? 'l_r' : 'l_g', true, 0.5);
    P(m, 'silencer', [13, 2, 1], [15, 6, 3], 'ci');
    return m;
  }

  // ---------------------------------------------------------------- water treatment
  // Dosing skid, 2x1: two PE day tanks with level gauges and stirrers, two plunger metering pumps on one motor,
  // a calibration column, discharge lines. The pump head (plunger and its head) is the partial at x 22..26.
  function dosing() {
    const m = mk('plant/light');
    skid(m, 'skid', [0, 0], [32, 16]);
    for (const [x, n] of [[5.5, 'a'], [14, 'b']]) {
      m.cylY('tank_' + n, [x, 8.5], 4.2, 1.4, 12, 'pe', { cap: null });
      P(m, 'lid_' + n, [x - 3.4, 12, 5.1], [x + 3.4, 12.6, 11.9], 'pe');
      m.cylY('stir_' + n, [x, 8.5], 1.1, 12.6, 15.4, 'light', { cap: null });
      P(m, 'lvl_' + n, [x - 0.3, 3, 3.8], [x + 0.3, 11, 4.2], 'glass');
    }
    // pumps: frame, motor along x at the east end, two heads (the near one is the partial, the far one static)
    P(m, 'pump_frame', [19, 1.4, 3], [30.6, 6, 13], 'blue');
    P(m, 'gear_case', [19.4, 6, 9], [26.6, 9, 13], 'blue');
    motor(m, 'pump_motor', 'x', [9.6, 11], 2.4, 26.6, 31.4, 'blue', { feet: false });
    P(m, 'head_far_body', [22, 8, 9.4], [26, 12.4, 12.6], 'ss');
    P(m, 'stroke_knob', [20, 9, 10], [21, 10.6, 11.6], 'red');
    P(m, 'column', [17, 1.4, 1.4], [18, 12, 2.4], 'glass');
    for (const [x, mat] of [[24, 'p_alk'], [24.8, 'p_acid']]) P(m, 'disch_' + mat, [x - 0.4, 12.4, 6 + (mat === 'p_acid' ? 4 : 0)], [x + 0.4, 16, 6.8 + (mat === 'p_acid' ? 4 : 0)], mat);
    P(m, 'suction_a', [5.1, 2, 12.6], [21, 2.8, 13.4], 'p_alk');
    P(m, 'suction_b', [14, 2.6, 13.6], [22, 3.4, 14.4], 'p_acid');
    m.decal('warn', [8, 8, 4.25], [11.2, 10.8, 4.35], 'pe', 'north', 'parts.warning');
    cabinet(m, 'starter', [27, 6.4, 0.4], [31.6, 12, 3], 'light', { plate: false });
    lamp(m, 'run', 29.3, 10.6, 0.2, 'l_g', false, 0.4);
    return m;
  }
  function pumpHead() {
    const m = mk('electronics/ss');
    P(m, 'body', [22, 8, 4], [26, 12.4, 8], 'ss');
    m.prism('plunger', 'z', [24, 10.2], 0.6, 8, 9.4, 'bright', { split: '' });
    P(m, 'check_up', [23.4, 12.4, 5.4], [24.6, 15, 6.6], 'ss');
    P(m, 'check_down', [23.4, 6.6, 5.4], [24.6, 8, 6.6], 'ss');
    return m;
  }
  // Forced-draft decarbonizer: FRP column on a concrete plinth, 3 high about the middle block; distributor and
  // demister at the top with the vent hood, side blower with its motor low on the east, sight glasses, drain.
  function decarb() {
    const m = mk('fuel/frp_green');
    P(m, 'plinth', [1, -16, 1], [15, -14, 15], 'conc');
    m.cylY('shell', [8, 8], 6.2, -14, 26, 'frpg', { seed: 3, cap: 'fuel/frp_green' });
    for (const y of [-6, 6, 18]) m.ringY('band' + y, [8, 8], 6.6, 0.6, y, y + 0.8, 'frame');
    m.cylY('roof', [8, 8], 5, 26, 27.4, 'frpg', { cap: 'fuel/frp_green' });
    m.cylY('vent', [8, 8], 2.2, 27.4, 30, 'frpg', { cap: 'fuel/frp_green' });
    P(m, 'hood', [5, 30, 5], [11, 31, 11], 'frpg');
    P(m, 'water_in', [13.6, 23, 7.2], [16, 24.6, 8.8], 'p_water'); flange(m, 'water_in_fl', 'x', [23.8, 8], 0.8, 14.4, 'ci');
    P(m, 'water_out', [13.6, -12, 7.2], [16, -10.4, 8.8], 'p_water');
    // blower: volute box, inlet bell with a guard, motor below it
    P(m, 'volute', [13, -10, 2], [16, -3, 8], 'blue');
    P(m, 'duct', [12, -8, 3], [14, -5, 7], 'blue');
    m.prism('inlet', 'z', [14.5, -6.5], 2, 0.6, 2, 'blue', { split: '', ends: ['north'] });
    P(m, 'motor_stool', [12.6, -14, 1.4], [16, -12, 8.6], 'frame');
    motor(m, 'motor', 'z', [14.3, -11], 1.6, 2, 8, 'blue', { feet: false });
    for (const y of [-2, 14]) { m.prism('sight' + y, 'z', [8, y], 1.2, 1.2, 2, 'ci', { split: '', ends: ['north'] }); P(m, 'sight_glass' + y, [7.2, y - 0.8, 1.1], [8.8, y + 0.8, 1.2], 'glass'); }
    ladder(m, 'ladder', 1.6, 0.6, -14, 27, 'x');
    m.decal('plate', [6, 8, 1.75], [10, 10, 1.85], 'frpg', 'north', 'parts.plate');
    return m;
  }

  // ---------------------------------------------------------------- process heater
  // Electric process heater (kind=electric): flanged pressure vessel with the heating element bundle, terminal
  // enclosure with the element glow behind a mesh when on, in/out flanges east and west.
  function heaterElectric(on) {
    const m = mk('plant/light');
    P(m, 'base', [1, 0, 2], [15, 2, 14], 'frame');
    m.prism('shell', 'x', [8, 8], 5, 2, 12, 'clad', { split: '' });
    m.prism('flange', 'x', [8, 8], 6, 1, 2, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('cover', 'x', [8, 8], 5.6, 0.2, 1, 'ci', { split: '', ends: ['west'] });
    cabinet(m, 'tbox', [11.4, 3, 2.6], [15.6, 15, 13.4], 'light', { plate: false });
    P(m, 'mesh', [12.6, 5, 2.1], [14.6, 10, 2.4], on ? 'coil1' : 'coil0');
    if (on) P(m, 'glow_mesh', [12.6, 5, 2], [14.6, 10, 2.1], 'coil1', { hide: ['south'] });
    lamp(m, 'on', 13.6, 12.6, 2.4, on ? 'l_a' : 'l_off', on, 0.5);
    P(m, 'in', [-1.5, 3, 7.2], [1, 4.6, 8.8], 'p_liq'); P(m, 'out', [6, 13, 7.2], [7.6, 17, 8.8], 'p_steam');
    P(m, 'tsensor', [9, 13, 7.6], [9.8, 15, 8.4], 'ss');
    m.decal('hv', [3, 10.2, 2.6], [6, 13, 2.8], 'clad', 'north', 'parts.warning');
    return m;
  }
  // Fired heater controller (kind=fired): a furnace front panel — casing plate, burner register with its gas
  // train, a peep door (lit when on), the draft gauge and the flame lamp.
  function heaterFired(on) {
    const m = mk('plant/furnace_casing');
    P(m, 'casing', [0, 0, 2], [16, 16, 16], 'casing');
    P(m, 'refr_lip', [1, 1, 1.6], [15, 15, 2], 'dinas');
    m.decal('peep', [5.5, 9, 1.4], [8.7, 13, 1.6], 'casing', 'north', on ? 'parts.peep_lit' : 'parts.peep');
    if (on) P(m, 'glow_peep', [6.3, 10.6, 1.35], [7.9, 12.2, 1.4], 'fglow', { hide: ['south'] });
    m.prism('register', 'z', [8, 5], 3, 0.6, 1.6, 'ci', { split: '', ends: ['north'] });
    m.prism('burner', 'z', [8, 5], 1.2, -0.6, 0.6, 'bright', { split: '', ends: ['north'] });
    P(m, 'gas_train', [8.6, 4.4, -0.6], [15, 5.6, 0.6], 'p_gas');
    P(m, 'gas_valve', [11.4, 3.8, -1.2], [12.6, 6.2, 0.2], 'red');
    P(m, 'air_damper', [1, 2, 0.6], [4, 8, 2], 'ci');
    gauge(m, 'draft', 12, 12, 2, 1.2);
    lamp(m, 'flame', 3, 12, 1.6, on ? 'l_g' : 'l_off', on, 0.6);
    m.decal('plate', [1.5, 14, 1.75], [5.5, 15.4, 1.85], 'casing', 'north', 'parts.tag');
    return m;
  }

  // ---------------------------------------------------------------- coal mill
  // Ball-tube mill, 3x3x3 about the middle block: concrete pedestals at both ends with trunnion bearings, the
  // gear guard over the girth gear, pinion, gearbox and motor on the south side, feed chute at the west, the
  // discharge cone and classifier duct at the east. The drum is the partial.
  function mill() {
    const m = mk('m3util/mill_shell');
    P(m, 'found', [-16, -16, -12], [32, -14, 28], 'conc');
    for (const [x0, n] of [[-16, 'w'], [28, 'e']]) {
      P(m, 'ped_' + n, [x0, -14, -2], [x0 + 4, 0, 18], 'conc');
      P(m, 'bearing_' + n, [x0 + 0.4, 0, 1], [x0 + 3.6, 6, 15], 'ci');
      P(m, 'bearing_cap_' + n, [x0 + 0.6, 6, 2], [x0 + 3.4, 9.4, 14], 'ci');
    }
    // gear guard: a hood over the girth gear at x 16..19
    P(m, 'guard_top', [15.2, 22.6, -7], [19.8, 23.6, 23], 'yel');
    for (const s of [-1, 1]) P(m, 'guard_side' + s, [15.2, -6, s < 0 ? -8 : 23], [19.8, 22.6, s < 0 ? -7 : 24], 'yel');
    // drive on the south: pinion housing, gearbox, coupling, motor
    P(m, 'pinion', [15, -6, 23], [20, 0, 26], 'ci');
    P(m, 'gearbox', [14, -14, 23.6], [22, -4, 28], 'green');
    P(m, 'coupling', [22, -11, 24.6], [24, -7, 27], 'yel');
    motor(m, 'motor', 'x', [-9, 25.8], 5, 24, 32, 'green', { feet: false });
    // feed chute down to the west trunnion, discharge at the east
    P(m, 'feed_hopper', [-15, 18, 3], [-9, 26, 13], 'mshell');
    P(m, 'feed_chute', [-15, 9, 5], [-12, 18, 11], 'mshell');
    P(m, 'disch_hood', [28, 4, -2], [32, 24, 18], 'mshell');
    P(m, 'class_duct', [26, 24, 4], [32, 30, 12], 'mshell');
    P(m, 'reject_chute', [26, -14, 6], [30, 4, 10], 'mshell');
    for (const [x, z] of [[-6, -12], [12, -12]]) P(m, 'walkway' + x, [x, -14, z], [x + 10, -13.4, z + 4], 'grat');
    m.decal('haz', [-15.5, -12, -2.1], [-12.5, -9.4, -2], 'conc', 'north', 'parts.warning');
    return m;
  }
  // Drum: armoured shell about x through (8, 8), r 13, the girth gear at x 16..19 and the trunnions at the ends.
  function millDrum() {
    const m = mk('m3util/mill_shell');
    m.prism('shell', 'x', [8, 8], 12.4, -10, 26, 'mshell', { split: 'x', ends: ['west', 'east'] });
    m.prism('head_w', 'x', [8, 8], 10, -12, -10, 'mshell', { split: '', ends: ['west'] });
    m.prism('head_e', 'x', [8, 8], 10, 26, 28, 'mshell', { split: '', ends: ['east'] });
    m.prism('gear', 'x', [8, 8], 13, 16, 19, 'gear', { split: '', ends: ['west', 'east'] });
    for (const x of [-4, 6, 22]) m.ring('band' + x, 'x', [8, 8], 12.9, 0.8, x, x + 1.4, 'ci');
    m.prism('manhole', 'x', [8, 20.6], 1.6, 0, 4, 'ci', { split: '' });
    return m;
  }

  const BUILDS = {
    instrument_air_compressor: { model: compressor, cat: 'automation' },
    'instrument_air_compressor/flywheel': { model: flywheel, cat: 'automation', java: 'instrument_air_compressor/partial/flywheel' },
    air_receiver: { model: receiver, cat: 'automation' },
    air_dryer: { model: () => dryer(false), cat: 'automation' },
    air_dryer_wet: { model: () => dryer(true), cat: 'automation' },
    chemical_dosing_skid: { model: dosing, cat: 'water_treatment' },
    'chemical_dosing_skid/pump_head': { model: pumpHead, cat: 'water_treatment', java: 'chemical_dosing_skid/partial/pump_head' },
    decarbonizer: { model: decarb, cat: 'water_treatment' },
    process_heater: { model: () => heaterElectric(false), cat: 'boilers' },
    process_heater_on: { model: () => heaterElectric(true), cat: 'boilers' },
    process_heater_fired: { model: () => heaterFired(false), cat: 'boilers' },
    process_heater_fired_on: { model: () => heaterFired(true), cat: 'boilers' },
    coal_mill: { model: mill, cat: 'boilers' },
    'coal_mill/drum': { model: millDrum, cat: 'boilers', java: 'coal_mill/partial/drum' },
  };
  window.M3U = FUTK.machine(BUILDS, paint);
})();
