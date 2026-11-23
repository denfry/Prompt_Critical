// DA-25 atmospheric deaerator at real scale (1 block = 1 m): a horizontal storage tank Ø 2.0 m x 5.5 m on concrete
// saddles with a deaerating column Ø 1.0 m x 2.5 m standing on its east end, both in aluminium cladding over the
// lagging. A service platform with handrails on the tank top, a ladder at the west end, the level gauge glass, a
// hydraulic seal and the pipe connections in GOST 14202 colours (water green, steam red). Dimensions and sources:
// docs/research/deaerator-dimensions.md (disk only).
//
// Footprint (agreed with 8f): Footprint.tiled(-2, 3, 0, 4, -1, 1), 6 x 5 x 3 (model x -32..64, y 0..80, z -16..32),
// controller (0, 0, 0), front north. Ports (cell, face, px):
//   chemically treated feedwater in: (2, 4, -1) north face, at (x 40, y 72, z -16)
//   condensate return in:            (2, 4, 1) south face, at (x 40, y 72, z 32)
//   heating steam in:                (3, 2, 0) east face, at (x 64, y 42, z 8)
//   deaerated water out:             (0, 0, 0) bottom face, at (x 8, y 0, z 8)
//   vapour vent:                     (2, 4, 0) top face, at (x 40, y 80, z 8)
// Climbable: the ladder (elements ladder_*) up the west end, cells (-2, 0..2, 0).
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then DAR.build(repo) /* wait */ DAR.save(repo).
(function () {
  const ID = 'deaerator_real';
  const TY = 20, TZ = 8, TR = 16, TX = [-28, 60];       // tank axis (y, z), radius, shell ends
  const CC = [40, 8], CR = 8, CY = [34, 74];             // column axis (x, z), radius, shell y range
  // prismY maps its side texture inside one block: build tall courses per block and lift them
  function courseY(m, name, c, r, y0, y1, mat, o) {
    let k = 0;
    for (let a = y0; a < y1 - 1e-6; ) {
      const b = Math.min(y1, (Math.floor(a / 16 + 1e-6) + 1) * 16), off = Math.floor(a / 16 + 1e-6) * 16, n0 = m.els.length;
      m.prismY(`${name}_${k++}`, c, r, a - off, b - off, mat, o || {});
      for (const e of m.els.slice(n0)) {
        e.from = [e.from[0], e.from[1] + off, e.from[2]]; e.to = [e.to[0], e.to[1] + off, e.to[2]];
        if (e.rot) e.rot = Object.assign({}, e.rot, { origin: [e.rot.origin[0], e.rot.origin[1] + off, e.rot.origin[2]] });
      }
      a = b;
    }
  }
  const pipeX = (m, n, y, z, x0, x1, mat) => m.box(n, [x0, y - 1, z - 1], [x1, y + 1, z + 1], mat, { split: 'x' });
  const pipeY = (m, n, x, z, y0, y1, mat) => m.box(n, [x - 1, y0, z - 1], [x + 1, y1, z + 1], mat, { split: 'y' });
  const pipeZ = (m, n, x, y, z0, z1, mat) => m.box(n, [x - 1, y - 1, z0], [x + 1, y + 1, z1], mat, { split: 'z' });

  function model() {
    const m = PCVV.mk('vver/clad');
    // saddles and the storage tank, shell cut per block along x, dished ends
    for (const x of [-18, 44]) {
      m.box(`saddle_${x}`, [x - 3, 0, TZ - 12], [x + 3, 6, TZ + 12], 'conc', { split: '' });
      m.box(`saddle_cradle_${x}`, [x - 3.4, 6, TZ - 10], [x + 3.4, 8, TZ + 10], 'st', { split: '' });
    }
    for (let x = TX[0]; x < TX[1]; x += 16) m.prism(`tank_${x}`, 'x', [TY, TZ], TR, x, Math.min(TX[1], x + 16), 'clad', { split: '' });
    m.prism('end_w_1', 'x', [TY, TZ], TR - 2.5, TX[0] - 1.6, TX[0], 'clad', { split: '' });
    m.prism('end_w_2', 'x', [TY, TZ], TR - 7, TX[0] - 2.8, TX[0] - 1.6, 'clad', { split: '', ends: ['west'] });
    m.prism('end_e_1', 'x', [TY, TZ], TR - 2.5, TX[1], TX[1] + 1.6, 'clad', { split: '' });
    m.prism('end_e_2', 'x', [TY, TZ], TR - 7, TX[1] + 1.6, TX[1] + 2.8, 'clad', { split: '', ends: ['east'] });
    for (const x of [-8, 24]) m.ring(`band_${x}`, 'x', [TY, TZ], TR + 0.3, 0.6, x, x + 1, 'st');
    // deaerating column on the east end, its flanges and the vent
    courseY(m, 'column', CC, CR, CY[0], CY[1], 'clad');
    for (const y of [50, 62]) m.ringY(`col_flange_${y}`, CC, CR + 0.8, 1.2, y, y + 1, 'st');
    m.prismY('col_dome_1', CC, CR - 1.6, CY[1], CY[1] + 1.6, 'clad', {});
    m.prismY('col_dome_2', CC, CR - 4.4, CY[1] + 1.6, 77, 'clad', { ends: ['up'] });
    m.prismY('vent', CC, 1.2, 77, 80, 'st', { ends: ['up'] });
    m.box('vent_valve', [38.6, 77.6, 9.4], [41.4, 79, 11], 'ci', { split: '' });
    // feedwater (north) and condensate (south) into the column head, steam into its foot from the east
    pipeZ(m, 'feed_in', 40, 72, -16, CC[1] - CR + 0.4, 'gw');
    m.box('feed_valve', [38.6, 70.6, -12], [41.4, 73.4, -9.6], 'ci', { split: '' });
    m.box('feed_wheel', [38.4, 73.4, -12.4], [41.6, 74, -9.2], 'red', { split: '' });
    pipeZ(m, 'cond_in', 40, 72, CC[1] + CR - 0.4, 32, 'gw');
    m.box('cond_valve', [38.6, 70.6, 25.6], [41.4, 73.4, 28], 'ci', { split: '' });
    pipeX(m, 'steam_in', 42, 8, CC[0] + CR - 0.4, 64, 'gs');
    m.box('steam_valve', [56, 40.6, 6.6], [58.4, 43.4, 9.4], 'ci', { split: '' });
    m.box('steam_wheel', [55.6, 43.4, 6.4], [58.8, 44, 9.6], 'red', { split: '' });
    // deaerated water out under the tank, the drain
    pipeY(m, 'outlet', 8, 8, 0, TY - TR + 0.4, 'gw');
    m.box('outlet_flange', [5.4, 0, 5.4], [10.6, 0.6, 10.6], 'ci', { split: '' });
    pipeY(m, 'drain', -22, 8, 1, TY - TR + 0.4, 'gw');
    // level gauge glass and pressure gauge on the north face, hydraulic seal (overflow) pipe on the west of the column
    m.box('gauge_glass', [-2, 10, TZ - TR - 1.2], [0, 30, TZ - TR - 0.2], 'bright', { split: '' });
    for (const y of [10, 29]) m.box(`gauge_cock_${y}`, [-2.6, y - 0.6, TZ - TR - 1.6], [0.6, y + 0.6, TZ - TR + 0.4], 'br', { split: '' });
    m.box('pg_pipe', [30, TY + TR - 0.4, -2], [31, 40, -1], 'br', { split: '' });
    m.disc('pressure_gauge', 'z', [30.5, 41.6], 1.6, -2.6, -2, 'br', 'material/gauge_dial', 'north');
    pipeY(m, 'seal_pipe', 30, 18, 34, 46, 'gw');
    pipeY(m, 'seal_leg', 30, 26, 4, 34, 'gw');
    pipeZ(m, 'seal_cross', 30, 46, 18, 26, 'gw');
    // service platform on the tank top round the column, handrails, ladder up the west end
    m.box('platform', [14, TY + TR, -10], [58, TY + TR + 0.8, 26], 'grate', { split: 'x' });
    for (const [n, f, t] of [['n', [14, TY + TR + 0.8, -10], [58, TY + TR + 9, -9.4]], ['s', [14, TY + TR + 0.8, 25.4], [58, TY + TR + 9, 26]]]) {
      m.box(`rail_top_${n}`, [f[0], t[1] - 0.6, f[2]], [t[0], t[1], t[2]], 'yel', { split: 'x' });
      m.box(`rail_mid_${n}`, [f[0], t[1] - 4.6, f[2]], [t[0], t[1] - 4, t[2]], 'yel', { split: 'x' });
      for (let x = 14; x <= 58; x += 11) m.box(`post_${n}_${x}`, [Math.min(x, 57.4), f[1], f[2]], [Math.min(x, 57.4) + 0.6, t[1], t[2]], 'yel', { split: '' });
    }
    m.box('rail_top_e', [57.4, TY + TR + 8.4, -10], [58, TY + TR + 9, 26], 'yel', { split: 'z' });
    m.box('rail_top_w', [14, TY + TR + 8.4, -10], [14.6, TY + TR + 9, 4], 'yel', { split: 'z' });
    // ladder from the floor at the west end up over the tank to the platform
    for (const z of [3, 13]) m.box(`ladder_stile_${z}`, [-31.6, 0, z], [-30.8, TY + TR + 1, z + 0.8], 'yel', { split: 'y' });
    for (let y = 2; y < TY + TR; y += 4) m.box(`ladder_rung_${y}`, [-31.4, y, 3.8], [-31, y + 0.6, 13], 'st', { split: '' });
    m.box('walkway', [-31, TY + TR, 2], [14, TY + TR + 0.8, 14], 'grate', { split: 'x' });
    m.decal('plate', [-8, 12, TZ - TR - 0.4], [0, 16.5, TZ - TR - 0.2], 'clad', 'north', 'pl.maker');
    return m;
  }

  window.DAR = {
    ID,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'water_treatment'),
    preview: (root) => { const m = model(); PCART.realize(root, m, 'da_preview'); return m.els.length; },
  };
})();
