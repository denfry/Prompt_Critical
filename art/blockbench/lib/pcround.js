// Upright round shapes for the hand-authored machines, on top of pcart.js (load it after the toolkit):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//
// pcart's prismY keeps a column inside one block; vessels, stacks and shields span several, so these split the
// 16-gon at the tile lines of their material and map every layer and facet onto the tile (world-aligned in y,
// a different slice of the tile per facet in u so a pattern does not repeat facet by facet).
(function () {
  const TAN16 = Math.tan(Math.PI / 16);
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const FACETS = [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]];

  // Upright 16-gon from 8 boxes turned about y. c = [x, z]. o.cap: radial texture on the top (the boxes step up by
  // 0.01 so the overlapping tops do not fight); o.bottom: keep the bottom faces in the material; o.seed shifts
  // the facet slices.
  PCART.Model.prototype.cylY = function (name, c, r, y0, y1, mat, o = {}) {
    const P = this.mat[mat].P, tex = this.mat[mat].tex, s = 16 / P, h = r * TAN16, w = Math.min(2 * h, P);
    const ys = [y0];
    for (let k = Math.floor(y0 / P) + 1; k * P < y1; k++) if (k * P > y0) ys.push(k * P);
    ys.push(y1);
    let n = 0;
    for (let L = 0; L < ys.length - 1; L++) {
      const a = ys[L], b = ys[L + 1], base = Math.floor((a + b) / 2 / P) * P;
      const v0 = (P - (b - base)) * s, v1 = (P - (a - base)) * s;
      const last = L === ys.length - 2, first = L === 0;
      FACETS.forEach(([wide, ang], i) => {
        const top = b + (last && o.cap ? i * 0.01 : 0);
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, top, c[1] + h] : [c[0] + h, top, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = (((i + (o.seed || 0)) * 0.6180339 + (d === sides[1] ? 0.5 : 0)) % 1) * (P - w) * s;
            face[d] = { tex, uv: [u0, v0, u0 + w * s, v1] };
          } else if (d === 'up' && last && o.cap) {
            const k = 16 / (2 * r);
            face.up = { tex: o.cap, uv: [(f[0] - c[0] + r) * k, (f[2] - c[1] + r) * k, (t[0] - c[0] + r) * k, (t[2] - c[1] + r) * k] };
          } else if (d === 'down' && first && o.bottom) {
            face.down = { tex, uv: [0, 0, Math.min(16, 2 * r * s), Math.min(16, 2 * h * s)] };
          } else hide.push(d);
        }
        this.els.push({
          name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined,
        });
      });
    }
  };

  // 16 tangent bars round a vertical axis (hand rails, support rings, bands): outer radius r, radial width w.
  PCART.Model.prototype.ringY = function (name, c, r, w, y0, y1, mat) {
    const rm = r - w / 2, L = 2 * rm * TAN16 + 0.2;
    let i = 0;
    const put = (f, t, a) => this.box(`${name}_${i++}`, f, t, mat, { split: '', rot: a ? { axis: 'y', angle: a, origin: [c[0], 8, c[1]] } : undefined });
    for (const s of [1, -1]) {
      for (const a of [0, 22.5, 45, -22.5, -45]) put([c[0] + s * rm - w / 2, y0, c[1] - L / 2], [c[0] + s * rm + w / 2, y1, c[1] + L / 2], a);
      for (const a of [0, 22.5, -22.5]) put([c[0] - L / 2, y0, c[1] + s * rm - w / 2], [c[0] + L / 2, y1, c[1] + s * rm + w / 2], a);
    }
  };

  // Square post at angle `deg` (a multiple of 22.5) on radius r about a vertical axis: placed on the east side and
  // turned, or on the north side for the angles a single +-45 turn cannot reach.
  PCART.Model.prototype.postY = function (name, c, r, deg, w, y0, y1, mat, o = {}) {
    let a = ((deg % 360) + 360) % 360, f, t;
    const east = a <= 45 || a >= 315, west = a >= 135 && a <= 225;
    if (east || west) {
      const s = east ? 1 : -1;
      f = [c[0] + s * r - w / 2, y0, c[1] - w / 2]; t = [c[0] + s * r + w / 2, y1, c[1] + w / 2];
      a = east ? (a > 180 ? a - 360 : a) : a - 180;
    } else {
      const s = a < 180 ? -1 : 1; // 90 deg (counter-clockwise from east, seen from above) is north = -z
      f = [c[0] - w / 2, y0, c[1] + s * r - w / 2]; t = [c[0] + w / 2, y1, c[1] + s * r + w / 2];
      a = a < 180 ? a - 90 : a - 270;
    }
    // Blockbench/Java turn +angle about y clockwise seen from above; our angles run counter-clockwise.
    this.box(name, f, t, mat, Object.assign({ split: o.split !== undefined ? o.split : 'y' }, o, { rot: a ? { axis: 'y', angle: -a, origin: [c[0], 8, c[1]] } : undefined }));
  };
})();
