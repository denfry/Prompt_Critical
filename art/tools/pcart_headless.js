// Headless runner for the hand-authored Blockbench scripts (art/blockbench/<cat>/*.author.js): builds the same
// PCART models in Node and writes the Java block models, the .bbmodel sources and the scripts' textures exactly
// where the Blockbench glue (pcart.js realize/save) would, without the desktop app.
//
//   node art/tools/pcart_headless.js <repo> <cat>/<name>.author.js <GLOBAL> [--paint] [--only id,id,...]
//
// PCART.saveWhole(repo, cat, id) saves a real-scale whole model as PCSLICE.saveWhole does in Blockbench: the model is
// compiled without the -16..32 clamp and cut into 3x3x3-block chunks (models/block/<id>/chunk/*.json and
// wholemodels/<id>.json). Rotated elements must not cross the 48-unit chunk grid (the slicer only cuts axis-aligned
// ones). A script that sets `handless: true` keeps its ids out of art/hand_models.json (whole models and partials).
//
// <GLOBAL> is the window object the script defines (M3U, ...). --paint repaints the script's own textures first.
// Java output follows Blockbench's java_block codec: textures keyed by folder_name in first-use order with the
// particle first, every element with its rotation block, faces in north/east/south/west/up/down order, glow_*
// elements full-bright through neoforge_data.
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');

const [root, script, globalName, ...rest] = process.argv.slice(2);
if (!root || !script || !globalName) { console.error('usage: pcart_headless.js <repo> <script> <GLOBAL> [--paint] [--only ids]'); process.exit(2); }
const doPaint = rest.includes('--paint');
const only = rest.includes('--only') ? rest[rest.indexOf('--only') + 1].split(',') : null;

// ---------------------------------------------------------------- PNG
const CRC = new Int32Array(256).map((_, n) => { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; return c; });
function crc32(b) { let c = -1; for (const x of b) c = CRC[(c ^ x) & 255] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function chunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}
function encodePng(im) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(im.w, 0); ihdr.writeUInt32BE(im.h, 4); ihdr[8] = 8; ihdr[9] = 6;
  const raw = Buffer.alloc((im.w * 4 + 1) * im.h);
  for (let y = 0; y < im.h; y++) Buffer.from(im.d.buffer, im.d.byteOffset + y * im.w * 4, im.w * 4).copy(raw, y * (im.w * 4 + 1) + 1);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', zlib.deflateSync(raw, { level: 9 })), chunk('IEND', Buffer.alloc(0))]);
}
function writePng(im, file) { fs.mkdirSync(path.dirname(file), { recursive: true }); fs.writeFileSync(file, encodePng(im)); }

// ---------------------------------------------------------------- load the toolkit and the script
global.window = global;
global.ModelProject = { all: [] };
function load(rel) { eval(fs.readFileSync(path.join(root, 'art/blockbench', rel), 'utf8')); } // eslint-disable-line no-eval
load('lib/pcart.js');
PCART.writePng = writePng;
let current = null;
PCART.realize = (_root, m, name) => { current = { m, name }; return current; };
PCART.save = (r, cat, id, javaPath) => compile(r, cat, id, javaPath, current.m, false);
PCART.saveWhole = (r, cat, id) => compile(r, cat, id, null, current.m, true);
const PCSLICE = require(path.resolve(root, 'art/blockbench/lib/pcslice.js'));
load('lib/pcround.js');
load('lib/plantkit.js');
load('lib/futurekit.js');
load(script);
const M = global[globalName];
if (!M) { console.error('script did not define window.' + globalName); process.exit(1); }

// ---------------------------------------------------------------- compile
const DIRS = ['north', 'east', 'south', 'west', 'up', 'down'];
const AX = { x: 0, y: 1, z: 2 };
const r4 = (v) => Math.round(v * 100000) / 100000 + 0;
function cellUv(dir, f, t, P) {
  const s = 16 / P, loc = (a, b) => { const c = Math.floor((a + b) / 2 / P) * P; return [Math.min(P, Math.max(0, a - c)), Math.min(P, Math.max(0, b - c))]; };
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
function fmt(v, ind) {
  if (Array.isArray(v) && v.every((x) => typeof x !== 'object')) return '[' + v.map((x) => JSON.stringify(x)).join(', ') + ']';
  const pad = '\t'.repeat(ind + 1), end = '\t'.repeat(ind);
  if (Array.isArray(v)) return v.length ? '[\n' + v.map((x) => pad + fmt(x, ind + 1)).join(',\n') + '\n' + end + ']' : '[]';
  if (v && typeof v === 'object') {
    const ks = Object.keys(v);
    return ks.length ? '{\n' + ks.map((k) => pad + JSON.stringify(k) + ': ' + fmt(v[k], ind + 1)).join(',\n') + '\n' + end + '}' : '{}';
  }
  return JSON.stringify(v);
}
const problems = [];
let uuidSeed = '', uuidN = 0;
const uuid = () => { const h = crypto.createHash('md5').update(uuidSeed + ':' + uuidN++).digest('hex'); return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`; };

function compile(r, cat, id, javaPath, m, whole) {
  uuidSeed = id; uuidN = 0;
  const texRoot = path.join(r, PCART.ASSETS, 'textures/block');
  const order = [];
  const want = (n) => { if (!order.includes(n)) order.push(n); };
  for (const e of m.els) {
    want(m.mat[e.mat].tex);
    for (const v of Object.values(e.face)) want(typeof v === 'string' ? m.mat[v].tex : v.tex);
  }
  for (const n of order) if (!fs.existsSync(path.join(texRoot, n + '.png'))) throw new Error(`${id}: missing texture ${n}`);
  const key = (n) => n.replace('/', '_');
  const used = new Set();
  const elements = [], bbEls = [];
  for (const e of m.els) {
    const rot = [0, 0, 0];
    let origin = [8, 8, 8];
    if (e.rot) { rot[AX[e.rot.axis]] = e.rot.angle; origin = e.rot.origin; }
    const faces = {}, bbFaces = {};
    for (const d of DIRS) {
      if (e.hide.includes(d)) { bbFaces[d] = { uv: [0, 0, 0, 0], texture: null }; continue; }
      const spec = e.face[d];
      let tex, uv;
      if (spec && typeof spec === 'object') { tex = spec.tex; uv = spec.uv; }
      else { const mat = m.mat[spec || e.mat]; tex = mat.tex; uv = cellUv(d, e.from, e.to, mat.P); }
      uv = uv.map(r4);
      used.add(tex);
      faces[d] = { uv, texture: '#' + key(tex) };
      bbFaces[d] = { uv, texture: order.indexOf(tex) };
    }
    if (!Object.keys(faces).length) continue;
    const axis = e.rot ? e.rot.axis : 'y';
    const clamp = (v) => { if (whole) return r4(v); if (v < -16.1 || v > 32.1) problems.push(`${id}: ${e.name} out of the -16..32 range (${v})`); return r4(Math.min(32, Math.max(-16, v))); };
    const el = { name: e.name, from: e.from.map(clamp), to: e.to.map(clamp), rotation: { angle: e.rot ? e.rot.angle : 0, axis, origin: origin.map(r4) }, faces };
    if (e.name.startsWith('glow_')) el.neoforge_data = { block_light: 15, sky_light: 15 };
    elements.push(el);
    bbEls.push({ name: e.name, box_uv: false, rescale: false, locked: false, export: true, from: el.from, to: el.to, autouv: 0, color: 2,
      rotation: rot, origin: el.rotation.origin, faces: bbFaces, type: 'cube', uuid: uuid() });
  }
  const particle = m.particle && order.includes(m.particle) ? m.particle : (m.particle && fs.existsSync(path.join(texRoot, m.particle + '.png')) ? m.particle : order[0]);
  if (!order.includes(particle)) order.push(particle);
  const textures = { particle: 'promptcritical:block/' + particle };
  for (const n of order) if (used.has(n) || n === particle) textures[key(n)] = 'promptcritical:block/' + n;
  const java = Object.assign({ credit: 'Made with Blockbench' }, m.renderType ? { render_type: m.renderType } : {}, { textures, elements });
  if (whole) {
    const sl = PCSLICE.build(java, id);
    const dir = path.join(r, PCART.ASSETS, 'models/block', id, 'chunk');
    fs.mkdirSync(dir, { recursive: true });
    for (const f of fs.readdirSync(dir)) if (f.endsWith('.json')) fs.unlinkSync(path.join(dir, f));
    for (const [k, cm] of Object.entries(sl.models)) {
      cm.elements = cm.elements.filter((e) => e.faces && Object.keys(e.faces).length);
      fs.writeFileSync(path.join(dir, k + '.json'), JSON.stringify(cm) + '\n');
    }
    const mf = path.join(r, PCART.ASSETS, 'wholemodels', id + '.json');
    fs.mkdirSync(path.dirname(mf), { recursive: true });
    fs.writeFileSync(mf, JSON.stringify(sl.manifest, null, '\t') + '\n');
  } else {
    const jp = path.join(r, PCART.ASSETS, 'models/block', (javaPath || id) + '.json');
    fs.mkdirSync(path.dirname(jp), { recursive: true });
    fs.writeFileSync(jp, fmt(java, 0));
  }

  const bb = {
    meta: { format_version: '5.0', model_format: 'java_block', box_uv: false }, name: id, parent: '', ambientocclusion: true,
    front_gui_light: false, visible_box: [1, 1, 0], resolution: { width: 16, height: 16 }, elements: bbEls,
    outliner: bbEls.map((e) => e.uuid),
    textures: order.map((n) => {
      const file = path.join(texRoot, n + '.png');
      return { name: path.basename(n) + '.png', path: file, folder: 'block/' + path.dirname(n).replace(/\\/g, '/'), namespace: 'promptcritical',
        id: key(n), particle: n === particle, render_mode: 'default', visible: true, internal: true, saved: true, uuid: uuid(),
        source: 'data:image/png;base64,' + fs.readFileSync(file).toString('base64') };
    }),
  };
  const bp = path.join(r, 'art/blockbench', cat, id + '.bbmodel');
  fs.mkdirSync(path.dirname(bp), { recursive: true });
  fs.writeFileSync(bp, JSON.stringify(bb));
  return elements.length;
}

if (doPaint) console.log('painted', M.paint(root));
const ids = only || M.ids;
const out = M.exportAll(root, ids);
if (problems.length) { console.error(problems.join('\n')); process.exitCode = 1; }
console.log(JSON.stringify(out));
// Record the Java model paths written by hand so the placeholder generator (art/tools/pclib.py) skips them.
if (M.handless) process.exit();
const handFile = path.join(root, 'art/hand_models.json');
const hand = fs.existsSync(handFile) ? JSON.parse(fs.readFileSync(handFile, 'utf8')) : { _comment: 'Models drawn by hand (art/blockbench/**/*.author.js); art/tools/pclib.py does not register these ids, so the placeholder generator cannot overwrite them. Kept by art/tools/pcart_headless.js.', models: [] };
const java = ids.map((id) => 'block/' + ((M.javaOf ? M.javaOf(id) : null) || id));
hand.models = [...new Set(hand.models.concat(java))].sort();
fs.writeFileSync(handFile, JSON.stringify(hand, null, 1) + '\n');
