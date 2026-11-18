// Primary coolant pump (single-stage centrifugal, end suction), hand-authored for Blockbench. Replaces the
// procedural placeholder from spec_base.py and its impeller partial.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mechanics/primary_pump.author.js', 'utf8'))
//   PUMP.paint('<repo>'); PUMP.build('<repo>', id); PUMP.save('<repo>', id)   for primary_pump, primary_pump/coupling
//
// One block, facade north: the suction flange faces north, the volute discharges up, the bearing frame runs back
// to the Create shaft in the south face. The shaft coupling between the bearing frame and the south face turns
// in an open guard: it is the partial primary_pump/partial/coupling, spun about z through x 8, y 7.
// Points used by code (PrimaryPumpBlockEntity): gland (8, 7, 9.4).
(function () {
  const path = require('path');
  const { rng, img, put, add, writePng } = PCART;
  const mk = () => Object.assign(new PCART.Model(PLANT.MAT, { parts: PLANT.PARTS }), { particle: 'plant/green' });

  // coupling face, radial: a ring of bolt heads on the flange, the bore
  function paint(root) {
    const im = img(16, 16), r = rng(1501);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      let c = add([140, 144, 148], (r() - 0.5) * 8);
      if (d > 0.9) c = [90, 92, 96];
      if (Math.abs(d - 0.66) < 0.1 && (Math.round(Math.atan2(y + 0.5 - 8, x + 0.5 - 8) / (Math.PI / 4)) + 8) % 1 === 0) c = [60, 60, 62];
      if (d < 0.24) c = [52, 52, 54];
      put(im, x, y, c);
    }
    for (let k = 0; k < 8; k++) {
      const a = k * Math.PI / 4;
      put(im, 7.5 + 5.2 * Math.cos(a), 7.5 + 5.2 * Math.sin(a), [40, 40, 42]);
    }
    writePng(im, path.join(root, PCART.ASSETS, 'textures/block/primary_pump/coupling.png'));
    return 1;
  }

  function pump() {
    const m = mk();
    m.box('baseplate', [1, 0, 1], [15, 1.4, 15], 'frame', {});
    for (const [x, z] of [[1.4, 1.4], [13.8, 1.4], [1.4, 13.8], [13.8, 13.8]]) m.box(`anchor_${x}_${z}`, [x, 1.4, z], [x + 0.8, 1.8, z + 0.8], 'st', { split: '' });
    // suction nozzle and flange on the north
    m.prism('suction', 'z', [8, 7], 2.4, 0.8, 3.4, 'green', { ends: [] });
    m.ring('suction_flange', 'z', [8, 7], 3.2, 1, 0, 0.8, 'green');
    m.disc('suction_bore', 'z', [8, 7], 2.3, 0.4, 0.6, 'frame', 'material/soot', 'north');
    // volute casing with its feet and the discharge nozzle up
    m.prism('volute', 'z', [8, 7], 5.4, 3.4, 8.6, 'green', { ends: ['north', 'south'] });
    m.box('volute_tongue', [8, 9, 3.4], [13, 12.4, 8.6], 'green', { split: '' });
    m.box('discharge', [9.6, 12.4, 4.4], [12.8, 15, 7.6], 'green', { split: '' });
    m.box('discharge_flange', [9, 15, 3.8], [13.4, 16, 8.2], 'green', { split: '' });
    for (const x of [3.6, 10.4]) m.box(`casing_foot_${x}`, [x, 1.4, 4.2], [x + 2, 3, 7.8], 'green', { split: '' });
    m.box('casing_drain', [7.4, 1.4, 5.4], [8.6, 1.8, 6.6], 'st', { split: '' });
    // stuffing box and bearing frame back to the coupling
    m.prism('stuffing_box', 'z', [8, 7], 2.2, 8.6, 10, 'st', { ends: [] });
    m.box('bearing_frame', [5, 3, 10], [11, 10.6, 13.2], 'green', {});
    m.box('bearing_foot', [4.4, 1.4, 10.4], [11.6, 3, 12.8], 'green', { split: '' });
    m.box('oil_cup', [10.6, 9.4, 11], [11.6, 11, 12], 'bright', { split: '' });
    m.box('oil_sight', [11, 6, 11.2], [11.3, 7.4, 12], 'p_air', { split: '' });
    // open coupling guard (bars), the half shaft of Create enters the south face
    for (const x of [4.4, 11]) m.box(`guard_side_${x}`, [x, 1.4, 13.2], [x + 0.6, 11, 16], 'yel', { split: '' });
    m.box('guard_top', [4.4, 10.4, 13.2], [11.6, 11, 16], 'yel', { split: '' });
    m.box('guard_mid', [4.4, 6.6, 15.4], [5, 7.4, 16], 'yel', { split: '' });
    m.decal('nameplate', [5.4, 8.6, 9.9], [7.6, 9.8, 10], 'green', 'north', 'tag', { hide: ['south'] });
    m.decal('gauge', [12.8, 13, 5.2], [14.4, 14.6, 5.4], 'frame', 'north', 'gauge', { hide: ['south'] });
    m.box('gauge_pipe', [12.8, 13.4, 5.4], [13.6, 14, 6.6], 'st', { split: '' });
    return m;
  }

  function coupling() {
    const m = mk();
    m.box('shaft', [7.4, 6.4, 13.2], [8.6, 7.6, 16], 'bright', { split: '' });
    m.disc('half_a', 'z', [8, 7], 2.4, 13.6, 14.4, 'st', 'primary_pump/coupling', 'north');
    m.disc('half_b', 'z', [8, 7], 2.4, 14.6, 15.4, 'st', 'primary_pump/coupling', 'south');
    return m;
  }

  const BUILDS = { primary_pump: { model: pump }, 'primary_pump/coupling': { model: coupling, java: 'primary_pump/partial/coupling' } };
  window.PUMP = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'mechanics', id, BUILDS[id].java),
  };
})();
