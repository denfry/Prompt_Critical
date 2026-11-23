// Double-ended ranging drum shearer at real scale (1 block = 1 m) on its armoured face conveyor, mid-seam version for
// a 2 m seam. Dimensions and sources: docs/research/longwall_shearer-dimensions.md (disk only).
//
// Footprint box(-6, 5, 0, 1, 0, 1) agreed with 8f: x -96..96 along the face, y 0..32, z 0..32, controller cell
// (0, 0, 0) in the middle. North (z 0) is the coal face: row z = 0 holds the drums, row z = 1 the conveyor pans,
// the body riding on them, the rack, the spill plate and the cable handler on the goaf side (z 32). The trailing
// cable enters at the tail end, cell (-6, 0, 1).
//
// Static whole model `longwall_shearer_real` (pans, body, gearheads, cable). Moving partials
// (`longwall_shearer_real/partial/*`):
// - `arm`: ranging arm pointing -x from its pivot at partial (28, 8, 8), plane z 5..11. West arm: pivot at model
//   (-PIV_X, PIV_Y, 16), no yaw. East arm: same pivot mirrored to (+PIV_X, PIV_Y, 16) and yawed 180 deg about y through
//   the pivot. Pitch about z through the pivot by phi (raises the drum): drum centre = pivot + ARM * (cos, sin).
// - `drum`: spins about z through partial (8, 8); its face end ring at partial z 1..2.5 sits at model z 0..1.5
//   (translate z by -1/16). Both drums use it unyawed, so the face ring stays on the coal side.
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then LWSR.build(repo) /* wait */ LWSR.save(repo);
// LWSR.buildPart(repo, p) / LWSR.savePart(repo, p) for p in arm, drum.
(function () {
  const ID = 'longwall_shearer_real';
  const PIV_X = 44, PIV_Y = 13, PIV_Z = 16, ARM = 38;   // arm pivot (west one at -PIV_X), pivot to drum centre
  const RD = 12.8, RB = 8.5;                             // drum radius (1.6 m) and barrel radius
  const PAN = [12, 30], DECK = 5;                       // conveyor pans z range, deck height
  const BODY = 34, GEAR = 50;                           // body half length, gearhead outer end
  const LW = 'longwall_shearer/';
  const MAT = {
    lw: { tex: LW + 'paint', P: 16 }, lwd: { tex: LW + 'dark', P: 16 }, drum: { tex: LW + 'drum', P: 16 },
    hose: { tex: LW + 'hose', P: 16 }, cable: { tex: LW + 'cable', P: 16 }, haz: { tex: LW + 'hazard', P: 16 },
    panel: { tex: LW + 'panel', P: 16 }, lamp: { tex: LW + 'lamp', P: 16 },
  };
  const mk = () => { const m = PCVV.mk(LW + 'paint'); Object.assign(m.mat, MAT); return m; };

  // ------------------------------------------------------------------ static: conveyor, body, gearheads, cable
  function model() {
    const m = mk();
    // armoured face conveyor: pans with joints every 1.5 m, chain channel and flight bars, rack, spill plate
    m.box('pans', [-96, 0, PAN[0]], [96, DECK, PAN[1]], 'lwd');
    m.box('toe', [-96, 0, PAN[0] - 0.8], [96, 2.2, PAN[0]], 'lwd', { split: 'x' });
    for (let x = -96 + 24; x < 96; x += 24) m.box(`pan_joint_${x}`, [x - 0.4, DECK, PAN[0]], [x + 0.4, DECK + 0.5, PAN[1]], 'st', { split: '' });
    m.box('chain_slot', [-96, DECK, 18], [96, DECK + 0.1, 24], 'bright', { split: 'x' });
    for (let x = -90; x < 96; x += 12) m.box(`flight_${x}`, [x - 0.5, DECK, 17], [x + 0.5, DECK + 0.6, 25], 'lwd', { split: '' });
    m.box('spill_plate', [-96, 0, 29], [96, 10, 32], 'lwd');
    m.box('rack', [-96, DECK, 26], [96, DECK + 2, 28.6], 'st', { split: 'x' });
    for (let x = -93; x < 96; x += 6) m.box(`rack_tooth_${x}`, [x - 1, DECK + 2, 26.2], [x + 1, DECK + 3, 28.4], 'st', { split: '' });
    // cable handler on top of the spill plate, the trailing cable lying in it
    m.box('handler_floor', [-96, 10, 28.6], [96, 10.6, 32], 'st', { split: 'x' });
    m.box('handler_lip', [-96, 10.6, 31.4], [96, 13, 32], 'st', { split: 'x' });
    m.box('cable_run', [-96, 10.6, 29.2], [-50, 12.4, 31], 'cable', { split: 'x' });

    // body: two haulage end sections and the centre electrical section, on face skids and goaf trapping shoes
    m.box('body', [-BODY, DECK + 1, PAN[0] + 1], [BODY, 19, PAN[1] - 1], 'lw');
    for (const x of [-BODY, -12, 12, BODY]) m.box(`flange_${x}`, [x - 0.5, DECK + 1, PAN[0] + 0.6], [x + 0.5, 19.4, PAN[1] - 0.6], 'lwd', { split: '' });
    m.box('fp_cover', [-11, 19, 15], [11, 20.2, 27], 'lw', { split: 'x' });
    for (let x = -10; x <= 10; x += 4) for (const z of [15.6, 26.4]) m.box(`cover_bolt_${x}_${z}`, [x - 0.4, 20.2, z - 0.4], [x + 0.4, 20.7, z + 0.4], 'bright', { split: '' });
    for (const s of [-1, 1]) {
      const x0 = s * 15, x1 = s * 31;
      m.box(`skid_${s}`, [Math.min(x0, x1), DECK - 0.4, PAN[0]], [Math.max(x0, x1), DECK + 1, PAN[0] + 2], 'lwd', { split: '' });
      m.box(`trap_shoe_${s}`, [Math.min(x0, x1) + 3, DECK + 1, 25], [Math.max(x0, x1) - 3, 10, 29.4], 'lwd', { split: 'x' });
      m.box(`sprocket_case_${s}`, [s * 23 - 4, DECK + 3, 27], [s * 23 + 4, 13, 29.6], 'lwd', { split: '' });
      // gearhead housing holding the arm pivot, hazard stripes on its outer end
      const g0 = s * BODY, g1 = s * GEAR;
      m.box(`gearhead_${s}`, [Math.min(g0, g1), DECK + 1, PAN[0] + 1], [Math.max(g0, g1), 17, PAN[1] - 1], 'lw');
      m.box(`hazard_${s}`, [s > 0 ? GEAR : -GEAR - 0.2, DECK + 2, 20], [s > 0 ? GEAR + 0.2 : -GEAR, 16, 28], 'haz', { split: '' });
      m.box(`lamp_${s}`, [s * (BODY - 3) - 1.5, 19, PAN[1] - 3], [s * (BODY - 3) + 1.5, 21, PAN[1] - 1], 'lamp', { split: '' });
    }
    // operator side (goaf): control panel, water hose to the drum sprays
    m.box('panel', [-9, 9, PAN[1] - 1], [9, 17, PAN[1] - 0.6], 'panel', { split: '' });
    m.box('hose', [-GEAR + 2, 19, PAN[1] - 4], [GEAR - 2, 20.2, PAN[1] - 2.8], 'hose', { split: 'x' });
    // trailing cable from the handler to the tail end of the body
    m.box('cable_rise', [-50, 11.2, 27], [-46, 15, 31], 'cable', { split: '' });
    m.box('cable_gland', [-GEAR - 0.8, 12, 24], [-GEAR, 18.6, 28], 'lwd', { split: '' });
    m.decal('plate', [-4, 12, PAN[1] - 0.6], [4, 16, PAN[1] - 0.4], 'lw', 'south', 'pl.maker');
    return m;
  }

  // ------------------------------------------------------------------ partials
  function arm() {
    const m = mk();
    m.disc('pivot_boss', 'z', [28, 8], 4, 5, 11, 'lw', LW + 'dark', 'north');
    m.box('arm', [-10, 4, 5.4], [28, 12, 10.6], 'lw', { split: 'x' });
    m.box('motor_hump', [8, 12, 6], [24, 14, 10], 'lw', { split: '' });
    m.disc('drum_boss', 'z', [-10, 8], 5.5, 4.6, 11.4, 'lw', LW + 'dark', 'north');
    m.box('spray_pipe', [-8, 12, 7.4], [8, 12.8, 8.6], 'hose', { split: '' });
    m.box('arm_stripe', [-4, 4, 10.6], [4, 12, 10.8], 'haz', { split: '' });
    return m;
  }

  // A two-diameter box through the axis is two opposite vanes; a slice rotated about z by a (any of the four
  // 22.5 deg steps, laid along x or y) stands in for the helix. Picks ride the vane tips.
  function drum() {
    const m = mk();
    const C = [8, 8], ROT = (a) => ({ axis: 'z', angle: a, origin: [C[0], C[1], 8] });
    m.disc('face_ring', 'z', C, RD, 1, 2.5, 'drum', LW + 'drum', 'north');
    m.prism('barrel', 'z', C, RB, 2.5, 13, 'drum', { split: '', ends: ['north', 'south'] });
    m.prism('hub', 'z', C, 3, 13, 15.5, 'lwd', { split: '', ends: ['south'] });
    const STEPS = [[0, 'x'], [22.5, 'x'], [45, 'x'], [-22.5, 'y']];   // 0, 22.5, 45, 67.5 degrees
    const R = RD - 0.6;
    STEPS.forEach(([a, along], k) => {
      const z0 = 2.5 + k * 2.6, z1 = z0 + 2.6;
      for (const [d, rot] of [[along, a], [along === 'x' ? 'y' : 'x', a]]) {
        const f = d === 'x' ? [C[0] - R, C[1] - 1.2, z0] : [C[0] - 1.2, C[1] - R, z0];
        const t = d === 'x' ? [C[0] + R, C[1] + 1.2, z1] : [C[0] + 1.2, C[1] + R, z1];
        m.box(`vane_${k}_${d}`, f, t, 'drum', { split: '', rot: rot ? ROT(rot) : undefined });
        for (const s of [-1, 1]) {
          const p = d === 'x' ? [C[0] + s * (R + 0.7), C[1], (z0 + z1) / 2] : [C[0], C[1] + s * (R + 0.7), (z0 + z1) / 2];
          m.box(`pick_${k}_${d}_${s}`, [p[0] - 0.7, p[1] - 0.7, p[2] - 0.5], [p[0] + 0.7, p[1] + 0.7, p[2] + 0.5], 'bright', { split: '', rot: rot ? ROT(rot) : undefined });
        }
      }
    });
    // denser picks round the face end ring, pointing at the coal
    for (const a of [0, 45, -45]) for (const s of [-1, 1]) for (const d of ['x', 'y']) {
      if (a !== 0 && d === 'y') continue;
      const p = d === 'x' ? [C[0] + s * (RD - 0.4), C[1]] : [C[0], C[1] + s * (RD - 0.4)];
      m.box(`ring_pick_${a}_${d}_${s}`, [p[0] - 0.6, p[1] - 0.6, 0], [p[0] + 0.6, p[1] + 0.6, 1.2], 'bright', { split: '', rot: a ? ROT(a) : undefined });
    }
    return m;
  }

  // drum centre for arm pitch phi (deg), west end s = -1, east s = +1
  const drumAt = (s, phi) => { const r = phi * Math.PI / 180; return [s * (PIV_X + ARM * Math.cos(r)), PIV_Y + ARM * Math.sin(r)]; };
  const PARTS = { arm, drum };
  window.LWSR = {
    ID, PARTS, PIV_X, PIV_Y, PIV_Z, ARM, RD, drumAt,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'mining'),
    buildPart: (root, p) => { const m = PARTS[p](); PCART.realize(root, m, p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePart: (root, p) => PCVV.savePartial(root, ID, p, 'mining'),
    // in place for the scale shot (never saved), both arms level (phi = 0): drums at pivot height, on the floor
    preview: (root) => {
      const m = model();
      for (const s of [-1, 1]) {
        const a = arm();
        if (s > 0) for (const e of a.els) {   // yaw 180 about the pivot (28, 8): x -> 56 - x, z -> 16 - z
          const fx = 56 - e.to[0], tx = 56 - e.from[0], fz = 16 - e.to[2], tz = 16 - e.from[2];
          e.from = [fx, e.from[1], fz]; e.to = [tx, e.to[1], tz];
          if (e.rot) e.rot = Object.assign({}, e.rot, { origin: [56 - e.rot.origin[0], e.rot.origin[1], 16 - e.rot.origin[2]] });
          if (e.faces) { const f = e.faces; [f.east, f.west, f.north, f.south] = [f.west, f.east, f.south, f.north]; }
        }
        m.merge(a, s * PIV_X - 28, PIV_Y - 8, PIV_Z - 8, `arm${s}_`);
        const [dx, dy] = drumAt(s, 0);
        m.merge(drum(), dx - 8, dy - 8, -1, `drum${s}_`);
      }
      PCART.realize(root, m, 'shearer_preview');
      return m.els.length;
    },
  };
})();
