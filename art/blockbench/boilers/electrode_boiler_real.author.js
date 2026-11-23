// 6.3 kV electrode boiler at real scale (1 block = 1 m, 16 px = 1 m), a jet-type unit of about 6 MW like the KEV /
// Parat IEH series: a stainless vertical vessel 1.5 m across and about 3.4 m tall on a skirt, a dished head carrying
// the three porcelain HV bushings under the cable termination box, the circulation pump and the feedwater line at
// the foot, the steam outlet to the boiler house header at the back, the level gauge, the safety valve and the
// control cabinet with its lamps on the front.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   EBR.build(repo); /* wait */ EBR.save(repo)
//   EBR.preview(repo) is the same model (no partials)
//
// Controller block = (0, 0, 0), front north. Footprint x 0..1, y 0..3, z 0..1. The vessel axis is vertical through
// (x 16, z 18). Connections (px): steam out on the south face of (0, 2, 1) at (12, 40, 32), to the header of the
// boiler it stands against; feedwater in on the south face of (1, 0, 1) at (26, 6, 32); the 6.3 kV cable comes down
// to the termination box on top, its gland on the top face of (1, 3, 1) at (24, 64, 22) (the code takes the cable
// on any face except the front).
// No partials: the RUNNING state can light the cabinet lamp through a state model if wanted (glow_ elements here
// are the cabinet's run lamp, lit in the model as drawn).
(function () {
  const C = [16, 18], RV = 12, VY = [8, 50];           // vessel axis [x, z], radius, shell y span
  const MAT = Object.assign({}, PCVV.MAT, {
    ss: { tex: 'electronics/ss', P: 16 }, por: { tex: 'disconnector/porcelain', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 },
    p_steam: { tex: 'plant/pipe_steam', P: 16 }, p_water: { tex: 'plant/pipe_water', P: 16 }, frame: { tex: 'plant/structure', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'electronics/ss' });

  function vessel(m) {
    m.box('plinth', [1, 0, 3], [31, 2, 32], 'conc', { split: '' });
    m.cylY('skirt', C, RV - 1, 2, VY[0], 'frame');
    m.ringY('base_ring', C, RV + 0.6, 1, 2, 3, 'ci');
    m.headDown('bottom', C, RV, VY[0] + 2, 2, 2, 'ss');
    m.cylY('shell', C, RV, VY[0] + 2, VY[1], 'ss');
    for (const y of [20, 36]) m.ringY(`girth_${y}`, C, RV + 0.2, 0.4, y, y + 0.6, 'ss');
    m.ringY('head_flange', C, RV + 1, 1, VY[1] - 1, VY[1] + 0.4, 'ci');
    m.nutsY('head_bolts', C, RV + 0.5, 16, VY[1] + 0.4, 0.6, 0.7);
    m.headUp('head', C, RV, VY[1] + 0.4, 4, 3, 'ss', 'vver/cap');
    m.decal('plate', [C[0] - 3, 28, C[1] - RV - 0.3], [C[0] + 3, 32, C[1] - RV - 0.2], 'ss', 'north', 'pl.maker');
  }

  // Three bushings at 120 degrees on the head, the copper leads up into the termination box, the cable gland on top.
  function hv(m) {
    const top = VY[1] + 4.4;
    for (let k = 0; k < 3; k++) {
      const a = (90 + k * 120) * Math.PI / 180, x = C[0] + 6 * Math.cos(a), z = C[1] - 6 * Math.sin(a);
      m.cylY(`bushing_base_${k}`, [x, z], 2.2, top - 1.2, top, 'ci');
      for (let s = 0; s < 4; s++) m.cylY(`shed_${k}_${s}`, [x, z], s % 2 ? 1.4 : 2, top + s * 1.4, top + s * 1.4 + 1.4, 'por');
      m.cylY(`lead_${k}`, [x, z], 0.5, top + 5.6, 58, 'cu');
    }
    m.box('term_box', [5, 58, 7], [27, 62, 29], 'frame', { split: '' });
    m.box('term_lid', [4.6, 62, 6.6], [27.4, 62.6, 29.4], 'frame', { split: '' });
    m.cylY('cable_gland', [24, 22], 1.8, 62.6, 64, 'dark');
    for (const [x, z] of [[6, 8], [25, 8], [6, 27], [25, 27]]) m.box(`term_post_${x}_${z}`, [x, VY[1] + 2, z], [x + 1, 58, z + 1], 'frame', { split: '' });
    // yellow HV warning plate on the box front
    m.box('hv_plate', [13.6, 58.8, 6.6], [18.4, 61.6, 7], 'yel', { split: '' });
  }

  function piping(m) {
    // steam outlet from the upper shell out of the back face, a stop valve on it
    m.prism('steam_out', 'z', [12, 40], 2, C[1] + RV - 1, 32, 'p_steam', { split: '' });
    m.disc('steam_flange', 'z', [12, 40], 3, 31.2, 31.9, 'ci', 'vver/flange', 'south');
    m.box('steam_valve', [9.6, 37.6, 26], [14.4, 42.4, 29], 'gm', { split: '' });
    m.cylY('steam_spindle', [12, 27.5], 0.4, 42.4, 45, 'bright');
    m.wheelY('steam_wheel', 12, 45.4, 27.5, 1.8, 'red');
    // safety valve on the head
    m.cylY('sv_branch', [8, 28], 1, VY[1] + 1, 58, 'p_steam');
    m.box('sv_body', [6.6, 52, 26.6], [9.4, 55, 29.4], 'red', { split: '' });
    // circulation pump at the foot on the west side, feedwater in from the back
    m.prism('pump_casing', 'z', [4, 6], 3, 12, 20, 'gw', { split: '', ends: ['north', 'south'] });
    m.prism('pump_motor', 'z', [4, 6], 2.6, 4, 12, 'gw', { split: '', ends: ['north'] });
    m.box('pump_feet', [1, 2, 5], [7, 3.4, 19], 'ci', { split: '' });
    m.prism('pump_to_vessel', 'x', [6, 16], 1.2, 7, C[0] - RV + 2, 'gw', { split: '' });
    m.prism('feed_in', 'z', [26, 6], 1.2, C[1] + RV - 3, 32, 'gw', { split: '' });
    m.disc('feed_flange', 'z', [26, 6], 2, 31.2, 31.9, 'ci', 'vver/flange', 'south');
    m.cylY('feed_drop', [26, C[1] + RV - 3], 1.2, 6, VY[0] + 3, 'gw');
  }

  // Front: level gauge on the shell, the control cabinet beside the vessel with its lamps and mode switch.
  function front(m) {
    m.box('gauge_top', [21, 42, C[1] - RV + 1], [22.4, 43.4, C[1] - RV + 3.6], 'st', { split: '' });
    m.box('gauge_bot', [21, 14, C[1] - RV + 1], [22.4, 15.4, C[1] - RV + 3.6], 'st', { split: '' });
    m.plate('gauge_glass', [21, 15.4, C[1] - RV + 0.6], [22.4, 42, C[1] - RV + 1.4], 'st', 'north', 'boilers/level');
    m.box('cabinet', [1, 2, 1.4], [11, 30, 6], 'pnt', { split: '' });
    m.box('cabinet_door', [1.6, 4, 1], [10.4, 28, 1.4], 'pnt', { split: '' });
    m.box('glow_run_lamp', [3, 24, 0.6], [5, 26, 1], 'gs', { split: '' });
    m.box('trip_lamp', [6.6, 24, 0.6], [8.6, 26, 1], 'yel', { split: '' });
    m.box('mode_switch', [4.6, 18, 0.4], [7, 20.4, 1], 'dark', { split: '' });
    m.box('ammeter', [3.4, 10, 0.7], [8.6, 15, 1], 'st', { split: '', face: { north: { tex: 'material/gauge_dial', uv: [0, 0, 16, 16] } } });
  }

  function model() { const m = mk(); vessel(m); hv(m); piping(m); front(m); return m; }
  const ID = 'electrode_boiler_real';
  window.EBR = {
    ID, model,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'boilers'),
  };
})();
