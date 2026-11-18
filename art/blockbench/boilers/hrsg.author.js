// Heat recovery steam generator of the combined-cycle unit, hand-authored for Blockbench (replaces the
// spec_grid2.py placeholders hrsg and hrsg_top).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (shared cladding,
// yellow):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/hrsg.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); HRSG.paint('<repo>')
//   build + save each model in turn (HRSG.build / HRSG.save, ids in HRSG.ids)
//
// Two 3x3x3 segments about their middle blocks (-16..32), front to the north. A horizontal-flow drum unit. The
// gas turbine exhaust enters at the back (south) through the fabric expansion joint and crosses the tube banks
// in the insulated casing (superheater, evaporator, economiser from back to front). It turns up through the
// outlet hood into the stack at the front.
// hrsg (DUCT): the casing between its corner columns, with stiffener ribs, access doors and a sampling
// platform. Feedwater comes into the economiser low on the east side (green) and the superheated steam leaves
// high at the back (red), each pipe coloured by GOST 14202.
// hrsg_top (DRUM): the roof with the steam drum along x on its saddles, the downcomers and risers. On top of the
// drum stand the two spring safety valves with their silencers, and a gauge glass sits on the west end. The
// outlet hood rises into the stack, which has its damper band and a railed platform.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, grain, writePng } = PCART;
  const T = {};

  // Casing: blue-grey panels between ribs every half block, grime along the bottom of each panel.
  T['hrsg/casing'] = () => {
    const im = img(32, 32), r = grain(im, [100, 112, 124], 3701, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = get(im, x, y);
      if (x % 16 === 0) c = add(c, 18); else if (x % 16 === 15) c = add(c, -20);
      c = mix(c, [70, 72, 74], Math.max(0, (y % 16 - 10) / 6) * 0.25);
      put(im, x, y, c);
    }
    return im;
  };
  // Fabric expansion joint: black, quilted, the steel frame edges.
  T['hrsg/fabric'] = () => {
    const im = img(16, 16), r = grain(im, [38, 38, 40], 3711, 4, 5, 4);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (x % 4 === 0 || y % 4 === 0) put(im, x, y, [26, 26, 28]);
    return im;
  };
  // Stack: weathered grey with a soot bloom near the top.
  T['hrsg/stack'] = () => {
    const im = img(32, 32), r = grain(im, [128, 128, 126], 3721, 4, 7, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) if (y % 16 === 0) put(im, x, y, add(get(im, x, y), -18));
    return im;
  };

  const MAT = {
    cas: { tex: 'hrsg/casing', P: 16 }, fab: { tex: 'hrsg/fabric', P: 16 }, stk: { tex: 'hrsg/stack', P: 16 },
    clad: { tex: 'pwr_reactor/clad', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'hrsg/casing' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function duct() {
    const m = mk();
    for (const x of [-15.5, 29]) for (const z of [-14.5, 29]) m.box(`column_${x}_${z}`, [x, -16, z], [x + 2.5, 32, z + 1.5], 'ci', { split: 'y' });
    m.box('casing', [-14, -14, -14], [30, 32, 29.4], 'cas');
    m.box('floor_beam', [-15.5, -16, -14], [31.5, -14, 30], 'ci');
    m.box('expansion_joint', [-12, -12, 29.4], [28, 30, 32], 'fab');
    for (const x of [-12.6, 27.4]) m.box(`joint_frame_${x}`, [x, -12.6, 29.4], [x + 1.2, 30.6, 32], 'ci', { split: 'y' });
    // stiffener ribs round the casing
    for (const y of [-4, 12, 26]) {
      m.box(`rib_n_${y}`, [-14, y, -14.8], [30, y + 1, -14], 'ci', { split: 'x' });
      m.box(`rib_e_${y}`, [30, y, -14], [30.8, y + 1, 29.4], 'ci', { split: 'z' });
      m.box(`rib_w_${y}`, [-14.8, y, -14], [-14, y + 1, 29.4], 'ci', { split: 'z' });
    }
    // access doors, drain valves, sample ports
    m.box('door_n', [4, -12, -14.6], [12, -4.6, -14], 'cas', { split: '' });
    m.box('door_n_handle', [10.4, -9, -15], [11, -7.4, -14.6], 'red', { split: '' });
    m.box('door_e', [30, -12, 14], [30.6, -4.6, 22], 'cas', { split: '' });
    for (const x of [-6, 8, 22]) m.box(`drain_${x}`, [x, -15.6, 4], [x + 1.2, -14, 5.2], 'red', { split: '' });
    m.box('sample_port', [-15, 14, 4], [-14, 16, 6], 'st', { split: '' });
    // platform along the front with its rail, ladder up the west column
    m.box('platform', [-15.5, 12, -16], [31.5, 12.6, -14.8], 'chequer', { split: 'x' });
    m.box('rail', [-15.5, 17, -16], [31.5, 17.5, -15.6], 'yel', { split: 'x' });
    for (const x of [-15.5, -2, 12, 26, 31]) m.box(`rail_post_${x}`, [x, 12.6, -16], [x + 0.5, 17, -15.6], 'yel', { split: '' });
    // feedwater into the economiser low on the east side, superheated steam out high at the back
    m.prism('feedwater', 'x', [-6, -4], 1.4, 30, 32, 'green', { split: '', ends: ['east'] });
    m.prism('feed_flange', 'x', [-6, -4], 2, 31.4, 32, 'st', { split: '', ends: ['east'] });
    m.prism('steam_out', 'x', [22, 24], 1.8, 30, 32, 'red', { split: '', ends: ['east'] });
    m.prism('steam_flange', 'x', [22, 24], 2.4, 31.4, 32, 'st', { split: '', ends: ['east'] });
    return m;
  }

  function drum() {
    const m = mk();
    // casing goes on up to the roof over the banks; the outlet hood at the front rises into the stack
    for (const x of [-15.5, 29]) m.box(`column_${x}`, [x, -16, 29], [x + 2.5, -4, 30.5], 'ci', { split: 'y' });
    m.box('casing', [-14, -16, 2], [30, -4, 29.4], 'cas');
    m.box('hood', [-14, -16, -14], [30, -10, 2], 'cas');
    m.box('hood_mid', [-8, -10, -13], [24, -4, 1], 'cas');
    m.box('hood_top', [-2, -4, -12], [18, 0, 0], 'cas');
    m.cylY('stack', [8, -6], 7, 0, 32, 'stk');
    m.ringY('stack_band', [8, -6], 7.4, 0.8, 8, 9.6, 'ci');
    m.box('damper_shaft', [-0.4, 8.4, -6.4], [16.4, 9.2, -5.6], 'st', { split: '' });
    m.box('damper_actuator', [16.4, 7.4, -7.4], [18.8, 10.4, -4.6], 'yel', { split: '' });
    m.cylY('stack_platform', [8, -6], 9.6, 18, 18.6, 'ci', { bottom: true, cap: 'material/chequer' });
    m.ringY('stack_rail', [8, -6], 9.6, 0.5, 23, 23.5, 'yel');
    // steam drum on saddles, dished ends
    m.prism('drum', 'x', [4, 17], 5, -11, 27, 'clad', { split: '' });
    m.disc('drum_end_w', 'x', [4, 17], 5, -12.4, -11, 'clad', 'pwr_reactor/flange', 'west');
    m.disc('drum_end_e', 'x', [4, 17], 5, 27, 28.4, 'clad', 'pwr_reactor/flange', 'east');
    for (const x of [-6, 20]) m.box(`saddle_${x}`, [x, -4, 13], [x + 2, 0, 21], 'ci', { split: '' });
    // downcomers and risers between the drum and the roof
    for (const x of [-9, 25]) m.box(`downcomer_${x}`, [x - 0.8, -4, 16.2], [x + 0.8, 0, 17.8], 'clad', { split: '' });
    for (const x of [-2, 4, 10, 16]) {
      m.box(`riser_${x}`, [x - 0.6, -4, 23.4], [x + 0.6, 2, 24.6], 'clad', { split: '' });
      m.box(`riser_top_${x}`, [x - 0.6, 2, 19], [x + 0.6, 3.2, 24.6], 'clad', { split: '' });
    }
    // safety valves with silencers, main steam out, gauge glass on the west end
    for (const x of [0, 14]) {
      m.box(`sv_${x}`, [x - 1, 9, 16], [x + 1, 12, 18], 'red', { split: '' });
      m.box(`sv_spring_${x}`, [x - 0.6, 12, 16.4], [x + 0.6, 14, 17.6], 'st', { split: '' });
      m.cylY(`silencer_${x}`, [x + 3, 17], 1.4, 10, 22, 'stk');
      m.box(`sv_pipe_${x}`, [x + 1, 10.6, 16.6], [x + 2, 11.6, 17.4], 'red', { split: '' });
    }
    m.box('main_steam', [21, 9, 16.2], [22.6, 14, 17.8], 'red', { split: '' });
    m.box('main_steam_run', [21, 12.4, 16.2], [32, 14, 17.8], 'red', { split: 'x' });
    m.box('gauge_glass', [-13.6, 1.6, 16.6], [-13, 6.6, 17.4], 'gl_level', { split: '' });
    m.box('gauge_cocks', [-14, 1, 16.4], [-12.6, 1.6, 17.6], 'red', { split: '' });
    return m;
  }
  MAT.gl_level = { tex: 'material/brass', P: 16 };

  const BUILDS = {
    hrsg: { model: duct },
    hrsg_top: { model: drum },
  };

  window.HRSG = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'boilers', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, duct().merge(drum(), 0, 48, 0, 'd_'), 'hrsg_preview'); },
  };
})();
