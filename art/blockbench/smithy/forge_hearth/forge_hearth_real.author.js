// Smith's forge hearth at real scale (1 block = 1 m, 16 units = 1 m): a brick hearth 1.1 x 1.0 m, 0.75 m high, with
// a cast firepot and tuyere, a sheet-iron hood over it into the brick chimney breast, 3 m to the top; on the east the
// great double bellows (1.6 m long) hung in a timber frame, worked from the front by the rocker pole and chain; a
// water bosh on the west, the coal box and a tong rack. Dimensions after Holmström ("Modern Blacksmithing", 1904) and
// Richardson ("Practical Blacksmithing", 1889: hearth 30 in high, bellows 5–6 ft).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   FHR.build(repo); /* wait */ FHR.save(repo)                  static model: wholemodels/forge_hearth_real.json
//   FHR.buildPartial(repo, name); FHR.savePartial(repo, name)   for glow, bellows
//
// Controller block = (0, 0, 0), the hearth cell, front north. Footprint x 0..1, y 0..2, z 0..1 (model x 0..32,
// y 0..48, z 0..32). Work: the fire at (10, 12, 9) on the hearth, reached from the north of (0, 0, 0). Quench: the
// bosh at (3.5, 12, 3.5). Smoke out: chimney top y 47 over (0, 2, 1). The rocker handle hangs at (28, 22, 1).
// Partials (model px, no shift):
//   glow    — full-bright coals in the firepot; show while lit.
//   bellows — saved shifted (0, -16, 0): draw it 16 px up. Upper board, its leather fold and the rocker pole with the chain; the board rises by translate +y up
//             to 2.4 px while the handle comes down (draw the whole partial moved, a simple stand-in for the rocker).
(function () {
  const ID = 'forge_hearth_real';
  const F = [10, 9];                                   // firepot centre [x, z]
  const MAT = Object.assign({}, PCVV.MAT, {
    leather: { tex: 'smithy/leather', P: 16 }, coals: { tex: 'smithy/coals', P: 16 }, dead: { tex: 'smithy/coals_dead', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'material/brick' });

  function hearth(m) {
    m.box('hearth', [1, 0, 1], [19, 11, 17], 'brick');
    m.box('hearth_top', [0.6, 11, 0.6], [19.4, 12, 17.4], 'ci', { hide: ['down'] });
    m.box('ash_hole', [7, 1, 0.9], [13, 5, 1], 'dark', { split: '' });
    // firepot sunk in the top, coals heaped round it, the tuyere from the back
    m.box('firepot_rim', [F[0] - 4, 12, F[1] - 4], [F[0] + 4, 12.4, F[1] + 4], 'ci', { split: '' });
    m.box('coal_bank_w', [3, 12, 4], [F[0] - 4, 13.2, 15], 'dead', { split: '' });
    m.box('coal_bank_e', [F[0] + 4, 12, 4], [17, 13.2, 15], 'dead', { split: '' });
    m.box('fire_bed', [F[0] - 3.6, 12, F[1] - 3.6], [F[0] + 3.6, 12.6, F[1] + 3.6], 'dead', { split: '' });
    // water bosh on the west front corner, coal box at the foot of the hearth
    m.box('bosh', [1.4, 12, 1.4], [5.6, 15, 5.6], 'ci', { split: '' });
    m.box('bosh_water', [1.8, 14.4, 1.8], [5.2, 14.6, 5.2], 'gw', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('coal_box', [13, 12, 13], [18.6, 15, 16.6], 'wood', { split: '' });
    m.box('coal_box_heap', [13.6, 15, 13.6], [18, 15.8, 16], 'dead', { split: '' });
  }

  function chimney(m) {
    m.box('breast', [0, 0, 17], [20, 30, 32], 'brick');
    m.box('stack', [4, 30, 20], [16, 47, 31], 'brick');
    m.box('stack_cap', [3.4, 46, 19.4], [16.6, 47.2, 31.6], 'ashlar', { split: '' });
    m.box('flue', [6.4, 47.2, 22.4], [13.6, 47.4, 28.6], 'dark', { split: '' });
    // sheet-iron hood over the fire, riveted, carried on two straps
    m.box('hood_front', [2, 22, 7], [18, 30, 7.6], 'st', { split: '' });
    m.box('hood_w', [2, 22, 7.6], [2.6, 30, 17], 'st', { split: '' });
    m.box('hood_e', [17.4, 22, 7.6], [18, 30, 17], 'st', { split: '' });
    m.box('hood_top', [2, 30, 7], [18, 30.6, 17], 'st', { split: '' });
    for (const x of [3, 16.4]) m.box(`hood_strap_${x}`, [x, 30.6, 7.6], [x + 0.6, 31.2, 17], 'ci', { split: '' });
    // tong rack on the west of the breast
    m.box('tong_rack', [-0.4 + 0.4, 14, 18], [0.6, 15, 30], 'wood', { split: '' });
    for (const z of [19, 21.4, 23.8, 26.2]) m.box(`tongs_${z}`, [0, 6, z], [0.4, 14.6, z + 0.5], 'soot', { split: '' });
  }

  function bellows(m) {
    // timber frame: two posts front and back, cross bar carrying the bellows on chains, the rocker post
    for (const z of [2, 28]) {
      m.box(`frame_post_w_${z}`, [20, 0, z], [21.2, 20, z + 1.2], 'wood', { split: 'y' });
      m.box(`frame_post_e_${z}`, [30.4, 0, z], [31.6, 20, z + 1.2], 'wood', { split: 'y' });
      m.box(`frame_bar_${z}`, [20, 18.8, z], [31.6, 20, z + 1.2], 'wood', { split: '' });
    }
    m.box('rocker_post', [25.4, 20, 26.6], [26.6, 34, 27.8], 'wood', { split: 'y' });
    // lower board, lower leather (fixed half) and the middle board with the nozzle
    m.box('lower_board', [21.4, 8, 4], [30.2, 8.6, 28], 'wood', { split: '' });
    m.box('lower_leather', [22, 8.6, 5], [29.6, 10.6, 27], 'leather', { split: '' });
    m.box('middle_board', [21.4, 10.6, 4], [30.2, 11.2, 28], 'wood', { split: '' });
    m.box('weight', [24, 7, 22], [28, 8, 26], 'ci', { split: '' });
    for (const z of [6, 26]) m.box(`hanger_${z}`, [25.6, 11.2, z], [26.2, 18.8, z + 0.6], 'ci', { split: '' });
    // nozzle at the north end and the blast pipe into the hearth's tuyere
    m.box('nozzle', [24.8, 9.4, 2.4], [26.8, 10.8, 4], 'br', { split: '' });
    m.box('blast_pipe', [18, 9.6, 1.6], [26.8, 10.6, 2.6], 'ci', { split: 'x' });
    m.box('tuyere_in', [17.6, 9.4, 1.4], [19, 10.8, 9], 'ci', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function glow(m) {
    m.box('glow_fire', [F[0] - 2.6, 12.6, F[1] - 2.6], [F[0] + 2.6, 13.2, F[1] + 2.6], 'coals', { split: '', hide: ['down'] });
  }
  function boards(m) {
    m.box('upper_leather', [22, 11.2, 5], [29.6, 13.6, 27], 'leather', { split: '' });
    m.box('upper_board', [21.4, 13.6, 4], [30.2, 14.2, 28], 'wood', { split: '' });
    m.box('rocker_pole', [25.6, 33, 0.8], [26.4, 34, 31], 'wood', { split: '' });
    m.box('chain_back', [25.8, 14.2, 25.6], [26.2, 33, 26], 'ci', { split: '' });
    m.box('chain_front', [27.8, 22, 1], [28.2, 33, 1.4], 'ci', { split: '' });
    m.box('handle', [27, 21.2, 0.4], [29, 22, 2], 'wood', { split: '' });
  }
  const PART = {
    glow: { draw: glow, note: 'full-bright while lit' },
    bellows: { draw: boards, shift: [0, -16, 0], note: 'saved 16 px down: translate +16 px y, then +y up to 2.4 px while blowing' },
  };

  function model() { const m = mk(); hearth(m); chimney(m); bellows(m); return m; }
  function partial(name) { const s = mk(); PART[name].draw(s); const [dx, dy, dz] = PART[name].shift || [0, 0, 0]; return mk().merge(s, dx, dy, dz, ''); }
  function preview() { const m = model(); for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.FHR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'smithy/forge_hearth'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'smithy/forge_hearth'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
