// Coke plant and steelmaking of 08, hand-authored for Blockbench (replaces the spec_metallurgy*.py placeholders):
// coke battery wall, pusher ram, gas collector main, quench tower, by-product plant apparatus (cooler, tar
// precipitator, saturator, scrubber), coke-gas exhauster with its rotor, electrode extruder with its screw, ring
// baking furnace; BOF base and tilting vessel, oxygen lance carriage and lance, ladle treatment station, electric
// arc furnace shell and roof, ingot mould.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/steelmaking/steel_coke.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); STL.paint('<repo>')
//   await STL.exportAll('<repo>')
//
// Front to the north. Contracts with the code:
//   gas_exhauster/partial/rotor spins about z through (16, 16) (FoundryMachineRenderer); electrode_extruder/
//     partial/screw about x through (y 8, z 8); bof_vessel/partial/vessel tilts about x through the trunnion axis
//     (y 15, z 8); oxygen_lance/partial/lance is moved straight down by the renderer.
//   coke_pusher_ram/partial/ram is moved by a Create piston along z; coke_battery_wall is a full block (faces on the
//     boundary cull). gas_collector_main is multipart: the core and a side arm to the north, turned about y.
//   electric_arc_furnace: the shell is the 3x3x3 round the anchor (-16..32); the roof segment's anchor is three
//     blocks higher, its model draws the roof into the block below it (-16..0) and the electrode mast above.
//   Footprints as the placeholders: quench tower 3x3x3 round the anchor, by-product plant x 0..32, y -16..32,
//     z 0..32, exhauster x 0..32, y 0..32, z 0..16, extruder x -16..32, baking furnace, BOF base and ladle
//     station 3x2x3 (x, z -16..32), ingot mould 1x2.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'steelmaking/';
  const T = {};

  // ---------------------------------------------------------------- textures (32 px per block)
  // Silica (dinas) brick of the coke battery: pale yellow, 8x4 px, grey joints, heat darkening at the top.
  T.dinas = () => {
    const im = img(32, 32), r = rng(8101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const row = Math.floor(y / 4), joint = y % 4 === 3 || (x + (row % 2) * 4) % 8 === 7;
      put(im, x, y, joint ? add([150, 144, 128], (r() - 0.5) * 8) : add([214, 196, 140], (r() - 0.5) * 16 - (y < 6 ? 14 : 0)));
    }
    return im;
  };
  // Soot-black steel of the BOF shell and ram, with a dull sheen.
  T.soot_steel = () => { const im = img(32, 32), r = grain(im, [42, 40, 40], 8111, 5, 8, 8); for (let k = 0; k < 16; k++) put(im, r() * 32, r() * 32, [86, 70, 58]); return im; };
  // Molten metal / mouth glow.
  T.melt = () => { const im = img(32, 32), r = rng(8121), n = vnoise(r, 32, 32, 6); for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([255, 140, 30], [255, 236, 150], n(x, y)), (r() - 0.5) * 14)); return im; };
  // Water-cooled panels of the EAF: green, tube runs, scale.
  T.eaf_panel = () => {
    const im = img(32, 32), r = grain(im, [70, 108, 78], 8131, 4, 6, 8);
    for (let x = 0; x < 32; x += 4) for (let y = 0; y < 32; y++) { put(im, x, y, add(get(im, x, y), 22)); put(im, x + 3, y, add(get(im, x + 3, y), -22)); }
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [120, 100, 80]);
    return im;
  };
  // Graphite electrode / green electrode mass: grey-black with fine sparkle.
  T.graphite = () => { const im = img(32, 32), r = grain(im, [52, 54, 58], 8141, 6, 4, 4); for (let k = 0; k < 24; k++) put(im, r() * 32, r() * 32, [120, 124, 130]); return im; };
  // Weathered concrete of the quench tower, streaked by water.
  T.wet_concrete = () => {
    const im = img(32, 32), r = grain(im, [140, 138, 130], 8151, 5, 7, 8);
    for (let k = 0; k < 8; k++) { const x = Math.floor(r() * 32); for (let y = 0; y < 32; y++) if (r() < 0.8) put(im, x, y, add(get(im, x, y), -20)); }
    return im;
  };
  // Grating deck.
  T.grating = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, (x % 3 === 0 || y % 4 === 0) ? [120, 122, 120] : [36, 36, 38]); return im; };
  // Coke-oven gas pipe yellow with tar stains.
  T.tar_yellow = () => {
    const im = img(32, 32), r = grain(im, [196, 164, 48], 8161, 4, 5, 8);
    for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 32), y0 = Math.floor(r() * 20); for (let j = 0; j < 10; j++) put(im, x, y0 + j, [64, 50, 30]); }
    return im;
  };

  const MAT = Object.assign({}, PLANT.MAT, {
    dinas: { tex: TEX + 'dinas', P: 16 }, sst: { tex: TEX + 'soot_steel', P: 16 }, melt: { tex: TEX + 'melt', P: 16 },
    eaf: { tex: TEX + 'eaf_panel', P: 16 }, gph: { tex: TEX + 'graphite', P: 16 }, wcon: { tex: TEX + 'wet_concrete', P: 16 },
    grat: { tex: TEX + 'grating', P: 8 }, tar: { tex: TEX + 'tar_yellow', P: 16 },
    fb: { tex: 'boilers/firebrick', P: 16 }, mblue: { tex: 'rolling/mill_blue', P: 16 }, fglow: { tex: 'rolling/furnace_glow', P: 16 },
    hot: { tex: 'rolling/hot', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 }, porb: { tex: 'disconnector/porcelain', P: 16 },
    cu: { tex: 'disconnector/copper', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 }, louv: { tex: 'grid_lines/louvre', P: 16 },
    idp: { tex: 'boilers/id_paint', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  function ladder(m, n, x, z, y0, y1) {
    for (const dx of [0, 2.2]) m.box(`${n}_rail_${dx}`, [x + dx, y0, z], [x + dx + 0.4, y1, z + 0.4], 'yel', { split: '' });
    for (let y = y0 + 1; y < y1; y += 2) m.box(`${n}_rung_${y}`, [x, y, z], [x + 2.6, y + 0.3, z + 0.4], 'yel', { split: '' });
  }

  // ---------------------------------------------------------------- coke plant
  // Battery wall: silica brick block with two inspection port lids on the top, their rims glowing when hot.
  function batteryWall(hot) {
    const m = mk(TEX + 'dinas');
    m.box('block', [0, 0, 0], [16, 16, 16], 'dinas', { split: '' });
    for (const z of [4, 12]) {
      m.box(`lid_${z}`, [6, 16, z - 2], [10, 16.6, z + 2], 'ci', { split: '' });
      m.box(`lid_knob_${z}`, [7.4, 16.6, z - 0.6], [8.6, 17.2, z + 0.6], 'ci', { split: '' });
      if (hot) m.box(`glow_rim_${z}`, [5.6, 16, z - 2.4], [10.4, 16.2, z + 2.4], 'melt', { split: '', hide: ['down'] });
    }
    return Object.assign(m, { cull: true });
  }
  // Pusher machine head: blue drive housing with motor and gear case over the rack pinion, a yellow guard rail,
  // guide rollers on the rails; the ram is the partial.
  function pusher() {
    const m = mk('rolling/mill_blue');
    m.box('frame', [1, 0, 0], [15, 2, 16], 'frame', { split: '' });
    for (const x of [1.4, 13.4]) m.box(`roller_${x}`, [x, 0, 2], [x + 1.2, 1.4, 14], 'st', { split: '' });
    m.box('housing', [2, 12.4, 2], [14, 16, 14], 'mblue', { split: '' });
    m.box('motor', [9, 13, 11], [14.6, 16, 15.8], 'blue', { split: '' });
    m.box('gear_case', [2.4, 10.6, 10], [7, 12.4, 15], 'mblue', { split: '' });
    for (const x of [1, 14.4]) m.box(`guard_${x}`, [x, 2, 15.4], [x + 0.6, 12, 16], 'yel', { split: '' });
    m.box('guard_rail', [1, 10, 15.4], [15, 10.6, 16], 'yel', { split: '' });
    return m;
  }
  function ram() {
    const m = mk(TEX + 'soot_steel');
    m.box('shoe', [3, 2, -16], [13, 12, -14], 'sst', { split: '' });
    m.box('bar', [5.4, 4.4, -14], [10.6, 9.6, 16], 'sst', { split: 'z' });
    m.box('rack', [6.6, 9.6, -12], [9.4, 10.6, 16], 'st', { split: 'z' });
    return m;
  }
  // Gas collector main: a tar-stained yellow drum at the node with an oven standpipe on top and saddle legs; the
  // side arm is the main itself.
  function collector() {
    const m = mk(TEX + 'tar_yellow');
    m.prismY('drum', [8, 8], 6, 2.6, 13.4, 'tar', { ends: ['up', 'down'] });
    m.prismY('standpipe', [8, 8], 2.2, 13.4, 16, 'sst', { ends: ['up'] });
    m.box('standpipe_lid', [5.4, 15.6, 5.4], [10.6, 16, 10.6], 'ci', { split: '' });
    for (const x of [3, 12]) m.box(`leg_${x}`, [x, 0, 6.6], [x + 1, 2.6, 9.4], 'frame', { split: '' });
    return m;
  }
  function collectorSide() {
    const m = mk(TEX + 'tar_yellow');
    m.prism('main', 'z', [8, 8], 5, 0, 3, 'tar', { split: '' });
    m.ring('flange', 'z', [8, 8], 5.4, 0.6, 0, 0.6, 'ci');
    return m;
  }
  // Quench tower: concrete tower over the quench car bay (rails run through along x), the spray header pipe up
  // the east side, the timber baffles in the stack, a water tank on the roof.
  function quenchTower() {
    const m = mk(TEX + 'wet_concrete');
    m.box('slab', [-16, -16, -16], [32, -15, 32], 'conc', { split: 'xz' });
    for (const z of [-6, 22]) m.box(`rail_${z}`, [-16, -15, z], [32, -14.4, z + 0.8], 'bright', { split: 'x' });
    for (const [n, z0, z1] of [['n', -14, -10], ['s', 26, 30]]) m.box(`pier_${n}`, [-14, -15, z0], [30, 2, z1], 'wcon', { split: 'xy' });
    m.box('lintel', [-14, 2, -14], [30, 6, 30], 'wcon', { split: 'xz' });
    m.box('stack', [-8, 6, -8], [24, 28, 24], 'wcon', { split: 'xyz' });
    for (const y of [12, 18, 24]) m.box(`baffle_${y}`, [-8.4, y, -8.4], [24.4, y + 1.2, 24.4], 'wood', { split: 'xz', hide: ['up', 'down'] });
    m.box('stack_top', [-9, 28, -9], [25, 30, -7.6], 'wcon', { split: 'x' });
    m.box('stack_top_s', [-9, 28, 23.6], [25, 30, 25], 'wcon', { split: 'x' });
    m.box('stack_top_w', [-9, 28, -7.6], [-7.6, 30, 23.6], 'wcon', { split: 'z' });
    m.box('stack_top_e', [23.6, 28, -7.6], [25, 30, 23.6], 'wcon', { split: 'z' });
    m.box('stack_void', [-7.6, 29.8, -7.6], [23.6, 30, 23.6], 'soot', { split: 'xz', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('header_riser', [30, -15, 6], [31.6, 8, 7.6], 'p_water', { split: 'y' });
    m.box('header', [6, 6.6, 6], [31.6, 8, 7.6], 'p_water', { split: 'x' });
    m.box('valve', [29.4, -6, 5.4], [32, -3.6, 8.2], 'red', { split: '' });
    return m;
  }
  // By-product plant apparatus on a concrete plinth: a vertical vessel by kind, the yellow gas inlet and outlet,
  // platform with rail at the top, a ladder.
  function byproduct(kind) {
    const m = mk('plant/green');
    m.box('plinth', [1, -16, 1], [31, -14, 31], 'conc', { split: 'xz' });
    const C = [16, 16];
    if (kind === 'cooler') {
      m.cylY('shell', C, 10, -14, 26, 'green', {});
      m.cylY('head', C, 7, 26, 29, 'green', { cap: 'material/steel' });
      for (const y of [-6, 4, 14]) m.ringY(`water_ring_${y}`, C, 10.6, 0.8, y, y + 1, 'p_water');
      m.box('water_in', [26, -12, 15], [32, -10.4, 16.6], 'p_water', { split: '' });
    } else if (kind === 'precipitator') {
      m.cylY('shell', C, 9, -14, 22, 'light', {});
      m.cylY('head', C, 6.5, 22, 25, 'light', { cap: 'material/steel' });
      for (const [x, z] of [[11, 11], [21, 11], [16, 21]]) {
        m.box(`ins_box_${x}_${z}`, [x - 2, 25, z - 2], [x + 2, 28, z + 2], 'en', { split: '' });
        m.prismY(`ins_${x}_${z}`, [x, z], 0.9, 28 - 16, 31 - 16, 'porb', {});
      }
      for (const e of m.els) if (/^ins_\d/.test(e.name)) { e.from[1] += 16; e.to[1] += 16; }
      m.box('hv_box', [24, 22, 24], [31, 30, 31], 'en', { split: '' });
    } else if (kind === 'saturator') {
      m.cylY('shell', C, 11, -14, 8, 'p_acid', {});
      m.cylY('dome', C, 8, 8, 12, 'p_acid', {});
      m.cylY('separator', [16, 16], 4, 12, 26, 'frame', { cap: 'material/steel' });
      m.box('acid_line', [0, -10, 15], [5.4, -8.4, 16.6], 'p_acid', { split: '' });
      m.box('salt_chute', [4, -14, 14], [7, -6, 18], 'frame', { split: '' });
    } else {
      m.cylY('shell', C, 8, -14, 30, 'frame', {});
      for (const y of [-6, 6, 18]) m.ringY(`flange_${y}`, C, 8.6, 1, y, y + 0.8, 'ci');
      m.box('oil_line', [24, -8, 15.2], [32, -6.6, 16.6], 'p_liq', { split: '' });
    }
    m.box('gas_in', [0, -6, 13], [7, -1, 19], 'tar', { split: '' });
    m.box('gas_out', [13, 26, 13], [19, 32, 19], 'tar', { split: '' });
    m.box('gas_out_bend', [19, 29, 13], [32, 32, 19], 'tar', { split: 'x' });
    m.ringY('rail', C, 12.4, 0.4, 22, 23.6, 'yel');
    m.ringY('deck', C, 12.4, 3, 20.6, 21, 'grat');
    ladder(m, 'ladder', 3, 3, -14, 21);
    return m;
  }
  // Coke-gas exhauster: a dark grey volute about z through (16, 16) on a base, the yellow inlet (south, open to
  // show the rotor) and the tangential outlet up the east side, the bearing pedestal on the north.
  function exhauster() {
    const m = mk('boilers/id_paint');
    m.box('base', [2, 0, 0], [30, 2.6, 16], 'frame', { split: 'x' });
    m.box('pedestal', [12, 2.6, 0], [20, 10, 2.4], 'ci', { split: '' });
    m.prism('volute', 'z', [16, 16], 12.6, 2.4, 11.4, 'idp', { split: '', ends: ['north'] });
    m.box('scroll', [16, 16, 2.4], [30, 30, 11.4], 'idp', { split: '' });
    m.box('outlet', [17, 22, 2.4], [31, 32, 11.4], 'idp', { split: '', face: { up: 'soot' } });
    m.box('outlet_flange', [16.4, 31.2, 2], [31.6, 32, 11.8], 'tar', { split: '' });
    m.ring('back', 'z', [16, 16], 12.6, 6.6, 11.4, 12, 'idp');
    m.ring('inlet', 'z', [16, 16], 6.6, 1.2, 12, 16, 'tar');
    m.box('mark', [16.6, 26, 2.2], [31.2, 27.4, 11.6], 'yel', { split: '' });
    return m;
  }
  function exhausterRotor() {
    const m = mk('material/steel');
    m.prism('coupling', 'z', [16, 16], 2.6, -0.02, 1.6, 'st', { split: '', ends: ['north', 'south'] });
    m.prism('shaft', 'z', [16, 16], 1, 1.6, 12, 'bright', { split: '' });
    m.prism('disc', 'z', [16, 16], 9.6, 3.6, 4.4, 'gal', { split: '', ends: ['south'] });
    for (const [n, a] of [['a', 0], ['b', 45], ['c', -45], ['d', 22.5], ['e', -22.5]]) {
      m.box(`blade_${n}`, [6.6, 15.6, 4.4], [25.4, 16.4, 11], 'gal', { split: '', rot: a ? { axis: 'z', angle: a, origin: [16, 16, 8] } : undefined });
    }
    for (const [n, a] of [['f', 0], ['g', 22.5], ['h', -22.5]]) {
      m.box(`blade_${n}`, [15.6, 6.6, 4.4], [16.4, 25.4, 11], 'gal', { split: '', rot: a ? { axis: 'z', angle: a, origin: [16, 16, 8] } : undefined });
    }
    m.prism('hub', 'z', [16, 16], 2.2, 10, 14, 'st', { split: '', ends: ['south'] });
    return m;
  }
  // Electrode extruder: mixing container along x on a bed, the feed hopper open over the screw, the die at the
  // east end with the black green electrode coming out on rollers, the drive at the west end.
  function extruder() {
    const m = mk('plant/structure');
    m.box('bed', [-16, 0, 2], [32, 2.4, 14], 'frame', { split: 'x' });
    // the container, open under the feed hopper so the screw shows
    m.prism('barrel_w', 'x', [8, 8], 5, -6, 0, 'frame', { split: '', ends: ['west'] });
    m.prism('barrel_e', 'x', [8, 8], 5, 10, 20, 'frame', { split: '' });
    m.box('trough', [0, 2.4, 3], [10, 3.6, 13], 'frame', { split: '' });
    m.box('hopper_n', [0, 3.6, 1.6], [10, 16, 3.2], 'frame', { split: 'y' });
    m.box('hopper_s', [0, 3.6, 12.8], [10, 16, 14.4], 'frame', { split: 'y' });
    m.box('hopper_w', [-0.6, 13, 3.2], [0.6, 16, 12.8], 'frame', { split: '' });
    m.box('hopper_e', [9.4, 13, 3.2], [10.6, 16, 12.8], 'frame', { split: '' });
    for (const x of [-6, 20]) m.box(`flange_${x}`, [x - 0.6, 2.4, 2.4], [x + 0.6, 14, 13.6], 'ci', { split: '' });
    m.box('die', [20, 4, 4.6], [24, 12, 11.4], 'ci', { split: '' });
    m.prism('electrode', 'x', [8, 8], 2.6, 24, 32, 'gph', { split: '', ends: ['east'] });
    for (const x of [26, 30]) m.box(`roller_${x}`, [x - 0.6, 2.4, 5], [x + 0.6, 5.4, 11], 'st', { split: '' });
    m.box('drive', [-16, 2.4, 3], [-8, 13, 13], 'blue', { split: '' });
    m.box('gearbox', [-8, 2.4, 4.6], [-6.6, 11.4, 11.4], 'ci', { split: '' });
    return m;
  }
  function extruderScrew() {
    const m = mk('material/steel');
    m.prism('core', 'x', [8, 8], 1.4, -6.6, 20, 'bright', { split: '' });
    for (let i = 0; i < 12; i++) {
      const x = -5 + i * 2.1;
      const a = [0, 45, -45, 22.5][i % 4], rot = a ? { axis: 'x', angle: a, origin: [8, 8, 8] } : undefined;
      m.box(`flight_${i}`, [x, 4.4, 7.4], [x + 0.6, 11.6, 8.6], 'st', { split: '', rot });
      m.box(`flight_${i}_b`, [x + 0.3, 7.4, 4.4], [x + 0.9, 8.6, 11.6], 'st', { split: '', rot });
    }
    return m;
  }
  // Ring baking furnace section: brick pits under cast lids in two rows, the flue main between them, the firing
  // bridge with burners and a yellow crane rail; lit: the burner ports and the lid gaps glow.
  function bakingFurnace(lit) {
    const m = mk('material/brick');
    m.box('body', [-16, 0, -16], [32, 12, 32], 'brick', { split: 'xyz' });
    for (const z of [-12, 16]) for (let i = 0; i < 4; i++) {
      const x = -14 + i * 11.4;
      m.box(`lid_${z}_${i}`, [x, 12, z], [x + 10, 13, z + 12], 'ci', { split: '' });
      if (lit) m.box(`glow_gap_${z}_${i}`, [x - 0.3, 12, z - 0.3], [x + 10.3, 12.2, z + 12.3], 'fglow', { split: '', hide: ['down'] });
    }
    m.box('flue_main', [-16, 12, 2], [32, 15, 14], 'brick', { split: 'x' });
    for (const x of [-15, 31]) m.box(`bridge_leg_${x}`, [x, 12, -4], [x + 1, 20, 20], 'yel', { split: '' });
    m.box('bridge', [-16, 18, 4], [32, 20, 12], 'yel', { split: 'x' });
    for (const x of [-4, 8, 20]) {
      m.box(`burner_${x}`, [x - 1.2, 13, 6.6], [x + 1.2, 18, 9.4], 'ci', { split: '' });
      m.box((lit ? 'glow_' : '') + `port_${x}`, [x - 0.8, 12.9, 7.2], [x + 0.8, 13, 8.8], lit ? 'fglow' : 'soot', { split: '' });
    }
    m.box('gas_main', [-16, 20, 7], [32, 21.4, 8.4], 'p_gas', { split: 'x' });
    return m;
  }

  // ---------------------------------------------------------------- steelmaking
  // BOF base: concrete piers carrying the trunnion bearings on the axis (y 15, z 8), the tilting drive with its
  // bull gear on the east pier, the slag pot pit and the charging floor edge.
  function bofBase() {
    const m = mk('plant/concrete');
    m.box('pit', [-12, 0, -16], [28, 0.6, 32], 'conc', { split: 'xz' });
    for (const [n, x0, x1] of [['w', -16, -10], ['e', 26, 32]]) {
      m.box(`pier_${n}`, [x0, 0, 1], [x1, 12, 15], 'conc', { split: 'y' });
      m.box(`bearing_${n}`, [x0 + 0.6, 12, 4], [x1 - 0.6, 18.4, 12], 'ci', { split: '' });
      m.box(`bearing_cap_${n}`, [x0 + 0.2, 18.4, 3.6], [x1 - 0.2, 19.6, 12.4], 'ci', { split: '' });
    }
    m.prism('bull_gear', 'x', [15, 8], 7, 31, 32, 'ci', { split: '', ends: ['east'] });
    m.box('drive', [26, 0, 16], [32, 10, 26], 'mblue', { split: '' });
    for (const z of [-12, 26]) m.box(`rail_${z}`, [-12, 0.6, z], [28, 1.2, z + 1], 'bright', { split: 'x' });
    m.box('slag_pot', [-2, 0.6, 18], [8, 6, 28], 'sst', { split: '' });
    return m;
  }
  // BOF vessel partial: pear-shaped soot-black shell about the vertical axis (x 8, z 8), trunnion ring round the
  // belly with the trunnion shafts to the bearings, the cone and the glowing mouth.
  function bofVessel() {
    const m = mk(TEX + 'soot_steel');
    m.cylY('bottom', [8, 8], 8, -6, -3, 'sst', {});
    m.cylY('belly', [8, 8], 10, -3, 18, 'sst', { cap: TEX + 'soot_steel' });
    m.cylY('cone_1', [8, 8], 8.6, 18, 22, 'sst', { cap: TEX + 'soot_steel' });
    m.cylY('cone_2', [8, 8], 7, 22, 25, 'sst', { cap: TEX + 'soot_steel' });
    m.cylY('cone_3', [8, 8], 5.6, 25, 28, 'sst', { cap: TEX + 'soot_steel' });
    m.ringY('mouth_ring', [8, 8], 5.6, 1.4, 28, 30.4, 'ci');
    m.box('glow_mouth', [4, 28.4, 4], [12, 29.4, 12], 'melt', { split: '', hide: ['down'] });
    m.ringY('trunnion_ring', [8, 8], 11, 1.2, 12, 18, 'ci');
    m.prism('trunnion_w', 'x', [15, 8], 2.4, -14, -2, 'bright', { split: '', ends: ['west'] });
    m.prism('trunnion_e', 'x', [15, 8], 2.4, 18, 30, 'bright', { split: '', ends: ['east'] });
    m.box('taphole', [17.4, 22, 6.6], [19.6, 24, 9.4], 'ci', { split: '' });
    return m;
  }
  // Oxygen lance carriage: a frame on the guide column with the hoist drum, the blue oxygen and water hoses; the
  // lance (partial) is the chrome tube with its copper nozzle tip.
  function lanceCarriage() {
    const m = mk('plant/yellow');
    m.box('carriage', [3, 6, 3], [13, 16, 13], 'yel', { split: '' });
    m.box('carriage_window', [4, 8, 2.9], [12, 14, 3], 'soot', { split: '', hide: ['south'] });
    m.box('guide', [13, 0, 6], [16, 16, 10], 'frame', { split: 'y' });
    m.prism('hoist', 'x', [14, 8], 1.6, 4, 12, 'ci', { split: '', ends: ['east', 'west'] });
    m.box('clamp', [5.6, 4.4, 5.6], [10.4, 6, 10.4], 'st', { split: '' });
    m.box('hose_o2', [0, 10, 5], [3, 12, 7], 'p_air', { split: '' });
    m.box('hose_water_in', [0, 12.4, 9], [3, 14.4, 11], 'p_water', { split: '' });
    m.box('hose_water_out', [0, 7, 9], [3, 9, 11], 'p_water', { split: '' });
    return m;
  }
  function lance() {
    const m = mk('material/bright_steel');
    const h = 1.4 * Math.tan(Math.PI / 8);
    m.box('tube_a', [6.6, -14, 8 - h], [9.4, 6, 8 + h], 'bright', { split: 'y' });
    m.box('tube_b', [8 - h, -14, 6.6], [8 + h, 6, 9.4], 'bright', { split: 'y' });
    m.box('tube_c', [6.6, -14, 8 - h], [9.4, 6, 8 + h], 'bright', { split: '', rot: { axis: 'y', angle: 45, origin: [8, 8, 8] } });
    m.box('tube_d', [6.6, -14, 8 - h], [9.4, 6, 8 + h], 'bright', { split: '', rot: { axis: 'y', angle: -45, origin: [8, 8, 8] } });
    m.box('tip', [6.8, -16, 6.8], [9.2, -14, 9.2], 'cu', { split: '' });
    return m;
  }
  // Ladle treatment station: grating deck on four columns over the ladle place, three ferroalloy bins with
  // chutes and gates, the wire feeder, the argon line with its hose down to the ladle position.
  function ladleStation() {
    const m = mk(TEX + 'grating');
    for (const [x, z] of [[-16, -16], [30, -16], [-16, 30], [30, 30]]) m.box(`col_${x}_${z}`, [x, 0, z], [x + 2, 18, z + 2], 'frame', { split: 'y' });
    m.box('deck', [-16, 18, -16], [32, 19, 32], 'grat', { split: 'xz' });
    m.box('deck_edge', [-16, 17, -16], [32, 18, -15], 'yel', { split: 'x' });
    for (const [i, x] of [[0, -10], [1, 4], [2, 18]]) {
      m.box(`bin_${i}`, [x, 22, -6], [x + 10, 30, 6], 'frame', { split: '' });
      m.box(`bin_${i}_cone`, [x + 2, 19, -4], [x + 8, 22, 4], 'frame', { split: '' });
      m.box(`chute_${i}`, [x + 4, 11, -1], [x + 6, 19, 1], 'st', { split: '' });
      m.box(`gate_${i}`, [x + 3.4, 15, -1.6], [x + 6.6, 16, 1.6], 'red', { split: '' });
    }
    m.box('rail', [-16, 19, 28], [32, 22, 28.6], 'yel', { split: 'x' });
    m.box('wire_feeder', [20, 19, 14], [28, 25, 22], 'blue', { split: '' });
    m.box('wire', [23.6, 6, 17.6], [24.4, 19, 18.4], 'bright', { split: '' });
    m.box('argon_line', [-16, 2, 10], [32, 3, 11], 'p_gas', { split: 'x' });
    m.box('argon_hose', [-2, 3, 10], [-1, 12, 11], 'rubber', { split: '' });
    return m;
  }
  // EAF shell (3x3x3 round the anchor): a refractory hearth on the rocker cradle, the shell of green
  // water-cooled panels, the spout to the north and the slag door, the tilting cylinders.
  function eafShell() {
    const m = mk(TEX + 'eaf_panel');
    m.box('cradle', [-14, -16, -10], [30, -12, 26], 'ci', { split: 'xz' });
    for (const x of [-10, 26]) m.box(`rocker_${x}`, [x - 2, -16, -6], [x, -10, 22], 'ci', { split: '' });
    // the shell fills the cube up to the roof segment, which sits on it at y 32
    m.cylY('hearth', [8, 8], 15, -12, 0, 'fb', { cap: 'boilers/firebrick' });
    m.cylY('shell', [8, 8], 15.4, 0, 32, 'eaf', {});
    for (const y of [2, 12, 22, 30]) m.ringY(`header_${y}`, [8, 8], 16, 0.8, y, y + 1, 'p_water');
    m.box('spout', [3, -2, -16], [13, 1, -6], 'fb', { split: '' });
    m.box('spout_shell', [2.4, -3, -16], [13.6, -1.8, -6], 'ci', { split: '' });
    m.box('slag_door', [2, 4, 22.6], [14, 14, 24], 'eaf', { split: '' });
    m.box('slag_door_frame', [1, 3, 22.8], [15, 15, 23.6], 'ci', { split: '' });
    for (const x of [-12, 28]) m.box(`tilt_cyl_${x}`, [x - 1, -16, 18], [x + 1, -6, 20], 'bright', { split: '' });
    return m;
  }
  // EAF roof segment: the refractory roof ring and the delta with the three electrode ports, the fume elbow;
  // the three graphite electrodes on their clamp arms up the mast (arcing: the arc glow under the roof).
  function eafRoof(arcing) {
    const m = mk(TEX + 'eaf_panel');
    m.cylY('roof_ring', [8, 8], 15.4, -16, -13, 'eaf', { cap: TEX + 'eaf_panel' });
    m.cylY('roof_dome', [8, 8], 12, -13, -10, 'fb', { cap: 'boilers/firebrick' });
    m.cylY('delta', [8, 8], 6, -10, -7, 'fb', { cap: 'material/cast_iron' });
    m.box('fume_elbow', [16, -13, 2], [26, -6, 12], 'eaf', { split: '' });
    m.box('fume_duct', [26, -10, 2], [32, -4, 12], 'eaf', { split: '' });
    const ports = [[4.4, 6], [11.6, 6], [8, 12.2]];
    ports.forEach(([x, z], i) => {
      m.box(`electrode_${i}`, [x - 1.4, -9, z - 1.4], [x + 1.4, 10, z + 1.4], 'gph', { split: 'y' });
      m.box(`clamp_${i}`, [x - 2, 6, z - 2], [x + 2, 8, z + 2], 'cu', { split: '' });
      m.box(`arm_${i}`, [-6, 6.4 + i * 0.01, z - 0.8], [x - 2, 7.6, z + 0.8], 'cu', { split: '' });
      if (arcing) m.box(`glow_arc_${i}`, [x - 1, -16, z - 1], [x + 1, -12, z + 1], 'melt', { split: '' });
    });
    m.box('mast', [-10, -16, 2], [-6, 12, 14], 'frame', { split: 'y' });
    m.box('busbar_tubes', [-6, 9, 3], [-2, 10, 13], 'cu', { split: '' });
    return m;
  }
  // Ingot mould (1x2): cast-iron mould tapered by steps on a stool, lifting lugs; filled: the glowing top.
  function ingotMould(filled) {
    const m = mk('material/cast_iron');
    m.box('stool', [1, 0, 1], [15, 2, 15], 'ci', { split: '' });
    m.box('body_lo', [2.4, 2, 2.4], [13.6, 14, 13.6], 'ci', { split: '' });
    m.box('body_hi', [3, 14, 3], [13, 28, 13], 'ci', { split: 'y' });
    m.box('hot_top', [2.6, 28, 2.6], [13.4, 30, 13.4], 'fb', { split: '' });
    for (const x of [1.6, 13.6]) m.box(`lug_${x}`, [x, 20, 6.6], [x + 0.8, 23, 9.4], 'ci', { split: '' });
    if (filled) m.box('glow_metal', [4, 29.6, 4], [12, 30.4, 12], 'melt', { split: '', hide: ['down'] });
    else m.box('cavity', [4, 29.95, 4], [12, 30.05, 12], 'soot', { split: '', hide: ['down'] });
    return m;
  }

  const BUILDS = {
    coke_battery_wall: { model: () => batteryWall(false), cat: 'coke' }, coke_battery_wall_hot: { model: () => batteryWall(true), cat: 'coke' },
    coke_pusher_ram: { model: pusher, cat: 'coke' }, 'coke_pusher_ram/ram': { model: ram, cat: 'coke', java: 'coke_pusher_ram/partial/ram' },
    gas_collector_main: { model: collector, cat: 'coke' }, gas_collector_main_side: { model: collectorSide, cat: 'coke' },
    quench_tower: { model: quenchTower, cat: 'coke' },
    byproduct_plant: { model: () => byproduct('cooler'), cat: 'coke' },
    byproduct_plant_precipitator: { model: () => byproduct('precipitator'), cat: 'coke' },
    byproduct_plant_saturator: { model: () => byproduct('saturator'), cat: 'coke' },
    byproduct_plant_scrubber: { model: () => byproduct('scrubber'), cat: 'coke' },
    gas_exhauster: { model: exhauster, cat: 'coke' }, 'gas_exhauster/rotor': { model: exhausterRotor, cat: 'coke', java: 'gas_exhauster/partial/rotor' },
    electrode_extruder: { model: extruder, cat: 'coke' },
    'electrode_extruder/screw': { model: extruderScrew, cat: 'coke', java: 'electrode_extruder/partial/screw' },
    baking_furnace: { model: () => bakingFurnace(false), cat: 'coke' }, baking_furnace_lit: { model: () => bakingFurnace(true), cat: 'coke' },
    bof_vessel: { model: bofBase, cat: 'steelmaking' }, 'bof_vessel/vessel': { model: bofVessel, cat: 'steelmaking', java: 'bof_vessel/partial/vessel' },
    oxygen_lance: { model: lanceCarriage, cat: 'steelmaking' }, 'oxygen_lance/lance': { model: lance, cat: 'steelmaking', java: 'oxygen_lance/partial/lance' },
    ladle_station: { model: ladleStation, cat: 'steelmaking' },
    electric_arc_furnace: { model: eafShell, cat: 'steelmaking' },
    electric_arc_furnace_roof: { model: () => eafRoof(false), cat: 'steelmaking' },
    electric_arc_furnace_roof_arcing: { model: () => eafRoof(true), cat: 'steelmaking' },
    ingot_mold: { model: () => ingotMould(false), cat: 'steelmaking' }, ingot_mold_filled: { model: () => ingotMould(true), cat: 'steelmaking' },
  };

  function cullBoundary() {
    for (const c of Cube.all) for (const [d, a, v] of [['north', 2, 0], ['south', 2, 16], ['west', 0, 0], ['east', 0, 16], ['down', 1, 0], ['up', 1, 16]]) {
      const on = (v === 0 ? c.from[a] : c.to[a]) === v && !c.rotation.some((r) => r);
      if (on && [0, 1, 2].every((i) => c.from[i] >= 0 && c.to[i] <= 16) && c.faces[d].texture !== null) c.faces[d].cullface = d;
    }
  }
  function realize(root, id) {
    const m = BUILDS[id].model();
    PCART.realize(root, m, id);
    if (m.cull) cullBoundary();
  }
  window.STL = {
    paint, ids: Object.keys(BUILDS), build: realize,
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    async exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        realize(root, id);
        await new Promise((r) => setTimeout(r, 1200));
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
      }
      return out;
    },
  };
})();
