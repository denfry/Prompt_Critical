// VVER-70 fuel handling at real scale (1 block = 1 m, 16 px = 1 m): the spent fuel pool (5 x 5 m basin, 8 m deep, on 1 m
// concrete walls, a stainless liner, 256 rack positions on a 0.25 m pitch, the transfer slot with its gate, cooling
// lines, ladder and edge railing) and the refuelling machine (a bridge on floor rails over the reactor cavity and the
// pool, with a trolley, operator cabin and a telescopic mast with its gripper). Rheinsberg held 246 assemblies in its
// pool at shutdown; everything else is estimated. Sheet: docs/research/2026-11-24-reactor-prototypes/
// vver70_fuel_handling.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for k in 'fuel_pool',
// 'refuelling_machine':
//   V70F.build(repo, k); /* wait */ V70F.save(repo, k)
//   for (const p of V70F.parts(k)) { V70F.buildPartial(repo, k, p); V70F.savePartial(repo, k, p) }
//
// Fuel pool (vver70_fuel_pool_real): controller (0, 0, 0) at the pool floor centre, front north (the slot side).
// Footprint x -3..3, y 0..8, z -3..3. Basin inside x -32..48, z -32..48, floor y 2.4, top y 128; water to be drawn by
// the renderer at y 120 (the model leaves the inside open). Ports: cooling water out on the west face of (-3, 6, 0) at
// (-48, 104, 8); cooling water in on the east face of (3, 6, 0) at (64, 104, 8); fuel transfer through the slot in the
// north wall, cells (0, 1..7, -3), slot x 0..16 from y 16 up. Partial gate: the slot gate, slides up y 0..64 px; stored
// -32 y, +40 z, draw at +32 y, -40 z (gate y 16..64 as stored).
// Refuelling machine (vver70_refuelling_machine_real): controller (0, 0, 0) at floor level in the middle of the
// runway, front north. Footprint x -4..4, y 0..3, z -6..6. Static: the two floor rails, end stops, cable reel. Ports:
// power and control on the east face of (4, 0, 6) at (80, 8, 112). Partials (all at the bridge's middle position,
// z 0..16 about z 8; they travel together along z -96..112; trolley, mast and gripper also along x on the bridge):
// - bridge_0..bridge_3: the gantry bridge in four pieces x -64..-30, -30..8, 8..46, 46..80 (legs and trucks on the
//   end pieces); piece n stored shifted (-x0 - 16, -17, 0).
// - trolley: on the bridge top, y 48..60; stored (0, -28, 0).
// - cabin: hung under the bridge at x 40..58; stored (-34, -4, 0).
// - mast_outer: outer mast y 12..60 at (8, 8); stored (0, -28, 0); travels with the trolley.
// - mast_inner_0..mast_inner_3: the inner tube in 3 m pieces hanging below the outer mast, piece n spans
//   y 12 - 48 (n + 1) .. 12 - 48 n as drawn fully lowered; stored (0, 48 n + 20, 0); they slide up into the outer mast.
// - gripper: at the bottom of the inner tube, drawn y -8..0 about (8, 8); no shift.
(function () {
  const C = [8, 8];

  // ------------------------------------------------------------------ pool
  function pool() {
    const m = PCVV.mk();
    m.box('floor', [-32, 0, -32], [48, 2, 48], 'conc', { split: 'xz' });
    m.box('liner_floor', [-32, 2, -32], [48, 2.4, 48], 'st', { split: 'xz' });
    // concrete walls 1 m with the slot in the north wall
    m.box('wall_w', [-48, 0, -48], [-32, 128, 64], 'conc', { split: 'yz' });
    m.box('wall_e', [48, 0, -48], [64, 128, 64], 'conc', { split: 'yz' });
    m.box('wall_s', [-32, 0, 48], [48, 128, 64], 'conc', { split: 'xy' });
    m.box('wall_n_w', [-32, 0, -48], [0, 128, -32], 'conc', { split: 'xy' });
    m.box('wall_n_e', [16, 0, -48], [48, 128, -32], 'conc', { split: 'xy' });
    m.box('wall_n_sill', [0, 0, -48], [16, 16, -32], 'conc', { split: '' });
    // stainless liner on the inner faces
    m.box('liner_w', [-32, 2, -32], [-31.6, 128, 48], 'st', { split: 'yz' });
    m.box('liner_e', [47.6, 2, -32], [48, 128, 48], 'st', { split: 'yz' });
    m.box('liner_s', [-32, 2, 47.6], [48, 128, 48], 'st', { split: 'xy' });
    m.box('liner_n_w', [-32, 2, -32], [0, 128, -31.6], 'st', { split: 'xy' });
    m.box('liner_n_e', [16, 2, -32], [48, 128, -31.6], 'st', { split: 'xy' });
    for (const [n, f, t] of [['slot_w', [-0.4, 16, -48], [0, 128, -32]], ['slot_e', [16, 16, -48], [16.4, 128, -32]], ['slot_sill', [0, 15.6, -48], [16, 16, -32]]]) m.box(`liner_${n}`, f, t, 'st', { split: '' });
    // racks: base frame and a grid of plates, 16 x 16 cells on a 4 px pitch over x, z -24..40
    m.box('rack_base', [-25, 2.4, -25], [41, 6, 41], 'st', { split: 'xz' });
    for (let k = 0; k <= 16; k++) {
      const p = -24 + k * 4;
      m.box(`rack_x_${k}`, [p - 0.15, 6, -24], [p + 0.15, 56, 40], 'bright', { split: '' });
      m.box(`rack_z_${k}`, [-24, 6, p - 0.15], [40, 56, p + 0.15], 'bright', { split: '' });
    }
    // 246 assembly heads standing in the racks
    let n = 0;
    for (let i = 0; i < 16 && n < 246; i++) for (let j = 0; j < 16 && n < 246; j++, n++) {
      const x = -24 + i * 4 + 2, z = -24 + j * 4 + 2;
      m.box(`fa_head_${i}_${j}`, [x - 1.3, 54, z - 1.3], [x + 1.3, 57.6, z + 1.3], 'dark', { split: '' });
    }
    // cooling lines: out from the west wall, in through the east wall with a sparger
    m.nozzle('cool_out', 'x', [104, 8], -32, -48, 3.6, 2.6, 'gw', { nuts: 8 });
    m.box('cool_out_suction', [-31.6, 60, 6.6], [-28.8, 104, 9.4], 'gw', { split: 'y' });
    m.nozzle('cool_in', 'x', [104, 8], 48, 64, 3.6, 2.6, 'gw', { nuts: 8 });
    m.box('cool_in_drop', [44.8, 64, 6.6], [47.6, 104, 9.4], 'gw', { split: 'y' });
    m.box('sparger', [-20, 62, 7], [44.8, 64, 9], 'gw', { split: 'x' });
    // ladder down the south wall and the edge railing on the wall tops (open at the slot)
    m.ladderZ('ladder', 26, 32, 47.6, -1, 4, 128, 'st', 0);
    const rails = [['w', [-48, 128, -48], [-47.4, 0, 64], 'z'], ['e', [63.4, 128, -48], [64, 0, 64], 'z'], ['s', [-48, 128, 63.4], [64, 0, 64], 'x'], ['n_w', [-48, 128, -48], [0, 0, -47.4], 'x'], ['n_e', [16, 128, -48], [64, 0, -47.4], 'x']];
    for (const [n2, f, t, ax] of rails) {
      m.box(`rail_top_${n2}`, [f[0], 142.6, f[2]], [t[0], 143.4, t[2]], 'yel', { split: ax });
      m.box(`rail_mid_${n2}`, [f[0], 135, f[2]], [t[0], 135.6, t[2]], 'yel', { split: ax });
    }
    for (const [x, z] of [[-48, -48], [-48, 8], [-48, 63.4], [63.4, -48], [63.4, 8], [63.4, 63.4], [8, 63.4], [-0.6, -48], [16, -48]]) {
      m.box(`rail_post_${x}_${z}`, [x, 128, z], [x + 0.6, 143.4, z + 0.6], 'yel', { split: '' });
    }
    m.decal('sign', [20, 110, -31.6], [30, 120, -31], 'st', 'south', 'pl.sign');
    return m;
  }
  function gate() {
    const m = PCVV.mk();
    m.box('gate', [0.4, 16, -42], [15.6, 61, -38], 'st', { split: '' });
    m.box('gate_seal', [0, 16, -42.4], [16, 17, -37.6], 'dark', { split: '' });
    m.box('gate_eye', [6, 61, -41], [10, 64, -39], 'st', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ refuelling machine
  function runway() {
    const m = PCVV.mk();
    for (const x of [-62, 74]) {
      m.box(`rail_${x}`, [x, 0, -96], [x + 4, 2.6, 112], 'pnt', { split: 'z' });
      m.box(`rail_head_${x}`, [x + 1, 2.6, -96], [x + 3, 3.4, 112], 'bright', { split: 'z' });
      for (const z of [-96, 108]) m.box(`stop_${x}_${z}`, [x - 1, 0, z], [x + 5, 6, z + 4], 'yel', { split: '' });
    }
    m.prism('cable_reel', 'x', [12, 106], 5, 66, 74, 'pnt', { split: '', ends: ['west', 'east'] });
    m.box('reel_stand', [67, 0, 103], [73, 8, 109], 'pnt', { split: '' });
    m.box('reel_box', [74, 2, 104], [80, 12, 112], 'pnt', { split: '' });
    return m;
  }
  function bridgePiece(x0, x1, leg) {
    return () => {
      const m = PCVV.mk();
      m.box(`girder_${x0}`, [x0, 36, 0], [x1, 48, 16], 'yel', { split: '' });
      m.box(`girder_rail_${x0}`, [x0, 48, 2], [x1, 48.6, 4], 'bright', { split: '' });
      m.box(`girder_rail2_${x0}`, [x0, 48, 12], [x1, 48.6, 14], 'bright', { split: '' });
      if (leg) {
        const lx = leg < 0 ? -62 : 74;
        m.box(`leg_${leg}`, [lx, 12, 2], [lx + 4, 36, 14], 'yel', { split: '' });
        m.box(`truck_${leg}`, [lx - 2, 3.4, -8], [lx + 6, 12, 24], 'yel', { split: '' });
        for (const z of [-6, 18]) m.prism(`wheel_${leg}_${z}`, 'x', [6, z + 2], 3, lx, lx + 4, 'dark', { split: '', ends: ['west', 'east'] });
        m.box(`drive_${leg}`, [lx + (leg < 0 ? 4 : -6), 6, 18], [lx + (leg < 0 ? 10 : 0), 12, 24], 'motor', { split: '' });
      }
      return m;
    };
  }
  function trolley() {
    const m = PCVV.mk();
    m.box('trolley', [-6, 48.6, -2], [22, 56, 18], 'pnt', { split: '' });
    m.box('winch', [-4, 56, 0], [8, 60, 16], 'motor', { split: '' });
    m.prism('winch_drum', 'z', [14, 58], 2, 2, 14, 'st', { split: '', ends: ['north', 'south'] });
    return m;
  }
  function cabin() {
    const m = PCVV.mk();
    m.box('cabin', [40, 16, 2], [58, 34, 16], 'pnt', { split: '' });
    m.box('cabin_window', [44, 22, 1.6], [56, 30, 2], 'dark', { split: '' });
    m.box('cabin_hanger', [46, 34, 6], [52, 36, 12], 'pnt', { split: '' });
    return m;
  }
  function mastOuter() {
    const m = PCVV.mk();
    m.box('mast_outer', [4.4, 12, 4.4], [11.6, 48.6, 11.6], 'st', { split: '' });
    m.box('mast_collar', [3.4, 12, 3.4], [12.6, 14, 12.6], 'ci', { split: '' });
    return m;
  }
  function mastInner(n) {
    return () => {
      const m = PCVV.mk(), y0 = 12 - 48 * (n + 1), y1 = 12 - 48 * n;
      m.box(`mast_inner_${n}`, [5.4, y0, 5.4], [10.6, y1, 10.6], 'bright', { split: '' });
      m.box(`mast_mark_${n}`, [10.6, y1 - 4, 7.6], [10.8, y1 - 2, 8.4], 'red', { split: '' });
      return m;
    };
  }
  function gripper() {
    const m = PCVV.mk();
    m.box('gripper_body', [4.6, -6, 4.6], [11.4, 0, 11.4], 'ci', { split: '' });
    for (const [dx, dz] of [[-2.4, 0], [2.4, 0], [0, -2.4], [0, 2.4]]) m.box(`finger_${dx}_${dz}`, [8 + dx - 0.5, -8, 8 + dz - 0.5], [8 + dx + 0.5, -6, 8 + dz + 0.5], 'bright', { split: '' });
    return m;
  }

  const B = [[-64, -30, -1], [-30, 8, 0], [8, 46, 0], [46, 80, 1]];
  const fmParts = {};
  B.forEach(([x0, x1, leg], i) => { fmParts[`bridge_${i}`] = { draw: bridgePiece(x0, x1, leg), shift: [-x0 - 16, -17, 0] }; });
  Object.assign(fmParts, {
    trolley: { draw: trolley, shift: [0, -28, 0] },
    cabin: { draw: cabin, shift: [-34, -4, 0] },
    mast_outer: { draw: mastOuter, shift: [0, -28, 0] },
    gripper: { draw: gripper, shift: [0, 0, 0] },
  });
  for (let n = 0; n < 4; n++) fmParts[`mast_inner_${n}`] = { draw: mastInner(n), shift: [0, 48 * n + 20, 0] };

  const UNITS = {
    fuel_pool: { id: 'vver70_fuel_pool_real', draw: pool, parts: { gate: { draw: gate, shift: [0, -32, 40] } } },
    refuelling_machine: { id: 'vver70_refuelling_machine_real', draw: runway, parts: fmParts },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return PCVV.mk().merge(d.draw(), dx, dy, dz, ''); };
  window.V70F = {
    UNITS, partial,
    parts: (k) => Object.keys(UNITS[k].parts),
    model: (k) => UNITS[k].draw(),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'core/vver70'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'core/vver70'),
    preview: (root) => {
      const m = pool(); m.merge(gate(), 0, 0, 0, 'g_');
      const r = runway(); for (const [k2, d] of Object.entries(fmParts)) if (!k2.startsWith('mast_inner_2') && !k2.startsWith('mast_inner_3')) r.merge(d.draw(), 0, 0, 0, k2 + '_');
      m.merge(r, 0, 144, 0, 'fm_'); PCVV.cut(m); PCART.realize(root, m, 'vver70_fuel_handling_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) };
    },
  };
})();
