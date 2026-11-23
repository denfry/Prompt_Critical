// Liquid-ring vacuum pump for methane drainage at real scale (1 block = 1 m, 16 px = 1 m): a VVN-25 class unit
// (casing about 0.9 m across, 1.4 m long between the end covers) on a cast iron bed with its outboard bearings and
// the Create input coupling in place of the motor, the gas-water separator standing beside it, the suction line
// with the flame arrester and vacuum gauge, the discharge into the separator, gas out of its dome and the ring water
// returning to the pump through the regulating cock. Dimensions are approximate (VVN-12/25/50 catalogue drawings).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   DGPR.build(repo); /* wait */ DGPR.save(repo)
//   DGPR.buildPartial(repo, 'shaft'); DGPR.savePartial(repo, 'shaft')
//   DGPR.preview(repo)                                                   everything in place, for looking only
//
// Controller block = (0, 0, 0), front north. Footprint x 0..1, y 0..1, z 0..2. Pump axis along z through
// (x 24, y 8); the separator stands on the west column (x 0). Connections (px):
// - Create shaft in on the south face of (1, 0, 2) at (24, 8, 48);
// - methane suction in on the north face of (1, 1, 0) at (24, 24, 0), yellow gas pipe;
// - drained gas out on the west face of (0, 1, 1) at (0, 24, 22), yellow gas pipe;
// - make-up water in on the west face of (0, 0, 2) at (0, 4, 40), GOST green.
// Partial `shaft` (degasification_pump_real/partial/shaft): shaft with the impeller hub and both coupling
// halves, turns about z through (x 24, y 8); drawn at z -16, draw it at +16 z.
(function () {
  const AX = [24, 8], RC = 7, CZ = [12, 34];        // pump axis (x, y), casing radius, casing z span
  const SEP = [8, 22], RS = 6.4, SY = [3, 24];      // separator axis (x, z), radius, shell y span
  const MAT = Object.assign({}, PCVV.MAT, {
    cas: { tex: 'degasification_pump/casing', P: 16 }, gas: { tex: 'degasification_pump/gas', P: 16 },
    vac: { tex: 'degasification_pump/vacuum', P: 16 }, sight: { tex: 'degasification_pump/sight', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'degasification_pump/casing' });

  // ------------------------------------------------------------------ static
  function bed(m) {
    m.box('bed', [14, 0, 2], [31, 1.4, 46], 'ci', { split: 'z' });
    m.box('bed_rail_w', [14, 1.4, 2], [15.4, 2.4, 46], 'ci', { split: 'z' });
    m.box('bed_rail_e', [29.6, 1.4, 2], [31, 2.4, 46], 'ci', { split: 'z' });
    for (const z of [5, 23, 41]) for (const x of [14.6, 29.8]) m.box(`bolt_${x}_${z}`, [x - 0.4, 2.4, z - 0.4], [x + 0.4, 3, z + 0.4], 'bright', { split: '' });
    // casing feet and bearing pedestals
    for (const z of [CZ[0] + 2, CZ[1] - 5]) for (const s of [-1, 1]) {
      m.box(`foot_${z}_${s}`, [AX[0] + s * 6 - 1.6, 1.4, z], [AX[0] + s * 6 + 1.6, 4, z + 3], 'cas', { split: '' });
    }
    for (const [n, z0, z1] of [['n', 4, 9], ['s', 37, 42]]) {
      m.box(`pedestal_${n}`, [AX[0] - 3, 1.4, z0], [AX[0] + 3, AX[1] - 2, z1], 'cas', { split: '' });
      m.prism(`bearing_${n}`, 'z', AX, 3, z0, z1, 'cas', { split: '', ends: ['north', 'south'] });
      m.box(`oil_cup_${n}`, [AX[0] - 0.6, AX[1] + 3, (z0 + z1) / 2 - 0.6], [AX[0] + 0.6, AX[1] + 4.6, (z0 + z1) / 2 + 0.6], 'br', { split: '' });
    }
  }

  function pump(m) {
    m.prism('casing', 'z', AX, RC, CZ[0], CZ[1], 'cas', { split: '' });
    for (const z of [CZ[0] + 5, CZ[1] - 5]) m.prism(`band_${z}`, 'z', AX, RC + 0.5, z, z + 1, 'cas', { split: '' });
    for (const [n, z0, z1, face] of [['n', CZ[0] - 2.4, CZ[0], 'north'], ['s', CZ[1], CZ[1] + 2.4, 'south']]) {
      m.disc(`cover_${n}`, 'z', AX, RC + 0.8, z0, z1, 'cas', 'vver/flange', face);
      m.nutsZ(`cover_nuts_${n}`, [AX[0], AX[1]], RC + 0.1, 12, face === 'north' ? z0 - 0.4 : z1, face === 'north' ? z0 : z1 + 0.4, 0.8);
      m.prism(`gland_${n}`, 'z', AX, 2.4, face === 'north' ? 9 : CZ[1] + 2.4, face === 'north' ? z0 : 37, 'cas', { split: '', ends: [face] });
    }
    // suction and discharge branches on top of the covers (port plates are cast on the end covers)
    m.box('suction_branch', [AX[0] - 2.4, AX[1] + RC - 1, CZ[0] - 2.4], [AX[0] + 2.4, 17.6, CZ[0] + 2.6], 'cas', { split: '' });
    m.box('discharge_branch', [AX[0] - 2.4, AX[1] + RC - 1, CZ[1] - 2.6], [AX[0] + 2.4, 17.6, CZ[1] + 2.4], 'cas', { split: '' });
    m.box('ring_water_branch', [AX[0] - 6, 2.6, 21], [AX[0] - 4, 5, 25], 'cas', { split: '' });
    m.decal('plate', [AX[0] + RC + 0.2, 7, 19], [AX[0] + RC + 0.3, 10, 27], 'cas', 'east', 'pl.maker');
    // input coupling guard at the south end over the Create shaft
    m.box('guard_top', [AX[0] - 4.4, AX[1] + 3.6, 42], [AX[0] + 4.4, AX[1] + 4.2, 48], 'yel', { split: '' });
    for (const s of [-1, 1]) m.box(`guard_side_${s}`, [AX[0] + s * 4.1 - 0.3, 2.4, 42], [AX[0] + s * 4.1 + 0.3, AX[1] + 3.6, 48], 'yel', { split: '' });
  }

  function separator(m) {
    const c = SEP;
    for (const [dx, dz] of [[-4, -4], [4, -4], [-4, 4], [4, 4]]) m.box(`sep_leg_${dx}_${dz}`, [c[0] + dx - 0.8, 0, c[1] + dz - 0.8], [c[0] + dx + 0.8, SY[0] + 1, c[1] + dz + 0.8], 'ci', { split: '' });
    m.headDown('sep_bottom', c, RS, SY[0] + 2, 2, 2, 'cas');
    m.cylY('sep_shell', c, RS, SY[0] + 2, SY[1], 'cas');
    m.headUp('sep_dome', c, RS, SY[1], 3, 3, 'cas', 'vver/cap');
    m.ringY('sep_flange', c, RS + 0.6, 0.6, SY[1] - 0.8, SY[1], 'cas');
    // water level glass on the east side, facing the pump walkway
    m.box('sight_frame', [c[0] + RS - 0.2, 6, c[1] + 2], [c[0] + RS + 0.8, 18, c[1] + 4], 'st', { split: '' });
    m.plate('sight_glass', [c[0] + RS + 0.8, 7, c[1] + 2.4], [c[0] + RS + 0.9, 17, c[1] + 3.6], 'st', 'east', 'degasification_pump/sight');
    m.box('drain_cock', [c[0] - 1, 0.4, c[1] - RS - 1.6], [c[0] + 1, 2.4, c[1] - RS + 0.4], 'br', { split: '' });
  }

  function pipes(m) {
    // suction: from the north face (24, 24, 0) back over the flame arrester and down into the suction branch
    m.prism('suction_in', 'z', [AX[0], 24], 1.6, 0, 4, 'yel', { split: '' });
    m.prism('arrester', 'z', [AX[0], 24], 3.2, 4, 9, 'yel', { split: '', ends: ['north', 'south'] });
    m.disc('arrester_cover', 'z', [AX[0], 24], 3.6, 3.4, 4, 'ci', 'vver/flange', 'north');
    m.prism('suction_run', 'z', [AX[0], 24], 1.6, 9, CZ[0] + 1.6, 'yel', { split: '' });
    m.cylY('suction_down', [AX[0], CZ[0]], 1.6, 17.6, 25.6, 'yel');
    m.box('vac_gauge_tee', [AX[0] - 0.5, 25.6, 8.6], [AX[0] + 0.5, 28, 9.6], 'bright', { split: '' });
    m.prism('vac_gauge', 'z', [AX[0], 29.6], 1.8, 8.2, 9.2, 'st', { split: '', ends: ['north', 'south'] });
    m.plate('vac_gauge_face', [AX[0] - 1.6, 28, 8.1], [AX[0] + 1.6, 31.2, 8.2], 'st', 'north', 'degasification_pump/vacuum');
    // discharge: up from the pump's south branch and west into the separator side
    m.cylY('discharge_up', [AX[0], CZ[1]], 1.8, 17.6, 20.8, 'yel');
    m.box('discharge_bend', [AX[0] - 1.8, 18.2, CZ[1] - 1.8], [AX[0] + 1.8, 21.8, CZ[1] + 1.8], 'yel', { split: '' });
    m.prism('discharge_run', 'x', [20, CZ[1]], 1.8, SEP[0] + 2, AX[0] - 1.8, 'yel', { split: 'x' });
    m.box('discharge_drop', [SEP[0] + 1.2, 16, CZ[1] - 1.8], [SEP[0] + 4.8, 21.8, CZ[1] + 1.8], 'yel', { split: '' });
    // drained gas out of the dome to the west face at (0, 24, 22)
    m.cylY('gas_riser', SEP, 1.6, SY[1] + 2.6, 27.4, 'yel');
    m.box('gas_bend', [SEP[0] - 1.6, 22.4, SEP[1] - 1.6], [SEP[0] + 1.6, 27.4, SEP[1] + 1.6], 'yel', { split: '', hide: ['down'] });
    m.prism('gas_out', 'x', [24, SEP[1]], 1.6, 0, SEP[0] - 1.6, 'yel', { split: '' });
    m.disc('gas_flange', 'x', [24, SEP[1]], 2.6, 0.1, 0.9, 'ci', 'vver/flange', 'west');
    for (const x of [3.4, 4.2]) m.box(`gas_ring_${x}`, [x, 22.2, SEP[1] - 1.8], [x + 0.6, 25.8, SEP[1] + 1.8], 'red', { split: '' });
    // ring water back from the separator bottom to the pump through the regulating cock
    m.prism('ring_water', 'x', [3.8, 23], 1, SEP[0] + RS - 0.4, AX[0] - 6, 'gw', { split: 'x' });
    m.box('ring_cock', [16.6, 2.4, 21.6], [19, 5.2, 24.4], 'br', { split: '' });
    m.box('ring_cock_handle', [17.4, 5.2, 20], [18.2, 5.8, 26], 'red', { split: '' });
    // make-up water from the west face (0, 4, 40) into the separator foot
    m.prism('makeup', 'x', [4, 40], 1, 0, SEP[0] - 0.6, 'gw', { split: '' });
    m.prism('makeup_turn', 'z', [SEP[0], 4], 1, SEP[1] + RS - 0.6, 40.6, 'gw', { split: '' });
    m.disc('makeup_flange', 'x', [4, 40], 1.8, 0.1, 0.7, 'ci', 'vver/flange', 'west');
    m.box('makeup_valve', [3, 2.4, 38.6], [5.4, 5.6, 41.4], 'gm', { split: '' });
    m.wheelY('makeup_wheel', 4.2, 7, 40, 1.4, 'red');
  }

  // ------------------------------------------------------------------ partial
  function shaft(m) {
    m.prism('shaft', 'z', AX, 1.1, 3, 46, 'bright', { split: '' });
    m.prism('impeller_hub', 'z', AX, 2, CZ[0] + 0.5, CZ[1] - 0.5, 'dark', { split: '' });
    m.prism('coupling_pump', 'z', AX, 2.8, 43, 45, 'forged', { split: '', ends: ['north', 'south'] });
    m.prism('coupling_drive', 'z', AX, 2.8, 45.6, 48, 'forged', { split: '', ends: ['north', 'south'] });
    m.box('coupling_key', [AX[0] - 3.2, AX[1] - 0.5, 43], [AX[0] + 3.2, AX[1] + 0.5, 48], 'st', { split: '' });
    m.prism('thrust_collar', 'z', AX, 1.8, 2, 3, 'bright', { split: '', ends: ['north'] });
  }

  const PART = { shaft: { draw: shaft, shift: [0, 0, -16], note: 'turns about z through (24, 8); draw at +16 z' } };
  function model() { const m = mk(); bed(m); pump(m); separator(m); pipes(m); return m; }
  function partial(name) {
    const src = mk(); PART[name].draw(src);
    const [dx, dy, dz] = PART[name].shift;
    return mk().merge(src, dx, dy, dz, '');
  }
  function preview() { const m = model(); const s = mk(); shaft(s); m.merge(s, 0, 0, 0, ''); return m; }
  const ID = 'degasification_pump_real';
  window.DGPR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'mining'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'mining'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
