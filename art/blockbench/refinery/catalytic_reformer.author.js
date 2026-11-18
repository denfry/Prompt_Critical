// Catalytic reformer (platformer) reactor, hand-authored for Blockbench (replaces the borrowed
// refinery/fixed_bed_reactor_regen).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (shared cladding,
// yellow) and the tube furnace (its hot casing):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/tube_furnace.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/catalytic_reformer.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); TF.paint('<repo>'); CRF.paint('<repo>')
//   build + save each model in turn (CRF.build / CRF.save, ids in CRF.ids)
//
// 1x1x1, front to the north. One of the platformer's reactors in series, the units that run at about 500 C over
// platinum on alumina. An insulated, metal-clad radial-flow vessel stands on its skirt with the access opening.
// The hot feed comes from the interheater (the hot casing at the back) into the top. The effluent leaves at
// the bottom. A row of thermocouples down the front reports to a junction box (each bed's temperature drop is
// what the operator watches), and the catalyst unloading nozzle with its blind sits low down.
(function () {
  const path = require('path');
  const { writePng } = PCART;
  const C = [8, 8];
  const MAT = {
    clad: { tex: 'pwr_reactor/clad', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 }, hot: { tex: 'tube_furnace/hot_casing', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'pwr_reactor/clad' });

  function reactor() {
    const m = mk();
    m.cylY('skirt', C, 4.4, 0, 3, 'ci', { bottom: true });
    m.box('skirt_opening', [6.6, 0.4, 3.4], [9.4, 2.4, 3.8], 'soot', { split: '' });
    m.cylY('bottom_head', C, 5.2, 3, 4, 'clad', { bottom: true });
    m.cylY('shell', C, 6, 4, 13, 'clad', { seed: 2, cap: 'material/steel', bottom: true });
    m.cylY('top_head', C, 5, 13, 14, 'clad', { seed: 4, cap: 'material/steel' });
    m.cylY('top_nozzle', C, 1.8, 14, 15, 'st', { cap: 'material/steel' });
    // hot feed from the interheater at the back, effluent out of the bottom to the east
    m.box('feed_over', [7, 14.4, 8], [9, 16, 16], 'clad', { split: '' });
    m.box('interheater_wall', [0, 0, 15], [16, 16, 16], 'hot', { split: '' });
    m.box('effluent', [12, 1.2, 7], [16, 3, 9], 'clad', { split: '' });
    m.box('effluent_flange', [15.4, 0.8, 6.6], [16, 3.4, 9.4], 'st', { split: '' });
    // thermocouples down the front to a junction box; catalyst unloading nozzle with its blind
    for (const y of [5.5, 8, 10.5]) {
      m.box(`tc_${y}`, [7.6, y, 1.4], [8.4, y + 0.8, 2.2], 'st', { split: '' });
      m.box(`tc_head_${y}`, [7.3, y - 0.1, 0.8], [8.7, y + 0.9, 1.4], 'yel', { split: '' });
    }
    m.box('tc_conduit', [9, 5.4, 1], [9.6, 12, 1.6], 'st', { split: '' });
    m.box('tc_junction', [9.6, 10, 0.6], [12, 12.6, 2], 'yel', { split: '' });
    m.box('unload_nozzle', [2.6, 3.6, 3.4], [4, 5, 4.8], 'st', { split: '' });
    m.box('unload_blind', [2, 3.2, 3], [2.6, 5.4, 5.2], 'red', { split: '' });
    m.box('nameplate', [4.4, 6, 2.2], [6.6, 7.4, 2.6], 'br', { split: '' });
    return m;
  }

  const BUILDS = { catalytic_reformer: { model: reactor } };
  window.CRF = {
    paint: () => 0, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'refinery', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, reactor(), 'catalytic_reformer_preview'); },
  };
})();
