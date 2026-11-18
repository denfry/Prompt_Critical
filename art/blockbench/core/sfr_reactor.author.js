// Sodium-cooled fast reactor (BN type, pool layout), hand-authored for Blockbench. Replaces the borrowed
// reactor_vessel_wall / control_rod_drive models, so the SFR no longer looks like the PWR.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/sfr_reactor.author.js', 'utf8'))
//   SFR.paint('<repo>'); SFR.build('<repo>', 'sfr_reactor'); SFR.save('<repo>', 'sfr_reactor')   // per model
//   SFR.preview('<repo>')                                                                         // both segments, look only
//
// Two 3x3x3 segments, each anchored at its middle block (local -16..32 on every axis):
//   sfr_reactor       the pool: the clad main vessel on its skirt and plinth, the service deck on the north side,
//                     the ionisation chamber housings, auxiliary sodium lines east and the argon cover-gas line west;
//   sfr_reactor_plug  three blocks higher: the upper fixed shield, the large and small rotating plugs with the
//                     control rod drives, six intermediate heat exchanger heads and the refuelling mechanism.
// Points used by code (SfrReactorBlockEntity): drive cluster top (10, 15.6, 4) on the plug segment.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/sfr_reactor';
  const T = {};

  // Aluminium-sheet cladding over the vessel insulation: 16 px sheets with lapped vertical seams and screws,
  // a stainless strap every 32 px, a little grime run down from the straps.
  T.cladding = () => {
    const im = img(32, 32), r = rng(301), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add([156, 160, 162], (n(x, y) - 0.5) * 10 + (r() - 0.5) * 7 + ((x + y * 3) % 5 === 0 ? 4 : 0)));
    }
    for (const sx of [0, 16]) for (let y = 0; y < 32; y++) {
      put(im, sx, y, add(get(im, sx, y), -30)); put(im, sx + 1, y, add(get(im, sx + 1, y), 16));
    }
    for (let x = 0; x < 32; x++) {
      put(im, x, 30, [176, 180, 184]); put(im, x, 31, [112, 116, 120]);
      if (r() < 0.25) for (let k = 1; k < 2 + r() * 6; k++) put(im, x, 30 - k - 1, add(get(im, x, 30 - k - 1), -12));
    }
    for (const sx of [2, 18]) for (let y = 3; y < 30; y += 7) { put(im, sx, y, [96, 98, 100]); put(im, sx, y + 1, [190, 192, 194]); }
    return im;
  };
  // Reinforced concrete of the reactor cavity: board-marked formwork, tie holes, a few blowholes.
  T.concrete = () => {
    const im = img(32, 32), r = grain(im, [134, 132, 124], 311, 6, 7, 8);
    for (let x = 0; x < 32; x++) { put(im, x, 15, add(get(im, x, 15), -14)); put(im, x, 31, add(get(im, x, 31), -14)); }
    for (const [x, y] of [[7, 7], [23, 7], [7, 23], [23, 23]]) { rect(im, x, y, 2, 2, [80, 78, 74]); put(im, x, y, [62, 60, 58]); }
    for (let k = 0; k < 18; k++) put(im, r() * 32, r() * 32, [100, 98, 92]);
    return im;
  };
  // Painted structural steel, Soviet hall grey-green, and its lighter shade for the rotating plugs.
  const painted = (base, seed) => () => {
    const im = img(32, 32), r = grain(im, base, seed, 4, 6, 8);
    for (let k = 0; k < 12; k++) put(im, r() * 32, r() * 32, add(base, -22));
    for (let k = 0; k < 5; k++) put(im, r() * 32, r() * 32, [110, 84, 60]);
    return im;
  };
  T.steel_green = painted([78, 92, 84], 321);
  T.steel_light = painted([122, 130, 122], 322);
  // Pipe paint to GOST 14202: brown for flammable liquids (sodium), yellow for gases (argon).
  T.sodium_pipe = painted([110, 70, 42], 323);
  T.argon_pipe = painted([192, 158, 44], 324);
  T.yellow = painted([206, 168, 40], 325);
  // CRDM drive housings: light grey enamel, a dark band at the joint, a white tag.
  T.drive = () => {
    const im = img(32, 32), r = grain(im, [168, 170, 166], 331, 4, 5, 8);
    for (let x = 0; x < 32; x++) { put(im, x, 0, [92, 94, 92]); put(im, x, 16, [92, 94, 92]); put(im, x, 17, [196, 198, 194]); }
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [120, 122, 118]);
    return im;
  };
  // Diagonal yellow and black hazard banding for moving handling gear.
  T.hazard = () => {
    const im = img(32, 32), r = rng(341);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add(((x + y) >> 3) % 2 ? [30, 30, 30] : [212, 172, 36], (r() - 0.5) * 10));
    }
    return im;
  };

  // Upper fixed shield seen from above: plate joints, a bolt circle, a hazard ring at the edge.
  T.roof = () => {
    const im = img(64, 64), r = rng(351);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const dx = x + 0.5 - 32, dy = y + 0.5 - 32, d = Math.hypot(dx, dy) / 32, a = Math.atan2(dy, dx);
      let c = add([76, 90, 82], (r() - 0.5) * 7);
      const seg = Math.abs(((a / (Math.PI / 4)) % 1 + 1) % 1 - 0.5);
      if (d > 0.58 && d < 0.95 && seg > 0.47) c = add(c, -20);
      if (d > 0.95) c = ((Math.floor((a + Math.PI) * 10)) % 2) ? [32, 32, 30] : [204, 166, 40];
      if (Math.abs(d - 0.57) < 0.012) c = add(c, -26);
      put(im, x, y, c);
    }
    for (let k = 0; k < 40; k++) {
      const a = k / 40 * Math.PI * 2;
      rivet(im, Math.floor(31.5 + 28.4 * Math.cos(a)), Math.floor(31.5 + 28.4 * Math.sin(a)), [76, 90, 82]);
    }
    return im;
  };
  // Large rotating plug: hydraulic-seal gap at the rim and a bolt circle. Caps of a 16-gon only agree on radial
  // patterns, so nothing off-centre is painted on them.
  T.plug_large = () => {
    const im = img(64, 64), r = rng(361);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      let c = add([124, 132, 124], (r() - 0.5) * 7 - d * 8);
      if (d > 0.94) c = [26, 28, 28];
      else if (d > 0.9) c = add(c, 20);
      put(im, x, y, c);
    }
    for (let k = 0; k < 32; k++) {
      const a = k / 32 * Math.PI * 2;
      rivet(im, Math.floor(31.5 + 26 * Math.cos(a)), Math.floor(31.5 + 26 * Math.sin(a)), [124, 132, 124]);
    }
    return im;
  };
  // Small rotating plug: seal gap, the seal groove under the ring of drives, the central boss.
  T.plug_small = () => {
    const im = img(32, 32), r = rng(371);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([132, 140, 132], (r() - 0.5) * 7);
      if (d > 0.92) c = [26, 28, 28];
      put(im, x, y, c);
    }
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      if (Math.abs(d - 0.57) < 0.045) put(im, x, y, add(get(im, x, y), -24));       // drive circle seal groove
      if (d < 0.2) put(im, x, y, add(get(im, x, y), d < 0.12 ? -40 : 16));         // central boss
    }
    return im;
  };
  // Heat-exchanger head flange from above.
  T.ihx_top = () => {
    const im = img(16, 16), r = rng(381);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, add(d > 0.86 ? [96, 110, 102] : [78, 92, 84], (r() - 0.5) * 8 - (d > 0.96 ? 24 : 0)));
    }
    for (let k = 0; k < 8; k++) {
      const a = k / 8 * Math.PI * 2;
      put(im, 7.5 + 6.4 * Math.cos(a), 7.5 + 6.4 * Math.sin(a), [190, 194, 190]);
    }
    return im;
  };

  // Atlas of one-off parts (64 px, 4 px per uv unit).
  const A = {
    nameplate: [0, 0, 32, 16], trefoil: [32, 0, 16, 16], detector: [48, 0, 16, 16], lamp_green: [0, 16, 4, 4],
    lamp_amber: [4, 16, 4, 4], tag: [8, 16, 12, 6],
  };
  T.fittings = () => {
    const im = img(64, 64), r = rng(391);
    // maker's plate on the cladding: anodised aluminium, black field, embossed lines of lettering
    for (let y = 0; y < 16; y++) for (let x = 0; x < 32; x++) {
      let c = add([44, 46, 50], (r() - 0.5) * 5);
      if (x === 0 || y === 0 || x === 31 || y === 15) c = [178, 182, 186];
      put(im, x, y, c);
    }
    for (const [y, a, b] of [[3, 4, 27], [6, 4, 20], [9, 4, 24], [12, 4, 14]]) for (let x = a; x <= b; x++) if (r() < 0.62) put(im, x, y, [196, 200, 204]);
    for (const [x, y] of [[2, 2], [29, 2], [2, 13], [29, 13]]) put(im, x, y, [120, 122, 126]);
    // radiation hazard sign (GOST 17925): black trefoil on yellow, black rim
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy), a = (Math.atan2(dy, dx) * 180 / Math.PI + 450) % 120;
      let c = [222, 184, 30];
      if (x === 0 || y === 0 || x === 15 || y === 15) c = [28, 28, 26];
      if (d < 1.5) c = [28, 28, 26];
      else if (d > 2.3 && d < 6.4 && a > 30 && a < 90) c = [28, 28, 26];
      put(im, 32 + x, y, add(c, (r() - 0.5) * 6));
    }
    // ionisation chamber housing face: grey box, connector, two lamp sockets
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = add([110, 116, 112], (r() - 0.5) * 6);
      if (x === 0 || y === 0) c = add(c, 16);
      if (x === 15 || y === 15) c = add(c, -20);
      put(im, 48 + x, y, c);
    }
    rect(im, 52, 9, 8, 4, [228, 228, 222]);
    for (let x = 53; x < 59; x++) if (r() < 0.6) put(im, x, 10 + (x % 2), [40, 40, 40]);
    rect(im, 50, 2, 3, 3, [30, 30, 30]); rect(im, 59, 2, 3, 3, [30, 30, 30]);
    for (let y = 16; y < 20; y++) for (let x = 0; x < 4; x++) {
      const e = Math.hypot(x + 0.5 - 2, y + 0.5 - 18) / 2;
      put(im, x, y, mix([160, 255, 150], [40, 170, 60], e));
      put(im, 4 + x, y, mix([255, 230, 140], [220, 140, 30], e));
    }
    for (let y = 16; y < 22; y++) for (let x = 8; x < 20; x++) put(im, x, y, y === 16 || y === 21 ? [150, 150, 146] : [232, 232, 226]);
    for (let x = 10; x < 18; x++) if (r() < 0.6) put(im, x, 18 + (x % 2), [30, 30, 30]);
    return im;
  };

  const MAT = {
    clad: { tex: 'sfr_reactor/cladding', P: 16 }, conc: { tex: 'sfr_reactor/concrete', P: 16 },
    gv: { tex: 'sfr_reactor/steel_green', P: 16 }, gl: { tex: 'sfr_reactor/steel_light', P: 16 },
    na: { tex: 'sfr_reactor/sodium_pipe', P: 16 }, ar: { tex: 'sfr_reactor/argon_pipe', P: 16 },
    yel: { tex: 'sfr_reactor/yellow', P: 16 }, drive: { tex: 'sfr_reactor/drive', P: 16 }, haz: { tex: 'sfr_reactor/hazard', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { fit: { tex: 'sfr_reactor/fittings', size: 64, rects: A } }),
    { particle: 'sfr_reactor/cladding' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const C = [8, 8], RV = 22;

  // ------------------------------------------------------------------ pool segment
  function pool() {
    const m = mk();
    m.box('plinth', [-16, -16, -16], [32, -12, 32], 'conc', { hide: ['down'] });
    m.prismY('skirt', C, 19, -12, -8, 'gv');
    m.prismY('guard', C, 22.5, -8, -4, 'gv', { ends: ['up'] });
    for (const [a, b] of [[-4, 0], [0, 16], [16, 28]]) m.prismY(`vessel_${a}`, C, RV, a, b, 'clad');
    for (const y of [6, 21]) m.prismY(`band_${y}`, C, RV + 0.4, y, y + 1, 'st', { ends: ['up', 'down'] });
    m.prismY('flange', C, 23.4, 28, 32, 'gv', { ends: ['down'] });

    // service deck on the north side, with handrails and a ladder from the plinth
    m.box('deck', [-10, 12, -16], [26, 13, -11], 'chequer', {});
    m.box('deck_beam', [-10, 11, -16], [26, 12, -15], 'gv', { hide: ['up'] });
    for (const x of [-10, 0, 10, 20, 25.4]) m.box(`post_${x}`, [x, 13, -16], [x + 0.6, 19, -15.4], 'yel', { split: '' });
    for (const x of [-10, 25.4]) m.box(`post_back_${x}`, [x, 13, -11.6], [x + 0.6, 19, -11], 'yel', { split: '' });
    m.box('rail_top', [-10, 18.4, -16], [26, 19, -15.4], 'yel', { split: 'x' });
    m.box('rail_mid', [-10, 15.7, -16], [26, 16.2, -15.4], 'yel', { split: 'x' });
    for (const x of [-10, 25.4]) m.box(`rail_side_${x}`, [x, 18.4, -15.4], [x + 0.6, 19, -11.6], 'yel', { split: '' });
    m.box('toe_board', [-10, 13, -16], [26, 13.8, -15.8], 'yel', { split: 'x' });
    for (const x of [27, 29.4]) m.box(`ladder_stile_${x}`, [x, -12, -15], [x + 0.6, 19, -14.4], 'yel', { split: 'y' });
    for (let y = -10; y <= 12; y += 2) m.box(`ladder_rung_${y}`, [27.6, y, -14.9], [29.4, y + 0.4, -14.5], 'st', { split: '' });
    m.box('ladder_brace', [27, 12, -14.4], [30, 12.6, -11], 'yel', { split: '' });

    // plates on the vessel: maker's plate above the deck, radiation sign under it
    m.decal('nameplate', [4, 20, -14.4], [12, 24, -14.1], 'st', 'north', 'nameplate', { hide: ['south'] });
    m.decal('trefoil', [5, 0, -14.4], [11, 6, -14.1], 'st', 'north', 'trefoil', { hide: ['south'] });

    // ionisation chamber housings for the out-of-core flux channels, conduit up to the deck
    for (const x of [-6, 18]) {
      m.decal(`chamber_${x}`, [x, -6, -13.6], [x + 4, -1, -10], 'gl', 'north', 'detector');
      m.decal(`glow_lamp_ok_${x}`, [x + 0.5, -2.2, -13.8], [x + 1.25, -1.45, -13.6], 'st', 'north', 'lamp_green',
        { hide: ['south'] });
      m.decal(`glow_lamp_hv_${x}`, [x + 2.75, -2.2, -13.8], [x + 3.5, -1.45, -13.6], 'st', 'north', 'lamp_amber',
        { hide: ['south'] });
      m.box(`conduit_${x}`, [x + 1.6, -1, -12.6], [x + 2.4, 11, -11.8], 'st', { split: '' });
    }

    // auxiliary sodium lines (purification circuit) on the east side, with valves, into the vessel near the top
    for (const z of [2, 12]) {
      m.box(`na_riser_${z}`, [30.2, -12, z], [31.8, 26, z + 1.6], 'na', { split: 'y' });
      m.box(`na_stub_${z}`, [28.4, 24.4, z], [30.2, 26, z + 1.6], 'na', { split: '' });
      m.box(`na_valve_${z}`, [29.6, 7, z - 0.6], [32, 10.5, z + 2.2], 'gv', { split: '' });
      m.box(`na_bonnet_${z}`, [30.2, 10.5, z + 0.1], [31.8, 12, z + 1.5], 'st', { split: '' });
      m.decal(`na_tag_${z}`, [30.4, 14, z - 0.1], [32, 15.5, z + 1.7], 'na', 'east', 'tag', { hide: ['west'] });
    }
    // argon cover-gas line on the west side, into the top flange
    m.box('ar_riser', [-15.4, -12, 14], [-14.2, 31, 15.2], 'ar', { split: 'y' });
    m.box('ar_stub', [-14.2, 29.6, 14], [-12.6, 30.8, 15.2], 'ar', { split: '' });
    m.box('ar_valve', [-16, 18, 13.4], [-13.8, 20.5, 15.8], 'gv', { split: '' });
    m.decal('ar_tag', [-16, 22, 13.6], [-15.4, 23.5, 15.6], 'ar', 'west', 'tag', { hide: ['east'] });
    return m;
  }

  // ------------------------------------------------------------------ plug segment
  const SMALL = [10, 4];
  function plug() {
    const m = mk();
    m.prismY('roof', C, 23.4, -16, -12, 'gv', { cap: 'sfr_reactor/roof' });
    m.prismY('plug_large', C, 13, -12, -10, 'gl', { cap: 'sfr_reactor/plug_large' });
    m.prismY('plug_small', SMALL, 7, -10, -8.5, 'gl', { cap: 'sfr_reactor/plug_small' });

    // control rod drives: the centre one and a ring of six on the small plug, a seismic support plate
    const drives = [[0, 0]].concat([0, 60, 120, 180, 240, 300].map((a) => [4 * Math.cos(a * Math.PI / 180), 4 * Math.sin(a * Math.PI / 180)]));
    drives.forEach(([dx, dz], i) => {
      const x = SMALL[0] + dx, z = SMALL[1] + dz;
      m.box(`crdm_tube_${i}`, [x - 0.6, -8.5, z - 0.6], [x + 0.6, 10, z + 0.6], 'st', { split: 'y' });
      m.box(`crdm_flange_${i}`, [x - 1.1, -8.5, z - 1.1], [x + 1.1, -7.6, z + 1.1], 'gv', { split: '' });
      m.box(`crdm_drive_${i}`, [x - 1.25, 10, z - 1.25], [x + 1.25, 14.8, z + 1.25], 'drive', { split: '' });
      m.box(`crdm_cap_${i}`, [x - 0.8, 14.8, z - 0.8], [x + 0.8, 15.6, z + 0.8], 'st', { split: '' });
    });
    m.box('crdm_support', [SMALL[0] - 6, 5, SMALL[1] - 5.5], [SMALL[0] + 6, 5.8, SMALL[1] + 5.5], 'gv', { split: '' });
    for (const [sx, sz] of [[-6, -5.5], [5.2, -5.5], [-6, 4.7], [5.2, 4.7]]) {
      m.box(`crdm_leg_${sx}_${sz}`, [SMALL[0] + sx, -8.5, SMALL[1] + sz], [SMALL[0] + sx + 0.8, 5, SMALL[1] + sz + 0.8], 'gv', { split: 'y' });
    }

    // six intermediate heat exchanger heads round the large plug, secondary sodium in and out
    for (let k = 0; k < 6; k++) {
      const a = (30 + k * 60) * Math.PI / 180, x = C[0] + 18 * Math.cos(a), z = C[1] + 18 * Math.sin(a);
      m.prismY(`ihx_${k}`, [x, z], 2.8, -12, -6, 'gv', { cap: 'sfr_reactor/ihx_top', seed: k * 3 });
      for (const s of [-1, 1]) {
        const px = x + s * 1.1;
        m.box(`ihx_pipe_${k}_${s}`, [px - 0.8, -6, z - 0.8], [px + 0.8, 6, z + 0.8], 'clad', { split: 'y' });
        m.box(`ihx_band_${k}_${s}`, [px - 0.9, 3, z - 0.9], [px + 0.9, 4.2, z + 0.9], s < 0 ? 'na' : 'gl', { split: '' });
        m.box(`ihx_flange_${k}_${s}`, [px - 1, 6, z - 1], [px + 1, 6.6, z + 1], 'st', { split: '' });
      }
    }

    // refuelling mechanism on the large plug: yellow column, hazard band, gearbox head with its motor
    m.box('refuel_column', [0.5, -10, 10.5], [3.5, 8, 13.5], 'yel', { split: 'y' });
    m.box('refuel_band', [0.3, -3, 10.3], [3.7, -1, 13.7], 'haz', { split: '' });
    m.box('refuel_head', [-0.5, 8, 9.5], [4.5, 12, 14.5], 'yel', { split: '' });
    m.box('refuel_motor', [4.5, 9, 10.8], [7.2, 11.6, 13.2], 'gv', { split: '' });
    m.box('refuel_motor_cap', [7.2, 9.4, 11.2], [7.8, 11.2, 12.8], 'st', { split: '' });
    m.decal('refuel_tag', [0.8, 4, 10.2], [3.2, 5.5, 10.5], 'yel', 'north', 'tag', { hide: ['south'] });
    return m;
  }

  const BUILDS = {
    sfr_reactor: { model: pool },
    sfr_reactor_plug: { model: plug },
  };

  window.SFR = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'core', id, BUILDS[id].java),
    exportAll: (root) => PCART.exportAll(root, 'core', BUILDS),
    preview: (root) => PCART.realize(root, pool().merge(plug(), 0, 48, 0, 'plug_'), 'sfr_reactor_preview'),
  };
})();
