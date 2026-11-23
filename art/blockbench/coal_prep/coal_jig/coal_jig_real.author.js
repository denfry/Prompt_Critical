// Baum jig at real scale (1 block = 1 m, 16 units = 1 m) for the sim's 36 t/h coal jig: a riveted U-tank, the
// jig bed (1.25 m wide, two compartments of 1.5 m) on the west side over its hutch hoppers, the air chamber on the
// east side under the rotary air valves, the fan and the line shaft driven from a Create shaft at the feed end,
// the feed hopper, refuse and middlings gates with their spouts, the clean-coal overflow weir and launder, and the
// make-up water pipe into the hutch. Dimensions and sources: docs/research/coal_jig-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   JIG.build(repo); /* wait */ JIG.save(repo)
//   JIG.buildPartial(repo, name); JIG.savePartial(repo, name)     for shaft_n, shaft_s, water
//   JIG.preview(repo)                                             everything, for looking only
//
// Controller block = (0, 0, 0) on the floor at the feed end, front (feed) north, coal flows +z. Footprint
// x -1..1, y 0..2, z 0..4. Connections (px): feed in top of (0, 2, 0); clean coal out south of (0, 1, 4) at
// (2, 24, 80); refuse out west of (-1, 0, 1) at (-16, 8, 24); middlings out west of (-1, 0, 3) at (-16, 8, 56);
// water in west of (-1, 1, 2) at (-16, 24, 40); shaft in north of (1, 2, 0) at (24, 40, 0).
(function () {
  const BX = [-8, 12], AX = [13, 27], WX = [-9, 28];    // bed, air chamber, tank walls (outer x)
  const ZT = [10, 62], ZC = [[12, 36], [36, 60]];        // tank, compartments
  const YH = 21, YS = 28, YW = 34, YT = 38, YA = 36;    // hutch top, screen, water, tank top, air chamber top
  const SH = [24, 40];                                   // line shaft [x, y]

  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'material/paint_green', P: 16 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/paint_green' });

  function supports(m) {
    for (const x of [-8.6, 25.8]) for (const z of [11, 35, 59]) m.box(`column_${x}_${z}`, [x, 0, z], [x + 1.6, YH, z + 1.6], 'ci', { split: 'y' });
    for (const z of [11, 35, 59]) m.box(`cross_${z}`, [-8.6, YH - 2, z], [27.4, YH, z + 1.6], 'ci', { split: 'x' });
    for (const x of [-8.6, 25.8]) m.box(`long_${x}`, [x, YH - 2, ZT[0] + 1], [x + 1.6, YH, ZT[1] - 1], 'ci', { split: 'z' });
  }

  function tank(m) {
    m.box('wall_w', [WX[0], YH, ZT[0]], [BX[0], YT, ZT[1]], 'plate', { split: 'z' });
    m.box('wall_e', [AX[1], YH, ZT[0]], [WX[1], YT, ZT[1]], 'plate', { split: 'z' });
    m.box('end_n', [WX[0], YH, ZT[0]], [WX[1], YT, ZT[0] + 1], 'plate', { split: '' });
    m.box('end_s_air', [BX[1], YH, ZT[1] - 1], [WX[1], YT, ZT[1]], 'plate', { split: '' });
    m.box('weir', [BX[0], YH, ZT[1] - 1], [BX[1], YW - 1, ZT[1]], 'plate', { split: '' });
    m.box('partition', [BX[1], 26, ZT[0] + 1], [AX[0], YT, ZT[1] - 1], 'plate', { split: 'z' });
    m.box('air_top', [AX[0], YA, ZT[0] + 1], [AX[1], YA + 1, ZT[1] - 1], 'plate', { split: 'z' });
    m.box('tank_floor', [WX[0], YH - 0.6, ZT[0]], [WX[1], YH, ZT[1]], 'plate', { split: 'z', hide: ['up'] });
    for (const [n, x0, x1] of [['w', WX[0] - 0.3, WX[0]], ['e', WX[1], WX[1] + 0.3]]) {
      for (const y of [YH + 1, YT - 1]) m.box(`seam_${n}_${y}`, [x0, y - 0.5, ZT[0]], [x1, y + 0.5, ZT[1]], 'rivets', { split: 'z' });
    }
    m.box('rim_w', [WX[0] - 0.3, YT, ZT[0]], [BX[0] + 0.3, YT + 0.6, ZT[1]], 'ci', { split: 'z' });
    m.box('rim_e', [AX[1] - 0.3, YT, ZT[0]], [WX[1] + 0.3, YT + 0.6, ZT[1]], 'ci', { split: 'z' });
    // jig bed: the screen plates and the compartment divider
    m.box('screen', [BX[0], YS, ZC[0][0]], [BX[1], YS + 0.6, ZC[1][1]], 'grate', { split: 'z' });
    m.box('divider', [BX[0], YS, 35.6], [BX[1], YS + 5, 36.4], 'plate', { split: '' });
    m.decal('maker', [WX[0] - 0.4, 29, 14], [WX[0] - 0.3, 32, 20], 'br', 'west', 'pl.maker', { hide: ['east'] });
  }

  // Hutch hoppers under each compartment, each with a screw gate and a spout out of the west side.
  function hutches(m) {
    ZC.forEach(([z0, z1], i) => {
      const zc = (z0 + z1) / 2, steps = [[17, -5, 9, z0 + 3, z1 - 3], [13, -2, 6, z0 + 6, z1 - 6]];
      for (const [y, x0, x1, za, zb] of steps) m.box(`hutch_${i}_${y}`, [x0, y, za], [x1, y + 4, zb], 'plate', { split: '' });
      m.box(`hutch_gate_${i}`, [0, 10, zc - 2], [4, 13, zc + 2], 'ci', { split: '' });
      m.box(`gate_screw_${i}`, [1.6, 13, zc - 0.4], [2.4, 16, zc + 0.4], 'bright', { split: '' });
      m.wheelY(`gate_wheel_${i}`, 2, 16.4, zc, 1.6, 'red');
      // spout from the gate to the west face: refuse (compartment 1) at z 24, middlings (compartment 2) at z 56
      const zp = i === 0 ? 24 : 56, za = Math.min(zc, zp) - 3, zb = Math.max(zc, zp) + 3;
      m.box(`spout_floor_${i}`, [-16, 6, za], [4, 7, zb], 'ci', { split: 'x' });
      m.box(`spout_wall_a_${i}`, [-16, 7, za], [4, 10, za + 0.6], 'ci', { split: 'x' });
      m.box(`spout_wall_b_${i}`, [-16, 7, zb - 0.6], [4, 10, zb], 'ci', { split: 'x' });
      m.box(`spout_back_${i}`, [3.4, 7, za], [4, 10, zb], 'ci', { split: '' });
      m.box(`spout_post_${i}`, [-14, 0, zp - 0.8], [-12.4, 6, zp + 0.8], 'ci', { split: '' });
      // refuse draw gate on the outside of the west wall at the end of the compartment
      m.box(`draw_gate_${i}`, [WX[0] - 1, 27, z1 - 4], [WX[0], 33, z1], 'ci', { split: '' });
      m.ring(`draw_wheel_${i}`, 'x', [30, z1 - 2], 2, 0.5, WX[0] - 1.8, WX[0] - 1.2, 'red');
      m.box(`draw_spindle_${i}`, [WX[0] - 1.8, 29.6, z1 - 2.4], [WX[0] - 1, 30.4, z1 - 1.6], 'bright', { split: '' });
    });
  }

  function feedAndClean(m) {
    // feed hopper over the head of the bed, coal in it
    m.box('feed_n', [-6, YW, 1], [10, 48, 1.6], 'plate', { split: '' });
    m.box('feed_w', [-6, YW, 1.6], [-5.4, 48, 12], 'plate', { split: '' });
    m.box('feed_e', [9.4, YW, 1.6], [10, 48, 12], 'plate', { split: '' });
    m.box('feed_s', [-6, YW + 4, 11.4], [10, 48, 12], 'plate', { split: '' });
    m.box('feed_rim', [-6.3, 47.4, 0.8], [10.3, 48, 12.2], 'ci', { split: '', hide: ['up'] });
    m.box('feed_coal', [-5.4, YW, 1.6], [9.4, 45, 11.4], 'dark', { split: '' });
    for (const x of [-7, 9.4]) m.box(`feed_leg_${x}`, [x, 0, 1.2], [x + 1.6, YW, 2.8], 'ci', { split: 'y' });
    // clean-coal overflow: drop chute over the weir and the launder to the south face of (0, 1, 4)
    m.box('drop_s', [BX[0] + 2, 23, ZT[1] + 1.4], [BX[1] - 2, YW, ZT[1] + 2], 'plate', { split: '' });
    m.box('drop_w', [BX[0] + 2, 23, ZT[1]], [BX[0] + 2.6, YW, ZT[1] + 1.4], 'plate', { split: '' });
    m.box('drop_e', [BX[1] - 2.6, 23, ZT[1]], [BX[1] - 2, YW, ZT[1] + 1.4], 'plate', { split: '' });
    m.box('launder_floor', [-6, 22, ZT[1]], [10, 23, 80], 'ci', { split: 'z' });
    m.box('launder_wall_w', [-6, 23, ZT[1] + 2], [-5.4, 26, 80], 'ci', { split: 'z' });
    m.box('launder_wall_e', [9.4, 23, ZT[1] + 2], [10, 26, 80], 'ci', { split: 'z' });
    m.box('launder_coal', [-5.4, 23, ZT[1] + 2], [9.4, 23.6, 80], 'dark', { split: 'z' });
    for (const z of [68, 77]) m.box(`launder_post_${z}`, [1.2, 0, z], [2.8, 22, z + 1.6], 'ci', { split: 'y' });
  }

  function water(m) {
    // make-up water into the hutch below the screen, flange on the west face of (-1, 1, 2)
    m.prism('water_pipe', 'x', [24, 40], 1.2, -14.6, WX[0], 'gw', { split: '' });
    m.prism('water_flange', 'x', [24, 40], 2.4, -16, -14.6, 'forged', { split: '', ends: ['west'] });
    m.box('water_valve', [-12.6, 22.4, 38.4], [-10.6, 25.6, 41.6], 'gm', { split: '' });
    m.box('water_spindle', [-12, 25.6, 39.6], [-11.2, 27.6, 40.4], 'bright', { split: '' });
    m.wheelY('water_wheel', -11.6, 28, 40, 1.6, 'red');
  }

  // Fan at the feed end blowing into the air chamber; the rotary air valves over the chamber on the line shaft.
  function air(m) {
    m.prism('fan_casing', 'z', SH, 6, 2, 9, 'green', { split: '', ends: ['north', 'south'] });
    m.box('fan_duct', [19, 28, 6], [29, YA, 12], 'green', { split: '' });
    m.box('fan_stand', [19, 0, 3], [29, 34, 8], 'ci', { split: 'y' });
    for (const z of [20, 44]) {
      m.prism(`valve_${z}`, 'z', SH, 3.2, z, z + 8, 'ci', { split: '', ends: ['north', 'south'] });
      m.box(`valve_neck_${z}`, [SH[0] - 2.4, YA + 1, z + 1], [SH[0] + 2.4, SH[1] - 2.6, z + 7], 'ci', { split: '' });
      m.prism(`valve_flange_${z}`, 'z', SH, 3.8, z - 0.6, z, 'ci', { split: '', ends: ['north', 'south'] });
      m.prism(`valve_flange_b_${z}`, 'z', SH, 3.8, z + 8, z + 8.6, 'ci', { split: '', ends: ['north', 'south'] });
    }
    for (const z of [12, 32, 56]) {
      m.box(`bearing_ped_${z}`, [SH[0] - 2, YA + 1, z], [SH[0] + 2, SH[1] - 1.6, z + 3], 'ci', { split: '' });
      m.box(`bearing_${z}`, [SH[0] - 2.4, SH[1] - 1.6, z], [SH[0] + 2.4, SH[1] + 1.8, z + 3], 'ci', { split: '' });
      m.box(`bearing_cup_${z}`, [SH[0] - 0.6, SH[1] + 1.8, z + 0.9], [SH[0] + 0.6, SH[1] + 3, z + 2.1], 'br', { split: '' });
    }
  }

  // ------------------------------------------------------------------ partials
  function shaftN(m) {
    m.prism('shaft_n', 'z', SH, 1, 1.6, 31, 'bright', { split: '' });
    m.prism('coupling', 'z', SH, 2.4, 0, 1.6, 'forged', { split: '', ends: ['north', 'south'] });
    m.box('coupling_key', [SH[0] - 2.8, SH[1] - 0.6, 0], [SH[0] + 2.8, SH[1] + 0.6, 1.6], 'st', { split: '' });
    m.prism('fan_hub', 'z', SH, 1.8, 9, 10.4, 'ci', { split: '', ends: ['south'] });
  }
  function shaftS(m) {
    m.prism('shaft_s', 'z', SH, 1, 31, 61, 'bright', { split: '' });
    m.box('crank_s', [SH[0] - 0.6, SH[1] - 0.6, 60], [SH[0] + 0.6, SH[1] + 3, 61.4], 'st', { split: '' });
  }
  function waterSurface(m) {
    ZC.forEach(([z0, z1], i) => m.box(`water_${i}`, [BX[0], YW - 0.6, z0 + (i ? 0.4 : 0)], [BX[1], YW, z1 - (i ? 0 : 0.4)], 'dark', { split: '' }));
  }

  const PART = {
    shaft_n: { draw: shaftN, shift: [0, 0, 0], note: 'turns about z through (x 24, y 40)' },
    shaft_s: { draw: shaftS, shift: [0, 0, -32], note: 'turns about z through (x 24, y 40); draw at +32 z' },
    water: { draw: waterSurface, shift: [0, -16, -28], note: 'bed water surface at y 34, pulses ±1 px at the jig rate; draw at (0, +16, +28)' },
  };
  function model() {
    const m = mk();
    supports(m); tank(m); hutches(m); feedAndClean(m); water(m); air(m);
    return m;
  }
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

  window.JIG = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'coal_jig_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'coal_jig_real', 'coal_prep/coal_jig'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'coal_jig_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'coal_jig_real', name, 'coal_prep/coal_jig'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'coal_jig_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
