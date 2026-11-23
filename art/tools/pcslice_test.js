// Checks of the whole-model slicer (art/blockbench/lib/pcslice.js): node art/tools/pcslice_test.js
const assert = require('assert');
const S = require('../blockbench/lib/pcslice.js');

function box(from, to, faces) {
  return { name: 'b', from, to, faces: faces || {
    north: { uv: [0, 0, 16, 16], texture: '#t' }, south: { uv: [0, 0, 16, 16], texture: '#t' },
    east: { uv: [0, 0, 16, 16], texture: '#t' }, west: { uv: [0, 0, 16, 16], texture: '#t' },
    up: { uv: [0, 0, 16, 16], texture: '#t' }, down: { uv: [0, 0, 16, 16], texture: '#t' } } };
}

// A long bar across three chunks along x keeps its volume, splits into three, and drops the cut faces.
{
  const r = S.slice({ elements: [box([0, 0, 0], [144, 16, 16])] });
  assert.strictEqual(r.chunks.size, 3);
  const pieces = [...r.chunks.values()].map((c) => c.elements[0]);
  for (const p of pieces) for (let i = 0; i < 3; i++) assert.ok(p.from[i] >= -16 && p.to[i] <= 32);
  const first = r.chunks.get('0_0_0').elements[0];
  assert.ok(first.faces.west && !first.faces.east, 'only the outer end face stays on the first piece');
  // south face u runs along x: the first third of the bar takes the first third of the texture
  assert.deepStrictEqual(first.faces.south.uv.map((v) => +v.toFixed(3)), [0, 0, 5.333, 16]);
  // north face u runs reversed along x: the first piece (low x) takes the last third
  assert.deepStrictEqual(first.faces.north.uv.map((v) => +v.toFixed(3)), [10.667, 0, 16, 16]);
}

// Negative coordinates and a tall column: chunks keyed below zero, manifest offsets in blocks.
{
  const out = S.build({ textures: { t: 'x' }, elements: [box([-40, 0, -8], [-24, 100, 8])] }, 'col');
  assert.ok(out.manifest.chunks.length >= 3);
  assert.deepStrictEqual(out.manifest.bounds.min.map((v) => +v.toFixed(3)), [-2.5, 0, -0.5]);
  for (const m of Object.values(out.models)) for (const e of m.elements) assert.ok(e.from[1] >= -16 && e.to[1] <= 32);
}

// A rotated element is kept whole; one that cannot fit a chunk is refused.
{
  const rot = box([0, 0, 0], [16, 16, 16]);
  rot.rotation = { angle: 22.5, axis: 'y', origin: [8, 8, 8] };
  assert.strictEqual(S.slice({ elements: [rot] }).chunks.size, 1);
  const big = box([0, 0, 0], [100, 16, 16]);
  big.rotation = { angle: 22.5, axis: 'y', origin: [8, 8, 8] };
  assert.throws(() => S.slice({ elements: [big] }), /leaves the chunk range/);
}
console.log('pcslice: all checks passed');
