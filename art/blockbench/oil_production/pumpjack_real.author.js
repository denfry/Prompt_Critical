// Beam pumping unit at real scale (1 block = 1 m, 16 px = 1 m) of the Soviet SK-8 class (GOST 5866, 8 t on the
// polished rod, 2.1 m stroke): the walking beam on the samson post with the horsehead over the well, the equalizer
// and two pitman arms down to the crank counterweights on the gearbox output shaft, the gearbox on its pedestal with
// the band brake on the input shaft, all on a welded base. The well and its wellhead stand in the cell column under
// the horsehead, outside the base.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   PJK.build(repo); /* wait */ PJK.save(repo)
//   PJK.buildPartial(repo, p); PJK.savePartial(repo, p)   for beam_rear, beam_front, horsehead, pitman, crank,
//                                                          carrier, polished_rod
//   PJK.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x -4..4, y 0..6, z -1..1 (9 x 7 x 3) less the well column
// x 4, y 0..2, z -1..1, where the wellhead block goes; the well axis is vertical through (72, y, 8).
// Kinematics: beam pivot (saddle bearing) at (24, 80, 8), front arm 48 px to the horsehead arc (centred on the
// pivot, so the bridle hangs on x 72 from y 80), rear arm 38 px to the equalizer pin at (-14, 72); crank shaft along
// z at (-16, 28), crank radius 13, pitman 44 eye to eye from the crank pin to the equalizer. Beam swing about +-20
// deg, polished rod stroke about +-16.4 px around the carrier bar at y 52 (2.05 m).
// Connections (px): Create shaft in on the south face of (-3, 1, 1) at (-36, 18, 32), the gearbox input shaft along
// z (an electric_motor drives it the same way); the polished rod down into the wellhead at (4, 0, 0), x 72, z 8.
// Partials (pumpjack_real/partial/*), each with its storage shift (draw at minus the shift):
// - beam_rear, beam_front, horsehead: the walking beam in three pieces, all turning about z at (24, 80); stored
//   shifts beam_rear (+1, -64, 0), beam_front (-28, -64, 0), horsehead (-48, -64, 0).
// - pitman: both pitman arms, bottom eye at (0, 0) and top eye at (0, 44) before the shift; the renderer puts the
//   bottom eye on the crank pin and turns the arm about z to point at the equalizer pin. Stored shift (0, -14, 0).
// - crank: both crank arms with counterweights and the crank pins (pin on +x at angle 0), about z at (-16, 28);
//   stored shift (+22, -8, 0).
// - carrier: carrier bar, rod clamp and the bridle ropes up 30 px; slides along y, the carrier bar at y 52 at mid
//   stroke; stored shift (-64, -52, 0).
// - polished_rod: 37 px of polished rod hanging from the carrier clamp; slides with the carrier; stored shift
//   (-64, -20, 0) (the rod spans y 12..49 under the carrier at mid stroke).
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, rope: { tex: 'vver/dark', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });
  const PIV = [24, 80], CR = [-16, 28], CRR = 13, WELL = 72, CARRY = 52;

  function base(m) {
    for (const z of [-2, 14]) {
      m.box(`base_web_${z}`, [-62, 0, z + 1.6], [60, 5, z + 2.4], 'frame', { split: 'x' });
      m.box(`base_fl_lo_${z}`, [-62, 0, z], [60, 1, z + 4], 'frame', { split: 'x' });
      m.box(`base_fl_hi_${z}`, [-62, 4, z], [60, 5, z + 4], 'frame', { split: 'x' });
    }
    for (const x of [-60, -40, -4, 16, 32, 56]) m.box(`cross_${x}`, [x, 0, -14], [x + 3, 4, 30], 'frame', { split: 'z' });
    for (const x of [-60, 56]) for (const z of [-14, 26]) m.box(`foot_${x}_${z}`, [x - 1, 0, z], [x + 4, 1, z + 4], 'ci', { split: '' });
  }

  function samson(m) {
    // two box-section posts with lacing, raking struts from the rear, the saddle plate on top
    for (const z of [1, 12]) {
      m.box(`post_${z}`, [20, 5, z], [28, 72, z + 3], 'pnt', { split: 'y' });
      m.box(`strut_${z}`, [8.5, 2.5, z + 0.5], [10.5, 62.5, z + 2.5], 'pnt', { split: '', rot: { axis: 'z', angle: -22.5, origin: [9.5, 32.5, z + 1.5] } });
      m.box(`strut_shoe_${z}`, [-6, 4, z - 0.5], [2, 6, z + 3.5], 'ci', { split: '' });
    }
    for (const y of [20, 40, 60]) m.box(`lace_${y}`, [21, y, 4], [27, y + 2, 12], 'pnt', { split: '' });
    m.box('saddle_plate', [18, 72, 0], [30, 74, 16], 'ci', { split: '' });
    // ladder up the north post
    for (const x of [19, 23]) m.box(`ladder_rail_${x}`, [x, 5, -1.6], [x + 0.6, 72, -1], 'st', { split: 'y' });
    for (let y = 9; y < 72; y += 5) m.box(`ladder_rung_${y}`, [19, y, -1.6], [23.6, y + 0.5, -1.1], 'st', { split: '' });
    m.decal('plate', [21, 30, 0.6], [27, 33.6, 1], 'pnt', 'north', 'pl.maker');
  }

  function gearbox(m) {
    m.box('pedestal', [-34, 5, 1], [2, 10, 15], 'pnt', { split: '' });
    m.box('gb_low', [-42, 10, 0], [2, 22, 16], 'pnt', { split: '' });
    m.box('gb_high', [-30, 22, 0], [-2, 36, 16], 'pnt', { split: '' });
    m.box('gb_split', [-42.4, 21.6, -0.4], [2.4, 22.6, 16.4], 'ci', { split: '' });
    m.box('gb_breather', [-18, 36, 7], [-16, 39, 9], 'ci', { split: '' });
    // output shaft hubs through the case to the cranks; input shaft with the band brake drum to the south
    m.prism('out_shaft', 'z', CR, 3, -4, 20, 'bright', { split: '' });
    for (const z of [-0.6, 16]) m.disc(`out_cap_${z}`, 'z', CR, 5, z, z + 0.6, 'ci', 'vver/flange', z < 0 ? 'north' : 'south');
    m.prism('in_shaft', 'z', [-36, 18], 1.6, 16, 31.9, 'bright', { split: '' });
    m.prism('brake_drum', 'z', [-36, 18], 6, 25.6, 29.6, 'ci', { split: '', ends: ['north', 'south'] });
    m.ring('brake_band', 'z', [-36, 18], 6.6, 0.6, 26, 29.2, 'dark');
    m.disc('in_coupling', 'z', [-36, 18], 3, 30.2, 31.4, 'ci', 'vver/flange', 'south');
    // brake lever at the rear with its rod to the band
    m.box('brake_lever', [-58, 5, 27], [-56, 34, 29], 'red', { split: '' });
    m.box('brake_rod', [-56, 24, 27.4], [-42, 25, 28.4], 'st', { split: '' });
    // crank guard fence (rails both sides of the counterweight sweep)
    // (the south fence stops short of the brake drum and the input coupling)
    for (const [z, x0] of [[-14, -46], [30.4, -28]]) {
      m.box(`fence_top_${z}`, [x0, 20, z], [12, 21, z + 1], 'yel', { split: 'x' });
      m.box(`fence_mid_${z}`, [x0, 12, z], [12, 13, z + 1], 'yel', { split: 'x' });
      for (const x of [x0, -8, 11]) m.box(`fence_post_${x}_${z}`, [x, 4, z], [x + 1, 21, z + 1], 'yel', { split: '' });
    }
  }

  // ------------------------------------------------------------------ partials (drawn in model coordinates)
  function beamRear(m) {
    m.box('beam_r_web', [-16, 77, 7], [24, 83, 9], 'pnt', { split: '' });
    m.box('beam_r_fl_t', [-16, 83, 4], [24, 84, 12], 'pnt', { split: '' });
    m.box('beam_r_fl_b', [-16, 76, 4], [24, 77, 12], 'pnt', { split: '' });
    // equalizer bearing and the equalizer bar across to both pitmans
    m.box('eq_brg', [-17, 72, 4], [-11, 76, 12], 'ci', { split: '' });
    m.prism('equalizer', 'z', [-14, 72], 2, -13, 29, 'pnt', { split: '', ends: ['north', 'south'] });
    // centre (saddle) bearing housing under the pivot
    m.box('saddle_brg', [18, 74, 2], [30, 77, 14], 'ci', { split: '' });
    m.prism('pivot_pin', 'z', PIV, 1.6, 1, 15, 'bright', { split: '', ends: ['north', 'south'] });
  }
  function beamFront(m) {
    m.box('beam_f_web', [24, 77, 7], [60, 83, 9], 'pnt', { split: '' });
    m.box('beam_f_fl_t', [24, 83, 4], [60, 84, 12], 'pnt', { split: '' });
    m.box('beam_f_fl_b', [24, 76, 4], [60, 77, 12], 'pnt', { split: '' });
    for (const x of [36, 48]) m.box(`stiff_${x}`, [x, 77, 4.6], [x + 1, 83, 11.4], 'pnt', { split: '' });
  }
  // Horsehead: side plates and an arc face approximated by three plates about the pivot-centred arc of radius 48.
  function horsehead(m) {
    m.box('hh_side_n', [58, 66, 3], [71, 94, 4], 'pnt', { split: '' });
    m.box('hh_side_s', [58, 66, 12], [71, 94, 13], 'pnt', { split: '' });
    m.box('hh_face_mid', [70, 74, 3], [72, 86, 13], 'st', { split: '' });
    for (const s of [1, -1]) {
      const a = 12 * Math.PI / 180, cx = PIV[0] + 47 * Math.cos(a), cy = PIV[1] + s * 47 * Math.sin(a);
      m.box(`hh_face_${s}`, [cx - 1, cy - 6, 3], [cx + 1, cy + 6, 13], 'st', { split: '', rot: { axis: 'z', angle: s * 22.5, origin: [cx, cy, 8] } });
    }
    m.box('hh_back', [58, 66, 4], [60, 94, 12], 'pnt', { split: '' });
    m.box('hh_latch', [56, 78, 6], [60, 82, 10], 'ci', { split: '' });
  }
  function pitman(m) {
    // drawn with the crank-pin eye at (0, 0); length 44 up +y to the equalizer
    for (const z of [-12, 25]) {
      m.box(`pitman_${z}`, [-1.4, 2, z], [1.4, 42, z + 3], 'pnt', { split: '' });
      m.prism(`eye_lo_${z}`, 'z', [0, 0], 2, z - 0.4, z + 3.4, 'ci', { split: '', ends: ['north', 'south'] });
      m.prism(`eye_hi_${z}`, 'z', [0, 44], 2, z - 0.4, z + 3.4, 'ci', { split: '', ends: ['north', 'south'] });
    }
  }
  function crank(m) {
    // crank arms at z -8..-4 and 20..24, the pin at radius 13 on +x, counterweights on the -x side
    for (const [z0, z1] of [[-8, -4], [20, 24]]) {
      m.box(`crank_arm_${z0}`, [CR[0] - 8, CR[1] - 4, z0], [CR[0] + CRR + 3, CR[1] + 4, z1], 'ci', { split: '' });
      m.box(`cw_${z0}`, [CR[0] - 22, CR[1] - 8, z0 - 1], [CR[0] - 8, CR[1] + 8, z1 + 1], 'dark', { split: '' });
      m.box(`cw_bolt_${z0}`, [CR[0] - 16, CR[1] - 1, z0 - 1.4], [CR[0] - 14, CR[1] + 1, z1 + 1.4], 'st', { split: '' });
      m.prism(`crank_pin_${z0}`, 'z', [CR[0] + CRR, CR[1]], 1.4, z0 < 0 ? -12.4 : z1, z0 < 0 ? z0 : 28.4, 'bright', { split: '', ends: ['north', 'south'] });
    }
  }
  function carrier(m) {
    m.box('carrier_bar', [WELL - 6, CARRY, 6], [WELL + 6, CARRY + 2, 10], 'st', { split: '' });
    m.box('rod_clamp', [WELL - 1.6, CARRY - 3, 6.4], [WELL + 1.6, CARRY, 9.6], 'ci', { split: '' });
    for (const z of [6.2, 9.2]) m.box(`bridle_${z}`, [WELL - 0.3, CARRY + 2, z], [WELL + 0.3, CARRY + 32, z + 0.6], 'rope', { split: '' });
    for (const x of [WELL - 5, WELL + 4]) m.box(`bridle_end_${x}`, [x, CARRY + 2, 7], [x + 1, CARRY + 4, 9], 'ci', { split: '' });
  }
  function polishedRod(m) {
    m.cylY('polished_rod', [WELL, 8], 0.8, CARRY - 40, CARRY - 3, 'bright');
  }

  const PART = {
    beam_rear: { draw: beamRear, shift: [1, -64, 0] },
    beam_front: { draw: beamFront, shift: [-28, -64, 0] },
    horsehead: { draw: horsehead, shift: [-48, -64, 0] },
    pitman: { draw: pitman, shift: [0, -14, 0] },
    crank: { draw: crank, shift: [22, -8, 0] },
    carrier: { draw: carrier, shift: [-64, -52, 0] },
    polished_rod: { draw: polishedRod, shift: [-64, -20, 0] },
  };
  function model() { const m = mk(); base(m); samson(m); gearbox(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'pumpjack_real';
  window.PJK = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'oil_production'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'oil_production'),
    // the pitman drawn upright from the crank pin at angle 0, (-3, 28)
    preview: (root) => {
      const m = model();
      for (const k of ['beam_rear', 'beam_front', 'horsehead', 'crank', 'carrier', 'polished_rod']) { const s = mk(); PART[k].draw(s); m.merge(s, 0, 0, 0, k + '_'); }
      const s = mk(); pitman(s); m.merge(s, CR[0] + CRR, CR[1], 0, 'pitman_');
      PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) };
    },
  };
})();
