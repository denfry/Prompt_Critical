// Whole-model slicer: cuts a Java block model of any size on a grid of 3x3x3-block chunks so that every
// chunk fits the Java block model range (-16..32 px about the chunk's middle block). The controller of a
// real-scale machine draws the chunks at their offsets (pc-mod client/whole). Pure functions over the
// compiled model JSON, so they run in Blockbench and in Node (art/tools/pcslice_test.js).
//
// In Blockbench, after building a model with PCART:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcslice.js', 'utf8'))
//   PCSLICE.saveWhole('<repo>', 'core/vver', 'vver_reactor')
// writes models/block/<id>/chunk/<cx>_<cy>_<cz>.json, wholemodels/<id>.json and the .bbmodel.
(function (root) {
  const CHUNK = 48;                 // px: three blocks
  const AXES = { x: 0, y: 1, z: 2 };

  // Default UV parameterisation of a face, as Minecraft maps a cube face (see BlockModel face UV rules):
  // for each face, which axis drives u and v, and whether the direction is reversed.
  const FACE_UV = {
    north: { u: ['x', true], v: ['y', true] },
    south: { u: ['x', false], v: ['y', true] },
    east: { u: ['z', true], v: ['y', true] },
    west: { u: ['z', false], v: ['y', true] },
    up: { u: ['x', false], v: ['z', false] },
    down: { u: ['x', false], v: ['z', true] },
  };
  // Faces that sit on the low or high side of an axis: dropped from a piece when they fall on a cut.
  const SIDE = { x: ['west', 'east'], y: ['down', 'up'], z: ['north', 'south'] };

  function chunkOf(v) { return Math.floor(v / CHUNK); }

  function volume(e) {
    return (e.to[0] - e.from[0]) * (e.to[1] - e.from[1]) * (e.to[2] - e.from[2]);
  }

  // Splits an axis-aligned element at plane p on axis a; UVs of the faces along a are interpolated.
  function cut(e, a, p) {
    const i = AXES[a], lo = e.from[i], hi = e.to[i];
    const parts = [];
    for (const [s, t] of [[lo, p], [p, hi]]) {
      const n = JSON.parse(JSON.stringify(e));
      n.from[i] = s; n.to[i] = t;
      const faces = {};
      for (const [f, face] of Object.entries(e.faces || {})) {
        if (f === SIDE[a][0] && s !== lo) continue;   // low face lies on the cut
        if (f === SIDE[a][1] && t !== hi) continue;   // high face lies on the cut
        const nf = JSON.parse(JSON.stringify(face));
        delete nf.cullface;
        if (face.uv && FACE_UV[f]) {
          for (const k of ['u', 'v']) {
            const [ax, rev] = FACE_UV[f][k];
            if (ax !== a) continue;
            if (face.rotation) throw new Error('face rotation on a split face: ' + (e.name || '') + ' ' + f);
            const j = k === 'u' ? 0 : 1, c0 = face.uv[j], c1 = face.uv[j + 2];
            const at = (x) => {
              const tt = (x - lo) / (hi - lo);
              return rev ? c1 + (c0 - c1) * tt : c0 + (c1 - c0) * tt;
            };
            const A = at(s), B = at(t);
            nf.uv[j] = rev ? B : A;
            nf.uv[j + 2] = rev ? A : B;
          }
        }
        faces[f] = nf;
      }
      n.faces = faces;
      parts.push(n);
    }
    return parts;
  }

  // Splits an element on every chunk boundary it crosses; rotated elements stay whole.
  function split(e) {
    if (e.rotation && e.rotation.angle) return [e];
    let pieces = [e];
    for (const a of ['x', 'y', 'z']) {
      const i = AXES[a], next = [];
      for (const q of pieces) {
        let cur = [q];
        const first = (chunkOf(q.from[i]) + 1) * CHUNK;
        for (let p = first; p < q.to[i]; p += CHUNK) {
          const last = cur.pop();
          cur.push(...cut(last, a, p));
        }
        next.push(...cur);
      }
      pieces = next;
    }
    return pieces;
  }

  // Groups pieces by chunk and moves them into chunk-local coordinates (-16..32 about the middle block).
  function slice(model) {
    const chunks = new Map(), pieces = [];
    let before = 0, after = 0;
    const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
    for (const e of model.elements || []) {
      before += volume(e);
      for (const q of split(e)) {
        after += volume(q);
        const centre = [0, 1, 2].map((i) => (q.from[i] + q.to[i]) / 2);
        const c = centre.map(chunkOf);
        const off = c.map((v) => v * CHUNK + 16);
        const local = JSON.parse(JSON.stringify(q));
        for (let i = 0; i < 3; i++) {
          local.from[i] -= off[i]; local.to[i] -= off[i];
          if (local.rotation && local.rotation.origin) local.rotation.origin[i] -= off[i];
          min[i] = Math.min(min[i], q.from[i]); max[i] = Math.max(max[i], q.to[i]);
        }
        for (let i = 0; i < 3; i++) {
          if (local.from[i] < -16 - 1e-6 || local.to[i] > 32 + 1e-6)
            throw new Error('element leaves the chunk range: ' + (q.name || '') + ' ' + JSON.stringify([q.from, q.to]));
        }
        // a piece whose every face fell on cuts is invisible, and Minecraft refuses a model with a faceless element
        if (!local.faces || !Object.keys(local.faces).length) continue;
        pieces.push(q);
        const key = c.join('_');
        if (!chunks.has(key)) chunks.set(key, { at: c, elements: [] });
        chunks.get(key).elements.push(local);
      }
    }
    if (Math.abs(before - after) > 1e-3 * Math.max(1, before)) throw new Error('volume changed: ' + before + ' -> ' + after);
    return { chunks, bounds: { min, max }, pieces };
  }

  // Axis-aligned bounds of an element in model units, its rotation (one axis, about its origin) included.
  function aabb(e) {
    const r = e.rotation;
    if (!r || !r.angle) return [e.from.slice(), e.to.slice()];
    const a = r.angle * Math.PI / 180, ax = { x: 0, y: 1, z: 2 }[r.axis], o = r.origin;
    const u = (ax + 1) % 3, v = (ax + 2) % 3, lo = [1e9, 1e9, 1e9], hi = [-1e9, -1e9, -1e9];
    for (let k = 0; k < 8; k++) {
      const p = [k & 1 ? e.to[0] : e.from[0], k & 2 ? e.to[1] : e.from[1], k & 4 ? e.to[2] : e.from[2]];
      const pu = p[u] - o[u], pv = p[v] - o[v];
      const q = p.slice();
      q[u] = o[u] + pu * Math.cos(a) - pv * Math.sin(a);
      q[v] = o[v] + pu * Math.sin(a) + pv * Math.cos(a);
      for (let i = 0; i < 3; i++) { lo[i] = Math.min(lo[i], q[i]); hi[i] = Math.max(hi[i], q[i]); }
    }
    return [lo, hi];
  }

  // Occupied space per block cell for collision: each cell is cut into 4x4x4 voxels of 4 units, a voxel is set when
  // an element's bounds overlap it. Returns {"x,y,z": 16 hex digits}, bit = vx + 4 vy + 16 vz, empty cells left out.
  const VOX = 4, CLIMB = /(^|_)(ladder|stair)/;
  function cellsOf(elements) {
    const cells = new Map();
    for (const e of elements) {
      if (CLIMB.test(e.name || '')) continue;   // ladders stay walk-in, as vanilla ladders: the player climbs inside the cell
      const [lo, hi] = aabb(e);
      const v0 = lo.map((w) => Math.floor(w / VOX + 1e-6)), v1 = hi.map((w) => Math.ceil(w / VOX - 1e-6) - 1);
      for (let x = v0[0]; x <= v1[0]; x++) for (let y = v0[1]; y <= v1[1]; y++) for (let z = v0[2]; z <= v1[2]; z++) {
        const key = [x, y, z].map((w) => Math.floor(w / 4)).join(',');
        const bit = BigInt(((x % 4) + 4) % 4 + 4 * (((y % 4) + 4) % 4) + 16 * (((z % 4) + 4) % 4));
        cells.set(key, (cells.get(key) || 0n) | (1n << bit));
      }
    }
    const out = {};
    for (const k of [...cells.keys()].sort()) out[k] = cells.get(k).toString(16).padStart(16, '0');
    return out;
  }

  // Chunk JSON models and the manifest; the manifest offsets are in blocks to the chunk's middle block.
  function build(model, id) {
    const { chunks, bounds, pieces } = slice(model);
    const out = { models: {}, manifest: { id, chunk_blocks: CHUNK / 16, chunks: [],
      bounds: { min: bounds.min.map((v) => v / 16), max: bounds.max.map((v) => v / 16) } } };
    for (const [key, ch] of [...chunks.entries()].sort()) {
      const m = { textures: model.textures, elements: ch.elements };
      if (model.texture_size) m.texture_size = model.texture_size;
      out.models[key] = m;
      out.manifest.chunks.push(ch.at.map((v) => v * (CHUNK / 16) + 1));
    }
    out.manifest.cells = cellsOf(pieces);
    out.manifest.climb = climbOf(pieces);
    return out;
  }

  // Blockbench: slices the open project and writes everything under the repository.
  function saveWhole(repo, cat, id) {
    const fs = require('fs'), path = require('path');
    const ASSETS = 'pc-mod/src/main/resources/assets/promptcritical';
    const bb = path.join(repo, 'art/blockbench', cat, id + '.bbmodel');
    fs.mkdirSync(path.dirname(bb), { recursive: true });
    fs.writeFileSync(bb, Codecs.project.compile());
    let j = Codecs.java_block.compile();
    if (typeof j === 'string') j = JSON.parse(j);
    for (const e of j.elements || []) {
      if ((e.name || '').startsWith('glow_')) e.neoforge_data = { block_light: 15, sky_light: 15 };
    }
    const r = build(j, id);
    const dir = path.join(repo, ASSETS, 'models/block', id, 'chunk');
    // старые куски удаляются по одному и до записи новых: удаление всей папки в Blockbench завершается позже записи
    fs.mkdirSync(dir, { recursive: true });
    for (const f of fs.readdirSync(dir)) if (f.endsWith('.json')) fs.unlinkSync(path.join(dir, f));
    for (const [key, m] of Object.entries(r.models)) fs.writeFileSync(path.join(dir, key + '.json'), JSON.stringify(m) + '\n');
    const mf = path.join(repo, ASSETS, 'wholemodels', id + '.json');
    fs.mkdirSync(path.dirname(mf), { recursive: true });
    fs.writeFileSync(mf, JSON.stringify(r.manifest, null, '\t') + '\n');
    return { chunks: r.manifest.chunks.length, bounds: r.manifest.bounds };
  }

  // Cells a player can climb: those holding a ladder or stair element (kit names: ladder*, *_ladder*, stair*).
  function climbOf(elements) {
    const out = new Set();
    for (const e of elements) {
      if (!CLIMB.test(e.name || '')) continue;
      const [lo, hi] = aabb(e);
      for (let x = Math.floor(lo[0] / 16); x <= Math.ceil(hi[0] / 16) - 1; x++)
        for (let y = Math.floor(lo[1] / 16); y <= Math.ceil(hi[1] / 16) - 1; y++)
          for (let z = Math.floor(lo[2] / 16); z <= Math.ceil(hi[2] / 16) - 1; z++) out.add([x, y, z].join(','));
    }
    return [...out].sort();
  }

  const api = { CHUNK, cut, split, slice, build, saveWhole, aabb, cellsOf, climbOf };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PCSLICE = api;
})(typeof window !== 'undefined' ? window : globalThis);
