// Lancashire boiler at real scale (1 block = 1 m, 16 units = 1 m): a riveted shell 8.5 m long and 2.3 m across
// with two furnace flues, set in a brick setting with side flues and a rear chamber. Front: the end plate with two
// firing fronts (fire door over the ash-pit mouth, dead plate), two gauge glasses, the pressure gauge, the maker's
// and fusible-plug plates and the feed check valve with its feed pipe. Crown: the stop valve to the steam main,
// the manhole and the high-steam low-water safety valve. Sides: cast-iron buckstays and soot doors, a ladder up the
// east wall to the coping walkway with a hand rail. Rear: the main flue to the chimney, low at the back centre.
// Dimensions and sources: docs/research/lancashire-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js (fire_tube_boiler and
// material textures are already in the assets), then
//   LANCS.build(repo); /* wait */ LANCS.save(repo)
//   LANCS.buildFire(repo); LANCS.saveFire(repo)       partial: models/block/lancashire_boiler/partial/fire
//
// Controller block = (0, 0, 0) on the floor at the front centre; front north (z = 0), the boiler runs to +z.
// Footprint x -2..2, y 0..3, z 0..9. Connections (cell, face): steam out top of (0, 3, 2); feed in north of
// (-1, 0, 0); flue gas out south of (0, 0, 9). Climb cells: the ladder in column (2, *, 7).
(function () {
  const CX = 8, AY = 28, R = 18.4;            // shell axis x, height, radius (2.3 m)
  const ZF = 4, Z0 = 5.5, Z1 = 141.5;          // end plate front face, shell 136 units = 8.5 m
  const FR = 6.8, FY = 23, FX = [CX - 8.6, CX + 8.6];   // furnace flues 0.85 m, centres
  const WI = [-17.6, 33.6], WO = [-27.2, 43.2], WT = 34;  // side wall inner and outer faces, wall top
  const ZB = 160;

  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const FIT = {
    door: [0, 0, 12, 9], nameplate: [24, 0, 14, 7], glass: [52, 0, 2, 14], mudhole: [0, 10, 12, 5],
    soot_door: [12, 10, 8, 6], back_door: [20, 10, 10, 8], slot_glow: [30, 10, 1, 5],
    pit: [32, 24, 12, 8], pit_glow: [44, 24, 12, 8],
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT }, pl: PCVV.ATLAS.pl,
  }), { particle: 'fire_tube_boiler/plate' });
  const half = (y) => Math.sqrt(Math.max(0, R * R - (y - AY) * (y - AY)));   // shell half-width at height y

  // A vertical handwheel facing along x: rim of 8 bars, two spokes and a boss, centre [y, z].
  function wheelX(m, name, x, cy, cz, r, mat) {
    m.ring(`${name}_rim`, 'x', [cy, cz], r, 0.7, x - 0.4, x + 0.4, mat);
    m.box(`${name}_spoke0`, [x - 0.3, cy - r, cz - 0.3], [x + 0.3, cy + r, cz + 0.3], mat, { split: '' });
    m.box(`${name}_spoke1`, [x - 0.3, cy - 0.3, cz - r], [x + 0.3, cy + 0.3, cz + r], mat, { split: '' });
    m.box(`${name}_boss`, [x - 0.6, cy - 0.7, cz - 0.7], [x + 0.6, cy + 0.7, cz + 0.7], 'st', { split: '' });
  }

  function setting(m) {
    // side walls with their coping (the walkway), the cover filling the side-flue roofs up to the shell
    for (const [s, wi, wo] of [['w', WI[0], WO[0]], ['e', WI[1], WO[1]]]) {
      const lo = Math.min(wi, wo), hi = Math.max(wi, wo);
      m.box(`wall_${s}`, [lo, 0, ZF + 1], [hi, WT, ZB], 'brick', { hide: ['down'] });
      m.box(`coping_${s}`, [lo - (s === 'w' ? 0.6 : 0), WT, ZF + 0.5], [hi + (s === 'e' ? 0.6 : 0), WT + 1, ZB], 'ci', { hide: ['down'] });
      const toShell = s === 'w' ? [wi, CX - half(WT - 2) + 1] : [CX + half(WT - 2) - 1, wi];
      m.box(`cover_${s}`, [toShell[0], WT - 2, ZF + 1], [toShell[1], WT + 0.6, Z1], 'brick', { hide: ['down'] });
    }
    // front wall behind the end plate, rear wall with the main flue opening low at the back centre
    m.box('front_wall', [WO[0], 0, ZF + 1], [WO[1], WT, ZF + 3], 'brick', { hide: ['down', 'south'] });
    m.box('rear_wall_w', [WO[0], 0, Z1], [0, WT, ZB], 'brick', { hide: ['down'] });
    m.box('rear_wall_e', [16, 0, Z1], [WO[1], WT, ZB], 'brick', { hide: ['down'] });
    m.box('rear_wall_m', [0, 16, Z1], [16, WT, ZB], 'brick', { hide: ['down'] });
    m.box('rear_coping', [WO[0], WT, Z1 + 2], [WO[1], WT + 1, ZB], 'ci', { hide: ['down'] });
    m.box('flue_floor', [0, 0, 150], [16, 0.5, ZB], 'soot', { split: '' });
    m.box('flue_back', [0, 0.5, 150], [16, 16, 151], 'soot', { split: '' });
    m.box('flue_side_w', [0, 0.5, 151], [0.5, 16, ZB], 'soot', { split: '' });
    m.box('flue_side_e', [15.5, 0.5, 151], [16, 16, ZB], 'soot', { split: '' });
    // damper frame round the flue mouth (the chimney flue joins here) and the damper plate inside it
    m.box('flue_frame_top', [-1, 14.5, ZB - 1], [17, 16.5, ZB], 'ci', { split: '' });
    m.box('flue_frame_w', [-1, 0, ZB - 1], [0.5, 14.5, ZB], 'ci', { split: '' });
    m.box('flue_frame_e', [15.5, 0, ZB - 1], [17, 14.5, ZB], 'ci', { split: '' });
    m.box('damper_plate', [1, 10, ZB - 2.2], [15, 14.5, ZB - 1.8], 'ci', { split: '' });

    // buckstays (cast-iron T posts) with the nuts of the tie rods through the setting
    for (const z of [12, 40, 72, 104, 136]) {
      for (const [s, x0, x1] of [['w', WO[0] - 1, WO[0]], ['e', WO[1], WO[1] + 1]]) {
        m.box(`buckstay_${s}${z}`, [x0, 0, z], [x1, WT + 3, z + 2], 'ci', { hide: ['down'] });
        for (const y of [6, WT - 2]) m.box(`tie_nut_${s}${z}_${y}`, [s === 'w' ? x0 - 0.8 : x1, y, z + 0.4], [s === 'w' ? x0 : x1 + 0.8, y + 1.2, z + 1.6], 'st', { split: '' });
      }
    }
    // soot doors into the side flues and the bottom flue
    for (const z of [24, 120]) {
      m.decal(`soot_door_w${z}`, [WO[0] - 0.5, 3, z], [WO[0], 8, z + 7], 'ci', 'west', 'fit.soot_door', { hide: ['east'] });
      m.decal(`soot_door_e${z}`, [WO[1], 3, z], [WO[1] + 0.5, 8, z + 7], 'ci', 'east', 'fit.soot_door', { hide: ['west'] });
    }
  }

  function shell(m) {
    m.prism('shell', 'z', [CX, AY], R, Z0, Z1, 'plate', { cullY: WT - 4 });
    // circumferential lap seams every ring of plates (about 1.1 m), a longitudinal strap along the crown
    for (let z = Z0 + 14; z < Z1 - 4; z += 17.5) m.prism(`seam_${Math.round(z)}`, 'z', [CX, AY], R + 0.3, z - 0.8, z + 0.8, 'rivets', { cullY: WT - 4 });
    m.disc('end_plate', 'z', [CX, AY], R + 0.6, ZF, Z0, 'plate', 'fire_tube_boiler/end_plate', 'north');
    m.disc('rear_plate', 'z', [CX, AY], R + 0.6, Z1, Z1 + 1.2, 'plate', 'fire_tube_boiler/end_plate', 'south');
    // gusset stays show as rivet rows on the end plate above the furnaces
    for (const x of [CX - 6, CX + 6]) m.box(`gusset_rivets_${x}`, [x - 0.6, 34, ZF - 0.2], [x + 0.6, 45, ZF], 'rivets', { split: '' });
  }

  function front(m, lit) {
    FX.forEach((c, i) => {
      m.disc(`flue_mouth_${i}`, 'z', [c, FY], FR + 0.9, ZF - 0.6, ZF, 'ci', 'fire_tube_boiler/flue_mouth', 'north');
      // firing front: fire door over the dead plate, ash-pit mouth with the fire bars under it
      m.box(`firing_front_${i}`, [c - 5.6, 16.4, ZF - 1.2], [c + 5.6, 29.2, ZF - 0.6], 'ci', { hide: ['south'], split: '' });
      m.decal(`fire_door_${i}`, [c - 4.4, 21.6, ZF - 1.9], [c + 4.4, 28.2, ZF - 1.2], 'ci', 'north', 'fit.door', { hide: ['south'] });
      m.box(`door_hinge_${i}a`, [c + 4.4, 22.4, ZF - 2], [c + 5.2, 23.4, ZF - 1.2], 'ci', { split: '' });
      m.box(`door_hinge_${i}b`, [c + 4.4, 26.4, ZF - 2], [c + 5.2, 27.4, ZF - 1.2], 'ci', { split: '' });
      m.box(`door_latch_${i}`, [c - 4.8, 24.6, ZF - 2.6], [c - 3.6, 25.4, ZF - 1.9], 'bright', { split: '' });
      m.box(`dead_plate_${i}`, [c - 5, 20.6, ZF - 3], [c + 5, 21.4, ZF - 1.2], 'ci', { split: '' });
      m.decal(`pit_${i}`, [c - 4.4, 16.8, ZF - 1.4], [c + 4.4, 20.4, ZF - 1.2], 'soot', 'north', 'fit.pit', { hide: ['south'] });
      if (lit) addFire(m, c, i);
    });

    // water gauges, one either side, each with its top and bottom cocks and a drain
    for (const [i, g] of [[0, CX - 12.5], [1, CX + 12.5]]) {
      m.decal(`glass_${i}`, [g - 0.5, 31, ZF - 1], [g + 0.5, 40, ZF - 0.4], 'br', 'north', 'fit.glass', { hide: ['south'] });
      for (const [k, y] of [['top', 40], ['bottom', 29]]) {
        m.box(`glass_cock_${k}_${i}`, [g - 1.1, y, ZF - 1.6], [g + 1.1, y + 2, ZF], 'br', { hide: ['south'], split: '' });
        m.box(`glass_handle_${k}_${i}`, [g + 1.1, y + 0.7, ZF - 1.4], [g + 3, y + 1.3, ZF - 0.9], 'bright', { split: '' });
      }
      m.box(`glass_guard_${i}a`, [g - 1.1, 31, ZF - 1.4], [g - 0.8, 40, ZF - 1.1], 'br', { split: '' });
      m.box(`glass_guard_${i}b`, [g + 0.8, 31, ZF - 1.4], [g + 1.1, 40, ZF - 1.1], 'br', { split: '' });
      m.box(`glass_drain_${i}`, [g - 0.3, 26, ZF - 1.2], [g + 0.3, 29, ZF - 0.6], 'br', { split: '' });
    }
    // pressure gauge on its syphon, maker's plate and the fusible-plug notice, mud hole under the furnaces
    m.box('gauge_syphon', [CX - 0.4, 38.5, ZF - 0.8], [CX + 0.4, 41.5, ZF], 'br', { split: '' });
    m.disc('gauge_case', 'z', [CX, 44], 3.4, ZF - 1.6, ZF - 0.6, 'br', 'material/brass_disc', 'north');
    m.plate('gauge_dial', [CX - 2.5, 41.5, ZF - 1.65], [CX + 2.5, 46.5, ZF - 1.6], 'br', 'north', 'material/gauge_dial',
      { hide: ['south', 'east', 'west', 'up', 'down'] });
    m.decal('nameplate', [CX - 4, 32.5, ZF - 0.4], [CX + 4, 36.5, ZF], 'br', 'north', 'fit.nameplate', { hide: ['south'] });
    m.decal('fusible_plate', [CX - 2, 30, ZF - 0.3], [CX + 2, 32, ZF], 'br', 'north', 'pl.maker', { hide: ['south'] });
    m.decal('mudhole', [CX - 3.5, 11.5, ZF - 0.5], [CX + 3.5, 14.5, ZF], 'ci', 'north', 'fit.mudhole', { hide: ['south'] });
    m.box('mudhole_dog', [CX - 4, 12.6, ZF - 1], [CX + 4, 13.4, ZF - 0.5], 'bright', { split: '' });

    // feed check valve on the front, viewer's right (west), its feed pipe down to the feed connection at the
    // north face of cell (-1, 0, 0), flange centre (-8, 8)
    const fx = -6;
    m.box('feed_valve_body', [fx - 2, 35, ZF - 3], [fx + 2, 39, ZF], 'gm', { split: '' });
    m.box('feed_valve_bonnet', [fx - 1.2, 39, ZF - 2.4], [fx + 1.2, 41, ZF - 0.6], 'gm', { split: '' });
    m.box('feed_valve_spindle', [fx - 0.3, 41, ZF - 1.8], [fx + 0.3, 43, ZF - 1.2], 'bright', { split: '' });
    m.wheelY('feed_valve_wheel', fx, 43.4, ZF - 1.5, 1.8, 'red');
    m.box('feed_pipe_down', [fx - 0.8, 9, ZF - 2.3], [fx + 0.8, 35, ZF - 0.7], 'gw', { split: 'y' });
    m.box('feed_pipe_bend', [-8.8, 7.2, ZF - 2.3], [fx + 0.8, 9, ZF - 0.7], 'gw', { split: '' });
    m.prism('feed_pipe_out', 'z', [-8, 8], 0.9, 1.3, ZF - 0.7, 'gw', { split: '' });
    m.prism('feed_flange', 'z', [-8, 8], 2.2, 0.3, 1.3, 'forged', { split: '', ends: ['north'] });
    m.nutsZ('feed_flange_nut', [-8, 8], 1.6, 4, 0, 0.3, 0.5, 'bright');
    for (const y of [12, 30]) m.box(`feed_clip_${y}`, [fx - 1.2, y, ZF - 2.6], [fx + 1.2, y + 0.8, ZF], 'ci', { split: '' });

    // ash discharge from the bottom flue: a cast chute through the front wall with a clamped door on the north
    // face of cell (1, 0, 0), where the slag hopper takes the ash
    m.box('ash_chute', [19, 1, 0.9], [29, 11, ZF + 1], 'ci', { hide: ['south'], split: '' });
    m.decal('ash_door', [19.5, 1.5, 0.4], [28.5, 10.5, 0.9], 'ci', 'north', 'fit.back_door', { hide: ['south'] });
    m.box('ash_door_bar', [18.5, 5.6, 0], [29.5, 6.4, 0.4], 'bright', { split: '' });
  }

  function crown(m) {
    const top = AY + R;                        // 46.4
    // crown stop valve to the steam main, centre (8, 40): seating block, globe body, branch up to the steam
    // connection on the top face of cell (0, 3, 2), spindle and handwheel to the east
    m.box('stop_seating', [CX - 4, top - 1.5, 34], [CX + 4, top + 1.2, 46], 'ci', { split: '' });
    m.prism('stop_body', 'x', [top + 5, 40], 3.6, CX - 4, CX + 4, 'ci', { split: '', ends: ['east', 'west'] });
    m.box('stop_neck', [CX - 2, top + 1.2, 38], [CX + 2, top + 2.5, 42], 'ci', { split: '' });
    m.box('stop_bonnet', [CX + 4, top + 3.2, 37.8], [CX + 6.5, top + 6.8, 42.2], 'ci', { split: '' });
    m.box('stop_spindle', [CX + 6.5, top + 4.7, 39.7], [CX + 9.5, top + 5.3, 40.3], 'bright', { split: '' });
    wheelX(m, 'stop_wheel', CX + 9.8, top + 5, 40, 3, 'red');
    m.cylY('steam_branch', [CX, 40], 2.4, top + 8, 61, 'clad');
    m.ringY('steam_band', [CX, 40], 2.7, 0.6, 57, 59, 'gs');
    m.cylY('steam_flange', [CX, 40], 4.2, 61, 63.9, 'forged', { cap: 'vver/flange' });
    m.nutsY('steam_flange_nut', [CX, 40], 3.4, 8, 63.3, 0.6, 0.7, 'bright');

    // manhole on the crown, two dogs over the cover
    m.box('manhole_ring', [CX - 6, top - 1, 64], [CX + 6, top + 1, 76], 'plate', { split: '' });
    m.box('manhole_cover', [CX - 5, top + 1, 65], [CX + 5, top + 2, 75], 'ci', { split: '' });
    for (const z of [67, 72]) {
      m.box(`manhole_dog_${z}`, [CX - 5.6, top + 2, z], [CX + 5.6, top + 3, z + 1], 'bright', { split: '' });
      m.box(`manhole_nut_${z}`, [CX - 0.6, top + 3, z - 0.1], [CX + 0.6, top + 4, z + 1.1], 'bright', { split: '' });
    }

    // high-steam low-water safety valve: seating, gunmetal chest, lever on a strut with its weight, escape pipe
    const sz = 100;
    m.box('sv_seating', [CX - 4.5, top - 1.5, sz - 4], [CX + 4.5, top + 1.2, sz + 6], 'ci', { split: '' });
    m.box('sv_chest', [CX - 3.5, top + 1.2, sz - 3], [CX + 3.5, top + 7, sz + 5], 'gm', { split: '' });
    m.box('sv_lid', [CX - 4, top + 7, sz - 3.5], [CX + 4, top + 8, sz + 5.5], 'gm', { split: '' });
    m.box('sv_strut', [CX - 0.6, top + 8, sz - 3], [CX + 0.6, top + 11, sz - 1.8], 'ci', { split: '' });
    m.box('sv_lever', [CX - 0.4, top + 10, sz - 3], [CX + 0.4, top + 10.8, sz + 18], 'bright', { split: 'z' });
    m.box('sv_weight', [CX - 2, top + 5, sz + 14], [CX + 2, top + 10, sz + 18], 'ci', { split: '' });
    m.box('sv_branch', [CX + 3.5, top + 3, sz - 1], [CX + 6, top + 5.4, sz + 1.4], 'gm', { split: '' });
    m.cylY('sv_escape', [CX + 5.2, sz + 0.2], 1.2, top + 3, 62, 'gs');
    m.ringY('sv_escape_lip', [CX + 5.2, sz + 0.2], 1.6, 0.4, 61, 62, 'gs');
    // blow-down handle at the rear top of the setting (the cock itself is under the shell)
    m.box('blowdown_stem', [CX - 0.4, WT + 1, 138], [CX + 0.4, WT + 6, 138.8], 'bright', { split: '' });
    m.box('blowdown_key', [CX - 3, WT + 6, 138], [CX + 3, WT + 6.8, 138.8], 'red', { split: '' });
  }

  // Ladder up the east wall face in cell column (2, *, 7) and a hand rail along the east coping walkway.
  function access(m) {
    const x0 = WO[1] + 1, x1 = x0 + 3.2, za = 117, zb = 124;
    m.box('ladder_l', [x0, 0, za], [x1, WT + 14, za + 0.6], 'yel', { split: 'y' });
    m.box('ladder_r', [x0, 0, zb - 0.6], [x1, WT + 14, zb], 'yel', { split: 'y' });
    let k = 0;
    for (let y = 4.8; y < WT + 2; y += 4.8) m.box(`ladder_rung${k++}`, [x1 - 1, y, za + 0.6], [x1 - 0.4, y + 0.5, zb - 0.6], 'yel', { split: '' });
    for (let z = 12; z <= 140; z += 16) {
      if (z > 110 && z < 128) continue;
      m.box(`rail_post_${z}`, [WO[1] - 0.8, WT + 1, z], [WO[1] - 0.2, WT + 16, z + 0.6], 'yel', { split: '' });
    }
    for (const [n, y] of [['top', WT + 15.4], ['knee', WT + 8]]) {
      m.box(`rail_${n}_a`, [WO[1] - 0.8, y, 12], [WO[1] - 0.2, y + 0.6, za], 'yel', { split: 'z' });
      m.box(`rail_${n}_b`, [WO[1] - 0.8, y, zb], [WO[1] - 0.2, y + 0.6, 140.6], 'yel', { split: 'z' });
    }
  }

  // Fire seen through the air register of the doors and the glowing ash pit: drawn separately so the game can show
  // it only while the grates burn (partial "fire", same coordinates as the whole model).
  function addFire(m, c, i) {
    for (const p of [3, 5, 7, 9]) {
      const x1 = c + 4.4 - p * 8.8 / 12, x0 = x1 - 0.7;
      m.decal(`glow_slot_${i}_${p}`, [x0, 23, ZF - 1.95], [x1, 26.5, ZF - 1.9], 'ci', 'north', 'fit.slot_glow',
        { hide: ['south', 'east', 'west', 'up', 'down'] });
    }
    m.decal(`glow_pit_${i}`, [c - 4.4, 16.8, ZF - 1.45], [c + 4.4, 20.4, ZF - 1.41], 'soot', 'north', 'fit.pit_glow',
      { hide: ['south', 'east', 'west', 'up', 'down'] });
  }

  function model() {
    const m = mk();
    setting(m); shell(m); front(m, false); crown(m); access(m);
    return m;
  }
  function fire() {
    const m = mk();
    FX.forEach((c, i) => addFire(m, c, i));
    return m;
  }

  window.LANCS = {
    model, fire,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'lancashire_boiler'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'lancashire_boiler', 'boilers/lancashire'),
    buildFire: (root) => { const m = fire(); PCART.realize(root, m, 'lancashire_boiler_fire'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    saveFire: (root) => PCVV.savePartial(root, 'lancashire_boiler', 'fire', 'boilers/lancashire'),
  };
})();
