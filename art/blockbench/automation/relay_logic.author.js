// A1-A2 automation of 03d, hand-authored for Blockbench (replaces the spec_automation*.py placeholders): the
// plug-in relay (RP-21 type) and the latching relay, the time relay with its setting pointer, the stepping switch
// (ShI-25) with its wipers, the cam controller (KEP-12) with its drum, the process switch in four kinds, the 24 V
// relay power supply, the relay rack with its door, the analog computer modules (summer, integrator,
// differentiator, multiplier, function generator, comparator; tube and transistor tiers), the manual loading
// station, the signal converter, the RTU cabinet and the SIS logic solver cabinet.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/automation/relay_logic.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); RLY.paint('<repo>')
//   await RLY.exportAll('<repo>')
//
// Front to the north. Contracts with the code (pc-mod automation):
//   Wire terminals: TerminalLayout puts each kind's terminals in rows on a plane z in front of the model, from
//     (x0, y0) to (x1, y1) in `cols` columns, mirrored in x (model x = 16 - x). TERM below repeats those numbers;
//     every model draws a screw terminal at each point just behind that plane, so the wires land on screws.
//   time_relay/partial/dial turns about z through (8, 8, 8); stepping_switch/partial/wiper about y through
//     (8, 8, 8); cam_controller/partial/drum about x through (8, 8, 8).
//   relay_rack/partial/door swings about y on the hinge at x 16, z 0 (110 degrees open): the static model shows
//     the open cabinet, the door is the partial.
//   Wall devices keep their hit boxes: relay and latching relay 0.5,1,5.9..15.5,15,16; time relay
//     0.5,1,8..15.5,15,16; process switch 4,0,9.4..12,15.5,16; power supply 0.5,1,8.4..15.5,15,16; manual station
//     0,0,4.5..16,16,16; signal converter 0.5,1,9..15.5,15,16.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'automation/';
  const T = {};

  // terminal rows, as in TerminalLayout: [z, x0, y0, x1, y1, cols, count]
  const TERM = {
    relay: [5.8, 2, 3, 14, 13, 4, 8], relay_latching: [5.8, 1.5, 3, 14.5, 13, 5, 10],
    time_relay: [7.9, 2, 2, 14, 6, 4, 8], stepping_switch: [0.9, 2, 2, 14, 13, 5, 15],
    cam_controller: [0.9, 1.5, 1.5, 14.5, 14, 6, 24], process_switch: [9.3, 5, 1.5, 11, 1.5, 3, 3],
    power_supply: [8.3, 4, 3, 12, 3, 2, 2], analog_summer: [-0.6, 2, 2, 14, 2, 5, 5],
    analog_integrator: [-1.6, 2, 2, 14, 4.5, 4, 8], analog_differentiator: [-0.6, 4, 2, 12, 2, 2, 2],
    analog_multiplier: [-1.6, 3, 2, 13, 2, 3, 3], analog_function: [-0.6, 4, 2, 12, 2, 2, 2],
    analog_comparator: [-0.6, 2, 2, 14, 2, 5, 5], manual_station: [4.4, 1.5, 1.5, 14.5, 4, 5, 9],
    signal_converter: [8.9, 4, 3, 12, 3, 2, 2], sis_logic: [0.4, 2, 3, 14, 13, 6, 11],
  };
  function termPoints(k) {
    const [z, x0, y0, x1, y1, cols, n] = TERM[k], rows = Math.ceil(n / cols), out = [];
    for (let i = 0; i < n; i++) {
      const r = Math.floor(i / cols), c = i % cols;
      const x = cols === 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * c / (cols - 1);
      const y = rows === 1 ? y1 : y1 - (y1 - y0) * r / (rows - 1);
      out.push([16 - x, y, z]);
    }
    return out;
  }
  // Screw terminals at the kind's points: a brass screw head just behind the wire plane on a grey clamp body
  // reaching back to `back`.
  function terminals(m, k, back) {
    termPoints(k).forEach(([x, y, z], i) => {
      m.box(`term_${i}`, [x - 0.7, y - 0.7, z + 0.15], [x + 0.7, y + 0.7, back], 'tblk', { split: '' });
      m.box(`screw_${i}`, [x - 0.45, y - 0.45, z + 0.02], [x + 0.45, y + 0.45, z + 0.15], 'br', { split: '' });
    });
  }

  // ---------------------------------------------------------------- textures (32 px per block)
  T.bakelite = () => {
    const im = img(32, 32), r = rng(5101), n = vnoise(r, 32, 32, 6);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([58, 36, 26], [84, 54, 36], n(x, y)), (r() - 0.5) * 6));
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [120, 84, 56]);
    return im;
  };
  // Black wrinkle finish of instrument panels.
  T.crinkle = () => {
    const im = img(32, 32), r = rng(5111), n = vnoise(r, 32, 32, 2);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([34, 35, 37], (n(x, y) - 0.5) * 18 + (r() - 0.5) * 4));
    return im;
  };
  // Grey terminal clamp body.
  T.tblock = () => { const im = img(16, 16); grain(im, [128, 130, 126], 5121, 4, 3, 4); for (let x = 0; x < 16; x++) put(im, x, 0, [170, 172, 168]); return im; };
  // Relay cap front, seen through the clear cap: the coil (black former, copper winding), the yoke, the armature
  // and the contact springs. Picked: the armature pulled in, the contacts made.
  const relayFace = (picked, latching, set) => () => {
    const im = img(32, 32), r = rng(5131 + (picked ? 1 : 0) + (latching ? 4 : 0) + (set ? 8 : 0));
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([150, 160, 166], (r() - 0.5) * 6 + (x < 4 ? 26 : 0)));
    const coil = (cx) => {
      rect(im, cx - 5, 14, 10, 14, [30, 28, 26]);
      for (let y = 16; y < 26; y++) rect(im, cx - 4, y, 8, 1, y % 2 ? [168, 98, 52] : [196, 122, 66]);
      rect(im, cx - 6, 12, 12, 2, [80, 82, 84]);
    };
    if (latching) { coil(9); coil(23); } else coil(16);
    const arm = picked || set;
    rect(im, 3, arm ? 10 : 8, 26, 2, [110, 112, 116]);                   // armature
    for (let i = 0; i < 4; i++) {                                         // contact springs
      const x = 5 + i * 6;
      rect(im, x, 2, 1, arm ? 7 : 5, [210, 212, 214]);
      rect(im, x + 2, 2, 1, 5, [210, 212, 214]);
      put(im, x, arm ? 8 : 6, [220, 160, 60]); put(im, x + 2, 6, [220, 160, 60]);
    }
    if (latching) { rect(im, 14, 28, 4, 3, set ? [200, 40, 30] : [236, 236, 230]); }
    for (let x = 0; x < 32; x++) { put(im, x, 0, [196, 204, 208]); put(im, x, 31, [110, 116, 120]); }
    return im;
  };
  T.relay_face = relayFace(false, false);
  T.relay_face_picked = relayFace(true, false);
  T.latch_face = relayFace(false, true, false);
  T.latch_face_set = relayFace(false, true, true);
  // Clear cap side: blue-grey, highlights.
  T.cap = () => { const im = img(16, 16), r = rng(5141); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([150, 160, 166], (r() - 0.5) * 6 + (x === 2 ? 40 : 0))); return im; };
  // Time relay scale: white disc, black ticks round 270 degrees from lower left, red set mark.
  T.time_scale = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x - 15.5, y - 15.5);
      put(im, x, y, d < 15.5 ? [238, 236, 226] : [40, 38, 36]);
    }
    for (let k = 0; k <= 30; k++) {
      const a = (-225 + k * 9) * Math.PI / 180, L = k % 5 === 0 ? 4 : 2;
      for (let s = 0; s < L; s++) put(im, 15.5 + Math.cos(a) * (14 - s), 15.5 - Math.sin(a) * (14 - s), [20, 20, 20]);
    }
    rect(im, 13, 22, 6, 2, [30, 30, 30]);
    return im;
  };
  // Stepping switch contact bank: bakelite layers with copper lamellae.
  T.lamellae = () => {
    const im = img(16, 16), r = rng(5151);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 === 1 ? add([200, 120, 64], (r() - 0.5) * 20) : add([60, 40, 30], (r() - 0.5) * 8));
    return im;
  };
  // Wire duct (slotted grey PVC) and a harness of coloured wires.
  T.duct = () => {
    const im = img(32, 32), r = grain(im, [176, 178, 174], 5161, 3, 2, 8);
    for (let x = 1; x < 32; x += 4) for (let y = 0; y < 10; y++) { put(im, x, y, [70, 72, 70]); put(im, x + 1, y, [70, 72, 70]); }
    return im;
  };
  T.harness = () => {
    const im = img(16, 16), r = rng(5171), cols = [[30, 30, 34], [190, 40, 30], [40, 80, 180], [220, 200, 60], [230, 230, 230], [60, 140, 70]];
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(cols[(x + Math.floor(y / 5)) % 6], (r() - 0.5) * 10 + (x % 2 ? -12 : 6)));
    return im;
  };
  // Mounting plate (galvanised, perforated) behind the gear in cabinets.
  T.plate = () => {
    const im = img(32, 32), r = grain(im, [168, 170, 166], 5181, 4, 3, 8);
    for (let y = 3; y < 32; y += 6) for (let x = 3; x < 32; x += 6) { put(im, x, y, [80, 82, 80]); put(im, x + 1, y, [80, 82, 80]); }
    return im;
  };
  // SIS cabinet stripe (red and yellow diagonals) and its module window (I/O cards with green LEDs).
  T.sis_stripe = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, Math.floor((x + y) / 8) % 2 ? [220, 182, 30] : [190, 36, 30]);
    return im;
  };
  T.sis_modules = () => {
    const im = img(32, 32), r = rng(5191);
    rect(im, 0, 0, 32, 32, [40, 44, 48]);
    for (let i = 0; i < 8; i++) {
      const x = 1 + i * 4;
      rect(im, x, 2, 3, 28, [70, 76, 82]); rect(im, x, 2, 3, 3, [196, 198, 192]);
      for (let k = 0; k < 4; k++) put(im, x + 1, 8 + k * 3, r() < 0.8 ? [80, 230, 90] : [240, 170, 40]);
    }
    return im;
  };
  // Small white engraved label.
  T.label = () => { const im = img(16, 16); grain(im, [230, 228, 220], 5201, 2, 2, 4); for (let x = 2; x < 14; x++) if (x % 3) put(im, x, 8, [40, 40, 40]); return im; };
  // Process switch setpoint window: white scale strip with a red pointer line.
  T.ps_scale = () => {
    const im = img(16, 16); rect(im, 0, 0, 16, 16, [236, 234, 224]);
    for (let x = 1; x < 16; x += 2) rect(im, x, 2, 1, x % 4 === 1 ? 4 : 2, [20, 20, 20]);
    rect(im, 7, 1, 1, 12, [200, 30, 24]); rect(im, 2, 11, 12, 2, [60, 60, 60]);
    return im;
  };
  // Valve-tube glow seen through the ventilation grille of the tube-tier modules.
  T.tubes = () => {
    const im = img(32, 32), r = rng(5211);
    rect(im, 0, 0, 32, 32, [22, 22, 24]);
    for (const x of [3, 11, 19, 27]) for (let y = 4; y < 28; y++) for (let k = -2; k <= 2; k++) put(im, x + k, y, add([255, 150, 60], -Math.abs(k) * 50 - (y > 22 ? 60 : 0) + (r() - 0.5) * 20));
    for (let x = 0; x < 32; x += 3) rect(im, x, 0, 1, 32, [60, 62, 64]);
    return im;
  };

  const MAT = Object.assign({}, PLANT.MAT, {
    bak: { tex: TEX + 'bakelite', P: 16 }, blk: { tex: TEX + 'crinkle', P: 16 }, tblk: { tex: TEX + 'tblock', P: 16 },
    cap: { tex: TEX + 'cap', P: 16 }, lam: { tex: TEX + 'lamellae', P: 8 }, duct: { tex: TEX + 'duct', P: 16 },
    harn: { tex: TEX + 'harness', P: 16 }, mplate: { tex: TEX + 'plate', P: 16 }, sis: { tex: TEX + 'sis_stripe', P: 16 },
    tubes: { tex: TEX + 'tubes', P: 16 },
    en: { tex: 'switchgear_cell/enamel', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 },
    tx: { tex: 'distribution_transformer/enamel', P: 16 },
    louv: { tex: 'grid_lines/louvre', P: 16 }, knob: { tex: 'common/auto_knob', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 },
    l_w: { tex: 'grid_lines/lamp_white', P: 16 }, l_off: { tex: 'grid_lines/lamp_off', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    m.box(`${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber', { split: '' });
    m.box((glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { split: '', hide: ['south'] });
  }
  function knob(m, n, x, y, z, r) {
    const k = r || 0.8;
    m.box(n, [x - k, y - k, z - 1], [x + k, y + k, z], 'knob', { split: '' });
    m.box(`${n}_b`, [x - k, y - k, z - 0.99], [x + k, y + k, z - 0.01], 'knob', { split: '', rot: { axis: 'z', angle: 45, origin: [x, y, z] } });
    m.box(`${n}_mark`, [x - 0.12, y, z - 1.05], [x + 0.12, y + k, z - 1], 'l_w', { split: '' });
  }
  function dinRail(m, y, z0) { m.box('din_rail', [0.4, y - 1, z0], [15.6, y + 1, 16], 'gal', { split: '' }); }

  // ---------------------------------------------------------------- relays
  // Plug-in relay on its socket on a DIN rail: bakelite socket with the screw terminals top and bottom, the clear
  // cap over the coil and contacts, the lamp in the cap lit while the coil is energised.
  function relay(picked) {
    const m = mk(TEX + 'bakelite');
    dinRail(m, 8, 15.2);
    m.box('socket', [1, 1, 6.4], [15, 15, 15.2], 'bak', { split: '' });
    terminals(m, 'relay', 6.4);
    m.box('cap', [3.4, 4.8, 2.6], [12.6, 11.2, 6.4], 'cap', { split: '', face: { north: { tex: TEX + (picked ? 'relay_face_picked' : 'relay_face'), uv: [0, 0, 16, 16] } } });
    m.box('cap_top', [3.6, 11.2, 2.8], [12.4, 11.5, 6.2], 'cap', { split: '' });
    m.box((picked ? 'glow_' : '') + 'coil_lamp', [10.6, 9.6, 2.4], [11.8, 10.8, 2.6], picked ? 'l_a' : 'l_off', { split: '', hide: ['south'] });
    m.plate('label', [4, 11.5, 3.2], [9, 11.6, 5.8], 'cap', 'up', TEX + 'label', { hide: ['down'] });
    return m;
  }
  // Latching relay: wider cap with two coils, a flag red when set.
  function latching(set) {
    const m = mk(TEX + 'bakelite');
    dinRail(m, 8, 15.2);
    m.box('socket', [0.6, 1, 6.4], [15.4, 15, 15.2], 'bak', { split: '' });
    terminals(m, 'relay_latching', 6.4);
    m.box('cap', [2.2, 4.8, 2.6], [13.8, 11.2, 6.4], 'cap', { split: '', face: { north: { tex: TEX + (set ? 'latch_face_set' : 'latch_face'), uv: [0, 0, 16, 16] } } });
    m.box('flag', [7.2, 11.2, 3.4], [8.8, 12.2, 3.8], set ? 'red' : 'light', { split: '' });
    return m;
  }
  // Time relay: bakelite body on a DIN rail, the white scale round the pointer (the pointer is the partial),
  // the setting knob, run (green) and done (red) lamps, screw terminals below the scale.
  function timeRelay(state) {
    const m = mk(TEX + 'bakelite');
    dinRail(m, 8, 15);
    m.box('body', [1.4, 0.6, 9], [14.6, 15.2, 15], 'bak', { split: '' });
    m.box('bezel', [4.6, 4.6, 8.7], [11.4, 11.4, 9], 'rubber', { split: '' });
    m.plate('scale', [5, 5, 8.6], [11, 11, 8.7], 'rubber', 'north', TEX + 'time_scale', { hide: ['south'] });
    knob(m, 'set_knob', 8, 13.2, 9, 0.9);
    lamp(m, 'lamp_run', 3.4, 13.2, 9, state === 'running' ? 'l_g' : 'l_off', state === 'running', 0.55);
    lamp(m, 'lamp_done', 12.6, 13.2, 9, state === 'done' ? 'l_r' : 'l_off', state === 'done', 0.55);
    terminals(m, 'time_relay', 9);
    return m;
  }
  function timeDial() {
    const m = mk('material/paint_red');
    m.box('needle', [7.82, 8, 8.4], [8.18, 10.7, 8.55], 'red', { split: '' });
    m.box('tail', [7.85, 7.1, 8.4], [8.15, 8, 8.55], 'red', { split: '' });
    m.box('hub', [7.5, 7.5, 8.3], [8.5, 8.5, 8.6], 'rubber', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- stepping switch and cam controller
  // Stepping switch: bakelite terminal board across the front, brass frame open at the sides and top, three
  // half-ring contact banks of lamellae round the wiper shaft at the back, the stepping magnet (black coil),
  // armature and pawl at the front left.
  function stepper() {
    const m = mk(TEX + 'bakelite');
    m.box('base', [1, 0, 1], [15, 1.2, 15], 'br', { split: '' });
    m.box('board', [1, 1.2, 1], [15, 15, 1.9], 'bak', { split: '' });
    terminals(m, 'stepping_switch', 1);
    for (const x of [1, 14]) for (const z of [1.9, 14]) m.box(`post_${x}_${z}`, [x, 1.2, z], [x + 1, 14, z + 1], 'br', { split: '' });
    for (const x of [1, 14]) m.box(`top_rail_${x}`, [x, 13, 1.9], [x + 1, 14, 15], 'br', { split: '' });
    m.box('bridge', [2, 13, 7.2], [14, 14, 8.8], 'br', { split: '' });
    [3.6, 7.1, 10.6].forEach((y, b) => {
      for (const a of [180, 202.5, 225, 247.5, 270, 292.5, 315, 337.5, 360]) m.postY(`bank_${b}_${a}`, [8, 8], 5.6, a, 2.2, y - 0.7, y + 0.7, 'lam', { split: '' });
    });
    m.prismY('magnet', [4.4, 4.4], 1.6, 1.2, 6.4, 'rubber', { ends: ['up'] });
    m.box('yoke', [2.4, 6.4, 3], [6.4, 7, 5.8], 'st', { split: '' });
    m.box('armature', [5.2, 6.6, 3.6], [9.2, 7.1, 5.2], 'st', { split: '' });
    m.box('pawl', [8.6, 2.4, 4.6], [9.2, 6.8, 5.2], 'st', { split: '' });
    return m;
  }
  function wiper() {
    const m = mk('disconnector/copper');
    m.prismY('shaft', [8, 8], 0.55, 1.2, 13, 'bright');
    m.prismY('ratchet', [8, 8], 2.2, 1.6, 2.4, 'br', { ends: ['up', 'down'] });
    for (const y of [3.6, 7.1, 10.6]) {
      m.box(`arm_${y}`, [8, y - 0.2, 7.7], [13.4, y + 0.2, 8.3], 'cu', { split: '' });
      m.box(`brush_${y}`, [13.2, y - 0.5, 7.6], [13.9, y + 0.5, 8.4], 'cu', { split: '' });
      m.box(`hub_${y}`, [7, y - 0.4, 7], [9, y + 0.4, 9], 'bak', { split: '' });
    }
    return m;
  }
  // Cam controller: terminal board across the front, cast-iron base and cheeks with bearings, the contact bar
  // over the drum with twelve copper fingers, the harness from the fingers to the board. The drum is open to
  // view from the top.
  function camController() {
    const m = mk('material/cast_iron');
    m.box('board', [0.4, 0.4, 1], [15.6, 15.6, 1.9], 'bak', { split: '' });
    terminals(m, 'cam_controller', 1);
    m.box('base', [0, 0, 1.9], [16, 1.8, 15.4], 'ci', { split: '' });
    for (const x of [0, 14.8]) {
      m.box(`cheek_${x}`, [x, 1.8, 2.4], [x + 1.2, 13.4, 14.4], 'ci', { split: '' });
      m.prism(`boss_${x}`, 'x', [8, 8], 2, x < 1 ? -0.4 : 16, x < 1 ? 0 : 16.4, 'ci', { split: '', ends: [x < 1 ? 'west' : 'east'] });
    }
    m.box('contact_bar', [1.2, 13, 10.6], [14.8, 14.4, 13.4], 'bak', { split: '' });
    for (let i = 0; i < 12; i++) {
      const x = 2.15 + i;
      m.box(`finger_${i}`, [x - 0.2, 10.5, 10.6], [x + 0.2, 13, 11], 'cu', { split: '' });
    }
    m.box('harness', [1.4, 13.4, 1.9], [3, 14.2, 10.6], 'harn', { split: '' });
    m.box('back_plate', [1.2, 1.8, 14.4], [14.8, 5.6, 15.4], 'ci', { split: '' });
    return m;
  }
  // Drum partial: shaft and twelve brass cam discs with their lobes set at different angles (the program).
  function camDrum() {
    const m = mk('material/brass');
    m.prism('shaft', 'x', [8, 8], 0.7, -0.4, 16.4, 'bright', { split: '', ends: ['east', 'west'] });
    // lobe direction (+y, -y, +z, -z) and turn about x (the program, as seen on the drum)
    const lobes = [['y', 0], ['z', 22.5], ['-y', -22.5], ['-z', 0], ['y', 45], ['-z', -22.5],
      ['-y', 22.5], ['z', 0], ['y', -22.5], ['-y', 45], ['z', -45], ['-z', 22.5]];
    for (let i = 0; i < 12; i++) {
      const x = 1.75 + i, [d, ang] = lobes[i];
      m.box(`cam_${i}_a`, [x, 5.6, 5.6], [x + 0.8, 10.4, 10.4], 'br', { split: '' });
      m.box(`cam_${i}_b`, [x + 0.01, 5.6, 5.6], [x + 0.79, 10.4, 10.4], 'br', { split: '', rot: { axis: 'x', angle: 45, origin: [8, 8, 8] } });
      const f = [x + 0.05, 7.3, 7.3], t = [x + 0.75, 8.7, 8.7];
      if (d === 'y') { f[1] = 10; t[1] = 11.4; } else if (d === '-y') { f[1] = 4.6; t[1] = 6; }
      else if (d === 'z') { f[2] = 10; t[2] = 11.4; } else { f[2] = 4.6; t[2] = 6; }
      m.box(`lobe_${i}`, f, t, 'br', { split: '', rot: ang ? { axis: 'x', angle: ang, origin: [8, 8, 8] } : undefined });
    }
    return m;
  }

  // ---------------------------------------------------------------- process switch and power supply
  // Process switch (DEM-102 type): grey case on a wall plate with the setpoint window and the trip lamp, the
  // terminal box below with its three screws; the sensing element by kind: pressure (brass union and impulse
  // line), temperature (copper capillary to a bulb), level (float chamber on the side), flow (paddle in a tee).
  function processSwitch(kind, tripped) {
    const m = mk('switchgear_cell/enamel');
    m.box('plate', [4.4, 1, 15], [11.6, 15, 16], 'gal', { split: '' });
    m.box('case', [4.6, 4.2, 9.8], [11.4, 14.6, 15], 'en', { split: '' });
    m.box('cover', [4.4, 14.6, 9.6], [11.6, 15.4, 15], 'en', { split: '' });
    m.plate('window', [5.6, 9.6, 9.65], [10.4, 13.6, 9.8], 'en', 'north', TEX + 'ps_scale', { hide: ['south'] });
    lamp(m, 'lamp_trip', 8, 7.2, 9.8, tripped ? 'l_r' : 'l_off', tripped, 0.6);
    m.box('adjust', [7.4, 15.4, 11.8], [8.6, 16, 13], 'br', { split: '' });
    m.box('tbox', [4.6, 0.2, 9.6], [11.4, 4.2, 14.6], 'en', { split: '' });
    terminals(m, 'process_switch', 9.6);
    m.prismY('gland', [8, 12.4], 0.8, -0.4, 0.2, 'rubber', { ends: ['down'] });
    if (kind === 'pressure') {
      m.box('union', [11.4, 10.6, 11.6], [12.8, 12, 13], 'br', { split: '' });
      m.box('impulse', [12.8, 0, 11.9], [13.4, 11.4, 12.5], 'cu', { split: '' });
    } else if (kind === 'temperature') {
      for (let k = 0; k < 4; k++) m.box(`capillary_${k}`, [11.4, 5 + k * 1.6, 12], [13.6, 5.4 + k * 1.6, 12.4], 'cu', { split: '' });
      m.box('capillary_down', [13.2, -2, 12], [13.6, 10.2, 12.4], 'cu', { split: '' });
      m.box('bulb', [12.8, -4, 11.6], [14, -2, 12.8], 'cu', { split: '' });
    } else if (kind === 'level') {
      m.box('chamber', [12, -3, 10.6], [15, 13, 14.4], 'st', { split: 'y' });
      m.box('chamber_top', [11.8, 13, 10.4], [15.2, 13.8, 14.6], 'ci', { split: '' });
      for (const y of [-1, 11]) m.box(`chamber_nozzle_${y}`, [13, y, 14.4], [14, y + 1, 16], 'st', { split: '' });
      m.box('lever', [11.4, 8.6, 12], [12, 9.2, 13], 'st', { split: '' });
    } else {
      m.box('stem', [7.4, -1, 11.6], [8.6, 0.2, 12.8], 'br', { split: '' });
      m.prism('tee', 'x', [-2.6, 12.2], 1.6, 3, 13, 'p_water', { split: '', ends: ['east', 'west'] });
      for (const x of [3, 12.4]) m.box(`flange_${x}`, [x, -4.4, 10.4], [x + 0.6, -0.8, 14], 'st', { split: '' });
    }
    return m;
  }
  // 24 V relay power supply: grey box on a DIN rail with ventilation slots, the "SET'" lamp (green when fed)
  // and the overload lamp (red), the two output terminals below.
  function powerSupply(state) {
    const m = mk('switchgear_cell/enamel');
    dinRail(m, 8, 15.4);
    m.box('body', [1, 1.2, 8.6], [15, 14.8, 15.4], 'en', { split: '', face: { north: 'louv' } });
    m.box('front', [2, 6, 8.4], [14, 14, 8.6], 'en', { split: '' });
    m.plate('label', [3, 11.6, 8.3], [9, 13.4, 8.4], 'en', 'north', TEX + 'label', { hide: ['south'] });
    lamp(m, 'lamp_mains', 11, 12.4, 8.4, state !== 'off' ? 'l_g' : 'l_off', state !== 'off', 0.55);
    lamp(m, 'lamp_overload', 11, 9.2, 8.4, state === 'overload' ? 'l_r' : 'l_off', state === 'overload', 0.55);
    m.box('pot', [4, 7, 8], [5.4, 8.4, 8.4], 'br', { split: '' });
    m.box('tstrip', [2, 1.6, 8.4], [14, 4.4, 8.6], 'tblk', { split: '' });
    terminals(m, 'power_supply', 8.4);
    return m;
  }

  // ---------------------------------------------------------------- relay rack
  // Open relay cabinet (ShchAP type) 1x2: the frame, the perforated mounting plate with three rows of plug-in
  // relays on DIN rails, wire ducts between the rows, terminal rows and the harness to the bottom gland plate.
  function rack() {
    const m = mk('switchgear_cell/enamel');
    m.box('plinth', [0, 0, 1], [16, 1, 16], 'rubber', { split: '' });
    m.box('side_w', [0, 1, 1], [1, 31.4, 16], 'en', { split: 'y' });
    m.box('side_e', [15, 1, 1], [16, 31.4, 16], 'en', { split: 'y' });
    m.box('roof', [0, 31.4, 1], [16, 32, 16], 'en', { split: '' });
    m.box('floor', [1, 1, 1], [15, 1.6, 16], 'en', { split: '' });
    m.box('back', [1, 1.6, 15], [15, 31.4, 16], 'en', { split: 'y' });
    m.box('frame_lip', [1, 30.6, 1], [15, 31.4, 2], 'en', { split: '' });
    m.box('mplate', [1.6, 2.4, 13.6], [14.4, 30.4, 14.2], 'mplate', { split: 'y' });
    for (const [r, y] of [[0, 25], [1, 18.4], [2, 11.8]]) {
      m.box(`rail_${r}`, [2, y - 0.8, 13], [14, y + 0.8, 13.6], 'gal', { split: '' });
      for (let i = 0; i < 4; i++) {
        const x = 2.6 + i * 2.8;
        m.box(`relay_${r}_${i}_base`, [x, y - 2.2, 11], [x + 2.4, y + 2.2, 13], 'bak', { split: '' });
        m.box(`relay_${r}_${i}_cap`, [x + 0.2, y - 1.4, 9.4], [x + 2.2, y + 1.4, 11], 'cap', { split: '' });
      }
      m.box(`duct_${r}`, [2, y - 4.8, 11.2], [14, y - 3, 13.6], 'duct', { split: '' });
    }
    m.box('duct_v', [12.6, 4.6, 11.2], [14, 28, 13.6], 'duct', { split: 'y' });
    m.box('tstrip', [2, 4.4, 11.6], [12, 6.6, 13.6], 'tblk', { split: '' });
    for (let i = 0; i < 9; i++) m.box(`tscrew_${i}`, [2.4 + i * 1.08, 5.1, 11.5], [3.0 + i * 1.08, 5.7, 11.6], 'br', { split: '' });
    m.box('harness', [6, 1.6, 12], [9, 4.4, 13.4], 'harn', { split: '' });
    m.box('gland_plate', [1, 1.6, 2], [15, 2.2, 15], 'ci', { split: '' });
    for (const y of [3, 29.6]) m.box(`hinge_${y}`, [15.4, y, 0.4], [16, y + 1.6, 1.2], 'st', { split: '' });
    return m;
  }
  // Door partial: louvres top and bottom, a wiring diagram pocket, the handle and lock at the free (west) edge,
  // the label plate, the warning plate.
  function rackDoor() {
    const m = mk('switchgear_cell/enamel');
    m.box('door', [0.2, 1.5, 0], [15.8, 31, 1], 'en', { split: 'y' });
    m.box('louvre_hi', [3, 26, -0.05], [13, 29.6, 0], 'louv', { split: '', hide: ['south'] });
    m.box('louvre_lo', [3, 2.6, -0.05], [13, 6.2, 0], 'louv', { split: '', hide: ['south'] });
    m.box('pocket', [4, 9, -0.4], [12, 17, 0], 'en', { split: '' });
    m.plate('label', [5, 22, -0.1], [11, 24, 0], 'en', 'north', TEX + 'label', { hide: ['south'] });
    m.plate('warn', [6.8, 18.4, -0.1], [9.2, 20.8, 0], 'en', 'north', 'grid_lines/warn', { hide: ['south'] });
    m.box('handle', [1.4, 13, -0.8], [2, 18, 0], 'rubber', { split: '' });
    m.box('lock', [1.2, 19, -0.3], [2.2, 20, 0], 'br', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- analog computer modules
  // MN-7 type plug-in module: RAL 7035 chassis, black wrinkle front panel with pull handles, the label plate,
  // the controls by function, the input/output terminal row along the bottom in front of the panel, a top
  // grille: valve glow behind it on the tube tier, plain louvres on the transistor tier.
  function module(kind, o) {
    const m = mk(TEX + 'crinkle');
    const z = TERM[kind][0] + 0.6; // the panel face sits 0.6 behind the terminal plane
    m.box('chassis', [0, 0, 1], [16, 16, 16], 'en', { split: '', face: { up: o.tube ? 'tubes' : 'louv' } });
    m.box('panel', [0.4, 0.4, z], [15.6, 15.6, 1], 'blk', { split: '' });
    for (const x of [0.8, 14.2]) {
      m.box(`handle_${x}`, [x, 6, z - 1.4], [x + 1, 10, z - 1], 'bright', { split: '' });
      m.box(`handle_${x}_t`, [x, 9.6, z - 1], [x + 1, 10, z], 'bright', { split: '' });
      m.box(`handle_${x}_b`, [x, 6, z - 1], [x + 1, 6.4, z], 'bright', { split: '' });
    }
    m.plate('label', [5, 14.4, z - 0.1], [11, 15.4, z], 'blk', 'north', TEX + 'label', { hide: ['south'] });
    const top = Math.max(...termPoints(kind).map((p) => p[1])) + 1.2;
    m.box('tstrip', [1.2, 0.8, z - 0.1], [14.8, top, z], 'tblk', { split: '' });
    terminals(m, kind, z);
    o.build(m, z);
    return m;
  }
  const gauge = (m, z) => {
    m.box('gauge_case', [3.6, 7.8, z - 0.5], [12.4, 13.8, z], 'rubber', { split: '' });
    m.plate('gauge', [4, 8.2, z - 0.6], [12, 13.4, z - 0.5], 'rubber', 'north', 'analog_gauge_dial', { hide: ['south'] });
  };
  const sw = (m, n, x, y, z, up) => {
    m.box(`${n}_base`, [x - 1, y - 0.8, z - 0.4], [x + 1, y + 0.8, z], 'bright', { split: '' });
    m.box(`${n}_lever`, [x - 0.2, y - 0.2, z - 1.6], [x + 0.2, y + 0.2, z - 0.4], 'bright', { split: '', rot: { axis: 'x', angle: up ? 22.5 : -22.5, origin: [x, y, z - 0.4] } });
  };
  const summer = (tube) => module('analog_summer', { tube, build(m, z) { gauge(m, z); for (const x of [3, 6.4, 9.6, 13]) knob(m, `k_${x}`, x, 5.6, z, 0.85); } });
  const integrator = (tube) => module('analog_integrator', { tube, build(m, z) {
    gauge(m, z); knob(m, 'k_t', 3.4, 6, z, 0.85); knob(m, 'k_ic', 7, 6, z, 0.85); sw(m, 'mode', 12, 6, z, true);
    lamp(m, 'lamp_hold', 12, 7.4, z, 'l_off', false, 0.4);
  } });
  const differentiator = (tube) => module('analog_differentiator', { tube, build(m, z) { gauge(m, z); knob(m, 'k_td', 5, 5.8, z, 0.9); knob(m, 'k_n', 11, 5.8, z, 0.9); } });
  const multiplier = (tube) => module('analog_multiplier', { tube, build(m, z) {
    gauge(m, z); knob(m, 'k_scale', 4, 5.8, z, 0.85); sw(m, 'mode', 9, 5.8, z, false); sw(m, 'range', 12.4, 5.8, z, true);
  } });
  const fnGen = (tube) => module('analog_function', { tube, build(m, z) {
    for (let i = 0; i < 11; i++) {
      const x = 2.2 + i * 1.16;
      m.box(`bp_${i}`, [x - 0.4, 10.8, z - 0.6], [x + 0.4, 11.6, z], 'knob', { split: '' });
      m.box(`sl_${i}`, [x - 0.4, 7.4, z - 0.6], [x + 0.4, 8.2, z], 'knob', { split: '' });
    }
    m.plate('fn_plate', [2, 12.2, z - 0.1], [14, 13.8, z], 'blk', 'north', TEX + 'label', { hide: ['south'] });
    knob(m, 'k_gain', 8, 5.2, z, 0.8);
  } });
  const comparator = (tripped) => module('analog_comparator', { tube: false, build(m, z) {
    knob(m, 'k_sp', 5, 9.6, z, 1.1); knob(m, 'k_hyst', 11, 9.6, z, 0.8);
    lamp(m, 'lamp_trip', 11, 12.6, z, tripped ? 'l_r' : 'l_off', tripped, 0.6);
    m.plate('sp_scale', [2.8, 11.4, z - 0.1], [7.2, 13.4, z], 'blk', 'north', TEX + 'ps_scale', { hide: ['south'] });
  } });

  // ---------------------------------------------------------------- stations and cabinets
  // Manual loading station: wall panel, case with two edgewise indicators (PV and output), the setter knob, the
  // A (auto, lit while in automatic) and R (manual, lit amber in manual) buttons, the terminal rail below.
  function manualStation(manual) {
    const m = mk('switchgear_cell/enamel');
    m.box('panel', [0, 0, 13], [16, 16, 16], 'en', { split: '' });
    m.box('case', [2, 5.6, 6.2], [14, 15.2, 13], 'blk', { split: '' });
    m.box('bezel', [1.8, 5.4, 6], [14.2, 15.4, 6.2], 'bright', { split: '' });
    m.plate('scale_pv', [2.6, 7, 5.9], [7.4, 14.6, 6], 'blk', 'north', 'manual_station_scale', { hide: ['south'] });
    m.plate('scale_out', [8.6, 7, 5.9], [13.4, 14.6, 6], 'blk', 'north', 'manual_station_scale', { hide: ['south'] });
    m.box('lower', [2, 0.4, 5], [14, 5.6, 13], 'blk', { split: '' });
    knob(m, 'setter', 8, 3.4, 5, 1.2);
    m.box((manual ? '' : 'glow_') + 'btn_auto', [2.6, 2.4, 4.4], [4.6, 4.4, 5], manual ? 'l_off' : 'l_g', { split: '' });
    m.box((manual ? 'glow_' : '') + 'btn_manual', [11.4, 2.4, 4.4], [13.4, 4.4, 5], manual ? 'l_a' : 'l_off', { split: '' });
    m.box('rail', [0.6, 0.6, 4.6], [15.4, 4.6, 5], 'tblk', { split: '' });
    terminals(m, 'manual_station', 4.6);
    return m;
  }
  // Signal converter: narrow module on a DIN rail, two terminals, a status LED and a range switch.
  function converter() {
    const m = mk('switchgear_cell/enamel');
    dinRail(m, 8, 15.4);
    m.box('body', [4.4, 1.2, 9.2], [11.6, 14.8, 15.4], 'en', { split: '' });
    m.box('front', [4.6, 5, 9], [11.4, 14.4, 9.2], 'en', { split: '' });
    m.plate('label', [5, 11, 8.9], [11, 13.6, 9], 'en', 'north', TEX + 'label', { hide: ['south'] });
    lamp(m, 'led', 8, 9.4, 9, 'l_g', true, 0.4);
    m.box('range', [7.2, 6.4, 8.6], [8.8, 7.6, 9], 'br', { split: '' });
    m.box('tstrip', [3, 1.6, 9], [13, 4.4, 9.2], 'tblk', { split: '' });
    terminals(m, 'signal_converter', 9);
    return m;
  }
  // RTU in an outdoor cabinet 1x2: concrete plinth, grey RAL 7032 cabinet with a rain canopy, the door with its
  // handle and the KP nameplate, the power lamp (green on grid, amber on battery, dark when off), the antenna.
  function rtu(power) {
    const m = mk('distribution_transformer/enamel');
    m.box('plinth', [0, 0, 1], [16, 2, 16], 'conc', { split: '' });
    m.box('body', [1, 2, 3], [15, 30, 15], 'tx', { split: 'y' });
    m.box('canopy', [0, 30, 1], [16, 31.4, 16], 'tx', { split: '' });
    m.box('door', [1.6, 3, 2.4], [14.4, 28.6, 3], 'tx', { split: 'y' });
    m.box('handle', [12.4, 13, 1.6], [13.2, 17, 2.4], 'rubber', { split: '' });
    m.plate('plate', [5, 25, 2.3], [11, 27, 2.4], 'tx', 'north', TEX + 'label', { hide: ['south'] });
    m.plate('warn', [6.8, 20, 2.3], [9.2, 22.4, 2.4], 'tx', 'north', 'grid_lines/warn', { hide: ['south'] });
    const lit = power !== 'off';
    lamp(m, 'lamp_power', 3, 27, 2.4, power === 'battery' ? 'l_a' : lit ? 'l_g' : 'l_off', lit, 0.7);
    m.box('louvre', [4, 5, 2.35], [12, 8, 2.4], 'louv', { split: '', hide: ['south'] });
    m.box('antenna', [13, 31.4, 13], [13.4, 32, 13.4], 'gal', { split: '' });
    m.box('antenna_base', [12.4, 31.4, 12.4], [14, 31.8, 14], 'rubber', { split: '' });
    m.box('cable_in', [6.6, 0, 9], [9.4, 2, 12], 'rubber', { split: '' });
    return m;
  }
  // SIS logic solver cabinet 1x2: RAL 7035 with the red-yellow PAZ stripe, a glazed door over the TMR modules,
  // the state display (green normal, red tripped, amber bypassed, red flashing on fault), the reset key and the
  // bypass key, the terminal rows at the bottom.
  function sis(state) {
    const m = mk('switchgear_cell/enamel');
    m.box('plinth', [0, 0, 1], [16, 1, 16], 'rubber', { split: '' });
    m.box('body', [0, 1, 1.2], [16, 31.2, 16], 'en', { split: 'y' });
    m.box('roof', [-0.2, 31.2, 0.8], [16.2, 32, 16.2], 'en', { split: '' });
    m.box('stripe', [0.4, 29.6, 0.8], [15.6, 31, 1.2], 'sis', { split: '' });
    m.box('door_frame', [1, 14.6, 0.8], [15, 28.8, 1.2], 'en', { split: '' });
    m.plate('window', [2, 15.6, 0.7], [14, 24, 0.8], 'en', 'north', TEX + 'sis_modules', { hide: ['south'] });
    const col = { normal: 'l_g', tripped: 'l_r', bypassed: 'l_a', fault: 'l_r' }[state];
    m.box('display_frame', [3, 25, 0.5], [13, 28.2, 0.8], 'rubber', { split: '' });
    m.box('glow_display', [3.6, 25.6, 0.3], [12.4, 27.6, 0.5], col, { split: '', hide: ['south'] });
    m.box('key_reset', [3, 14.4, 0.4], [4, 15.4, 0.8], 'br', { split: '' });
    m.box('key_bypass', [12, 14.4, 0.4], [13, 15.4, 0.8], state === 'bypassed' ? 'yel' : 'br', { split: '' });
    m.box('tstrip', [1, 1.6, 0.6], [15, 14, 1.2], 'tblk', { split: '' });
    terminals(m, 'sis_logic', 0.6);
    m.box('handle', [14.2, 18, 0.2], [14.8, 22, 0.8], 'rubber', { split: '' });
    return m;
  }

  const BUILDS = {
    relay: { model: () => relay(false) }, relay_picked: { model: () => relay(true) },
    relay_latching: { model: () => latching(false) }, relay_latching_set: { model: () => latching(true) },
    time_relay: { model: () => timeRelay('idle') }, time_relay_running: { model: () => timeRelay('running') },
    time_relay_done: { model: () => timeRelay('done') },
    'time_relay/dial': { model: timeDial, java: 'time_relay/partial/dial' },
    stepping_switch: { model: stepper }, 'stepping_switch/wiper': { model: wiper, java: 'stepping_switch/partial/wiper' },
    cam_controller: { model: camController }, 'cam_controller/drum': { model: camDrum, java: 'cam_controller/partial/drum' },
    process_switch: { model: () => processSwitch('pressure', false) },
    process_switch_tripped: { model: () => processSwitch('pressure', true) },
    process_switch_temperature: { model: () => processSwitch('temperature', false) },
    process_switch_level: { model: () => processSwitch('level', false) },
    process_switch_flow: { model: () => processSwitch('flow', false) },
    relay_power_supply: { model: () => powerSupply('off') },
    relay_power_supply_powered: { model: () => powerSupply('powered') },
    relay_power_supply_overload: { model: () => powerSupply('overload') },
    relay_rack: { model: rack }, 'relay_rack/door': { model: rackDoor, java: 'relay_rack/partial/door' },
    analog_summer: { model: () => summer(false) },
    analog_integrator: { model: () => integrator(true) }, analog_integrator_transistor: { model: () => integrator(false) },
    analog_differentiator: { model: () => differentiator(true) }, analog_differentiator_transistor: { model: () => differentiator(false) },
    analog_multiplier: { model: () => multiplier(true) }, analog_multiplier_transistor: { model: () => multiplier(false) },
    analog_function: { model: () => fnGen(true) }, analog_function_transistor: { model: () => fnGen(false) },
    analog_comparator: { model: () => comparator(false) }, analog_comparator_tripped: { model: () => comparator(true) },
    manual_station: { model: () => manualStation(false) }, manual_station_manual: { model: () => manualStation(true) },
    signal_converter: { model: converter },
    rtu_station: { model: () => rtu('grid') }, rtu_station_battery: { model: () => rtu('battery') }, rtu_station_off: { model: () => rtu('off') },
    sis_logic_solver: { model: () => sis('normal') }, sis_logic_solver_tripped: { model: () => sis('tripped') },
    sis_logic_solver_bypassed: { model: () => sis('bypassed') }, sis_logic_solver_fault: { model: () => sis('fault') },
  };

  window.RLY = {
    paint, ids: Object.keys(BUILDS), TERM, termPoints,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    exportAll: (root, ids) => PCART.exportAll(root, 'automation', ids ? Object.fromEntries(ids.map((i) => [i, BUILDS[i]])) : BUILDS),
  };
})();
