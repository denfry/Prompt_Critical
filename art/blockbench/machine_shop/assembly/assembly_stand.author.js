// Assembly stand of GDD 18 §18.8.2 at real scale (1 block = 1 m, 16 units = 1 m), 3 x 3 x 3 blocks, front north.
// A cast-iron T-slotted assembly plate 2.5 x 1.75 m with its top at 0.875 m on two box pedestals; over it a hand
// gantry of 3 m span (two portal frames, an I-beam girder along x at 2.6 m under the flange) with a chain hoist on a
// plain trolley; on the west half of the plate a run-in and balancing rig: a test motor coupled to a rotor that lies
// in two roller balancing supports, a dial indicator on a magnetic stand reading the rotor; on the east half the
// part being assembled (a volute pump casing on its feet), V-blocks, an angle plate, a tote of fasteners and a torque
// wrench; in the north-west corner the pendant post with the start and stop buttons.
// Sizes after Soviet shop practice: assembly plates to GOST 10905 (cast iron, scraped, T-slots 22 mm at 250 mm
// pitch), roller balancing supports of a static balancing stand, a 1 t hand chain hoist (TRShS type) on a push
// trolley.
//
// Run headless (no Blockbench): node art/tools/pcart_headless.js <repo> machine_shop/assembly/assembly_stand.author.js ASM --paint
// --paint also writes the GUI board textures/gui/panel/assembly_stand.png.
//
// Contracts (model px, whole-model frame: controller cell (0, 0, 0) at the north-west bottom corner, footprint x, y,
// z 0..2):
//   whole model   models/block/assembly_stand/chunk/*.json, wholemodels/assembly_stand.json (one chunk).
//   rotor         partial assembly_stand/partial/rotor, saved unshifted. SPIN_X about (y 22, z 14): coupling, shaft,
//                 laminated core, end rings and the red balancing weight (which makes the spin readable).
//   hook          partial assembly_stand/partial/hook, saved shifted (-16, -16, 0): draw it at (+1, +1, 0) blocks.
//                 TRANSLATE -y by 0..7 px (hook lowered onto the pump casing at 7). The load chain runs up inside
//                 the girder web, so it never shows a gap at any travel in that range. Hook eye at (38, 29.5, 14).
// Effect points: rotor bearings (13.5, 22, 14) and (27.5, 22, 14); coupling (10.5, 22, 14); indicator tip (20.3, 25.8,
// 14); torque work on the pump casing flange bolts (38, 22.5, 11); hoist housing (38, 39, 14).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Crane yellow enamel, scuffed to grey primer on the edges, a little grime.
  T['assembly/crane_yellow'] = () => {
    const im = img(32, 32), r = grain(im, [214, 172, 40], 5101, 3, 6, 8);
    for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 31), y = Math.floor(r() * 31); put(im, x, y, [120, 118, 110]); put(im, x + 1, y, [140, 136, 124]); }
    for (let x = 0; x < 32; x++) { put(im, x, 0, [234, 196, 70]); put(im, x, 31, [168, 132, 30]); }
    return im;
  };
  // Hazard stripes of the hook block: yellow and black at 45 degrees, 4 px bands.
  T['assembly/hazard'] = () => {
    const im = img(32, 32), r = rng(5111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const band = Math.floor(((x + y) % 32) / 8) % 2;
      put(im, x, y, add(band ? [30, 30, 32] : [220, 178, 44], (r() - 0.5) * 10));
    }
    return im;
  };
  // Rotor core: a stack of electrical steel laminations, one dark seam every 2 px across the axis (texture u = x).
  T['assembly/laminations'] = () => {
    const im = img(32, 32), r = grain(im, [112, 118, 124], 5121, 3, 3, 8);
    for (let x = 0; x < 32; x += 2) for (let y = 0; y < 32; y++) put(im, x, y, add([74, 78, 84], (r() - 0.5) * 6));
    return im;
  };
  // Motor enamel: the blue-grey of Soviet A-series induction motors.
  T['assembly/motor'] = () => grainOnly([84, 104, 124], 5131);
  // Dial indicator face: white dial, black ticks, the red needle at about eleven o'clock, chromed bezel.
  T['assembly/dial'] = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy) / 8, a = Math.atan2(dy, dx);
      let c = d > 1 ? [60, 62, 66] : d > 0.82 ? [196, 200, 206] : [236, 234, 226];
      if (d > 0.62 && d <= 0.8 && Math.abs(Math.sin(a * 10)) < 0.3) c = [30, 30, 32];
      put(im, x, y, c);
    }
    for (let k = 0; k < 6; k++) put(im, 8 - k * 0.6, 8 - k * 0.8, [196, 40, 36]);
    return im;
  };
  function grainOnly(base, seed) { const im = img(32, 32); grain(im, base, seed, 3, 5, 8); return im; }

  const MAT = {
    mp: { tex: 'machine_shop/paint', P: 16 }, sc: { tex: 'machine_shop/scraped', P: 16 }, ts: { tex: 'machine_shop/tslot', P: 16 },
    yel: { tex: 'assembly/crane_yellow', P: 16 }, haz: { tex: 'assembly/hazard', P: 16 }, lam: { tex: 'assembly/laminations', P: 16 },
    mot: { tex: 'assembly/motor', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'material/cast_iron' });

  // ------------------------------------------------------------------ static stand
  function stand() {
    const m = mk();
    // assembly plate: scraped T-slotted top, cast sides with a rib band, on two box pedestals with levelling feet
    m.box('plate', [4, 10.6, 8], [44, 14, 36], 'ci', { face: { up: 'ts' } });
    m.box('plate_rib_n', [4.4, 9.6, 8.4], [43.6, 10.6, 9.2], 'ci', { split: 'x' });
    m.box('plate_rib_s', [4.4, 9.6, 34.8], [43.6, 10.6, 35.6], 'ci', { split: 'x' });
    for (const [n, x0, x1] of [['w', 6, 13], ['e', 35, 42]]) {
      m.box(`pedestal_${n}`, [x0, 1, 10], [x1, 9.6, 34], 'ci', { split: 'yz' });
      m.box(`pedestal_foot_${n}`, [x0 - 0.6, 0, 9.4], [x1 + 0.6, 1, 34.6], 'ci', { split: 'z' });
      for (const z of [12, 22, 32]) m.box(`pedestal_window_${n}_${z}`, [x0 - 0.01, 3, z - 1.6], [x0, 7.6, z + 1.6], 'soot', { split: '' });
    }
    // gantry: two portal frames at the ends, the I-beam girder along x at z 14 over the run-in rig and the casing
    for (const [n, x] of [['w', 0.6], ['e', 45.4]]) {
      for (const z of [3, 41]) {
        m.box(`col_${n}_${z}`, [x, 1, z], [x + 2, 41, z + 2], 'yel', { split: 'y' });
        m.box(`col_foot_${n}_${z}`, [x - 0.4, 0, z - 0.4], [x + 2.4, 1, z + 2.4], 'st', { split: '' });
        m.box(`col_gusset_${n}_${z}`, [x + 0.6, 36, z < 20 ? z + 2 : z - 3], [x + 1.4, 41, z < 20 ? z + 5 : z], 'yel', { split: '' });
      }
      m.box(`portal_beam_${n}`, [x, 41, 3], [x + 2, 45, 43], 'yel', { split: 'z' });
      m.box(`portal_stay_${n}`, [x + 0.7, 10, 5], [x + 1.3, 10.6, 41], 'yel', { split: 'z' });
    }
    m.box('girder_bottom', [0.6, 41, 12], [47.4, 41.6, 16], 'yel', { split: 'x' });
    m.box('girder_web', [0.6, 41.6, 13.6], [47.4, 44.4, 14.4], 'yel', { split: 'x' });
    m.box('girder_top', [0.6, 44.4, 12], [47.4, 45, 16], 'yel', { split: 'x' });
    // push trolley on the bottom flange, the chain hoist hung from it over the east half
    for (const z of [11.2, 16]) m.box(`trolley_side_${z}`, [35, 38.6, z], [41, 42.4, z + 0.8], 'yel', { split: '' });
    for (const x of [36, 40]) for (const z of [12, 15.2]) m.prism(`trolley_wheel_${x}_${z}`, 'z', [x, 41.8], 0.8, z, z + 0.8, 'st', { split: '' });
    m.box('hoist_hanger', [37.4, 38.6, 12], [38.6, 41, 16], 'st', { split: '' });
    m.box('hoist_housing', [35.6, 35, 11.4], [40.4, 38.6, 16.6], 'yel', { split: '' });
    m.disc('hoist_hand_wheel', 'x', [36.8, 14], 2.6, 40.4, 41, 'st', 'vver/flange', 'east');
    for (const z of [12, 16]) m.box(`hand_chain_${z}`, [40.9, 20, z - 0.2], [41.2, 35, z + 0.2], 'st', { split: '' });
    m.box('hoist_plate', [37, 36, 11.39], [39, 37.4, 11.4], 'br', { split: '' });
    // run-in rig: test motor on its feet, two roller balancing supports carrying the rotor (partial) at (y 22, z 14)
    m.box('motor_feet', [4.6, 14, 10.8], [9.8, 16.4, 17.2], 'mot', { split: '' });
    m.prism('motor_body', 'x', [22, 14], 3.2, 5, 9.4, 'mot', { split: '', ends: ['west'] });
    for (const x of [5.8, 6.8, 7.8, 8.8]) m.ring(`motor_fin_${x}`, 'x', [22, 14], 3.5, 0.4, x, x + 0.4, 'mot');
    m.prism('motor_endshield', 'x', [22, 14], 2.6, 9.4, 9.8, 'mot', { split: '', ends: ['east'] });
    m.box('motor_terminal_box', [6.4, 25, 12.6], [8.6, 26.4, 15.4], 'mot', { split: '' });
    m.box('motor_cable', [7.2, 14, 15.4], [7.8, 25.6, 16], 'soot', { split: 'y' });
    for (const [n, x] of [['w', 12.5], ['e', 26.5]]) {
      m.box(`support_base_${n}`, [x - 0.5, 14, 10], [x + 2.5, 15, 18], 'mp', { split: '' });
      m.box(`support_body_${n}`, [x, 15, 11], [x + 2, 19.6, 17], 'mp', { split: '' });
      for (const z of [12.8, 15.2]) {
        m.prism(`support_roller_${n}_${z}`, 'x', [20.4, z], 1, x + 0.2, x + 1.8, 'sc', { split: '', ends: ['east', 'west'] });
        m.box(`support_axle_${n}_${z}`, [x - 0.2, 20.1, z - 0.3], [x + 2.2, 20.7, z + 0.3], 'bright', { split: '' });
      }
      m.box(`support_guard_${n}`, [x + 0.6, 19.6, 10.6], [x + 1.4, 21, 11], 'mp', { split: '' });
    }
    // dial indicator on a magnetic base, the plunger on top of the rotor core
    m.box('indicator_base', [18.6, 14, 8.6], [21.4, 15.8, 11], 'st', { split: '' });
    m.box('indicator_switch', [19.6, 15.8, 8.5], [20.4, 16.4, 9.1], 'red', { split: '' });
    m.box('indicator_column', [20, 15.8, 9.6], [20.6, 29, 10.2], 'bright', { split: 'y' });
    m.box('indicator_clamp', [19.8, 28.2, 9.4], [20.8, 29.2, 10.4], 'st', { split: '' });
    m.box('indicator_arm', [20, 28.4, 10.2], [20.6, 29, 14.4], 'bright', { split: '' });
    m.disc('indicator_dial', 'z', [20.3, 27.2], 1.3, 13.4, 14, 'st', 'assembly/dial', 'north');
    m.box('indicator_plunger', [20.15, 25.8, 13.65], [20.45, 25.9, 13.95], 'bright', { split: '' });
    // part under assembly: volute pump casing on its feet, suction branch north, discharge flange up
    m.box('casing_feet', [34.4, 14, 10.4], [41.6, 15.4, 17.6], 'ci', { split: '' });
    m.prism('casing_volute', 'z', [38, 19], 3.8, 12, 17, 'ci', { split: '', ends: ['north', 'south'] });
    m.disc('casing_suction_flange', 'z', [38, 19], 2, 11, 12, 'ci', 'vver/flange', 'north');
    m.box('casing_discharge', [39.4, 21.6, 13.4], [41.8, 22.6, 15.6], 'ci', { split: '' });
    m.box('casing_discharge_flange', [39, 22.6, 13], [42.2, 23.2, 16], 'sc', { split: '' });
    for (const x of [35.6, 40.4]) m.box(`casing_bolt_${x}`, [x - 0.3, 22.4, 10.6], [x + 0.3, 23, 11.2], 'bright', { split: '' });
    // V-blocks, angle plate, fastener tote and the torque wrench laid on the plate
    for (const z of [22, 27]) {
      m.box(`vblock_${z}`, [33, 14, z], [36, 16.4, z + 2], 'sc', { split: '' });
      m.box(`vblock_vee_${z}`, [34, 15.6, z - 0.01], [35, 16.41, z + 2.01], 'soot', { split: '' });
    }
    m.box('angle_plate_base', [37, 14, 26], [43, 15, 33], 'ci', { split: '' });
    m.box('angle_plate_web', [37, 15, 31.6], [43, 21, 33], 'ci', { split: '' });
    m.box('angle_plate_rib', [39.6, 15, 27.6], [40.4, 19, 31.6], 'ci', { split: '' });
    m.box('tote', [22, 14, 28], [28, 16.6, 33], 'red', { split: '' });
    m.box('tote_inside', [22.6, 15.6, 28.6], [27.4, 16.61, 32.4], 'st', { split: '' });
    m.box('torque_wrench', [8, 14, 30.4], [19, 14.5, 31], 'bright', { split: 'x' });
    m.box('torque_wrench_grip', [16, 14, 30.2], [19, 14.7, 31.2], 'soot', { split: '' });
    m.box('torque_wrench_head', [7, 14, 30], [8.4, 14.8, 31.4], 'st', { split: '' });
    // pendant post at the north-west corner (the controller cell): start, stop and the three test lamps
    m.box('pendant_post', [1.4, 0, 1.4], [2.6, 20, 2.6], 'st', { split: 'y' });
    m.box('pendant_foot', [0.6, 0, 0.6], [3.4, 0.6, 3.4], 'st', { split: '' });
    m.box('pendant_box', [0.8, 20, 0.8], [3.8, 26, 3.4], 'mp', { split: '' });
    m.box('button_start', [1.4, 21.2, 0.4], [2.2, 22, 0.8], 'green', { split: '' });
    m.box('button_stop', [2.6, 21, 0.3], [3.4, 22.2, 0.8], 'red', { split: '' });
    for (const [i, mat] of [[0, 'green'], [1, 'br'], [2, 'red']]) m.box(`lamp_${i}`, [1.2 + i * 0.9, 24, 0.5], [1.8 + i * 0.9, 24.6, 0.8], mat, { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ partials
  function rotor() {
    const m = mk();
    m.prism('coupling', 'x', [22, 14], 1.9, 9.8, 11.6, 'ci', { split: '', ends: ['east', 'west'] });
    for (const a of [0, 90, 180, 270]) {
      const t = a * Math.PI / 180, y = 22 + 1.4 * Math.sin(t), z = 14 + 1.4 * Math.cos(t);
      m.box(`coupling_bolt_${a}`, [11.6, y - 0.25, z - 0.25], [12, y + 0.25, z + 0.25], 'bright', { split: '' });
    }
    m.prism('shaft', 'x', [22, 14], 0.8, 11.6, 31, 'bright', { split: '', ends: ['east'] });
    m.prism('core', 'x', [22, 14], 3.8, 16, 24, 'lam', { split: '' });
    m.prism('end_ring_w', 'x', [22, 14], 3.4, 15.4, 16, 'bright', { split: '', ends: ['west'] });
    m.prism('end_ring_e', 'x', [22, 14], 3.4, 24, 24.6, 'bright', { split: '', ends: ['east'] });
    m.box('balance_weight', [24.6, 24.2, 13.5], [25.2, 25.2, 14.5], 'red', { split: '' });
    return m;
  }
  function hook() {
    const m = mk();
    m.box('load_chain', [37.7, 37, 13.7], [38.3, 44, 14.3], 'st', { split: '' });
    m.box('hook_block', [36.5, 33, 12.8], [39.5, 37, 15.2], 'haz', { split: '' });
    m.box('hook_sheave_pin', [37.6, 35.4, 12.6], [38.4, 36.2, 15.4], 'bright', { split: '' });
    m.box('hook_shank', [37.6, 31, 13.6], [38.4, 33, 14.4], 'bright', { split: '' });
    m.box('hook_back', [36.4, 30, 13.6], [37.2, 32, 14.4], 'st', { split: '' });
    m.box('hook_bowl', [36.4, 29, 13.6], [39.2, 30, 14.4], 'st', { split: '' });
    m.box('hook_tip', [38.4, 30, 13.6], [39.2, 31.2, 14.4], 'st', { split: '' });
    m.box('hook_latch', [37.3, 31.4, 13.85], [38.6, 31.7, 14.15], 'bright', { split: '' });
    return m;
  }
  const PARTS = { rotor: { model: rotor, shift: [0, 0, 0] }, hook: { model: hook, shift: [-16, -16, 0] } };
  function partial(p) { const s = PARTS[p].model(); const [dx, dy, dz] = PARTS[p].shift; return mk().merge(s, dx, dy, dz, ''); }

  // ------------------------------------------------------------------ GUI board, 220 x 152
  // Engraved machine-shop board: brass frame, the T-slotted plate under the 3 x 3 position grid (slots at
  // x 16 + 20 i, y 30 + 20 j, i, j 0..2), an engraved arrow to the result seat (slot at x 100, y 50, ringed
  // bezel), three lamp bezels for fit, tightening and balance (lamps at x 130, y 30 / 50 / 70, labels to the right
  // to x 214), the run-in trough (bar at x 14, y 108, w 192, label above at y 98) and the bottom strip for the status
  // read-out (x 10, y 128, w 140) and the start button (x 156, y 126, w 54).
  const GUI_W = 220, GUI_H = 152;
  function gui() {
    const W = GUI_W, H = GUI_H, im = img(W, H), r = rng(5141), base = [64, 74, 70];
    grain(im, base, 5142, 3, 5, 12);
    const inset = (x0, y0, x1, y1, c, k = 22) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const e = x === x0 || y === y0 ? -k : x === x1 - 1 || y === y1 - 1 ? k * 0.8 : 0;
        put(im, x, y, add(c, e + (r() - 0.5) * 4));
      }
    };
    // brass frame
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    // T-slotted plate under the grid: grey cast iron, slots between the rows, scraping marks, four corner bolts
    inset(9, 24, 79, 92, [118, 122, 126], 26);
    for (let y = 25; y < 91; y++) for (let x = 10; x < 78; x++) if ((x * 7 + y * 3) % 11 === 0) put(im, x, y, [138, 142, 146]);
    // T-slots run along the gutters between the rows of seats (slot sprites cover y-1 .. y+16)
    for (const y of [47, 67]) { rect(im, 10, y, 68, 2, [40, 42, 44]); rect(im, 10, y + 2, 68, 1, [160, 164, 168]); }
    for (const [x, y] of [[11, 26], [75, 26], [11, 88], [75, 88]]) { rect(im, x, y, 2, 2, [196, 200, 204]); put(im, x + 1, y + 1, [80, 82, 86]); }
    // a one-pixel shadow under and right of each of the nine seats
    for (let j = 0; j < 3; j++) for (let i = 0; i < 3; i++) {
      const x = 16 + 20 * i, y = 30 + 20 * j;
      rect(im, x - 1, y + 17, 19, 1, [70, 74, 78]); rect(im, x + 17, y - 1, 1, 19, [70, 74, 78]);
    }
    // engraved arrow to the result seat
    for (let x = 81; x < 87; x++) { put(im, x, 57, add(base, -30)); put(im, x, 58, add(base, -30)); put(im, x, 59, add(base, 26)); }
    for (let k = 0; k < 4; k++) { put(im, 87 + k, 54 + k, add(base, -30)); put(im, 87 + k, 61 - k, add(base, -30)); put(im, 87 + k, 62 - k, add(base, 26)); }
    // result seat: ringed bezel round the output slot
    for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, 108 + 15 * Math.cos(t), 58 + 15 * Math.sin(t), add(base, -40));
      put(im, 108.5 + 16 * Math.cos(t), 58.5 + 16 * Math.sin(t), [170, 134, 70]);
    }
    inset(96, 46, 120, 70, [52, 58, 56], 20);
    // lamp bezels and the label strip beside them
    inset(126, 24, 214, 88, add(base, -10), 18);
    for (const y of [30, 50, 70]) inset(129, y - 1, 145, y + 15, [36, 38, 40], 14);
    // run-in trough with tick marks every 24 px below it
    inset(9, 95, 211, 121, add(base, -8), 18);
    inset(13, 107, 207, 117, [34, 36, 38], 12);
    for (let x = 14; x <= 206; x += 24) rect(im, x, 117, 1, 3, [200, 190, 150]);
    // bottom strip for the read-out and the button
    inset(7, 124, 213, 146, add(base, -6), 16);
    return im;
  }

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    writePng(gui(), path.join(root, PCART.ASSETS, 'textures/gui/panel', 'assembly_stand.png'));
    return Object.keys(T).length + 1;
  }

  const DIR = 'machine_shop/assembly';
  function exportAll(root, ids) {
    const out = {};
    for (const id of ids) {
      if (id === 'assembly_stand') {
        PCART.realize(root, stand(), id);
        PCART.saveWhole(root, DIR, id);
        out[id] = 'whole';
      } else {
        const p = id.split('/')[1];
        PCART.realize(root, partial(p), id);
        out[id] = PCART.save(root, DIR + '/assembly_stand', p, 'assembly_stand/partial/' + p);
      }
    }
    return out;
  }

  window.ASM = {
    paint, exportAll, handless: true, ids: ['assembly_stand', 'assembly_stand/rotor', 'assembly_stand/hook'],
    stand, rotor, hook, GUI_W, GUI_H,
  };
})();
