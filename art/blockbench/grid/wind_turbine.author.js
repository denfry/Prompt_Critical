// TsAGI D-30 wind power plant (Balaklava, 1931), hand-authored for Blockbench (replaces the spec_visual.py
// placeholder): the lattice tower in three segments, the nacelle and one blade.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/grid/wind_turbine.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); WTB.paint('<repo>')
//   build + save each model in turn (WTB.build / WTB.save, ids in WTB.ids)
//
// Tower: 1x9x1 in three segments of 1x3x1 (model y -16..32 each, x and z 0..16): galvanised angle legs at the
// corners, K-bracing on every face, struts each block, a ladder on the south face. The base segment stands on a
// concrete foundation with the control cabinet and the cable entry; the top segment carries the platform with
// rails and the yaw ring.
// Nacelle and blade are partials laid out round the hub at (8, 8, 8) px; WindTurbineRenderer scales the nacelle
// x1.5 and the wheel x3 and turns both to the wind, and spins the blade three times at 120 degrees about z. The
// rotor runs upwind at z 6, the nacelle trails back to z 30. A blade is 24 px from the hub, so the wheel is
// 9 blocks across in the world (the real D-30 was 30 m: the world runs at about 1 : 3).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Galvanised steel: dull zinc grey with spangle and white rust spots.
  T['wind_turbine/galvanised'] = () => {
    const im = img(32, 32), r = grain(im, [150, 154, 156], 2301, 5, 7, 5);
    for (let k = 0; k < 24; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [196, 198, 196]);
    return im;
  };
  T['wind_turbine/concrete'] = () => { const im = img(32, 32), r = grain(im, [146, 144, 138], 2311, 7, 8, 6); for (let k = 0; k < 30; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [104, 102, 98]); return im; };
  // Nacelle sheeting: light grey paint over riveted sheet, streaked by rain.
  T['wind_turbine/sheet'] = () => {
    const im = img(32, 32), r = grain(im, [176, 178, 172], 2321, 3, 5, 8);
    for (let x = 0; x < 32; x += 8) for (let y = 2; y < 32; y += 4) put(im, x, y, [128, 130, 126]);
    for (let x = 0; x < 32; x++) if (r() < 0.2) for (let y = 0; y < 8 + r() * 16; y++) put(im, x, y, add([170, 172, 166], -10));
    return im;
  };
  // Blade skin: roofing steel on wooden ribs, painted silver-grey, the leading edge worn.
  T['wind_turbine/blade'] = () => {
    const im = img(32, 32), r = grain(im, [196, 198, 196], 2331, 3, 4, 8);
    for (let y = 0; y < 32; y += 6) rect(im, 0, y, 32, 1, [160, 162, 160]);
    for (let y = 0; y < 32; y++) { put(im, 0, y, [120, 118, 112]); put(im, 1, y, [150, 148, 142]); }
    return im;
  };
  // Control cabinet door with a small window over the meters.
  T['wind_turbine/cabinet'] = () => {
    const im = img(32, 32), r = grain(im, [96, 112, 104], 2341, 3, 4, 8);
    rect(im, 6, 5, 20, 10, [30, 34, 34]);
    for (const x of [9, 19]) { rect(im, x, 7, 5, 5, [220, 214, 196]); put(im, x + 2, 9, [30, 30, 30]); put(im, x + 3, 8, [30, 30, 30]); }
    rect(im, 24, 18, 2, 5, [180, 180, 176]);
    for (let i = 0; i < 32; i++) { put(im, i, 0, [60, 70, 66]); put(im, 0, i, [60, 70, 66]); put(im, i, 31, [60, 70, 66]); put(im, 31, i, [60, 70, 66]); }
    return im;
  };

  const MAT = {
    gal: { tex: 'wind_turbine/galvanised', P: 16 }, conc: { tex: 'wind_turbine/concrete', P: 16 },
    sh: { tex: 'wind_turbine/sheet', P: 16 }, bl: { tex: 'wind_turbine/blade', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'wind_turbine/galvanised' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // One segment of the lattice tower between y0 and y1: corner angles, K-bracing per block on each face,
  // struts at each block line, a ladder on the south face.
  function lattice(m, y0, y1, topStrut) {
    for (const [x, z] of [[0, 0], [14.4, 0], [0, 14.4], [14.4, 14.4]]) {
      m.box(`leg_${x}_${z}`, [x, y0, z], [x + 1.6, y1, z + 0.5], 'gal', { split: 'y' });
      m.box(`leg_web_${x}_${z}`, [x, y0, z + 0.5], [x + 0.5, y1, z + 1.6], 'gal', { split: 'y' });
    }
    const half = 8 * Math.SQRT2 - 1.2;
    for (let y = y0, k = 0; y < y1; y += 16, k++) {
      const c = y + 8, a = k % 2 ? -45 : 45;
      m.box(`brace_n_${k}`, [8 - half, c - 0.4, 0.3], [8 + half, c + 0.4, 0.9], 'gal', { rot: { axis: 'z', angle: a, origin: [8, c, 0.6] } });
      m.box(`brace_s_${k}`, [8 - half, c - 0.4, 15.1], [8 + half, c + 0.4, 15.7], 'gal', { rot: { axis: 'z', angle: -a, origin: [8, c, 15.4] } });
      m.box(`brace_w_${k}`, [0.3, c - 0.4, 8 - half], [0.9, c + 0.4, 8 + half], 'gal', { rot: { axis: 'x', angle: a, origin: [0.6, c, 8] } });
      m.box(`brace_e_${k}`, [15.1, c - 0.4, 8 - half], [15.7, c + 0.4, 8 + half], 'gal', { rot: { axis: 'x', angle: -a, origin: [15.4, c, 8] } });
      if (topStrut || y + 16 < y1) {
        m.box(`strut_n_${k}`, [1.6, y + 15.2, 0.2], [14.4, y + 16, 1], 'gal', { split: '' });
        m.box(`strut_s_${k}`, [1.6, y + 15.2, 15], [14.4, y + 16, 15.8], 'gal', { split: '' });
        m.box(`strut_w_${k}`, [0.2, y + 15.2, 1.6], [1, y + 16, 14.4], 'gal', { split: '' });
        m.box(`strut_e_${k}`, [15, y + 15.2, 1.6], [15.8, y + 16, 14.4], 'gal', { split: '' });
      }
      // ladder outside the south face: two stiles, rungs every 3 px
      for (const x of [6, 9.6]) m.box(`ladder_stile_${x}_${k}`, [x, y, 16.2], [x + 0.4, y + 16, 16.6], 'st', { split: '' });
      for (let ry = y + 1.5; ry < y + 16; ry += 3) m.box(`rung_${k}_${ry}`, [6.4, ry, 16.3], [9.6, ry + 0.3, 16.5], 'st', { split: '' });
    }
  }

  function towerBase() {
    const m = mk('wind_turbine/concrete');
    m.box('foundation', [-2, -16, -2], [18, -14, 18], 'conc', { split: 'xz' });
    for (const [x, z] of [[0, 0], [14.4, 0], [0, 14.4], [14.4, 14.4]]) m.box(`anchor_${x}_${z}`, [x - 0.4, -14, z - 0.4], [x + 2, -13, z + 2], 'st', { split: '' });
    lattice(m, -14, 32, true);
    m.box('cabinet', [3, -14, 1.4], [13, -3, 5], 'ci', { split: '', face: { north: { tex: 'wind_turbine/cabinet', uv: [0, 0, 16, 16] } } });
    m.box('cabinet_roof', [2.6, -3, 1], [13.4, -2.4, 5.4], 'st', { split: '' });
    m.box('cabinet_lamp', [10.4, -5.4, 1], [11.6, -4.2, 1.4], 'red', { split: '' });
    m.box('cable_entry', [6, -14, 5], [10, -12, 9], 'ci', { split: '' });
    m.box('cable_up', [7.4, -12, 7.4], [8.6, 32, 8.6], 'ci', { split: 'y' });
    return m;
  }
  function towerMiddle() { const m = mk(); lattice(m, -16, 32, true); m.box('cable_up', [7.4, -16, 7.4], [8.6, 32, 8.6], 'ci', { split: 'y' }); return m; }
  function towerTop() {
    const m = mk();
    lattice(m, -16, 28, false);
    m.box('cable_up', [7.4, -16, 7.4], [8.6, 28, 8.6], 'ci', { split: 'y' });
    m.box('platform', [-1, 28, -1], [17, 29, 17], 'chequer', { split: 'xz' });
    for (const [n, f, t] of [['n', [-1, 29, -1], [17, 31, -0.6]], ['s', [-1, 29, 16.6], [17, 31, 17]],
      ['w', [-1, 29, -0.6], [-0.6, 31, 16.6]], ['e', [16.6, 29, -0.6], [17, 31, 16.6]]]) m.box(`rail_${n}`, f, t, 'gal', { split: '' });
    m.prismY('yaw_ring', [8, 8], 6, 29, 31.6, 'ci', { ends: ['up'] });
    m.prismY('yaw_gear', [8, 8], 6.4, 29.6, 30.6, 'st', { ends: ['up', 'down'] });
    return m;
  }

  // Nacelle partial (x1.5 about the hub): bedplate on the yaw bearing, main bearing, shaft and brake, gearbox and
  // the induction generator at the back under sheet housing, the spinner over the hub, anemometer and vane on top.
  function nacelle() {
    const m = mk('wind_turbine/sheet');
    m.prismY('yaw_bearing', [8, 16], 3.2, 2.6, 3.4, 'st', { ends: ['down'] });
    m.box('bedplate', [4.4, 3.4, 6.6], [11.6, 4.8, 28], 'ci', { split: '' });
    m.box('housing', [4.6, 4.8, 9], [11.4, 11.2, 26], 'sh', { split: '' });
    m.box('housing_roof', [5.4, 11.2, 10], [10.6, 12, 25], 'sh', { split: '' });
    for (let z = 12; z < 24; z += 3) m.box(`louvre_${z}`, [4.4, 7, z], [4.6, 9, z + 1.6], 'ci', { split: '' });
    m.prism('main_bearing', 'z', [8, 8], 2.2, 6.6, 9, 'ci', { split: '', ends: ['north'] });
    m.prism('main_shaft', 'z', [8, 8], 1, 4.6, 6.6, 'bright', { split: '' });
    m.prism('generator', 'z', [8, 8], 3.4, 25, 30, 'ci', { split: '', ends: ['south'] });
    for (let z = 25.6; z < 30; z += 1.2) m.prism(`gen_fin_${z}`, 'z', [8, 8], 3.7, z, z + 0.4, 'ci', { split: '' });
    // spinner over the hub
    m.prism('spinner', 'z', [8, 8], 2.4, 3, 5, 'sh', { split: '', ends: ['north'] });
    m.prism('spinner_nose', 'z', [8, 8], 1.4, 2, 3, 'sh', { split: '', ends: ['north'] });
    // cup anemometer and wind vane on a mast at the back
    m.box('mast', [7.7, 12, 23], [8.3, 17, 23.6], 'gal', { split: '' });
    m.box('anemo_arm_x', [6, 16.6, 23], [10, 16.9, 23.6], 'gal', { split: '' });
    m.box('anemo_arm_z', [7.7, 16.6, 21.3], [8.3, 16.9, 25.3], 'gal', { split: '' });
    for (const [x, z] of [[5.6, 23], [10, 23], [7.7, 20.9], [7.7, 25.3]]) m.box(`cup_${x}_${z}`, [x, 16.2, z], [x + 0.6, 16.9, z + 0.6], 'red', { split: '' });
    m.box('vane', [7.85, 13, 24.6], [8.15, 15.4, 28], 'sh', { split: '' });
    return m;
  }

  // Blade partial (x3 about the hub, spun about z): steel root spar into the hub, the blade twisted from root to
  // tip in three panels with tapering chord, and the stabiliser (Sabinin's pitch flap) near the tip.
  function blade() {
    const m = mk('wind_turbine/blade');
    m.prismY('root_spar', [8, 6], 0.7, 9.6, 13, 'st');
    m.box('root_clamp', [7, 9.6, 5], [9, 10.6, 7], 'ci', { split: '' });
    m.box('panel_inner', [5.6, 13, 5.6], [10.4, 20, 6.4], 'bl', { split: '', rot: { axis: 'y', angle: 22.5, origin: [8, 16, 6] } });
    m.box('panel_mid', [6, 20, 5.65], [10, 26.5, 6.35], 'bl', { split: '' });
    m.box('panel_tip', [6.6, 26.5, 5.7], [9.4, 31.6, 6.3], 'bl', { split: '' });
    m.box('spar_line', [7.7, 13, 5.5], [8.3, 31.6, 5.6], 'st', { split: '' });
    m.box('stabilizer', [10, 23.4, 6.1], [11.6, 27, 6.5], 'red', { split: '' });
    m.box('stabilizer_arm', [9.6, 25, 6.2], [10, 25.6, 6.4], 'st', { split: '' });
    return m;
  }

  const BUILDS = {
    wind_turbine: { model: towerBase },
    wind_turbine_middle: { model: towerMiddle },
    wind_turbine_top: { model: towerTop },
    'wind_turbine/nacelle': { model: nacelle, java: 'wind_turbine/partial/nacelle' },
    'wind_turbine/blade': { model: blade, java: 'wind_turbine/partial/blade' },
  };

  window.WTB = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'grid', id, BUILDS[id].java),
    preview(root) {
      return PCART.realize(root, towerTop().merge(nacelle(), 0, 40, 0, 'n_').merge(blade(), 20, 0, 0, 'b_'), 'wind_turbine_preview');
    },
  };
})();
