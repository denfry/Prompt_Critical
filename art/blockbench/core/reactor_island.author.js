// Reactor island and fuel cycle pieces, hand-authored for Blockbench (replace the spec_plant.py,
// spec_fuelcycle.py and spec_oilgas.py placeholders): the PWR pressurizer (heaters off and on), the gas centrifuge
// and the low-temperature separation unit of the gas plant (warm and cold).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/reactor_island.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); RIS.paint('<repo>')
//   build + save each model in turn (RIS.build / RIS.save, ids in RIS.ids)
//
// Front to the north.
// pressurizer 1x1x1: the vessel in reflective metal insulation on its skirt, the spray line in at the head, the
//   three pilot-operated relief valves with the discharge line to the relief tank going east, the heater terminal
//   box at the foot with its contactor lamps (lit = heaters on, glow_ in pressurizer_lit). Relief steam leaves at
//   the head (PressurizerBlockEntity sends it at y 1.2 above the centre).
// gas_centrifuge 1x1x2 (y 0..32): the rotor casing of an aluminium-alloy tube on its motor housing and the
//   damper base, the top flange with the three UF6 lines (feed, product, tails) to the cascade headers.
// low_temperature_separator 3x3x3 (x, y, z -16..32): the gas/gas exchanger, the choke valve, the propane chiller
//   (kettle) and the three-phase low-temperature separator with its glycol boot, the inlet scrubber, on a skid
//   with the methanol injection pump. In the cold model the cold side (chiller, separator, cold piping) is under frost. Frost
//   particles at the chiller x 10, y -4, z -8; separator top at x -6, y 22, z 18.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Reflective metal insulation: stainless cassettes 8 px square with folded seams and buckles.
  T['reactor_island/rmi'] = () => {
    const im = img(32, 32), r = rng(3601), n = PCART.vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([188, 192, 196], (n(x, y) - 0.5) * 26 + (r() - 0.5) * 6);
      if (x % 16 === 0 || y % 8 === 0) c = [130, 134, 138];
      else if (x % 16 === 1 || y % 8 === 1) c = add(c, 18);
      put(im, x, y, c);
    }
    for (let y = 4; y < 32; y += 8) for (const x of [7, 23]) rect(im, x, y, 2, 1, [96, 98, 102]);
    return im;
  };
  T['reactor_island/rmi_top'] = () => {
    const im = img(16, 16), r = rng(3611);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8), a = Math.atan2(y + 0.5 - 8, x + 0.5 - 8);
      let c = add([184, 188, 192], (r() - 0.5) * 8);
      if (Math.abs(Math.sin(a * 4)) < 0.12 || Math.abs(d - 4.5) < 0.5) c = [134, 138, 142];
      put(im, x, y, c);
    }
    return im;
  };
  // Heater terminal box: grey enclosure with two rows of lamps, painted off.
  T['reactor_island/terminal'] = () => {
    const im = img(16, 16), r = grain(im, [150, 156, 150], 3621, 3, 3, 4);
    rect(im, 1, 1, 14, 3, [226, 222, 200]); rect(im, 2, 2, 10, 1, [60, 60, 60]);
    for (let x = 2; x < 14; x += 3) { rect(im, x, 7, 2, 2, [40, 40, 40]); rect(im, x, 11, 2, 2, [40, 40, 40]); }
    return im;
  };
  // Centrifuge casing: anodised aluminium alloy tube, faint lengthwise drawing marks, a red serial band.
  T['reactor_island/casing'] = () => {
    const im = img(32, 32), r = rng(3631);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([170, 176, 182], (x % 4 === 0 ? -8 : 0) + (r() - 0.5) * 6));
    return im;
  };
  // UF6 process lines: GOST 14202 for gases is yellow; UF6 lines are traced and lagged — yellow cladding.
  T['reactor_island/uf6'] = () => { const im = img(16, 16), r = grain(im, [206, 170, 66], 3641, 3, 4, 4); for (let x = 0; x < 16; x += 6) rect(im, x, 0, 1, 16, [150, 120, 40]); return im; };
  // Skid steel painted in light grey-green, gas plant practice.
  T['reactor_island/skid'] = () => { const im = img(32, 32), r = grain(im, [124, 138, 128], 3651, 3, 5, 8); for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [86, 74, 60]); return im; };
  // Vessel paint, grey.
  T['reactor_island/vessel'] = () => { const im = img(32, 32), r = grain(im, [150, 154, 156], 3661, 3, 5, 8); for (let x = 0; x < 32; x++) put(im, x, 15, [118, 122, 124]); return im; };
  // Frost on the cold side: white crystalline crust over the paint, thicker in patches.
  T['reactor_island/frost'] = () => {
    const im = img(32, 32), r = rng(3671), n = PCART.vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = n(x, y);
      put(im, x, y, add(mix([196, 206, 214], [238, 244, 248], t), (r() - 0.5) * 16 + (r() < 0.08 ? 20 : 0)));
    }
    return im;
  };
  T['reactor_island/frost_top'] = () => {
    const im = img(16, 16), r = rng(3681);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([232, 238, 244], (r() - 0.5) * 14));
    return im;
  };
  T['reactor_island/vessel_top'] = () => {
    const im = img(16, 16), r = rng(3691);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
      put(im, x, y, add(d > 6.6 ? [124, 128, 130] : [152, 156, 158], (r() - 0.5) * 8));
    }
    return im;
  };

  const MAT = {
    rmi: { tex: 'reactor_island/rmi', P: 16 }, term: { tex: 'reactor_island/terminal', P: 16 }, cas: { tex: 'reactor_island/casing', P: 16 },
    uf6: { tex: 'reactor_island/uf6', P: 16 }, skid: { tex: 'reactor_island/skid', P: 16 }, ves: { tex: 'reactor_island/vessel', P: 16 },
    frost: { tex: 'reactor_island/frost', P: 16 }, con: { tex: 'candu_reactor/concrete', P: 32 }, yel: { tex: 'candu_reactor/yellow', P: 16 },
    ps: { tex: 'process_units/pipe_steam', P: 16 }, pg: { tex: 'process_units/pipe_gas', P: 16 },
  };
  const ATL = { pz: { tex: 'reactor_island/terminal', size: 16, rects: { box: [0, 0, 16, 16] } } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATL), { particle: particle || 'reactor_island/rmi' });

  // Upright cylinder over several blocks: prismY per block-high course, side texture rows matched to the course.
  function tower(m, name, c, r, y0, y1, mat, tex, cap) {
    let k = 0;
    for (let b = Math.floor(y0 / 16); b * 16 < y1; b++) {
      const lo = Math.max(y0, b * 16), hi = Math.min(y1, b * 16 + 16);
      if (hi <= lo) continue;
      const n0 = m.els.length;
      m.prismY(`${name}_c${k++}`, c, r, lo, hi, mat, { tex, seed: k * 3, cap: hi === y1 ? cap : undefined });
      for (const e of m.els.slice(n0)) for (const d of ['north', 'south', 'east', 'west']) {
        const f = e.face[d];
        if (f && f.uv) e.face[d] = { tex: f.tex, uv: [f.uv[0], f.uv[1] + 16 * b, f.uv[2], f.uv[3] + 16 * b] };
      }
    }
  }

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // ----------------------------------------------------------------- pressurizer
  function pressurizer(lit) {
    const m = mk();
    m.prismY('skirt', [8, 8], 4.6, 0, 2, 'ci');
    m.prismY('shell', [8, 8], 6, 2, 12, 'rmi', { tex: 'reactor_island/rmi' });
    m.prismY('head', [8, 8], 4.8, 12, 13.4, 'rmi', { cap: 'reactor_island/rmi_top' });
    m.prismY('spray_nozzle', [8, 8], 1.2, 13.4, 14.4, 'st');
    // spray line from the cold leg, down the west side
    m.box('spray_line', [0.6, 4, 7.4], [1.6, 15, 8.6], 'bright', { split: '' });
    m.box('spray_line_top', [1.6, 14, 7.4], [7, 15, 8.6], 'bright', { split: '' });
    m.box('spray_valve', [0, 9, 6.8], [2.2, 11, 9.2], 'green', { split: '' });
    // three relief valves on the head and their discharge header to the east
    for (const [k, z] of [[0, 5.4], [1, 8], [2, 10.6]]) {
      m.box(`relief_valve_${k}`, [10.6, 13.4, z - 0.8], [12.2, 15.4, z + 0.8], 'red', { split: '' });
      m.box(`relief_pilot_${k}`, [10.9, 15.4, z - 0.4], [11.9, 16, z + 0.4], 'st', { split: '' });
    }
    m.box('discharge', [12.2, 14, 4.6], [16, 15, 11.4], 'ps', { split: '' });
    // level instrument lines on the north side
    m.box('level_line_top', [11.4, 3, 1.4], [12, 12, 2], 'st', { split: '' });
    m.box('level_pot', [11, 6, 1], [12.4, 8.4, 2.4], 'st', { split: '' });
    // heater terminal box at the foot with its contactor lamps
    m.decal('heater_box', [3, 0, 0.6], [13, 3.4, 2.6], 'ci', 'north', 'pz.box', { split: '' });
    for (let k = 0; k < 4; k++) {
      const x = 4.5 + k * 2.2;
      m.box((lit ? 'glow_' : '') + `heater_lamp_${k}`, [x, 1, 0.3], [x + 0.9, 1.9, 0.6], lit ? 'red' : 'soot', { split: '' });
    }
    m.box('surge_line', [7.2, 0, 13.4], [8.8, 1.4, 16], 'bright', { split: '' });
    return m;
  }

  // ----------------------------------------------------------------- gas centrifuge
  function centrifuge() {
    const m = mk('reactor_island/casing');
    m.box('base', [2, 0, 2], [14, 1.4, 14], 'ci');
    for (const [x, z] of [[2.4, 2.4], [12, 2.4], [2.4, 12], [12, 12]]) m.box(`damper_${x}_${z}`, [x, 1.4, z], [x + 1.6, 2.4, z + 1.6], 'soot', { split: '' });
    m.prismY('motor', [8, 8], 4.6, 1.4, 5.4, 'green');
    m.prismY('casing_lo', [8, 8], 3.6, 5.4, 16, 'cas', { tex: 'reactor_island/casing' });
    tower(m, 'casing_hi', [8, 8], 3.6, 16, 29, 'cas', 'reactor_island/casing');
    for (const y of [5.4, 15.4, 28.4]) m.prismY(`band_${y}`, [8, 8], 3.9, y, y + 0.6, 'st');
    m.prismY('top_flange', [8, 8], 4.4, 29, 30, 'st');
    m.prismY('cap', [8, 8], 1.8, 30, 31.4, 'st', { cap: 'reactor_island/vessel_top' });
    // feed, product, tails lines from the top to the header pipes along the edges
    m.box('feed', [7.5, 30.4, 1], [8.5, 31.4, 6.2], 'uf6', { split: '' });
    m.box('product', [1, 30.4, 7.5], [6.2, 31.4, 8.5], 'uf6', { split: '' });
    m.box('tails', [9.8, 30.4, 7.5], [15, 31.4, 8.5], 'uf6', { split: '' });
    m.box('label', [6.4, 20, 4.3], [9.6, 22, 4.45], 'yel', { split: '' });
    return m;
  }

  // ----------------------------------------------------------------- low-temperature separation
  function lts(cold) {
    const m = mk('reactor_island/skid');
    const c = (warm) => cold ? 'frost' : warm;
    m.box('skid', [-16, -16, -16], [32, -14.6, 32], 'skid');
    m.box('skid_plate', [-16, -14.6, -16], [32, -14.4, 32], 'chequer', { split: 'xz' });
    // gas/gas exchanger: horizontal shell along x on two saddles, front
    m.prism('exchanger', 'x', [-9, -9], 3.4, -14, 8, 'ves', { ends: ['east', 'west'] });
    for (const x of [-11, 4]) m.box(`ex_saddle_${x}`, [x, -14.4, -12], [x + 2, -11, -6], 'skid', { split: '' });
    // choke (Joule-Thomson) valve with its diaphragm actuator on the cold line out of the exchanger
    m.box('choke_line', [8, -10, -10], [12, -8, -8], c('ves'), { split: '' });
    m.box('choke_body', [12, -11, -11], [15, -7, -7], c('ves'), { split: '' });
    m.box('choke_yoke', [13, -7, -9.6], [14, -4, -8.4], 'st', { split: '' });
    m.prismY('choke_actuator', [13.5, -9], 2.2, -4, -2.4, 'green');
    // propane chiller (kettle) east of the choke
    m.prism('chiller', 'x', [-6, -8], 4.2, 16, 30, c('ves'), { ends: ['east', 'west'] });
    m.prismY('chiller_dome', [23, -8], 2.2, -1.8, 1, c('ves'), { cap: cold ? 'reactor_island/frost_top' : 'reactor_island/vessel_top' });
    for (const x of [17, 27]) m.box(`ch_saddle_${x}`, [x, -14.4, -11], [x + 2, -10, -5], 'skid', { split: '' });
    // cold gas across to the separator
    m.box('cold_line_up', [27, -6, 0], [29, 6, 2], c('ves'), { split: 'y' });
    m.box('cold_line_over', [-6, 6, 0], [29, 8, 2], c('ves'), { split: 'x' });
    // three-phase low-temperature separator, back west, with its glycol boot and level glasses
    m.prismY('sep_skirt', [-6, 18], 4, -14.4, -10, 'skid');
    tower(m, 'separator', [-6, 18], 6, -10, 16, c('ves'), cold ? 'reactor_island/frost' : 'reactor_island/vessel');
    m.prismY('separator_head', [-6, 18], 4.6, 16, 18, c('ves'), { cap: cold ? 'reactor_island/frost_top' : 'reactor_island/vessel_top' });
    m.prismY('gas_outlet', [-6, 18], 1.2, 18, 22, 'st');
    m.box('inlet_nozzle', [-6.8, 5, 8], [-5.2, 8, 12.4], c('ves'), { split: '' });
    m.box('inlet_drop', [-6.8, 6, 2], [-5.2, 8, 8], c('ves'), { split: '' });
    m.box('level_glass', [0, -6, 17.2], [0.8, 6, 18.8], 'bright', { split: 'y' });
    m.box('glycol_boot', [-8, -14.4, 10], [-4, -10, 12], c('ves'), { split: '' });
    // methanol injection pump and its day tank, back east
    m.prismY('methanol_tank', [22, 20], 4, -14.4, 2, 'red', { cap: 'reactor_island/vessel_top' });
    m.box('injection_pump', [12, -14.4, 18], [17, -11, 23], 'green', { split: '' });
    m.box('injection_line', [13.6, -11, 13], [14.6, 6, 14], 'st', { split: 'y' });
    // inlet scrubber (free water and slugs out before the exchanger), east, and its line to the exchanger
    tower(m, 'scrubber', [22, 6], 3.6, -14.4, 18, 'ves', 'reactor_island/vessel', 'reactor_island/vessel_top');
    m.prismY('scrubber_outlet', [22, 6], 1, 18, 21, 'st');
    m.box('scrubber_line', [8, 19, 5.4], [22, 20.4, 6.6], 'pg', { split: 'x' });
    m.box('scrubber_drop', [8, -9, 5.4], [9.4, 20.4, 6.6], 'pg', { split: 'y' });
    m.box('scrubber_in', [8, -9.6, -8], [9.4, -8.4, 6.6], 'pg', { split: 'z' });
    // stair-ladder to the separator platform
    m.box('platform', [-14, 14, 8], [2, 14.5, 12], 'chequer', { split: 'x' });
    for (let y = -12; y < 14; y += 2) m.box(`rung_${y}`, [-14, y, 8], [-13.6, y + 0.4, 12], 'st', { split: '' });
    return m;
  }

  const BUILDS = {
    pressurizer: { model: () => pressurizer(false) },
    pressurizer_lit: { model: () => pressurizer(true) },
    gas_centrifuge: { model: centrifuge },
    low_temperature_separator: { model: () => lts(false) },
    low_temperature_separator_cold: { model: () => lts(true) },
  };
  const CAT = { pressurizer: 'core', pressurizer_lit: 'core', gas_centrifuge: 'enrichment', low_temperature_separator: 'gas_processing', low_temperature_separator_cold: 'gas_processing' };

  window.RIS = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, CAT[id], id, BUILDS[id].java),
    preview(root, id) { return PCART.realize(root, BUILDS[id].model(), id + '_preview'); },
  };
})();
