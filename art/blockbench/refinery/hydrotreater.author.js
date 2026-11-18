// Hydrotreater: a thick-walled hydrotreating reactor on its skirt, hand-authored for Blockbench. Replaces the
// borrowed fixed_bed_reactor_large model.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/hydrotreater.author.js', 'utf8'))
//   HDT.build('<repo>', 'hydrotreater'); HDT.save('<repo>', 'hydrotreater')    // and _running
//
// One block, facade north. The reactor is a down-flow fixed bed: feed and hydrogen enter the top nozzle, the
// cold hydrogen quench comes in between the beds on the north side, product leaves the bottom elbow to the east.
// Points used by code (HydrotreaterBlockEntity): top nozzle (8, 16, 8); junction box lamp (3.2, 5.2, 1.6).
(function () {
  const mk = () => Object.assign(new PCART.Model(PLANT.MAT, { parts: PLANT.PARTS }), { particle: 'plant/light' });

  function reactor(running) {
    const m = mk();
    m.box('pad', [0.5, 0, 0.5], [15.5, 1, 15.5], 'conc', { hide: ['down'] });
    m.prismY('skirt', [8, 8], 4.6, 1, 3, 'frame', { ends: ['up'] });
    m.box('skirt_opening', [7, 1.2, 3.3], [9, 2.6, 3.5], 'rubber', { split: '' });
    m.prismY('shell', [8, 8], 5, 3, 12.5, 'light', { ends: ['up'] });
    for (const y of [5.5, 9]) m.prismY(`seam_${y}`, [8, 8], 5.2, y, y + 0.4, 'st', { ends: ['up', 'down'] });
    m.prismY('head_a', [8, 8], 4.4, 12.5, 13.6, 'light', { ends: ['up'] });
    m.prismY('head_b', [8, 8], 3.2, 13.6, 14.4, 'light', { ends: ['up'] });
    m.prismY('inlet', [8, 8], 1.4, 14.4, 15.4, 'st');
    m.prismY('inlet_flange', [8, 8], 2, 15.4, 16, 'frame', { cap: 'plant/structure', ends: ['down'] });
    // feed and hydrogen line over the top to the west edge
    m.box('feed_line', [0, 14.8, 7.3], [6.6, 16, 8.7], 'clad', { split: '' });
    // bottom outlet elbow to the east
    m.box('outlet_down', [12, 3.4, 7.3], [13.4, 5, 8.7], 'clad', { split: '' });
    m.box('outlet', [13.4, 3.4, 7.3], [16, 4.8, 8.7], 'clad', { split: '' });
    // cold hydrogen quench (yellow, gas) with its control valve, into the shell between the beds
    m.box('quench_riser', [11.6, 1, 2.2], [12.6, 9.5, 3.2], 'p_gas', { split: '' });
    m.box('quench_in', [9.6, 8.5, 2.2], [11.6, 9.5, 4.4], 'p_gas', { split: '' });
    m.box('quench_band', [11.5, 6.6, 2.1], [12.7, 7.2, 3.3], 'p_steam', { split: '' });
    m.box('quench_valve', [11, 4, 1.6], [13.2, 5.8, 3.8], 'green', { split: '' });
    m.box('quench_actuator', [11.3, 5.8, 1.9], [12.9, 7, 3.5], 'blue', { split: '' });
    // multipoint thermocouple: sheath up the shell, junction box with a lamp on the north side
    m.box('tc_sheath', [5.6, 3.5, 2.6], [6.2, 12.5, 3.2], 'st', { split: '' });
    m.box('tc_box', [2.2, 3.6, 1.6], [5.8, 7.2, 3.4], 'light', { split: '' });
    m.decal('tc_tag', [2.4, 6, 1.5], [5.6, 7, 1.6], 'light', 'north', 'tag', { hide: ['south'] });
    m.decal(running ? 'glow_tc_lamp' : 'tc_lamp', [2.8, 4.6, 1.5], [3.6, 5.4, 1.6], 'light', 'north', running ? 'lamp_green' : 'lamp_red',
      { hide: ['south'] });
    m.decal('gauge', [9.4, 10, 2.6], [11.4, 12, 2.8], 'frame', 'north', 'gauge', { hide: ['south'] });
    m.box('gauge_stem', [10.1, 9.4, 2.8], [10.7, 10, 3.6], 'st', { split: '' });
    // catalyst unloading nozzle low on the south side, blanked
    m.box('dump_nozzle', [7.2, 3.6, 12.6], [8.8, 5.2, 14.4], 'st', { split: '' });
    m.box('dump_blind', [6.8, 3.2, 14.4], [9.2, 5.6, 14.9], 'frame', { split: '' });
    return m;
  }

  const BUILDS = {
    hydrotreater: { model: () => reactor(false) },
    hydrotreater_running: { model: () => reactor(true) },
  };

  window.HDT = {
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'refinery', id, BUILDS[id].java),
  };
})();
