// Mainline oil pump unit at real scale (1 block = 1 m, 16 px = 1 m): the Soviet NM 1250-260 single-stage horizontal
// centrifugal pump (double-suction impeller, axially split casing, DN 350 suction and discharge horizontal on
// opposite sides) and its 1250 kW synchronous motor on one baseplate, 5.3 x 2.0 x 1.5 m, the standard unit of a
// pipeline pump station. The fire wall a real station puts between pump and motor is left to the player.
// Sheet: docs/research/2026-11-24-i2-batch2/mainline_pump_real.md.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   MLP.build(repo); /* wait */ MLP.save(repo)
//   MLP.buildPartial(repo, p); MLP.savePartial(repo, p)    for coupling, motor_fan
//   MLP.preview(repo)
//
// Controller block = (0, 0, 0) at the coupling, front north. Footprint x -2..3, y 0..1, z -1..1 (6 x 2 x 3). Shaft
// along x through (y 14.4, z 8): pump casing x -16..0, coupling x 0..8, motor x 8..52, tail shaft to x 64.
// Connections (px): suction in on the north face of (-1, 0, -1) at (-8, 12, -16); discharge out on the south face of
// (-1, 0, 1) at (-8, 12, 32); grid power to the terminal box on the south face of (2, 0, 1) at (40, 8, 32); or a
// Create shaft on the east face of (3, 0, 0) at (64, 14.4, 8) at the motor tail (pick one drive).
// Partials (mainline_pump_real/partial/*):
// - coupling: the coupling halves and the exposed shaft under the guard; turn about x at (y 14.4, z 8); no shift.
// - motor_fan: the cooling fan behind the tail grille; same axis, same speed; stored -40 x, draw at +40 x.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });
  const S = [14.4, 8];

  function base(m) {
    m.box('baseplate', [-32, 0, -8], [58, 3, 24], 'frame', { split: 'xz' });
    m.box('drip_lip_n', [-32, 3, -8], [58, 3.6, -7.4], 'frame', { split: 'x' });
    m.box('drip_lip_s', [-32, 3, 23.4], [58, 3.6, 24], 'frame', { split: 'x' });
    for (const x of [-30, -2, 26, 54]) for (const z of [-7, 22]) m.box(`anchor_${x}_${z}`, [x, 3, z], [x + 1.4, 4, z + 1.4], 'st', { split: '' });
  }

  function pump(m) {
    // axially split casing: lower half with the feet and nozzles, upper half bolted on the split flange
    m.prism('casing', 'x', S, 10, -16, 0, 'pnt', { split: '' });
    m.box('casing_feet', [-16, 3, -4], [0, 8, 20], 'pnt', { split: '' });
    m.box('split_flange', [-17, 14, -3.4], [1, 15.2, 19.4], 'pnt', { split: '' });
    for (const x of [-15, -10, -5, -1]) for (const z of [-2.6, 18.6]) m.box(`split_bolt_${x}_${z}`, [x - 0.4, 15.2, z - 0.4], [x + 0.4, 16, z + 0.4], 'st', { split: '' });
    m.box('casing_lift', [-9, 24, 6], [-7, 26, 10], 'pnt', { split: '' });
    // bearing housings on both ends of the pump shaft
    for (const [x0, x1, n] of [[-28, -18, 'out'], [0, 3, 'in']]) {
      m.prism(`brg_${n}`, 'x', S, 4.4, x0, x1, 'ci', { split: '', ends: n === 'out' ? ['west'] : [] });
      m.box(`brg_ped_${n}`, [x0, 3, 4], [x1, 11, 12], 'ci', { split: '' });
    }
    m.prism('shaft_out', 'x', S, 1.6, -30, -28, 'bright', { split: '', ends: ['west'] });
    m.box('seal_drain', [-19, 6, 10], [-17, 10, 11], 'st', { split: '' });
    // DN 350 suction to the north and discharge to the south with its check valve stub
    m.prism('suction', 'z', [-8, 12], 5.6, -15.5, -6, 'oil', { split: 'z' });
    m.disc('suction_flange', 'z', [-8, 12], 7, -15.9, -15.5, 'ci', 'vver/flange', 'north');
    m.prism('discharge', 'z', [-8, 12], 5.6, 22, 31.4, 'oil', { split: 'z' });
    m.disc('discharge_flange', 'z', [-8, 12], 7, 31.4, 31.8, 'ci', 'vver/flange', 'south');
    m.prism('check_stub', 'z', [-8, 12], 7.4, 24, 27, 'ci', { split: '', ends: ['north', 'south'] });
    m.box('check_bonnet', [-11, 19, 24], [-5, 22, 27], 'ci', { split: '' });
    // gauges on the nozzles
    for (const [z, d] of [[-12, 'north'], [28, 'south']]) {
      m.box(`pg_stem_${z}`, [-8.6, 17.6, z], [-7.4, 21, z + 1], 'st', { split: '' });
      m.prism(`pg_${z}`, 'z', [-8, 23], 2, z, z + 1, 'st', { split: '', ends: [d] });
    }
    m.decal('plate', [-14, 6, -4.3], [-8, 9.6, -4], 'pnt', 'north', 'pl.maker');
  }

  function guard(m) {
    m.box('guard_top', [0, 21, 1], [8, 21.6, 15], 'grate', { split: '' });
    m.box('guard_n', [0, 8, 1], [8, 21, 1.6], 'grate', { split: '' });
    m.box('guard_s', [0, 8, 14.4], [8, 21, 15], 'grate', { split: '' });
    m.box('guard_foot', [0, 3, 1], [8, 8, 15], 'yel', { split: '' });
  }

  function motor(m) {
    m.prism('stator', 'x', S, 10, 10, 50, 'motor', { split: 'x' });
    for (let x = 14; x < 48; x += 4) m.ring(`rib_${x}`, 'x', S, 10.6, 0.8, x, x + 1, 'motor');
    m.box('motor_feet', [12, 3, -3], [48, 8, 19], 'motor', { split: '' });
    for (const x of [8, 50]) m.prism(`end_shield_${x}`, 'x', S, 8, x, x + 2, 'mcap', { split: '', ends: [x < 20 ? 'west' : 'east'] });
    // air-to-air cooler on top
    m.box('cooler', [16, 23, 0], [44, 30, 16], 'motor', { split: 'x' });
    m.box('cooler_cap', [15, 30, -1], [45, 31, 17], 'mcap', { split: 'x' });
    for (let x = 18; x < 44; x += 3) m.box(`cooler_fin_${x}`, [x, 24, -0.4], [x + 1, 29, 0], 'mcap', { split: '' });
    // terminal box with the 6 kV cable gland to the south face
    m.box('term_box', [34, 4, 18], [46, 14, 26], 'motor', { split: '' });
    m.prism('cable_gland', 'z', [40, 8], 2, 26, 31.6, 'dark', { split: '', ends: ['south'] });
    m.decal('motor_plate', [20, 12, -2.3], [26, 15.6, -2], 'mcap', 'north', 'pl.maker');
    // tail: grille over the fan, tail shaft and its pedestal to the east face (Create alternative)
    m.disc('tail_grille', 'x', S, 7.6, 52, 52.6, 'grate', 'vver/grating_disc', 'east');
    m.prism('tail_shaft', 'x', S, 1.6, 52.6, 63.4, 'bright', { split: '' });
    m.box('tail_ped', [57, 3, 4], [61, 11, 12], 'ci', { split: '' });
    m.prism('tail_brg', 'x', S, 3.4, 57, 61, 'ci', { split: '', ends: ['west', 'east'] });
    m.disc('tail_end', 'x', S, 2.6, 63.4, 63.9, 'ci', 'vver/flange', 'east');
  }

  // ------------------------------------------------------------------ partials
  function coupling(m) {
    m.prism('shaft', 'x', S, 1.8, 0, 8, 'bright', { split: '' });
    m.prism('hub_p', 'x', S, 4.4, 1, 3.6, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('hub_m', 'x', S, 4.4, 4.4, 7, 'ci', { split: '', ends: ['west', 'east'] });
    for (let k = 0; k < 4; k++) {
      const a = k * Math.PI / 2, y = S[0] + 3 * Math.sin(a), z = S[1] + 3 * Math.cos(a);
      m.box(`pin_${k}`, [3, y - 0.5, z - 0.5], [5, y + 0.5, z + 0.5], 'st', { split: '' });
    }
  }
  function fan(m) {
    m.prism('fan_hub', 'x', S, 1.8, 50.6, 52, 'ci', { split: '', ends: ['east'] });
    // four blades: a vertical and a horizontal bar, and the same bar turned +-45 deg
    m.box('fan_blade_v', [51, S[0] - 7, S[1] - 0.6], [51.6, S[0] + 7, S[1] + 0.6], 'dark', { split: '' });
    m.box('fan_blade_h', [51, S[0] - 0.6, S[1] - 7], [51.6, S[0] + 0.6, S[1] + 7], 'dark', { split: '' });
    for (const a of [45, -45]) m.box(`fan_blade_${a}`, [51, S[0] - 7, S[1] - 0.6], [51.6, S[0] + 7, S[1] + 0.6], 'dark', { split: '', rot: { axis: 'x', angle: a, origin: [51.3, S[0], S[1]] } });
  }

  const PART = { coupling: { draw: coupling, shift: [0, 0, 0] }, motor_fan: { draw: fan, shift: [-40, 0, 0] } };
  function model() { const m = mk(); base(m); pump(m); guard(m); motor(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'mainline_pump_real';
  window.MLP = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'oil_transport'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'oil_transport'),
    preview: (root) => { const m = model(); for (const [k, p] of Object.entries(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, k + '_'); } PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
