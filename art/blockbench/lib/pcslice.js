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
    const chunks = new Map();
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
        const key = c.join('_');
        if (!chunks.has(key)) chunks.set(key, { at: c, elements: [] });
        chunks.get(key).elements.push(local);
      }
    }
    if (Math.abs(before - after) > 1e-3 * Math.max(1, before)) throw new Error('volume changed: ' + before + ' -> ' + after);
    return { chunks, bounds: { min, max } };
  }

  // Chunk JSON models and the manifest; the manifest offsets are in blocks to the chunk's middle block.
  function build(model, id) {
    const { chunks, bounds } = slice(model);
    const out = { models: {}, manifest: { id, chunk_blocks: CHUNK / 16, chunks: [],
      bounds: { min: bounds.min.map((v) => v / 16), max: bounds.max.map((v) => v / 16) } } };
    for (const [key, ch] of [...chunks.entries()].sort()) {
      const m = { textures: model.textures, elements: ch.elements };
      if (model.texture_size) m.texture_size = model.texture_size;
      out.models[key] = m;
      out.manifest.chunks.push(ch.at.map((v) => v * (CHUNK / 16) + 1));
    }
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
    fs.rmSync(dir, { recursive: true, force: true });
    fs.mkdirSync(dir, { recursive: true });
    for (const [key, m] of Object.entries(r.models)) fs.writeFileSync(path.join(dir, key + '.json'), JSON.stringify(m) + '\n');
    const mf = path.join(repo, ASSETS, 'wholemodels', id + '.json');
    fs.mkdirSync(path.dirname(mf), { recursive: true });
    fs.writeFileSync(mf, JSON.stringify(r.manifest, null, '\t') + '\n');
    return { chunks: r.manifest.chunks.length, bounds: r.manifest.bounds };
  }

  const api = { CHUNK, cut, split, slice, build, saveWhole };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.PCSLICE = api;
})(typeof window !== 'undefined' ? window : globalThis);
