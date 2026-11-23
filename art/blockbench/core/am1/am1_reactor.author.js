// AM-1 of the Obninsk plant at real scale (1 block = 1 m): the concrete shaft round the graphite stack, the cast
// iron top plate with the 151 channel heads (128 fuel, 23 control and protection rods with their servo drives),
// the ring collectors of the coolant over the plate fed from the east and drained to the west, a walkway with a
// rail round the plate and a ladder up the front. Dimensions and sources: docs/research/am1-dimensions.md (disk
// only). Load after vverkit.js, then AM1.build(repo); /* wait */ AM1.save(repo).
//
// Controller block = (0, 0, 0) on the floor under the reactor axis (x = z = 8). Front north.
// Connections: coolant inlet east face of cell (3, 7, 0), outlet west face of cell (-3, 7, 0).
(function () {
  const C = [8, 8], PITCH = 1.92, TOP = 104;

  // channel lattice: 151 nodes of a square grid inside the core circle; control channels on a coarser grid
  function channels() {
    const pts = [];
    for (let j = -7; j <= 7; j++) for (let i = -7; i <= 7; i++) pts.push([i, j, i * i + j * j]);
    pts.sort((a, b) => a[2] - b[2] || a[1] - b[1] || a[0] - b[0]);
    // 23 control and protection channels: a 3-pitch grid (21) and the two on the north-south axis at 5 pitches
    return pts.slice(0, 151).map(([i, j]) => ({ i, j, ctrl: (i % 3 === 0 && j % 3 === 0) || (i === 0 && Math.abs(j) === 5) }));
  }

  function model() {
    const m = PCVV.mk('vver/concrete');
    m.cylY('shaft', C, 55, 0, 96, 'conc', { bottom: true, cap: 'vver/concrete' });
    for (const y of [30, 70]) m.ringY(`band_${y}`, C, 55.6, 1.2, y, y + 2, 'pnt');
    m.cylY('plate', C, 48, 96, TOP, 'forged', { cap: 'vver/flange' });
    m.nutsY('plate_bolt', C, 46, 24, TOP, 1, 1.4, 'bright');
    m.railY('rail', C, 54.4, 96, 'yel');

    // channel heads: standpipes with caps; control channels carry servo drives with red caps
    let nc = 0;
    channels().forEach(({ i, j, ctrl }, k) => {
      const x = C[0] + i * PITCH, z = C[1] + j * PITCH;
      if (ctrl) {
        nc++;
        m.box(`drv_${k}`, [x - 0.8, TOP, z - 0.8], [x + 0.8, 146, z + 0.8], 'drv', { split: 'y' });
        m.box(`drv_cap_${k}`, [x - 0.9, 146, z - 0.9], [x + 0.9, 149, z + 0.9], 'red', { split: '' });
      } else {
        m.box(`head_${k}`, [x - 0.6, TOP, z - 0.6], [x + 0.6, 112, z + 0.6], 'forged', { split: '' });
        m.box(`head_cap_${k}`, [x - 0.75, 112, z - 0.75], [x + 0.75, 113, z + 0.75], 'bright', { split: '' });
      }
    });

    // ring collectors over the plate on posts, tube bundles from the head field to the rings, inlet and outlet
    m.ringY('col_in', C, 34, 3, 117, 123, 'gw');
    m.ringY('col_out', C, 42, 3, 117, 123, 'gw');
    m.ringY('col_out_band', C, 42.3, 3.6, 119.2, 120.8, 'gs');
    for (let a = 0; a < 360; a += 45) {
      m.postY(`col_post_${a}`, C, 38, a + 22.5, 1.6, TOP, 117, 'pnt', { split: '' });
      m.radial(`bundle_in_${a}`, C, 16, 33, a, 3, 113, 115, 'st');
      m.radial(`bundle_out_${a}`, C, 16, 41, a + 22.5, 3, 114.5, 116.5, 'st');
    }
    m.nozzle('in', 'x', [120, 8], 36, 64, 4.2, 3, 'gw', { nuts: 8 });
    m.nozzle('out', 'x', [120, 8], -26, -48, 4.2, 3, 'gs', { nuts: 8 });

    // ladder up the front of the shaft
    m.ladderZ('ladder', 4, 12, -46, -1, 0, 96, 'yel', 0);
    // plates on a stand at the north-west of the walkway
    m.box('stand', [-40, 96, -30], [-38, 112, -28], 'pnt', { split: '' });
    m.decal('sign', [-44, 112, -30.7], [-36, 120, -30], 'pnt', 'north', 'pl.sign');
    m.decal('name', [-44, 121, -30.7], [-30, 124.6, -30], 'pnt', 'north', 'pl.am_name');
    m.__nc = nc;
    return m;
  }

  window.AM1 = {
    model, channels,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'am1_reactor'); return { elements: m.els.length, control: m.__nc, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'am1_reactor', 'core/am1'),
  };
})();
