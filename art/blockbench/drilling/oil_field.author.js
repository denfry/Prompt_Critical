// Drilling rig and oil field equipment of 09, hand-authored for Blockbench (replaces the spec_oilgas.py
// placeholders): the beam pumping unit (gearbox base and its multipart parts: crank and counterweights, samson
// post with walking beam and horsehead, pitman arms, bridle with polished rod and wellhead), the rotary table
// with turntable and kelly, the triplex mud pump with its drive sheave, the drawworks, the derrick section,
// the annular BOP, the choke manifold, the mud mixing hopper, the Christmas tree (open, master closed, wing
// closed), the wellhead, the three-phase separator and the geophone.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/drilling/oil_field.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); OIL.paint('<repo>')
//   await OIL.exportAll('<repo>')
//
// Front to the north. The parts are static multipart (PcBlockStateProvider): pumpjack = pumpjack_gearbox +
// crank_counterweight + walking_beam + pitman_arm + polished_rod (all drawn round the same anchor, 3x1x3
// footprint, rising to y 32); rotary_table + turntable + kelly; mud_pump + crankshaft. Footprints as the
// placeholders: mud pump, drawworks and separator 3x2x2 (x -16..32, z 0..32), derrick section 3x3x3 round the
// anchor, choke manifold 2x1x1, Christmas tree 1x2.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'oil/';
  const T = {};

  // Wire rope on a drum: helical lay, greasy dark steel.
  T.rope = () => {
    const im = img(32, 32), r = rng(10101);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add((x + y * 2) % 4 < 2 ? [104, 106, 104] : [60, 62, 62], (y % 4 === 3 ? -24 : 0) + (r() - 0.5) * 8));
    return im;
  };
  // Pumping unit grey-blue paint with oil streaks.
  T.pump_paint = () => {
    const im = img(32, 32), r = grain(im, [96, 116, 130], 10111, 4, 6, 8);
    for (let k = 0; k < 5; k++) { const x = Math.floor(r() * 32), y0 = Math.floor(r() * 16); for (let j = 0; j < 12; j++) put(im, x, y0 + j, add(get(im, x, y0 + j), -26)); }
    return im;
  };
  // Rig red (BOP, mud pump, drawworks).
  T.rig_red = () => { const im = img(32, 32), r = grain(im, [170, 44, 34], 10121, 4, 6, 8); for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [90, 40, 30]); return im; };
  // Derrick yellow lattice angle.
  T.derrick = () => { const im = img(16, 16), r = grain(im, [214, 176, 36], 10131, 4, 4, 4); for (let y = 0; y < 16; y++) { put(im, 0, y, [240, 210, 80]); put(im, 15, y, [150, 120, 20]); } return im; };
  // Mud-spattered skid deck.
  T.mud = () => { const im = img(32, 32), r = grain(im, [96, 84, 70], 10141, 8, 8, 6); for (let k = 0; k < 40; k++) put(im, r() * 32, r() * 32, [70, 60, 50]); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    rope: { tex: TEX + 'rope', P: 8 }, pj: { tex: TEX + 'pump_paint', P: 16 }, rred: { tex: TEX + 'rig_red', P: 16 },
    der: { tex: TEX + 'derrick', P: 16 }, mud: { tex: TEX + 'mud', P: 16 },
    grat: { tex: 'steelmaking/grating', P: 8 }, gal: { tex: 'disconnector/galvanised', P: 16 }, glass: { tex: 'electronics/glass', P: 16 },
    l_r: { tex: 'grid_lines/lamp_red', P: 16 }, l_g: { tex: 'grid_lines/lamp_green', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  // Gate valve with a handwheel on a vertical run: body, bonnet up the given axis, wheel; `shut` tags it red.
  function gateValve(m, n, x, y, z, along, shut) {
    m.box(`${n}_body`, [x - 1.6, y - 1.6, z - 1.6], [x + 1.6, y + 1.6, z + 1.6], 'st', { split: '' });
    if (along === 'x') {
      m.box(`${n}_bonnet`, [x - 0.8, y - 0.8, z - 4], [x + 0.8, y + 0.8, z - 1.6], 'st', { split: '' });
      m.box(`${n}_wheel`, [x - 1.8, y - 1.8, z - 4.6], [x + 1.8, y + 1.8, z - 4], shut ? 'red' : 'green', { split: '' });
    } else {
      m.box(`${n}_bonnet`, [x - 4, y - 0.8, z - 0.8], [x - 1.6, y + 0.8, z + 0.8], 'st', { split: '' });
      m.box(`${n}_wheel`, [x - 4.6, y - 1.8, z - 1.8], [x - 4, y + 1.8, z + 1.8], shut ? 'red' : 'green', { split: '' });
    }
  }

  // ---------------------------------------------------------------- beam pumping unit
  // Base: concrete pad, the I-beam skid, the gear reducer with its brake, the motor on its slide rails and the
  // belt guard.
  function pjBase() {
    const m = mk(TEX + 'pump_paint');
    m.box('pad', [-16, 0, 0], [32, 1.2, 16], 'conc', { split: 'x' });
    m.box('skid', [-10, 1.2, 6], [30, 2.8, 10], 'frame', { split: 'x' });
    m.box('reducer', [19, 2.8, 4.4], [27, 10.6, 11.6], 'pj', { split: '' });
    m.box('reducer_cap', [20, 10.6, 5], [26, 11.4, 11], 'pj', { split: '' });
    m.prism('crank_shaft', 'z', [23, 8.4], 0.9, 2, 14, 'bright', { split: '' });
    m.box('brake_drum', [17.4, 5, 11.6], [19.6, 9, 13], 'ci', { split: '' });
    m.box('brake_lever', [14, 2.8, 12], [14.6, 10, 12.6], 'red', { split: '' });
    for (const z of [11.6, 14]) m.box(`motor_rail_${z}`, [27, 1.2, z], [32, 2, z + 0.8], 'frame', { split: '' });
    m.box('motor', [27.6, 2, 11.6], [31.6, 6.6, 15.4], 'blue', { split: '' });
    m.box('belt_guard', [21.6, 3, 11.6], [27.6, 9.6, 13.4], 'yel', { split: '' });
    return m;
  }
  // Cranks either side of the reducer with their counterweights (hazard striped), pointing down and back.
  function pjCrank() {
    const m = mk(TEX + 'pump_paint');
    for (const [s, z0, z1] of [['n', 2.4, 3.6], ['s', 12.4, 13.6]]) {
      m.box(`crank_${s}`, [16, 7.6, z0], [24, 9.2, z1], 'pj', { split: '', rot: { axis: 'z', angle: -22.5, origin: [23, 8.4, 8] } });
      m.box(`weight_${s}`, [14.4, 3, z0 - 0.6], [19.4, 9.6, z1 + 0.6], 'haz', { split: '', rot: { axis: 'z', angle: -22.5, origin: [23, 8.4, 8] } });
      m.box(`pin_${s}`, [16.4, 9.4, z0 - 0.8], [17.6, 10.6, z1 + 0.8], 'bright', { split: '', rot: { axis: 'z', angle: -22.5, origin: [23, 8.4, 8] } });
    }
    return m;
  }
  // Samson post (front leg upright, back leg raked), the walking beam on the saddle bearing, the horsehead at
  // the west end and the equaliser at the east end.
  function pjBeam() {
    const m = mk(TEX + 'pump_paint');
    m.box('post_front', [6.6, 1.2, 6], [8.4, 24, 7.4], 'pj', { split: 'y' });
    m.box('post_front_b', [6.6, 1.2, 8.6], [8.4, 24, 10], 'pj', { split: 'y' });
    m.box('post_back', [11, 0, 6.2], [12.6, 23, 9.8], 'pj', { split: '', rot: { axis: 'z', angle: 22.5, origin: [8, 23, 8] } });
    m.box('saddle', [6, 24, 6], [10, 25.6, 10], 'ci', { split: '' });
    m.box('beam_web', [-11, 26.4, 7.6], [26, 28.6, 8.4], 'pj', { split: 'x' });
    m.box('beam_top', [-11, 28.6, 6.6], [26, 29.4, 9.4], 'pj', { split: 'x' });
    m.box('beam_bot', [-11, 25.6, 6.6], [26, 26.4, 9.4], 'pj', { split: 'x' });
    m.box('head_back', [-12, 20, 6.4], [-10.6, 31, 9.6], 'pj', { split: '' });
    for (const [i, y0, y1, x0] of [[0, 29, 31.4, -15], [1, 25, 29, -16], [2, 21, 25, -15.6], [3, 18.4, 21, -14.4]]) {
      m.box(`head_${i}`, [x0, y0, 6.6], [-12, y1, 9.4], 'pj', { split: '' });
    }
    m.box('equaliser', [24, 22.4, 2], [27, 25.6, 14], 'pj', { split: '' });
    m.box('ladder', [7, 2, 5.6], [8, 22, 6], 'yel', { split: 'y' });
    return m;
  }
  // Pitman arms from the equaliser down to the crank pins.
  function pjPitman() {
    const m = mk(TEX + 'pump_paint');
    for (const [s, z0, z1] of [['n', 2.2, 3], ['s', 13, 13.8]]) {
      m.box(`pitman_${s}`, [21.15, 8.6, z0], [22.35, 24, z1], 'pj', { split: '', rot: { axis: 'z', angle: -22.5, origin: [21.75, 16.3, 8] } });
    }
    return m;
  }
  // Bridle wires down the horsehead, carrier bar, polished rod, stuffing box and the wellhead tee with its valves.
  function pjRod() {
    const m = mk('material/bright_steel');
    for (const z of [7.4, 8.6]) m.box(`bridle_${z}`, [-15.9, 7, z - 0.15], [-15.6, 19, z + 0.15], 'st', { split: '' });
    m.box('carrier_bar', [-16, 6.4, 6.6], [-15.2, 7, 9.4], 'ci', { split: '' });
    m.box('rod_clamp', [-15.9, 5.6, 7.4], [-15, 6.4, 8.6], 'st', { split: '' });
    m.box('polished_rod', [-15.75, 2.6, 7.75], [-15.25, 6.4, 8.25], 'bright', { split: '' });
    m.box('stuffing_box', [-16, 1.4, 7], [-14.6, 2.6, 9], 'br', { split: '' });
    m.box('tee', [-16, 0, 6.6], [-14, 1.4, 9.4], 'st', { split: '' });
    m.box('flowline', [-14, 0.2, 7.4], [-8, 1.2, 8.6], 'p_liq', { split: '' });
    m.box('wing_valve', [-12, 0, 7], [-10.4, 1.8, 9], 'green', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- rig floor machinery
  // Rotary table housing (cast, grey) with the chain case to the east; turntable with the master bushing and
  // kelly bushing; the square kelly rising through it.
  function rotaryTable() {
    const m = mk('plant/structure');
    m.box('base', [0.4, 0, 0.4], [15.6, 2, 15.6], 'frame', { split: '' });
    m.box('housing', [1, 2, 1], [15, 11.6, 15], 'ci', { split: '' });
    m.box('chain_case', [15, 2, 4], [16, 10, 12], 'yel', { split: '' });
    m.box('lock', [1.6, 8, 0.4], [3.6, 10, 1], 'red', { split: '' });
    return m;
  }
  function turntable() {
    const m = mk('material/steel');
    m.prismY('table', [8, 8], 6.8, 11.6, 13, 'st', { ends: ['up'] });
    m.box('master_bushing', [4, 13, 4], [12, 14, 12], 'ci', { split: '' });
    m.box('kelly_bushing', [5, 14, 5], [11, 15.6, 11], 'yel', { split: '' });
    for (const [x, z] of [[4.6, 4.6], [10.6, 4.6], [4.6, 10.6], [10.6, 10.6]]) m.box(`drive_pin_${x}_${z}`, [x, 14, z], [x + 0.8, 15, z + 0.8], 'bright', { split: '' });
    return m;
  }
  function kelly() {
    const m = mk('material/steel');
    m.box('kelly', [6.4, 15.6, 6.4], [9.6, 32, 9.6], 'frame', { split: 'y' });
    m.box('kelly_cock', [6, 30, 6], [10, 32, 10], 'red', { split: '' });
    return m;
  }
  // Triplex mud pump: red power end on a skid, three fluid-end modules with valve covers to the north, the
  // suction manifold below and the discharge with its pulsation dampener (ball) and relief valve, the chain
  // guard and the electric motor at the back; the crankshaft part is the big drive sheave on the west end.
  function mudPump() {
    const m = mk(TEX + 'rig_red');
    m.box('skid', [-16, 0, 0], [32, 2, 32], 'mud', { split: 'xz' });
    m.box('power_end', [-6, 2, 10], [26, 18, 26], 'rred', { split: 'x' });
    m.box('power_cover', [-4, 18, 12], [24, 20, 24], 'rred', { split: 'x' });
    for (let i = 0; i < 3; i++) {
      const x = 0 + i * 8;
      m.box(`fluid_${i}`, [x - 3, 4, 2], [x + 3, 14, 10], 'st', { split: '' });
      m.box(`cover_top_${i}`, [x - 2, 14, 4], [x + 2, 16, 8], 'ci', { split: '' });
      m.box(`cover_front_${i}`, [x - 2, 7, 0.4], [x + 2, 11, 2], 'ci', { split: '' });
      m.box(`liner_${i}`, [x - 1.4, 8, 10], [x + 1.4, 11, 12], 'bright', { split: '' });
    }
    m.box('suction', [-6, 2, 3], [22, 4, 7], 'p_liq', { split: 'x' });
    m.box('discharge', [-6, 16, 5], [26, 17.6, 6.6], 'st', { split: 'x' });
    m.cylY('dampener_body', [28, 6], 3.4, 4, 16, 'rred', { cap: TEX + 'rig_red' });
    m.box('dampener_leg', [26.6, 2, 4.6], [29.4, 4, 7.4], 'frame', { split: '' });
    m.box('relief', [-9, 15, 4.6], [-6, 18, 7], 'red', { split: '' });
    m.box('chain_guard', [-16, 2, 14], [-6, 20, 24], 'yel', { split: 'y' });
    m.box('motor', [4, 2, 26], [24, 14, 32], 'blue', { split: 'x' });
    return m;
  }
  function mudSheave() {
    const m = mk('material/steel');
    m.prism('sheave', 'x', [10, 18], 7, -6.6, -6, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('hub', 'x', [10, 18], 2, -7.4, -6, 'st', { split: '', ends: ['west'] });
    return m;
  }
  // Drawworks: the drum with wire rope on its shaft between bearing stands, the brake bands and rims, the red
  // frame and covers, two drive motors, the driller's side guard rail.
  function drawworks() {
    const m = mk(TEX + 'rig_red');
    m.box('skid', [-16, 0, 0], [32, 2, 32], 'mud', { split: 'xz' });
    for (const x of [-12, 24]) m.box(`stand_${x}`, [x, 2, 4], [x + 4, 20, 26], 'rred', { split: 'y' });
    m.prism('drum', 'x', [13, 15], 7, -8, 24, 'rope', { split: '', ends: [] });
    for (const x of [-8, 22]) m.prism(`rim_${x}`, 'x', [13, 15], 8.4, x, x + 2, 'bright', { split: '', ends: ['east', 'west'] });
    m.box('brake_lever', [26, 14, 2], [27, 24, 3], 'st', { split: '' });
    m.box('cover', [-12, 20, 4], [28, 22, 26], 'rred', { split: 'x' });
    for (const x of [-16, 28]) m.box(`motor_${x}`, [x, 2, 14], [x + 4, 12, 26], 'blue', { split: '' });
    m.box('chain_case', [-16, 2, 26], [32, 14, 30], 'yel', { split: 'x' });
    m.box('rope_off', [10.6, 21, 14.6], [11.4, 32, 15.4], 'st', { split: '' });
    return m;
  }
  // Derrick section: four yellow corner legs with girts at each block line and X-braces on every face, a ladder.
  function derrick() {
    const m = mk(TEX + 'derrick');
    const L = [-12, 27];
    for (const x of L) for (const z of L) m.box(`leg_${x}_${z}`, [x, -16, z], [x + 1.4, 32, z + 1.4], 'der', { split: 'y' });
    for (const y of [-16, 0, 16]) {
      m.box(`girt_n_${y}`, [-12, y, -12], [28.4, y + 0.8, -11.4], 'der', { split: 'x' });
      m.box(`girt_s_${y}`, [-12, y, 27.6], [28.4, y + 0.8, 28.2], 'der', { split: 'x' });
      m.box(`girt_w_${y}`, [-12, y, -11.4], [-11.4, y + 0.8, 27.6], 'der', { split: 'z' });
      m.box(`girt_e_${y}`, [27.8, y, -11.4], [28.4, y + 0.8, 27.6], 'der', { split: 'z' });
      const cy = y + 8, len = 39.4 / Math.cos(Math.PI / 8);
      for (const a of [22.5, -22.5]) {
        m.box(`brace_n_${y}_${a}`, [8.2 - len / 2, cy - 0.3, -12], [8.2 + len / 2, cy + 0.3, -11.6], 'der', { split: '', rot: { axis: 'z', angle: a, origin: [8.2, cy, -11.8] } });
        m.box(`brace_s_${y}_${a}`, [8.2 - len / 2, cy - 0.3, 28], [8.2 + len / 2, cy + 0.3, 28.4], 'der', { split: '', rot: { axis: 'z', angle: a, origin: [8.2, cy, 28.2] } });
        m.box(`brace_w_${y}_${a}`, [-12, cy - 0.3, 8.2 - len / 2], [-11.6, cy + 0.3, 8.2 + len / 2], 'der', { split: '', rot: { axis: 'x', angle: a, origin: [-11.8, cy, 8.2] } });
        m.box(`brace_e_${y}_${a}`, [28, cy - 0.3, 8.2 - len / 2], [28.4, cy + 0.3, 8.2 + len / 2], 'der', { split: '', rot: { axis: 'x', angle: a, origin: [28.2, cy, 8.2] } });
      }
    }
    for (const x of [24, 26.2]) m.box(`ladder_rail_${x}`, [x, -16, -12.6], [x + 0.4, 32, -12.2], 'der', { split: 'y' });
    for (let y = -15; y < 32; y += 2) m.box(`ladder_rung_${y}`, [24, y, -12.6], [26.6, y + 0.3, -12.2], 'der', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- well control and wellheads
  // Annular BOP: a red body with the bolted cap, side outlets, hydraulic open/close ports and hoses; closed: the
  // red indicator lit.
  function bop(closed) {
    const m = mk(TEX + 'rig_red');
    m.prismY('flange_lo', [8, 8], 7.4, 0, 1.6, 'rred', { ends: ['up', 'down'] });
    m.prismY('body', [8, 8], 6.6, 1.6, 10, 'rred', {});
    m.prismY('cap', [8, 8], 7.2, 10, 12.4, 'rred', { ends: ['up', 'down'] });
    m.prismY('top', [8, 8], 5, 12.4, 14.6, 'rred', {});
    m.prismY('flange_hi', [8, 8], 6, 14.6, 16, 'rred', { ends: ['up'] });
    m.box('bore', [6, 15.95, 6], [10, 16.05, 10], 'soot', { split: '', hide: ['down'] });
    for (const z of [0.4, 13.6]) m.box(`port_${z}`, [6.6, 4, z], [9.4, 6.4, z + 2], 'st', { split: '' });
    m.box('hose_open', [1, 4.6, 0.6], [6.6, 5.6, 1.6], 'rubber', { split: '' });
    m.box('hose_close', [9.4, 4.6, 14.4], [15, 5.6, 15.4], 'rubber', { split: '' });
    m.box((closed ? 'glow_' : '') + 'indicator', [7.2, 11, 0.6], [8.8, 12, 0.85], closed ? 'l_r' : 'l_g', { split: '' });
    return m;
  }
  // Choke manifold on a skid: the inlet cross, a manual and a hydraulic choke with their wheels, gate valves,
  // the gauge, the outlet to the poor-boy separator.
  function choke() {
    const m = mk('material/steel');
    m.box('skid', [0, 0, 0.6], [32, 1.4, 15], 'frame', { split: 'x' });
    m.box('header', [2, 6, 6.6], [30, 8.4, 9], 'st', { split: 'x' });
    for (const x of [6, 14, 22]) {
      gateValve(m, `valve_${x}`, x, 7.2, 7.8, 'x', false);
      m.box(`leg_${x}`, [x - 1, 1.4, 7], [x + 1, 5.6, 8.6], 'frame', { split: '' });
    }
    m.box('choke_manual', [25, 5, 6], [29, 9.6, 10], 'st', { split: '' });
    m.box('choke_wheel', [24.6, 5.4, 1.4], [29.4, 9.4, 2], 'red', { split: '' });
    m.box('choke_stem', [26.6, 6.8, 2], [27.4, 7.6, 6], 'st', { split: '' });
    m.box('choke_hydraulic', [9, 9.6, 6.4], [11.6, 13, 9.2], 'yel', { split: '' });
    m.box('gauge_case', [2.2, 9.6, 6.6], [5.6, 13, 7.4], 'rubber', { split: '' });
    m.plate('gauge', [2.6, 10, 6.5], [5.2, 12.6, 6.6], 'rubber', 'north', 'control/pressure_dial', { hide: ['south'] });
    m.box('outlet', [30, 6.4, 7], [32, 8, 8.6], 'st', { split: '' });
    return m;
  }
  // Mud mixing hopper: a funnel with a grating, the sack table, the venturi jet below and the mud line.
  function hopper() {
    const m = mk('material/steel');
    for (const [x, z] of [[2, 2], [12.8, 2], [2, 12.8], [12.8, 12.8]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1.2, 8, z + 1.2], 'frame', { split: '' });
    for (const [n, f, t] of [['n', [1.6, 10, 1.6], [14.4, 15, 2.4]], ['s', [1.6, 10, 13.6], [14.4, 15, 14.4]], ['w', [1.6, 10, 2.4], [2.4, 15, 13.6]], ['e', [13.6, 10, 2.4], [14.4, 15, 13.6]]]) m.box(`funnel_${n}`, f, t, 'st', { split: '' });
    m.box('cone', [4, 8, 4], [12, 10, 12], 'st', { split: '' });
    m.box('grating', [2.4, 14.6, 2.4], [13.6, 15, 13.6], 'grat', { split: '', hide: ['down'] });
    m.box('table', [-0, 7.6, 14.4], [16, 8.2, 16], 'mud', { split: '' });
    m.box('venturi', [0, 4, 7], [16, 6, 9], 'p_liq', { split: 'x' });
    m.box('throat', [6.8, 6, 7.2], [9.2, 8, 8.8], 'st', { split: '' });
    return m;
  }
  // Christmas tree: casing and tubing heads, the lower and upper master valves, the cross with the wing valve and
  // the adjustable choke to the flowline, the swab valve and the tree cap with its gauge (1x2).
  function tree(state) {
    const m = mk('material/steel');
    m.prismY('casing_head', [8, 8], 4.6, 0, 3, 'st', { ends: ['up'] });
    m.prismY('tubing_head', [8, 8], 3.6, 3, 6, 'st', { ends: ['up'] });
    gateValve(m, 'master_lo', 8, 8.4, 8, 'x', state === 'master');
    gateValve(m, 'master_hi', 8, 13.2, 8, 'x', false);
    m.box('cross', [6, 16, 6], [10, 20, 10], 'st', { split: '' });
    m.box('wing_run', [10, 17.2, 7.2], [16, 18.8, 8.8], 'st', { split: '' });
    gateValve(m, 'wing', 12.6, 18, 8, 'x', state === 'wing');
    m.box('choke', [14.4, 16.4, 6.4], [16, 19.6, 9.6], 'st', { split: '' });
    m.box('choke_wheel', [15.4, 20, 7], [16, 22, 9], 'red', { split: '' });
    gateValve(m, 'swab', 8, 22.6, 8, 'x', false);
    m.prismY('cap', [8, 8], 2.2, 24.4, 26.4, 'st', { ends: ['up'] });
    m.prismY('gauge_stem', [8, 8], 0.5, 26.4, 27, 'br', {});
    m.box('gauge', [6.8, 27, 7.4], [9.2, 29.4, 8.6], 'rubber', { split: '', face: { north: { tex: 'control/pressure_dial', uv: [0, 0, 16, 16] } } });
    if (state !== 'open') m.box('glow_tag', [5, 10, 4.6], [6, 11.6, 5], 'l_r', { split: '' });
    return m;
  }
  // Wellhead: casing head flange stack with the two side outlets and their valves, bolts round the flanges.
  function wellhead() {
    const m = mk('material/steel');
    m.prismY('casing', [8, 8], 3.6, 0, 3, 'frame', {});
    m.prismY('flange_lo', [8, 8], 6, 3, 5, 'st', { ends: ['up', 'down'] });
    m.prismY('spool', [8, 8], 4.4, 5, 10, 'st', {});
    m.prismY('flange_hi', [8, 8], 6, 10, 12, 'st', { ends: ['up', 'down'] });
    m.prismY('bonnet', [8, 8], 3.4, 12, 15, 'st', { ends: ['up'] });
    m.box('bore_cap', [6.6, 15, 6.6], [9.4, 16, 9.4], 'ci', { split: '' });
    for (const [n, x0, x1] of [['w', 0, 3.6], ['e', 12.4, 16]]) {
      m.box(`outlet_${n}`, [x0, 6.6, 6.8], [x1, 8.8, 9.2], 'st', { split: '' });
      m.box(`valve_${n}`, [n === 'w' ? 0.6 : 13, 6, 6.2], [n === 'w' ? 3 : 15.4, 9.4, 9.8], 'st', { split: '' });
      m.box(`wheel_${n}`, [n === 'w' ? 1 : 13.4, 9.4, 6.4], [n === 'w' ? 2.6 : 15, 10, 9.6], 'green', { split: '' });
    }
    for (const a of [0, 90, 180, 270]) {
      const t = a * Math.PI / 180, x = 8 + Math.cos(t) * 5.2, z = 8 + Math.sin(t) * 5.2;
      m.box(`bolt_${a}`, [x - 0.4, 12, z - 0.4], [x + 0.4, 12.6, z + 0.4], 'bright', { split: '' });
    }
    return m;
  }
  // Three-phase separator: horizontal vessel on saddles (x -16..32), the gas outlet with the relief valve on
  // top, the oil and water outlets with level controllers, sight glasses, the inlet nozzle, a ladder.
  function separator() {
    const m = mk('plant/light');
    for (const x of [-10, 22]) m.box(`saddle_${x}`, [x, 0, 7], [x + 4, 8, 24], 'frame', { split: '' });
    m.prism('shell', 'x', [16, 15.5], 9, -14, 30, 'light', { split: '' });
    for (const x of [-16, 30]) m.prism(`head_${x}`, 'x', [16, 15.5], 7, x, x + 2, 'light', { split: '', ends: [x < 0 ? 'west' : 'east'] });
    m.box('inlet', [-16, 18, 14], [-13, 20.6, 17], 'p_liq', { split: '' });
    m.box('gas_out', [24, 25, 14], [27, 28, 17], 'p_gas', { split: '' });
    m.box('relief', [16, 25, 14.4], [18.4, 28, 16.8], 'red', { split: '' });
    m.box('oil_out', [10, 2, 20], [12.6, 7.4, 22.6], 'p_liq', { split: '' });
    m.box('water_out', [2, 2, 20], [4.6, 7.4, 22.6], 'p_water', { split: '' });
    for (const x of [3.2, 11.2]) {
      m.box(`lc_${x}`, [x - 1.6, 14, 5.6], [x + 1.6, 18, 7], 'en', { split: '' });
      m.box(`sight_${x}`, [x + 2, 10, 6.2], [x + 3, 21, 6.8], 'glass', { split: '' });
    }
    m.box('manway', [24, 12, 5.8], [29, 19, 6.6], 'st', { split: '' });
    for (const x of [-12.4, -10.2]) m.box(`ladder_rail_${x}`, [x, 0, 5.4], [x + 0.4, 25, 5.8], 'yel', { split: 'y' });
    for (let y = 1; y < 25; y += 2) m.box(`ladder_rung_${y}`, [-12.4, y, 5.4], [-9.8, y + 0.3, 5.8], 'yel', { split: '' });
    return m;
  }
  // Geophone: an orange spike sensor pressed into the ground, its takeout cable.
  function geophone() {
    const m = mk('plant/yellow');
    m.box('spike', [7.6, 0, 7.6], [8.4, 1.6, 8.4], 'st', { split: '' });
    m.box('body', [6.6, 1.6, 6.6], [9.4, 4.6, 9.4], 'p_acid', { split: '' });
    m.box('cap', [7, 4.6, 7], [9, 5.4, 9], 'rubber', { split: '' });
    m.box('cable', [9.4, 3, 7.7], [16, 3.6, 8.3], 'rubber', { split: '' });
    return m;
  }

  const BUILDS = {
    pumpjack_gearbox: { model: pjBase, cat: 'oil_production' },
    'pumpjack_gearbox/crank_counterweight': { model: pjCrank, cat: 'oil_production', java: 'pumpjack_gearbox/partial/crank_counterweight' },
    'pumpjack_gearbox/walking_beam': { model: pjBeam, cat: 'oil_production', java: 'pumpjack_gearbox/partial/walking_beam' },
    'pumpjack_gearbox/pitman_arm': { model: pjPitman, cat: 'oil_production', java: 'pumpjack_gearbox/partial/pitman_arm' },
    'pumpjack_gearbox/polished_rod': { model: pjRod, cat: 'oil_production', java: 'pumpjack_gearbox/partial/polished_rod' },
    rotary_table: { model: rotaryTable, cat: 'drilling' },
    'rotary_table/turntable': { model: turntable, cat: 'drilling', java: 'rotary_table/partial/turntable' },
    'rotary_table/kelly': { model: kelly, cat: 'drilling', java: 'rotary_table/partial/kelly' },
    mud_pump: { model: mudPump, cat: 'drilling' },
    'mud_pump/crankshaft': { model: mudSheave, cat: 'drilling', java: 'mud_pump/partial/crankshaft' },
    drawworks: { model: drawworks, cat: 'drilling' },
    derrick_segment: { model: derrick, cat: 'drilling' },
    bop_annular: { model: () => bop(false), cat: 'drilling' }, bop_annular_closed: { model: () => bop(true), cat: 'drilling' },
    choke_manifold: { model: choke, cat: 'drilling' },
    mud_mixing_hopper: { model: hopper, cat: 'drilling' },
    christmas_tree: { model: () => tree('open'), cat: 'oil_production' },
    christmas_tree_master_closed: { model: () => tree('master'), cat: 'oil_production' },
    christmas_tree_wing_closed: { model: () => tree('wing'), cat: 'oil_production' },
    wellhead: { model: wellhead, cat: 'oil_production' },
    three_phase_separator: { model: separator, cat: 'oil_production' },
    geophone: { model: geophone, cat: 'oil_exploration' },
  };

  window.OIL = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    async exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        PCART.realize(root, BUILDS[id].model(), id);
        await new Promise((r) => setTimeout(r, 1200));
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
      }
      return out;
    },
  };
})();
