// Scale check of a whole model in Blockbench (real-scale series): a player figure 0.6 x 1.8 x 0.3 blocks (head,
// torso, arms, legs; 16 units = 1 block) placed next to the model, an orthogonal-ish shot from an offscreen view,
// then the figure is removed again so it is never saved into a .bbmodel. Load after pcart.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcscale.js', 'utf8'))
//   PCSCALE.add([x, y, z])      // feet centre in model units; returns the number of cubes
//   ... capture a screenshot (MCP capture_screenshot or PCSCALE.shot) ...
//   PCSCALE.remove()
// PCSCALE.open(repo, 'core/bwr/bwr240_reactor') opens a saved .bbmodel to shoot it without rebuilding.
(function () {
  const GROUP = 'pcscale_figure';
  // figure in units: legs 10.8, torso 10.8, head 6.4 high (= 28 + a hair under 1.8 blocks with the hat line)
  const PARTS = [
    ['leg_l', [-2.9, 0, -2.4], [0, 10.8, 2.4], '#2f3f8f'], ['leg_r', [0, 0, -2.4], [2.9, 10.8, 2.4], '#2f3f8f'],
    ['torso', [-3, 10.8, -2.4], [3, 21.6, 2.4], '#2b9a9a'],
    ['arm_l', [-4.8, 11.2, -2.2], [-3, 21.6, 2.2], '#c99c74'], ['arm_r', [3, 11.2, -2.2], [4.8, 21.6, 2.2], '#c99c74'],
    ['head', [-3.2, 21.6, -3.2], [3.2, 28.8, 3.2], '#c99c74'], ['hair', [-3.3, 26.4, -3.3], [3.3, 28.8, 3.3], '#4a3222'],
  ];
  let tex = null;

  // one-colour 16x16 textures per part, created once per project
  function colourTex(hex) {
    const c = document.createElement('canvas'); c.width = c.height = 16;
    const g = c.getContext('2d'); g.fillStyle = hex; g.fillRect(0, 0, 16, 16);
    g.fillStyle = 'rgba(0,0,0,0.18)'; g.fillRect(0, 15, 16, 1); g.fillRect(15, 0, 1, 16);
    return new Texture({ name: 'pcscale_' + hex.slice(1) }).fromDataURL(c.toDataURL()).add(false);
  }

  function add(at) {
    remove();
    tex = {};
    Undo.initEdit({ outliner: true, textures: [] });
    const grp = new Group({ name: GROUP }).init();
    for (const [name, f, t, col] of PARTS) {
      if (!tex[col]) tex[col] = colourTex(col);
      const cube = new Cube({ name: 'pcscale_' + name, from: [at[0] + f[0], at[1] + f[1], at[2] + f[2]], to: [at[0] + t[0], at[1] + t[1], at[2] + t[2]] })
        .addTo(grp).init();
      cube.applyTexture(tex[col], true);
    }
    Undo.finishEdit('pcscale figure');
    Canvas.updateAll();
    return PARTS.length;
  }

  function remove() {
    for (const g of Group.all.filter((g) => g.name === GROUP)) g.remove(false);
    for (const t of Texture.all.filter((t) => t.name.startsWith('pcscale_'))) t.remove(true);
    tex = null;
    Canvas.updateAll();
  }

  function open(repo, rel) {
    const fs = require('fs'), file = require('path').join(repo, 'art/blockbench', rel + '.bbmodel');
    Codecs.project.load(JSON.parse(fs.readFileSync(file, 'utf8')), { path: file });
    return Outliner.elements.length;
  }

  window.PCSCALE = { add, remove, open, GROUP };
})();
