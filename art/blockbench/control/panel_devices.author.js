// Control room and plant instruments, hand-authored for Blockbench (replaces the spec_*.py placeholders):
// panel pushbutton, selector switch with its knob, key switch with its key, indicator lamp, the SCRAM button
// under its flip guard, the gas detector, the pressure gauge, the synchroscope, the energy meter, the recording
// gauge with its chart, the vibration transmitter, the neutron detector, the document lectern, the reactor
// controller and the protection relay cabinet.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/control/panel_devices.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); PNL.paint('<repo>')
//   await PNL.exportAll('<repo>')
//
// Front to the north. Contracts with the code:
//   Panel devices are wall blocks whose terminals (TerminalLayout) sit on the front plane: pushbutton z 10.87,
//     corners 2.5..13.5; selector switch z 12.4, corners; key switch z 12.4, bottom row y 2; indicator lamp z 10.4,
//     bottom row y 2; gas detector z 7.9, three rows x 5..11.5, y 3.5..13.5.
//   selector_switch/partial/knob turns about z through (8, 8, 8); key_switch/partial/key through (8, 8.5, 8).
//   Needles drawn by renderers on the plane z 10.85: pressure gauge about (8, 8.5), synchroscope about (8, 8); the
//     dial faces sit at z 10.9 and carry no needle.
//   recording_gauge is multipart: the base (with or without the pen) and recording_gauge/partial/drum, the chart.
(function () {
  const fs = require('fs');
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'control/';
  const T = {};

  const TERM = {
    pushbutton: [10.87, 2.5, 2.5, 13.5, 13.5, 2, 4], selector_switch: [12.4, 2.5, 2.5, 13.5, 13.5, 2, 4],
    key_switch: [12.4, 2.5, 2, 13.5, 2, 3, 3], indicator_lamp: [10.4, 3, 2, 13, 2, 2, 2],
    gas_detector: [7.9, 5, 3.5, 11.5, 13.5, 3, 7],
  };
  function termPoints(k) {
    const [z, x0, y0, x1, y1, cols, n] = TERM[k], rows = Math.ceil(n / cols), out = [];
    for (let i = 0; i < n; i++) {
      const r = Math.floor(i / cols), c = i % cols;
      out.push([16 - (cols === 1 ? (x0 + x1) / 2 : x0 + (x1 - x0) * c / (cols - 1)), rows === 1 ? y1 : y1 - (y1 - y0) * r / (rows - 1), z]);
    }
    return out;
  }
  function terminals(m, k, back) {
    termPoints(k).forEach(([x, y, z], i) => {
      m.box(`term_${i}`, [x - 0.65, y - 0.65, z + 0.15], [x + 0.65, y + 0.65, back], 'tblk', { split: '' });
      m.box(`screw_${i}`, [x - 0.42, y - 0.42, z + 0.02], [x + 0.42, y + 0.42, z + 0.15], 'br', { split: '' });
    });
  }

  // ---------------------------------------------------------------- textures (32 px per block)
  // Control panel sheet: light grey-green (Soviet control rooms), fine stipple.
  T.panel = () => { const im = img(32, 32), r = grain(im, [176, 184, 172], 6101, 3, 3, 8); for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [150, 158, 148]); return im; };
  // Engraved legend plate: black with white lettering strokes.
  T.legend = () => { const im = img(16, 16), r = rng(6111); rect(im, 0, 0, 16, 16, [26, 26, 28]); for (let x = 2; x < 14; x++) if (r() < 0.7) { put(im, x, 7, [230, 230, 224]); if (r() < 0.4) put(im, x, 8, [230, 230, 224]); } return im; };
  // Selector legend: positions round the knob (0 at the top, 1-3 to the right).
  T.selector_plate = () => {
    const im = img(32, 32);
    rect(im, 0, 0, 32, 32, [30, 30, 32]);
    for (const a of [90, 45, 0, -45]) { const t = a * Math.PI / 180; for (let s = 11; s < 14; s++) put(im, 15.5 + Math.cos(t) * s, 15.5 - Math.sin(t) * s, [235, 235, 228]); }
    return im;
  };
  // Pressure gauge dial: white, black scale over 270 degrees, the unit block, no needle (drawn by the renderer).
  T.pressure_dial = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, Math.hypot(x - 15.5, y - 15.5) < 15.6 ? [240, 238, 230] : [30, 30, 30]);
    for (let k = 0; k <= 40; k++) {
      const a = (225 - k * 6.75) * Math.PI / 180, L = k % 5 === 0 ? 4 : 2;
      for (let s = 0; s < L; s++) put(im, 15.5 + Math.cos(a) * (13.5 - s), 15.5 - Math.sin(a) * (13.5 - s), [20, 20, 20]);
    }
    for (let k = 32; k <= 40; k++) { const a = (225 - k * 6.75) * Math.PI / 180; put(im, 15.5 + Math.cos(a) * 14.4, 15.5 - Math.sin(a) * 14.4, [200, 30, 24]); }
    rect(im, 12, 21, 8, 2, [40, 40, 40]);
    return im;
  };
  // Synchroscope dial: black ring scale, white SLOW / FAST arrows, the synchronising mark at the top.
  T.synchro_dial = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, Math.hypot(x - 15.5, y - 15.5) < 15.6 ? [236, 234, 224] : [24, 24, 24]);
    for (let k = 0; k < 24; k++) { const a = k * 15 * Math.PI / 180; for (let s = 0; s < 2; s++) put(im, 15.5 + Math.cos(a) * (13 - s), 15.5 - Math.sin(a) * (13 - s), [20, 20, 20]); }
    rect(im, 15, 1, 2, 5, [200, 30, 24]);
    for (let k = 0; k < 6; k++) { put(im, 6 + k, 24 - k, [40, 40, 40]); put(im, 25 - k, 24 - k, [40, 40, 40]); }
    rect(im, 5, 25, 3, 1, [40, 40, 40]); rect(im, 24, 25, 3, 1, [40, 40, 40]);
    return im;
  };
  // Energy meter face: the window over the aluminium disc with its red mark, the cyclometer counter, the plate.
  T.meter_face = () => {
    const im = img(32, 32), r = rng(6131);
    rect(im, 0, 0, 32, 32, [214, 210, 196]);
    rect(im, 4, 4, 24, 8, [20, 20, 22]);
    for (let i = 0; i < 6; i++) { rect(im, 5 + i * 4, 5, 3, 6, i === 5 ? [190, 40, 30] : [236, 236, 232]); rect(im, 6 + i * 4, 7, 1, 2, [20, 20, 20]); }
    rect(im, 4, 15, 24, 6, [60, 64, 70]); rect(im, 5, 17, 22, 2, [196, 200, 204]); rect(im, 14, 17, 3, 2, [190, 40, 30]);
    for (let x = 6; x < 26; x++) if (r() < 0.6) put(im, x, 25, [60, 60, 60]);
    for (let x = 8; x < 24; x++) if (r() < 0.6) put(im, x, 28, [60, 60, 60]);
    return im;
  };
  // Circular chart paper of the recording gauge: rings, time arcs, a blue trace.
  T.chart = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x - 15.5, y - 15.5), a = Math.atan2(y - 15.5, x - 15.5);
      let c = [244, 242, 232];
      if (Math.round(d) % 3 === 0) c = [200, 210, 220];
      if (Math.abs(((a * 12 / Math.PI) % 1 + 1) % 1 - 0.5) > 0.47) c = [210, 200, 214];
      if (Math.abs(d - (8 + Math.sin(a * 3) * 2.5 + Math.sin(a * 7))) < 0.6) c = [40, 60, 180];
      put(im, x, y, d < 15.6 ? c : [30, 30, 30]);
    }
    return im;
  };
  // Explosion-proof yellow paint and the sensor mesh.
  T.ex_yellow = () => { const im = img(32, 32), r = grain(im, [214, 178, 40], 6141, 4, 5, 8); for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [160, 120, 30]); return im; };
  T.mesh = () => { const im = img(16, 16); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, (x + y) % 2 ? [150, 152, 150] : [60, 62, 62]); return im; };
  // Alarm lamp blinking red (two frames, .mcmeta).
  T.blink_red = () => { const im = img(16, 32); rect(im, 0, 0, 16, 16, [255, 60, 40]); rect(im, 4, 4, 6, 6, [255, 200, 180]); rect(im, 0, 16, 16, 16, [90, 20, 16]); return im; };
  // Protection relay case front (round A1 relays): black bakelite rim, glass, a white scale, the target flag.
  const relayRound = (flag) => () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x - 7.5, y - 7.5);
      put(im, x, y, d > 7.6 ? [20, 20, 20] : d > 6.4 ? [36, 36, 38] : [214, 214, 206]);
    }
    for (let x = 4; x < 12; x++) put(im, x, 6, [40, 40, 40]);
    rect(im, 6, 9, 4, 2, flag ? [220, 40, 30] : [60, 60, 60]);
    return im;
  };
  T.relay_round = relayRound(false);
  T.relay_round_flag = relayRound(true);
  // Reactor controller front: dark console face with a mimic of the core rods (grid of squares) and a bar row.
  T.rc_front = () => {
    const im = img(32, 32), r = rng(6151);
    rect(im, 0, 0, 32, 32, [44, 50, 54]);
    for (let j = 0; j < 7; j++) for (let i = 0; i < 7; i++) { const c = (i + j) % 3 === 0 ? [90, 200, 110] : [70, 80, 86]; rect(im, 4 + i * 3, 3 + j * 3, 2, 2, c); }
    for (let i = 0; i < 6; i++) rect(im, 5 + i * 4, 26, 2, 3, [200, 180, 60]);
    return im;
  };
  T.folder = () => { const im = img(16, 16), r = grain(im, [70, 96, 140], 6161, 4, 3, 4); rect(im, 3, 4, 10, 3, [230, 228, 220]); for (let x = 4; x < 12; x++) if (r() < 0.6) put(im, x, 5, [60, 60, 60]); return im; };
  T.tblock = () => { const im = img(16, 16); grain(im, [128, 130, 126], 6171, 4, 3, 4); return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    pan: { tex: TEX + 'panel', P: 16 }, exy: { tex: TEX + 'ex_yellow', P: 16 }, mesh: { tex: TEX + 'mesh', P: 8 },
    tblk: { tex: TEX + 'tblock', P: 16 }, bak: { tex: 'automation/bakelite', P: 16 }, blk: { tex: 'automation/crinkle', P: 16 },
    cap: { tex: 'automation/cap', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 },
    knob: { tex: 'common/auto_knob', P: 16 }, blink: { tex: TEX + 'blink_red', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 },
    l_w: { tex: 'grid_lines/lamp_white', P: 16 }, l_off: { tex: 'grid_lines/lamp_off', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/block', TEX);
    for (const n of Object.keys(T)) writePng(T[n](), path.join(dir, n + '.png'));
    fs.writeFileSync(path.join(dir, 'blink_red.png.mcmeta'), JSON.stringify({ animation: { frametime: 8 } }, null, '\t') + '\n');
    return Object.keys(T).length;
  }
  // Regular octagon about z from four boxes (a bar each way and the bar turned +-45 degrees).
  const oct = (m, n, x, y, r, z0, z1, mat) => {
    const h = r * Math.tan(Math.PI / 8);
    m.box(`${n}_a`, [x - r, y - h, z0], [x + r, y + h, z1], mat, { split: '' });
    m.box(`${n}_b`, [x - h, y - r, z0 + 0.01], [x + h, y + r, z1 - 0.01], mat, { split: '' });
    for (const [k, a] of [['c', 45], ['d', -45]]) {
      m.box(`${n}_${k}`, [x - r, y - h, z0 + (a > 0 ? 0.02 : 0.03)], [x + r, y + h, z1 - (a > 0 ? 0.02 : 0.03)], mat, { split: '', rot: { axis: 'z', angle: a, origin: [x, y, 8] } });
    }
  };
  // A panel section: the sheet from z0 to the wall.
  const panelSheet = (m, z0) => m.box('panel', [0, 0, z0], [16, 16, 16], 'pan', { split: '' });

  // ---------------------------------------------------------------- panel devices
  // Pushbutton (KE type): chrome bezel, green flush button with a lamp in it (lit), legend plate above.
  function pushbutton(lit) {
    const m = mk(TEX + 'panel');
    panelSheet(m, 11.4);
    terminals(m, 'pushbutton', 11.4);
    oct(m, 'bezel', 8, 7.4, 3, 10.9, 11.4, 'bright');
    oct(m, (lit ? 'glow_' : '') + 'button', 8, 7.4, 2.2, 10.2, 10.9, lit ? 'l_g' : 'green');
    m.plate('legend', [5, 11.6, 11.3], [11, 13.2, 11.4], 'pan', 'north', TEX + 'legend', { hide: ['south'] });
    return m;
  }
  // Selector switch: legend disc with the positions; the knob is the partial.
  function selector() {
    const m = mk(TEX + 'panel');
    panelSheet(m, 12.6);
    terminals(m, 'selector_switch', 12.6);
    m.plate('dial', [4.4, 4.4, 12.5], [11.6, 11.6, 12.6], 'pan', 'north', TEX + 'selector_plate', { hide: ['south'] });
    return m;
  }
  function selectorKnob() {
    const m = mk('common/auto_knob');
    oct(m, 'base', 8, 8, 2.4, 11.4, 12.5, 'knob');
    m.box('grip', [7.2, 5, 10], [8.8, 11, 11.4], 'knob', { split: '' });
    m.box('pointer', [7.75, 9.6, 9.9], [8.25, 10.9, 10], 'l_w', { split: '' });
    return m;
  }
  // Key switch: chrome escutcheon with positions engraved; the key (with its bow) is the partial.
  function keySwitch() {
    const m = mk(TEX + 'panel');
    panelSheet(m, 12.6);
    terminals(m, 'key_switch', 12.6);
    oct(m, 'escutcheon', 8, 8.5, 2.8, 12.2, 12.6, 'bright');
    m.plate('legend', [5, 12.2, 12.5], [11, 13.8, 12.6], 'pan', 'north', TEX + 'legend', { hide: ['south'] });
    return m;
  }
  function key() {
    const m = mk('material/brass');
    oct(m, 'cylinder', 8, 8.5, 1.6, 11.4, 12.2, 'bright');
    m.box('blade', [7.8, 7.6, 10.8], [8.2, 9.4, 11.4], 'br', { split: '' });
    m.box('bow', [6.6, 7.4, 9.8], [9.4, 9.6, 10.8], 'br', { split: '' });
    m.box('tag', [7.3, 4.6, 9.9], [8.7, 7.4, 10.3], 'red', { split: '' });
    return m;
  }
  // Indicator lamp: chrome bezel with a domed white lens, legend below.
  function indicator(lit) {
    const m = mk(TEX + 'panel');
    panelSheet(m, 10.9);
    terminals(m, 'indicator_lamp', 10.9);
    oct(m, 'bezel', 8, 8.6, 2.8, 10.4, 10.9, 'bright');
    oct(m, (lit ? 'glow_' : '') + 'lens', 8, 8.6, 2.1, 9.4, 10.4, lit ? 'l_w' : 'l_off');
    m.plate('legend', [5, 4.4, 10.8], [11, 5.8, 10.9], 'pan', 'north', TEX + 'legend', { hide: ['south'] });
    return m;
  }
  // SCRAM button: the red mushroom on a yellow-black hazard surround under a flip guard of clear plastic held
  // open on its hinge at the top, the "AZ" plate.
  function scram() {
    const m = mk(TEX + 'panel');
    panelSheet(m, 13);
    m.box('surround', [2, 2, 12.4], [14, 14, 13], 'haz', { split: '' });
    oct(m, 'collar', 8, 7.6, 3, 11.6, 12.4, 'rubber');
    oct(m, 'stem', 8, 7.6, 1.4, 10.8, 11.6, 'bright');
    oct(m, 'mushroom', 8, 7.6, 3.4, 9.9, 10.8, 'red');
    m.box('hinge', [3, 13.4, 11.6], [13, 14.2, 12.4], 'bright', { split: '' });
    m.box('guard', [3, 14, 6.6], [13, 14.4, 12.2], 'cap', { split: '' });
    m.box('guard_lip', [3, 13.4, 6.4], [13, 14.4, 6.8], 'cap', { split: '' });
    m.plate('legend', [5.6, 2.6, 12.3], [10.4, 4, 12.4], 'pan', 'north', TEX + 'legend', { hide: ['south'] });
    return m;
  }

  // ---------------------------------------------------------------- field and panel instruments
  // Gas detector: yellow Ex housing on a bracket, the screw-on cover with the state lamp (green, amber, red
  // blinking), the cable gland, the sensor head below with its mesh; terminals on the housing front.
  function gasDetector(state) {
    const m = mk(TEX + 'ex_yellow');
    m.box('bracket', [4, 3, 15], [12, 15, 16], 'frame', { split: '' });
    m.box('body', [4.6, 3.6, 8.2], [11.4, 14, 15], 'exy', { split: '' });
    m.box('cover', [4.2, 13.6, 7.8], [11.8, 15, 15], 'exy', { split: '' });
    terminals(m, 'gas_detector', 8.2);
    const col = { none: 'l_g', warn: 'l_a', alarm: 'blink' }[state];
    m.box('glow_status', [7, 14, 7.3], [9, 14.8, 7.8], col, { split: '', hide: ['south'] });
    m.prism('gland', 'x', [6, 11.6], 0.9, 11.4, 12.6, 'br', { split: '', ends: ['east'] });
    oct(m, 'sensor', 8, 2.4, 1.6, 9.6, 13.6, 'exy');
    m.box('sensor_head', [6.2, 1, 9.8], [9.8, 3.6, 13.4], 'exy', { split: '', face: { down: 'mesh' } });
    return m;
  }
  // Pressure gauge (MP-100 type) on a wall plate: black case, chrome bezel, white dial at z 10.9 for the needle
  // drawn at z 10.85 about (8, 8.5), the brass siphon loop below.
  function pressureGauge() {
    const m = mk(TEX + 'pressure_dial');
    m.box('plate', [3, 1, 15], [13, 15, 16], 'frame', { split: '' });
    m.prism('case', 'z', [8, 8.5], 5.2, 11, 15, 'rubber', { split: '' });
    m.ring('bezel', 'z', [8, 8.5], 5.6, 0.8, 10.5, 11, 'bright');
    m.disc('dial', 'z', [8, 8.5], 4.9, 10.9, 11, 'rubber', TEX + 'pressure_dial', 'north');
    m.box('socket', [7.2, 2, 12], [8.8, 3.6, 13.6], 'br', { split: '' });
    m.box('siphon_down', [7.6, 0, 12.4], [8.4, 2, 13.2], 'br', { split: '' });
    m.box('siphon_loop', [6, 0.6, 12.4], [7.6, 1.4, 13.2], 'br', { split: '' });
    return m;
  }
  // Synchroscope (E-327 type): square black ebonite case, the dial, the two sync lamps.
  function synchroscope() {
    const m = mk(TEX + 'synchro_dial');
    m.box('plate', [2, 2, 15], [14, 14, 16], 'frame', { split: '' });
    m.box('case', [2.6, 2.6, 11.4], [13.4, 14.6, 15], 'bak', { split: '' });
    m.box('rim', [2.4, 2.4, 11], [13.6, 13.6, 11.4], 'rubber', { split: '' });
    m.disc('dial', 'z', [8, 8], 5, 10.9, 11, 'rubber', TEX + 'synchro_dial', 'north');
    lamp(m, 'lamp_w', 4, 13.6, 11.4, 'l_off', false, 0.5);
    lamp(m, 'lamp_e', 12, 13.6, 11.4, 'l_off', false, 0.5);
    return m;
  }
  // Energy meter (SAZU type): black bakelite case, the face behind glass, the terminal cover with the lead seal
  // on its wire.
  function energyMeter() {
    const m = mk(TEX + 'meter_face');
    m.box('plate', [3, 1, 15], [13, 15, 16], 'frame', { split: '' });
    m.box('case', [3.6, 3.4, 11.2], [12.4, 14.6, 15], 'bak', { split: '' });
    m.plate('face', [4.4, 5.6, 11], [11.6, 13.8, 11.2], 'bak', 'north', TEX + 'meter_face', { hide: ['south'] });
    m.box('glass_rim', [4.2, 13.8, 10.9], [11.8, 14.2, 11.2], 'rubber', { split: '' });
    m.box('terminal_cover', [4, 1, 11.6], [12, 3.4, 15], 'bak', { split: '' });
    m.box('seal_wire', [7.9, 1.4, 11.5], [8.1, 3, 11.6], 'gal', { split: '' });
    m.box('seal', [7.5, 0.6, 11.2], [8.5, 1.4, 11.8], 'gm', { split: '' });
    return m;
  }
  // Recording gauge: round black case with a hinged glass door; the pen arm in red ink while a chart is fitted.
  function recorder(pen) {
    const m = mk('material/steel');
    m.box('plate', [3, 2, 15.4], [13, 15, 16], 'frame', { split: '' });
    m.prism('case', 'z', [8, 8.4], 5.6, 12, 15.4, 'rubber', { split: '' });
    m.ring('door', 'z', [8, 8.4], 5.8, 1, 11.2, 12, 'bright');
    m.box('latch', [13, 8, 11.4], [13.8, 9, 12], 'bright', { split: '' });
    if (pen) {
      m.box('pen_arm', [8, 8.2, 11.3], [12.6, 8.5, 11.45], 'bright', { split: '', rot: { axis: 'z', angle: 22.5, origin: [12.6, 8.4, 11.4] } });
      m.box('pen', [7.6, 9.7, 11.2], [8.2, 10.3, 11.45], 'red', { split: '' });
    }
    m.box('gland', [7.4, 2, 13], [8.6, 3, 14.2], 'br', { split: '' });
    return m;
  }
  function chartDrum() {
    const m = mk(TEX + 'chart');
    m.disc('chart', 'z', [8, 8.4], 4.8, 11.5, 11.9, 'rubber', TEX + 'chart', 'north');
    m.box('hub', [7.6, 8, 11.3], [8.4, 8.8, 11.5], 'bright', { split: '' });
    return m;
  }
  // Vibration transmitter: steel sensor body on a stud pad, the connector and the armoured cable down.
  function vibration() {
    const m = mk('material/steel');
    m.box('pad', [5, 5, 15], [11, 11, 16], 'frame', { split: '' });
    m.prism('body', 'z', [8, 8], 2.2, 11.2, 15, 'st', { split: '', ends: ['north'] });
    m.ring('flats', 'z', [8, 8], 2.4, 0.4, 14, 15, 'bright');
    m.box('connector', [7.2, 4.6, 12], [8.8, 5.9, 13.6], 'br', { split: '' });
    m.box('cable', [7.5, 0, 12.3], [8.5, 4.6, 13.3], 'rubber', { split: '' });
    return m;
  }
  // Neutron detector: ionisation chamber in a steel thimble, the brass head with the HV and signal connectors,
  // the cables out of the top.
  function neutron() {
    const m = mk('material/steel');
    m.prismY('thimble', [8, 9.8], 2.6, 0, 13, 'st', { ends: ['down'] });
    for (const y of [3, 8]) m.ringY(`band_${y}`, [8, 9.8], 2.8, 0.3, y, y + 0.6, 'gal');
    m.prismY('head', [8, 9.8], 3, 13, 15, 'br', { ends: ['up', 'down'] });
    m.box('conn_hv', [6.2, 15, 9], [7.4, 16, 10.6], 'red', { split: '' });
    m.box('conn_sig', [8.6, 15, 9], [9.8, 16, 10.6], 'bright', { split: '' });
    m.box('cable_hv', [6.5, 16, 9.3], [7.1, 16.5, 15], 'rubber', { split: '' });
    m.box('cable_sig', [8.9, 16, 9.3], [9.5, 16.5, 15], 'rubber', { split: '' });
    m.box('clamp', [5, 9, 12.4], [11, 10, 15], 'gal', { split: '' });
    return m;
  }
  // Document lectern: grey steel foot and post, the sloping shelf with its lip, a name plate; with a document:
  // the blue procedure folder open on the shelf.
  function lectern(doc) {
    const m = mk('plant/light');
    m.box('foot', [4, 0, 4], [12, 1, 12], 'frame', { split: '' });
    m.box('post', [7.2, 1, 7.2], [8.8, 10.6, 8.8], 'light', { split: '' });
    m.box('shelf', [2, 10.4, 3], [14, 11, 12.6], 'light', { split: '', rot: { axis: 'x', angle: -22.5, origin: [8, 10.8, 8] } });
    m.box('lip', [2, 9, 2.6], [14, 10, 3.4], 'light', { split: '', rot: { axis: 'x', angle: -22.5, origin: [8, 10.8, 8] } });
    m.plate('plate', [5.4, 6, 6.9], [10.6, 8.6, 7.2], 'light', 'north', TEX + 'legend', { hide: ['south'] });
    if (doc) m.box('folder', [3, 11, 3.6], [13, 11.5, 12.2], 'light', { split: '', face: { up: { tex: TEX + 'folder', uv: [0, 0, 16, 16] } }, rot: { axis: 'x', angle: -22.5, origin: [8, 10.8, 8] } });
    return m;
  }
  // Reactor controller: console cabinet with the core mimic face (lit), key, bar indicators, the frame.
  function reactorController() {
    const m = mk('plant/structure');
    m.box('body', [0, 0, 1], [16, 16, 16], 'frame', { split: '' });
    m.box('frame', [0, 0, 0.4], [16, 16, 1], 'en', { split: '' });
    m.box('glow_face', [1, 4, 0.2], [15, 15, 0.4], 'frame', { split: '', face: { north: { tex: TEX + 'rc_front', uv: [0, 0, 16, 16] } }, hide: ['south'] });
    m.box('ledge', [0.6, 1, -0.6], [15.4, 3.6, 0.4], 'en', { split: '' });
    m.box('key', [2, 2, -0.9], [3, 3, -0.6], 'br', { split: '' });
    for (const x of [6, 9, 12]) lamp(m, `btn_${x}`, x, 2.3, -0.6, 'l_off', false, 0.55);
    return m;
  }
  // Protection relay cabinet (EPZ-1636 type) 1x2: RAL 7035 with a glazed door frame, three rows of round A1
  // relays behind the glass (their target flags red when tripped), the blinker row (amber picked, red tripped),
  // the test blocks and the key at the bottom.
  function relayCabinet(state) {
    const m = mk('switchgear_cell/enamel');
    m.box('plinth', [0, 0, 0.6], [16, 1, 16], 'rubber', { split: '' });
    m.box('body', [0, 1, 1.4], [16, 32, 16], 'en', { split: 'y' });
    m.box('frame_t', [0.4, 29.6, 0.8], [15.6, 31.4, 1.4], 'en', { split: '' });
    m.box('frame_b', [0.4, 1.4, 0.8], [15.6, 6, 1.4], 'en', { split: '' });
    m.box('frame_w', [0.4, 6, 0.8], [1.6, 29.6, 1.4], 'en', { split: 'y' });
    m.box('frame_e', [14.4, 6, 0.8], [15.6, 29.6, 1.4], 'en', { split: 'y' });
    for (const [r, y] of [[0, 25.4], [1, 19.4], [2, 13.4]]) for (let i = 0; i < 3; i++) {
      const x = 4 + i * 4;
      m.box(`relay_${r}_${i}`, [x - 1.7, y - 1.7, 1.6], [x + 1.7, y + 1.7, 3], 'rubber', { split: '', face: { north: { tex: TEX + (state === 'tripped' && r === 1 && i === 0 ? 'relay_round_flag' : 'relay_round'), uv: [0, 0, 16, 16] } } });
    }
    for (let i = 0; i < 5; i++) {
      const x = 3.6 + i * 2.2, on = (state === 'picked' && i === 1) || (state === 'tripped' && i < 2);
      m.box((on ? 'glow_' : '') + `blinker_${i}`, [x - 0.6, 8.6, 1.2], [x + 0.6, 9.8, 1.6], on ? (state === 'tripped' ? 'l_r' : 'l_a') : 'l_off', { split: '' });
    }
    for (let i = 0; i < 6; i++) m.box(`test_${i}`, [2 + i * 2.1, 2.2, 0.4], [3.4 + i * 2.1, 4.4, 0.8], 'blk', { split: '' });
    m.box('handle', [14, 16, 0.2], [14.6, 20, 0.8], 'rubber', { split: '' });
    return m;
  }
  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    m.box(`${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber', { split: '' });
    m.box((glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { split: '', hide: ['south'] });
  }

  const BUILDS = {
    pushbutton: { model: () => pushbutton(false), cat: 'control' }, pushbutton_lit: { model: () => pushbutton(true), cat: 'control' },
    selector_switch: { model: selector, cat: 'control' },
    'selector_switch/knob': { model: selectorKnob, cat: 'control', java: 'selector_switch/partial/knob' },
    key_switch: { model: keySwitch, cat: 'control' }, 'key_switch/key': { model: key, cat: 'control', java: 'key_switch/partial/key' },
    indicator_lamp: { model: () => indicator(true), cat: 'control' }, indicator_lamp_off: { model: () => indicator(false), cat: 'control' },
    scram_button: { model: scram, cat: 'control' },
    gas_detector: { model: () => gasDetector('none'), cat: 'instruments' },
    gas_detector_warn: { model: () => gasDetector('warn'), cat: 'instruments' },
    gas_detector_alarm: { model: () => gasDetector('alarm'), cat: 'instruments' },
    instrument_pressure: { model: pressureGauge, cat: 'instruments' },
    instrument_synchroscope: { model: synchroscope, cat: 'instruments' },
    energy_meter: { model: energyMeter, cat: 'instruments' },
    recording_gauge: { model: () => recorder(true), cat: 'instruments' },
    recording_gauge_empty: { model: () => recorder(false), cat: 'instruments' },
    'recording_gauge/drum': { model: chartDrum, cat: 'instruments', java: 'recording_gauge/partial/drum' },
    instrument_vibration: { model: vibration, cat: 'instruments' },
    neutron_detector: { model: neutron, cat: 'instruments' },
    document_lectern: { model: () => lectern(false), cat: 'control' },
    document_lectern_document: { model: () => lectern(true), cat: 'control' },
    reactor_controller: { model: reactorController, cat: 'control' },
    relay_cabinet: { model: () => relayCabinet('normal'), cat: 'control' },
    relay_cabinet_picked: { model: () => relayCabinet('picked'), cat: 'control' },
    relay_cabinet_tripped: { model: () => relayCabinet('tripped'), cat: 'control' },
  };

  window.PNL = {
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
