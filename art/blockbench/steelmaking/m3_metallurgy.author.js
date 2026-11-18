// M3 metallurgy not yet in the mod, hand-authored (replaces the spec_metallurgy*.py placeholders): EAF transformer
// and electrode mast, continuous caster (tundish, mould, strand rolls), induction furnace, permanent mould, wire
// drawing bench and rope stranding machine, aluminium and copper-nickel lines (precipitator, digester, refining
// cell, Peirce-Smith converter, reverberatory furnace, reduction pot), dense-medium drum, flotation cell, CDQ shaft.
// Run headless: node art/tools/pcart_headless.js <repo> steelmaking/m3_metallurgy.author.js MET3 --paint
//
// Front to the north. Partial pivots as the placeholders and GDD rows: caster mould oscillates in y at x/z 3..13;
// strand rolls along x at y 8, z 2.5 and 13.5; tundish stopper at (16, 16) from y 18; mast electrode at (8, -12);
// induction crucible tilts at x/z 3..13 from y 8; mould half on its hinge at z 0.5..8; stranding cradle about x
// through (y 10, z 8); drawing drum about y through (24, 8); precipitator agitator about y through (16, 16);
// converter barrel about x through (y 16, z 16); DMS drum about x through (y 16, z 16); flotation impeller about
// y through (8, 8); reduction pot anode frame moves in y.
(function () {
  const { rng, img, put, get, add, mix, rect, grain, vnoise } = PCART;
  const { P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder, insulatorY } = FUTK;
  const D = 'm3met/', T = {};

  T.tank7033 = FUTK.enamel([112, 120, 104], 32101, 2);
  T.riveted = () => {
    const im = img(32, 32), r = grain(im, [84, 86, 88], 32111, 4, 6, 8);
    for (const y of [1, 17]) for (let x = 1; x < 32; x += 4) PCART.rivet(im, x, y, [84, 86, 88]);
    for (let x = 0; x < 32; x++) { put(im, x, 15, add(get(im, x, 15), -20)); put(im, x, 31, add(get(im, x, 31), -20)); }
    return im;
  };
  T.crust = () => { const im = img(32, 32), r = grain(im, [150, 148, 142], 32121, 8, 10, 8); for (let k = 0; k < 6; k++) { let x = r() * 32, y = r() * 32; for (let i = 0; i < 12; i++) { put(im, x, y, [70, 68, 66]); x += r() - 0.3; y += r() - 0.5; } } return im; };
  T.frozen = () => { const im = img(32, 32); grain(im, [222, 224, 226], 32131, 4, 6, 8); return im; };
  T.cu_el = () => { const im = img(16, 16), r = rng(32141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([40, 120, 170], Math.sin(x * 0.8 + y * 0.3) * 8 + (r() - 0.5) * 8)); return im; };
  T.ni_el = () => { const im = img(16, 16), r = rng(32142); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([60, 150, 96], Math.sin(x * 0.8 + y * 0.3) * 8 + (r() - 0.5) * 8)); return im; };
  T.anode = () => { const im = img(16, 16); grain(im, [126, 76, 50], 32151, 6, 8, 4); return im; };
  T.cathode = () => { const im = img(16, 16), r = rng(32152); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([196, 118, 80], (r() - 0.5) * 14 + (y % 5 === 0 ? 10 : 0))); return im; };
  T.bag = () => { const im = img(16, 16), r = rng(32153); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([206, 200, 182], ((x + y) % 2 ? 6 : -6) + (r() - 0.5) * 8)); return im; };
  T.wire = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 152, 150], ((x + y) % 4 < 2 ? 18 : -22))); return im; };
  T.froth = () => {
    const im = img(16, 16), r = rng(32161);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 146, 128], (r() - 0.5) * 20));
    for (let k = 0; k < 26; k++) { const x = r() * 15, y = r() * 15; put(im, x, y, [236, 234, 220]); put(im, x + 1, y + 1, [96, 92, 80]); }
    return im;
  };
  T.pulp = () => { const im = img(16, 16); grain(im, [70, 70, 66], 32171, 4, 6, 4); return im; };
  T.medium = () => { const im = img(16, 16), r = rng(32181); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([62, 56, 50], Math.sin(x * 0.9 + y * 0.4) * 6 + (r() - 0.5) * 8)); return im; };
  T.liquor = () => { const im = img(16, 16), r = rng(32191); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([170, 120, 90], Math.sin(x * 0.7) * 6 + (r() - 0.5) * 8)); return im; };

  const MATX = {
    t33: { tex: D + 'tank7033', P: 16 }, riv: { tex: D + 'riveted', P: 16 }, crust: { tex: D + 'crust', P: 16 }, frozen: { tex: D + 'frozen', P: 16 },
    cuel: { tex: D + 'cu_el', P: 16 }, niel: { tex: D + 'ni_el', P: 16 }, anode: { tex: D + 'anode', P: 16 }, cath: { tex: D + 'cathode', P: 16 },
    bag: { tex: D + 'bag', P: 16 }, wire: { tex: D + 'wire', P: 8 }, froth: { tex: D + 'froth', P: 16 }, pulp: { tex: D + 'pulp', P: 16 },
    medium: { tex: D + 'medium', P: 16 }, liquor: { tex: D + 'liquor', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);

  // ---------------------------------------------------------------- EAF
  // Furnace transformer 2x2x2: tank with radiators on the east, the secondary bus bars out of the west wall,
  // three HV bushings and the conservator on the cover, the OLTC drive.
  function eafTransformer() {
    const m = mk(D + 'tank7033');
    for (const z of [5, 25]) P(m, 'rail' + z, [1, 0, z], [27, 1.4, z + 2], 'frame');
    P(m, 'tank', [3, 1.4, 4], [24, 21, 28], 't33');
    for (let z = 6; z < 27; z += 5) P(m, 'rib' + z, [2.4, 2, z], [3, 20, z + 1], 't33');
    P(m, 'cover', [2.4, 21, 3.4], [24.6, 22, 28.6], 't33');
    for (let z = 5, i = 0; z < 27; z += 2.6, i++) P(m, 'rad' + i, [24, 3, z], [31.4, 19, z + 0.9], 'fin');
    for (const y of [3, 18]) P(m, 'rad_head' + y, [24, y, 4.6], [26, y + 1.4, 27.6], 't33');
    for (const [y, i] of [[8, 0], [12, 1], [16, 2]]) {
      P(m, 'sec_bush' + i, [1.2, y - 1, 8], [3, y + 1, 24], 'rubber');
      for (const z of [9.5, 13.5, 17.5, 21.5]) P(m, `sec_${i}_${z}`, [0, y - 0.6, z - 1], [3, y + 0.6, z + 1], 'cu');
    }
    for (const z of [9, 16, 23]) insulatorY(m, 'hv' + z, [18, z], 1.6, 22.6, 7, 'porc', { pitch: 1.3 });
    drum(m, 'cons', 'z', [8, 28], 2.4, 6, 24, 't33');
    for (const z of [8, 20]) P(m, 'cons_br' + z, [7.4, 22, z], [8.6, 25.8, z + 1.2], 'frame');
    cabinet(m, 'oltc', [6, 4, 2.4], [14, 14, 4], 't33', { plate: true });
    m.decal('haz', [16, 12, 3.8], [19, 14.6, 4], 't33', 'north', 'parts.warning');
    return m;
  }
  // Electrode mast 1x4x1 standing south (z 8..14); the arm runs north at the top to the clamp over (8, -12).
  function mast() {
    const m = mk('material/steel');
    P(m, 'guide', [4.4, -16, 8], [11.6, -12, 15], 'frame');
    P(m, 'column', [5, -12, 9], [11, 26, 14], 'st', { split: 'y' });
    for (let y = -8; y < 24; y += 8) P(m, 'col_band' + y, [4.6, y, 8.6], [11.4, y + 0.8, 14.4], 'frame');
    P(m, 'arm', [5.4, 24, -16], [10.6, 28, 14], 'st', { split: 'z' });
    P(m, 'clamp', [4.6, 22, -15.6], [11.4, 29, -8.4], 'cu');
    P(m, 'clamp_ring', [4.2, 21.4, -15.2], [11.8, 22.4, -8.8], 'ci');
    for (const x of [5.6, 9.2]) P(m, 'bus' + x, [x, 28, -12], [x + 1.2, 29.4, 13], 'cu');
    P(m, 'flex', [5.4, 20, 12], [10.6, 28, 13.6], 'rubber');
    P(m, 'hyd', [6.8, -12, 14], [9.2, 10, 16], 'bright');
    P(m, 'water_in', [10.6, 25, -6], [11.6, 26, 10], 'p_water');
    return m;
  }
  function electrode() {
    const m = mk('steelmaking/graphite');
    m.cylY('rod', [8, -12], 2.5, -16, 32, 'graph', { cap: 'steelmaking/graphite', bottom: true });
    m.ringY('nipple', [8, -12], 2.6, 0.4, 6, 6.6, 'graph');
    return m;
  }

  // ---------------------------------------------------------------- continuous caster
  // Tundish 2x2x2 on its yellow car: refractory-lined shell, slag cover (melt glows when casting), the stopper
  // lever and guide at (16, 16), the shrouded nozzle below.
  function tundish(casting) {
    const m = mk(D + 'riveted');
    for (const z of [4, 26]) P(m, 'rail' + z, [0, 0, z], [32, 1, z + 2], 'st');
    for (const x of [2, 28]) for (const z of [4, 26]) P(m, `wheel_${x}_${z}`, [x - 1, 1, z - 0.2], [x + 1, 3, z + 2.2], 'ci');
    P(m, 'car_n', [0, 3, 3.4], [32, 6, 6.6], 'yel'); P(m, 'car_s', [0, 3, 25.4], [32, 6, 28.6], 'yel');
    for (const x of [0, 29]) P(m, 'car_x' + x, [x, 3, 6.6], [x + 3, 6, 25.4], 'yel');
    for (const x of [1, 28]) for (const z of [5, 24]) P(m, `post_${x}_${z}`, [x, 6, z], [x + 3, 12, z + 3], 'yel');
    P(m, 'shell_bot', [3, 12, 7], [29, 14, 25], 'riv');
    for (const [f, t] of [[[3, 14, 7], [29, 22, 9]], [[3, 14, 23], [29, 22, 25]], [[3, 14, 9], [5, 22, 23]], [[27, 14, 9], [29, 22, 23]]]) P(m, 'wall', f, t, 'riv');
    P(m, 'lining', [5, 14, 9], [27, 19.6, 23], 'dinas');
    P(m, casting ? 'glow_bath' : 'slag', [5, 19.6, 9], [27, 20, 23], casting ? 'melt' : 'soot', { hide: ['down'] });
    P(m, 'lid', [3, 22, 7], [14, 23, 25], 'riv');
    P(m, 'nozzle_well', [14, 9, 14], [18, 12, 18], 'dinas');
    m.cylY('shroud', [16, 16], 1.4, 2, 9, 'dinas', { cap: 'steelmaking/dinas', bottom: true });
    if (casting) P(m, 'glow_stream', [15.4, 1, 15.4], [16.6, 2, 16.6], 'melt');
    // stopper mechanism: column on the car, lever over to the stopper head
    P(m, 'mech_col', [20, 6, 26], [22, 30, 28], 'yel');
    P(m, 'lever', [15, 29, 15], [21, 30.4, 27], 'st');
    P(m, 'lever_hand', [21.6, 26, 27], [22.6, 30, 28], 'red');
    return m;
  }
  function stopper() {
    const m = mk('steelmaking/dinas');
    m.cylY('rod', [16, 16], 1.4, 18, 29, 'dinas', { bottom: true });
    m.cylY('head', [16, 16], 1.2, 29, 31.9, 'st', { cap: 'material/steel' });
    return m;
  }
  // Mould oscillation table: frame, eccentric drive and levers, water hoses to the jacket.
  function mouldTable() {
    const m = mk('plant/structure');
    for (const [f, t] of [[[0, 0, 0], [16, 4, 3]], [[0, 0, 13], [16, 4, 16]], [[0, 0, 3], [3, 4, 13]], [[13, 0, 3], [16, 4, 13]]]) P(m, 'frame', f, t, 'frame');
    for (const x of [0.6, 13.4]) P(m, 'lever' + x, [x, 4, 5], [x + 2, 5.4, 11], 'st');
    m.prism('ecc', 'x', [2, 8], 1.4, -0.01, 3, 'ci', { split: '', ends: ['west'] });
    for (const [x, z] of [[3.4, 0.4], [11.6, 0.4], [3.4, 14.6], [11.6, 14.6]]) P(m, `hose_${x}_${z}`, [x, 2, z], [x + 1, 6, z + 1], 'p_water');
    return m;
  }
  function mould() {
    const m = mk('disconnector/copper');
    P(m, 'jacket', [3, 4, 3], [13, 14, 13], 'st');
    for (const [f, t] of [[[3, 14, 3], [13, 16, 5]], [[3, 14, 11], [13, 16, 13]], [[3, 14, 5], [5, 16, 11]], [[11, 14, 5], [13, 16, 11]]]) P(m, 'cu_top', f, t, 'cu');
    P(m, 'meniscus', [5, 15, 5], [11, 15.4, 11], 'soot', { hide: ['down'] });
    for (const [x, z] of [[3.6, 2.6], [11.4, 2.6]]) P(m, `nip_${x}`, [x, 6, z], [x + 1, 7, z + 0.4], 'cu');
    return m;
  }
  function strandFrame(hot) {
    const m = mk('plant/structure');
    for (const x of [0, 14]) P(m, 'housing' + x, [x, 0, 0], [x + 2, 16, 16], 'frame');
    for (const x of [0, 14]) for (const z of [2.5, 13.5]) m.prism(`chock_${x}_${z}`, 'x', [8, z], 1.8, x - 0.01, x + 2.01, 'ci', { split: '', ends: ['west', 'east'] });
    for (const z of [5.2, 10.8]) {
      P(m, 'spray' + z, [2, 12, z], [14, 12.8, z + 0.8], 'p_water');
      for (const x of [4, 8, 12]) P(m, `noz_${z}_${x}`, [x - 0.3, 11.4, z + 0.1], [x + 0.3, 12, z + 0.7], 'br');
    }
    if (hot) P(m, 'glow_strand', [3, 0, 4.2], [13, 16, 11.8], 'melt', { hide: ['up', 'down'] });
    return m;
  }
  function rolls() {
    const m = mk('rolling/roll');
    for (const z of [2.5, 13.5]) m.prism('roll' + z, 'x', [8, z], 1.5, 2, 14, 'roll', { split: '', ends: ['west', 'east'] });
    return m;
  }

  // ---------------------------------------------------------------- foundry
  function inductionFurnace(melting) {
    const m = mk('plant/structure');
    P(m, 'platform', [0, 0, 0], [16, 2, 16], 'chequer');
    for (const x of [0.6, 13.4]) {
      P(m, 'pedestal' + x, [x, 2, 6], [x + 2, 13, 10], 'frame');
      m.prism('trunnion' + x, 'x', [12, 8], 1.4, x - 0.4, x + 2.4, 'ci', { split: '', ends: ['west', 'east'] });
    }
    P(m, 'hyd', [3, 2, 12.6], [5, 9, 14.6], 'bright');
    for (const z of [12, 14]) P(m, 'cable' + z, [13, 2, z], [15, 3, z + 1], 'rubber');
    P(m, 'cable_up', [14.6, 3, 12], [15.6, 7, 15], 'rubber');
    cabinet(m, 'cap', [9, 2, 0.6], [15.4, 8, 3], 'en', { plate: false });
    lamp(m, 'heat', 12, 6.4, 0.4, melting ? 'l_a' : 'l_off', melting, 0.5);
    P(m, 'spout_guard', [3, 21, 0], [13, 23, 1], 'yel');
    return m;
  }
  function crucible() {
    const m = mk('disconnector/copper');
    P(m, 'yoke', [3, 8, 3], [13, 22, 13], 'frame');
    for (let y = 9; y < 21; y += 2) P(m, 'coil' + y, [2.9, y, 2.9], [13.1, y + 1, 13.1], 'cu');
    P(m, 'rim', [3, 22, 3], [13, 24, 13], 'dinas');
    P(m, 'spout', [6.6, 22.6, 2], [9.4, 23.6, 3], 'dinas');
    P(m, 'glow_melt', [4.6, 23.4, 4.6], [11.4, 23.8, 11.4], 'melt', { hide: ['down'] });
    return m;
  }
  function permanentMold(poured) {
    const m = mk('material/cast_iron');
    P(m, 'table', [0, 0, 0], [16, 2, 16], 'frame');
    P(m, 'fixed', [2, 2, 8], [14, 13, 15.5], 'ci');
    for (const x of [1.4, 13.6]) P(m, 'hinge' + x, [x, 4, 7.2], [x + 1, 11, 8.8], 'bright');
    P(m, 'clamp', [14, 6, 3], [15.4, 8, 13], 'st');
    P(m, 'clamp_handle', [14.6, 8, 3], [15.4, 12, 4], 'red');
    if (poured) {
      P(m, 'cup', [6.6, 13, 6.6], [9.4, 13.5, 9.4], 'ci');
      P(m, 'glow_sprue', [7.2, 13.1, 7.2], [8.8, 13.55, 8.8], 'melt');
    }
    return m;
  }
  function moldHalf() {
    const m = mk('material/cast_iron');
    P(m, 'half', [2, 2, 0.5], [14, 13, 8], 'ci');
    for (const y of [4, 8]) P(m, 'rib' + y, [2.4, y, 0], [13.6, y + 1, 0.5], 'ci');
    P(m, 'handle', [6, 10, -0.4], [10, 11, 0.5], 'bright');
    return m;
  }

  // ---------------------------------------------------------------- wire and rope
  function drawingBench() {
    const m = mk('rolling/mill_blue');
    P(m, 'body', [0, 0, 2], [32, 8, 14], 'mblue');
    P(m, 'top', [-0.01, 7.6, 1.6], [32.01, 8, 14.4], 'st');
    m.cylY('payoff', [5, 8], 3.6, 8, 9, 'st', { cap: 'material/steel' });
    m.cylY('coil', [5, 8], 3, 9, 12, 'wire', { cap: D + 'wire' });
    P(m, 'diebox', [11, 8, 6], [15, 11, 10], 'ci');
    P(m, 'soap', [11.4, 11, 6.4], [14.6, 11.4, 9.6], 'light');
    P(m, 'die', [15, 9, 7.4], [16, 10, 8.6], 'bright');
    P(m, 'wire_in', [8, 9.6, 7.8], [11, 10, 8.2], 'wire'); P(m, 'wire_out', [16, 9.6, 7.8], [18, 10, 8.2], 'wire');
    P(m, 'guard', [17, 8, 1.4], [31, 9, 2], 'yel');
    cabinet(m, 'panel', [26, 1, 1], [31, 7, 2], 'mblue', { plate: false });
    return m;
  }
  function drawingDrum() {
    const m = mk('material/steel');
    m.cylY('drum', [24, 8], 5.2, 8, 13, 'wire', { cap: 'material/steel' });
    m.cylY('flange', [24, 8], 6, 13, 14, 'st', { cap: 'material/steel' });
    P(m, 'stripper', [28.6, 13, 7.4], [30, 14, 8.6], 'bright');
    return m;
  }
  function stranding() {
    const m = mk('rolling/mill_blue');
    P(m, 'bed', [-16, 0, 1], [32, 2, 15], 'frame');
    for (const x of [-16, 22]) {
      P(m, 'bearing' + x, [x, 2, 3], [x + 4, 13, 13], 'mblue');
      m.prism('bearing_cap' + x, 'x', [10, 8], 3, x - 0.01, x + 4.01, 'mblue', { split: '', ends: ['west', 'east'] });
    }
    P(m, 'die_head', [26, 6, 5], [29, 14, 11], 'ci');
    m.prism('closing_die', 'x', [10, 8], 1.2, 26, 30, 'bright', { split: '' });
    m.prism('capstan', 'z', [28.6, 12], 3, 2, 14, 'st', { split: '' });
    P(m, 'capstan_stand', [28, 2, 2], [32, 9, 14], 'mblue');
    P(m, 'rope_out', [30, 14, 7.6], [32, 15.4, 8.8], 'wire');
    motor(m, 'motor', 'x', [6, 13], 3.4, -8, 4, 'mblue', { feet: false });
    P(m, 'motor_base', [-8, 2, 8.6], [4, 2.6, 15], 'frame');
    return m;
  }
  function cradle() {
    const m = mk('material/steel');
    m.prism('rotor', 'x', [10, 8], 1.4, -12, 24, 'st', { split: 'x' });
    for (const x of [-12, 0, 12, 23]) m.ring('disc' + x, 'x', [10, 8], 8.6, 1.2, x, x + 1, 'mblue');
    for (const [x, s] of [[-10, 1], [-4, -1], [2, 1], [8, -1], [14, 1], [19, -1]]) {
      const y = 10 + s * 5;
      m.prism('bobbin' + x, 'x', [y, 8], 2.6, x + 0.6, x + 4.4, 'wire', { split: '', ends: ['west', 'east'] });
      m.prism('bob_fl0' + x, 'x', [y, 8], 3.4, x, x + 0.6, 'st', { split: '', ends: ['west', 'east'] });
      m.prism('bob_fl1' + x, 'x', [y, 8], 3.4, x + 4.4, x + 5, 'st', { split: '', ends: ['west', 'east'] });
    }
    return m;
  }

  // ---------------------------------------------------------------- aluminium
  function precipitator() {
    const m = mk('plant/cladding');
    m.cylY('tank', [16, 16], 14, -16, 26, 'clad', { cap: 'plant/cladding' });
    for (const y of [-6, 6, 18]) m.ringY('band' + y, [16, 16], 14.4, 0.6, y, y + 1, 'frame');
    P(m, 'bridge', [2, 26, 13], [30, 28, 19], 'frame');
    P(m, 'gearbox', [12, 28, 12], [20, 31, 20], 'green');
    motor(m, 'drive', 'x', [29.2, 16], 1.2, 20, 28, 'blue', { feet: false });
    P(m, 'feed', [24, 24, 2], [26, 32, 4], 'p_alk');
    P(m, 'feed_run', [24, 30, 4], [26, 32, 12], 'p_alk');
    P(m, 'outlet', [15, -14, 0], [17, -12, 3], 'p_alk');
    ladder(m, 'ladder', 30.4, 8, -16, 26, 'z');
    return m;
  }
  function agitator() {
    const m = mk('material/steel');
    m.cylY('shaft', [16, 16], 0.8, -12, 28, 'st', { cap: 'material/steel', bottom: true });
    for (const y of [-11, 6]) for (const a of [0, 45, -45, 90]) {
      const rot = a && a !== 90 ? { axis: 'y', angle: a, origin: [16, 8, 16] } : undefined;
      if (a === 90) P(m, `blade_${y}_${a}`, [15.4, y, 6], [16.6, y + 1.2, 26], 'st');
      else P(m, `blade_${y}_${a}`, [6, y, 15.4], [26, y + 1.2, 16.6], 'st', { rot });
    }
    return m;
  }
  function digester() {
    const m = mk('plant/cladding');
    for (const x of [8, 24]) {
      m.cylY('skirt' + x, [x, 16], 5, -16, -10, 'frame');
      vessel(m, 'vessel' + x, [x, 16], 6.4, -9, 24, 'clad', { bottom: true });
      P(m, 'steam_in' + x, [x - 0.6, -6, 8], [x + 0.6, 20, 9.6], 'p_steam');
    }
    P(m, 'deck', [0, 10, 4], [32, 11, 28], 'grat');
    PLANT.railing(m, 'rail', 0, 32, 11, 4, 'x');
    P(m, 'alk_top', [8, 29, 15.2], [24, 30.6, 16.8], 'p_alk');
    P(m, 'alk_in', [0, 26, 15.2], [3, 27.6, 16.8], 'p_alk');
    P(m, 'alk_out', [29, -4, 15.2], [32, -2.4, 16.8], 'p_alk');
    P(m, 'flash', [14, -14, 22], [18, 4, 26], 'clad');
    m.decal('plate', [6, 2, 9.4], [10, 4, 9.6], 'clad', 'north', 'parts.plate');
    return m;
  }

  // ---------------------------------------------------------------- copper and nickel
  function refiningCell(ni) {
    const m = mk(D + 'riveted');
    P(m, 'tank', [0, 0, 1], [32, 12, 15], 'rubber');
    P(m, 'lip', [-0.01, 11.6, 0.6], [32.01, 12.4, 15.4], 'ci');
    P(m, 'electrolyte', [0.6, 10.8, 1.6], [31.4, 11, 14.4], ni ? 'niel' : 'cuel', { hide: ['down'] });
    for (const z of [1.4, 14]) P(m, 'bus' + z, [0, 12.4, z], [32, 13.2, z + 0.6], 'cu');
    for (let x = 2, i = 0; x < 30.5; x += 2, i++) {
      const anode = i % 2 === 0;
      P(m, 'plate' + i, [x, anode ? 3 : 4, 3], [x + 0.4, 12.8, 13], ni && !anode ? 'bag' : (anode ? 'anode' : 'cath'));
      P(m, 'bar' + i, [x - 0.2, 12.8, 1.2], [x + 0.6, 13.6 + (anode ? 0 : 0.6), 14.8], anode ? 'anode' : 'cu');
      if (!anode) P(m, 'lug' + i, [x - 0.1, 14.2, 7.4], [x + 0.5, 14.75, 8.6], 'cu');
    }
    return m;
  }
  function converterBase() {
    const m = mk('plant/structure');
    for (const x of [-12, 25]) {
      P(m, 'pier' + x, [x - 2, 0, 6], [x + 5, 2, 30], 'conc');
      for (const z of [8, 24]) m.prism(`roller_${x}_${z}`, 'x', [3.4, z], 1.4, x - 1, x + 4, 'ci', { split: '', ends: ['west', 'east'] });
    }
    P(m, 'gear_case', [28, 2, 22], [32, 14, 31], 'green');
    motor(m, 'motor', 'x', [6, 27], 2, 20, 27.6, 'blue', { feet: false });
    P(m, 'air_main', [-14, 14, 4], [32, 16, 6], 'p_air');
    for (const x of [-10, 0, 10, 20]) P(m, 'air_drop' + x, [x, 4, 4.2], [x + 1, 14, 5.8], 'p_air');
    P(m, 'hood_post', [-14, 2, 30], [-12, 18, 32], 'frame');
    return m;
  }
  function converterVessel() {
    const m = mk('material/soot');
    m.prism('barrel', 'x', [16, 16], 12.6, -12, 28, 'riv', { split: 'x' });
    m.prism('end_w', 'x', [16, 16], 11, -14, -12, 'riv', { split: '', ends: ['west'] });
    m.prism('end_e', 'x', [16, 16], 11, 28, 30, 'riv', { split: '', ends: ['east'] });
    for (const x of [-11, 26]) m.prism('tyre' + x, 'x', [16, 16], 13, x, x + 2, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('gear', 'x', [16, 16], 13.6, 22, 24, 'ci', { split: '', ends: ['west', 'east'] });
    P(m, 'mouth', [4, 27, 11], [12, 29.4, 21], 'ci');
    P(m, 'glow_mouth', [5, 28.4, 12], [11, 29.5, 20], 'melt', { hide: ['down'] });
    P(m, 'tuyere_pipe', [-10, 12, 2.6], [24, 13.4, 3.6], 'p_air');
    for (let x = -8; x < 24; x += 4) P(m, 'tuyere' + x, [x, 12.2, 3.4], [x + 1, 13.2, 5], 'ci');
    return m;
  }
  function reverberatory(lit) {
    const m = mk('material/brick');
    P(m, 'hearth', [-16, -16, -16], [32, -12, 32], 'conc');
    P(m, lit ? 'glow_bath' : 'bath', [-6, -12, -16], [22, -10, 32], lit ? 'melt' : 'soot', { hide: ['down', 'north', 'south'] });
    for (const [x0, x1, n] of [[-14, -6, 'w'], [22, 30, 'e']]) P(m, 'wall_' + n, [x0, -12, -16], [x1, 18, 32], 'brick');
    for (const [y, x0, x1] of [[18, -14, 30], [21, -11, 27], [24, -6, 22], [26, 0, 16]]) P(m, 'arch' + y, [x0, y, -16], [x1, y + 3, 32], 'brick');
    for (const z of [-12, 4, 20]) {
      for (const x of [-16, 30]) P(m, `stay_${x}_${z}`, [x, -12, z], [x + 2, 26, z + 2], 'frame');
      P(m, 'tie' + z, [-16, 29, z + 0.6], [32, 30, z + 1.4], 'st');
      for (const x of [-16, 30]) P(m, `tie_v_${x}_${z}`, [x + 0.6, 26, z + 0.6], [x + 1.4, 30, z + 1.4], 'st');
      P(m, 'charge' + z, [6, 29, z - 2], [10, 30, z + 2], 'ci');
      if (lit) P(m, 'glow_charge' + z, [7, 30, z - 1], [9, 30.2, z + 1], 'fglow');
    }
    for (const z of [-4, 12, 28]) {
      for (const [x, d] of [[-14.4, 'west'], [30.4, 'east']]) {
        const f = d === 'west' ? [x, -2, z - 2] : [x - 0.4, -2, z - 2], t = d === 'west' ? [x + 0.4, 3, z + 2] : [x, 3, z + 2];
        P(m, `door_${d}_${z}`, f, t, 'ci');
        if (lit) P(m, `glow_peep_${d}_${z}`, d === 'west' ? [x - 0.1, 0, z - 0.6] : [x, 0, z - 0.6], d === 'west' ? [x, 1.2, z + 0.6] : [x + 0.1, 1.2, z + 0.6], 'fglow');
      }
    }
    P(m, 'taphole', [-15, -11, 10], [-14, -9, 13], 'ci');
    return m;
  }
  // Reduction pot 3x2x3: riveted shell in its cradle, crust (or frozen bath, or the anode-effect glow at the
  // anode edges), the superstructure portal with the anode bus beam (partial) and the side hoods.
  function pot(state) {
    const m = mk(D + 'riveted');
    P(m, 'shell', [-14, 0, -12], [30, 10, 28], 'riv');
    for (let x = -14; x < 30; x += 6) for (const [z0, z1] of [[-16, -12], [28, 32]]) P(m, `cradle_${x}_${z0}`, [x, 0, z0], [x + 1.4, 10, z1], 'frame');
    P(m, 'top', [-14, 10, -12], [30, 10.2, 28], state === 'frozen' ? 'frozen' : 'crust', { hide: ['down'] });
    if (state === 'anode_effect') for (const z of [-2, 8, 18]) P(m, 'glow_edge' + z, [-10, 10.1, z - 0.4], [26, 10.4, z + 0.4], 'fglow');
    for (const x of [-16, 30]) {
      P(m, 'portal' + x, [x, 0, 6], [x + 2, 30, 10], 'frame');
      P(m, 'riser' + x, [x + 0.4, 0, 2], [x + 1.6, 22, 5], 'alu');
    }
    P(m, 'portal_beam', [-16, 27, 6], [32, 30, 10], 'frame');
    for (const s of [-1, 1]) P(m, 'hood' + s, [-14, 10.2, s < 0 ? -12 : 26], [30, 16, s < 0 ? -11.4 : 26.6], 'alu');
    P(m, 'feeder', [6, 16, 6.6], [10, 27, 9.4], 'st');
    P(m, 'hopper', [4, 22, 5], [12, 27, 11], 'st');
    return m;
  }
  function anodeFrame() {
    const m = mk('steelmaking/graphite');
    for (const z of [-2, 18]) P(m, 'beam' + z, [-14, 23, z], [30, 25, z + 2], 'alu');
    for (let x = -12, i = 0; x < 28; x += 6, i++) for (const z of [-8, 0, 12, 20]) {
      const zz = z < 8 ? -1.4 : 18.6;
      P(m, `rod_${i}_${z}`, [x + 2, 16, zz], [x + 3, 23, zz + 0.8], 'alu');
      P(m, `anode_${i}_${z}`, [x, 10, z], [x + 5, 16, z + 6], 'graph');
    }
    return m;
  }

  // ---------------------------------------------------------------- coal preparation
  function dms() {
    const m = mk('plant/structure');
    for (const x of [0, 29]) {
      P(m, 'pier' + x, [x, 0, 4], [x + 3, 4, 28], 'conc');
      for (const z of [8, 24]) m.prism(`roller_${x}_${z}`, 'x', [5.4, z], 1.6, x - 0.01, x + 3.01, 'ci', { split: '', ends: ['west', 'east'] });
    }
    P(m, 'feed_chute', [-0.01, 18, 12], [4, 22, 20], 'st');
    P(m, 'feed_box', [-0.01, 22, 11], [3, 27, 21], 'st');
    P(m, 'medium_pipe', [0, 26, 22], [6, 27.4, 23.4], 'p_liq');
    P(m, 'float_launder', [28, 12, 2], [32, 16, 14], 'st');
    P(m, 'float_flow', [28.4, 15.4, 2.4], [31.6, 15.6, 13.6], 'medium', { hide: ['down'] });
    P(m, 'sink_chute', [28, 2, 18], [32, 12, 30], 'st');
    motor(m, 'drive', 'z', [16, 4], 2, 26, 31, 'green', { feet: false });
    P(m, 'drive_base', [13, 0, 26], [19, 2, 31], 'frame');
    return m;
  }
  function dmsDrum() {
    const m = mk('material/steel');
    m.prism('shell', 'x', [16, 16], 11.4, 4, 28, 'st', { split: 'x' });
    for (const x of [5, 25]) m.prism('tyre' + x, 'x', [16, 16], 12, x, x + 2, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('gear', 'x', [16, 16], 12.4, 15, 17, 'ci', { split: '', ends: ['west', 'east'] });
    m.ring('end_w', 'x', [16, 16], 11.4, 4, 3, 4, 'st');
    m.ring('end_e', 'x', [16, 16], 11.4, 4, 28, 29, 'st');
    m.prism('pool', 'x', [16, 16], 7.2, 3.6, 4, 'medium', { split: '', ends: ['west'] });
    return m;
  }
  function flotation(froth) {
    const m = mk('material/steel');
    P(m, 'tank', [0, 0, 0], [16, 13, 16], 'st');
    P(m, 'pulp', [0.6, 12.4, 0.6], [15.4, 12.6, 15.4], froth ? 'froth' : 'pulp', { hide: ['down'] });
    P(m, 'launder', [-1, 9, 0], [0, 12.6, 16], 'st');
    if (froth) P(m, 'froth_lip', [-0.6, 12.6, 0.6], [1, 13, 15.4], 'froth');
    for (const y of [4, 8]) P(m, 'stiff' + y, [0, y, -0.4], [16, y + 0.8, 0], 'frame');
    P(m, 'bridge', [0, 13, 6.4], [16, 14.4, 9.6], 'frame');
    P(m, 'bearing', [6.6, 14.4, 6.6], [9.4, 15, 9.4], 'ci');
    motor(m, 'motor', 'z', [13, 17.6], 2, 5, 11, 'blue', { feet: false });
    P(m, 'motor_stool', [11, 14.4, 5], [15, 15.4, 11], 'frame');
    P(m, 'belt_guard', [8, 14.6, 10], [14, 16, 11], 'yel');
    P(m, 'tail_box', [15.4, 2, 5], [16.6, 7, 11], 'st');
    return m;
  }
  function impeller() {
    const m = mk('material/steel');
    m.cylY('shaft', [8, 8], 0.6, 5, 16, 'st', { cap: 'material/steel' });
    m.cylY('rotor', [8, 8], 2.6, 2, 5, 'rubber', { cap: 'plant/rubber', bottom: true });
    for (const a of [0, 45]) P(m, 'vane' + a, [5, 2.6, 7.6], [11, 4.4, 8.4], 'rubber', { rot: a ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined });
    return m;
  }

  // ---------------------------------------------------------------- coke dry quenching
  function cdq(top) {
    const m = mk(D + 'riveted');
    if (!top) {
      m.cylY('shaft', [8, 8], 14, -16, 32, 'riv', { seed: 2 });
      for (const y of [-8, 8, 24]) m.ringY('band' + y, [8, 8], 14.4, 0.6, y, y + 1, 'frame');
      P(m, 'deck', [-16, 14, -16], [32, 15, -6], 'grat');
      PLANT.railing(m, 'rail', -16, 32, 15, -16, 'x');
      P(m, 'gas_out', [20, 2, 4], [32, 10, 12], 'riv');
      P(m, 'gas_flange', [30, 1.4, 3.4], [31, 10.6, 12.6], 'ci');
      for (const a of [45, 135, 225, 315]) m.postY('brace' + a, [8, 8], 14.6, a, 1.2, -16, 14, 'frame');
      P(m, 'sample', [-6.6, 0, 6], [-5.6, 2, 10], 'ci');
    } else {
      m.cylY('shaft', [8, 8], 14, -16, 8, 'riv', { seed: 2, cap: D + 'riveted' });
      m.cylY('cone', [8, 8], 9, 8, 14, 'riv', { cap: D + 'riveted' });
      m.cylY('neck', [8, 8], 5, 14, 22, 'riv', { cap: D + 'riveted' });
      P(m, 'bell_frame', [0, 22, 0], [16, 23, 16], 'frame');
      for (const x of [0, 15]) P(m, 'guide' + x, [x, 22, 7], [x + 1, 32, 9], 'frame');
      P(m, 'guide_top', [0, 31, 7], [16, 32, 9], 'frame');
      P(m, 'gas_pipe', [17, 4, 6], [32, 8, 10], 'p_gas');
      P(m, 'gas_riser', [24, 8, 6], [28, 20, 10], 'p_gas');
      P(m, 'deck', [-7, 14, -7], [23, 15, -2], 'grat');
      PLANT.railing(m, 'rail', -7, 23, 15, -7, 'x');
    }
    return m;
  }

  const B = (model, cat, java) => ({ model, cat, java });
  const BUILDS = {
    arc_furnace_transformer: B(eafTransformer, 'steelmaking'),
    eaf_electrode_mast: B(mast, 'steelmaking'), 'eaf_electrode_mast/electrode': B(electrode, 'steelmaking', 'eaf_electrode_mast/partial/electrode'),
    continuous_caster: B(() => tundish(false), 'steelmaking'), continuous_caster_casting: B(() => tundish(true), 'steelmaking'),
    'continuous_caster/stopper': B(stopper, 'steelmaking', 'continuous_caster/partial/stopper'),
    caster_mould: B(mouldTable, 'steelmaking'), 'caster_mould/mould': B(mould, 'steelmaking', 'caster_mould/partial/mould'),
    caster_strand_roll: B(() => strandFrame(false), 'steelmaking'), caster_strand_roll_hot: B(() => strandFrame(true), 'steelmaking'),
    'caster_strand_roll/rolls': B(rolls, 'steelmaking', 'caster_strand_roll/partial/rolls'),
    induction_furnace: B(() => inductionFurnace(false), 'foundry'), induction_furnace_melting: B(() => inductionFurnace(true), 'foundry'),
    'induction_furnace/crucible': B(crucible, 'foundry', 'induction_furnace/partial/crucible'),
    permanent_mold: B(() => permanentMold(false), 'foundry'), permanent_mold_poured: B(() => permanentMold(true), 'foundry'),
    'permanent_mold/half': B(moldHalf, 'foundry', 'permanent_mold/partial/half'),
    wire_drawing_bench: B(drawingBench, 'rolling'), 'wire_drawing_bench/drum': B(drawingDrum, 'rolling', 'wire_drawing_bench/partial/drum'),
    rope_stranding_machine: B(stranding, 'rolling'), 'rope_stranding_machine/cradle': B(cradle, 'rolling', 'rope_stranding_machine/partial/cradle'),
    alumina_precipitator: B(precipitator, 'nonferrous'), 'alumina_precipitator/agitator': B(agitator, 'nonferrous', 'alumina_precipitator/partial/agitator'),
    bayer_digester: B(digester, 'nonferrous'),
    electrorefining_cell: B(() => refiningCell(false), 'nonferrous'), electrorefining_cell_ni: B(() => refiningCell(true), 'nonferrous'),
    peirce_smith_converter: B(converterBase, 'nonferrous'), 'peirce_smith_converter/vessel': B(converterVessel, 'nonferrous', 'peirce_smith_converter/partial/vessel'),
    matte_smelter: B(() => reverberatory(false), 'nonferrous'), matte_smelter_lit: B(() => reverberatory(true), 'nonferrous'),
    reduction_pot: B(() => pot('normal'), 'nonferrous'), reduction_pot_anode_effect: B(() => pot('anode_effect'), 'nonferrous'),
    reduction_pot_frozen: B(() => pot('frozen'), 'nonferrous'), 'reduction_pot/anode_frame': B(anodeFrame, 'nonferrous', 'reduction_pot/partial/anode_frame'),
    dense_medium_separator: B(dms, 'coal_prep'), 'dense_medium_separator/drum': B(dmsDrum, 'coal_prep', 'dense_medium_separator/partial/drum'),
    flotation_cell: B(() => flotation(false), 'coal_prep'), flotation_cell_froth: B(() => flotation(true), 'coal_prep'),
    'flotation_cell/impeller': B(impeller, 'coal_prep', 'flotation_cell/partial/impeller'),
    cdq_chamber: B(() => cdq(false), 'coke'), cdq_chamber_top: B(() => cdq(true), 'coke'),
  };
  window.MET3 = FUTK.machine(BUILDS, paint);
})();
