// Mechanical stokers for the Lancashire boiler at real scale (1 block = 1 m, 16 units = 1 m): two ram-fed
// overfeed stokers of the Proctor / Bennis kind, one per furnace, on a common cast frame in front of the boiler.
// Each has a coal hopper (about 3 cwt) over a cast feeding box whose pusher plate is worked by a lever and an
// eccentric rod from the line shaft across the front; the line shaft takes its drive from a Create shaft at the
// west end through a clutch. Dimensions and sources: docs/research/lancashire_stoker-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   STOKER.build(repo); /* wait */ STOKER.save(repo)
//   STOKER.buildPartial(repo, name); STOKER.savePartial(repo, name)     for shaft, glow
//   STOKER.preview(repo)                                                 everything, for looking only
//
// Controller block = (0, 0, 0) on the floor at the front centre, front north. Footprint x -1..1, y 0..2,
// z -1..0; the south face of the z = 0 cells (px z 16) lies against the Lancashire front (boiler px z 0, same x).
// Stoker mouths over the fire doors at x -0.6 and 16.6. Connections: shaft in west of (-1, 0, -1) at (-16, 8, -8);
// coal in at the hopper tops (y 40). Partials: shaft turns about x through (y 8, z -8); glow in whole px.
(function () {
  const FX = [-0.6, 16.6], SY = 8, SZ = -8;

  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const FIT = { slot_glow: [30, 10, 1, 5] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT }, pl: PCVV.ATLAS.pl,
  }), { particle: 'material/cast_iron' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  function glowBox(m, name, f, t) {
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', hide: [], face });
  }

  function frame(m) {
    m.box('bed', [-16, 0, -14], [32, 1, 15.6], 'ci', { hide: ['down'] });
    for (const x of [-15, 7, 29]) {
      for (const z of [-12, 10]) m.box(`standard_${x}_${z}`, [x, 1, z], [x + 2, 19, z + 2], 'ci', { split: 'y' });
      m.box(`standard_top_${x}`, [x, 19, -12], [x + 2, 20.4, 12], 'ci', { split: '' });
      m.box(`standard_foot_${x}`, [x - 0.4, 1, -12.4], [x + 2.4, 2.4, 12.4], 'ci', { split: '' });
      m.box(`bearing_web_${x}`, [x + 0.4, 2.4, SZ - 1], [x + 1.6, SY - 3, SZ + 1], 'ci', { split: '' });
      m.box(`bearing_${x}`, [x - 0.4, SY - 3, SZ - 3], [x + 2.4, SY + 3, SZ + 3], 'ci', { split: '' });
      m.box(`bearing_cap_${x}`, [x - 0.2, SY + 3, SZ - 2.4], [x + 2.2, SY + 3.8, SZ + 2.4], 'ci', { split: '' });
      m.box(`oil_cup_${x}`, [x + 0.4, SY + 3.8, SZ - 0.6], [x + 1.6, SY + 5.2, SZ + 0.6], 'br', { split: '' });
    }
    m.box('beam', [-15, 19, 2], [31, 20.4, 15.6], 'ci', { split: 'x' });
    m.decal('maker', [6.6, 12, -12.3], [9.4, 14.4, -12], 'br', 'north', 'pl.maker', { hide: ['south'] });
    // clutch lever at the west end, by the drive coupling
    m.box('clutch_quadrant', [-12.6, 1, -14], [-11.4, 4, -10], 'ci', { split: '' });
    m.box('clutch_lever', [-12.4, 4, -12.4], [-11.6, 22, -11.6], 'st', { split: 'y' });
    m.box('clutch_grip', [-12.6, 22, -12.6], [-11.4, 24, -11.4], 'red', { split: '' });
  }

  function unit(m, c, i) {
    // feeding box on the beam, its mouthpiece against the furnace front
    m.box(`box_${i}`, [c - 5, 20.4, 3], [c + 5, 29.4, 15], 'ci', { split: '' });
    m.box(`mouth_${i}`, [c - 4.4, 21, 15], [c + 4.4, 28.8, 16], 'ci', { split: '' });
    for (const y of [20.8, 28.6]) for (const x of [c - 4.4, c + 3.6]) m.box(`box_bolt_${i}_${x}_${y}`, [x, y, 2.6], [x + 0.8, y + 0.8, 3], 'bright', { split: '' });
    m.box(`sight_${i}`, [c - 1, 25, 2.6], [c + 1, 27, 3], 'soot', { split: '' });
    // air regulator: a handwheel on the front with its spindle
    m.box(`reg_spindle_${i}`, [c + 3, 22.6, 1.6], [c + 3.8, 23.4, 3], 'bright', { split: '' });
    m.ring(`reg_wheel_${i}`, 'z', [c + 3.4, 23], 1.8, 0.5, 1.2, 1.8, 'red');
    // feed adjustment: ratchet quadrant on the east side of the box
    m.box(`ratchet_${i}`, [c + 5, 22, 5], [c + 5.6, 27, 9], 'ci', { split: '' });
    m.box(`ratchet_pawl_${i}`, [c + 5.6, 24, 6], [c + 6.2, 24.8, 8.4], 'bright', { split: '' });
    // hopper: cast throat, sheet hopper with its rim, coal inside
    m.box(`hopper_flange_${i}`, [c - 6, 29.4, 2], [c + 6, 30.4, 16], 'ci', { split: '' });
    m.box(`hopper_throat_${i}`, [c - 4.5, 30.4, 4], [c + 4.5, 34, 14.6], 'ci', { split: '' });
    m.box(`hopper_n_${i}`, [c - 6, 34, 2.4], [c + 6, 40, 2.8], 'plate', { split: '' });
    m.box(`hopper_s_${i}`, [c - 6, 34, 15.2], [c + 6, 40, 15.6], 'plate', { split: '' });
    m.box(`hopper_w_${i}`, [c - 6, 34, 2.8], [c - 5.6, 40, 15.2], 'plate', { split: '' });
    m.box(`hopper_e_${i}`, [c + 5.6, 34, 2.8], [c + 6, 40, 15.2], 'plate', { split: '' });
    m.box(`hopper_rim_${i}`, [c - 6.3, 39.6, 2.1], [c + 6.3, 40, 15.9], 'ci', { split: '', hide: ['up'] });
    m.box(`coal_${i}`, [c - 5.6, 34, 2.8], [c + 5.6, 38.4, 15.2], 'dark', { split: '' });
    // ram lever on the west side of the box, worked by the eccentric rod from the line shaft
    m.box(`lever_${i}`, [c - 6, 13, 4], [c - 5.2, 27, 5], 'st', { split: '' });
    m.box(`lever_boss_${i}`, [c - 6.4, 25.4, 3.6], [c - 5, 27.4, 5.4], 'ci', { split: '' });
    const L = Math.hypot(13 - SY, 4.5 - SZ) + 1, cy = (13 + SY) / 2, cz = (4.5 + SZ) / 2;
    m.box(`ecc_rod_${i}`, [c - 6, cy - 0.4, cz - L / 2], [c - 5.2, cy + 0.4, cz + L / 2], 'st',
      { rot: { axis: 'x', angle: -22.5, origin: [c - 5.6, cy, cz] } });
    m.box(`ecc_strap_${i}`, [c - 6.4, SY - 3, SZ - 3], [c - 4.8, SY + 3, SZ + 3], 'br', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function shaft(m) {
    m.prism('line_shaft', 'x', [SY, SZ], 1.1, -14.4, 31, 'bright', { split: '' });
    m.prism('coupling', 'x', [SY, SZ], 2.4, -16, -14.4, 'forged', { split: '', ends: ['east', 'west'] });
    m.box('coupling_key', [-16, SY - 0.6, SZ - 2.8], [-14.4, SY + 0.6, SZ + 2.8], 'st', { split: '' });
    FX.forEach((c, i) => m.prism(`eccentric_${i}`, 'x', [SY + 0.8, SZ], 2.2, c - 6.2, c - 5, 'ci', { split: '', ends: ['east', 'west'] }));
  }
  function glow(m) {
    FX.forEach((c, i) => glowBox(m, `sight_${i}`, [c - 0.8, 25.2, 2.5], [c + 0.8, 26.8, 2.58]));
  }

  const PART = {
    shaft: { draw: shaft, shift: [0, 0, 0], note: 'turns about x through (y 8, z -8)' },
    glow: { draw: glow, shift: [0, 0, 0], note: 'full-bright while the fire burns' },
  };
  function model() {
    const m = mk();
    frame(m);
    FX.forEach((c, i) => unit(m, c, i));
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

  window.STOKER = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'lancashire_stoker'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'lancashire_stoker', 'boilers/lancashire'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'lancashire_stoker_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'lancashire_stoker', name, 'boilers/lancashire'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'lancashire_stoker_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
