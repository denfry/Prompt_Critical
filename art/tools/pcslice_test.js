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
// Collision cells: a full block sets all 64 voxels, a 4-unit cube one, a box over two cells marks both.
{
  assert.deepStrictEqual(S.cellsOf([box([0, 0, 0], [16, 16, 16])]), { '0,0,0': 'ffffffffffffffff' });
  assert.deepStrictEqual(S.cellsOf([box([0, 0, 0], [4, 4, 4])]), { '0,0,0': '0000000000000001' });
  assert.deepStrictEqual(S.cellsOf([box([12, 0, 0], [20, 4, 4])]), { '0,0,0': '0000000000000008', '1,0,0': '0000000000000001' });
  assert.deepStrictEqual(S.cellsOf([box([-4, 0, 0], [0, 4, 4])]), { '-1,0,0': '0000000000000008' });
  // a box turned 45 degrees about y takes its rotated bounds
  const r = S.aabb(Object.assign(box([4, 0, 4], [12, 16, 12]), { rotation: { angle: 45, axis: 'y', origin: [8, 8, 8] } }));
  assert.ok(Math.abs(r[0][0] - (8 - 4 * Math.SQRT2)) < 1e-9 && Math.abs(r[1][2] - (8 + 4 * Math.SQRT2)) < 1e-9);
}

// A piece whose faces all land on cuts is left out of the chunk models (Minecraft rejects faceless elements).
{
  const r = S.build({ textures: {}, elements: [box([0, 0, 0], [144, 16, 16], { west: { uv: [0, 0, 16, 16], texture: '#t' } })] }, 't');
  for (const m of Object.values(r.models)) for (const e of m.elements) assert.ok(Object.keys(e.faces).length > 0, 'no faceless element');
  assert.strictEqual(Object.keys(r.models).length, 1, 'only the piece with the west face stays');
}

// Climbable cells: a ladder over two cells marks both, other elements none.
{
  const lad = Object.assign(box([4, 0, 0], [6, 20, 2]), { name: 'ladder_l' });
  assert.deepStrictEqual(S.climbOf([lad, Object.assign(box([0, 0, 0], [16, 16, 16]), { name: 'shell' })]), ['0,0,0', '0,1,0']);
  assert.deepStrictEqual(S.climbOf([Object.assign(box([0, 0, 0], [4, 4, 4]), { name: 'fm_ladder_rung3' })]), ['0,0,0']);
}

console.log('pcslice: all checks passed');
