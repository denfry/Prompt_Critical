// Self-contained pneumatic power hammer at real scale (1 block = 1 m, 16 units = 1 m): a 2 cwt (100 kg tup) hammer of
// the Massey/Bêché pattern, the sim's 20 kW at 64 rpm (18 §18.6.3). One cast box column on a bed carries both
// vertical cylinders: the compressor at the back over the crank, the working cylinder on the overhung arm in front
// over the anvil, the rotary valves between them under the valve chest worked by the treadle and the hand lever. The
// anvil block stands on its own on the foundation, apart from the frame. Overall about 0.9 x 2.0 x 2.8 m (Massey
// catalogues: 2 cwt, floor space 1.9 x 0.85 m, height 2.7 m, stroke 0.4 m, anvil block 0.7 t).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   PHR.build(repo); /* wait */ PHR.save(repo)                 static model: wholemodels/power_hammer_real.json
//   PHR.buildPartial(repo, name); PHR.savePartial(repo, name)  for ram, crank
//   PHR.preview(repo)                                          everything, for looking only
//
// Controller block = (0, 0, 0), the drive cell at the back (the Create shaft enters the controller), front north; the
// anvil cell is (0, 0, -1). Footprint x 0..0, y 0..2, z -1..0 (model x 0..16, y 0..48, z -16..16). Shaft in: west face
// of (0, 0, 0) at (0, 8, 8), axis x (ShaftSide.LEFT). Work: the lower die face y 12 over the anvil at (8, ., -10); the workpiece is put
// in from the north or the west of (0, 0, -1). The drawing below is in the old frame (anvil cell at z 0..16) and every
// model and partial is saved moved by Z0 = -16.
// Partials (model px, saved in place):
//   ram   — piston rod, tup and upper die, raised at rest (die face y 18.6); translate down along y by up to 6.2 px
//           (0.39 m stroke) to strike the work at y 12.4.
//   crank — crankshaft, flywheel-pulley on the east and the coupling to the Create shaft on the west; turns about x,
//           axis (y 8, z 8).
(function () {
  const ID = 'power_hammer_real';
  const W = [8, 6], CP = [8, 22], AY = 8, AZ = 24;   // working and compressor cylinder axes [x, z], crank axis
  const mk = () => Object.assign(new PCART.Model(PCVV.MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/cast_iron' });

  function foundation(m) {
    m.box('plinth', [0, 0, 0], [16, 1, 32], 'conc', { hide: ['down'] });
    // anvil block (sow), cap and the lower die, standing free of the frame
    m.box('sow', [3, 1, 1], [13, 8, 11], 'ci');
    m.box('sow_cap', [4, 8, 2], [12, 10, 10], 'ci', { split: '' });
    m.box('lower_die', [5, 10, 3], [11, 12, 9], 'bright', { split: '' });
    m.box('die_key', [11, 10.4, 5.4], [12.6, 11.4, 6.6], 'st', { split: '' });
    for (const x of [3.6, 11.8]) for (const z of [1.6, 9.8]) m.box(`sow_bolt_${x}_${z}`, [x, 1, z], [x + 0.6, 1.6, z + 0.6], 'bright', { split: '' });
  }

  function frame(m) {
    m.box('bed', [3, 1, 12], [13.2, 4, 31], 'green');
    for (const x of [3.6, 12.8]) for (const z of [12.8, 29.6]) m.box(`bed_bolt_${x}_${z}`, [x, 4, z], [x + 0.6, 4.6, z + 0.6], 'bright', { split: '' });
    m.box('column', [4, 4, 14], [13, 30, 30], 'green');
    m.box('column_rib_w', [3.4, 4, 15], [4, 28, 16], 'green', { split: 'y' });
    m.box('column_rib_e', [13, 4, 15], [13.6, 28, 16], 'green', { split: 'y' });
    // crank case door and the shaft bearing bosses
    m.box('crank_door', [13, 9, 18], [13.6, 18, 28], 'ci', { split: '' });
    for (const y of [10, 16.4]) m.box(`door_hinge_${y}`, [13.6, y, 27.4], [14, y + 1, 28.2], 'bright', { split: '' });
    m.prism('brg_w', 'x', [AY, AZ], 2.6, 2.6, 4, 'ci', { split: '', ends: ['west'] });
    m.prism('brg_e', 'x', [AY, AZ], 2.6, 12, 13.4, 'ci', { split: '', ends: ['east'] });
    // overhung arm forward to the working cylinder
    m.box('arm', [4.4, 30, 3], [11.6, 38, 18], 'green');
    m.box('arm_web', [5.2, 26, 9.6], [10.8, 30, 14], 'green', { split: '' });
  }

  function cylinders(m) {
    // working cylinder (bore 0.24 m, jacket 0.42 m) over the anvil, gland below
    m.cylY('work_gland', W, 2.2, 27.6, 30, 'ci', { bottom: true });
    m.cylY('work_cyl', W, 3.4, 30, 44, 'ci', { bottom: true });
    m.ringY('work_flange', W, 3.8, 0.4, 43, 44, 'ci');
    m.cylY('work_cover', W, 3.6, 44, 45, 'ci', { cap: 'vver/flange' });
    // compressor cylinder (bore 0.3 m) at the back over the crank
    m.cylY('comp_cyl', CP, 4, 30, 42, 'ci', { bottom: true });
    m.ringY('comp_flange', CP, 4.4, 0.4, 41, 42, 'ci');
    m.cylY('comp_cover', CP, 4.2, 42, 43, 'ci', { cap: 'vver/flange' });
    m.cylY('air_inlet', [8, 26.4], 1, 43, 45, 'bright', { cap: 'vver/dark' });
    // valve chest between the cylinders with the two rotary valves and their linkage
    m.box('valve_chest', [5, 38, 9.4], [11, 43.4, 18.6], 'ci', { split: '' });
    for (const [n, z] of [['upper', 12], ['lower', 16]]) {
      m.prism(`valve_${n}`, 'x', [40.6, z], 1.2, 3.6, 12.4, 'bright', { split: '', ends: ['east', 'west'] });
      m.box(`valve_lever_${n}`, [2.8, 40.2, z - 0.4], [3.6, 44.6, z + 0.4], 'st', { split: '' });
    }
    m.box('valve_link', [2.8, 44, 11.4], [3.6, 44.8, 16.6], 'st', { split: '' });
    // sight-feed lubricator on the chest
    m.box('lubricator_stem', [9.4, 43.4, 14.6], [10, 44.6, 15.2], 'br', { split: '' });
    m.cylY('lubricator', [9.7, 14.9], 1, 44.6, 47.2, 'br', { cap: 'material/brass_disc' });
  }

  function controls(m) {
    // treadle along the west of the anvil, pull rod up to the valve link and the hand lever over it
    m.box('treadle', [0.6, 1, 2], [2, 1.8, 12], 'ci', { split: '' });
    m.box('treadle_pivot', [0.4, 1, 11], [2.2, 2.6, 13], 'ci', { split: '' });
    m.box('treadle_pad', [0.4, 1.8, 2], [2.2, 2.2, 4.4], 'chequer', { split: '' });
    m.box('pull_rod', [1, 1.8, 11.6], [1.6, 44.4, 12.2], 'st', { split: 'y' });
    m.box('rod_guide', [1, 20, 12.2], [4.4, 21, 13], 'ci', { split: '' });
    m.box('rod_crank', [1, 44, 11.4], [3.6, 44.8, 12.2], 'st', { split: '' });
    m.box('hand_lever_pivot', [2.4, 34, 3.6], [4.4, 36, 5.6], 'ci', { split: '' });
    m.box('hand_lever', [1.8, 35, 1.4], [2.6, 35.8, 9.4], 'st', { split: '' });
    m.box('hand_grip', [1.6, 34.8, 0.2], [2.8, 36, 1.4], 'wood', { split: '' });
    m.box('lever_link', [2.4, 35.6, 9.4], [3, 40.4, 10], 'st', { split: '' });
  }

  function details(m) {
    m.decal('maker_plate', [5.6, 32, 2.6], [10.4, 35, 3], 'ci', 'north', 'pl.maker');
    m.box('oil_tank', [9.2, 30, 27.4], [12.6, 33, 30.4], 'gw', { split: '' });
    m.box('oil_pipe', [10.6, 18, 29.8], [11.2, 30, 30.4], 'br', { split: 'y' });
    // guard over the crank on the floor behind, a tool rack with tongs and swages on the column side
    m.box('tool_rack', [1.8, 22, 19], [3, 23, 29], 'wood', { split: '' });
    for (const z of [20.4, 23.4, 26.4]) m.box(`tong_${z}`, [1.4, 12, z], [1.8, 22.6, z + 0.5], 'soot', { split: '' });
    m.box('swage', [1, 20, 27.6], [1.8, 22.2, 28.6], 'st', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function ram(m) {
    m.cylY('piston_rod', W, 1.2, 27, 32, 'bright');
    m.box('tup', [4.6, 20, 2.6], [11.4, 27, 9.4], 'st', { split: '' });
    m.box('tup_key', [11.4, 21, 5.4], [12.4, 22, 6.6], 'st', { split: '' });
    m.box('upper_die', [5, 18.6, 3], [11, 20, 9], 'bright', { split: '' });
  }
  function crank(m) {
    m.prism('shaft', 'x', [AY, AZ], 1, 0.4, 16, 'bright', { split: '', ends: ['east', 'west'] });
    m.disc('flywheel', 'x', [AY, AZ], 6.4, 13.6, 15.6, 'ci', 'vver/flange', 'east');
    m.prism('flywheel_rim', 'x', [AY, AZ], 6.6, 13.4, 15.8, 'ci', { split: '' });
    m.prism('coupling', 'x', [AY, AZ], 2.2, 0, 1.4, 'ci', { split: '', ends: ['west'] });
    m.box('coupling_key', [0, AY + 1.6, AZ - 0.5], [1.4, AY + 2.6, AZ + 0.5], 'st', { split: '' });
  }
  const PART = {
    ram: { draw: ram, note: 'translate -y up to 6.2 px (stroke 0.39 m), rest = raised' },
    crank: { draw: crank, note: 'turns about x, axis (y 8, z 24)' },
  };

  const Z0 = -16;
  function model() { const m = mk(); foundation(m); frame(m); cylinders(m); controls(m); details(m); return mk().merge(m, 0, 0, Z0, ''); }
  function partial(name) { const m = mk(); PART[name].draw(m); return mk().merge(m, 0, 0, Z0, ''); }
  function preview() { const m = model(); for (const p of Object.keys(PART)) m.merge(partial(p), 0, 0, 0, ''); return m; }

  window.PHR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'smithy/power_hammer'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'smithy/power_hammer'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
