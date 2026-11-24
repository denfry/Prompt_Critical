// Drilling mud pump at real scale (1 block = 1 m, 16 px = 1 m) of the 1950s-60s Soviet U8-6M class: a horizontal
// duplex double-acting piston pump, 400 mm stroke. The power end with the herringbone gear and crankshaft, the
// pinion shaft taking the drive at the north, the distance piece with the crossheads and piston rods, the cast
// fluid end with two cylinders and eight valve covers, the suction manifold below, the discharge manifold with the
// pulsation dampener, relief valve and gauge, all on a beam skid.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   MPM.build(repo); /* wait */ MPM.save(repo)
//   MPM.buildPartial(repo, p); MPM.savePartial(repo, p)    for input, rod_a, rod_b
//   MPM.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x -2..3, y 0..2, z -1..1 (6 x 3 x 3). Crankshaft along z
// through (x -14, y 22); piston rods along x at (y 18, z 2) and (y 18, z 14).
// Connections (px): Create shaft in on the north face of (-2, 0, -1) at (-26, 12, -16) into the pinion shaft (axis
// along z); mud suction in on the south face of (2, 0, 1) at (40, 4, 32); mud discharge out on the east face of
// (3, 1, 0) at (64, 24, 8), toward the standpipe.
// Partials (mud_pump_real/partial/*):
// - input: pinion shaft end and coupling; turns about z at (x -26, y 12); stored +16 x, draw at -16 x.
// - rod_a, rod_b: crosshead and piston rod of the north and south cylinders; slide along x, stroke 0..+6.4 px
//   (crank end as stored), the two 90 deg apart in crank angle.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });
  const RODS = [2, 14], RY = 18, IN = [-26, 12];

  function skid(m) {
    for (const z of [-15, 27]) {
      m.box(`skid_web_${z}`, [-32, 0, z + 1.6], [64, 6, z + 2.4], 'frame', { split: 'x' });
      m.box(`skid_fl_lo_${z}`, [-32, 0, z], [64, 1, z + 4], 'frame', { split: 'x' });
      m.box(`skid_fl_hi_${z}`, [-32, 5, z], [64, 6, z + 4], 'frame', { split: 'x' });
    }
    for (const x of [-31, -10, 10, 30, 52]) m.box(`cross_${x}`, [x, 0, -11], [x + 3, 5, 27], 'frame', { split: 'z' });
    m.box('base', [-31, 6, -12], [58, 7, 28], 'pnt', { split: 'xz' });
  }

  function powerEnd(m) {
    m.box('pe_body', [-30, 7, -10], [2, 26, 26], 'pnt', { split: '' });
    m.prism('pe_crown', 'z', [-14, 26], 16, -10, 26, 'pnt', { split: 'z', cullY: 26, ends: ['north', 'south'] });
    m.box('pe_crown_fill', [-30, 22, -10], [2, 26, 26], 'pnt', { split: '' });
    // crankshaft bearing caps and the pinion shaft bearings on both sides
    for (const [z, d] of [[-10.6, 'north'], [26, 'south']]) {
      m.disc(`crank_cap_${z}`, 'z', [-14, 22], 6, z, z + 0.6, 'ci', 'vver/flange', d);
      m.disc(`pinion_cap_${z}`, 'z', IN, 3.6, z, z + 0.6, 'ci', 'vver/flange', d);
    }
    m.prism('pinion_out', 'z', IN, 1.8, -14, -10.6, 'bright', { split: '' });
    m.box('pinion_brg', [-30, 7, -14], [-22, 10, -10], 'ci', { split: '' });
    // inspection covers, breather, oil sight glass, lube pump on the crosshead side
    m.box('cover_top', [-22, 41.4, -2], [-6, 42.2, 18], 'ci', { split: '' });
    m.box('breather', [-15, 42, 6], [-13, 45, 8], 'ci', { split: '' });
    m.box('sight', [-6, 12, -10.4], [-3, 16, -10], 'st', { split: '', face: { north: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    m.box('lube_pump', [-4, 26, 18], [2, 32, 24], 'pnt', { split: '' });
    m.box('lube_line', [-1, 28, 4], [0, 29, 18], 'oil', { split: '' });
    m.decal('plate', [-24, 16, -10.3], [-18, 19.6, -10], 'pnt', 'north', 'pl.maker');
  }

  function distancePiece(m) {
    // open frame between the power end and the fluid end: bottom tray, top rails, side posts, guards
    m.box('dp_tray', [2, 7, -8], [14, 10, 24], 'pnt', { split: '' });
    m.box('dp_rail_n', [2, 26, -8], [14, 28, -5], 'pnt', { split: '' });
    m.box('dp_rail_s', [2, 26, 21], [14, 28, 24], 'pnt', { split: '' });
    m.box('dp_rail_m', [2, 26, 6.5], [14, 28, 9.5], 'pnt', { split: '' });
    for (const z of RODS) {
      // crosshead guide bores (lower and upper slides)
      m.box(`guide_lo_${z}`, [2, 10, z - 4], [9, 13, z + 4], 'ci', { split: '' });
      m.box(`guide_hi_${z}`, [2, 23, z - 4], [9, 26, z + 4], 'ci', { split: '' });
      // stuffing box at the fluid end
      m.prism(`gland_${z}`, 'x', [RY, z], 2.8, 13, 16, 'br', { split: '', ends: ['west'] });
    }
    for (const z of [-8, 24]) m.box(`guard_${z}`, [2, 10, z], [14, 26, z + 0.6], 'yel', { split: '', hide: [] });
  }

  function fluidEnd(m) {
    m.box('fe_body', [16, 7, -10], [52, 32, 26], 'pnt', { split: '' });
    for (const z of RODS) {
      m.disc(`cyl_head_${z}`, 'x', [RY, z], 5.6, 52, 54, 'ci', 'vver/flange', 'east');
      m.prism(`cyl_bolt_ring_${z}`, 'x', [RY, z], 4.4, 54, 55, 'st', { split: '', ends: ['east'] });
    }
    // eight valve covers on the top deck: suction (lower row by the manifold) and discharge for each cylinder end
    for (const x of [22, 44]) for (const z of [-4, 20]) {
      m.cylY(`valve_cover_${x}_${z}`, [x, z], 4, 32, 34, 'ci', { cap: 'vver/flange' });
      m.box(`valve_nut_${x}_${z}`, [x - 1, 34, z - 1], [x + 1, 35, z + 1], 'st', { split: '' });
    }
    for (const x of [33]) for (const z of [-4, 20]) m.cylY(`disch_cover_${z}`, [x, z], 3.4, 32, 34, 'ci', { cap: 'vver/flange' });
    // suction manifold below the fluid end, out to the south face
    m.prism('suction', 'z', [40, 4], 3, -8, 32, 'pnt', { split: 'z', ends: ['south'] });
    m.disc('suction_flange', 'z', [40, 4], 3.8, 30.9, 31.9, 'ci', 'vver/flange', 'south');
    for (const z of RODS) m.box(`suction_riser_${z}`, [38, 4, z - 2], [42, 7, z + 2], 'pnt', { split: '' });
    // discharge manifold across the top, the dampener and the line to the east face
    m.prism('disch_mf', 'z', [33, 37], 2, -6, 22, 'gs', { split: 'z', ends: ['north', 'south'] });
    for (const z of [-4, 20]) m.box(`disch_drop_${z}`, [32, 34, z - 1], [34, 37, z + 1], 'gs', { split: '' });
    m.box('disch_run', [33, 36, 7], [58, 38, 9], 'gs', { split: 'x' });
    m.box('disch_drop_e', [57, 24, 7], [59, 38, 9], 'gs', { split: 'y' });
    m.box('disch_out', [59, 23, 6], [63.4, 25, 10], 'gs', { split: '' });
    m.disc('disch_flange', 'x', [24, 8], 3, 63.4, 63.9, 'ci', 'vver/flange', 'east');
    // pulsation dampener (pneumatic compensator) on its own stand by the discharge
    m.cylY('damp_stand', [58, -4], 2, 7, 26, 'pnt');
    m.cylY('damp_body', [58, -4], 5, 26, 36, 'pnt');
    m.headUp('damp_dome', [58, -4], 5, 36, 5, 4, 'pnt');
    m.box('damp_link', [56, 30, -4], [58, 32, 8], 'gs', { split: '' });
    m.cylY('damp_valve', [58, -4], 1, 41, 44, 'br');
    // relief (shear-pin) valve and the pressure gauge on the discharge
    m.box('relief', [46, 38, 5], [50, 42, 11], 'red', { split: '' });
    m.box('relief_vent', [47, 42, 7], [49, 46, 9], 'red', { split: '' });
    m.box('gauge_stem', [52, 38, 7.4], [53, 42, 8.6], 'st', { split: '' });
    m.prism('gauge', 'z', [52.5, 44], 2.2, 6.6, 7.4, 'st', { split: '', ends: ['north'] });
    m.plate('gauge_face', [50.6, 42.1, 6.5], [54.4, 45.9, 6.6], 'st', 'north', 'material/gauge_dial');
  }

  // ------------------------------------------------------------------ partials
  function input(m) {
    m.prism('input_shaft', 'z', IN, 1.6, -15.4, -14, 'bright', { split: '' });
    m.disc('input_coupling', 'z', IN, 3.6, -15.9, -15.4, 'ci', 'vver/flange', 'north');
  }
  function rod(z) {
    return (m) => {
      m.box(`crosshead_${z}`, [3, 13, z - 3.4], [8.6, 23, z + 3.4], 'st', { split: '' });
      m.box(`crosshead_pin_${z}`, [5, 17, z - 3.8], [6.6, 19, z + 3.8], 'bright', { split: '' });
      m.prism(`rod_${z}`, 'x', [RY, z], 1.3, 8.6, 13, 'bright', { split: '' });
      m.box(`rod_clamp_${z}`, [9.4, RY - 1.8, z - 1.8], [10.8, RY + 1.8, z + 1.8], 'ci', { split: '' });
    };
  }

  const PART = {
    input: { draw: input, shift: [16, 0, 0], note: 'turns about z at (x -26, y 12); stored +16 x (Java range), draw at -16 x' },
    rod_a: { draw: rod(RODS[0]), shift: [0, 0, 0], note: 'slides along x, stroke 0..+6.4' },
    rod_b: { draw: rod(RODS[1]), shift: [0, 0, 0], note: 'slides along x, stroke 0..+6.4, 90 deg after rod_a' },
  };
  function model() { const m = mk(); skid(m); powerEnd(m); distancePiece(m); fluidEnd(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'mud_pump_real';
  window.MPM = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'drilling'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'drilling'),
    preview: (root) => { const m = model(); for (const [k, p] of Object.entries(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, k + '_'); } PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
