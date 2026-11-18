// Primary steam reformer of an ammonia train (3x3x3), hand-authored for Blockbench; replaces the borrowed
// tube_furnace_radiant_catalyst models. A top-fired box furnace in the manner of the AM-1360 reformers: catalyst
// tubes hang through the roof between rows of downward-firing burners, take mixed feed from inlet headers through
// pigtails, and give up reformed gas to a collector under the floor. Flue gas turns into the convection section on
// the east side and leaves by the stack stub. Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/petrochem/steam_reformer.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); REFORMER.paint('<repo>')
//   REFORMER.build('<repo>', 'steam_reformer'); PCART.save('<repo>', 'petrochem', 'steam_reformer')   — one per call
//
// Local -16..32 on every axis (anchor in the middle). Points used by code (SteamReformerBlockEntity.clientTick):
// peep doors (0 | 8 | 16, 1, -12.6) on the north wall, stack top (26.5, 32, 8), burner air registers on the roof.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/steam_reformer';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Furnace casing: heat-resistant aluminium paint over welded plate, 1 m panels, a scorch bloom near the top.
  T.casing = () => {
    const im = img(32, 32), r = rng(401), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([164, 166, 162], (n(x, y) - 0.5) * 20 + (r() - 0.5) * 6);
      if (y < 6) c = mix(c, [120, 104, 82], (6 - y) / 14);
      if (x === 0) c = add(c, -32);
      if (x === 1) c = add(c, 12);
      put(im, x, y, c);
    }
    for (let k = 0; k < 12; k++) put(im, r() * 32, r() * 32, [118, 96, 72]);
    return im;
  };
  // Buckstays and platform steel: rolled sections in dark primer.
  T.steel = () => {
    const im = img(32, 32), r = grain(im, [64, 66, 64], 411, 4, 6, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [102, 70, 48]);
    return im;
  };
  // Centrifugally cast HK-40 catalyst tube above the roof: dark scaled oxide, temper colours near the roof.
  T.tube = () => {
    const im = img(32, 32), r = rng(421);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([78, 70, 64], Math.sin(x * 0.9) * 6 + (r() - 0.5) * 12);
      if (y > 26) c = mix(c, [110, 74, 60], (y - 26) / 6);
      put(im, x, y, c);
    }
    return im;
  };
  // Mineral wool lagging under aluminium sheet on the hot headers: overlapped sheets and bands.
  T.cladding = () => {
    const im = img(32, 32), r = rng(431), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([190, 192, 194], (n(x, y) - 0.5) * 16 + (r() - 0.5) * 6);
      if (x % 16 === 0) c = [140, 142, 146];
      if (x % 16 === 1) c = [214, 216, 218];
      if (x % 16 === 8) c = [124, 126, 128];
      put(im, x, y, c);
    }
    return im;
  };
  // Fuel gas pipe, yellow by GOST 14202 for combustible gas, with weather dirt.
  T.gas = () => {
    const im = img(32, 32), r = grain(im, [196, 160, 40], 441, 5, 8, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [110, 96, 60]);
    return im;
  };
  // Cast-in-place concrete plinth.
  T.concrete = () => {
    const im = img(32, 32), r = rng(451), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([146, 144, 136], (n(x, y) - 0.5) * 20 + (r() - 0.5) * 8));
    return im;
  };
  // Atlas (64 px, 4 px per uv unit): peep doors cold and lit, the burner register, the local gauge board, its lamps.
  const A = {
    peep: [0, 0, 8, 8], peep_lit: [8, 0, 8, 8], register: [16, 0, 8, 8], board: [24, 0, 24, 12],
    lamp_off: [48, 0, 4, 4], lamp_on: [52, 0, 4, 4], plate: [0, 8, 16, 6],
  };
  T.parts = () => {
    const im = img(64, 64), r = rng(461);
    // peep door: cast frame, a hinged cover with a mica window; lit, the window shows the tubes against the fire
    const peep = (ox, lit) => {
      for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
        let c = add([58, 58, 56], (r() - 0.5) * 8);
        if (x === 0 || y === 0) c = add(c, 18);
        if (x === 7 || y === 7) c = add(c, -18);
        const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
        if (d < 2.4) {
          if (lit) c = (x === 3 || x === 5) ? [150, 46, 20] : mix([255, 214, 120], [255, 120, 30], d / 2.4);
          else c = add([34, 30, 28], (r() - 0.5) * 6);
        }
        put(im, ox + x, y, c);
      }
      put(im, ox + 6, 3, [150, 150, 146]); put(im, ox + 6, 4, [110, 110, 106]);
    };
    peep(0, false); peep(8, true);
    // burner air register: a slotted ring seen from above
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const dx = x + 0.5 - 4, dy = y + 0.5 - 4, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
      let c = d > 3.6 ? [62, 64, 62] : d > 1.4 ? (Math.sin(a * 8) > 0 ? [88, 90, 88] : [26, 24, 22]) : [118, 120, 118];
      put(im, 16 + x, y, add(c, (r() - 0.5) * 6));
    }
    // gauge board: enamel panel with three gauges and a tag strip
    for (let y = 0; y < 12; y++) for (let x = 0; x < 24; x++) {
      let c = add([66, 98, 116], (r() - 0.5) * 6);
      if (x === 0 || y === 0) c = add(c, 22);
      if (x === 23 || y === 11) c = add(c, -22);
      put(im, 24 + x, y, c);
    }
    for (let k = 0; k < 3; k++) {
      const cx = 24 + 4 + k * 8, cy = 5;
      for (let y = -3; y <= 3; y++) for (let x = -3; x <= 3; x++) {
        const d = Math.hypot(x, y);
        if (d <= 3.2) put(im, cx + x, cy + y, d > 2.4 ? [40, 40, 40] : [230, 226, 210]);
      }
      put(im, cx, cy, [30, 30, 30]); put(im, cx - 1, cy - 1 + (k % 2), [30, 30, 30]);
      put(im, cx + 2, cy - 1, [190, 30, 26]);
    }
    for (let x = 26; x < 46; x++) if (r() < 0.6) put(im, x, 10, [220, 220, 210]);
    // lamps
    rect(im, 48, 0, 4, 4, [70, 40, 30]); rect(im, 49, 1, 2, 2, [110, 60, 40]);
    rect(im, 52, 0, 4, 4, [255, 150, 60]); rect(im, 53, 1, 2, 2, [255, 236, 190]);
    // maker's plate
    for (let y = 0; y < 6; y++) for (let x = 0; x < 16; x++) {
      let c = add([178, 180, 176], (r() - 0.5) * 6);
      if (x === 0 || y === 0 || x === 15 || y === 5) c = [96, 98, 96];
      if (y > 1 && y < 4 && x > 1 && x < 14 && r() < 0.45) c = [40, 42, 44];
      put(im, x, 8 + y, c);
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    casing: { tex: 'steam_reformer/casing', P: 16 }, rsteel: { tex: 'steam_reformer/steel', P: 16 },
    tube: { tex: 'steam_reformer/tube', P: 16 }, clad: { tex: 'steam_reformer/cladding', P: 16 },
    gas: { tex: 'steam_reformer/gas', P: 16 }, concrete: { tex: 'steam_reformer/concrete', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'steam_reformer/parts', size: 64, rects: A } }),
    { particle: 'steam_reformer/casing' });

  // Upright 16-gon cut at block lines so a P = 16 texture tiles; the same helper as the BWR's.
  const TAN16 = Math.tan(Math.PI / 16);
  function cylY(m, name, c, r, y0, y1, mat, o = {}) {
    const h = r * TAN16, w = 2 * h, tex = m.mat[mat].tex, cuts = [y0];
    for (let y = Math.floor(y0 / 16) * 16 + 16; y < y1; y += 16) cuts.push(y);
    cuts.push(y1);
    let facet = o.seed || 0, n = 0;
    for (let s = 0; s + 1 < cuts.length; s++) {
      const a = cuts[s], b = cuts[s + 1], base = Math.floor(a / 16) * 16, last = s + 2 === cuts.length;
      const boxes = [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]];
      boxes.forEach(([wide, ang]) => {
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, b, c[1] + h] : [c[0] + h, b, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else if (!(d === 'up' && last && o.top)) hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }

  const TUBE_X = [-10, -4, 2, 8, 14], TUBE_Z = [-4, 8, 20], BURNER_Z = [2, 14], BURNER_X = [-7, 5, 17];
  function reformer(lit) {
    const m = mk();
    m.box('plinth', [-16, -16, -16], [32, -14, 32], 'concrete', { hide: ['down'] });
    // furnace legs and the reformed gas collector under the floor
    for (const x of [-14, 6, 20]) for (const z of [-12, 26]) m.box(`leg_${x}_${z}`, [x, -14, z], [x + 2, -10, z + 2], 'rsteel');
    m.prism('collector', 'x', [-12.4, 8], 1.8, -14, 22, 'clad', { ends: ['west'] });
    for (const x of TUBE_X) m.box(`outlet_${x}`, [x - 0.5, -11, 7.5], [x + 0.5, -10, 8.5], 'tube', { split: '' });
    m.prism('transfer_line', 'z', [20, -12.4], 1.8, 9.8, 32, 'clad');
    m.box('transfer_elbow', [18.2, -14.2, 6.2], [21.8, -10.6, 9.8], 'clad', { split: '' });
    // radiant box: casing with buckstays, the roof, peep doors on the north wall
    m.box('box', [-14, -10, -12], [22, 16, 28], 'casing');
    for (const x of [-14.6, -2, 10, 21.4]) {
      m.box(`buck_n_${x}`, [x - 0.6, -10, -12.8], [x + 0.6, 16, -12], 'rsteel', { hide: ['south'] });
      m.box(`buck_s_${x}`, [x - 0.6, -10, 28], [x + 0.6, 16, 28.8], 'rsteel', { hide: ['north'] });
    }
    for (const y of [-2, 9]) {
      m.box(`wale_w_${y}`, [-14.8, y, -12], [-14, y + 1, 28], 'rsteel', { hide: ['east'] });
    }
    m.box('roof', [-14.6, 16, -12.6], [22.6, 17, 28.6], 'rsteel');
    [0, 8, 16].forEach((x, i) => {
      m.decal(lit ? `glow_peep_${i}` : `peep_${i}`, [x - 2, -1, -12.6], [x + 2, 3, -12], 'rsteel', 'north', lit ? 'p.peep_lit' : 'p.peep', { hide: ['south'] });
    });
    m.decal('nameplate', [-10, 6, -12.3], [-2, 9, -12], 'casing', 'north', 'p.plate', { hide: ['south'] });
    // catalyst tubes through the roof, pigtails up to the inlet headers
    for (const z of TUBE_Z) {
      for (const x of TUBE_X) {
        m.box(`tube_${x}_${z}`, [x - 0.9, 17, z - 0.9], [x + 0.9, 24, z + 0.9], 'tube', { split: '' });
        m.box(`tube_flange_${x}_${z}`, [x - 1.2, 24, z - 1.2], [x + 1.2, 25, z + 1.2], 'rsteel', { split: '' });
        m.box(`pigtail_${x}_${z}`, [x - 0.3, 25, z - 0.3], [x + 0.3, 27.4, z + 0.3], 'tube', { split: '' });
        m.box(`pigtail_bend_${x}_${z}`, [x - 0.3, 27.1, z - 0.3], [x + 0.3, 27.7, z + 1.4], 'tube', { split: '' });
      }
      m.prism(`header_${z}`, 'x', [28.8, z + 2.2], 1.3, -13, 17, 'clad', { ends: ['west', 'east'] });
    }
    // mixed feed riser to the headers along the east end
    m.prism('feed_main', 'z', [28.8, 17.8], 1.4, -4, 24, 'clad');
    m.box('feed_drop', [16.4, 17, 22.6], [19.2, 28.8, 25.4], 'clad', { split: '' });
    // top-fired burners between the tube rows, fed from yellow fuel gas headers
    for (const z of BURNER_Z) {
      m.prism(`fuel_${z}`, 'x', [21, z + 2.6], 0.7, -12, 22, 'gas', { ends: ['west'] });
      for (const x of BURNER_X) {
        m.box(`burner_${x}_${z}`, [x - 1.6, 17, z - 1.6], [x + 1.6, 19.6, z + 1.6], 'rsteel', { split: '' });
        m.decal(`register_${x}_${z}`, [x - 1.6, 19.6, z - 1.6], [x + 1.6, 20, z + 1.6], 'rsteel', 'up', 'p.register', { hide: ['down'] });
        m.box(`burner_feed_${x}_${z}`, [x - 0.3, 20, z + 0.3], [x + 0.3, 21, z + 2.9], 'gas', { split: '' });
      }
    }
    // convection section and the stack stub on the east
    m.box('convection', [22, -10, -6], [31, 22, 22], 'casing');
    for (const y of [2, 14]) m.box(`conv_band_${y}`, [31, y, -6], [31.6, y + 1, 22], 'rsteel', { hide: ['west'] });
    m.box('conv_hood', [23, 22, 2], [30, 24, 14], 'rsteel');
    cylY(m, 'stack', [26.5, 8], 3.4, 24, 32, 'rsteel', { top: false });
    m.box('stack_lip', [22.8, 31, 4.3], [30.2, 32, 11.7], 'rsteel', { split: '' });
    // local gauge board and the firing lamp on the north side
    m.box('board_post', [-13, -14, -15], [-12, 0, -14], 'rsteel', { split: '' });
    m.decal('board', [-14, -6, -15.5], [-8, -3, -15], 'rsteel', 'north', 'p.board', { hide: ['south'] });
    m.decal(lit ? 'glow_lamp' : 'lamp', [-9.6, -2.6, -15.6], [-8.6, -1.6, -15], 'rsteel', 'north', lit ? 'p.lamp_on' : 'p.lamp_off', { hide: ['south'] });
    // stair up to the roof on the west end
    for (let k = 0; k < 9; k++) m.box(`stair_${k}`, [-16, -14 + k * 3.4, 20 - k * 3], [-14, -13.4 + k * 3.4, 23 - k * 3], 'rsteel', { split: '' });
    return m;
  }

  const BUILDS = {
    steam_reformer: { model: () => reformer(false) },
    steam_reformer_lit: { model: () => reformer(true) },
  };
  window.REFORMER = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
