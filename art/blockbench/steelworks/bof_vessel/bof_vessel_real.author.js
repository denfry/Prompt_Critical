// LD (basic oxygen) converter at real scale (1 block = 1 m, 16 units = 1 m) for the sim's ≈ 15 t heat: a welded
// vessel ⌀3.6 m × 4.8 m (dished bottom, barrel, cone to a ⌀1.4 m mouth) in a box-section trunnion ring, trunnion
// pins in bearings on two steel pedestals, a trunnion-mounted tilt reducer on the west pin held by a torque arm, the
// fixed water-cooled hood over the mouth with its gas duct, and a small platform with a ladder at the east bearing.
// Dimensions and sources: docs/research/bof_vessel-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   BOF.build(repo); /* wait */ BOF.save(repo)                 static whole model bof_vessel_real
//   BOF.buildTilt(repo); /* wait */ BOF.saveTilt(repo)         tilting group as whole model bof_vessel_real_tilt
//   BOF.buildPartial(repo, name); BOF.savePartial(repo, name)  for mouth_glow
//   BOF.preview(repo)                                          everything, for looking only
//
// Controller block = (0, 0, 0): bottom centre under the vessel, front north (charging), tapping south. Footprint
// x -3..3, y 0..5, z -3..3. The tilting group turns about x through (8, 46, 8). Ports (px): tilt shaft in west of
// (-3, 2, 0) at (-48, 46, 8), coaxial with the trunnion; converter gas out on top of (0, 5, 1) at (8, 96, 24); the
// oxygen lance block sits at (0, 6, 0). Climb: the ladder column (3, *, 0) to the platform at y 40.
(function () {
  const C = [8, 8], AY = 46, R = 28.8, YB = 4.4, Y1 = 12, Y2 = 62, YM = 80, RM = 11.2;

  const MAT = Object.assign({}, PCVV.MAT);
  const FIT = { slot_glow: [30, 10, 1, 5] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT },
  }), { particle: 'material/steel' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  function glowBox(m, name, f, t) {
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', hide: [], face });
  }
  const o = { split: '' };

  // ------------------------------------------------------------------ tilting group
  function tilt(m) {
    m.headDown('bottom', C, R, Y1, Y1 - YB, 4, 'st');
    m.cylY('barrel', C, R, Y1, Y2, 'st');
    const n = 6;
    for (let i = 0; i < n; i++) {
      const r = R + (RM - R) * (i + 0.5) / n;
      m.cylY(`cone_${i}`, C, r, Y2 + (YM - Y2) * i / n, Y2 + (YM - Y2) * (i + 1) / n, i >= 4 ? 'dark' : 'st', { seed: i, cap: i >= 4 ? 'vver/dark' : 'material/steel' });
    }
    m.ringY('mouth_lip', C, RM + 0.6, 2.4, YM, YM + 1.6, 'ci');
    m.cylY('mouth', C, RM - 1.6, YM - 0.6, YM + 0.4, 'soot', o);
    // stiffening bands on the barrel and the taphole on the cone, tapping side (south)
    for (const y of [20, 58]) m.ringY(`band_${y}`, C, R + 0.3, 0.8, y, y + 1.2, 'ci');
    m.box('taphole', [6, 66, 27], [10, 70, 32], 'ci', o);
    m.box('taphole_mouth', [6.8, 66.8, 32], [9.2, 69.2, 32.2], 'soot', o);
    // trunnion ring with its brackets to the shell and the trunnion pins
    m.ringY('ring', C, 33, 5, AY - 6, AY + 6, 'pnt');
    m.ringY('ring_top', C, 33, 6, AY + 6, AY + 7, 'ci');
    m.ringY('ring_bot', C, 33, 6, AY - 7, AY - 6, 'ci');
    for (const a of [45, 135, -45, -135]) m.radial(`bracket_${a}`, C, R, 30.6, a, 4, AY - 6, AY + 6, 'ci');
    m.prism('pin_w', 'x', [AY, 8], 4.4, -38, -25, 'bright', { split: '', ends: ['west'] });
    m.prism('pin_e', 'x', [AY, 8], 4.4, 41, 56, 'bright', { split: '', ends: ['east'] });
    m.prism('pin_boss_w', 'x', [AY, 8], 7, -27.6, -25, 'ci', { split: '', ends: ['west'] });
    m.prism('pin_boss_e', 'x', [AY, 8], 7, 41, 43.6, 'ci', { split: '', ends: ['east'] });
  }

  // ------------------------------------------------------------------ static
  function pedestal(m, n, x0, x1) {
    m.box(`ped_${n}`, [x0, 0, -6], [x1, 36, 22], 'st');
    m.box(`ped_${n}_sole`, [x0 - 1, 0, -7], [x1 + 1, 1.4, 23], 'ci', o);
    for (const z of [-6, 21]) m.box(`ped_${n}_stiff_${z}`, [x0 + 2, 1.4, z - 0.6], [x1 - 2, 36, z + 1.6], 'ci', o);
    m.box(`brg_${n}`, [x0 + 1, 36, -2], [x1 - 1, AY, 18], 'ci', o);
    m.prism(`brg_${n}_cap`, 'x', [AY, 8], 8, x0 + 1, x1 - 1, 'ci', { split: '', ends: ['east', 'west'], cullY: AY });
    m.box(`brg_${n}_bolt_a`, [x0 + 2, AY, -1.6], [x0 + 3, AY + 2, -0.6], 'bright', o);
    m.box(`brg_${n}_bolt_b`, [x0 + 2, AY, 16.6], [x0 + 3, AY + 2, 17.6], 'bright', o);
  }
  function drive(m) {
    m.box('reducer', [-47.4, 30, -2], [-40, 60, 18], 'pnt', o);
    for (const y of [36, 46, 56]) m.box(`reducer_rib_${y}`, [-48, y, -2], [-47.4, y + 1, 18], 'pnt', o);
    m.box('reducer_cover', [-47, 32, -2.6], [-41, 58, -2], 'ci', o);
    m.prism('input_coupling', 'x', [AY, 8], 3.2, -48, -47.2, 'forged', { split: '', ends: ['west'] });
    m.box('torque_arm', [-47, 4, 4], [-42, 28, 12], 'st', o);
    m.box('torque_anchor', [-48, 0, 2], [-41, 4, 14], 'ci', o);
    m.box('nameplate', [-46.4, 50, -2.8], [-41.6, 54, -2.6], 'br', o);
  }
  function hood(m) {
    m.ringY('hood_skirt', C, 15, 2, 84, 94, 'st');
    m.ringY('hood_water', C, 16.2, 0.8, 86, 87.2, 'bright');
    m.box('hood_top', [-8, 93.4, -8], [24, 95.4, 24], 'st', o);
    m.ringY('lance_collar', C, 3.4, 1.2, 95.4, 96, 'ci');
    m.box('duct', [0, 88, 22], [16, 96, 32], 'st', o);
    m.box('duct_flange', [-1, 95.4, 16], [17, 96, 32], 'ci', o);
    for (const x of [-6, 20]) m.box(`hood_hanger_${x}`, [x, 64, -2], [x + 2, 94, 0], 'st', { split: 'y' });
    for (const x of [-6, 20]) m.box(`hood_hanger_s_${x}`, [x, 64, 16], [x + 2, 94, 18], 'st', { split: 'y' });
  }
  function access(m) {
    m.box('plat', [58, 39, -12], [64, 40, 28], 'grate', { hide: [] });
    m.box('plat_rail', [63.4, 55, -12], [64, 55.6, 28], 'st', o);
    m.box('plat_knee', [63.4, 47.6, -12], [64, 48, 28], 'st', o);
    for (const z of [-12, 8, 27.4]) m.box(`plat_post_${z}`, [63.4, 40, z], [64, 55, z + 0.6], 'st', o);
    m.ladderZ('ladder', 59, 63, 3, -1.5, 0, 40, 'st');
  }

  function model() {
    const m = mk();
    pedestal(m, 'w', -40, -28); pedestal(m, 'e', 46, 58); drive(m); hood(m); access(m);
    return m;
  }
  function tiltModel() { const m = mk(); tilt(m); return m; }

  // ------------------------------------------------------------------ partials
  function mouthGlow(m) { glowBox(m, 'mouth', [C[0] - 8, YM + 0.45, C[1] - 8], [C[0] + 8, YM + 0.55, C[1] + 8]); }
  const PART = {
    mouth_glow: { draw: mouthGlow, shift: [0, -64, 0], note: 'whole y = partial y + 64; tilts with the vessel; full-bright while blowing' },
  };
  function partial(name) {
    const src = mk(); PART[name].draw(src);
    const [dx, dy, dz] = PART[name].shift;
    return mk().merge(src, dx, dy, dz, '');
  }
  // Preview with the vessel tilted by deg about the trunnion axis (only multiples of 22.5 within ±45 render).
  function preview(deg) {
    const m = model(), t = tiltModel();
    for (const p of Object.values(PART)) p.draw(t);
    if (deg) for (const e of t.els) if (!e.rot) e.rot = { axis: 'x', angle: deg, origin: [8, AY, 8] };
    m.merge(t, 0, 0, 0, '');
    return m;
  }

  window.BOF = {
    model, tiltModel, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'bof_vessel_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'bof_vessel_real', 'steelworks/bof_vessel'),
    buildTilt: (root) => { const m = PCVV.cut(tiltModel()); PCART.realize(root, m, 'bof_vessel_real_tilt'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    saveTilt: (root) => PCVV.save(root, 'bof_vessel_real_tilt', 'steelworks/bof_vessel'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'bof_vessel_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'bof_vessel_real', name, 'steelworks/bof_vessel'),
    preview: (root, deg) => { const m = PCVV.cut(preview(deg || 0)); PCART.realize(root, m, 'bof_vessel_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
