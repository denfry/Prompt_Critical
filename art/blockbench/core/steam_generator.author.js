// Horizontal steam generator (one cell), hand-authored for Blockbench; replaces the spec_plant.py placeholder.
// The VVER type (PGV-440/1000): a horizontal shell full of U-tube bundles; primary coolant enters and leaves
// through the hot and cold collectors, two vertical cylinders through the shell with flanged covers on top; the
// steam collects in the upper part of the shell and leaves through the steam header (red band, GOST 14202), the
// feedwater comes in on the north side. The shell sits on two saddle supports with snubbers.
// Load in Blockbench desktop after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/steam_generator.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); STEAMGEN.paint('<repo>')
//   STEAMGEN.build('<repo>', 'steam_generator'); PCART.save('<repo>', 'core', 'steam_generator')
// Point used by code (SteamGeneratorBlockEntity.clientTick): blowdown cock (8, 1.2, 12.5).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/steam_generator';
  const T = {};

  // Shell under reflective metal insulation: stainless cassettes, raised seams.
  T.rmi = () => {
    const im = img(32, 32), r = rng(1201), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([176, 180, 182], (n(x, y) - 0.5) * 24 + (r() - 0.5) * 5);
      if (x === 0 || y === 0) c = [206, 210, 212];
      if (x === 1 || y === 1) c = [118, 122, 126];
      put(im, x, y, c);
    }
    for (const [x, y] of [[15, 0], [0, 15]]) { rect(im, x, y, 3, 2, [96, 98, 100]); put(im, x, y, [220, 222, 224]); }
    return im;
  };
  // End caps: dished head under insulation, radial gores.
  T.end = () => {
    const im = img(32, 32), r = rng(1211);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy) / 16, a = Math.atan2(dy, dx);
      let c = add([174, 178, 180], (r() - 0.5) * 6 - d * 20);
      if (((a / (Math.PI * 2)) * 8 + 8) % 1 < 0.06) c = [124, 128, 132];
      if (d > 0.94) c = [110, 114, 118];
      put(im, x, y, c);
    }
    return im;
  };
  // Collector cover from above: forged flange with a ring of studs, the central plug.
  T.cover = () => {
    const im = img(32, 32), r = rng(1221);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([100, 102, 106], (r() - 0.5) * 6 + Math.sin(d * 30) * 4);
      if (d > 0.92) c = [70, 72, 74];
      if (d < 0.4) c = add([128, 132, 136], (r() - 0.5) * 6);
      put(im, x, y, c);
    }
    for (let k = 0; k < 16; k++) {
      const a = k / 16 * Math.PI * 2, x = 15.5 + 11.5 * Math.cos(a), y = 15.5 + 11.5 * Math.sin(a);
      rect(im, x - 1, y - 1, 2, 2, [56, 58, 60]); put(im, x - 1, y - 1, [160, 162, 166]);
    }
    return im;
  };
  // Supports: painted steel.
  T.steel = () => { const im = img(32, 32); grain(im, [86, 98, 92], 1231, 4, 6, 8); return im; };
  // Pipe bands: steam red, water green (GOST 14202) over stainless.
  T.steam = () => { const im = img(32, 32); grain(im, [168, 38, 32], 1241, 5, 6, 8); return im; };
  T.water = () => { const im = img(32, 32); grain(im, [44, 128, 66], 1251, 5, 6, 8); return im; };
  T.pipe = () => {
    const im = img(32, 32), r = rng(1261);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([150, 154, 158], Math.sin(x * 0.4) * 5 + (r() - 0.5) * 6));
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    rmi: { tex: 'steam_generator/rmi', P: 16 }, sgst: { tex: 'steam_generator/steel', P: 16 },
    steam: { tex: 'steam_generator/steam', P: 16 }, water: { tex: 'steam_generator/water', P: 16 },
    pipe: { tex: 'steam_generator/pipe', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT), { particle: 'steam_generator/rmi' });

  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const TAN16 = Math.tan(Math.PI / 16);
  // Upright 16-gon within one block with an optional radial top.
  function cylY(m, name, c, r, y0, y1, mat, cap) {
    const h = r * TAN16, w = 2 * h, k = 16 / (2 * r), tex = m.mat[mat].tex;
    [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]].forEach(([wide, ang], i) => {
      const top = y1 + (cap ? i * 0.01 : 0);
      const f = wide ? [c[0] - r, y0, c[1] - h] : [c[0] - h, y0, c[1] - r];
      const t = wide ? [c[0] + r, top, c[1] + h] : [c[0] + h, top, c[1] + r];
      const sides = wide ? ['east', 'west'] : ['north', 'south'];
      const face = {}, hide = [];
      for (const d of DIRS) {
        if (sides.includes(d)) {
          const u0 = ((i * 0.6180339) % 1) * (16 - w);
          face[d] = { tex, uv: [u0, 16 - y1, u0 + w, 16 - y0] };
        } else if (d === 'up' && cap) {
          face.up = { tex: cap, uv: [(f[0] - c[0] + r) * k, (f[2] - c[1] + r) * k, (t[0] - c[0] + r) * k, (t[2] - c[1] + r) * k] };
        } else hide.push(d);
      }
      m.els.push({ name: `${name}_${i}`, from: f, to: t, mat, hide, face,
        rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
    });
  }

  function sg() {
    const m = mk();
    // saddles with their snubber struts
    for (const x of [2.5, 12]) {
      m.box(`saddle_${x}`, [x, 0, 2], [x + 1.6, 3.2, 14], 'sgst', { split: '' });
      m.box(`saddle_base_${x}`, [x - 0.4, 0, 1.4], [x + 2, 0.6, 14.6], 'sgst', { split: '' });
    }
    m.box('snubber', [7.2, 0, 13.6], [8.8, 3.6, 14.4], 'sgst', { split: '' });
    // shell along x with dished ends
    m.prism('shell', 'x', [8, 8], 6, 0.8, 15.2, 'rmi');
    m.disc('end_w', 'x', [8, 8], 6, 0, 0.8, 'rmi', 'steam_generator/end', 'west');
    m.disc('end_e', 'x', [8, 8], 6, 15.2, 16, 'rmi', 'steam_generator/end', 'east');
    // hot and cold primary collectors through the top, with their covers
    cylY(m, 'collector_hot', [5, 8], 2.4, 12, 15.6, 'pipe', 'steam_generator/cover');
    cylY(m, 'collector_cold', [11, 8], 2.4, 12, 15.6, 'pipe', 'steam_generator/cover');
    // primary nozzles low on the south side (to the loop)
    for (const x of [5, 11]) {
      m.prism(`primary_${x}`, 'z', [x, 5], 1.6, 13.8, 16, 'pipe', { ends: [] });
    }
    // steam header on top, outlet to the east with a red band
    m.prism('steam_header', 'x', [15.2, 3], 0.9, 2, 16, 'pipe', { ends: ['west'] });
    for (const x of [4, 8, 12]) m.box(`steam_riser_${x}`, [x - 0.4, 11, 2.6], [x + 0.4, 14.4, 3.4], 'pipe', { split: '' });
    m.box('steam_band', [14, 14.2, 2], [15, 16.2, 4], 'steam', { split: '' });
    // feedwater on the north side with a green band
    m.prism('feed', 'z', [8, 11], 1, 0, 3, 'pipe', { ends: ['north'] });
    m.box('feed_band', [6.8, 9.8, 0.4], [9.2, 12.2, 1], 'water', { split: '' });
    // blowdown under the shell with its cock
    m.box('blowdown', [7.6, 1, 11.6], [8.4, 3.6, 12.4], 'pipe', { split: '' });
    m.box('blowdown_cock', [7.1, 0.8, 12.1], [8.9, 1.6, 12.9], 'sgst', { split: '' });
    return m;
  }

  const BUILDS = { steam_generator: { model: sg } };
  window.STEAMGEN = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
