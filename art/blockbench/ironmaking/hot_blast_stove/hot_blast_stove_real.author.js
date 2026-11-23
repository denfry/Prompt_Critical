// Cowper regenerative hot blast stove at real scale (1 block = 1 m, 16 units = 1 m): a riveted plate shell ⌀4.5 m
// to 11.8 m with a dome to 14 m, holding the sim's 60 t of firebrick checker and the internal combustion chamber
// on the furnace (north) side. Outside: the base ring on a concrete pad, the water-cooled hot blast gate valve at
// 3.5 m, the burner wind box with gas and air pipes at 1.5 m, the cold blast and chimney mushroom valves under the
// checker, the valve platform at 3 m and a caged ladder on the west to the dome manhole. Dimensions and sources:
// docs/research/hot_blast_stove-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   HBS.build(repo); /* wait */ HBS.save(repo)
//   HBS.buildPartial(repo, name); HBS.savePartial(repo, name)     for hot_valve, cold_valve, chimney_valve, burner_glow
//   HBS.preview(repo)                                              everything, for looking only
//
// Controller block = (0, 0, 0): north bottom centre, front north (towards the furnace). Footprint x -2..2,
// y 0..13, z 0..4. Ports (px): hot blast out north of (0,3,0) at (8, 56, 0); gas in north of (-1,1,0) at
// (-8, 24, 0); burner air in north of (1,1,0) at (24, 24, 0); cold blast in south of (0,0,4) at (8, 12, 80);
// chimney valve (flue out) east of (2,0,2) at (48, 12, 40). Climb: the ladder column (-2, *, 2) and the
// platform at y 48.
(function () {
  const C = [8, 40], R = 36, Y0 = 4, YS = 189, YD = 224;     // shell centre [x, z], radius, base, shell top, dome top
  const HB = [8, 56], PR = 7;                                // hot blast port [x, y], pipe radius (⌀0.8 m with lining)

  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const FIT = { slot_glow: [30, 10, 1, 5] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT },
  }), { particle: 'fire_tube_boiler/plate' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  function glowBox(m, name, f, t) {
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', hide: [], face });
  }
  const o = { split: '' };

  function shell(m) {
    m.box('pad', [-32, 0, 0], [48, 2, 80], 'conc', { hide: ['down'] });
    m.cylY('base_ring', C, R + 1.2, 2, Y0 + 2, 'ci', o);
    m.cylY('shell', C, R, Y0 + 2, YS, 'st');
    // riveted courses every 1.5 m and the top angle ring
    for (let y = Y0 + 26; y < YS; y += 24) m.ringY(`seam_${y}`, C, R + 0.15, 0.6, y, y + 0.8, 'rivets');
    m.ringY('top_angle', C, R + 0.4, 1.2, YS - 1.2, YS, 'ci');
    m.headUp('dome', C, R, YS, YD - YS - 2, 6, 'st', 'material/steel');
    // dome manhole on the west and the bleeder pipe at the crown
    m.box('manhole', [C[0] - R + 4, YS + 4, C[1] - 5], [C[0] - R + 7, YS + 13, C[1] + 5], 'ci', o);
    m.cylY('bleeder', C, 2.4, YD - 2, YD, 'ci', o);
    // anchor lugs round the base
    for (const a of [45, 135, -45, -135]) m.radial(`lug_${a}`, C, R, R + 2.6, a, 3, 2, 14, 'ci');
  }

  function hotValve(m) {
    // nozzle from the shell (combustion chamber) to the valve, and the valve body with its bonnet above
    m.prism('hb_nozzle', 'z', HB, PR, 2.4, 8, 'st', o);
    m.box('hb_body', [HB[0] - 9, HB[1] - 9, 0.2], [HB[0] + 9, HB[1] + 9, 2.4], 'ci', o);
    m.ring('hb_flange', 'z', HB, PR + 1.6, 1.4, 0, 0.6, 'ci');
    m.box('hb_bonnet', [HB[0] - 7, HB[1] + 9, 0.6], [HB[0] + 7, HB[1] + 26, 2.4], 'ci', o);
    m.box('hb_yoke_w', [HB[0] - 6, HB[1] + 26, 1], [HB[0] - 5, HB[1] + 38, 2], 'st', o);
    m.box('hb_yoke_e', [HB[0] + 5, HB[1] + 26, 1], [HB[0] + 6, HB[1] + 38, 2], 'st', o);
    m.box('hb_yoke_top', [HB[0] - 6, HB[1] + 38, 1], [HB[0] + 6, HB[1] + 39.4, 2], 'st', o);
    // cooling water in and out
    for (const [n, x] of [['in', HB[0] - 11], ['out', HB[0] + 10]]) {
      m.box(`hb_water_${n}`, [x, HB[1] - 6, 0.8], [x + 1, HB[1] + 20, 1.8], 'bright', { split: 'y' });
    }
  }

  function burner(m) {
    // wind box across the north face, gas pipe from the west port and air pipe from the east port, throat into
    // the shell at the foot of the combustion chamber
    m.box('wind_box', [-14, 17, 0.4], [30, 31, 4.4], 'ci', o);
    m.prism('throat', 'z', [8, 24], 5.5, 4.4, 10, 'ci', o);
    m.prism('gas_pipe', 'z', [-8, 24], 5, 0, 0.4, 'ci', o);
    m.ring('gas_flange', 'z', [-8, 24], 6, 1.2, 0, 0.4, 'ci');
    m.prism('air_pipe', 'z', [24, 24], 5, 0, 0.4, 'ci', o);
    m.ring('air_flange', 'z', [24, 24], 6, 1.2, 0, 0.4, 'ci');
    // gas shut-off: a valve chest on the gas side with a handwheel
    m.box('gas_chest', [-14, 31, 0.6], [-2, 37, 4], 'ci', o);
    m.box('gas_spindle', [-8.4, 37, 2.8], [-7.6, 41, 3.6], 'bright', o);
    m.ringY('gas_wheel', [-8, 3.2], 2.6, 0.6, 41, 41.8, 'red');
    m.box('peep', [6, 31, 1.2], [10, 34, 4], 'ci', o);
    m.box('peep_glass', [7, 31.8, 1.1], [9, 33.2, 1.2], 'soot', o);
  }

  // Mushroom valve on a short branch: housing on the pipe, stem up, handwheel on top (the stem and wheel are the
  // partial). axis 'z' = branch to the south face, axis 'x' = branch to the east face.
  function mushroom(m, n, axis) {
    if (axis === 'z') {
      m.prism(`${n}_branch`, 'z', [8, 12], PR, 72, 80, 'ci', o);
      m.ring(`${n}_flange`, 'z', [8, 12], PR + 1.6, 1.4, 79.4, 80, 'ci');
      m.box(`${n}_housing`, [-1, 2, 71], [17, 22, 78], 'ci', o);
      m.box(`${n}_bonnet`, [4, 22, 72.5], [12, 25, 76.5], 'ci', o);
    } else {
      m.prism(`${n}_branch`, 'x', [12, 40], PR, 41, 48, 'ci', o);
      m.ring(`${n}_flange`, 'x', [12, 40], PR + 1.6, 1.4, 47.4, 48, 'ci');
      m.box(`${n}_housing`, [38, 2, 31], [45, 22, 49], 'ci', o);
      m.box(`${n}_bonnet`, [39.5, 22, 36], [43.5, 25, 44], 'ci', o);
    }
  }
  function stem(m, n, axis) {
    const c = axis === 'z' ? [8, 74.5] : [41.5, 40];
    m.box(`${n}_stem`, [c[0] - 0.5, 25, c[1] - 0.5], [c[0] + 0.5, 34, c[1] + 0.5], 'bright', o);
    m.ringY(`${n}_wheel`, c, 3, 0.6, 34, 34.8, 'red');
    m.box(`${n}_spoke_a`, [c[0] - 2.7, 34.1, c[1] - 0.3], [c[0] + 2.7, 34.7, c[1] + 0.3], 'red', o);
    m.box(`${n}_spoke_b`, [c[0] - 0.3, 34.1, c[1] - 2.7], [c[0] + 0.3, 34.7, c[1] + 2.7], 'red', o);
  }

  function platform(m) {
    // grating at 3 m across the north face and along the west side to the ladder
    m.box('plat_n', [-32, 47, 0.4], [48, 48, 12], 'grate', { hide: [] });
    m.box('plat_w', [-32, 47, 12], [-22, 48, 47], 'grate', { hide: [] });
    for (const [x0, x1] of [[-32, -2], [18, 48]]) {
      m.box(`rail_n_top_${x0}`, [x0, 63, 0.4], [x1, 63.6, 1], 'st', o);
      m.box(`rail_n_knee_${x0}`, [x0, 55.6, 0.4], [x1, 56, 1], 'st', o);
    }
    m.box('rail_w_top', [-32, 63, 1], [-31.4, 63.6, 32], 'st', o);
    m.box('rail_w_knee', [-32, 55.6, 1], [-31.4, 56, 32], 'st', o);
    for (const x of [-32, -17, -2, 18, 33, 47.4]) m.box(`post_n_${x}`, [x, 48, 0.4], [x + 0.6, 63, 1], 'st', o);
    for (const z of [16, 31.4]) m.box(`post_w_${z}`, [-32, 48, z], [-31.4, 63, z + 0.6], 'st', o);
    // brackets under the platform off the shell
    for (const [x, z] of [[-24, 6], [40, 6], [-26, 30]]) m.box(`bracket_${x}`, [x, 40, z], [x + 1, 47, z + 6], 'st', o);
    // caged ladder on the west from the ground past the platform to the dome manhole
    m.ladderZ('ladder', -30, -27, 46, -1.5, 0, YS + 8, 'st', 1.6);
    m.box('manhole_landing', [-32, YS + 2, 34], [-26, YS + 3, 47], 'grate', { hide: [] });
  }

  // ------------------------------------------------------------------ partials
  function hotGate(m) {
    // the gate's spindle and crosshead above the bonnet; opening lifts it 13 px
    m.box('hb_spindle', [HB[0] - 0.6, HB[1] + 26, 1.1], [HB[0] + 0.6, HB[1] + 38, 1.9], 'bright', o);
    m.box('hb_crosshead', [HB[0] - 4.6, HB[1] + 30, 0.9], [HB[0] + 4.6, HB[1] + 31.4, 2.1], 'ci', o);
  }
  function burnerGlow(m) { glowBox(m, 'peep', [7.1, 31.9, 1.04], [8.9, 33.1, 1.1]); }

  const PART = {
    hot_valve: { draw: hotGate, shift: [0, -64, 0], note: 'whole y = partial y + 64; open = +13 px y' },
    cold_valve: { draw: (m) => stem(m, 'cold', 'z'), shift: [0, -16, -64], note: 'whole y = partial y + 16, z = partial z + 64; open = +6 px y' },
    chimney_valve: { draw: (m) => stem(m, 'chimney', 'x'), shift: [-32, -16, -32], note: 'whole = partial + (32, 16, 32); open = +6 px y' },
    burner_glow: { draw: burnerGlow, shift: [0, -16, 0], note: 'whole y = partial y + 16; full-bright on gas' },
  };
  function model() {
    const m = mk();
    shell(m); hotValve(m); burner(m); mushroom(m, 'cold', 'z'); mushroom(m, 'chimney', 'x'); platform(m);
    return m;
  }
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

  window.HBS = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'hot_blast_stove_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'hot_blast_stove_real', 'ironmaking/hot_blast_stove'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'hot_blast_stove_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'hot_blast_stove_real', name, 'ironmaking/hot_blast_stove'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'hot_blast_stove_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
