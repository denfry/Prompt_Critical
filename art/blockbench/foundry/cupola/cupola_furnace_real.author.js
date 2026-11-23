// Foundry cupola at real scale (1 block = 1 m, 16 units = 1 m): Kirk's 24-inch cupola ("The Cupola Furnace",
// 1899) for the sim's 1.2 t/h. A riveted shell ⌀0.85 m on four cast posts over the drop-bottom doors (bottom plate
// 1 m up), the air chamber round two rows of tuyeres with peep sights, the fan blower on its bracket driven from a
// Create shaft, the tapping spout in front and the slag spout to the west, the charging door at 3.6 m opening onto
// the iron charging platform with its rail and ladder, the casing run on as the stack to the spark-arrester drum and
// hood. Dimensions and sources: docs/research/cupola-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   CUPR.build(repo); /* wait */ CUPR.save(repo)          static model: wholemodels/cupola_furnace_real.json
//   CUPR.buildPartial(repo, name); CUPR.savePartial(repo, name)   for glow, door_glow, drop_doors, blower_shaft
//   CUPR.preview(repo)                                    everything, for looking only
//
// Controller block = (0, 0, 0) on the floor at the front centre, front north. Footprint x -1..1, y 0..7, z 0..3;
// cupola axis at (8, ., 24). Connections (cell, face): metal out north of (0, 0, 0), spout lip (8, 14.8, 0);
// slag out west of (-1, 1, 1), lip (-16, 18, 24); shaft in east of (1, 1, 1) at (32, 24, 24); charging from the
// platform cell (0, 3, 2) through the door y 57.6..67.2 on the south of the shell. Climb cells: column (1, *, 3).
(function () {
  const C = [8, 24], RS = 6.8, RW = 9.6;           // axis, shell radius (0.85 m), air chamber radius (1.2 m)
  const YP = 16, WB = [21, 30], YD = 57.6, YF = 48, YT = 99.2;   // bottom plate top, wind box, door sill, platform, stack top
  const ZS = C[1] + RS;                              // south face of the shell, 30.8

  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const FIT = { slot_glow: [30, 10, 1, 5], soot_door: [12, 10, 8, 6], mudhole: [0, 10, 12, 5] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT }, pl: PCVV.ATLAS.pl,
  }), { particle: 'fire_tube_boiler/plate' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  // Full-bright box with the glow slice of the fittings atlas on every face.
  function glowBox(m, name, f, t, rot) {
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', rot, hide: [], face });
  }

  // ------------------------------------------------------------------ static
  function base(m) {
    for (const a of [45, 135, 225, 315]) m.postY(`post_${a}`, C, RS + 0.2, a, 1.8, 0, YP - 1.6, 'ci');
    m.cylY('bottom_plate', C, RS + 1.2, YP - 1.6, YP, 'ci', { bottom: true, cap: 'fire_tube_boiler/end_plate' });
    m.box('door_prop', [C[0] - 0.6, 0, C[1] - 0.6], [C[0] + 0.6, YP - 2.4, C[1] + 0.6], 'wood', { split: '' });
    m.box('prop_foot', [C[0] - 1.6, 0, C[1] - 1.6], [C[0] + 1.6, 0.8, C[1] + 1.6], 'ci', { split: '' });
  }

  function shell(m) {
    m.cylY('shell', C, RS, YP, YT, 'plate');
    for (const y of [36, 48, 64, 80, 96]) m.ringY(`seam_${y}`, C, RS + 0.3, 0.5, y - 0.6, y + 0.6, 'rivets');
    // air chamber round both tuyere rows, riveted top and bottom
    m.cylY('wind_box', C, RW, WB[0], WB[1], 'plate', { bottom: true, cap: 'fire_tube_boiler/end_plate' });
    m.ringY('wind_box_seam_lo', C, RW + 0.3, 0.5, WB[0], WB[0] + 1, 'rivets');
    m.ringY('wind_box_seam_hi', C, RW + 0.3, 0.5, WB[1] - 1, WB[1], 'rivets');
    // peep sights: row 1 between the posts, row 2 on the axes (the east one over the blower duct)
    for (const a of [45, 135, 225, 315]) {
      m.radial(`peep1_${a}`, C, RW, RW + 1.2, a, 1.8, 22.6, 24.4, 'br');
    }
    for (const a of [0, 90, 180, 270]) {
      m.radial(`peep2_${a}`, C, RW, RW + 1.2, a, 1.6, 26.6, 28.2, 'br');
    }
    // blast gauge on the air chamber
    m.box('gauge_pipe', [C[0] + 4.6, WB[1], C[1] - 7.4], [C[0] + 5.4, WB[1] + 3, C[1] - 6.6], 'br', { split: '' });
    m.disc('gauge_case', 'z', [C[0] + 5, WB[1] + 4.6], 1.9, C[1] - 7.6, C[1] - 6.8, 'br', 'material/brass_disc', 'north');
    m.plate('gauge_dial', [C[0] + 3.6, WB[1] + 3.2, C[1] - 7.65], [C[0] + 6.4, WB[1] + 6, C[1] - 7.6], 'br', 'north',
      'material/gauge_dial', { hide: ['south', 'east', 'west', 'up', 'down'] });
  }

  function spouts(m) {
    // tapping spout: a cast trough lined with clay from the taphole to the lip on the north face of (0, 0, 0)
    const zt = C[1] - RS + 0.4;
    m.box('tap_breast', [C[0] - 2.4, YP, zt - 1.2], [C[0] + 2.4, YP + 4, zt], 'ci', { split: '' });
    m.box('tap_hole', [C[0] - 0.8, YP + 0.4, zt - 1.25], [C[0] + 0.8, YP + 1.8, zt - 1.2], 'soot', { split: '' });
    m.box('spout_floor', [C[0] - 2.6, 13.4, 0], [C[0] + 2.6, 14.4, zt - 1.2], 'ci', { split: '' });
    m.box('spout_clay', [C[0] - 1.8, 14.4, 0], [C[0] + 1.8, 14.8, zt - 1.2], 'soot', { split: '' });
    m.box('spout_wall_w', [C[0] - 2.6, 14.4, 0], [C[0] - 1.8, 16.4, zt - 1.2], 'ci', { split: '' });
    m.box('spout_wall_e', [C[0] + 1.8, 14.4, 0], [C[0] + 2.6, 16.4, zt - 1.2], 'ci', { split: '' });
    m.box('spout_post', [C[0] - 0.8, 0, 2], [C[0] + 0.8, 13.4, 3.6], 'ci', { split: 'y' });
    // slag hole under the air chamber and its spout out to the west face of (-1, 1, 1)
    const xs = C[0] - RS + 0.4;
    m.box('slag_breast', [xs - 1.2, 16.6, C[1] - 2], [xs, 20.6, C[1] + 2], 'ci', { split: '' });
    m.box('slag_hole', [xs - 1.25, 17.8, C[1] - 0.7], [xs - 1.2, 19.2, C[1] + 0.7], 'soot', { split: '' });
    m.box('slag_floor', [-16, 17, C[1] - 2.2], [xs - 1.2, 17.8, C[1] + 2.2], 'ci', { split: 'x' });
    m.box('slag_clay', [-16, 17.8, C[1] - 1.5], [xs - 1.2, 18.1, C[1] + 1.5], 'soot', { split: 'x' });
    m.box('slag_wall_n', [-16, 17.8, C[1] - 2.2], [xs - 1.2, 19.4, C[1] - 1.5], 'ci', { split: 'x' });
    m.box('slag_wall_s', [-16, 17.8, C[1] + 1.5], [xs - 1.2, 19.4, C[1] + 2.2], 'ci', { split: 'x' });
    m.box('slag_post', [-13.6, 0, C[1] - 0.8], [-12, 17, C[1] + 0.8], 'ci', { split: 'y' });
  }

  // Fan blower in cell (1, 1, 1): spiral casing on a bracket, the outlet duct into the air chamber, the shaft
  // bearing; the shaft and coupling to the Create shaft on the east face are the partial blower_shaft.
  function blower(m) {
    const A = [24, 24];                               // shaft axis [y, z]
    m.box('fan_base', [19.6, 0, 17.6], [29.6, 1.6, 30.4], 'ci', { split: '' });
    for (const z of [18.4, 28]) m.box(`fan_leg_${z}`, [22.6, 1.6, z], [24.4, 18.2, z + 1.6], 'ci', { split: 'y' });
    m.prism('fan_casing', 'x', A, 6, 21, 26, 'green', { split: '', ends: ['east', 'west'] });
    m.prism('fan_scroll', 'x', [A[0] + 0.6, A[1] - 0.6], 6.4, 21.4, 25.6, 'green', { split: '' });
    m.prism('fan_eye', 'x', A, 2.6, 26, 27, 'ci', { split: '', ends: ['east'] });
    m.box('fan_outlet', [21, A[0] + 1.6, A[1] - 6.8], [26, A[0] + 6.4, A[1] - 0.8], 'green', { split: '' });
    m.box('blast_duct', [16.8, 22.4, A[1] - 6.4], [21, 28.8, A[1] - 1.2], 'green', { split: '' });
    m.box('duct_flange', [17, 22, A[1] - 6.8], [17.8, 29.2, A[1] - 0.8], 'ci', { split: '' });
    m.box('fan_bearing_stand', [28, 1.6, 21.6], [30.4, 21.8, 26.4], 'ci', { split: 'y' });
    m.box('fan_bearing', [27.8, 21.8, 21.8], [30.6, 26.6, 26.2], 'ci', { split: '' });
    m.box('fan_oil_cup', [28.6, 26.6, 23.4], [29.8, 28, 24.6], 'br', { split: '' });
  }

  // Charging door on the south of the shell, opening onto the platform (door swung back against the shell).
  function charging(m) {
    m.box('door_frame', [3, YD - 0.8, ZS - 0.4], [13, YD + 10.4, ZS + 0.8], 'ci', { hide: ['north'] });
    m.box('door_mouth', [3.8, YD, ZS + 0.8], [12.2, YD + 9.6, ZS + 0.85], 'soot', { split: '' });
    m.box('door_leaf', [13, YD, ZS + 0.8], [13.8, YD + 9.6, ZS + 9], 'ci', { split: '' });
    m.box('door_hinge_a', [12.6, YD + 1, ZS + 0.6], [13.8, YD + 2, ZS + 1.4], 'bright', { split: '' });
    m.box('door_hinge_b', [12.6, YD + 7.6, ZS + 0.6], [13.8, YD + 8.6, ZS + 1.4], 'bright', { split: '' });
    m.box('charge_apron', [3, YD - 1.4, ZS + 0.8], [13, YD - 0.8, ZS + 5], 'ci', { split: '' });
  }

  function platform(m) {
    const y0 = YF - 1, rest = [];
    // floor plates: behind the cupola (z 31..64), beside it (z 16..31), a hatch left open over the ladder
    rest.push(['back_w', [-16, y0, 31], [26, YF, 64]]);
    rest.push(['back_e_n', [26, y0, 31], [32, YF, 51]]);
    rest.push(['back_e_s', [26, y0, 60], [32, YF, 64]]);
    rest.push(['side_w', [-16, y0, 16], [0.4, YF, 31]]);
    rest.push(['side_e', [15.6, y0, 16], [32, YF, 31]]);
    for (const [n, f, t] of rest) m.box(`floor_${n}`, f, t, 'chequer', { hide: ['down'] });
    for (const z of [17, 40, 63]) m.box(`joist_${z}`, [-16, y0 - 1.6, z - 0.6], [32, y0, z + 0.6], 'ci', { split: 'x' });
    for (const x of [-15.2, 31.2]) for (const z of [16.4, 63.2]) m.box(`column_${x}_${z}`, [x - 0.6, 0, z - 0.6], [x + 0.6, y0 - 1.6, z + 0.6], 'ci', { split: 'y' });
    // hand rail: top and knee rails with posts, open over the ladder
    const rail = (n, f, t, ax) => {
      m.box(`rail_${n}_top`, [f[0], YF + 15, f[1]], [t[0], YF + 15.6, t[1]], 'ci', { split: ax });
      m.box(`rail_${n}_knee`, [f[0], YF + 7.6, f[1]], [t[0], YF + 8.2, t[1]], 'ci', { split: ax });
    };
    rail('w', [-16, 16], [-15.4, 64], 'z');
    rail('s', [-16, 63.4], [32, 64], 'x');
    rail('e_n', [31.4, 16], [32, 51], 'z');
    rail('n_w', [-16, 16], [0.4, 16.6], 'x');
    rail('n_e', [15.6, 16], [32, 16.6], 'x');
    for (const [x, z] of [[-15.7, 16.3], [-15.7, 32], [-15.7, 48], [-15.7, 63.7], [0, 16.3], [16, 16.3], [31.7, 16.3], [31.7, 32], [31.7, 50.6], [8, 63.7], [24, 63.7], [31.7, 63.7]]) {
      m.box(`rail_post_${x}_${z}`, [x - 0.3, YF, z - 0.3], [x + 0.3, YF + 15.6, z + 0.3], 'ci', { split: '' });
    }
    // ladder up the east side through the hatch, column (1, *, 3)
    m.box('ladder_l', [28.4, 0, 51.4], [31.6, YF + 12, 52], 'ci', { split: 'y' });
    m.box('ladder_r', [28.4, 0, 59], [31.6, YF + 12, 59.6], 'ci', { split: 'y' });
    let k = 0;
    for (let y = 4.8; y < YF + 10; y += 4.8) m.box(`ladder_rung${k++}`, [29.6, y, 52], [30.4, y + 0.6, 59], 'ci', { split: '' });
    // a charging barrow and a coke basket on the platform
    m.box('barrow_tray', [-11, YF + 2, 44], [-3, YF + 5, 52], 'ci', { split: '' });
    m.box('barrow_wheel', [-7.6, YF, 42.6], [-6.4, YF + 2.6, 44.4], 'ci', { split: '' });
    for (const x of [-10.4, -3.6]) m.box(`barrow_leg_${x}`, [x - 0.4, YF, 51], [x + 0.4, YF + 2, 51.8], 'ci', { split: '' });
    m.box('coke_heap', [-10.4, YF + 5, 44.6], [-3.6, YF + 6, 51.4], 'soot', { split: '' });
  }

  function stack(m) {
    m.cylY('arrester_skirt', C, 8.6, YT, YT + 1, 'plate', { bottom: true });
    m.cylY('arrester', C, 10.4, YT + 1, 117.6, 'plate', { bottom: true });
    for (const y of [YT + 1.4, 117]) m.ringY(`arrester_seam_${y}`, C, 10.7, 0.5, y - 0.4, y + 0.4, 'rivets');
    for (const a of [0, 90, 180, 270]) m.postY(`hood_stay_${a}`, C, 9.6, a, 0.8, 117.6, 119.4, 'ci');
    m.cylY('hood_rim', C, 11.6, 119.4, 120.6, 'plate', { bottom: true });
    m.cylY('hood_mid', C, 8.4, 120.6, 122, 'plate');
    m.cylY('hood_top', C, 4.8, 122, 123.4, 'plate', { cap: 'fire_tube_boiler/end_plate' });
  }

  // ------------------------------------------------------------------ partials (whole-model px unless shifted)
  function glow(m) {
    // only the outward tip of each peep sight and the taphole glow
    for (const a of [45, 135, 225, 315]) {
      const s = mk(); s.radial(`p1_${a}`, C, RW + 1.2, RW + 1.3, a, 1, 23, 24, 'ci');
      for (const e of s.els) glowBox(m, `peep1_${a}`, e.from, e.to, e.rot);
    }
    for (const a of [0, 90, 180, 270]) {
      const s = mk(); s.radial(`p2_${a}`, C, RW + 1.2, RW + 1.3, a, 0.9, 27, 27.8, 'ci');
      for (const e of s.els) glowBox(m, `peep2_${a}`, e.from, e.to, e.rot);
    }
    const zt = C[1] - RS + 0.4;
    glowBox(m, 'taphole', [C[0] - 0.7, YP + 0.5, zt - 1.3], [C[0] + 0.7, YP + 1.7, zt - 1.25]);
  }
  function doorGlow(m) {
    glowBox(m, 'charging', [4.2, YD + 0.4, ZS + 0.86], [11.8, YD + 9.2, ZS + 0.9]);
  }
  function dropDoors(m) {
    // two half doors under the bottom plate, hinged along x at their outer edges (z 17.6 and 30.4), shut
    m.box('drop_door_n', [C[0] - 6.4, YP - 2.4, C[1] - 6.4], [C[0] + 6.4, YP - 1.6, C[1]], 'ci', { split: '' });
    m.box('drop_door_s', [C[0] - 6.4, YP - 2.4, C[1]], [C[0] + 6.4, YP - 1.6, C[1] + 6.4], 'ci', { split: '' });
    for (const z of [C[1] - 6.4, C[1] + 5.6]) m.box(`drop_hinge_${z}`, [C[0] - 5, YP - 2.8, z], [C[0] + 5, YP - 2, z + 0.8], 'bright', { split: '' });
    m.box('drop_latch', [C[0] - 1, YP - 3, C[1] - 1], [C[0] + 1, YP - 2.4, C[1] + 1], 'bright', { split: '' });
  }
  function blowerShaft(m) {
    m.prism('shaft', 'x', [24, 24], 0.9, 26, 32, 'bright', { split: '' });
    m.prism('coupling', 'x', [24, 24], 2.4, 30.6, 32, 'forged', { split: '', ends: ['east', 'west'] });
    m.box('coupling_key', [30.4, 23.4, 21.2], [32, 24.6, 26.8], 'st', { split: '' });
  }

  const PART = {
    glow: { draw: glow, shift: [0, 0, -16], note: 'whole z = partial z + 16; full-bright while in blast' },
    door_glow: { draw: doorGlow, shift: [0, -48, 0], note: 'full-bright while in blast' },
    drop_doors: { draw: dropDoors, shift: [0, 0, 0], note: 'north door hinge axis x at (y 13.6, z 17.6), south at (y 13.6, z 30.4); open = 90 deg down' },
    blower_shaft: { draw: blowerShaft, shift: [0, 0, 0], note: 'turns about x, axis (y 24, z 24)' },
  };

  function model() {
    const m = mk();
    base(m); shell(m); spouts(m); blower(m); charging(m); platform(m); stack(m);
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

  window.CUPR = {
    model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'cupola_furnace_real'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'cupola_furnace_real', 'foundry/cupola'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, 'cupola_furnace_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'cupola_furnace_real', name, 'foundry/cupola'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, 'cupola_furnace_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
