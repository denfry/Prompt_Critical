// Gas compressor of the gas processing plant, hand-authored for Blockbench (replaces the borrowed
// oil_transport/reciprocating_compressor and its crankshaft and pistons partials).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (shared yellow):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/gas_processing/gas_compressor.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); GCM.paint('<repo>')
//   build + save each model in turn (GCM.build / GCM.save, ids in GCM.ids)
//
// 2x2x2 (x, y, z 0..32), front to the north. A balanced-opposed two-throw reciprocating compressor (the 2GM
// type of Soviet gas plants). The crankcase runs along x on its foundation block, with the two cylinders
// opposite each other north and south on their distance pieces, each on its own throw so the inertia forces
// cancel. Each cylinder has its suction pulsation bottle above, and the valve covers are on top and bottom.
// The Create shaft comes into the lower west cell at y 8, z 8 and drives the crankshaft through the gear case
// on the west end. The flywheel is at the east end. Gas lines are yellow (GOST 14202) and jacket cooling water
// is green.
// Crankshaft partial (gas_compressor/crankshaft): the shaft along x through y 8, z 16, the flywheel with its
// spokes and the two crank throws. The renderer turns it with the Create shaft.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, grain, writePng } = PCART;
  const T = {};
  const AY = 8, AZ = 16;

  // Machine enamel: the grey-green of Soviet compressor frames, oil-darkened low down.
  T['gas_compressor/frame'] = () => {
    const im = img(32, 32), r = grain(im, [92, 110, 100], 3801, 3, 5, 8);
    for (let y = 22; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), -(y - 22) * 2));
    for (let k = 0; k < 6; k++) put(im, r() * 32, 20 + r() * 12, [40, 38, 34]);
    return im;
  };
  // Foundation: concrete with the grout line.
  T['gas_compressor/foundation'] = () => {
    const im = img(32, 32), r = grain(im, [132, 130, 124], 3811, 5, 6, 8);
    for (let x = 0; x < 32; x++) put(im, x, 0, [96, 94, 90]);
    return im;
  };

  const MAT = {
    fr: { tex: 'gas_compressor/frame', P: 16 }, fd: { tex: 'gas_compressor/foundation', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
  };
  const mk = (p) => Object.assign(new PCART.Model(MAT, {}), { particle: p || 'gas_compressor/frame' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function compressor() {
    const m = mk();
    m.box('foundation', [0, 0, 4], [32, 2, 28], 'fd');
    // crankcase along x with the bearing caps on top and the inspection doors on the sides
    m.box('crankcase', [5, 2, 11], [27, 13, 21], 'fr');
    for (const x of [7, 15, 23]) m.box(`cap_${x}`, [x, 13, 13.4], [x + 2, 14.2, 18.6], 'fr', { split: '' });
    for (const x of [9, 19]) {
      m.box(`door_n_${x}`, [x, 4, 10.6], [x + 4, 9, 11], 'fr', { split: '' });
      m.box(`door_s_${x}`, [x, 4, 21], [x + 4, 9, 21.4], 'fr', { split: '' });
    }
    // gear case from the input shaft (y 8, z 8) up to the crankshaft line, the input bearing
    m.box('gear_case', [0.4, 2, 2], [5, 14, 21], 'fr', { split: '' });
    m.prism('input_bearing', 'x', [8, 8], 2.2, 0, 0.4, 'ci', { split: '', ends: ['west'] });
    // flywheel guard on the east end
    m.box('flywheel_guard_base', [27, 2, 7.6], [31.6, 3, 24.4], 'yel', { split: '' });
    // two opposed throws: distance piece with crosshead guide, cylinder, valve covers, suction bottle above
    for (const [n, s] of [['n', -1], ['s', 1]]) {
      const z0 = s < 0 ? 5 : 21, z1 = s < 0 ? 11 : 27, c0 = s < 0 ? 0.2 : 27, c1 = s < 0 ? 5 : 31.8, x0 = n === 'n' ? 9 : 17;
      m.box(`distance_${n}`, [x0, 4, z0], [x0 + 6, 11, z1], 'fr', { split: '' });
      m.box(`guide_window_${n}`, [x0 + 1, 6, s < 0 ? z0 + 1 : z0 + 1], [x0 + 5, 9, s < 0 ? z1 - 1 : z1 - 1], 'soot', { split: '', hide: ['up', 'down'] });
      m.prism(`cylinder_${n}`, 'z', [x0 + 3, 8], 4.2, c0, c1, 'fr', { split: '', ends: [s < 0 ? 'north' : 'south'] });
      m.box(`head_${n}`, [x0 - 1.4, 3.4, s < 0 ? 0 : 31.4], [x0 + 7.4, 12.6, s < 0 ? 0.6 : 32], 'ci', { split: '' });
      for (const dz of [1.2, 3]) {
        const z = s < 0 ? c0 + dz : c1 - dz - 1.4;
        m.box(`valve_top_${n}_${dz}`, [x0 + 1.6, 12, z], [x0 + 4.4, 13.2, z + 1.4], 'ci', { split: '' });
        m.box(`valve_bot_${n}_${dz}`, [x0 + 1.6, 2.6, z], [x0 + 4.4, 3.8, z + 1.4], 'ci', { split: '' });
      }
      const zb = s < 0 ? 2.8 : 29.2;
      m.prism(`bottle_${n}`, 'x', [19, zb], 2.6, x0 - 3, x0 + 9, 'yel', { split: '', ends: ['east', 'west'] });
      m.box(`bottle_neck_${n}`, [x0 + 2.2, 13.2, zb - 0.8], [x0 + 3.8, 16.6, zb + 0.8], 'yel', { split: '' });
      m.box(`suction_${n}`, [x0 + 2.2, 21.4, zb - 0.8], [x0 + 3.8, 32, zb + 0.8], 'yel', { split: 'y' });
      m.box(`discharge_${n}`, [x0 + 7.4, 0.6, s < 0 ? 0.6 : 29.6], [x0 + 8.8, 2.6, s < 0 ? 2.4 : 31.4], 'yel', { split: '' });
      m.box(`cw_${n}`, [x0 - 0.6, 6, s < 0 ? 1.4 : 29.6], [x0 + 0.2, 13, s < 0 ? 2.2 : 30.4], 'green', { split: '' });
      m.box(`relief_${n}`, [x0 + 5, 21, zb - 0.6], [x0 + 6.4, 23, zb + 0.6], 'red', { split: '' });
    }
    // suction header across the top, discharge header low at the back
    m.box('suction_header', [0, 30, 1.6], [32, 32, 3.6], 'yel', { split: 'x' });
    m.box('suction_header_s', [0, 30, 28.4], [32, 32, 30.4], 'yel', { split: 'x' });
    m.box('lube_box', [24, 13, 11.6], [27, 17, 15], 'fr', { split: '' });
    m.box('lube_sight', [24.4, 15, 11.4], [26.6, 16.4, 11.6], 'br', { split: '' });
    return m;
  }

  // Crankshaft partial: turned about x through y 8, z 16.
  function crankshaft() {
    const m = mk('material/bright_steel');
    m.prism('shaft', 'x', [AY, AZ], 1.1, 5, 30, 'bright', { split: '' });
    m.prism('gear_shaft', 'x', [AY, AZ], 1.1, 0.6, 5, 'bright', { split: '' });
    for (const [n, x, dy] of [['n', 11, 2], ['s', 19, -2]]) {
      m.box(`web_${n}_0`, [x - 1.6, AY - 2.6, AZ - 1.6], [x - 0.8, AY + 2.6, AZ + 1.6], 'st', { split: '' });
      m.box(`web_${n}_1`, [x + 0.8, AY - 2.6, AZ - 1.6], [x + 1.6, AY + 2.6, AZ + 1.6], 'st', { split: '' });
      m.box(`pin_${n}`, [x - 0.8, AY + dy - 0.8, AZ - 0.8], [x + 0.8, AY + dy + 0.8, AZ + 0.8], 'bright', { split: '' });
    }
    m.prism('flywheel_rim', 'x', [AY, AZ], 6.6, 28, 31, 'ci', { split: '', ends: ['east', 'west'] });
    m.box('flywheel_mark', [31, AY + 4.6, AZ - 0.5], [31.2, AY + 6.4, AZ + 0.5], 'red', { split: '' });
    return m;
  }

  const BUILDS = {
    gas_compressor: { model: compressor },
    'gas_compressor/crankshaft': { model: crankshaft, java: 'gas_compressor/partial/crankshaft' },
  };

  window.GCM = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'gas_processing', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, compressor().merge(crankshaft(), 0, 0, 0, 'c_'), 'gas_compressor_preview'); },
  };
})();
