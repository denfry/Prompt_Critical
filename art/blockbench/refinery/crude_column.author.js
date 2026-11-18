// Atmospheric crude column, hand-authored for Blockbench; replaces the column_base / column_base_insulated /
// column_top placeholders. The column is built from the crude_column base (controller, 1x2x1), any number of
// column_tray_section blocks (1x2x1 each) and a column_top (1x1). Every piece keeps the same shell radius, ladder
// line and cladding seams, so the stack reads as one tower whatever its height. Load in Blockbench desktop:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/crude_column.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); COLUMN.paint('<repo>')
//   COLUMN.build('<repo>', 'crude_column'); PCART.save('<repo>', 'refinery', 'crude_column')   — one model per call
// The base: a skirt with its access opening and fireproofing, the bottom head, the bottoms outlet north (to the
// pump), the feed nozzle from the furnace transfer line on the east, a manway. A tray section: insulated shell with
// a side-draw nozzle and its block valve, a manway, a platform on the south with the caged ladder. The top: the
// head, the overhead vapour line rising to its elbow, and the relief valve on the head.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/crude_column';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Aluminium cladding on the hot shell: sheets 1 m tall lapped downwards, a band every sheet, dirt streaks.
  T.clad = () => {
    const im = img(32, 32), r = rng(701), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([186, 188, 190], (n(x, y) - 0.5) * 14 + (r() - 0.5) * 5 + y * 0.3);
      if (y === 0) c = [210, 212, 214];
      if (y === 1) c = [134, 136, 138];
      if (y === 15 || y === 16) c = [112, 114, 116];
      put(im, x, y, c);
    }
    for (let k = 0; k < 4; k++) { const x = r() * 32; for (let y = 2; y < 2 + r() * 12; y++) put(im, x, y, [150, 148, 140]); }
    return im;
  };
  // Skirt fireproofing: gunned concrete, rough.
  T.fireproof = () => {
    const im = img(32, 32), r = rng(711), n = vnoise(r, 32, 32, 4);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([140, 138, 130], (n(x, y) - 0.5) * 30 + (r() - 0.5) * 12));
    return im;
  };
  // Nozzles, flanges, platform steel: grey primer.
  T.steel = () => {
    const im = img(32, 32), r = grain(im, [92, 96, 98], 721, 4, 6, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [110, 76, 52]);
    return im;
  };
  // Ladder, cage and handrail: safety yellow.
  T.yellow = () => {
    const im = img(32, 32), r = grain(im, [206, 166, 34], 731, 5, 8, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [96, 90, 80]);
    return im;
  };
  // Platform grating.
  T.grating = () => {
    const im = img(32, 32), r = rng(741);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = [30, 30, 32];
      if (x % 2 === 0) c = add([96, 98, 100], (r() - 0.5) * 12);
      if (y % 8 === 0) c = add([108, 110, 112], (r() - 0.5) * 10);
      put(im, x, y, c);
    }
    return im;
  };
  // Hot oil and product lines: brown, GOST 14202 for combustible liquids.
  T.oil = () => {
    const im = img(32, 32), r = grain(im, [104, 66, 42], 751, 5, 6, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [70, 46, 32]);
    return im;
  };
  // Atlas (32 px, 2 px per uv unit): manway cover, nameplate, relief valve tag.
  const A = { manway: [0, 0, 8, 8], plate: [8, 0, 12, 6], tag: [20, 0, 6, 3], head: [0, 16, 16, 16] };
  T.parts = () => {
    const im = img(32, 32), r = rng(761);
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
      let c = d > 3.9 ? [0, 0, 0, 0] : d > 3.2 ? [76, 80, 82] : add([100, 104, 106], (r() - 0.5) * 6);
      put(im, x, y, c);
    }
    for (let k = 0; k < 8; k++) { const a = k / 8 * Math.PI * 2; put(im, 3.5 + 3.4 * Math.cos(a), 3.5 + 3.4 * Math.sin(a), [150, 152, 154]); }
    rect(im, 6, 3, 1, 2, [60, 62, 64]);
    for (let y = 0; y < 6; y++) for (let x = 0; x < 12; x++) {
      let c = add([170, 172, 168], (r() - 0.5) * 6);
      if (x === 0 || y === 0 || x === 11 || y === 5) c = [92, 94, 92];
      if (y > 1 && y < 4 && x > 1 && x < 10 && r() < 0.5) c = [42, 44, 46];
      put(im, 8 + x, y, c);
    }
    for (let y = 0; y < 3; y++) for (let x = 0; x < 6; x++) put(im, 20 + x, y, y === 1 && x > 0 && x < 5 && r() < 0.6 ? [30, 30, 28] : [212, 172, 36]);
    // top head from above: cladding gores round the vapour nozzle
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy) / 8, a = Math.atan2(dy, dx);
      let c = add([184, 186, 188], (r() - 0.5) * 6 - d * 16);
      if (((a / (Math.PI * 2)) * 8 + 8) % 1 < 0.08) c = [132, 134, 136];
      put(im, x, 16 + y, c);
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    clad: { tex: 'crude_column/clad', P: 16 }, fire: { tex: 'crude_column/fireproof', P: 16 }, cst: { tex: 'crude_column/steel', P: 16 },
    yellow: { tex: 'crude_column/yellow', P: 16 }, grating: { tex: 'crude_column/grating', P: 16 }, oil: { tex: 'crude_column/oil', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'crude_column/parts', size: 32, rects: A } }),
    { particle: 'crude_column/clad' });

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
            const u0 = o.wrap ? 0 : ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else if (d === 'up' && last && o.cap) {
            const [x0, y0, x1, y1] = o.cap.rect;
            const map = (u, v) => [x0 + u * (x1 - x0), y0 + v * (y1 - y0)];
            const p = map((f[0] - c[0] + r) / (2 * r), (f[2] - c[1] + r) / (2 * r)), q = map((t[0] - c[0] + r) / (2 * r), (t[2] - c[1] + r) / (2 * r));
            face.up = { tex: o.cap.tex, uv: [p[0], p[1], q[0], q[1]] };
          } else hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }

  const R = 6.5, C = [8, 8];
  // Caged ladder on the south side, x 6..10, out at z 15..16; the cage hoops every 4 units.
  function ladder(m, y0, y1, cage) {
    for (const x of [6.2, 9.4]) m.box(`ladder_rail_${x}`, [x, y0, 15.2], [x + 0.4, y1, 15.6], 'yellow');
    for (let y = y0 + 1; y < y1; y += 2) m.box(`ladder_rung_${y}`, [6.6, y, 15.25], [9.4, y + 0.3, 15.55], 'cst', { split: '' });
    if (!cage) return;
    for (let y = y0 + 2; y < y1; y += 4) {
      m.box(`cage_hoop_s_${y}`, [5.2, y, 15.8 + 2], [10.8, y + 0.3, 16 + 2], 'yellow', { split: '' });
      m.box(`cage_hoop_w_${y}`, [5.2, y, 15.6], [5.4, y + 0.3, 18], 'yellow', { split: '' });
      m.box(`cage_hoop_e_${y}`, [10.6, y, 15.6], [10.8, y + 0.3, 18], 'yellow', { split: '' });
    }
    for (const x of [5.2, 8, 10.6]) m.box(`cage_bar_${x}`, [x, y0 + 2, 17.7], [x + 0.2, y1, 17.9], 'yellow');
  }

  function base() {
    const m = mk();
    m.box('footing', [1, 0, 1], [15, 1, 15], 'fire');
    cylY(m, 'skirt', C, R - 0.4, 1, 9, 'fire', { seed: 1 });
    m.decal('skirt_opening', [5.5, 2, 1.7], [10.5, 7, 2.2], 'cst', 'north', 'p.manway', { hide: ['south'] });
    m.box('skirt_flange', [1.5, 9, 1.5], [14.5, 9.6, 14.5], 'cst', { split: '' });
    cylY(m, 'head', C, R - 1.5, 9.6, 11, 'clad', { seed: 3 });
    cylY(m, 'shell', C, R, 11, 32, 'clad', { seed: 5 });
    // bottoms outlet north to the pump, feed from the transfer line on the east, a manway west
    m.prism('bottoms', 'z', [8, 12.5], 1.3, 0, 2.2, 'oil', { ends: [] });
    m.box('bottoms_flange', [6.2, 10.7, 0.2], [9.8, 14.3, 0.8], 'cst', { split: '' });
    m.prism('feed', 'x', [22, 8], 1.6, 13.8, 16, 'oil', { ends: [] });
    m.box('feed_flange', [15.2, 19.8, 5.8], [15.8, 24.2, 10.2], 'cst', { split: '' });
    m.decal('manway', [1.2, 15, 5.5], [1.6, 19, 10.5], 'cst', 'west', 'p.manway', { hide: ['east'] });
    m.box('manway_davit', [1, 19, 7.8], [1.6, 21, 8.2], 'cst', { split: '' });
    m.decal('nameplate', [6, 26, 1.4], [10, 28, 1.6], 'cst', 'north', 'p.plate', { hide: ['south'] });
    ladder(m, 1, 32, false);
    return m;
  }

  function tray() {
    const m = mk();
    cylY(m, 'shell', C, R, 0, 32, 'clad', { seed: 7 });
    // side draw: nozzle, block valve with handwheel, the line down the east side
    m.prism('draw', 'x', [10, 8], 1.1, 14.4, 16, 'oil', { ends: [] });
    m.box('draw_valve', [14.6, 8.4, 6.6], [16, 11.6, 9.4], 'cst', { split: '' });
    m.box('draw_flange', [14.2, 8.6, 6.6], [14.6, 11.4, 9.4], 'cst', { split: '' });
    m.box('reflux_nozzle', [6.6, 26, 1.2], [9.4, 28.8, 1.6], 'cst', { split: '' });
    m.prism('reflux', 'z', [8, 27.4], 1, 0, 1.2, 'oil', { ends: [] });
    m.decal('manway', [1.2, 18, 5.5], [1.6, 22, 10.5], 'cst', 'west', 'p.manway', { hide: ['east'] });
    // south platform with handrail, at the manway level
    m.box('platform', [1, 16, 14.5], [15, 16.6, 16], 'grating', { split: 'x' });
    m.box('platform_bracket_w', [2, 14, 14.5], [2.6, 16, 15.2], 'cst', { split: '' });
    m.box('platform_bracket_e', [13.4, 14, 14.5], [14, 16, 15.2], 'cst', { split: '' });
    for (const y of [20.5, 24.5]) {
      m.box(`rail_w_${y}`, [1, y, 14.5], [1.4, y + 0.4, 16], 'yellow', { split: '' });
      m.box(`rail_e_${y}`, [14.6, y, 14.5], [15, y + 0.4, 16], 'yellow', { split: '' });
    }
    for (const x of [1, 14.6]) m.box(`post_${x}`, [x, 16.6, 15.6], [x + 0.4, 24.9, 16], 'yellow', { split: '' });
    ladder(m, 0, 32, true);
    return m;
  }

  function top() {
    const m = mk();
    cylY(m, 'shell', C, R, 0, 5, 'clad', { seed: 9 });
    cylY(m, 'head_a', C, R - 0.8, 5, 7, 'clad', { seed: 11, cap: { tex: 'crude_column/parts', rect: [0, 8, 8, 16] } });
    cylY(m, 'head_b', C, R - 2.6, 7, 8.6, 'clad', { seed: 13, cap: { tex: 'crude_column/parts', rect: [0, 8, 8, 16] } });
    // overhead vapour line up to its elbow, which turns north toward the condensers
    cylY(m, 'vapour', C, 1.8, 8.6, 14, 'oil', { wrap: true });
    m.prism('vapour_run', 'z', [8, 14.2], 1.8, -0.01, 9.8, 'oil', { ends: [] });
    m.box('vapour_elbow', [6.2, 12.4, 6.2], [9.8, 16, 9.8], 'oil', { split: '' });
    // relief valve on the head, with its discharge stub and tag
    m.box('psv_body', [11.4, 7, 6.8], [13.4, 9.6, 9.2], 'cst', { split: '' });
    m.box('psv_bonnet', [11.9, 9.6, 7.4], [12.9, 13.4, 8.6], 'cst', { split: '' });
    m.box('psv_outlet', [13.4, 7.6, 7.4], [15.4, 8.8, 8.6], 'cst', { split: '' });
    m.decal('psv_tag', [11.4, 11, 7.1], [12.9, 12, 7.4], 'yellow', 'north', 'p.tag', { hide: ['south'] });
    ladder(m, 0, 6, true);
    for (const x of [6.2, 9.4]) m.box(`ladder_hoop_${x}`, [x, 6, 15.2], [x + 0.4, 9, 15.6], 'yellow', { split: '' });
    return m;
  }

  const BUILDS = {
    crude_column: { model: base },
    column_tray_section: { model: tray },
    column_top: { model: top },
  };
  window.COLUMN = {
    paint, BUILDS,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    // A short tower for looking only: base, two tray sections, top.
    preview(root) {
      const m = base().merge(tray(), 0, 32, 0, 't1_').merge(tray(), 0, 64, 0, 't2_').merge(top(), 0, 96, 0, 'top_');
      return PCART.realize(root, m, 'crude_column_preview');
    },
  };
})();
