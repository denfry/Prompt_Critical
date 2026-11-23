// Coke-fired pit crucible furnace at real scale (1 block = 1 m, 16 units = 1 m): a "melting hole" of a brass
// foundry for the sim's 100 kg crucible. A firebrick-lined square pit sunk in the floor with its grate and ash pit,
// the iron floor frame and the firebrick lid with its handle, the flue from the top of the pit into a brick stack
// stub, and a sunk stoke-hole aisle with the ash door and steps. Dimensions and sources:
// docs/research/crucible_furnace-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   CRUC.build(repo); /* wait */ CRUC.save(repo)
//   CRUC.buildPartial(repo, name); CRUC.savePartial(repo, name)     for lid, glow
//   CRUC.preview(repo)                                               everything, for looking only
//
// Controller block = (0, 0, 0): the floor-level cell over the pit, front north. Footprint x 0..1, y -1..0,
// z 0..1. Connections (px): charging and crucible through the lid on top of (0, 0, 0); flue gas out top of
// (1, 0, 0) at (26, 16, 8). Climb cells: the steps in (1, -1, 1).
(function () {
  const P = [7, 17, 3, 13], PC = [12, 8];            // pit x0, x1, z0, z1 and centre [x, z]
  const YG = -12, YF = 0;                             // grate, floor

  const MAT = Object.assign({}, PCVV.MAT);
  const FIT = { slot_glow: [30, 10, 1, 5], soot_door: [12, 10, 8, 6] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT },
  }), { particle: 'material/brick' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  function glowBox(m, name, f, t) {
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', hide: [], face });
  }

  // Brick mass round the pit, lined with firebrick (ashlar reads as the pale firebrick), the flue and stack stub.
  function pit(m) {
    m.box('mass_w', [0, -16, 0], [P[0], YF, 16], 'brick', { hide: ['down', 'up'] });
    m.box('mass_e', [P[1], -16, 0], [22, YF, 16], 'brick', { hide: ['down', 'up'] });
    m.box('mass_n', [P[0], -16, 0], [P[1], YF, P[2]], 'brick', { hide: ['down', 'up'] });
    m.box('mass_s', [P[0], -16, P[3]], [P[1], YF, 16], 'brick', { hide: ['down', 'up'] });
    for (const [n, f, t] of [['w', [P[0] - 0.01, -16, P[2]], [P[0], YF, P[3]]], ['e', [P[1], -16, P[2]], [P[1] + 0.01, YF, P[3]]],
      ['n', [P[0], -16, P[2] - 0.01], [P[1], YF, P[2]]], ['s', [P[0], -16, P[3]], [P[1], YF, P[3] + 0.01]]]) {
      m.box(`lining_${n}`, f, t, 'ashlar', { split: '' });
    }
    m.box('ash_floor', [P[0], -16, P[2]], [P[1], -15.6, P[3]], 'soot', { split: '' });
    for (let i = 0; i < 6; i++) {
      const x = P[0] + 0.6 + i * 1.6;
      m.box(`grate_bar_${i}`, [x, YG - 0.8, P[2]], [x + 0.8, YG, P[3]], 'ci', { split: '' });
    }
    m.box('grate_bearer_n', [P[0], YG - 1.6, P[2]], [P[1], YG - 0.8, P[2] + 1], 'ci', { split: '' });
    m.box('grate_bearer_s', [P[0], YG - 1.6, P[3] - 1], [P[1], YG - 0.8, P[3]], 'ci', { split: '' });
    // crucible on its stand, coke packed round it
    m.box('crucible_stand', [PC[0] - 2, YG, PC[1] - 2], [PC[0] + 2, YG + 1.2, PC[1] + 2], 'ashlar', { split: '' });
    m.cylY('crucible', PC, 2.6, YG + 1.2, -3.2, 'dark');
    m.ringY('crucible_rim', PC, 2.8, 0.5, -3.6, -3.2, 'dark');
    m.box('coke', [P[0], YG, P[2]], [P[1], -5, P[3]], 'soot', { split: '', hide: ['down'] });
    // ash door on the south face into the stoke-hole aisle
    m.decal('ash_door', [8, -15.4, 16], [16, -12.4, 16.6], 'ci', 'south', 'fit.soot_door', { hide: ['north'] });
    m.box('ash_door_frame', [7.4, -16, 16], [16.6, -11.8, 16.3], 'ci', { split: '', hide: ['north'] });
    // floor frame round the pit and the floor plates over the brick mass
    m.box('frame_n', [P[0] - 1.4, YF - 0.6, P[2] - 1.4], [P[1] + 1.4, YF + 0.3, P[2]], 'ci', { split: '' });
    m.box('frame_s', [P[0] - 1.4, YF - 0.6, P[3]], [P[1] + 1.4, YF + 0.3, P[3] + 1.4], 'ci', { split: '' });
    m.box('frame_w', [P[0] - 1.4, YF - 0.6, P[2]], [P[0], YF + 0.3, P[3]], 'ci', { split: '' });
    m.box('frame_e', [P[1], YF - 0.6, P[2]], [P[1] + 1.4, YF + 0.3, P[3]], 'ci', { split: '' });
    for (const [n, f, t] of [['w', [0, YF - 0.4, 0], [P[0] - 1.4, YF, 16]], ['n', [P[0] - 1.4, YF - 0.4, 0], [22, YF, P[2] - 1.4]],
      ['s', [P[0] - 1.4, YF - 0.4, P[3] + 1.4], [22, YF, 16]], ['e', [P[1] + 1.4, YF - 0.4, P[2] - 1.4], [22, YF, P[3] + 1.4]]]) {
      m.box(`floor_${n}`, f, t, 'chequer', { split: '', hide: ['down'] });
    }
  }

  function flue(m) {
    // flue from the top of the pit east wall into the stack stub (x 22..30, z 2..14), up to y 16
    m.box('flue_mouth', [P[1] - 0.02, -5, 5], [P[1] + 0.02, -1, 11], 'soot', { split: '' });
    m.box('stack', [22, -16, 1], [31, 14, 15], 'brick', { hide: ['down', 'up'] });
    m.box('stack_cap', [21.6, 14, 0.6], [31.4, 15.2, 15.4], 'ci', { split: '', hide: ['up'] });
    m.box('stack_mouth', [23.4, 14.6, 2.4], [29.6, 15.25, 13.6], 'soot', { split: '' });
    for (const y of [-8, 2, 10]) m.box(`stack_band_${y}`, [21.8, y, 0.8], [31.2, y + 0.6, 15.2], 'ci', { split: '' });
    // damper: a sliding plate through the stack with its handle
    m.box('damper_plate', [20, 4, 3], [22, 4.6, 13], 'ci', { split: '' });
    m.box('damper_handle', [18.6, 3.8, 7.4], [20, 4.8, 8.6], 'bright', { split: '' });
  }

  // Sunk stoke-hole aisle along z 16..32: floor plates, a coke bin, steps up in (1, -1, 1), a kerb at floor level.
  function aisle(m) {
    m.box('aisle_floor', [0, -16, 16], [32, -15.4, 32], 'chequer', { split: '', hide: ['down'] });
    m.box('coke_bin', [1, -15.4, 18], [9, -10, 30], 'wood', { split: '' });
    m.box('coke_bin_coke', [1.6, -10.6, 18.6], [8.4, -10, 29.4], 'soot', { split: '' });
    for (let k = 0; k < 4; k++) m.box(`stair_${k}`, [20, -15.4, 20 + 3 * k], [32, -12 + 4 * k, 32], 'ashlar', { split: '' });
    m.box('kerb_s', [0, -1, 31], [20, 0.4, 32], 'ashlar', { split: '' });
    m.box('kerb_w', [0, -1, 16], [1, 0.4, 31], 'ashlar', { split: '' });
    m.box('tongs_rest', [10.6, -15.4, 30], [17.4, -11, 31.4], 'ci', { split: '' });
    m.box('tongs', [11, -11, 30.2], [17, -10.4, 31.2], 'bright', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function lid(m) {
    m.box('lid_frame', [P[0] - 1.2, YF + 0.3, P[2] - 1.2], [P[1] + 1.2, YF + 1, P[3] + 1.2], 'ci', { split: '' });
    m.box('lid_brick', [P[0] - 0.8, YF + 1, P[2] - 0.8], [P[1] + 0.8, YF + 2.4, P[3] + 0.8], 'ashlar', { split: '' });
    m.box('lid_handle_bar', [PC[0] - 3, YF + 3.8, PC[1] - 0.3], [PC[0] + 3, YF + 4.4, PC[1] + 0.3], 'bright', { split: '' });
    for (const x of [PC[0] - 3, PC[0] + 2.4]) m.box(`lid_handle_post_${x}`, [x, YF + 2.4, PC[1] - 0.3], [x + 0.6, YF + 3.8, PC[1] + 0.3], 'bright', { split: '' });
  }
  function glow(m) {
    glowBox(m, 'coke', [P[0] + 0.2, -5, P[2] + 0.2], [P[1] - 0.2, -4.9, P[3] - 0.2]);
    glowBox(m, 'grate', [P[0] + 0.2, YG - 0.9, P[2] + 0.2], [P[1] - 0.2, YG - 0.85, P[3] - 0.2]);
  }

  const PART = {
    lid: { draw: lid, shift: [0, 0, 0], note: 'closed pose; open = lift 4 px and slide 10 px +x' },
    glow: { draw: glow, shift: [0, 0, 0], note: 'full-bright while lit' },
  };
  function model() { const m = mk(); pit(m); flue(m); aisle(m); return m; }
  function partial(name) {
    const src = mk(); PART[name].draw(src);
    const [dx, dy, dz] = PART[name].shift;
    return mk().merge(src, dx, dy, dz, '');
  }
  function preview() {
    const m = model();
    for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); }
    return m;
  }

  window.CRUC = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'crucible_furnace_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'crucible_furnace_real', 'foundry/crucible'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'crucible_furnace_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'crucible_furnace_real', name, 'foundry/crucible'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'crucible_furnace_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
