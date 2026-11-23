// Cast house machines at real scale (1 block = 1 m, 16 units = 1 m), the two kinds of cast_house_machine: Brosius'
// electric clay gun (1914) and a taphole drill on the same swinging pedestal. Both stand south of the taphole with the
// working end pointing north at it: a cast pedestal on its foundation, a slewing column 1.9 m high, the arm over it,
// and either the clay gun (barrel ⌀0.5 m, 1.8 m long, the motor and screw drive at the back, the nozzle in front) or
// the drill boom with the motor carriage and the 2.4 m drill rod. After Johnson, "Blast-Furnace Construction in
// America" (1917) and the Brosius catalogue figures (gun 1.7–2.2 m, slewing radius 3 m).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in CHM.ids:
//   CHM.build(repo, id); CHM.save(repo, id); CHM.buildPartial(repo, id); CHM.savePartial(repo, id)
//
// Both: controller (0, 0, 0) at the working end, front north toward the taphole. Footprint x -1..1, y 0..2, z 0..2
// (model x -16..32, y 0..48, z 0..48). Working point on the north face of (0, 1, 0) at (8, 20, 0). Create shaft
// into the pedestal gear on the west face of (-1, 0, 2) at (-16, 8, 40), axis x.
//   cast_house_mud_gun_real — partial `gun` (barrel, nozzle, motor and screw box): slews about y through the column
//                             axis (x 8, z 36), stowed = as drawn, a quarter turn west to park; saved shifted
//                             (0, -16, -16): draw it 16 px up and 16 px south.
//   cast_house_drill_real   — partial `bit` (drill rod, chuck): turns about z, axis (x 8, y 20); advance north by
//                             translate -z up to 2 px; saved unshifted.
(function () {
  const COL = [8, 36];
  const mk = () => Object.assign(new PCART.Model(PCVV.MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/cast_iron' });

  function pedestal(m) {
    m.box('foundation', [-6, 0, 24], [22, 2, 48], 'conc', { hide: ['down'] });
    m.cylY('pedestal', COL, 7, 2, 8, 'ci', { bottom: true });
    m.ringY('slew_ring', COL, 6.2, 1, 8, 9.4, 'bright');
    m.cylY('column', COL, 3, 9.4, 30, 'ci');
    m.cylY('column_cap', COL, 3.8, 30, 31, 'ci', { cap: 'vver/flange' });
    // slewing gear: the Create shaft in from the west to the worm box on the pedestal
    m.box('worm_box', [-6, 2, 34], [1, 9, 42], 'gw', { split: '' });
    m.prism('worm_shaft', 'x', [8, 40], 1.2, -16, -6, 'bright', { split: '', ends: ['west'] });
    m.prism('shaft_boss', 'x', [8, 40], 2.2, -16, -14.6, 'ci', { split: '', ends: ['west'] });
    // operator's levers and the cable to the controller on the east
    m.box('lever_stand', [18, 2, 30], [21, 14, 33], 'ci', { split: '' });
    for (const z of [30.6, 32]) m.box(`lever_${z}`, [19, 14, z], [19.8, 20, z + 0.6], 'st', { split: '' });
    m.box('cable', [14, 2, 31], [18, 3, 32], 'dark', { split: '' });
  }

  function gun(m) {
    m.box('arm', [5, 31, 10], [11, 34, 40], 'ci', { split: '' });
    m.box('hanger_front', [6.4, 24, 10], [9.6, 31, 13], 'ci', { split: '' });
    m.box('hanger_back', [6.4, 24, 28], [9.6, 31, 31], 'ci', { split: '' });
    m.prism('barrel', 'z', [8, 20], 4, 4, 34, 'ci', { split: '', ends: ['south'] });
    for (const z of [8, 18, 28]) m.ring(`barrel_band_${z}`, 'z', [8, 20], 4.3, 0.8, z, z + 1.4, 'st');
    m.prism('nose_cone', 'z', [8, 20], 2.6, 1.6, 4, 'ci', { split: '' });
    m.prism('nozzle', 'z', [8, 20], 1.2, 0, 1.6, 'bright', { split: '', ends: ['north'] });
    m.box('clay_door', [11.4, 17, 14], [12.6, 23, 22], 'st', { split: '' });
    m.box('screw_box', [3, 15, 34], [13, 25, 40], 'gw', { split: '' });
    m.prism('motor', 'z', [8, 20], 3.4, 40, 46, 'gw', { split: '', ends: ['south'] });
  }

  function drill(m) {
    // lattice boom: two chords with diagonals, the motor carriage on it, the rod out to the front
    m.box('arm', [5, 31, 26], [11, 34, 40], 'ci', { split: '' });
    for (const x of [4.6, 10.6]) {
      m.box(`chord_top_${x}`, [x, 24, 4], [x + 0.8, 25, 34], 'st', { split: '' });
      m.box(`chord_bot_${x}`, [x, 15, 4], [x + 0.8, 16, 34], 'st', { split: '' });
      for (let z = 6; z < 32; z += 6) m.box(`strut_${x}_${z}`, [x, 16, z], [x + 0.8, 24, z + 0.8], 'st', { split: '' });
    }
    m.box('boom_hanger', [6.4, 25, 28], [9.6, 31, 31], 'ci', { split: '' });
    m.box('nose_guide', [5.4, 16, 3], [10.6, 24, 4.6], 'ci', { split: '' });
    m.box('carriage', [5, 16.4, 20], [11, 23.6, 30], 'gw', { split: '' });
    m.prism('drill_motor', 'z', [8, 20], 3, 30, 36, 'gw', { split: '', ends: ['south'] });
    m.box('feed_screw', [7.4, 24.4, 4], [8.6, 25.2, 34], 'bright', { split: '' });
  }

  function bit(m) {
    m.prism('rod', 'z', [8, 20], 0.8, 0.4, 20, 'bright', { split: '' });
    m.prism('bit_head', 'z', [8, 20], 1.2, 0, 1.6, 'st', { split: '', ends: ['north'] });
    m.prism('chuck', 'z', [8, 20], 2, 18, 20, 'ci', { split: '', ends: ['north'] });
  }

  const KINDS = {
    cast_house_mud_gun_real: { draw: pedestal, part: 'gun', partDraw: gun, shift: [0, -16, -16] },
    cast_house_drill_real: { draw: (m) => { pedestal(m); drill(m); }, part: 'bit', partDraw: bit, shift: [0, 0, 0] },
  };
  const DIR = 'ironmaking/cast_house_machine';
  function model(id) { const m = mk(); KINDS[id].draw(m); return m; }
  function partial(id, raw) { const s = mk(); KINDS[id].partDraw(s); const [dx, dy, dz] = raw ? [0, 0, 0] : KINDS[id].shift; return mk().merge(s, dx, dy, dz, ''); }

  window.CHM = {
    ids: Object.keys(KINDS), model, partial,
    build: (root, id) => { const m = PCVV.cut(model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, DIR),
    buildPartial: (root, id) => { const m = partial(id); PCART.realize(root, m, id + '_' + KINDS[id].part); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, id) => PCVV.savePartial(root, id, KINDS[id].part, DIR),
    preview: (root, id) => { const m = model(id).merge(partial(id, true), 0, 0, 0, ''); PCART.realize(root, PCVV.cut(m), id + '_preview'); return PCVV.bounds(m); },
  };
})();
