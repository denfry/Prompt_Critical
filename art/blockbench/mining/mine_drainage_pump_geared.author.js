// Geared three-throw ram pump for the shaft sump at real scale (1 block = 1 m), as Pearn and Hathorn Davey built
// them for collieries: three vertical gunmetal rams worked from a crankshaft with cranks at 120 degrees, single
// reduction spur gearing from the drive, valve boxes on a cast iron bed, a suction manifold to the sump pipe and a
// delivery manifold into an air vessel that steadies the flow up the rising main. Dimensions and sources:
// docs/research/mine_drainage_pump-dimensions.md (disk only).
//
// Footprint x 0..2, y 0..1, z 0..1 (x 0..48, y 0..32, z 0..32), controller (0, 0, 0) at the west end, front north.
// Ports: suction pipe down through the floor of cell (0, 0, 0) at (8, 0, 8); rising main out of the top face of
// cell (0, 1, 0) at (8, 32, 8); Create input on the east face of cell (2, 0, 1), axis along x at (y 8, z 24).
// Moving parts (partials of mine_drainage_pump_geared): crank (crankshaft, three disc cranks, spur wheel; about x
// through the model's y 20, z 24), pinion (about x through y 8, z 24), ram (crosshead + ram, slides along y),
// conrod (swings about x at its small end).
//
// Saved as a whole model (wholemodels/mine_drainage_pump_geared.json): three blocks do not fit one Java model.
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then MDPG.build(repo) /* wait */ MDPG.save(repo); and
// MDPG.buildPart(repo, p) / MDPG.savePart(repo, p) for p in crank, pinion, ram, conrod.
(function () {
  const ID = 'mine_drainage_pump_geared';
  const CY = 20, CZ = 24, PY = 8, R_CRANK = 2.5, L_ROD = 8;      // crankshaft axis, pinion axis y, crank, rod
  const THROWS = [14, 24, 34], GEAR_X = [40, 43], RG = 8, RP = 4;
  const DX = 20;                                                 // model x = partial x + DX for the crank partial

  function model() {
    const m = PCVV.mk('material/cast_iron');
    // bed plate, cast iron, with a drip lip
    m.box('bed', [2, 0, 2], [46, 2, 30], 'ci');
    m.box('bed_lip', [1.5, 0, 1.5], [46.5, 0.6, 30.5], 'ci');
    // valve boxes under the rams, their covers, and the ram glands
    for (const x of THROWS) {
      m.box(`vbox_${x}`, [x - 4, 2, CZ - 5], [x + 4, 6, CZ + 5], 'ci', { split: '' });
      m.box(`vbox_cover_${x}`, [x - 3, 6, CZ - 4], [x + 3, 6.6, CZ + 4], 'ci', { split: '' });
      m.prismY(`gland_${x}`, [x, CZ], 3, 6.6, 8, 'br');
      // guides for the crosshead
      for (const s of [-1, 1]) m.box(`guide_${x}_${s}`, [x + s * 3.2 - 0.5, 8, CZ - 1], [x + s * 3.2 + 0.5, 16.5, CZ + 1], 'bright', { split: '' });
      m.box(`guide_top_${x}`, [x - 3.7, 16.5, CZ - 1.2], [x + 3.7, 17.2, CZ + 1.2], 'ci', { split: '' });
    }
    // standards carrying the crankshaft bearings, braced to the bed
    for (const x of [9, 19, 29, 38]) {
      m.box(`std_${x}`, [x - 1, 2, CZ - 3], [x + 1, CY - 2, CZ + 3], 'ci', { split: '' });
      m.box(`brg_${x}`, [x - 1.5, CY - 2.5, CZ - 3], [x + 1.5, CY + 2.5, CZ + 3], 'ci', { split: '' });
      m.box(`brg_cap_${x}`, [x - 1.2, CY + 2.5, CZ - 2], [x + 1.2, CY + 3.3, CZ + 2], 'ci', { split: '' });
      m.box(`oilcup_${x}`, [x - 0.5, CY + 3.3, CZ - 0.5], [x + 0.5, CY + 4.5, CZ + 0.5], 'br', { split: '' });
    }
    // pinion shaft bearings and the guard over the gearing
    for (const x of [38.5, 45]) m.box(`pbrg_${x}`, [x - 1, 2, CZ - 3], [x + 1, PY + 3, CZ + 3], 'ci', { split: '' });
    m.box('guard_top', [39, CY + RG + 1, CZ - 9], [44.5, CY + RG + 1.6, 32], 'pnt');
    m.box('guard_e', [44.5, PY - 4, CZ - 9], [45, CY + RG + 1.6, 32], 'pnt');
    m.box('guard_n', [39, PY - 4, CZ - 9.6], [45, CY + RG + 1.6, CZ - 9], 'pnt');
    m.prism('input_collar', 'x', [PY, CZ], 3.2, 47, 48, 'br', { split: '', ends: ['east', 'west'] });

    // suction manifold from the three valve boxes to the sump pipe at the controller; the pipe drops through the floor
    m.prism('suction_mani', 'x', [4, 14], 2.6, 6, THROWS[2] + 2, 'gw', { split: '' });
    for (const x of THROWS) m.box(`suction_branch_${x}`, [x - 1.8, 2.5, 14], [x + 1.8, 5.5, CZ - 5], 'gw', { split: '' });
    m.box('suction_elbow', [5, 0, 11], [11, 7, 17], 'gw', { split: '' });
    m.prismY('suction_down', [8, 8], 2.8, 0, 4, 'gw');
    m.box('suction_join', [5.2, 0.5, 5.2], [10.8, 4, 14], 'gw', { split: '' });
    m.prismY('suction_flange', [8, 8], 3.8, 0, 0.8, 'ci');
    // delivery manifold along the valve box tops into the air vessel over the controller
    m.prism('deliv_mani', 'x', [10, 18], 2.2, 8, THROWS[2] + 2, 'gw', { split: '' });
    for (const x of THROWS) m.box(`deliv_branch_${x}`, [x - 1.5, 6, 17], [x + 1.5, 11.5, CZ - 5], 'gw', { split: '' });
    // air vessel: riveted dome on a base, the rising main out of its top through a stop valve
    m.prismY('vessel', [8, 8], 6, 4, 24, 'gw');
    for (const y of [8, 16]) m.ringY(`vessel_seam_${y}`, [8, 8], 6.3, 0.6, y, y + 0.6, 'ci');
    for (let i = 0; i < 4; i++) { const r = 6 * Math.sqrt(1 - Math.pow((i + 1) / 5, 2)); m.prismY(`vessel_dome_${i}`, [8, 8], r, 24 + i, 25 + i, 'gw'); }
    m.box('vessel_feed', [8, 9, 10], [14, 12, 18], 'gw', { split: '' });
    m.prismY('rising', [8, 8], 2.4, 28, 32, 'gw');
    m.prismY('rising_valve', [8, 8], 3.4, 28, 29.6, 'ci');
    m.box('rising_wheel_spindle', [7.6, 29.6, 7.6], [8.4, 31, 8.4], 'bright', { split: '' });
    m.wheelY('rising_wheel', 8, 31, 8, 2.4, 'red');
    m.prismY('rising_flange', [8, 8], 3.4, 31.4, 32, 'ci');
    // pressure gauge and priming cock
    m.box('gauge_pipe', [1.4, 16, 7.6], [2, 20, 8.4], 'br', { split: '' });
    m.plate('gauge', [0.4, 20, 6], [2, 23.2, 10], 'br', 'west', 'material/gauge_dial');
    m.box('prime_cock', [13, 7, 4], [15, 9, 6], 'br', { split: '' });
    m.decal('plate', [24, 0.7, 1.3], [30, 1.9, 1.5], 'ci', 'north', 'pl.maker');
    return m;
  }

  // crankshaft with three disc cranks at 0, 120 and 240 degrees and the spur wheel; axis along x through the
  // partial's (y 8, z 8); model x = partial x + DX
  function crank() {
    const m = PCVV.mk('material/bright_steel');
    m.prism('shaft', 'x', [8, 8], 1.4, 8 - DX + 1, GEAR_X[1] - DX + 1, 'bright', { split: '', ends: ['east', 'west'] });
    THROWS.forEach((x, k) => {
      const a = k * 2 * Math.PI / 3, py = 8 + R_CRANK * Math.cos(a), pz = 8 + R_CRANK * Math.sin(a);
      const px = x - DX;
      for (const s of [-1, 1]) m.prism(`web_${x}_${s}`, 'x', [(8 + py) / 2, (8 + pz) / 2], R_CRANK / 2 + 2, px + s * 1.6 - 0.6, px + s * 1.6 + 0.6, 'ci', { split: '', ends: ['east', 'west'] });
      m.prism(`pin_${x}`, 'x', [py, pz], 1.1, px - 1.6, px + 1.6, 'bright', { split: '' });
    });
    const g0 = GEAR_X[0] - DX, g1 = GEAR_X[1] - DX;
    m.ring('gear_rim', 'x', [8, 8], RG - 0.6, 1.6, g0, g1, 'ci');
    m.disc('gear_web', 'x', [8, 8], RG - 2, g0 + 1, g1 - 1, 'ci', 'vver/flange', 'west');
    for (let k = 0; k < 8; k++) {
      m.box(`gear_tooth_${k}`, [g0, 8 - RG - 0.6, 7.5], [g1, 8 + RG + 0.6, 8.5], 'ci',
        { split: '', rot: k % 4 ? { axis: 'x', angle: [0, 22.5, 45, -22.5][k % 4], origin: [8, 8, 8] } : undefined });
    }
    return m;
  }
  // pinion on the input shaft, axis along x through the partial's (y 8, z 8), model x 38..48 = partial x 6..16
  function pinion() {
    const m = PCVV.mk('material/bright_steel');
    m.prism('shaft', 'x', [8, 8], 1.4, -2, 16, 'bright', { split: '', ends: ['east'] });
    m.prism('body', 'x', [8, 8], RP - 0.6, 2, 5, 'st', { split: '', ends: ['east', 'west'] });
    for (const a of [0, 45]) {
      m.box(`tooth_${a}_a`, [2, 8 - RP - 0.5, 7.5], [5, 8 + RP + 0.5, 8.5], 'st', { split: '', rot: a ? { axis: 'x', angle: a, origin: [8, 8, 8] } : undefined });
      m.box(`tooth_${a}_b`, [2, 7.5, 8 - RP - 0.5], [5, 8.5, 8 + RP + 0.5], 'st', { split: '', rot: a ? { axis: 'x', angle: a, origin: [8, 8, 8] } : undefined });
    }
    return m;
  }
  // crosshead (pin at the partial's (8, 8, 8)) and the gunmetal ram going down 7 units into the gland
  function ram() {
    const m = PCVV.mk('material/brass');
    m.box('crosshead', [5.2, 7, 6.6], [10.8, 9, 9.4], 'ci', { split: '' });
    m.prism('pin', 'x', [8, 8], 0.8, 5, 11, 'bright', { split: '', ends: ['east', 'west'] });
    m.prismY('ram', [8, 8], 2, 0.5, 7, 'br');
    return m;
  }
  // connecting rod, small end at the partial's (8, 8, 8), big end L_ROD up
  function conrod() {
    const m = PCVV.mk('material/bright_steel');
    m.box('shank', [7.2, 8.8, 7.4], [8.8, 8 + L_ROD - 1.2, 8.6], 'bright', { split: '' });
    m.box('small_end', [7, 7, 6.9], [9, 9.2, 9.1], 'ci', { split: '' });
    m.box('big_end', [6.8, 8 + L_ROD - 1.6, 6.4], [9.2, 8 + L_ROD + 1.6, 9.6], 'ci', { split: '' });
    return m;
  }

  const PARTS = { crank, pinion, ram, conrod };
  window.MDPG = {
    ID, PARTS, CY, CZ, PY, R_CRANK, L_ROD, THROWS, DX,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'mining'),   // 3 blocks long: a whole model with a manifest
    buildPart: (root, p) => { const m = PARTS[p](); PCART.realize(root, m, p); return m.els.length; },
    savePart: (root, p) => PCVV.savePartial(root, ID, p, 'mining'),
    // everything in place at crank angle 0 for the throw at x 14 (scale shots only, never saved)
    preview: (root) => {
      const m = model().merge(crank(), DX, CY - 8, CZ - 8, 'ck_').merge(pinion(), 32, PY - 8, CZ - 8, 'pn_');
      THROWS.forEach((x, k) => {
        const a = k * 2 * Math.PI / 3, py = CY + R_CRANK * Math.cos(a), dz = R_CRANK * Math.sin(a);
        const xy = py - Math.sqrt(L_ROD * L_ROD - dz * dz);
        m.merge(ram(), x - 8, xy - 8, CZ - 8, `rm${x}_`);
        m.merge(conrod(), x - 8, xy - 8, CZ - 8, `cr${x}_`);
      });
      PCART.realize(root, m, ID + '_preview');
      return m.els.length;
    },
  };
})();
