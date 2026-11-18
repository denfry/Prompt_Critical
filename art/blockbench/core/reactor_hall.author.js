// Reactor hall, radiation protection and radwaste, hand-authored for Blockbench (replaces the spec_*.py
// placeholders): graphite moderator block, fuel channel, control rod channel, irradiation channel (empty and
// loaded), startup source, control rod drive, zip rod winch, ECCS accumulator, spent fuel rack, RTG (active and
// depleted); decontamination shower, hand-and-foot monitor, lead block, ultrasonic bath; bitumeniser, cementation
// mixer, drum compactor; oxalate precipitator, shearing machine; HEPA filter housing.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/reactor_hall.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); RXH.paint('<repo>')
//   await RXH.exportAll('<repo>')
//
// Front to the north; all single blocks. The graphite stack blocks (moderator, fuel, control rod and
// irradiation channels) and the lead block are full cubes: faces on the block boundary cull against solid
// neighbours, and the channel heads stand on the top face, as on the top plate of a channel-type reactor.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'reactor/';
  const T = {};

  // Reactor graphite: dark grey machined blocks, 8 px courses, a fine machining sheen.
  T.graphite = () => {
    const im = img(32, 32), r = rng(13101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const joint = y % 16 === 15 || x % 16 === 15;
      put(im, x, y, joint ? [24, 24, 26] : add([62, 64, 68], (x % 3 === 0 ? 6 : 0) + (r() - 0.5) * 8));
    }
    return im;
  };
  // Graphite top with the channel bore (the head covers it).
  T.graphite_bore = () => {
    const im = T.graphite();
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) { const d = Math.hypot(x - 15.5, y - 15.5); if (d < 7) put(im, x, y, d < 5.5 ? [16, 16, 18] : [150, 150, 146]); }
    return im;
  };
  // Lead bricks: dull blue-grey, interlocking chevron courses.
  T.lead = () => {
    const im = img(32, 32), r = rng(13111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const row = Math.floor(y / 8), joint = y % 8 === 7 || (x + row * 8) % 16 === 15;
      put(im, x, y, joint ? [70, 74, 80] : add([118, 124, 132], (r() - 0.5) * 10));
    }
    return im;
  };
  // Hand-and-foot monitor front: hand slots, foot plate outline, the count display.
  T.hfm = () => {
    const im = img(32, 32), r = grain(im, [210, 210, 202], 13121, 2, 2, 8);
    rect(im, 4, 6, 8, 4, [40, 40, 44]); rect(im, 20, 6, 8, 4, [40, 40, 44]);
    rect(im, 10, 14, 12, 5, [16, 26, 20]); for (let x = 12; x < 20; x++) if (r() < 0.7) put(im, x, 16, [90, 230, 120]);
    rect(im, 4, 24, 24, 5, [80, 80, 84]);
    return im;
  };
  // RTG fins glowing dull red when fresh, cold grey when depleted.
  T.rtg_hot = () => { const im = img(16, 16), r = rng(13131); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 4 < 2 ? [176, 70, 40] : [110, 40, 26], (r() - 0.5) * 10)); return im; };
  T.rtg_cold = () => { const im = img(16, 16), r = rng(13141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 4 < 2 ? [96, 98, 100] : [60, 62, 64], (r() - 0.5) * 8)); return im; };
  // Bitumen and waste drum yellow with a trefoil band.
  T.bitumen = () => { const im = img(16, 16), r = grain(im, [24, 22, 20], 13151, 4, 4, 4); for (let k = 0; k < 6; k++) put(im, r() * 16, r() * 16, [70, 66, 60]); return im; };
  T.drum = () => {
    const im = img(32, 32), r = grain(im, [210, 176, 40], 13161, 4, 5, 8);
    for (const y of [6, 25]) for (let x = 0; x < 32; x++) { put(im, x, y, [150, 120, 20]); put(im, x, y + 1, [236, 210, 90]); }
    for (let y = 12; y < 20; y++) for (let x = 12; x < 20; x++) { const a = Math.atan2(y - 15.5, x - 15.5), d = Math.hypot(x - 15.5, y - 15.5); if (d < 1.3 || (d < 4 && Math.floor((a + Math.PI) / (Math.PI / 3)) % 2 === 0)) put(im, x, y, [24, 22, 20]); }
    return im;
  };
  // Lead glass of hot cell windows (yellowish, deep).
  T.lead_glass = () => { const im = img(16, 16), r = rng(13171); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([150, 140, 70], [70, 66, 30], y / 16), (r() - 0.5) * 6 + (x - y === 3 ? 40 : 0))); return im; };
  // HEPA pleats.
  T.hepa = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 2 ? [236, 234, 222] : [196, 192, 178]); return im; };
  // Fuel rack cell walls (boral sandwich, stainless).
  T.rack = () => { const im = img(16, 16), r = grain(im, [176, 180, 184], 13181, 3, 3, 4); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if (x % 4 === 0 || y % 4 === 0) put(im, x, y, [110, 114, 118]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    gph: { tex: TEX + 'graphite', P: 16 }, bore: { tex: TEX + 'graphite_bore', P: 16 }, lead: { tex: TEX + 'lead', P: 16 },
    rtgh: { tex: TEX + 'rtg_hot', P: 16 }, rtgc: { tex: TEX + 'rtg_cold', P: 16 }, bit: { tex: TEX + 'bitumen', P: 16 },
    drum: { tex: TEX + 'drum', P: 16 }, lglass: { tex: TEX + 'lead_glass', P: 16 }, hepa: { tex: TEX + 'hepa', P: 8 },
    rack: { tex: TEX + 'rack', P: 8 },
    ss: { tex: 'electronics/ss', P: 16 }, glass: { tex: 'electronics/glass', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 },
    gal: { tex: 'disconnector/galvanised', P: 16 }, rope: { tex: 'oil/rope', P: 8 }, mon: { tex: 'fuel/monel', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 },
    l_off: { tex: 'grid_lines/lamp_off', P: 16 }, scr: { tex: 'electronics/screen', P: 16 }, grat: { tex: 'steelmaking/grating', P: 8 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });
  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  const B = (m, n, f, t, mat, o) => m.box(n, f, t, mat, Object.assign({ split: '' }, o || {}));
  const oct = (m, n, x, z, r, y0, y1, mat) => {
    const h = r * Math.tan(Math.PI / 8);
    B(m, `${n}_a`, [x - r, y0, z - h], [x + r, y1, z + h], mat);
    B(m, `${n}_b`, [x - h, y0 + 0.01, z - r], [x + h, y1 - 0.01, z + r], mat);
    for (const [k, a, d] of [['c', 45, 0.02], ['d', -45, 0.03]]) B(m, `${n}_${k}`, [x - r, y0 + d, z - h], [x + r, y1 - d, z + h], mat, { rot: { axis: 'y', angle: a, origin: [x, 8, z] } });
  };

  // ---------------------------------------------------------------- graphite stack
  function moderator() {
    const m = mk(TEX + 'graphite');
    B(m, 'block', [0, 0, 0], [16, 16, 16], 'gph');
    return Object.assign(m, { cull: true });
  }
  // A channel block: graphite with the bore on top under the channel head (plug in the colour of the channel kind).
  function channel(kind, loaded) {
    const m = mk(TEX + 'graphite');
    B(m, 'block', [0, 0, 0], [16, 16, 16], 'gph', { face: { up: 'bore' } });
    if (kind === 'fuel') {
      oct(m, 'head', 8, 8, 3, 16, 16.5, 'st');
      oct(m, 'plug', 8, 8, 2, 16.5, 16.8, 'gal');
      B(m, 'tag', [7, 16.8, 7.6], [9, 16.9, 8.4], 'green');
    } else if (kind === 'rod') {
      oct(m, 'head', 8, 8, 3, 16, 16.6, 'st');
      oct(m, 'servo', 8, 8, 1.8, 16.6, 17.6, 'yel');
      B(m, 'band', [6.2, 17, 6.2], [9.8, 17.3, 9.8], 'haz');
    } else {
      oct(m, 'head', 8, 8, 3, 16, 16.6, 'ss');
      oct(m, 'capsule_cap', 8, 8, 2, 16.6, 17.2, 'yel');
      B(m, (loaded ? 'glow_' : '') + 'lamp', [10.4, 16, 10.4], [11.6, 17, 11.6], loaded ? 'l_a' : 'l_off');
      if (loaded) oct(m, 'capsule', 8, 8, 1.2, 17.2, 18, 'ss');
    }
    return Object.assign(m, { cull: true });
  }
  // Startup source: a stainless rod assembly in its holder with a hazard band and a lifting eye.
  function startupSource() {
    const m = mk('electronics/ss');
    oct(m, 'holder', 8, 8, 4.4, 0, 2, 'st');
    oct(m, 'rod', 8, 8, 2, 2, 14, 'ss');
    B(m, 'band', [5.9, 9, 5.9], [10.1, 10.6, 10.1], 'haz');
    B(m, 'eye', [7.4, 14, 6.6], [8.6, 16, 9.4], 'st');
    return m;
  }
  // Control rod drive: the servo housing in machine grey on its flange, the motor and gear head, the position
  // indicator column, the cable gland.
  function rodDrive() {
    const m = mk('plant/structure');
    oct(m, 'flange', 8, 8, 6, 0, 1.6, 'st');
    oct(m, 'housing', 8, 8, 4.4, 1.6, 11, 'frame');
    B(m, 'gear_head', [3.6, 11, 3.6], [12.4, 13, 12.4], 'ci');
    B(m, 'motor', [5, 13, 5], [11, 16, 11], 'blue');
    B(m, 'indicator', [12.4, 3, 6.6], [14, 11, 9.4], 'en');
    for (let i = 0; i < 4; i++) B(m, `led_${i}`, [14, 4 + i * 1.8, 7.4], [14.2, 5 + i * 1.8, 8.6], i === 0 ? 'l_g' : 'l_off');
    B(m, 'band', [3.4, 9, 3.4], [12.6, 9.6, 12.6], 'haz');
    B(m, 'gland', [7.2, 14, 1.6], [8.8, 15.4, 5], 'rubber');
    return m;
  }
  // Zip rod winch: a base frame with the cable drum, the brake and the release solenoid, the rope going down.
  function zipWinch() {
    const m = mk('plant/structure');
    B(m, 'base', [0, 0, 1], [16, 1.6, 15], 'frame');
    for (const x of [1, 13]) B(m, `cheek_${x}`, [x, 1.6, 3], [x + 2, 12, 13], 'frame');
    m.prism('drum', 'x', [7.4, 8], 3.6, 3, 13, 'rope', { split: '', ends: [] });
    m.prism('brake', 'x', [7.4, 8], 4.2, 13, 14, 'ci', { split: '', ends: ['east'] });
    B(m, 'solenoid', [14, 4, 2], [16.4, 9, 6], 'yel');
    B(m, 'haz_bar', [1, 12, 2.6], [15, 13, 3.4], 'haz');
    B(m, 'rope_down', [7.6, 0, 3.8], [8.4, 3.8, 4.6], 'st');
    return m;
  }
  // ECCS hydro-accumulator: a tall vessel on a skirt, the nitrogen line on top, the check valve and the
  // discharge line, the level gauge.
  function accumulator() {
    const m = mk('plant/light');
    oct(m, 'skirt', 8, 8, 5, 0, 2, 'frame');
    m.prismY('vessel', [8, 8], 6, 2, 14, 'light', {});
    m.prismY('head', [8, 8], 4, 14, 16, 'light', { ends: ['up'] });
    B(m, 'n2_line', [7.4, 15.4, 2], [8.6, 16, 7.4], 'yel');
    B(m, 'discharge', [12.6, 2, 7], [16, 3.6, 9], 'st');
    B(m, 'check_valve', [13.4, 1.4, 6.2], [15, 4.4, 9.8], 'red');
    B(m, 'level', [2, 4, 1.6], [3, 13, 2.2], 'glass');
    return m;
  }
  // Spent fuel pool rack, open top: a lattice of square stainless cells on feet.
  function fuelRack() {
    const m = mk(TEX + 'rack');
    B(m, 'base_plate', [0, 0, 0], [16, 1, 16], 'ss');
    for (const v of [0, 4, 8, 12]) {
      B(m, `wall_x_${v}`, [0, 1, v], [16, 16, v + 0.4], 'rack', { split: '' });
      B(m, `wall_z_${v}`, [v, 1, 0.4], [v + 0.4, 16, 16], 'rack', { split: '' });
    }
    B(m, 'wall_x_16', [0, 1, 15.6], [16, 16, 16], 'rack');
    B(m, 'wall_z_16', [15.6, 1, 0.4], [16, 16, 15.6], 'rack');
    return m;
  }
  // RTG: a finned cylindrical radiator round the heat source (fins glowing dull red when fresh), the converter
  // caps and the trefoil band, the mounting base.
  function rtg(hot) {
    const m = mk('plant/structure');
    B(m, 'base', [1, 0, 1], [15, 1.6, 15], 'frame');
    oct(m, 'body', 8, 8, 3.6, 1.6, 13.4, 'frame');
    for (const [n, a] of [['x', 0], ['z', 90], ['d1', 45], ['d2', -45]]) {
      const rot = a === 0 || a === 90 ? undefined : { axis: 'y', angle: a, origin: [8, 8, 8] };
      if (a === 90) B(m, `fin_${n}`, [7.6, 2.6, 1.4], [8.4, 12.4, 14.6], hot ? 'rtgh' : 'rtgc');
      else B(m, `fin_${n}`, [1.4, 2.6, 7.6], [14.6, 12.4, 8.4], hot ? 'rtgh' : 'rtgc', { rot });
    }
    oct(m, 'cap', 8, 8, 2.6, 13.4, 15, 'frame');
    B(m, 'band', [4.2, 7, 4.2], [11.8, 8, 11.8], 'yel');
    if (hot) B(m, 'glow_core', [6.6, 15, 6.6], [9.4, 15.1, 9.4], 'rtgh');
    return m;
  }

  // ---------------------------------------------------------------- radiation protection
  function deconShower() {
    const m = mk('plant/acid_tile');
    B(m, 'panel', [0, 0, 14], [16, 16, 16], 'tile');
    B(m, 'pipe', [7.4, 4, 13], [8.6, 15, 14], 'st');
    B(m, 'arm', [7.4, 14, 9], [8.6, 15, 14], 'st');
    oct(m, 'head', 8, 10, 1.8, 13, 14, 'st');
    B(m, 'eyewash', [5, 8, 12], [11, 9, 14], 'green');
    B(m, 'pull_rod', [10, 9, 10.6], [10.4, 14, 11], 'st');
    B(m, 'pull_handle', [9.4, 8.4, 10], [11, 9, 11.6], 'red');
    B(m, 'drain', [3, 0, 7], [13, 0.2, 14], 'grat');
    B(m, 'sign', [2, 12, 13.9], [5, 15, 14], 'green');
    return m;
  }
  function handFoot() {
    const m = mk('switchgear_cell/enamel');
    B(m, 'base', [1, 0, 2], [15, 2, 16], 'en');
    B(m, 'foot_plate', [3, 2, 3], [13, 2.4, 11], 'ss');
    B(m, 'column', [2, 2, 11], [14, 16, 16], 'en', { face: { north: { tex: TEX + 'hfm', uv: [0, 0, 16, 16] } } });
    for (const x of [3, 10]) B(m, `hand_${x}`, [x, 11, 8], [x + 3, 13, 11], 'en');
    B(m, 'glow_ready', [7, 15, 10.8], [9, 15.6, 11], 'l_g');
    return m;
  }
  function leadBlock() {
    const m = mk(TEX + 'lead');
    B(m, 'block', [0, 0, 0], [16, 16, 16], 'lead');
    return Object.assign(m, { cull: true });
  }
  function ultrasonic() {
    const m = mk('electronics/ss');
    B(m, 'cabinet', [0.6, 0, 1], [15.4, 10, 15.4], 'ss');
    B(m, 'tank_rim', [1.6, 10, 3], [14.4, 10.6, 14.4], 'ss');
    B(m, 'liquid', [2.4, 10.1, 3.8], [13.6, 10.3, 13.6], 'glass');
    B(m, 'basket', [4, 10.3, 5.6], [12, 12, 12], 'gal', { hide: ['up'] });
    B(m, 'panel', [2, 2, 0.6], [14, 8, 1], 'en');
    B(m, 'glow_display', [3, 5, 0.4], [7, 7, 0.6], 'scr');
    B(m, 'knob', [9, 4, 0.2], [10.4, 5.4, 0.6], 'rubber');
    B(m, 'lid', [1.6, 10.6, 14], [14.4, 14, 14.6], 'ss');
    return m;
  }

  // ---------------------------------------------------------------- radwaste and radiochemistry
  function bitumeniser() {
    const m = mk('electronics/ss');
    B(m, 'stand', [0.4, 0, 2.6], [15.6, 2, 13.4], 'frame');
    m.prism('evaporator', 'x', [9, 8], 4, 1, 13, 'ss', { split: '', ends: ['west'] });
    B(m, 'jacket', [3, 4.6, 3.6], [11, 13.4, 12.4], 'p_steam');
    B(m, 'motor', [13, 5, 4.6], [16, 13, 11.4], 'blue');
    B(m, 'drum', [1, 0, 3], [6, 6, 8], 'drum');
    B(m, 'drum_fill', [1.4, 6, 3.4], [5.6, 6.1, 7.6], 'bit');
    B(m, 'chute', [2.6, 6, 4.6], [4.4, 9, 6.4], 'st');
    B(m, 'vapour', [7.4, 13, 7.4], [8.6, 14, 8.6], 'p_steam');
    return m;
  }
  function cementer() {
    const m = mk('plant/concrete');
    B(m, 'frame', [0, 0, 0], [16, 1, 16], 'frame');
    for (const [x, z] of [[0.6, 0.6], [14.4, 0.6], [0.6, 14.4], [14.4, 14.4]]) B(m, `post_${x}_${z}`, [x, 1, z], [x + 1, 16, z + 1], 'frame');
    B(m, 'drum', [4, 1, 4], [12, 11, 12], 'drum');
    B(m, 'mixer_head', [5, 11, 5], [11, 13, 11], 'ci');
    B(m, 'motor', [6.4, 13, 6.4], [9.6, 16, 9.6], 'blue');
    B(m, 'hopper', [11, 12, 1.6], [15, 16, 5.6], 'conc');
    B(m, 'cement_chute', [10, 11, 3], [11.4, 12.6, 4.4], 'st');
    B(m, 'waste_line', [1.6, 12, 7.4], [5, 13.2, 8.6], 'p_liq');
    return m;
  }
  function compactor() {
    const m = mk('plant/yellow');
    B(m, 'base', [0, 0, 0], [16, 2, 16], 'yel');
    for (const x of [0.6, 13.4]) B(m, `column_${x}`, [x, 2, 4], [x + 2, 16, 12], 'yel');
    B(m, 'crown', [0.6, 14, 3], [15.4, 17.4, 13], 'yel');
    B(m, 'cylinder', [6, 10, 6], [10, 14, 10], 'bright');
    B(m, 'platen', [3.6, 8.6, 3.6], [12.4, 10, 12.4], 'st');
    B(m, 'drum', [4, 2, 4], [12, 8, 12], 'drum');
    B(m, 'guard', [2.6, 2, 3.4], [13.4, 13, 3.8], 'gal', { hide: [] });
    B(m, 'power_pack', [13.4, 2, 12.4], [16, 7, 16], 'blue');
    return m;
  }
  function oxalate() {
    const m = mk('electronics/ss');
    for (const [x, z] of [[3, 2], [12, 2], [3, 12], [12, 12]]) B(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 4, z + 1], 'frame');
    m.prismY('vessel', [8, 7.5], 5, 4, 14, 'ss', {});
    m.prismY('cover', [8, 7.5], 5.4, 14, 14.6, 'ss', { ends: ['up', 'down'] });
    B(m, 'motor', [6.4, 14.6, 5.9], [9.6, 17.4, 9.1], 'blue');
    B(m, 'filter', [2, 0, 1], [6, 4, 4.6], 'ss');
    B(m, 'acid_in', [12.6, 12, 7], [14, 15, 8.2], 'p_acid');
    B(m, 'slurry_out', [7.4, 2, 7], [8.6, 4, 8.2], 'st');
    return m;
  }
  function shear() {
    const m = mk(TEX + 'lead');
    B(m, 'cell', [0, 0, 1], [16, 15, 16], 'lead');
    B(m, 'window_frame', [3, 5, 0.6], [13, 13, 1], 'st');
    B(m, 'window', [4, 6, 0.4], [12, 12, 0.6], 'lglass');
    for (const x of [3.4, 11.4]) {
      oct(m, `manip_${x}`, x + 1.2, 1, 1, 13, 17.5, 'ss');
    }
    B(m, 'roof', [0, 15, 1], [16, 16, 16], 'st');
    B(m, 'feed_port', [12.6, 1, 0.6], [15.4, 4, 1], 'ci');
    B(m, 'sign', [6.4, 13.2, 0.9], [9.6, 14.6, 1], 'haz');
    return m;
  }
  function hepa() {
    const m = mk('electronics/ss');
    B(m, 'housing', [0, 1, 0.6], [16, 15, 16], 'ss');
    B(m, 'door', [1, 2, 0], [15, 14, 0.6], 'ss');
    B(m, 'pleats', [3, 4, -0.1], [13, 12, 0], 'hepa');
    for (const [x, y] of [[1.6, 7], [14, 7]]) B(m, `clamp_${x}`, [x, y, -0.4], [x + 0.6, y + 2, 0], 'st');
    B(m, 'gauge', [12, 13, -0.2], [14, 15, 0], 'en', { face: { north: { tex: 'control/pressure_dial', uv: [0, 0, 16, 16] } } });
    B(m, 'duct_in', [4, 15, 4], [12, 16.75, 12], 'gal');
    B(m, 'feet', [1, 0, 2], [15, 1, 14], 'frame');
    return m;
  }

  const BUILDS = {
    moderator_block_graphite: { model: moderator, cat: 'core' },
    fuel_channel: { model: () => channel('fuel'), cat: 'core' },
    control_rod_channel: { model: () => channel('rod'), cat: 'core' },
    irradiation_channel: { model: () => channel('irr', false), cat: 'core' },
    irradiation_channel_loaded: { model: () => channel('irr', true), cat: 'core' },
    startup_source: { model: startupSource, cat: 'core' },
    control_rod_drive: { model: rodDrive, cat: 'mechanics' }, zip_rod_winch: { model: zipWinch, cat: 'mechanics' },
    eccs_accumulator: { model: accumulator, cat: 'safety' }, fuel_pool_rack_open: { model: fuelRack, cat: 'spent_fuel' },
    rtg_thermoelectric: { model: () => rtg(true), cat: 'isotopes' }, rtg_thermoelectric_depleted: { model: () => rtg(false), cat: 'isotopes' },
    decon_shower: { model: deconShower, cat: 'radiation' }, hand_foot_monitor: { model: handFoot, cat: 'radiation' },
    lead_block: { model: leadBlock, cat: 'radiation' }, ultrasonic_bath: { model: ultrasonic, cat: 'radiation' },
    bitumenizer: { model: bitumeniser, cat: 'waste' }, cementation_mixer: { model: cementer, cat: 'waste' },
    waste_compactor: { model: compactor, cat: 'waste' },
    oxalate_precipitator: { model: oxalate, cat: 'radiochemistry' }, shearing_machine: { model: shear, cat: 'radiochemistry' },
    hepa_filter: { model: hepa, cat: 'ventilation' },
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
  window.RXH = {
    paint, ids: Object.keys(BUILDS), build: realize,
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
        realize(root, id);
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
        while (Dialog.open) Dialog.open.close();
      }
      return out;
    },
  };
})();
