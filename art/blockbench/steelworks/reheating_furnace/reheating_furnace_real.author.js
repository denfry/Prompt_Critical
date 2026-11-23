// Continuous ("methodical") billet reheating furnace at real scale (1 block = 1 m, 16 units = 1 m) for the sim's
// 12 × 100 kg charge: a brick box on a brick base with a 1.4 × 2.4 m hearth at 0.6 m, a sprung firebrick arch,
// buckstays and tie rods, the counterweighted discharge door with its gallows in front, the charging door and the
// rack pusher at the back, the gas burner on the east side at the discharge end, peep holes and the flue damper.
// Dimensions and sources: docs/research/reheating_furnace-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   RHF.build(repo); /* wait */ RHF.save(repo)
//   RHF.buildPartial(repo, name); RHF.savePartial(repo, name)     for door, charge_door, glow
//   RHF.preview(repo)                                              everything, for looking only
//
// Controller block = (0, 0, 0): bottom centre, front north. Footprint x -1..1, y 0..1, z -1..1 (FP_FURNACE3).
// Ports (px): discharge window north of (0, 0, -1), sill at y 10, x -2..18; charging and item input south of
// (0, 0, 1). Burner (east) and flue (down under (0, 0, 1)) are cosmetic.
(function () {
  const X0 = -12, X1 = 28, Z0 = -12, Z1 = 28, YH = 10, YW = 26;   // body box and hearth level
  const MAT = Object.assign({}, PCVV.MAT);
  const FIT = { slot_glow: [30, 10, 1, 5] };
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
  const o = { split: '' };

  function body(m) {
    m.box('base', [X0 - 1, 0, Z0 - 1], [X1 + 1, YH - 2, Z1 + 1], 'brick', { hide: ['down'] });
    m.box('base_course', [X0 - 1.4, YH - 2, Z0 - 1.4], [X1 + 1.4, YH - 1.2, Z1 + 1.4], 'ashlar', o);
    // walls with the discharge and charging openings (front and back are framed separately)
    m.box('wall_w', [X0, YH - 1.2, Z0], [X0 + 6, YW, Z1], 'brick');
    m.box('wall_e', [X1 - 6, YH - 1.2, Z0], [X1, YW, Z1], 'brick');
    m.box('wall_n_low', [X0 + 6, YH - 1.2, Z0], [X1 - 6, YH, Z0 + 6], 'brick', o);
    m.box('wall_n_top', [X0 + 6, 18, Z0], [X1 - 6, YW, Z0 + 6], 'brick');
    m.box('wall_s_low', [X0 + 6, YH - 1.2, Z1 - 6], [X1 - 6, YH, Z1], 'brick', o);
    m.box('wall_s_top', [X0 + 6, 16, Z1 - 6], [X1 - 6, YW, Z1], 'brick');
    m.box('hearth', [X0 + 6, YH - 1.2, Z0 + 6], [X1 - 6, YH, Z1 - 6], 'ashlar', o);
    m.box('mouth_n', [X0 + 6, YH, Z0], [X1 - 6, 18, Z0 + 2], 'soot', { split: '', hide: ['north'] });
    m.box('mouth_s', [X0 + 6, YH, Z1 - 2], [X1 - 6, 16, Z1], 'soot', { split: '', hide: ['south'] });
    m.box('interior', [X0 + 6, YH, Z0 + 2], [X1 - 6, YW - 1, Z1 - 2], 'dark', o);
    // sprung arch over the body, three courses
    m.box('arch_0', [X0, YW, Z0], [X1, YW + 2, Z1], 'ashlar', { hide: ['down'] });
    m.box('arch_1', [X0 + 4, YW + 2, Z0], [X1 - 4, YW + 3.4, Z1], 'ashlar', { hide: ['down'] });
    m.box('arch_2', [X0 + 10, YW + 3.4, Z0], [X1 - 10, YW + 4.2, Z1], 'ashlar', { hide: ['down'] });
    m.box('skewback_w', [X0 - 0.6, YW - 1, Z0], [X0 + 1, YW + 1.6, Z1], 'st', o);
    m.box('skewback_e', [X1 - 1, YW - 1, Z0], [X1 + 0.6, YW + 1.6, Z1], 'st', o);
  }
  function stays(m) {
    for (const z of [-10, 1, 13, 24]) {
      m.box(`buck_w_${z}`, [X0 - 2.4, 0, z], [X0, YW + 3.6, z + 1.6], 'st', { split: 'y' });
      m.box(`buck_e_${z}`, [X1, 0, z], [X1 + 2.4, YW + 3.6, z + 1.6], 'st', { split: 'y' });
      m.box(`tie_${z}`, [X0 - 3, YW + 3.6, z + 0.5], [X1 + 3, YW + 4.2, z + 1.1], 'bright', o);
      for (const x of [X0 - 3.6, X1 + 3]) m.box(`tie_nut_${z}_${x}`, [x, YW + 3.4, z + 0.3], [x + 0.6, YW + 4.4, z + 1.3], 'ci', o);
    }
  }
  function front(m) {
    // discharge door frame and the apron onto the roller table
    m.box('dframe_w', [-4, YH - 0.6, Z0 - 1], [-2, 19.4, Z0], 'ci', o);
    m.box('dframe_e', [18, YH - 0.6, Z0 - 1], [20, 19.4, Z0], 'ci', o);
    m.box('dframe_top', [-4, 18, Z0 - 1], [20, 19.4, Z0], 'ci', o);
    m.box('apron', [-2, YH - 0.6, -16], [18, YH, Z0], 'ci', o);
    // gallows with pulleys over the door, and the counterweight on the east post
    for (const x of [-7, 21.6]) m.box(`gallows_${x}`, [x, YH - 1.2, -15.6], [x + 1.4, 31, -14.2], 'st', { split: 'y' });
    m.box('gallows_beam', [-7, 30, -15.6], [23, 31.4, -14.2], 'st', o);
    for (const x of [1, 15]) m.ring(`pulley_${x}`, 'z', [x, 28.6], 1.4, 0.6, -15.8, -15, 'ci');
    m.box('counterweight', [23.2, 20, -15.8], [27.2, 25, -13.6], 'ci', o);
    m.box('cw_chain', [24.9, 25, -14.9], [25.5, 30, -14.3], 'dark', o);
  }
  function back(m) {
    m.box('cframe_w', [-4, YH - 0.6, Z1], [-2, 17.4, Z1 + 1], 'ci', o);
    m.box('cframe_e', [18, YH - 0.6, Z1], [20, 17.4, Z1 + 1], 'ci', o);
    m.box('cframe_top', [-4, 16, Z1], [20, 17.4, Z1 + 1], 'ci', o);
    // rack pusher under the charging door: gear case, the rack and the push head
    m.box('pusher_case', [2, 1, Z1 + 1], [14, 9, 32], 'pnt', o);
    m.box('pusher_rack', [6.6, 9, Z1 + 0.5], [9.4, YH + 0.6, 32], 'st', o);
    m.box('pusher_head', [-1, YH + 0.1, Z1 + 0.2], [17, YH + 1.4, Z1 + 1], 'ci', o);
    m.prism('pusher_input', 'z', [8, 5], 2, 32 - 0.6, 32, 'forged', { split: '', ends: ['south'] });
    // flue damper on the back corner (the flue goes down under the charging end)
    m.box('damper_box', [20, 1, Z1], [27, 7, 31], 'ci', o);
    m.box('damper_lever', [26.4, 7, 29.4], [27, 13, 30], 'st', o);
    m.box('damper_weight', [26, 13, 29], [27.4, 14.4, 30.4], 'ci', o);
  }
  function burner(m) {
    m.prism('burner', 'x', [20, -4], 2.4, X1 + 2.4, 31.4, 'ci', { split: '', ends: ['east'] });
    m.ring('burner_flange', 'x', [20, -4], 3, 1, X1 + 2.4, X1 + 3.2, 'ci');
    m.box('gas_pipe', [30.6, 0, -4.6], [31.8, 17.6, -3.4], 'yel', { split: 'y' });
    m.box('air_pipe', [30.4, 22.4, -5], [32, 24, -3]
      , 'st', o);
    m.box('gas_valve', [29.8, 10, -5.6], [32, 10.6, -2.4], 'red', o);
    for (const z of [6, 16]) {
      m.box(`peep_${z}`, [X1 + 2.4, 14, z], [X1 + 3.4, 16.4, z + 2.4], 'ci', o);
    }
    m.box('nameplate', [X1 + 2.4, 4, 4], [X1 + 2.6, 7, 10], 'br', o);
  }

  // ------------------------------------------------------------------ partials (whole px, no shift)
  function door(m) {
    m.box('door', [-3, YH - 0.4, -14], [19, 19, Z0 - 1], 'ci', o);
    m.box('door_lining', [-2, YH, Z0 - 1.01], [18, 18.4, Z0 - 1], 'ashlar', o);
    for (const x of [1, 15]) m.box(`door_lug_${x}`, [x - 0.4, 19, -13.6], [x + 0.4, 20.4, -13.2], 'st', o);
    for (const x of [1, 15]) m.box(`door_chain_${x}`, [x - 0.3, 20.4, -15.6], [x + 0.3, 28, -15], 'dark', { split: 'y' });
  }
  function chargeDoor(m) {
    m.box('cdoor', [-3, YH + 1.4, Z1 + 1], [19, 17, Z1 + 2], 'ci', o);
    m.box('cdoor_handle', [7, 17, Z1 + 1.2], [9, 18.4, Z1 + 1.8], 'st', o);
  }
  function glow(m) {
    glowBox(m, 'discharge', [-1.6, YH + 0.1, Z0 + 0.4], [17.6, 17.8, Z0 + 0.45]);
    glowBox(m, 'charge', [-1.6, YH + 1.5, Z1 - 0.45], [17.6, 15.8, Z1 - 0.4]);
    for (const z of [6, 16]) glowBox(m, `peep_${z}`, [X1 + 3.4, 14.6, z + 0.6], [X1 + 3.46, 15.8, z + 1.8]);
    glowBox(m, 'burner', [31.4, 18.4, -5.6], [31.46, 21.6, -2.4]);
  }

  const PART = {
    door: { draw: door, shift: [0, 0, 0], note: 'open = +12 px y' },
    charge_door: { draw: chargeDoor, shift: [0, 0, 0], note: 'open = +8 px y' },
    glow: { draw: glow, shift: [0, 0, 0], note: 'full-bright while LIT' },
  };
  function model() { const m = mk(); body(m); stays(m); front(m); back(m); burner(m); return m; }
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

  window.RHF = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'reheating_furnace_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'reheating_furnace_real', 'steelworks/reheating_furnace'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'reheating_furnace_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'reheating_furnace_real', name, 'steelworks/reheating_furnace'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'reheating_furnace_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
