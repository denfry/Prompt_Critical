// Acheson graphitising furnace at real scale (1 block = 1 m, 16 px = 1 m) for the reactor-grade graphite of the I1
// pile: an open trough 12 m long and 3 m wide on a concrete bed, the two refractory head walls each carrying six
// graphite current electrodes held in water-cooled copper clamps, the copper bus bars from the clamps along the
// north side to the transformer lead-in at the west end, loose firebrick side walls with vent gaps, and the charge
// of baked electrodes buried in the resistor coke and covered by the sand-coke insulation mound.
// acheson_furnace_real_lit is the same furnace while it runs (LIT): the mound cracked and glowing, the CO burning
// off in small flames at the side wall gaps and over the cracks.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for v in cold, lit:
//   ACHR.build(repo, v); /* wait */ ACHR.save(repo, v)
//
// Controller block = (0, 0, 0) at the north edge of the middle; footprint x -6..5, y 0..1, z 0..2.
// Connections (px): power in on the west face of (-6, 1, 0) at (-96, 26, 5), where the two bus bars end at the
// lead-in; the charge is loaded and unloaded over the open top (no item port faces).
// No partials (the furnace has no moving parts; the running look is the _lit whole model).
(function () {
  const X = [-96, 96], Z = [0, 48], HW = 12;             // trough length, width, head wall thickness
  const YW = 22, YM = 28;                                // side wall top, mound crest
  const MAT = Object.assign({}, PCVV.MAT, {
    cu: { tex: 'disconnector/copper', P: 16 }, por: { tex: 'disconnector/porcelain', P: 16 }, soot: { tex: 'material/soot', P: 16 }, frame: { tex: 'plant/structure', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/brick' });

  function bed(m) {
    m.box('bed', [X[0], 0, Z[0]], [X[1], 2, Z[1]], 'conc', { split: 'xz' });
  }

  function heads(m) {
    for (const [n, x0, x1, s] of [['w', X[0], X[0] + HW, -1], ['e', X[1] - HW, X[1], 1]]) {
      m.box(`head_${n}`, [x0, 2, Z[0]], [x1, 30, Z[1]], 'brick', { split: 'xyz' });
      m.box(`head_cap_${n}`, [x0, 30, Z[0]], [x1, 31, Z[1]], 'ci', { split: 'z' });
      // tie rods and buckstays holding the head wall
      for (const z of [2, 46]) m.box(`buckstay_${n}_${z}`, [s < 0 ? x0 : x1 - 1, 2, z - 0.6], [s < 0 ? x0 + 1 : x1, 31, z + 0.6], 'frame', { split: 'y' });
      // six electrodes in two rows through the wall, their clamps on the outer face
      for (const y of [10, 19]) for (const z of [12, 24, 36]) {
        const xo = s < 0 ? x0 : x1, xi = s < 0 ? x1 : x0;
        m.prism(`electrode_${n}_${y}_${z}`, 'x', [y, z], 3, Math.min(xo - s, xi - 2 * s), Math.max(xo - s, xi - 2 * s), 'dark', { split: '' });
        m.box(`clamp_${n}_${y}_${z}`, [s < 0 ? xo : xo - 1, y - 3.6, z - 3.6], [s < 0 ? xo + 1 : xo, y + 3.6, z + 3.6], 'cu', { split: '' });
        m.box(`clamp_water_${n}_${y}_${z}`, [s < 0 ? xo + 0.2 : xo - 0.8, y + 3.6, z - 0.6], [s < 0 ? xo + 0.8 : xo - 0.2, y + 5, z + 0.6], 'gw', { split: '' });
      }
      // vertical copper risers joining the clamps, then the bus over the wall top to the north side
      for (const z of [12, 24, 36]) m.box(`riser_${n}_${z}`, [s < 0 ? x0 + 0.2 : x1 - 0.8, 8, z - 1], [s < 0 ? x0 + 0.8 : x1 - 0.2, 26, z + 1], 'cu', { split: '' });
      m.box(`bus_head_${n}`, [s < 0 ? x0 + 0.2 : x1 - 0.8, 24, 4], [s < 0 ? x0 + 0.8 : x1 - 0.2, 26, 37], 'cu', { split: '' });
    }
    // two bus bars along the north side on insulator posts, both ending at the west lead-in
    for (const [k, y] of [[0, 24], [1, 27]]) m.box(`bus_${k}`, [X[0], y, 4], [X[1] - 0.2, y + 1.6, 5.2], 'cu', { split: 'x' });
    for (let x = -80; x <= 80; x += 20) {
      m.box(`bus_post_${x}`, [x - 0.8, 2, 3.8], [x + 0.8, 23.6, 5.4], 'frame', { split: 'y' });
      m.box(`bus_insulator_${x}`, [x - 1.2, 23.6, 3.4], [x + 1.2, 24, 5.8], 'por', { split: '' });
    }
    m.box('lead_in', [X[0], 20, 2], [X[0] + 2, 30, 8], 'frame', { split: '' });
  }

  // Loose firebrick side walls with vent gaps every 1.5 m; the gaps show the hot charge when lit.
  function sides(m, lit) {
    for (const [n, z0, z1] of [['n', 6, 9], ['s', 39, 42]]) {
      for (let x = X[0] + HW; x < X[1] - HW; x += 24) {
        m.box(`side_${n}_${x}`, [x, 2, z0], [Math.min(x + 22, X[1] - HW), YW, z1], 'brick', { split: 'xy' });
        if (x + 22 < X[1] - HW) {
          m.box(`gap_${n}_${x}`, [x + 22, 4, z0 + 1], [x + 24, YW - 4, z1 - 1], lit ? 'gs' : 'soot', { split: '' });
          if (lit) m.box(`glow_gap_${n}_${x}`, [x + 22.4, 6, n === 'n' ? z0 - 0.01 : z1], [x + 23.6, YW - 6, n === 'n' ? z0 : z1 + 0.01], 'yel', { split: '' });
        }
      }
    }
  }

  // Insulation mound over the charge, stepped to a crest along the middle; lit: cracks glowing and CO flames.
  function mound(m, lit) {
    const xa = X[0] + HW, xb = X[1] - HW;
    m.box('fill', [xa, 2, 9], [xb, YW, 39], 'dark', { split: 'xz', hide: ['up'] });
    const steps = [[9, 39, YW, YW + 2], [12, 36, YW + 2, YW + 4], [16, 32, YW + 4, YM]];
    steps.forEach(([z0, z1, y0, y1], i) => m.box(`mound_${i}`, [xa, y0, z0], [xb, y1, z1], 'soot', { split: 'xz' }));
    if (!lit) return;
    for (let x = xa + 8; x < xb - 4; x += 16) {
      const z = 20 + ((x / 16) % 3) * 3;
      m.box(`glow_crack_${x}`, [x, YM, z], [x + 6, YM + 0.05, z + 1.2], 'yel', { split: '' });
      m.box(`glow_flame_${x}`, [x + 2, YM, z - 0.4], [x + 3.6, YM + 3.6, z + 1.6], 'yel', { split: '', hide: ['down'] });
      m.box(`glow_crack_side_${x}`, [x + 4, YW + 2, 12], [x + 9, YW + 2.05, 13], 'gs', { split: '' });
    }
  }

  function model(lit) {
    const m = mk(); bed(m); heads(m); sides(m, lit); mound(m, lit);
    m.decal('plate', [-6, 6, 5.9], [2, 10, 6], 'brick', 'north', 'pl.maker');
    return m;
  }
  const ID = { cold: 'acheson_furnace_real', lit: 'acheson_furnace_real_lit' };
  window.ACHR = {
    ID, model,
    build: (root, v) => { const m = PCVV.cut(model(v === 'lit')); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, ID[v], 'fuel_fabrication'),
  };
})();
