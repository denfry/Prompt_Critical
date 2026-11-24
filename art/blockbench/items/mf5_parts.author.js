// Item icons of the machine parts of GDD 18 §18.8 (MF5, assembly by machine spec): rolling bearing, coupling,
// fastener kit, spring, pulley, machine bed, pump casing, valve body, flange, end cover, rotor core, field coil.
// Flat 16 px icons in the style of the generated part icons (art/tools/tex_ztools.py spur_gear and the rest): the
// same disc and line rasterisation, the top-left edge of every shape lit, the bottom-right edge shaded, then a
// dark outline round the opaque pixels.
// Run headless: node art/tools/pcart_headless.js <repo> items/mf5_parts.author.js MF5I --paint
// Writes textures/item/<id>.png and models/item/<id>.json (item/generated).
(function () {
  const path = require('path');
  const fs = require('fs');
  const { img, put, writePng } = PCART;

  const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const px = (a, x, y, c) => { if (x >= 0 && y >= 0 && x < 16 && y < 16) put(a, x, y, hex(c)); };
  const box = (a, x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) px(a, x, y, c); };
  // Bresenham, as tex_plant.line.
  function line(a, x0, y0, x1, y1, c) {
    const dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
    let err = dx + dy;
    for (;;) {
      px(a, x0, y0, c);
      if (x0 === x1 && y0 === y1) break;
      const e2 = 2 * err;
      if (e2 >= dy) { err += dy; x0 += sx; }
      if (e2 <= dx) { err += dx; y0 += sy; }
    }
  }
  // Pixels whose centre-free distance from (cx, cy) is in [r0, r), as tex_plant.circ.
  function circ(a, cx, cy, r, c, r0) {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x - cx, y - cy);
      if (d >= (r0 === undefined ? -1 : r0) && d < r) px(a, x, y, c);
    }
  }
  const at = (cx, cy, rad, ang) => [Math.round(cx + rad * Math.cos(ang)), Math.round(cy + rad * Math.sin(ang))];
  const opaque = (a, x, y) => x >= 0 && y >= 0 && x < 16 && y < 16 && a.d[(y * 16 + x) * 4 + 3] > 0;
  // tex_plant.lit and tex_plant.outline.
  function finish(a) {
    const m = [];
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) m.push(opaque(a, x, y));
    const on = (x, y) => x >= 0 && y >= 0 && x < 16 && y < 16 && m[y * 16 + x];
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      if (!on(x, y)) continue;
      const i = (y * 16 + x) * 4;
      let k = 1;
      if (!on(x, y - 1) || !on(x - 1, y)) k = 1.15;
      else if (!on(x, y + 1) || !on(x + 1, y)) k = 0.82;
      for (let c = 0; c < 3; c++) a.d[i + c] = Math.min(255, a.d[i + c] * k);
    }
    const out = img(16, 16);
    out.d.set(a.d);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      if (on(x, y)) continue;
      if (on(x + 1, y) || on(x - 1, y) || on(x, y + 1) || on(x, y - 1)) put(out, x, y, hex('#18191b'));
    }
    return out;
  }

  const I = {};
  // Deep-groove ball bearing face on: outer ring, the dark cage with eight bright balls, inner ring, bore.
  I.rolling_bearing = (a) => {
    circ(a, 7.5, 7.5, 7, '#8e9399');
    circ(a, 7.5, 7.5, 5.6, '#44484c');
    for (let k = 0; k < 8; k++) {
      const [x, y] = at(7.5, 7.5, 4.3, 2 * Math.PI * (k + 0.5) / 8);
      px(a, x, y, '#dde2e6');
    }
    circ(a, 7.5, 7.5, 3.2, '#a4aab0');
    circ(a, 7.5, 7.5, 1.9, '#1d1d1f');
  };
  // Flanged rigid coupling from the side: two shaft ends, two cast flanges bolted face to face.
  I.coupling = (a) => {
    box(a, 0, 6, 4, 9, '#a4aab0');
    box(a, 11, 6, 15, 9, '#a4aab0');
    box(a, 4, 2, 7, 13, '#7d8288');
    box(a, 8, 2, 11, 13, '#7d8288');
    line(a, 7, 2, 7, 13, '#4a4e52');
    for (const y of [3, 12]) { box(a, 4, y, 11, y, '#c8ccd0'); px(a, 3, y, '#c8ccd0'); px(a, 12, y, '#c8ccd0'); }
    box(a, 5, 6, 6, 9, '#6e7378');
    box(a, 9, 6, 10, 9, '#6e7378');
  };
  // Fastener kit: hex-head bolt across the icon with a nut run on, a flat washer beside.
  I.fastener_kit = (a) => {
    box(a, 1, 2, 4, 5, '#8e9399');
    px(a, 1, 2, '#c8ccd0'); px(a, 2, 2, '#c8ccd0');
    for (let i = 0; i < 7; i++) {
      const x = 5 + i, y = 5 + i;
      px(a, x, y, i % 2 ? '#7d8288' : '#b4b9be');
      px(a, x + 1, y, i % 2 ? '#6e7378' : '#a4aab0');
    }
    box(a, 10, 11, 14, 14, '#7d8288');
    px(a, 10, 11, '#c8ccd0'); px(a, 11, 11, '#c8ccd0');
    px(a, 12, 12, '#2a2c2e');
    circ(a, 4, 11.5, 3.2, '#a4aab0');
    circ(a, 4, 11.5, 1.3, '#1d1d1f');
  };
  // Helical compression spring standing up: bright front strands over dark back strands, ground ends flat.
  I.spring = (a) => {
    for (let k = 0; k < 4; k++) line(a, 11, 3 + 3 * k, 4, 5 + 3 * k, '#3e464e');
    for (let k = 0; k < 4; k++) line(a, 4, 2 + 3 * k, 11, 3 + 3 * k, '#8a98a4');
    box(a, 4, 1, 11, 1, '#6c7884');
    box(a, 4, 14, 11, 14, '#6c7884');
  };
  // Flat-belt pulley face on: crowned rim, six cast spokes, hub with a keyed bore.
  I.pulley = (a) => {
    circ(a, 7.5, 7.5, 7, '#7a7f84', 5.4);
    circ(a, 7.5, 7.5, 6.3, '#9aa0a5', 5.6);
    for (let k = 0; k < 6; k++) {
      const ang = 2 * Math.PI * k / 6 + 0.26;
      const [x0, y0] = at(7.5, 7.5, 2.2, ang), [x1, y1] = at(7.5, 7.5, 5.6, ang);
      line(a, x0, y0, x1, y1, '#6e7378');
    }
    circ(a, 7.5, 7.5, 2.6, '#8e9399');
    circ(a, 7.5, 7.5, 1.2, '#1d1d1f');
    px(a, 7, 5, '#1d1d1f');
  };
  // Cast-iron machine bed: long ribbed box in machine-tool enamel, two scraped ways on top, feet at the ends.
  I.machine_bed = (a) => {
    box(a, 1, 6, 14, 11, '#687a72');
    box(a, 2, 4, 5, 5, '#c0c4c8');
    box(a, 10, 4, 13, 5, '#c0c4c8');
    box(a, 6, 5, 9, 5, '#56665e');
    for (const x0 of [3, 7, 11]) box(a, x0, 8, x0 + 1, 9, '#2e3532');
    box(a, 1, 12, 3, 13, '#56665e');
    box(a, 12, 12, 14, 13, '#56665e');
  };
  // Volute pump casing face on: the scroll, the suction eye, the discharge branch up and to the right with its flange.
  I.pump_casing = (a) => {
    circ(a, 7, 9, 5.8, '#5f7a94');
    box(a, 9, 2, 12, 6, '#5f7a94');
    box(a, 8, 1, 13, 1, '#9fb4c6');
    box(a, 8, 2, 13, 2, '#4a6278');
    for (let t = 0; t < 1; t += 0.02) {
      const ang = Math.PI * (0.6 + 1.7 * t), rad = 3.2 + 1.8 * t;
      px(a, Math.round(7 + rad * Math.cos(ang)), Math.round(9 + rad * Math.sin(ang)), '#3e5266');
    }
    circ(a, 7, 9, 2.2, '#1d1d1f');
    for (const ang of [0.3, 1.9, 3.5, 4.9]) { const [x, y] = at(7, 9, 4.8, ang); px(a, x, y, '#c8ccd0'); }
  };
  // Cast-steel globe valve body: two end flanges, the round belly, the bonnet flange on top, no handwheel yet.
  I.valve_body = (a) => {
    box(a, 0, 4, 2, 12, '#8e9399');
    box(a, 13, 4, 15, 12, '#8e9399');
    box(a, 3, 6, 4, 10, '#6e7378');
    box(a, 11, 6, 12, 10, '#6e7378');
    circ(a, 7.5, 8.5, 4.4, '#6e7378');
    box(a, 6, 2, 9, 4, '#6e7378');
    box(a, 4, 1, 11, 2, '#8e9399');
    px(a, 5, 1, '#c8ccd0'); px(a, 10, 1, '#c8ccd0');
    px(a, 1, 5, '#c8ccd0'); px(a, 1, 11, '#c8ccd0'); px(a, 14, 5, '#c8ccd0'); px(a, 14, 11, '#c8ccd0');
    px(a, 6, 7, '#9aa0a5'); px(a, 7, 6, '#9aa0a5');
  };
  // Weld-neck flange face on: raised face round the bore, eight bolt holes on the pitch circle.
  I.flange = (a) => {
    circ(a, 7.5, 7.5, 7, '#8e9399');
    circ(a, 7.5, 7.5, 4.2, '#b4b9be');
    circ(a, 7.5, 7.5, 2.6, '#1d1d1f');
    for (let k = 0; k < 8; k++) { const [x, y] = at(7.5, 7.5, 5.6, 2 * Math.PI * (k + 0.5) / 8); px(a, x, y, '#2a2c2e'); }
  };
  // Bearing end cover: painted cast cap, machined spigot ring, the boss with its brass grease nipple, four bolts.
  I.end_cover = (a) => {
    circ(a, 7.5, 7.5, 6.8, '#687a72');
    circ(a, 7.5, 7.5, 5.2, '#56665e', 4.4);
    circ(a, 7.5, 7.5, 3, '#7c8e86');
    circ(a, 7.5, 7.5, 1.2, '#c9a14a');
    for (let k = 0; k < 4; k++) { const [x, y] = at(7.5, 7.5, 5.6, Math.PI / 4 + Math.PI * k / 2); px(a, x, y, '#c8ccd0'); }
  };
  // Squirrel-cage rotor core from the side: shaft through a stack of laminations, skewed bars, cast end rings.
  I.rotor_core = (a) => {
    box(a, 0, 7, 15, 8, '#b4b9be');
    box(a, 3, 3, 12, 12, '#5f666c');
    for (let x = 4; x <= 11; x += 2) box(a, x, 3, x, 12, '#727a80');
    for (let k = 0; k < 3; k++) line(a, 4 + 3 * k, 12, 6 + 3 * k, 3, '#a8adb2');
    box(a, 2, 3, 3, 12, '#c4c8cc');
    box(a, 12, 3, 13, 12, '#c4c8cc');
  };
  // Field coil: racetrack winding of copper strip, the window in the middle, two bands of insulating tape, two leads.
  I.field_coil = (a) => {
    box(a, 2, 3, 13, 13, '#b8733a');
    box(a, 1, 4, 14, 12, '#b8733a');
    for (const y of [4, 6, 10, 12]) box(a, 2, y, 13, y, '#9a5c2c');
    for (let y = 7; y <= 9; y++) for (let x = 5; x <= 10; x++) a.d[(y * 16 + x) * 4 + 3] = 0;
    box(a, 4, 3, 4, 13, '#d8cfa8');
    box(a, 11, 3, 11, 13, '#d8cfa8');
    box(a, 6, 0, 6, 2, '#d0884a');
    box(a, 9, 0, 9, 2, '#d0884a');
  };

  function paint(root) {
    const A = path.join(root, PCART.ASSETS);
    for (const [n, f] of Object.entries(I)) {
      const a = img(16, 16);
      f(a);
      writePng(finish(a), path.join(A, 'textures/item', n + '.png'));
      fs.writeFileSync(path.join(A, 'models/item', n + '.json'),
        JSON.stringify({ parent: 'minecraft:item/generated', textures: { layer0: 'promptcritical:item/' + n } }, null, '\t') + '\n');
    }
    return Object.keys(I).length;
  }

  window.MF5I = { paint, ids: [], exportAll: () => ({}), icons: Object.keys(I) };
})();
