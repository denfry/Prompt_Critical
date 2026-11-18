// Double-ended ranging-drum longwall shearer, hand-authored for Blockbench (replaces the spec_visual.py
// placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/longwall_shearer.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); LWS.paint('<repo>')
//   await LWS.exportAll('<repo>')
//
// 3x1x1 blocks along the face, model x -16..32, y 0..16, z 0..16; north (z 0) looks at the coal, south is the
// goaf side where the operators walk along the supports. The machine rides the armoured face conveyor: guide
// shoes on the face side, trapping shoes and the haulage sprocket housings on the goaf side, where the rack
// runs. The body is three bolted sections (two haulage end sections and the centre electrical section under a
// flameproof cover). A ranging arm at each end carries its drum on an axis along x through y 16, z 8 (the drum
// partials, which ShearerRenderer spins about that axis). Each drum is a three-start helix of vanes with
// conical picks on the vane tips and a denser ring of picks on the face end ring.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/longwall_shearer';
  const T = {};
  const AY = 16, AZ = 8;   // drum axis

  // Machine paint, orange enamel under coal dust: dust settles from the top and runs down in streaks, the
  // lower part of every tile is darker, and the paint is scuffed to grey here and there.
  T.paint = () => {
    const im = img(32, 32), r = grain(im, [198, 106, 36], 1101, 3, 6, 8);
    const streak = []; for (let x = 0; x < 32; x++) streak.push(r() < 0.3 ? 0.12 + r() * 0.12 : r() * 0.05);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4, c = [im.d[i], im.d[i + 1], im.d[i + 2]];
      let out = mix(c, [40, 36, 32], 0.14 + streak[x] + Math.max(0, (y - 18) / 14) * 0.22);
      if (r() < 0.012) out = [120, 112, 100];
      put(im, x, y, out);
    }
    return im;
  };
  // Underframe and shoes, bare steel black with coal and polished where it slides on the pans.
  T.dark = () => {
    const im = img(32, 32), r = grain(im, [44, 44, 46], 1111, 5, 6, 8);
    for (let x = 0; x < 32; x++) if (r() < 0.25) put(im, x, 31, [104, 106, 108]);
    for (let k = 0; k < 14; k++) { const x = Math.floor(r() * 32), y = Math.floor(r() * 32); put(im, x, y, [22, 21, 20]); }
    return im;
  };
  // Hazard bands on the ends: yellow and black at 45 degrees, chipped.
  T.hazard = () => {
    const im = img(32, 32), r = rng(1121);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const band = Math.floor((x + y) / 6) % 2 === 0;
      let c = band ? [214, 176, 34] : [30, 28, 26];
      if (r() < 0.04) c = [88, 86, 84];
      put(im, x, y, add(mix(c, [36, 34, 32], 0.2), (r() - 0.5) * 10));
    }
    return im;
  };
  // Drum steel: hardfaced vanes worn bright on the outer edges, the rest packed with coal fines.
  T.drum = () => {
    const im = img(32, 32), r = grain(im, [88, 88, 90], 1131, 5, 6, 8);
    for (let k = 0; k < 40; k++) {
      const x = Math.floor(r() * 32), y = Math.floor(r() * 32), l = 2 + Math.floor(r() * 5);
      for (let j = 0; j < l; j++) put(im, x + j, y, [132, 132, 134]);
    }
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) if (r() < 0.06) put(im, x, y, [34, 33, 32]);
    return im;
  };
  // Rubber: armoured trailing cable (black) and spray water hose (blue).
  T.cable = () => { const im = img(32, 32); grain(im, [28, 28, 30], 1141, 3, 4, 8); for (let x = 0; x < 32; x += 4) rect(im, x, 0, 1, 32, [40, 40, 42]); return im; };
  T.hose = () => { const im = img(32, 32); grain(im, [36, 70, 140], 1151, 4, 6, 8); for (let x = 0; x < 32; x += 8) rect(im, x, 0, 1, 32, [24, 46, 96]); return im; };
  // Operator's panel on the goaf side: a text display, run/stop buttons, haulage speed and direction switches,
  // arm raise/lower buttons, a key switch. Painted plate 32x32 shown on one face.
  T.panel = () => {
    const im = img(32, 32), r = rng(1161);
    grain(im, [58, 60, 62], 1162, 3, 3, 8);
    for (let i = 0; i < 32; i++) { put(im, i, 0, [96, 98, 100]); put(im, 0, i, [96, 98, 100]); put(im, i, 31, [28, 28, 30]); put(im, 31, i, [28, 28, 30]); }
    rect(im, 3, 3, 26, 9, [14, 22, 16]);                                    // display
    for (let row = 0; row < 3; row++) for (let x = 5; x < 27; x++) if (r() < 0.55) put(im, x, 5 + row * 2, [96, 214, 112]);
    const button = (x, y, c) => { rect(im, x, y, 4, 4, c); put(im, x, y, add(c, 60)); put(im, x + 3, y + 3, add(c, -50)); rect(im, x - 1, y + 5, 6, 1, [200, 200, 196]); };
    button(4, 15, [40, 150, 56]);   // run
    button(11, 15, [180, 34, 28]);  // stop
    button(18, 15, [200, 196, 188]); // arm raise
    button(24, 15, [200, 196, 188]); // arm lower
    rect(im, 4, 24, 8, 4, [26, 26, 28]); rect(im, 7, 23, 2, 6, [180, 180, 176]);  // haulage direction lever
    for (let a = 0; a < 360; a += 4) {                                      // haulage speed knob
      const t = a * Math.PI / 180; put(im, 19 + 3 * Math.cos(t), 26 + 3 * Math.sin(t), [20, 20, 22]);
    }
    rect(im, 18, 25, 3, 3, [30, 30, 32]); rect(im, 19, 23, 1, 3, [220, 220, 216]);
    rect(im, 25, 24, 4, 4, [170, 140, 60]); put(im, 26, 25, [40, 36, 30]); put(im, 27, 26, [40, 36, 30]); // key switch
    return im;
  };
  // Headlight lens, warm white with a hot centre (drawn full bright; the element glows).
  T.lamp = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      put(im, x, y, d > 0.9 ? [80, 78, 72] : mix([255, 250, 226], [236, 206, 140], d));
    }
    return im;
  };

  const MAT = {
    pt: { tex: 'longwall_shearer/paint', P: 16 }, dk: { tex: 'longwall_shearer/dark', P: 16 },
    hz: { tex: 'longwall_shearer/hazard', P: 16 }, dr: { tex: 'longwall_shearer/drum', P: 16 },
    cb: { tex: 'longwall_shearer/cable', P: 16 }, hs: { tex: 'longwall_shearer/hose', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'longwall_shearer/paint' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // A radial bar about the drum axis at angle a (degrees, a multiple of 22.5; 0 = up, 90 = toward +z), from
  // radius r0 to r1, thickness w across, x0..x1 along. Built as an axis-aligned bar in the nearest quarter and
  // turned about x by the rest, which stays inside the -45..45 Java model range.
  function radial(m, name, x0, x1, r0, r1, w, a, mat) {
    const q = Math.round(a / 90), rest = a - q * 90, dir = ((q % 4) + 4) % 4;
    let f, t;
    if (dir === 0) { f = [x0, AY + r0, AZ - w / 2]; t = [x1, AY + r1, AZ + w / 2]; }
    else if (dir === 1) { f = [x0, AY - w / 2, AZ + r0]; t = [x1, AY + w / 2, AZ + r1]; }
    else if (dir === 2) { f = [x0, AY - r1, AZ - w / 2]; t = [x1, AY - r0, AZ + w / 2]; }
    else { f = [x0, AY - w / 2, AZ - r1]; t = [x1, AY + w / 2, AZ - r0]; }
    m.box(name, f, t, mat, { split: '', rot: rest ? { axis: 'x', angle: rest, origin: [8, AY, AZ] } : undefined });
  }

  // One drum between x0 and x1; `outer` is the end facing out along the face ('west' or 'east'), which carries
  // the end ring. Three vane starts 120 degrees apart (to the nearest 22.5) wind a quarter turn over the width.
  function drum(x0, x1, outer) {
    const m = mk('longwall_shearer/drum');
    const west = outer === 'west', inner = west ? 'east' : 'west';
    const ringA = west ? x0 : x1 - 1, ringB = west ? x0 + 1 : x1;
    const plateA = west ? x1 - 0.8 : x0, plateB = west ? x1 : x0 + 0.8;
    m.prism('end_ring', 'x', [AY, AZ], 7.2, ringA, ringB, 'dr', { split: '', ends: [outer, inner] });
    m.prism('back_plate', 'x', [AY, AZ], 6.4, plateA, plateB, 'dr', { split: '', ends: [inner] });
    m.prism('barrel', 'x', [AY, AZ], 4.6, Math.min(ringB, plateB), Math.max(ringA, plateA), 'dr', { split: '' });
    const v0 = west ? ringB : plateB, v1 = west ? plateA : ringA, n = 5, step = (v1 - v0) / n;
    const hand = west ? 1 : -1;
    for (const s of [0, 112.5, 247.5]) {
      for (let k = 0; k < n; k++) {
        const xa = v0 + k * step, xb = xa + step, a = s + hand * k * 22.5;
        radial(m, `vane_${s}_${k}`, xa, xb, 4.5, 6.8, 0.9, a, 'dr');
        const xm = (xa + xb) / 2;
        radial(m, `holder_${s}_${k}`, xm - 0.45, xm + 0.45, 6.7, 7.5, 1.1, a, 'st');
        radial(m, `pick_${s}_${k}`, xm - 0.22, xm + 0.22, 7.5, 8.5, 0.5, a, 'bright');
      }
    }
    // end ring picks, eight round the ring, splayed out of the ring alternately
    for (let k = 0; k < 8; k++) {
      const a = k * 45 + 22.5, xm = (ringA + ringB) / 2;
      radial(m, `ring_holder_${k}`, xm - 0.45, xm + 0.45, 7.1, 7.8, 1.1, a, 'st');
      radial(m, `ring_pick_${k}`, xm - 0.22, xm + 0.22, 7.8, 8.6, 0.5, a, 'bright');
    }
    return m;
  }

  // Ranging arm at one end, mirrored about x 8: hub at the drum, gear case sloping down at 45 degrees to the
  // pivot on the end section, and the ranging ram that holds it.
  function arm(m, side) {
    const X = (x) => (side === 'w' ? x : 16 - x);
    const lo = (a, b) => Math.min(X(a), X(b)), hi = (a, b) => Math.max(X(a), X(b));
    m.prism(`arm_${side}_hub`, 'x', [AY, AZ], 3.6, lo(-9.1, -7), hi(-9.1, -7), 'pt', { split: '', ends: [side === 'w' ? 'west' : 'east'] });
    m.box(`arm_${side}_case`, [lo(-10.75, 0.75), 10.5, 4.6], [hi(-10.75, 0.75), 15.5, 11.4], 'pt',
      { rot: { axis: 'z', angle: side === 'w' ? -45 : 45, origin: [X(-5), 13, 8] } });
    m.box(`arm_${side}_cover`, [lo(-9, -1), 12.4, 11.4], [hi(-9, -1), 14, 11.8], 'dk',
      { rot: { axis: 'z', angle: side === 'w' ? -45 : 45, origin: [X(-5), 13, 8] } });
    m.prism(`arm_${side}_pivot`, 'z', [X(-2), 10], 2.2, 3.4, 12.6, 'st', { split: '', ends: ['north', 'south'] });
    m.box(`arm_${side}_ram`, [lo(-7.6, -6.2), 3, 3], [hi(-7.6, -6.2), 8.2, 4.4], 'st', { split: '' });
    m.box(`arm_${side}_ram_rod`, [lo(-7.3, -6.5), 8.2, 3.3], [hi(-7.3, -6.5), 11.4, 4.1], 'bright', { split: '' });
    m.box(`arm_${side}_hose`, [lo(-4.6, -3.8), 9, 11.8], [hi(-4.6, -3.8), 12.8, 12.6], 'hs', { split: '' });
  }

  function body() {
    const m = mk();
    // underframe, face side guide shoes on the pans, goaf side trapping shoes on the rack
    m.box('underframe', [-9, 0, 1], [25, 1.5, 15], 'dk', { split: 'x' });
    for (const [a, b] of [[-8, -3], [19, 24]]) {
      m.box(`guide_shoe_${a}`, [a, 0, 0.3], [b, 2.4, 2], 'dk', { split: '' });
      m.box(`trap_shoe_${a}`, [a + 1, 0, 15.6], [b - 1, 1.6, 16], 'dk', { split: '' });
    }
    // three bolted sections; the ends get hazard bands on the outer faces
    m.box('section_w', [-9, 1.5, 2], [-1, 9, 14], 'pt', { face: { west: 'hz' } });
    m.box('section_c', [-0.6, 1.5, 2], [16.6, 10, 14], 'pt');
    m.box('section_e', [17, 1.5, 2], [25, 9, 14], 'pt', { face: { east: 'hz' } });
    for (const x of [-1, 16.6]) {
      m.box(`joint_${x}`, [x, 1.5, 1.6], [x + 0.4, 9.6, 14.4], 'pt', { split: '' });
      for (const y of [3, 5.5, 8]) for (const z of [1.2, 14.4]) {
        m.box(`joint_bolt_${x}_${y}_${z}`, [x - 0.2, y, z], [x + 0.6, y + 0.8, z + 0.4], 'st', { split: '' });
      }
    }
    // haulage units: sprocket housings on the goaf side, hydraulic motor caps
    for (const [a, b] of [[-7, -1.4], [17.4, 23]]) {
      m.box(`haulage_${a}`, [a, 0.4, 14], [b, 5.6, 15.6], 'pt', { split: '' });
      m.prism(`sprocket_cap_${a}`, 'z', [(a + b) / 2, 3], 1.4, 15.6, 16, 'dk', { split: '', ends: ['south'] });
    }
    // end section top covers and the flameproof enclosure on the centre section with its bolted lid
    for (const [a, b] of [[-8.4, -1.8], [17.8, 24.4]]) m.box(`top_cover_${a}`, [a, 9, 3], [b, 9.8, 13], 'pt', { split: '' });
    m.box('ex_box', [1, 10, 3], [15, 12.4, 13], 'pt');
    m.box('ex_lid', [1.4, 12.4, 3.4], [14.6, 13, 12.6], 'pt', { split: '' });
    for (let x = 2; x <= 14; x += 2) for (const z of [3.6, 11.8]) m.box(`lid_bolt_${x}_${z}`, [x - 0.3, 13, z], [x + 0.3, 13.3, z + 0.6], 'st', { split: '' });
    for (const x of [1.6, 13.8]) for (const z of [5.5, 8, 10.5]) m.box(`lid_bolt_e_${x}_${z}`, [x, 13, z - 0.3], [x + 0.6, 13.3, z + 0.3], 'st', { split: '' });
    // machine-mounted methane monitor on the lid, its sensor head toward the face
    m.box('ch4_monitor', [11, 13, 4], [13.6, 15, 6], 'red', { split: '' });
    m.box('ch4_head', [11.6, 13.4, 3.4], [13, 14.6, 4], 'st', { split: '' });
    // operator's panel and emergency stop on the goaf side of the centre section
    m.box('panel_box', [2.6, 3, 14], [13.4, 9, 15], 'pt', { split: '' });
    m.plate('panel_face', [2.9, 3.3, 15], [13.1, 8.7, 15.05], 'pt', 'south', 'longwall_shearer/panel', { hide: ['north'] });
    m.box('estop_base', [13.8, 6, 14], [15.4, 7.6, 14.8], 'dk', { split: '' });
    m.box('estop', [14, 6.2, 14.8], [15.2, 7.4, 15.4], 'red', { split: '' });
    // trailing cable from the clamp on the goaf side down to the cable handler, water hose to both arms
    m.box('cable_clamp', [-1.4, 10, 13], [2.4, 11.4, 15.2], 'dk', { split: '' });
    m.box('cable_down', [-0.1, 1.6, 15.2], [1.3, 11.2, 16], 'cb', { split: '' });
    m.box('hose_run', [-4.6, 9.8, 12.6], [20.6, 10.6, 13.4], 'hs', { split: 'x' });
    // headlights at both ends, under the arms, looking along the face
    for (const [a, b, d] of [[-9.6, -9, 'west'], [25, 25.6, 'east']]) {
      m.box(`lamp_body_${d}`, [a, 4.6, 9.4], [b, 7.8, 12.6], 'st', { split: '', hide: [d] });
      m.plate(`glow_lamp_${d}`, [d === 'west' ? a - 0.05 : b, 5, 9.8], [d === 'west' ? a : b + 0.05, 7.4, 12.2], 'st', d,
        'longwall_shearer/lamp', { hide: DIRS_BUT(d) });
    }
    arm(m, 'w');
    arm(m, 'e');
    return m;
  }
  function DIRS_BUT(d) { return ['north', 'south', 'east', 'west', 'up', 'down'].filter((x) => x !== d); }

  const BUILDS = {
    longwall_shearer: { model: body },
    'longwall_shearer/drum_a': { model: () => drum(-15.9, -9.1, 'west'), java: 'longwall_shearer/partial/drum_a' },
    'longwall_shearer/drum_b': { model: () => drum(25.1, 31.9, 'east'), java: 'longwall_shearer/partial/drum_b' },
  };

  window.LWS = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, body().merge(drum(-15.9, -9.1, 'west'), 0, 0, 0, 'da_').merge(drum(25.1, 31.9, 'east'), 0, 0, 0, 'db_'),
        'longwall_shearer_preview');
    },
  };
})();
