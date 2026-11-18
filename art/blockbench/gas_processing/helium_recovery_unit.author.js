// Helium recovery unit: cryogenic cold box for crude helium and a pressure-swing adsorption pair for the final
// purification, hand-authored for Blockbench. Replaces the borrowed helium_purifier model.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/gas_processing/helium_recovery_unit.author.js', 'utf8'))
//   HELIUM.paint('<repo>'); HELIUM.build('<repo>', id); HELIUM.save('<repo>', id)   for helium_recovery_unit, _running
//
// 2x2x1 (local x 0..32, y 0..32, z 0..16), controller at the lower west cell, facade north.
//   west: the cold box, a tall perlite-filled sheet casing on a plinth with its stiffeners, manhole, purge vent on
//         the roof and the frosted warm-end pipe stubs (frost only while running);
//   east: two adsorbers on skirts over the switching-valve skid, the vent silencer stack between them.
// Points used by code (HeliumRecoveryBlockEntity): cold box vent (8, 32, 8); PSA silencer top (25, 30, 4).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const mk = () => Object.assign(new PCART.Model(Object.assign({}, PLANT.MAT, {
    box: { tex: 'helium_recovery_unit/cold_box', P: 16 }, frost: { tex: 'helium_recovery_unit/frost', P: 16 },
  }), { parts: PLANT.PARTS }), { particle: 'helium_recovery_unit/cold_box' });

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/block/helium_recovery_unit');
    // cold box sheet: white enamel, a blue band every 32 px, weld lines and the odd condensation streak
    const cb = img(32, 32), r = grain(cb, [210, 212, 208], 1601, 4, 5, 8);
    for (let x = 0; x < 32; x++) {
      for (let y = 26; y < 30; y++) put(cb, x, y, add([52, 92, 150], (r() - 0.5) * 8));
      put(cb, x, 15, add([210, 212, 208], -24));
      if (r() < 0.12) for (let y = 16; y < 26; y++) put(cb, x, y, add([210, 212, 208], -12 - (r() * 6)));
    }
    writePng(cb, path.join(dir, 'cold_box.png'));
    // hoar frost on cold pipework
    const fr = img(32, 32), q = rng(1602);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(fr, x, y, add(mix([226, 236, 242], [190, 206, 218], q()), (q() - 0.5) * 10));
    writePng(fr, path.join(dir, 'frost.png'));
    return 2;
  }

  function unit(running) {
    const m = mk();
    m.box('plinth', [0, 0, 0.5], [32, 1.5, 15.5], 'conc', { hide: ['down'] });
    // ---- cold box
    m.box('cold_box', [1, 1.5, 2], [15, 31, 14], 'box', {});
    for (const x of [1, 7.7, 14.4]) m.box(`stiffener_${x}`, [x - 0.3, 1.5, 1.6], [x + 0.3, 31, 2], 'frame', { split: 'y' });
    for (const y of [10, 20]) m.box(`girt_${y}`, [0.7, y, 1.6], [15.3, y + 0.6, 2], 'frame', { split: '' });
    m.box('roof_edge', [0.6, 31, 1.6], [15.4, 31.6, 14.4], 'frame', { split: '' });
    m.box('purge_vent', [7.2, 31.6, 7.2], [8.8, 32, 8.8], 'st', { split: '' });
    m.box('manhole', [3, 4, 1.4], [6.6, 8, 2], 'frame', { split: '' });
    for (const y of [4.4, 7.2]) m.box(`manhole_dog_${y}`, [2.6, y, 1.2], [7, y + 0.4, 1.4], 'st', { split: '' });
    m.decal('nameplate', [9, 22, 1.8], [13, 24, 2], 'box', 'north', 'plate', { hide: ['south'] });
    m.decal('gauge', [9.2, 12, 1.8], [11.2, 14, 2], 'box', 'north', 'gauge', { hide: ['south'] });
    m.decal(running ? 'glow_lamp' : 'lamp', [12, 12.6, 1.8], [12.8, 13.4, 2], 'box', 'north', running ? 'lamp_green' : 'lamp_amber',
      { hide: ['south'] });
    // warm-end stubs out of the east wall, frosted while cold helium flows; crude helium on to the adsorbers
    for (const [y, mat] of [[6, running ? 'frost' : 'clad'], [13, running ? 'frost' : 'clad'], [24, 'p_gas']]) {
      m.box(`stub_${y}`, [15, y, 6.6], [18.4, y + 1.6, 8.2], mat, { split: '' });
      m.box(`stub_flange_${y}`, [15, y - 0.3, 6.3], [15.6, y + 1.9, 8.5], 'frame', { split: '' });
    }
    m.box('crude_he', [18.4, 24, 6.6], [28.6, 25.6, 8.2], 'p_gas', { split: 'x' });
    // ---- PSA skid: valve manifold and two adsorbers
    m.box('skid', [16.4, 1.5, 1.4], [31.6, 2.4, 14.6], 'frame', {});
    m.box('manifold', [17, 3.4, 2.6], [31, 4.6, 3.8], 'p_gas', { split: 'x' });
    for (const x of [18.4, 21.4, 25.2, 28.2]) {
      m.box(`valve_${x}`, [x, 2.4, 2.2], [x + 1.8, 5, 4.2], 'green', { split: '' });
      m.box(`actuator_${x}`, [x + 0.1, 5, 2.3], [x + 1.7, 6.6, 4.1], 'blue', { split: '' });
    }
    for (const cx of [21, 28.2]) {
      m.prismY(`skirt_${cx}`, [cx, 9.4], 2.8, 2.4, 4.4, 'frame', { ends: ['up'] });
      m.prismY(`adsorber_${cx}_a`, [cx, 9.4], 3.2, 4.4, 16, 'light');
      m.prismY(`adsorber_${cx}_b`, [cx, 9.4], 3.2, 16, 25.6, 'light');
      m.prismY(`adsorber_${cx}_head`, [cx, 9.4], 2.2, 25.6, 26.8, 'light', { ends: ['up'] });
      m.prismY(`adsorber_${cx}_nozzle`, [cx, 9.4], 0.8, 26.8, 28.6, 'st', { ends: ['up'] });
      m.box(`adsorber_${cx}_tag`, [cx - 1.2, 18, 6.1], [cx + 1.2, 19.4, 6.3], 'light', { split: '' });
    }
    m.box('top_header', [20.2, 28, 9], [29, 29.2, 10.2], 'p_gas', { split: '' });
    m.box('product_he', [29, 28, 9], [32, 29.2, 10.2], 'p_gas', { split: '' });
    m.box('product_band', [30.4, 27.9, 8.9], [31, 29.3, 10.3], 'blue', { split: '' });
    // vent silencer between the adsorbers, on the front
    m.prismY('silencer', [24.6, 4.2], 1.3, 6.6, 16, 'frame');
    m.prismY('silencer_b', [24.6, 4.2], 1.3, 16, 29, 'frame');
    m.prismY('silencer_top', [24.6, 4.2], 1.6, 29, 30, 'st', { ends: ['up'] });
    m.decal('warning', [22.4, 10, 2.6], [24.8, 12.2, 2.8], 'frame', 'north', 'warning', { hide: ['south'] });
    return m;
  }

  const BUILDS = { helium_recovery_unit: { model: () => unit(false) }, helium_recovery_unit_running: { model: () => unit(true) } };
  window.HELIUM = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'gas_processing', id, BUILDS[id].java),
  };
})();
