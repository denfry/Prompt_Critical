// Oil field line heater at real scale (1 block = 1 m, 16 px = 1 m), the Soviet indirect-fired PP-0.63 (0.63 MW):
// a horizontal shell filled with a water bath, a U fire tube from the gas burner on the west head, two flat crude
// coils in the bath piped out through the east head, the smoke box and flue stack over the burner end, the expansion
// tank on top, all on a channel skid. Lit variant: the flame in the burner sight port and the glowing register.
// Sheet: docs/research/2026-11-24-i2-batch2/line_heater_real.md.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   LHT.build(repo, lit); /* wait */ LHT.save(repo, lit)      lit = false | true (line_heater_real_lit)
//   LHT.buildPartial(repo, 'stack_damper'); LHT.savePartial(repo, 'stack_damper')
//   LHT.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint: body x -5..5, y 0..3, z -1..1 (11 x 4 x 3) plus the stack
// column (-4, 4..8, 0). Shell axis along x through (y 26, z 8), r 19, cylinder x -64..72, heads to -72 and 80.
// Connections (px): crude in on the east face of (5, 1, -1) at (96, 24, -6); crude out on the east face of (5, 1, 1)
// at (96, 24, 22); fuel gas in on the west face of (-5, 0, 1) at (-80, 10, 24); flue out of the stack top, the top
// face of (-4, 8, 0) at (-56, 144, 8).
// Partials (line_heater_real/partial/*):
// - stack_damper: butterfly in the stack and its outside lever and quadrant; turns about x at (-56, 100, 8), 0 deg
//   open .. 70 deg closed; stored +56 x, -88 y, draw at -56 x, +88 y.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });
  const A = [26, 8], R = 19, ST = [-56, 8];

  function head(m, name, x0, dir) {
    const n = 4, depth = 8;
    for (let i = 0; i < n; i++) {
      const r = R * Math.sqrt(1 - Math.pow((i + 0.5) / n, 2)) + 0.4;
      const a = x0 + dir * depth * i / n, b = x0 + dir * depth * (i + 1) / n;
      m.prism(`${name}_${i}`, 'x', A, r, Math.min(a, b), Math.max(a, b), 'pnt', { split: '', ends: [dir > 0 ? 'east' : 'west'] });
    }
  }

  function skid(m) {
    for (const z of [-12, 24]) {
      m.box(`skid_${z}`, [-80, 0, z], [92, 5, z + 4], 'frame', { split: 'x' });
    }
    for (const x of [-78, -40, 0, 48, 88]) m.box(`skid_x_${x}`, [x, 0, -8], [x + 3, 4, 24], 'frame', { split: 'z' });
    for (const x of [-40, 48]) {
      m.box(`saddle_${x}`, [x - 5, 5, -8], [x + 5, 8, 24], 'frame', { split: '' });
      m.box(`saddle_web_${x}`, [x - 0.6, 8, -6], [x + 0.6, 14, 22], 'frame', { split: '' });
    }
  }

  function shell(m) {
    m.prism('shell', 'x', A, R, -64, 72, 'pnt', { split: 'x' });
    head(m, 'head_w', -64, -1);
    head(m, 'head_e', 72, 1);
    for (const x of [-20, 24]) m.ring(`band_${x}`, 'x', A, R + 0.3, 0.6, x, x + 1, 'pnt');
    // manhole on the east head side, bath level gauge and thermometers on the north
    m.prism('manhole', 'z', [56, 14], 4, -12, -8, 'pnt', { split: '', ends: ['north'] });
    m.disc('manhole_cover', 'z', [56, 14], 4.6, -12.6, -12, 'ci', 'vver/flange', 'north');
    m.box('level_col', [20, 14, -12.4], [22, 40, -10.4], 'st', { split: '' });
    m.box('level_glass', [20.4, 16, -12.6], [21.6, 38, -12.4], 'st', { split: '', face: { north: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    for (const y of [16, 37]) m.box(`level_nz_${y}`, [20.4, y, -10.4], [21.6, y + 1.2, -6], 'st', { split: '' });
    for (const x of [0, 36]) {
      m.box(`tw_${x}`, [x - 0.6, 30, -12], [x + 0.6, 31.2, -8], 'st', { split: '' });
      m.prism(`tg_${x}`, 'z', [x, 33], 2, -13, -12, 'st', { split: '', ends: ['north'] });
    }
    m.decal('plate', [-30, 30, -11.3], [-24, 33.6, -11], 'pnt', 'north', 'pl.maker');
    // ladder up the south side to the expansion tank
    for (const x of [-2, 2]) m.box(`ladder_rail_${x}`, [x, 5, 28.6], [x + 0.6, 58, 29.2], 'st', { split: 'y' });
    for (let y = 8; y < 58; y += 5) m.box(`rung_${y}`, [-2, y, 28.6], [2.6, y + 0.5, 29.1], 'st', { split: '' });
  }

  function top(m) {
    // expansion tank on legs over the shell with its vent
    m.prism('exp_tank', 'x', [57, 8], 5, -20, 4, 'pnt', { split: '', ends: ['west', 'east'] });
    for (const x of [-16, 0]) m.box(`exp_leg_${x}`, [x, 44, 5], [x + 1.4, 53, 11], 'frame', { split: '' });
    m.cylY('exp_vent', [-14, 8], 0.8, 61, 64, 'st');
    m.box('exp_drop', [-4, 44, 7], [-2, 53, 9], 'gw', { split: '' });
    // smoke box over the fire-tube return and the stack
    m.box('smoke_box', [-64, 42, -2], [-48, 54, 18], 'dark', { split: '' });
    m.cylY('stack_lo', ST, 4, 54, 64, 'dark');
    for (let y = 64; y < 136; y += 16) m.cylY(`stack_${y}`, ST, 4, y, y + 16, 'dark', { seed: y });
    for (const y of [80, 112]) m.ringY(`stack_flange_${y}`, ST, 4.8, 1, y - 0.5, y + 0.5, 'ci');
    m.cylY('stack_top', ST, 4, 136, 140, 'dark');
    m.cylY('rain_cap', ST, 6, 142, 143, 'dark');
    for (const [dx, dz] of [[-3.4, 0], [3.4, 0], [0, -3.4], [0, 3.4]]) m.box(`cap_leg_${dx}_${dz}`, [ST[0] + dx - 0.3, 140, ST[1] + dz - 0.3], [ST[0] + dx + 0.3, 142, ST[1] + dz + 0.3], 'st', { split: '' });
    m.box('damper_quadrant', [-51.6, 96, 6], [-51, 104, 10], 'st', { split: '' });
  }

  function burner(m, lit) {
    // burner housing on the west head, sight port, air register, gas train from the west face
    m.box('burner', [-79, 12, 0], [-71, 30, 16], 'pnt', { split: '' });
    m.box('register', [-79.6, 13, 2], [-79, 18, 14], lit ? 'gs' : 'dark', { split: '' });
    for (let z = 3; z < 14; z += 2) m.box(`register_slot_${z}`, [-79.8, 13.6, z], [-79.6, 17.4, z + 1], 'dark', { split: '' });
    m.box('sight_frame', [-79.6, 19.4, 5.4], [-79, 24.6, 10.6], 'ci', { split: '' });
    if (lit) {
      m.box('glow_flame', [-79.8, 20, 6], [-79.6, 24, 10], 'gs', { split: '', face: { west: { tex: 'material/paint_red', uv: [0, 0, 4, 4] } } });
    } else {
      m.box('sight_glass', [-79.8, 20, 6], [-79.6, 24, 10], 'dark', { split: '' });
    }
    m.box('gas_in', [-79.9, 8.4, 22.4], [-66, 11.6, 25.6], 'yel', { split: 'x' });
    m.box('gas_up', [-70, 11.6, 22.4], [-66.8, 21, 25.6], 'yel', { split: '' });
    m.box('gas_into_burner', [-71, 18, 14], [-67.8, 21, 25.6], 'yel', { split: '' });
    m.box('gas_shutoff', [-77, 7, 21], [-73, 13, 27], 'red', { split: '' });
    m.wheelY('gas_shutoff_wheel', -75, 15, 24, 2.6, 'red');
    m.box('gas_shutoff_stem', [-75.3, 13, 23.7], [-74.7, 15, 24.3], 'st', { split: '' });
    m.cylY('regulator', [-68.4, 24], 3, 13, 16, 'ci');
    m.cylY('regulator_dome', [-68.4, 24], 2, 16, 18, 'ci');
  }

  function coils(m) {
    // two coil nozzles through the east head: inlet with its gate valve, outlet with the thermometer pocket
    for (const [z, n] of [[-6, 'in'], [22, 'out']]) {
      m.prism(`coil_${n}`, 'x', [24, z], 3.4, 74, 95.4, 'oil', { split: 'x' });
      m.disc(`coil_${n}_flange`, 'x', [24, z], 4.4, 95.4, 95.9, 'ci', 'vver/flange', 'east');
      m.disc(`coil_${n}_head_flange`, 'x', [24, z], 4.6, 80, 81, 'ci', 'vver/flange', 'east');
    }
    m.box('in_valve', [86, 19, -10], [91, 29, -2], 'ci', { split: '' });
    m.box('in_valve_stem', [88.2, 29, -6.3], [88.8, 33, -5.7], 'st', { split: '' });
    m.wheelY('in_valve_wheel', 88.5, 33.4, -6, 3, 'red');
    m.box('out_tw', [88, 27.4, 21.4], [89.2, 33, 22.6], 'st', { split: '' });
    m.prism('out_tg', 'z', [88.6, 35], 2, 16, 17, 'st', { split: '', ends: ['north'] });
    m.box('out_tg_stem', [88, 33, 17], [89.2, 34, 22], 'st', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function damper(m) {
    m.box('blade', [-59.6, 99.6, 4.4], [-52.4, 100.4, 11.6], 'dark', { split: '' });
    m.box('spindle', [-60, 99.4, 7.4], [-50.4, 100.6, 8.6], 'bright', { split: '' });
    m.box('lever', [-50.4, 99.4, 7.4], [-49.6, 106, 8.6], 'red', { split: '' });
  }
  const PART = { stack_damper: { draw: damper, shift: [56, -88, 0] } };
  function model(lit) { const m = mk(); skid(m); shell(m); top(m); burner(m, !!lit); coils(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'line_heater_real', id = (lit) => ID + (lit ? '_lit' : '');
  window.LHT = {
    ID, model, partial, PART,
    build: (root, lit) => { const m = PCVV.cut(model(lit)); PCART.realize(root, m, id(lit)); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, lit) => PCVV.save(root, id(lit), 'oil_production'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'oil_production'),
    preview: (root) => { const m = model(true); const s = mk(); damper(s); m.merge(s, 0, 0, 0, 'damper_'); PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
