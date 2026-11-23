// Jolt-squeeze moulding machine at real scale (1 block = 1 m, 16 units = 1 m): a swing-yoke machine of the 1910s–20s
// (Tabor, Osborn): the cast base over the jolt cylinder, the table 1.1 x 0.65 m with the pattern plate and the flask,
// two posts at the back carrying the yoke with the squeeze head over the table, 1.9 m high, the air valves at the
// front, the drive box with its compressor on the west taking the Create shaft, and on the east the overhead sand
// hopper on its stand with the gate and the chute over the flask, a rack of pattern plates below it. After Tabor
// catalogue figures (table 26 x 42 in, squeeze 9 t, jolt stroke 2 in) and the AFS "Moulding Machine" handbook.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   MSR.build(repo); /* wait */ MSR.save(repo)                  static model: wholemodels/molding_station_real.json
//   MSR.buildPartial(repo, 'table'); MSR.savePartial(repo, 'table')
// Textures: the existing sand_mold/* and molding_station/* (foundry/molding.author.js).
//
// Controller block = (0, 0, 0), the machine cell, front north. Footprint x 0..1, y 0..1, z 0..0 (model x 0..32,
// y 0..32, z 0..16). Shaft in: west face of (0, 0, 0) at (0, 8, 8), axis x (same as the old block). Sand comes in
// at the hopper top, y 31, over (1, 1, 0); the moulded flask comes off the table to the north of (0, 0, 0).
// Partial table (table, pattern plate, flask with its sand): jolted by translate +y up to 0.8 px (2 in), raised
// for the squeeze by up to 2 px; no shift.
(function () {
  const ID = 'molding_station_real';
  const J = [12, 7.6];                                     // jolt cylinder axis [x, z]
  const MAT = Object.assign({}, PCVV.MAT, {
    fl: { tex: 'sand_mold/flask', P: 16 }, sand: { tex: 'sand_mold/sand', P: 16 }, mp: { tex: 'molding_station/paint', P: 16 },
    pat: { tex: 'molding_station/pattern', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'molding_station/paint' });

  function machine(m) {
    m.box('pit_curb', [3, 0, 1], [21, 1, 15], 'conc', { hide: ['down'] });
    m.box('base', [4, 1, 2], [20, 6, 13.6], 'mp');
    m.cylY('jolt_cyl', J, 4.6, 6, 10.6, 'ci');
    m.ringY('jolt_cushion', J, 4.9, 0.4, 9.6, 10.6, 'ci');
    // posts at the back, the swing yoke forward over the table and the squeeze head under it
    for (const x of [5, 17]) {
      m.box(`post_${x}`, [x, 6, 12.4], [x + 2, 28, 15.4], 'mp', { split: 'y' });
      m.box(`post_cap_${x}`, [x - 0.4, 28, 12], [x + 2.4, 29, 15.8], 'ci', { split: '' });
    }
    m.box('yoke', [4.4, 26, 2.4], [19.6, 29, 12.4], 'mp', { split: '' });
    m.box('yoke_pin', [4, 27, 13.4], [20, 28, 14.4], 'bright', { split: '' });
    m.box('squeeze_head', [6, 23.6, 3], [18, 26, 11.6], 'ci', { split: '' });
    for (const x of [6.6, 16.4]) m.box(`head_rib_${x}`, [x, 24.6, 2.6], [x + 1, 26, 12], 'ci', { split: '' });
    // air valves and levers on the front of the base; the air main up the west post
    for (const [n, x] of [['jolt', 7], ['squeeze', 10.4], ['draw', 13.8]]) {
      m.box(`valve_${n}`, [x, 3, 1.2], [x + 2.4, 5, 2], 'br', { split: '' });
      m.box(`lever_${n}`, [x + 0.8, 5, 0.6], [x + 1.6, 9.4, 1.4], 'st', { split: '' });
      m.box(`knob_${n}`, [x + 0.6, 9.4, 0.4], [x + 1.8, 10.4, 1.6], 'dark', { split: '' });
    }
    m.box('air_main', [4.2, 1, 14.2], [5, 22, 15], 'gw', { split: 'y' });
    m.decal('maker_plate', [8, 2, 1.6], [14, 4, 2], 'mp', 'north', 'pl.maker');
  }

  function drive(m) {
    // drive box on the west: the Create shaft into a belt-driven two-cylinder compressor and its receiver
    m.box('drive_box', [0, 1, 3], [4, 13, 13], 'mp', { split: '' });
    m.prism('shaft_boss', 'x', [8, 8], 2, 0, 0.6, 'ci', { split: '', ends: ['west'] });
    m.cylY('comp_cyl_a', [2, 5.4], 1.4, 13, 17, 'ci', { cap: 'vver/flange' });
    m.cylY('comp_cyl_b', [2, 10.6], 1.4, 13, 17, 'ci', { cap: 'vver/flange' });
    m.box('receiver_leg', [0.6, 0, 0.6], [3.4, 1, 2.6], 'ci', { split: '' });
    m.prism('receiver', 'z', [2, 4], 1.8, 0.4, 3, 'gw', { split: '', ends: ['north', 'south'] });
    m.box('comp_pipe', [1.6, 17, 5.4], [2.4, 18, 14.6], 'gw', { split: '' });
    m.box('comp_pipe_up', [1.6, 18, 14], [5, 18.8, 14.8], 'gw', { split: '' });
  }

  function hopper(m) {
    // stand on four legs in the east cell, the hopper, gate with its lever and the chute over the flask
    for (const x of [22, 30.4]) for (const z of [1, 13.4]) m.box(`stand_leg_${x}_${z}`, [x, 0, z], [x + 1.6, 18, z + 1.6], 'ci', { split: 'y' });
    m.box('stand_frame', [22, 18, 1], [32, 19, 15], 'ci', { split: '' });
    m.box('hopper_top', [22.4, 25, 1.4], [31.6, 31, 14.6], 'st', { split: '' });
    m.box('hopper_mid', [23.4, 21, 3], [30.6, 25, 13], 'st', { split: '' });
    m.box('hopper_neck', [24.6, 19, 5], [29.4, 21, 11], 'st', { split: '' });
    m.box('hopper_sand', [22.8, 30.8, 1.8], [31.2, 31.2, 14.2], 'sand', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('gate', [24.2, 17.4, 4.6], [29.8, 18, 11.4], 'ci', { split: '' });
    m.box('gate_lever', [29.8, 14, 7.6], [30.6, 18.6, 8.4], 'st', { split: '' });
    m.box('chute', [17, 16.4, 5.2], [25, 17.2, 10.8], 'st', { split: '' });
    m.box('chute_lip_n', [17, 17.2, 5.2], [25, 18.2, 5.8], 'st', { split: '' });
    m.box('chute_lip_s', [17, 17.2, 10.2], [25, 18.2, 10.8], 'st', { split: '' });
    // spare pattern plates in a rack under the stand
    m.box('rack', [23.6, 0, 3], [30.4, 1, 13], 'wood', { split: '' });
    for (const x of [24.4, 26.4, 28.4]) m.box(`spare_plate_${x}`, [x, 1, 3.4], [x + 0.8, 10, 12.6], 'pat', { split: '' });
  }

  // ------------------------------------------------------------------ partial
  function table(m) {
    m.box('table', [3, 10.6, 1.6], [21, 12, 12.2], 'ci', { split: '' });
    for (const x of [3.6, 19.6]) m.box(`table_lug_${x}`, [x, 9.8, 6.6], [x + 0.8, 10.6, 8.6], 'ci', { split: '' });
    m.box('pattern_plate', [4, 12, 2.2], [20, 12.8, 11.6], 'pat', { split: '' });
    m.box('flask', [4.6, 12.8, 2.6], [19.4, 17.6, 11.2], 'fl', { split: '' });
    for (const x of [3.6, 19.4]) m.box(`flask_handle_${x}`, [x, 14.4, 6], [x + 1, 15.4, 8], 'fl', { split: '' });
    m.box('flask_sand', [5.2, 17.6, 3.2], [18.8, 18, 10.6], 'sand', { split: '', hide: ['down'] });
  }
  const PART = { table: { draw: table, note: 'jolt +y 0.8 px, squeeze +y 2 px' } };

  function model() { const m = mk(); machine(m); drive(m); hopper(m); return m; }
  function partial(name) { const m = mk(); PART[name].draw(m); return m; }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.MSR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'foundry/molding_station'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'foundry/molding_station'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
