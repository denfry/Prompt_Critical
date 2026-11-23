// Wet coke quench tower at real scale (1 block = 1 m, 16 units = 1 m): the open-bottomed spray chamber that stands
// over the quench car track on player-built piers (its skirt has bolting flanges for them), the spray header and
// nozzles fed from the west water port, a stepped hood into a 2.5 m square steel stack lined with brick, louvred
// timber dust baffles near the top and the top band. A small chute with a flap on the east face is the item
// input and output. The car stands in QuenchTowerBlockEntity.zone(), 1–4 blocks under the controller.
// Dimensions and sources: docs/research/quench_tower-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   QTWR.build(repo); /* wait */ QTWR.save(repo)
//   QTWR.buildPartial(repo, 'spray'); QTWR.savePartial(repo, 'spray')
//   QTWR.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x -1..1, y -1..11, z -1..1. Ports (px): water in west of
// (-1, -1, 0) at (-16, -8, 8); chute (items) east of (1, -1, 0) at (32, -8, 8). Steam plume at (8, 192, 8).
(function () {
  const S0 = -12, S1 = 28, YT = 176;   // stack box and top
  const MAT = Object.assign({}, PCVV.MAT);
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'material/steel' });
  const o = { split: '' };

  function chamber(m) {
    // skirt: four plate walls, open at the bottom, with an outward bolting flange
    for (const [n, f, t] of [['n', [-16, -16, -15.6], [32, 0, -14.6]], ['s', [-16, -16, 31], [32, 0, 32]],
      ['w', [-16, -16, -15], [-15, 0, 31]], ['e', [31, -16, -15], [32, 0, 31]]]) m.box(`skirt_${n}`, f, t, 'st');
    m.box('flange_n', [-16, -16, -16], [32, -15, -14.6], 'ci', o);
    m.box('flange_s', [-16, -16, 30.6], [32, -15, 32], 'ci', o);
    m.box('flange_w', [-16, -16, -14.6], [-14.6, -15, 30.6], 'ci', o);
    m.box('flange_e', [30.6, -16, -14.6], [32, -15, 30.6], 'ci', o);
    for (const x of [-12, -4, 4, 12, 20, 28]) for (const z of [-15.8, 31.4]) m.box(`bolt_${x}_${z}`, [x, -15, z], [x + 0.6, -14.4, z + 0.4], 'bright', o);
    for (const y of [-12, -6]) m.box(`skirt_band_${y}`, [-16, y, -16], [32, y + 0.8, -15.6], 'ci', o);
    // inside: soot-blackened lining and the spray header grid with nozzles
    m.box('lining_top', [-15, -1, -15], [31, 0, 31], 'soot', { split: '', hide: ['up'] });
    m.box('header_w', [-14, -5, -14], [-12.4, -3.4, 30], 'gw', o);
    m.box('header_e', [28.4, -5, -14], [30, -3.4, 30], 'gw', o);
    for (const z of [-8, 0, 8, 16, 24]) {
      m.box(`spray_pipe_${z}`, [-12.4, -4.8, z - 0.6], [28.4, -3.6, z + 0.6], 'gw', o);
      for (const x of [-6, 2, 10, 18, 26]) m.box(`nozzle_${z}_${x}`, [x - 0.4, -5.6, z - 0.4], [x + 0.4, -4.8, z + 0.4], 'br', o);
    }
    // water inlet through the west skirt with a flange and a valve
    m.prism('water_in', 'x', [-8, 8], 1.6, -16, -14, 'gw', { split: '', ends: ['west'] });
    m.ring('water_flange', 'x', [-8, 8], 2.4, 1, -16, -15.6, 'ci');
    m.box('water_valve_stem', [-15.4, -6.4, 7.6], [-14.6, -2.6, 8.4], 'bright', o);
    m.box('water_valve_wheel', [-15.6, -2.6, 6], [-14.4, -2, 10], 'red', o);
    // item chute on the east face with its flap
    m.box('chute', [28, -10, 2], [32, -2, 14], 'ci', o);
    m.box('chute_mouth', [31.9, -9, 3], [32, -3, 13], 'soot', o);
    m.box('chute_flap', [31.6, -3.4, 2.6], [32, -2.6, 13.4], 'st', o);
  }
  function hood(m) {
    m.box('hood_0', [-16, 0, -16], [32, 4, 32], 'st', { hide: ['down'] });
    m.box('hood_1', [-14, 4, -14], [30, 8, 30], 'st', { hide: ['down'] });
    m.box('hood_2', [S0 - 1, 8, S0 - 1], [S1 + 1, 10, S1 + 1], 'ci', { hide: ['down'] });
  }
  function stack(m) {
    m.box('stack', [S0, 10, S0], [S1, YT, S1], 'st', { hide: ['down', 'up'] });
    for (let y = 26; y < YT - 30; y += 24) m.box(`stiff_${y}`, [S0 - 0.6, y, S0 - 0.6], [S1 + 0.6, y + 1, S1 + 0.6], 'ci', o);
    for (const [x, z] of [[S0 - 0.8, S0 - 0.8], [S1 - 1.2, S0 - 0.8], [S0 - 0.8, S1 - 1.2], [S1 - 1.2, S1 - 1.2]]) {
      m.box(`corner_${x}_${z}`, [x, 10, z], [x + 2, YT, z + 2], 'ci', { split: 'y' });
    }
    // louvred timber dust baffles: slats on all four sides near the top
    for (let y = 148; y < 170; y += 3) {
      m.box(`louvre_n_${y}`, [S0 + 1, y, S0 - 1.4], [S1 - 1, y + 1.6, S0], 'wood', { split: '', rot: { axis: 'x', angle: 22.5, origin: [8, y + 0.8, S0 - 0.7] } });
      m.box(`louvre_s_${y}`, [S0 + 1, y, S1], [S1 - 1, y + 1.6, S1 + 1.4], 'wood', { split: '', rot: { axis: 'x', angle: -22.5, origin: [8, y + 0.8, S1 + 0.7] } });
      m.box(`louvre_w_${y}`, [S0 - 1.4, y, S0 + 1], [S0, y + 1.6, S1 - 1], 'wood', { split: '', rot: { axis: 'z', angle: -22.5, origin: [S0 - 0.7, y + 0.8, 8] } });
      m.box(`louvre_e_${y}`, [S1, y, S0 + 1], [S1 + 1.4, y + 1.6, S1 - 1], 'wood', { split: '', rot: { axis: 'z', angle: 22.5, origin: [S1 + 0.7, y + 0.8, 8] } });
    }
    m.box('top_band', [S0 - 1, YT - 4, S0 - 1], [S1 + 1, YT, S1 + 1], 'ci', o);
    m.box('top_soot', [S0 + 1, YT - 3, S0 + 1], [S1 - 1, YT - 2.9, S1 - 1], 'soot', o);
    m.box('lining_rim', [S0 + 0.01, YT - 3, S0 + 0.01], [S1 - 0.01, YT, S1 - 0.01], 'brick', { split: '', hide: ['down'] });
    // inspection ladder rungs up the north face (no climb cells: maintenance only)
    for (let y = 14; y < YT - 6; y += 5) m.box(`rung_${y}`, [6, y, S0 - 1.4], [10, y + 0.4, S0 - 0.8], 'st', o);
    m.box('rung_rail_w', [5.6, 12, S0 - 1.4], [6, YT - 6, S0], 'st', { split: 'y' });
    m.box('rung_rail_e', [10, 12, S0 - 1.4], [10.4, YT - 6, S0], 'st', { split: 'y' });
  }

  // ------------------------------------------------------------------ partial
  function spray(m) {
    for (const z of [-8, 0, 8, 16, 24]) {
      for (const x of [-6, 2, 10, 18, 26]) m.box(`curtain_${z}_${x}`, [x - 1.6, -16, z - 0.1], [x + 1.6, -5.6, z + 0.1], 'clad', o);
    }
  }
  const PART = { spray: { draw: spray, shift: [0, 16, 0], note: 'whole y = partial y - 16; shown while RUNNING' } };

  function model() { const m = mk(); chamber(m); hood(m); stack(m); return m; }
  function partial(name) {
    const src = mk(); PART[name].draw(src);
    const [dx, dy, dz] = PART[name].shift;
    return mk().merge(src, dx, dy, dz, '');
  }
  function preview() {
    const m = model();
    for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); }
    return m;
  }

  window.QTWR = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'quench_tower_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'quench_tower_real', 'coke/quench_tower'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'quench_tower_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'quench_tower_real', name, 'coke/quench_tower'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'quench_tower_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
