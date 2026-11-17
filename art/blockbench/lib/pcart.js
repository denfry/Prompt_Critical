// Shared toolkit for the hand-authored Blockbench machines (art/blockbench/<cat>/<id>.author.js).
// Load once per Blockbench session before a machine script:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//
// Conventions every machine follows:
//   * one model unit = 1/16 block = 6.25 cm; textures keep 2 px per unit (32 px per block);
//   * tiled materials map faces by world position (period P units), so tiles run on across elements;
//   * radial textures are mapped about a disc centre, so the rotated boxes of a 16-gon agree;
//   * an element named glow_* is exported full-bright (neoforge_data).
(function () {
  const fs = require('fs');
  const path = require('path');
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const ASSETS = 'pc-mod/src/main/resources/assets/promptcritical';

  // ------------------------------------------------------------------ pixels
  function rng(seed) {
    let s = seed >>> 0;
    return function () {
      s = (s + 0x6d2b79f5) >>> 0;
      let t = s;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  function img(w, h) { return { w, h, d: new Uint8ClampedArray(w * h * 4) }; }
  function put(im, x, y, c) {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
    const i = (y * im.w + x) * 4;
    im.d[i] = c[0]; im.d[i + 1] = c[1]; im.d[i + 2] = c[2]; im.d[i + 3] = c.length > 3 ? c[3] : 255;
  }
  function get(im, x, y) {
    x = ((x % im.w) + im.w) % im.w; y = ((y % im.h) + im.h) % im.h;
    const i = (y * im.w + x) * 4;
    return [im.d[i], im.d[i + 1], im.d[i + 2]];
  }
  const add = (c, k) => [c[0] + k, c[1] + k, c[2] + k];
  const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
  function rect(im, x, y, w, h, c) { for (let j = 0; j < h; j++) for (let i = 0; i < w; i++) put(im, x + i, y + j, c); }
  // Tileable value noise, period = image size, cell g px.
  function vnoise(r, w, h, g) {
    const gw = Math.max(1, Math.round(w / g)), gh = Math.max(1, Math.round(h / g));
    const v = []; for (let i = 0; i < gw * gh; i++) v.push(r());
    const at = (i, j) => v[(((j % gh) + gh) % gh) * gw + (((i % gw) + gw) % gw)];
    return function (x, y) {
      const fx = x / w * gw, fy = y / h * gh, i = Math.floor(fx), j = Math.floor(fy), tx = fx - i, ty = fy - j;
      const sx = tx * tx * (3 - 2 * tx), sy = ty * ty * (3 - 2 * ty);
      const a = at(i, j) + (at(i + 1, j) - at(i, j)) * sx, b = at(i, j + 1) + (at(i + 1, j + 1) - at(i, j + 1)) * sx;
      return a + (b - a) * sy;
    };
  }
  function grain(im, base, seed, amp, blotchAmp, cell) {
    const r = rng(seed), n = vnoise(r, im.w, im.h, cell || 8);
    for (let y = 0; y < im.h; y++) for (let x = 0; x < im.w; x++) {
      put(im, x, y, add(base, (n(x, y) - 0.5) * 2 * blotchAmp + (r() - 0.5) * 2 * amp));
    }
    return r;
  }
  function rivet(im, x, y, base) {
    put(im, x, y, add(base, 58)); put(im, x + 1, y, add(base, 26));
    put(im, x, y + 1, add(base, 14)); put(im, x + 1, y + 1, add(base, -26));
    put(im, x + 2, y + 1, add(base, -10)); put(im, x + 1, y + 2, add(base, -12));
  }
  function writePng(im, file) {
    const c = document.createElement('canvas');
    c.width = im.w; c.height = im.h;
    c.getContext('2d').putImageData(new ImageData(im.d, im.w, im.h), 0, 0);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(c.toDataURL('image/png').split(',')[1], 'base64'));
  }

  // ------------------------------------------------------------------ shared materials (textures/block/material)
  const M = {};
  // Common red brick in stretcher bond, 8x4 px per brick with mortar (24x9 cm at this scale), lime mortar.
  M.brick = () => {
    const im = img(64, 64), r = rng(21);
    const reds = [[138, 72, 52], [150, 82, 58], [124, 64, 46], [158, 94, 68], [132, 78, 60], [112, 58, 44]];
    for (let row = 0; row < 16; row++) {
      const y0 = row * 4, off = (row % 2) * 4;
      for (let k = 0; k < 9; k++) {
        const x0 = off + k * 8 - 8;
        let c = reds[Math.floor(r() * reds.length)];
        if (r() < 0.06) c = [84, 50, 42];
        if (r() < 0.05) c = mix(c, [40, 36, 34], 0.45);
        for (let j = 0; j < 3; j++) for (let i = 0; i < 7; i++) {
          let p = add(c, (r() - 0.5) * 12);
          if (j === 0) p = add(p, 6);
          if (j === 2) p = add(p, -7);
          if (i === 6) p = add(p, -4);
          put(im, ((x0 + i) % 64 + 64) % 64, y0 + j, p);
        }
      }
      for (let i = 0; i < 64; i++) put(im, i, y0 + 3, add([146, 136, 120], (r() - 0.5) * 16 - (r() < 0.1 ? 22 : 0)));
      for (let k = 0; k < 9; k++) {
        const x = ((off + k * 8 - 1) % 64 + 64) % 64;
        for (let j = 0; j < 3; j++) put(im, x, y0 + j, add([138, 128, 112], (r() - 0.5) * 16));
      }
    }
    return im;
  };
  M.cast_iron = () => {
    const im = img(32, 32), r = grain(im, [52, 54, 56], 31, 6, 4, 8);
    for (let k = 0; k < 16; k++) put(im, r() * 32, r() * 32, [38, 39, 40]);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [86, 62, 46]);
    return im;
  };
  M.brass = () => {
    const im = img(32, 32), r = rng(41), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = n(x, y);
      put(im, x, y, add(mix([176, 140, 74], [118, 96, 54], t > 0.62 ? (t - 0.62) * 2.2 : 0), (r() - 0.5) * 10));
    }
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [214, 186, 120]);
    return im;
  };
  M.gunmetal = () => {
    const im = img(32, 32), r = rng(51), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add(mix([108, 90, 62], [78, 84, 66], n(x, y) * 0.7), (r() - 0.5) * 10));
    }
    return im;
  };
  M.steel = () => {
    const im = img(32, 32), r = rng(61);
    const streak = []; for (let y = 0; y < 32; y++) streak.push((r() - 0.5) * 14);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([120, 124, 128], streak[y] + (r() - 0.5) * 6));
    return im;
  };
  M.paint_red = () => {
    const im = img(32, 32), r = grain(im, [128, 36, 30], 71, 5, 6, 8);
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [70, 66, 62]);
    return im;
  };
  M.soot = () => {
    const im = img(32, 32), r = grain(im, [30, 29, 28], 81, 4, 6, 8);
    for (let k = 0; k < 12; k++) put(im, r() * 32, r() * 32, [58, 46, 38]);
    return im;
  };
  // Torn plate after a rupture: bare scraped steel, heat colours, rust and soot.
  M.torn = () => {
    const im = img(64, 64), r = rng(91), n = vnoise(r, 64, 64, 16), m = vnoise(r, 64, 64, 8);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      let c = mix([104, 106, 108], [36, 34, 32], n(x, y) > 0.55 ? (n(x, y) - 0.55) * 2.6 : 0);
      if (m(x, y) > 0.68) c = mix(c, [120, 74, 46], 0.7);
      put(im, x, y, add(c, (r() - 0.5) * 14));
    }
    for (let k = 0; k < 30; k++) { const x = r() * 60, y = r() * 64; for (let i = 0; i < 5; i++) put(im, x + i, y + i * 0.3, [150, 152, 154]); }
    return im;
  };
  // Machined and oiled steel for rods, crossheads, shafts: bright, faint tooling marks, an oil sheen.
  M.bright_steel = () => {
    const im = img(32, 32), r = rng(201), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add([142, 146, 150], (y % 4 === 0 ? -10 : 0) + (n(x, y) - 0.5) * 24 + (r() - 0.5) * 6));
    }
    return im;
  };
  // Victorian engine green, hard-wearing gloss with chips down to the iron and an oily bloom.
  M.paint_green = () => {
    const im = img(32, 32), r = grain(im, [44, 72, 56], 211, 3, 5, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [58, 58, 56]);
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [70, 98, 80]);
    return im;
  };
  // Mahogany lagging strips round a cylinder: 3 px boards with dark joints, grain along the board.
  M.lagging_wood = () => {
    const im = img(32, 32), r = rng(221);
    for (let y = 0; y < 32; y++) {
      const board = Math.floor(y / 3), tone = [[112, 52, 32], [126, 60, 36], [104, 48, 30], [120, 58, 38]][board % 4];
      for (let x = 0; x < 32; x++) {
        let c = add(tone, (Math.sin(x * 0.7 + board * 2.1) * 6) + (r() - 0.5) * 8);
        if (y % 3 === 2) c = add(c, -26);
        put(im, x, y, c);
      }
    }
    return im;
  };
  // Hair-felt and canvas lagging on steam pipes, painted off-white, grubby.
  M.pipe_lagging = () => {
    const im = img(32, 32), r = grain(im, [196, 190, 176], 231, 5, 8, 8);
    for (let k = 0; k < 20; k++) put(im, r() * 32, r() * 32, [150, 142, 128]);
    for (let y = 7; y < 32; y += 8) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), -18));
    return im;
  };
  // Dressed sandstone ashlar for engine foundations: 16x8 px blocks, fine joints.
  M.ashlar = () => {
    const im = img(64, 64), r = rng(241);
    for (let row = 0; row < 8; row++) {
      const off = (row % 2) * 8;
      for (let k = 0; k < 5; k++) {
        const c = add([168, 150, 118], (r() - 0.5) * 22);
        for (let j = 0; j < 8; j++) for (let i = 0; i < 16; i++) {
          let p = add(c, (r() - 0.5) * 10);
          if (j === 7 || i === 15) p = [112, 104, 90];
          else if (j === 0) p = add(p, 8);
          put(im, ((off + k * 16 - 16 + i) % 64 + 64) % 64, row * 8 + j, p);
        }
      }
    }
    return im;
  };
  // Floor-plate chequer for walkways round machines.
  M.chequer = () => {
    const im = img(32, 32), r = grain(im, [78, 80, 82], 251, 4, 4, 8);
    for (let y = 0; y < 32; y += 8) for (let x = 0; x < 32; x += 8) {
      const o = ((y / 8) % 2) * 4;
      put(im, x + o + 1, y + 2, [120, 122, 124]); put(im, x + o + 2, y + 1, [120, 122, 124]);
      put(im, x + o + 2, y + 2, [40, 41, 42]); put(im, x + o + 3, y + 3, [40, 41, 42]);
    }
    return im;
  };
  // Bourdon gauge face at instrument resolution (32 px over 5 units): 0..1.6 MPa on a 270 degree arc,
  // major ticks every 0.2 MPa, minor every 0.1, red working-pressure line at 1.2. The needle is rendered.
  M.gauge_dial = () => {
    const im = img(32, 32), r = rng(161), C = 16;
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - C, y + 0.5 - C);
      let c = add([232, 226, 208], (r() - 0.5) * 5);
      if (d > 14.6) c = [168, 132, 70];
      else if (d > 13.6) c = [44, 42, 40];
      put(im, x, y, c);
    }
    const tick = (k, n, r0, r1, col) => {
      const a = (225 - k * 270 / n) * Math.PI / 180;
      for (let s = r0; s <= r1; s += 0.35) put(im, C - 0.5 + s * Math.cos(a), C - 0.5 - s * Math.sin(a), col);
    };
    for (let k = 0; k <= 16; k++) tick(k, 16, k % 2 ? 12.4 : 11, 12.9, k % 2 ? [120, 116, 108] : [40, 38, 36]);
    for (let k = 12; k <= 12.01; k += 1) tick(k, 16, 9.8, 13, [196, 34, 28]);
    for (const [x, y] of [[13, 21], [14, 22], [15, 21], [16, 21], [17, 21], [18, 22], [16, 22], [13, 22], [18, 21]]) put(im, x, y, [70, 66, 60]);
    for (let x = 11; x <= 20; x++) if (r() < 0.6) put(im, x, 24, [150, 144, 132]);
    rect(im, 15, 15, 2, 2, [36, 34, 32]);
    return im;
  };
  M.brass_disc = () => {
    const im = img(16, 16), r = rng(151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, add(d > 0.86 ? [200, 164, 92] : [168, 132, 70], (r() - 0.5) * 8 - (d > 0.95 ? 30 : 0)));
    }
    return im;
  };

  // Tiled materials by key; P = tile period in model units.
  const MAT = {
    brick: { tex: 'material/brick', P: 32 }, ci: { tex: 'material/cast_iron', P: 16 }, br: { tex: 'material/brass', P: 16 },
    gm: { tex: 'material/gunmetal', P: 16 }, st: { tex: 'material/steel', P: 16 }, red: { tex: 'material/paint_red', P: 16 },
    soot: { tex: 'material/soot', P: 16 }, torn: { tex: 'material/torn', P: 32 }, bright: { tex: 'material/bright_steel', P: 16 },
    green: { tex: 'material/paint_green', P: 16 }, wood: { tex: 'material/lagging_wood', P: 16 },
    felt: { tex: 'material/pipe_lagging', P: 16 }, ashlar: { tex: 'material/ashlar', P: 32 }, chequer: { tex: 'material/chequer', P: 16 },
  };

  function paintMaterials(root) {
    const dir = path.join(root, ASSETS, 'textures/block/material');
    for (const [n, f] of Object.entries(M)) writePng(f(), path.join(dir, n + '.png'));
    return Object.keys(M).length;
  }

  // ------------------------------------------------------------------ geometry
  function cellUv(dir, f, t, P) {
    const s = 16 / P, loc = (a, b) => {
      const c = Math.floor((a + b) / 2 / P) * P;
      return [Math.min(P, Math.max(0, a - c)), Math.min(P, Math.max(0, b - c))];
    };
    const [x0, x1] = loc(f[0], t[0]), [y0, y1] = loc(f[1], t[1]), [z0, z1] = loc(f[2], t[2]);
    const m = (u0, v0, u1, v1) => [u0 * s, v0 * s, u1 * s, v1 * s];
    switch (dir) {
      case 'north': return m(P - x1, P - y1, P - x0, P - y0);
      case 'south': return m(x0, P - y1, x1, P - y0);
      case 'east': return m(P - z1, P - y1, P - z0, P - y0);
      case 'west': return m(z0, P - y1, z1, P - y0);
      case 'up': return m(x0, z0, x1, z1);
      default: return m(x0, P - z1, x1, P - z0);
    }
  }
  function splitBox(f, t, P, axes) {
    let boxes = [[f.slice(), t.slice()]];
    for (const a of axes) {
      const out = [];
      for (const [bf, bt] of boxes) {
        const cuts = [];
        for (let k = Math.floor(bf[a] / P) + 1; k * P < bt[a]; k++) if (k * P > bf[a]) cuts.push(k * P);
        let lo = bf[a];
        for (const c of cuts.concat([bt[a]])) {
          const nf = bf.slice(), nt = bt.slice();
          nf[a] = lo; nt[a] = c; out.push([nf, nt]); lo = c;
        }
      }
      boxes = out;
    }
    return boxes;
  }
  const TAN16 = Math.tan(Math.PI / 16);
  const deg = (a) => a * Math.PI / 180;
  const AX = { x: 0, y: 1, z: 2 };

  // A model: element specs over the shared MAT plus machine materials, atlases {tex, rects} by key.
  function Model(extraMat, atlases) {
    this.els = [];
    this.mat = Object.assign({}, MAT, extraMat || {});
    this.atlases = atlases || {};
  }
  // o.rot = {axis, angle, origin}; o.hide = dirs without a face; o.face = {dir: {tex, uv} | mat key};
  // o.split = axes cut on tile lines (default 'xyz' for unrotated boxes).
  Model.prototype.box = function (name, f, t, mat, o = {}) {
    const P = this.mat[mat].P, axes = o.split !== undefined ? o.split : (o.rot ? '' : 'xyz');
    const parts = splitBox(f, t, P, [...axes].map((c) => AX[c]));
    parts.forEach(([pf, pt], i) => this.els.push({
      name: parts.length > 1 ? `${name}_${i}` : name, from: pf, to: pt, mat, rot: o.rot, hide: o.hide || [], face: o.face || {},
    }));
  };
  // Atlas decal on one face; atlasRef = 'atlasName.rectKey'. The other faces in mat.
  Model.prototype.decal = function (name, f, t, mat, dir, atlasRef, o = {}) {
    const [an, key] = atlasRef.includes('.') ? atlasRef.split('.') : [Object.keys(this.atlases)[0], atlasRef];
    const a = this.atlases[an], [x, y, w, h] = a.rects[key], k = 16 / a.size;
    const face = Object.assign({}, o.face, { [dir]: { tex: a.tex, uv: [x * k, y * k, (x + w) * k, (y + h) * k] } });
    this.box(name, f, t, mat, Object.assign({}, o, { face, split: '' }));
  };
  // Full texture on one face (instrument dials and the like).
  Model.prototype.plate = function (name, f, t, mat, dir, tex, o = {}) {
    const face = Object.assign({}, o.face, { [dir]: { tex, uv: [0, 0, 16, 16] } });
    this.box(name, f, t, mat, Object.assign({}, o, { face, split: '' }));
  };
  // 16-gon prism along `axis` ('x' or 'z') from 8 boxes, outer faces only. c = centre in the two other axes
  // (for z: [x, y]; for x: [y, z]). Faces whose centre lies below o.cullY are dropped; o.ends keeps end caps.
  Model.prototype.prism = function (name, axis, c, r, a0, a1, mat, o = {}) {
    const h = r * TAN16, cull = o.cullY !== undefined ? o.cullY : -99, keep = o.ends || [];
    const out = [];
    for (const [wide, angles] of [[true, [0, 22.5, 45, -22.5, -45]], [false, [0, 22.5, -22.5]]]) {
      for (const a of angles) out.push({ wide, a });
    }
    let i = 0;
    for (const { wide, a } of out) {
      let f, t, sides, endDirs, ys;
      if (axis === 'z') {
        f = wide ? [c[0] - r, c[1] - h, a0] : [c[0] - h, c[1] - r, a0];
        t = wide ? [c[0] + r, c[1] + h, a1] : [c[0] + h, c[1] + r, a1];
        sides = wide ? ['east', 'west'] : ['up', 'down'];
        endDirs = ['north', 'south'];
        ys = wide ? [c[1] + r * Math.sin(deg(a)), c[1] - r * Math.sin(deg(a))] : [c[1] + r * Math.cos(deg(a)), c[1] - r * Math.cos(deg(a))];
      } else {
        f = wide ? [a0, c[0] - h, c[1] - r] : [a0, c[0] - r, c[1] - h];
        t = wide ? [a1, c[0] + h, c[1] + r] : [a1, c[0] + r, c[1] + h];
        sides = wide ? ['south', 'north'] : ['up', 'down'];
        endDirs = ['east', 'west'];
        ys = wide ? [c[0] - r * Math.sin(deg(a)), c[0] + r * Math.sin(deg(a))] : [c[0] + r * Math.cos(deg(a)), c[0] - r * Math.cos(deg(a))];
      }
      const hide = DIRS.filter((d) => !sides.includes(d) && !(endDirs.includes(d) && keep.includes(d)));
      if (ys[0] < cull) hide.push(sides[0]);
      if (ys[1] < cull) hide.push(sides[1]);
      if (sides.every((s) => hide.includes(s)) && !keep.length) { i++; continue; }
      const origin = axis === 'z' ? [c[0], c[1], 8] : [8, c[0], c[1]];
      this.box(`${name}_${i++}`, f, t, mat, {
        rot: a ? { axis, angle: a, origin } : undefined, split: o.split !== undefined ? o.split : axis, hide, face: o.face,
      });
    }
  };
  // 16-sided ring of tangent bars about `axis` ('x' or 'z'): outer radius r, radial thickness w, a0..a1 along axis.
  Model.prototype.ring = function (name, axis, c, r, w, a0, a1, mat) {
    const L = 2 * (r - w / 2) * TAN16 + 0.25, rm = r - w / 2;
    let i = 0;
    const put = (f, t, a) => {
      const origin = axis === 'z' ? [c[0], c[1], 8] : [8, c[0], c[1]];
      this.box(`${name}_${i++}`, f, t, mat, { rot: a ? { axis, angle: a, origin } : undefined, split: '' });
    };
    for (const s of [1, -1]) {
      for (const a of [0, 22.5, 45, -22.5, -45]) {      // bars over and under the centre
        if (axis === 'z') put([c[0] - L / 2, c[1] + s * rm - w / 2, a0], [c[0] + L / 2, c[1] + s * rm + w / 2, a1], a);
        else put([a0, c[0] + s * rm - w / 2, c[1] - L / 2], [a1, c[0] + s * rm + w / 2, c[1] + L / 2], a);
      }
      for (const a of [0, 22.5, -22.5]) {                // bars at the sides
        if (axis === 'z') put([c[0] + s * rm - w / 2, c[1] - L / 2, a0], [c[0] + s * rm + w / 2, c[1] + L / 2, a1], a);
        else put([a0, c[0] - L / 2, c[1] + s * rm - w / 2], [a1, c[0] + L / 2, c[1] + s * rm + w / 2], a);
      }
    }
  };
  // Upright 16-gon prism (chimney stacks, columns) from 8 boxes turned about y, centre c = [x, z], y0..y1 inside
  // one block. o.tex gives the sides their own texture (P = 16), each facet a different slice of it so a brick
  // bond does not repeat facet by facet; without it the sides take `mat`. o.cap puts a radial texture on the top
  // (the boxes step up by 0.01 so the overlapping tops do not fight); o.ends lists end faces kept in `mat`.
  Model.prototype.prismY = function (name, c, r, y0, y1, mat, o = {}) {
    const h = r * TAN16, w = 2 * h, k = 16 / (2 * r), ends = o.ends || [];
    const boxes = [];
    for (const a of [0, 22.5, 45, -22.5, -45]) boxes.push([true, a]);
    for (const a of [0, 22.5, -22.5]) boxes.push([false, a]);
    let facet = o.seed || 0;
    boxes.forEach(([wide, a], i) => {
      const top = y1 + (o.cap ? i * 0.01 : 0);
      const f = wide ? [c[0] - r, y0, c[1] - h] : [c[0] - h, y0, c[1] - r];
      const t = wide ? [c[0] + r, top, c[1] + h] : [c[0] + h, top, c[1] + r];
      const sides = wide ? ['east', 'west'] : ['north', 'south'];
      const face = {}, hide = [];
      for (const d of DIRS) {
        if (sides.includes(d)) {
          if (o.tex) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex: o.tex, uv: [u0, 16 - y1, u0 + w, 16 - y0] };
          }
        } else if (d === 'up' && o.cap) {
          face.up = { tex: o.cap, uv: [(f[0] - c[0] + r) * k, (f[2] - c[1] + r) * k, (t[0] - c[0] + r) * k, (t[2] - c[1] + r) * k] };
        } else if (!ends.includes(d)) hide.push(d);
      }
      this.els.push({
        name: `${name}_${i}`, from: f, to: t, mat, hide, face,
        rot: a ? { axis: 'y', angle: a, origin: [c[0], 8, c[1]] } : undefined,
      });
    });
  };
  // Disc facing `dir` ('north' for z discs, 'east' for x discs) with a radial texture about the centre.
  Model.prototype.disc = function (name, axis, c, R, a0, a1, mat, radialTex, dir) {
    const h = R * TAN16, k = 16 / (2 * R);
    const boxes = [];
    for (const a of [0, 22.5, 45, -22.5, -45]) boxes.push([true, a]);
    for (const a of [0, 22.5, -22.5]) boxes.push([false, a]);
    boxes.forEach(([wide, a], i) => {
      let f, t, uv;
      if (axis === 'z') {
        f = wide ? [c[0] - R, c[1] - h, a0 - i * 0.01] : [c[0] - h, c[1] - R, a0 - i * 0.01];
        t = wide ? [c[0] + R, c[1] + h, a1 + i * 0.01] : [c[0] + h, c[1] + R, a1 + i * 0.01];
        uv = [(c[0] + R - t[0]) * k, (c[1] + R - t[1]) * k, (c[0] + R - f[0]) * k, (c[1] + R - f[1]) * k];
      } else {
        f = wide ? [a0 - i * 0.01, c[0] - h, c[1] - R] : [a0 - i * 0.01, c[0] - R, c[1] - h];
        t = wide ? [a1 + i * 0.01, c[0] + h, c[1] + R] : [a1 + i * 0.01, c[0] + R, c[1] + h];
        uv = [(c[1] + R - t[2]) * k, (c[0] + R - t[1]) * k, (c[1] + R - f[2]) * k, (c[0] + R - f[1]) * k];
      }
      const back = { north: 'south', south: 'north', east: 'west', west: 'east' }[dir];
      this.els.push({
        name: `${name}_${i}`, from: f, to: t, mat, hide: [back],
        rot: a ? { axis, angle: a, origin: axis === 'z' ? [c[0], c[1], 8] : [8, c[0], c[1]] } : undefined,
        face: { [dir]: { tex: radialTex, uv } },
      });
    });
  };
  // Handwheel about a vertical axis: 8 tangent bars on radius r, two spokes and a boss.
  Model.prototype.wheelY = function (name, cx, cy, cz, r, mat) {
    const w = 0.7, L = 2 * r * Math.tan(Math.PI / 8) + 0.2, y0 = cy - 0.4, y1 = cy + 0.4;
    const bx = (s) => [[cx + s * r - w / 2, y0, cz - L / 2], [cx + s * r + w / 2, y1, cz + L / 2]];
    let i = 0;
    for (const s of [1, -1]) {
      const [f, t] = bx(s);
      this.box(`${name}_rim${i++}`, f, t, mat, { split: '' });
      for (const a of [45, -45]) this.box(`${name}_rim${i++}`, f, t, mat, { rot: { axis: 'y', angle: a, origin: [cx, cy, cz] } });
      this.box(`${name}_rim${i++}`, [cx - L / 2, y0, cz + s * r - w / 2], [cx + L / 2, y1, cz + s * r + w / 2], mat, { split: '' });
    }
    this.box(`${name}_spoke0`, [cx - r, cy - 0.3, cz - 0.3], [cx + r, cy + 0.3, cz + 0.3], mat, { split: '' });
    this.box(`${name}_spoke1`, [cx - 0.3, cy - 0.3, cz - r], [cx + 0.3, cy + 0.3, cz + r], mat, { split: '' });
    this.box(`${name}_boss`, [cx - 0.7, cy - 0.5, cz - 0.7], [cx + 0.7, cy + 0.6, cz + 0.7], 'st', { split: '' });
  };
  // Shifted copy of another model's elements (whole-machine previews only).
  Model.prototype.merge = function (other, dx, dy, dz, prefix) {
    const s = (p) => [p[0] + dx, p[1] + dy, p[2] + dz];
    for (const e of other.els) {
      this.els.push(Object.assign({}, e, { name: prefix + e.name, from: s(e.from), to: s(e.to),
        rot: e.rot && Object.assign({}, e.rot, { origin: s(e.rot.origin) }) }));
    }
    return this;
  };

  // ------------------------------------------------------------------ Blockbench glue
  function realize(root, m, name) {
    newProject(Formats.java_block);
    Project.name = name;
    const texRoot = path.join(root, ASSETS, 'textures/block'), tex = {};
    const want = (n) => { if (!tex[n]) tex[n] = null; };
    for (const e of m.els) {
      want(m.mat[e.mat].tex);
      for (const v of Object.values(e.face)) want(typeof v === 'string' ? m.mat[v].tex : v.tex);
    }
    for (const n of Object.keys(tex)) {
      const t = new Texture({ name: n.split('/').pop() }).fromPath(path.join(texRoot, n + '.png')).add(false);
      t.reloadTexture(); // the PNG may have just been repainted; do not show Blockbench's cached copy
      t.id = n.replace('/', '_');
      tex[n] = t;
    }
    const first = m.particle || m.mat[m.els[0].mat].tex;
    if (tex[first]) tex[first].particle = true;
    for (const e of m.els) {
      const rot = [0, 0, 0];
      let origin = [8, 8, 8];
      if (e.rot) { rot[AX[e.rot.axis]] = e.rot.angle; origin = e.rot.origin; }
      const c = new Cube({ name: e.name, from: e.from, to: e.to, origin, rotation: rot, autouv: 0 }).init();
      for (const d of DIRS) {
        if (e.hide.includes(d)) { c.faces[d].texture = null; continue; }
        const spec = e.face[d];
        if (spec && typeof spec === 'object') {
          c.faces[d].texture = tex[spec.tex].uuid;
          c.faces[d].uv = spec.uv;
        } else {
          const mat = m.mat[spec || e.mat];
          c.faces[d].texture = tex[mat.tex].uuid;
          c.faces[d].uv = cellUv(d, e.from, e.to, mat.P);
        }
      }
    }
    Canvas.updateAll();
    return Project;
  }

  // Writes the open project as art/blockbench/<cat>/<id>.bbmodel and models/block/<javaPath>.json.
  function save(root, cat, id, javaPath) {
    const bb = path.join(root, 'art/blockbench', cat, id + '.bbmodel');
    fs.mkdirSync(path.dirname(bb), { recursive: true });
    fs.writeFileSync(bb, Codecs.project.compile());
    let j = Codecs.java_block.compile();
    if (typeof j === 'string') j = JSON.parse(j);
    delete j.format_version;
    for (const e of j.elements || []) {
      if ((e.name || '').startsWith('glow_')) e.neoforge_data = { block_light: 15, sky_light: 15 };
    }
    const jp = path.join(root, ASSETS, 'models/block', (javaPath || id) + '.json');
    fs.mkdirSync(path.dirname(jp), { recursive: true });
    fs.writeFileSync(jp, compileJSON(j));
    return (j.elements || []).length;
  }

  function writeAlias(root, id, parent) {
    const jp = path.join(root, ASSETS, 'models/block', id + '.json');
    fs.writeFileSync(jp, JSON.stringify({ parent: 'promptcritical:block/' + parent }, null, '\t') + '\n');
  }

  // Builds and saves every model of a machine in turn; textures load asynchronously, so wait between.
  async function exportAll(root, cat, builds) {
    const out = {};
    for (const [id, b] of Object.entries(builds)) {
      ModelProject.all.slice().forEach((p) => p.close(true));
      realize(root, b.model(), id);
      await new Promise((r) => setTimeout(r, 1200));
      out[id] = save(root, cat, id, b.java);
    }
    return out;
  }

  window.PCART = {
    rng, img, put, get, add, mix, rect, vnoise, grain, rivet, writePng,
    MATERIALS: M, MAT, paintMaterials, Model, realize, save, writeAlias, exportAll, ASSETS,
  };
})();
