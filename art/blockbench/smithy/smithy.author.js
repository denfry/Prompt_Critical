// Hand stage of GDD 18 (§18.6.1–18.6.4, §18.7): bloomery, forge hearth, smithing anvil, their panel boards and the
// item icons of the workpiece, the smith's hammer and the shaft. Hand-authored for Blockbench / pcart_headless.
//   node art/tools/pcart_headless.js <repo> smithy/smithy.author.js SMY --paint
// All blocks 1x1x1, model x 0..16, y 0..16, z 0..16, front to the north.
//  - bloomery: a squat clay-daubed brick shaft on a stone footing, charging mouth on top, tap arch low in front,
//    a tuyere from leather bellows on the west side. Lit: glow in the mouth and behind the tap arch.
//  - forge_hearth: a stone hearth table with a firepot of coals, a brick hood and chimney at the back, bellows on the
//    east side with a blast pipe into the firepot. Lit: the coals glow.
//  - smithing_anvil: a London-pattern anvil of wrought iron with a steel face, horn to the west, on an oak stump.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/smithy';
  const T = {};

  // Clay daub over brick: ochre, cracked, scorched dark towards the top.
  T.daub = () => {
    const im = img(32, 32), r = rng(5101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const crack = (x * 7 + y * 3) % 23 === 0 || (x + y * 5) % 29 === 0;
      const base = mix([168, 126, 84], [70, 52, 40], Math.max(0, (8 - y) / 8) * 0.7);
      put(im, x, y, crack ? add(base, -40) : add(base, (r() - 0.5) * 22));
    }
    return im;
  };
  // Glowing charcoal: orange cores, black crust.
  T.coals = () => {
    const im = img(16, 16), r = rng(5111);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const v = r();
      put(im, x, y, v > 0.55 ? mix([255, 200, 80], [230, 90, 20], r()) : v > 0.25 ? [120, 40, 16] : [30, 24, 22]);
    }
    return im;
  };
  // Dead coals for the unlit hearth.
  T.coals_dead = () => { const im = img(16, 16), r = rng(5112); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([38, 34, 32], (r() - 0.5) * 24)); return im; };
  // Bellows leather: brown, folds every 4 px.
  T.leather = () => {
    const im = img(16, 16), r = rng(5121);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(y % 4 === 0 ? [74, 46, 28] : [116, 74, 44], (r() - 0.5) * 12));
    return im;
  };
  // Anvil body: dark wrought iron; face: ground bright steel.
  T.anvil = () => { const im = img(16, 16); grain(im, [58, 58, 62], 5131, 3, 4, 6); return im; };
  T.anvil_face = () => { const im = img(16, 16), r = rng(5141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([176, 178, 184], (r() - 0.5) * 16 + (x === y ? 18 : 0))); return im; };
  // Oak stump: end grain rings on top, bark on the sides.
  T.stump_top = () => {
    const im = img(16, 16), r = rng(5151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, d > 7 ? [70, 50, 34] : add(Math.floor(d) % 2 ? [168, 132, 86] : [146, 110, 70], (r() - 0.5) * 10));
    }
    return im;
  };
  T.bark = () => { const im = img(16, 16), r = rng(5161); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 3 === 0 ? [58, 42, 30] : [88, 66, 46], (r() - 0.5) * 14)); return im; };

  // MF4: copper winding wire, and the edge of a stacked core: thin bright steel lines with dark varnish between them.
  T.copper = () => { const im = img(16, 16), r = rng(5181); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(y % 2 ? [178, 98, 54] : [204, 124, 72], (r() - 0.5) * 14)); return im; };
  T.lams = () => { const im = img(16, 16), r = rng(5191); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(y % 2 ? [62, 60, 70] : [138, 142, 156], (r() - 0.5) * 8)); return im; };

  const MAT = {
    cu: { tex: 'smithy/copper', P: 16 }, lams: { tex: 'smithy/lams', P: 16 },
    daub: { tex: 'smithy/daub', P: 16 }, coals: { tex: 'smithy/coals', P: 16 }, dead: { tex: 'smithy/coals_dead', P: 16 },
    lea: { tex: 'smithy/leather', P: 16 }, anv: { tex: 'smithy/anvil', P: 16 }, face: { tex: 'smithy/anvil_face', P: 16 },
    stump: { tex: 'smithy/stump_top', P: 16 }, bark: { tex: 'smithy/bark', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'smithy/daub' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // Leather bellows with a wooden head and board, nozzle pointing along +x (rotated by the caller's position).
  function bellows(m, x0, y0, z0, lit) {
    m.box('bellows_board_top', [x0, y0 + 3.4, z0], [x0 + 5, y0 + 3.9, z0 + 4], 'wood', { split: '' });
    m.box('bellows_board_bot', [x0, y0, z0], [x0 + 5, y0 + 0.5, z0 + 4], 'wood', { split: '' });
    m.box('bellows_leather', [x0 + 0.4, y0 + 0.5, z0 + 0.3], [x0 + 4.6, y0 + 3.4, z0 + 3.7], 'lea', { split: '' });
    m.box('bellows_handle', [x0 - 2.4, y0 + 3.5, z0 + 1.6], [x0 + 0.2, y0 + 3.9, z0 + 2.4], 'wood', { split: '' });
  }

  function bloomery(lit) {
    const m = mk();
    m.box('footing', [1, 0, 1], [15, 1.4, 15], 'ashlar', { split: '' });
    m.prismY('shaft', [8, 8], 6.4, 1.4, 14.6, 'daub', { seed: 3 });
    m.prismY('collar', [8, 8], 6.7, 13.8, 14.8, 'brick', { ends: ['up', 'down'] });
    m.prismY('mouth', [8, 8], 3, 14.8, 15, lit ? 'coals' : 'dead', { ends: ['up'] });
    if (lit) m.box('glow_mouth', [5.4, 14.85, 5.4], [10.6, 14.9, 10.6], 'coals', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    // tap arch in front, plugged with clay; glows when lit
    m.box('tap_frame', [5.6, 1.4, 1.2], [10.4, 5.6, 1.8], 'brick', { split: '' });
    m.box(lit ? 'glow_tap' : 'tap_plug', [6.4, 1.6, 1.0], [9.6, 4.8, 1.4], lit ? 'coals' : 'daub', { split: '' });
    // tuyere from the bellows on the west side
    m.box('tuyere', [0.2, 3.2, 7.2], [2.2, 4.6, 8.8], 'daub', { split: '' });
    bellows(m, 0.2, 1.4, 11, lit);
    m.box('blast_pipe', [1.6, 3.4, 8.6], [2.4, 4.2, 11], 'ci', { split: '' });
    return m;
  }

  function forge(lit) {
    const m = mk('smithy/coals_dead');
    m.box('table', [0.5, 0, 0.5], [15.5, 9, 15.5], 'ashlar', { split: '' });
    m.box('rim_n', [0.5, 9, 0.5], [15.5, 10, 2], 'brick', { split: '' });
    m.box('rim_w', [0.5, 9, 2], [2, 10, 12], 'brick', { split: '' });
    m.box('rim_e', [14, 9, 2], [15.5, 10, 12], 'brick', { split: '' });
    m.box(lit ? 'glow_coals' : 'coals', [2, 8.8, 2], [14, 9.6, 12], lit ? 'coals' : 'dead', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    // brick hood and chimney at the back
    m.box('hood', [2, 10, 11], [14, 13.5, 15.5], 'brick', { split: '' });
    m.box('chimney', [5, 13.5, 12], [11, 16, 15.5], 'brick', { split: '' });
    m.box('hood_lip', [2.5, 12.6, 10.4], [13.5, 13.4, 11], 'soot', { split: '' });
    // blast pipe from the bellows on the east into the firepot
    bellows(m, 10.6, 10, 2.6, lit);
    m.box('blast_pipe', [12, 9.4, 4.2], [12.8, 10.6, 5], 'ci', { split: '' });
    // tongs and a quenching ladle lying on the rim
    m.box('tongs', [1, 10, 4], [1.6, 10.3, 11], 'st', { split: '' });
    return m;
  }

  function anvil() {
    const m = mk('smithy/anvil');
    m.prismY('stump', [8, 8], 5.4, 0, 6, 'bark', { ends: [] });
    m.prismY('stump_top', [8, 8], 5.4, 5.9, 6, 'stump', { ends: ['up'] });
    m.box('foot', [4, 6, 5], [13, 7.6, 11], 'anv', { split: '' });
    m.box('waist', [6, 7.6, 6.2], [11, 10.4, 9.8], 'anv', { split: '' });
    m.box('body', [3, 10.4, 5.2], [14.6, 12.6, 10.8], 'anv', { split: '' });
    m.box('face', [5, 12.6, 5.4], [14.4, 13, 10.6], 'face', { split: '' });
    m.box('horn', [0.6, 11, 6.4], [3, 12.4, 9.6], 'anv', { split: '' });
    m.box('horn_tip', [0, 11.4, 7.2], [0.6, 12, 8.8], 'anv', { split: '' });
    m.box('hardy_hole', [12.4, 12.95, 7.4], [13.4, 13.05, 8.4], 'soot', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    return m;
  }

  // Draw plate: an oak bench, an upright steel plate with a row of graded holes at the front, a windlass with a crank
  // at the back that pulls the wire through.
  function drawPlate() {
    const m = mk('smithy/anvil');
    for (const [x, z] of [[1.5, 1.5], [13, 1.5], [1.5, 13], [13, 13]]) m.box('leg', [x, 0, z], [x + 1.5, 9, z + 1.5], 'wood', { split: '' });
    m.box('top', [0.5, 9, 0.5], [15.5, 10.4, 15.5], 'wood', { split: '' });
    m.box('plate', [2, 10.4, 3], [14, 15, 4], 'anv', { split: '' });
    for (let i = 0; i < 5; i++) m.box('hole' + i, [3 + i * 2.2, 12.2, 2.95], [3.4 + i * 2.2 - i * 0.05, 12.6, 3.05], 'soot', { split: '', hide: ['down', 'up', 'east', 'west', 'north'] });
    m.box('wire', [7.6, 11.5, 4], [8.4, 11.9, 10], 'face', { split: '' });
    m.box('drum', [4, 10.4, 10], [12, 13.2, 12.8], 'wood', { split: '' });
    m.box('axle', [3, 11.4, 11], [13, 12.2, 11.8], 'st', { split: '' });
    m.box('crank_arm', [12.6, 11.4, 11], [13.4, 14.6, 11.8], 'st', { split: '' });
    m.box('crank_grip', [13.4, 14, 10.6], [15, 14.6, 12.2], 'wood', { split: '' });
    m.box('post_w', [3.2, 10.4, 10.6], [4, 13, 12.2], 'wood', { split: '' });
    m.box('post_e', [12, 10.4, 10.6], [12.8, 13, 12.2], 'wood', { split: '' });
    return m;
  }

  // Powered stage (GDD 18 §18.7). The Create shaft comes in at the back (south) for the hammer and the press, at the
  // west for the wire mill; a coupling boss marks it.
  //  - power_hammer: a cast-iron anvil block on a footing, two columns, a cylinder head on top; the ram with its upper
  //    die is a partial that drops 3 px per crank turn.
  //  - crank_press: a closed cast frame, the crank housing on top with a flywheel at the back; the slide is a partial.
  //  - wire_mill: a bench with a die box at the front and a capstan drum at the back driven from a gearbox on the west.
  //  - heating_furnace / hardening_furnace: a brick chamber with a steel door and a stack; lit, the door slot glows.
  //    The hardening furnace has a quench tank on its east side.
  function coupling(m, axis) {
    if (axis === 'z') m.box('coupling', [6, 6, 15], [10, 10, 16], 'st', { split: '' });
    else m.box('coupling', [0, 6, 6], [1, 10, 10], 'st', { split: '' });
  }

  function powerHammer() {
    const m = mk('smithy/anvil');
    m.box('footing', [1, 0, 1], [15, 2, 15], 'ashlar', { split: '' });
    m.box('anvil_block', [4, 2, 4], [12, 6, 12], 'ci', { split: '' });
    m.box('lower_die', [5, 6, 5], [11, 6.8, 11], 'face', { split: '' });
    m.box('column_w', [1.5, 2, 6], [3.5, 14, 10], 'ci', { split: '' });
    m.box('column_e', [12.5, 2, 6], [14.5, 14, 10], 'ci', { split: '' });
    m.box('head', [1.5, 14, 4], [14.5, 16, 14], 'ci', { split: '' });
    m.box('cylinder', [5, 11.6, 5.5], [11, 14, 10.5], 'st', { split: '' });
    m.box('drive', [5, 9, 12], [11, 15, 15], 'ci', { split: '' });
    coupling(m, 'z');
    return m;
  }

  function hammerRam() {
    const m = mk('smithy/anvil');
    m.box('ram', [5.5, 8, 6], [10.5, 11.6, 10], 'st', { split: '' });
    m.box('upper_die', [5, 7.2, 5], [11, 8, 11], 'face', { split: '' });
    return m;
  }

  function crankPress() {
    const m = mk('smithy/anvil');
    m.box('bed', [1, 0, 2], [15, 4, 14], 'ci', { split: '' });
    m.box('bolster', [4, 4, 4], [12, 5, 12], 'face', { split: '' });
    m.box('upright_w', [1, 4, 3], [3.5, 13, 13], 'ci', { split: '' });
    m.box('upright_e', [12.5, 4, 3], [15, 13, 13], 'ci', { split: '' });
    m.box('crown', [1, 13, 3], [15, 16, 13], 'ci', { split: '' });
    m.prismZ ? m.prismZ('flywheel', [8, 12], 3.6, 13, 15, 'st', {}) : m.box('flywheel', [4.5, 8.5, 13], [11.5, 15.5, 15], 'st', { split: '' });
    coupling(m, 'z');
    return m;
  }

  function pressSlide() {
    const m = mk('smithy/anvil');
    m.box('slide', [4, 8, 4], [12, 12.6, 12], 'st', { split: '' });
    m.box('upper_die', [4.5, 7.4, 4.5], [11.5, 8, 11.5], 'face', { split: '' });
    return m;
  }

  function wireMill() {
    const m = mk('smithy/anvil');
    for (const [x, z] of [[1.5, 1.5], [13, 1.5], [1.5, 13], [13, 13]]) m.box('leg', [x, 0, z], [x + 1.5, 7, z + 1.5], 'ci', { split: '' });
    m.box('bed', [0.5, 7, 0.5], [15.5, 8.5, 15.5], 'ci', { split: '' });
    m.box('die_box', [5.5, 8.5, 1.5], [10.5, 12, 4.5], 'st', { split: '' });
    m.box('soap_box', [5, 8.5, 4.5], [11, 10, 6.5], 'wood', { split: '' });
    m.box('wire', [7.6, 10.2, 4.5], [8.4, 10.6, 9], 'face', { split: '' });
    m.prismY('drum', [9, 11.5], 3.4, 8.5, 14.5, 'st', { ends: ['up', 'down'] });
    m.box('gearbox', [1, 8.5, 9], [4.5, 13, 14], 'ci', { split: '' });
    m.box('drive_shaft', [4.5, 10, 11], [6, 11, 12], 'st', { split: '' });
    coupling(m, 'x');
    return m;
  }

  function chamber(lit, tank) {
    const m = mk('smithy/daub');
    const x1 = tank ? 11 : 15.5;
    m.box('chamber', [0.5, 0, 1], [x1, 12, 15], 'brick', { split: '' });
    m.box('frame', [1.5, 2, 0.4], [x1 - 1, 9, 1], 'ci', { split: '' });
    m.box('door', [2.5, 3, 0.2], [x1 - 2, 8, 0.6], 'st', { split: '' });
    m.box(lit ? 'glow_slot' : 'slot', [3.5, 7.6, 0.1], [x1 - 3, 8.3, 0.3], lit ? 'coals' : 'soot', { split: '' });
    m.box('firebox', [2.5, 0.5, 0.6], [x1 - 2, 2, 1], lit ? 'coals' : 'soot', { split: '' });
    m.box('stack', [3, 12, 9], [7, 16, 13], 'brick', { split: '' });
    if (tank) {
      m.box('tank', [11.5, 0, 3], [15.5, 7, 13], 'st', { split: '' });
      m.box('tank_liquid', [12, 6.6, 3.5], [15, 6.9, 12.5], 'soot', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    }
    return m;
  }

  // MF4 (§18.6.8–18.6.9).
  //  - sheet_mill: a two-high cold rolling stand: cast housings west and east, two bright rolls along x, entry and
  //    exit tables with a sheet on them, a pinion box on the west with the coupling.
  //  - notching_press: a gap-frame press: a column at the back, an overhanging head with the flywheel, a rotary index
  //    table at the front carrying a half-notched lamination; shaft at the back.
  //  - winding_machine: a bed with a headstock on the west turning a faceplate that holds a stator core with copper
  //    end turns, a wire spool on a stand on the east feeding through a tensioner.
  function sheetMill() {
    const m = mk('smithy/anvil');
    m.box('base', [0.5, 0, 2], [15.5, 3, 14], 'ci', { split: '' });
    m.box('housing_w', [3, 3, 5], [5, 15, 11], 'ci', { split: '' });
    m.box('housing_e', [13, 3, 5], [15, 15, 11], 'ci', { split: '' });
    m.box('cap', [3, 15, 5], [15, 16, 11], 'ci', { split: '' });
    for (const [y0, y1] of [[6, 9], [9.4, 12.4]]) {
      m.box('roll', [5, y0 + 0.5, 6.5], [13, y1 - 0.5, 9.5], 'bright', { split: '' });
      m.box('roll', [5, y0, 7], [13, y1, 9], 'bright', { split: '' });
    }
    m.box('screw', [8, 12.4, 7.5], [10, 15, 8.5], 'st', { split: '' });
    m.box('table_in', [5.5, 7.6, 0.5], [12.5, 8.6, 6.5], 'st', { split: '' });
    m.box('table_out', [5.5, 7.6, 9.5], [12.5, 8.6, 15.5], 'st', { split: '' });
    m.box('sheet', [6, 8.6, 1], [12, 8.8, 6.5], 'lams', { split: '' });
    m.box('pinion_box', [0.5, 3, 4], [3, 12, 12], 'ci', { split: '' });
    coupling(m, 'x');
    return m;
  }

  function notchingPress() {
    const m = mk('smithy/anvil');
    m.box('base', [1, 0, 1], [15, 3, 15], 'ci', { split: '' });
    m.box('column', [4, 3, 10], [12, 15, 15], 'ci', { split: '' });
    m.box('head', [4, 12, 3], [12, 16, 10], 'ci', { split: '' });
    m.box('ram', [6.5, 8.5, 4.5], [9.5, 12, 7.5], 'st', { split: '' });
    m.box('punch', [7.2, 7.4, 5.2], [8.8, 8.5, 6.8], 'face', { split: '' });
    m.prismY('index_table', [8, 6], 4.6, 3, 5.4, 'st', { ends: ['up', 'down'] });
    m.prismY('lamination', [8, 6], 4.2, 5.4, 5.7, 'lams', { ends: ['up', 'down'] });
    m.box('flywheel', [12, 8, 10.5], [13.2, 15.5, 14.5], 'st', { split: '' });
    coupling(m, 'z');
    return m;
  }

  function windingMachine() {
    const m = mk('smithy/anvil');
    m.box('bed', [0.5, 0, 3], [15.5, 5, 13], 'ci', { split: '' });
    m.box('headstock', [1, 5, 4], [5, 12, 12], 'ci', { split: '' });
    m.box('faceplate', [5, 6, 5], [5.8, 13, 11], 'st', { split: '' });
    m.box('core', [5.8, 6.5, 5.5], [10.5, 12.5, 10.5], 'lams', { split: '' });
    m.box('end_turns_w', [5.8, 7.3, 6.3], [6.6, 11.7, 9.7], 'cu', { split: '' });
    m.box('end_turns_e', [10.5, 7.3, 6.3], [11.4, 11.7, 9.7], 'cu', { split: '' });
    m.box('spool_stand', [13, 5, 6], [15, 9, 10], 'ci', { split: '' });
    m.prismY('spool', [14, 8], 1.9, 9, 13, 'cu', { ends: ['up', 'down'] });
    m.box('tensioner', [11.6, 9, 7.6], [13, 9.6, 8.4], 'st', { split: '' });
    coupling(m, 'x');
    return m;
  }

  // Panel boards 200xH in the style of the foundry boards: brass frame, dark plates under the bars and buttons.
  function board(seed, plates, H = 150) {
    const W = 200, im = img(W, H), r = rng(seed);
    grain(im, [62, 54, 50], seed + 1, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const [x0, y0, x1, y1] of plates) for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
      put(im, x, y, add([50, 44, 40], edge + (r() - 0.5) * 4));
    }
    return im;
  }

  function paintGui(root) {
    const out = (n, im) => writePng(im, path.join(root, PCART.ASSETS, 'textures/gui/panel/' + n + '.png'));
    out('bloomery', board(5201, [[80, 18, 194, 104], [6, 106, 194, 146]]));
    out('forge_hearth', board(5211, [[80, 18, 194, 84], [6, 88, 194, 120], [6, 122, 194, 146]]));
    out('smithing_anvil', board(5221, [[6, 6, 194, 92], [6, 94, 194, 154], [6, 158, 194, 186]], 190));
    out('draw_plate', board(5231, [[6, 6, 194, 94], [6, 96, 194, 120], [6, 122, 194, 146]]));
    out('power_hammer', board(5241, [[6, 6, 194, 92], [6, 96, 194, 146]]));
    out('crank_press', board(5251, [[6, 6, 194, 92], [6, 96, 194, 146]]));
    out('wire_mill', board(5261, [[6, 6, 194, 96], [6, 98, 194, 146]]));
    out('heating_furnace', board(5271, [[80, 18, 194, 84], [6, 88, 194, 120], [6, 122, 194, 146]]));
    out('hardening_furnace', board(5281, [[80, 18, 194, 84], [6, 88, 194, 120], [6, 122, 194, 146]]));
    out('sheet_mill', board(5291, [[6, 6, 194, 92], [6, 98, 194, 146]]));
    out('notching_press', board(5301, [[6, 6, 194, 92], [6, 98, 194, 146]]));
    out('winding_machine', board(5311, [[6, 6, 194, 92], [6, 98, 194, 146]]));
    return 12;
  }

  // Item icons 16x16 and their item/generated models.
  function icon(draw) { const im = img(16, 16); draw(im); return im; }
  const ICONS = {
    workpiece: icon((im) => { for (let y = 6; y < 10; y++) for (let x = 2; x < 14; x++) put(im, x, y, y === 6 ? [120, 116, 112] : y === 9 ? [40, 38, 38] : [78, 74, 72]); }),
    smith_hammer: icon((im) => {
      for (let i = 0; i < 9; i++) put(im, 4 + i, 12 - i, [134, 96, 58]);
      for (let y = 2; y < 6; y++) for (let x = 9; x < 15; x++) put(im, x, y, y === 2 ? [170, 170, 176] : [96, 96, 102]);
    }),
    tongs: icon((im) => {
      for (let i = 0; i < 9; i++) { put(im, 3 + i, 13 - i, [70, 70, 76]); put(im, 5 + i, 13 - i, [96, 96, 102]); }
      for (let y = 2; y < 5; y++) { put(im, 12, y, [70, 70, 76]); put(im, 14, y, [96, 96, 102]); }
    }),
    wire: icon((im) => {
      for (let a = 0; a < 64; a++) for (const rr of [4.5, 5.5]) {
        const t = a / 64 * 2 * Math.PI;
        put(im, Math.round(8 + rr * Math.cos(t)), Math.round(8 + rr * Math.sin(t)), rr > 5 ? [196, 110, 70] : [150, 80, 50]);
      }
    }),
    shaft: icon((im) => {
      for (let i = 1; i < 15; i++) { put(im, i, 15 - i, [150, 150, 158]); put(im, i, 14 - i, [104, 104, 112]); }
      for (let i = 4; i < 7; i++) put(im, i, 15 - i - 1, [70, 70, 78]);
    }),
    // MF4: a grey-blue sheet with a varnish sheen, a lamination ring with slots, a stacked core and a wound stator
    electrical_sheet: icon((im) => {
      for (let y = 3; y < 13; y++) for (let x = 1; x < 15; x++) put(im, x, y, y === 3 ? [176, 182, 198] : y === 12 ? [54, 58, 70] : (x + y) % 7 === 0 ? [150, 156, 176] : [112, 118, 136]);
    }),
    lamination: icon((im) => {
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const d = Math.hypot(x - 7.5, y - 7.5), a = Math.atan2(y - 7.5, x - 7.5);
        const slot = d < 5.2 && Math.abs(Math.sin(a * 6)) < 0.3;
        if (d <= 7.4 && d >= 3.6 && !slot) put(im, x, y, d > 6.6 ? [168, 172, 186] : [124, 128, 144]);
      }
    }),
    stator_core: icon((im) => {
      for (let y = 2; y < 14; y++) for (let x = 2; x < 14; x++) {
        const d = Math.abs(x - 7.5);
        if (!(d < 2.5 && y > 4 && y < 11)) put(im, x, y, y % 2 ? [70, 72, 84] : [140, 144, 160]);
      }
    }),
    stator: icon((im) => {
      for (let y = 3; y < 13; y++) for (let x = 3; x < 13; x++) put(im, x, y, y % 2 ? [70, 72, 84] : [140, 144, 160]);
      for (let y = 4; y < 12; y++) for (const x of [1, 2, 13, 14]) put(im, x, y, (x + y) % 2 ? [204, 124, 72] : [170, 92, 50]);
      for (let y = 6; y < 10; y++) for (let x = 6; x < 10; x++) put(im, x, y, [30, 30, 34]);
    }),
  };

  function paintItems(root) {
    for (const [n, im] of Object.entries(ICONS)) {
      writePng(im, path.join(root, PCART.ASSETS, 'textures/item/' + n + '.png'));
      require('fs').writeFileSync(path.join(root, PCART.ASSETS, 'models/item/' + n + '.json'),
        JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'promptcritical:item/' + n } }, null, 2) + '\n');
    }
    return Object.keys(ICONS).length;
  }

  const BUILDS = {
    bloomery: { model: () => bloomery(false) }, bloomery_lit: { model: () => bloomery(true) },
    forge_hearth: { model: () => forge(false) }, forge_hearth_lit: { model: () => forge(true) },
    smithing_anvil: { model: anvil }, draw_plate: { model: drawPlate },
    power_hammer: { model: powerHammer }, 'power_hammer/ram': { model: hammerRam, java: 'power_hammer/partial/ram' },
    crank_press: { model: crankPress }, 'crank_press/ram': { model: pressSlide, java: 'crank_press/partial/ram' },
    wire_mill: { model: wireMill },
    sheet_mill: { model: sheetMill }, notching_press: { model: notchingPress }, winding_machine: { model: windingMachine },
    heating_furnace: { model: () => chamber(false, false) }, heating_furnace_lit: { model: () => chamber(true, false) },
    hardening_furnace: { model: () => chamber(false, true) }, hardening_furnace_lit: { model: () => chamber(true, true) },
  };

  window.SMY = {
    paint(root) { return paint(root) + paintGui(root) + paintItems(root); }, paintGui, paintItems, BUILDS, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'smithy', id, BUILDS[id].java),
    // синхронно через PCART.realize/save на момент вызова: headless-раннер подменяет их своими
    exportAll: (root, ids) => {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) { PCART.realize(root, BUILDS[id].model(), id); out[id] = PCART.save(root, 'smithy', id, BUILDS[id].java); }
      return out;
    },
  };
})();
