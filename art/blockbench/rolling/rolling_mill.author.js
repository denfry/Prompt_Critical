// The rolling and pipe shop of 08, hand-authored for Blockbench (replaces the spec_metallurgy*.py placeholders):
// the two-high mill stand (duo 500) with its rolls, the roller table and the cooling bed, the piercing mill
// with its barrel rolls, the ERW pipe welder with its forming rolls, the hydrotest bench, the heat-treatment
// furnace, the quench tank and the pusher reheating furnace.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/rolling/rolling_mill.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); ROL.paint('<repo>')
//   await ROL.exportAll('<repo>')
//
// Front to the north. The rolls are static multipart parts (PcBlockStateProvider): rolling_mill_stand +
// partial/roll_top + partial/roll_bottom, roller_table + partial/rollers, piercing_mill + partial/roll_l +
// partial/roll_r, pipe_welder + partial/forming_rolls. Footprints as the placeholders: mill stand 3x3x1
// (x -16..32, y -16..32, z 0..16), piercing mill and pipe welder 3x2x1 (x -16..32), hydrotest bench 3x1x1,
// heat-treatment furnace 2x2x2 and quench tank 2x1x2 (0..32), reheating furnace 3x2x3 (x, z -16..32).
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'rolling/';
  const T = {};

  // ---------------------------------------------------------------- textures (32 px per block)
  // Machine blue of the mill housings, scuffed and oily at the bottom.
  T.mill_blue = () => {
    const im = img(32, 32), r = grain(im, [54, 82, 124], 7101, 4, 6, 8);
    for (let y = 22; y < 32; y++) for (let x = 0; x < 32; x++) if (r() < 0.25) put(im, x, y, add(get(im, x, y), -18));
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [150, 160, 170]);
    return im;
  };
  // Polished roll barrel: bright steel with circumferential streaks and a dark oil film band.
  T.roll = () => {
    const im = img(32, 32), r = rng(7111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([170, 174, 176], (x % 7 === 0 ? 14 : 0) + Math.sin(y * 0.4) * 8 + (r() - 0.5) * 8));
    return im;
  };
  // Hot steel: orange-white glow with scale flecks.
  T.hot = () => {
    const im = img(32, 32), r = rng(7121), n = vnoise(r, 32, 32, 6);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([255, 120, 30], [255, 220, 120], n(x, y)), (r() - 0.5) * 16 - (r() < 0.04 ? 60 : 0)));
    return im;
  };
  // Furnace interior glow with flame tongues (for windows and open doors).
  T.furnace_glow = () => {
    const im = img(32, 32), r = rng(7131), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([230, 90, 20], [255, 210, 90], n(x, y) * (1 - y / 48)), (r() - 0.5) * 12));
    return im;
  };
  // Quench media: water (green tinted, ripples) and oil (brown, sheen).
  const liquid = (base, hi, seed) => () => {
    const im = img(32, 32), r = rng(seed);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix(base, hi, (Math.sin(x * 0.5 + Math.sin(y * 0.3) * 3) + 1) / 4), (r() - 0.5) * 8));
    return im;
  };
  T.water = liquid([46, 92, 84], [120, 170, 160], 7141);
  T.oil = liquid([60, 40, 22], [140, 104, 60], 7151);
  // Cooling bed rack: notched skid bars along the bed on a dark ground.
  T.rack = () => {
    const im = img(32, 32), r = rng(7161);
    rect(im, 0, 0, 32, 32, [40, 40, 42]);
    for (let x = 1; x < 32; x += 4) for (let y = 0; y < 32; y++) put(im, x, y, y % 4 === 0 ? [80, 82, 84] : add([128, 130, 132], (r() - 0.5) * 12));
    return im;
  };
  // Mineral wool cladding of the heat-treatment furnace (light grey sheet with seams).
  T.htf_casing = () => {
    const im = img(32, 32), r = grain(im, [176, 172, 160], 7171, 3, 4, 8);
    for (let i = 0; i < 32; i++) { put(im, i, 15, [140, 136, 126]); put(im, 15, i, [140, 136, 126]); }
    return im;
  };
  // Mill scale on the floor plates of the line, and the bed of the bench.
  T.bed = () => { const im = img(32, 32), r = grain(im, [64, 64, 66], 7181, 6, 6, 8); for (let k = 0; k < 30; k++) put(im, r() * 32, r() * 32, [92, 70, 54]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    mblue: { tex: TEX + 'mill_blue', P: 16 }, roll: { tex: TEX + 'roll', P: 16 }, hot: { tex: TEX + 'hot', P: 16 },
    fglow: { tex: TEX + 'furnace_glow', P: 16 }, water: { tex: TEX + 'water', P: 16 }, oil: { tex: TEX + 'oil', P: 16 },
    rack: { tex: TEX + 'rack', P: 16 }, htf: { tex: TEX + 'htf_casing', P: 16 }, bed: { tex: TEX + 'bed', P: 16 },
    fb: { tex: 'boilers/firebrick', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 },
    gal: { tex: 'disconnector/galvanised', P: 16 }, louv: { tex: 'grid_lines/louvre', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 }, l_off: { tex: 'grid_lines/lamp_off', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }

  // ---------------------------------------------------------------- mill stand
  // Two-high stand (duo 500, rolls 0.6 m) at real scale, the drive on the west where the Create port is (cell
  // (-1, 0, 0)): a pinion stand x -16..-8 takes the Create shaft on its west face at (y 8, z 8) and splits it to
  // the two rolls; short spindles with wobbler couplings x -8..-4; the closed-top housings in machine blue at
  // x -4..4 and 24..32 on soleplates bolted to the foundation, chocks round the roll necks with the balance springs
  // between them, the hand screwdown (screws, brass nuts, a worm shaft across both caps with its hand wheel), entry
  // and exit guides at the pass line (y 8), and cooling water sprays along the barrels. Rolls (partials roll_top at
  // y 14, roll_bottom at y 2, axis along x at z 8): barrel x 4..24.
  const HOUS = [[-4, 4], [24, 32]];
  function millStand() {
    const m = mk(TEX + 'mill_blue');
    m.box('foundation', [-16, -16, 0], [32, -13, 16], 'conc', { split: 'x' });
    for (let x = -14; x <= 30; x += 4) for (const z of [0.6, 14.6]) m.box(`hd_bolt_${x}_${z}`, [x - 0.4, -13, z], [x + 0.4, -12.2, z + 0.8], 'bright', { split: '' });
    HOUS.forEach(([x0, x1], k) => {
      const n = k ? 'e' : 'w';
      m.box(`sole_${n}`, [x0 - 0.6, -13, 0], [x1 + (k ? 0 : 0.6), -12, 16], 'ci', { split: '' });
      m.box(`post_${n}_n`, [x0, -12, 0], [x1, 26, 3], 'mblue', { split: 'y' });
      m.box(`post_${n}_s`, [x0, -12, 13], [x1, 26, 16], 'mblue', { split: 'y' });
      m.box(`cap_${n}`, [x0, 22, 3], [x1, 27, 13], 'mblue', { split: '' });
      m.box(`sill_${n}`, [x0, -12, 3], [x1, -8, 13], 'mblue', { split: '' });
      for (const [z0, z1] of [[-0.3, 0], [16, 16.3]]) for (const y of [-8, 0, 16, 24]) m.box(`post_bolt_${n}_${y}_${z0}`, [x0 + 1, y, z0], [x0 + 2, y + 1, z1], 'bright', { split: '' });
      // chocks: bottom on the sill, top hung from the screw, balance springs between them
      m.box(`chock_lo_${n}`, [x0 + 0.4, -8, 3.2], [x1 - 0.4, 6.4, 12.8], 'ci', { split: 'y' });
      m.box(`chock_hi_${n}`, [x0 + 0.4, 9.6, 3.2], [x1 - 0.4, 20, 12.8], 'ci', { split: 'y' });
      for (const z of [4, 11]) m.box(`spring_${n}_${z}`, [x0 + 1.4, 6.4, z], [x0 + 2.6, 9.6, z + 1], 'st', { split: '' });
      m.box(`screw_${n}`, [x0 + 2.8, 20, 6.8], [x1 - 2.8, 30, 9.2], 'bright', { split: '' });
      m.box(`nut_${n}`, [x0 + 1, 27, 5], [x1 - 1, 29, 11], 'br', { split: '' });
      m.box(`worm_case_${n}`, [x0 + 0.6, 29, 4.6], [x1 - 0.6, 31.6, 11.4], 'mblue', { split: '' });
    });
    // worm shaft across both caps, hand wheel on the east end
    m.prism('worm_shaft', 'x', [30.3, 8], 0.9, -3, 31, 'bright', { split: '', ends: ['east', 'west'] });
    m.disc('hand_wheel', 'x', [30.3, 8], 1.6, 31, 31.6, 'ci', 'material/cast_iron', 'east');
    // pinion stand on the drive side: housing with the bearing caps of the two pinions and the input boss
    m.box('pinion_stand', [-15.4, -12, 1], [-8, 20, 15], 'mblue', { split: 'y' });
    m.box('pinion_sole', [-16, -13, 0], [-7.4, -12, 16], 'ci', { split: '' });
    m.box('pinion_parting', [-15.6, 7.6, 0.6], [-7.8, 8.4, 15.4], 'ci', { split: '' });
    for (const y of [2, 14]) m.disc(`pinion_cap_${y}`, 'x', [y, 8], 3.6, -8, -7.2, 'ci', 'material/cast_iron', 'east');
    m.disc('input_boss', 'x', [8, 8], 3, -15.9, -15.4, 'ci', 'material/cast_iron', 'west');
    // entry and exit guides at the pass line, with their tables
    for (const [n, z0, z1] of [['entry', 0, 3], ['exit', 13, 16]]) {
      m.box(`guide_${n}`, [4, 6.2, z0], [24, 7.2, z1], 'st', { split: '' });
      m.box(`guide_${n}_top`, [4, 8.8, z0], [24, 9.6, z1], 'st', { split: '' });
      for (const x of [6, 21]) m.box(`guide_${n}_cheek_${x}`, [x, 7.2, z0], [x + 1, 8.8, z1], 'st', { split: '' });
    }
    // cooling water: a header along each side of the barrels, GOST 14202 water green
    m.prism('spray_n', 'x', [19.5, 3.6], 0.6, 4, 24, 'p_water', { split: '', ends: ['east', 'west'] });
    m.prism('spray_s', 'x', [19.5, 12.4], 0.6, 4, 24, 'p_water', { split: '', ends: ['east', 'west'] });
    m.box('pass_mark', [4, -12, 7.4], [24, -11.8, 8.6], 'yel', { split: '' });
    return m;
  }
  // A roll: barrel between the housings, necks into the chocks, a wobbler and a short spindle west to the pinion
  // stand. About x through (y cy, z 8).
  function roll(cy) {
    const m = mk(TEX + 'roll');
    m.prism('barrel', 'x', [cy, 8], 5, 4, 24, 'roll', { split: '', ends: ['east', 'west'] });
    m.prism('neck_w', 'x', [cy, 8], 3, -4, 4, 'bright', { split: '' });
    m.prism('neck_e', 'x', [cy, 8], 3, 24, 31.4, 'bright', { split: '', ends: ['east'] });
    m.box('wobbler', [-6, cy - 2.6, 5.4], [-4, cy + 2.6, 10.6], 'st', { split: '' });
    m.box('wobbler_b', [-6, cy - 1.2, 4], [-4, cy + 1.2, 12], 'st', { split: '' });
    m.box('spindle', [-8, cy - 1.4, 6.6], [-6, cy + 1.4, 9.4], 'bright', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- roller table and cooling bed
  // Roller table: channel side frames, cross bearers, bevel boxes on the Create shaft line, roller bearings; the
  // cooling bed carries notched skids over the frame instead of rollers.
  function rollerTable(bed) {
    const m = mk('material/steel');
    for (const [n, x0] of [['w', 0], ['e', 14.4]]) {
      m.box(`side_${n}`, [x0, 0, 0], [x0 + 1.6, 6.4, 16], 'frame', { split: 'z' });
      m.box(`flange_${n}`, [x0 - 0.2, 6.4, 0], [x0 + 1.8, 7, 16], 'frame', { split: 'z' });
    }
    for (const z of [1, 14.6]) m.box(`bearer_${z}`, [1.6, 0, z], [14.4, 1.4, z + 0.4], 'frame', { split: '' });
    if (bed) {
      // skids clear of the Create shaft that the renderer runs through the centre (x 8, y 8, along z)
      for (const x of [1.8, 3.9, 11.3, 13.4]) m.box(`skid_${x}`, [x, 6, 0], [x + 0.8, 9, 16], 'st', { split: 'z', face: { up: 'rack' } });
      m.box('rack_beam_n', [1.6, 4.6, 3], [14.4, 6, 4], 'frame', { split: '' });
      m.box('rack_beam_s', [1.6, 4.6, 12], [14.4, 6, 13], 'frame', { split: '' });
      for (const z of [0.4, 15]) m.box(`shaft_bearing_${z}`, [6.6, 6.2, z], [9.4, 9.8, z + 0.6], 'ci', { split: '' });
    } else {
      // bevel boxes where the rollers meet the Create shaft (the renderer's shaft runs along z through x 8, y 8)
      for (const z of [4, 12]) {
        m.box(`bevel_${z}`, [6, 5.6, z - 1.8], [10, 8.8, z + 1.8], 'ci', { split: '' });
        m.box(`bearing_w_${z}`, [0.2, 6, z - 1], [1.4, 8.8, z + 1], 'ci', { split: '' });
        m.box(`bearing_e_${z}`, [14.6, 6, z - 1], [15.8, 8.8, z + 1], 'ci', { split: '' });
      }
    }
    m.box('scale_pan', [1.6, 0, 1.4], [14.4, 0.4, 14.6], 'bed', { split: '' });
    return m;
  }
  // Two rollers across the table, each in two halves either side of its bevel box; tops at y 9 under the piece
  // the renderer lays at y 9.5.
  function tableRollers() {
    const m = mk(TEX + 'roll');
    for (const z of [4, 12]) for (const [n, x0, x1] of [['w', 1.6, 6], ['e', 10, 14.4]]) {
      m.prism(`roller_${z}_${n}`, 'x', [7.4, z], 1.6, x0, x1, 'roll', { split: '', ends: ['east', 'west'] });
      m.prism(`journal_${z}_${n}`, 'x', [7.4, z], 0.6, n === 'w' ? 0.4 : 14.4, n === 'w' ? 1.6 : 15.6, 'bright', { split: '' });
    }
    return m;
  }

  // ---------------------------------------------------------------- piercing mill
  // Cross-roll piercing mill: grey housings round the two barrel rolls, the entry trough to the west, the
  // mandrel bar and its thrust block to the east, the roll drive covers.
  function piercing() {
    const m = mk('plant/structure');
    m.box('bed', [-16, 0, 0], [32, 2, 16], 'bed', { split: 'x' });
    for (const [n, x0, x1] of [['w', -14, -5], ['e', 21, 30]]) {
      m.box(`housing_${n}`, [x0, 2, 0], [x1, 18, 16], 'frame', { split: 'y' });
      m.box(`housing_${n}_top`, [x0 - 0.4, 18, -0.4], [x1 + 0.4, 20, 16.4], 'frame', { split: '' });
      m.box(`window_${n}`, [x0 - 0.05, 6, 2], [x1 + 0.05, 13, 14], 'soot', { split: '' });
    }
    m.box('top_cover', [-5, 16, 1], [21, 18, 15], 'frame', { split: 'x' });
    m.box('cover_ribs', [-5, 18, 7], [21, 19, 9], 'frame', { split: '' });
    m.box('trough', [-16, 6.4, 6.2], [-14, 8, 9.8], 'st', { split: '' });
    m.box('trough_side_n', [-16, 8, 5.8], [-14, 10.6, 6.2], 'st', { split: '' });
    m.box('trough_side_s', [-16, 8, 9.8], [-14, 10.6, 10.2], 'st', { split: '' });
    m.box('plug', [6.6, 7.6, 6.6], [9.4, 10.4, 9.4], 'hot', { split: '' });
    m.prism('mandrel', 'x', [9, 8], 0.9, 9.4, 32, 'bright', { split: '' });
    m.box('thrust_block', [30, 2, 4], [32, 16, 12], 'ci', { split: '' });
    m.box('drive_cover', [21, 18, 2], [30, 26, 14], 'blue', { split: '' });
    return m;
  }
  // Barrel roll: thickest in the middle, stepping down to both ends.
  function barrelRoll(z) {
    const m = mk(TEX + 'roll');
    for (const [n, r, x0, x1] of [['mid', 3, 4, 12], ['w', 2.6, -1, 4], ['e', 2.6, 12, 17], ['ww', 2.2, -4, -1], ['ee', 2.2, 17, 20]]) {
      m.prism(`barrel_${n}`, 'x', [9, z], r, x0, x1, 'roll', { split: '', ends: ['east', 'west'] });
    }
    return m;
  }

  // ---------------------------------------------------------------- pipe welder
  // ERW line on a long bed: the forming section (rolls are the partial) to the west, the HF induction coil and
  // the squeeze rolls in the middle (the seam glows while welding), the welded pipe running out east under the
  // scarfing tool, the HF generator cabinet on the south side and the cut-off carriage at the east end.
  function welder(welding) {
    const m = mk('switchgear_cell/enamel');
    m.box('bed', [-16, 0, 2], [32, 4, 14], 'bed', { split: 'x' });
    for (let i = 0; i < 4; i++) {
      const x = -13 + i * 4.4;
      m.box(`stand_${i}_n`, [x, 4, 2.4], [x + 1.6, 16, 4], 'mblue', { split: '' });
      m.box(`stand_${i}_s`, [x, 4, 12], [x + 1.6, 16, 13.6], 'mblue', { split: '' });
      m.box(`stand_${i}_top`, [x, 16, 2.4], [x + 1.6, 17, 13.6], 'mblue', { split: '' });
    }
    m.prism('strip', 'x', [11, 8], 2.4, -16, 4, 'st', { split: '' });
    m.ring('coil', 'x', [11, 8], 3.4, 0.7, 5, 7, 'cu');
    m.box('coil_leads', [5.4, 14, 10], [6.6, 20, 11], 'cu', { split: '' });
    for (const z of [3.6, 12.4]) m.prism(`squeeze_${z}`, 'z', [9, 11], 1.6, z - 1, z + 1, 'roll', { split: '', ends: ['north', 'south'] });
    m.box((welding ? 'glow_' : '') + 'seam', [5, 13.3, 7.6], [11, 13.7, 8.4], welding ? 'hot' : 'st', { split: '' });
    m.prism('pipe', 'x', [11, 8], 2.4, 7, 32, 'st', { split: '' });
    m.box('scarf_tool', [13.4, 13.4, 7.2], [14.6, 16, 8.8], 'ci', { split: '' });
    m.box('hf_cabinet', [10, 4, 12], [26, 27, 14], 'en', { split: 'y' });
    m.box('hf_vents', [11, 20, 11.95], [25, 25, 12], 'louv', { split: '', hide: ['south'] });
    for (const x of [13, 17, 21]) m.box(`hf_meter_${x}`, [x, 14, 11.6], [x + 3, 17, 12], 'rubber', { split: '' });
    m.box('carriage', [26, 4, 2.4], [32, 18, 11], 'mblue', { split: '' });
    m.box('saw_guard', [27, 14, 3], [31, 20, 10.4], 'yel', { split: '' });
    return m;
  }
  // Forming rolls partial: four pairs of upper and lower rolls on the stands, the edge rolls between.
  function formingRolls() {
    const m = mk(TEX + 'roll');
    for (let i = 0; i < 4; i++) {
      const x = -12.2 + i * 4.4;
      m.box(`lower_${i}`, [x - 1.4, 8, 3], [x + 1.4, 9.6, 13], 'roll', { split: '' });
      m.box(`upper_${i}`, [x - 1.2, 12.6, 4], [x + 1.2, 14, 12], 'roll', { split: '' });
      m.box(`shaft_lo_${i}`, [x - 0.4, 8.4, 2.4], [x + 0.4, 9.2, 13.6], 'bright', { split: '' });
    }
    for (const z of [3.4, 12.6]) m.prism(`edge_${z}`, 'z', [-4.4, 11], 1.2, z - 1, z + 1, 'roll', { split: '', ends: ['north', 'south'] });
    return m;
  }

  // ---------------------------------------------------------------- hydrotest bench
  // Hydrostatic test bench: a long bed, the fixed head with the pressure gauge and the water line (west), the
  // moving head on rails (east), the pipe under test between them; burst: the pipe split open, water on the bed.
  function hydrotest(burst) {
    const m = mk(TEX + 'bed');
    m.box('bed', [-16, 0, 2], [32, 3, 14], 'bed', { split: 'x' });
    for (const z of [3, 12]) m.box(`rail_${z}`, [-6, 3, z], [32, 3.6, z + 1], 'bright', { split: 'x' });
    m.box('head_fixed', [-15, 3, 2.4], [-9, 16, 13.6], 'mblue', { split: '' });
    m.prism('gauge_case', 'z', [-12, 13], 2.2, 1.6, 2.4, 'rubber', { split: '', ends: ['north'] });
    m.disc('gauge', 'z', [-12, 13], 1.9, 1.5, 1.6, 'rubber', 'control/pressure_dial', 'north');
    m.box('water_line', [-16, 4, 5], [-15, 14, 6.4], 'p_water', { split: '' });
    m.box('pump', [-16, 3, 8], [-15, 8, 13], 'blue', { split: '' });
    m.box('head_moving', [24, 3.6, 2.4], [30, 15, 13.6], 'mblue', { split: '' });
    m.box('cylinder', [30, 6, 5], [32, 12, 11], 'bright', { split: '' });
    m.box('plug_w', [-9, 6.4, 5.4], [-8, 11.6, 10.6], 'st', { split: '' });
    m.box('plug_e', [23, 6.4, 5.4], [24, 11.6, 10.6], 'st', { split: '' });
    if (!burst) m.prism('pipe', 'x', [9, 8], 2.4, -8, 23, 'st', { split: '' });
    else {
      m.prism('pipe_w', 'x', [9, 8], 2.4, -8, 6, 'st', { split: '' });
      m.prism('pipe_e', 'x', [9, 8], 2.4, 10, 23, 'st', { split: '' });
      m.box('flap_n', [6, 8, 4.6], [10, 12, 5.2], 'st', { split: '', rot: { axis: 'x', angle: 22.5, origin: [8, 9, 5.6] } });
      m.box('flap_s', [6, 6, 10.8], [10, 10, 11.4], 'st', { split: '', rot: { axis: 'x', angle: -22.5, origin: [8, 9, 10.4] } });
      m.box('pipe_lip_lo', [6, 6.6, 6.4], [10, 7, 9.6], 'st', { split: '' });
      m.box('puddle', [-4, 3, 3.6], [20, 3.15, 12.4], 'water', { split: '' });
    }
    return m;
  }

  // ---------------------------------------------------------------- heat-treatment furnace and quench tank
  // Box furnace 2x2x2: clad casing on legs, the vertical-lift door on the north face with its counterweight,
  // chains and the peephole (glowing while lit); open: the door raised, the glowing chamber and a hot charge.
  function htFurnace(state) {
    const m = mk(TEX + 'htf_casing');
    for (const [x, z] of [[1, 1], [29, 1], [1, 29], [29, 29]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 2, 3, z + 2], 'frame', { split: '' });
    m.box('casing', [0.5, 3, 1], [31.5, 26, 31.5], 'htf', { split: 'xyz' });
    m.box('roof', [0, 26, 0.5], [32, 27, 32], 'frame', { split: 'xz' });
    m.box('door_frame', [4, 3, 0.2], [28, 22, 1], 'frame', { split: 'x' });
    const open = state === 'open', lit = state !== 'off';
    const dy = open ? 9 : 0;
    m.box('door', [6, 4 + dy, -1], [26, 20 + dy, 0.2], 'fb', { split: 'x', face: { north: 'htf' } });
    m.box('door_rim', [5.6, 3.6 + dy, -1.4], [26.4, 4.2 + dy, 0.2], 'frame', { split: '' });
    if (!open) {
      m.box((lit ? 'glow_' : '') + 'peephole', [15, 13, -1.3], [17, 15, -1], lit ? 'fglow' : 'soot', { split: '', hide: ['south'] });
    } else {
      m.box('glow_chamber', [6.4, 4, 0.6], [25.6, 13, 0.8], 'fglow', { split: '', hide: ['south'] });
      m.box('glow_charge', [9, 4, 0.4], [23, 6, 0.6], 'hot', { split: '' });
    }
    for (const x of [6.4, 25]) m.box(`guide_${x}`, [x - 1, 3, -1.6], [x, 30, -0.6], 'st', { split: 'y' });
    m.box('beam', [4, 29, -2], [28, 31, 2], 'frame', { split: 'x' });
    for (const x of [9, 23]) m.box(`chain_${x}`, [x - 0.3, 20 + dy, -0.6], [x + 0.3, 29, -0.2], 'st', { split: '' });
    m.box('counterweight', [28, 14 - dy, -1], [31, 22 - dy, 3], 'ci', { split: '' });
    m.box('panel', [27.6, 8, -0.6], [31.6, 14, 1], 'en', { split: '' });
    m.box((lit ? 'glow_' : '') + 'lamp', [29, 12, -0.8], [30.2, 13.2, -0.6], lit ? 'l_a' : 'l_off', { split: '' });
    m.box('flue', [14, 27, 14], [18, 32, 18], 'st', { split: '' });
    return m;
  }
  // Quench tank 2x1x2: riveted steel tank with a rim and walkway grating, the medium (water green, oil brown),
  // the basket rail over it, the agitator motor and the overflow.
  function quenchTank(oil) {
    const m = mk('plant/structure');
    m.box('tank_n', [0, 0, 0], [32, 13, 1.2], 'frame', { split: 'x' });
    m.box('tank_s', [0, 0, 30.8], [32, 13, 32], 'frame', { split: 'x' });
    m.box('tank_w', [0, 0, 1.2], [1.2, 13, 30.8], 'frame', { split: 'z' });
    m.box('tank_e', [30.8, 0, 1.2], [32, 13, 30.8], 'frame', { split: 'z' });
    m.box('floor', [1.2, 0, 1.2], [30.8, 1, 30.8], 'frame', { split: 'xz' });
    m.box('medium', [1.2, 1, 1.2], [30.8, 11.6, 30.8], oil ? 'oil' : 'water', { split: 'xz', hide: ['north', 'south', 'east', 'west', 'down'] });
    for (const [n, f, t] of [['n', [0, 13, 0], [32, 14, 1.6]], ['s', [0, 13, 30.4], [32, 14, 32]],
      ['w', [0, 13, 1.6], [1.6, 14, 30.4]], ['e', [30.4, 13, 1.6], [32, 14, 30.4]]]) m.box(`rim_${n}`, f, t, 'yel', { split: '' });
    for (const x of [4, 28]) m.box(`rail_post_${x}`, [x, 14, 14], [x + 1, 15, 18], 'frame', { split: '' });
    m.box('rail', [4, 14.2, 15.6], [29, 15, 16.4], 'bright', { split: 'x' });
    m.box('basket', [10, 6, 10], [22, 11, 22], 'gal', { split: '', hide: ['up'] });
    m.box('agitator_motor', [26, 14, 26], [30, 15, 30], 'blue', { split: '' });
    m.box('overflow_pipe', [30.8, 9, 14.6], [32, 10.6, 16.2], oil ? 'p_liq' : 'p_water', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- reheating furnace
  // Pusher reheating furnace 3x2x3: brick body in a frame of steel buckstays and tie rods, the charging door at
  // the north end and the discharge door at the south, side burners with air (blue) and gas (yellow) lines,
  // peepholes and door windows that glow while lit, the flue up from the charging end.
  function reheating(lit) {
    const m = mk('material/brick');
    m.box('hearth', [-14, 0, -16], [30, 2, 32], 'conc', { split: 'xz' });
    m.box('body', [-12, 2, -14], [28, 20, 30], 'brick', { split: 'xyz' });
    m.box('roof', [-13, 20, -15], [29, 22, 31], 'fb', { split: 'xz' });
    m.box('roof_crown', [-8, 22, -14], [24, 23.4, 30], 'fb', { split: 'xz' });
    for (const z of [-14.6, -4, 6, 16, 29.4]) {
      m.box(`stay_w_${z}`, [-13, 2, z - 0.6], [-12, 22.6, z + 0.6], 'frame', { split: 'y' });
      m.box(`stay_e_${z}`, [28, 2, z - 0.6], [29, 22.6, z + 0.6], 'frame', { split: 'y' });
      m.box(`tie_${z}`, [-13, 22.6, z - 0.3], [29, 23.2, z + 0.3], 'st', { split: 'x' });
    }
    for (const [n, z0, z1] of [['charge', -15, -14], ['discharge', 30, 31]]) {
      m.box(`${n}_frame`, [-6, 2, z0], [22, 14, z1], 'ci', { split: 'x' });
      m.box((lit ? 'glow_' : '') + `${n}_window`, [4, 8, n === 'charge' ? z0 - 0.1 : z1], [12, 11, n === 'charge' ? z0 : z1 + 0.1], lit ? 'fglow' : 'soot', { split: '' });
    }
    for (const side of [-1, 1]) {
      const x = side < 0 ? -12 : 28, xo = x + side * 2.4;
      for (const z of [1, 11, 21]) {
        m.box(`burner_${side}_${z}`, [Math.min(x, xo), 8, z - 1.6], [Math.max(x, xo), 12, z + 1.6], 'ci', { split: '' });
        m.box((lit ? 'glow_' : '') + `peep_${side}_${z}`, [Math.min(x, x + side * 0.2), 14, z - 0.6], [Math.max(x, x + side * 0.2), 15.2, z + 0.6], lit ? 'fglow' : 'soot', { split: '' });
      }
      const xa = side < 0 ? -15.6 : 31.6;
      m.box(`air_main_${side}`, [Math.min(xa, xa - side * 1.6), 12.4, -12], [Math.max(xa, xa - side * 1.6), 14, 28], 'p_air', { split: 'z' });
      m.box(`gas_main_${side}`, [Math.min(xo, xo + side * 0.8), 6, -12], [Math.max(xo, xo + side * 0.8), 7, 28], 'p_gas', { split: 'z' });
      for (const z of [1, 11, 21]) m.box(`air_drop_${side}_${z}`, [Math.min(xo, xa), 11.2, z - 0.5], [Math.max(xo, xa), 12.4, z + 0.5], 'p_air', { split: '' });
    }
    m.box('flue', [0, 22, -12], [8, 32, -4], 'brick', { split: 'y' });
    m.box('flue_band', [-0.4, 28, -12.4], [8.4, 29, -3.6], 'frame', { split: '' });
    m.box('pusher', [2, 2, -16], [14, 6, -15], 'mblue', { split: '' });
    return m;
  }

  const BUILDS = {
    rolling_mill_stand: { model: millStand },
    'rolling_mill_stand/roll_top': { model: () => roll(14), java: 'rolling_mill_stand/partial/roll_top' },
    'rolling_mill_stand/roll_bottom': { model: () => roll(2), java: 'rolling_mill_stand/partial/roll_bottom' },
    roller_table: { model: () => rollerTable(false) }, roller_table_cooling_bed: { model: () => rollerTable(true) },
    'roller_table/rollers': { model: tableRollers, java: 'roller_table/partial/rollers' },
    piercing_mill: { model: piercing },
    'piercing_mill/roll_l': { model: () => barrelRoll(4), java: 'piercing_mill/partial/roll_l' },
    'piercing_mill/roll_r': { model: () => barrelRoll(12), java: 'piercing_mill/partial/roll_r' },
    pipe_welder: { model: () => welder(false) }, pipe_welder_welding: { model: () => welder(true) },
    'pipe_welder/forming_rolls': { model: formingRolls, java: 'pipe_welder/partial/forming_rolls' },
    hydrotest_bench: { model: () => hydrotest(false) }, hydrotest_bench_burst: { model: () => hydrotest(true) },
    heat_treatment_furnace: { model: () => htFurnace('off') }, heat_treatment_furnace_lit: { model: () => htFurnace('lit') },
    heat_treatment_furnace_open: { model: () => htFurnace('open') },
    quench_tank: { model: () => quenchTank(false) }, quench_tank_oil: { model: () => quenchTank(true) },
    reheating_furnace: { model: () => reheating(false) }, reheating_furnace_lit: { model: () => reheating(true) },
  };

  window.ROL = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    exportAll: (root, ids) => PCART.exportAll(root, 'rolling', ids ? Object.fromEntries(ids.map((i) => [i, BUILDS[i]])) : BUILDS),
  };
})();
