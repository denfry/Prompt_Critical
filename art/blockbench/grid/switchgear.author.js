// Distribution gear of the I1 grid, hand-authored for Blockbench (replaces the spec_grid.py placeholders): the
// 6-10 kV withdrawable switchgear cell (K-104 type) in four states, the 0.4 kV switchboard (ShchO-70 type) in
// three, the outdoor disconnector with its blade, and the oil distribution transformer (TMG-1000/10 type).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/grid/switchgear.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); SWG.paint('<repo>')
//   build + save each model in turn (SWG.build / SWG.save, ids in SWG.ids)
//
// Front to the north. Contracts:
//   switchgear cell 1x2x1: PosterRenderer hangs the work permit poster at x 5, y 20 on the plane z 0, so the
//     door face stays flat there. Signal lamps in Soviet practice: red = breaker closed, green = open.
//   disconnector 3x1x1 (x -16..32): DisconnectorRenderer turns the blade partial about z through x -10, y 18;
//     the poster hangs at x 5, y 3 on the plane z 0 — a tag board on a bracket is there to carry it.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Cabinet enamel, RAL 7035 light grey.
  T['switchgear_cell/enamel'] = () => { const im = img(32, 32), r = grain(im, [196, 198, 192], 2501, 3, 3, 8); for (let k = 0; k < 6; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [150, 150, 146]); return im; };
  // Cell door: mimic of the cell (busbar, disconnector, breaker, cable) in black on grey, a viewing window.
  T['switchgear_cell/door'] = () => {
    const im = img(32, 32), r = grain(im, [196, 198, 192], 2511, 3, 3, 8);
    const ink = [30, 30, 34];
    rect(im, 4, 4, 24, 1, [40, 120, 60]);                    // busbar (green = 6 kV)
    rect(im, 15, 4, 1, 22, ink); rect(im, 13, 9, 5, 1, ink);  // drop and disconnector stroke
    rect(im, 12, 13, 7, 6, ink); rect(im, 13, 14, 5, 4, [196, 60, 50]); // breaker square
    rect(im, 14, 25, 3, 1, ink); rect(im, 13, 26, 5, 1, ink); // cable end
    rect(im, 4, 21, 6, 7, [40, 46, 52]); rect(im, 5, 22, 4, 5, [90, 110, 120]); // window
    for (let i = 0; i < 32; i++) { put(im, i, 0, [150, 150, 146]); put(im, 0, i, [150, 150, 146]); put(im, i, 31, [120, 120, 116]); put(im, 31, i, [120, 120, 116]); }
    return im;
  };
  // Relay compartment front: dark panel.
  T['switchgear_cell/relay'] = () => { const im = img(32, 32); grain(im, [70, 74, 78], 2521, 2, 3, 8); return im; };
  // Switchboard upper panel: three ammeters and labels.
  T['lv_switchboard/meters'] = () => {
    const im = img(32, 32), r = grain(im, [196, 198, 192], 2531, 3, 3, 8);
    for (const x of [2, 12, 22]) {
      rect(im, x, 6, 8, 8, [40, 40, 44]); rect(im, x + 1, 7, 6, 6, [232, 228, 214]);
      for (let k = 0; k < 6; k++) put(im, x + 1 + k, 8 + Math.abs(2.5 - k) * 0.4, [40, 38, 36]);
      put(im, x + 4, 11, [30, 30, 30]); put(im, x + 5, 10, [30, 30, 30]); put(im, x + 6, 9, [196, 40, 30]);
      rect(im, x + 1, 17, 6, 2, [240, 236, 220]);
    }
    rect(im, 2, 24, 28, 3, [240, 236, 220]);
    return im;
  };
  // Transformer tank: RAL 7032 pebble grey corrugation.
  T['distribution_transformer/fins'] = () => {
    const im = img(32, 32), r = grain(im, [176, 172, 156], 2541, 3, 3, 8);
    for (let x = 0; x < 32; x += 3) { rect(im, x, 0, 1, 32, [120, 118, 106]); rect(im, x + 1, 0, 1, 32, [200, 196, 180]); }
    return im;
  };
  T['distribution_transformer/enamel'] = () => { const im = img(32, 32); grain(im, [176, 172, 156], 2551, 3, 3, 8); return im; };
  // Glazed porcelain, brown, for the bushings and post insulators.
  T['disconnector/porcelain'] = () => {
    const im = img(16, 16), r = grain(im, [120, 62, 36], 2561, 3, 4, 4);
    for (let x = 0; x < 16; x += 4) rect(im, x, 0, 1, 16, [170, 100, 66]);
    return im;
  };
  T['disconnector/copper'] = () => { const im = img(16, 16); grain(im, [190, 116, 64], 2571, 5, 6, 4); return im; };
  T['disconnector/galvanised'] = () => { const im = img(32, 32), r = grain(im, [150, 154, 156], 2581, 5, 6, 5); for (let k = 0; k < 20; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [196, 198, 196]); return im; };

  const MAT = {
    en: { tex: 'switchgear_cell/enamel', P: 16 }, rel: { tex: 'switchgear_cell/relay', P: 16 },
    fin: { tex: 'distribution_transformer/fins', P: 16 }, tx: { tex: 'distribution_transformer/enamel', P: 16 },
    por: { tex: 'disconnector/porcelain', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 },
    grate: { tex: 'vver/grating', P: 16 }, haz: { tex: 'plant/hazard', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'switchgear_cell/enamel' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Signal lamp on the front: lens in a bezel; a lit lamp glows.
  function lamp(m, n, x, y, col, lit) {
    m.box(`${n}_bezel`, [x - 0.9, y - 0.9, 0.2], [x + 0.9, y + 0.9, 0.6], 'st', { split: '' });
    m.box((lit ? 'glow_' : '') + n, [x - 0.6, y - 0.6, 0], [x + 0.6, y + 0.6, 0.2], lit ? col : 'soot', { split: '' });
  }

  // Post insulator: core and sheds, steel cap.
  function insulator(m, n, x, z, y0, h, r) {
    m.prismY(`${n}_core`, [x, z], r * 0.55, y0, y0 + h, 'por');
    for (let y = y0 + 1, i = 0; y < y0 + h - 0.8; y += 1.8, i++) m.prismY(`${n}_shed_${i}`, [x, z], r, y, y + 0.6, 'por', { ends: ['up', 'down'] });
    m.prismY(`${n}_cap`, [x, z], r * 0.75, y0 + h, y0 + h + 1, 'st', { ends: ['up'] });
  }

  // ---------------------------------------------------------------- switchgear cell
  function cellLower(state) {
    const m = mk();
    m.box('body', [0, 0, 1], [16, 31.6, 16], 'en', { split: 'y' });
    m.box('relay_panel', [0.5, 24, 0.6], [15.5, 31.4, 1], 'rel', { split: '' });
    m.box('bushing_box', [3, 31.6, 5], [13, 32, 13], 'en', { split: '' });
    const lampCol = { closed: 'red', open: 'green', racked_out: 'green', alarm: 'br' }[state];
    lamp(m, 'lamp_state', 3.4, 28.4, lampCol, true);
    lamp(m, 'lamp_spare', 6.6, 28.4, 'green', false);
    m.plate('ammeter', [10, 26.4, 0.4], [14, 30.4, 0.6], 'rel', 'north', 'material/gauge_dial', { hide: ['south'] });
    m.box('key_switch', [7.8, 25, 0.2], [9, 26.2, 0.6], 'st', { split: '' });
    if (state === 'racked_out') {
      // door open on its hinges, the withdrawable truck rolled out with the breaker's three poles
      m.box('door_open', [-0.4, 1, -14], [0, 23, 0.6], 'en', { split: '' });
      m.box('cell_inside', [0.6, 1, 0.9], [15.4, 23, 1], 'rel', { split: '' });
      m.box('truck', [2, 0.6, -10], [14, 9, 0.4], 'en', { split: '' });
      for (const x of [3.6, 12.4]) for (const z of [-8.6, -1]) m.box(`wheel_${x}_${z}`, [x - 0.6, 0, z - 1], [x + 0.6, 1.6, z + 1], 'soot', { split: '' });
      for (const x of [4.6, 8, 11.4]) {
        m.prismY(`pole_${x}`, [x, -4.6], 1.3, 9, 18, 'en', { ends: ['up'] });
        m.box(`contact_${x}`, [x - 0.5, 15, -3.4], [x + 0.5, 16, 0.2], 'cu', { split: '' });
        m.box(`contact_lo_${x}`, [x - 0.5, 11, -3.4], [x + 0.5, 12, 0.2], 'cu', { split: '' });
      }
      m.box('truck_handle', [5, 6, -10.6], [11, 7, -10], 'bright', { split: '' });
    } else {
      m.plate('door', [0.5, 1, 0.5], [15.5, 23.4, 0.6], 'en', 'north', 'switchgear_cell/door', { hide: ['south'] });
      m.box('handle', [12.8, 9, -0.4], [13.8, 14, 0.5], 'bright', { split: '' });
      for (const y of [3, 19]) m.box(`hinge_${y}`, [0, y, 0.2], [0.6, y + 1.6, 0.8], 'st', { split: '' });
      if (state === 'alarm') m.box('glow_alarm_flag', [10.4, 24.4, 0.2], [14, 25.4, 0.6], 'br', { split: '' });
    }
    return m;
  }

  // KSO-type cell at real size, 1 x 3 x 1 with the controller in the middle cell (y -16..32): the breaker and relay
  // compartments (the old 2-block cell) below, and on top the busbar compartment behind a mesh front: the bus
  // disconnector's operating lever, three busbars running through both side walls to the next cell, a danger
  // sign and the bushing box.
  function cell(state) {
    const m = mk().merge(cellLower(state), 0, -16, 0, '');
    m.box('bus_body', [0, 15.6, 1], [16, 31.6, 16], 'en', { split: '' });
    m.box('bus_frame', [0, 15.6, 0.4], [16, 16.6, 1], 'en', { split: '' });
    m.box('bus_frame_top', [0, 30.6, 0.4], [16, 31.6, 1], 'en', { split: '' });
    for (const x of [0, 15]) m.box(`bus_frame_${x}`, [x, 16.6, 0.4], [x + 1, 30.6, 1], 'en', { split: '' });
    m.box('mesh', [1, 16.6, 0.7], [15, 30.6, 0.9], 'grate', { split: '' });
    for (const x of [4, 8, 12]) m.box(`busbar_${x}`, [-0.01, 27, x], [16.01, 28.4, x + 0.6], 'cu', { split: '' });
    m.box('lever_shaft', [14, 22, 0], [15, 23, 1], 'st', { split: '' });
    m.box('lever', [14.1, 18, -0.6], [14.9, 23, 0], 'red', { split: '' });
    m.box('danger_sign', [5, 24, 0.3], [11, 28, 0.4], 'haz', { split: '' });
    m.box('bushing_box', [3, 31.6, 5], [13, 32, 13], 'en', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- 0.4 kV switchboard
  function lvBoard(state) {
    const m = mk();
    m.box('body', [0, 0, 2], [16, 31.6, 16], 'en', { split: 'y' });
    m.plate('panel_high', [0.5, 15.6, 1.6], [15.5, 31, 2], 'en', 'north', 'lv_switchboard/meters', { hide: ['south'] });
    m.box('panel_low', [0.5, 1, 1.6], [15.5, 15, 2], 'en', { split: '' });
    m.box('cable_duct', [2, 31.6, 4], [14, 32, 14], 'en', { split: '' });
    // three knife switches: handle up = on
    for (let i = 0; i < 3; i++) {
      const x = 3.4 + i * 4.6, on = state !== 'off';
      m.box(`switch_base_${i}`, [x - 1.4, 5, 1.2], [x + 1.4, 11, 1.6], 'soot', { split: '' });
      m.box(`switch_handle_${i}`, [x - 0.4, on ? 8 : 4.6, 0], [x + 0.4, on ? 12 : 8.6, 1.2], 'red', { split: '' });
      lamp(m, `lamp_${i}`, x, 13.4, state === 'overload' ? 'red' : 'green', state !== 'off');
    }
    for (const y of [2, 26]) m.box(`hinge_${y}`, [0, y, 1.2], [0.5, y + 1.4, 1.8], 'st', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- disconnector
  function disconnector(earthed) {
    const m = mk('disconnector/galvanised');
    m.box('frame', [-15, 0, 6], [31, 2, 10], 'gal', { split: 'x' });
    m.box('frame_lip', [-15, 2, 6], [31, 2.4, 6.6], 'gal', { split: 'x' });
    for (const x of [-10, 26]) {
      m.box(`post_base_${x}`, [x - 2, 2, 6], [x + 2, 3, 10], 'gal', { split: '' });
      insulator(m, `post_${x}`, x, 8, 3, 13, 2);
      m.box(`contact_${x}`, [x - 2, 17, 6.6], [x + 2, 19, 9.4], 'cu', { split: '' });
      m.box(`terminal_${x}`, [x + (x < 0 ? -4 : 2), 18.6, 7.4], [x + (x < 0 ? -2 : 4), 19.4, 8.6], 'cu', { split: '' });
    }
    // manual lever drive below the hinge post, drive rod up to it
    m.box('drive', [-14, 0, 0.6], [-9, 7, 4], 'gal', { split: '' });
    m.box('drive_lever', [-12, 7, 1.6], [-11, 11, 2.6], 'red', { split: '' });
    m.box('drive_rod', [-11, 2, 10.4], [-10, 14, 11.4], 'gal', { split: '' });
    // earthing blade at the fixed contact: down along the frame when off, up against the contact when earthed
    if (earthed) m.box('earth_blade', [24.6, 3, 10.4], [25.6, 18, 11.2], 'red', { split: '' });
    else m.box('earth_blade', [25.6, 2.4, 10.4], [31, 3.2, 11.2], 'red', { split: '' });
    m.box('earth_lead', [28, 0, 10.4], [28.8, 2.4, 11.2], 'cu', { split: '' });
    // tag board on a bracket, where the permit poster hangs
    m.box('tag_bracket', [7.6, 2, 0.8], [8.4, 3, 6], 'gal', { split: '' });
    m.box('tag_board', [4.6, 2.4, 0.2], [11.4, 7.6, 0.8], 'gal', { split: '' });
    return m;
  }

  // Blade partial: copper twin blade from the hinge contact on the left post to the fixed contact on the right.
  function blade() {
    const m = mk('disconnector/copper');
    m.box('hinge', [-12, 17, 6.6], [-8, 19.6, 9.4], 'cu', { split: '' });
    for (const z of [7.1, 8.3]) m.box(`blade_${z}`, [-10, 17.6, z], [26.4, 18.6, z + 0.6], 'cu', { split: 'x' });
    m.box('blade_tie', [12, 17.4, 7.1], [13, 18.8, 8.9], 'st', { split: '' });
    m.box('blade_tip', [25, 17.4, 7], [27, 18.8, 9], 'cu', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- distribution transformer
  function transformer() {
    // TM-630/6 at real size, 2 x 2 x 1 (x 0..32, y 0..32, z 0..16): tank 1.5 x 1.2 x 0.5 m on wheeled skids,
    // tubular radiators on both long sides, cover with three HV bushings (top cell) and four LV bushings, the
    // conservator on brackets with its oil gauge and the Buchholz pipe, off-circuit tap changer handle.
    const m = mk('distribution_transformer/enamel');
    for (const z of [2, 12]) m.box(`skid_${z}`, [3, 1.4, z], [29, 3, z + 2], 'ci', { split: 'x' });
    for (const x of [5, 27]) for (const z of [2, 12]) m.box(`wheel_${x}_${z}`, [x - 1.4, 0, z + 0.2], [x + 1.4, 2.8, z + 1.8], 'soot', { split: '' });
    m.box('tank', [4, 3, 4], [28, 20, 12], 'tx', { split: 'x' });
    for (const [n, z0, z1] of [['n', 0.6, 4], ['s', 12, 15.4]]) {
      m.box(`header_top_${n}`, [5, 17, z0 + 0.6], [27, 18.4, z1 - 0.6], 'tx', { split: 'x' });
      m.box(`header_bot_${n}`, [5, 4.6, z0 + 0.6], [27, 6, z1 - 0.6], 'tx', { split: 'x' });
      for (let x = 5.6; x < 27; x += 2.2) m.box(`tube_${n}_${x.toFixed(1)}`, [x, 6, z0], [x + 1.2, 17, z1], 'fin', { split: '' });
    }
    m.box('cover', [3.4, 20, 3.4], [28.6, 21, 12.6], 'tx', { split: 'x' });
    for (const [i, x] of [[0, 9], [1, 16], [2, 23]]) insulator(m, `hv_${i}`, x, 7, 21, 8, 1.4);
    for (let i = 0; i < 4; i++) {
      const x = 8 + i * 5;
      m.prismY(`lv_${i}`, [x, 10.6], 0.8, 21, 25, 'por');
      m.box(`lv_stud_${i}`, [x - 0.3, 25, 10.3], [x + 0.3, 26.2, 10.9], 'cu', { split: '' });
    }
    // conservator on two brackets over the east end, Buchholz pipe down to the cover, oil gauge on its end
    for (const x of [22, 28]) m.box(`cons_bracket_${x}`, [x, 21, 13], [x + 0.8, 26, 14], 'tx', { split: '' });
    m.prism('conservator', 'x', [27.6, 13.4], 2.4, 20, 31, 'tx', { split: '', ends: ['east', 'west'] });
    m.box('buchholz_pipe', [21, 21, 12.6], [22.4, 26, 14], 'tx', { split: '' });
    m.box('buchholz', [20.6, 23, 12.2], [22.8, 25, 14.4], 'ci', { split: '' });
    m.plate('oil_gauge', [31, 26.4, 12.4], [31.2, 28.6, 14.4], 'tx', 'east', 'material/gauge_dial', { hide: ['west'] });
    m.box('tap_handle', [5, 21, 9], [6.4, 23, 10.4], 'red', { split: '' });
    for (const x of [3.4, 27.6]) m.box(`lug_${x}`, [x, 19.6, 7.4], [x + 1, 22, 8.6], 'st', { split: '' });
    m.box('nameplate', [6, 12, 3.6], [11, 15.4, 4], 'br', { split: '' });
    m.box('drain_valve', [26, 3.4, 3.4], [27.4, 4.8, 4], 'ci', { split: '' });
    m.box('earth_bolt', [4.6, 3.4, 3.4], [5.6, 4.4, 4], 'cu', { split: '' });
    m.plate('thermometer', [12, 21, 10.6], [13.4, 22.6, 10.8], 'tx', 'north', 'material/gauge_dial', { hide: ['south'] });
    return m;
  }

  const BUILDS = {
    switchgear_cell: { model: () => cell('closed') },
    switchgear_cell_open: { model: () => cell('open') },
    switchgear_cell_racked_out: { model: () => cell('racked_out') },
    switchgear_cell_alarm: { model: () => cell('alarm') },
    lv_switchboard: { model: () => lvBoard('on') },
    lv_switchboard_off: { model: () => lvBoard('off') },
    lv_switchboard_overload: { model: () => lvBoard('overload') },
    disconnector: { model: () => disconnector(false) },
    disconnector_earthed: { model: () => disconnector(true) },
    'disconnector/blade': { model: blade, java: 'disconnector/partial/blade' },
    distribution_transformer: { model: transformer },
  };

  window.SWG = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'grid', id, BUILDS[id].java),
    preview(root) {
      return PCART.realize(root, cell('closed').merge(cell('racked_out'), 20, 0, 0, 'k_').merge(lvBoard('overload'), 40, 0, 0, 'l_')
        .merge(transformer(), 60, 0, 0, 't_').merge(disconnector(false), 20, 0, 40, 'd_').merge(blade(), 20, 0, 40, 'b_'), 'switchgear_preview');
    },
  };
})();
