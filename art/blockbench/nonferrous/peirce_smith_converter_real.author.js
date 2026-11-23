// Peirce-Smith copper converter at real scale (1 block = 1 m, 16 px = 1 m): a 4 m by 8.5 m horizontal barrel (the
// classic 13 ft x 28 ft size) lined with magnesite brick, on two riding rings carried by trunnion roller pairs on
// concrete piers, the mouth on top, the tuyere line of 30 tuyeres along the north side fed from the air header with
// its inlet at the west end, the girth gear at the east end driven by the pinion, reducer and motor for tilting.
// peirce_smith_converter_real_running is the converter blowing (RUNNING): the bath glowing in the mouth and the
// flame and fume standing over it.
// The barrel cannot be a partial (4 m across is wider than a partial model's 3 m range) and the block has no tilt
// state, so the barrel is drawn upright in the whole model.
//
// Load after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for v in idle, running:
//   PSR.build(repo, v); /* wait */ PSR.save(repo, v)
//
// Controller block = (0, 0, 0). Footprint x -4..4, y 0..4, z -2..2; barrel axis along x through (y 42, z 8).
// Connections (px): blast air in on the west face of (-4, 2, -2) at (-64, 40, -24), the header flange; matte and
// flux in and converter copper and slag out through the mouth on the top face of (0, 4, 0) (the ladles are poured
// by tilting, which the model does not show).
(function () {
  const CX = 8, A = [42, 8], R = 32, XS = [-60, 76];      // barrel centre x, axis [y, z], radius, shell x span
  const RINGS = [CX - 36, CX + 36];
  const MAT = Object.assign({}, PCVV.MAT, {
    shell: { tex: 'material/steel', P: 16 }, frame: { tex: 'plant/structure', P: 16 }, soot: { tex: 'material/soot', P: 16 },
    fire: { tex: 'plant/yellow', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/steel' });

  function barrel(m, running) {
    m.prism('shell', 'x', A, R, XS[0] + 2, XS[1] - 2, 'shell', { split: 'x' });
    for (const [n, x0, x1, d] of [['w', XS[0], XS[0] + 2, 'west'], ['e', XS[1] - 2, XS[1], 'east']]) m.disc(`end_${n}`, 'x', A, R - 1, x0, x1, 'shell', 'vver/cap', d);
    for (const x of RINGS) {
      m.ring(`ring_${x}`, 'x', A, R + 2, 2.4, x - 3, x + 3, 'ci');
      m.ring(`ring_shoe_${x}`, 'x', A, R + 0.4, 1, x - 4, x + 4, 'frame');
    }
    for (let x = XS[0] + 8; x < XS[1] - 4; x += 12) if (!RINGS.some((r) => Math.abs(r - x) < 6)) m.ring(`seam_${x}`, 'x', A, R + 0.2, 0.3, x, x + 0.4, 'frame');
    // mouth on top: a throat box through the crown with its steel lip and the hot or dark opening
    m.box('mouth_throat', [CX - 14, A[0] + R - 6, A[1] - 12], [CX + 14, A[0] + R + 2, A[1] + 12], 'shell', { split: '' });
    m.box('mouth_lip', [CX - 15, A[0] + R + 2, A[1] - 13], [CX + 15, A[0] + R + 3, A[1] + 13], 'ci', { split: '', hide: ['up'] });
    for (const [n, f, t] of [['n', [CX - 15, A[0] + R + 3, A[1] - 13], [CX + 15, A[0] + R + 3.01, A[1] - 11]], ['s', [CX - 15, A[0] + R + 3, A[1] + 11], [CX + 15, A[0] + R + 3.01, A[1] + 13]], ['w', [CX - 15, A[0] + R + 3, A[1] - 11], [CX - 13, A[0] + R + 3.01, A[1] + 11]], ['e', [CX + 13, A[0] + R + 3, A[1] - 11], [CX + 15, A[0] + R + 3.01, A[1] + 11]]]) m.box(`lip_top_${n}`, f, t, 'ci', { split: '' });
    m.box(running ? 'glow_bath' : 'mouth_dark', [CX - 13, A[0] + R + 1.5, A[1] - 11], [CX + 13, A[0] + R + 2.6, A[1] + 11], running ? 'fire' : 'soot', { split: '' });
    if (running) {
      for (const [dx, dz, h] of [[-6, -4, 5], [4, 2, 5], [-1, 6, 4], [8, -6, 3]]) m.box(`glow_flame_${dx}_${dz}`, [CX + dx - 3, A[0] + R + 2.6, A[1] + dz - 3], [CX + dx + 3, Math.min(80, A[0] + R + 2.6 + h), A[1] + dz + 3], 'fire', { split: '', hide: ['down'] });
    }
  }

  // Tuyere line on the north side and the air header with its inlet flange at the west end.
  function tuyeres(m) {
    const yz = [40, -26];
    m.prism('header', 'x', yz, 2.6, -64, XS[1] - 10, 'frame', { split: 'x' });
    m.disc('header_flange', 'x', yz, 3.8, -63.9, -63.2, 'ci', 'vver/flange', 'west');
    for (let k = 0; k < 30; k++) {
      const x = XS[0] + 12 + k * 3.8;
      if (RINGS.some((r) => Math.abs(r - x) < 5)) continue;
      m.box(`tuyere_${k}`, [x - 0.5, yz[0] - 0.5, yz[1] + 2.4], [x + 0.5, yz[0] + 0.5, A[1] - R + 1], 'ci', { split: '' });
      m.box(`tuyere_cap_${k}`, [x - 0.8, yz[0] - 2.6, yz[1] - 0.6], [x + 0.8, yz[0] - 1.6, yz[1] + 0.6], 'bright', { split: '' });
    }
    for (const x of [-40, 0, 40]) m.box(`header_hanger_${x}`, [x - 0.6, yz[0] + 2.6, yz[1] - 0.6], [x + 0.6, yz[0] + 8, A[1] - R + 4], 'frame', { split: '' });
  }

  // Roller stands under both riding rings, the girth gear and the tilting drive at the east end.
  function supports(m) {
    m.box('floor', [-64, 0, -32], [80, 1, 48], 'conc', { split: 'xz' });
    for (const x of RINGS) {
      m.box(`pier_${x}`, [x - 8, 1, -18], [x + 8, 6, 34], 'conc', { split: 'z' });
      for (const s of [-1, 1]) {
        const zc = A[1] + s * 20;
        m.box(`roller_frame_${x}_${s}`, [x - 6, 6, zc - 5], [x + 6, 8, zc + 5], 'frame', { split: '' });
        m.prism(`roller_${x}_${s}`, 'x', [11.5, zc], 4, x - 4, x + 4, 'ci', { split: '', ends: ['west', 'east'] });
        for (const xx of [x - 5, x + 4]) m.box(`roller_brg_${x}_${s}_${xx}`, [xx, 8, zc - 2], [xx + 1, 14, zc + 2], 'frame', { split: '' });
      }
    }
    // girth gear at the east end, the pinion under it, reducer and motor
    m.ring('girth_gear', 'x', A, R + 4, 3.4, XS[1] - 12, XS[1] - 8, 'ci');
    m.prism('pinion', 'x', [8, A[1] + 22], 3.2, XS[1] - 12, XS[1] - 7, 'bright', { split: '', ends: ['west', 'east'] });
    m.box('reducer', [XS[1] - 7, 1, A[1] + 16], [XS[1] + 4, 16, A[1] + 30], 'pnt', { split: '' });
    m.prism('motor', 'z', [XS[1] - 1, 9], 4.4, A[1] + 30, 48, 'pnt', { split: '', ends: ['south'] });
    m.box('motor_feet', [XS[1] - 6, 1, A[1] + 31], [XS[1] + 4, 5, 47], 'pnt', { split: '' });
    m.decal('plate', [-6, 6, -18.1], [2, 10, -18], 'conc', 'north', 'pl.maker');
  }

  function model(running) { const m = mk(); barrel(m, running); tuyeres(m); supports(m); return m; }
  const ID = { idle: 'peirce_smith_converter_real', running: 'peirce_smith_converter_real_running' };
  window.PSR = {
    ID, model,
    build: (root, v) => { const m = PCVV.cut(model(v === 'running')); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, ID[v], 'nonferrous'),
  };
})();
