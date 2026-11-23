// VVER-1000 reactor at real scale (1 block = 1 m): pressure vessel with its bottom and upper heads, the main
// flange joint, the two rows of DN850 main nozzles, the support ring on its columns and the upper unit with the
// 61 control rod drive (ShEM) housings. Dimensions and sources: docs/research/vver-dimensions.md (disk only).
// Load after vverkit.js (see its header), then:
//   VVER_REACTOR.build(repo); /* wait for textures */ VVER_REACTOR.save(repo)
//   VVER_REACTOR.buildRods(repo); VVER_REACTOR.saveRods(repo)     partial: models/block/vver_reactor/partial/rods
//
// Controller block = (0, 0, 0), inside the bottom head; vessel axis through its centre (x = z = 8). Front north.
// Vessel: outer cladding diameter 4.5 m, bottom head from y = 0, main joint at 10.9 m, top of the lifting
// traverse at 19.1 m. Main nozzles (ends on block faces, axis on block centres):
//   cold legs (lower row, y = 7.5 m): east face of cells (2, 7, -1) and (2, 7, 1); west face of (-2, 7, -1), (-2, 7, 1)
//   hot legs (upper row, y = 9.5 m):  east face of cells (2, 9, -1) and (2, 9, 1); west face of (-2, 9, -1), (-2, 9, 1)
(function () {
  const C = [8, 8];
  const PITCH = 5.5;          // drive pitch on the head, units (0.34 m; a hexagonal grid as the 163-assembly core)

  // The 61 drive positions: four rings of a hexagonal grid round the centre (1 + 6 + 12 + 18 + 24).
  function drives() {
    const out = [];
    for (let q = -4; q <= 4; q++) for (let r = -4; r <= 4; r++) {
      if (Math.max(Math.abs(q), Math.abs(r), Math.abs(q + r)) > 4) continue;
      out.push([C[0] + PITCH * (q + r / 2), C[1] + PITCH * r * Math.sqrt(3) / 2]);
    }
    return out;
  }
  const HEAD_Y = 184, HEAD_D = 24, HEAD_R = 33;
  const headTop = (x, z) => {
    const rr = Math.min(HEAD_R, Math.hypot(x - C[0], z - C[1]));
    return HEAD_Y + HEAD_D * Math.sqrt(1 - Math.pow(rr / HEAD_R, 2));
  };

  // One main nozzle sticking out east (s = 1) or west (s = -1) at height y, centre z.
  function nozzle(m, name, s, y, z, band) {
    const X = (a) => (s > 0 ? a : 16 - a);
    const span = (a, b) => (s > 0 ? [a, b] : [X(b), X(a)]);
    const end = s > 0 ? 'east' : 'west';
    m.prism(`${name}_boss`, 'x', [y, z], 10, ...span(38, 42), 'clad', { split: '' });
    m.prism(`${name}_safe`, 'x', [y, z], 8, ...span(42, 46), 'forged', { split: '' });
    m.prism(`${name}_band`, 'x', [y, z], 8.3, ...span(43, 44.6), band, { split: '' });
    m.prism(`${name}_flange`, 'x', [y, z], 10.5, ...span(46, 48), 'forged', { split: '', ends: [end] });
    m.nutsX(`${name}_nut`, [y, z], 9.4, 12, ...span(45, 46), 1.4);
  }

  function model() {
    const m = PCVV.mk();
    // vessel: bottom head, shell, the thicker nozzle ring, the flange pair and the upper head
    m.headDown('bot', C, 36, 22, 22, 6, 'clad');
    m.cylY('shell', C, 36, 22, 104, 'clad');
    m.cylY('nzone', C, 37.5, 104, 164, 'clad', { seed: 2, bottom: true });
    m.cylY('vflange', C, 39, 164, 174, 'forged', { cap: 'vver/flange', bottom: true });
    m.cylY('hflange', C, 39, 174.2, 184, 'forged', { cap: 'vver/flange', seed: 4 });
    m.nutsY('nut', C, 35.6, 54, 184, 3.4, 2.6, 'bright');
    m.nutsY('stud', C, 35.6, 54, 187.4, 1.6, 1.3, 'st');
    m.headUp('uhead', C, HEAD_R, HEAD_Y, HEAD_D, 6, 'clad');
    // leak-off lines from the flange joint down the front
    for (const x of [-3, 19]) {
      m.box(`leakoff_${x}`, [x - 0.5, 144, -30.6], [x + 0.5, 172, -29.6], 'st', { split: 'y' });
      m.box(`leakoff_valve_${x}`, [x - 1.2, 150, -31.4], [x + 1.2, 152.4, -29.4], 'gw', { split: '' });
    }

    // support ring on eight columns under the nozzle ring
    m.ringY('sup_ring', C, 40, 4, 96, 104, 'pnt');
    for (let k = 0; k < 8; k++) {
      const a = k * 45 + 22.5;
      m.postY(`sup_col_${k}`, C, 38, a, 3.6, 1.5, 96, 'pnt');
      m.postY(`sup_foot_${k}`, C, 38, a, 6, 0, 1.5, 'conc', { split: '' });
      m.radial(`sup_bracket_${k}`, C, 35, 38.5, a, 3, 98, 104, 'pnt');
    }

    // main nozzles, two loops a side; hot legs (upper row) take the red band, cold legs the green one (GOST 14202)
    for (const s of [1, -1]) for (const z of [-8, 24]) {
      nozzle(m, `cold_${s}_${z}`, s, 120, z, 'gw');
      nozzle(m, `hot_${s}_${z}`, s, 152, z, 'gs');
    }
    // loop numbers between the nozzle pairs, on the nozzle ring faces east and west
    m.decal('loopno_e', [45.4, 133, 4], [46, 139, 11.2], 'clad', 'east', 'pl.n1');
    m.decal('loopno_w', [-30, 133, 4.8], [-29.4, 139, 12], 'clad', 'west', 'pl.n3');
    // front: radiation sign and the reactor plate on the nozzle ring
    m.decal('sign', [2, 124, -30.1], [14, 136, -29.4], 'clad', 'north', 'pl.sign');
    m.decal('name', [1.2, 138, -30.1], [14.8, 141.6, -29.4], 'clad', 'north', 'pl.r_name');
    m.decal('maker', [5, 84, -28.6], [11, 87.5, -27.9], 'clad', 'north', 'pl.maker');

    // upper unit: eight posts on the head, two bands, the cable deck with its rail
    for (let k = 0; k < 8; k++) m.postY(`uu_post_${k}`, C, 30, k * 45 + 22.5, 2.6, 192, 268, 'pnt');
    m.ringY('uu_band_0', C, 31.6, 2, 220, 222.5, 'pnt');
    m.ringY('uu_band_1', C, 31.6, 2, 246, 248.5, 'pnt');
    m.cylY('deck', C, 32.5, 268, 270, 'pnt', { cap: 'vver/grating_disc', bottom: true });
    m.railY('deck_rail', C, 32.2, 270, 'yel');
    m.box('tray_n', [-6, 270, -18.5], [22, 271.6, -16.5], 'ci', { split: '' });
    m.box('tray_s', [-6, 270, 32.5], [22, 271.6, 34.5], 'ci', { split: '' });

    // the 61 drive housings: tube from the head, electromagnet block, position indicator cap and plug
    drives().forEach(([x, z], i) => {
      const y0 = Math.floor(headTop(x, z) - 1);
      m.box(`crd_${i}`, [x - 1.3, y0, z - 1.3], [x + 1.3, 280, z + 1.3], 'st', { split: 'y' });
      m.box(`crd_coil_${i}`, [x - 1.8, 224, z - 1.8], [x + 1.8, 240, z + 1.8], 'drv', { split: '' });
      m.box(`crd_cap_${i}`, [x - 1.6, 280, z - 1.6], [x + 1.6, 283, z + 1.6], 'pnt', { split: '' });
      m.box(`crd_plug_${i}`, [x - 0.8, 283, z - 0.8], [x + 0.8, 284.4, z + 0.8], 'dark', { split: '' });
    });
    // in-core instrumentation nozzles on the head (thermocouple and detector channel flanges), four groups
    for (const a of [45, 135, 225, 315]) {
      const rad = a * Math.PI / 180, x = C[0] + 27 * Math.cos(rad), z = C[1] + 27 * Math.sin(rad), y0 = Math.floor(headTop(x, z) - 1);
      m.cylY(`kni_${a}`, [x, z], 2.2, y0, 206, 'forged');
      m.cylY(`kni_fl_${a}`, [x, z], 3.2, 206, 208, 'forged', { cap: 'vver/flange' });
      m.box(`kni_box_${a}`, [x - 1.5, 208, z - 1.5], [x + 1.5, 212, z + 1.5], 'pnt', { split: '' });
    }

    // drive cooling: air duct down the west side from the deck, fan at its foot on the flange
    m.box('duct', [-31.5, 200, 0], [-25.5, 264, 16], 'pnt', { split: 'y' });
    m.box('duct_elbow', [-31.5, 264, 0], [-22, 270, 16], 'pnt', { split: '' });
    m.box('fan', [-32, 188, -2], [-24, 200, 18], 'pnt', { split: '' });
    m.box('fan_motor', [-32, 190, 18], [-26, 196, 24], 'motor', { split: '' });

    // ladder from the flange up the north side to the deck
    m.ladderZ('ladder', 4, 12, -23, -1, 188, 270, 'yel', 3);

    // lifting traverse over the deck: four ties and beams to a hub with the crane eye (top at 19.1 m)
    for (const a of [45, 135, 225, 315]) {
      m.postY(`tie_${a}`, C, 22, a, 1.6, 270, 300, 'yel', { split: '' });
      m.radial(`beam_${a}`, C, 4, 23, a, 1.8, 300, 302.5, 'yel');
    }
    m.box('hub', [0, 300, 0], [16, 303, 16], 'yel', { split: '' });
    m.box('eye', [6, 303, 7], [10, 306, 9], 'st', { split: '' });
    return m;
  }

  // Partial: rod position indicator extensions over the drive caps. Drawn at the controller + (0, 17, 0) blocks;
  // they sit on the caps (local y 11..14) when the rods are in, and slide up along +y by up to 8 units when out.
  function rods() {
    const m = PCVV.mk('material/bright_steel');
    drives().forEach(([x, z], i) => {
      m.box(`rod_${i}`, [x - 0.45, 11, z - 0.45], [x + 0.45, 18, z + 0.45], 'bright', { split: '' });
      m.box(`rod_tip_${i}`, [x - 0.7, 18, z - 0.7], [x + 0.7, 19, z + 0.7], 'red', { split: '' });
    });
    return m;
  }

  window.VVER_REACTOR = {
    model, rods, drives,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'vver_reactor'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'vver_reactor'),
    buildRods: (root) => { const m = rods(); PCART.realize(root, m, 'rods'); return m.els.length; },
    saveRods: (root) => PCVV.savePartial(root, 'vver_reactor', 'rods'),
  };
})();
