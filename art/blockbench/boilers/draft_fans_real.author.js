// Forced draught fan and induced draught fan of the I1 water-tube boiler at real scale (1 block = 1 m, 16 px = 1 m),
// sized for a TP-35 class boiler: a VDN-11.2 type blower (impeller 1.12 m) and a DN-12.5 type smoke exhauster
// (impeller 1.25 m). Single-inlet centrifugal fans: the Create input coupling and the bearing pedestal at the north
// end, the scroll casing with its discharge rising to an outlet flange on top, and the inlet with the guide vane
// ring (the boiler's regulators set its opening) at the south end: an open bell-mouth with a guard on the blower,
// a flanged inlet box for the flue gas duct on the exhauster. Both stand on a concrete foundation one block deep.
// Dimensions approximate the VDN/DN catalogue drawings.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for k in fd, id:
//   FANR.build(repo, k); /* wait */ FANR.save(repo, k)
//   FANR.buildPartial(repo, k); FANR.savePartial(repo, k)     the impeller partial
//   FANR.preview(repo, k)                                     everything in place, for looking only
//
// Controller block = (0, 0, 0): the Create shaft comes in through its north face at (8, 8, 0), the fan axis runs
// along z through (x 8, y 8). Footprint x -1..1, y -1..1, z 0..2 (the foundation fills y -1).
// Connections (px):
// - fd (forced_draft_fan_real): air in through the bell-mouth on the south face of (0, 0, 2) at (8, 8, 48);
//   air out on the top face of (1, 1, 1) at (20, 32, 23), to the windbox duct.
// - id (induced_draft_fan_real): flue gas in on the south face of (0, 0, 2) at (8, 8, 48), from the boiler's
//   last pass; gas out on the top face of (1, 1, 1) at (20, 32, 23), to the chimney.
// Partial `impeller` (<id>/partial/impeller): shaft, coupling half, back plate, blades and the shroud; turns about
// z through (8, 8), drawn in place.
(function () {
  const A = [8, 8];
  const KIND = {
    fd: { id: 'forced_draft_fan_real', R: 9, paint: 'plant/blue', P: 16 },
    id: { id: 'induced_draft_fan_real', R: 10, paint: 'boilers/id_paint', P: 16 },
  };
  const CZ = [16, 30];                       // scroll casing z span
  const mkFor = (k) => {
    const MAT = Object.assign({}, PCVV.MAT, {
      fan: { tex: KIND[k].paint, P: 16 }, duct: { tex: 'plant/structure', P: 16 }, soot: { tex: 'material/soot', P: 16 },
    });
    return () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: KIND[k].paint });
  };

  // ------------------------------------------------------------------ static
  function foundation(m) {
    m.box('foundation', [-14, -16, 1], [30, -11, 47], 'conc', { split: 'xz' });
    m.box('frame', [-12, -11, 3], [28, -9.6, 45], 'ci', { split: 'z' });
    for (const x of [-11, 27]) for (const z of [5, 24, 43]) m.box(`anchor_${x}_${z}`, [x - 0.5, -9.6, z - 0.5], [x + 0.5, -9, z + 0.5], 'bright', { split: '' });
  }

  function pedestal(m) {
    m.box('pedestal', [2, -9.6, 4], [14, A[1] - 3, 14], 'fan', { split: '' });
    m.box('pedestal_foot', [0.6, -9.6, 3], [15.4, -8.4, 15], 'fan', { split: '' });
    for (const [n, z0, z1] of [['n', 4, 7.4], ['s', 10.6, 14]]) {
      m.prism(`bearing_${n}`, 'z', A, 3.6, z0, z1, 'fan', { split: '', ends: ['north', 'south'] });
      m.box(`bearing_cap_${n}`, [A[0] - 2.6, A[1] + 3, z0 + 0.4], [A[0] + 2.6, A[1] + 4.2, z1 - 0.4], 'fan', { split: '' });
      m.box(`oil_glass_${n}`, [A[0] + 3.2, A[1] - 1, (z0 + z1) / 2 - 0.6], [A[0] + 3.8, A[1] + 1, (z0 + z1) / 2 + 0.6], 'br', { split: '' });
    }
    m.box('coupling_guard', [A[0] - 4.4, A[1] + 4.4, 0], [A[0] + 4.4, A[1] + 5, 4], 'yel', { split: '' });
    for (const s of [-1, 1]) m.box(`guard_side_${s}`, [A[0] + s * 4.1 - 0.3, -9.6, 0], [A[0] + s * 4.1 + 0.3, A[1] + 4.4, 4], 'yel', { split: '' });
    m.prism('shaft_seal', 'z', A, 2.4, 14, CZ[0], 'fan', { split: '', ends: ['north'] });
  }

  // Scroll casing: the round part about the axis grows into a second, larger offset part on the discharge side,
  // whose tangent runs straight up into the outlet; side plates, stiffeners and feet.
  function scroll(m, R) {
    m.prism('scroll_a', 'z', A, R + 2, CZ[0], CZ[1], 'fan', { split: '', ends: ['north', 'south'] });
    m.prism('scroll_b', 'z', [A[0] + 2.5, A[1] + 1], R + 3.5, CZ[0], CZ[1], 'fan', { split: '', ends: ['north', 'south'] });
    const x0 = A[0] + 1, x1 = A[0] + R + 6.5;
    m.box('discharge', [x0, A[1] + 2, CZ[0]], [x1, 31, CZ[1]], 'fan', { split: 'y' });
    m.box('outlet_flange', [x0 - 1, 31, CZ[0] - 1], [x1 + 1, 32, CZ[1] + 1], 'ci', { split: '' });
    for (const z of [CZ[0] - 0.4, CZ[1]]) m.box(`stiff_${z}`, [x0, 14, z], [x1, 15, z + 0.4], 'ci', { split: '' });
    for (const x of [A[0] - R, A[0] + R + 1]) m.box(`scroll_foot_${x}`, [x - 1.5, -9.6, CZ[0] + 1], [x + 1.5, A[1] - R + 2, CZ[1] - 1], 'fan', { split: '' });
    m.box('drain', [A[0] - 1, A[1] - R - 3.2, 22], [A[0] + 1, A[1] - R - 1, 24], 'ci', { split: '' });
    m.box('access_door', [A[0] - R - 2.1, A[1] - 3, 19], [A[0] - R - 1.6, A[1] + 3, 27], 'ci', { split: '' });
    m.decal('plate', [A[0] - R - 2.3, A[1] + 4, 20], [A[0] - R - 2.1, A[1] + 7, 26], 'fan', 'west', 'pl.maker');
    m.box('arrow', [x1 + 0.01, 22, 20], [x1 + 0.1, 26, 26], 'fan', { split: '', face: { east: { tex: 'boilers/arrow', uv: [0, 0, 16, 16] } } });
  }

  // Guide vane ring at the eye with its lever and the actuator on a bracket; then the inlet of each kind.
  function inlet(m, k, R) {
    m.ring('vane_ring', 'z', A, R - 1, 2.4, CZ[1], CZ[1] + 3, 'ci');
    for (let a = 0; a < 8; a++) {
      const t = (a + 0.5) * Math.PI / 4, x = A[0] + (R - 2.2) * Math.cos(t), y = A[1] + (R - 2.2) * Math.sin(t);
      m.box(`vane_pin_${a}`, [x - 0.4, y - 0.4, CZ[1] + 3], [x + 0.4, y + 0.4, CZ[1] + 3.6], 'bright', { split: '' });
    }
    m.box('vane_lever', [A[0] - R - 4, A[1] + 1, CZ[1] + 2], [A[0] - R + 1, A[1] + 2, CZ[1] + 3], 'red', { split: '' });
    m.box('actuator_bracket', [A[0] - R - 6, -9.6, CZ[1] + 1], [A[0] - R - 3, A[1] - 2, CZ[1] + 4], 'ci', { split: '' });
    m.box('actuator', [A[0] - R - 6.6, A[1] - 2, CZ[1]], [A[0] - R - 2.4, A[1] + 3, CZ[1] + 5], 'dark', { split: '' });
    if (k === 'fd') {
      m.ring('bell_neck', 'z', A, R - 1, 1.6, CZ[1] + 3, 40, 'fan');
      m.ring('bell_flare', 'z', A, R + 1.5, 2.2, 40, 44, 'fan');
      m.ring('bell_lip', 'z', A, R + 2.8, 1.2, 44, 46, 'fan');
      for (const d of [-4, 0, 4]) {
        m.box(`guard_v_${d}`, [A[0] + d - 0.2, A[1] - R - 2, 45.4], [A[0] + d + 0.2, A[1] + R + 2, 45.8], 'st', { split: '' });
        m.box(`guard_h_${d}`, [A[0] - R - 2, A[1] + d - 0.2, 45.4], [A[0] + R + 2, A[1] + d + 0.2, 45.8], 'st', { split: '' });
      }
      m.box('bell_stand', [A[0] - 2, -9.6, 38], [A[0] + 2, A[1] - R + 1, 42], 'ci', { split: '' });
    } else {
      const w = R + 2;
      m.box('inlet_box', [A[0] - w, A[1] - w, CZ[1] + 3], [A[0] + w, A[1] + w, 46], 'fan', { split: 'z', hide: ['south'] });
      m.box('inlet_flange_t', [A[0] - w - 1, A[1] + w - 1.6, 46], [A[0] + w + 1, A[1] + w + 1, 48], 'ci', { split: '' });
      m.box('inlet_flange_b', [A[0] - w - 1, A[1] - w - 1, 46], [A[0] + w + 1, A[1] - w + 1.6, 48], 'ci', { split: '' });
      m.box('inlet_flange_w', [A[0] - w - 1, A[1] - w + 1.6, 46], [A[0] - w + 1.6, A[1] + w - 1.6, 48], 'ci', { split: '' });
      m.box('inlet_flange_e', [A[0] + w - 1.6, A[1] - w + 1.6, 46], [A[0] + w + 1, A[1] + w - 1.6, 48], 'ci', { split: '' });
      m.box('inlet_mouth', [A[0] - w + 1.6, A[1] - w + 1.6, 46.4], [A[0] + w - 1.6, A[1] + w - 1.6, 46.6], 'soot', { split: '' });
      for (const s of [-1, 1]) m.box(`inlet_leg_${s}`, [A[0] + s * (w - 2) - 1.5, -9.6, 40], [A[0] + s * (w - 2) + 1.5, A[1] - w, 44], 'ci', { split: '' });
      m.box('thermo_well', [A[0] + w, A[1] + 2, 40], [A[0] + w + 2, A[1] + 3, 41], 'bright', { split: '' });
    }
  }

  // ------------------------------------------------------------------ partial
  const ROT = (a) => ({ axis: 'z', angle: a, origin: [A[0], A[1], 22] });
  function impeller(m, R) {
    m.prism('shaft', 'z', A, 1.4, 1.6, CZ[0] + 2, 'bright', { split: '' });
    m.prism('coupling', 'z', A, 3, 0, 1.6, 'forged', { split: '', ends: ['north', 'south'] });
    m.box('coupling_key', [A[0] - 3.4, A[1] - 0.5, 0], [A[0] + 3.4, A[1] + 0.5, 1.6], 'st', { split: '' });
    m.disc('back_plate', 'z', A, R - 1, CZ[0] + 1.4, CZ[0] + 2, 'dark', 'vver/flange', 'south');
    m.prism('hub', 'z', A, 2.2, CZ[0] + 2, CZ[0] + 4, 'dark', { split: '', ends: ['south'] });
    // backward-curved blades stood in for by straight vanes from hub to rim, twelve at 30-degree-ish spacing on
    // the 22.5 grid: pairs of opposite vanes along x and y, turned by 0, 22.5, 45, -22.5
    for (const a of [0, 22.5, 45, -22.5]) for (const d of ['x', 'y']) {
      const f = d === 'x' ? [A[0] + 2.4, A[1] - 0.3, CZ[0] + 2] : [A[0] - 0.3, A[1] + 2.4, CZ[0] + 2];
      const t = d === 'x' ? [A[0] + R - 1.2, A[1] + 0.3, CZ[1] - 3] : [A[0] + 0.3, A[1] + R - 1.2, CZ[1] - 3];
      const f2 = d === 'x' ? [A[0] - R + 1.2, f[1], f[2]] : [f[0], A[1] - R + 1.2, f[2]];
      const t2 = d === 'x' ? [A[0] - 2.4, t[1], t[2]] : [t[0], A[1] - 2.4, t[2]];
      m.box(`blade_${a}_${d}_p`, f, t, 'dark', { split: '', rot: a ? ROT(a) : undefined });
      m.box(`blade_${a}_${d}_n`, f2, t2, 'dark', { split: '', rot: a ? ROT(a) : undefined });
    }
    m.ring('shroud', 'z', A, R - 1, 3, CZ[1] - 3, CZ[1] - 2.2, 'dark');
  }

  const built = {};
  function model(k) { const mk = mkFor(k), m = mk(), R = KIND[k].R; foundation(m); pedestal(m); scroll(m, R); inlet(m, k, R); return m; }
  function partial(k) { const m = mkFor(k)(); impeller(m, KIND[k].R); return m; }
  window.FANR = {
    KIND, model, partial,
    build: (root, k) => { const m = PCVV.cut(model(k)); PCART.realize(root, m, KIND[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, KIND[k].id, 'boilers'),
    buildPartial: (root, k) => { const m = partial(k); PCART.realize(root, m, KIND[k].id + '_impeller'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k) => PCVV.savePartial(root, KIND[k].id, 'impeller', 'boilers'),
    preview: (root, k) => { const m = model(k); m.merge(partial(k), 0, 0, 0, 'imp_'); PCVV.cut(m); PCART.realize(root, m, KIND[k].id + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
