// Horizontal mill engine at real scale (1 block = 1 m, 16 units = 1 m): the reference engine of the sim, bore 0.4 m,
// stroke 0.6 m, 120 rpm. A lagged slide-valve cylinder with the valve chest and throttle on top, a cast-iron girder
// bed with bored guides, an overhung disc crank in the main bearing, a 2.8 m flywheel, an outboard bearing on its own
// pedestal and the shaft coupling, a belt-driven Porter governor, all on a dressed-stone foundation that lifts the
// shaft 1.5 m so the wheel clears the floor. Dimensions and sources: docs/research/mill_engine-dimensions.md (disk).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   MILL.build(repo); /* wait */ MILL.save(repo)          static engine: wholemodels/mill_engine.json
//   MILL.savePartials(repo)                               partials flywheel, crank, crosshead, rod, governor
//   MILL.preview(repo)                                    everything in its rest pose, for looking only
//
// Controller block = (0, 0, 0) on the floor at the cylinder end; cylinder back cover north at z = 0, the crankshaft
// along x. Footprint x 0..2, y 0..2, z 0..4. Connections (cell, face): steam in top of (0, 2, 0) at (8, 48, 8);
// exhaust west of (0, 0, 0) at (0, 8, 8); shaft out east of (2, 1, 3) at (48, 24, 56).
// Rest pose of the moving parts: crank pin at inner dead centre (towards the cylinder), crosshead pin at z 27.2.
// Each partial is drawn shifted by PART[name].shift so it fits one block model; PART[name].pivot is the pivot in
// partial px, PART[name].at the pivot in whole-model px.
(function () {
  const E = 8, AY = 24, CZ = 56, CR = 4.8, ROD = 24, XZ = CZ - CR - ROD;    // crosshead pin at rest: 27.2
  const GX = 19, GZ = 30, GPY = 34.8;                                        // governor spindle, its pulley height

  const MAT = Object.assign({}, PCVV.MAT);
  const mk = () => Object.assign(new PCART.Model(MAT, {
    eng: { tex: 'steam_engine/fittings', size: 64, rects: { nameplate: [0, 0, 14, 7], sight: [14, 0, 2, 6] } },
  }), { particle: 'material/paint_green' });

  // ------------------------------------------------------------------ static engine
  function foundation(m) {
    m.box('foundation', [2, 0, 0], [23, 12, 64], 'ashlar', { hide: ['down'] });
    m.box('pedestal', [33, 0, 48], [43, 17, 64], 'ashlar', { hide: ['down'] });
    for (const [n, x0, x1, z0, z1] of [['f', 2, 23, 0, 64], ['p', 33, 43, 48, 64]]) {
      m.box(`plinth_${n}`, [x0 - 0.5, 0, z0], [x1 + 0.5, 1.5, z1], 'ashlar', { hide: ['down'], split: '' });
    }
    // holding-down bolts along the bed sole
    for (const z of [6, 22, 40, 58]) for (const x of [3.4, 12.6]) m.box(`hd_bolt_${x}_${z}`, [x - 0.5, 14, z - 0.5], [x + 0.5, 15, z + 0.5], 'bright', { split: '' });
  }

  function cylinder(m) {
    m.box('cyl_foot', [3, 12, 1], [13, 19, 14], 'ci', { split: '' });
    m.disc('back_cover', 'z', [E, AY], 6.4, 0.1, 2, 'ci', 'steam_engine/cyl_cover', 'north');
    m.prism('barrel', 'z', [E, AY], 5.6, 2, 15, 'wood');
    for (const [n, z] of [['a', 2], ['b', 8.1], ['c', 14.2]]) m.prism(`band_${n}`, 'z', [E, AY], 5.8, z, z + 0.8, 'br', { split: '' });
    m.prism('front_cover', 'z', [E, AY], 6.4, 15, 17, 'ci', { split: '', ends: ['south'] });
    m.prism('gland', 'z', [E, AY], 2.2, 17, 20, 'br', { split: '', ends: ['south'] });
    m.nutsZ('gland_nut', [E, AY], 2.8, 2, 18.6, 19.6, 0.8, 'bright');
    // drain cocks under both ends, handles to the west
    for (const z of [3.6, 12.6]) {
      m.box(`drain_${z}`, [E - 0.6, 16.6, z], [E + 0.6, 18.6, z + 1.2], 'br', { split: '' });
      m.box(`drain_handle_${z}`, [E - 2.6, 17.2, z + 0.3], [E - 0.6, 17.8, z + 0.9], 'bright', { split: '' });
    }
    // slide-valve chest on top, its cover studs, the valve spindle out to the eccentric rod, a sight-feed lubricator
    m.box('chest', [3.5, 29, 3], [12.5, 35, 14], 'ci', { split: '' });
    m.box('chest_cover', [3, 35, 2.5], [13, 36, 14.5], 'ci', { split: '' });
    for (const x of [4, 12]) for (const z of [3.5, 8.5, 13.5]) m.box(`chest_stud_${x}_${z}`, [x - 0.4, 36, z - 0.4], [x + 0.4, 36.6, z + 0.4], 'bright', { split: '' });
    m.box('valve_gland', [E - 1.2, 30.8, 14], [E + 1.2, 33.2, 16], 'br', { split: '' });
    m.box('valve_spindle', [E - 0.4, 31.6, 16], [E + 0.4, 32.4, 21], 'bright', { split: '' });
    m.box('valve_guide', [E - 1, 30.6, 20], [E + 1, 33.4, 22], 'ci', { split: '' });
    m.box('lubricator', [10.6, 36, 11], [12.4, 39, 13], 'br', { split: '' });
    m.decal('lubricator_sight', [10.6, 36.4, 10.9], [11.4, 38.6, 11], 'br', 'north', 'eng.sight', { hide: ['south'] });
    // throttle valve and the steam pipe up to the steam connection, top face of cell (0, 2, 0)
    m.box('throttle_body', [5, 36, 5], [11, 40.4, 11], 'gm', { split: '' });
    m.box('throttle_spindle', [11, 37.8, 7.6], [15, 38.6, 8.4], 'bright', { split: '' });
    m.box('throttle_lever', [14.6, 37.4, 6], [15.4, 39, 12], 'st', { split: '' });
    m.cylY('steam_pipe', [E, 8], 2.2, 40.4, 45.5, 'felt');
    m.ringY('steam_band', [E, 8], 2.5, 0.6, 42.6, 44, 'gs');
    m.cylY('steam_flange', [E, 8], 3.6, 45.5, 47.9, 'forged', { cap: 'vver/flange' });
    // exhaust: belt round the cylinder bottom, the pipe down into the foundation and out of its west face
    m.prism('exhaust_out', 'x', [8, 8], 2.2, 1.4, 2, 'felt', { split: '' });
    m.prism('exhaust_flange', 'x', [8, 8], 3.6, 0, 1.4, 'forged', { split: '', ends: ['west'] });
    m.nutsX('exhaust_nut', [8, 8], 2.9, 6, 0, 0.3, 0.6, 'bright');
  }

  function bed(m) {
    m.box('bed_sole', [2.5, 12, 14], [15, 14, 64], 'ci', { split: 'z' });
    // girder sides under the guides, the guide brackets at both ends, the bored guide bars
    m.box('girder_w', [2.5, 14, 20], [4, 20, 50], 'ci', { split: 'z' });
    m.box('girder_e', [12, 14, 20], [13.5, 20, 50], 'ci', { split: 'z' });
    m.box('bracket_gland', [3, 14, 20], [13, 31, 22], 'ci', { split: '' });
    m.box('bracket_crank', [3, 14, 42], [13, 31, 44], 'ci', { split: '' });
    m.box('guide_top', [4.5, 28, 22], [11.5, 29.5, 42], 'bright', { split: 'z' });
    m.box('guide_bottom', [4.5, 18.5, 22], [11.5, 20, 42], 'bright', { split: 'z' });
    m.box('guide_cap', [4, 29.5, 22], [12, 30.5, 42], 'ci', { split: 'z' });
    m.decal('nameplate', [2, 15, 26], [2.5, 18.5, 33], 'br', 'west', 'eng.nameplate', { hide: ['east'] });
    // main bearing: pedestal on the bed, housing round the shaft, cap with its bolts and an oil cup
    m.box('main_pedestal', [15, 12, 50], [21, 21, 62], 'ci', { split: '' });
    m.box('main_housing', [15.4, 21, 51], [20.6, 27, 61], 'ci', { split: '' });
    m.box('main_cap', [15, 27, 51.5], [21, 28.6, 60.5], 'ci', { split: '' });
    for (const z of [52.5, 59.5]) m.box(`main_cap_bolt_${z}`, [17.4, 28.6, z - 0.5], [18.6, 29.6, z + 0.5], 'bright', { split: '' });
    m.box('main_oil_cup', [17.2, 28.6, 55.2], [18.8, 31, 56.8], 'br', { split: '' });
  }

  function outboard(m) {
    m.box('ob_pedestal', [34.5, 17, 50], [41.5, 21, 62], 'ci', { split: '' });
    m.box('ob_housing', [35, 21, 51], [41, 27, 61], 'ci', { split: '' });
    m.box('ob_cap', [34.6, 27, 51.5], [41.4, 28.6, 60.5], 'ci', { split: '' });
    m.box('ob_oil_cup', [37.2, 28.6, 55.2], [38.8, 31, 56.8], 'br', { split: '' });
    m.prism('ob_shaft', 'x', [AY, CZ], 1.8, 31, 45, 'bright', { split: '' });
    m.prism('coupling', 'x', [AY, CZ], 4, 45, 48, 'forged', { split: '', ends: ['east'] });
    m.nutsX('coupling_nut', [AY, CZ], 3, 6, 47.7, 48, 0.7, 'bright');
  }

  // Porter governor stand: column, bevel box, the horizontal pulley belted from the crankshaft pulley (plane
  // x 21.5..24, belt at 22.5 degrees), the throttle rod back to the throttle lever.
  function governorStand(m) {
    m.box('gov_column', [17, 12, 27.5], [21.5, 32, 32.5], 'ci', { split: '' });
    m.box('gov_bevel', [16.5, 32, 27], [21.5, 37.6, 33], 'ci', { split: '' });
    m.prism('gov_pulley_shaft', 'x', [GPY, GZ], 0.8, 21.5, 24.4, 'bright', { split: '' });
    m.prism('gov_pulley', 'x', [GPY, GZ], 3, 21.6, 24, 'ci', { split: '' });
    const L = Math.hypot(CZ - GZ, GPY - AY);
    for (const [n, s] of [['a', 1], ['b', -1]]) {
      const cy = (AY + GPY) / 2 + s * 3 * Math.cos(Math.PI / 8), cz = (CZ + GZ) / 2 + s * 3 * Math.sin(Math.PI / 8);
      m.box(`belt_${n}`, [21.7, cy - 0.2, cz - L / 2], [23.8, cy + 0.2, cz + L / 2], 'dark',
        { rot: { axis: 'x', angle: 22.5, origin: [22.75, cy, cz] } });
    }
    m.box('gov_rod', [14.6, 38, 8], [15.4, 38.8, 30], 'bright', { split: 'z' });
    m.box('gov_bell_crank', [14.4, 37.6, 29.4], [17, 39.2, 30.6], 'ci', { split: '' });
  }

  // ------------------------------------------------------------------ moving parts, whole-model px at rest
  function flywheel(m) {
    m.prism('fw_shaft', 'x', [AY, CZ], 1.8, 21, 31, 'bright', { split: '' });
    m.prism('fw_hub', 'x', [AY, CZ], 3.6, 24.5, 31.5, 'ci', { split: '', ends: ['east', 'west'] });
    m.ring('fw_rim', 'x', [AY, CZ], 22.4, 2.4, 25, 31, 'ci');
    m.ring('fw_rim_lip', 'x', [AY, CZ], 20.4, 0.8, 25.6, 30.4, 'ci');
    for (const a of [0, 45, -45]) {
      m.box(`fw_arm_${a}`, [26.6, AY - 1.3, CZ - 20.4], [29.4, AY + 1.3, CZ + 20.4], 'ci',
        { split: '', rot: a ? { axis: 'x', angle: a, origin: [28, AY, CZ] } : undefined });
    }
    m.box('fw_arm_v', [26.6, AY - 20.4, CZ - 1.3], [29.4, AY + 20.4, CZ + 1.3], 'ci', { split: '' });
    m.prism('fw_pulley', 'x', [AY, CZ], 3, 21.6, 24, 'ci', { split: '', ends: ['east', 'west'] });
  }
  function crank(m) {
    m.prism('crank_shaft', 'x', [AY, CZ], 1.8, 13, 21, 'bright', { split: '' });
    m.disc('crank_disc', 'x', [AY, CZ], 7, 13, 15, 'ci', 'steam_engine/cyl_cover', 'west');
    m.box('crank_balance', [13.2, AY - 1.6, CZ + 2.2], [14.8, AY + 1.6, CZ + 6.4], 'ci', { split: '' });
    m.prism('crank_pin', 'x', [AY, CZ - CR], 1.4, E + 1.6, 13, 'bright', { split: '' });
    m.prism('crank_pin_collar', 'x', [AY, CZ - CR], 1.9, E - 2.4, E - 1.6, 'bright', { split: '', ends: ['west'] });
  }
  function crosshead(m) {
    m.prism('piston_rod', 'z', [E, AY], 0.9, 4.4, XZ - 3, 'bright', { split: '' });
    m.box('xh_block', [5, 20.6, XZ - 3], [11, 27.4, XZ + 3], 'st', { split: '' });
    m.box('xh_shoe_top', [4.5, 27.4, XZ - 3.4], [11.5, 28, XZ + 3.4], 'br', { split: '' });
    m.box('xh_shoe_bottom', [4.5, 20, XZ - 3.4], [11.5, 20.6, XZ + 3.4], 'br', { split: '' });
    m.prism('xh_pin', 'x', [AY, XZ], 0.8, 4.4, 11.6, 'bright', { split: '', ends: ['east', 'west'] });
    m.box('xh_oil_cup', [7.4, 28, XZ - 0.6], [8.6, 29, XZ + 0.6], 'br', { split: '' });
  }
  function rod(m) {
    m.box('rod_small_end', [6.4, AY - 2, XZ - 2], [9.6, AY + 2, XZ + 2], 'bright', { split: '' });
    m.box('rod_shank', [6.9, AY - 1.1, XZ + 2], [9.1, AY + 1.1, XZ + 20.2], 'bright', { split: '' });
    m.box('rod_big_end', [6.2, AY - 2.8, XZ + 20.2], [9.8, AY + 2.8, XZ + 26.6], 'bright', { split: '' });
    m.box('rod_big_strap', [6, AY - 3, XZ + 25], [10, AY + 3, XZ + 26.2], 'st', { split: '' });
    for (const y of [AY - 2.4, AY + 1.6]) m.box(`rod_bolt_${y}`, [9.8, y, XZ + 22], [10.4, y + 0.8, XZ + 23.2], 'st', { split: '' });
  }
  function governor(m) {
    m.cylY('gov_spindle', [GX, GZ], 0.5, 37.6, 47.6, 'bright');
    m.box('gov_top', [GX - 1, 46.4, GZ - 1], [GX + 1, 47.6, GZ + 1], 'br', { split: '' });
    m.box('gov_sleeve', [GX - 1.1, 38.6, GZ - 1.1], [GX + 1.1, 40.6, GZ + 1.1], 'br', { split: '' });
    for (const s of [1, -1]) {
      // upper arms from the top joint, balls, lower links to the sleeve (balls in the x plane)
      m.box(`gov_arm_${s}`, [GX + s * 0.4 - 0.25, 41.6, GZ - 0.25], [GX + s * 3.6 + 0.25, 46.4, GZ + 0.25], 'bright', { split: '' });
      m.box(`gov_ball_${s}`, [GX + s * 3.6 - 1.4, 40.2, GZ - 1.4], [GX + s * 3.6 + 1.4, 43, GZ + 1.4], 'br', { split: '' });
      m.box(`gov_link_${s}`, [GX + s * 1.1 - 0.2, 40.6, GZ - 0.2], [GX + s * 2.6 + 0.2, 41.8, GZ + 0.2], 'bright', { split: '' });
    }
    m.box('gov_weight', [GX - 1.6, 38.6, GZ - 1.6], [GX + 1.6, 39.6, GZ + 1.6], 'ci', { split: '' });
  }

  const PART = {
    flywheel: { draw: flywheel, shift: [-16, -16, -48], at: [null, AY, CZ], motion: 'turn x' },
    crank: { draw: crank, shift: [0, -16, -48], at: [null, AY, CZ], motion: 'turn x' },
    crosshead: { draw: crosshead, shift: [0, -16, -20], at: [E, AY, XZ], motion: 'slide z' },
    rod: { draw: rod, shift: [0, -16, -24], at: [E, AY, XZ], motion: 'turn x at small end + slide z' },
    governor: { draw: governor, shift: [-11, -32, -22], at: [GX, null, GZ], motion: 'turn y' },
  };
  for (const p of Object.values(PART)) p.pivot = p.at.map((v, i) => (v === null ? null : v + p.shift[i]));

  function model() {
    const m = mk();
    foundation(m); cylinder(m); bed(m); outboard(m); governorStand(m);
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

  window.MILL = {
    model, partial, preview, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'mill_engine'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'mill_engine', 'boilers/mill_engine'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'mill_engine_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'mill_engine', name, 'boilers/mill_engine'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'mill_engine_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
