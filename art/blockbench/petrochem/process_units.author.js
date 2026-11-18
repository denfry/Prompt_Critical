// Chemical process units of I3, hand-authored for Blockbench (replace the borrowed spec_petrochem.py /
// spec_oilgas.py / spec_mining.py models): the cracked gas separation unit, the EDC unit, the methanol converter
// and the ammonia converter.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/petrochem/process_units.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PRU.paint('<repo>')
//   build + save each model in turn (PRU.build / PRU.save, ids in PRU.ids)
//
// All front to the north.
// cracked_gas_unit 3x3x3 (x, y, z -16..32): the separation end of an ethylene plant as it stands behind the
//   furnaces. The water quench tower at the back west, the caustic wash tower at the back east, the demethaniser
//   and the C2 splitter (the tallest column) in the middle behind the cold box, and in front the cracked gas
//   compressor: three barrel casings in line on one shaft, driven from the east by the condensing steam turbine
//   on HP steam from the furnace TLEs. A pipe rack crosses at y 10. Effects: turbine gland steam at x 24, y -6,
//   z -10; quench tower vent at x -6, y 32, z 20.
// edc_unit 1x2x1 (y 0..32): the direct chlorination reactor (boiling EDC, steel, with its overhead condenser)
//   east and the fluidised-bed oxychlorinator (the taller vessel, with its cyclones at the top) west.
//   Vent at x 5, y 31, z 8.
// methanol_converter 1x2x1 (y 0..32), shaft in at the back: the Lurgi isothermal tubular converter with the
//   steam drum on its top and the recycle circulator at its foot. Circulator partial
//   (methanol_converter/partial/coupling): the coupling hub, spins about z through x 8, y 8 (the shaft line).
//   Drum safety valve at x 8, y 31, z 5.
// ammonia_converter 1x3x1 (y -16..32 about the middle block), shaft in at the back of the bottom cell: the
//   high-pressure converter, a thick forged shell with its closure head and the bolting ring, the cold-shot
//   line up the east side, and the syngas circulator at its foot. Circulator partial (ammonia_converter/partial/coupling), drawn about
//   the controller (the bottom cell, y 0..16 of its own): spins about z through x 8, y 8 (model y -8).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Aluminium jacketing over insulation, bands every 6 px and a lap seam (towers, cold box).
  T['process_units/cladding'] = () => {
    const im = img(32, 32), r = grain(im, [180, 184, 186], 3401, 3, 6, 8);
    for (let y = 0; y < 32; y += 6) for (let x = 0; x < 32; x++) { put(im, x, y, [130, 134, 136]); put(im, x, y + 1, [206, 210, 212]); }
    for (let y = 0; y < 32; y++) put(im, 21, y, [142, 146, 148]);
    return im;
  };
  // Cold box casing: painted steel panels with stiffeners, cold-grey.
  T['process_units/coldbox'] = () => {
    const im = img(32, 32), r = grain(im, [168, 176, 184], 3411, 3, 4, 8);
    for (let x = 0; x < 32; x += 16) for (let y = 0; y < 32; y++) { put(im, x, y, [110, 116, 124]); put(im, x + 1, y, [196, 204, 210]); }
    for (let y = 0; y < 32; y += 16) for (let x = 0; x < 32; x++) put(im, x, y, [120, 126, 134]);
    return im;
  };
  // Uninsulated carbon steel vessel in grey primer (reactors), weld seams.
  T['process_units/vessel'] = () => {
    const im = img(32, 32), r = grain(im, [122, 128, 132], 3421, 3, 5, 8);
    for (let x = 0; x < 32; x++) put(im, x, 15, [92, 96, 100]);
    for (let y = 0; y < 32; y++) put(im, 9, y, [96, 100, 104]);
    return im;
  };
  // Machine green of compressors and turbines (Soviet plant practice), oil streaks.
  T['process_units/machine'] = () => {
    const im = img(32, 32), r = grain(im, [70, 104, 84], 3431, 3, 5, 8);
    for (let k = 0; k < 4; k++) { const x = r() * 32; for (let y = 16; y < 32; y++) put(im, x, y, [52, 70, 58]); }
    return im;
  };
  // Forged high-pressure shell: dark, heavy, ring seams of the forgings every 8 px.
  T['process_units/forged'] = () => {
    const im = img(32, 32), r = grain(im, [76, 80, 84], 3441, 3, 5, 8);
    for (let y = 0; y < 32; y += 8) for (let x = 0; x < 32; x++) { put(im, x, y, [48, 50, 54]); put(im, x, y + 1, [104, 108, 112]); }
    return im;
  };
  // Pipe rack pipes in GOST 14202 colours: steam red, water green, gas yellow-ochre.
  T['process_units/pipe_steam'] = () => { const im = img(16, 16); grain(im, [150, 52, 40], 3451, 4, 4, 4); return im; };
  T['process_units/pipe_water'] = () => { const im = img(16, 16); grain(im, [60, 120, 70], 3461, 4, 4, 4); return im; };
  T['process_units/pipe_gas'] = () => { const im = img(16, 16); grain(im, [196, 160, 60], 3471, 4, 4, 4); return im; };
  // Radial cap of a tower or vessel head.
  T['process_units/cap'] = () => {
    const im = img(16, 16), r = rng(3481);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, add(d > 6.6 ? [150, 154, 158] : d > 2 ? [176, 180, 184] : [96, 100, 104], (r() - 0.5) * 8));
    }
    return im;
  };
  // Bolted closure of the ammonia converter seen from above: ring of stud nuts.
  T['process_units/closure'] = () => {
    const im = img(16, 16), r = rng(3491);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, add(d > 5.5 ? [70, 74, 78] : [96, 100, 106], (r() - 0.5) * 8));
    }
    for (let a = 0; a < 360; a += 30) { const t = a * Math.PI / 180; put(im, 8 + 6.6 * Math.cos(t), 8 + 6.6 * Math.sin(t), [190, 192, 196]); }
    return im;
  };
  // Coupling hub with its bolts, radial.
  T['process_units/hub'] = () => {
    const im = img(16, 16), r = rng(3501);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, add(d > 6.5 ? [60, 62, 64] : [140, 144, 148], (r() - 0.5) * 10));
    }
    for (const a of [0, 90, 180, 270]) { const t = a * Math.PI / 180; rect(im, 7 + 4.6 * Math.cos(t), 7 + 4.6 * Math.sin(t), 2, 2, [40, 40, 40]); }
    rect(im, 2, 7, 2, 2, [220, 190, 40]); // timing mark, makes the spin readable
    return im;
  };

  const MAT = {
    clad: { tex: 'process_units/cladding', P: 16 }, cbx: { tex: 'process_units/coldbox', P: 16 }, ves: { tex: 'process_units/vessel', P: 16 },
    mach: { tex: 'process_units/machine', P: 16 }, frg: { tex: 'process_units/forged', P: 16 },
    ps: { tex: 'process_units/pipe_steam', P: 16 }, pw: { tex: 'process_units/pipe_water', P: 16 }, pg: { tex: 'process_units/pipe_gas', P: 16 },
    con: { tex: 'candu_reactor/concrete', P: 32 }, yel: { tex: 'candu_reactor/yellow', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'process_units/cladding' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Upright cylinder of any height: prismY in block-high courses, each course's side texture taken from the
  // matching rows of `tex` so the bands run on; the top course carries the radial cap.
  function tower(m, name, c, r, y0, y1, mat, tex, cap) {
    let k = 0;
    for (let b = Math.floor(y0 / 16); b * 16 < y1; b++) {
      const lo = Math.max(y0, b * 16), hi = Math.min(y1, b * 16 + 16);
      if (hi <= lo) continue;
      const n0 = m.els.length, top = hi === y1;
      m.prismY(`${name}_c${k++}`, c, r, lo, hi, mat, { tex, seed: k * 3, cap: top ? cap : undefined });
      for (const e of m.els.slice(n0)) for (const d of ['north', 'south', 'east', 'west']) {
        const f = e.face[d];
        if (f && f.uv) e.face[d] = { tex: f.tex, uv: [f.uv[0], f.uv[1] + 16 * b, f.uv[2], f.uv[3] + 16 * b] };
      }
    }
  }
  // Circular platform round a tower: a square grating with a toe board, and a ladder cage up the north side.
  function platform(m, name, c, r, y) {
    m.box(`${name}_grating`, [c[0] - r - 1.5, y, c[1] - r - 1.5], [c[0] + r + 1.5, y + 0.5, c[1] + r + 1.5], 'chequer', { split: 'xz' });
    m.box(`${name}_rail`, [c[0] - r - 1.5, y + 2.4, c[1] - r - 1.5], [c[0] + r + 1.5, y + 2.9, c[1] - r - 1], 'yel', { split: '' });
  }

  // ----------------------------------------------------------------- cracked gas unit
  function crackedGas() {
    const m = mk();
    m.box('pad', [-16, -16, -16], [32, -15, 32], 'con');
    // quench tower, back west
    tower(m, 'quench', [-6, 20], 7, -15, 31, 'clad', 'process_units/cladding', 'process_units/cap');
    platform(m, 'quench_pf1', [-6, 20], 7, 8);
    platform(m, 'quench_pf2', [-6, 20], 7, 24);
    m.box('quench_vent', [-6.6, 31, 19.4], [-5.4, 32, 20.6], 'st', { split: '' });
    // caustic wash tower, back east
    tower(m, 'caustic', [24, 22], 4.5, -15, 22, 'clad', 'process_units/cladding', 'process_units/cap');
    platform(m, 'caustic_pf', [24, 22], 4.5, 12);
    // demethaniser and C2 splitter behind the cold box
    tower(m, 'demethaniser', [6, 26], 3, -15, 26, 'clad', 'process_units/cladding', 'process_units/cap');
    tower(m, 'c2_splitter', [14, 26], 3.4, -15, 31.9, 'clad', 'process_units/cladding', 'process_units/cap');
    // cold box (plate-fin exchangers in perlite) in the middle
    m.box('cold_box', [2, -15, 6], [18, 12, 18], 'cbx');
    m.box('cold_box_roof', [1.6, 12, 5.6], [18.4, 12.6, 18.4], 'st', { split: '' });
    // compressor train in front: three barrel casings on a common bedplate, turbine at the east end
    m.box('bedplate', [-14, -15, -14], [30, -13, -4], 'ci');
    for (const [k, x0, x1, r] of [[0, -13, -4, 3.6], [1, -3, 5, 3.2], [2, 6, 13, 2.8]]) {
      m.prism(`casing_${k}`, 'x', [-9, -9], r, x0, x1, 'mach', { ends: ['east', 'west'] });
      m.box(`suction_${k}`, [(x0 + x1) / 2 - 1, -9 + r - 0.5, -10], [(x0 + x1) / 2 + 1, 11, -8], 'pg', { split: 'y' });
    }
    for (const [x0, x1] of [[-4, -3], [5, 6], [13, 15]]) m.box(`coupling_guard_${x0}`, [x0, -11, -11], [x1, -7, -7], 'yel', { split: '' });
    // condensing steam turbine
    m.box('turbine_low', [15, -13, -14], [27, -9, -4], 'mach');
    m.prism('turbine', 'x', [-7, -9], 4.2, 15, 25, 'mach', { ends: ['west', 'east'] });
    m.box('turbine_steam_chest', [17, -3, -12], [22, 0, -6], 'mach', { split: '' });
    m.box('hp_steam', [18.8, 0, -9.8], [20.2, 12, -8.2], 'ps', { split: 'y' });
    m.box('condenser', [25, -15, -14], [31, -6, -4], 'ves');
    // pipe rack across the unit at y 10..12 and its bents
    for (const x of [-15, 0, 15, 30]) for (const z of [0, 4]) m.box(`bent_${x}_${z}`, [Math.min(x, 30), -15, z], [Math.min(x, 30) + 1, 10, z + 1], 'st', { split: 'y' });
    m.box('rack_beam_n', [-16, 10, 0], [32, 11, 1], 'st', { split: 'x' });
    m.box('rack_beam_s', [-16, 10, 4], [32, 11, 5], 'st', { split: 'x' });
    for (const [k, z, mat] of [[0, 1.6, 'ps'], [1, 2.8, 'pw'], [2, 4, 'pg']]) m.prism(`rack_pipe_${k}`, 'x', [11.6, z], 0.55, -16, 32, mat);
    // the cracked gas line from the quench tower top over to the first stage
    m.box('cg_line_down', [-1.2, 11, 18], [0.6, 28, 19.8], 'pg', { split: 'y' });
    m.box('cg_line_over', [-1.2, 28, 18], [0.6, 29.8, 13], 'pg', { split: '' });
    return m;
  }

  // ----------------------------------------------------------------- EDC unit
  function edc() {
    const m = mk('process_units/vessel');
    m.box('pad', [0, 0, 0], [16, 1, 16], 'con');
    // oxychlorinator: fluid bed vessel, west, with the cyclone diplegs inside the top and the outlet on its head
    tower(m, 'oxy', [5, 9], 4.4, 1, 28, 'ves', 'process_units/vessel', 'process_units/cap');
    m.box('oxy_outlet', [4.4, 28, 8.4], [5.6, 31, 9.6], 'st', { split: '' });
    m.box('oxy_air_inlet', [4, 2, 2], [6, 4, 4.6], 'pg', { split: '' });
    platform(m, 'oxy_pf', [5, 9], 4.4, 20);
    // direct chlorination reactor, east, with its overhead condenser across the top and the reflux drop
    tower(m, 'dc', [12, 10], 3.2, 1, 18, 'ves', 'process_units/vessel', 'process_units/cap');
    m.prism('dc_condenser', 'z', [12, 22], 1.8, 4, 15, 'ves', { ends: ['north', 'south'] });
    for (const z of [5, 13]) m.box(`dc_saddle_${z}`, [10.6, 18, z], [13.4, 20.4, z + 1], 'st', { split: '' });
    m.box('dc_vapour', [11.4, 18, 9.4], [12.6, 20.4, 10.6], 'st', { split: '' });
    m.box('chlorine_inlet', [14.6, 3, 9.4], [16, 4.2, 10.6], 'pg', { split: '' });
    m.box('edc_rundown', [11.4, 1, 6], [12.6, 2.2, 7.2], 'st', { split: '' });
    // ladder up the front of the oxychlorinator
    for (let y = 2; y < 20; y += 2) m.box(`rung_${y}`, [3.4, y, 3.8], [6.6, y + 0.4, 4.2], 'st', { split: '' });
    for (const x of [3, 6.6]) m.box(`stile_${x}`, [x, 1, 3.6], [x + 0.4, 20, 4.4], 'st', { split: '' });
    return m;
  }

  // ----------------------------------------------------------------- methanol converter
  function methanol() {
    const m = mk('process_units/vessel');
    m.box('pad', [0, 0, 0], [16, 1, 16], 'con');
    // circulator casing at the foot, back, on the shaft line
    m.box('circ_base', [4, 1, 10], [12, 5.4, 16], 'ci');
    m.prism('circ_casing', 'z', [8, 8], 2.6, 10, 15.4, 'mach', { ends: ['north'] });
    m.box('circ_discharge', [9.6, 10.4, 11], [11, 13, 12.4], 'pg', { split: '' });
    // converter shell (boiling water around the catalyst tubes) and its heads
    tower(m, 'shell', [8, 6], 4.2, 2, 24, 'ves', 'process_units/vessel', 'process_units/cap');
    m.box('outlet', [7.4, 1, 1], [8.6, 3, 3], 'pg', { split: '' });
    // steam drum across the top on two risers
    for (const z of [3, 9]) m.box(`riser_${z}`, [7.2, 24, z], [8.8, 26, z + 1.6], 'ps', { split: '' });
    m.prism('steam_drum', 'z', [8, 27.6], 2.6, 0.6, 12.4, 'ves', { ends: ['north', 'south'] });
    m.box('safety_valve', [7.5, 30, 4.5], [8.5, 31.6, 5.5], 'red', { split: '' });
    m.box('level_glass', [10.4, 26, 6], [11, 29.4, 6.6], 'bright', { split: '' });
    // downcomer back to the shell bottom
    m.box('downcomer', [12, 4, 11], [13.2, 26, 12.2], 'pw', { split: 'y' });
    m.box('downcomer_top', [8.8, 25, 11], [13.2, 26.2, 12.2], 'pw', { split: '' });
    return m;
  }
  function coupling(cy) {
    const m = mk('process_units/machine');
    m.prism('shaft', 'z', [8, cy], 0.8, 13, 16, 'bright');
    m.disc('hub', 'z', [8, cy], 2.2, 15.4, 15.8, 'st', 'process_units/hub', 'south');
    return m;
  }

  // ----------------------------------------------------------------- ammonia converter
  function ammonia() {
    const m = mk('process_units/forged');
    m.box('pad', [0, -16, 0], [16, -15, 16], 'con');
    // syngas circulator at the foot, back, on the shaft line (the shaft enters the back of the bottom cell)
    m.box('circ_base', [4, -15, 11], [12, -10.6, 16], 'ci');
    m.prism('circ_casing', 'z', [8, -8], 2.6, 12.2, 15.4, 'mach', { ends: ['north'] });
    m.box('circ_out', [2.4, -8.6, 9], [5, -7.4, 12.2], 'pg', { split: '' });
    // the converter: skirt, forged shell, the bolting ring and closure head on top
    m.box('skirt', [3, -15, 2], [13, -11, 12], 'ci');
    tower(m, 'shell', [8, 7], 5, -11, 26, 'frg', 'process_units/forged');
    m.prismY('bolting_ring', [8, 7], 5.8, 26, 28, 'frg');
    m.prismY('closure', [8, 7], 4.4, 28, 30, 'frg', { cap: 'process_units/closure' });
    m.box('thermowell_nozzle', [7.4, 30, 6.4], [8.6, 32, 7.6], 'st', { split: '' });
    // gas in at the bottom front, the hot outlet to the waste heat boiler on the east, the cold-shot line
    m.box('inlet', [7.2, -13, 0], [8.8, -11.4, 2], 'pg', { split: '' });
    m.box('outlet_hot', [13, -6, 6.2], [16, -4.4, 7.8], 'ps', { split: '' });
    m.box('quench_line', [13.4, -4.4, 9.4], [14.6, 20, 10.6], 'pg', { split: 'y' });
    m.box('quench_in', [12.6, 18.8, 9.4], [13.4, 20, 10.6], 'pg', { split: '' });
    // service platform at the closure and the ladder up the front
    m.box('platform', [0, 25, 0], [16, 25.5, 16], 'chequer', { split: 'xz' });
    m.box('platform_rail', [0, 27.4, 0], [16, 27.9, 0.6], 'yel', { split: '' });
    for (let y = -10; y < 25; y += 2) m.box(`rung_${y}`, [6, y, 1.2], [10, y + 0.4, 1.6], 'st', { split: '' });
    for (const x of [5.6, 10]) m.box(`stile_${x}`, [x, -11, 1], [x + 0.4, 25, 1.8], 'st', { split: '' });
    return m;
  }

  const BUILDS = {
    cracked_gas_unit: { model: crackedGas },
    edc_unit: { model: edc },
    methanol_converter: { model: methanol },
    'methanol_converter/coupling': { model: () => coupling(8), java: 'methanol_converter/partial/coupling' },
    ammonia_converter: { model: ammonia },
    'ammonia_converter/coupling': { model: () => coupling(8), java: 'ammonia_converter/partial/coupling' },
  };
  const CAT = { cracked_gas_unit: 'petrochem', edc_unit: 'chlor_alkali', methanol_converter: 'petrochem', ammonia_converter: 'reagents' };

  window.PRU = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, CAT[id.split('/')[0]], id, BUILDS[id].java),
    preview(root, id) { return PCART.realize(root, BUILDS[id].model(), id + '_preview'); },
  };
})();
