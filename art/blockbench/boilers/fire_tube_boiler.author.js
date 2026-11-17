// Lancashire fire-tube boiler, hand-authored for Blockbench (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/fire_tube_boiler.author.js', 'utf8'))
//   FTB.paint('<repo>')                 // draws the textures into textures/block/fire_tube_boiler/
//   FTB.build('<repo>', 'front', 'cold') // opens one variant as a project; FTB.save(...) writes .bbmodel + JSON
//
// Scale: one model unit = 1/16 block = 6.25 cm. Every texture keeps 2 px per unit (32 px per block), so
// rivets, bricks and plates read at one density across the machine.
// Front segment (controller): local z -16..32, firing front faces north at z = -14.
// Rear segment: the next three blocks, local z -16..32, chimney uptake under block (0, +2, +1).
// Points used by code: water glasses x 0..1 / 15..16, y 11..18, z -14.9 (FireTubeBoilerRenderer);
// pressure gauge centre (8, 21.5, -15.55); safety valve escape (12, 30.5, 6); fire doors y 3.5, z -15.6.
(function () {
  const fs = require('fs');
  const path = require('path');
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const TEX_REL = 'pc-mod/src/main/resources/assets/promptcritical/textures/block/fire_tube_boiler';

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

  // ------------------------------------------------------------------ textures
  const PLATE = [60, 63, 66];
  const T = {};

  // Boiler plate in black boiler paint, worn: mottling, pits, a little rust bloom, the odd scuff.
  T.plate = () => {
    const im = img(64, 64), r = grain(im, PLATE, 11, 3, 6, 16);
    for (let k = 0; k < 70; k++) put(im, r() * 64, r() * 64, add(get(im, 0, 0), -14));
    for (let k = 0; k < 40; k++) put(im, r() * 64, r() * 64, [94, 70, 52]);
    for (let k = 0; k < 6; k++) { const x = r() * 60, y = r() * 64; for (let i = 0; i < 4; i++) put(im, x + i, y, add(PLATE, 12)); }
    return im;
  };
  // Lap seam: heads on a 4 px pitch both ways, so the band reads the same on every cylinder face.
  T.rivets = () => {
    const im = img(32, 32), base = add(PLATE, -6);
    grain(im, base, 12, 3, 3);
    for (let j = 0; j < 8; j++) for (let i = 0; i < 8; i++) rivet(im, i * 4 + 1, j * 4 + 1, base);
    return im;
  };
  // Common red brick in stretcher bond, 8x4 px per brick with mortar (24x9 cm at this scale), lime mortar.
  T.brick = () => {
    const im = img(64, 64), r = rng(21);
    const reds = [[138, 72, 52], [150, 82, 58], [124, 64, 46], [158, 94, 68], [132, 78, 60], [112, 58, 44]];
    for (let row = 0; row < 16; row++) {
      const y0 = row * 4, off = (row % 2) * 4;
      for (let k = 0; k < 9; k++) {
        const x0 = off + k * 8 - 8;
        let c = reds[Math.floor(r() * reds.length)];
        if (r() < 0.06) c = [84, 50, 42];          // over-burnt clinker
        if (r() < 0.05) c = mix(c, [40, 36, 34], 0.45); // sooted
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
  T.cast_iron = () => {
    const im = img(32, 32), r = grain(im, [52, 54, 56], 31, 6, 4, 8);
    for (let k = 0; k < 16; k++) put(im, r() * 32, r() * 32, [38, 39, 40]);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [86, 62, 46]);
    return im;
  };
  T.brass = () => {
    const im = img(32, 32), r = rng(41), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = n(x, y);
      put(im, x, y, add(mix([176, 140, 74], [118, 96, 54], t > 0.62 ? (t - 0.62) * 2.2 : 0), (r() - 0.5) * 10));
    }
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [214, 186, 120]);
    return im;
  };
  T.gunmetal = () => {
    const im = img(32, 32), r = rng(51), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add(mix([108, 90, 62], [78, 84, 66], n(x, y) * 0.7), (r() - 0.5) * 10));
    }
    return im;
  };
  T.steel = () => {
    const im = img(32, 32), r = rng(61);
    const streak = []; for (let y = 0; y < 32; y++) streak.push((r() - 0.5) * 14);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([120, 124, 128], streak[y] + (r() - 0.5) * 6));
    return im;
  };
  T.paint_red = () => {
    const im = img(32, 32), r = grain(im, [128, 36, 30], 71, 5, 6, 8);
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [70, 66, 62]);
    return im;
  };
  T.soot = () => {
    const im = img(32, 32), r = grain(im, [30, 29, 28], 81, 4, 6, 8);
    for (let k = 0; k < 12; k++) put(im, r() * 32, r() * 32, [58, 46, 38]);
    return im;
  };
  // Torn plate after a shell rupture: bare scraped steel, heat colours, rust and soot.
  T.torn = () => {
    const im = img(64, 64), r = rng(91), n = vnoise(r, 64, 64, 16), m = vnoise(r, 64, 64, 8);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      let c = mix([104, 106, 108], [36, 34, 32], n(x, y) > 0.55 ? (n(x, y) - 0.55) * 2.6 : 0);
      if (m(x, y) > 0.68) c = mix(c, [120, 74, 46], 0.7);
      put(im, x, y, add(c, (r() - 0.5) * 14));
    }
    for (let k = 0; k < 30; k++) { const x = r() * 60, y = r() * 64; for (let i = 0; i < 5; i++) put(im, x + i, y + i * 0.3, [150, 152, 154]); }
    return im;
  };
  // Front end plate, radial so every box of the 16-gon shows the same pattern: flanged edge, one rivet circle.
  T.end_plate = () => {
    const im = img(64, 64), r = rng(101), base = add(PLATE, 3);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      let c = add(base, (r() - 0.5) * 6);
      if (d > 0.935 && d <= 0.965) c = add(c, 16);
      else if (d > 0.965) c = add(c, -18 - (d - 0.965) * 200);
      else if (Math.abs(d - 0.875) < 0.03) c = add(c, -5);
      put(im, x, y, c);
    }
    for (let k = 0; k < 44; k++) {
      const a = k / 44 * Math.PI * 2;
      rivet(im, Math.floor(31.5 + 28 * Math.cos(a)), Math.floor(31.5 + 28 * Math.sin(a)), base);
    }
    return im;
  };
  // Furnace mouth: flanged ring riveted to the end plate around the cast-iron firing front.
  T.flue_mouth = () => {
    const im = img(32, 32), r = rng(111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = d < 0.74 ? [42, 43, 44] : [70, 72, 74];
      if (d >= 0.74 && d < 0.79) c = [92, 94, 96];
      if (d > 0.95) c = [46, 48, 50];
      put(im, x, y, add(c, (r() - 0.5) * 8));
    }
    for (let k = 0; k < 14; k++) {
      const a = k / 14 * Math.PI * 2;
      rivet(im, Math.floor(15.5 + 13.6 * Math.cos(a)), Math.floor(15.5 + 13.6 * Math.sin(a)), [70, 72, 74]);
    }
    return im;
  };

  // Atlas of one-off parts (64 px, 4 px per uv unit). Rectangles in px, used by the model below.
  const A = {
    door: [0, 0, 12, 9], door_wrecked: [12, 0, 12, 9], nameplate: [24, 0, 14, 7], dial: [40, 0, 10, 10],
    glass: [52, 0, 2, 14], glass_soot: [54, 0, 2, 14], mudhole: [0, 10, 12, 5], soot_door: [12, 10, 8, 6],
    back_door: [20, 10, 10, 8], slot_glow: [30, 10, 1, 5], pit: [32, 24, 12, 12], pit_glow: [44, 24, 12, 12],
  };
  function fireDoor(im, ox, oy, wrecked) {
    const r = rng(wrecked ? 131 : 121), ci = [50, 52, 54];
    for (let y = 0; y < 9; y++) for (let x = 0; x < 12; x++) {
      let c = add(ci, (r() - 0.5) * 8);
      if (x === 0 || y === 0) c = add(c, 14);
      if (x === 11 || y === 8) c = add(c, -16);
      if (x >= 2 && x <= 9 && y >= 1 && y <= 7) c = add(c, 6);          // sliding air register plate
      if (wrecked) c = mix(c, r() < 0.5 ? [28, 26, 24] : [112, 70, 44], 0.45);
      put(im, ox + x, oy + y, c);
    }
    for (const x of [3, 5, 7, 9]) for (let y = 2; y <= 6; y++) put(im, ox + x, oy + y, wrecked && r() < 0.4 ? [70, 50, 40] : [18, 16, 15]);
    for (const y of [1, 2, 6, 7]) put(im, ox, oy + y, [88, 90, 92]);  // hinge straps
    put(im, ox + 10, oy + 4, [150, 152, 156]); put(im, ox + 11, oy + 4, [120, 122, 126]); // latch keeper
  }
  T.fittings = () => {
    const im = img(64, 64), r = rng(141);
    fireDoor(im, 0, 0, false);
    fireDoor(im, 12, 0, true);
    // maker's plate: oval cast brass, raised rim, three lines of lettering
    for (let y = 0; y < 7; y++) for (let x = 0; x < 14; x++) {
      const e = ((x + 0.5 - 7) / 7) ** 2 + ((y + 0.5 - 3.5) / 3.5) ** 2;
      let c = add(PLATE, (r() - 0.5) * 6);
      if (e <= 1) c = e > 0.62 ? (y < 3 ? [208, 172, 100] : [132, 100, 52]) : add([176, 140, 74], (r() - 0.5) * 10);
      put(im, 24 + x, y, c);
    }
    for (const [y, a, b] of [[2, 4, 9], [4, 3, 10]]) for (let x = a; x <= b; x++) if (r() < 0.5) put(im, 24 + x, y, [134, 102, 54]);
    // Bourdon gauge dial, 0..1.6 MPa over 270 degrees, red mark at the 1.2 MPa working pressure
    for (let y = 0; y < 10; y++) for (let x = 0; x < 10; x++) {
      const d = Math.hypot(x + 0.5 - 5, y + 0.5 - 5);
      put(im, 40 + x, y, d > 4.6 ? [168, 132, 70] : add([228, 222, 204], (r() - 0.5) * 6));
    }
    for (let k = 0; k <= 8; k++) {
      const a = (225 - k * 270 / 8) * Math.PI / 180;
      put(im, 40 + 4.5 + 3.6 * Math.cos(a), 4.5 - 3.6 * Math.sin(a), k === 6 ? [190, 30, 26] : [36, 34, 32]);
    }
    put(im, 44, 4, [40, 40, 40]); put(im, 45, 4, [40, 40, 40]); put(im, 44, 5, [40, 40, 40]); put(im, 45, 5, [40, 40, 40]);
    put(im, 44, 7, [150, 146, 136]); put(im, 45, 7, [150, 146, 136]);
    // gauge glass tube (the water column is drawn by the renderer over it)
    for (let y = 0; y < 14; y++) { put(im, 52, y, [206, 222, 226]); put(im, 53, y, [150, 168, 174]); }
    for (let y = 0; y < 14; y++) { put(im, 54, y, [44, 40, 36]); put(im, 55, y, [30, 28, 26]); }
    // mud-hole door: oval cast cover
    for (let y = 0; y < 5; y++) for (let x = 0; x < 12; x++) {
      const e = ((x + 0.5 - 6) / 6) ** 2 + ((y + 0.5 - 2.5) / 2.5) ** 2;
      put(im, x, 10 + y, e <= 1 ? add(e > 0.6 ? [70, 72, 74] : [52, 54, 56], (r() - 0.5) * 8) : add(PLATE, (r() - 0.5) * 6));
    }
    // flue cleaning doors in the brickwork
    const smallDoor = (ox, oy, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let c = add([50, 52, 54], (r() - 0.5) * 8);
        if (x === 0 || y === 0) c = add(c, 14);
        if (x === w - 1 || y === h - 1) c = add(c, -16);
        put(im, ox + x, oy + y, c);
      }
      put(im, ox + w - 2, oy + (h >> 1), [148, 150, 154]);
    };
    smallDoor(12, 10, 8, 6);
    smallDoor(20, 10, 10, 8);
    for (let y = 0; y < 5; y++) put(im, 30, 10 + y, mix([255, 236, 170], [255, 140, 40], Math.abs(y - 2) / 2));
    // ash pit seen through the front: underside of the fire bars, darkness, an ash heap
    const pit = (ox, oy, lit) => {
      for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) {
        let c;
        if (y < 3) c = x % 2 === 0 ? [40, 38, 36] : (lit ? mix([255, 120, 30], [255, 196, 90], r()) : [12, 10, 10]);
        else {
          const heap = 11 - 2.6 * Math.cos((x - 5.5) / 6);
          c = y > heap - 1 ? add([92, 88, 84], (r() - 0.5) * 18) : (lit ? mix([62, 26, 10], [20, 12, 10], (y - 3) / 6) : [14, 12, 12]);
          if (lit && y > heap - 1 && r() < 0.18) c = [255, 150, 50];
        }
        put(im, ox + x, oy + y, add(c, (r() - 0.5) * 4));
      }
    };
    pit(32, 24, false);
    pit(44, 24, true);
    return im;
  };

  function writePng(im, file) {
    const c = document.createElement('canvas');
    c.width = im.w; c.height = im.h;
    const ctx = c.getContext('2d');
    ctx.putImageData(new ImageData(im.d, im.w, im.h), 0, 0);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(c.toDataURL('image/png').split(',')[1], 'base64'));
  }

  // ------------------------------------------------------------------ materials
  // P = tile period in model units (texture px = 2 * P). radial: R and centre for disc faces.
  const MAT = {
    plate: { tex: 'plate', P: 32 }, torn: { tex: 'torn', P: 32 }, rivets: { tex: 'rivets', P: 16 },
    brick: { tex: 'brick', P: 32 }, ci: { tex: 'cast_iron', P: 16 }, br: { tex: 'brass', P: 16 },
    gm: { tex: 'gunmetal', P: 16 }, st: { tex: 'steel', P: 16 }, red: { tex: 'paint_red', P: 16 },
    soot: { tex: 'soot', P: 16 },
  };

  // ------------------------------------------------------------------ geometry helpers
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

  // One model under construction: a flat list of element specs, turned into Blockbench cubes by realize().
  function Model() { this.els = []; }
  // mat: key of MAT. o.rot = {axis, angle, origin}; o.hide = dirs without a face; o.face = {dir: {tex, uv} | mat key};
  // o.split = axes to cut on tile lines (default 'xyz' for unrotated boxes).
  Model.prototype.box = function (name, f, t, mat, o = {}) {
    const P = MAT[mat].P, axes = o.split !== undefined ? o.split : (o.rot ? '' : 'xyz');
    const ax = [...axes].map((c) => 'xyz'.indexOf(c));
    const parts = splitBox(f, t, P, ax);
    parts.forEach(([pf, pt], i) => this.els.push({
      name: parts.length > 1 ? `${name}_${i}` : name, from: pf, to: pt, mat, rot: o.rot, hide: o.hide || [], face: o.face || {},
    }));
  };
  // Atlas decal on one face, the other faces in mat.
  Model.prototype.decal = function (name, f, t, mat, dir, rectKey, o = {}) {
    const [x, y, w, h] = A[rectKey];
    const face = Object.assign({}, o.face, { [dir]: { tex: 'fittings', uv: [x / 4, y / 4, (x + w) / 4, (y + h) / 4] } });
    this.box(name, f, t, mat, Object.assign({}, o, { face, split: '' }));
  };
  const TAN = Math.tan(Math.PI / 16);
  // 16-gon along z from 8 boxes (5 wide, 3 tall), outer faces only; faces centred below cullY are dropped.
  Model.prototype.cylZ = function (name, cx, cy, r, z0, z1, mat, o = {}) {
    const h = r * TAN, cull = o.cullY !== undefined ? o.cullY : -99, keepEnds = o.ends || [];
    const wide = [0, 22.5, 45, -22.5, -45], tall = [0, 22.5, -22.5];
    const deg = (a) => a * Math.PI / 180;
    const ends = ['north', 'south'].filter((d) => !keepEnds.includes(d));
    let i = 0;
    for (const a of wide) {
      const hide = ['up', 'down', ...ends];
      if (cy + r * Math.sin(deg(a)) < cull) hide.push('east');
      if (cy - r * Math.sin(deg(a)) < cull) hide.push('west');
      if (hide.includes('east') && hide.includes('west') && !keepEnds.length) { i++; continue; }
      this.box(`${name}_${i++}`, [cx - r, cy - h, z0], [cx + r, cy + h, z1], mat,
        { rot: a ? { axis: 'z', angle: a, origin: [cx, cy, 8] } : undefined, split: o.split || 'z', hide, face: o.face });
    }
    for (const a of tall) {
      const hide = ['east', 'west', ...ends];
      if (cy + r * Math.cos(deg(a)) < cull) hide.push('up');
      if (cy - r * Math.cos(deg(a)) < cull) hide.push('down');
      if (hide.includes('up') && hide.includes('down') && !keepEnds.length) { i++; continue; }
      this.box(`${name}_${i++}`, [cx - h, cy - r, z0], [cx + h, cy + r, z1], mat,
        { rot: a ? { axis: 'z', angle: a, origin: [cx, cy, 8] } : undefined, split: o.split || 'z', hide, face: o.face });
    }
  };
  // Disc facing north: the north faces carry a radial texture mapped about (cx, cy), the rim uses mat.
  Model.prototype.discN = function (name, cx, cy, R, z0, z1, mat, radialTex, o = {}) {
    const h = R * TAN, k = 16 / (2 * R);
    const uv = (f, t) => [(cx + R - t[0]) * k, (cy + R - t[1]) * k, (cx + R - f[0]) * k, (cy + R - f[1]) * k];
    const boxes = [];
    for (const a of [0, 22.5, 45, -22.5, -45]) boxes.push([[cx - R, cy - h], [cx + R, cy + h], a]);
    for (const a of [0, 22.5, -22.5]) boxes.push([[cx - h, cy - R], [cx + h, cy + R], a]);
    boxes.forEach(([f2, t2, a], i) => {
      const f = [f2[0], f2[1], z0 - i * 0.01], t = [t2[0], t2[1], z1 + i * 0.01];
      this.els.push({
        name: `${name}_${i}`, from: f, to: t, mat, hide: ['south'].concat(o.hide || []),
        rot: a ? { axis: 'z', angle: a, origin: [cx, cy, 8] } : undefined,
        face: { north: { tex: radialTex, uv: uv(f, t) } },
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

  // ------------------------------------------------------------------ the boiler
  const SHELL_R = 15, CX = 8, CY = 8, FURNACES = [1.5, 14.5], FY = 3, GLASSES = [0.5, 15.5];

  // The front segment shows the wall ends at z0; the rear segment hides both ends (front segment / back wall).
  function setting(m, z0, z1, buckstays, sootDoorZ, showFront) {
    const ends = showFront ? ['south'] : ['south', 'north'];
    m.box('setting_w', [-15, -16, z0], [-7, 6, z1], 'brick', { hide: ['east', 'down', ...ends] });
    m.box('setting_e', [23, -16, z0], [31, 6, z1], 'brick', { hide: ['west', 'down', ...ends] });
    m.box('coping_w', [-15.5, 6, z0 - (showFront ? 0.5 : 0)], [-6.5, 7, z1], 'ci', { hide: ['down', ...ends] });
    m.box('coping_e', [22.5, 6, z0 - (showFront ? 0.5 : 0)], [31.5, 7, z1], 'ci', { hide: ['down', ...ends] });
    for (const z of buckstays) {
      m.box(`buckstay_w${z}`, [-16, -16, z], [-15, 7, z + 1.5], 'ci', { hide: ['east', 'down'] });
      m.box(`buckstay_e${z}`, [31, -16, z], [32, 7, z + 1.5], 'ci', { hide: ['west', 'down'] });
    }
    m.decal('soot_door_w', [-16, -11, sootDoorZ], [-15, -8, sootDoorZ + 4], 'ci', 'west', 'soot_door', { hide: ['east'] });
    m.decal('soot_door_e', [31, -11, sootDoorZ], [32, -8, sootDoorZ + 4], 'ci', 'east', 'soot_door', { hide: ['west'] });
  }

  function seam(m, name, z0, z1, cull) {
    m.cylZ(name, CX, CY, SHELL_R + 0.3, z0, z1, 'rivets', { cullY: cull, split: '' });
  }

  function front(state) {
    const m = new Model(), lit = state === 'lit', wrecked = state === 'exploded';
    const shell = wrecked ? 'torn' : 'plate';
    m.cylZ('shell', CX, CY, SHELL_R, -13.4, 32, shell, { cullY: 6.5 });
    seam(m, 'seam_a', -0.8, 0.8, 6.5);
    seam(m, 'seam_b', 15.2, 16.8, 6.5);
    seam(m, 'seam_c', 31.2, 32, 6.5);
    m.discN('end_plate', CX, CY, 15.6, -14.2, -13.2, shell, wrecked ? 'torn' : 'end_plate');
    setting(m, -14, 32, [-12, 8, 26], 10, true);

    // front brickwork below the shell with the two ash-pit openings
    const W = (n, f, t, hide) => m.box(n, f, t, 'brick', { hide: (hide || []).concat(['south']) });
    W('front_pier_w', [-7, -16, -14], [-1.5, -7, -5.5], ['down']);
    W('front_pier_m', [4.5, -16, -14], [11.5, -7, -5.5], ['down']);
    W('front_pier_e', [17.5, -16, -14], [23, -7, -5.5], ['down']);
    for (const c of FURNACES) {
      W(`pit_sill_${c}`, [c - 3, -16, -14], [c + 3, -14, -5.5], ['down', 'east', 'west']);
      W(`pit_head_${c}`, [c - 3, -8, -14], [c + 3, -7, -5.5], ['up', 'east', 'west']);
      m.decal(lit ? `glow_pit_${c}` : `pit_${c}`, [c - 3, -14, -6], [c + 3, -8, -5.5], 'soot', 'north', lit ? 'pit_glow' : 'pit',
        { hide: ['south', 'east', 'west', 'up', 'down'] });
    }
    m.box('front_bearer', [-15.5, -7, -14.6], [31.5, -6, -11], 'ci', { hide: ['down', 'south'] });
    // brick closing the corners between the round end plate and the side walls, set back behind the plate
    m.box('front_fill', [-7, -6, -13.2], [23, 6, -11], 'brick', { hide: ['south', 'down', 'east', 'west'] });

    // firing fronts
    FURNACES.forEach((c, i) => {
      m.discN(`flue_mouth_${i}`, c, FY, 5.5, -14.7, -14.2, 'ci', 'flue_mouth');
      m.box(`firing_front_${i}`, [c - 4, -0.5, -15.1], [c + 4, 7, -14.7], 'ci', { hide: ['south'], split: '' });
      const swing = wrecked && i === 1;
      m.decal(`fire_door_${i}`, [c - 3, 1.5, -15.6], [c + 3, 6, -15.1], 'ci', 'north', wrecked ? 'door_wrecked' : 'door',
        { hide: ['south'], rot: swing ? { axis: 'y', angle: 45, origin: [c + 3, 3.5, -15.35] } : undefined });
      if (!swing) {
        m.box(`door_hinge_${i}a`, [c + 3, 2, -15.7], [c + 3.6, 2.8, -15], 'ci', { split: '' });
        m.box(`door_hinge_${i}b`, [c + 3, 4.7, -15.7], [c + 3.6, 5.5, -15], 'ci', { split: '' });
        m.box(`door_latch_${i}`, [c - 3.2, 3.4, -16], [c - 2.2, 4.1, -15.6], 'st', { split: '' });
      }
      m.box(`dead_plate_${i}`, [c - 3.5, 0.7, -16], [c + 3.5, 1.3, -15.1], 'ci', { split: '' });
      if (lit) {
        for (const p of [3, 5, 7, 9]) {
          const x1 = c + 3 - p / 2, x0 = x1 - 0.5;
          m.decal(`glow_slot_${i}_${p}`, [x0, 2.5, -15.64], [x1, 5, -15.6], 'ci', 'north', 'slot_glow',
            { hide: ['south', 'east', 'west', 'up', 'down'] });
        }
      }
    });

    // water gauges: two, as the rules require; the water column is drawn by FireTubeBoilerRenderer
    GLASSES.forEach((g, i) => {
      m.decal(`glass_${i}`, [g - 0.5, 11, -14.9], [g + 0.5, 18, -14.5], 'br', 'north', wrecked ? 'glass_soot' : 'glass', { hide: ['south'] });
      for (const [k, y] of [['top', 18], ['bottom', 9]]) {
        m.box(`glass_cock_${k}_${i}`, [g - 1, y, -15.3], [g + 1, y + 2, -14.2], 'br', { hide: ['south'], split: '' });
        m.box(`glass_handle_${k}_${i}`, [g + 1, y + 0.7, -15.1], [g + 2.6, y + 1.3, -14.7], 'st', { split: '' });
      }
      m.box(`glass_guard_${i}a`, [g - 1, 11, -15.2], [g - 0.75, 18, -15], 'br', { split: '' });
      m.box(`glass_guard_${i}b`, [g + 0.75, 11, -15.2], [g + 1, 18, -15], 'br', { split: '' });
    });

    // pressure gauge on its syphon, maker's plate, mud-hole door
    m.box('gauge_syphon', [7.6, 13.6, -14.8], [8.4, 18.2, -14.2], 'br', { split: '' });
    m.box('gauge_cock', [7.1, 15.6, -15.1], [8.9, 16.8, -14.2], 'gm', { split: '' });
    if (!wrecked) {
      m.discN('gauge_case', 8, 21.5, 3.5, -15.4, -14.4, 'br', 'brass_disc');
      m.box('gauge_dial', [5.5, 19, -15.55], [10.5, 24, -15.5], 'br',
        { split: '', hide: ['south', 'east', 'west', 'up', 'down'], face: { north: { tex: 'gauge_dial', uv: [0, 0, 16, 16] } } });
    }
    m.decal('nameplate', [4.5, 10, -14.4], [11.5, 13.5, -14.2], 'br', 'north', 'nameplate', { hide: ['south'] });
    m.decal('mudhole', [5, -5.5, -14.5], [11, -3, -14.2], 'ci', 'north', 'mudhole', { hide: ['south'] });
    m.box('mudhole_dog', [4.5, -4.6, -14.9], [11.5, -3.9, -14.5], 'st', { split: '' });
    m.box('mudhole_nut', [7.5, -4.8, -15.2], [8.5, -3.7, -14.9], 'st', { split: '' });

    if (!wrecked) {
      // manhole on the crown
      m.box('manhole_ring', [3, 22, -11.5], [13, 23.4, -2.5], 'plate', { hide: ['down'], split: '' });
      m.box('manhole_cover', [4, 23.4, -10.5], [12, 24.2, -3.5], 'ci', { hide: ['down'], split: '' });
      for (const z of [-9, -5.7]) {
        m.box(`manhole_dog_${z}`, [3.5, 24.2, z], [12.5, 25, z + 0.7], 'st', { split: '' });
        m.box(`manhole_nut_${z}`, [7.5, 25, z - 0.15], [8.5, 25.8, z + 0.85], 'st', { split: '' });
      }
      // lever safety valve: seating, gunmetal body, escape pipe, lever on a fulcrum strut, weight at the end
      m.box('sv_seating', [4.5, 22, 2], [11.5, 23.6, 10], 'ci', { hide: ['down'], split: '' });
      m.box('sv_body', [5.5, 23.6, 3.5], [10.5, 27, 8.5], 'gm', { split: '' });
      m.box('sv_spindle', [7.5, 27, 5.5], [8.5, 28, 6.5], 'st', { split: '' });
      m.box('sv_branch', [10.5, 24.6, 5.2], [12, 26, 6.8], 'gm', { split: '' });
      m.box('sv_escape', [11.2, 24.6, 5.2], [12.8, 30.5, 6.8], 'br', { split: '' });
      m.box('sv_strut', [7.4, 23.6, 2.2], [8.6, 28.8, 3.2], 'ci', { split: '' });
      m.box('sv_lever', [7.6, 28, 2], [8.4, 28.8, 22], 'st', { split: '' });
      m.box('sv_weight', [6, 24.5, 18], [10, 28, 21.5], 'ci', { split: '' });
    } else {
      m.box('rupture', [3, 22.9, -8], [13, 23.3, 26], 'soot', { hide: ['down'], split: '' });
      m.box('flap_w', [0, 22, -6], [3, 30, 24], 'torn', { rot: { axis: 'z', angle: 22.5, origin: [2, 23, 8] } });
      m.box('flap_e', [13, 22, -6], [16, 30, 24], 'torn', { rot: { axis: 'z', angle: -22.5, origin: [14, 23, 8] } });
    }
    // junction (stop) valve to the steam main
    m.box('jv_seating', [3, 22, 23], [13, 23.8, 32], 'ci', { hide: ['down', 'south'], split: '' });
    m.box('jv_body', [4.5, 23.8, 24], [11.5, 28, 31], 'ci', { split: '' });
    m.box('jv_outlet', [5, 24.2, 31], [11, 27.6, 32], 'ci', { hide: ['south'], split: '' });
    m.box('jv_bonnet', [5.5, 28, 25], [10.5, 29.5, 30], 'ci', { split: '' });
    m.box('jv_spindle', [7.6, 29.5, 27.1], [8.4, 31, 27.9], 'st', { split: '' });
    m.wheelY('jv_wheel', 8, 31.4, 27.5, 3, 'red');
    return m;
  }

  function rear(state) {
    const m = new Model(), wrecked = state === 'exploded', shell = wrecked ? 'torn' : 'plate';
    m.cylZ('shell', CX, CY, SHELL_R, -16, 26, shell, { cullY: 6.5 });
    seam(m, 'seam_a', -16, -15.2, 6.5);
    seam(m, 'seam_b', -0.8, 0.8, 6.5);
    seam(m, 'seam_c', 15.2, 16.8, 6.5);
    setting(m, -16, 27, [4, 20], 12);
    m.box('back_wall', [-15, -16, 27], [31, 7, 31], 'brick', { hide: ['down', 'north'] });
    m.box('back_bridge', [-8, 7, 26], [24, 24, 31], 'brick', { hide: ['down'] });
    const up = wrecked ? { rot: { axis: 'x', angle: 22.5, origin: [8, 24, 29] }, split: '' } : {};
    m.box('uptake', [0, 24, 18], [16, 31, 31], 'brick', Object.assign({ hide: ['down'] }, up));
    m.box('uptake_flange', [-0.5, 31, 17.5], [16.5, 32, 31.5], 'ci', Object.assign({}, up, { split: '' }));
    if (!wrecked) {
      m.box('damper_frame', [16, 25, 20], [16.6, 30, 29], 'ci', { hide: ['west'], split: '' });
      m.box('damper_lever', [16.6, 29, 19], [17.2, 29.6, 30], 'st', { split: '' });
      m.box('damper_weight', [16.6, 27, 28], [18.2, 29, 30], 'ci', { split: '' });
    }
    m.decal('flue_door_w', [0, -13, 31], [5, -9, 32], 'ci', 'south', 'back_door', { hide: ['north'] });
    m.decal('flue_door_e', [11, -13, 31], [16, -9, 32], 'ci', 'south', 'back_door', { hide: ['north'] });
    m.box('blowdown_cock', [6.5, -14, 31], [9.5, -11.5, 32], 'gm', { hide: ['north'], split: '' });
    m.box('blowdown_key', [9.5, -12.9, 31.3], [12, -12.4, 31.8], 'st', { split: '' });
    if (wrecked) {
      m.box('rupture', [3, 22.9, -16], [13, 23.3, 20], 'soot', { hide: ['down'], split: '' });
      return m;
    }
    // high-steam low-water valve on the crown
    m.box('hslw_seating', [4.5, 22, 1.5], [11.5, 23.6, 8.5], 'ci', { hide: ['down'], split: '' });
    m.box('hslw_body', [5.5, 23.6, 2.5], [10.5, 26.5, 7.5], 'gm', { split: '' });
    m.box('hslw_cap', [7, 26.5, 4], [9, 27.5, 6], 'st', { split: '' });
    m.box('hslw_lever', [7.6, 27, 5], [8.4, 27.6, 13], 'st', { split: '' });
    m.box('hslw_weight', [6.8, 25, 11], [9.2, 27, 13], 'ci', { split: '' });
    // feed check valve on the shell side, feed pipe down into the setting
    m.box('feed_check', [21.5, 12, 2], [25.5, 16, 7], 'gm', { split: '' });
    m.box('feed_bonnet', [22.5, 16, 3.5], [24.5, 17.5, 5.5], 'gm', { split: '' });
    m.box('feed_spindle', [23.2, 17.5, 4.2], [23.8, 18.6, 4.8], 'st', { split: '' });
    m.box('feed_wheel_a', [22, 18.6, 4.2], [25, 19.2, 4.8], 'red', { split: '' });
    m.box('feed_wheel_b', [23.2, 18.6, 3], [23.8, 19.2, 6], 'red', { split: '' });
    m.box('feed_pipe', [23, 7, 4], [24, 12, 5], 'gm', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ Blockbench glue
  const TEX_NAMES = ['plate', 'torn', 'rivets', 'brick', 'cast_iron', 'brass', 'gunmetal', 'steel', 'paint_red',
    'soot', 'end_plate', 'flue_mouth', 'brass_disc', 'gauge_dial', 'fittings'];
  // Bourdon gauge face at instrument resolution (32 px over 5 units): 0..1.6 MPa on a 270 degree arc,
  // major ticks every 0.2 MPa, minor every 0.1, red working-pressure line at 1.2. The needle is rendered.
  T.gauge_dial = () => {
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
    // "MPa" legend and maker's line under the pivot
    for (const [x, y] of [[13, 21], [14, 22], [15, 21], [16, 21], [17, 21], [18, 22], [16, 22], [13, 22], [18, 21]]) put(im, x, y, [70, 66, 60]);
    for (let x = 11; x <= 20; x++) if (r() < 0.6) put(im, x, 24, [150, 144, 132]);
    rect(im, 15, 15, 2, 2, [36, 34, 32]);
    return im;
  };
  T.brass_disc = () => {
    const im = img(16, 16), r = rng(151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, add(d > 0.86 ? [200, 164, 92] : [168, 132, 70], (r() - 0.5) * 8 - (d > 0.95 ? 30 : 0)));
    }
    return im;
  };

  // Local panel background, 240x202, drawn for BoilerPanels.BOILER: instruments are drawn by the game on top,
  // so this only holds the plate they are mounted on. Keep in step with the layout coordinates.
  function guiBoiler() {
    const W = 240, H = 202, im = img(W, H), r = rng(171), n = vnoise(r, W, H, 24);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      put(im, x, y, add([40, 43, 46], (n(x, y) - 0.5) * 8 + (r() - 0.5) * 5 + (1 - y / H) * 6));
    }
    const bevel = (x0, y0, w, h, base, hi, lo) => {
      for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
        let c = add(base, (r() - 0.5) * 6);
        if (x === x0 || y === y0) c = add(base, hi);
        if (x === x0 + w - 1 || y === y0 + h - 1) c = add(base, lo);
        put(im, x, y, c);
      }
    };
    const head = (x, y, base) => {
      put(im, x, y, add(base, 60)); put(im, x + 1, y, add(base, 30));
      put(im, x, y + 1, add(base, 18)); put(im, x + 1, y + 1, add(base, -18));
      put(im, x + 2, y + 2, add(base, -14));
    };
    // flanged frame with a rivet row, like the angle ring of the end plate
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d === 0) put(im, x, y, [18, 19, 20]);
      else if (d < 6) put(im, x, y, add([60, 64, 68], (d === 1 ? 18 : d === 5 ? -16 : 0) + (r() - 0.5) * 6));
    }
    for (let x = 8; x < W - 6; x += 10) { head(x, 2, [60, 64, 68]); head(x, H - 5, [60, 64, 68]); }
    for (let y = 12; y < H - 8; y += 10) { head(2, y, [60, 64, 68]); head(W - 5, y, [60, 64, 68]); }
    // cast-iron firing front around the peep hole, four studs
    bevel(100, 19, 88, 70, [50, 52, 54], 14, -18);
    for (const [x, y] of [[103, 22], [183, 22], [103, 84], [183, 84]]) head(x, y, [50, 52, 54]);
    // shadows the instruments throw on the plate
    for (let a = 0; a < 360; a += 1) {
      const t = a * Math.PI / 180;
      put(im, 40 + 33 * Math.cos(t) + 1, 56 + 33 * Math.sin(t) + 1, [24, 25, 26]);
      put(im, 208 + 25 * Math.cos(t) + 1, 128 + 25 * Math.sin(t) + 1, [24, 25, 26]);
    }
    for (let y = 25; y < 89; y++) put(im, 223, y, [24, 25, 26]);
    for (let x = 199; x < 223; x++) put(im, x, 89, [24, 25, 26]);
    // brass mounting strip behind the gauge glass
    bevel(79, 21, 18, 82, [138, 108, 58], 30, -34);
    // cast-iron shelf for the stoking controls
    bevel(6, 179, W - 12, H - 185, [52, 54, 56], 18, -20);
    for (let x = 6; x < W - 6; x++) put(im, x, 178, [22, 23, 24]);
    return im;
  }

  function paintGui(root) {
    writePng(guiBoiler(), path.join(root, 'pc-mod/src/main/resources/assets/promptcritical/textures/gui/panel/fire_tube_boiler.png'));
    return 1;
  }

  function paint(root) {
    const dir = path.join(root, TEX_REL);
    for (const n of TEX_NAMES) writePng(T[n](), path.join(dir, n + '.png'));
    return TEX_NAMES.length;
  }

  function realize(root, m, name) {
    newProject(Formats.java_block);
    Project.name = name;
    const dir = path.join(root, TEX_REL), tex = {};
    const used = new Set(['plate']);
    for (const e of m.els) {
      used.add(MAT[e.mat].tex);
      for (const v of Object.values(e.face)) used.add(typeof v === 'string' ? MAT[v].tex : v.tex);
    }
    for (const n of TEX_NAMES.filter((n) => used.has(n))) {
      const t = new Texture({ name: n }).fromPath(path.join(dir, n + '.png')).add(false);
      t.reloadTexture(); // the PNG was just repainted; do not show Blockbench's cached copy
      t.id = n;
      tex[n] = t;
    }
    tex.plate.particle = true;
    for (const e of m.els) {
      const rot = [0, 0, 0];
      let origin = [8, 8, 8];
      if (e.rot) { rot['xyz'.indexOf(e.rot.axis)] = e.rot.angle; origin = e.rot.origin; }
      const c = new Cube({ name: e.name, from: e.from, to: e.to, origin, rotation: rot, autouv: 0 }).init();
      for (const d of DIRS) {
        if (e.hide.includes(d)) { c.faces[d].texture = null; continue; }
        const spec = e.face[d];
        if (spec && typeof spec === 'object') {
          c.faces[d].texture = tex[spec.tex].uuid;
          c.faces[d].uv = spec.uv;
        } else {
          const mat = MAT[spec || e.mat];
          c.faces[d].texture = tex[mat.tex].uuid;
          c.faces[d].uv = cellUv(d, e.from, e.to, mat.P);
        }
      }
    }
    Canvas.updateAll();
    return Project;
  }

  const VARIANTS = {
    fire_tube_boiler: () => front('cold'),
    fire_tube_boiler_lit: () => front('lit'),
    fire_tube_boiler_exploded: () => front('exploded'),
    fire_tube_boiler_rear: () => rear('cold'),
    fire_tube_boiler_rear_exploded: () => rear('exploded'),
  };
  // Low water changes only the glass, which the renderer draws, so these models reuse the normal ones.
  const ALIASES = { fire_tube_boiler_low_water: 'fire_tube_boiler', fire_tube_boiler_lit_low_water: 'fire_tube_boiler_lit' };

  function save(root, id) {
    fs.writeFileSync(path.join(root, 'art/blockbench/boilers', id + '.bbmodel'), Codecs.project.compile());
    let j = Codecs.java_block.compile();
    if (typeof j === 'string') j = JSON.parse(j);
    delete j.format_version;
    for (const e of j.elements || []) {
      if ((e.name || '').startsWith('glow_')) e.neoforge_data = { block_light: 15, sky_light: 15 };
    }
    const models = path.join(root, 'pc-mod/src/main/resources/assets/promptcritical/models/block');
    fs.writeFileSync(path.join(models, id + '.json'), compileJSON(j));
    return (j.elements || []).length;
  }

  window.FTB = {
    paint,
    paintGui,
    variants: Object.keys(VARIANTS),
    count: (id) => VARIANTS[id]().els.length,
    build: (root, id) => realize(root, VARIANTS[id](), id),
    // Whole boiler for inspection only (never exported): rear segment moved three blocks back.
    preview(root, state = 'cold') {
      const m = front(state), r = rear(state === 'exploded' ? 'exploded' : 'cold');
      for (const e of r.els) {
        const s = (p) => [p[0], p[1], p[2] + 48];
        m.els.push(Object.assign({}, e, { name: 'rear_' + e.name, from: s(e.from), to: s(e.to),
          rot: e.rot && Object.assign({}, e.rot, { origin: s(e.rot.origin) }) }));
      }
      return realize(root, m, 'fire_tube_boiler_preview');
    },
    save,
    aliases(root) {
      const models = path.join(root, 'pc-mod/src/main/resources/assets/promptcritical/models/block');
      for (const [id, parent] of Object.entries(ALIASES)) {
        fs.writeFileSync(path.join(models, id + '.json'), JSON.stringify({ parent: 'promptcritical:block/' + parent }, null, '\t') + '\n');
      }
      return Object.keys(ALIASES);
    },
  };
})();
