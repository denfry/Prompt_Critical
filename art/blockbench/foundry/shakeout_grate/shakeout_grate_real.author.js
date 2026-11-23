// Vibrating shakeout grate at real scale (1 block = 1 m, 16 units = 1 m): a deck 1.5 x 1.6 m of grate bars in a
// walled frame on four coil springs over the sand pit, the unbalanced-mass vibrator on the west side of the deck
// driven through the belt guard from the Create shaft, and the side-draft dust hood on the east with its slotted face
// and the duct up to the extraction main. Deck top 0.6 m. After the AFS "Foundry Sand Handling" figures (shakeouts
// 4 x 5 ft to 6 x 8 ft, 0.5–0.7 m deck, 1500–1800 vibrations a minute, side-draft hoods 1 m high).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   SGR.build(repo); /* wait */ SGR.save(repo)                  static model: wholemodels/shakeout_grate_real.json
//   SGR.buildPartial(repo, 'deck'); SGR.savePartial(repo, 'deck')
// Textures: the existing sand_mold/*, molding_station/*, shakeout_grate/spring (foundry/molding.author.js).
//
// Controller block = (0, 0, 0), front north. Footprint x 0..1, y 0..1, z 0..1 (model x 0..32, y 0..32, z 0..32).
// Shaft in: west face of (0, 0, 0) at (0, 8, 8), axis x (the old block's lower west cell). Flasks are set on the
// deck (top y 10) from the north or above; sand falls into the pit; dust out of the duct top at (28.6, 32, 16),
// up face of (1, 1, 1).
// Partial deck (deck frame, bars, vibrator body and a shaken-out casting): jolted by translate ±y 0.4 px; no shift.
(function () {
  const ID = 'shakeout_grate_real';
  const MAT = Object.assign({}, PCVV.MAT, {
    fl: { tex: 'sand_mold/flask', P: 16 }, sand: { tex: 'sand_mold/sand', P: 16 }, crust: { tex: 'sand_mold/crust', P: 16 },
    mp: { tex: 'molding_station/paint', P: 16 }, spring: { tex: 'shakeout_grate/spring', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'molding_station/paint' });
  const SPR = [[6, 6], [22, 6], [6, 26], [22, 26]];

  function base(m) {
    // pit curb round the sand pit (open below the deck), a sand heap in the pit
    m.box('curb_n', [0, 0, 0], [28, 1, 3], 'conc', { hide: ['down'] });
    m.box('curb_s', [0, 0, 29], [28, 1, 32], 'conc', { hide: ['down'] });
    m.box('curb_w', [0, 0, 3], [3, 1, 29], 'conc', { hide: ['down'] });
    m.box('pit', [3, 0, 3], [28, 0.2, 29], 'dark', { split: '', hide: ['down'] });
    m.box('pit_sand', [7, 0.2, 9], [21, 1.4, 23], 'sand', { split: '' });
    // base frame of channels on the curb, spring seats and the springs
    m.box('frame_n', [3, 1, 3], [26, 3, 5], 'ci', { split: 'x' });
    m.box('frame_s', [3, 1, 27], [26, 3, 29], 'ci', { split: 'x' });
    for (const [x, z] of SPR) m.cylY(`spring_${x}_${z}`, [x, z], 1.6, 3, 7.6, 'spring');
  }

  function drive(m) {
    m.prism('shaft_boss', 'x', [8, 8], 2, 0, 0.6, 'ci', { split: '', ends: ['west'] });
    m.box('pedestal', [0.6, 0, 5], [2.6, 6, 11], 'ci', { split: '' });
    m.box('belt_guard', [0.4, 6, 4.4], [2.6, 14, 18], 'mp', { split: '' });
    m.box('guard_mesh', [0.3, 7, 6], [0.4, 13, 16], 'grate', { split: '' });
  }

  function hood(m) {
    // side-draft hood on the east: slotted face toward the deck, plenum, the duct up and out of the top
    m.box('hood_body', [28, 1, 2], [32, 24, 30], 'st');
    m.box('hood_face', [27.6, 10, 3], [28, 22, 29], 'grate', { split: '' });
    m.box('hood_lip', [26, 22, 2], [28, 23, 30], 'st', { split: '' });
    m.box('hood_roof', [26, 23, 2], [32, 24, 30], 'st', { split: '' });
    m.cylY('duct', [28.6, 16], 2.6, 24, 32, 'st');
    m.ringY('duct_flange', [28.6, 16], 3, 0.4, 24, 25, 'ci');
    m.box('damper_lever', [31, 27, 13], [31.6, 30, 13.6], 'st', { split: '' });
  }

  // ------------------------------------------------------------------ partial
  function deck(m) {
    m.box('deck_frame_n', [3, 7.6, 3], [26, 13, 4], 'mp', { split: '' });
    m.box('deck_frame_s', [3, 7.6, 28], [26, 13, 29], 'mp', { split: '' });
    m.box('deck_frame_w', [3, 7.6, 4], [4, 13, 28], 'mp', { split: '' });
    m.box('deck_frame_e', [25, 7.6, 4], [26, 10, 28], 'mp', { split: '' });
    for (let z = 5; z < 28; z += 2) m.box(`bar_${z}`, [4, 9, z], [25, 10, z + 0.8], 'ci', { split: '' });
    for (const x of [8.6, 14.6, 20.6]) m.box(`bearer_${x}`, [x, 7.6, 4], [x + 0.8, 9, 28], 'ci', { split: '' });
    // vibrator: two bearing housings and the eccentric-weight casing on the west wall
    m.box('vibrator', [1.2, 8.6, 6], [3, 13.4, 18], 'mp', { split: '' });
    m.prism('eccentric', 'x', [11, 12], 2.4, 0.8, 1.2, 'ci', { split: '', ends: ['west'] });
    // a shaken-out casting with burnt sand lumps
    m.box('casting', [10, 10, 11], [18, 12.4, 20], 'crust', { split: '' });
    m.box('sand_lump_a', [8, 10, 18], [11, 11.4, 22], 'sand', { split: '' });
    m.box('sand_lump_b', [17, 10, 9], [20, 11, 12.6], 'sand', { split: '' });
  }
  const PART = { deck: { draw: deck, note: 'jolt ±y 0.4 px' } };

  function model() { const m = mk(); base(m); drive(m); hood(m); return m; }
  function partial(name) { const m = mk(); PART[name].draw(m); return m; }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.SGR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'foundry/shakeout_grate'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'foundry/shakeout_grate'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
