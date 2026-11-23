// Bloomery at real scale (1 block = 1 m, 16 units = 1 m): a clay shaft furnace of the late European kind, 1.7 m
// high, ⌀1.0 m at the base tapering to 0.7 m at the mouth, inner shaft ⌀0.3 m, on a dry-stone footing; the tap arch
// low in front, the clay tuyere on the west fed by a pair of leather pot-and-board bellows on a log bed, worked by a
// rocking beam on two forked posts. A charcoal basket, an ore heap and a bloom on the slag by the arch. Dimensions
// after experimental bloomeries (Pleiner, "Iron in Archaeology"; Sauder & Williams 2002: shaft 1.2–1.8 m, inner
// 0.25–0.35 m, tuyere 0.2–0.25 m above the floor).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   BLMR.build(repo); /* wait */ BLMR.save(repo)                  static model: wholemodels/bloomery_real.json
//   BLMR.buildPartial(repo, name); BLMR.savePartial(repo, name)   for glow, bellows
//
// Controller block = (0, 0, 0), the furnace cell, front north; furnace axis (8, ., 8). Footprint x -1..0, y 0..1,
// z 0..0 (model x -16..16, y 0..32, z 0..16). Charging: the mouth on top at y 27.2 (from above (0, 1, 0)). Bloom and
// slag out: tap arch on the north face of (0, 0, 0), y 1.6..5.6. Bellows cell (-1, 0, 0).
// Partials (model px, no shift):
//   glow    — full-bright coals in the mouth and behind the tap arch; show while lit.
//   bellows — the two top boards with their handles; translate -y by up to 1.6 px, the two boards in turn (the
//             wiring can draw the partial twice, mirrored on z about 8, or move it as one).
(function () {
  const ID = 'bloomery_real';
  const C = [8, 8], Y0 = 2, YT = 27.2, R0 = 7.4, R1 = 5.4;
  const MAT = Object.assign({}, PCVV.MAT, {
    daub: { tex: 'smithy/daub', P: 32 }, leather: { tex: 'smithy/leather', P: 16 }, bark: { tex: 'smithy/bark', P: 16 },
    coals: { tex: 'smithy/coals', P: 16 }, dead: { tex: 'smithy/coals_dead', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'smithy/daub' });

  function furnace(m) {
    m.cylY('footing', C, R0 + 0.2, 0, Y0, 'ashlar', { bottom: true });
    // terraced clay shaft: 10 courses tapering from R0 to R1
    const n = 10, h = (YT - Y0) / n;
    for (let i = 0; i < n; i++) {
      const r = R0 - (R0 - R1) * i / (n - 1);
      m.cylY(`shaft_${i}`, C, r, Y0 + i * h, Y0 + (i + 1) * h, 'daub', i === n - 1 ? { cap: 'vver/dark' } : {});
    }
    m.ringY('mouth_rim', C, R1 + 0.4, 1.4, YT - 0.4, YT + 0.6, 'daub');
    // tap arch: a stone lintel over a clay-plugged opening, the slag tongue and a bloom on it in front
    m.box('arch_jamb_w', [4.2, Y0, 0.4], [5.4, 6.4, 1.6], 'ashlar', { split: '' });
    m.box('arch_jamb_e', [10.6, Y0, 0.4], [11.8, 6.4, 1.6], 'ashlar', { split: '' });
    m.box('arch_lintel', [4.2, 6.4, 0.2], [11.8, 7.8, 1.6], 'ashlar', { split: '' });
    m.box('tap_mouth', [5.4, Y0 - 0.4, 0.9], [10.6, 5.6, 1], 'dark', { split: '' });
    m.box('slag_tongue', [5, 0, 0], [11, 0.8, 1.4], 'soot', { split: '' });
    m.box('bloom', [12.4, 0, 0.6], [15, 1.8, 3.2], 'soot', { split: '' });
    // tuyere: clay nozzle through the west wall at 0.25 m
    m.prism('tuyere', 'x', [5.2, 8], 1.2, -2.4, 0.8, 'daub', { split: '', ends: ['west'] });
    // binding hoops of withy round the upper shaft
    for (const y of [14, 21]) m.ringY(`withy_${y}`, C, R0 - (R0 - R1) * (y - Y0) / (YT - Y0) + 0.3, 0.4, y, y + 0.6, 'bark');
  }

  function bellows(m) {
    // log bed and the two pots (boards with leather walls), nozzles joined into the tuyere
    m.box('log_bed', [-15, 0, 2], [-2.4, 1.4, 14], 'bark', { split: '' });
    for (const [n, z0, z1] of [['n', 2.6, 7.4], ['s', 8.6, 13.4]]) {
      m.box(`bottom_board_${n}`, [-14.6, 1.4, z0], [-3.6, 2, z1], 'wood', { split: '' });
      m.box(`leather_${n}`, [-14, 2, z0 + 0.4], [-5.4, 4.2, z1 - 0.4], 'leather', { split: '' });
      m.box(`leather_neck_${n}`, [-5.4, 2, z0 + 1.2], [-4, 3.6, z1 - 1.2], 'leather', { split: '' });
      m.box(`nozzle_${n}`, [-4, 2.6, (z0 + z1) / 2 - 0.5], [-2.4, 3.4, (z0 + z1) / 2 + 0.5], 'ci', { split: '' });
    }
    m.box('nozzle_joint', [-2.6, 2.6, 4.4], [-1.8, 5.6, 11.6], 'daub', { split: '' });
    // rocking beam on two forked posts, cords down to the board handles
    for (const z of [0.6, 14.4]) {
      m.box(`post_${z}`, [-9.6, 0, z], [-8.4, 18, z + 1], 'bark', { split: 'y' });
      m.box(`fork_${z}`, [-9.8, 18, z - 0.2], [-8.2, 19.4, z + 1.2], 'bark', { split: '' });
    }
    m.box('axle', [-9.4, 18.6, 0.4], [-8.6, 19.4, 15.6], 'wood', { split: '' });
    m.box('beam', [-15.4, 19.4, 7.4], [-2.6, 20.6, 8.6], 'wood', { split: '' });
    m.box('cord_w', [-13.6, 7.2, 4.8], [-13.2, 19.4, 5.2], 'leather', { split: '' });
    m.box('cord_e', [-4.6, 7.2, 10.8], [-4.2, 19.4, 11.2], 'leather', { split: '' });
  }

  function stores(m) {
    // charcoal basket and ore heap by the bellows; a wooden stand to reach the mouth on the south
    m.cylY('basket', [-1, 1.8], 1.6, 0, 3.4, 'bark', { bottom: true });
    m.cylY('basket_coal', [-1, 1.8], 1.3, 3.4, 3.8, 'dead', { cap: 'smithy/coals_dead' });
    m.box('ore_heap', [12, 0, 11.6], [15.6, 1.6, 15.6], 'soot', { split: '' });
    m.box('ore_top', [12.8, 1.6, 12.4], [14.8, 2.6, 14.8], 'soot', { split: '' });
    m.box('stand', [1, 0, 13.6], [6, 8, 16], 'wood', { split: '' });
    m.box('stand_step', [1, 0, 11.6], [6, 4, 13.6], 'wood', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function glow(m) {
    m.box('glow_mouth', [C[0] - 2.4, YT - 0.3, C[1] - 2.4], [C[0] + 2.4, YT - 0.2, C[1] + 2.4], 'coals', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('glow_tap', [5.6, Y0 - 0.3, 0.8], [10.4, 5.4, 0.9], 'coals', { split: '', hide: ['up', 'down', 'south', 'east', 'west'] });
  }
  function boards(m) {
    for (const [n, z0, z1] of [['n', 2.6, 7.4], ['s', 8.6, 13.4]]) {
      m.box(`top_board_${n}`, [-14.6, 4.2, z0], [-4.6, 4.8, z1], 'wood', { split: '' });
      m.box(`handle_${n}`, [-14, 4.8, (z0 + z1) / 2 - 0.4], [-13, 7.2, (z0 + z1) / 2 + 0.4], 'wood', { split: '' });
    }
  }
  const PART = {
    glow: { draw: glow, note: 'full-bright while lit' },
    bellows: { draw: boards, note: 'translate -y up to 1.6 px, boards alternate' },
  };

  function model() { const m = mk(); furnace(m); bellows(m); stores(m); return m; }
  function partial(name) { const m = mk(); PART[name].draw(m); return m; }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.BLMR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'smithy/bloomery'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'smithy/bloomery'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
