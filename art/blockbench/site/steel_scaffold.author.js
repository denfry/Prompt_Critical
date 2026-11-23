// Steel scaffold module (real-scale series): four tube standards, ledgers top and bottom, a diagonal brace on the
// north and south faces, a grating deck with yellow toe boards. 32 px per block. Collision (SteelScaffoldBlock):
// standards and deck. Load after pcart.js, then SCAFFOLD.build(repo); /* wait */ SCAFFOLD.save(repo).
(function () {
  const MAT = { gr: { tex: 'steelmaking/grating', P: 16 }, yl: { tex: 'vver/yellow', P: 8 } };

  function model() {
    const m = new PCART.Model(MAT);
    m.particle = 'material/steel';
    for (const [x, z] of [[0, 0], [14.5, 0], [0, 14.5], [14.5, 14.5]]) m.box(`std_${x}_${z}`, [x, 0, z], [x + 1.5, 16, z + 1.5], 'st', { split: '' });
    for (const y of [0.5, 13]) {
      m.box(`ledger_n_${y}`, [1.5, y, 0.25], [14.5, y + 1, 1.25], 'st', { split: '' });
      m.box(`ledger_s_${y}`, [1.5, y, 14.75], [14.5, y + 1, 15.75], 'st', { split: '' });
      m.box(`ledger_w_${y}`, [0.25, y, 1.5], [1.25, y + 1, 14.5], 'st', { split: '' });
      m.box(`ledger_e_${y}`, [14.75, y, 1.5], [15.75, y + 1, 14.5], 'st', { split: '' });
    }
    // diagonal braces on the north and south faces (45 degrees about z through the face centre)
    for (const z of [0.4, 14.9]) m.box(`brace_${z}`, [-1.2, 7.6, z], [17.2, 8.4, z + 0.7], 'st', { split: '', rot: { axis: 'z', angle: 45, origin: [8, 8, 8] } });
    m.box('deck', [0, 14, 0], [16, 15, 16], 'gr', { split: '' });
    for (const [n, f, t] of [['n', [0, 15, 0], [16, 16.5, 0.4]], ['s', [0, 15, 15.6], [16, 16.5, 16]],
      ['w', [0, 15, 0.4], [0.4, 16.5, 15.6]], ['e', [15.6, 15, 0.4], [16, 16.5, 15.6]]]) m.box(`toe_${n}`, f, t, 'yl', { split: '' });
    return m;
  }

  window.SCAFFOLD = {
    model,
    build: (root) => { PCART.realize(root, model(), 'steel_scaffold'); return Outliner.elements.length; },
    save: (root) => PCART.save(root, 'site', 'steel_scaffold'),
  };
})();
