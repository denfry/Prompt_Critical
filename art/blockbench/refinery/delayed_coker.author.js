// Delayed coker, hand-authored for Blockbench (replaces the borrowed tube_furnace_controller): the pair of coke
// drums on their concrete table with the decoking derrick, the switch valve and the overhead vapour line.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/delayed_coker.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); DCK.paint('<repo>')
//   build + save each model in turn (DCK.build / DCK.save, ids in DCK.ids)
//
// 1x1x1, front to the north. Two drums side by side (west x 4.5, east x 11.5, both z 9, r 3.2, shell y 3.6..11.4),
// clad in aluminium over the insulation, on a concrete table with the bottom heads over the coke chute in front.
// The deck at y 9 round the drums carries the derrick over the east drum (the one off line for hydraulic decoking) and the
// overhead vapour line, which leaves through the south face to the fractionator. The feed comes up to the
// four-way switch valve between the drums at the front.
// Drill partial (delayed_coker/partial/drill): the crosshead and the drill stem over the east drum, axis x 11.5,
// z 9; CokerRenderer lowers it by up to 4 px while the coker runs (one cutting pass every 20 s).
// MachineFx points: steam vent at x 11.5, y 13.6, z 6.7 (top of the off-line drum), coke chute at x 11.5, y 1, z 2.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Aluminium jacketing over the drum insulation: overlapped bands every 6 px, a vertical lap seam, dull patches.
  T['delayed_coker/cladding'] = () => {
    const im = img(32, 32), r = grain(im, [178, 182, 184], 3301, 3, 6, 8);
    for (let y = 0; y < 32; y += 6) for (let x = 0; x < 32; x++) { put(im, x, y, [128, 132, 134]); put(im, x, y + 1, [204, 208, 210]); }
    for (let y = 0; y < 32; y++) put(im, 13, y, [140, 144, 146]);
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [120, 104, 80]);
    return im;
  };
  // Cladding seen from above, radial, with coke dust settled on the top head.
  T['delayed_coker/head'] = () => {
    const im = img(16, 16), r = rng(3311);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      let c = d > 6.5 ? [150, 154, 156] : [176, 180, 182];
      if (d < 2.4) c = [80, 82, 84];
      if (r() < 0.12) c = [44, 42, 40];
      put(im, x, y, add(c, (r() - 0.5) * 8));
    }
    return im;
  };
  // Structural steel of the coker structure, grey primer gone dark with coke dust.
  T['delayed_coker/structure'] = () => {
    const im = img(32, 32), r = grain(im, [88, 92, 96], 3321, 3, 6, 8);
    for (let k = 0; k < 40; k++) put(im, r() * 32, r() * 32, [40, 40, 40]);
    return im;
  };
  // Petroleum coke in the chute and the pit: black, glinting lumps.
  T['delayed_coker/coke'] = () => {
    const im = img(16, 16), r = grain(im, [30, 30, 32], 3331, 6, 8, 3);
    for (let k = 0; k < 14; k++) put(im, r() * 16, r() * 16, [90, 92, 96]);
    return im;
  };
  // Hot oil pipework, uninsulated near the valve, heat-tinted.
  T['delayed_coker/hot_pipe'] = () => {
    const im = img(16, 16), r = grain(im, [96, 76, 62], 3341, 4, 6, 4);
    for (let k = 0; k < 8; k++) put(im, r() * 16, r() * 16, [60, 48, 40]);
    return im;
  };

  const MAT = {
    clad: { tex: 'delayed_coker/cladding', P: 16 }, stc: { tex: 'delayed_coker/structure', P: 16 }, coke: { tex: 'delayed_coker/coke', P: 16 },
    hot: { tex: 'delayed_coker/hot_pipe', P: 16 }, con: { tex: 'candu_reactor/concrete', P: 32 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'delayed_coker/cladding' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function coker() {
    const m = mk();
    // concrete table on four legs, the chute between them in front
    for (const [x, z] of [[0, 4], [13, 4], [0, 13], [13, 13]]) m.box(`table_leg_${x}_${z}`, [x, 0, z], [x + 3, 2.4, z + 3], 'con');
    m.box('table', [0, 2.4, 4], [16, 3.4, 16], 'con');
    m.box('chute', [9, 0, 1], [14, 1.6, 4], 'stc', { split: '' });
    m.box('coke_pile', [8.6, 0, 0.4], [14.4, 0.6, 3.6], 'coke', { split: '' });
    m.box('coke_pile_top', [9.6, 0.6, 1.2], [13.4, 1.2, 3], 'coke', { split: '' });
    for (const [n, x] of [['w', 4.5], ['e', 11.5]]) {
      // bottom head and unheading valve under the table, conical bottom, the shell and the top head
      m.prismY(`bottom_valve_${n}`, [x, 9], 1.4, 1.4, 2.4, 'stc', { ends: ['down'] });
      m.prismY(`cone_${n}`, [x, 9], 2.2, 3.4, 3.6, 'clad');
      m.prismY(`drum_${n}`, [x, 9], 3.2, 3.6, 11.4, 'clad', { tex: 'delayed_coker/cladding', seed: n === 'w' ? 0 : 5 });
      m.prismY(`top_head_${n}`, [x, 9], 2.6, 11.4, 12, 'clad', { cap: 'delayed_coker/head' });
      m.prismY(`top_nozzle_${n}`, [x, 9], 0.9, 12, 13, 'stc', { ends: ['up'] });
      // vapour line from each top head to the common overhead going south
      m.box(`vapour_${n}`, [x - 0.5, 12.2, 9.8], [x + 0.5, 13, 16], 'hot', { split: '' });
    }
    // switch valve between the drums at the front, the feed line up from the heater and its two drum inlets
    m.box('feed_riser', [7.4, 0, 1.4], [8.6, 4.6, 2.6], 'hot', { split: '' });
    m.box('switch_valve', [6.8, 4.6, 0.8], [9.2, 6.6, 3.2], 'stc', { split: '' });
    m.box('switch_actuator', [7.4, 6.6, 1.4], [8.6, 8.4, 2.6], 'green', { split: '' });
    for (const [n, x0, x1] of [['w', 4.5, 7], ['e', 9, 11.5]]) m.box(`inlet_${n}`, [x0 - (n === 'w' ? 0 : 0), 5, 1.6], [x1, 5.8, 2.4], 'hot', { split: '' });
    for (const x of [4.1, 11.1]) m.box(`inlet_drop_${x}`, [x, 3.6, 1.6], [x + 0.8, 5.8, 5.9], 'hot', { split: '' });
    // structure: four columns to the deck, the deck, a handrail on the front edge
    for (const [x, z] of [[0, 4], [15, 4], [0, 15], [15, 15]]) m.box(`column_${x}_${z}`, [x, 3.4, z], [x + 1, 9, z + 1], 'stc', { split: 'y' });
    // deck at y 9 round the drums: the drums pass through it, their heads stand above
    for (const [k, f, t] of [['n', [0, 9, 4], [16, 9.5, 5.6]], ['s', [0, 9, 12.4], [16, 9.5, 16]], ['w', [0, 9, 5.6], [1.2, 9.5, 12.4]],
      ['e', [14.8, 9, 5.6], [16, 9.5, 12.4]], ['m', [7.7, 9, 5.6], [8.3, 9.5, 12.4]]]) m.box(`deck_${k}`, f, t, 'chequer', { split: '' });
    for (let x = 0; x <= 15; x += 5) m.box(`rail_post_${x}`, [Math.min(x, 15.4), 9.5, 4], [Math.min(x, 15.4) + 0.6, 11.8, 4.6], 'yel_rail', { split: '' });
    m.box('rail_top', [0, 11.2, 4], [16, 11.8, 4.6], 'yel_rail', { split: '' });
    // derrick over the east drum: four legs on the deck to the crown block, braces
    const legs = [[8.4, 4.6], [14.4, 4.6], [8.4, 12.8], [14.4, 12.8]];
    for (const [x, z] of legs) m.box(`derrick_leg_${x}_${z}`, [x, 9.5, z], [x + 0.6, 16, z + 0.6], 'stc', { split: '' });
    for (const y of [13.4, 15.4]) {
      m.box(`derrick_brace_n_${y}`, [8.4, y, 4.6], [15, y + 0.5, 5.2], 'stc', { split: '' });
      m.box(`derrick_brace_s_${y}`, [8.4, y, 12.8], [15, y + 0.5, 13.4], 'stc', { split: '' });
      m.box(`derrick_brace_w_${y}`, [8.4, y, 5.2], [9, y + 0.5, 12.8], 'stc', { split: '' });
    }
    m.box('crown_block', [10.6, 15.4, 8], [12.4, 16, 10], 'red', { split: '' });
    // west drum on line: the vapour line is lagged, the drum hot; a pressure gauge and the steam purge on the east
    m.box('purge_line', [14.8, 3.6, 8.6], [15.4, 9, 9.4], 'st', { split: '' });
    m.box('vent_stub', [11, 12, 6.2], [12, 13.6, 7.2], 'st', { split: '' });
    return m;
  }
  MAT.yel_rail = { tex: 'candu_reactor/yellow', P: 16 };

  // Drill partial: crosshead running in the derrick and the drill stem down into the top nozzle of the east drum.
  function drill() {
    const m = mk('delayed_coker/structure');
    m.box('crosshead', [9, 14.4, 8.4], [14.4, 15, 9.6], 'yel_rail', { split: '' });
    m.box('swivel', [11, 13.6, 8.5], [12, 14.4, 9.5], 'stc', { split: '' });
    m.box('stem', [11.25, 12.4, 8.75], [11.75, 13.6, 9.25], 'bright', { split: '' });
    m.box('hose', [12, 14.4, 8.8], [15.4, 14.8, 9.2], 'soot', { split: '' });
    return m;
  }

  const BUILDS = {
    delayed_coker: { model: coker },
    'delayed_coker/drill': { model: drill, java: 'delayed_coker/partial/drill' },
  };

  window.DCK = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'refinery', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, coker().merge(drill(), 0, 0, 0, 'd_'), 'delayed_coker_preview'); },
  };
})();
