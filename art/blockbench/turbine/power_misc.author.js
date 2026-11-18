// Remaining power and plant blocks, hand-authored for Blockbench (replaces the spec_*.py placeholders): gas
// turbine (compressor, combustor and turbine segments), Francis hydro unit, pumped-storage unit, tidal bulb unit,
// dam gate (open and closed), tide gauge, steam turbine casing, electrode boiler; control desk, absorption tower
// and fractionating column (the lower two blocks of their three), repository controller, maintenance panel,
// canary cage, cap lamp charging rack, hydrotest hand pump with its lever.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/turbine/power_misc.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); PWR.paint('<repo>')
//   PWR.exportAll('<repo>')
//
// Front to the north. Footprints as the placeholders: gas turbine segments are 3x3x3 round their anchors with the
// shaft along z through x 8, y 8; the hydro, pumped-storage and tidal units are 3x2x3 with the anchor in the top
// layer (model y -16..16) and the Create shaft going up from the top centre (x 8, z 8). The columns' controller is
// the bottom cell of three (models reach y 32). hydrotest_pump + partial/lever is static multipart.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'power/';
  const T = {};

  T.gt_enamel = () => { const im = img(32, 32), r = grain(im, [190, 194, 190], 14101, 3, 4, 8); for (let x = 0; x < 32; x += 8) for (let y = 0; y < 32; y++) put(im, x, y, [170, 174, 170]); return im; };
  T.water = () => { const im = img(32, 32), r = rng(14111); for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([40, 84, 104], [90, 140, 160], (Math.sin(x * 0.5 + y * 0.3) + 1) / 2), (r() - 0.5) * 6)); return im; };
  T.staff = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, (y % 4 < 2 && x < 8) ? [196, 40, 32] : [236, 236, 230]); for (let y = 0; y < 16; y += 4) rect(im, 8, y, 8, 1, [30, 30, 30]); return im; };
  T.desk_top = () => {
    const im = img(32, 32), r = rng(14121);
    rect(im, 0, 0, 32, 32, [176, 184, 172]);
    for (let i = 0; i < 4; i++) { rect(im, 3 + i * 7, 4, 5, 5, [30, 30, 32]); rect(im, 4 + i * 7, 5, 3, 3, [210, 210, 200]); }
    for (let i = 0; i < 12; i++) rect(im, 3 + (i % 6) * 4.5, 14 + Math.floor(i / 6) * 4, 2, 2, [[200, 40, 30], [40, 160, 70], [230, 230, 220], [240, 180, 40]][i % 4]);
    for (let x = 3; x < 29; x++) put(im, x, 26, [40, 40, 40]);
    return im;
  };
  T.cage_bars = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 3 === 0 ? [196, 160, 70] : [64, 58, 50]); return im; };
  T.bird = () => { const im = img(16, 16), r = grain(im, [240, 210, 40], 14131, 6, 4, 4); rect(im, 10, 6, 2, 2, [20, 20, 20]); rect(im, 13, 7, 3, 1, [230, 130, 30]); return im; };
  T.maint = () => { const im = img(32, 32), r = grain(im, [88, 124, 96], 14141, 3, 4, 8); rect(im, 4, 14, 24, 10, [230, 228, 214]); for (let y = 16; y < 23; y += 2) for (let x = 6; x < 26; x++) if (r() < 0.7) put(im, x, y, [60, 60, 60]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    gten: { tex: TEX + 'gt_enamel', P: 16 }, wat: { tex: TEX + 'water', P: 16 }, staff: { tex: TEX + 'staff', P: 16 },
    bars: { tex: TEX + 'cage_bars', P: 8 }, bird: { tex: TEX + 'bird', P: 16 },
    ss: { tex: 'electronics/ss', P: 16 }, glass: { tex: 'electronics/glass', P: 16 }, fglow: { tex: 'rolling/furnace_glow', P: 16 },
    en: { tex: 'switchgear_cell/enamel', P: 16 }, porb: { tex: 'disconnector/porcelain', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 },
    grat: { tex: 'steelmaking/grating', P: 8 }, louv: { tex: 'grid_lines/louvre', P: 16 }, mblue: { tex: 'rolling/mill_blue', P: 16 },
    fin: { tex: 'distribution_transformer/fins', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 },
    l_off: { tex: 'grid_lines/lamp_off', P: 16 }, l_w: { tex: 'grid_lines/lamp_white', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });
  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  const B = (m, n, f, t, mat, o) => m.box(n, f, t, mat, Object.assign({ split: '' }, o || {}));
  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    B(m, `${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber');
    B(m, (glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { hide: ['south'] });
  }
  function ladder(m, n, x, z, y0, y1) {
    for (const dx of [0, 2.2]) B(m, `${n}_rail_${dx}`, [x + dx, y0, z], [x + dx + 0.4, y1, z + 0.4], 'yel');
    for (let y = y0 + 1; y < y1; y += 2) B(m, `${n}_rung_${y}`, [x, y, z], [x + 2.6, y + 0.3, z + 0.4], 'yel');
  }

  // ---------------------------------------------------------------- gas turbine
  const gtBase = (m) => {
    B(m, 'skid', [-16, -16, -16], [32, -13, 32], 'frame', { split: 'xz' });
    for (const x of [-6, 22]) B(m, `pedestal_${x}`, [x - 2, -13, -14], [x + 2, -2, 30], 'frame', { split: 'z' });
  };
  // Compressor: the bellmouth and the louvred filter house on top at the north end, the compressor casing
  // (tapering by stages to the south), its horizontal split flange and the bleed valves.
  function gtCompressor() {
    const m = mk(TEX + 'gt_enamel');
    gtBase(m);
    m.prism('bellmouth', 'z', [8, 8], 11, -16, -12, 'gten', { split: '', ends: ['north'] });
    m.prism('casing_1', 'z', [8, 8], 9.4, -12, 4, 'gten', { split: 'z' });
    m.prism('casing_2', 'z', [8, 8], 8.2, 4, 20, 'gten', { split: 'z' });
    m.prism('casing_3', 'z', [8, 8], 7.2, 20, 32, 'gten', { split: 'z' });
    B(m, 'split_flange', [-2.4, 7.6, -12], [18.4, 8.4, 32], 'frame', { split: 'z' });
    B(m, 'plenum', [-12, 14, -16], [28, 31.4, 0], 'gten', { split: 'xy', face: { north: 'louv', east: 'louv', west: 'louv' } });
    B(m, 'duct', [-2, 12, -14], [18, 14, -6], 'gten');
    for (const z of [10, 24]) B(m, `bleed_${z}`, [16, 10, z], [19, 13, z + 3], 'red');
    return m;
  }
  // Combustor: the casing with eight can combustors round it, the cross-fire tubes, the fuel gas ring and its
  // pigtails, the igniter.
  function gtCombustor() {
    const m = mk(TEX + 'gt_enamel');
    gtBase(m);
    m.prism('casing', 'z', [8, 8], 7, -16, 32, 'gten', { split: 'z' });
    for (let k = 0; k < 8; k++) {
      const a = k * 45 * Math.PI / 180, x = 8 + Math.cos(a) * 10, y = 8 + Math.sin(a) * 10;
      if (y < -12) continue;
      B(m, `can_${k}`, [x - 2, y - 2, -10], [x + 2, y + 2, 14], 'ss', { split: 'z' });
      B(m, `pigtail_${k}`, [x - 0.4, y - 0.4, -12], [x + 0.4, y + 0.4, -10], 'p_gas');
    }
    m.ring('fuel_ring', 'z', [8, 8], 13.4, 1, -13, -12, 'p_gas');
    B(m, 'fuel_in', [21, 6, -14], [32, 8, -12], 'p_gas');
    B(m, 'igniter', [6.4, 19, 0], [9.6, 22, 3], 'en');
    return m;
  }
  // Turbine: the hot casing with its heat shield straps, the exhaust diffuser growing to the south, the
  // exhaust stack with its silencer going up, the lube oil console beside it.
  function gtTurbine() {
    const m = mk(TEX + 'gt_enamel');
    gtBase(m);
    m.prism('casing', 'z', [8, 8], 8, -16, 4, 'ss', { split: 'z' });
    for (const z of [-12, -6, 0]) m.ring(`strap_${z}`, 'z', [8, 8], 8.4, 0.4, z, z + 1, 'frame');
    m.prism('diffuser_1', 'z', [8, 8], 9.6, 4, 16, 'gten', { split: 'z' });
    B(m, 'exhaust_box', [-4, -2, 16], [20, 18, 32], 'gten', { split: 'yz' });
    B(m, 'stack', [-2, 18, 18], [18, 32, 30], 'gten', { split: 'y' });
    B(m, 'silencer_band', [-2.4, 26, 17.6], [18.4, 27, 30.4], 'frame');
    B(m, 'oil_console', [22, -13, -10], [32, -2, 6], 'blue', { split: 'y' });
    return m;
  }

  // ---------------------------------------------------------------- hydro units (anchor in the top layer)
  // Francis unit: the spiral case round the wicket gates in the lower layer fed by the penstock from the north,
  // the draft tube to the south, the servomotors, the generator stator with its ribs and the top bracket under
  // the Create shaft.
  function hydro(kind) {
    const m = mk('plant/concrete');
    B(m, 'pit', [-16, -16, -16], [32, -15, 32], 'conc', { split: 'xz' });
    if (kind === 'tidal') {
      // bulb unit in a concrete dam block: water passage north-south, the bulb with its stay fins, gate slot
      B(m, 'block_w', [-16, -15, -16], [-6, 16, 32], 'conc', { split: 'yz' });
      B(m, 'block_e', [22, -15, -16], [32, 16, 32], 'conc', { split: 'yz' });
      B(m, 'roof', [-6, 6, -16], [22, 16, 32], 'conc', { split: 'xz' });
      B(m, 'water', [-6, -14, -16], [22, -13.8, 32], 'wat', { split: 'xz', hide: ['down', 'north', 'south', 'east', 'west'] });
      m.prism('bulb', 'z', [-4, 8], 7, -8, 18, 'mblue', { split: 'z', ends: ['north'] });
      m.prism('nose', 'z', [-4, 8], 4.6, -12, -8, 'mblue', { split: '', ends: ['north'] });
      for (const [n, f, t] of [['up', [7, 3, -2], [9, 6, 8]], ['w', [-5, -5, -2], [1, -3, 8]], ['e', [15, -5, -2], [21, -3, 8]]]) B(m, `stay_${n}`, f, t, 'frame');
      m.prism('runner_hub', 'z', [-4, 8], 3, 18, 22, 'bright', { split: '', ends: ['south'] });
      for (const [n, a] of [['a', 0], ['b', 45], ['c', -45]]) B(m, `blade_${n}`, [-4, -5, 20], [20, -3, 21], 'br', { rot: a ? { axis: 'z', angle: a, origin: [8, -4, 20.5] } : undefined });
      B(m, 'gate_slot', [-6, -15, -14], [22, 6, -12], 'frame', { split: 'x' });
      B(m, 'shaft_tube', [5, 3, 5], [11, 16, 11], 'mblue');
      B(m, 'hatch', [10, 16, 18], [18, 16.2, 26], 'gal');
      return m;
    }
    m.cylY('spiral', [8, 8], 20, -15, -6, 'mblue', { cap: 'material/steel' });
    B(m, 'penstock', [3, -14, -16], [13, -7, -10], 'mblue');
    B(m, 'draft_tube', [0, -15, 24], [16, -9, 32], 'conc');
    for (const [x, z] of [[-6, 0], [22, 16]]) B(m, `servo_${x}`, [x - 2, -6, z - 2], [x + 2, -3, z + 6], 'yel');
    const gen = kind === 'pumped' ? 'blue' : 'light';
    m.cylY('stator', [8, 8], kind === 'pumped' ? 15 : 13, -6, 10, gen, { cap: 'material/steel' });
    for (let k = 0; k < 8; k++) m.postY(`rib_${k}`, [8, 8], kind === 'pumped' ? 15.4 : 13.4, k * 45, 1.2, -6, 10, 'frame', { split: '' });
    m.cylY('bracket', [8, 8], 6, 10, 14, 'frame', { cap: 'material/steel' });
    B(m, 'shaft_collar', [5.6, 14, 5.6], [10.4, 16, 10.4], 'bright');
    B(m, 'deck', [-16, -6.6, -16], [-4, -6, 32], 'grat', { split: 'z' });
    B(m, 'cabinet', [-15, -6, 24], [-9, 6, 31], 'en');
    lamp(m, 'lamp', -12, 3, 24, 'l_g', true, 0.6);
    return m;
  }

  // ---------------------------------------------------------------- water structures
  // Dam gate: concrete piers with gate slots, the steel leaf (down when closed, raised when open), the hoist
  // and the yellow guard rail on the deck.
  function damGate(closed) {
    const m = mk('plant/concrete');
    B(m, 'pier_w', [0, 0, 4], [3, 16, 12], 'conc');
    B(m, 'pier_e', [13, 0, 4], [16, 16, 12], 'conc');
    B(m, 'sill', [3, 0, 4], [13, 1, 12], 'conc');
    B(m, 'beam', [3, 13, 4], [13, 16, 12], 'conc');
    const y0 = closed ? 1 : 9;
    B(m, 'leaf', [3, y0, 7], [13, y0 + 12, 9], 'gal', { split: 'y' });
    for (const y of [y0 + 3, y0 + 7]) B(m, `girder_${y}`, [3, y, 6.4], [13, y + 0.8, 7], 'frame');
    B(m, 'hoist', [5.6, 16, 6], [10.4, 18.4, 10], 'yel');
    B(m, 'rod', [7.6, 13, 7.6], [8.4, 16, 8.4], 'bright');
    for (const z of [4, 11.6]) B(m, `rail_${z}`, [0, 16, z], [16, 17.4, z + 0.4], 'yel');
    return m;
  }
  // Tide gauge: concrete plinth, the stilling well pipe down, the recorder hut with its door, the red-and-white
  // staff gauge on the front.
  function tideGauge() {
    const m = mk('plant/concrete');
    B(m, 'plinth', [2, 0, 2], [14, 2, 12], 'conc');
    m.prismY('well', [5, 7], 2, 2, 10, 'gal', {});
    B(m, 'hut', [6, 2, 3], [13, 12, 11], 'light');
    B(m, 'roof', [5.4, 12, 2.4], [13.6, 13, 11.6], 'red');
    B(m, 'door', [7, 3, 2.8], [10, 10, 3], 'frame');
    B(m, 'staff', [2.4, 2, 1.6], [3.4, 16, 2.4], 'staff', { face: { north: { tex: TEX + 'staff', uv: [0, 0, 16, 16] } } });
    B(m, 'antenna', [12, 13, 9], [12.4, 16, 9.4], 'gal');
    return m;
  }

  // ---------------------------------------------------------------- boiler-house odds
  // Steam turbine casing segment: the horizontally split HP casing with its flange bolts, the lagging, the steam
  // chest with the stop valve on top.
  function turbineCasing() {
    const m = mk('plant/cladding');
    m.prism('casing', 'x', [8, 8], 6.6, 0, 16, 'clad', { split: '' });
    B(m, 'flange_n', [0, 7.4, 1], [16, 8.6, 2.6], 'frame');
    B(m, 'flange_s', [0, 7.4, 13.4], [16, 8.6, 15], 'frame');
    for (let x = 1.6; x < 16; x += 3.2) for (const z of [1.4, 14.2]) B(m, `bolt_${x}_${z}`, [x - 0.4, 8.6, z - 0.4], [x + 0.4, 9.2, z + 0.4], 'bright');
    B(m, 'chest', [5, 13.6, 5], [11, 15.4, 11], 'frame');
    B(m, 'stop_valve', [6.6, 15.4, 6.6], [9.4, 16, 9.4], 'red');
    B(m, 'feet', [1, 0, 3], [15, 1.6, 13], 'frame');
    return m;
  }
  // Electrode boiler: a stainless vessel on a concrete base with three HV electrodes through porcelain bushings
  // on the head, the cable lugs, the steam outlet (red) and feed (green), a sight glass.
  function electrodeBoiler() {
    const m = mk('electronics/ss');
    B(m, 'base', [1, 0, 1], [15, 1.6, 15], 'conc');
    m.prismY('vessel', [8, 8], 6.4, 1.6, 12, 'ss', {});
    m.prismY('head', [8, 8], 5, 12, 13, 'ss', { ends: ['up'] });
    for (const [x, z] of [[5, 6], [11, 6], [8, 11]]) {
      m.prismY(`bushing_${x}_${z}`, [x, z], 0.9, 13, 15.6, 'porb', {});
      B(m, `lug_${x}_${z}`, [x - 0.5, 15.6, z - 0.5], [x + 0.5, 16, z + 0.5], 'cu');
    }
    B(m, 'steam_out', [14, 9, 7.4], [16, 10.2, 8.6], 'p_steam');
    B(m, 'feed_in', [14, 3, 7.4], [16, 4.2, 8.6], 'p_water');
    B(m, 'sight', [7.2, 5, 1.4], [8.8, 10, 1.7], 'glass');
    return m;
  }

  // ---------------------------------------------------------------- control and process odds
  // Control desk: a console with a sloping instrument panel (dials, keys, lamps), the writing ledge, the cabinet
  // below.
  function desk() {
    const m = mk('plant/light');
    B(m, 'cabinet', [0, 0, 4], [16, 9, 16], 'light');
    B(m, 'kick', [0.4, 0, 3.4], [15.6, 1, 4], 'frame');
    B(m, 'panel', [0, 8.4, 4], [16, 9.4, 14], 'light', { rot: { axis: 'x', angle: -22.5, origin: [8, 9, 9] }, face: { up: { tex: TEX + 'desk_top', uv: [0, 0, 16, 16] } } });
    B(m, 'back', [0, 9, 12], [16, 15.5, 16], 'light');
    B(m, 'ledge', [0, 8.6, 0.6], [16, 9.2, 4.4], 'frame');
    return m;
  }
  // Column bottom two blocks: a skirt with its access opening, the shell with the trays' manways, the feed and
  // reflux nozzles, the reboiler return, platform with rail at y 30, a ladder; the absorption tower is wider
  // and has the packing hold-down manholes and the liquid distributor line.
  function column(absorber) {
    const m = mk('plant/cladding');
    const r = absorber ? 6.6 : 5.4;
    m.cylY('skirt', [8, 8], r, 0, 4, 'frame', {});
    B(m, 'skirt_opening', [6.4, 0.4, 8 - r - 0.05], [9.6, 3.4, 8 - r], 'soot', { hide: ['south'] });
    m.cylY('shell', [8, 8], r, 4, 31, 'clad', {});
    for (const y of [10, 20]) B(m, `manway_${y}`, [6, y, 8 - r - 1], [10, y + 4, 8 - r], 'frame');
    B(m, 'feed', [8 + r - 0.4, 16, 7.2], [16, 17.6, 8.8], absorber ? 'p_gas' : 'p_liq');
    B(m, 'bottoms', [7.2, 4.6, 8 + r - 0.4], [8.8, 6.2, 16], 'p_liq');
    B(m, 'reflux', [0, 26, 7.2], [8 - r + 0.4, 27.6, 8.8], absorber ? 'p_water' : 'p_liq');
    m.ringY('platform', [8, 8], r + 2.6, 2.6, 29.6, 30, 'grat');
    m.ringY('rail', [8, 8], r + 2.6, 0.3, 31, 31.6, 'yel');
    ladder(m, 'ladder', 13.4, 13.4, 4, 30);
    return m;
  }
  // Repository controller: a concrete portal to the vault with the steel door, the monitoring cabinet with its
  // lamps and the radiation sign.
  function repository() {
    const m = mk('plant/concrete');
    B(m, 'portal', [1, 0, 6], [15, 16, 15], 'conc');
    B(m, 'lintel', [0.6, 12, 5], [15.4, 16, 6], 'conc');
    B(m, 'door', [3, 0.4, 5.4], [11, 12, 6], 'gal');
    B(m, 'door_wheel', [6, 5, 4.8], [8, 7, 5.4], 'red');
    B(m, 'cabinet', [11.6, 2, 3.6], [15, 10, 6], 'en');
    lamp(m, 'lamp_ok', 13.3, 8.6, 3.6, 'l_g', true, 0.5);
    lamp(m, 'lamp_alarm', 13.3, 7, 3.6, 'l_off', false, 0.5);
    B(m, 'sign', [5.4, 13, 4.9], [8.6, 15.4, 5], 'yel');
    return m;
  }
  // Maintenance panel: a green enamel wall board with the work-order card and the severity lamps (amber, orange,
  // red by severity).
  function maintPanel(sev) {
    const m = mk(TEX + 'maint');
    B(m, 'board', [1, 1, 12.6], [15, 15, 16], 'green', { face: { north: { tex: TEX + 'maint', uv: [0, 0, 16, 16] } } });
    const cols = ['l_a', 'l_a', 'l_r'];
    for (let i = 0; i < 3; i++) lamp(m, `lamp_${i}`, 4 + i * 4, 12, 12.6, sev > i ? cols[i] : 'l_off', sev > i, 0.8);
    B(m, 'clip', [6, 9, 12.2], [10, 9.6, 12.6], 'bright');
    return m;
  }
  // Canary cage: brass-framed cage with bars, the perch and the bird, the carrying ring.
  function canary() {
    const m = mk(TEX + 'cage_bars');
    B(m, 'floor', [3, 0, 3], [13, 1, 13], 'br');
    // single wires on all four sides so the bird shows between them
    for (let v = 3; v <= 12.8; v += 1.95) {
      B(m, `bar_n_${v}`, [v, 1, 3], [v + 0.25, 12, 3.25], 'br');
      B(m, `bar_s_${v}`, [v, 1, 12.75], [v + 0.25, 12, 13], 'br');
      B(m, `bar_w_${v}`, [3, 1, v], [3.25, 12, v + 0.25], 'br');
      B(m, `bar_e_${v}`, [12.75, 1, v], [13, 12, v + 0.25], 'br');
    }
    for (const y of [4, 8]) for (const [n, f, t] of [['n', [3, y, 3], [13, y + 0.25, 3.25]], ['s', [3, y, 12.75], [13, y + 0.25, 13]], ['w', [3, y, 3], [3.25, y + 0.25, 13]], ['e', [12.75, y, 3], [13, y + 0.25, 13]]]) B(m, `hoop_${n}_${y}`, f, t, 'br');
    B(m, 'roof', [3, 12, 3], [13, 13, 13], 'br');
    B(m, 'ring', [7.6, 13, 7.6], [8.4, 15.5, 8.4], 'br');
    B(m, 'perch', [4, 5, 7.6], [12, 5.4, 8.4], 'wood');
    B(m, 'bird', [6, 5.4, 7], [10, 8.4, 9], 'bird');
    B(m, 'tail', [4.6, 5.6, 7.6], [6, 6.4, 8.4], 'bird');
    return m;
  }
  // Cap lamp charging rack: a steel rack on the wall with two rows of charging bays, each holding a cap lamp with
  // its green charge light, the battery packs below.
  function lampRack() {
    const m = mk('plant/structure');
    B(m, 'back', [0, 0, 15], [16, 16, 16], 'frame');
    for (const y of [2, 9]) {
      B(m, `shelf_${y}`, [0, y, 8], [16, y + 0.6, 15], 'frame');
      for (let i = 0; i < 4; i++) {
        const x = 1 + i * 3.8;
        B(m, `battery_${y}_${i}`, [x, y + 0.6, 10], [x + 2.6, y + 4, 13], 'rubber');
        B(m, `lamp_${y}_${i}`, [x + 0.4, y + 4, 9], [x + 2.2, y + 5.4, 11], 'yel');
        B(m, `glow_charge_${y}_${i}`, [x + 1, y + 2.4, 9.8], [x + 1.6, y + 3, 10], 'l_g');
      }
    }
    return m;
  }
  // Hydrotest hand pump: cast pump body on its tank, the outlet hose and gauge; the lever is the part.
  function handPump() {
    const m = mk('material/cast_iron');
    B(m, 'tank', [3, 0, 6], [14, 6, 16], 'red');
    B(m, 'body', [6, 6, 8], [10, 11, 12], 'ci');
    B(m, 'fulcrum', [7, 10, 13], [9, 12, 14], 'ci');
    B(m, 'hose', [10, 7, 9.4], [14, 8, 10.4], 'rubber');
    B(m, 'gauge', [12.4, 8, 4], [14.4, 10, 6], 'rubber', { face: { north: { tex: 'control/pressure_dial', uv: [0, 0, 16, 16] } } });
    B(m, 'gauge_stem', [13, 6, 5.6], [13.8, 8, 6.4], 'br');
    return m;
  }
  function pumpLever() {
    const m = mk('material/steel');
    B(m, 'lever', [7.4, 11, 1], [8.6, 12, 14], 'st');
    B(m, 'grip', [7, 10.6, 1], [9, 12.4, 4], 'rubber');
    return m;
  }

  const BUILDS = {
    gas_turbine: { model: gtCompressor, cat: 'boilers' }, gas_turbine_combustor: { model: gtCombustor, cat: 'boilers' },
    gas_turbine_turbine: { model: gtTurbine, cat: 'boilers' },
    hydro_turbine: { model: () => hydro('francis'), cat: 'grid' }, pumped_storage_unit: { model: () => hydro('pumped'), cat: 'grid' },
    tidal_unit: { model: () => hydro('tidal'), cat: 'grid' },
    dam_gate: { model: () => damGate(false), cat: 'grid' }, dam_gate_closed: { model: () => damGate(true), cat: 'grid' },
    tide_gauge: { model: tideGauge, cat: 'grid' },
    turbine_casing: { model: turbineCasing, cat: 'turbine' }, electrode_boiler: { model: electrodeBoiler, cat: 'boilers' },
    control_desk: { model: desk, cat: 'control' },
    absorption_tower: { model: () => column(true), cat: 'reagents' }, fractionating_column: { model: () => column(false), cat: 'reagents' },
    repository_controller: { model: repository, cat: 'waste' },
    maintenance_panel: { model: () => maintPanel(0), cat: 'ops' }, maintenance_panel_severity1: { model: () => maintPanel(1), cat: 'ops' },
    maintenance_panel_severity2: { model: () => maintPanel(2), cat: 'ops' }, maintenance_panel_severity3: { model: () => maintPanel(3), cat: 'ops' },
    canary_cage: { model: canary, cat: 'world' }, lamp_rack: { model: lampRack, cat: 'mining' },
    hydrotest_pump: { model: handPump, cat: 'ops' }, 'hydrotest_pump/lever': { model: pumpLever, cat: 'ops', java: 'hydrotest_pump/partial/lever' },
  };

  window.PWR = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    // Synchronous: Blockbench freezes timers while its window is minimised.
    exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        PCART.realize(root, BUILDS[id].model(), id);
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
        while (Dialog.open) Dialog.open.close();
      }
      return out;
    },
  };
})();
