// Blast furnace dust catcher at real scale (1 block = 1 m, 16 units = 1 m): the dry gravity catcher of a 1900s
// furnace of some 250 m³: a riveted shell ⌀2.9 m and 5 m high on four columns, the dust cone below it to the dust
// valve, the domed top with the downcomer coming in from above and the central bell pipe, the clean-gas main out of
// the side, a platform round the top with its rail and a ladder up a column, the dust valve with its lever and the
// chute over a wagon track. Sizes after Johnson, "Blast-Furnace Construction in America" (1917): catchers 8–12 ft
// for furnaces of 300–500 t a day.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   DCR.build(repo); DCR.save(repo); DCR.buildPartial(repo, 'valve'); DCR.savePartial(repo, 'valve')
//
// Controller block = (0, 0, 0) under the axis, front north; axis (8, ., 8). Footprint x -1..1, y 0..9, z -1..1
// (model x -16..32, y 0..160, z -16..32). Ports: dirty gas in at the downcomer flange, up face of (0, 9, 0), at
// (8, 159.6, 8); clean gas out of the side main, south face of (0, 8, 1), at (8, 136, 32); dust out of the valve chute
// onto the north of (0, 0, -1), lip (8, 20, -16).
// Partial valve (dust valve flap and its lever): swings about x through (y 33, z 8), 0 = shut, open 60 deg down to
// the north; saved shifted (0, -16, 0): draw it 16 px up.
(function () {
  const ID = 'dust_catcher_real';
  const C = [8, 8], R = 23, YB = 64, YT = 144;             // axis, shell radius, shell bottom and top
  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'fire_tube_boiler/plate' });

  function body(m) {
    for (const a of [45, 135, 225, 315]) {
      m.postY(`column_${a}`, C, R - 1, a, 2, 0, YB + 8, 'st');
      m.postY(`col_base_${a}`, C, R - 1, a, 3.4, 0, 1.2, 'conc');
    }
    // ring girder and the cone
    m.ringY('ring_girder', C, R + 0.6, 1.4, YB - 2, YB + 2, 'st');
    const n = 8;
    for (let i = 0; i < n; i++) {
      const r = R - (R - 4) * (i + 1) / n, y1 = YB - i * 3, y0 = y1 - 3;
      m.cylY(`cone_${i}`, C, r, y0, y1, 'plate', { bottom: i === n - 1 });
    }
    m.cylY('shell', C, R, YB, YT, 'plate');
    for (const y of [YB + 0.6, 96, 120, YT - 0.6]) m.ringY(`seam_${y}`, C, R + 0.3, 0.5, y - 0.6, y + 0.6, 'rivets');
    // domed top in three steps, the downcomer coming in from above with its flange
    m.cylY('dome_0', C, R - 3, YT, YT + 3, 'plate');
    m.cylY('dome_1', C, R - 9, YT + 3, YT + 6, 'plate');
    m.cylY('dome_2', C, R - 15, YT + 6, YT + 8, 'plate', { cap: 'fire_tube_boiler/end_plate' });
    m.cylY('downcomer', C, 6, YT + 8, 158.6, 'plate');
    m.cylY('downcomer_flange', C, 7.2, 158.6, 159.6, 'st', { cap: 'vver/flange' });
    m.box('bleeder', [C[0] + 9, YT + 6, C[1] - 1.6], [C[0] + 12, YT + 12, C[1] + 1.6], 'st', { split: '' });
    // clean-gas main out of the south side
    m.prism('gas_main', 'z', [C[0], 136], 5, C[1] + R - 1, 31, 'plate', { split: '' });
    m.prism('gas_main_flange', 'z', [C[0], 136], 6, 31, 32, 'st', { split: '', ends: ['south'] });
    // manholes
    m.radial('manhole_n', C, R, R + 1, 270, 4, 72, 78, 'st');
    m.radial('manhole_e', C, R, R + 1, 0, 4, 126, 132, 'st');
  }

  function access(m) {
    // platform round the top on brackets, rail, the ladder up the north-west column
    m.ringY('platform', C, R + 1, 1.2, YT - 1, YT, 'grate');
    for (const a of [0, 90, 180, 270]) m.radial(`bracket_${a}`, C, R, R + 0.6, a + 22.5, 1, YT - 3, YT - 1, 'st');
    m.ringY('rail_top', C, R - 0.6, 0.6, YT + 14.6, YT + 15.2, 'yel');
    for (const a of [22.5, 67.5, 112.5, 157.5, 202.5, 247.5, 292.5, 337.5]) m.postY(`rail_post_${a}`, C, R - 0.3, a, 0.6, YT, YT + 15.2, 'yel');
    m.box('ladder_l', [-15.4, 0, -6], [-14.8, YT, -5.4], 'st', { split: 'y' });
    m.box('ladder_r', [-15.4, 0, -1.6], [-14.8, YT, -1], 'st', { split: 'y' });
    for (let y = 4; y < YT; y += 4.8) m.box(`rung_${Math.round(y)}`, [-15.4, y, -5.4], [-14.8, y + 0.5, -1.6], 'st', { split: '' });
    m.decal('plate_no', [4, 100, C[1] - R - 0.6], [12, 104.8, C[1] - R - 0.4], 'st', 'north', 'pl.maker');
  }

  function dustValve(m) {
    // valve body under the cone, chute out north over the wagon track
    m.cylY('valve_body', C, 4, 34, 40, 'st', { bottom: true });
    m.box('chute', [3, 20, -16], [13, 21, 6], 'st', { split: 'z' });
    m.box('chute_w', [3, 21, -16], [4, 24, 6], 'st', { split: 'z' });
    m.box('chute_e', [12, 21, -16], [13, 24, 6], 'st', { split: 'z' });
    m.box('chute_hanger', [7.4, 24, 2], [8.6, 34, 3], 'st', { split: '' });
    // wagon track under the chute (standard gauge rails to the west and east)
    for (const x of [-4, 19.6]) m.box(`rail_${x}`, [x, 0, -16], [x + 0.8, 1, 32], 'bright', { split: 'z' });
    for (let z = -14; z < 32; z += 6) m.box(`sleeper_${z}`, [-8, 0, z], [24, 0.4, z + 2], 'wood', { split: '' });
  }

  function valve(m) {
    m.box('flap', [4.6, 32.4, 4.6], [11.4, 33.6, 11.4], 'ci', { split: '' });
    m.box('lever', [12, 32.6, 7.6], [24, 33.4, 8.4], 'st', { split: '' });
    m.box('counterweight', [22, 30.6, 6.6], [25, 33.4, 9.4], 'ci', { split: '' });
  }
  const PART = { valve: { draw: valve, shift: [0, -16, 0] } };

  function model() { const m = mk(); body(m); access(m); dustValve(m); return m; }
  function partial(name) { const s = mk(); PART[name].draw(s); const [dx, dy, dz] = PART[name].shift; return mk().merge(s, dx, dy, dz, ''); }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.DCR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'ironmaking/dust_catcher'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'ironmaking/dust_catcher'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
