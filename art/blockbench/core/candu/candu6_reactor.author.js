// CANDU 6 reactor assembly at real scale (1 block = 1 m): the horizontal calandria 7.6 m across and 6 m long
// between the concrete end walls of its vault, the end shields 6.76 m across with 380 end fittings on each face
// (286 mm lattice, 2.4 m long, closure plugs), feeder banks up to the inlet and outlet headers above each face,
// the reactivity mechanism deck with shutoff rod and adjuster drives on top, cradles under the shell, moderator
// lines, and on the north face the fuelling machine columns (the bridge and the machine head are partials).
// The vault side walls are left out to show the calandria. Dimensions and sources: docs/research/candu-dimensions.md
// (disk only). Load after vverkit.js, then CANDU6.build(repo); /* wait */ CANDU6.save(repo); the partials with
// CANDU6.buildHead / saveHead and CANDU6.buildBeam / saveBeam.
//
// Controller block = (0, 0, 0) on the vault floor under the calandria centre (x = 8, z = 8). Front north.
// Calandria axis along z at x 8, y 80. Connections (ends on block faces, axes on block centres):
//   inlet header (north face):  east face of cell (4, 10, -5)
//   outlet header (south face): east face of cell (4, 10, 5)
(function () {
  const AX = [8, 80], PITCH = 4.576;
  const R_SHELL = 61, R_SHIELD = 54;
  const ZN = -40, ZS = 56;                  // calandria shell between the inner faces of the end walls
  const WALL = 16;                          // end wall (end shield) thickness
  const FIT = 36;                           // end fitting length out of the end shield face

  // the 380 lattice nodes in pitches from the axis: half-integer 22 x 22 grid inside r^2 <= 120.5 less the four
  // nodes (+-9.5, +-5.5)
  function lattice() {
    const out = [];
    for (let j = 10; j >= -11; j--) for (let i = -11; i <= 10; i++) {
      const u = i + 0.5, v = j + 0.5;
      if (u * u + v * v > 120.5) continue;
      if (Math.abs(u) === 9.5 && Math.abs(v) === 5.5) continue;
      out.push([u, v]);
    }
    return out;
  }

  function face(m, tag, z0, dir) {
    // end shield outer tubesheet, end fittings with their closure plugs, feeder banks up to the header
    const zf = z0 + dir * 2, end = dir < 0 ? 'north' : 'south';
    m.prism(`es_${tag}`, 'z', AX, R_SHIELD, Math.min(z0, zf), Math.max(z0, zf), 'forged', { split: '', ends: [end] });
    const cols = new Map();
    lattice().forEach(([u, v], k) => {
      const x = AX[0] + u * PITCH, y = AX[1] + v * PITCH;
      const a = zf, b = zf + dir * FIT, c = b + dir * 2;
      m.box(`ef_${tag}_${k}`, [x - 1.3, y - 1.3, Math.min(a, b)], [x + 1.3, y + 1.3, Math.max(a, b)], 'forged', { split: '' });
      m.box(`plug_${tag}_${k}`, [x - 1.5, y - 1.5, Math.min(b, c)], [x + 1.5, y + 1.5, Math.max(b, c)], 'bright', { split: '' });
      cols.set(u, Math.min(cols.has(u) ? cols.get(u) : 1e9, y));
    });
    // feeders: one bank per lattice column between the fittings, from the lowest fitting up over the face
    const zb = zf + dir * 8;
    for (const [u, y0] of cols) {
      const x = AX[0] + u * PITCH + PITCH / 2;
      if (u === 10.5) continue;
      m.box(`feed_${tag}_${u}`, [x - 0.7, y0, Math.min(zb, zb + dir * 3)], [x + 0.7, 160, Math.max(zb, zb + dir * 3)], 'st', { split: 'y' });
    }
    // header above the face, running east to the connection
    const zh = dir < 0 ? -72 : 88;
    m.prism(`hdr_${tag}`, 'x', [168, zh], 5, -50, 72, 'gw', { split: '' });
    m.nozzle(`hdr_${tag}_out`, 'x', [168, zh], 66, 80, 6.5, 5, 'gw', { nuts: 12 });
    m.box(`hdr_${tag}_feed`, [-50, 160, Math.min(zb, zh)], [66, 163, Math.max(zb, zh)], 'st', { split: '' });
  }

  function model() {
    const m = PCVV.mk('vver/concrete');
    // vault floor, end walls round the end shields, cradles under the shell
    m.box('floor', [-64, 0, ZN], [80, 4, ZS], 'conc');
    for (const [tag, z0, z1] of [['n', ZN - WALL, ZN], ['s', ZS, ZS + WALL]]) {
      m.box(`wall_${tag}`, [-64, 0, z0], [80, 150, z1], 'conc');
      m.box(`wall_${tag}_cap`, [-64, 150, z0 - 1], [80, 153, z1 + 1], 'pnt', { split: 'xz' });
    }
    for (const z of [-22, 38]) m.box(`cradle_${z}`, [-36, 4, z - 5], [52, 26, z + 5], 'conc', { split: 'x' });

    // calandria: stainless shell, stiffening rings, steps down to the end shields
    m.prism('shell', 'z', AX, R_SHELL, ZN, ZS, 'st', { split: '' });
    for (const z of [-24, 8, 40]) m.ring(`rib_${z}`, 'z', AX, R_SHELL + 1, 1.2, z - 1, z + 1, 'st');
    for (const [tag, z0, z1] of [['n', ZN, ZN + 4], ['s', ZS - 4, ZS]]) m.prism(`step_${tag}`, 'z', AX, R_SHELL + 2, z0, z1, 'forged', { split: '' });

    // moderator lines from the bottom of the shell down to the floor and east
    for (const z of [-10, 26]) {
      m.cylY(`mod_${z}`, [8, z], 4, 4, 22, 'gw');
      m.prism(`mod_h_${z}`, 'x', [10, z], 4, 12, 80, 'gw', { split: '' });
      m.box(`mod_band_${z}`, [70, 5.5, z - 4.5], [72, 14.5, z + 4.5], 'gw', { split: '' });
    }

    // reactivity mechanism deck on four columns, shutoff rod and adjuster drives standing on it
    m.box('deck', [-44, 141, ZN - 2], [60, 147, ZS + 2], 'pnt', { split: 'xz' });
    m.box('deck_grate', [-44, 147, ZN - 2], [60, 147.4, ZS + 2], 'grate', { split: 'xz' });
    for (const x of [-42, 56]) for (const z of [ZN, ZS - 4]) m.box(`deck_col_${x}_${z}`, [x, 4, z], [x + 4, 141, z + 4], 'pnt', { split: 'y' });
    let k = 0;
    for (let i = -4; i <= 4; i++) for (let j = -3; j <= 3; j++) {
      if ((i + j) % 2) continue;
      const x = AX[0] + i * 11, z = 8 + j * 12, h = j % 3 === 0 ? 196 : 184;
      m.box(`drv_${k}`, [x - 1.6, 147.4, z - 1.6], [x + 1.6, h, z + 1.6], 'drv', { split: 'y' });
      m.box(`drv_cap_${k++}`, [x - 2.2, h, z - 2.2], [x + 2.2, h + 3, z + 2.2], 'mcap', { split: '' });
    }
    for (const [tag, z] of [['n', ZN - 2], ['s', ZS + 1.6]]) {
      m.box(`rail_${tag}`, [-44, 162, z], [60, 162.6, z + 0.4], 'yel', { split: 'x' });
      for (let x = -44; x <= 60; x += 13) m.box(`rail_${tag}_post_${x}`, [x, 147.4, z], [x + 0.5, 162, z + 0.4], 'yel', { split: '' });
    }

    face(m, 'n', ZN - WALL, -1);
    face(m, 's', ZS + WALL, 1);

    // fuelling machine columns and the top tie on the north face (the bridge and the head are partials)
    for (const x of [-64, 74]) {
      m.box(`fm_col_${x}`, [x, 0, -128], [x + 6, 182, -120], 'pnt', { split: 'y' });
      m.box(`fm_rack_${x}`, [x + (x < 0 ? 6 : -1), 4, -125], [x + (x < 0 ? 7 : 0), 176, -123], 'bright', { split: 'y' });
    }
    m.box('fm_tie', [-64, 182, -128], [80, 188, -120], 'pnt', { split: 'x' });
    m.ladderZ('fm_ladder', -63, -59, -120, 1, 0, 182, 'yel', 0);

    // plates on the north end wall
    m.decal('sign', [-58, 120, ZN - WALL - 0.7], [-48, 130, ZN - WALL], 'conc', 'north', 'pl.sign');
    m.decal('name', [-58, 132, ZN - WALL - 0.7], [-48, 134.6, ZN - WALL], 'conc', 'north', 'pl.candu_name');
    return m;
  }

  // Partial: the fuelling machine head, local axis along z at x 8, y 8; the snout end (local z 32) meets the
  // closure plugs. Drawn at the controller + (px / 16, (72 + py) / 16, -129 / 16) blocks.
  function head() {
    const m = PCVV.mk('vver/paint');
    m.prism('body', 'z', [8, 8], 9.6, 4, 24, 'pnt', { split: '', ends: ['north', 'south'] });
    m.prism('collar', 'z', [8, 8], 6, 24, 27, 'forged', { split: '', ends: ['south'] });
    m.prism('snout', 'z', [8, 8], 3, 27, 32, 'bright', { split: '', ends: ['south'] });
    m.prism('ram', 'z', [8, 8], 4, -4, 4, 'mcap', { split: '', ends: ['north'] });
    m.box('carriage', [0, -2, -8], [16, 2, 4], 'yel', { split: '' });
    m.box('motor', [12, 12, -2], [17, 17, 8], 'motor', { split: '' });
    return m;
  }

  // Partial: one 3-block section of the bridge beam (local x -16..32), drawn three times along x at the
  // controller + (k * 3 - 3, (72 + py) / 16, -8) blocks; the beam rides the column racks.
  function beam() {
    const m = PCVV.mk('vver/paint');
    m.box('beam', [-16, 3, 0], [32, 13, 6], 'yel', { split: '' });
    m.box('rail', [-16, 1.5, 4], [32, 3, 6], 'bright', { split: '' });
    return m;
  }

  window.CANDU6 = {
    model, head, beam, lattice,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'candu6_reactor'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'candu6_reactor', 'core/candu'),
    buildHead: (root) => { const m = head(); PCART.realize(root, m, 'head'); return m.els.length; },
    saveHead: (root) => PCVV.savePartial(root, 'candu6_reactor', 'head', 'core/candu'),
    buildBeam: (root) => { const m = beam(); PCART.realize(root, m, 'beam'); return m.els.length; },
    saveBeam: (root) => PCVV.savePartial(root, 'candu6_reactor', 'beam', 'core/candu'),
  };
})();
