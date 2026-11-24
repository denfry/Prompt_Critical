// Radiation protection units at real scale (1 block = 1 m, 16 px = 1 m): a hot cell operating station with a lead
// glass window, two CRL Model 8 class master-slave manipulators and a shielded transfer port; a CASTOR V/19 class
// spent fuel cask and its rail transfer cart; the sanitary checkpoint's portal and hand-and-foot contamination
// monitors; four 1 x 1 shield tiles; a NUHOMS class dry storage module.
// Sheet: docs/research/2026-11-24-radprot/radprot-dimensions.md (disk only); the port, pivot and partial list there
// is repeated per unit below, and this header is the final word where they differ.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then:
//   RP.paint(repo)                                   // the shield tile, glass, lamp and cask textures
//   for k in UNITS: RP.build(repo, k); /* wait */ RP.save(repo, k)
//   for (const p of RP.parts(k)) { RP.buildPartial(repo, k, p); RP.savePartial(repo, k, p) }
//
// Every unit: controller (0, 0, 0), front north.
// hot_cell (hot_cell_real, lit variant hot_cell_real_on with the cell lamps and the window glowing), x -2..2, y 0..3,
//   z -1..2: operator side z -16..0, wall z 0..16, cell z 16..48. Power on the west face of (-2, 0, -1) at
//   (-32, 8, -8); container transfer on the north face of (2, 0, -1) at (40, 16, -16); cell exhaust on the top face of
//   (-2, 3, 2) at (-24, 64, 40); cell drain on the bottom face of (0, 0, 2) at (8, 0, 40). Partials master_handle_l /
//   master_handle_r (grips, turn about x at (x, 23, -6), x = -12 / 28), slave_tong_l / slave_tong_r (jaws, turn about
//   x at (x, 21, 20.5)), transfer_gate (slides y 0..14; stored -16 x). None of them shifted except the gate.
// cask (spent_fuel_cask_real), x -1..1, y 0..5, z -1..1, axis (8, y, 8): lid seal monitoring on the top face of
//   (0, 5, 0) at (8, 96, 8). Partial protective_plate (lifted off by the crane, slides y 0..24; stored -80 y).
// cart (cask_transport_cart_real, loaded variant cask_transport_cart_real_loaded with the cask lying in the cradle,
//   axis along x at (y 36.5, z 8)), x -4..4, y 0..3, z -1..1: power (cable reel) on the east face of (4, 0, 0) at
//   (80, 8, 8). Partials wheelset_0..3: turn about z at (x, 5) for x = -48, -36, 52, 64; stored +56, +44, -44, -56 x
//   (all drawn at x 8).
// portal (portal_monitor_real, alarm variant portal_monitor_real_alarm), x -1..1, y 0..2, z 0: passage is the
//   controller cell, walk along z. Power on the bottom face of (-1, 0, 0) at (-8, 0, 8). Partial status_lamp: beacon
//   shroud, turns about y at (8, 8), y 43..46.4; stored -24 y.
// hand_foot (hand_foot_monitor_real, alarm variant hand_foot_monitor_real_alarm), x 0, y 0..1, z 0: power on the
//   bottom face of (0, 0, 0) at (8, 0, 12). Partial status_lamp: beacon shroud, turns about y at (8, 12),
//   y 27..30.4; no shift.
// tiles shield_heavy_concrete_real, shield_lead_brick_real, shield_borated_water_tank_real, shield_borated_pe_real:
//   one block each, no ports, no partials.
// dry_storage (dry_storage_module_real), x -1..1, y 0..4, z 0..5: canister transfer on the north face of (0, 1, 0) at
//   (8, 32, 0); air in on the north face of (-1, 0, 0) at (-10, 6, 0) and of (1, 0, 0) at (26, 6, 0); air out on the
//   top face of (-1, 4, 4) at (-10, 80, 76) and of (1, 4, 4) at (26, 80, 76). Partial door (slides z 0..-24 when
//   removed; stored -16 y).
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    frame: { tex: 'plant/structure', P: 16 }, blue: { tex: 'plant/blue', P: 16 },
    hc: { tex: 'radprot/heavy_concrete', P: 16 }, lead: { tex: 'radprot/lead_brick', P: 16 },
    bwt: { tex: 'radprot/borated_water_tank', P: 16 }, bpe: { tex: 'radprot/borated_pe', P: 16 },
    glass: { tex: 'radprot/lead_glass', P: 16 }, lamp: { tex: 'radprot/lamp', P: 16 }, cask: { tex: 'radprot/cask', P: 16 },
  });
  const mk = (p) => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: p || 'vver/paint' });

  // ------------------------------------------------------------------ textures (32 px per block)
  function paint(root) {
    const { img, put, get, add, mix, rng, grain, rect, writePng } = PCART;
    const out = (n, im) => writePng(im, require('path').join(root, PCART.ASSETS, 'textures/block/radprot', n + '.png'));
    // magnetite heavy concrete: dark brown-grey, black and rusty aggregate, two formwork tie holes
    let im = img(32, 32), r = grain(im, [96, 90, 84], 301, 5, 8, 8);
    for (let k = 0; k < 80; k++) {
      const x = Math.floor(r() * 32), y = Math.floor(r() * 32), c = r() < 0.75 ? [46, 44, 42] : [92, 66, 50];
      put(im, x, y, c); if (r() < 0.45) put(im, x + 1, y, add(c, 6)); if (r() < 0.3) put(im, x, y + 1, add(c, -4));
    }
    for (const [x, y] of [[7, 7], [23, 23]]) {
      rect(im, x - 1, y - 1, 4, 4, [118, 112, 104]); rect(im, x, y, 2, 2, [40, 38, 36]);
    }
    out('heavy_concrete', im);
    // interlocking chevron lead bricks on edge (100 x 200 mm faces = 4 x 8 px here), V end joints, oxide bloom
    im = img(32, 32); r = rng(311);
    for (let j = 0; j < 8; j++) {
      const y0 = j * 4, off = (j % 2) * 4;
      for (let k = 0; k < 5; k++) {
        const shade = (r() - 0.5) * 12;
        for (let dy = 0; dy < 3; dy++) for (let i = 0; i < 8; i++) {
          const x = (off + k * 8 + i) % 32;
          put(im, x, y0 + dy, add([102, 106, 114], shade + (r() - 0.5) * 8 + (dy === 0 ? 7 : dy === 2 ? -5 : 0)));
        }
      }
      for (let x = 0; x < 32; x++) put(im, x, y0 + 3, add([64, 66, 72], (r() - 0.5) * 6));
      for (let k = 0; k < 4; k++) {
        const x = off + k * 8;
        for (let dy = 0; dy < 3; dy++) put(im, (x + [0, 1, 0][dy]) % 32, y0 + dy, [62, 64, 70]);
      }
    }
    for (let k = 0; k < 16; k++) {
      const x = Math.floor(r() * 32), y = Math.floor(r() * 32);
      if ((y % 4) === 3) continue;
      put(im, x, y, mix(get(im, x, y), [182, 184, 186], 0.6)); if (r() < 0.5) put(im, x + 1, y, mix(get(im, x + 1, y), [170, 172, 176], 0.4));
    }
    out('lead_brick', im);
    // borated water tank: brushed stainless, weld seam beads round the edge
    im = img(32, 32); r = rng(321);
    const streak = []; for (let y = 0; y < 32; y++) streak.push((r() - 0.5) * 10);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([156, 160, 164], streak[y] + (r() - 0.5) * 5));
    for (let i = 0; i < 32; i++) for (const [x, y] of [[i, 0], [i, 31], [0, i], [31, i]]) put(im, x, y, add([118, 120, 122], (i % 2) * 14 + (r() - 0.5) * 6));
    out('borated_water_tank', im);
    // borated polyethylene: off-white, faint green, slab seams every 0.5 m, countersunk bolts
    im = img(32, 32); r = grain(im, [214, 220, 204], 331, 3, 4, 8);
    for (let x = 0; x < 32; x++) { put(im, x, 0, [168, 174, 160]); put(im, x, 16, [168, 174, 160]); }
    for (let y = 0; y < 32; y++) put(im, 0, y, [176, 182, 168]);
    for (const [x, y] of [[3, 3], [28, 3], [3, 19], [28, 19], [3, 12], [28, 12], [3, 28], [28, 28]]) {
      rect(im, x, y, 2, 2, [128, 132, 126]); put(im, x, y, [176, 180, 172]);
    }
    out('borated_pe', im);
    // lead glass: dark amber-olive, a diagonal reflection band
    im = img(32, 32); r = rng(341);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.abs(x - y - 4), band = d < 3 ? 0.55 : d < 5 ? 0.25 : 0;
      put(im, x, y, add(mix(mix([86, 88, 52], [116, 112, 66], y / 31), [196, 190, 140], band), (r() - 0.5) * 3));
    }
    out('lead_glass', im);
    // lamp diffuser: warm white
    im = img(32, 32); r = grain(im, [246, 238, 210], 351, 3, 4, 8);
    out('lamp', im);
    // cask paint: light grey on cast iron
    im = img(32, 32); r = grain(im, [168, 170, 166], 361, 4, 6, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [120, 118, 112]);
    out('cask', im);
    return 7;
  }

  // ------------------------------------------------------------------ hot cell
  const ARM_X = [-12, 28];
  function hotCell(on) {
    const m = mk();
    // shield wall with the window hole (x -4..20, y 18..34) and the transfer port hole (x 34..44, y 10..20)
    const wall = [[-32, 0, -4, 64], [20, 0, 34, 64], [44, 0, 48, 64], [-4, 0, 20, 18], [-4, 34, 20, 64], [34, 0, 44, 10], [34, 20, 44, 64]];
    wall.forEach(([x0, y0, x1, y1], i) => {
      m.box(`wall_${i}`, [x0, y0, 0], [x1, y1, 16], 'hc');
      m.box(`liner_${i}`, [x0, y0 + (y0 ? 0 : 0.5), 16], [x1, y1, 16.3], 'bright');
    });
    m.box('wall_cap', [-32, 63.6, -0.4], [48, 64, 16.3], 'st', { split: 'x' });
    // window: oil-filled lead glass slabs in a steel housing
    m.box(on ? 'glow_window' : 'window', [-4, 18, 0.6], [20, 34, 15.4], 'glass', { split: '' });
    for (const [z0, z1, mat] of [[-1.5, 0.6, 'st'], [15.4, 17, 'bright']]) {
      m.box(`win_frame_b_${z0}`, [-6, 16, z0], [22, 18, z1], mat, { split: '' });
      m.box(`win_frame_t_${z0}`, [-6, 34, z0], [22, 36, z1], mat, { split: '' });
      m.box(`win_frame_w_${z0}`, [-6, 18, z0], [-4, 34, z1], mat, { split: '' });
      m.box(`win_frame_e_${z0}`, [20, 18, z0], [22, 34, z1], mat, { split: '' });
    }
    m.box('win_sill', [-6, 14.5, -4], [22, 16, -1.5], 'st', { split: '' });
    m.box('oil_fill', [21, 35, -2.5], [23, 38, -1.5], 'oil', { split: '' });
    // manipulators: through-tubes with collars, master arm on the operator side, slave arm in the cell
    for (const x of ARM_X) {
      m.prism(`tube_${x}`, 'z', [x, 46], 2, -3, 19, 'st', { split: '', ends: ['north', 'south'] });
      m.ring(`collar_op_${x}`, 'z', [x, 46], 3.4, 1.4, -1, 0, 'st');
      m.ring(`collar_cell_${x}`, 'z', [x, 46], 3.4, 1.4, 16.3, 17.3, 'bright');
      m.box(`master_shoulder_${x}`, [x - 2.5, 42, -7.5], [x + 2.5, 50, -3], 'pnt', { split: '' });
      m.box(`master_cw_${x}`, [x - 2, 50, -7], [x + 2, 55, -2], 'ci', { split: '' });
      m.box(`master_arm_${x}`, [x - 1, 24, -7], [x + 1, 42, -5], 'pnt', { split: '' });
      m.box(`master_wrist_${x}`, [x - 1.3, 22.6, -7.2], [x + 1.3, 24, -4.8], 'st', { split: '' });
      m.box(`slave_shoulder_${x}`, [x - 2.5, 42, 17.3], [x + 2.5, 50, 21.5], 'bright', { split: '' });
      m.box(`slave_arm_${x}`, [x - 1, 22, 19], [x + 1, 42, 21], 'bright', { split: '' });
      m.box(`slave_boot_${x}`, [x - 1.4, 26, 18.6], [x + 1.4, 34, 21.4], 'yel', { split: '' });
    }
    // transfer port: lined tunnel, gate rails (the gate is a partial), roller tables both sides
    m.box('port_lining_b', [34, 10, 0], [44, 10.4, 16], 'st', { split: '' });
    m.box('port_lining_t', [34, 19.6, 0], [44, 20, 16], 'st', { split: '' });
    for (const x of [33, 44]) m.box(`gate_rail_${x}`, [x, 8, -3], [x + 1, 38, 0], 'st', { split: '' });
    m.box('gate_rail_top', [33, 38, -3], [45, 39, 0], 'st', { split: '' });
    m.box('gate_motor', [36, 39, -3], [42, 43, 0], 'motor', { split: '' });
    for (const [z0, z1, tag] of [[-15.5, -3, 'op'], [16.3, 30, 'cell']]) {
      m.box(`roller_frame_w_${tag}`, [34, 8.6, z0], [35, 9.6, z1], 'st', { split: '' });
      m.box(`roller_frame_e_${tag}`, [43, 8.6, z0], [44, 9.6, z1], 'st', { split: '' });
      for (let z = z0 + 1; z < z1 - 0.5; z += 2) m.prism(`roller_${tag}_${z}`, 'x', [9.6, z], 0.5, 35, 43, 'bright', { split: '' });
      for (const z of [z0 + 0.5, z1 - 1.5]) for (const x of [34, 43]) m.box(`roller_leg_${tag}_${x}_${z}`, [x, 0, z], [x + 1, 8.6, z + 1], 'st', { split: '' });
    }
    // operator side: power box and conduit, intercom, trefoil, maker plate
    m.box('power_box', [-31.9, 4, -12], [-27, 12, -4], 'pnt', { split: '' });
    m.box('power_conduit', [-30, 12, -6], [-29, 40, -5], 'dark', { split: '' });
    m.box('intercom', [-26, 20, -1], [-20, 30, 0], 'dark', { split: '' });
    m.box(on ? 'glow_intercom_lamp' : 'intercom_lamp', [-24, 27, -1.3], [-22, 28.5, -1], on ? 'gw' : 'dark', { split: '' });
    m.decal('sign', [35, 46, -0.6], [43, 54, 0], 'hc', 'north', 'pl.sign');
    m.decal('plate', [-26, 34, -0.6], [-18, 38, 0], 'hc', 'north', 'pl.maker');
    // cell: floor, drain, table and a lead pot, lamps on the wall top, exhaust duct
    m.box('cell_floor', [-32, 0, 16.3], [48, 0.5, 48], 'bright');
    m.box('drain', [5, 0.5, 37], [11, 0.6, 43], 'grate', { split: '' });
    m.box('table_top', [-8, 11, 24], [24, 12.5, 36], 'bright', { split: '' });
    for (const [x, z] of [[-7, 25], [22, 25], [-7, 34], [22, 34]]) m.box(`table_leg_${x}_${z}`, [x, 0.5, z], [x + 1, 11, z + 1], 'st', { split: '' });
    m.cylY('lead_pot', [8, 30], 2.4, 12.5, 16.5, 'lead');
    m.prismY('lead_pot_lid', [8, 30], 2.6, 16.5, 17.2, 'st', { ends: ['up'] });
    for (const x of [-20, 24]) {
      m.box(`lamp_housing_${x}`, [x, 56, 16.3], [x + 8, 60, 21], 'st', { split: '' });
      m.box(on ? `glow_lamp_${x}` : `lamp_${x}`, [x + 0.6, 55.6, 16.8], [x + 7.4, 56, 20.4], on ? 'lamp' : 'st', { split: '' });
    }
    m.box('exhaust_duct', [-28, 40, 36], [-20, 63.9, 44], 'st', { split: 'y' });
    m.box('exhaust_hood', [-30, 36, 34], [-18, 40, 46], 'st', { split: '' });
    m.box('exhaust_filter', [-29, 50, 35], [-19, 54, 45], 'pnt', { split: '' });
    return m;
  }
  const masterHandle = (x) => () => {
    const m = mk();
    m.box(`grip_${x}`, [x - 0.8, 17, -7.6], [x + 0.8, 22.6, -6], 'dark', { split: '' });
    m.box(`trigger_${x}`, [x - 0.3, 19, -8.4], [x + 0.3, 20.6, -7.6], 'red', { split: '' });
    m.box(`bar_${x}`, [x - 1.8, 21.6, -8], [x + 1.8, 22.6, -5], 'st', { split: '' });
    return m;
  };
  const slaveTong = (x) => () => {
    const m = mk();
    m.box(`tong_body_${x}`, [x - 1.2, 18, 19.4], [x + 1.2, 21, 22.2], 'st', { split: '' });
    m.box(`jaw_a_${x}`, [x - 1.2, 13.4, 20.2], [x - 0.4, 18, 21.4], 'bright', { split: '' });
    m.box(`jaw_b_${x}`, [x + 0.4, 13.4, 20.2], [x + 1.2, 18, 21.4], 'bright', { split: '' });
    return m;
  };
  function transferGate() {
    const m = mk();
    m.box('gate', [34, 8, -2.6], [44, 22, -0.4], 'pnt', { split: '' });
    m.box('gate_handle', [37, 13, -3], [41, 14, -2.6], 'st', { split: '' });
    m.decal('gate_sign', [37, 16, -2.8], [41, 20, -2.6], 'pnt', 'north', 'pl.sign');
    return m;
  }

  // ------------------------------------------------------------------ cask (standing)
  const FIN_Y = []; for (let y = 14; y < 80; y += 4) if (y < 42 || y > 58) FIN_Y.push(y);
  function cask() {
    const m = mk();
    m.prismY('base', [8, 8], 18.6, 0, 3, 'cask', { ends: ['down'] });
    m.cylY('body', [8, 8], 17.4, 3, 89, 'cask');
    for (const y of FIN_Y) m.ringY(`fin_${y}`, [8, 8], 19.5, 2.2, y, y + 1.4, 'cask');
    for (const y of [10, 84]) for (const s of [1, -1]) {
      const x0 = s > 0 ? 25.2 : -9.2 - 5.8;
      m.box(`trunnion_${y}_${s}`, [x0, y - 2.9, 5.1], [x0 + 5.8, y + 2.9, 10.9], 'forged', { split: '' });
    }
    m.prismY('lid', [8, 8], 18.6, 89, 91, 'forged', { ends: ['up'] });
    for (let k = 0; k < 24; k++) {
      const a = k * Math.PI / 12, x = 8 + 17.6 * Math.cos(a), z = 8 + 17.6 * Math.sin(a);
      m.box(`bolt_${k}`, [x - 0.5, 91, z - 0.5], [x + 0.5, 91.8, z + 0.5], 'st', { split: '' });
    }
    m.decal('sign', [4, 46, -9.9], [12, 54, -9.3], 'cask', 'north', 'pl.sign');
    m.decal('plate', [14, 47, -9.9], [20, 50, -9.3], 'cask', 'north', 'pl.maker');
    return m;
  }
  function protectivePlate() {
    const m = mk();
    m.prismY('plate', [8, 8], 16.8, 91.8, 95, 'cask', { ends: ['up', 'down'] });
    for (const s of [1, -1]) m.box(`lug_${s}`, [8 + s * 10 - 1, 95, 7], [8 + s * 10 + 1, 95.9, 9], 'forged', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ cask transport cart
  const WHEELSETS = [[-48, 56], [-36, 44], [52, -44], [64, -56]];
  function cart(loaded) {
    const m = mk();
    for (const z of [-12, 24]) m.box(`beam_${z}`, [-60, 7, z], [76, 12, z + 4], 'yel', { split: 'x' });
    for (const x of [-58, -20, 10, 40, 72]) m.box(`cross_${x}`, [x, 8, -8], [x + 2, 12, 24], 'yel', { split: '' });
    m.box('deck', [-60, 12, -14], [76, 13, 30], 'chequer', { split: 'xz' });
    for (const [bx, tag] of [[-42, 'w'], [58, 'e']]) {
      for (const z of [-9, 22.6]) m.box(`bogie_${tag}_${z}`, [bx - 11, 2.5, z], [bx + 11, 7, z + 2.4], 'dark', { split: '' });
      m.box(`bolster_${tag}`, [bx - 3, 6, -9], [bx + 3, 7, 25], 'dark', { split: '' });
    }
    // saddles with felt pads; the cask rests on the side wedges
    for (const x of [-26, 42]) {
      m.box(`saddle_${x}`, [x - 4, 13, -10], [x + 4, 16, 26], 'yel', { split: '' });
      m.box(`wedge_n_${x}`, [x - 4, 16, -10], [x + 4, 23, -4], 'yel', { split: '' });
      m.box(`wedge_s_${x}`, [x - 4, 16, 20], [x + 4, 23, 26], 'yel', { split: '' });
      m.box(`pad_n_${x}`, [x - 3.5, 22, -4], [x + 3.5, 23.4, -1.5], 'dark', { split: '' });
      m.box(`pad_s_${x}`, [x - 3.5, 22, 17.5], [x + 3.5, 23.4, 20], 'dark', { split: '' });
    }
    // drive end: battery and drive housing, beacon, control pendant hook; cable reel on the east face
    m.box('drive_housing', [62, 13, -12], [76, 28, 28], 'yel', { split: '' });
    for (let z = -10; z < 26; z += 3) m.box(`louvre_${z}`, [76, 16, z], [76.4, 25, z + 1.6], 'dark', { split: '' });
    m.prismY('beacon', [70, -6], 1.4, 28, 30.6, 'gs', { ends: ['up'] });
    m.box('pendant', [62, 18, -12.6], [65, 24, -12], 'dark', { split: '' });
    m.prism('cable_reel', 'x', [8, 8], 5, 76.4, 79.9, 'red', { split: '', ends: ['east'] });
    for (const [x0, x1] of [[-64, -60], [76, 80]]) for (const z of [-11, 21]) {
      if (x0 === 76) continue;
      m.box(`buffer_${x0}_${z}`, [x0, 7, z], [x1, 11, z + 6], 'dark', { split: '' });
    }
    m.decal('sign', [-8, 7, -12.6], [0, 11.9, -12], 'yel', 'north', 'pl.sign');
    if (loaded) {
      const c = [36.5, 8];
      m.prism('cask_body', 'x', c, 17.4, -37, 52, 'cask', { split: '' });
      m.prism('cask_base', 'x', c, 18.6, -40, -37, 'cask', { split: '', ends: ['west'] });
      m.prism('cask_lid', 'x', c, 18.6, 52, 55, 'forged', { split: '', ends: ['east'] });
      for (let x = -34; x < 50; x += 4) {
        if ((x > -32 && x < -20) || (x > 36 && x < 48) || x === -14 || x === 26) continue;
        m.ring(`cask_fin_${x}`, 'x', c, 19.5, 2.2, x, x + 1.4, 'cask');
      }
      for (const x of [-13, 25]) m.ring(`strap_${x}`, 'x', c, 19.9, 0.5, x, x + 1.6, 'yel');
      for (const x of [-30, 46]) for (const s of [1, -1]) m.box(`cask_trunnion_${x}_${s}`, [x - 2.9, c[0] - 2.9, s > 0 ? 25.2 : -15], [x + 2.9, c[0] + 2.9, s > 0 ? 27.9 : -9.2], 'forged', { split: '' });
    }
    return m;
  }
  const wheelset = (x) => () => {
    const m = mk();
    for (const [z0, z1] of [[-6, -2.6], [18.6, 22]]) m.prism(`wheel_${x}_${z0}`, 'z', [x, 5], 4.8, z0, z1, 'st', { split: '', ends: ['north', 'south'] });
    m.prism(`axle_${x}`, 'z', [x, 5], 1.2, -2.6, 18.6, 'forged', { split: '' });
    return m;
  };

  // ------------------------------------------------------------------ portal monitor
  function portal(alarm) {
    const m = mk();
    for (const [x0, x1, tag] of [[-8, 0, 'w'], [16, 24, 'e']]) {
      m.box(`column_${tag}`, [x0, 0, 3], [x1, 37, 13], 'pnt', { split: 'y' });
      const xi = tag === 'w' ? [0, 0.4] : [15.6, 16];
      m.box(`detector_${tag}`, [xi[0], 3, 4], [xi[1], 35, 12], 'dark', { split: 'y' });
      m.box(`plinth_${tag}`, [x0 - 0.3, 0, 2.7], [x1 + 0.3, 1.5, 13.3], 'dark', { split: '' });
    }
    m.box('header', [-8, 37, 3], [24, 42, 13], 'pnt', { split: 'x' });
    m.box(alarm ? 'glow_display' : 'glow_display_ok', [2, 38, 2.6], [14, 41, 3], alarm ? 'gs' : 'gw', { split: '' });
    m.box(alarm ? 'lamp_clean' : 'glow_lamp_clean', [-6, 38.5, 2.6], [-3, 40.5, 3], alarm ? 'dark' : 'gw', { split: '' });
    m.box('mat', [0.4, 0, 2], [15.6, 0.6, 14], 'dark', { split: '' });
    m.box('beacon_base', [6, 42, 6], [10, 43, 10], 'dark', { split: '' });
    m.prismY(alarm ? 'glow_beacon' : 'beacon', [8, 8], 1.8, 43, 46, 'gs', { ends: ['up'] });
    m.decal('sign', [-7, 26, 2.4], [-1, 32, 3], 'pnt', 'north', 'pl.sign');
    m.box('conduit', [-10, 0, 6], [-6, 1, 10], 'dark', { split: '' });
    return m;
  }
  const shroud = (cx, cz, y0, r) => () => {
    const m = mk(), y1 = y0 + 3.4;
    m.box('shroud_n', [cx - r, y0, cz - r], [cx + r, y1, cz - r + 0.4], 'bright', { split: '' });
    m.box('shroud_w', [cx - r, y0, cz - r], [cx - r + 0.4, y1, cz], 'bright', { split: '' });
    m.box('shroud_e', [cx + r - 0.4, y0, cz - r], [cx + r, y1, cz], 'bright', { split: '' });
    m.box('shroud_top', [cx - r, y1 - 0.4, cz - r], [cx + r, y1, cz], 'bright', { split: '' });
    return m;
  };

  // ------------------------------------------------------------------ hand and foot monitor
  function handFoot(alarm) {
    const m = mk();
    m.box('column', [4, 0, 9], [12, 26.6, 14], 'pnt', { split: 'y' });
    m.box('foot_unit', [3, 0, 2], [13, 2, 9], 'pnt', { split: '' });
    for (const x of [4, 8.4]) m.box(`foot_${x}`, [x, 2, 3], [x + 3.6, 2.2, 8.6], 'dark', { split: '' });
    m.box('hand_unit', [2.5, 15, 5], [13.5, 21, 9], 'pnt', { split: '' });
    for (const x of [3.5, 8.5]) m.box(`pocket_${x}`, [x, 16.5, 4.7], [x + 4, 19.5, 5], 'dark', { split: '' });
    m.box(alarm ? 'glow_display' : 'glow_display_ok', [5, 22, 8.6], [11, 25, 9], alarm ? 'gs' : 'gw', { split: '' });
    m.box(alarm ? 'lamp_clean' : 'glow_lamp_clean', [5, 25.3, 8.6], [6.6, 26.2, 9], alarm ? 'dark' : 'gw', { split: '' });
    m.box('beacon_base', [6.5, 26.6, 10.5], [9.5, 27, 13.5], 'dark', { split: '' });
    m.prismY(alarm ? 'glow_beacon' : 'beacon', [8, 12], 1.4, 27, 29.6, 'gs', { ends: ['up'] });
    m.decal('sign', [12, 6, 10], [12.4, 12, 13.6], 'pnt', 'east', 'pl.sign');
    return m;
  }

  // ------------------------------------------------------------------ shield tiles
  const tile = (mat) => () => { const m = mk(); m.box('block', [0, 0, 0], [16, 16, 16], mat, { split: '' }); return m; };
  function waterTank() {
    const m = mk();
    m.box('tank', [0, 0, 0.4], [16, 15.2, 16], 'bwt', { split: '' });
    m.box('sight', [12, 2, 0], [13.5, 13, 0.4], 'st', { split: '', face: { north: { tex: 'boilers/level', uv: [0, 0, 16, 16] } } });
    m.box('lid_seam', [0.5, 15.2, 0.9], [15.5, 15.4, 15.5], 'bwt', { split: '' });
    m.prismY('fill_plug', [8, 8], 1.6, 15.2, 16, 'st', { ends: ['up'] });
    m.box('drain_plug', [2, 1, 0], [4, 3, 0.4], 'br', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ dry storage module
  function dryStorage() {
    const m = mk();
    for (const [x0, y0, x1, y1] of [[-15.6, 0, -6, 74], [22, 0, 31.6, 74], [-6, 0, 22, 16], [-6, 48, 22, 74]]) m.box(`front_${x0}_${y0}`, [x0, y0, 0.5], [x1, y1, 9], 'conc');
    m.box('body', [-15.6, 0, 9], [31.6, 74, 96], 'conc');
    m.box('opening', [-6, 16, 8.6], [22, 48, 9], 'dark', { split: '' });
    for (const [f, t] of [[[-7, 15, 0.2], [23, 16, 0.5]], [[-7, 48, 0.2], [23, 49, 0.5]], [[-7, 16, 0.2], [-6, 48, 0.5]], [[22, 16, 0.2], [23, 48, 0.5]]]) m.box(`embed_${f[0]}_${f[1]}`, f, t, 'st', { split: '' });
    for (const x of [-14, 22]) m.box(`inlet_${x}`, [x, 2, 0.2], [x + 8, 10, 0.5], 'grate', { split: '' });
    m.box('roof_slab', [-15.6, 74, 2], [31.6, 75, 94], 'conc');
    for (const [x, gx] of [[-14, -14.2], [22, 30]]) {
      m.box(`outlet_hood_${x}`, [x, 75, 64], [x + 8, 79, 88], 'conc', { split: 'z' });
      m.box(`outlet_grille_${x}`, [gx, 75.5, 66], [gx + 0.2, 78.5, 86], 'grate', { split: '' });
    }
    m.decal('sign', [4, 52, 0.2], [12, 60, 0.5], 'conc', 'north', 'pl.sign');
    m.decal('plate', [14, 56, 0.2], [20, 59, 0.5], 'conc', 'north', 'pl.maker');
    for (const z of [30, 62]) m.box(`tc_conduit_${z}`, [31.6, 20, z], [31.9, 21, z + 1], 'dark', { split: '' });
    return m;
  }
  function door() {
    const m = mk();
    m.box('door', [-6, 16, 0.05], [22, 48, 8.6], 'st', { split: '' });
    for (const x of [-4, 20]) for (const y of [18, 46]) m.box(`bolt_${x}_${y}`, [x - 0.6, y - 0.6, -0.4], [x + 0.6, y + 0.6, 0.05], 'forged', { split: '' });
    for (const x of [0, 16]) m.box(`lug_${x}`, [x - 1, 44, -1], [x + 1, 46, 0.05], 'forged', { split: '' });
    m.decal('door_sign', [4, 26, -0.3], [12, 34, 0.05], 'st', 'north', 'pl.sign');
    return m;
  }

  const UNITS = {
    hot_cell: { id: 'hot_cell_real', draw: () => hotCell(false), parts: {
      master_handle_l: { draw: masterHandle(-12), shift: [0, 0, 0] }, master_handle_r: { draw: masterHandle(28), shift: [0, 0, 0] },
      slave_tong_l: { draw: slaveTong(-12), shift: [0, 0, 0] }, slave_tong_r: { draw: slaveTong(28), shift: [0, 0, 0] },
      transfer_gate: { draw: transferGate, shift: [-16, 0, 0] } } },
    hot_cell_on: { id: 'hot_cell_real_on', draw: () => hotCell(true), parts: {} },
    cask: { id: 'spent_fuel_cask_real', draw: cask, parts: { protective_plate: { draw: protectivePlate, shift: [0, -80, 0] } } },
    cart: { id: 'cask_transport_cart_real', draw: () => cart(false), parts: Object.fromEntries(WHEELSETS.map(([x, s], i) => [`wheelset_${i}`, { draw: wheelset(x), shift: [s, 0, 0] }])) },
    cart_loaded: { id: 'cask_transport_cart_real_loaded', draw: () => cart(true), parts: {} },
    portal: { id: 'portal_monitor_real', draw: () => portal(false), parts: { status_lamp: { draw: shroud(8, 8, 43, 2.4), shift: [0, -24, 0] } } },
    portal_alarm: { id: 'portal_monitor_real_alarm', draw: () => portal(true), parts: {} },
    hand_foot: { id: 'hand_foot_monitor_real', draw: () => handFoot(false), parts: { status_lamp: { draw: shroud(8, 12, 27, 2), shift: [0, 0, 0] } } },
    hand_foot_alarm: { id: 'hand_foot_monitor_real_alarm', draw: () => handFoot(true), parts: {} },
    heavy_concrete: { id: 'shield_heavy_concrete_real', draw: tile('hc'), parts: {}, particle: 'radprot/heavy_concrete' },
    lead_brick: { id: 'shield_lead_brick_real', draw: tile('lead'), parts: {}, particle: 'radprot/lead_brick' },
    borated_water_tank: { id: 'shield_borated_water_tank_real', draw: waterTank, parts: {}, particle: 'radprot/borated_water_tank' },
    borated_pe: { id: 'shield_borated_pe_real', draw: tile('bpe'), parts: {}, particle: 'radprot/borated_pe' },
    dry_storage: { id: 'dry_storage_module_real', draw: dryStorage, parts: { door: { draw: door, shift: [0, -16, 0] } } },
  };
  const model = (k) => { const m = UNITS[k].draw(); if (UNITS[k].particle) m.particle = UNITS[k].particle; return m; };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return mk().merge(d.draw(), dx, dy, dz, ''); };
  window.RP = {
    UNITS, partial, paint, model,
    parts: (k) => Object.keys(UNITS[k].parts),
    build: (root, k) => { const m = PCVV.cut(model(k)); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'radprot'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'radprot'),
  };
})();
