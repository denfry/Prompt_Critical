// Refinery tube furnace (crude heater), hand-authored for Blockbench. It replaces the borrowed
// petrochem/tube_furnace_radiant, which the pyrolysis furnace keeps.
// Load inside Blockbench desktop, after the shared toolkit and the round shapes:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/tube_furnace.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); TF.paint('<repo>')
//   build + save each model in turn (TF.build / TF.save, ids in TF.ids)
//
// 3x3x3 about the middle block (-16..32), front to the north. A box-type, floor-fired furnace of the kind on
// Soviet AT/AVT crude units. The firebox (radiant section) is a steel casing lined with firebrick, standing on
// columns high enough to reach the burners from below. Each floor burner has its gas and air register and its
// own cock on the fuel gas ring. The convection section with its return bend boxes sits on top. Crude comes in
// at the top of the convection coil from the north and leaves through the transfer line low on the west side.
// The stack has its damper. Peep doors on the front show the radiant tubes against the fire when lit (the
// _lit model), and the burners glow under the floor.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, rivet, writePng } = PCART;
  const T = {};

  // Casing: grey heat-resistant paint over plates with vertical stiffeners, darkened and rust-streaked up top.
  T['tube_furnace/casing'] = () => {
    const im = img(32, 32), r = grain(im, [108, 112, 112], 3301, 3, 6, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = get(im, x, y);
      if (x % 16 === 0) c = add(c, 22); else if (x % 16 === 1) c = add(c, -18);
      if (y % 32 === 0) c = add(c, -14);
      put(im, x, y, c);
    }
    for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 32), y = Math.floor(r() * 20); for (let j = 0; j < 8; j++) put(im, x, y + j, mix(get(im, x, y + j), [118, 78, 52], 0.4)); }
    return im;
  };
  // Convection section and stack: darker, soot over the paint.
  T['tube_furnace/hot_casing'] = () => {
    const im = img(32, 32), r = grain(im, [78, 76, 74], 3311, 4, 8, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) if (x % 16 === 0) put(im, x, y, add(get(im, x, y), 16));
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [44, 40, 38]);
    return im;
  };
  // Peep door glass, cold: dark with a reflection.
  T['tube_furnace/peep'] = () => {
    const im = img(16, 16); grain(im, [40, 38, 36], 3321, 3, 4, 4);
    for (let i = 0; i < 6; i++) put(im, 3 + i, 9 - i, [86, 84, 80]);
    return im;
  };
  // Peep door, fired: the radiant tubes standing dark against the orange-white fire behind.
  T['tube_furnace/peep_hot'] = () => {
    const im = img(16, 16), r = rng(3331);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = mix([255, 236, 150], [232, 104, 28], Math.abs(y - 8) / 8);
      if (x % 5 === 1 || x % 5 === 2) c = mix([120, 40, 18], [70, 26, 14], x % 5 === 2 ? 0.6 : 0);
      put(im, x, y, add(c, (r() - 0.5) * 18));
    }
    return im;
  };
  // Burner throat from below: firebrick quarl round the gas tip, or the flame when lit.
  T['tube_furnace/burner'] = () => {
    const im = img(16, 16), r = grain(im, [150, 112, 82], 3341, 6, 6, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (Math.hypot(x - 7.5, y - 7.5) < 4) put(im, x, y, [30, 28, 26]);
    return im;
  };
  T['tube_furnace/burner_hot'] = () => {
    const im = img(16, 16), r = rng(3351);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x - 7.5, y - 7.5) / 8;
      put(im, x, y, d < 0.5 ? mix([150, 200, 255], [255, 180, 80], d * 2) : add(mix([255, 150, 50], [190, 70, 30], d), (r() - 0.5) * 20));
    }
    return im;
  };

  const MAT = {
    cas: { tex: 'tube_furnace/casing', P: 16 }, hot: { tex: 'tube_furnace/hot_casing', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
    clad: { tex: 'pwr_reactor/clad', P: 16 }, peep: { tex: 'tube_furnace/peep', P: 16 }, peepH: { tex: 'tube_furnace/peep_hot', P: 16 },
    brn: { tex: 'tube_furnace/burner', P: 16 }, brnH: { tex: 'tube_furnace/burner_hot', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'tube_furnace/casing' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function furnace(lit) {
    const m = mk();
    const full = (t) => ({ tex: t, uv: [0, 0, 16, 16] });
    // columns and the floor of the firebox
    for (const x of [-14, 28]) for (const z of [-12, 26]) m.box(`column_${x}_${z}`, [x, -16, z], [x + 2, -9, z + 2], 'ci', { split: '' });
    m.box('floor', [-14, -10, -12], [30, -8, 28], 'cas');
    // floor burners in two rows, each with its register below and the cock on the gas ring
    for (const x of [-6, 8, 22]) for (const z of [-2, 18]) {
      const n = `${x}_${z}`;
      m.box(`${lit ? 'glow_' : ''}burner_${n}`, [x - 2, -10.6, z - 2], [x + 2, -10, z + 2], lit ? 'brnH' : 'brn',
        { split: '', face: { down: full(lit ? 'tube_furnace/burner_hot' : 'tube_furnace/burner') } });
      m.box(`register_${n}`, [x - 1.4, -13, z - 1.4], [x + 1.4, -10.6, z + 1.4], 'ci', { split: '' });
      m.box(`gas_tip_${n}`, [x - 0.4, -14.4, z - 0.4], [x + 0.4, -13, z + 0.4], 'st', { split: '' });
      m.box(`cock_${n}`, [x + 0.4, -14.6, z - 0.3], [x + 1.6, -14, z + 0.3], 'red', { split: '' });
    }
    for (const z of [-2, 18]) m.box(`gas_ring_${z}`, [-14, -15, z - 0.4], [30, -14.4, z + 0.4], 'yel', { split: 'x' });
    // radiant section: casing, buckstays, peep doors and the explosion door
    m.box('radiant', [-14, -8, -12], [30, 14, 28], 'cas');
    for (const x of [-14.6, 29.6]) for (const y of [-6, 4]) m.box(`buckstay_${x}_${y}`, [x, y, -12.6], [x + 1, y + 1.6, 28.6], 'ci', { split: '' });
    for (const x of [-6, 8, 22]) {
      m.box(`${lit ? 'glow_' : ''}peep_${x}`, [x - 1.6, 1, -12.6], [x + 1.6, 4.6, -12], lit ? 'peepH' : 'peep',
        { split: '', face: { north: full(lit ? 'tube_furnace/peep_hot' : 'tube_furnace/peep') } });
      m.box(`peep_frame_${x}`, [x - 2.2, 0.4, -12.4], [x + 2.2, 1, -12], 'ci', { split: '' });
      m.box(`peep_hinge_${x}`, [x + 1.6, 1, -12.8], [x + 2.2, 4.6, -12], 'ci', { split: '' });
    }
    m.box('explosion_door', [13, -6, 28], [19, 0, 28.8], 'ci', { split: '' });
    // service platform round the top of the firebox
    m.box('platform', [-15.6, 14, -13.6], [31.6, 14.6, 29.6], 'chequer');
    for (const [f, t] of [[[-15.6, 14.6, -13.6], [31.6, 15.2, -13]], [[-15.6, 14.6, 29], [31.6, 15.2, 29.6]]]) m.box(`toe_${f[2]}`, f, t, 'yel', { split: '' });
    for (const z of [-13.4, 29.2]) {
      m.box(`rail_${z}`, [-15.6, 19, z], [31.6, 19.5, z + 0.4], 'yel', { split: '' });
      for (const x of [-15.4, -2, 12, 24, 31]) m.box(`rail_post_${z}_${x}`, [x, 14.6, z], [x + 0.5, 19, z + 0.4], 'yel', { split: '' });
    }
    // convection section with the return bend boxes on the ends
    m.box('convection', [-8, 14.6, -4], [24, 25, 20], 'hot');
    for (const [n, x0] of [['w', -11], ['e', 24]]) m.box(`bend_box_${n}`, [x0, 15.6, -2], [x0 + 3, 24, 18], 'hot', { split: '' });
    m.box('breeching', [0, 25, 2], [16, 27, 14], 'hot');
    m.cylY('stack', [8, 8], 4.2, 27, 32, 'hot', { seed: 2 });
    m.box('damper_lever', [12.2, 28.4, 7.6], [15, 29, 8.4], 'red', { split: '' });
    // crude to the convection coil inlet from the north, transfer line out low on the west side
    m.prism('crude_in', 'z', [-6, 22], 1.2, -16, -4, 'clad', { split: '', ends: ['north'] });
    m.box('crude_in_flange', [-7.6, 20.4, -16], [-4.4, 23.6, -15.4], 'st', { split: '' });
    m.prism('transfer_line', 'x', [-4, 20], 1.8, -16, -14, 'clad', { split: '', ends: ['west'] });
    m.box('transfer_flange', [-16, -6.2, 17.8], [-15.4, -1.8, 22.2], 'st', { split: '' });
    // fuel gas riser to the ring under the floor, with its shut-off valve
    m.box('gas_riser', [30, -15, 3.6], [30.8, 6, 4.4], 'yel', { split: 'y' });
    m.box('gas_riser_run', [28, -15, 3.6], [30.8, -14.4, 4.4], 'yel', { split: '' });
    m.box('gas_valve', [30.8, 2, 3], [31.8, 3.6, 5], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    tube_furnace: { model: () => furnace(false) },
    tube_furnace_lit: { model: () => furnace(true) },
  };

  window.TF = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'refinery', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, furnace(false).merge(furnace(true), 52, 0, 0, 'l_'), 'tube_furnace_preview'); },
  };
})();
