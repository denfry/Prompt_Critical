// Boiler-house chimney at real scale (1 block = 1 m), built from stacked 3 x 3 x 3 segments: a square brick
// pedestal with the flue inlet arch on the north face (base), a round brick shaft with a raised band at every
// segment joint (shaft), and the oversailing corbelled cap with an iron
// band, a stone coping and the lightning conductor (cap). Flue 1.8 m inside at the top. Dimensions and sources:
// docs/research/chimney_stack-dimensions.md (disk only).
//
// Each segment is one Java block model about its middle block (x, y, z -16..32). Controller = the middle block.
// The flue inlet of the base is the north face of the base's cell (0, -1, -1), opening x 0..16, y -16..0, which meets
// the Lancashire boiler's flue port (south face of boiler cell (0, 0, 9)) when the base controller sits at boiler (0, 1, 11).
//
// Load after pcart.js, pcround.js and vverkit.js, then CHIMNEY_STACK.build(repo, part) /* wait */
// CHIMNEY_STACK.save(repo, part), part = 'base' | 'shaft' | 'cap'.
(function () {
  const C = [8, 8], RB = 20, RT = 20, RI = 14.4;     // shaft outer radius, flue radius at the top
  const brick = { tex: 'material/brick', seed: 0 };
  // prismY maps its side texture inside one block: cut a tall brick course on the block grid
  function course(m, name, r, y0, y1, seed) {
    let k = 0;
    for (let a = y0; a < y1 - 1e-6; ) {
      const b = Math.min(y1, (Math.floor(a / 16 + 1e-6) + 1) * 16);
      // its side UVs come from y, so build the piece inside the block 0..16 and lift it into place
      const off = Math.floor(a / 16 + 1e-6) * 16, n0 = m.els.length;
      m.prismY(`${name}_${k++}`, C, r, a - off, b - off, 'brick', { tex: brick.tex, seed: seed + k * 3 });
      for (const e of m.els.slice(n0)) {
        e.from = [e.from[0], e.from[1] + off, e.from[2]]; e.to = [e.to[0], e.to[1] + off, e.to[2]];
        if (e.rot) e.rot = Object.assign({}, e.rot, { origin: [e.rot.origin[0], e.rot.origin[1] + off, e.rot.origin[2]] });
      }
      a = b;
    }
  }

  function shaft(m, y0, y1, band) {
    // two courses with different brick bonds; the band at each joint marks the segment
    const mid = (y0 + y1) / 2;
    course(m, 'shaft_lo', RB, y0, mid, 1);
    course(m, 'shaft_hi', RB, mid, y1, 5);
    if (band) m.ringY('band', C, RB + 1, 1.6, y0, y0 + 2, 'brick');
  }

  function base() {
    const m = PCVV.mk('material/brick');
    // ashlar plinth and the square brick pedestal round the inlet arch (north face, x 0..16, y -16..0: the bottom row,
    // at floor level to meet the Lancashire flue); the plinth stops either side of the opening
    m.box('plinth_w', [-16, -16, -16], [0, -12, 32], 'ashlar');
    m.box('plinth_e', [16, -16, -16], [32, -12, 32], 'ashlar');
    m.box('plinth_back', [0, -16, -10], [16, -12, 32], 'ashlar');
    m.box('ped_w', [-14, -12, -14], [0, 18, 30], 'brick');
    m.box('ped_e', [16, -12, -14], [30, 18, 30], 'brick');
    m.box('ped_high', [0, 0, -14], [16, 18, 30], 'brick');
    m.box('ped_back', [0, -12, -10], [16, 0, 30], 'brick');
    m.box('inlet_soot', [0, -16, -10.2], [16, 0, -10], 'soot', { split: '' });
    m.box('inlet_sill', [-1, -16, -15], [17, -15.5, -14], 'ashlar', { split: '' });
    m.box('inlet_lintel', [-2, 0, -15], [18, 3, -14], 'ashlar', { split: '' });
    // pedestal cornice and the start of the round shaft
    m.box('cornice', [-15, 18, -15], [31, 21, 31], 'ashlar');
    shaft(m, 21, 32, false);
    // soot door for sweeping the flue, west face
    m.box('door', [-14.6, -8, 2], [-14, 2, 14], 'ci', { split: '' });
    m.box('door_bar', [-15, -4, 1], [-14.6, -2.8, 15], 'st', { split: '' });
    m.decal('plate', [-1, 20, -15.2], [5, 23, -15], 'ashlar', 'north', 'pl.maker');
    return m;
  }

  function shaftSeg() {
    const m = PCVV.mk('material/brick');
    shaft(m, -16, 32, true);
    // iron ladder dogs up the east side, one every 0.5 m
    for (let y = -14; y < 32; y += 8) m.box(`dog_${y}`, [8 + RB - 0.2, y, 7], [8 + RB + 1.4, y + 0.6, 9], 'st', { split: '' });
    return m;
  }

  function cap() {
    const m = PCVV.mk('material/brick');
    shaft(m, -16, 12, true);
    // corbelled oversail: three courses stepping out, an iron band round the neck, stone coping
    course(m, 'corbel_1', RT + 1, 12, 15, 9);
    m.ringY('corbel_1_soffit', C, RT + 1, RT + 1 - RB + 0.2, 12, 12 + 0.4, 'brick');
    course(m, 'corbel_2', RT + 2.3, 15, 18, 13);
    m.ringY('corbel_2_soffit', C, RT + 2.3, RT + 2.3 - RB + 0.2, 15, 15 + 0.4, 'brick');
    course(m, 'corbel_3', RT + 3.6, 18, 22, 17);
    m.ringY('corbel_3_soffit', C, RT + 3.6, RT + 3.6 - RB + 0.2, 18, 18 + 0.4, 'brick');
    m.ringY('iron_band', C, RT + 0.8, 0.8, 8, 9.5, 'ci');
    m.ringY('coping', C, RT + 4, RT + 4 - RI, 22, 24, 'ashlar');
    // the flue seen from above: soot inside the coping
    m.prismY('flue', C, RI, 20, 22.6, 'soot', { cap: 'material/soot' });
    // lightning conductor: copper tape up the side to a rod over the coping
    m.box('conductor_tape', [8 + RT + 0.3, -16, 7.6], [8 + RT + 0.8, 18, 8.4], 'br', { split: 'y' });
    m.box('conductor_rod', [8 + RT + 2.6, 24, 7.6], [8 + RT + 3.4, 32, 8.4], 'br', { split: '' });
    return m;
  }

  const PARTS = { base, shaft: shaftSeg, cap };
  const id = (p) => (p === 'shaft' ? 'chimney_stack' : 'chimney_stack_' + p);
  window.CHIMNEY_STACK = {
    PARTS,
    build: (root, p) => { const m = PARTS[p](); PCART.realize(root, m, id(p)); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, p) => PCART.save(root, 'boilers', id(p)),
    // a 5-segment preview stack for the scale shot (never saved)
    preview: (root, n) => {
      const m = base();
      for (let i = 1; i < n - 1; i++) m.merge(shaftSeg(), 0, 48 * i, 0, `s${i}_`);
      m.merge(cap(), 0, 48 * (n - 1), 0, 'cap_');
      PCART.realize(root, m, 'chimney_preview');
      return m.els.length;
    },
  };
})();
