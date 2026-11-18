// Boiling water reactor T2b (3x6x3), hand-authored for Blockbench; replaces the borrowed reactor_vessel_wall and
// steam_drum models. Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/bwr_reactor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); BWR.paint('<repo>')
//   BWR.build('<repo>', 'bwr_reactor'); PCART.save('<repo>', 'core', 'bwr_reactor')   — one model per call
//   BWR.preview('<repo>')                    — the whole reactor in one project, for looking only
//
// Lower segment (controller, segment=vessel): local -16..32 on every axis, the vessel axis on (8, 8).
// The vessel stands on its support skirt over the control rod drive housings, which in a BWR enter from below;
// recirculation outlets leave east and west at mid-height, the jet pump inlets come in from the south, feedwater
// from the north. A service deck at y 18 rings the vessel; the local desk stands on the floor on the north side.
// Upper segment (segment=separators, anchor three blocks up): the steam dome, the main closure flange with its
// studs and the insulated top head; four main steam lines run south through their isolation valves, and two
// safety relief valves sit on the outer lines with tailpipes down to the suppression pool.
// Points used by code (BwrReactorBlockEntity.clientTick): relief tailpipe ends (-6, -13, 25) and (22, -13, 25),
// the safety relief valve bonnets (-6, 61, 25) and (22, 61, 25) in controller coordinates.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/bwr_reactor';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Cast-in-place concrete: board-formed, tie holes on a 0.75 m grid, a few dark pours and pits.
  T.concrete = () => {
    const im = img(64, 64), r = rng(301), n = vnoise(r, 64, 64, 16);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      let c = add([150, 148, 140], (n(x, y) - 0.5) * 22 + (r() - 0.5) * 10);
      if (y % 16 === 15) c = add(c, -14);
      put(im, x, y, c);
    }
    for (let k = 0; k < 60; k++) put(im, r() * 64, r() * 64, [112, 110, 104]);
    for (const [x, y] of [[12, 6], [44, 6], [12, 38], [44, 38]]) { rect(im, x, y, 2, 2, [92, 90, 86]); put(im, x, y, [70, 68, 66]); }
    return im;
  };
  // Reflective metal insulation: stainless cassettes 1 m square, a raised seam with toggle latches on every joint.
  T.rmi = () => {
    const im = img(32, 32), r = rng(311), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([178, 182, 184], (n(x, y) - 0.5) * 26 + (r() - 0.5) * 5 + (y < 16 ? 4 : 0));
      if (x === 0 || y === 0) c = [206, 210, 212];
      if (x === 1 || y === 1) c = [118, 122, 126];
      if (x === 31 || y === 31) c = [142, 146, 150];
      put(im, x, y, c);
    }
    for (const [x, y] of [[15, 0], [0, 15]]) {
      rect(im, x, y, 3, 2, [96, 98, 100]); put(im, x, y, [220, 222, 224]);
    }
    for (let k = 0; k < 5; k++) { const x = 4 + r() * 22, y = 4 + r() * 22; put(im, x, y, [150, 154, 158]); put(im, x + 1, y, [200, 204, 206]); }
    return im;
  };
  // Top head insulation seen from above: radial cassettes round a central vent boss.
  T.head = () => {
    const im = img(64, 64), r = rng(321);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const dx = x + 0.5 - 32, dy = y + 0.5 - 32, d = Math.hypot(dx, dy) / 32, a = Math.atan2(dy, dx);
      let c = add([176, 180, 182], (r() - 0.5) * 6 - d * 18);
      const seg = ((a / (Math.PI * 2)) * 12 + 12) % 1;
      if (seg < 0.04) c = [120, 124, 128];
      for (const ring of [0.36, 0.68]) if (Math.abs(d - ring) < 0.025) c = [124, 128, 132];
      if (d > 0.96) c = add(c, -30);
      if (d < 0.14) c = add([92, 96, 100], (r() - 0.5) * 6);
      put(im, x, y, c);
    }
    return im;
  };
  // Open steel floor grating: load bars every 2 px, cross rods every 8, the dark under-floor between.
  T.grating = () => {
    const im = img(32, 32), r = rng(331);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = [26, 27, 28];
      if (x % 2 === 0) c = add([92, 94, 96], (r() - 0.5) * 14);
      if (y % 8 === 0) c = add([104, 106, 108], (r() - 0.5) * 10);
      put(im, x, y, c);
    }
    return im;
  };
  // Safety yellow on handrails and toe boards (GOST 12.4.026), worn through to steel on the top rail.
  T.yellow = () => {
    const im = img(32, 32), r = grain(im, [204, 164, 32], 341, 5, 8, 8);
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [86, 84, 80]);
    for (let y = 0; y < 32; y += 16) for (let x = 0; x < 32; x++) if (r() < 0.2) put(im, x, y, [128, 120, 96]);
    return im;
  };
  // Painted steel of the support skirt, deck frame and columns: Soviet machinery grey-green.
  T.frame = () => {
    const im = img(32, 32), r = grain(im, [86, 98, 92], 351, 4, 6, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [104, 72, 50]);
    return im;
  };
  // Stainless process pipe with a faint weld every metre and a lengthwise polish.
  T.pipe = () => {
    const im = img(32, 32), r = rng(361);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([150, 154, 158], Math.sin(x * 0.4) * 5 + (r() - 0.5) * 6);
      if (y === 0) c = [118, 116, 110];
      if (y === 1) c = [174, 170, 160];
      put(im, x, y, c);
    }
    return im;
  };
  // Forged and machined closure flange, studs and nuts: dark oiled steel with tool marks.
  T.forging = () => {
    const im = img(32, 32), r = rng(371), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add([96, 98, 102], (y % 3 === 0 ? -8 : 0) + (n(x, y) - 0.5) * 18 + (r() - 0.5) * 6));
    }
    return im;
  };

  // Atlas of one-off parts (64 px, 4 px per uv unit): desk face, its lamps, the vessel nameplate and pipe bands.
  const A = {
    desk: [0, 0, 40, 16], lamps_off: [0, 16, 40, 4], lamps_on: [0, 20, 40, 4], nameplate: [40, 0, 16, 8],
    band_steam: [40, 8, 8, 4], band_water: [48, 8, 8, 4], scram: [56, 0, 8, 8], valve_tag: [40, 12, 8, 4],
  };
  T.parts = () => {
    const im = img(64, 64), r = rng(381);
    // desk face: a grey enamel panel with a core mimic (vessel, recirculation loops, steam lines) and four meters
    for (let y = 0; y < 16; y++) for (let x = 0; x < 40; x++) {
      let c = add([150, 156, 150], (r() - 0.5) * 6);
      if (x === 0 || y === 0) c = [180, 186, 180];
      if (x === 39 || y === 15) c = [96, 100, 96];
      put(im, x, y, c);
    }
    const line = (x0, y0, x1, y1, c) => { for (let t = 0; t <= 1; t += 0.02) put(im, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, c); };
    rect(im, 4, 3, 5, 9, [60, 66, 72]); rect(im, 5, 4, 3, 4, [196, 120, 40]);          // vessel with the core
    line(9, 4, 16, 4, [190, 40, 34]); line(16, 4, 16, 2, [190, 40, 34]);               // main steam, red
    line(9, 10, 14, 10, [40, 140, 70]); line(14, 10, 14, 13, [40, 140, 70]); line(2, 13, 14, 13, [40, 140, 70]); line(2, 13, 2, 9, [40, 140, 70]);
    line(2, 9, 4, 9, [40, 140, 70]);                                                    // recirculation loop, green
    for (let k = 0; k < 4; k++) {
      const x0 = 19 + k * 5;
      rect(im, x0, 2, 4, 5, [24, 24, 22]); rect(im, x0 + 0.5, 2.5, 3, 3, [226, 220, 196]);
      put(im, x0 + 1 + (k % 2), 4, [30, 30, 30]); put(im, x0 + 2, 5, [190, 40, 30]);
    }
    for (let k = 0; k < 6; k++) { rect(im, 19 + k * 3.4, 10, 2, 3, [44, 46, 48]); put(im, 19.5 + k * 3.4, 10, [120, 122, 124]); }
    // lamp row, off and lit: green, green, amber, red, white
    const lamp = (oy, on) => {
      for (let y = 0; y < 4; y++) for (let x = 0; x < 40; x++) put(im, x, oy + y, add([132, 138, 132], (r() - 0.5) * 6));
      const cols = [[60, 220, 90], [60, 220, 90], [255, 176, 40], [255, 60, 40], [240, 240, 220]];
      cols.forEach((c, k) => {
        const x0 = 4 + k * 7;
        rect(im, x0, oy + 1, 3, 2, on ? c : mix(c, [30, 30, 30], 0.75));
        if (on) put(im, x0, oy + 1, [255, 255, 240]);
      });
    };
    lamp(16, false); lamp(20, true);
    // cast nameplate: dark ground, raised border and lettering lines
    for (let y = 0; y < 8; y++) for (let x = 0; x < 16; x++) {
      let c = add([62, 64, 70], (r() - 0.5) * 6);
      if (x === 0 || y === 0 || x === 15 || y === 7) c = [168, 170, 172];
      put(im, 40 + x, y, c);
    }
    for (const [y, a, b] of [[2, 3, 12], [4, 2, 13], [5, 5, 10]]) for (let x = a; x <= b; x++) if (r() < 0.7) put(im, 40 + x, y, [196, 198, 200]);
    // identification bands, GOST 14202: steam red with a white arrow, water green
    for (let y = 0; y < 4; y++) for (let x = 0; x < 8; x++) {
      put(im, 40 + x, 8 + y, add([170, 36, 30], (r() - 0.5) * 8));
      put(im, 48 + x, 8 + y, add([40, 128, 64], (r() - 0.5) * 8));
    }
    for (const [x, y] of [[2, 1], [3, 1], [4, 1], [5, 1], [4, 0], [4, 2]]) { put(im, 40 + x, 8 + y + 0.5, [236, 236, 230]); put(im, 48 + x, 8 + y + 0.5, [236, 236, 230]); }
    // mushroom scram button on a yellow collar
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
      put(im, 56 + x, y, d < 2.6 ? add([200, 30, 26], (y < 3 ? 30 : 0) + (r() - 0.5) * 8) : d < 3.9 ? [214, 172, 30] : [132, 138, 132]);
    }
    // valve tag: yellow plate with black stencil
    for (let y = 0; y < 4; y++) for (let x = 0; x < 8; x++) put(im, 40 + x, 12 + y, y > 0 && y < 3 && x > 0 && x < 7 && r() < 0.5 ? [30, 30, 28] : [210, 170, 34]);
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    concrete: { tex: 'bwr_reactor/concrete', P: 32 }, rmi: { tex: 'bwr_reactor/rmi', P: 16 }, grating: { tex: 'bwr_reactor/grating', P: 16 },
    yellow: { tex: 'bwr_reactor/yellow', P: 16 }, frame: { tex: 'bwr_reactor/frame', P: 16 }, pipe: { tex: 'bwr_reactor/pipe', P: 16 },
    forging: { tex: 'bwr_reactor/forging', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'bwr_reactor/parts', size: 64, rects: A } }),
    { particle: 'bwr_reactor/rmi' });

  // Upright 16-gon of any height: like Model.prismY, but cut at every block line so the texture tiles per block
  // (P = 16) and each facet takes its own slice. o.cap = radial texture on the top, o.bottom keeps the underside.
  const TAN16 = Math.tan(Math.PI / 16);
  function cylY(m, name, c, r, y0, y1, mat, o = {}) {
    const h = r * TAN16, w = 2 * h, k = 16 / (2 * r), tex = m.mat[mat].tex;
    const cuts = [y0];
    for (let y = Math.floor(y0 / 16) * 16 + 16; y < y1; y += 16) cuts.push(y);
    cuts.push(y1);
    let facet = o.seed || 0, n = 0;
    for (let s = 0; s + 1 < cuts.length; s++) {
      const a = cuts[s], b = cuts[s + 1], base = Math.floor(a / 16) * 16, last = s + 2 === cuts.length;
      const boxes = [];
      for (const ang of [0, 22.5, 45, -22.5, -45]) boxes.push([true, ang]);
      for (const ang of [0, 22.5, -22.5]) boxes.push([false, ang]);
      boxes.forEach(([wide, ang], i) => {
        const top = b + (last && o.cap ? i * 0.01 : 0);
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, top, c[1] + h] : [c[0] + h, top, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else if (d === 'up' && last && o.cap) {
            face.up = { tex: o.cap, uv: [(f[0] - c[0] + r) * k, (f[2] - c[1] + r) * k, (t[0] - c[0] + r) * k, (t[2] - c[1] + r) * k] };
          } else if (d === 'down' && s === 0 && o.bottom) {
            face.down = { tex, uv: [0, 0, w, w] };
          } else hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }
  // Ring of n small boxes round the vertical axis (studs, nuts, bolts on a flange).
  function boltCircle(m, name, c, R, n, y0, y1, s, mat) {
    for (let i = 0; i < n; i++) {
      const a = (i + 0.5) / n * Math.PI * 2, x = c[0] + R * Math.cos(a), z = c[1] + R * Math.sin(a);
      m.box(`${name}_${i}`, [x - s / 2, y0, z - s / 2], [x + s / 2, y1, z + s / 2], mat, { split: '' });
    }
  }
  // Square deck with a hole for the vessel: grating on a frame, toe boards and a two-rail handrail on the outside.
  function deck(m, name, y, hole, rail) {
    const [h0, h1] = hole;
    const G = (n, f, t) => m.box(`${name}_${n}`, f, t, 'grating', { split: 'xz' });
    G('n', [-16, y, -16], [32, y + 0.6, h0]);
    G('s', [-16, y, h1], [32, y + 0.6, 32]);
    G('w', [-16, y, h0], [h0, y + 0.6, h1]);
    G('e', [h1, y, h0], [32, y + 0.6, h1]);
    m.box(`${name}_beam_n`, [-16, y - 1.4, -16], [32, y, -15], 'frame');
    m.box(`${name}_beam_s`, [-16, y - 1.4, 31], [32, y, 32], 'frame');
    m.box(`${name}_beam_w`, [-16, y - 1.4, -15], [-15, y, 31], 'frame');
    m.box(`${name}_beam_e`, [31, y - 1.4, -15], [32, y, 31], 'frame');
    if (!rail) return;
    for (const side of rail) {
      const [f, t, axis] = {
        n: [[-16, 0, -16], [32, 0, -15.5], 'x'], s: [[-16, 0, 31.5], [32, 0, 32], 'x'],
        w: [[-16, 0, -16], [-15.5, 0, 32], 'z'], e: [[31.5, 0, -16], [32, 0, 32], 'z'],
      }[side];
      m.box(`${name}_toe_${side}`, [f[0], y + 0.6, f[2]], [t[0], y + 1.6, t[2]], 'yellow');
      for (const ry of [y + 5, y + 9.5]) m.box(`${name}_rail_${side}_${ry}`, [f[0], ry, f[2]], [t[0], ry + 0.6, t[2]], 'yellow');
      for (let p = -16; p <= 31.5; p += 12) {
        const px = axis === 'x' ? Math.min(p, 31.5) : f[0], pz = axis === 'z' ? Math.min(p, 31.5) : f[2];
        m.box(`${name}_post_${side}_${p}`, [px, y + 0.6, pz], [px + 0.5, y + 10.1, pz + 0.5], 'yellow', { split: '' });
      }
    }
  }
  // Pipe along x or z: prism with a weld band at each end.
  function pipeH(m, name, axis, c, r, a0, a1, mat) {
    m.prism(name, axis, c, r, a0, a1, mat || 'pipe', { ends: [] });
  }
  function pipeV(m, name, x, z, r, y0, y1, mat) {
    cylY(m, name, [x, z], r, y0, y1, mat || 'pipe');
  }

  // ------------------------------------------------------------------ lower segment: vessel, skirt, drives
  const VX = 8, VZ = 8, VR = 13;
  function vessel(lit) {
    const m = mk();
    m.box('floor', [-16, -16, -16], [32, -13, 32], 'concrete', { hide: ['down'] });
    // pedestal under the skirt with an access opening to the north, control rod drive housings inside
    m.box('pedestal_w', [-4, -13, -4], [0, -6, 20], 'concrete', { hide: ['down'] });
    m.box('pedestal_e', [16, -13, -4], [20, -6, 20], 'concrete', { hide: ['down'] });
    m.box('pedestal_s', [0, -13, 16], [16, -6, 20], 'concrete', { hide: ['down'] });
    m.box('pedestal_lintel', [0, -8, -4], [16, -6, 0], 'concrete');
    for (let i = 0; i < 5; i++) for (let j = 0; j < 5; j++) {
      const x = 2.5 + i * 2.75, z = 2.5 + j * 2.75;
      m.box(`crd_${i}_${j}`, [x - 0.5, -13, z - 0.5], [x + 0.5, -2, z + 0.5], 'pipe', { split: '' });
      m.box(`crd_flange_${i}_${j}`, [x - 0.8, -11.5, z - 0.8], [x + 0.8, -10.8, z + 0.8], 'forging', { split: '' });
    }
    // hydraulic control unit lines: insert and withdraw headers running out to the north
    m.box('hcu_header_a', [1, -12.6, -2], [15, -12, -1.4], 'red', { split: '' });
    m.box('hcu_header_b', [1, -12.6, -3.2], [15, -12, -2.6], 'pipe', { split: '' });
    cylY(m, 'skirt', [VX, VZ], 11, -6, 2, 'frame');
    m.box('skirt_ring', [-3.5, -6.5, -3.5], [19.5, -6, 19.5], 'forging', { split: '' });
    // bottom head in three steps, then the cylindrical shell under its insulation
    cylY(m, 'bottom_head_a', [VX, VZ], 9, 0, 1.5, 'rmi', { bottom: true });
    cylY(m, 'bottom_head_b', [VX, VZ], 11.2, 1.5, 3, 'rmi', { bottom: true, seed: 3 });
    cylY(m, 'bottom_head_c', [VX, VZ], 12.6, 3, 5, 'rmi', { bottom: true, seed: 5 });
    cylY(m, 'shell', [VX, VZ], VR, 5, 32, 'rmi', { seed: 7 });
    cylY(m, 'band_a', [VX, VZ], VR + 0.3, 12, 12.8, 'forging');
    cylY(m, 'band_b', [VX, VZ], VR + 0.3, 26, 26.8, 'forging');
    m.decal('nameplate', [5, 20, -5.3], [11, 23, -4.8], 'forging', 'north', 'p.nameplate', { hide: ['south'] });

    // recirculation outlet nozzles east and west, elbowing down through the floor to the pumps
    for (const [s, x0, x1, vx] of [['e', 20.5, 28, 26], ['w', -12, -4.5, -10]]) {
      m.box(`recirc_nozzle_${s}`, s === 'e' ? [20, 4.5, 4.5] : [-6, 4.5, 4.5], s === 'e' ? [22, 11.5, 11.5] : [-4, 11.5, 11.5], 'forging', { split: '' });
      pipeH(m, `recirc_out_${s}`, 'x', [8, 8], 2.4, x0, x1);
      pipeV(m, `recirc_down_${s}`, vx, 8, 2.4, -13, 10.4);
      m.box(`recirc_elbow_${s}`, [vx - 2.4, 5.6, 5.6], [vx + 2.4, 10.4, 10.4], 'pipe', { split: '' });
      m.decal(`recirc_band_${s}`, [vx - 2.6, 0, 5.4], [vx + 2.6, 2, 10.6], 'pipe', s === 'e' ? 'east' : 'west', 'p.band_water');
      m.box(`recirc_hanger_${s}`, [vx - 0.4, 10.4, 7.6], [vx + 0.4, 17.4, 8.4], 'frame', { split: '' });
    }
    // jet pump inlet risers from the south: two, each from the floor into a nozzle
    for (const x of [3, 13]) {
      m.box(`jp_nozzle_${x}`, [x - 1.6, 14.4, 19], [x + 1.6, 17.6, 22.4], 'forging', { split: '' });
      pipeH(m, `jp_in_${x}`, 'z', [x, 16], 1.5, 21.5, 27);
      pipeV(m, `jp_up_${x}`, x, 26, 1.5, -13, 17.5);
      m.box(`jp_elbow_${x}`, [x - 1.5, 14.5, 24.5], [x + 1.5, 17.5, 27.5], 'pipe', { split: '' });
    }
    // feedwater from the north into two nozzles under the steam dome
    for (const x of [1, 15]) {
      const zs = 8 - Math.sqrt(VR * VR - (x - VX) ** 2);
      m.box(`fw_nozzle_${x}`, [x - 1.8, 26.2, zs - 1.5], [x + 1.8, 29.8, zs + 1], 'forging', { split: '' });
      pipeH(m, `fw_${x}`, 'z', [x, 28], 1.6, -16, zs - 1.5);
      m.decal(`fw_band_${x}`, [x - 1.8, 26.2, -12], [x + 1.8, 29.8, -9], 'pipe', 'up', 'p.band_water', { hide: ['down'] });
    }
    // relief tailpipes from the safety relief valves above, down through the deck into the suppression pool
    for (const x of [-6, 22]) {
      pipeV(m, `srv_tail_${x}`, x, 25, 1.2, -13, 32);
      m.box(`srv_quencher_${x}`, [x - 2, -13, 23], [x + 2, -12, 27], 'pipe', { split: '' });
    }
    // corner columns and the service deck at y 18
    for (const [x, z] of [[-16, -16], [30, -16], [-16, 30], [30, 30]]) m.box(`column_${x}_${z}`, [x, -13, z], [x + 2, 16.6, z + 2], 'frame');
    deck(m, 'deck', 18, [-6.5, 22.5], ['n', 'e', 'w']);
    // ladder up the west side
    for (const z of [10, 13]) m.box(`ladder_rail_${z}`, [-15.6, -13, z], [-15, 18, z + 0.5], 'yellow');
    for (let y = -11; y < 18; y += 2.5) m.box(`ladder_rung_${y}`, [-15.5, y, 10.2], [-15.1, y + 0.4, 13.3], 'frame', { split: '' });

    // local desk on the north side: sloped face with the mimic, lamp strip and the scram button
    m.box('desk_body', [-2, -13, -15], [18, -5, -9], 'frame', { hide: ['down'] });
    m.decal('desk_face', [-2, -5, -14], [18, -4, -9], 'frame', 'up', 'p.desk', { hide: ['down'], rot: { axis: 'x', angle: -22.5, origin: [8, -5, -14] } });
    m.decal(lit ? 'glow_desk_lamps' : 'desk_lamps', [-2, -9, -15.2], [18, -7, -15], 'frame', 'north', lit ? 'p.lamps_on' : 'p.lamps_off', { hide: ['south'] });
    m.decal('desk_scram', [14, -12, -15.6], [16, -10, -15], 'red', 'north', 'p.scram', { hide: ['south'] });
    return m;
  }

  // ------------------------------------------------------------------ upper segment: dome, head, steam lines
  function separators() {
    const m = mk();
    cylY(m, 'shell', [VX, VZ], VR, -16, 10, 'rmi', { seed: 11 });
    cylY(m, 'band', [VX, VZ], VR + 0.3, -6, -5.2, 'forging');
    // main closure: vessel flange, head flange, 24 studs with their nuts
    cylY(m, 'flange_v', [VX, VZ], 15, 10, 12, 'forging', { bottom: true });
    cylY(m, 'flange_h', [VX, VZ], 15, 12, 14, 'forging');
    boltCircle(m, 'stud', [VX, VZ], 14.1, 24, 14, 15.6, 0.9, 'forging');
    boltCircle(m, 'nut', [VX, VZ], 14.1, 24, 14, 14.9, 1.5, 'forging');
    // insulated top head in steps, lifting lugs, the head vent
    cylY(m, 'head_a', [VX, VZ], 13.4, 14, 17, 'rmi', { cap: 'bwr_reactor/head', seed: 13 });
    cylY(m, 'head_b', [VX, VZ], 11.6, 17, 20, 'rmi', { cap: 'bwr_reactor/head', seed: 17 });
    cylY(m, 'head_c', [VX, VZ], 9, 20, 22.5, 'rmi', { cap: 'bwr_reactor/head', seed: 19 });
    cylY(m, 'head_d', [VX, VZ], 5.6, 22.5, 24.2, 'rmi', { cap: 'bwr_reactor/head', seed: 23 });
    for (const [x, z] of [[0, 8], [16, 8], [8, 0], [8, 16]]) {
      const ax = x === 8 ? 'x' : 'z';
      m.box(`lug_${x}_${z}`, ax === 'z' ? [x - 0.6, 20, z - 2] : [x - 2, 20, z - 0.6], ax === 'z' ? [x + 0.6, 24, z + 2] : [x + 2, 24, z + 0.6], 'forging', { split: '' });
    }
    pipeV(m, 'vent', 8, 8, 1.1, 24.2, 28);
    pipeH(m, 'vent_run', 'x', [27, 8], 0.9, 8, 26);
    pipeV(m, 'vent_down', 26, 8, 0.9, 10, 27.9);
    m.box('vent_valve', [24.6, 18, 6.6], [27.4, 21, 9.4], 'gm', { split: '' });
    m.wheelY('vent_wheel', 26, 22.2, 8, 1.6, 'red');

    // four main steam lines leaving south, each with its isolation valve and air operator
    for (const x of [-1, 4, 12, 17]) {
      const zs = 8 + Math.sqrt(VR * VR - (x - VX) ** 2) - 0.6;
      m.box(`msl_nozzle_${x}`, [x - 2.2, -0.2, zs - 1], [x + 2.2, 4.2, zs + 1.6], 'forging', { split: '' });
      pipeH(m, `msl_${x}`, 'z', [x, 2], 1.8, zs + 1.6, 32);
      m.box(`msiv_body_${x}`, [x - 2.3, -0.3, 26], [x + 2.3, 4.3, 29.5], 'ci', { split: '' });
      m.box(`msiv_bonnet_${x}`, [x - 1.3, 4.3, 26.7], [x + 1.3, 6.5, 28.8], 'ci', { split: '' });
      m.box(`msiv_yoke_${x}`, [x - 0.3, 6.5, 27.4], [x + 0.3, 9, 28.1], 'bright', { split: '' });
      m.box(`msiv_actuator_${x}`, [x - 1.6, 9, 26.1], [x + 1.6, 12.6, 29.4], 'frame', { split: '' });
      m.decal(`msl_band_${x}`, [x - 1.9, 0.1, 30], [x + 1.9, 3.9, 31.5], 'pipe', 'up', 'p.band_steam', { hide: ['down'] });
    }
    // safety relief valves on the outer lines: body on a riser, spring bonnet, tailpipe down to the pool
    for (const [x, line] of [[-6, -1], [22, 17]]) {
      pipeH(m, `srv_branch_${x}`, 'x', [2, 25], 1.1, Math.min(x, line), Math.max(x, line));
      m.box(`srv_body_${x}`, [x - 1.8, 0, 23.2], [x + 1.8, 4, 26.8], 'gm', { split: '' });
      m.box(`srv_bonnet_${x}`, [x - 1, 4, 24], [x + 1, 11, 26], 'gm', { split: '' });
      m.box(`srv_cap_${x}`, [x - 0.6, 11, 24.4], [x + 0.6, 12.4, 25.6], 'bright', { split: '' });
      m.box(`srv_solenoid_${x}`, [x + 1, 6, 24.2], [x + 2.6, 8.4, 25.8], 'frame', { split: '' });
      m.decal(`srv_tag_${x}`, [x - 1, 8.6, 23.6], [x + 1, 9.6, 24], 'gm', 'north', 'p.valve_tag', { hide: ['south'] });
      pipeV(m, `srv_tail_${x}`, x, 25, 1.2, -16, 0);
    }
    // corner columns from the deck below, the stud tensioning platform at the flange
    for (const [x, z] of [[-16, -16], [30, -16], [-16, 30], [30, 30]]) m.box(`column_${x}_${z}`, [x, -16, z], [x + 2, 8.6, z + 2], 'frame');
    deck(m, 'platform', 10, [-7.5, 23.5], ['n', 'w', 'e']);
    return m;
  }

  const BUILDS = {
    bwr_reactor: { model: () => vessel(false) },
    bwr_reactor_lit: { model: () => vessel(true) },
    bwr_reactor_separators: { model: () => separators() },
  };

  window.BWR = {
    paint,
    BUILDS,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    // Whole reactor for inspection only (never exported): separators three blocks up.
    preview(root) {
      return PCART.realize(root, vessel(true).merge(separators(), 0, 48, 0, 'top_'), 'bwr_reactor_preview');
    },
  };
})();
