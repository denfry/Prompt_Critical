// Drilling drawworks at real scale (1 block = 1 m, 16 px = 1 m) of the 1950s-60s Soviet U2-5-5 class for a 3000 m
// rig: the grooved hoisting drum with the drill line, two band brakes on its rims worked by the driller's lever,
// the chain transmission case taking the drive at the west end, the hydrodynamic auxiliary brake coupled at the east
// end, the cathead shaft above with the friction and spinning catheads, all on a skid of rolled beams.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   DWK.build(repo); /* wait */ DWK.save(repo)
//   DWK.buildPartial(repo, p); DWK.savePartial(repo, p)    for drum, brake_lever, cathead
//   DWK.preview(repo)
//
// Controller block = (0, 0, 0), front north (the drill line leaves the drum to the north, up to the crown). Footprint
// x -2..3, y 0..2, z -1..1 (6 x 3 x 3). Drum axis along x through (y 22, z 8), the barrel over the controller.
// Connections (px): Create shaft in on the west face of (-2, 0, 0) at (-32, 12, 8) into the chain case (axis along x);
// drill line out off the top of the drum toward the north at (8, 31, 2), on the north face of (0, 1, -1) at
// (8, 31, -16) as drawn up to the derrick; cooling water to the hydrodynamic brake in on the south face of (2, 0, 1)
// at (40, 8, 32).
// Partials (drawworks_real/partial/*):
// - drum: shaft, barrel with the drill line wraps, flanges and brake rims; turns about x at (y 22, z 8).
// - brake_lever: the driller's brake handle; turns about x at (y 14, z -10), 0 deg = brake on, -30 deg = released
//   (lifted toward the driller); stored +12 z, draw at -12 z.
// - cathead: the friction cathead and the spinning cathead (the shaft stays in the model); turn about x at
//   (y 38, z 22); stored -24 x, -16 y, draw at +24 x, +16 y.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, rope: { tex: 'vver/dark', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/paint' });
  const D = [22, 8], CH = [38, 22];

  function skid(m) {
    for (const z of [-15, 27]) {
      m.box(`skid_web_${z}`, [-32, 0, z + 1.6], [64, 6, z + 2.4], 'frame', { split: 'x' });
      m.box(`skid_fl_lo_${z}`, [-32, 0, z], [64, 1, z + 4], 'frame', { split: 'x' });
      m.box(`skid_fl_hi_${z}`, [-32, 5, z], [64, 6, z + 4], 'frame', { split: 'x' });
    }
    for (const x of [-31, -14, 6, 28, 46, 60]) m.box(`cross_${x}`, [x, 0, -11], [x + 3, 5, 27], 'frame', { split: 'z' });
    m.box('deck', [-31, 6, -15], [64, 7, 31], 'chequer', { split: 'xz' });
    // tow eyes on the skid ends
    for (const z of [-13, 29]) m.box(`tow_${z}`, [-32, 1, z - 1], [-31, 5, z + 1], 'ci', { split: '' });
  }

  function drumFrame(m) {
    // bearing pedestals at both ends of the drum shaft
    for (const x of [-16, 26]) {
      m.box(`ped_${x}`, [x, 7, 0], [x + 6, 16, 16], 'pnt', { split: '' });
      m.prism(`ped_cap_${x}`, 'x', D, 5, x, x + 6, 'pnt', { split: '' });
      m.box(`ped_bolts_${x}`, [x + 1, 26.5, 5], [x + 5, 27.6, 11], 'st', { split: '' });
    }
    // brake bands round the rims, anchored below; equalizer bar under the drum
    for (const x of [-10, 21]) {
      m.ring(`band_${x}`, 'x', D, 10.8, 0.8, x - 0.2, x + 5.2, 'dark');
      m.box(`band_anchor_${x}`, [x, 7, 13], [x + 5, 12, 17], 'ci', { split: '' });
    }
    m.box('equalizer', [-10, 9, -4], [26, 11, -2], 'st', { split: 'x' });
    m.box('brake_shaft', [-12, 13, -11], [32, 15, -9], 'bright', { split: 'x' });
    for (const x of [-12, 30]) m.box(`brake_shaft_brg_${x}`, [x, 7, -12], [x + 2, 16, -8], 'ci', { split: '' });
    // guard behind and over the drum (the line leaves at the front)
    m.box('guard_back', [-6, 7, 20], [22, 34, 21], 'yel', { split: 'x' });
    m.box('guard_top', [-6, 33, 11], [22, 34, 21], 'yel', { split: 'x' });
    m.box('guard_end_w', [-6, 26, 11], [-5, 34, 20], 'yel', { split: '' });
    m.box('guard_end_e', [21, 26, 11], [22, 34, 20], 'yel', { split: '' });
  }

  function transmission(m) {
    // chain case at the west end: input shaft below, drum and cathead sprockets inside
    m.box('case', [-31.4, 7, -12], [-16, 44, 28], 'pnt', { split: '' });
    m.box('case_rib', [-32, 7, 6], [-31.4, 44, 10], 'pnt', { split: '' });
    for (const [y, z] of [[12, 8], [22, 8], [38, 22]]) m.disc(`case_cover_${y}`, 'x', [y, z], 4, -31.92, -31.5, 'ci', 'vver/flange', 'west');
    m.prism('input_shaft', 'x', [12, 8], 2, -32, -31.9, 'bright', { split: '', ends: ['west'] });
    m.box('oil_level', [-31.6, 16, -6], [-31.4, 20, -4], 'st', { split: '', face: { west: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    m.box('breather', [-26, 44, 0], [-24, 46, 2], 'ci', { split: '' });
    m.box('lube_line', [-24, 40, -12.6], [-22, 41, 30], 'oil', { split: 'z' });
    m.decal('plate', [-30, 30, -12.2], [-24, 33.6, -12], 'pnt', 'north', 'pl.maker');
    // clutch housing between the case and the drum shaft
    m.prism('clutch', 'x', D, 7, -16, -11, 'ci', { split: '' });
    // shift levers on the case front
    for (let k = 0; k < 2; k++) m.box(`shift_${k}`, [-22 + k * 3, 16, -14], [-21 + k * 3, 26, -12], 'red', { split: '' });
  }

  function auxBrake(m) {
    // hydrodynamic brake on the east end of the drum shaft through a jaw clutch, with its water piping
    m.prism('jaw_clutch', 'x', D, 4.6, 32, 35, 'ci', { split: '' });
    m.prism('hydro', 'x', D, 11, 35, 48, 'pnt', { split: 'x' });
    m.disc('hydro_cover', 'x', D, 11.2, 48, 49, 'ci', 'vver/flange', 'east');
    m.box('hydro_foot', [36, 7, 0], [48, 12, 16], 'pnt', { split: '' });
    m.box('water_in', [40, 7, 16], [43, 10, 32], 'gw', { split: 'z' });
    m.box('water_in_drop', [40, 10, 14], [43, 14, 17], 'gw', { split: '' });
    m.box('water_out', [44, 30, 6], [47, 34, 9], 'gw', { split: '' });
    m.box('water_out_run', [44, 33, 6], [47, 36, 31], 'gw', { split: 'z' });
    m.box('water_valve', [39, 10, 26], [44, 13, 29], 'red', { split: '' });
  }

  function catheadFrame(m) {
    // cathead shaft bearings on posts over the drum, the spinning cathead's air cylinder at the east end
    for (const x of [-14, 24, 52]) {
      m.box(`ch_post_${x}`, [x, 7, 19], [x + 4, 34, 25], 'pnt', { split: 'y' });
      m.prism(`ch_brg_${x}`, 'x', CH, 3.6, x, x + 4, 'ci', { split: '' });
    }
    m.prism('air_cyl', 'x', [41, 12], 2.4, 52, 62, 'st', { split: '', ends: ['east'] });
    // drill line lead-off from the top of the drum toward the derrick (north)
    m.box('line_off', [7.4, 30.4, -16], [8.6, 31.6, 8], 'rope', { split: 'z' });
    m.prism('ch_shaft', 'x', CH, 1.6, -16, 56, 'bright', { split: 'x' });
    m.box('air_cyl_post', [58, 7, 10], [62, 39, 14], 'pnt', { split: 'y' });
  }

  // ------------------------------------------------------------------ partials
  function drum(m) {
    m.prism('shaft', 'x', D, 2.4, -16, 32, 'bright', { split: 'x' });
    m.prism('barrel', 'x', D, 6.6, -5, 21, 'ci', { split: 'x' });
    // drill line wraps on the barrel: three layers' worth of turns, a darker spiral band every other turn
    m.prism('rope', 'x', D, 8, -4.8, 20.8, 'rope', { split: 'x' });
    for (let x = -4; x < 20.5; x += 2) m.ring(`wrap_${x}`, 'x', D, 8.3, 0.5, x, x + 0.8, 'dark');
    for (const x of [-6, 21]) m.disc(`flange_${x}`, 'x', D, 9.6, x, x + 1, 'ci', 'vver/flange', x < 0 ? 'east' : 'west');
    for (const x of [-10, 21]) m.prism(`rim_${x}`, 'x', D, 10, x === 21 ? 22 : -10, x === 21 ? 26 : -6, 'bright', { split: '' });
  }
  function brakeLever(m) {
    m.box('lever_hub', [28, 12, -12], [32, 16, -8], 'ci', { split: '' });
    // handle standing up and leaning toward the driller on the north side
    const r = { axis: 'x', angle: -22.5, origin: [30, 14, -10] };
    m.box('lever', [29.2, 14, -10.8], [30.8, 30, -9.2], 'red', { split: '', rot: r });
    m.box('lever_grip', [29, 25, -11.2], [31, 29.6, -8.8], 'dark', { split: '', rot: r });
  }
  function cathead(m) {
    m.prism('friction', 'x', CH, 5, 30, 36, 'st', { split: '', ends: ['west', 'east'] });
    m.prism('friction_flange', 'x', CH, 6.4, 29, 30, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('spinning', 'x', CH, 4, 44, 51, 'st', { split: '', ends: ['west', 'east'] });
    m.prism('spinning_flange', 'x', CH, 5.4, 43, 44, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('spinning_nose', 'x', CH, 3, 51, 52, 'ci', { split: '', ends: ['west', 'east'] });
  }

  const PART = {
    drum: { draw: drum, shift: [0, 0, 0], note: 'turns about x at (y 22, z 8)' },
    brake_lever: { draw: brakeLever, shift: [0, 0, 12], note: 'turns about x at (y 14, z -10); draw at -12 z' },
    cathead: { draw: cathead, shift: [-24, -16, 0], note: 'turns about x at (y 38, z 22); draw at +24 x, +16 y' },
  };
  function model() { const m = mk(); skid(m); drumFrame(m); transmission(m); auxBrake(m); catheadFrame(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'drawworks_real';
  window.DWK = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'drilling'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'drilling'),
    preview: (root) => { const m = model(); for (const [k, p] of Object.entries(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, k + '_'); } PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
