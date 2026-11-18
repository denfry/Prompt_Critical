// Machine shop, hand-authored for Blockbench (replaces the spec_visual.py and spec_metallurgy.py placeholders):
// screw-cutting lathe, column drill press, horizontal knee milling machine (6R82 type), crank shaper (7305
// type) and surface grinder (3G71 type), with their panel boards.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/machine_shop/machine_tools.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); MTL.paint('<repo>'); MTL.paintGui('<repo>')
//   build + save each model in turn (MTL.build / MTL.save, ids in MTL.ids)
//
// Front to the north everywhere. Partial contracts (FoundryClient):
//   lathe 2x1x1, spindle SPIN_X about y 14, z 8; tool tip at x 17, y 14.5, z 9.6 (MachineFx chips).
//   drill press 1x2x1, spindle SPIN_Y about x 8, z 7; drill point at y 11..12 over the table.
//   milling machine 1x2x1, arbor SPIN_Z about x 8, y 18 (horizontal mill); the disc cutter's lowest point at
//     x 8, y 14.5, z 4.8 (chips).
//   shaper 2x1x1, ram SLIDE_X +-4 px; tool point at y 13.2 over the work at x 23..27.
//   surface grinder 2x1x1, wheel SPIN_Z about x 13, y 14, radius 4: its face at y 10 on the work (sparks).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Machine tool enamel: the grey-green of Soviet shop machines, oil-darkened low down, chipped to primer.
  T['machine_shop/paint'] = () => {
    const im = img(32, 32), r = grain(im, [104, 122, 114], 2201, 3, 4, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4;
      put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [52, 54, 50], Math.max(0, (y - 22) / 10) * 0.35));
    }
    for (let k = 0; k < 2; k++) { const x = Math.floor(r() * 31), y = Math.floor(r() * 31); put(im, x, y, [112, 74, 60]); }
    return im;
  };
  // Scraped ways and machined faces: bright steel with the crescent flaking of hand scraping.
  T['machine_shop/scraped'] = () => {
    const im = img(32, 32), r = grain(im, [176, 180, 184], 2211, 3, 3, 8);
    for (let y = 1; y < 32; y += 4) for (let x = (y % 8 === 1 ? 0 : 2); x < 32; x += 4) {
      put(im, x, y, [206, 210, 214]); put(im, x + 1, y, [196, 200, 204]); put(im, x + 1, y + 1, [150, 154, 158]);
    }
    return im;
  };
  // T-slotted table top: machined grey with three dark slots per block width.
  T['machine_shop/tslot'] = () => {
    const im = img(32, 32), r = grain(im, [150, 154, 158], 2221, 3, 3, 8);
    for (const y of [4, 15, 26]) { rect(im, 0, y, 32, 2, [40, 42, 44]); rect(im, 0, y - 1, 32, 1, [196, 200, 204]); }
    return im;
  };
  // Vitrified grinding wheel: grey alumina grit with a dressed face.
  T['machine_shop/wheel'] = () => { const im = img(32, 32), r = grain(im, [176, 172, 164], 2231, 10, 4, 4); for (let k = 0; k < 80; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [120, 116, 110]); return im; };
  // Cutter: ground high-speed steel, dark straw temper colours at the teeth.
  T['machine_shop/cutter'] = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8, a = Math.atan2(y - 7.5, x - 7.5);
      const tooth = Math.cos(a * 12) > 0.3 && d > 0.75;
      put(im, x, y, d > 1 ? [60, 60, 62] : tooth ? [176, 150, 90] : d < 0.3 ? [60, 62, 66] : [160, 164, 168]);
    }
    return im;
  };

  const MAT = {
    mp: { tex: 'machine_shop/paint', P: 16 }, sc: { tex: 'machine_shop/scraped', P: 16 }, ts: { tex: 'machine_shop/tslot', P: 16 },
    wh: { tex: 'machine_shop/wheel', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'machine_shop/paint' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Handwheel about x or z: rim from four bars, two spokes, a knob.
  function wheel(m, n, axis, c, r, a0, a1) {
    const w = 0.5;
    if (axis === 'x') {
      m.box(`${n}_rim_t`, [a0, c[0] + r - w, c[1] - r], [a1, c[0] + r, c[1] + r], 'ci', { split: '' });
      m.box(`${n}_rim_b`, [a0, c[0] - r, c[1] - r], [a1, c[0] - r + w, c[1] + r], 'ci', { split: '' });
      m.box(`${n}_rim_n`, [a0, c[0] - r, c[1] - r], [a1, c[0] + r, c[1] - r + w], 'ci', { split: '' });
      m.box(`${n}_rim_s`, [a0, c[0] - r, c[1] + r - w], [a1, c[0] + r, c[1] + r], 'ci', { split: '' });
      m.box(`${n}_spoke`, [a0, c[0] - 0.25, c[1] - r], [a1, c[0] + 0.25, c[1] + r], 'st', { split: '' });
      m.box(`${n}_knob`, [a1, c[0] + r - 0.6, c[1] - 0.3], [a1 + 1, c[0] + r, c[1] + 0.3], 'red', { split: '' });
    } else {
      m.box(`${n}_rim_t`, [c[0] - r, c[1] + r - w, a0], [c[0] + r, c[1] + r, a1], 'ci', { split: '' });
      m.box(`${n}_rim_b`, [c[0] - r, c[1] - r, a0], [c[0] + r, c[1] - r + w, a1], 'ci', { split: '' });
      m.box(`${n}_rim_w`, [c[0] - r, c[1] - r, a0], [c[0] - r + w, c[1] + r, a1], 'ci', { split: '' });
      m.box(`${n}_rim_e`, [c[0] + r - w, c[1] - r, a0], [c[0] + r, c[1] + r, a1], 'ci', { split: '' });
      m.box(`${n}_spoke`, [c[0] - r, c[1] - 0.25, a0], [c[0] + r, c[1] + 0.25, a1], 'st', { split: '' });
      m.box(`${n}_knob`, [c[0] + r - 0.6, c[1] - 0.3, a0 - 1], [c[0] + r, c[1] + 0.3, a0], 'red', { split: '' });
    }
  }

  // ---------------------------------------------------------------- lathe
  function lathe() {
    const m = mk();
    m.box('pedestal_w', [0.4, 0, 3], [9, 7, 13], 'mp', { split: '' });
    m.box('pedestal_e', [24, 0, 3], [31.6, 7, 13], 'mp', { split: '' });
    m.box('chip_pan', [0, 7, 1.4], [32, 7.6, 14.6], 'st', { split: 'x' });
    m.box('bed', [0, 7.6, 4], [32, 10, 12], 'mp', { split: 'x' });
    m.box('way_n', [8, 10, 4.6], [31.4, 10.8, 6.6], 'sc', { split: 'x' });
    m.box('way_s', [8, 10, 9.4], [31.4, 10.8, 11.4], 'sc', { split: 'x' });
    // headstock with the gear cover over the Create drive end and change levers
    m.box('headstock', [0.4, 10, 3], [8, 19, 13], 'mp', { split: '' });
    m.box('gear_cover', [0, 10.4, 3.4], [0.6, 18.6, 12.6], 'mp', { split: '' });
    m.prism('drive_boss', 'x', [8, 8], 2.4, 0, 0.6, 'ci', { split: '', ends: ['west'] });
    m.box('lever_a', [3, 19, 6], [3.8, 22, 6.8], 'red', { split: '' });
    m.box('lever_b', [5.2, 19, 9.2], [6, 21.2, 10], 'red', { split: '' });
    m.box('spindle_nose', [8, 12, 6], [8.6, 16, 10], 'sc', { split: '' });
    // quick-change gearbox, lead screw and feed rod along the front
    m.box('feed_box', [0.6, 7.6, 2.2], [8, 10.6, 4], 'mp', { split: '' });
    m.box('lead_screw', [8, 8.6, 1.6], [31.4, 9.2, 2.2], 'bright', { split: 'x' });
    m.box('feed_rod', [8, 7.8, 2.4], [31.4, 8.3, 2.9], 'bright', { split: 'x' });
    // carriage: saddle, apron with handwheel, cross slide, compound, four-way tool post
    m.box('saddle', [14, 10.8, 2.4], [20, 12.4, 13.6], 'mp', { split: '' });
    m.box('apron', [14, 6, 1.4], [20, 10.8, 2.6], 'mp', { split: '' });
    wheel(m, 'apron_wheel', 'z', [16, 8], 1.8, 0.6, 1.4);
    m.box('cross_slide', [15, 12.4, 4], [19, 13.4, 12], 'sc', { split: '' });
    m.box('compound', [15.4, 13.4, 6.6], [18.6, 14, 10.6], 'sc', { split: '' });
    m.box('tool_post', [15.6, 14, 7.2], [18.4, 16.2, 10], 'st', { split: '' });
    m.box('tool_bit', [16.6, 14.2, 9.6], [17.4, 14.8, 10.8], 'bright', { split: '' });
    // tailstock with quill and handwheel
    m.box('tailstock', [25, 10.8, 5], [31, 17, 11], 'mp', { split: '' });
    m.prism('tail_quill', 'x', [14, 8], 0.8, 22, 25, 'sc', { split: '', ends: ['west'] });
    wheel(m, 'tail_wheel', 'x', [14, 8], 2, 31, 31.6);
    return m;
  }

  function latheSpindle() {
    const m = mk('material/bright_steel');
    m.prism('chuck', 'x', [14, 8], 3.4, 8.6, 10.6, 'st', { split: '', ends: ['east'] });
    for (const [dy, dz] of [[2.2, 0], [-1.1, 1.9], [-1.1, -1.9]]) m.box(`jaw_${dy}_${dz}`, [10.6, 14 + dy - 0.6, 8 + dz - 0.6], [11.6, 14 + dy + 0.6, 8 + dz + 0.6], 'st', { split: '' });
    m.prism('work', 'x', [14, 8], 1.5, 10.6, 17.4, 'ci', { split: '', ends: ['east'] });
    m.prism('work_turned', 'x', [14, 8], 1.2, 17.4, 22, 'bright', { split: '', ends: ['east'] });
    return m;
  }

  // ---------------------------------------------------------------- drill press
  function drillPress() {
    const m = mk();
    m.box('base', [1, 0, 1], [15, 2.4, 15], 'mp', { split: '', face: { up: 'ts' } });
    m.prismY('column', [8, 13], 2, 2.4, 27, 'sc');
    m.box('drive_box', [5.4, 4.6, 13], [10.6, 11, 16], 'ci', { split: '' });
    m.box('table_arm', [6, 8.6, 9], [10, 11, 13], 'mp', { split: '' });
    m.box('table', [2.6, 9.6, 1.6], [13.4, 11, 11.4], 'mp', { split: '', face: { up: 'ts' } });
    m.box('work', [5.4, 11, 4.4], [10.6, 11.6, 9.6], 'ci', { split: '' });
    m.box('head', [4, 18.6, 3], [12, 27.6, 15], 'mp', { split: '' });
    m.box('quill_housing', [6, 15.4, 4.6], [10, 18.6, 9.4], 'mp', { split: '' });
    m.box('belt_guard', [3, 27.6, 3], [13, 30.2, 11], 'mp', { split: '' });
    m.box('motor', [5, 27.6, 11], [11, 32, 15.6], 'ci', { split: '' });
    for (let k = 0; k < 4; k++) m.box(`motor_fin_${k}`, [4.6, 28.2 + k, 11.4], [11.4, 28.6 + k, 15.2], 'ci', { split: '' });
    // three-star feed handle on the right
    m.box('feed_hub', [12, 21.4, 6.4], [13.4, 23.6, 8.6], 'st', { split: '' });
    for (const [n, f, t] of [['a', [13.4, 22.2, 7.2], [16, 22.8, 7.8]], ['b', [12.4, 23.6, 7.2], [13, 26.4, 7.8]], ['c', [12.4, 18.6, 7.2], [13, 21.4, 7.8]]]) {
      m.box(`feed_arm_${n}`, f, t, 'bright', { split: '' });
    }
    m.box('feed_knob', [15.6, 21.9, 7], [16, 23.1, 8], 'red', { split: '' });
    m.box('depth_stop', [10, 16, 3.6], [10.6, 22, 4.2], 'bright', { split: '' });
    return m;
  }

  function drillSpindle() {
    const m = mk('material/bright_steel');
    m.prismY('spindle', [8, 7], 0.9, 13.6, 15.4, 'sc');
    m.prismY('chuck', [8, 7], 1.4, 12.6, 13.6, 'st', { ends: ['down'] });
    m.prismY('drill', [8, 7], 0.4, 11.4, 12.6, 'bright');
    return m;
  }

  // ---------------------------------------------------------------- horizontal mill
  function mill() {
    const m = mk();
    m.box('base', [1, 0, 1], [15, 2, 16], 'mp', { split: '' });
    m.box('column', [3, 2, 9], [13, 30, 16], 'mp', { split: 'y' });
    m.box('column_ways', [3.6, 2, 8.6], [12.4, 26, 9], 'sc', { split: 'y' });
    m.prism('drive_boss', 'x', [8, 12.6], 2.2, 2.4, 3, 'ci', { split: '', ends: ['west'] });
    // knee on the column ways with the elevating screw, saddle, table with T-slots overhanging both sides
    m.box('knee', [3.4, 4, 2.6], [12.6, 10, 8.6], 'mp', { split: '' });
    m.box('elevating_screw', [7.6, 2, 4.6], [8.4, 4, 5.4], 'bright', { split: '' });
    m.box('saddle', [3.4, 10, 3], [12.6, 11.6, 8.6], 'mp', { split: '' });
    m.box('table', [-6, 11.6, 3.4], [22, 13, 8.2], 'mp', { split: 'x', face: { up: 'ts' } });
    wheel(m, 'table_wheel_w', 'x', [12.3, 5.8], 1.6, -7, -6);
    wheel(m, 'knee_crank', 'z', [6, 7], 1.4, 1.6, 2.4);
    m.box('feed_lever', [11, 8, 2.2], [11.6, 10.6, 2.6], 'red', { split: '' });
    // machine vise with the work under the cutter
    m.box('vise_body', [4.6, 13, 3.8], [11.4, 13.6, 7.8], 'mp', { split: '' });
    m.box('vise_jaw_fixed', [4.6, 13.6, 6.8], [11.4, 14.6, 7.8], 'sc', { split: '' });
    m.box('vise_jaw_moving', [4.6, 13.6, 3.8], [11.4, 14.6, 4.4], 'sc', { split: '' });
    m.box('work', [5.4, 13.6, 4.4], [10.6, 14.4, 6.8], 'ci', { split: '' });
    // overarm on top, arbor support hanging at the front, spindle nose on the column face
    m.box('overarm', [5.2, 26.4, 0.6], [10.8, 29.4, 16], 'sc', { split: 'z' });
    m.box('arbor_support', [6, 19.6, 0.8], [10, 26.4, 2.8], 'mp', { split: '' });
    m.box('arbor_bearing', [6.6, 16.6, 0.8], [9.4, 19.6, 2.8], 'mp', { split: '' });
    m.prism('spindle_nose', 'z', [8, 18], 2, 8.6, 9, 'sc', { split: '', ends: ['north'] });
    // motor at the back of the column
    m.box('motor', [4, 4, 16], [12, 12, 16.6], 'ci', { split: '' });
    return m;
  }

  // Arbor partial, spun about z through (8, 18): the arbor, spacer collars and the side-and-face disc cutter.
  function millArbor() {
    const m = mk('material/bright_steel');
    m.prism('arbor', 'z', [8, 18], 0.7, 1.4, 8.6, 'bright', { split: '' });
    for (const [a, b] of [[2.8, 4], [5.6, 8.6]]) m.prism(`collar_${a}`, 'z', [8, 18], 1.2, a, b, 'sc', { split: '' });
    m.disc('cutter', 'z', [8, 18], 3.5, 4, 5.6, 'st', 'machine_shop/cutter', 'north');
    return m;
  }

  // ---------------------------------------------------------------- shaper
  function shaper() {
    const m = mk();
    m.box('base', [0, 0, 2], [32, 2, 14], 'mp', { split: 'x' });
    m.box('body', [0.4, 2, 3], [18, 14, 13], 'mp', { split: '' });
    m.prism('drive_boss', 'x', [8, 8], 2.4, 0, 0.4, 'ci', { split: '', ends: ['west'] });
    m.box('motor', [2, 3, 13], [10, 10, 16], 'ci', { split: '' });
    for (let k = 0; k < 4; k++) m.box(`motor_fin_${k}`, [2.4 + k * 2, 3, 15.6], [3 + k * 2, 10, 16.2], 'ci', { split: '' });
    m.box('ram_ways', [2, 14, 4.4], [18, 14.6, 11.6], 'sc', { split: '' });
    m.box('stroke_lever', [8, 14, 2.2], [8.8, 17, 3], 'red', { split: '' });
    wheel(m, 'stroke_wheel', 'z', [12, 8], 1.6, 2.2, 3);
    // cross rail on the front of the body, the table on it with a vise and the work
    m.box('cross_rail', [18, 6, 1], [19.6, 11, 15], 'sc', { split: '' });
    m.box('table', [19.6, 4, 3], [30, 11.6, 13], 'mp', { split: '', face: { up: 'ts' } });
    m.box('table_support', [22, 2, 5], [28, 4, 11], 'ci', { split: '' });
    m.box('vise', [21.6, 11.6, 5], [28.4, 12.6, 11], 'ci', { split: '' });
    m.box('work', [22.6, 12.6, 6], [27.4, 13.2, 10], 'ci', { split: '' });
    m.box('feed_screw', [20, 9, 0.4], [30, 9.6, 1], 'bright', { split: '' });
    return m;
  }

  // Ram partial, slid along x: ram on its ways, the tool head with clapper box and the tool.
  function shaperRam() {
    const m = mk();
    m.box('ram', [4, 14.6, 5], [22, 18, 11], 'sc', { split: '' });
    m.box('ram_top', [4, 18, 5.6], [22, 18.4, 10.4], 'mp', { split: '' });
    m.box('head', [20, 14, 6], [24, 18, 10], 'mp', { split: '' });
    m.box('clapper', [21, 13.6, 6.6], [23, 14, 9.4], 'st', { split: '' });
    m.box('tool', [21.6, 13.2, 7.6], [22.4, 13.6, 8.4], 'bright', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- surface grinder
  function grinder() {
    const m = mk();
    m.box('base', [0, 0, 2], [32, 7.6, 14], 'mp', { split: 'x' });
    m.box('saddle_ways', [1, 7.6, 3.4], [26, 8.2, 12.6], 'sc', { split: 'x' });
    m.box('table', [1.6, 8.2, 3], [26, 9.2, 13], 'mp', { split: 'x' });
    m.box('chuck', [5, 9.2, 4], [21, 9.6, 12], 'sc', { split: '' });
    m.box('work', [10, 9.6, 6], [16, 10, 10], 'ci', { split: '' });
    for (const x of [3, 23.6]) m.box(`table_dog_${x}`, [x, 9.2, 3.2], [x + 0.6, 10.4, 3.8], 'red', { split: '' });
    wheel(m, 'table_wheel', 'z', [8, 4.6], 1.8, 1.2, 2);
    // column at the east end, wheel head on its ways reaching over the table, guard over the wheel
    m.box('column', [26, 0, 4], [32, 23, 12], 'mp', { split: 'y' });
    m.box('wheel_head', [15, 16, 5], [26, 22, 11], 'mp', { split: '' });
    m.box('head_motor', [18, 22, 5.6], [24, 25, 10.4], 'ci', { split: '' });
    m.box('guard_top', [8.4, 18.4, 4.4], [17.6, 19, 11.6], 'mp', { split: '' });
    m.box('guard_back', [8.4, 12, 11], [17.6, 18.4, 11.6], 'mp', { split: '' });
    m.box('guard_east', [17, 12, 4.4], [17.6, 18.4, 11], 'mp', { split: '' });
    m.box('coolant_nozzle', [8.6, 11, 7.6], [9.6, 12, 8.4], 'br', { split: '' });
    wheel(m, 'downfeed_wheel', 'z', [28, 17], 1.6, 3.2, 4);
    return m;
  }

  // Wheel partial, spun about z through (13, 14): the wheel between flanges.
  function grinderWheel() {
    const m = mk('machine_shop/wheel');
    m.prism('wheel', 'z', [13, 14], 4, 5.4, 10.6, 'wh', { split: '', ends: ['north', 'south'] });
    m.prism('flange', 'z', [13, 14], 2, 5, 11, 'st', { split: '', ends: ['north', 'south'] });
    return m;
  }

  // Panel board shared by FoundryPanels.tool() and production() (200x150): rings behind the two dials, a plate
  // for the lamps and bars, a strip for the read-out and the slots. Tinted per machine.
  function guiTool(seed, base) {
    const W = 200, H = 150, im = img(W, H), r = rng(seed);
    grain(im, base, seed + 1, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (const cx of [32, 90]) for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, cx + 26 * Math.cos(t), 48 + 26 * Math.sin(t), add(base, -40)); put(im, cx + 27 * Math.cos(t) + 0.5, 48 + 27 * Math.sin(t) + 0.5, add(base, 50));
    }
    for (const [x0, y0, x1, y1] of [[120, 22, W - 6, 92], [6, 118, W - 6, 146]]) for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
      const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
      put(im, x, y, add(add(base, -12), edge + (r() - 0.5) * 4));
    }
    return im;
  }

  function paintGui(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/gui/panel');
    const boards = { machine_lathe: [2241, [70, 82, 76]], drill_press: [2251, [72, 80, 82]], milling_machine: [2261, [68, 80, 74]],
      shaping_machine: [2271, [76, 80, 72]], surface_grinder: [2281, [72, 76, 82]] };
    for (const [id, [seed, base]] of Object.entries(boards)) writePng(guiTool(seed, base), path.join(dir, id + '.png'));
    return Object.keys(boards).length;
  }

  const BUILDS = {
    machine_lathe: { model: lathe },
    'machine_lathe/spindle': { model: latheSpindle, java: 'machine_lathe/partial/spindle' },
    drill_press: { model: drillPress },
    'drill_press/spindle': { model: drillSpindle, java: 'drill_press/partial/spindle' },
    milling_machine: { model: mill },
    'milling_machine/spindle': { model: millArbor, java: 'milling_machine/partial/spindle' },
    shaping_machine: { model: shaper },
    'shaping_machine/ram': { model: shaperRam, java: 'shaping_machine/partial/ram' },
    surface_grinder: { model: grinder },
    'surface_grinder/wheel': { model: grinderWheel, java: 'surface_grinder/partial/wheel' },
  };

  window.MTL = {
    paint, paintGui, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'machine_shop', id, BUILDS[id].java),
    preview(root) {
      return PCART.realize(root, lathe().merge(latheSpindle(), 0, 0, 0, 'ls_').merge(drillPress(), 40, 0, 0, 'd_').merge(drillSpindle(), 40, 0, 0, 'ds_')
        .merge(mill(), 60, 0, 0, 'm_').merge(millArbor(), 60, 0, 0, 'ma_').merge(shaper(), 0, 0, 30, 's_').merge(shaperRam(), 0, 0, 30, 'sr_')
        .merge(grinder(), 40, 0, 30, 'g_').merge(grinderWheel(), 40, 0, 30, 'gw_'), 'machine_tools_preview');
    },
  };
})();
