// Station storage of the I1 grid, hand-authored for Blockbench (replaces the spec_grid2.py placeholders): the
// 10 kV shunt capacitor bank (off and on) and the station control battery of open lead-acid cells (SK type) in
// five states of charge.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/grid/station_storage.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); STS.paint('<repo>')
//   build + save each model in turn (STS.build / STS.save, ids in STS.ids)
//
// Both 1x1x1, front to the north.
// Capacitor bank: a grey rack of two shelves of three cans; each can has its porcelain bushing and an expulsion
// fuse to the busbar on top; a discharge resistor and the "on" lamp on the front.
// Battery: two open cells in glass jars on a wooden rack (lead-acid stations stand on wood and glass: acid
// eats steel). The jars are clear glass (cutout): the plates are seen inside under the electrolyte line. The
// state of charge shows as the plate colour — a charged positive plate is dark chocolate lead dioxide, it pales
// toward greyish white as it sulphates on discharge — and as the float of the hydrometer standing in the east
// cell, which rides higher in denser (charged) acid.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  T['capacitor_bank/enamel'] = () => { const im = img(32, 32); grain(im, [190, 192, 186], 2601, 3, 3, 8); return im; };
  // Capacitor can: welded steel box, grey, a rating label.
  T['capacitor_bank/can'] = () => {
    const im = img(32, 32), r = grain(im, [150, 154, 156], 2611, 3, 4, 8);
    rect(im, 8, 10, 16, 8, [226, 222, 208]); for (let y = 12; y < 17; y += 2) rect(im, 10, y, 12, 1, [80, 80, 80]);
    return im;
  };
  T['capacitor_bank/porcelain'] = () => { const im = img(16, 16), r = grain(im, [120, 62, 36], 2621, 3, 4, 4); return im; };
  // Glass jar: clear (transparent) with a faint green tint at the edges and the electrolyte line.
  T['station_battery/glass'] = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      if (x === 0 || x === 15 || y === 15) put(im, x, y, [150, 186, 170]);
      else if (y === 3) put(im, x, y, [190, 220, 210]);
    }
    return im;
  };
  // Plates by state of charge: k = 0 (flat) .. 4 (full).
  for (let k = 0; k <= 4; k++) {
    T[`station_battery/plates_${k}`] = () => {
      const im = img(16, 16), r = rng(2631 + k);
      const pos = mix([196, 188, 176], [74, 40, 26], k / 4), neg = mix([150, 150, 150], [96, 98, 102], k / 4);
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(x % 4 < 2 ? pos : neg, (r() - 0.5) * 10));
      return im;
    };
  }
  // Electrolyte surface seen from above: dithered so the plates show through between the pixels.
  T['station_battery/acid'] = () => {
    const im = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) if ((x + 2 * y) % 5 === 0) put(im, x, y, [150, 178, 170]);
    return im;
  };
  T['station_battery/wood'] = () => {
    const im = img(32, 32), r = rng(2641);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([118, 84, 52], Math.sin((x + Math.sin(y * 0.3) * 2) * 0.9) * 8 + (r() - 0.5) * 10));
    return im;
  };
  T['station_battery/lead'] = () => { const im = img(16, 16); grain(im, [112, 114, 120], 2651, 5, 6, 4); return im; };

  const MAT = {
    cb: { tex: 'capacitor_bank/enamel', P: 16 }, can: { tex: 'capacitor_bank/can', P: 16 }, por: { tex: 'capacitor_bank/porcelain', P: 16 },
    gl: { tex: 'station_battery/glass', P: 16 }, wd: { tex: 'station_battery/wood', P: 16 }, pb: { tex: 'station_battery/lead', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 }, acid: { tex: 'station_battery/acid', P: 16 },
  };
  for (let k = 0; k <= 4; k++) MAT[`pl${k}`] = { tex: `station_battery/plates_${k}`, P: 16 };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'capacitor_bank/enamel' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function capBank(on) {
    const m = mk();
    for (const x of [1, 14]) m.box(`frame_${x}`, [x, 0, 2], [x + 1, 15, 14], 'cb', { split: '' });
    m.box('busbar', [1, 15, 7.4], [15, 15.6, 8.6], 'cu', { split: '' });
    for (const [j, y] of [[0, 1], [1, 8]]) {
      m.box(`shelf_${j}`, [1, y - 1, 2], [15, y, 14], 'cb', { split: '' });
      for (let i = 0; i < 3; i++) {
        const x = 2.4 + i * 4;
        m.box(`can_${j}_${i}`, [x, y, 4], [x + 3.2, y + 5.4, 12], 'can', { split: '' });
        m.prismY(`bushing_${j}_${i}`, [x + 1.6, 8], 0.6, y + 5.4, y + 6.4, 'por', { ends: ['up'] });
        // expulsion fuse from the bushing to the busbar above (top shelf only reaches it directly)
        if (j === 1) m.box(`fuse_${i}`, [x + 1.3, y + 6.4, 7.7], [x + 1.9, 15, 8.3], 'st', { split: '' });
      }
    }
    m.box('discharge_resistor', [11, 15.6, 3], [14, 16, 6], 'ci', { split: '' });
    m.box((on ? 'glow_' : '') + 'lamp_on', [7.4, 15.2, 1.6], [8.6, 16, 2.2], on ? 'red' : 'soot', { split: '' });
    return m;
  }

  function battery(k) {
    const m = mk('station_battery/wood');
    m.box('rack_base', [0, 0, 2], [16, 2, 14], 'wd', { split: '' });
    m.box('rack_rail', [0, 2, 2], [16, 3, 3], 'wd', { split: '' });
    for (const x of [1.6, 13.4]) m.box(`insulator_${x}`, [x, 2, 6], [x + 1, 3, 10], 'por', { split: '' });
    for (const [n, x0, x1] of [['w', 1, 7.4], ['e', 8.6, 15]]) {
      // plates inside first, then the jar round them, the electrolyte surface between
      for (let p = 0; p < 4; p++) {
        const z = 4.4 + p * 2;
        m.box(`plate_${n}_${p}`, [x0 + 0.8, 3.8, z], [x1 - 0.8, 11, z + 0.5], `pl${k}`, { split: '' });
      }
      const full = { tex: 'station_battery/glass', uv: [0, 0, 16, 16] };
      m.box(`jar_${n}`, [x0, 3, 3.4], [x1, 13, 12.6], 'gl', { split: '', hide: ['up'], face: { north: full, south: full, east: full, west: full } });
      m.box(`electrolyte_${n}`, [x0 + 0.1, 11.6, 3.5], [x1 - 0.1, 11.7, 12.5], 'acid', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
      m.box(`lug_${n}`, [x0 + 2, 11, 7.4], [x1 - 2, 13.6, 8.6], 'pb', { split: '' });
    }
    m.box('strap', [4, 13.6, 7.4], [12, 14.2, 8.6], 'pb', { split: '' });
    m.box('post_w', [3.4, 13, 7], [5, 14.8, 9], 'pb', { split: '' });
    m.box('post_e', [11, 13, 7], [12.6, 14.8, 9], 'pb', { split: '' });
    // hydrometer in the east cell: the float rides higher the denser the acid
    m.box('hydrometer_tube', [13.4, 9, 10.4], [14.2, 16, 11.2], 'gl', { split: '' });
    m.box('hydrometer_bulb', [13.2, 15.6, 10.2], [14.4, 16, 11.4], 'soot', { split: '' });
    const fy = 10 + k * 0.9;
    m.box('hydrometer_float', [13.6, fy, 10.6], [14, fy + 1.4, 11], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    capacitor_bank: { model: () => capBank(false) },
    capacitor_bank_on: { model: () => capBank(true) },
    station_battery: { model: () => battery(4) },
  };
  for (let k = 0; k < 4; k++) BUILDS[`station_battery_soc${k}`] = { model: () => battery(k) };

  window.STS = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'grid', id, BUILDS[id].java),
    preview(root) {
      let m = capBank(true);
      for (let k = 0; k <= 4; k++) m = m.merge(battery(k), 20 + k * 20, 0, 0, `b${k}_`);
      return PCART.realize(root, m, 'station_storage_preview');
    },
  };
})();
