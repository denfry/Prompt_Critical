// The village consumer (grid_consumer, 07 §7.6.1) at real scale (1 block = 1 m, 16 px = 1 m): a closed 6/0.4 kV
// brick transformer substation of the 1950s, 2.75 m square and 2.8 m to the eaves under a pitched iron roof, as one
// 3 x 3 x 3 Java block model round its anchor (x, y, z -16..32), the same footprint as the old model:
// - north front: the louvred double doors of the transformer chamber with the warning plate, the 6 kV switchroom
//   door, and the lamp over it (glow_ in the lit variant);
// - west: the 6 kV cable entry with its pothead on the wall;
// - east: the 0.4 kV distribution board door and the outgoing overhead lines to the village on a steel bracket
//   with pin insulators (three phases and the neutral, two feeders);
// - south: louvred ventilation openings and the earthing conductor.
// grid_consumer_real_lit is the same with the lamp lit (LIT: the village is supplied).
//
// Load after pcart.js, pcround.js and core/vver/vverkit.js, then GCR.build(repo, v) /* wait */ GCR.save(repo, v),
// v = cold | lit. The cable may come to any outer face (as now); the drawn 6 kV entry is on the west face.
(function () {
  const W = [-14, 30], YE = 28;                         // walls x/z span, eaves height
  const MAT = Object.assign({}, PCVV.MAT, {
    roof: { tex: 'material/steel', P: 16 }, door: { tex: 'material/paint_green', P: 16 }, frame: { tex: 'plant/structure', P: 16 },
    por: { tex: 'disconnector/porcelain', P: 16 }, soot: { tex: 'material/soot', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/brick' });

  function building(m) {
    m.box('plinth', [W[0] - 1, -16, W[0] - 1], [W[1] + 1, -13, W[1] + 1], 'conc', { split: 'xz' });
    m.box('walls', [W[0], -13, W[0]], [W[1], YE - 16, W[1]], 'brick', { split: 'xyz' });
    m.box('eaves_course', [W[0] - 0.6, YE - 17, W[0] - 0.6], [W[1] + 0.6, YE - 16, W[1] + 0.6], 'brick', { split: 'xz' });
    // pitched roof along x, two slopes stepped in three courses, ridge cap
    for (let k = 0; k < 4; k++) {
      const y = YE - 16 + k * 2, inset = k * 6;
      m.box(`roof_${k}`, [W[0] - 1.4, y, W[0] - 1.4 + inset], [W[1] + 1.4, y + 2, W[1] + 1.4 - inset], 'roof', { split: 'xz' });
    }
    m.box('ridge', [W[0] - 1.4, YE - 8, 6], [W[1] + 1.4, YE - 7, 10], 'frame', { split: 'x' });
  }

  function front(m, lit) {
    const z = W[0] - 0.3;
    // transformer chamber: louvred double doors
    m.box('tx_frame', [-12, -13, z], [8, 12, W[0]], 'frame', { split: '' });
    for (const [n, x0, x1] of [['l', -11.4, -2.2], ['r', -1.8, 7.4]]) {
      m.box(`tx_door_${n}`, [x0, -12.6, z - 0.3], [x1, 11.4, z], 'door', { split: '' });
      for (let y = -9; y < 10; y += 2.4) m.box(`louvre_${n}_${y}`, [x0 + 1, y, z - 0.7], [x1 - 1, y + 0.8, z - 0.3], 'door', { split: '' });
    }
    m.box('tx_warning', [-4, 5, z - 0.4], [0, 9, z - 0.3], 'yel', { split: '' });
    // switchroom door with the lamp over it
    m.box('sw_door', [14, -13, z - 0.3], [24, 6, z], 'door', { split: '' });
    m.box('sw_handle', [22.4, -4, z - 0.8], [23.2, -2.4, z - 0.3], 'bright', { split: '' });
    m.box('lamp_arm', [18.6, 8, z - 1.2], [19.4, 8.6, z], 'st', { split: '' });
    m.box('lamp_shade', [17, 6.4, z - 1.7], [21, 8, z - 0.2], 'dark', { split: '' });
    m.box(lit ? 'glow_lamp' : 'lamp', [17.6, 5.8, z - 1.5], [20.4, 6.4, z - 0.5], lit ? 'yel' : 'clad', { split: '' });
    m.decal('plate', [26, 0, z - 0.1], [30, 4, z], 'brick', 'north', 'pl.maker');
  }

  // 6 kV cable entry on the west wall: the cable up the wall in its guard to the pothead and the bushing plate.
  function hvEntry(m) {
    const x = W[0] - 0.3;
    m.box('cable_guard', [x - 1, -16, 6], [x, 6, 9], 'st', { split: 'y' });
    m.box('pothead', [x - 1.6, 6, 5], [x, 11, 10], 'ci', { split: '' });
    for (const z of [5.6, 7.5, 9.4]) m.cylY(`pothead_bushing_${z}`, [x - 0.8, z], 0.5, 11, 13.4, 'por');
    m.box('entry_plate', [x - 0.4, 13, 4], [x, 17, 11], 'frame', { split: '' });
    m.box('hv_sign', [x - 0.1, -2, 4], [x, 2, 10], 'yel', { split: '' });
  }

  // 0.4 kV side on the east: board door, the outgoing line bracket on the gable with pin insulators.
  function lvSide(m) {
    const x = W[1] + 0.3;
    m.box('lv_door', [W[1], -13, 8], [x, -1, 18], 'door', { split: '' });
    m.box('lv_handle', [x, -8, 16], [x + 0.5, -6.4, 16.8], 'bright', { split: '' });
    for (const [k, y] of [[0, 0], [1, 5]]) {
      m.box(`bracket_${k}`, [W[1], y - 0.6, -12], [W[1] + 1, y + 0.6, 28], 'frame', { split: 'z' });
      for (let i = 0; i < 4; i++) {
        const z = -10 + i * 4 + (k ? 20 : 0);
        m.box(`pin_${k}_${i}`, [x + 0.2, y + 0.6, z - 0.3], [x + 0.8, y + 2, z + 0.3], 'st', { split: '' });
        m.cylY(`insulator_${k}_${i}`, [x + 0.5, z], 0.9, y + 2, y + 4, 'por');
        // line conductors leaving east over the edge of the model
        m.box(`wire_${k}_${i}`, [x + 0.5, y + 3.6, z - 0.1], [32, y + 3.8, z + 0.1], 'dark', { split: '' });
      }
    }
    m.box('lv_drop', [W[1], -6, 26], [x, 6, 27], 'st', { split: 'y' });
  }

  function back(m) {
    const z = W[1];
    for (const x of [-8, 16]) {
      m.box(`vent_${x}`, [x - 4, 14, z], [x + 4, 22, z + 0.3], 'frame', { split: '' });
      for (let y = 15; y < 21; y += 2) m.box(`vent_slat_${x}_${y}`, [x - 3.4, y, z + 0.3], [x + 3.4, y + 0.8, z + 0.7], 'frame', { split: '' });
      m.box(`vent_low_${x}`, [x - 4, -10, z], [x + 4, -4, z + 0.3], 'soot', { split: '' });
    }
    m.box('earth_tape', [26, -16, z], [27, 10, z + 0.4], 'st', { split: 'y' });
  }

  function model(lit) { const m = mk(); building(m); front(m, lit); hvEntry(m); lvSide(m); back(m); return m; }
  const ID = { cold: 'grid_consumer_real', lit: 'grid_consumer_real_lit' };
  window.GCR = {
    ID, model,
    build: (root, v) => { const m = model(v === 'lit'); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCART.save(root, 'grid', ID[v]),
  };
})();
