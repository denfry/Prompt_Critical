// Reactor hall exhaust stack and its special filters at real scale (1 block = 1 m, 16 px = 1 m), 03a §3a.9.1, §3a.9.3.
// The stack is a reinforced-concrete tube 2.5 m across with a 1.8 m flue, built like chimney_stack from stacked
// 3 x 3 x 3 segments:
//   vent_stack_real_base  square concrete pedestal with the exhaust duct from the filter house entering its north
//                         face, the stack monitor sampling line and its sampler cabinet, an access door;
//   vent_stack_real       plain shaft with the formwork lift joints and the ladder rungs up the east side;
//   vent_stack_real_cap   shaft head in red and white aviation bands, the service platform with its handrail, the
//                         isokinetic sampling probe and the obstruction lamps (glow_).
// Each segment is one Java block model about its middle block (x, y, z -16..32), controller = the middle block.
// The exhaust inlet of the base is the north face of its cell (0, -1, -1), opening x 0..16, y -16..0.
//
// The filters are plain one-block models at their real size (a 610 mm cell bank housing is about 1 m each way):
//   hepa_filter_real      bag-in/bag-out HEPA housing, air through it along x (duct collars west and east), the
//                         access door with two clamp handwheels and the differential pressure gauge on the north;
//   charcoal_filter_real  deep-bed iodine adsorber, air along x, the row of test canisters and the bed temperature
//                         gauge on the north, the charcoal fill hatch on top and the drain hopper underneath.
//
// Load after pcart.js, pcround.js and core/vver/vverkit.js, then VSR.build(repo, part) /* wait */
// VSR.save(repo, part), part = base | shaft | cap | hepa | charcoal.
(function () {
  const C = [8, 8], RS = 20, RI = 14.4;
  const MAT = Object.assign({}, PCVV.MAT, {
    duct: { tex: 'plant/structure', P: 16 }, frame: { tex: 'plant/structure', P: 16 }, ss: { tex: 'electronics/ss', P: 16 },
    soot: { tex: 'material/soot', P: 16 },
  });
  const mk = (p) => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: p || 'vver/concrete' });

  // concrete tube with a lift joint (a thin proud ring) every 1.5 m
  function tube(m, y0, y1, mat) {
    m.cylY('tube', C, RS, y0, y1, mat || 'conc');
    for (let y = Math.ceil((y0 + 2) / 24) * 24; y < y1; y += 24) m.ringY(`joint_${y}`, C, RS + 0.2, 0.3, y, y + 0.4, 'conc');
  }
  function rungs(m, y0, y1) {
    for (let y = y0 + 2; y < y1; y += 5) m.box(`rung_${y}`, [C[0] + RS - 0.2, y, 6.6], [C[0] + RS + 1.4, y + 0.5, 9.4], 'st', { split: '' });
    m.box('rail_l', [C[0] + RS + 1, y0, 6], [C[0] + RS + 1.6, y1, 6.6], 'st', { split: 'y' });
    m.box('rail_r', [C[0] + RS + 1, y0, 9.4], [C[0] + RS + 1.6, y1, 10], 'st', { split: 'y' });
  }

  function base() {
    const m = mk();
    m.box('plinth', [-16, -16, -16], [32, -13, 32], 'conc');
    // pedestal walls round the inlet (north face, x 0..16, y -16..0)
    m.box('ped_w', [-14, -13, -14], [0, 14, 30], 'conc');
    m.box('ped_e', [16, -13, -14], [30, 14, 30], 'conc');
    m.box('ped_over', [0, 0, -14], [16, 14, 30], 'conc');
    m.box('ped_back', [0, -13, -8], [16, 0, 30], 'conc');
    m.box('ped_cornice', [-15, 14, -15], [31, 16, 31], 'conc');
    tube(m, 16, 32);
    // exhaust duct from the filter house: a square steel collar in the opening with its flange
    m.box('duct_floor', [0, -13, -16], [16, -12, -8], 'duct', { split: '' });
    m.box('duct_roof', [0, -1, -16], [16, 0, -8], 'duct', { split: '' });
    for (const x of [0, 15]) m.box(`duct_side_${x}`, [x, -12, -16], [x + 1, -1, -8], 'duct', { split: '' });
    m.box('duct_flange', [-1, -14, -16], [17, 1, -15.4], 'ci', { split: '', hide: ['north'] });
    m.box('duct_dark', [1, -12, -8.2], [15, -1, -8], 'soot', { split: '' });
    // stack monitor: sampling line down the west side into the sampler cabinet, the cabinet's lamp
    m.box('sample_line', [-14.8, -6, 6.6], [-14, 32, 7.4], 'ss', { split: 'y' });
    m.box('sampler', [-16, -13, 2], [-14, -2, 12], 'pnt', { split: '' });
    m.box('glow_sampler_lamp', [-15.6, -4, 1.8], [-14.4, -3, 2], 'gs', { split: '' });
    m.box('door', [30, -13, 2], [30.4, -1, 10], 'ci', { split: '' });
    m.box('door_bar', [30.4, -8, 3], [30.8, -7, 9], 'st', { split: '' });
    m.decal('sign', [3, 4, -14.1], [9, 10, -14], 'conc', 'north', 'pl.sign');
    return m;
  }

  function shaft() {
    const m = mk();
    tube(m, -16, 32);
    rungs(m, -16, 32);
    m.box('sample_line', [C[0] - RS - 0.8, -16, 6.6], [C[0] - RS, 32, 7.4], 'ss', { split: 'y' });
    return m;
  }

  function cap() {
    const m = mk();
    tube(m, -16, 4);
    rungs(m, -16, 14);
    m.box('sample_line', [C[0] - RS - 0.8, -16, 6.6], [C[0] - RS, 14, 7.4], 'ss', { split: 'y' });
    // aviation bands, red and white, then the head ring and the flue
    m.cylY('band_r1', C, RS, 4, 10, 'gs');
    m.cylY('band_w', C, RS, 10, 16, 'clad');
    m.cylY('band_r2', C, RS, 16, 22, 'gs');
    m.ringY('head_ring', C, RS + 0.6, RS + 0.6 - RI, 22, 23.4, 'conc');
    m.cylY('flue', C, RI, 20, 22.8, 'soot', { cap: 'material/soot' });
    // service platform at the band joint with its grating, brackets and handrail
    m.ringY('platform', C, RS + 4, 4, 14, 14.6, 'grate');
    for (const a of [0, 90, 180, 270]) m.radial(`bracket_${a}`, C, RS, RS + 4, a + 45, 0.8, 11, 14, 'frame');
    m.railY('rail', C, RS + 3.8, 14.6, 'yel');
    // isokinetic sampling probe through the wall, from the sampling line
    m.box('probe', [C[0] - RS - 0.8, 12, 6.6], [C[0] - RS + 3, 12.8, 7.4], 'ss', { split: '' });
    // obstruction lamps at four points round the head
    for (const [x, z] of [[C[0] + RS + 0.6, 8], [C[0] - RS - 0.6, 8], [8, C[1] + RS + 0.6], [8, C[1] - RS - 0.6]]) {
      m.box(`lamp_post_${x}_${z}`, [x - 0.4, 23.4, z - 0.4], [x + 0.4, 25, z + 0.4], 'st', { split: '' });
      m.box(`glow_lamp_${x}_${z}`, [x - 0.8, 25, z - 0.8], [x + 0.8, 26.6, z + 0.8], 'gs', { split: '' });
    }
    m.box('conductor', [8 - 0.4, 23.4, C[1] + RS - 1], [8 + 0.4, 31, C[1] + RS - 0.2], 'br', { split: '' });
    return m;
  }

  // Air through both filters along x: duct collars on the west and east faces.
  function collars(m) {
    for (const [n, x0, x1, d] of [['w', 0, 1.4, 'west'], ['e', 14.6, 16, 'east']]) {
      m.box(`collar_${n}`, [x0, 3, 3], [x1, 13, 13], 'duct', { split: '', hide: [d] });
      m.box(`collar_hole_${n}`, [d === 'west' ? x0 : x1 - 0.01, 4, 4], [d === 'west' ? x0 + 0.01 : x1, 12, 12], 'soot', { split: '' });
    }
  }

  function hepa() {
    const m = mk('electronics/ss');
    m.box('housing', [1.4, 1, 1.4], [14.6, 15, 14.6], 'ss', { split: '' });
    for (const [x, z] of [[1.4, 1.4], [13.6, 1.4], [1.4, 13.6], [13.6, 13.6]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1, 1, z + 1], 'frame', { split: '' });
    collars(m);
    // access door (bag-out port) with two clamp handwheels, the dP gauge and its tubing
    m.box('door', [3, 3, 0.8], [13, 13, 1.4], 'ss', { split: '' });
    m.box('door_gasket', [2.6, 2.6, 1], [13.4, 13.4, 1.4], 'dark', { split: '' });
    for (const y of [5, 11]) {
      m.box(`clamp_${y}`, [7.6, y - 0.4, 0.2], [8.4, y + 0.4, 0.8], 'bright', { split: '' });
      m.ring(`clamp_wheel_${y}`, 'z', [8, y], 1.4, 0.4, 0, 0.3, 'red');
    }
    m.prism('dp_gauge', 'z', [12, 14], 1.4, 0.6, 1.4, 'st', { split: '', ends: ['north'] });
    m.plate('dp_face', [10.8, 12.8, 0.5], [13.2, 15.2, 0.6], 'st', 'north', 'material/gauge_dial');
    m.box('dp_tube', [13.6, 9, 1], [14, 13.6, 1.4], 'br', { split: '' });
    m.box('label', [3, 13.6, 1.3], [8, 14.6, 1.4], 'yel', { split: '' });
    return m;
  }

  function charcoal() {
    const m = mk('vver/paint');
    m.box('housing', [1.4, 1, 1.4], [14.6, 14, 14.6], 'pnt', { split: '' });
    for (const [x, z] of [[1.4, 1.4], [13.6, 1.4], [1.4, 13.6], [13.6, 13.6]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1, 1, z + 1], 'frame', { split: '' });
    collars(m);
    // fill hatch on top, drain hopper underneath
    m.cylY('fill_hatch_c', [8, 8], 2.4, 14, 15, 'ci', { cap: 'vver/flange' });
    m.box('hatch_handle', [7, 15, 7.6], [9, 15.6, 8.4], 'st', { split: '' });
    m.box('drain_hopper', [5, 0.2, 5], [11, 1, 11], 'ci', { split: '' });
    // row of test canisters on the north face, the bed thermometer and the stencilled label
    for (let k = 0; k < 4; k++) {
      const x = 3 + k * 3;
      m.cylY(`canister_${k}`, [x + 0.8, 0.9], 0.8, 5, 9, 'ss');
      m.box(`canister_cap_${k}`, [x + 0.2, 9, 0], [x + 1.4, 9.6, 1.2], 'dark', { split: '' });
    }
    m.prism('thermo', 'z', [8, 11.6], 1.2, 0.6, 1.4, 'st', { split: '', ends: ['north'] });
    m.plate('thermo_face', [6.9, 10.5, 0.5], [9.1, 12.7, 0.6], 'st', 'north', 'material/gauge_dial');
    m.box('label', [2.6, 2.4, 1.3], [13.4, 3.6, 1.4], 'yel', { split: '' });
    return m;
  }

  const PARTS = {
    base: { id: 'vent_stack_real_base', draw: base }, shaft: { id: 'vent_stack_real', draw: shaft }, cap: { id: 'vent_stack_real_cap', draw: cap },
    hepa: { id: 'hepa_filter_real', draw: hepa }, charcoal: { id: 'charcoal_filter_real', draw: charcoal },
  };
  window.VSR = {
    PARTS,
    build: (root, p) => { const m = PARTS[p].draw(); PCART.realize(root, m, PARTS[p].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, p) => PCART.save(root, 'ventilation', PARTS[p].id),
    preview: (root, n) => {
      const m = base();
      for (let i = 1; i < n - 1; i++) m.merge(shaft(), 0, 48 * i, 0, `s${i}_`);
      m.merge(cap(), 0, 48 * (n - 1), 0, 'cap_');
      m.merge(hepa(), 0, -16, -48, 'hepa_'); m.merge(charcoal(), 16, -16, -48, 'char_');
      PCART.realize(root, m, 'vent_stack_preview');
      return m.els.length;
    },
  };
})();
