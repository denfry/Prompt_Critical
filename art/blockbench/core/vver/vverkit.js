// Shared kit of the real-scale VVER-1000 unit (1 block = 1 m, 16 model units = 1 m), on top of pcart.js and
// pcround.js. Load inside Blockbench desktop in this order:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcslice.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/vver/vverkit.js', 'utf8'))
//   PCVV.paint('<repo>')
// then a machine script (vver_reactor.author.js, ...): VVER_<X>.build(repo) and VVER_<X>.save(repo).
//
// Whole models are cut by PCSLICE into 3x3x3-block chunks. Axis-aligned boxes are cut by the slicer itself; a
// rotated box has to sit inside one chunk, so PCVV.cut splits every rotated box on the 48-unit chunk grid in its own
// (unrotated) frame, all pieces keeping the box's rotation origin: turned about the same point the pieces still make
// up the original box exactly. Faces that land on a cut are dropped and explicit UVs are interpolated as the slicer
// does it, so big 16-gons (pcround cylY, pcart prism/disc/ring) can be used at any size.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, writePng } = PCART;
  const T = {};
  const G = 48;

  // ------------------------------------------------------------------ lettering and signs
  const FONT = {
    'В': ['110', '101', '110', '101', '110'], 'Э': ['110', '001', '011', '001', '110'], 'Р': ['110', '101', '110', '100', '100'],
    'П': ['111', '101', '101', '101', '101'], 'Г': ['111', '100', '100', '100', '100'], 'Ц': ['101', '101', '101', '111', '001'],
    'Н': ['101', '101', '111', '101', '101'], 'К': ['101', '110', '100', '110', '101'], 'Д': ['011', '101', '101', '111', '101'],
    'М': ['101', '111', '111', '101', '101'], 'А': ['010', '101', '111', '101', '101'], 'Т': ['111', '010', '010', '010', '010'],
    'Е': ['111', '100', '110', '100', '111'], 'Л': ['011', '101', '101', '101', '101'], 'О': ['111', '101', '101', '101', '111'],
    'С': ['111', '100', '100', '100', '111'], 'Я': ['111', '101', '111', '011', '101'], 'Ч': ['101', '101', '111', '001', '001'],
    '-': ['000', '000', '111', '000', '000'], '.': ['000', '000', '000', '000', '010'],
    '0': ['111', '101', '101', '101', '111'], '1': ['010', '110', '010', '010', '111'], '2': ['111', '001', '111', '100', '111'],
    '3': ['111', '001', '011', '001', '111'], '4': ['101', '101', '111', '001', '001'], '5': ['111', '100', '111', '001', '111'],
    '6': ['111', '100', '111', '101', '111'], '7': ['111', '001', '010', '010', '010'], '8': ['111', '101', '111', '101', '111'],
    '9': ['111', '101', '111', '001', '111'],
  };
  function stencil(im, x, y, text, col) {
    for (const ch of text) {
      const g = FONT[ch];
      if (g) g.forEach((row, j) => [...row].forEach((b, i) => { if (b === '1') put(im, x + i, y + j, col); }));
      x += 4;
    }
  }
  // Radiation sign (GOST 17925): black trefoil on yellow, black border.
  function trefoil(im, x0, y0, n) {
    const c = n / 2;
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
      const dx = x + 0.5 - c, dy = y + 0.5 - c, d = Math.hypot(dx, dy) / c, a = Math.atan2(dy, dx) * 180 / Math.PI;
      let col = [214, 178, 40];
      const sector = (((a + 90) % 120) + 120) % 120 < 60;
      if (d < 0.16 || (d > 0.24 && d < 0.78 && sector)) col = [24, 22, 20];
      if (x === 0 || y === 0 || x === n - 1 || y === n - 1) col = [24, 22, 20];
      put(im, x0 + x, y0 + y, col);
    }
  }
  function namePlate(im, x, y, w, h, text) {
    rect(im, x, y, w, h, [226, 222, 208]);
    for (let i = 0; i < w; i++) { put(im, x + i, y, [40, 40, 40]); put(im, x + i, y + h - 1, [40, 40, 40]); }
    for (let j = 0; j < h; j++) { put(im, x, y + j, [40, 40, 40]); put(im, x + w - 1, y + j, [40, 40, 40]); }
    stencil(im, x + Math.round((w - text.length * 4 + 1) / 2), y + Math.round((h - 5) / 2), text, [30, 30, 30]);
  }

  // ------------------------------------------------------------------ textures (textures/block/vver)
  // Metal-clad thermal insulation: aluminium sheets, lap seams every metre-ish tile, screws along the laps.
  T['vver/clad'] = () => {
    const im = img(32, 32), r = grain(im, [182, 184, 182], 4101, 4, 6, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (y === 31) put(im, x, y, [124, 128, 130]);
      if (y === 0) put(im, x, y, [212, 214, 212]);
      if (x === 31) put(im, x, y, [138, 142, 144]);
      if (x === 0) put(im, x, y, [200, 202, 200]);
    }
    for (let x = 3; x < 32; x += 7) { put(im, x, 2, [108, 110, 112]); put(im, x, 29, [108, 110, 112]); }
    for (let k = 0; k < 5; k++) { const x = Math.floor(r() * 30), y = Math.floor(4 + r() * 22); for (let j = 0; j < 4; j++) put(im, x, y + j, add(get(im, x, y + j), -9)); }
    return im;
  };
  // Bare forged vessel steel (flanges, nozzle safe ends): dark, machined rings, a faint temper tint.
  T['vver/forged'] = () => {
    const im = img(32, 32), r = rng(4111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add(mix([96, 100, 106], [110, 96, 84], (Math.sin(x * 0.3) + 1) * 0.12), (y % 3 === 0 ? 6 : 0) + (r() - 0.5) * 8));
    }
    return im;
  };
  // Grey-green enamel of the upper unit and the steel structures, chipped.
  T['vver/paint'] = () => {
    const im = img(32, 32), r = grain(im, [128, 150, 136], 4121, 3, 5, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [90, 94, 92]);
    return im;
  };
  // Pump motor frame: blue-grey enamel with the cooling ribs of the stator housing.
  T['vver/motor'] = () => {
    const im = img(32, 32), r = grain(im, [86, 108, 128], 4131, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (x % 4 === 0) put(im, x, y, add(get(im, x, y), 22));
      if (x % 4 === 1) put(im, x, y, add(get(im, x, y), -14));
    }
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [60, 66, 70]);
    return im;
  };
  // Smooth enamel of the motor caps and covers (no ribs).
  T['vver/motor_cap'] = () => {
    const im = img(32, 32), r = grain(im, [90, 112, 132], 4132, 3, 4, 8);
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [64, 70, 74]);
    return im;
  };
  // Control rod drive (ShEM) housing: stainless tube with electromagnet coil bands.
  T['vver/drive'] = () => {
    const im = img(32, 32), r = rng(4141);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const band = y % 8 < 5;
      let c = band ? [58, 70, 88] : [152, 156, 160];
      if (band && y % 8 === 0) c = [86, 98, 116];
      if (!band && y % 8 === 7) c = [118, 122, 126];
      put(im, x, y, add(c, (r() - 0.5) * 8));
    }
    return im;
  };
  T['vver/yellow'] = () => {
    const im = img(16, 16), r = grain(im, [206, 166, 42], 4151, 3, 5, 4);
    for (let k = 0; k < 4; k++) put(im, r() * 16, r() * 16, [96, 84, 50]);
    return im;
  };
  // GOST 14202 identification colours: water green, steam red, oil (flammable liquid) brown.
  T['vver/gost_green'] = () => grainOnly([50, 118, 66], 4161);
  T['vver/gost_red'] = () => grainOnly([164, 40, 32], 4162);
  T['vver/gost_brown'] = () => grainOnly([110, 72, 42], 4163);
  function grainOnly(c, seed) { const im = img(32, 32); grain(im, c, seed, 4, 5, 8); return im; }
  // Rubber, bitumen and shadowed recesses.
  T['vver/dark'] = () => grainOnly([46, 48, 50], 4171);
  // Steel grating, bars every 2 px one way and 8 px the other.
  T['vver/grating'] = () => {
    const im = img(32, 32), r = rng(4181);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const bar = x % 3 === 0 || y % 8 === 0;
      put(im, x, y, bar ? add([140, 148, 142], (r() - 0.5) * 8) : [34, 36, 38]);
    }
    return im;
  };
  // Grating disc for platforms round the vessels (radial texture), yellow edge strip.
  T['vver/grating_disc'] = () => {
    const im = img(64, 64), r = rng(4182);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      const bar = x % 3 === 0 || y % 8 === 0;
      let c = bar ? add([140, 148, 142], (r() - 0.5) * 8) : [34, 36, 38];
      if (d > 0.94) c = [196, 158, 40];
      put(im, x, y, c);
    }
    return im;
  };
  // Machined flange face from above: stud holes ring, gasket groove.
  T['vver/flange'] = () => {
    const im = img(64, 64), r = rng(4191);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32, a = Math.atan2(y + 0.5 - 32, x + 0.5 - 32);
      let c = add([138, 142, 148], Math.sin(d * 120) * 5 + (r() - 0.5) * 6);
      if (Math.abs(d - 0.62) < 0.02) c = [84, 88, 92];
      if (d > 0.97) c = [100, 104, 108];
      const k = Math.round(a / (Math.PI / 18)), ad = Math.abs(a - k * Math.PI / 18) * 32 * 0.86;
      if (Math.abs(d - 0.86) < 0.05 && ad < 1.3) c = [50, 52, 56];
      put(im, x, y, c);
    }
    return im;
  };
  // Insulation on a head from above: petal sheets with radial and ring seams.
  T['vver/cap'] = () => {
    const im = img(64, 64), r = rng(4201);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32, a = Math.atan2(y + 0.5 - 32, x + 0.5 - 32);
      let c = add([184, 186, 184], (r() - 0.5) * 7);
      const k = Math.round(a / (Math.PI / 8)), ad = Math.abs(a - k * Math.PI / 8) * 32 * d;
      if (ad < 0.5 && d > 0.25) c = [132, 136, 138];
      if (Math.abs(d - 0.25) < 0.03 || Math.abs(d - 0.62) < 0.02) c = [132, 136, 138];
      put(im, x, y, c);
    }
    return im;
  };
  // Concrete of pedestals and anchor blocks.
  T['vver/concrete'] = () => {
    const im = img(32, 32), r = grain(im, [140, 138, 130], 4211, 5, 7, 8);
    for (let x = 0; x < 32; x++) put(im, x, 0, [160, 158, 150]);
    return im;
  };
  // Plates: radiation signs, name plates, loop numbers, flow arrow.
  T['vver/plates'] = () => {
    const im = img(128, 128);
    trefoil(im, 0, 0, 24);
    trefoil(im, 0, 26, 12);
    namePlate(im, 26, 0, 46, 12, 'ВВЭР-1000');
    namePlate(im, 26, 14, 46, 12, 'ПГВ-1000М');
    namePlate(im, 26, 28, 46, 12, 'ГЦН-195М');
    namePlate(im, 26, 42, 46, 12, 'КД-79');
    // further real-scale units (RS3) take the free rows below
    namePlate(im, 26, 56, 46, 12, 'BWR-240');
    for (let k = 0; k < 4; k++) {
      rect(im, 74 + k * 13, 0, 12, 10, [28, 30, 32]);
      stencil(im, 74 + k * 13 + 4, 2, String(k + 1), [232, 228, 214]);
    }
    // flow arrow, white on the GOST green
    rect(im, 74, 14, 24, 8, [50, 118, 66]);
    for (let x = 0; x < 16; x++) for (let y = 3; y < 5; y++) put(im, 77 + x, 14 + y, [236, 236, 230]);
    for (let k = 0; k < 4; k++) for (let y = 4 - k; y <= 3 + k; y++) put(im, 93 + (3 - k), 14 + y, [236, 236, 230]);
    // brass maker's plate
    rect(im, 100, 14, 24, 14, [172, 136, 72]);
    for (let y = 17; y < 26; y += 2) for (let x = 103; x < 121; x++) if ((x + y) % 3) put(im, x, y, [96, 74, 40]);
    return im;
  };

  const MAT = {
    clad: { tex: 'vver/clad', P: 16 }, forged: { tex: 'vver/forged', P: 16 }, pnt: { tex: 'vver/paint', P: 16 },
    motor: { tex: 'vver/motor', P: 16 }, mcap: { tex: 'vver/motor_cap', P: 16 }, drv: { tex: 'vver/drive', P: 16 },
    yel: { tex: 'vver/yellow', P: 8 }, gw: { tex: 'vver/gost_green', P: 16 }, gs: { tex: 'vver/gost_red', P: 16 },
    oil: { tex: 'vver/gost_brown', P: 16 }, dark: { tex: 'vver/dark', P: 16 }, grate: { tex: 'vver/grating', P: 16 },
    conc: { tex: 'vver/concrete', P: 16 },
  };
  const ATLAS = { pl: { tex: 'vver/plates', size: 128, rects: {
    sign: [0, 0, 24, 24], sign_s: [0, 26, 12, 12], r_name: [26, 0, 46, 12], sg_name: [26, 14, 46, 12],
    pump_name: [26, 28, 46, 12], kd_name: [26, 42, 46, 12], bwr_name: [26, 56, 46, 12], n1: [74, 0, 12, 10], n2: [87, 0, 12, 10], n3: [100, 0, 12, 10],
    n4: [113, 0, 12, 10], arrow: [74, 14, 24, 8], maker: [100, 14, 24, 14] } } };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATLAS), { particle: particle || 'vver/clad' });

  // ------------------------------------------------------------------ geometry helpers
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const P = PCART.Model.prototype;

  // Ellipsoidal head going up from y0 (the vessel's top tangent line): n steps over `depth`, step i of radius
  // R*sqrt(1-(i/n)^2) — a terraced ellipse. Tops carry a radial cap texture.
  P.headUp = function (name, c, R, y0, depth, n, mat, cap) {
    for (let i = 0; i < n; i++) {
      const r = R * Math.sqrt(1 - Math.pow(i / n, 2));
      this.cylY(`${name}_${i}`, c, r, y0 + depth * i / n, y0 + depth * (i + 1) / n, mat, { cap: cap || 'vver/cap', seed: i });
    }
  };
  // Ellipsoidal head going down from yTop.
  P.headDown = function (name, c, R, yTop, depth, n, mat) {
    for (let i = 0; i < n; i++) {
      const r = R * Math.sqrt(1 - Math.pow(i / n, 2));
      this.cylY(`${name}_${i}`, c, r, yTop - depth * (i + 1) / n, yTop - depth * i / n, mat, { bottom: true, seed: i + 7 });
    }
  };
  // Box sticking out radially from a vertical axis at angle deg (from +x towards +z, a multiple of 22.5),
  // r0..r1 from the axis, w wide, y0..y1. Blockbench turns +angle about y from +x towards -z.
  P.radial = function (name, c, r0, r1, deg, w, y0, y1, mat, o = {}) {
    let a = ((deg % 360) + 540) % 360 - 180, f, t, beta;
    if (Math.abs(a) <= 45) { f = [c[0] + r0, y0, c[1] - w / 2]; t = [c[0] + r1, y1, c[1] + w / 2]; beta = -a; }
    else if (Math.abs(a) >= 135) { f = [c[0] - r1, y0, c[1] - w / 2]; t = [c[0] - r0, y1, c[1] + w / 2]; beta = -(a > 0 ? a - 180 : a + 180); }
    else if (a > 0) { f = [c[0] - w / 2, y0, c[1] + r0]; t = [c[0] + w / 2, y1, c[1] + r1]; beta = 90 - a; }
    else { f = [c[0] - w / 2, y0, c[1] - r1]; t = [c[0] + w / 2, y1, c[1] - r0]; beta = -90 - a; }
    this.box(name, f, t, mat, Object.assign({ split: '' }, o, { rot: beta ? { axis: 'y', angle: beta, origin: [c[0], 8, c[1]] } : undefined }));
  };
  // Vertical pipe (16-gon) with a flange at one end.
  P.pipeY = function (name, c, r, y0, y1, mat, o = {}) { this.cylY(name, c, r, y0, y1, mat, o); };
  // Caged ladder up a face: x0..x1 between the stringers, at z (rungs depth dz towards +dz sign), y0..y1.
  P.ladderZ = function (name, x0, x1, z, dz, y0, y1, mat, cage) {
    const z0 = Math.min(z, z + dz), z1 = Math.max(z, z + dz);
    this.box(`${name}_l`, [x0 - 0.5, y0, z0], [x0, y1, z1], mat, { split: 'y' });
    this.box(`${name}_r`, [x1, y0, z0], [x1 + 0.5, y1, z1], mat, { split: 'y' });
    let k = 0;
    for (let y = y0 + 3; y < y1 - 0.5; y += 5) this.box(`${name}_rung${k++}`, [x0, y, z0 + 0.2], [x1, y + 0.4, z0 + 0.6], mat, { split: '' });
    if (cage) {
      const s = Math.sign(dz) || -1, zc = z + s * 0.6, out = zc + cage * -s;   // cage hoops stand out from the face
      const za = Math.min(zc, out), zb = Math.max(zc, out);
      let h = 0;
      for (let y = y0 + 40; y < y1 - 4; y += 16) {
        this.box(`${name}_hoop${h}_f`, [x0 - 2, y, (s < 0 ? za : zb - 0.4)], [x1 + 2, y + 0.4, (s < 0 ? za + 0.4 : zb)], mat, { split: '' });
        this.box(`${name}_hoop${h}_a`, [x0 - 2, y, za], [x0 - 1.6, y + 0.4, zb], mat, { split: '' });
        this.box(`${name}_hoop${h++}_b`, [x1 + 1.6, y, za], [x1 + 2, y + 0.4, zb], mat, { split: '' });
      }
      for (const x of [x0 - 2, (x0 + x1) / 2 - 0.2, x1 + 1.6]) {
        this.box(`${name}_cbar${x}`, [x, y0 + 40, s < 0 ? za : zb - 0.4], [x + 0.4, y1 - 4, s < 0 ? za + 0.4 : zb], mat, { split: 'y' });
      }
    }
  };
  // Hand rail round a vertical axis: top and knee rails, posts every 45 degrees; `gap` = [deg0, deg1] left open.
  P.railY = function (name, c, r, y0, mat) {
    this.ringY(`${name}_top`, c, r, 0.5, y0 + 15, y0 + 15.6, mat);
    this.ringY(`${name}_knee`, c, r, 0.4, y0 + 7.6, y0 + 8, mat);
    for (let k = 0; k < 8; k++) this.postY(`${name}_post${k}`, c, r - 0.25, k * 45 + 22.5, 0.5, y0, y0 + 15, mat, { split: '' });
  };
  // Ring of nuts (studs) about a vertical axis.
  P.nutsY = function (name, c, r, n, y0, h, s, mat) {
    for (let k = 0; k < n; k++) {
      const a = (k + 0.5) * 2 * Math.PI / n, x = c[0] + r * Math.cos(a), z = c[1] + r * Math.sin(a);
      this.box(`${name}_${k}`, [x - s / 2, y0, z - s / 2], [x + s / 2, y0 + h, z + s / 2], mat || 'bright', { split: '' });
    }
  };
  // Ring of nuts on a flange facing along x (dir +1 east, -1 west) at axial position a0..a1, centre [y, z].
  P.nutsX = function (name, c, r, n, a0, a1, s, mat) {
    for (let k = 0; k < n; k++) {
      const a = (k + 0.5) * 2 * Math.PI / n, y = c[0] + r * Math.cos(a), z = c[1] + r * Math.sin(a);
      this.box(`${name}_${k}`, [a0, y - s / 2, z - s / 2], [a1, y + s / 2, z + s / 2], mat || 'bright', { split: '' });
    }
  };
  P.nutsZ = function (name, c, r, n, a0, a1, s, mat) {
    for (let k = 0; k < n; k++) {
      const a = (k + 0.5) * 2 * Math.PI / n, x = c[0] + r * Math.cos(a), y = c[1] + r * Math.sin(a);
      this.box(`${name}_${k}`, [x - s / 2, y - s / 2, a0], [x + s / 2, y + s / 2, a1], mat || 'bright', { split: '' });
    }
  };

  // Horizontal flanged nozzle along `axis` ('x' or 'z') from a0 (inside the vessel) to the end a1 (on a block
  // face): boss rb, pipe rp, a GOST colour band and a bolted flange at the end. c = centre in the other two axes
  // (for x: [y, z], for z: [x, y]).
  P.nozzle = function (name, axis, c, a0, a1, rb, rp, band, o = {}) {
    const s = Math.sign(a1 - a0), L = Math.abs(a1 - a0), at = (d) => a1 - s * d;
    const seg = (n, r, d0, d1, mat, ends) => {
      const p = [at(d0), at(d1)].sort((u, v) => u - v);
      this.prism(`${name}_${n}`, axis, c, r, p[0], p[1], mat, { split: '', ends: ends || [] });
    };
    const end = axis === 'x' ? (s > 0 ? 'east' : 'west') : (s > 0 ? 'south' : 'north');
    const fl = o.flange || 2;
    seg('boss', rb, Math.min(L, fl + 4 + (o.safe || 4)), L, o.boss || 'clad');
    seg('safe', rp, fl, fl + 4 + (o.safe || 4), o.pipe || 'forged');
    if (band) seg('band', rp + 0.3, fl + 1.5, fl + 3, band);
    seg('flange', rp + 2.5, 0, fl, 'forged', [end]);
    const p = [at(fl + 1), at(fl)].sort((u, v) => u - v), n = o.nuts || 12;
    if (axis === 'x') this.nutsX(`${name}_nut`, c, rp + 1.4, n, p[0], p[1], Math.max(1, rp * 0.17));
    else this.nutsZ(`${name}_nut`, c, rp + 1.4, n, p[0], p[1], Math.max(1, rp * 0.17));
  };

  // ------------------------------------------------------------------ chunk grid
  const FACE_UV = {
    north: { u: ['x', true], v: ['y', true] }, south: { u: ['x', false], v: ['y', true] },
    east: { u: ['z', true], v: ['y', true] }, west: { u: ['z', false], v: ['y', true] },
    up: { u: ['x', false], v: ['z', false] }, down: { u: ['x', false], v: ['z', true] },
  };
  const SIDE = [['west', 'east'], ['down', 'up'], ['north', 'south']];
  function cutAt(e, i, p) {
    const a = 'xyz'[i], lo = e.from[i], hi = e.to[i];
    return [[lo, p, 'a'], [p, hi, 'b']].map(([s, t, tag]) => {
      const n = Object.assign({}, e, { name: e.name + tag, from: e.from.slice(), to: e.to.slice(), hide: e.hide.slice(), face: Object.assign({}, e.face) });
      n.from[i] = s; n.to[i] = t;
      if (s !== lo) n.hide.push(SIDE[i][0]);
      if (t !== hi) n.hide.push(SIDE[i][1]);
      for (const [d, spec] of Object.entries(e.face)) {
        if (!spec || typeof spec !== 'object' || !spec.uv) continue;
        const uv = spec.uv.slice();
        for (const k of ['u', 'v']) {
          const [ax, rev] = FACE_UV[d][k];
          if (ax !== a) continue;
          const j = k === 'u' ? 0 : 1, c0 = spec.uv[j], c1 = spec.uv[j + 2];
          const at = (x) => { const tt = (x - lo) / (hi - lo); return rev ? c1 + (c0 - c1) * tt : c0 + (c1 - c0) * tt; };
          const A = at(s), B = at(t);
          uv[j] = rev ? B : A; uv[j + 2] = rev ? A : B;
        }
        n.face[d] = { tex: spec.tex, uv };
      }
      return n;
    });
  }
  // Splits rotated boxes on the chunk grid (axis-aligned ones are left to the slicer). Returns the model.
  function cut(m) {
    const out = [];
    for (const e of m.els) {
      if (!(e.rot && e.rot.angle)) { out.push(e); continue; }
      let pieces = [e];
      for (let i = 0; i < 3; i++) {
        const next = [];
        for (const q of pieces) {
          let cur = [q];
          for (let p = (Math.floor(q.from[i] / G) + 1) * G; p < q.to[i] - 1e-6; p += G) {
            if (p <= q.from[i] + 1e-6) continue;
            const last = cur.pop();
            cur.push(...cutAt(last, i, p));
          }
          next.push(...cur);
        }
        pieces = next;
      }
      // a piece whose every face is hidden adds nothing
      out.push(...pieces.filter((q) => DIRS.some((d) => !q.hide.includes(d))));
    }
    m.els = out;
    return m;
  }

  // Bounds in blocks of a model's elements (unrotated boxes), for the footprint report.
  function bounds(m) {
    const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
    for (const e of m.els) for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], e.from[i]); hi[i] = Math.max(hi[i], e.to[i]); }
    return { cells_from: lo.map((v) => Math.floor(v / 16 + 1e-6)), cells_to: hi.map((v) => Math.ceil(v / 16 - 1e-6) - 1), px: [lo, hi] };
  }

  // PCSLICE.saveWhole, except that stale chunk files are unlinked one by one: in Blockbench's Electron a
  // recursive fs.rmSync of the chunk folder completes after the new files are written and takes them with it.
  // folder: the .bbmodel folder under art/blockbench (the real-scale series beyond the VVER keeps its sources by category).
  function save(root, id, folder) {
    const fs = require('fs');
    const bb = path.join(root, 'art/blockbench', folder || 'core/vver', id + '.bbmodel');
    fs.writeFileSync(bb, Codecs.project.compile());
    let j = Codecs.java_block.compile();
    if (typeof j === 'string') j = JSON.parse(j);
    for (const e of j.elements || []) if ((e.name || '').startsWith('glow_')) e.neoforge_data = { block_light: 15, sky_light: 15 };
    const r = PCSLICE.build(j, id);
    const dir = path.join(root, PCART.ASSETS, 'models/block', id, 'chunk');
    fs.mkdirSync(dir, { recursive: true });
    for (const f of fs.readdirSync(dir)) if (f.endsWith('.json')) fs.unlinkSync(path.join(dir, f));
    for (const [key, mm] of Object.entries(r.models)) fs.writeFileSync(path.join(dir, key + '.json'), JSON.stringify(mm) + '\n');
    const mf = path.join(root, PCART.ASSETS, 'wholemodels', id + '.json');
    fs.mkdirSync(path.dirname(mf), { recursive: true });
    fs.writeFileSync(mf, JSON.stringify(r.manifest, null, '\t') + '\n');
    return { chunks: r.manifest.chunks.length, elements: Object.values(r.models).reduce((n, mm) => n + mm.elements.length, 0), bounds: r.manifest.bounds };
  }
  function savePartial(root, id, part, dir) { return PCART.save(root, (dir || 'core/vver') + '/' + id, part, id + '/partial/' + part); }

  window.PCVV = { paint, mk, cut, bounds, save, savePartial, MAT, ATLAS, G };
})();
