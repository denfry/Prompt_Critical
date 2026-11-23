// Shaft landing signal at real scale (1 block = 1 m): a timber backboard on the shaft wall with the cast iron bell
// box of the signal (a rapper: the hammer strikes a bronze gong, rung from the other landing by a pull wire), the
// pull knocker handle under it, a signal lamp lit while the cage stands at this landing, and the enamel code board.
// Dimensions: docs/research/shaft_signal-dimensions.md (disk only).
//
// One block, front north, the backboard on the south face (z 15.2..16). Two states: shaft_signal (lamp off) and
// shaft_signal_lit (lamp on, the lens a glow_ element). Load after pcart.js, pcround.js and vverkit.js, then
// SHAFT_SIGNAL.build(repo, lit) /* wait */ SHAFT_SIGNAL.save(repo, lit).
(function () {
  const MAT = { enamel: { tex: 'mine_dispatch_cabinet/enamel', P: 16 } };

  function model(lit) {
    const m = Object.assign(new PCART.Model(Object.assign({}, PCVV.MAT, MAT), PCVV.ATLAS), { particle: 'material/lagging_wood' });
    // backboard with two coach bolts
    m.box('board', [3.2, 0.8, 15.2], [12.8, 15.2, 16], 'wood', { split: '' });
    for (const y of [2, 14]) m.box(`bolt_${y}`, [7.5, y - 0.5, 14.9], [8.5, y + 0.5, 15.2], 'bright', { split: '' });

    // bell box: cast iron, a lid with a lip, the gong on its front, the hammer arm coming out of the right side
    m.box('box', [5.4, 7, 12.4], [10.6, 12.6, 15.2], 'ci', { split: '' });
    m.box('box_lid', [5.1, 12.6, 12.1], [10.9, 13.2, 15.2], 'ci', { split: '' });
    m.box('box_foot', [5.8, 6.4, 13], [10.2, 7, 15.2], 'ci', { split: '' });
    m.disc('gong', 'z', [8, 9.8], 1.8, 11.7, 12.4, 'br', 'material/brass_disc', 'north');
    m.box('gong_stud', [7.7, 9.5, 11.4], [8.3, 10.1, 11.7], 'bright', { split: '' });
    m.box('hammer_arm', [10.6, 11.2, 11.8], [11.2, 11.8, 13.6], 'st', { split: '' });
    m.box('hammer_shank', [8.6, 11.3, 11.3], [10.9, 11.7, 11.7], 'st', { split: '' });
    m.box('hammer_head', [8, 10.9, 10.9], [8.8, 12.1, 11.7], 'ci', { split: '' });

    // pull knocker: rod down from the box, wooden grip
    m.box('knock_rod', [9.2, 3.4, 13.4], [9.6, 7, 13.8], 'st', { split: '' });
    m.box('knock_grip', [8.3, 2.4, 13], [10.5, 3.4, 14.2], 'wood', { split: '' });

    // signal lamp on a bracket at the left: housing, hood, lens (lit: glow)
    m.box('lamp_brk', [4, 4, 14], [5.8, 4.6, 15.2], 'st', { split: '' });
    m.prism('lamp', 'z', [4.6, 4.6], 1.4, 12.6, 14.6, 'ci', { split: '', ends: ['south'] });
    m.box('lamp_hood', [3, 6, 12.2], [6.2, 6.4, 14.6], 'ci', { split: '' });
    m.disc(lit ? 'glow_lens' : 'lens', 'z', [4.6, 4.6], 1.1, 12.3, 12.6, 'ci',
      lit ? 'mine_dispatch_cabinet/lens' : 'mine_dispatch_cabinet/lens_off', 'north');

    // code board under the lid line: 1 stop, 2 raise, 3 lower
    m.box('code', [11.2, 3, 14.9], [12.6, 6.4, 15.2], 'enamel', { split: '' });
    m.box('code_top', [4.2, 13.6, 14.9], [11.8, 14.8, 15.2], 'enamel', { split: '' });
    return m;
  }

  const id = (lit) => (lit ? 'shaft_signal_lit' : 'shaft_signal');
  window.SHAFT_SIGNAL = {
    model,
    build: (root, lit) => { const m = model(lit); PCART.realize(root, m, id(lit)); return m.els.length; },
    save: (root, lit) => PCART.save(root, 'mining', id(lit)),
  };
})();
