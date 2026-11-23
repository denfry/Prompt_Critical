// Coke-oven gas exhauster at real scale (1 block = 1 m, 16 units = 1 m): a single-stage centrifugal exhauster of
// the 1910s by-product plants (Bryan Donkin, Sturtevant; 15–30 000 m³/h at 300–500 mm water suction): the cast
// volute casing ⌀1.6 m on a concrete block, the suction main coming in axially from the west, the tangential
// discharge rising on the north side, the shaft out east through two ring-oiled pedestal bearings and the coupling
// to the speed-raising gearbox that takes the Create shaft, a bypass with its valve, the U-tube suction gauges on a
// board and a drain pot. GOST colours: gas lines yellow.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   GXR.build(repo); GXR.save(repo); GXR.buildPartial(repo, 'rotor'); GXR.savePartial(repo, 'rotor')
//
// Controller block = (0, 0, 0), the casing cell, front north. Footprint x 0..2, y 0..2, z -1..1 (model x 0..48,
// y 0..48, z -16..32). Casing axis along x at (y 24, z 8). Ports: gas in, west face of (0, 1, 0) at (0, 24, 8);
// gas out, up face of (0, 2, -1) at (8, 48, -8); Create shaft in, east face of (2, 1, 0) at (48, 24, 8), axis x.
// Partial rotor (shaft, couplings, gearbox input): turns about x, axis (y 24, z 8); saved shifted (-16, 0, 0):
// draw it 16 px east.
(function () {
  const ID = 'gas_exhauster_real';
  const A = [24, 8];                                      // shaft axis [y, z]
  const MAT = Object.assign({}, PCVV.MAT, { idp: { tex: 'boilers/id_paint', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'boilers/id_paint' });

  function base(m) {
    m.box('foundation', [0, 0, -14], [46, 6, 30], 'conc', { hide: ['down'] });
    for (const [x, z] of [[3, -12], [13, -12], [3, 27.6], [13, 27.6], [28, -2], [44, -2], [28, 18], [44, 18]]) {
      m.box(`hd_bolt_${x}_${z}`, [x, 6, z], [x + 0.8, 6.8, z + 0.8], 'bright', { split: '' });
    }
  }

  function casing(m) {
    // volute: the round casing, the scroll growing to the discharge, the side plates and the feet
    m.prism('casing', 'x', A, 13, 2, 14, 'idp', { split: '', ends: ['east', 'west'] });
    m.prism('scroll', 'x', [A[0] + 1, A[1] - 2], 14, 3, 13, 'idp', { split: '' });
    for (const x of [1.4, 13.6]) m.ring(`casing_flange_${x}`, 'x', A, 13.4, 1, x, x + 1, 'ci');
    m.box('casing_foot', [2, 6, -4], [14, 12, 20], 'idp', { split: '' });
    // tangential discharge up the north side to the gas main above
    m.box('discharge', [2.6, 24, -12], [13.4, 46.6, -4], 'idp', { split: 'y' });
    m.box('discharge_flange', [2, 46.6, -12.6], [14, 48, -3.4], 'ci', { split: '' });
    // suction main in axially from the west
    m.prism('suction', 'x', A, 6, 0, 2, 'yel', { split: '', ends: ['west'] });
    m.ring('suction_flange', 'x', A, 6.6, 1.2, 0, 1, 'ci');
    // bypass from discharge back to suction, with its valve
    m.box('bypass_up', [0, 34, -1], [2, 36, 1], 'yel', { split: '' });
    m.box('bypass_run', [0, 34, -4], [2, 36, 1], 'yel', { split: '' });
    m.box('bypass_drop', [0, 28, -1], [2, 34, 1], 'yel', { split: '' });
    m.prism('bypass_valve', 'z', [1.8, 35], 1.6, -3.6, -2, 'ci', { split: '' });
    m.box('bypass_wheel_stem', [0.6, 36.6, -3.2], [1.4, 39, -2.4], 'bright', { split: '' });
    m.box('bypass_wheel', [0, 39, -4.6], [3, 39.6, -1], 'red', { split: '' });
    // drain pot under the casing and the nameplate
    m.cylY('drain_pot', [8, 24], 2, 6, 10, 'ci', { cap: 'vver/flange' });
    m.box('drain_pipe', [7.4, 10, 18], [8.6, 12, 24], 'ci', { split: '' });
    m.decal('maker_plate', [5, 13, 20.8], [11, 16, 21], 'idp', 'south', 'pl.maker');
  }

  function drive(m) {
    // two pedestal bearings with oil rings, the gearbox taking the Create shaft
    for (const x of [16, 24]) {
      m.box(`pedestal_${x}`, [x, 6, 3], [x + 4, A[0] - 2, 13], 'ci', { split: '' });
      m.box(`bearing_cap_${x}`, [x + 0.4, A[0] + 2, 4], [x + 3.6, A[0] + 4, 12], 'ci', { split: '' });
      m.box(`oil_glass_${x}`, [x + 1.4, A[0] - 4, 2.6], [x + 2.6, A[0] - 2.6, 3], 'br', { split: '' });
    }
    m.box('gearbox', [32, 6, -2], [46, 32, 18], 'gw');
    m.box('gearbox_cover', [33, 32, -1], [45, 33.4, 17], 'gw', { split: '' });
    m.box('breather', [38.6, 33.4, 7.4], [39.4, 35, 8.2], 'br', { split: '' });
    m.prism('input_boss', 'x', A, 2.6, 46, 48, 'ci', { split: '', ends: ['east'] });
    m.box('tachometer', [36, 26, -2.6], [40, 30, -2], 'br', { split: '' });
  }

  function gauges(m) {
    // board with two U-tube water gauges (suction and discharge) on the west of the casing
    m.box('gauge_post', [0.6, 6, 22], [1.6, 34, 23], 'wood', { split: 'y' });
    m.box('gauge_board', [0.6, 18, 19], [1.6, 34, 30], 'wood', { split: '' });
    for (const z of [21, 26]) {
      m.box(`u_tube_l_${z}`, [0.2, 20, z], [0.6, 32, z + 0.6], 'br', { split: '' });
      m.box(`u_tube_r_${z}`, [0.2, 20, z + 2], [0.6, 32, z + 2.6], 'br', { split: '' });
      m.box(`u_water_${z}`, [0.1, 20, z], [0.2, z === 21 ? 26 : 23, z + 2.6], 'gw', { split: '' });
    }
    m.box('gauge_line', [1.6, 26, 18], [2.6, 27, 22], 'yel', { split: '' });
  }

  function rotor(m) {
    m.prism('shaft', 'x', A, 1.4, 14, 46, 'bright', { split: '' });
    m.prism('coupling_a', 'x', A, 3.6, 28.4, 30, 'ci', { split: '', ends: ['east', 'west'] });
    m.prism('coupling_b', 'x', A, 3.6, 30.2, 31.8, 'ci', { split: '', ends: ['east', 'west'] });
    for (const [dy, dz] of [[2.4, 0], [-2.4, 0], [0, 2.4], [0, -2.4]]) m.box(`cpl_bolt_${dy}_${dz}`, [28, A[0] + dy - 0.4, A[1] + dz - 0.4], [32.2, A[0] + dy + 0.4, A[1] + dz + 0.4], 'bright', { split: '' });
    m.prism('input_shaft', 'x', A, 1.2, 46, 48, 'bright', { split: '', ends: ['east'] });
  }
  const PART = { rotor: { draw: rotor, shift: [-16, 0, 0] } };

  function model() { const m = mk(); base(m); casing(m); drive(m); gauges(m); return m; }
  function partial(name) { const s = mk(); PART[name].draw(s); const [dx, dy, dz] = PART[name].shift; return mk().merge(s, dx, dy, dz, ''); }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.GXR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'coke/gas_exhauster'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'coke/gas_exhauster'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
