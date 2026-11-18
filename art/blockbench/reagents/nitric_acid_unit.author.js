// Nitric acid unit (Ostwald process, single-pressure), hand-authored for Blockbench. Replaces the borrowed
// ostwald_converter model.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/reagents/nitric_acid_unit.author.js', 'utf8'))
//   HNO3.paint('<repo>'); HNO3.build('<repo>', id); HNO3.save('<repo>', id)
//     for nitric_acid_unit, nitric_acid_unit_running, nitric_acid_unit/impeller
//
// 3x1x1 along x, anchored at the middle block (local x -16..32, y 0..16, z 0..16). Facade north.
//   west block   the ammonia converter: platinum-rhodium gauze pack under a cone (ammonia yellow and air blue
//                mixed above it), a sight glass on the gauze that glows while it burns, the waste-heat boiler below;
//   middle block the centrifugal air compressor on the Create shaft (shaft from the back): volute, inlet bell with
//                a guard; the impeller is the partial nitric_acid_unit/partial/impeller, spun about z through x 8, y 8;
//   east block   the cooler-condenser and the stainless absorption column with its tail-gas line, acid out (orange).
// Points used by code (NitricAcidBlockEntity): tail gas top (25, 16, 10); gauze sight (-8, 10.5, 1.8).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, writePng } = PCART;
  const mk = () => Object.assign(new PCART.Model(Object.assign({}, PLANT.MAT, {
    ss: { tex: 'nitric_acid_unit/stainless', P: 16 },
  }), { parts: PLANT.PARTS, own: { tex: 'nitric_acid_unit/parts', size: 32, rects: { sight: [0, 0, 8, 8], sight_lit: [8, 0, 8, 8] } } }),
  { particle: 'plant/blue' });

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/block/nitric_acid_unit');
    // Brushed stainless sheet (nitric acid service, 08Kh18N10T): vertical grain, faint weld lines.
    const ss = img(32, 32), r = rng(1301);
    const col = []; for (let x = 0; x < 32; x++) col.push((r() - 0.5) * 12);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(ss, x, y, add([176, 180, 182], col[x] + (r() - 0.5) * 5 + (y === 15 ? -18 : 0)));
    writePng(ss, path.join(dir, 'stainless.png'));
    // Sight glass on the gauze: dark when cold, white-orange glow of the burning gauze (about 900 C) when lit.
    const pt = img(32, 32), q = rng(1302);
    for (const lit of [0, 1]) for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
      let c = d > 3.6 ? [60, 62, 66] : d > 3 ? [150, 152, 156] : lit ? mix([255, 236, 170], [240, 110, 30], d / 3) : [30, 28, 26];
      if (!lit && d <= 3 && (x + y) % 2 === 0) c = [46, 44, 42];
      put(pt, lit * 8 + x, y, add(c, (q() - 0.5) * 6));
    }
    // impeller face, radial: backward-curved blades on a hub (16 px)
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy) / 8, a = Math.atan2(dy, dx) + d * 1.4;
      let c = [70, 74, 78];
      if (d < 1 && Math.abs(((a / (Math.PI / 4)) % 1 + 1) % 1 - 0.5) > 0.38) c = [190, 194, 198];
      if (d < 0.3) c = [120, 124, 128];
      if (d < 0.12) c = [40, 40, 42];
      put(pt, x, 16 + y, add(c, (q() - 0.5) * 6));
    }
    writePng(pt, path.join(dir, 'parts.png'));
    // impeller as its own radial texture
    const imp = img(16, 16);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) imp.d.set(pt.d.subarray(((16 + y) * 32 + x) * 4, ((16 + y) * 32 + x) * 4 + 4), (y * 16 + x) * 4);
    writePng(imp, path.join(dir, 'impeller.png'));
    return 3;
  }

  function unit(running) {
    const m = mk();
    m.box('plinth', [-16, 0, 1], [32, 1.5, 15], 'conc', { hide: ['down'] });

    // ---- west block: converter over the waste-heat boiler
    m.prismY('boiler', [-8, 8], 6, 1.5, 7, 'green', { ends: ['up'] });
    m.prismY('converter_flange', [-8, 8], 6.6, 7, 8, 'frame', { ends: ['up', 'down'] });
    m.prismY('converter', [-8, 8], 6, 8, 12.5, 'ss', { ends: ['up'] });
    m.prismY('cone_a', [-8, 8], 4.6, 12.5, 13.8, 'ss', { ends: ['up'] });
    m.prismY('cone_b', [-8, 8], 3, 13.8, 14.8, 'ss', { ends: ['up'] });
    m.prismY('mix_in', [-8, 8], 1.4, 14.8, 16, 'ss', { ends: ['up'] });
    m.box('mix_line', [-16, 14.6, 7.3], [-9.4, 16, 8.7], 'ss', { split: '' });
    m.box('ammonia_line', [-16, 12.4, 3], [-13, 13.6, 4.2], 'p_gas', { split: '' });
    m.box('ammonia_up', [-14.2, 13.6, 3], [-13, 15, 4.2], 'p_gas', { split: '' });
    m.box('ammonia_mix', [-14.2, 14.6, 4.2], [-13, 15.4, 7.3], 'p_gas', { split: '' });
    m.decal(running ? 'glow_gauze_sight' : 'gauze_sight', [-10, 8.6, 1.6], [-6, 12.6, 2.1], 'frame', 'north', running ? 'own.sight_lit' : 'own.sight',
      { hide: ['south'] });
    m.box('sight_hood', [-10.4, 12.6, 1.4], [-5.6, 13, 2.4], 'frame', { split: '' });
    m.decal('boiler_gauge', [-13.6, 3.4, 3], [-11.6, 5.4, 3.2], 'frame', 'north', 'gauge', { hide: ['south'] });
    m.box('boiler_steam', [-3, 4, 7.2], [0, 5.4, 8.8], 'p_steam', { split: '' });
    PLANT.ladder(m, 'ladder', -15.8, 12, 1.5, 15, 'z');

    // ---- middle block: compressor volute and inlet
    m.box('pedestal', [3, 1.5, 9], [13, 4, 15], 'frame', {});
    m.box('bearing', [5.5, 4, 11], [10.5, 10, 15.5], 'blue', { split: '' });
    m.prism('volute', 'z', [8, 8], 6, 4.5, 11, 'blue', { ends: ['north', 'south'] });
    m.box('scroll_out', [1.2, 9, 5], [5, 16, 10], 'blue', { split: '' });
    m.box('scroll_flange', [0.8, 15, 4.6], [5.4, 16, 10.4], 'frame', { split: '' });
    m.ring('inlet_bell', 'z', [8, 8], 4, 1.2, 2.4, 4.5, 'blue');
    for (const a of [0, 1]) {
      const bar = a ? [[7.7, 4.2, 2.2], [8.3, 11.8, 2.4]] : [[4.2, 7.7, 2.2], [11.8, 8.3, 2.4]];
      m.box(`guard_${a}`, bar[0], bar[1], 'frame', { split: '' });
    }
    m.box('volute_foot', [4, 1.5, 5], [12, 2.6, 10.5], 'frame', { split: '' });
    m.decal('nameplate', [1.4, 12.6, 4.8], [4.4, 14.1, 5], 'blue', 'north', 'tag', { hide: ['south'] });
    m.decal(running ? 'glow_lamp' : 'lamp', [3.6, 10.6, 4.8], [4.4, 11.4, 5], 'blue', 'north', running ? 'lamp_green' : 'lamp_amber',
      { hide: ['south'] });
    m.box('air_to_mixer', [-3, 14.6, 5.8], [1.2, 16, 7.2], 'p_air', { split: '' });
    m.box('air_to_mixer_b', [-9.4, 14.6, 5.8], [-3, 16, 7.3], 'p_air', { split: '' });

    // ---- east block: cooler-condenser and absorption column
    m.prism('cooler', 'x', [4.5, 4.5], 2.8, 15.5, 22, 'ss', { ends: ['west', 'east'] });
    for (const x of [15.8, 21.2]) m.box(`cooler_saddle_${x}`, [x, 1.5, 2.6], [x + 0.8, 2.4, 6.4], 'frame', { split: '' });
    m.box('cooler_water_in', [17, 7, 3.8], [18.2, 10, 5], 'p_water', { split: '' });
    m.box('cooler_water_out', [19.6, 1.5, 3.8], [20.8, 2, 5], 'p_water', { split: '' });
    m.prismY('column', [25, 10], 5, 1.5, 16, 'ss', { ends: ['up'] });
    for (const y of [5, 10]) m.prismY(`column_ring_${y}`, [25, 10], 5.3, y, y + 0.5, 'frame', { ends: ['up', 'down'] });
    m.box('manhole', [23, 6.5, 4.6], [27, 9, 5.2], 'ss', { split: '' });
    m.box('manhole_cover', [23.4, 6.9, 4.2], [26.6, 8.6, 4.6], 'frame', { split: '' });
    m.box('gas_to_column', [22, 3.6, 3.8], [24, 5.2, 5.4], 'ss', { split: '' });
    m.box('tail_gas', [28.6, 12, 9.2], [30, 16, 10.6], 'ss', { split: '' });
    m.box('acid_out', [29.4, 1.6, 6], [32, 2.8, 7.2], 'p_acid', { split: '' });
    m.box('acid_band', [30.4, 1.5, 5.9], [31, 2.9, 7.3], 'haz', { split: '' });
    m.decal('acid_warning', [23.6, 11, 4.85], [26.4, 13.4, 5.05], 'ss', 'north', 'warning', { hide: ['south'] });
    m.box('process_water', [20.6, 13, 12.4], [21.8, 16, 13.6], 'p_water', { split: '' });
    return m;
  }

  function impeller() {
    const m = mk();
    m.disc('wheel', 'z', [8, 8], 2.9, 3.4, 4.6, 'frame', 'nitric_acid_unit/impeller', 'north');
    m.box('nut', [7.4, 7.4, 3], [8.6, 8.6, 3.4], 'st', { split: '' });
    return m;
  }

  const BUILDS = {
    nitric_acid_unit: { model: () => unit(false) },
    nitric_acid_unit_running: { model: () => unit(true) },
    'nitric_acid_unit/impeller': { model: impeller, java: 'nitric_acid_unit/partial/impeller' },
  };

  window.HNO3 = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'reagents', id, BUILDS[id].java),
  };
})();
