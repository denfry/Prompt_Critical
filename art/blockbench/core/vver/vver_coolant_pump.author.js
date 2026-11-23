// GCN-195M reactor coolant pump at real scale (1 block = 1 m): vertical single-stage centrifugal pump. The
// insulated spherical hydraulic casing with the suction and discharge DN850 nozzles sits on three legs; above it
// the cover flange, the shaft seal housing with its seal water lines, the open motor stand where the coupling
// turns, and the vertical motor with its two air coolers, terminal box, oil lines and the upper bearing cover.
// Dimensions and sources: docs/research/vver-dimensions.md (disk only).
// Load after vverkit.js, then VVER_PUMP.build(repo); /* wait */ VVER_PUMP.save(repo);
//   VVER_PUMP.buildCoupling(repo); VVER_PUMP.saveCoupling(repo)   partial: models/block/vver_coolant_pump/partial/coupling
//
// Controller block = (0, 0, 0) under the casing; pump axis through its centre (x = z = 8). Front north.
// Connections (ends on block faces, axes on block centres):
//   suction   (from the steam generator): west face of cell (-2, 1, 0)
//   discharge (to the reactor):           east face of cell (2, 1, 0)
//   Create shaft input: the motor's upper shaft end, vertical, up face of cell (0, 9, 0)
(function () {
  const C = [8, 8];

  function model() {
    const m = PCVV.mk('vver/motor');
    // hydraulic casing: a sphere-like body, cover flange with its studs
    m.headDown('cas_bot', C, 22, 12, 12, 4, 'clad');
    m.cylY('casing', C, 22, 12, 36, 'clad');
    m.headUp('cas_top', C, 22, 36, 6, 3, 'clad');
    m.cylY('cover', C, 19, 40, 46, 'forged', { cap: 'vver/flange', bottom: true });
    m.nutsY('cover_nut', C, 17.4, 24, 46, 2.4, 1.9, 'bright');
    // suction west, discharge east (cold leg water: green bands, GOST 14202)
    m.nozzle('suction', 'x', [24, 8], -12, -32, 10, 8, 'gw');
    m.nozzle('discharge', 'x', [24, 8], 28, 48, 10, 8, 'gw');
    // three legs with pads
    for (const a of [90, 225, 315]) {
      m.postY(`leg_${a}`, C, 18, a, 4, 1.5, 12, 'pnt');
      m.postY(`pad_${a}`, C, 18, a, 6, 0, 1.5, 'conc', { split: '' });
    }
    // plates on the casing front
    m.decal('sign', [4.8, 22, -14.7], [11.2, 28.4, -14], 'clad', 'north', 'pl.sign_s');

    // shaft seal housing and the seal water lines (in and leak-off) with valves
    m.cylY('seal', C, 11, 46, 66, 'forged');
    m.cylY('seal_band', C, 11.4, 56, 58, 'forged', { cap: 'vver/flange', bottom: true });
    for (const [a, n] of [[45, 'in'], [135, 'out'], [-45, 'cool']]) {
      m.radial(`seal_${n}`, C, 10.5, 15.5, a, 1.6, 51, 52.6, 'gw');
      const rad = a * Math.PI / 180, x = C[0] + 16 * Math.cos(rad), z = C[1] + 16 * Math.sin(rad);
      m.box(`seal_${n}_up`, [x - 0.8, 51, z - 0.8], [x + 0.8, 64, z + 0.8], 'gw', { split: '' });
      m.box(`seal_${n}_valve`, [x - 1.6, 58, z - 1.6], [x + 1.6, 60.5, z + 1.6], 'gw', { split: '' });
      m.wheelY(`seal_${n}_wheel`, x, 62, z, 1.8, 'red');
    }

    // motor stand: plates and four posts, the lower shaft up to the coupling (partial)
    m.cylY('stand_bot', C, 17, 66, 69, 'pnt', { cap: 'vver/flange', bottom: true });
    for (const a of [45, 135, 225, 315]) m.postY(`stand_post_${a}`, C, 14.5, a, 4, 69, 97, 'pnt');
    m.cylY('stand_top', C, 19.5, 97, 100, 'pnt', { bottom: true });
    m.cylY('shaft_low', C, 2.5, 69, 80, 'bright');
    // guard rails between the posts at the coupling (yellow)
    m.ringY('guard', C, 16, 0.5, 86, 86.6, 'yel');

    // motor: ribbed frame between two end rings, upper bearing cover, shaft end on top
    m.cylY('motor_ring_lo', C, 20.2, 100, 103, 'mcap', { bottom: true });
    m.cylY('motor', C, 19, 103, 145, 'motor');
    m.cylY('motor_ring_hi', C, 20.2, 145, 148, 'mcap', { cap: 'vver/cap' });
    m.cylY('motor_top', C, 15, 148, 154, 'mcap', { cap: 'vver/flange' });
    m.cylY('bearing_cap', C, 8, 154, 157, 'mcap', { cap: 'vver/flange' });
    m.cylY('shaft_top', C, 2.4, 157, 159.93, 'bright', { cap: 'material/brass_disc' });
    m.box('shaft_key', [7.6, 157, 5.2], [8.4, 160, 5.8], 'dark', { split: '' });
    for (const a of [0, 90, 180, 270]) m.radial(`lug_${a}`, C, 14, 17.5, a, 2, 148, 152, 'mcap');
    // air coolers north and south, intermediate-circuit cooling water lines (green) down to the floor
    for (const [n, z0, z1, dir] of [['n', -15.3, -11, 'north'], ['s', 27, 32, 'south']]) {
      m.box(`cooler_${n}`, [-6, 108, z0], [22, 140, z1], 'motor');
      m.box(`cooler_${n}_hdr`, [-7, 106, z0], [23, 108, z1], 'mcap', { split: '' });
      for (const x of [-4, 20]) {
        const zp = dir === 'north' ? z0 + 1 : z1 - 2;
        m.box(`cooler_${n}_pipe${x}`, [x - 0.8, 0, zp], [x + 0.8, 106, zp + 1], 'gw', { split: 'y' });
        m.box(`cooler_${n}_valve${x}`, [x - 1.5, 60, zp - 0.5], [x + 1.5, 62.4, zp + 1.5], 'gw', { split: '' });
      }
    }
    m.decal('name', [-2, 126, -16], [18, 131.2, -15.3], 'motor', 'north', 'pl.pump_name');
    m.decal('sign_m', [3, 112, -16], [13, 122, -15.3], 'motor', 'north', 'pl.sign');
    m.decal('maker', [26, 104, 5], [26.7, 108, 11], 'mcap', 'east', 'pl.maker');
    // terminal box east and the cable conduit down to a junction box on the stand
    m.box('term', [26, 112, 2], [30, 124, 14], 'mcap', { split: '' });
    m.box('conduit', [27, 72, 6], [29.4, 112, 10], 'dark', { split: 'y' });
    m.box('junction', [25, 69, 3], [30, 74, 13], 'mcap', { split: '' });
    // oil lines (brown) from the lower bearing down the west side
    for (const z of [5, 10]) {
      m.box(`oil_${z}`, [-12, 69, z], [-10.6, 100, z + 1.4], 'oil', { split: 'y' });
      m.box(`oil_${z}_tap`, [-12, 98.6, z], [-9, 100, z + 1.4], 'oil', { split: '' });
    }
    return m;
  }

  // Partial: the coupling between the pump and motor shafts with its bolted sleeve and a paint mark. Drawn at the
  // controller + (0, 5, 0) blocks (world y 80); turns about the vertical axis through local x = 8, z = 8.
  function coupling() {
    const m = PCVV.mk('material/bright_steel');
    m.cylY('shaft', C, 2.5, 0, 17, 'bright');
    m.cylY('hub_lo', C, 6, 3, 7, 'forged', { cap: 'vver/flange', bottom: true });
    m.cylY('sleeve', C, 7, 7, 10, 'st', { cap: 'vver/flange', bottom: true });
    m.cylY('hub_hi', C, 6, 10, 14, 'forged', { cap: 'vver/flange', bottom: true });
    m.nutsY('bolt', C, 7.3, 8, 7.4, 2.2, 1.3, 'bright');
    m.box('mark', [14.6, 7, 7.3], [15.3, 10, 8.7], 'red', { split: '' });
    m.box('mark_hub', [13.6, 3, 7.4], [14.3, 7, 8.6], 'red', { split: '' });
    return m;
  }

  window.VVER_PUMP = {
    model, coupling,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'vver_coolant_pump'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'vver_coolant_pump'),
    buildCoupling: (root) => { const m = coupling(); PCART.realize(root, m, 'coupling'); return m.els.length; },
    saveCoupling: (root) => PCVV.savePartial(root, 'vver_coolant_pump', 'coupling'),
  };
})();
