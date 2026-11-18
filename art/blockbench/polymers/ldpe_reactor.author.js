// High-pressure polyethylene (LDPE) unit: secondary hyper compressor and the jacketed tubular reactor, hand-authored
// for Blockbench. Replaces the borrowed hyper_compressor model and its static partials.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/polymers/ldpe_reactor.author.js', 'utf8'))
//   LDPE.build('<repo>', id); LDPE.save('<repo>', id)    for ldpe_reactor, ldpe_reactor_running, ldpe_reactor/crank
//
// 3x1x1 along x, anchored at the middle block (local x -16..32, y 0..16, z 0..16). Facade north.
//   west block   the plunger cylinder of the hyper compressor (about 250 MPa) with its head, suction from above,
//                discharge up and over the back to the reactor;
//   middle block the crankcase on the Create shaft (shaft from the back) with the crosshead guide; the crank disc on
//                the north face is the partial ldpe_reactor/partial/crank, spun about z through x 8, y 6;
//   east block   four jacketed tube passes in a rack (hot-water jackets, green to GOST 14202) in front of the
//                concrete barricade, the let-down valve at the bottom of the east end.
// Points used by code (LdpeReactorBlockEntity): gland drip (-5.5, 4, 8.5); let-down valve (29, 3, 2); vents y 16 at x 20, 27.
(function () {
  const mk = () => Object.assign(new PCART.Model(PLANT.MAT, { parts: PLANT.PARTS }), { particle: 'plant/green' });
  const PASSES = [4.5, 7.5, 10.5, 13.5];

  function unit(running) {
    const m = mk();
    m.box('plinth', [-16, 0, 1], [32, 1.5, 15], 'conc', { hide: ['down'] });

    // ---- middle block: crankcase and crosshead guide
    m.box('crankcase', [1, 1.5, 3], [15, 10, 14.5], 'green', {});
    m.box('crankcase_cover', [0.5, 10, 2.5], [15.5, 11, 15], 'green', {});
    for (const x of [2, 7.5, 13]) m.box(`cover_bolt_${x}`, [x, 11, 3], [x + 1, 11.5, 14.5], 'st', { split: '' });
    m.box('main_bearing', [5, 2.5, 2.2], [11, 9.5, 3], 'frame', { split: '' });
    m.box('lubricator', [11, 11, 4], [15, 14.5, 8], 'light', { split: '' });
    m.decal('lub_gauge', [11.6, 12, 3.8], [13.6, 14, 4], 'light', 'north', 'gauge', { hide: ['south'] });
    m.decal(running ? 'glow_lub_lamp' : 'lub_lamp', [14, 12.6, 3.8], [14.8, 13.4, 4], 'light', 'north', running ? 'lamp_green' : 'lamp_red',
      { hide: ['south'] });
    for (const z of [5, 6.5]) m.box(`lub_line_${z}`, [11.5, 10.6, z], [12, 11.2, z + 0.5], 'st', { split: '' });
    m.box('guide', [-6, 3.5, 5], [1, 8.5, 12], 'green', {});
    m.decal('guide_window', [-4.5, 4.5, 4.8], [-0.5, 7.5, 5], 'green', 'north', 'vent', { hide: ['south'] });
    m.decal('warning', [-4, 9.2, 7], [-1.2, 11.6, 7.2], 'green', 'north', 'warning', { hide: ['south'] });
    m.box('warning_post', [-2.9, 8.5, 7.2], [-2.3, 9.6, 7.8], 'frame', { split: '' });
    m.decal('nameplate', [3, 5, 2.8], [5, 7, 3], 'green', 'north', 'tag', { hide: ['south'] });

    // ---- west block: plunger cylinder and head
    m.box('gland', [-7, 4.6, 6.1], [-6, 7.4, 10.9], 'st', { split: '' });
    m.prism('cylinder', 'x', [6, 8.5], 3.4, -14, -7, 'bright', { ends: ['east'] });
    for (const x of [-13, -9.5]) m.ring(`hoop_${x}`, 'x', [6, 8.5], 3.8, 0.6, x, x + 1.2, 'frame');
    m.box('head', [-15.6, 1.5, 4], [-14, 10.5, 13], 'frame', {});
    for (const [y, z] of [[2.4, 4.9], [2.4, 11.4], [8.9, 4.9], [8.9, 11.4]]) {
      m.box(`head_nut_${y}_${z}`, [-16, y, z], [-15.6, y + 0.8, z + 0.8], 'st', { split: '' });
      m.box(`tie_rod_${y}_${z}`, [-14, y + 0.2, z + 0.2], [-6, y + 0.6, z + 0.6], 'st', { split: '' });
    }
    m.box('cyl_foot', [-14, 1.5, 6], [-7, 2.8, 11], 'frame', {});
    // suction (ethylene, yellow) from above, discharge up and along the back to the reactor
    m.box('suction', [-11.2, 9.6, 7.7], [-9.6, 16, 9.3], 'p_gas', { split: '' });
    m.box('suction_flange', [-11.6, 12, 7.3], [-9.2, 12.6, 9.7], 'frame', { split: '' });
    m.box('discharge_up', [-15.4, 10.5, 12.8], [-14, 14.6, 14.2], 'p_gas', { split: '' });
    m.box('discharge', [-15.4, 13.2, 12.8], [17, 14.6, 14.2], 'p_gas', { split: 'x' });
    for (const x of [-13, 3, 16]) m.box(`discharge_band_${x}`, [x, 13.1, 12.7], [x + 0.8, 14.7, 14.3], 'p_steam', { split: '' });
    PLANT.valve(m, 'suction_valve', -10.4, 14.2, 8.5, 0.9, 'green');

    // ---- east block: tubular reactor rack in front of its barricade
    m.box('barricade', [16, 1.5, 14], [32, 16, 16], 'conc', { hide: ['down'] });
    for (const x of [16.5, 30.6]) for (const z of [2, 12.4]) m.box(`rack_${x}_${z}`, [x, 1.5, z], [x + 0.9, 15.5, z + 0.9], 'frame', { split: 'y' });
    for (const y of [3, 15]) {
      m.box(`rack_beam_n_${y}`, [16.5, y, 2], [31.5, y + 0.6, 2.9], 'frame', { split: 'x' });
      m.box(`rack_beam_s_${y}`, [16.5, y, 12.4], [31.5, y + 0.6, 13.3], 'frame', { split: 'x' });
    }
    PASSES.forEach((y, i) => {
      m.box(`jacket_${i}`, [18, y - 1, 6], [30, y + 1, 9.2], 'p_water', { split: 'x' });
      for (const x of [18, 23.6, 29.2]) m.box(`jacket_flange_${i}_${x}`, [x - 0.2, y - 1.3, 5.7], [x + 0.6, y + 1.3, 9.5], 'frame', { split: '' });
      m.box(`jw_in_${i}`, [24.8, y - 0.4, 9.2], [25.6, y + 0.4, 12.4], 'p_water', { split: '' });
      m.box(`tc_${i}`, [21, y + 1, 7], [21.8, y + 1.6, 7.8], 'st', { split: '' });
    });
    // return bends: east end joins passes 0-1 and 2-3, west end joins 1-2; feed enters pass 3 from the discharge
    for (const [x0, x1, a, b] of [[30, 31.6, 0, 1], [30, 31.6, 2, 3], [16.4, 18, 1, 2]]) {
      m.box(`bend_${a}${b}`, [x0, PASSES[a] - 0.9, 6.6], [x1, PASSES[b] + 0.9, 8.6], 'st', { split: '' });
    }
    m.box('feed_in', [16.4, 13.2, 8.6], [18, 14.6, 12.8], 'p_gas', { split: '' });
    m.box('feed_down', [16.4, 13.2, 6.6], [18, 14.4, 8.6], 'st', { split: '' });
    m.box('jw_header', [24.6, 2.4, 10.8], [25.8, 15, 12], 'p_water', { split: 'y' });
    // let-down valve and its hydraulic actuator under pass 0, product line to the separator
    m.box('letdown_body', [26.6, 1.5, 1.2], [30, 3.4, 4.6], 'green', { split: '' });
    m.box('letdown_actuator', [30, 1.6, 1.6], [32, 3.2, 4.2], 'blue', { split: '' });
    m.box('letdown_from_reactor', [28, 3.4, 5.6], [29.4, 3.6, 7], 'st', { split: '' });
    m.box('product_line', [22, 1.6, 2.4], [26.6, 2.8, 3.6], 'p_liq', { split: '' });
    m.decal('rack_tag', [19, 1.6, 1.9], [22, 3, 2], 'frame', 'north', 'tag', { hide: ['south'] });
    // rupture-disc vents through the barricade top
    for (const x of [20, 27]) {
      m.box(`vent_${x}`, [x - 0.6, 15, 12.6], [x + 0.6, 16, 13.8], 'st', { split: '' });
      m.box(`vent_line_${x}`, [x - 0.4, 13.4, 9.6], [x + 0.4, 15, 13], 'st', { split: '' });
    }
    return m;
  }

  // Crank disc on the north face of the crankcase, with the crankpin and counterweight; spun about (8, 6) along z.
  function crank() {
    const m = mk();
    m.disc('disc', 'z', [8, 6], 4.2, 1.2, 2.2, 'frame', 'ldpe_reactor/crank_disc', 'north');
    m.box('hub', [6.6, 4.6, 2.2], [9.4, 7.4, 3], 'st', { split: '' });
    m.box('crankpin', [7.3, 8.1, 0.4], [8.7, 9.5, 1.2], 'bright', { split: '' });
    m.box('counterweight', [5.4, 2.2, 0.7], [10.6, 3.8, 1.2], 'ci', { split: '' });
    return m;
  }

  const BUILDS = {
    ldpe_reactor: { model: () => unit(false) },
    ldpe_reactor_running: { model: () => unit(true) },
    'ldpe_reactor/crank': { model: crank, java: 'ldpe_reactor/partial/crank' },
  };

  // Radial crank disc texture: machined rim, painted web, a polished hub ring.
  function paint(root) {
    const { rng, img, put, add, writePng } = PCART;
    const im = img(32, 32), r = rng(1101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([78, 92, 84], (r() - 0.5) * 8);
      if (d > 0.86) c = add([150, 154, 158], (r() - 0.5) * 10 - (d > 0.96 ? 30 : 0));
      else if (d > 0.8) c = [40, 44, 42];
      else if (d < 0.36 && d > 0.3) c = [168, 172, 176];
      put(im, x, y, c);
    }
    writePng(im, require('path').join(root, PCART.ASSETS, 'textures/block/ldpe_reactor/crank_disc.png'));
    return 1;
  }

  window.LDPE = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'polymers', id, BUILDS[id].java),
  };
})();
