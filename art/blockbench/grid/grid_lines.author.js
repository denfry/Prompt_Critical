// The 0.4-10 kV distribution of the I1 grid, hand-authored for Blockbench (replaces the spec_grid.py and
// spec_grid2.py placeholders): wooden and concrete line poles (base, middle and top links), the LV roller
// insulator and the MV pin insulator, the outdoor busbar on its post insulator, the power cable, the LV
// distribution box (ShchRN type), the consumer substation with its village,
// and the rectifier unit (thyristor cabinets, and the mercury-arc valve of the early eras).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/grid/grid_lines.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); GRL.paint('<repo>')
//   await GRL.exportAll('<repo>')
//
// Front to the north. Contracts with the code (pc-mod grid):
//   wire_connector_lv: wall mount, hit box 6,4,5.4..10,10,16; the span hangs at the block centre, y 8.8, so the
//     groove of the roller sits there. wire_connector_mv: floor mount, hit box 5.5,0,5.5..10.5,12,10.5; the span
//     hangs at the centre, y 12, on the head of the pin insulator.
//   lv_distribution_box: hit box 2,0.5,10.4..14,14,16.
//   wooden_pole radius 2.5, concrete_pole radius 3 (PoleBlock); links base / middle / top.
//   busbar and power_cable are multipart: the core is always drawn, the side model per connected face (north,
//     turned about y; the cable also turned about x for up and down, so its side stays centred on x 8, y 8).
//   grid_consumer: 3x3x3 round the anchor (x, y, z -16..32). rectifier_unit: 2x2x1, x 0..32, y 0..32, z 0..16.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'grid_lines/';
  const T = {};

  // ---------------------------------------------------------------- textures (32 px per block)
  // Creosoted pine: dark brown, wavy grain, drying checks.
  T.pole_wood = () => {
    const im = img(32, 32), r = rng(3101), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const g = Math.sin((x + Math.sin(y * 0.35 + x) * 1.2) * 1.9) * 7;
      put(im, x, y, add([72, 52, 36], g + (n(x, y) - 0.5) * 14 + (r() - 0.5) * 6));
    }
    for (let k = 0; k < 5; k++) {
      const x = Math.floor(r() * 32), y0 = Math.floor(r() * 32), L = 5 + Math.floor(r() * 10);
      for (let j = 0; j < L; j++) { const y = (y0 + j) % 32; put(im, x, y, [30, 22, 16]); put(im, x + 1, y, add(get(im, x + 1, y), 12)); }
    }
    return im;
  };
  // Pole top end grain: growth rings about the centre, a radial check.
  T.pole_end = () => {
    const im = img(32, 32), r = rng(3111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x - 15.5, y - 15.5);
      put(im, x, y, add([118, 90, 60], (Math.floor(d) % 3 === 0 ? -22 : 0) - d * 1.2 + (r() - 0.5) * 8));
    }
    for (let k = 0; k < 13; k++) put(im, 16 + k, 15, [40, 30, 20]);
    return im;
  };
  // Vibrated concrete of the SV poles: fine pores, formwork seam, through holes every half block.
  T.pole_concrete = () => {
    const im = img(32, 32), r = grain(im, [152, 150, 142], 3121, 5, 6, 8);
    for (let k = 0; k < 16; k++) put(im, r() * 32, r() * 32, [126, 124, 118]);
    for (let y = 0; y < 32; y++) put(im, 0, y, add(get(im, 0, y), -16));
    for (const hy of [7, 23]) { rect(im, 15, hy, 2, 2, [52, 50, 48]); put(im, 15, hy + 2, [176, 174, 166]); put(im, 16, hy + 2, [176, 174, 166]); }
    return im;
  };
  // Glazed white porcelain: a highlight down one side, a shadow down the other.
  T.porcelain = () => {
    const im = img(32, 32), r = rng(3131);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const s = Math.cos((x / 32) * Math.PI * 2) * 14;
      put(im, x, y, add([214, 212, 204], s + (r() - 0.5) * 4));
    }
    for (let y = 0; y < 32; y++) { put(im, 5, y, [246, 246, 242]); put(im, 6, y, [236, 236, 232]); }
    return im;
  };
  // Aluminium tube: bright, brushed along its length, oxide speckle.
  T.alu = () => {
    const im = img(32, 32), r = rng(3141);
    for (let y = 0; y < 32; y++) {
      const row = (r() - 0.5) * 10;
      for (let x = 0; x < 32; x++) put(im, x, y, add([176, 182, 186], row + (r() - 0.5) * 6 + (y % 16 < 3 ? 14 : 0)));
    }
    for (let k = 0; k < 20; k++) put(im, r() * 32, r() * 32, [146, 150, 150]);
    return im;
  };
  // Armoured cable in bitumen-soaked jute: dark lay of strands, chalk dust.
  T.cable = () => {
    const im = img(32, 32), r = rng(3151);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const s = (x + y) % 4;
      put(im, x, y, add(s === 0 ? [62, 54, 42] : s === 1 ? [40, 36, 30] : [30, 28, 26], (r() - 0.5) * 8));
    }
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [96, 92, 84]);
    return im;
  };
  // Signal lamp lenses and windows.
  const lens = (base, seed, hi) => () => {
    const im = img(16, 16), r = rng(seed);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x - 6, y - 6) / 10;
      put(im, x, y, add(mix(hi, base, Math.min(1, d)), (r() - 0.5) * 6));
    }
    return im;
  };
  T.lamp_green = lens([40, 200, 80], 3161, [210, 255, 210]);
  T.lamp_red = lens([220, 40, 30], 3162, [255, 210, 190]);
  T.lamp_amber = lens([240, 160, 30], 3163, [255, 240, 190]);
  T.lamp_white = lens([250, 226, 160], 3164, [255, 255, 240]);
  T.lamp_off = lens([54, 56, 58], 3165, [110, 114, 118]);
  // Village windows with a cross frame, lit (warm, curtains at the sides) or dark (sky reflection).
  const windowTex = (lit) => () => {
    const im = img(16, 16), r = rng(lit ? 3171 : 3172);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const c = lit ? add(mix([255, 224, 140], [236, 170, 70], y / 16), (r() - 0.5) * 8)
        : add(mix([70, 84, 104], [30, 36, 46], y / 16), (r() - 0.5) * 6);
      put(im, x, y, c);
    }
    if (lit) for (let y = 1; y < 15; y++) { rect(im, 1, y, 2, 1, [196, 84, 60]); rect(im, 13, y, 2, 1, [196, 84, 60]); }
    else for (let k = 0; k < 6; k++) put(im, 3 + k, 2 + k, [130, 146, 164]);
    const fr = [236, 234, 226];
    rect(im, 0, 0, 16, 1, fr); rect(im, 0, 15, 16, 1, fr); rect(im, 0, 0, 1, 16, fr); rect(im, 15, 0, 1, 16, fr);
    rect(im, 7, 0, 2, 16, fr); rect(im, 0, 6, 16, 1, fr);
    return im;
  };
  T.window_lit = windowTex(true);
  T.window_dark = windowTex(false);
  // Weathered log wall of a village house: round logs every 4 px, silver-grey with brown under the rounding.
  T.logs = () => {
    const im = img(32, 32), r = rng(3181);
    for (let y = 0; y < 32; y++) {
      const k = y % 4, shade = [-26, 6, 10, -8][k];
      for (let x = 0; x < 32; x++) put(im, x, y, add(mix([118, 104, 86], [96, 72, 50], k === 0 ? 0.6 : 0.2), shade + (r() - 0.5) * 8));
    }
    for (let k = 0; k < 10; k++) { const x = Math.floor(r() * 30), y = Math.floor(r() * 8) * 4 + 2; rect(im, x, y, 2, 1, [80, 64, 48]); }
    return im;
  };
  // Small-format red brick for the houses at this scale (2 px courses).
  T.brick = () => {
    const im = img(32, 32), r = rng(3191);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const row = Math.floor(y / 2), joint = y % 2 === 1 || (x + (row % 2) * 2) % 4 === 3;
      put(im, x, y, joint ? add([184, 176, 160], (r() - 0.5) * 8) : add([150, 74, 52], (r() - 0.5) * 18));
    }
    return im;
  };
  // Corrugated asbestos-cement roof sheets (shifer): waves 3 px, sheet laps every 16 px.
  T.slate = () => {
    const im = img(32, 32), r = rng(3201);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const w = [12, 0, -14][x % 3];
      put(im, x, y, add([148, 150, 146], w + (y % 16 === 15 ? -24 : 0) + (r() - 0.5) * 6 + (r() < 0.02 ? -30 : 0)));
    }
    return im;
  };
  // Green painted roofing iron with standing seams.
  T.roof_tin = () => {
    const im = img(32, 32), r = grain(im, [66, 112, 82], 3211, 4, 6, 8);
    for (let x = 0; x < 32; x += 8) for (let y = 0; y < 32; y++) { put(im, x, y, [110, 156, 120]); put(im, x + 1, y, [44, 76, 56]); }
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [120, 84, 54]);
    return im;
  };
  // PO-2 concrete fence panel: a lattice of raised rhombi, a joint every block.
  T.fence_po2 = () => {
    const im = img(32, 32), r = grain(im, [158, 156, 148], 3221, 4, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const a = (x + y) % 16, b = (x - y + 32) % 16;
      if (a === 0 || b === 0) put(im, x, y, [196, 194, 186]);
      else if (a === 1 || b === 1) put(im, x, y, [114, 112, 106]);
    }
    for (let y = 0; y < 32; y++) put(im, 0, y, [96, 94, 90]);
    return im;
  };
  // Gravel of the substation yard and the grass of the village.
  T.gravel = () => {
    const im = img(32, 32), r = grain(im, [126, 122, 114], 3231, 8, 6, 4);
    for (let k = 0; k < 70; k++) { const c = add([150, 144, 134], (r() - 0.5) * 50); put(im, r() * 32, r() * 32, c); }
    return im;
  };
  T.grass = () => {
    const im = img(32, 32), r = grain(im, [86, 122, 56], 3241, 10, 8, 8);
    for (let k = 0; k < 40; k++) put(im, r() * 32, r() * 32, add([110, 150, 70], (r() - 0.5) * 30));
    return im;
  };
  // The settlement sign: white board, black border and lettering, a red line under the name.
  T.sign_village = () => {
    const im = img(32, 32), r = rng(3251);
    rect(im, 0, 0, 32, 32, [236, 236, 230]);
    rect(im, 0, 0, 32, 2, [24, 24, 24]); rect(im, 0, 30, 32, 2, [24, 24, 24]); rect(im, 0, 0, 2, 32, [24, 24, 24]); rect(im, 30, 0, 2, 32, [24, 24, 24]);
    // seven block letters of the name, two lines of small text below
    for (let i = 0; i < 6; i++) {
      const x = 4 + i * 4;
      rect(im, x, 8, 3, 1, [20, 20, 20]); rect(im, x, 13, 3, 1, [20, 20, 20]);
      rect(im, x, 8, 1, 6, [20, 20, 20]); if (i % 2 === 0) rect(im, x + 2, 8, 1, 6, [20, 20, 20]); else rect(im, x, 10, 3, 1, [20, 20, 20]);
    }
    for (let x = 6; x < 26; x++) if (r() < 0.75) { put(im, x, 19, [60, 60, 60]); put(im, x, 23, [60, 60, 60]); }
    return im;
  };
  // Warning plate (Soviet "Ostorozhno, elektricheskoe napryazhenie"): yellow triangle, black border, black bolt.
  T.warn = () => {
    const im = img(32, 32);
    rect(im, 0, 0, 32, 32, [232, 230, 222]);
    for (let y = 3; y < 29; y++) {
      const half = (y - 3) * 0.55;
      for (let x = Math.round(16 - half); x <= Math.round(16 + half); x++) {
        const edge = x - (16 - half) < 2 || (16 + half) - x < 2 || y > 26;
        put(im, x, y, edge ? [20, 20, 20] : [246, 200, 30]);
      }
    }
    const bolt = [[17, 10], [16, 11], [16, 12], [15, 13], [15, 14], [14, 15], [15, 16], [16, 16], [17, 16], [17, 17], [16, 18], [16, 19], [15, 20], [15, 21], [14, 22], [14, 23]];
    for (const [x, y] of bolt) { put(im, x, y, [20, 20, 20]); put(im, x + 1, y, [20, 20, 20]); }
    return im;
  };
  // Pole number plate: white enamel, a black number.
  T.numplate = () => {
    const im = img(16, 16);
    rect(im, 0, 0, 16, 16, [228, 228, 222]); rect(im, 0, 0, 16, 1, [60, 60, 60]); rect(im, 0, 15, 16, 1, [60, 60, 60]);
    for (const x of [3, 7, 11]) { rect(im, x, 4, 3, 1, [20, 20, 20]); rect(im, x + 2, 4, 1, 8, [20, 20, 20]); rect(im, x, 11, 3, 1, [20, 20, 20]); rect(im, x, 7, 3, 1, [20, 20, 20]); }
    return im;
  };
  // Birch bark and leaves for the village tree.
  T.birch = () => {
    const im = img(32, 32), r = grain(im, [222, 220, 208], 3261, 4, 4, 8);
    for (let k = 0; k < 26; k++) { const x = Math.floor(r() * 30), y = Math.floor(r() * 32); rect(im, x, y, 2 + Math.floor(r() * 4), 1, [40, 38, 34]); }
    return im;
  };
  T.leaves = () => {
    const im = img(32, 32), r = grain(im, [74, 112, 48], 3271, 14, 10, 4);
    for (let k = 0; k < 60; k++) put(im, r() * 32, r() * 32, r() < 0.5 ? [44, 76, 30] : [116, 156, 70]);
    return im;
  };
  // Rectifier cabinet door louvres (RAL 7035): horizontal slots, a dark slot under a bright lip.
  T.louvre = () => {
    const im = img(32, 32), r = grain(im, [194, 196, 190], 3281, 3, 3, 8);
    for (let y = 1; y < 32; y += 3) for (let x = 3; x < 29; x++) { put(im, x, y, [214, 216, 210]); put(im, x, y + 1, [52, 54, 56]); }
    return im;
  };
  // Mercury-arc bulb glass: dark violet-tinted glass with highlights, and the lit glow of the arc.
  T.bulb = () => {
    const im = img(32, 32), r = rng(3291);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([70, 76, 96], Math.cos(x / 32 * Math.PI * 2) * 16 + (r() - 0.5) * 6));
    for (let y = 0; y < 32; y++) put(im, 6, y, [190, 200, 220]);
    return im;
  };
  T.bulb_lit = () => {
    const im = img(32, 32), r = rng(3301);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const c = Math.cos(x / 32 * Math.PI * 2);
      put(im, x, y, add(mix([110, 140, 255], [220, 230, 255], Math.max(0, c) * 0.6), (r() - 0.5) * 14));
    }
    return im;
  };
  // Blank white label strip.
  T.label = () => { const im = img(16, 16); grain(im, [226, 224, 214], 3311, 3, 2, 4); for (let x = 2; x < 14; x++) if (x % 3) put(im, x, 8, [70, 70, 70]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    wood: { tex: TEX + 'pole_wood', P: 16 }, cpole: { tex: TEX + 'pole_concrete', P: 16 }, porw: { tex: TEX + 'porcelain', P: 16 },
    alu: { tex: TEX + 'alu', P: 16 }, cable: { tex: TEX + 'cable', P: 16 }, logs: { tex: TEX + 'logs', P: 8 },
    brk: { tex: TEX + 'brick', P: 8 }, slate: { tex: TEX + 'slate', P: 16 }, tin: { tex: TEX + 'roof_tin', P: 16 },
    po2: { tex: TEX + 'fence_po2', P: 16 }, grav: { tex: TEX + 'gravel', P: 16 }, grass: { tex: TEX + 'grass', P: 16 },
    birch: { tex: TEX + 'birch', P: 16 }, leaves: { tex: TEX + 'leaves', P: 16 }, louv: { tex: TEX + 'louvre', P: 16 },
    bulb: { tex: TEX + 'bulb', P: 16 }, bulb_lit: { tex: TEX + 'bulb_lit', P: 16 },
    l_g: { tex: TEX + 'lamp_green', P: 16 }, l_r: { tex: TEX + 'lamp_red', P: 16 }, l_a: { tex: TEX + 'lamp_amber', P: 16 },
    l_w: { tex: TEX + 'lamp_white', P: 16 }, l_off: { tex: TEX + 'lamp_off', P: 16 },
    en: { tex: 'switchgear_cell/enamel', P: 16 }, fin: { tex: 'distribution_transformer/fins', P: 16 },
    tx: { tex: 'distribution_transformer/enamel', P: 16 }, porb: { tex: 'disconnector/porcelain', P: 16 },
    cu: { tex: 'disconnector/copper', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }

  // ---------------------------------------------------------------- shared parts
  // Small upright octagon from two boxes (square and square turned 45 degrees): cheap round parts. o.ends keeps
  // the top / bottom faces; the turned box sits 0.01 lower so the tops do not fight.
  function oct(m, n, x, z, r, y0, y1, mat, ends) {
    const e = ends || [], hide = ['up', 'down'].filter((d) => !e.includes(d));
    m.box(`${n}_a`, [x - r, y0, z - r], [x + r, y1, z + r], mat, { split: '', hide });
    m.box(`${n}_b`, [x - r, y0 + 0.01, z - r], [x + r, y1 - 0.01, z + r], mat, { split: '', hide, rot: { axis: 'y', angle: 45, origin: [x, 8, z] } });
  }
  // Pin insulator (ShF type) with its steel pin from y0: lower skirt, body, upper skirt, the groove the line wire
  // is tied into, the crown. s scales it (1 = ShF-20 at y0 + 7.6 to the crown).
  function pinInsulator(m, n, x, z, y0, s, pinH) {
    const h = pinH === undefined ? 1.2 : pinH;
    if (h > 0) oct(m, `${n}_pin`, x, z, 0.45 * s, y0, y0 + h, 'gal');
    const b = y0 + h;
    m.prismY(`${n}_skirt`, [x, z], 2.3 * s, b + 0.4 * s, b + 1.1 * s, 'porw', { ends: ['up', 'down'] });
    oct(m, `${n}_body`, x, z, 1.45 * s, b, b + 5 * s, 'porw', ['down']);
    m.prismY(`${n}_skirt2`, [x, z], 2 * s, b + 2.8 * s, b + 3.3 * s, 'porw', { ends: ['up', 'down'] });
    oct(m, `${n}_groove`, x, z, 1.05 * s, b + 5 * s, b + 5.6 * s, 'porw');
    oct(m, `${n}_crown`, x, z, 1.35 * s, b + 5.6 * s, b + 6.2 * s, 'porw', ['up', 'down']);
  }
  // Signal lamp lens in a black bezel on a face looking north at plane z.
  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    m.box(`${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber', { split: '' });
    m.box((glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { split: '', hide: ['south'] });
  }
  // Up-and-over steel brace at 45 degrees in the x-y plane on plane z0..z1, from (x0, y0) going `dir` (+1 / -1) in x.
  function brace(m, n, x0, y0, len, dir, z0, z1, mat) {
    const L = len * Math.SQRT2, cx = x0 + dir * len / 2, cy = y0 + len / 2;
    m.box(n, [cx - L / 2, cy - 0.25, z0], [cx + L / 2, cy + 0.25, z1], mat, { split: '', rot: { axis: 'z', angle: dir * 45, origin: [cx, cy, (z0 + z1) / 2] } });
  }

  // ---------------------------------------------------------------- poles
  // Wooden 0.4-10 kV pole: creosoted pine r 2.5. The base stands on a reinforced-concrete stub (pasynok) bound with
  // two wire bandages; the top carries a steel angle crossarm on braces and three pin insulators. An earth wire
  // runs down the west side of every link.
  function woodPole(part) {
    const m = mk(TEX + 'pole_wood');
    const top = part === 'top' ? 13 : 16;
    m.prismY('pole', [8, 8], 2.5, 0, top, 'wood', Object.assign({ tex: TEX + 'pole_wood', seed: part.length },
      part === 'top' ? { cap: TEX + 'pole_end' } : {}));
    m.box('earth_wire', [5.25, 0, 7.8], [5.55, top, 8.2], 'gal', { split: '' });
    for (let y = 2; y < top; y += 5) m.box(`staple_${y}`, [5.1, y, 7.6], [5.6, y + 0.3, 8.4], 'st', { split: '' });
    if (part === 'base') {
      m.box('stub', [10.4, 0, 6.6], [13.6, 12.4, 9.4], 'conc', { split: 'y' });
      m.box('stub_top', [10.6, 12.4, 6.8], [13.4, 12.8, 9.2], 'conc', { split: '' });
      for (const y of [2.6, 9.4]) {
        m.ringY(`band_${y}`, [8, 8], 2.75, 0.3, y, y + 0.7, 'gal');
        m.box(`band_n_${y}`, [8, y, 5.25], [13.8, y + 0.7, 5.55], 'gal', { split: '' });
        m.box(`band_s_${y}`, [8, y, 10.45], [13.8, y + 0.7, 10.75], 'gal', { split: '' });
        m.box(`band_e_${y}`, [13.6, y, 5.25], [13.8, y + 0.7, 10.75], 'gal', { split: '' });
      }
      m.box('collar', [3.6, 0, 3.6], [14.6, 0.5, 12.4], 'grav', { split: '' });
      m.plate('number', [6.8, 12.2, 5.1], [9.2, 14.4, 5.45], 'gal', 'north', TEX + 'numplate', { hide: ['south'] });
    }
    if (part === 'top') {
      // crossarm: steel angle across the north face, a U-bolt round the pole
      m.box('arm_h', [-6, 11.9, 4.0], [22, 12.4, 5.5], 'gal', { split: 'x' });
      m.box('arm_v', [-6, 10.4, 4.0], [22, 12.4, 4.5], 'gal', { split: 'x' });
      m.box('ubolt', [5.3, 10.8, 4.5], [10.7, 11.5, 10.8], 'st', { split: '' });
      brace(m, 'brace_w', 6, 3.6, 7, -1, 4.6, 5.2, 'gal');
      brace(m, 'brace_e', 10, 3.6, 7, 1, 4.6, 5.2, 'gal');
      m.box('brace_bolt', [6.6, 3.4, 4.2], [9.4, 4.4, 5.6], 'st', { split: '' });
      for (const [i, x] of [[0, -4], [1, 20]]) pinInsulator(m, `ins_${i}`, x, 4.75, 12.4, 0.62);
      m.box('top_hook', [7.6, 13, 7.6], [8.4, 14.4, 8.4], 'gal', { split: '' });
      pinInsulator(m, 'ins_top', 8, 8, 14.4, 0.62, 0);
    }
    return m;
  }

  // Concrete SV pole: tapered-looking rectangular section 5.6 x 4.8, through holes. The top has a steel cap, a
  // galvanised channel crossarm on clamps and braces and three pin insulators; the base a gravel collar, the
  // number plate, the warning plate and the earth outlet.
  function concretePole(part) {
    const m = mk(TEX + 'pole_concrete');
    const top = part === 'top' ? 14 : 16;
    m.box('pole', [5.2, 0, 5.6], [10.8, top, 10.4], 'cpole', { split: 'y', hide: part === 'top' ? [] : ['up', 'down'] });
    for (const [cx, cz] of [[5.2, 5.6], [10.8, 5.6], [5.2, 10.4], [10.8, 10.4]]) {
      m.box(`edge_${cx}_${cz}`, [cx - 0.15, 0, cz - 0.15], [cx + 0.15, top, cz + 0.15], 'cpole', { split: '', hide: ['up', 'down'] });
    }
    if (part === 'base') {
      m.box('collar', [3.4, 0, 3.6], [12.6, 0.6, 12.4], 'grav', { split: '' });
      m.plate('number', [6.6, 11.6, 5.25], [9.4, 13.8, 5.6], 'gal', 'north', TEX + 'numplate', { hide: ['south'] });
      m.plate('warn', [6.2, 7.4, 5.25], [9.8, 10.8, 5.6], 'gal', 'north', TEX + 'warn', { hide: ['south'] });
      m.box('earth_outlet', [10.8, 3, 7.4], [11.4, 4.4, 8.6], 'gal', { split: '' });
      m.box('earth_bolt', [11.4, 3.4, 7.7], [11.8, 4, 8.3], 'st', { split: '' });
    }
    if (part === 'top') {
      m.box('cap', [5, 14, 5.4], [11, 14.8, 10.6], 'gal', { split: '' });
      m.box('arm_web', [-6, 11.6, 4.1], [22, 13.4, 4.45], 'gal', { split: 'x' });
      m.box('arm_fl_top', [-6, 13.05, 4.45], [22, 13.4, 5.6], 'gal', { split: 'x' });
      m.box('arm_fl_bot', [-6, 11.6, 4.45], [22, 11.95, 5.6], 'gal', { split: 'x' });
      for (const y of [11.9, 12.8]) m.box(`clamp_${y}`, [5.0, y, 5.55], [11.0, y + 0.4, 10.6], 'st', { split: '' });
      brace(m, 'brace_w', 5.4, 4.2, 6.8, -1, 4.6, 5.2, 'gal');
      brace(m, 'brace_e', 10.6, 4.2, 6.8, 1, 4.6, 5.2, 'gal');
      m.box('brace_clamp', [5.0, 3.8, 5.0], [11.0, 4.6, 10.8], 'st', { split: '' });
      for (const [i, x] of [[0, -4], [1, 20]]) pinInsulator(m, `ins_${i}`, x, 4.85, 13.4, 0.66);
      m.box('top_pin_base', [7, 14.8, 7], [9, 15.2, 9], 'gal', { split: '' });
      pinInsulator(m, 'ins_top', 8, 8, 15.2, 0.66, 0.5);
    }
    return m;
  }

  // ---------------------------------------------------------------- wire connectors
  // LV roller insulator (RF type) on a wall hook: back plate, a hook arm with the yellow 0.4 kV band, the porcelain
  // roller with its groove at y 8.8, the through bolt.
  function connectorLv() {
    const m = mk(TEX + 'porcelain');
    m.box('plate', [6.4, 4.2, 15.3], [9.6, 9.8, 16], 'st', { split: '' });
    for (const y of [4.8, 9]) m.box(`screw_${y}`, [7.6, y, 15.1], [8.4, y + 0.6, 15.3], 'gal', { split: '' });
    m.box('arm', [7.6, 4.4, 8], [8.4, 5.2, 15.3], 'st', { split: '' });
    m.box('band', [7.45, 4.25, 12], [8.55, 5.35, 13.2], 'yel', { split: '' });
    m.prismY('flange_lo', [8, 8], 2, 5.2, 6.6, 'porw', { ends: ['up', 'down'] });
    m.prismY('waist', [8, 8], 1.4, 6.6, 8.2, 'porw');
    m.prismY('groove', [8, 8], 1.1, 8.2, 9.4, 'porw');
    m.prismY('flange_hi', [8, 8], 1.8, 9.4, 10, 'porw', { ends: ['up', 'down'] });
    m.prismY('bolt', [8, 8], 0.5, 10, 10.4, 'gal', { ends: ['up'] });
    return m;
  }
  // MV pin insulator (ShF-20) on its steel pin and a base plate; the crown at y 12 carries the span.
  function connectorMv() {
    const m = mk(TEX + 'porcelain');
    m.box('base', [6, 0, 6], [10, 0.4, 10], 'gal', { split: '' });
    for (const [x, z] of [[6.4, 6.4], [9, 6.4], [6.4, 9], [9, 9]]) m.box(`bolt_${x}_${z}`, [x, 0.4, z], [x + 0.6, 0.7, z + 0.6], 'st', { split: '' });
    m.prismY('collar', [8, 8], 0.9, 0.4, 1.2, 'gal', { ends: ['up'] });
    pinInsulator(m, 'ins', 8, 8, 1.2, 1.29, 3.2);
    return m;
  }

  // ---------------------------------------------------------------- busbar and cable
  // Outdoor busbar: aluminium tube on a brown porcelain post insulator on a galvanised pedestal; the clamp carries
  // a short tube and the phase colour.
  function busbar() {
    const m = mk(TEX + 'alu');
    m.box('foot', [5.5, 0, 5.5], [10.5, 0.4, 10.5], 'gal', { split: '' });
    m.box('pedestal', [6.4, 0.4, 6.4], [9.6, 2.8, 9.6], 'gal', { split: '' });
    m.prismY('flange_lo', [8, 8], 1.3, 2.8, 3.4, 'ci', { ends: ['up'] });
    m.prismY('core', [8, 8], 0.85, 3.4, 12.2, 'porb');
    for (let y = 4, i = 0; y < 11.8; y += 1.3, i++) m.prismY(`shed_${i}`, [8, 8], 1.8, y, y + 0.45, 'porb', { ends: ['up', 'down'] });
    m.prismY('flange_hi', [8, 8], 1.3, 12.2, 12.8, 'ci', { ends: ['down'] });
    m.box('clamp', [6.3, 12.8, 6.6], [9.7, 15.6, 9.4], 'alu', { split: '' });
    m.box('phase', [6.2, 15.6, 6.5], [9.8, 16, 9.5], 'yel', { split: '' });
    m.prism('tube', 'z', [8, 14.5], 1.5, 6, 10, 'alu', { split: '' });
    return m;
  }
  function busbarSide() {
    const m = mk(TEX + 'alu');
    m.prism('tube', 'z', [8, 14.5], 1.5, 0, 6.6, 'alu', { split: '' });
    return m;
  }
  // Power cable: a jointing sleeve at the node, the jute-covered cable to each neighbour with a yellow band.
  function cable() {
    const m = mk(TEX + 'cable');
    m.box('joint', [5.5, 5.5, 5.5], [10.5, 10.5, 10.5], 'cable', { split: '' });
    return m;
  }
  function cableSide() {
    const m = mk(TEX + 'cable');
    m.prism('cable', 'z', [8, 8], 2, 0, 5.7, 'cable', { split: '' });
    m.box('band', [5.95, 5.95, 2.4], [10.05, 10.05, 3], 'yel', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- wall gear
  // LV distribution box (ShchRN): grey steel box on the wall, a hinged door with a window over a row of breaker
  // toggles, lock, warning plate, label, the state lamp, cable glands below. Tripped: one toggle down, lamp red.
  function lvBox(tripped) {
    const m = mk('switchgear_cell/enamel');
    m.box('body', [2.6, 1, 11.2], [13.4, 13.4, 16], 'en', { split: '' });
    m.box('door', [3, 1.4, 10.8], [13, 13, 11.2], 'en', { split: '' });
    m.box('drip', [2.3, 13.4, 10.6], [13.7, 13.9, 16], 'en', { split: '' });
    for (const y of [2.4, 10.6]) m.box(`hinge_${y}`, [2.7, y, 10.7], [3.1, y + 1.6, 11.2], 'st', { split: '' });
    m.box('window', [4.2, 7.4, 10.7], [11.4, 10.4, 10.8], 'soot', { split: '' });
    for (let i = 0; i < 5; i++) {
      const x = 4.8 + i * 1.3, down = tripped && i === 2;
      m.box(`breaker_${i}`, [x - 0.1, 7.7, 10.6], [x + 0.9, 10.1, 10.7], 'light', { split: '' });
      m.box(`toggle_${i}`, [x + 0.15, down ? 7.9 : 8.9, 10.2], [x + 0.65, down ? 8.9 : 9.9, 10.6], down ? 'red' : 'rubber', { split: '' });
    }
    m.plate('warn', [6.2, 10.9, 10.65], [9.0, 12.7, 10.8], 'en', 'north', TEX + 'warn', { hide: ['south'] });
    m.plate('label', [4.2, 3.6, 10.65], [8.4, 5, 10.8], 'en', 'north', TEX + 'label', { hide: ['south'] });
    m.box('lock', [11.6, 5.6, 10.5], [12.4, 6.8, 10.8], 'rubber', { split: '' });
    lamp(m, 'lamp', 10.6, 4.3, 10.8, tripped ? 'l_r' : 'l_g', true, 0.5);
    for (const x of [5, 11]) {
      m.prismY(`gland_${x}`, [x, 13.6], 0.8, 0.5, 1, 'rubber', { ends: ['down'] });
      m.prismY(`cable_${x}`, [x, 13.6], 0.55, 0, 0.5, 'cable');
    }
    return m;
  }
  // ---------------------------------------------------------------- consumer substation and village
  // A diorama in the 3x3x3 block (about 1:4): the 10/0.4 kV substation yard behind a PO-2 concrete fence (an oil
  // transformer, the kiosk with its doors and warning plates, the incoming line pole), two log houses and a brick
  // house of the village with windows lit while the consumer is fed, a street lamp, a birch, the settlement sign.
  function consumer(lit) {
    const m = mk(TEX + 'brick');
    const Y = -16, G = Y + 0.5;
    m.box('ground_yard', [-16, Y, -16], [6, G, 6], 'grav', { split: 'xz' });
    m.box('ground_v1', [6, Y, -16], [32, G, 6], 'grass', { split: 'xz' });
    m.box('ground_v2', [-16, Y, 6], [32, G, 32], 'grass', { split: 'xz' });
    // fence: PO-2 panels round the yard x -15..5, z -15..5, a steel gate in the north side
    const FH = G + 6.5;
    m.box('fence_w', [-15, G, -15], [-14.5, FH, 5], 'po2', { split: 'z' });
    m.box('fence_e', [4.5, G, -15], [5, FH, 5], 'po2', { split: 'z' });
    m.box('fence_s', [-14.5, G, 4.5], [4.5, FH, 5], 'po2', { split: 'x' });
    m.box('fence_nw', [-14.5, G, -15], [-8, FH, -14.5], 'po2', { split: 'x' });
    m.box('fence_ne', [-2, G, -15], [4.5, FH, -14.5], 'po2', { split: 'x' });
    for (const x of [-15.2, -8.2, -2.2, 4.3]) m.box(`post_${x}`, [x, G, -15.2], [x + 0.9, FH + 0.4, -14.3], 'conc', { split: '' });
    m.box('gate_l', [-8, G + 0.3, -14.9], [-5.05, FH - 0.6, -14.6], 'green', { split: '' });
    m.box('gate_r', [-4.95, G + 0.3, -14.9], [-2, FH - 0.6, -14.6], 'green', { split: '' });
    m.plate('gate_warn', [-4.6, G + 3.2, -15.05], [-2.6, G + 5.2, -14.9], 'green', 'north', TEX + 'warn', { hide: ['south'] });
    // oil transformer TM-250 on its plinth: corrugated tank, cover, three HV and four LV bushings, conservator
    m.box('tx_plinth', [-13, G, -12], [-3, G + 1, -4], 'conc', { split: '' });
    m.box('tx_tank', [-12, G + 1, -11], [-4, G + 7, -5], 'fin', { split: 'x' });
    m.box('tx_cover', [-12.4, G + 7, -11.4], [-3.6, G + 7.6, -4.6], 'tx', { split: '' });
    for (const [i, x] of [[0, -10.4], [1, -8], [2, -5.6]]) m.cylY(`tx_hv_${i}`, [x, -6.2], 0.55, G + 7.6, G + 10.6, 'porb', { cap: TEX + 'porcelain' });
    for (let i = 0; i < 4; i++) m.box(`tx_lv_${i}`, [-11 + i * 1.6, G + 7.6, -10.4], [-10.4 + i * 1.6, G + 9, -9.8], 'porb', { split: '' });
    m.box('tx_conservator', [-12, G + 9.6, -6], [-4.6, G + 11, -4.6], 'tx', { split: '' });
    for (const x of [-11.4, -5.4]) m.box(`tx_cons_leg_${x}`, [x, G + 7.6, -5.6], [x + 0.5, G + 9.6, -5.1], 'tx', { split: '' });
    // kiosk (KTP): enamel cabinet with two doors, a roof, warning plates, an LV cable going out underground
    m.box('ktp', [-2, G, -12], [3.8, G + 9, -3], 'en', { split: '' });
    m.box('ktp_roof', [-2.5, G + 9, -12.5], [4.3, G + 9.6, -2.5], 'en', { split: '' });
    for (const [n, z0] of [['a', -11.6], ['b', -7.4]]) {
      m.box(`ktp_door_${n}`, [3.8, G + 0.6, z0], [4.1, G + 8.2, z0 + 3.8], 'en', { split: '' });
      m.box(`ktp_handle_${n}`, [4.1, G + 4, z0 + 3], [4.4, G + 5.2, z0 + 3.4], 'rubber', { split: '' });
      m.plate(`ktp_warn_${n}`, [4.1, G + 6, z0 + 1], [4.25, G + 7.6, z0 + 2.6], 'en', 'east', TEX + 'warn', { hide: ['west'] });
    }
    m.box('ktp_lv_out', [-1, G + 0.4, -3], [0.6, G + 2, -1], 'cable', { split: '' });
    // incoming 10 kV line: concrete pole in the yard with a crossarm, three insulators and drops to the bushings
    m.box('in_pole', [-14, G, -1.6], [-12, 30, 0.4], 'cpole', { split: 'y' });
    m.box('in_arm', [-15.6, 27, -2.4], [-6, 27.6, -1.6], 'gal', { split: '' });
    for (const [i, x] of [[0, -15], [1, -10.4], [2, -7]]) {
      m.box(`in_ins_${i}`, [x - 0.5, 27.6, -2.5], [x + 0.5, 29.2, -1.5], 'porw', { split: '' });
      m.box(`in_ins_skirt_${i}`, [x - 0.8, 28, -2.8], [x + 0.8, 28.4, -1.2], 'porw', { split: '' });
    }
    m.box('in_drop', [-10.6, G + 11, -6.6], [-10.2, 27, -6.2], 'alu', { split: '' });
    m.box('in_bus', [-10.6, 26.6, -6.6], [-10.2, 27, -2], 'alu', { split: '' });
    // log house 1 (north-east), gable along x, painted window frames
    house(m, 'h1', [9, G, -13], [23, G + 9, -3], 'logs', 'slate', lit, 'x');
    // brick house (south-east), gable along z, tin roof
    house(m, 'h2', [12, G, 10], [26, G + 10, 26], 'brk', 'tin', lit, 'z');
    // log house 2 (south-west)
    house(m, 'h3', [-13, G, 12], [1, G + 8, 25], 'logs', 'slate', lit, 'x');
    // street lamp on a wooden post
    m.box('lamp_post', [6.6, G, 6.6], [7.4, G + 14, 7.4], 'wood', { split: '' });
    m.box('lamp_arm', [6.8, G + 13.4, 5], [7.2, G + 13.8, 7.4], 'st', { split: '' });
    m.box((lit ? 'glow_' : '') + 'lamp_head', [6.4, G + 12.6, 4.4], [7.6, G + 13.4, 5.6], lit ? 'l_w' : 'l_off', { split: '' });
    // birch
    m.box('birch_trunk', [27, G, 1], [28, G + 16, 2], 'birch', { split: 'y' });
    m.box('birch_crown', [24.4, G + 11, -1.6], [30.6, G + 19, 4.6], 'leaves', { split: '' });
    m.box('birch_crown_n', [25.4, G + 13, -3], [29.4, G + 18, -1.6], 'leaves', { split: '' });
    m.box('birch_crown_e', [30.6, G + 12.6, -0.4], [31.8, G + 17, 3.4], 'leaves', { split: '' });
    m.box('birch_crown_top', [25.6, G + 19, -0.4], [29.4, G + 22.4, 3.4], 'leaves', { split: '' });
    // settlement sign at the road
    for (const x of [24.4, 30.2]) m.box(`sign_post_${x}`, [x, G, -15.4], [x + 0.6, G + 7, -14.8], 'gal', { split: '' });
    m.plate('sign', [23.6, G + 4, -15.6], [31.6, G + 7.6, -15.4], 'light', 'north', TEX + 'sign_village');
    // service drop on insulators from the kiosk to the house
    m.box('drop_pole', [6.2, G, -9.4], [7, G + 11, -8.6], 'wood', { split: '' });
    m.box('drop_wire', [4, G + 10.4, -9.1], [9, G + 10.6, -8.9], 'rubber', { split: '' });
    return m;
  }
  // A small house with a pitched roof and windows on the long sides; the ridge runs along `ridge`.
  function house(m, n, f, t, wall, roof, lit, ridge) {
    m.box(`${n}_plinth`, [f[0] - 0.3, f[1], f[2] - 0.3], [t[0] + 0.3, f[1] + 0.8, t[2] + 0.3], 'conc', { split: '' });
    m.box(`${n}_walls`, [f[0], f[1] + 0.8, f[2]], t, wall, { split: '' });
    const win = lit ? TEX + 'window_lit' : TEX + 'window_dark', wy = f[1] + 3;
    if (ridge === 'x') {
      for (let x = f[0] + 2; x + 2.4 <= t[0] - 1; x += 4) {
        for (const [side, z, dir] of [['n', f[2], 'north'], ['s', t[2], 'south']]) {
          const s = side === 'n' ? -1 : 1;
          m.box(`${n}_frame_${side}_${x}`, [x - 0.3, wy - 0.4, z + s * 0.0 - (s < 0 ? 0.25 : 0)], [x + 2.7, wy + 3.4, z + (s > 0 ? 0.25 : 0)], 'light', { split: '' });
          m.plate((lit ? 'glow_' : '') + `${n}_win_${side}_${x}`, [x, wy, z - (s < 0 ? 0.35 : 0)], [x + 2.4, wy + 3, z + (s > 0 ? 0.35 : 0)], 'light', dir, win, { hide: [dir === 'north' ? 'south' : 'north'] });
        }
      }
    } else {
      for (let z = f[2] + 2; z + 2.4 <= t[2] - 1; z += 4) {
        for (const [side, x, dir] of [['w', f[0], 'west'], ['e', t[0], 'east']]) {
          const s = side === 'w' ? -1 : 1;
          m.box(`${n}_frame_${side}_${z}`, [x - (s < 0 ? 0.25 : 0), wy - 0.4, z - 0.3], [x + (s > 0 ? 0.25 : 0), wy + 3.4, z + 2.7], 'light', { split: '' });
          m.plate((lit ? 'glow_' : '') + `${n}_win_${side}_${z}`, [x - (s < 0 ? 0.35 : 0), wy, z], [x + (s > 0 ? 0.35 : 0), wy + 3, z + 2.4], 'light', dir, win, { hide: [dir === 'west' ? 'east' : 'west'] });
        }
      }
    }
    // roof: two slopes at 45 degrees from the eaves to the ridge, gable ends filled in the wall material by steps
    const along = ridge === 'x' ? 0 : 2, across = ridge === 'x' ? 2 : 0;
    const half = (t[across] - f[across]) / 2, mid = (t[across] + f[across]) / 2, H = half; // 45 degrees
    const L = half * Math.SQRT2 + 1, eave = t[1];
    for (const s of [-1, 1]) {
      const cA = mid + s * half / 2, cY = eave + H / 2;
      const a0 = f[along] - 0.8, a1 = t[along] + 0.8;
      const bf = [0, cY - 0.3, 0], bt = [0, cY + 0.3, 0];
      bf[along] = a0; bt[along] = a1; bf[across] = cA - L / 2; bt[across] = cA + L / 2;
      const axis = ridge === 'x' ? 'x' : 'z';
      const sign = ridge === 'x' ? s : -s;
      const origin = [0, cY, 0]; origin[along] = (a0 + a1) / 2; origin[across] = cA;
      m.box(`${n}_roof_${s}`, bf, bt, roof, { split: '', rot: { axis, angle: sign * 45, origin } });
    }
    for (let k = 0; k < 4; k++) {
      const w = half * (1 - (k + 1) / 4) - 0.3, y0 = eave + H * k / 4, y1 = eave + H * (k + 1) / 4;
      if (w <= 0) continue;
      const bf = [0, y0, 0], bt = [0, y1, 0];
      bf[along] = f[along]; bt[along] = t[along]; bf[across] = mid - w; bt[across] = mid + w;
      m.box(`${n}_gable_${k}`, bf, bt, wall, { split: '' });
    }
    const cf = [0, eave + H - 1, 0], ct = [0, eave + H + 1.6, 0];
    cf[along] = f[along] + 2; ct[along] = f[along] + 3.4; cf[across] = mid + 1; ct[across] = mid + 2.4;
    m.box(`${n}_chimney`, cf, ct, 'brk', { split: '' });
  }

  // ---------------------------------------------------------------- rectifier unit
  // Thyristor rectifier (VAKV type): two cabinets in RAL 7035 on a channel plinth, louvred double doors, a meter
  // panel (ammeter and voltmeter), the state lamps, the DC busbars out through the roof on insulators. The mercury
  // variant keeps the control cabinet on the right and puts the glass mercury-arc valve, lit blue in operation,
  // in an open frame on the left.
  function rectifier(state) {
    const m = mk('switchgear_cell/enamel');
    m.box('plinth', [0, 0, 1], [32, 1, 16], 'rubber', { split: 'x' });
    const mercury = state === 'mercury';
    for (const c of mercury ? [1] : [0, 1]) cabinet(m, c, state);
    if (mercury) mercuryValve(m);
    // DC outlets: two copper bars up through the roof on standoff insulators
    for (const x of [21, 26]) {
      m.cylY(`dc_ins_${x}`, [x, 9], 0.7, 30.6, 31.2, 'porb', {});
      m.box(`dc_bar_${x}`, [x - 0.6, 31.2, 4], [x + 0.6, 32, 14], 'cu', { split: '' });
    }
    m.plate('nameplate', [13, 1.6, 0.85], [19, 3.2, 1], 'en', 'north', TEX + 'label', { hide: ['south'] });
    return m;
  }
  function cabinet(m, c, state) {
    const x0 = c * 16, x1 = x0 + 16;
    m.box(`cab_${c}`, [x0 + 0.2, 1, 1.4], [x1 - 0.2, 30, 16], 'en', { split: 'y' });
    m.box(`cab_roof_${c}`, [x0, 30, 1], [x1, 30.6, 16], 'en', { split: '' });
    for (const [d, dx0, dx1] of [['l', x0 + 0.6, x0 + 7.9], ['r', x0 + 8.1, x1 - 0.6]]) {
      m.box(`door_${c}${d}`, [dx0, 1.6, 1], [dx1, 22, 1.4], 'en', { split: 'y' });
      m.box(`louvre_lo_${c}${d}`, [dx0 + 0.8, 2.6, 0.95], [dx1 - 0.8, 7.4, 1], 'louv', { split: '', hide: ['south'] });
      m.box(`louvre_hi_${c}${d}`, [dx0 + 0.8, 15.4, 0.95], [dx1 - 0.8, 20.6, 1], 'louv', { split: '', hide: ['south'] });
    }
    m.box(`handle_${c}`, [x0 + 7.3, 10, 0.6], [x0 + 7.7, 13, 1], 'rubber', { split: '' });
    m.box(`handle_r_${c}`, [x0 + 8.3, 10, 0.6], [x0 + 8.7, 13, 1], 'rubber', { split: '' });
    m.plate(`warn_${c}`, [x0 + 3, 11, 0.85], [x0 + 5.6, 13.6, 1], 'en', 'north', TEX + 'warn', { hide: ['south'] });
    // top panel: meters on the left cabinet, lamps and keys on the right one
    m.box(`panel_${c}`, [x0 + 0.6, 22.4, 1], [x1 - 0.6, 29.6, 1.4], 'en', { split: '' });
    if (c === 0) {
      for (const x of [x0 + 2, x0 + 9]) {
        m.box(`meter_case_${x}`, [x, 23.4, 0.6], [x + 5, 28.4, 1], 'rubber', { split: '' });
        m.plate(`meter_${x}`, [x + 0.4, 23.8, 0.5], [x + 4.6, 28, 0.6], 'rubber', 'north', 'material/gauge_dial', { hide: ['south'] });
      }
    } else {
      const on = state === 'on' || state === 'mercury', fault = state === 'fault';
      lamp(m, 'lamp_run', x0 + 3, 27, 1, on ? 'l_g' : 'l_off', on, 0.7);
      lamp(m, 'lamp_fault', x0 + 6.4, 27, 1, fault ? 'l_r' : 'l_off', fault, 0.7);
      lamp(m, 'lamp_warn', x0 + 9.8, 27, 1, fault ? 'l_a' : 'l_off', fault, 0.7);
      for (const x of [x0 + 3, x0 + 6.4]) {
        m.prism(`key_${x}`, 'z', [x, 24], 0.8, 0.4, 1, 'rubber', { split: '', ends: ['north'] });
      }
      m.box('key_switch', [x0 + 9.2, 23.4, 0.5], [x0 + 10.4, 24.8, 1], on || fault ? 'red' : 'rubber', { split: '' });
      m.plate('mimic', [x0 + 12, 23, 0.85], [x1 - 1, 28.6, 1], 'en', 'north', TEX + 'label', { hide: ['south'] });
    }
  }
  function mercuryValve(m) {
    // open steel frame with a mesh guard at the back, the valve on an insulating stool
    for (const [x, z] of [[0.4, 1.2], [14.8, 1.2], [0.4, 14.8], [14.8, 14.8]]) m.box(`frame_${x}_${z}`, [x, 1, z], [x + 0.8, 30, z + 0.8], 'frame', { split: 'y' });
    m.box('frame_top', [0.4, 29.4, 1.2], [15.6, 30.6, 15.6], 'frame', { split: '' });
    m.box('frame_back', [1.2, 1, 15.2], [14.8, 29.4, 15.6], 'soot', { split: 'y' });
    m.box('stool', [3, 1, 4], [13, 3, 14], 'porb', { split: '' });
    m.cylY('cathode', [8, 9], 3.6, 3, 6, 'st', {});
    m.cylY('glow_bulb', [8, 9], 5, 6, 21, 'bulb_lit', {});
    m.cylY('glow_dome', [8, 9], 3.4, 21, 25, 'bulb_lit', { cap: TEX + 'bulb_lit' });
    // six anode arms round the bulb, each with a graphite anode cap and a lead
    for (let k = 0; k < 6; k++) {
      const a = k * 60, rad = a * Math.PI / 180, x = 8 + Math.cos(rad) * 6, z = 9 - Math.sin(rad) * 6;
      m.box(`glow_arm_${k}`, [x - 0.9, 16, z - 0.9], [x + 0.9, 22, z + 0.9], 'bulb_lit', { split: '' });
      m.box(`anode_${k}`, [x - 0.6, 22, z - 0.6], [x + 0.6, 23.4, z + 0.6], 'soot', { split: '' });
      m.box(`lead_${k}`, [x - 0.2, 23.4, z - 0.2], [x + 0.2, 27.4, z + 0.2], 'cu', { split: '' });
    }
    m.box('lead_bus', [1.6, 27.4, 1.6], [14.4, 28, 2.4], 'cu', { split: '' });
    for (const x of [2, 14]) m.box(`lead_bus_side_${x}`, [x - 0.4, 27.4, 2.4], [x + 0.4, 28, 15], 'cu', { split: '' });
  }

  const BUILDS = {
    wooden_pole_bottom: { model: () => woodPole('base') },
    wooden_pole: { model: () => woodPole('middle') },
    wooden_pole_top: { model: () => woodPole('top') },
    concrete_pole_bottom: { model: () => concretePole('base') },
    concrete_pole: { model: () => concretePole('middle') },
    concrete_pole_top: { model: () => concretePole('top') },
    wire_connector_lv: { model: connectorLv },
    wire_connector_mv: { model: connectorMv },
    busbar: { model: busbar },
    busbar_side: { model: busbarSide },
    power_cable: { model: cable },
    power_cable_side: { model: cableSide },
    lv_distribution_box: { model: () => lvBox(false) },
    lv_distribution_box_tripped: { model: () => lvBox(true) },
    grid_consumer: { model: () => consumer(true) },
    grid_consumer_dark: { model: () => consumer(false) },
    rectifier_unit: { model: () => rectifier('on') },
    rectifier_unit_off: { model: () => rectifier('off') },
    rectifier_unit_fault: { model: () => rectifier('fault') },
    rectifier_unit_mercury: { model: () => rectifier('mercury') },
  };

  window.GRL = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'grid', id, BUILDS[id].java),
    // Several models side by side (x step `dx`), for looking at a family at once.
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    exportAll: (root, ids) => PCART.exportAll(root, 'grid', ids ? Object.fromEntries(ids.map((i) => [i, BUILDS[i]])) : BUILDS),
  };
})();
