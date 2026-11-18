// CANDU-6 type pressurised heavy-water reactor (T3), hand-authored for Blockbench (replaces the borrowed
// reactor_vessel_wall / refueling_machine models): the calandria vault with its end-shield face and the
// fuelling machine, and above it the feeder cabinet, the headers and the reactivity mechanism deck.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/candu_reactor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CANDU.paint('<repo>')
//   build + save each model in turn (CANDU.build / CANDU.save, ids in CANDU.ids)
//
// 3x6x3: two 3x3x3 segments, each model x -16..32, y -16..32, z -16..32 about its middle block, front to the
// north. The calandria axis runs north-south (horizontal, as in every CANDU); the reactor face is the north
// end shield at z -6, a lattice of 97 end fittings at a 3-unit pitch round x 8, y 9 (i*i + j*j <= 30, the same
// rule as CanduRenderer.CHANNELS; channel order: rows from the top, west to east). The feeders run up between
// the fitting columns into the feeder cabinet above and over to the inlet and outlet headers.
// In front of the face, z -16..-9, stands the fuelling machine bridge on two columns. Partials, moved by
// CanduRenderer: bridge (the cross girder, moves up and down) and head (the fuelling machine on its carriage,
// moves with the bridge and across it; snout on the axis x 8, y 9 at rest, face of the snout at z -9.5).
// Above: the reactivity mechanism deck over the calandria with the shut-off rod drives (red), the adjuster
// and control absorber drives (grey) and the liquid zone control columns.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};
  const PITCH = 3, CX = 8, CY = 9, R2 = 30;
  const LATTICE = [];
  for (let j = 6; j >= -6; j--) for (let i = -6; i <= 6; i++) if (i * i + j * j <= R2) LATTICE.push([i, j]);

  // Cast-in-place concrete of the vault: formwork board marks every 4 px, tie holes, damp patches.
  T['candu_reactor/concrete'] = () => {
    const im = img(64, 64), r = grain(im, [150, 148, 140], 3101, 4, 7, 16);
    for (let y = 0; y < 64; y += 8) for (let x = 0; x < 64; x++) put(im, x, y, add([130, 128, 122], (r() - 0.5) * 8));
    for (let y = 4; y < 64; y += 16) for (let x = 6; x < 64; x += 20) { put(im, x, y, [70, 70, 68]); put(im, x + 1, y, [96, 96, 92]); }
    const n = PCART.vnoise(r, 64, 64, 16);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) if (n(x, y) > 0.72) {
      const i = (y * 64 + x) * 4; put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [104, 104, 100], 0.4));
    }
    return im;
  };
  // Epoxy-painted steel of the end shield: pale green, the decontaminable coat of reactor building surfaces.
  T['candu_reactor/epoxy'] = () => {
    const im = img(32, 32), r = grain(im, [160, 184, 164], 3111, 3, 4, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [120, 136, 124]);
    return im;
  };
  // Crane yellow of the fuelling machine bridge and carriage.
  T['candu_reactor/yellow'] = () => {
    const im = img(32, 32), r = grain(im, [214, 172, 52], 3121, 3, 5, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [120, 104, 70]);
    return im;
  };
  // Hazard stripes at the ends of the girder.
  T['candu_reactor/hazard'] = () => {
    const im = img(16, 16), r = rng(3131);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(((x + y) >> 2) % 2 ? [30, 30, 30] : [220, 176, 40], (r() - 0.5) * 8));
    return im;
  };
  // Feeder pipes, carbon steel, uninsulated, packed side by side: vertical runs 2 px wide with a shadow.
  T['candu_reactor/feeders'] = () => {
    const im = img(32, 32), r = rng(3141);
    for (let x = 0; x < 32; x++) {
      const k = x % 3, base = k === 0 ? [60, 58, 56] : k === 1 ? [118, 112, 104] : [92, 88, 82];
      for (let y = 0; y < 32; y++) put(im, x, y, add(base, (r() - 0.5) * 8 + (y % 16 === 0 && k ? -16 : 0)));
    }
    return im;
  };
  // Closure plug of an end fitting, seen end on: stainless ring, the plug with its hex and the channel number tab.
  T['candu_reactor/closure'] = () => {
    const im = img(16, 16), r = rng(3151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      let c = d > 7 ? [110, 112, 116] : d > 5.4 ? [176, 180, 184] : d > 4.6 ? [70, 72, 76] : [150, 154, 158];
      if (d < 2.2) c = [104, 108, 112];
      put(im, x, y, add(c, (r() - 0.5) * 8));
    }
    rect(im, 6, 0, 4, 2, [226, 222, 200]);
    return im;
  };
  // Stainless of the fittings and the fuelling machine snout.
  T['candu_reactor/stainless'] = () => {
    const im = img(32, 32), r = rng(3161), n = PCART.vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([168, 172, 176], (n(x, y) - 0.5) * 20 + (r() - 0.5) * 6 + (x % 5 === 0 ? -6 : 0)));
    return im;
  };
  // Shut-off rod drive housing: red, as the safety system is coded.
  T['candu_reactor/sor'] = () => { const im = img(16, 16), r = grain(im, [168, 44, 36], 3171, 3, 4, 4); for (let y = 0; y < 16; y += 5) rect(im, 0, y, 16, 1, [96, 30, 26]); return im; };
  // Adjuster and absorber drive housing: machine grey with a band.
  T['candu_reactor/drive'] = () => { const im = img(16, 16), r = grain(im, [124, 130, 134], 3181, 3, 4, 4); rect(im, 0, 3, 16, 2, [70, 76, 80]); return im; };
  // Drive cap seen from above: radial.
  T['candu_reactor/cap'] = () => {
    const im = img(16, 16), r = rng(3191);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, add(d > 6.5 ? [90, 94, 98] : d > 3 ? [140, 146, 150] : [60, 62, 66], (r() - 0.5) * 8));
    }
    return im;
  };
  // Atlas of the end shield face (2 px per unit): the face plate x -12..28, y -14..32 (80 x 92 px), with the
  // calandria outline (the tube sheet edge, r 18.5) and the lattice grid lines lightly scribed; the lattice
  // position plate (row letters, column numbers) at the top; the face number "1" (north end).
  T['candu_reactor/face'] = () => {
    const im = img(128, 128), r = grain(im, [160, 184, 164], 3201, 3, 4, 8);
    const px = (x) => (x + 12) * 2, py = (y) => (32 - y) * 2;
    for (let a = 0; a < 360; a += 0.3) {
      const t = a * Math.PI / 180;
      for (const [rr, c] of [[18.8, [96, 112, 100]], [19.3, [190, 210, 194]]]) put(im, px(CX + rr * Math.cos(t)), py(CY + rr * Math.sin(t)), c);
    }
    // shield plug ring round every fitting
    for (const [i, j] of LATTICE) {
      const x = px(CX + i * PITCH), y = py(CY + j * PITCH);
      for (let a = 0; a < 360; a += 12) { const t = a * Math.PI / 180; put(im, x + 2.6 * Math.cos(t), y + 2.6 * Math.sin(t), [120, 140, 126]); }
    }
    // row letters / column numbers strip
    rect(im, px(-4), py(31), 48, 5, [226, 222, 200]);
    for (let k = 0; k < 13; k++) rect(im, px(-3) + k * 3.6, py(30.4), 2, 2, [40, 40, 40]);
    // face number plate
    rect(im, px(22), py(30), 10, 10, [226, 222, 200]); rect(im, px(22) + 4, py(30) + 2, 2, 6, [30, 30, 30]); put(im, px(22) + 3, py(30) + 3, [30, 30, 30]);
    // radiation trefoil plate low on the west side
    rect(im, px(-10), py(-6), 10, 10, [222, 196, 50]);
    for (let a = 0; a < 360; a += 4) {
      const t = a * Math.PI / 180, sector = Math.floor(((a + 30) % 360) / 60) % 2 === 0;
      for (let rr = 1.4; rr < 4.2; rr += 0.5) if (sector) put(im, px(-10) + 5 + rr * Math.cos(t), py(-6) + 5 + rr * Math.sin(t), [140, 40, 120]);
    }
    rect(im, px(-10) + 4, py(-6) + 4, 2, 2, [140, 40, 120]);
    return im;
  };
  // Fuelling machine head: the magazine housing in crane yellow with its hatch and the hose reels.
  T['candu_reactor/head'] = () => {
    const im = img(32, 32), r = grain(im, [214, 172, 52], 3211, 3, 5, 8);
    for (let x = 0; x < 32; x += 8) rect(im, x, 0, 1, 32, [150, 120, 40]);
    rect(im, 10, 8, 12, 10, [180, 146, 44]); rect(im, 11, 9, 10, 1, [236, 200, 90]);
    return im;
  };

  const MAT = {
    con: { tex: 'candu_reactor/concrete', P: 32 }, epx: { tex: 'candu_reactor/epoxy', P: 16 }, yel: { tex: 'candu_reactor/yellow', P: 16 },
    haz: { tex: 'candu_reactor/hazard', P: 8 }, fdr: { tex: 'candu_reactor/feeders', P: 16 }, ss: { tex: 'candu_reactor/stainless', P: 16 },
    sor: { tex: 'candu_reactor/sor', P: 16 }, drv: { tex: 'candu_reactor/drive', P: 16 }, head: { tex: 'candu_reactor/head', P: 16 },
  };
  const ATL = { face: { tex: 'candu_reactor/face', size: 128, rects: { shield: [0, 0, 80, 92] } } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATL), { particle: particle || 'candu_reactor/concrete' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Vault walls on the west, east and south and the floor; y0..y1 of the walls.
  function vault(m, y0, y1, floor) {
    if (floor) {
      m.box('plinth', [-16, -16, -6], [32, -14, 32], 'con');
      m.box('vault_floor', [-16, -16, -16], [32, -15, -6], 'chequer');
    }
    m.box('wall_w', [-16, y0, -6], [-12, y1, 32], 'con');
    m.box('wall_e', [28, y0, -6], [32, y1, 32], 'con');
    m.box('wall_s', [-12, y0, 28], [28, y1, 32], 'con');
  }

  // Bridge columns with their rails, z -15..-11 at the west and east edges.
  function columns(m, y0, y1) {
    for (const [n, x] of [['w', -15], ['e', 28]]) {
      m.box(`column_${n}`, [x, y0, -15], [x + 3, y1, -11], 'yel');
      m.box(`rail_${n}`, [n === 'w' ? x + 3 : x - 0.6, y0, -13.8], [n === 'w' ? x + 3.6 : x, y1, -12.2], 'bright', { split: 'y' });
    }
  }

  function calandria() {
    const m = mk();
    vault(m, -14, 32, true);
    // end shield face plate, the atlas decal on its north side
    m.decal('end_shield', [-12, -14, -6], [28, 32, -4], 'epx', 'north', 'face.shield', { hide: ['south', 'up', 'down'] });
    // feeder banks between the fitting columns, from below the lattice up into the feeder cabinet
    for (let i = -6; i <= 5; i++) {
      const x = CX + (i + 0.5) * PITCH;
      const half = Math.sqrt(Math.max(0, R2 - (i + 0.5) * (i + 0.5))) * PITCH;
      m.box(`feeders_${i + 6}`, [x - 0.6, CY - half, -6.9], [x + 0.6, 32, -6], 'fdr', { hide: ['south', 'down'] });
    }
    // end fittings with their closure plugs
    LATTICE.forEach(([i, j], k) => {
      const x = CX + i * PITCH, y = CY + j * PITCH;
      m.box(`fitting_${k}`, [x - 1, y - 1, -9], [x + 1, y + 1, -6], 'ss',
        { split: '', hide: ['south'], face: { north: { tex: 'candu_reactor/closure', uv: [0, 0, 16, 16] } } });
    });
    columns(m, -15, 32);
    // the bridge at its lowest parking position rests on these buffers
    for (const x of [-12, 26]) m.box(`buffer_${x}`, [x, -15, -15], [x + 2, -12, -11], 'haz', { split: '' });
    // walkway grating along the front of the face and its ladder on the east column
    m.box('walkway', [-12, -15, -9], [28, -14.4, -6], 'chequer', { split: '' });
    for (let y = -12; y < 32; y += 3) m.box(`rung_${y}`, [31, y, -15], [31.6, y + 0.5, -11], 'st', { split: '' });
    return m;
  }

  function cabinet() {
    const m = mk();
    vault(m, -16, 16, false);
    // feeder cabinet: the feeder banks rise, then turn back over the calandria into the headers
    for (let i = -6; i <= 5; i++) {
      const x = CX + (i + 0.5) * PITCH;
      m.box(`feeders_${i + 6}`, [x - 0.6, -16, -6.9], [x + 0.6, 2, -6], 'fdr', { hide: ['south'] });
      m.box(`feeders_top_${i + 6}`, [x - 0.6, 2, -6.9], [x + 0.6, 3.2, 6], 'fdr', { hide: ['down'] });
    }
    m.box('cabinet_wall', [-12, -16, -6], [28, 2, -4], 'epx', { hide: ['north'] });
    // inlet and outlet headers of the two heat transport loops, along x over the face
    for (const [k, z, y] of [[0, 0, 5.4], [1, 5, 5.4], [2, 10, 5.4], [3, 15, 5.4]]) {
      m.prism(`header_${k}`, 'x', [y, z], 2.2, -12, 28, 'ci');
    }
    // deck slab over the calandria, the reactivity mechanism deck on it
    m.box('deck_slab', [-12, 8, -4], [28, 16, 28], 'con');
    m.box('deck_plate', [-12, 16, -4], [28, 16.6, 28], 'chequer', { split: 'xz' });
    m.box('deck_edge', [-12, 8, -6], [28, 16.6, -4], 'epx');
    // vertical reactivity mechanisms: rows of drive housings
    const drives = [];
    for (let row = 0; row < 3; row++) for (let col = 0; col < 5; col++) drives.push([-4 + col * 6 + (row % 2) * 3, 4 + row * 8, row === 1 ? 'sor' : 'drv']);
    drives.forEach(([x, z, mat], k) => {
      const h = mat === 'sor' ? 11 : 8;
      m.prismY(`drive_${k}`, [x, z], 1.4, 16.6, 16.6 + h, mat, { cap: 'candu_reactor/cap' });
      m.box(`drive_flange_${k}`, [x - 1.8, 16.6, z - 1.8], [x + 1.8, 17.4, z + 1.8], 'st', { split: '' });
    });
    // liquid zone control: two tall columns of the zone compartments at the back corners
    for (const [n, x] of [['w', -9], ['e', 25]]) {
      m.prismY(`lzc_${n}`, [x, 24], 1.8, 16.6, 30, 'ss', { cap: 'candu_reactor/cap' });
    }
    // handrail along the front edge of the deck
    for (let x = -12; x <= 28; x += 8) m.box(`post_${x}`, [Math.min(x, 27.4), 16.6, -5], [Math.min(x, 27.4) + 0.6, 23, -4.4], 'yel', { split: '' });
    m.box('rail_top', [-12, 22.4, -5], [28, 23, -4.4], 'yel', { split: '' });
    m.box('rail_mid', [-12, 19.6, -5], [28, 20.2, -4.4], 'yel', { split: '' });
    // bridge columns, the top girder with the hoist drive and the cable trolley
    columns(m, -16, 14);
    m.box('top_girder', [-15, 14, -15], [31, 18, -11], 'yel');
    m.box('hoist_drive', [-14, 18, -15], [-6, 23, -11], 'drv');
    m.box('hoist_motor', [-6, 18.6, -14.4], [-1, 22.4, -11.6], 'green');
    m.box('cable_tray', [-12, 18, -11], [28, 18.8, -9], 'st', { split: '' });
    return m;
  }

  // Bridge partial: the cross girder between the columns at the height of the lattice centre.
  function bridge() {
    const m = mk('candu_reactor/yellow');
    m.box('girder', [-12, CY - 2, -15], [28, CY + 2, -11], 'yel');
    for (const [n, x] of [['w', -12], ['e', 26]]) m.box(`girder_end_${n}`, [x, CY - 2.2, -15.2], [x + 2, CY + 2.2, -10.8], 'haz', { split: '' });
    m.box('rack', [-12, CY + 2, -13.4], [28, CY + 2.6, -12.6], 'bright', { split: '' });
    return m;
  }

  // Head partial: carriage on the girder, the fuelling machine head (magazine drum along z) and its snout.
  function head() {
    const m = mk('candu_reactor/yellow');
    m.box('carriage', [CX - 4, CY + 2, -15.4], [CX + 4, CY + 5, -10.6], 'yel');
    m.box('carriage_low', [CX - 4, CY - 5, -15.4], [CX + 4, CY - 2, -10.6], 'yel');
    m.prism('magazine', 'z', [CX, CY], 3.6, -15.8, -11.8, 'head');
    m.disc('magazine_end', 'z', [CX, CY], 3.6, -15.9, -15.8, 'head', 'candu_reactor/cap', 'north');
    m.prism('ram_housing', 'z', [CX, CY], 2.4, -11.8, -10.4, 'drv');
    m.prism('snout', 'z', [CX, CY], 1.3, -10.4, -9.5, 'ss', { ends: ['south'] });
    m.box('hose_reel', [CX + 3.6, CY - 1.5, -15], [CX + 5, CY + 1.5, -12], 'soot', { split: '' });
    m.box('glow_status_lamp', [CX - 0.5, CY + 5, -13.5], [CX + 0.5, CY + 5.8, -12.5], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    candu_reactor: { model: calandria },
    candu_reactor_fuelling: { model: cabinet },
    'candu_reactor/bridge': { model: bridge, java: 'candu_reactor/partial/bridge' },
    'candu_reactor/head': { model: head, java: 'candu_reactor/partial/head' },
  };

  window.CANDU = {
    paint, ids: Object.keys(BUILDS), LATTICE,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'core', id, BUILDS[id].java),
    preview(root) {
      const m = calandria().merge(bridge(), 0, 0, 0, 'b_').merge(head(), 0, 0, 0, 'h_').merge(cabinet(), 0, 48, 0, 'u_');
      return PCART.realize(root, m, 'candu_preview');
    },
  };
})();
