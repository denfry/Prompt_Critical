// Brick chimney stack, hand-authored for Blockbench (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/chimney.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CHY.paint('<repo>')
//   await CHY.exportAll('<repo>')
//
// ChimneyBlock picks the part from its neighbours: `base` has no chimney under it, `top` none over it, `middle`
// both. On the Lancashire boiler the base stands on the cast-iron flange of the brick uptake over the rear
// segment, so the base is open underneath: a square brick plinth with a stone weathering course, corbelled into
// the round stack. Bricks run 2 units to the course (2 px per unit, as on the boiler setting). The stack is
// 12 units across: about 0.75 m, a small works chimney for one hand-fired boiler.
//
// Real fittings that every block carries: iron step irons up the west side for the sweep, the copper lightning
// conductor tape down the east side in bronze holdfasts, and on the shaft an iron band against cracking. The
// air terminal stands at the rim, clamped to the cast-iron cap, not over the flue.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/chimney';
  const T = {};
  const R = 6;            // stack radius
  const C = [8, 8];       // stack centre (x, z)

  // Common red stock brick in lime mortar, half bond, 8 px bricks on 4 px courses; a few overburnt headers and
  // pale salmon bricks, rain streaks down the joints, salts bloomed out of the mortar here and there.
  // `soot` blackens it upward (0 = clean, 1 = the top block under the cap).
  const brick = (seed, soot) => () => {
    const im = img(32, 32), r = rng(seed);
    const streak = [];
    for (let x = 0; x < 32; x++) streak.push(r() < 0.18 ? -10 - r() * 10 : 0);
    for (let row = 0; row < 8; row++) {
      const off = (row % 2) * 4;
      for (let k = 0; k < 5; k++) {
        const v = r();
        let c = add([142, 74, 54], (r() - 0.5) * 22);
        if (v < 0.12) c = add([92, 48, 40], (r() - 0.5) * 10);
        else if (v > 0.9) c = add([170, 104, 76], (r() - 0.5) * 12);
        for (let j = 0; j < 4; j++) for (let i = 0; i < 8; i++) {
          const x = ((off + k * 8 - 8 + i) % 32 + 32) % 32, y = row * 4 + j;
          let p;
          if (j === 3 || i === 7) {
            p = add([146, 138, 124], (r() - 0.5) * 14);
            if (r() < 0.05) p = [196, 192, 182];                       // efflorescence
          } else {
            p = add(c, (r() - 0.5) * 12);
            if (j === 0) p = add(p, 6);                                 // arris catching the light
            if (j === 2) p = add(p, -6);
          }
          put(im, x, y, add(p, streak[x]));
        }
      }
    }
    if (soot) {
      for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
        const up = 1 - y / 31, s = Math.min(1, soot * (0.3 + 0.7 * up) * (0.75 + r() * 0.5) + (streak[x] ? 0.1 : 0));
        const i = (y * 32 + x) * 4;
        put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [30, 27, 25], s));
      }
    }
    return im;
  };
  T.brick = brick(601, 0);
  T.brick_top = brick(602, 0.9);
  // The flue seen from above: sooted iron coping at the rim, a lip of black brick, then the dark of the flue.
  T.mouth = () => {
    const im = img(32, 32), r = rng(611);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c;
      if (d > 0.95) c = add([70, 66, 62], (r() - 0.5) * 10);
      else if (d > 0.8) c = add([44, 41, 38], (r() - 0.5) * 12);
      else if (d > 0.72) c = add([34, 30, 28], (r() - 0.5) * 10);
      else c = mix([10, 9, 9], [30, 26, 24], Math.pow(d / 0.72, 3));
      put(im, x, y, c);
    }
    return im;
  };
  // Copper conductor tape, weathered brown with verdigris where the rain runs off.
  T.copper = () => {
    const im = img(16, 16), r = rng(621);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = add([150, 92, 58], (r() - 0.5) * 18);
      if (r() < 0.22) c = mix(c, [86, 150, 124], 0.6 + r() * 0.3);
      put(im, x, y, c);
    }
    return im;
  };

  const MAT = {
    cbrick: { tex: 'chimney/brick', P: 16 }, ctop: { tex: 'chimney/brick_top', P: 16 }, cu: { tex: 'chimney/copper', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // ------------------------------------------------------------------ fittings
  // Step iron: a U of 25 mm round bar let into the brickwork, 3 units wide, standing 1.2 units off the face.
  function stepIron(m, y, face) {
    m.box(`step_${y}_bar`, [face - 1.6, y, 6.3], [face - 1.2, y + 0.4, 9.7], 'ci', { split: '' });
    for (const z of [6.3, 9.3]) m.box(`step_${y}_leg_${z}`, [face - 1.2, y, z], [face + 0.3, y + 0.4, z + 0.4], 'ci', { split: '' });
  }
  // Conductor tape on the east face, standing off it on bronze holdfasts so it clears the iron bands.
  function tape(m, y0, y1, face, clips, off = 0.35) {
    m.box(`tape_${y0}`, [face + off, y0, 7.6], [face + off + 0.25, y1, 8.4], 'cu', { split: '' });
    for (const y of clips) m.box(`holdfast_${y}`, [face - 0.05, y, 7.3], [face + off + 0.45, y + 0.6, 8.7], 'br', { split: '' });
  }
  // Iron band round the stack with its draw bolt.
  function band(m, y) {
    m.prismY(`band_${y}`, C, R + 0.3, y, y + 1.2, 'ci', { ends: ['up', 'down'] });
    m.box(`band_${y}_lug`, [7.2, y - 0.1, 14.1], [8.8, y + 1.3, 14.9], 'ci', { split: '' });
    m.box(`band_${y}_bolt`, [6.8, y + 0.4, 14.3], [9.2, y + 0.8, 14.7], 'st', { split: '' });
  }

  // ------------------------------------------------------------------ parts
  function middle() {
    const m = mk('chimney/brick');
    m.prismY('stack', C, R, 0, 16, 'cbrick', { tex: 'chimney/brick' });
    band(m, 10);
    for (const y of [2.5, 7.8, 13.2]) stepIron(m, y, C[0] - R);
    tape(m, 0, 16, C[0] + R, [3, 13]);
    return m;
  }

  function base() {
    const m = mk('chimney/brick');
    // cast-iron bed plate on the uptake flange (or on a footing)
    m.box('bed_plate', [0.5, 0, 0.5], [15.5, 0.8, 15.5], 'ci', { split: '', hide: ['down'] });
    // square brick plinth, a stone weathering course, and a corbel course out of which the round stack rises
    m.box('plinth', [1, 0.8, 1], [15, 7, 15], 'cbrick', { hide: ['down'] });
    m.box('weathering', [0.6, 7, 0.6], [15.4, 8.2, 15.4], 'ashlar', { split: '' });
    m.prismY('corbel', C, R + 0.6, 8.2, 9.4, 'cbrick', { tex: 'chimney/brick', seed: 3, ends: ['up'] });
    m.prismY('stack', C, R, 9.4, 16, 'cbrick', { tex: 'chimney/brick', seed: 7 });
    // soot door in the plinth for raking out the base of the flue
    m.box('soot_frame', [5.4, 1.6, 15], [10.6, 5.8, 15.3], 'ci', { split: '' });
    m.box('soot_door', [5.9, 2, 15.3], [10.1, 5.4, 15.6], 'ci', { split: '' });
    for (const y of [2.4, 4.6]) m.box(`soot_hinge_${y}`, [5.5, y, 15.3], [6.1, y + 0.6, 15.8], 'st', { split: '' });
    m.box('soot_latch', [8.6, 3.4, 15.6], [10.4, 3.9, 16], 'st', { split: '' });
    // step irons on the plinth and on the stack
    for (const y of [2.5, 5.4]) stepIron(m, y, 1);
    stepIron(m, 13.2, C[0] - R);
    // conductor down the stack, over the weathering and down the plinth face to earth
    tape(m, 9.4, 16, C[0] + R, [13]);
    m.box('tape_over', [C[0] + R + 0.35, 8.2, 7.6], [15.65, 8.45, 8.4], 'cu', { split: '' });
    tape(m, 0, 8.2, 15.4, [2, 5], 0);
    return m;
  }

  function top() {
    const m = mk('chimney/brick_top');
    m.prismY('stack', C, R, 0, 8, 'ctop', { tex: 'chimney/brick_top' });
    band(m, 4);
    // corbelled cornice, three courses stepping out, under a cast-iron coping cap
    m.prismY('cornice_a', C, R + 0.5, 8, 9.5, 'ctop', { tex: 'chimney/brick_top', seed: 2, ends: ['down'] });
    m.prismY('cornice_b', C, R + 1, 9.5, 11, 'ctop', { tex: 'chimney/brick_top', seed: 5, ends: ['down'] });
    m.prismY('cornice_c', C, R + 1.4, 11, 12, 'ctop', { tex: 'chimney/brick_top', seed: 9, ends: ['down'] });
    m.prismY('cap', C, R + 1.6, 12, 13, 'ci', { cap: 'chimney/mouth', ends: ['down'] });
    for (const y of [2.5, 7.8]) stepIron(m, y, C[0] - R);
    // conductor up the stack and round the cornice to the air terminal at the rim
    tape(m, 0, 8, C[0] + R, [3]);
    m.box('tape_out', [C[0] + R + 0.35, 8, 7.6], [15.65, 8.25, 8.4], 'cu', { split: '' });
    m.box('tape_cornice', [15.4, 8.25, 7.6], [15.65, 12.4, 8.4], 'cu', { split: '', hide: ['west'] });
    m.box('terminal_clamp', [14.6, 12.4, 7.2], [15.8, 13.6, 8.8], 'br', { split: '' });
    m.box('terminal_rod', [14.95, 13.6, 7.75], [15.45, 22, 8.25], 'cu', { split: '' });
    m.box('terminal_tip', [15.05, 22, 7.85], [15.35, 23.2, 8.15], 'cu', { split: '' });
    return m;
  }

  const BUILDS = {
    chimney: { model: middle },
    chimney_base: { model: base },
    chimney_top: { model: top },
  };

  window.CHY = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'boilers', BUILDS),
    // a three-block stack, base / middle / top, shifted down a block (preview only, outside the Java limits)
    preview(root) {
      const m = base().merge(middle(), 0, 16, 0, 'm_').merge(top(), 0, 32, 0, 't_');
      for (const e of m.els) {
        e.from = [e.from[0], e.from[1] - 16, e.from[2]]; e.to = [e.to[0], e.to[1] - 16, e.to[2]];
        if (e.rot) e.rot = Object.assign({}, e.rot, { origin: [e.rot.origin[0], e.rot.origin[1] - 16, e.rot.origin[2]] });
      }
      return PCART.realize(root, m, 'chimney_preview');
    },
  };
})();
