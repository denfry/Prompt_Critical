// PGV-1000M horizontal steam generator at real scale (1 block = 1 m): insulated shell 13.8 m long and about
// 4.25 m across over the cladding, elliptical ends with the secondary-side manholes, the hot and cold primary
// collectors rising through the shell bottom with their covers on top, ten steam pipes into the steam header,
// the feedwater nozzle, two saddle supports with clamp bands and hydraulic snubbers, a service deck and a caged
// ladder. Dimensions and sources: docs/research/vver-dimensions.md (disk only).
// Load after vverkit.js, then VVER_SG.build(repo); /* wait */ VVER_SG.save(repo).
//
// Controller block = (0, 0, 0) under the middle of the shell (between the collectors); shell axis along x at
// y = 3.5 m, z = 0.5 m. Front north. Connections (nozzle ends on block faces, axis on block centres):
//   primary in  (hot collector):  down face of cell (-1, 0, 0)
//   primary out (cold collector): down face of cell (1, 0, 0)
//   steam out (header end):       east face of cell (7, 7, 0)
//   feedwater in:                 south face of cell (2, 4, 2)
(function () {
  const Y = 56, Z = 8, R = 34;          // shell axis and radius over the cladding
  const X0 = -86, X1 = 102;             // cylindrical part; elliptical ends add 16 units each side
  const HOT = -8, COLD = 24;            // collector axes (x)
  const STEAM = [-80, -66, -52, -38, -26, 8, 40, 54, 68, 82];

  function saddle(m, n, xc) {
    m.box(`${n}_base`, [xc - 6, 0, -22], [xc + 6, 2, 38], 'pnt');
    for (const [z0, z1] of [[-19, -12], [28, 35]]) m.box(`${n}_leg${z0}`, [xc - 3, 2, z0], [xc + 3, 22, z1], 'pnt');
    for (let dz = -26; dz < 26; dz += 4) {
      const zm = Math.max(Math.abs(dz), Math.abs(dz + 4)), top = Y - Math.sqrt(R * R - zm * zm) + 0.5;
      m.box(`${n}_web${dz}`, [xc - 4, 18, Z + dz], [xc + 4, top, Z + dz + 4], 'pnt');
    }
    m.ring(`${n}_band`, 'x', [Y, Z], R + 1, 1.2, xc - 3, xc + 3, 'pnt');
    // hydraulic snubbers to brackets either side
    for (const s of [-1, 1]) {
      const z0 = Z + s * 30, span = (d0, d1) => [Math.min(z0 + s * d0, z0 + s * d1), Math.max(z0 + s * d0, z0 + s * d1)];
      m.prism(`${n}_snub${s}`, 'z', [xc, 40], 2, ...span(0, 6), 'gw', { split: '' });
      m.prism(`${n}_rod${s}`, 'z', [xc, 40], 0.9, ...span(6, 9), 'bright', { split: '' });
      const [b0, b1] = span(8, 10);
      m.box(`${n}_brk${s}`, [xc - 3, 34, b0], [xc + 3, 46, b1], 'pnt', { split: '' });
      m.box(`${n}_brkleg${s}`, [xc - 2, 0, b0], [xc + 2, 34, b1], 'pnt');
    }
  }

  function model() {
    const m = PCVV.mk();
    // shell and the stepped elliptical ends
    m.prism('shell', 'x', [Y, Z], R, X0, X1, 'clad');
    for (let i = 0; i < 4; i++) {
      const r = R * Math.sqrt(1 - Math.pow(i / 4, 2));
      m.prism(`end_w${i}`, 'x', [Y, Z], r, X0 - 4 * (i + 1), X0 - 4 * i, 'clad', { ends: ['west'], split: '' });
      m.prism(`end_e${i}`, 'x', [Y, Z], r, X1 + 4 * i, X1 + 4 * (i + 1), 'clad', { ends: ['east'], split: '' });
    }
    // secondary-side manholes in the ends
    m.prism('mh_w', 'x', [Y, Z], 8, X0 - 19, X0 - 16, 'forged', { ends: ['west'], split: '' });
    m.nutsX('mh_w_nut', [Y, Z], 6.8, 12, X0 - 19.8, X0 - 19, 1.3);
    m.prism('mh_e', 'x', [Y, Z], 8, X1 + 16, X1 + 19, 'forged', { ends: ['east'], split: '' });
    m.nutsX('mh_e_nut', [Y, Z], 6.8, 12, X1 + 19, X1 + 19.8, 1.3);

    // primary collectors: DN850 nozzles down out of the shell bottom, covers on top of the shell
    for (const [n, x, band] of [['hot', HOT, 'gs'], ['cold', COLD, 'gw']]) {
      m.cylY(`${n}_pipe`, [x, Z], 8, 2, 26, 'clad');
      m.cylY(`${n}_band`, [x, Z], 8.3, 12, 14, band);
      m.cylY(`${n}_flange`, [x, Z], 10.5, 0, 2, 'forged', { bottom: true, cap: 'vver/flange' });
      m.nutsY(`${n}_nut`, [x, Z], 9.4, 12, 2, 1.4, 1.4, 'bright');
      m.cylY(`${n}_neck`, [x, Z], 7, 86, 96, 'forged');
      m.cylY(`${n}_cover`, [x, Z], 9, 96, 99, 'forged', { cap: 'vver/flange', bottom: true });
      m.nutsY(`${n}_cnut`, [x, Z], 8, 12, 99, 1.6, 1.3, 'bright');
      m.box(`${n}_vent`, [x - 0.6, 99, Z - 0.6], [x + 0.6, 104, Z + 0.6], 'st', { split: '' });
      m.box(`${n}_ventv`, [x - 1.5, 102, Z - 1.5], [x + 1.5, 103.6, Z + 1.5], 'gs', { split: '' });
    }
    // radiation sign on the hot collector nozzle
    m.decal('sign_hot', [HOT - 1.6, 6, Z - 8.1], [HOT + 1.6, 9.2, Z - 7.7], 'clad', 'north', 'pl.sign_s');

    // steam: ten pipes from the shell top into the header, header out east (red bands, GOST 14202 steam)
    STEAM.forEach((x, i) => {
      m.cylY(`steam_${i}`, [x, Z], 2.6, 86, 116, 'clad');
      m.cylY(`steam_band_${i}`, [x, Z], 2.9, 106, 108, 'gs');
    });
    m.prism('header', 'x', [120, Z], 6, -92, 118, 'clad');
    m.prism('header_cap', 'x', [120, Z], 6.4, -94, -92, 'forged', { ends: ['west'], split: '' });
    for (const x of [-70, -20, 30, 80]) m.prism(`header_band_${x}`, 'x', [120, Z], 6.3, x, x + 2.5, 'gs', { split: '' });
    m.nozzle('steam_out', 'x', [120, Z], 112, 128, 7, 5.5, 'gs', { nuts: 12 });

    // feedwater nozzle on the back, green band (water)
    m.nozzle('feed', 'z', [40, 72], 30, 48, 4.8, 3.4, 'gw', { nuts: 8 });

    // supports
    saddle(m, 'sup_w', -48);
    saddle(m, 'sup_e', 64);

    // blowdown lines from the shell bottom near the ends
    for (const x of [-78, 94]) {
      m.cylY(`blow_${x}`, [x, Z], 1.4, 2, 26, 'gw');
      m.box(`blow_valve_${x}`, [x - 2, 8, Z - 2], [x + 2, 11, Z + 2], 'gw', { split: '' });
      m.wheelY(`blow_wheel_${x}`, x, 12.5, Z, 2.2, 'red');
    }

    // level gauge vessels on the front with their impulse lines
    for (const x of [-72, 84]) {
      m.box(`lvl_${x}`, [x - 1.5, 30, -30], [x + 1.5, 86, -27], 'st', { split: 'y' });
      for (const y of [40, 76]) m.box(`lvl_tap_${x}_${y}`, [x - 0.6, y - 0.6, -27], [x + 0.6, y + 0.6, -19], 'st', { split: '' });
      m.box(`lvl_head_${x}`, [x - 2.5, 86, -31], [x + 2.5, 90, -26], 'pnt', { split: '' });
    }

    // front plates on the shell
    m.decal('sign', [-22, 50, -26.7], [-10, 62, -26], 'clad', 'north', 'pl.sign');
    m.decal('name', [-6, 52, -26.7], [24, 59.8, -26], 'clad', 'north', 'pl.sg_name');
    m.decal('maker', [-36, 52, -26.7], [-28, 56.7, -26], 'clad', 'north', 'pl.maker');

    // service deck on the top round the collector covers, rails and a caged ladder down the front
    m.box('deck', [-32, 92, -6], [48, 93, 22], 'grate', { split: 'xz' });
    m.box('landing', [32, 92, -27], [48, 93, -6], 'grate', { split: 'xz' });
    for (const x of [-30, 0, 46]) for (const z of [-4, 20]) m.box(`deck_leg_${x}_${z}`, [x - 0.8, 86, z - 0.8], [x + 0.8, 92, z + 0.8], 'pnt', { split: '' });
    for (const [n, f, t] of [['n', [-32, 0, -6], [32, 0, -5.5]], ['s', [-32, 0, 21.5], [48, 0, 22]], ['w', [-32, 0, -6], [-31.5, 0, 22]],
      ['e', [47.5, 0, -27], [48, 0, 22]], ['ln', [42, 0, -27], [48, 0, -26.5]], ['lw', [32, 0, -27], [32.5, 0, -6]]]) {
      m.box(`rail_${n}_top`, [f[0], 107, f[2]], [t[0], 107.6, t[2]], 'yel', { split: '' });
      m.box(`rail_${n}_knee`, [f[0], 100, f[2]], [t[0], 100.4, t[2]], 'yel', { split: '' });
    }
    for (const [x, z] of [[-32, -6], [0, -6], [32, -6], [-32, 22], [0, 22], [32, 22], [48, 22], [48, -6], [48, -27], [42, -27]]) {
      m.box(`rail_post_${x}_${z}`, [x - 0.3, 93, z - 0.3], [x + 0.3, 107, z + 0.3], 'yel', { split: '' });
    }
    m.ladderZ('ladder', 34, 42, -27, -1, 0, 92, 'yel', 3);
    return m;
  }

  window.VVER_SG = {
    model,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'vver_steam_generator'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'vver_steam_generator'),
  };
})();
