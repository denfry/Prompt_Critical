// Backfills sliced whole models without Blockbench: drops chunk elements with no faces (Minecraft rejects them) and
// writes the collision cells (PCSLICE.cellsOf) into each manifest. New saves get both from pcslice.js itself.
//   node art/tools/wholecells.js            all manifests under assets/promptcritical/wholemodels
//   node art/tools/wholecells.js <id> ...   only these
const fs = require('fs'), path = require('path');
const S = require('../blockbench/lib/pcslice.js');
const ASSETS = path.join(__dirname, '../../pc-mod/src/main/resources/assets/promptcritical');

const ids = process.argv.slice(2).length ? process.argv.slice(2)
  : fs.readdirSync(path.join(ASSETS, 'wholemodels')).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5));
for (const id of ids) {
  const mf = path.join(ASSETS, 'wholemodels', id + '.json'), dir = path.join(ASSETS, 'models/block', id, 'chunk');
  const manifest = JSON.parse(fs.readFileSync(mf, 'utf8'));
  const global = [];
  let dropped = 0;
  for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.json')).sort()) {
    const file = path.join(dir, f), m = JSON.parse(fs.readFileSync(file, 'utf8'));
    const keep = m.elements.filter((e) => e.faces && Object.keys(e.faces).length);
    if (keep.length !== m.elements.length) {
      dropped += m.elements.length - keep.length;
      m.elements = keep;
      fs.writeFileSync(file, JSON.stringify(m) + '\n');
    }
    const off = f.slice(0, -5).split('_').map((v) => Number(v) * S.CHUNK + 16);
    for (const e of keep) {
      const g = JSON.parse(JSON.stringify(e));
      for (let i = 0; i < 3; i++) {
        g.from[i] += off[i]; g.to[i] += off[i];
        if (g.rotation && g.rotation.origin) g.rotation.origin[i] += off[i];
      }
      global.push(g);
    }
  }
  manifest.cells = S.cellsOf(global);
  manifest.climb = S.climbOf(global);
  fs.writeFileSync(mf, JSON.stringify(manifest, null, '\t') + '\n');
  console.log(id + ': ' + Object.keys(manifest.cells).length + ' occupied cells, ' + manifest.climb.length + ' climbable, ' + dropped + ' faceless elements dropped');
}
