// Ethylene pyrolysis furnace (SRT type: floor-fired radiant box, convection section, quench exchangers on a
// steam drum), hand-authored for Blockbench. Replaces the borrowed tube_furnace_radiant model.
// Load inside Blockbench desktop, after the shared toolkits:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/petrochem/pyrolysis_furnace.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>')
//   PYRO.build('<repo>', 'pyrolysis_furnace'); PYRO.save('<repo>', 'pyrolysis_furnace')    // and _lit
//
// 3x3x3, anchored at the middle block (local -16..32). Facade north. Points used by code
// (PyrolysisFurnaceBlockEntity): stack top (8, 32, 8); peep doors y 0 and 7.5 on z -6.4; drum vent (8, 32, 24).
(function () {
  const BAYS = [-8.5, -0.5, 8, 16.5, 24.5];
  const mk = () => Object.assign(new PCART.Model(PLANT.MAT, { parts: PLANT.PARTS }), { particle: 'plant/furnace_casing' });

  function furnace(lit) {
    const m = mk();
    // steel columns and the floor of the radiant box, floor burners hung under it on a fuel gas header
    for (const x of [-13, 7.2, 27.4]) for (const z of [-5, 19.4]) {
      m.box(`column_${x}_${z}`, [x, -16, z], [x + 1.6, -10, z + 1.6], 'frame', { split: 'y', hide: ['down'] });
    }
    m.box('floor', [-13, -10, -5], [29, -9, 21], 'frame', {});
    for (const z of [3, 13]) for (let x = -9; x <= 27; x += 6) {
      m.box(`burner_${x}_${z}`, [x - 1, -13.5, z - 1], [x + 1, -10, z + 1], 'ci', { split: '' });
      m.box(`register_${x}_${z}`, [x - 1.4, -14.6, z - 1.4], [x + 1.4, -13.5, z + 1.4], 'frame', { split: '' });
      m.box(`gas_drop_${x}_${z}`, [x - 0.3, -13, z < 8 ? z + 1 : 7.2], [x + 0.3, -12.4, z < 8 ? 8.8 : z - 1], 'p_gas', { split: '' });
    }
    m.box('gas_header', [-14, -13.2, 7.2], [30, -11.8, 8.8], 'p_gas', { split: 'x' });
    m.box('gas_inlet', [-16, -13.2, 7.2], [-14, -11.8, 8.8], 'p_gas', { split: '' });

    // radiant box: casing, buckstays and girts, rows of peep doors on the long sides
    m.box('radiant', [-12, -9, -4], [28, 12, 20], 'casing', { hide: ['down'] });
    for (const x of [-13, -5, 3, 12, 20, 28]) {
      m.box(`buck_n_${x}`, [x, -9, -5], [x + 1, 12, -4], 'frame', { split: 'y' });
      m.box(`buck_s_${x}`, [x, -9, 20], [x + 1, 12, 21], 'frame', { split: 'y' });
    }
    for (const z of [-4, 7.5, 19]) {
      m.box(`buck_w_${z}`, [-13, -9, z], [-12, 12, z + 1], 'frame', { split: 'y' });
      m.box(`buck_e_${z}`, [28, -9, z], [29, 12, z + 1], 'frame', { split: 'y' });
    }
    m.box('girt_n', [-13, 3, -5], [29, 4, -4], 'frame', { split: 'x' });
    m.box('girt_s', [-13, 3, 20], [29, 4, 21], 'frame', { split: 'x' });
    BAYS.forEach((x) => {
      for (const y of [-2, 6]) {
        const name = lit ? `glow_peep_${x}_${y}` : `peep_${x}_${y}`;
        m.decal(name, [x - 1, y, -4.4], [x + 1, y + 2.5, -4], 'frame', 'north', lit ? 'peep_lit' : 'peep', { hide: ['south'] });
        m.decal(`${name}_s`, [x - 1, y, 20], [x + 1, y + 2.5, 20.4], 'frame', 'south', lit ? 'peep_lit' : 'peep', { hide: ['north'] });
      }
    });
    m.decal('nameplate', [-3.5, -6.5, -4.4], [2.5, -3.5, -4], 'frame', 'north', 'plate', { hide: ['south'] });
    m.decal('warning', [9.5, -7, -4.4], [12.5, -4.2, -4.1], 'frame', 'north', 'warning', { hide: ['south'] });

    // roof deck with a handrail, ladder up the west end
    m.box('roof', [-14, 12, -6], [30, 13, 22], 'chequer', {});
    PLANT.railing(m, 'rail_n', -14, 30, 13, -6, 'x');
    PLANT.railing(m, 'rail_w', 3.6, 21.4, 13, -14, 'z');     // gap at the ladder head
    PLANT.ladder(m, 'ladder', -15.4, 0, -16, 13, 'z');

    // convection section: casing, buckstays, return-bend header boxes at both ends
    m.box('convection', [-8, 13, -1], [24, 24, 17], 'casing', {});
    for (const x of [-8.6, 2, 13, 23.6]) {
      m.box(`cbuck_n_${x}`, [x, 13, -1.6], [x + 1, 24, -1], 'frame', { split: 'y' });
      m.box(`cbuck_s_${x}`, [x, 13, 17], [x + 1, 24, 17.6], 'frame', { split: 'y' });
    }
    m.box('header_w', [-10.5, 14, 0.5], [-8, 23, 15.5], 'frame', {});
    m.box('header_e', [24, 14, 0.5], [26.5, 23, 15.5], 'frame', {});
    for (const y of [15.5, 19, 22]) {
      m.box(`header_w_bolt_${y}`, [-10.9, y, 1], [-10.5, y + 0.5, 15], 'st', { split: '' });
      m.box(`header_e_bolt_${y}`, [26.5, y, 1], [26.9, y + 0.5, 15], 'st', { split: '' });
    }
    // hydrocarbon feed (brown) and dilution steam (red) come in on the east end
    for (const z of [3, 11]) m.box(`feed_${z}`, [26.5, 20, z], [32, 21.6, z + 1.6], 'p_liq', { split: '' });
    m.box('dilution_steam', [26.5, 16.5, 7], [32, 18.1, 8.6], 'p_steam', { split: '' });
    PLANT.valve(m, 'feed_valve', 29.5, 20.8, 3.8, 1.1, 'green');

    // hood and stack
    m.box('hood', [-5, 24, 2], [21, 26, 14], 'casing', { hide: ['down'] });
    m.prismY('stack_base', [8, 8], 5, 26, 27.5, 'frame', { ends: ['up'] });
    m.prismY('stack', [8, 8], 4, 27.5, 31.9, 'ci', { cap: 'material/soot' });   // caps step up 0.01 each: stay under 32
    m.prismY('stack_ring', [8, 8], 4.4, 30, 30.8, 'frame', { ends: ['up', 'down'] });

    // quench exchangers (TLE) on the south side, on a beam over the edge, feeding the steam drum
    m.box('tle_beam', [-6, 11.5, 20], [22, 12.5, 28], 'frame', {});
    for (const x of [-6, 20.4]) m.box(`tle_leg_${x}`, [x, -16, 26.4], [x + 1.6, 11.5, 28], 'frame', { split: 'y', hide: ['down'] });
    for (const x of [2, 14]) {
      m.box(`crossover_${x}`, [x - 0.9, 12.5, 16], [x + 0.9, 14.3, 24], 'clad', { split: 'z' });
      m.prismY(`tle_${x}_a`, [x, 24], 1.9, 12.5, 16, 'st', { ends: ['down'] });
      m.prismY(`tle_${x}_b`, [x, 24], 1.9, 16, 26, 'st');
      m.prismY(`tle_${x}_flange`, [x, 24], 2.4, 18, 18.8, 'frame', { ends: ['up', 'down'] });
    }
    m.box('cracked_gas_out', [-16, 14.6, 23.2], [0.5, 16.4, 24.8], 'clad', { split: 'x' });
    m.prism('drum', 'x', [28.6, 24], 3.2, -4, 20, 'st', { ends: ['east', 'west'] });
    m.box('drum_vent', [7.4, 31.4, 23.4], [8.6, 32, 24.6], 'p_steam', { split: '' });
    for (const x of [-2, 18]) m.box(`drum_saddle_${x}`, [x, 25, 21.5], [x + 1.4, 26.5, 26.5], 'frame', { split: '' });
    m.decal('drum_gauge', [-4.4, 27, 22.6], [-4, 30, 25.4], 'frame', 'west', 'gauge', { hide: ['east'] });
    return m;
  }

  const BUILDS = {
    pyrolysis_furnace: { model: () => furnace(false) },
    pyrolysis_furnace_lit: { model: () => furnace(true) },
  };

  window.PYRO = {
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'petrochem', id, BUILDS[id].java),
    exportAll: (root) => PCART.exportAll(root, 'petrochem', BUILDS),
  };
})();
