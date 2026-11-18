// Hand-painted textures for future content without models: the four overhead and service wire textures the wire
// renderer (BER) wraps round its geometry (block/wire/*, over the tex_grid.py placeholders; listed in
// art/hand_textures.json) and the GDD 15 PV material item icons that had no art (solar cell, glass fibre, EVA film).
// Run headless: node art/tools/pcart_headless.js <repo> items/future_icons.author.js FIC --paint
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, writePng } = PCART;
  const W = {}, I = {};

  // Stranded conductor, 16 px tile along the wire: helical lay of strands, one highlight row, a dark core seam.
  const strands = (cols, seed, core) => () => {
    const im = img(16, 16), r = rng(seed);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const s = (x + y * 2) % 8, c = cols[Math.floor(((x + y * 2) / 8)) % cols.length];
      let p = add(c, s === 0 ? -46 : s === 1 ? -20 : s === 4 ? 24 : 0);
      if (core && (y === 7 || y === 8) && s > 5) p = mix(p, [96, 100, 104], 0.6);
      put(im, x, y, add(p, (r() - 0.5) * 8));
    }
    return im;
  };
  W.copper = strands([[190, 104, 52], [172, 92, 44]], 44101, false);
  W.aluminium = strands([[196, 200, 204], [178, 184, 188]], 44102, false);
  W.acsr = strands([[188, 192, 196], [170, 176, 180]], 44103, true);
  // XLPE-insulated service cable: black sheath, yellow print every 8 px, a moulded ridge.
  W.insulated_lv = () => {
    const im = img(16, 16), r = rng(44104);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([30, 31, 33], (y === 3 ? 14 : 0) + (r() - 0.5) * 5));
    for (const x0 of [1, 9]) for (let x = x0; x < x0 + 5; x++) if ((x + x0) % 2 === 0 || x === x0) put(im, x, 10, [214, 180, 40]);
    return im;
  };

  // Item icons, 16 px with a dark outline like the other flat icons.
  I.solar_cell = () => {
    const im = img(16, 16);
    for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
      const corner = (x < 3 || x > 12) && (y < 3 || y > 12);
      if (corner) continue;
      let c = [30, 52, 108];
      if (x === 2 || y === 2 || x === 13 || y === 13) c = [20, 30, 60];
      else if (x === 5 || x === 10) c = [196, 200, 206];
      else if ((x + y) % 5 === 0) c = [44, 70, 136];
      put(im, x, y, c);
    }
    return im;
  };
  I.glass_fiber = () => {
    const im = img(16, 16), r = rng(44111);
    for (let y = 3; y < 13; y++) for (let x = 2; x < 14; x++) {
      const edge = y === 3 || y === 12 || x === 2 || x === 13;
      put(im, x, y, edge ? [120, 124, 120] : add(y % 2 ? [236, 236, 228] : [206, 208, 200], (r() - 0.5) * 10));
    }
    for (let y = 5; y < 11; y++) { put(im, 7, y, [150, 120, 70]); put(im, 8, y, [170, 140, 90]); }
    return im;
  };
  I.eva_film = () => {
    const im = img(16, 16);
    for (let y = 4; y < 12; y++) for (let x = 1; x < 15; x++) {
      const edge = y === 4 || y === 11 || x === 1 || x === 14;
      put(im, x, y, edge ? [130, 150, 150, 255] : [210, 232, 228, 170 + ((x + y) % 4 === 0 ? 40 : 0)]);
    }
    for (let y = 5; y < 11; y++) { put(im, 12, y, [170, 190, 188, 255]); put(im, 13, y, [150, 170, 168, 255]); }
    return im;
  };

  function paint(root) {
    const A = path.join(root, PCART.ASSETS);
    for (const n of Object.keys(W)) writePng(W[n](), path.join(A, 'textures/block/wire', n + '.png'));
    for (const n of Object.keys(I)) {
      writePng(I[n](), path.join(A, 'textures/item', n + '.png'));
      require('fs').writeFileSync(path.join(A, 'models/item', n + '.json'),
        JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'promptcritical:item/' + n } }, null, '\t') + '\n');
    }
    return Object.keys(W).length + Object.keys(I).length;
  }
  window.FIC = { paint, ids: [], exportAll: () => ({}) };
})();
