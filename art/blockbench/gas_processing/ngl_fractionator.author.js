// NGL fractionation train (3x3x3), hand-authored for Blockbench; replaces the borrowed column_base_large.
// Natural gas liquids are split in three columns in series, as at every gas fractionation unit (GFU): the
// deethanizer (tallest, west), the depropanizer (east) and the debutanizer (south). Each stands on a skirt with a
// kettle reboiler at its foot and an overhead line to the air coolers on the pipe rack along the north edge, which
// return the reflux to the drums under them. Product lines leave the south face. Load in Blockbench desktop:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/gas_processing/ngl_fractionator.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); NGL.paint('<repo>')
//   NGL.build('<repo>', 'ngl_fractionator'); PCART.save('<repo>', 'gas_processing', 'ngl_fractionator')
// Local -16..32 on every axis (anchor in the middle).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/ngl_fractionator';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Cold columns: white paint over carbon steel (no cladding below ambient), rust weeps under the nozzles.
  T.white = () => {
    const im = img(32, 32), r = rng(1101), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([214, 214, 208], (n(x, y) - 0.5) * 12 + (r() - 0.5) * 5);
      if (y === 15) c = add(c, -26);
      put(im, x, y, c);
    }
    for (let k = 0; k < 4; k++) { const x = r() * 32; for (let y = 0; y < 3 + r() * 10; y++) put(im, x, y, [168, 120, 84]); }
    return im;
  };
  // Concrete deck and skirts' fireproofing.
  T.concrete = () => {
    const im = img(32, 32), r = rng(1111), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([150, 148, 140], (n(x, y) - 0.5) * 20 + (r() - 0.5) * 8));
    return im;
  };
  // Structural steel of the rack and platforms, galvanised.
  T.galv = () => {
    const im = img(32, 32), r = rng(1121), n = vnoise(r, 32, 32, 4);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([150, 156, 158], (n(x, y) - 0.5) * 26 + (r() - 0.5) * 8));
    return im;
  };
  // Light hydrocarbon lines, brown by GOST 14202, and the yellow handrails.
  T.oil = () => { const im = img(32, 32); grain(im, [118, 74, 44], 1131, 5, 6, 8); return im; };
  T.yellow = () => { const im = img(32, 32); grain(im, [206, 168, 34], 1141, 5, 8, 8); return im; };
  // Air cooler bundle seen from above: fan rings over finned tubes.
  T.fans = () => {
    const im = img(32, 32), r = rng(1151);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16), a = Math.atan2(y + 0.5 - 16, x + 0.5 - 16);
      let c = y % 2 ? [92, 96, 98] : [132, 136, 138];
      if (d < 14) c = d > 12.6 ? [70, 74, 76] : (Math.sin(a * 3) > 0.6 ? [44, 46, 48] : [20, 20, 22]);
      if (d < 2) c = [90, 94, 96];
      put(im, x, y, add(c, (r() - 0.5) * 6));
    }
    return im;
  };
  // Column top seen from above: dished head, white.
  T.head = () => {
    const im = img(32, 32), r = rng(1161);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      put(im, x, y, add([218, 218, 212], (r() - 0.5) * 5 - d * 26));
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    white: { tex: 'ngl_fractionator/white', P: 16 }, concrete: { tex: 'ngl_fractionator/concrete', P: 16 },
    galv: { tex: 'ngl_fractionator/galv', P: 16 }, oil: { tex: 'ngl_fractionator/oil', P: 16 },
    yellow: { tex: 'ngl_fractionator/yellow', P: 16 }, fans: { tex: 'ngl_fractionator/fans', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT), { particle: 'ngl_fractionator/white' });

  const TAN16 = Math.tan(Math.PI / 16);
  function cylY(m, name, c, r, y0, y1, mat, o = {}) {
    const h = r * TAN16, w = 2 * h, k = 16 / (2 * r), tex = m.mat[mat].tex, cuts = [y0];
    for (let y = Math.floor(y0 / 16) * 16 + 16; y < y1; y += 16) cuts.push(y);
    cuts.push(y1);
    let facet = o.seed || 0, n = 0;
    for (let s = 0; s + 1 < cuts.length; s++) {
      const a = cuts[s], b = cuts[s + 1], base = Math.floor(a / 16) * 16, last = s + 2 === cuts.length;
      [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]].forEach(([wide, ang], i) => {
        const top = b + (last && o.cap ? i * 0.01 : 0);
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, top, c[1] + h] : [c[0] + h, top, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else if (d === 'up' && last && o.cap) {
            face.up = { tex: o.cap, uv: [(f[0] - c[0] + r) * k, (f[2] - c[1] + r) * k, (t[0] - c[0] + r) * k, (t[2] - c[1] + r) * k] };
          } else hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }

  // A column: skirt, shell, top head, platforms with handrails every 16 units, reboiler at the foot, overhead.
  function column(m, name, x, z, r, top, seed) {
    cylY(m, `${name}_skirt`, [x, z], r - 0.3, -15, -10, 'concrete', { seed });
    cylY(m, `${name}_shell`, [x, z], r, -10, top, 'white', { seed: seed + 1 });
    cylY(m, `${name}_head`, [x, z], r - 1.2, top, top + 1.2, 'white', { seed: seed + 2, cap: 'ngl_fractionator/head' });
    for (let y = 2; y < top - 4; y += 16) {
      m.box(`${name}_platform_${y}`, [x - r - 0.5, y, z + r - 1], [x + r + 0.5, y + 0.5, z + r + 2.4], 'galv', { split: '' });
      m.box(`${name}_rail_${y}`, [x - r - 0.5, y + 4.4, z + r + 2], [x + r + 0.5, y + 4.8, z + r + 2.4], 'yellow', { split: '' });
      for (const px of [x - r - 0.5, x + r + 0.1]) m.box(`${name}_post_${y}_${px}`, [px, y + 0.5, z + r + 2], [px + 0.4, y + 4.8, z + r + 2.4], 'yellow', { split: '' });
    }
    // kettle reboiler at the foot, toward the deck centre
    m.box(`${name}_reboiler`, [x - 2, -13, z - r - 4], [x + 2, -9, z - r + 0.5], 'white', { split: '' });
  }
  // Overhead vapour line: up from the head to height y, then north over the coolers.
  function overhead(m, name, x, z, top, y) {
    m.box(`${name}_ovh_up`, [x - 0.8, top + 1.2, z - 0.8], [x + 0.8, y + 0.8, z + 0.8], 'oil', { split: '' });
    m.box(`${name}_ovh_run`, [x - 0.8, y - 0.8, -12], [x + 0.8, y + 0.8, z - 0.8], 'oil', { split: '' });
  }

  function fractionator() {
    const m = mk();
    m.box('deck', [-16, -16, -16], [32, -15, 32], 'concrete', { hide: ['down'] });
    // the three columns: deethanizer, depropanizer, debutanizer
    column(m, 'c2', -6, 8, 5, 26, 1);
    column(m, 'c3', 13, 4, 4.4, 22, 5);
    column(m, 'c4', 12, 22, 3.8, 16, 9);
    // pipe rack along the north edge carrying the air coolers, reflux drums under it
    for (const x of [-15, 0, 15, 30]) m.box(`rack_leg_${x}`, [x, -15, -15], [x + 1.2, 20, -13.8], 'galv');
    m.box('rack_beam_lo', [-15, 4, -15], [31.2, 5, -13.8], 'galv');
    m.box('rack_beam_hi', [-15, 19, -15.8], [31.2, 20, -7], 'galv');
    m.box('cooler', [-14, 20, -15.8], [30, 22.4, -7.4], 'galv', { face: { up: 'fans' } });
    for (const x of [-12, 4, 20]) m.box(`cooler_plenum_${x}`, [x, 22.4, -15], [x + 8, 23, -8.2], 'galv', { split: '' });
    for (const [x, len] of [[-12, 9], [3, 8], [17, 7]]) {
      m.prism(`drum_${x}`, 'x', [-9, -11], 2.2, x, x + len, 'white', { ends: ['west', 'east'] });
      m.box(`drum_saddle_${x}`, [x + 1, -15, -12.6], [x + 2, -11, -9.4], 'galv', { split: '' });
      m.box(`drum_saddle2_${x}`, [x + len - 2, -15, -12.6], [x + len - 1, -11, -9.4], 'galv', { split: '' });
    }
    // overheads to the coolers
    overhead(m, 'c2', -6, 8, 26, 29.5);
    overhead(m, 'c3', 13, 4, 22, 26.5);
    overhead(m, 'c4', 12, 22, 16, 24.5);
    // product lines along the deck to the south face
    for (const [x, n] of [[-2, 'ethane'], [6, 'propane'], [26, 'butane']]) {
      m.box(`product_${n}`, [x - 0.7, -14.6, 14], [x + 0.7, -13.2, 32], 'oil', { split: 'z' });
    }
    m.box('feed', [-16, -14.6, 0], [-11, -13.2, 1.4], 'oil', { split: '' });
    return m;
  }

  const BUILDS = { ngl_fractionator: { model: fractionator } };
  window.NGL = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
