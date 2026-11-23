// BN-600 sodium fast reactor at real scale (1 block = 1 m): the pool vessel (12.8 m across, 12.6 m tall) stands in
// a concrete shaft 15 m across, so outside one sees the shaft drum with its stiffening bands, the secondary sodium
// nozzles of the three loops through the wall and a stair up the west side. On the roof (upper fixed shield): the
// large and the eccentric small rotating plug, the control rod drives on the small plug, two refuelling mechanisms,
// the three primary pump motors at 120 degrees and the heads of the six intermediate heat exchangers beside them,
// with a rail round the edge. Dimensions and sources: docs/research/bn600-dimensions.md (disk only).
// Load after vverkit.js, then BN600.build(repo); /* wait */ BN600.save(repo).
//
// Controller block = (0, 0, 0) on the floor under the vessel axis (x = z = 8). Front north.
// Connections (ends on block faces, axes on cell centres), hot leg at y 9, cold leg at y 11:
//   loop 1 east face of (7, 9|11, 0)   loop 2 south face of (0, 9|11, 7)   loop 3 north face of (0, 9|11, -7)
(function () {
  const C = [8, 8], RS = 112, TOP = 208;   // drum drawn at 14 m so the loop nozzles stand out to the 15 m footprint
  const at = (r, deg) => [C[0] + r * Math.cos(deg * Math.PI / 180), C[1] + r * Math.sin(deg * Math.PI / 180)];

  function model() {
    const m = PCVV.mk('vver/concrete');
    // shaft drum with a base ring and steel bands, the roof
    m.cylY('shaft', C, RS, 0, TOP, 'conc', { bottom: true, cap: 'vver/concrete' });
    for (const y of [48, 104, 200]) m.ringY(`band_${y}`, C, RS + 1, 1.5, y, y + 3, 'pnt');
    m.cylY('roof', C, 108, TOP, TOP + 8, 'pnt', { cap: 'vver/grating_disc' });
    m.railY('rail', C, 107.5, TOP + 8, 'yel');

    // secondary sodium nozzles through the wall (sodium: grey with a yellow warning band)
    for (const [tag, axis, a1] of [['e', 'x', 128], ['s', 'z', 128], ['n', 'z', -112]]) {
      for (const y of [152, 184]) {
        const a0 = a1 > 0 ? 104 : -88;
        m.nozzle(`na_${tag}_${y}`, axis, axis === 'x' ? [y, 8] : [8, y], a0, a1, 9, 7, 'yel', { nuts: 16, boss: 'pnt' });
      }
    }

    // large rotating plug, eccentric small plug with the control rod drives, refuelling mechanisms
    m.cylY('plug_l', C, 72, TOP + 8, TOP + 14, 'forged', { cap: 'vver/flange' });
    m.nutsY('plug_l_bolt', C, 69, 32, TOP + 14, 1.5, 1.6, 'bright');
    const S = [C[0] + 24, C[1]];
    m.cylY('plug_s', S, 40, TOP + 14, TOP + 20, 'forged', { cap: 'vver/flange' });
    m.cylY('column', S, 14, TOP + 20, TOP + 34, 'pnt', { cap: 'vver/cap' });
    let k = 0;
    for (let i = -3; i <= 3; i++) for (let j = -3; j <= 3; j++) {
      const x = S[0] + i * 9, z = S[1] + j * 9;
      if (Math.hypot(i, j) > 3.2 || (i === 0 && j === 0)) continue;
      const h = (i + j) % 2 ? 270 : 262;
      m.box(`drv_${k}`, [x - 1.6, TOP + 20, z - 1.6], [x + 1.6, h, z + 1.6], 'drv', { split: 'y' });
      m.box(`drv_cap_${k++}`, [x - 2.2, h, z - 2.2], [x + 2.2, h + 3, z + 2.2], 'mcap', { split: '' });
    }
    m.box('rods_rack', [S[0] - 30, 250, S[1] - 0.6], [S[0] + 30, 251, S[1] + 0.6], 'yel', { split: '' });
    for (const [tag, x, z] of [['a', -40, 8], ['b', 8, -50]]) {
      m.box(`refuel_${tag}`, [x - 6, TOP + 14, z - 6], [x + 6, 288, z + 6], 'pnt', { split: 'y' });
      m.box(`refuel_${tag}_head`, [x - 8, 288, z - 8], [x + 8, 296, z + 8], 'yel', { split: '' });
      m.box(`refuel_${tag}_motor`, [x + 6, 270, z - 3], [x + 11, 280, z + 3], 'motor', { split: '' });
    }

    // three primary pumps with their motors, six intermediate heat exchanger heads
    for (const a of [0, 120, 240]) {
      const p = at(90, a);
      m.cylY(`pump_${a}_stand`, p, 15, TOP + 8, TOP + 30, 'pnt', { cap: 'vver/flange' });
      m.cylY(`pump_${a}_motor`, p, 12, TOP + 30, TOP + 74, 'motor');
      m.cylY(`pump_${a}_cap`, p, 13, TOP + 74, TOP + 80, 'mcap', { cap: 'vver/cap' });
      m.box(`pump_${a}_box`, [p[0] + 12, TOP + 44, p[1] - 4], [p[0] + 18, TOP + 56, p[1] + 4], 'dark', { split: '' });
      for (const d of [-32, 32]) {
        const q = at(90, a + d);
        m.cylY(`ihx_${a}_${d}`, q, 10, TOP + 8, TOP + 28, 'clad', { cap: 'vver/flange' });
        m.cylY(`ihx_${a}_${d}_band`, q, 10.4, TOP + 20, TOP + 22, 'yel');
        m.nutsY(`ihx_${a}_${d}_nut`, q, 8.5, 12, TOP + 28, 1, 1, 'bright');
      }
    }

    // stair up the west side of the shaft to the roof
    m.ladderZ('stair', -111, -107, 2, 1, 0, TOP + 8, 'yel', 0);
    for (let y = 16; y < TOP; y += 48) m.box(`landing_${y}`, [-112, y, -6], [-104, y + 1.5, 10], 'grate', { split: '' });

    // plates on the north side of the shaft
    m.decal('sign', [-2, 120, -104.7], [8, 130, -104], 'conc', 'north', 'pl.sign');
    m.decal('name', [-2, 132, -104.7], [18, 135.6, -104], 'conc', 'north', 'pl.bn_name');
    return m;
  }

  window.BN600 = {
    model,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'bn600_reactor'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'bn600_reactor', 'core/bn'),
  };
})();
