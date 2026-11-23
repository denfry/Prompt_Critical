// Mannesmann two-high cross-roll piercing mill at real scale (1 block = 1 m, 16 units = 1 m), of the 1900s tube
// works (Mannesmann/Stiefel): two barrel rolls ⌀0.9 m side by side in a cast housing with top screws and the guide
// shoes over and under the pass, driven from the pinion stand on the entry side by two inclined wobbler spindles;
// the billet charged along the entry trough over the pinion stand, the mandrel bar running out 4 m behind the rolls
// to its thrust block, the exit table with roller troughs and the kick-off skids sending the shell east. Pass line
// 1.5 m. After Stiefel, "Die Herstellung nahtloser Rohre" (1910) and Pfeiffer, "Walzwerke" (1925).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   PMR.build(repo); PMR.save(repo); PMR.buildPartial(repo, name); PMR.savePartial(repo, name)   (roll_l, roll_r)
//
// Controller block = (0, 0, 0) at the entry end, the pass running south along z at (x 8, y 24). Footprint x -1..1,
// y 0..2, z 0..7 (model x -16..32, y 0..48, z 0..128). Ports: hot round billet in, north face of (0, 1, 0) at
// (8, 24, 0); pierced shell out, kicked east off the exit table, east face of (1, 1, 5) at (32, 24, 88); Create shaft
// into the pinion stand, west face of (-1, 0, 0) at (-16, 8, 8), axis x.
// Partials (rolls turn about their own axes along z; the 8 deg feed angle is left out):
//   roll_l — west roll, axis (x -1, y 24); roll_r — east roll, axis (x 17, y 24); both saved shifted (0, 0, -24):
//            draw them 24 px south. They turn the same way.
(function () {
  const ID = 'piercing_mill_real';
  const PY = 24, XL = -1, XR = 17, ZR0 = 36, ZR1 = 52;    // pass line, roll axes, roll barrel ends
  const MAT = Object.assign({}, PCVV.MAT, {
    bed: { tex: 'rolling/bed', P: 16 }, hot: { tex: 'rolling/hot', P: 16 }, blue: { tex: 'plant/blue', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/cast_iron' });

  function drive(m) {
    m.box('foundation', [-16, 0, 0], [32, 1, 60], 'conc', { hide: ['down'] });
    // pinion stand under the entry trough, the Create shaft into its west side
    m.box('pinion_stand', [-14, 1, 0], [30, 14, 8], 'blue');
    m.box('pinion_cap', [-13, 14, 0.6], [29, 16, 7.4], 'blue', { split: '' });
    m.prism('input_boss', 'x', [8, 8], 2.6, -16, -14, 'ci', { split: '', ends: ['west'] });
    for (const x of [XL, XR]) m.prism(`pinion_neck_${x}`, 'z', [x, 12], 2.4, 8, 10, 'ci', { split: '', ends: ['south'] });
    // wobbler spindles rising at 22.5 deg from the pinions (z 10, y 12) to the roll necks (z 39, y 24)
    const L = 12 / Math.sin(Math.PI / 8), dz = 12 / Math.tan(Math.PI / 8);
    for (const x of [XL, XR]) {
      m.box(`spindle_${x}`, [x - 1.6, 12 - 1.6, 10], [x + 1.6, 12 + 1.6, 10 + L], 'st',
        { split: '', rot: { axis: 'x', angle: -22.5, origin: [x, 12, 10] } });
      m.box(`spindle_box_${x}`, [x - 2.4, PY - 2.4, 10 + dz - 4], [x + 2.4, PY + 2.4, 10 + dz], 'ci', { split: '' });
    }
    // entry trough over the stand on its posts
    m.box('entry_trough', [5, 20.4, 0], [11, 21.6, 32], 'st', { split: 'z' });
    for (const x of [5, 10.4]) m.box(`entry_side_${x}`, [x, 21.6, 0], [x + 0.6, 23.6, 32], 'st', { split: 'z' });
    for (const z of [12, 24]) m.box(`trough_post_${z}`, [6.6, 1, z], [9.4, 20.4, z + 2], 'st', { split: 'y' });
    m.box('pusher_head', [6.4, 22, 2], [9.6, 26, 4], 'ci', { split: '' });
  }

  function stand(m) {
    // housing posts, caps with the screw-downs, roll chocks, guide shoes over and under the pass
    for (const [n, x0, x1] of [['w', -14, -9], ['e', 25, 30]]) {
      for (const [z0, z1] of [[32, 37], [51, 56]]) m.box(`post_${n}_${z0}`, [x0, 1, z0], [x1, 40, z1], 'ci', { split: 'y' });
      m.box(`post_${n}_tie`, [x0, 1, 37], [x1, 8, 51], 'ci', { split: '' });
    }
    m.box('housing_cap', [-14, 40, 32], [30, 44, 56], 'ci', { split: '' });
    m.box('housing_sill', [-9, 1, 32], [25, 12, 56], 'ci', { split: '' });
    for (const x of [XL, XR]) {
      for (const [n, z0, z1] of [['n', 32, 36], ['s', 52, 56]]) m.box(`chock_${x}_${n}`, [x - 6, PY - 7, z0], [x + 6, PY + 7, z1], 'ci', { split: '' });
      m.cylY(`screw_${x}`, [x, 44], 1.4, 44, 47.4, 'bright');
      m.box(`screw_nut_${x}`, [x - 2.4, 44, 41.6], [x + 2.4, 45.4, 46.4], 'ci', { split: '' });
    }
    m.box('screw_bar', [XL, 46.4, 43.4], [XR, 47.4, 44.6], 'st', { split: '' });
    m.box('shoe_top', [6.6, PY + 2, 38], [9.4, 40, 50], 'st', { split: '' });
    m.box('shoe_bottom', [6.6, 12, 38], [9.4, PY - 2, 50], 'st', { split: '' });
    m.decal('maker_plate', [-13, 26, 31.6], [-10, 29, 32], 'ci', 'north', 'pl.maker');
  }

  function exitSide(m) {
    // mandrel bar out to the thrust block, the piercing point at the rolls' gorge
    m.prism('mandrel_bar', 'z', [8, PY], 0.9, 46, 120, 'bright', { split: '' });
    m.prism('piercing_point', 'z', [8, PY], 1.5, 44, 47, 'bright', { split: '', ends: ['north'] });
    m.box('thrust_block', [2, 1, 120], [14, 30, 128], 'ci', { split: '' });
    m.box('thrust_latch', [5, 27, 117], [11, 30, 120], 'st', { split: '' });
    // exit table: bed, roller troughs on stands, kick-off skids to the east cooling bank
    m.box('exit_bed', [-6, 0, 56], [22, 1, 120], 'conc', { hide: ['down'] });
    for (let z = 60; z < 118; z += 8) {
      m.box(`roll_stand_${z}`, [5, 1, z], [11, 20, z + 2], 'bed', { split: 'y' });
      m.prism(`trough_roll_${z}`, 'x', [21, z + 1], 1.4, 5, 11, 'ci', { split: '', ends: ['east', 'west'] });
    }
    for (const z of [72, 88, 104]) {
      m.box(`skid_${z}`, [11, 18, z], [32, 19, z + 1.6], 'st', { split: 'x' });
      m.box(`skid_post_${z}`, [28, 1, z], [29.6, 18, z + 1.6], 'st', { split: 'y' });
    }
    m.box('kicker_shaft', [10, 17, 64], [11, 18, 112], 'bright', { split: 'z' });
    // a pierced shell lying on the skids
    m.prism('shell_out', 'z', [24, 20.4], 1.6, 66, 112, 'hot', { split: '', ends: ['north', 'south'] });
  }

  // ------------------------------------------------------------------ partials
  function roll(m, x) {
    // barrel: entry cone, gorge, exit cone, necks
    const c = [x, PY];
    m.prism('neck_n', 'z', c, 3, ZR0 - 4, ZR0, 'bright', { split: '' });
    m.prism('entry_cone_0', 'z', c, 5.6, ZR0, ZR0 + 3, 'st', { split: '', ends: ['north'] });
    m.prism('entry_cone_1', 'z', c, 6.6, ZR0 + 3, ZR0 + 6, 'st', { split: '' });
    m.prism('gorge', 'z', c, 7.4, ZR0 + 6, ZR0 + 10, 'st', { split: '' });
    m.prism('exit_cone', 'z', c, 6.8, ZR0 + 10, ZR1, 'st', { split: '', ends: ['south'] });
    m.prism('neck_s', 'z', c, 3, ZR1, ZR1 + 4, 'bright', { split: '' });
  }
  const PART = {
    roll_l: { draw: (m) => roll(m, XL), shift: [0, 0, -24] },
    roll_r: { draw: (m) => roll(m, XR), shift: [0, 0, -24] },
  };

  function model() { const m = mk(); drive(m); stand(m); exitSide(m); return m; }
  function partial(name) { const s = mk(); PART[name].draw(s); const [dx, dy, dz] = PART[name].shift; return mk().merge(s, dx, dy, dz, ''); }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.PMR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'rolling/piercing_mill'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'rolling/piercing_mill'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
