// BOR-60 (NIIAR Dimitrovgrad, 60 MWt) sodium-cooled loop-type fast reactor at real scale (1 block = 1 m, 16 px =
// 1 m): the guard vessel in the pit round the 1.4 m stainless vessel, the four lagged DN 250 sodium nozzles (two
// outlets to the intermediate heat exchangers, two inlets from the pumps), the top shield deck with the lead-bismuth
// seal rings, the large rotating plug carrying the refuelling mechanism and the small eccentric plug carrying the
// seven control rod drives, the argon cover gas line, the cable tray, the deck rail and ladder.
// Sheet: docs/research/2026-11-24-reactor-prototypes/bor60.md (disk only; vessel ∅, core, plugs, drives on the small
// plug and the two outlets sourced, heights and the rest estimated).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   BOR.build(repo); /* wait */ BOR.save(repo)
//   for (const p of BOR.parts()) { BOR.buildPartial(repo, p); BOR.savePartial(repo, p) }
//   BOR.preview(repo)
//
// Controller block = (0, 0, 0) under the axis in the reactor pit, front north. Footprint x -2..2, y 0..8, z -2..2
// (5 x 9 x 5). Vessel axis vertical through (8, y, 8). Ports at y 77: sodium out loop 1 on the west face of
// (-2, 4, 0) at (-32, 77, 8); sodium out loop 2 on the east face of (2, 4, 0) at (48, 77, 8); sodium in loop 1 on the
// north face of (0, 4, -2) at (8, 77, -32); sodium in loop 2 on the south face of (0, 4, 2) at (8, 77, 48); argon on
// the top face of (1, 8, 0) at (30, 144, 8); control cables on the top face of (0, 8, 0) at (8, 144, 8).
// Partials (bor60_reactor_real/partial/*):
// - large_plug: the large rotating plug with the refuelling mechanism; turns about y at (8, 8); stored -104 y, draw
//   at +104 y.
// - small_plug: the small plug with the seven drives; turns about y at (12.4, 8) (eccentric) and is carried round by
//   the large plug; stored -104 y, draw at +104 y.
// - rods: drive position indicators over the drive caps (on the small plug); slide up +y 0..8 px; stored -112 y,
//   draw at +112 y.
(function () {
  const C = [8, 8], SP = [12.4, 8], DP = 3.6;

  function drives() {
    const out = [[SP[0], SP[1]]];
    for (let k = 0; k < 6; k++) { const a = k * Math.PI / 3; out.push([SP[0] + DP * Math.cos(a), SP[1] + DP * Math.sin(a)]); }
    return out;
  }

  function model() {
    const m = PCVV.mk();
    // guard vessel on four legs, the reactor vessel inside it above
    for (const a of [45, 135, 225, 315]) m.postY(`leg_${a}`, C, 13, a, 3, 0, 8, 'pnt', { split: '' });
    m.headDown('guard_bot', C, 15.2, 12, 8, 4, 'pnt');
    m.cylY('guard', C, 15.2, 12, 80, 'pnt');
    m.cylY('guard_lip', C, 16.4, 80, 82, 'forged', { cap: 'vver/flange' });
    m.cylY('vessel_up', C, 11.2, 82, 96, 'clad');
    for (const y of [30, 55]) m.ringY(`guard_band_${y}`, C, 15.6, 0.8, y, y + 1.2, 'pnt');
    // the four sodium nozzles, lagged, through the guard vessel: outlets red band, inlets green band
    const o = { nuts: 8, boss: 'felt', pipe: 'felt' };
    m.nozzle('na_out_1', 'x', [77, 8], -6, -32, 4.4, 3.2, 'gs', o);
    m.nozzle('na_out_2', 'x', [77, 8], 22, 48, 4.4, 3.2, 'gs', o);
    m.nozzle('na_in_1', 'z', [8, 77], -6, -32, 4.4, 3.2, 'gw', o);
    m.nozzle('na_in_2', 'z', [8, 77], 22, 48, 4.4, 3.2, 'gw', o);
    // heat-tracing junction boxes on the lagging
    for (const [x, z] of [[-22, 3], [36, 3], [3, -22], [3, 36]]) m.box(`trace_${x}_${z}`, [x - 1.2, 80, z - 1.2], [x + 1.2, 82.4, z + 1.2], 'yel', { split: '' });
    // top shield deck with the two Pb-Bi seal troughs (round the large plug and round the small plug)
    m.cylY('deck', C, 27, 96, 104, 'forged', { cap: 'vver/flange', bottom: true });
    m.ringY('seal_large', C, 19, 1.4, 104, 106, 'dark');
    m.box('deck_edge_n', [-14, 104, -19], [30, 105, -18], 'yel', { split: '' });
    m.railY('deck_rail', C, 26.4, 104, 'yel');
    // argon cover gas line and the cable tray up from the deck
    m.cylY('argon', [30, 8], 1, 104, 143.9, 'yel');
    m.box('argon_valve', [28.6, 112, 6.6], [31.4, 115, 9.4], 'ci', { split: '' });
    m.box('tray_post', [-14, 104, 6], [-12, 142, 10], 'ci', { split: 'y' });
    m.box('tray', [-14, 140, 6], [10, 141.6, 10], 'ci', { split: '' });
    m.box('tray_up', [6, 141.6, 6], [10, 143.9, 10], 'ci', { split: '' });
    // ladder from the pit floor to the deck on the north side
    m.ladderZ('ladder', 2, 10, -26, -1, 0, 104, 'yel', 0);
    m.decal('sign', [3, 86, -3.6], [13, 94, -3], 'clad', 'north', 'pl.sign');
    m.decal('maker', [-4, 98, -18.9], [2, 101.6, -18.6], 'forged', 'north', 'pl.maker');
    return m;
  }

  function largePlug() {
    const m = PCVV.mk();
    m.cylY('large_plug', C, 17.6, 104, 107, 'forged', { cap: 'vver/flange' });
    // refuelling mechanism stand on the side away from the small plug
    m.cylY('rm_tube', [0, 8], 2.6, 107, 126, 'st');
    m.box('rm_drive', [-4, 126, 4], [4, 132, 12], 'drv', { split: '' });
    m.box('rm_motor', [-3, 132, 5], [3, 134, 11], 'motor', { split: '' });
    for (const a of [0, 180]) m.radial(`lift_${a}`, C, 14, 16, a, 1.4, 107, 109, 'yel');
    return m;
  }
  function smallPlug() {
    const m = PCVV.mk();
    m.cylY('small_plug', SP, 8.8, 107, 109, 'forged', { cap: 'vver/flange' });
    m.ringY('seal_small', SP, 9.4, 0.8, 107, 108, 'dark');
    drives().forEach(([x, z], i) => {
      m.box(`crd_${i}`, [x - 1, 109, z - 1], [x + 1, 128, z + 1], 'st', { split: '' });
      m.box(`crd_motor_${i}`, [x - 1.4, 122, z - 1.4], [x + 1.4, 127, z + 1.4], 'motor', { split: '' });
      m.box(`crd_cap_${i}`, [x - 1.2, 128, z - 1.2], [x + 1.2, 130, z + 1.2], 'pnt', { split: '' });
    });
    return m;
  }
  function rods() {
    const m = PCVV.mk('material/bright_steel');
    drives().forEach(([x, z], i) => {
      m.box(`rod_${i}`, [x - 0.4, 130, z - 0.4], [x + 0.4, 136, z + 0.4], 'bright', { split: '' });
      m.box(`rod_tip_${i}`, [x - 0.6, 136, z - 0.6], [x + 0.6, 137, z + 0.6], 'red', { split: '' });
    });
    return m;
  }

  const PART = { large_plug: { draw: largePlug, shift: [0, -104, 0] }, small_plug: { draw: smallPlug, shift: [0, -104, 0] }, rods: { draw: rods, shift: [0, -112, 0] } };
  const partial = (p) => { const d = PART[p]; const [dx, dy, dz] = d.shift; return PCVV.mk().merge(d.draw(), dx, dy, dz, ''); };
  const ID = 'bor60_reactor_real';
  window.BOR = {
    ID, model, partial, drives,
    parts: () => Object.keys(PART),
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'core/bor60'),
    buildPartial: (root, p) => { const m = partial(p); PCART.realize(root, m, ID + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, p) => PCVV.savePartial(root, ID, p, 'core/bor60'),
    preview: (root) => { const m = model(); for (const d of Object.values(PART)) m.merge(d.draw(), 0, 0, 0, 'p'); PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
