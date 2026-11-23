// Steam drum of the I1 water-tube boiler at real scale (1 block = 1 m, 16 px = 1 m): a TP-35 class drum of 1.5 m
// bore, 1.6 m over the aluminium-clad lagging, built from 1 m segments that join along x like the old steam_drum.
// Plain block models (one block each, no slicing): the drum axis runs along x through (y 12, z 8), so the shell
// takes y 0..24 and z -4..20 and stands 0.5 m into the block above and 0.25 m into each side neighbour.
//
// steam_drum_real       mid segment: clad shell with two straps, a saddle on the roof wall, the downcomer (GOST
//                       green) down into the block below, a row of riser tubes from the screens entering the
//                       south underside, the steam offtake (GOST red) on top, the water gauge glass on the north
//                       side (the boiler house side, as on the old drum).
// steam_drum_real_end   the same segment closed by a dished head with the manhole cover on its east end, the
//                       safety valve with its lever and weight on top; mirror it (y 180) for the west end.
//
// Load in Blockbench after pcart.js, pcround.js and core/vver/vverkit.js, then for n in segment, end:
//   SDR.build(repo, n); SDR.save(repo, n)
(function () {
  const C = [12, 8], R = 12.8;                      // axis [y, z], radius over the cladding
  const MAT = Object.assign({}, PCVV.MAT, {
    clad: { tex: 'plant/cladding', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 }, frame: { tex: 'plant/structure', P: 16 },
    p_water: { tex: 'plant/pipe_water', P: 16 }, p_steam: { tex: 'plant/pipe_steam', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'plant/cladding' });

  function segment(m, x1) {
    m.prism('shell', 'x', C, R, 0, x1, 'clad', { split: '', ends: x1 < 16 ? ['east'] : [] });
    for (const x of [3, Math.min(12, x1 - 1.2)]) m.ring(`strap_${x}`, 'x', C, R + 0.2, 0.4, x, x + 0.6, 'gal');
    // saddle on the roof wall: a cradle under the shell
    m.box('saddle_base', [5, 0, -2], [11, 0.8, 18], 'frame', { split: '' });
    for (const z of [-2, 15]) m.box(`saddle_web_${z}`, [5, 0.8, z], [11, 4, z + 3], 'frame', { split: '' });
    // downcomer down into the roof block (it carries the water to the lower headers)
    m.cylY('downcomer', [8, 4], 2.6, -16, 1.4, 'p_water');
    m.ringY('downcomer_flange', [8, 4], 3.2, 0.8, -2, -1.2, 'frame');
    // riser tubes from the screens into the south underside
    for (let x = 1.5; x < x1 - 0.5; x += 2.5) m.cylY(`riser_${x}`, [x, 15.4], 0.8, -16, 3.2, 'gal');
    // steam offtake on top
    m.cylY('steam_off', [8, 8], 1.8, R + C[0] - 0.6, 30, 'p_steam');
    m.ringY('steam_flange', [8, 8], 2.6, 0.8, 29.2, 30, 'frame');
    // water gauge glass on the north side with its upper and lower cocks
    m.box('gauge_upper', [3.4, 17, -5.6], [4.6, 18, -3.8], 'st', { split: '' });
    m.box('gauge_lower', [3.4, 6, -5.6], [4.6, 7, -3.8], 'st', { split: '' });
    m.plate('gauge_glass', [3.2, 7, -6], [4.8, 17, -5.2], 'st', 'north', 'boilers/level');
    m.box('gauge_drain', [3.6, 4, -5.6], [4.4, 6, -4.8], 'st', { split: '' });
  }

  function end(m) {
    segment(m, 10);
    // dished head from x 10: three stepped discs, the manhole cover and its yoke on the last
    m.disc('head_1', 'x', C, R - 1, 10, 12.6, 'clad', 'vver/cap', 'east');
    m.disc('head_2', 'x', C, R - 4, 12.6, 14.4, 'clad', 'vver/cap', 'east');
    m.disc('head_3', 'x', C, R - 7.4, 14.4, 15.4, 'clad', 'vver/cap', 'east');
    m.box('manhole', [15.4, C[0] - 4, C[1] - 3], [16, C[0] + 4, C[1] + 3], 'forged', { split: '' });
    m.box('yoke', [16, C[0] - 0.6, C[1] - 4], [16.6, C[0] + 0.6, C[1] + 4], 'ci', { split: '' });
    m.box('yoke_nut', [16.6, C[0] - 0.8, C[1] - 0.8], [17.2, C[0] + 0.8, C[1] + 0.8], 'bright', { split: '' });
    // safety valve on top with lever and weight
    m.cylY('sv_branch', [4, 8], 1.4, R + C[0] - 0.6, 27, 'p_steam');
    m.box('sv_body', [2.4, 27, 6.4], [5.6, 30, 9.6], 'red', { split: '' });
    m.box('sv_lever', [4, 30, 7.6], [15, 30.6, 8.4], 'st', { split: '' });
    m.box('sv_weight', [12.6, 28.8, 7], [14.4, 31.2, 9], 'ci', { split: '' });
    m.box('sv_exhaust', [3.4, 30, 7.4], [4.6, 32, 8.6], 'p_steam', { split: '' });
  }

  const BUILD = { segment: { id: 'steam_drum_real', draw: (m) => segment(m, 16) }, end: { id: 'steam_drum_real_end', draw: end } };
  window.SDR = {
    BUILD,
    build: (root, n) => { const m = mk(); BUILD[n].draw(m); PCART.realize(root, m, BUILD[n].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, n) => PCART.save(root, 'boilers', BUILD[n].id, BUILD[n].id),
  };
})();
