// Diaphragm chlor-alkali cell (Hooker / BGK type, as DiaphragmCellRoom models it), hand-authored for Blockbench.
// Replaces the procedural placeholder from spec_mining.py (which sat in reagents/).
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/chlor_alkali/chlor_alkali_cell.author.js', 'utf8'))
//   CELL.paint('<repo>'); CELL.build('<repo>', id); CELL.save('<repo>', id)   for chlor_alkali_cell, _running
//
// One block, facade north. The cell stands on porcelain insulators: the anode base with the DSA anodes and their
// copper bar (west), the steel cathode box carrying the asbestos diaphragm, the concrete cover. Brine drips in
// through the feeder on the cover; chlorine leaves the cover up an FRP riser to the yellow header, hydrogen leaves
// the cathode box (yellow with a red ring), and the cell liquor runs out of the swan-neck percolator on the north
// face into the collecting trough. Points used by code (CellRoomBlockEntity): percolator mouth (11.5, 3.3, 1).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, writePng } = PCART;
  const mk = () => Object.assign(new PCART.Model(Object.assign({}, PLANT.MAT, {
    cu: { tex: 'chlor_alkali_cell/copper', P: 16 }, porc: { tex: 'chlor_alkali_cell/porcelain', P: 16 },
  }), { parts: PLANT.PARTS }), { particle: 'plant/green' });

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/block/chlor_alkali_cell');
    const cu = img(32, 32), q = rng(1202);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(cu, x, y, add(mix([186, 104, 60], [96, 120, 96], q() < 0.05 ? 0.6 : 0), (q() - 0.5) * 12 + (y % 8 === 0 ? -14 : 0)));
    }
    writePng(cu, path.join(dir, 'copper.png'));
    // glazed brown porcelain insulator sheds
    const po = img(32, 32), r = rng(1203);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(po, x, y, add(y % 4 < 2 ? [126, 70, 44] : [96, 50, 32], (r() - 0.5) * 8 + (x % 8 === 2 ? 22 : 0)));
    writePng(po, path.join(dir, 'porcelain.png'));
    return 2;
  }

  function cell(running) {
    const m = mk();
    m.box('skid', [0.5, 0, 1], [15.5, 0.6, 15], 'frame', {});
    for (const [x, z] of [[1.5, 2.5], [12.5, 2.5], [1.5, 11.5], [12.5, 11.5]]) m.box(`insulator_${x}_${z}`, [x, 0.6, z], [x + 2, 1.8, z + 2], 'porc', { split: '' });
    m.box('anode_base', [1, 1.8, 2], [15, 4, 14], 'frame', {});
    m.box('cathode', [0.6, 4, 1.6], [15.4, 10, 14.4], 'green', {});
    for (const y of [4, 9.4]) m.box(`flange_${y}`, [0.3, y, 1.3], [15.7, y + 0.6, 14.7], 'frame', { split: '' });
    for (const x of [3, 8, 13]) m.box(`stiffener_${x}`, [x, 4.6, 1.2], [x + 0.6, 9.4, 1.6], 'frame', { split: '' });
    m.box('cover', [1, 10, 2], [15, 12.5, 14], 'conc', {});
    // anode bar west, cathode bar and its flexible links east
    m.box('anode_bar', [0, 1.8, 6], [1, 6, 10], 'cu', { split: '' });
    m.box('anode_bar_down', [0, 0, 6.5], [1, 1.8, 9.5], 'cu', { split: '' });
    m.box('cathode_bar', [15.4, 5, 5.5], [16, 8.5, 10.5], 'cu', { split: '' });
    for (const z of [6.2, 8.2]) m.box(`flex_${z}`, [15.6, 0, z], [16, 5, z + 1.4], 'cu', { split: '' });
    // brine feeder on the cover, brine from the brown header at the back
    m.box('feeder', [3, 12.5, 9], [5.5, 14.5, 11.5], 'frp', { split: '' });
    m.box('feeder_cap', [2.8, 14.5, 8.8], [5.7, 15, 11.7], 'rubber', { split: '' });
    m.box('brine_drop', [3.8, 15, 9.8], [4.7, 16, 13.4], 'p_liq', { split: '' });
    m.box('brine_header', [0, 15, 13.4], [16, 16, 14.4], 'p_liq', { split: '' });
    // chlorine: FRP riser off the cover to the yellow header
    m.box('cl2_riser', [7.4, 12.5, 7.4], [9, 15, 9], 'frp', { split: '' });
    m.box('cl2_header', [0, 14.8, 7.2], [16, 16, 8.6], 'p_gas', { split: '' });
    // hydrogen from the cathode box, yellow with a red ring
    m.box('h2_out', [12.6, 10, 12.2], [13.8, 15.6, 13.4], 'p_gas', { split: '' });
    m.box('h2_band', [12.5, 13, 12.1], [13.9, 13.6, 13.5], 'p_steam', { split: '' });
    // swan-neck percolator on the north face and the liquor trough
    m.box('perc_out', [10.9, 5, 0.6], [12.1, 6.2, 1.6], 'p_alk', { split: '' });
    m.box('perc_neck', [10.9, 3.4, 0.6], [12.1, 6.2, 1.4], 'p_alk', { split: '' });
    m.box('trough', [8, 1.8, 0], [15, 2.6, 1.4], 'rubber', { split: '' });
    m.box('trough_lip', [8, 2.6, 0], [15, 2.9, 0.3], 'rubber', { split: '' });
    // brine head gauge glass, cell voltage box and its lamp, hazard sign
    m.box('head_gauge', [5.8, 12.5, 2.2], [6.4, 15.5, 2.8], 'frp', { split: '' });
    m.box('volt_box', [1.6, 6, 1], [5.4, 8.8, 1.6], 'light', { split: '' });
    m.decal('volt_tag', [1.9, 6.3, 0.9], [4.1, 7.5, 1], 'light', 'north', 'tag', { hide: ['south'] });
    m.decal(running ? 'glow_volt_lamp' : 'volt_lamp', [4.3, 7.7, 0.9], [5.1, 8.5, 1], 'light', 'north', running ? 'lamp_green' : 'lamp_amber',
      { hide: ['south'] });
    m.decal('warning', [6.6, 10, 1.8], [9.4, 12.4, 2], 'conc', 'north', 'warning', { hide: ['south'] });
    return m;
  }

  const BUILDS = {
    chlor_alkali_cell: { model: () => cell(false) },
    chlor_alkali_cell_running: { model: () => cell(true) },
  };

  window.CELL = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'chlor_alkali', id, BUILDS[id].java),
  };
})();
