// Copper (and nickel) electrorefining cell at real scale (1 block = 1 m, 16 px = 1 m): one cell of a tankhouse row,
// a lead-lined concrete tank 4.6 m long, 1.15 m wide and 1.3 m deep on concrete piers with insulating pads, the
// electrolyte near the brim, 30 cast anodes and 31 cathode starting sheets hung across it from their lugs on the two
// side busbars (contact bars) along the tank walls, the electrolyte feed and overflow, and the crane bail rails.
// electrorefining_cell_real_ni is the nickel cell: the green nickel electrolyte and the cathodes in their diaphragm
// bags (canvas frames) round each cathode.
//
// Load after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for v in cu, ni:
//   ERR.build(repo, v); /* wait */ ERR.save(repo, v)
//
// Controller block = (0, 0, 0), front north. Footprint x -2..2, y 0..1, z 0..1; tank along x. Connections (px):
// DC in on the west face of (-2, 1, 0) at (-32, 20, 4) (the north contact bar) and out on the east face of
// (2, 1, 1) at (48, 20, 28) (the south bar); electrolyte in on the west face of (-2, 0, 1) at (-32, 12, 24);
// overflow out on the east face of (2, 0, 0) at (48, 8, 8). No partials.
(function () {
  const X = [-30, 46], Z = [3, 29], YB = 6, YT = 27, YE = 25;     // tank outer x, z, floor, brim, electrolyte
  const MAT = Object.assign({}, PCVV.MAT, {
    cu: { tex: 'disconnector/copper', P: 16 }, lead: { tex: 'material/steel', P: 16 }, frame: { tex: 'plant/structure', P: 16 },
    ecu: { tex: 'plant/blue', P: 16 }, eni: { tex: 'material/paint_green', P: 16 }, anode: { tex: 'material/cast_iron', P: 16 },
    canvas: { tex: 'material/pipe_lagging', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/concrete' });

  function tank(m, ni) {
    for (const x of [-26, 8, 42]) {
      m.box(`pier_${x}`, [x - 3, 0, Z[0] + 2], [x + 3, YB - 1, Z[1] - 2], 'conc', { split: '' });
      m.box(`pad_${x}`, [x - 3, YB - 1, Z[0] + 2], [x + 3, YB, Z[1] - 2], 'dark', { split: '' });
    }
    m.box('tank_floor', [X[0], YB, Z[0]], [X[1], YB + 2, Z[1]], 'conc', { split: 'x' });
    m.box('wall_n', [X[0], YB + 2, Z[0]], [X[1], YT, Z[0] + 2], 'conc', { split: 'x' });
    m.box('wall_s', [X[0], YB + 2, Z[1] - 2], [X[1], YT, Z[1]], 'conc', { split: 'x' });
    m.box('wall_w', [X[0], YB + 2, Z[0] + 2], [X[0] + 2, YT, Z[1] - 2], 'conc', { split: '' });
    m.box('wall_e', [X[1] - 2, YB + 2, Z[0] + 2], [X[1], YT, Z[1] - 2], 'conc', { split: '' });
    // lead lining showing at the brim
    m.box('lining_n', [X[0] + 2, YE, Z[0] + 2], [X[1] - 2, YT, Z[0] + 2.3], 'lead', { split: 'x' });
    m.box('lining_s', [X[0] + 2, YE, Z[1] - 2.3], [X[1] - 2, YT, Z[1] - 2], 'lead', { split: 'x' });
    m.box('electrolyte', [X[0] + 2, YE - 0.4, Z[0] + 2.3], [X[1] - 2, YE, Z[1] - 2.3], ni ? 'eni' : 'ecu', { split: 'x', hide: ['down', 'north', 'south', 'east', 'west'] });
  }

  // Contact bars along both walls, anodes and cathodes alternating across the tank.
  function electrodes(m, ni) {
    m.box('bar_n', [X[0] - 2, YT, Z[0] + 0.6], [X[1] + 2, YT + 1, Z[0] + 1.6], 'cu', { split: 'x' });
    m.box('bar_s', [X[0] - 2, YT, Z[1] - 1.6], [X[1] + 2, YT + 1, Z[1] - 0.6], 'cu', { split: 'x' });
    m.box('bar_ins_n', [X[0], YT - 0.3, Z[0] + 0.4], [X[1], YT, Z[0] + 1.8], 'dark', { split: 'x' });
    m.box('bar_ins_s', [X[0], YT - 0.3, Z[1] - 1.8], [X[1], YT, Z[1] - 0.4], 'dark', { split: 'x' });
    const n = 30, x0 = X[0] + 4, step = (X[1] - X[0] - 8) / n;
    for (let i = 0; i <= n; i++) {
      const xc = x0 + i * step;
      // cathode: thin sheet on its copper hanger bar spanning both bars
      m.box(`cathode_${i}`, [xc - 0.15, YB + 5, Z[0] + 3.4], [xc + 0.15, YT - 0.6, Z[1] - 3.4], 'cu', { split: '' });
      m.box(`hanger_${i}`, [xc - 0.3, YT + 1, Z[0] + 0.6], [xc + 0.3, YT + 1.6, Z[1] - 0.6], 'cu', { split: '' });
      if (ni) m.box(`bag_${i}`, [xc - 0.7, YB + 4, Z[0] + 3], [xc + 0.7, YT + 0.4, Z[1] - 3], 'canvas', { split: '' });
      if (i === n) break;
      // anode: thicker cast plate with its two lugs, one on each bar
      const xa = xc + step / 2;
      m.box(`anode_${i}`, [xa - 0.35, YB + 4, Z[0] + 3], [xa + 0.35, YT - 0.4, Z[1] - 3], 'anode', { split: '' });
      for (const z of [Z[0] + 1.6, Z[1] - 2.6]) m.box(`lug_${i}_${z}`, [xa - 0.35, YT, z], [xa + 0.35, YT + 1.2, z + 1], 'anode', { split: '' });
    }
  }

  function pipes(m, ni) {
    // feed pipe in at the west end below the surface, overflow box and drop at the east end
    m.prism('feed', 'x', [12, 24], 0.8, -32, X[0], 'dark', { split: '' });
    m.disc('feed_flange', 'x', [12, 24], 1.4, -31.9, -31.4, 'ci', 'vver/flange', 'west');
    m.box('overflow_box', [X[1], 18, 5], [X[1] + 1.6, YE, 11], 'lead', { split: '' });
    m.prism('overflow', 'x', [8, 8], 0.8, X[1], 48, 'dark', { split: '' });
    m.cylY('overflow_drop', [X[1] + 0.8, 8], 0.8, 8, 18, 'dark');
    m.decal('plate', [-4, 14, Z[0] - 0.1], [4, 18, Z[0]], 'conc', 'north', 'pl.maker');
    if (ni) m.box('ni_mark', [8, 18, Z[0] - 0.1], [12, 22, Z[0]], 'eni', { split: '' });
  }

  function model(ni) { const m = mk(); tank(m, ni); electrodes(m, ni); pipes(m, ni); return m; }
  const ID = { cu: 'electrorefining_cell_real', ni: 'electrorefining_cell_real_ni' };
  window.ERR = {
    ID, model,
    build: (root, v) => { const m = PCVV.cut(model(v === 'ni')); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, ID[v], 'nonferrous'),
  };
})();
