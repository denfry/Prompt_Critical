// Uranium conversion, fuel fabrication and hydrometallurgy, hand-authored for Blockbench (replaces the
// spec_fuelcycle.py and spec_mining.py placeholders): dissolver, denitration reactor, hydrofluorination reactor,
// fluorination flame tower, fluorine cell, HF kiln with its shell, reduction furnace; pellet press with its ram,
// sintering furnace, rod loading station, assembly jig, Acheson furnace; leach tank and mixer-settler with their
// agitators, precipitation tank, ion exchange columns, rotary calciner with its shell.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/conversion/fuel_cycle.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); FUEL.paint('<repo>')
//   await FUEL.exportAll('<repo>')
//
// Front to the north; all single blocks. Static multipart parts (PcBlockStateProvider): hf_kiln + partial/shell,
// rotary_calciner + partial/shell, pellet_press + partial/ram, leach_tank + partial/agitator, mixer_settler +
// partial/impeller. Pipe colours by GOST 14202: acids orange, gases yellow, steam red, water green.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'fuel/';
  const T = {};

  // Monel/Inconel: dull warm grey with heat tint.
  T.monel = () => { const im = img(32, 32), r = grain(im, [150, 146, 136], 11101, 4, 6, 8); for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [130, 110, 90]); return im; };
  // Uranium powders: UO3 orange, UO2 brown-black, UF4 green, yellowcake.
  const powder = (base, seed) => () => { const im = img(16, 16), r = grain(im, base, seed, 10, 6, 4); return im; };
  T.uo3 = powder([214, 128, 40], 11111);
  T.uo2 = powder([52, 40, 34], 11112);
  T.uf4 = powder([96, 150, 70], 11113);
  T.yellowcake = powder([214, 186, 50], 11114);
  // Leach pulp: brown slurry with froth.
  T.pulp = () => { const im = img(32, 32), r = grain(im, [118, 92, 60], 11121, 8, 8, 6); for (let k = 0; k < 40; k++) put(im, r() * 32, r() * 32, [170, 150, 120]); return im; };
  // Rubber-lined / FRP vessel green and glovebox perspex.
  T.frp_green = () => { const im = img(32, 32), r = grain(im, [70, 110, 84], 11131, 4, 6, 8); for (let y = 0; y < 32; y += 8) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), 12)); return im; };
  T.fuel_green = () => { const im = img(32, 32), r = grain(im, [94, 136, 108], 11141, 3, 5, 8); return im; };
  // Fuel rod ends in a tray (zircaloy tubes seen end-on).
  T.rods = () => { const im = img(16, 16); rect(im, 0, 0, 16, 16, [60, 62, 66]); for (let y = 1; y < 16; y += 2) for (let x = 1; x < 16; x += 2) { put(im, x, y, [196, 198, 200]); } return im; };

  const MAT = Object.assign({}, PLANT.MAT, {
    mon: { tex: TEX + 'monel', P: 16 }, uo3: { tex: TEX + 'uo3', P: 16 }, uo2: { tex: TEX + 'uo2', P: 16 }, uf4: { tex: TEX + 'uf4', P: 16 },
    yc: { tex: TEX + 'yellowcake', P: 16 }, pulp: { tex: TEX + 'pulp', P: 16 }, frpg: { tex: TEX + 'frp_green', P: 16 },
    fgreen: { tex: TEX + 'fuel_green', P: 16 }, rods: { tex: TEX + 'rods', P: 16 },
    ss: { tex: 'electronics/ss', P: 16 }, glass: { tex: 'electronics/glass', P: 16 }, fb: { tex: 'boilers/firebrick', P: 16 },
    gph: { tex: 'steelmaking/graphite', P: 16 }, fglow: { tex: 'rolling/furnace_glow', P: 16 }, frost: { tex: 'electronics/frost', P: 16 },
    cu: { tex: 'disconnector/copper', P: 16 }, en: { tex: 'switchgear_cell/enamel', P: 16 }, grat: { tex: 'steelmaking/grating', P: 8 },
    l_a: { tex: 'grid_lines/lamp_amber', P: 16 }, l_g: { tex: 'grid_lines/lamp_green', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });
  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', TEX + n + '.png'));
    return Object.keys(T).length;
  }
  const legs = (m, mat, h, inset) => { const a = inset || 2; for (const [x, z] of [[a, a], [15 - a, a], [a, 15 - a], [15 - a, 15 - a]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1, h, z + 1], mat || 'frame', { split: '' }); };
  const pipe = (m, n, f, t, mat) => m.box(n, f, t, mat, { split: '' });

  // ---------------------------------------------------------------- conversion
  // Dissolver: stainless vessel on legs with a cone bottom, steam jacket, nitric acid feed, off-gas line, top
  // agitator drive, sight glass and the product drain.
  function dissolver() {
    const m = mk('electronics/ss');
    legs(m, 'frame', 4, 2.4);
    m.prismY('cone', [8, 8], 3.6, 2, 4, 'ss', { ends: ['down'] });
    m.prismY('shell', [8, 8], 5.6, 4, 13, 'ss', {});
    m.prismY('jacket', [8, 8], 5.9, 5, 9, 'p_steam', {});
    m.prismY('head', [8, 8], 4.6, 13, 14, 'ss', { ends: ['up'] });
    m.box('motor', [6.6, 14, 6.6], [9.4, 16, 9.4], 'blue', { split: '' });
    pipe(m, 'acid_in', [9.6, 13.4, 3], [10.8, 15, 4.2], 'p_acid'); pipe(m, 'acid_run', [10.8, 14.4, 3], [16, 15, 4.2], 'p_acid');
    pipe(m, 'offgas', [4, 14, 9], [5.2, 16, 10.2], 'p_gas');
    m.box('sight', [7.2, 9.6, 2.2], [8.8, 11.4, 2.5], 'glass', { split: '' });
    pipe(m, 'drain', [7.4, 0, 7.4], [8.6, 2, 8.6], 'p_liq');
    return m;
  }
  // Denitration reactor: a framed fluid-bed column with heater bands, the UNH feed, NOx off-gas to the top, the
  // UO3 product hopper below and the status lamp.
  function denitration() {
    const m = mk('electronics/ss');
    m.box('frame_base', [0.4, 0, 1], [15.6, 1, 15], 'frame', { split: '' });
    for (const [x, z] of [[0.4, 1], [14.6, 1], [0.4, 14], [14.6, 14]]) m.box(`post_${x}_${z}`, [x, 1, z], [x + 1, 16, z + 1], 'frame', { split: '' });
    m.prismY('column', [8, 8], 3.4, 5, 15, 'ss', { ends: ['up'] });
    for (const y of [7, 10, 13]) m.prismY(`heater_${y}`, [8, 8], 3.8, y, y + 1.2, 'ci', { ends: ['up', 'down'] });
    m.box('hopper', [5.4, 1, 5.4], [10.6, 5, 10.6], 'ss', { split: '' });
    m.box('hopper_window', [6.4, 2, 5.3], [9.6, 4, 5.4], 'uo3', { split: '', hide: ['south'] });
    pipe(m, 'unh_feed', [11.4, 11, 7.4], [15.6, 12.2, 8.6], 'p_acid');
    pipe(m, 'nox', [7.4, 15, 7.4], [8.6, 16, 8.6], 'p_gas');
    m.box('panel', [1.4, 8, 1.1], [4.6, 14, 2], 'en', { split: '' });
    m.box('glow_lamp', [2.4, 12.4, 0.9], [3.6, 13.4, 1.1], 'l_a', { split: '' });
    return m;
  }
  // Hydrofluorination reactor: a moving-bed column in Inconel with heater bands, the UO2 feed screw on top, HF
  // inlet low, off-gas high, the UF4 discharge.
  function hydrofluor() {
    const m = mk(TEX + 'monel');
    m.box('base', [1, 0, 1], [15, 1.6, 15], 'frame', { split: '' });
    m.prismY('column', [8, 8], 4, 1.6, 13, 'mon', {});
    for (const y of [3, 6, 9]) m.prismY(`band_${y}`, [8, 8], 4.4, y, y + 1.4, 'ci', { ends: ['up', 'down'] });
    m.box('feed_screw', [2, 13, 6.6], [12, 15, 9.4], 'mon', { split: '' });
    m.box('feed_motor', [0.4, 12.6, 6.2], [2.4, 15.4, 9.8], 'blue', { split: '' });
    m.box('feed_hopper', [9, 15, 6], [13, 16, 10], 'mon', { split: '' });
    pipe(m, 'hf_in', [12, 3.6, 7.4], [16, 4.8, 8.6], 'p_gas');
    pipe(m, 'offgas', [12, 11, 7.4], [16, 12.2, 8.6], 'p_gas');
    m.box('discharge', [6.8, 1.6, 2], [9.2, 3.4, 4], 'mon', { split: '' });
    m.box('uf4_bin', [6, 0, 0.6], [10, 1.6, 4], 'uf4', { split: '' });
    return m;
  }
  // Fluorination flame tower: tall Monel tower with the flame window glowing, fluorine inlet at the top, UF6 off
  // to the frosted cold traps, the ash can below.
  function flameTower() {
    const m = mk(TEX + 'monel');
    m.box('base', [1, 0, 1.6], [15, 1, 12.4], 'frame', { split: '' });
    m.box('ash_can', [5, 1, 4], [11, 4, 10], 'mon', { split: '' });
    m.prismY('tower', [8, 7], 3.2, 4, 17, 'mon', { ends: ['up'] });
    m.box('window_rim', [6.6, 9, 3.4], [9.4, 13, 3.8], 'mon', { split: '' });
    m.box('glow_window', [7, 9.4, 3.3], [9, 12.6, 3.4], 'fglow', { split: '' });
    pipe(m, 'f2_in', [7.4, 15, 7], [8.6, 17, 8.2], 'p_gas');
    pipe(m, 'uf6_out', [11, 13, 6.4], [14, 14.2, 7.6], 'ss');
    for (const x of [12.2, 14.4]) m.box(`cold_trap_${x}`, [x - 1, 2, 5], [x + 1, 13, 9], 'frost', { split: '' });
    return m;
  }
  // Fluorine cell: steel cell tank with hazard edge, graphite anodes through the cover, copper busbars, the
  // separate H2 and F2 off-takes, cooling water.
  function fluorineCell() {
    const m = mk('plant/structure');
    m.box('tank', [0.6, 0, 2], [15.4, 10, 14], 'frame', { split: '' });
    m.box('edge', [0.4, 10, 1.6], [15.6, 11, 14.4], 'haz', { split: '' });
    m.box('cover', [1, 11, 2.4], [15, 12, 13.6], 'frame', { split: '' });
    for (const x of [3.4, 7, 10.6]) {
      m.box(`anode_${x}`, [x - 1, 12, 5], [x + 1, 14.4, 11], 'gph', { split: '' });
      m.box(`anode_bus_${x}`, [x - 0.4, 14.4, 7.6], [x + 0.4, 15, 8.4], 'cu', { split: '' });
    }
    m.box('bus', [2, 14.6, 7.6], [15.4, 15.2, 8.4], 'cu', { split: '' });
    pipe(m, 'f2_out', [13, 12, 3], [14.2, 15, 4.2], 'p_gas');
    pipe(m, 'h2_out', [13, 12, 11.6], [14.2, 15, 12.8], 'p_steam');
    pipe(m, 'water', [0, 3, 7.4], [0.6, 4.2, 8.6], 'p_water');
    return m;
  }
  // Rotary kiln base (HF kiln, calciner): concrete piers with trunnion roller stations, the firing hood with its
  // burner glowing, the feed chute; the shell part is the rotating drum on its tyres with the girth gear.
  function kilnBase(hood) {
    const m = mk('plant/concrete');
    for (const x of [2.4, 11.6]) {
      m.box(`pier_${x}`, [x - 1.6, 0, 3], [x + 1.6, 2.2, 13], 'conc', { split: '' });
      for (const z of [4.6, 11.4]) m.prism(`roller_${x}_${z}`, 'x', [2.6, z], 1, x - 1, x + 1, 'st', { split: '', ends: ['east', 'west'] });
    }
    m.box('hood', [14, 0, 1.6], [16, 15, 14.4], hood, { split: '' });
    m.box('glow_burner', [15.9, 6, 6.4], [16, 9, 9.6], 'fglow', { split: '' });
    m.box('feed_chute', [-1.5, 9, 6], [1.6, 16, 10], 'frame', { split: '' });
    m.box('gear_drive', [5, 0, 13.4], [9, 4, 15.6], 'blue', { split: '' });
    return m;
  }
  function kilnShell(lining) {
    const m = mk('material/steel');
    m.prism('drum', 'x', [8, 8], 5.4, 1.9, 14.1, 'frame', { split: '' });
    for (const x of [1.9, 10.6]) m.ring(`tyre_${x}`, 'x', [8, 8], 5.9, 0.8, x + 0.4, x + 1.8, 'bright');
    m.ring('girth', 'x', [8, 8], 6.2, 1, 6.6, 7.6, 'ci');
    m.disc('mouth', 'x', [8, 8], 4.4, 13.9, 14.1, 'frame', lining, 'east');
    return m;
  }
  // Hydrogen reduction furnace: a brick box with steel casing on legs, the boat loading door with a glowing
  // window, the H2 inlet and the burn-off stack.
  function reduction() {
    const m = mk('boilers/firebrick');
    legs(m, 'frame', 3, 1);
    m.box('body', [-1, 3, 1], [17, 13, 15], 'casing', { split: 'x' });
    m.box('door', [3, 4, 0.6], [13, 12, 1], 'ci', { split: '' });
    m.box('glow_window', [6.6, 8, 0.4], [9.4, 10, 0.6], 'fglow', { split: '' });
    pipe(m, 'h2_in', [-1, 6, 6], [0, 7.2, 7.2], 'p_gas');
    m.box('stack', [12, 13, 11], [14, 15, 13], 'frame', { split: '' });
    m.box('glow_flare', [12.4, 15, 11.4], [13.6, 15.6, 12.6], 'fglow', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- fuel fabrication
  // Pellet press: green C-frame with the die table, the powder feed shoe and hopper, the guard; the ram part is
  // the upper punch on its holder.
  function pelletPress() {
    const m = mk(TEX + 'fuel_green');
    m.box('base', [1, 0, 1], [15, 4, 15], 'fgreen', { split: '' });
    m.box('column', [3, 4, 11], [13, 16, 15], 'fgreen', { split: '' });
    m.box('crown', [3, 13, 3], [13, 16, 11], 'fgreen', { split: '' });
    m.box('table', [2, 4, 2], [14, 5.4, 11], 'ss', { split: '' });
    m.box('die', [6, 5.4, 5], [10, 6, 9], 'bright', { split: '' });
    m.box('hopper', [10.4, 8, 4], [14, 12, 8], 'ss', { split: '' });
    m.box('shoe', [10, 5.4, 5], [11.4, 6.4, 9], 'ss', { split: '' });
    m.box('guard', [1.6, 5.4, 1.6], [2, 12, 11], 'glass', { split: '' });
    m.box('lamp', [11, 14, 2.8], [12.2, 15.2, 3], 'l_g', { split: '' });
    return m;
  }
  function pelletRam() {
    const m = mk('material/bright_steel');
    m.box('holder', [5.4, 10, 5.4], [10.6, 13, 10.6], 'ss', { split: '' });
    m.box('punch', [7, 7, 7], [9, 10, 9], 'bright', { split: '' });
    return m;
  }
  // Sintering furnace: a long stainless muffle furnace with the glowing entrance, H2 lines, water-cooled exit.
  function sintering() {
    const m = mk('electronics/ss');
    m.box('stand', [0.4, 0, 1], [15.6, 3, 15], 'frame', { split: '' });
    m.box('body', [1, 3, 2], [15, 14, 14], 'ss', { split: '' });
    m.box('entry', [4, 5, 1.2], [12, 10, 2], 'ci', { split: '' });
    m.box('glow_entry', [5, 6, 1], [11, 9, 1.2], 'fglow', { split: '' });
    for (const y of [11, 12.4]) pipe(m, `h2_${y}`, [1, y, 0.6], [15, y + 0.8, 1.4], y === 11 ? 'p_gas' : 'p_water');
    m.box('vent', [6, 14, 6], [10, 18, 10], 'frame', { split: '' });
    return m;
  }
  // Rod loading station: a stainless bench with a perspex hood over the rod tray, the helium bottle and the end
  // plug welding head.
  function rodLoading() {
    const m = mk('electronics/ss');
    m.box('bench', [0, 6, 3], [16, 7, 14], 'ss', { split: '' });
    for (const x of [0.6, 14.4]) m.box(`bench_leg_${x}`, [x, 0, 4], [x + 1, 6, 13], 'frame', { split: '' });
    m.box('tray', [2, 7, 4.4], [12, 8, 12.6], 'ss', { split: '', face: { up: 'rods' } });
    m.box('hood', [1.4, 8, 4], [12.6, 14, 4.4], 'glass', { split: '' });
    m.box('hood_top', [1.4, 14, 4], [12.6, 14.4, 13], 'ss', { split: '' });
    m.prismY('bottle', [14, 9], 1.4, 7, 15, 'p_steam', { ends: ['up'] });
    m.box('weld_head', [12.6, 7, 7], [15, 10, 10.6], 'en', { split: '' });
    return m;
  }
  // Assembly jig: a strongback frame holding a fuel assembly horizontally with its spacer grids, the rod
  // magazine and the pulling carriage.
  function assemblyJig() {
    const m = mk('plant/light');
    m.box('strongback', [0, 0, 2], [16, 4, 14], 'light', { split: '' });
    m.box('assembly', [0, 4, 4.6], [16, 10.4, 11.4], 'gal', { split: '', face: { east: 'rods', west: 'rods' } });
    for (const x of [1, 5.6, 10.2, 14.6]) m.box(`grid_${x}`, [x, 3.8, 4.2], [x + 0.6, 10.8, 11.8], 'br', { split: '' });
    m.box('magazine', [0, 10.8, 1], [16, 13, 4], 'ss', { split: '', face: { east: 'rods', west: 'rods' } });
    m.box('carriage', [12, 10.4, 11.4], [16, 16, 15], 'en', { split: '' });
    return m;
  }
  // Acheson furnace: a long brick trough charged with coke and the electrodes, graphite electrode heads at both
  // ends with copper bus, the sand cover.
  function acheson() {
    const m = mk('material/brick');
    m.box('trough', [0, 0, 2], [16, 8, 14], 'brick', { split: '' });
    m.box('charge', [0.6, 8, 3], [15.4, 10, 13], 'soot', { split: '' });
    m.box('cover', [1, 10, 4], [15, 11.6, 12], 'conc', { split: '' });
    for (const x of [-1, 15]) {
      m.box(`head_${x}`, [x, 2, 4], [x + 2, 10, 12], 'gph', { split: '' });
      m.box(`bus_${x}`, [x, 10, 7], [x + 2, 13, 9], 'cu', { split: '' });
    }
    return m;
  }

  // ---------------------------------------------------------------- hydrometallurgy
  // Leach tank: rubber-lined tank with the pulp surface, the agitator bridge carrying the drive, acid feed and
  // overflow launder; the agitator part is the shaft and impeller blades in the pulp.
  function leachTank() {
    const m = mk(TEX + 'frp_green');
    m.prismY('tank', [8, 8], 7.4, 0, 12, 'frpg', {});
    m.box('pulp', [2, 11, 2], [14, 11.2, 14], 'pulp', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('bridge', [0, 12, 6.6], [16, 13.4, 9.4], 'frame', { split: '' });
    m.box('drive', [6, 13.4, 6], [10, 16, 10], 'blue', { split: '' });
    pipe(m, 'acid_feed', [1, 12, 2], [2.2, 15.3, 3.2], 'p_acid');
    m.box('launder', [14, 9, 6], [16, 11, 10], 'frpg', { split: '' });
    return m;
  }
  function leachAgitator() {
    const m = mk('material/steel');
    m.box('shaft', [7.5, 2, 7.5], [8.5, 13.4, 8.5], 'bright', { split: '' });
    for (const [n, a] of [['a', 0], ['b', 45], ['c', -45]]) m.box(`blade_${n}`, [3.4, 3, 7.6], [12.6, 3.6, 8.4], 'st', { split: '', rot: a ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined });
    m.box('blade_d', [7.6, 3, 3.4], [8.4, 3.6, 12.6], 'st', { split: '' });
    return m;
  }
  // Mixer-settler: a square mixer box with its drive and the long settler trough with the organic and aqueous
  // weirs; the impeller part is the motor shaft and turbine in the mixer.
  function mixerSettler() {
    const m = mk(TEX + 'frp_green');
    m.box('mixer', [0.6, 0, 3], [6.6, 10, 13], 'frpg', { split: '' });
    m.box('settler', [6.6, 0, 3], [15.6, 6, 13], 'frpg', { split: '' });
    m.box('organic', [7, 5.4, 3.4], [15.2, 5.6, 12.6], 'p_liq', { split: '', hide: ['down', 'north', 'south', 'east', 'west'] });
    m.box('weir', [14.6, 6, 3], [15.6, 7.4, 13], 'frpg', { split: '' });
    m.box('mixer_top', [0.6, 10, 3], [6.6, 10.6, 13], 'frame', { split: '' });
    pipe(m, 'aq_in', [0, 7, 5], [0.6, 8.2, 6.2], 'p_acid');
    pipe(m, 'org_out', [15.6, 4, 9], [16, 5.2, 10.2], 'p_liq');
    return m;
  }
  function mixerImpeller() {
    const m = mk('material/steel');
    m.box('motor', [2, 10.6, 6], [5.2, 15, 10], 'blue', { split: '' });
    m.box('shaft', [3.1, 2, 7.5], [4.1, 10.6, 8.5], 'bright', { split: '' });
    m.box('turbine', [1.6, 2.6, 6.2], [5.6, 3.6, 9.8], 'st', { split: '' });
    return m;
  }
  // Precipitation tank: stainless agitated tank with the ammonia sparge (yellow), the drive, the yellowcake
  // slurry outlet and a sample cock.
  function precipitation() {
    const m = mk('electronics/ss');
    legs(m, 'frame', 3, 2);
    m.prismY('tank', [8, 8], 6, 3, 14, 'ss', {});
    m.prismY('cover', [8, 8], 6.4, 14, 14.6, 'ss', { ends: ['up', 'down'] });
    m.box('drive', [6.4, 14.6, 6.4], [9.6, 16, 9.6], 'blue', { split: '' });
    pipe(m, 'nh3', [12.6, 10, 7.4], [16, 11.2, 8.6], 'p_gas'); pipe(m, 'nh3_down', [12.6, 4, 7.4], [13.8, 10, 8.6], 'p_gas');
    m.box('slurry_out', [7, 1.6, 1.4], [9, 3, 2.6], 'yc', { split: '' });
    pipe(m, 'outlet', [7.4, 1.6, 2.6], [8.6, 3, 4], 'ss');
    m.box('sample', [3, 7, 1.6], [4, 8, 2.4], 'br', { split: '' });
    return m;
  }
  // Ion exchange: two rubber-lined columns with the valve nest of brass valves and the acid manifold.
  function ionExchange() {
    const m = mk(TEX + 'frp_green');
    m.box('skid', [0, 0, 1.6], [16, 1, 14.4], 'frame', { split: '' });
    for (const x of [4.6, 11.4]) {
      m.prismY(`column_${x}`, [x, 9], 3.2, 1, 14, 'frpg', {});
      m.prismY(`head_${x}`, [x, 9], 2.4, 14, 16, 'frpg', { ends: ['up'] });
    }
    pipe(m, 'manifold_top', [1, 12, 3], [15, 13.2, 4.2], 'p_acid');
    pipe(m, 'manifold_bot', [1, 2.6, 3], [15, 3.8, 4.2], 'p_liq');
    for (const x of [3, 6.2, 9.8, 13]) for (const y of [12.6, 3.2]) m.box(`valve_${x}_${y}`, [x - 0.7, y - 0.8, 2.2], [x + 0.7, y + 0.8, 3], 'br', { split: '' });
    return m;
  }

  const BUILDS = {
    dissolver: { model: dissolver, cat: 'conversion' }, denitration_reactor: { model: denitration, cat: 'conversion' },
    hydrofluorination_reactor: { model: hydrofluor, cat: 'conversion' }, fluorination_reactor: { model: flameTower, cat: 'conversion' },
    fluorine_cell: { model: fluorineCell, cat: 'conversion' },
    hf_kiln: { model: () => kilnBase('fb'), cat: 'conversion' },
    'hf_kiln/shell': { model: () => kilnShell(TEX + 'uf4'), cat: 'conversion', java: 'hf_kiln/partial/shell' },
    reduction_furnace: { model: reduction, cat: 'conversion' },
    pellet_press: { model: pelletPress, cat: 'fuel_fabrication' },
    'pellet_press/ram': { model: pelletRam, cat: 'fuel_fabrication', java: 'pellet_press/partial/ram' },
    sintering_furnace: { model: sintering, cat: 'fuel_fabrication' }, rod_loading_station: { model: rodLoading, cat: 'fuel_fabrication' },
    assembly_jig: { model: assemblyJig, cat: 'fuel_fabrication' }, acheson_furnace: { model: acheson, cat: 'fuel_fabrication' },
    leach_tank: { model: leachTank, cat: 'hydrometallurgy' },
    'leach_tank/agitator': { model: leachAgitator, cat: 'hydrometallurgy', java: 'leach_tank/partial/agitator' },
    mixer_settler: { model: mixerSettler, cat: 'hydrometallurgy' },
    'mixer_settler/impeller': { model: mixerImpeller, cat: 'hydrometallurgy', java: 'mixer_settler/partial/impeller' },
    precipitation_tank: { model: precipitation, cat: 'hydrometallurgy' }, ion_exchange_column: { model: ionExchange, cat: 'hydrometallurgy' },
    rotary_calciner: { model: () => kilnBase('fb'), cat: 'hydrometallurgy' },
    'rotary_calciner/shell': { model: () => kilnShell(TEX + 'uo3'), cat: 'hydrometallurgy', java: 'rotary_calciner/partial/shell' },
  };

  window.FUEL = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    async exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        PCART.realize(root, BUILDS[id].model(), id);
        await new Promise((r) => setTimeout(r, 1200));
        out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
      }
      return out;
    },
  };
})();
