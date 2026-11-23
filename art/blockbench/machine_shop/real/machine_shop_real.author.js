// I0 machine shop at real scale (1 block = 1 m, 16 units = 1 m): five belt-driven machine tools of about 1900, each
// under its own overhead countershaft on two hanger columns, the countershaft taking the Create shaft at its west
// end (the line shaft of the shop) and driving the machine's cone pulley by a flat leather belt.
//   machine_lathe_real     16 in swing screw-cutting engine lathe, 8 ft bed (Lodge & Shipley, Hendey): 3.0 m long,
//                          centres 1.19 m high, 4-step cone with back gear, carriage with apron, tailstock.
//   drill_press_real       24 in upright drill (Barnes, Sibley): round column, round table on its bracket, head
//                          with quill and capstan feed, cone shaft and bevel gear over the spindle, 2.0 m.
//   milling_machine_real   plain horizontal knee-and-column mill (Cincinnati No. 2): column, cone pulley at the back
//                          of the spindle, overarm with the arbor yoke, knee, saddle and 1.9 m table across.
//   shaping_machine_real   16 in crank shaper (Gould & Eberhardt): box column, ram with the clapper head, the table
//                          on the cross rail at the front, cone pulley on the side.
//   surface_grinder_real   Brown & Sharpe No. 2 surface grinder: bed, reciprocating table with a work chuck, column
//                          with the wheel head and guard, jack pulley on the head.
// Sizes after the makers' catalogues as reprinted in Colvin & Stanley, "American Machinists' Handbook" (1908) and
// Rose, "Modern Machine Shop Practice" (1887–1904).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in MSRL.ids:
//   MSRL.build(repo, id); /* wait */ MSRL.save(repo, id)
//   MSRL.buildPartial(repo, id, part); /* wait */ MSRL.savePartial(repo, id, part)   parts in MSRL.parts(id)
//
// All: front north, controller (0, 0, 0) at the west end, footprint y 0..2. Create shaft in: west face of (0, 2, z)
// at (0, 40, zc), axis x. Partial `countershaft` (shaft, cone, belt pulley) turns about x through (y 40, z zc); it is
// saved shifted (0, -16, 0) (and (-16, -16, 0) for the lathe), so draw it back up by the opposite shift. The other
// partials are saved unshifted. Per machine (model px):
//   lathe   footprint x 0..2, z 0..0. zc 8. spindle (cone, spindle, chuck, bar): turns about x, axis (y 19, z 8);
//           tool point (25, 19, 6.8).
//   drill   footprint x 0..0, z 0..0. zc 8. spindle (spindle, chuck, drill): turns about y, axis (x 8, z 8); drill
//           point y 19.4 over the vice on the table; feed down by translate -y up to 1 px.
//   mill    footprint x 0..1, z 0..1. zc 24. arbor (cone, arbor, cutter): turns about x, axis (y 24, z 24); cutter's
//           lowest point (21, 20.6, 24) over the vice at z 21..27.
//   shaper  footprint x 0..1, z 0..0. zc 8. cone (side cone pulley): turns about x, axis (y 16, z 8). ram (ram,
//           clapper head, tool): slides along x by ±6 px; tool point at
//           y 14 over the work at x 25..29, z 8.
//   grinder footprint x 0..1, z 0..0. zc 8. wheel (wheel and its spindle): turns about z, axis (x 16, y 19), radius
//           4, face at y 15 on the work; table (table, chuck, work): slides along x by ±6 px.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    mp: { tex: 'machine_shop/paint', P: 16 }, scr: { tex: 'machine_shop/scraped', P: 16 }, ts: { tex: 'machine_shop/tslot', P: 16 },
    cut: { tex: 'machine_shop/cutter', P: 16 }, wh: { tex: 'machine_shop/wheel', P: 16 }, belt: { tex: 'smithy/leather', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'machine_shop/paint' });
  const YC = 40;                                           // countershaft height (2.5 m)

  // Stepped cone pulley along x: steps of width w from x0, radii in order.
  function cone(m, name, c, x0, w, radii, mat) {
    radii.forEach((r, i) => m.prism(`${name}_${i}`, 'x', c, r, x0 + i * w, x0 + (i + 1) * w, mat || 'ci', { split: '', ends: ['east', 'west'] }));
  }
  // Two hanger columns at the back with brackets and bearings carrying the countershaft along x at (YC, zc).
  function hangers(m, xs, zc) {
    for (const x of xs) {
      m.box(`hanger_col_${x}`, [x - 0.7, 0, zc + 5], [x + 0.7, YC + 3.4, zc + 6.4], 'ci', { split: 'y' });
      m.box(`hanger_foot_${x}`, [x - 1.6, 0, zc + 4.2], [x + 1.6, 0.6, zc + 7.2], 'ci', { split: '' });
      m.box(`hanger_arm_${x}`, [x - 0.6, YC + 2, zc - 1.4], [x + 0.6, YC + 3.4, zc + 5], 'ci', { split: '' });
      m.box(`bearing_${x}`, [x - 1, YC - 1.4, zc - 1.4], [x + 1, YC + 2, zc + 1.4], 'ci', { split: '' });
      m.box(`oil_cup_${x}`, [x - 0.4, YC + 3.4, zc - 0.4], [x + 0.4, YC + 4.4, zc + 0.4], 'br', { split: '' });
    }
  }
  // Flat belt between two pulleys on x-axis shafts stacked in y at the same z: two straight strands.
  function belt(m, name, x0, x1, yLo, yHi, zc, r) {
    m.box(`${name}_front`, [x0, yLo, zc - r - 0.3], [x1, yHi, zc - r], 'belt', { split: '' });
    m.box(`${name}_back`, [x0, yLo, zc + r], [x1, yHi, zc + r + 0.3], 'belt', { split: '' });
  }
  function countershaft(m, x1, zc, coneX, radii) {
    m.prism('cs_shaft', 'x', [YC, zc], 0.8, 0, x1, 'bright', { split: '', ends: ['east', 'west'] });
    cone(m, 'cs_cone', [YC, zc], coneX, 1.6, radii);
    m.prism('cs_coupling', 'x', [YC, zc], 1.6, 0, 1.2, 'ci', { split: '', ends: ['west'] });
  }

  // ------------------------------------------------------------------ lathe (x 0..48)
  const lathe = {
    static(m) {
      for (const [n, x0, x1] of [['head', 2, 9], ['tail', 38, 45]]) {
        m.box(`leg_${n}`, [x0, 0, 3], [x1, 12, 13], 'mp', { split: 'y' });
        m.box(`leg_foot_${n}`, [x0 - 0.6, 0, 2.4], [x1 + 0.6, 0.8, 13.6], 'mp', { split: '' });
      }
      m.box('chip_pan', [9, 5, 3], [38, 6, 13], 'st', { split: 'x' });
      m.box('bed', [1, 12, 4], [46, 15, 12], 'mp', { split: 'x' });
      for (const z of [4.4, 10.6]) m.box(`way_${z}`, [1, 15, z], [46, 15.6, z + 1], 'scr', { split: 'x' });
      m.box('gear_guard', [0, 12, 4], [2, 22, 12], 'mp', { split: '' });
      // headstock: base and two bearing uprights round the cone, back gear behind
      m.box('head_base', [2, 15, 3.6], [12, 15.6, 12.4], 'mp', { split: '' });
      for (const x of [2, 10.6]) {
        m.box(`head_upright_${x}`, [x, 15.6, 5], [x + 1.4, 21.6, 11], 'mp', { split: '' });
        m.box(`head_cap_${x}`, [x, 21.6, 5.6], [x + 1.4, 22.6, 10.4], 'mp', { split: '' });
      }
      m.prism('back_gear', 'x', [17, 12], 1.2, 3.4, 10.6, 'ci', { split: '' });
      m.box('back_gear_lever', [10.8, 17, 12.4], [11.4, 22, 13], 'st', { split: '' });
      // tailstock with quill and handwheel
      m.box('tail_base', [36, 15.6, 4], [42, 17, 12], 'mp', { split: '' });
      m.box('tailstock', [36.6, 17, 5.4], [41.6, 21.6, 10.6], 'mp', { split: '' });
      m.prism('quill', 'x', [19, 8], 0.9, 32.6, 36.6, 'bright', { split: '', ends: ['west'] });
      m.prism('tail_centre', 'x', [19, 8], 0.4, 31.4, 32.6, 'bright', { split: '', ends: ['west'] });
      m.disc('tail_wheel', 'x', [19, 8], 2.4, 41.6, 42.2, 'ci', 'vver/flange', 'east');
      // carriage: saddle, apron with its handwheel, cross slide, compound and tool post
      m.box('saddle', [21, 15.6, 3], [29, 17, 13], 'mp', { split: '' });
      m.box('apron', [21.4, 9.6, 2], [28.6, 17, 3.4], 'mp', { split: '' });
      m.disc('apron_wheel', 'z', [23.4, 13], 1.8, 1.4, 2, 'ci', 'vver/flange', 'north');
      m.box('half_nut_lever', [26.6, 11, 1.4], [27.2, 14, 2], 'st', { split: '' });
      m.box('cross_slide', [23, 17, 4.4], [27, 18, 12], 'scr', { split: '' });
      m.box('compound', [23.6, 18, 5.4], [26.4, 18.8, 9.4], 'scr', { split: '' });
      m.box('tool_post', [24.4, 18.8, 6.4], [25.6, 21, 7.8], 'st', { split: '' });
      m.box('tool', [24.6, 18.8, 6.8], [25.4, 19.4, 8.6], 'bright', { split: '' });
      m.box('cross_handle', [24.6, 17.2, 2.6], [25.4, 18, 4.4], 'st', { split: '' });
      // lead screw and feed rod along the front
      m.prism('lead_screw', 'x', [13.4, 2.8], 0.4, 2, 46, 'bright', { split: '' });
      m.prism('feed_rod', 'x', [11.4, 2.8], 0.3, 2, 46, 'bright', { split: '' });
      m.decal('maker_plate', [30, 12.6, 3.9], [36, 14.6, 4], 'mp', 'north', 'pl.maker');
      hangers(m, [3, 44], 8);
      belt(m, 'belt', 6.2, 7.6, 19, YC, 8, 3.1);
    },
    parts: {
      spindle(m) {
        cone(m, 'cone', [19, 8], 3.4, 1.8, [3.6, 3.1, 2.6, 2.1]);
        m.prism('spindle', 'x', [19, 8], 0.9, 2, 13, 'bright', { split: '', ends: ['west'] });
        m.disc('chuck', 'x', [19, 8], 3.4, 12, 14, 'ci', 'vver/flange', 'east');
        for (const a of [0, 120, 240]) {
          const r = 2.2, y = 19 + r * Math.sin(a * Math.PI / 180), z = 8 + r * Math.cos(a * Math.PI / 180);
          m.box(`jaw_${a}`, [14, y - 0.5, z - 0.5], [14.8, y + 0.5, z + 0.5], 'bright', { split: '' });
        }
        m.prism('bar', 'x', [19, 8], 1.2, 14, 31.4, 'bright', { split: '', ends: ['east'] });
      },
      countershaft(m) { countershaft(m, 48, 8, 3.4, [2.1, 2.6, 3.1, 3.6]); },
    },
    shift: { countershaft: [-16, -16, 0] },
  };

  // ------------------------------------------------------------------ drill press (x 0..16)
  const drill = {
    static(m) {
      m.box('base', [2, 0, 1], [14, 2, 15.6], 'mp', { split: '' });
      m.box('base_top', [3, 2, 2], [13, 2.6, 10], 'ts', { split: '' });
      m.cylY('column', [8, 13.4], 1.8, 2, 30, 'mp');
      m.box('rack', [7.4, 3, 11.2], [8.6, 28, 11.6], 'bright', { split: 'y' });
      // table bracket and the round table with a vice and the work
      m.box('table_arm', [6.2, 13.6, 8], [9.8, 16, 12.2], 'mp', { split: '' });
      m.box('arm_crank', [9.8, 14.4, 10], [11.4, 15.2, 10.8], 'st', { split: '' });
      m.cylY('table', [8, 8], 3.6, 16, 17.2, 'mp', { bottom: true, cap: 'vver/flange' });
      m.box('vice', [5.6, 17.2, 6.4], [10.4, 18.4, 9.6], 'ci', { split: '' });
      m.box('work', [6.6, 18.4, 6.8], [9.4, 18.8, 9.2], 'scr', { split: '' });
      // head on the column: quill housing, capstan feed, bevel box and cone shaft over the spindle
      m.box('head', [5, 24, 5.4], [11, 30, 15.4], 'mp', { split: '' });
      m.cylY('quill_housing', [8, 8], 1.6, 21.6, 24, 'mp', { bottom: true });
      m.prism('feed_hub', 'x', [26, 10], 1, 11, 12.4, 'ci', { split: '', ends: ['east'] });
      for (const [dy, dz] of [[3, 0], [-1.5, 2.6], [-1.5, -2.6]]) m.box(`capstan_${dy}_${dz}`, [11.8, 26 + Math.min(0, dy) - 0.3, 10 + Math.min(0, dz) - 0.3], [12.4, 26 + Math.max(0, dy) + 0.3, 10 + Math.max(0, dz) + 0.3], 'st', { split: '' });
      m.box('bevel_box', [6, 30, 6], [10, 32.4, 10], 'mp', { split: '' });
      for (const x of [2, 13.4]) m.box(`cone_bracket_${x}`, [x, 28, 7], [x + 0.6, 31.6, 9], 'mp', { split: '' });
      m.prism('cone_shaft', 'x', [31, 8], 0.5, 2, 14, 'bright', { split: '' });
      cone(m, 'drill_cone', [31, 8], 2.6, 1.4, [2.6, 2.2, 1.8, 1.4]);
      hangers(m, [1.8, 14.2], 8);
      belt(m, 'belt', 4.6, 5.6, 31, YC, 8, 2.1);
    },
    parts: {
      spindle(m) {
        m.cylY('spindle', [8, 8], 0.7, 20.4, 30, 'bright');
        m.cylY('chuck', [8, 8], 1.3, 20.4, 21.8, 'ci', { bottom: true });
        m.cylY('drill', [8, 8], 0.3, 19.4, 20.4, 'bright', { bottom: true });
      },
      countershaft(m) { countershaft(m, 16, 8, 2.6, [1.4, 1.8, 2.2, 2.6]); },
    },
    shift: { countershaft: [0, -16, 0] },
  };

  // ------------------------------------------------------------------ milling machine (x 0..32, z 0..32)
  const mill = {
    static(m) {
      m.box('base', [5, 0, 6], [31, 2, 31], 'mp', { split: '' });
      m.box('column', [6, 2, 17], [16, 30, 31], 'mp');
      m.box('column_face', [16, 8, 18], [16.6, 30, 30], 'scr', { split: 'y' });
      m.box('overarm', [12, 28.6, 22.4], [31, 31.4, 25.6], 'bright', { split: '' });
      m.box('yoke', [27, 21.4, 22.6], [29, 28.6, 25.4], 'mp', { split: '' });
      // knee with its elevating screw, saddle and the table across (along z) with a vice and the work
      m.box('knee', [16.6, 8, 8], [30, 15, 30], 'mp', { split: '' });
      m.box('elev_screw', [22.4, 2, 12], [23.6, 8, 13.2], 'bright', { split: '' });
      m.box('knee_crank', [21.6, 11, 7.2], [23.4, 12, 8], 'st', { split: '' });
      m.box('saddle', [17.4, 15, 10], [29.6, 16.4, 30], 'mp', { split: '' });
      m.box('table', [17, 16.4, 1], [26, 17.6, 31], 'ts', { split: 'z' });
      m.disc('table_wheel', 'z', [21.5, 17], 1.6, 0.2, 1, 'ci', 'vver/flange', 'north');
      m.box('vice', [17.8, 17.6, 21], [24.2, 19.4, 27], 'ci', { split: '' });
      m.box('work', [18.6, 19.4, 21.6], [23.4, 20.4, 26.4], 'scr', { split: '' });
      m.decal('maker_plate', [8, 14, 16.9], [14, 16, 17], 'mp', 'north', 'pl.maker');
      hangers(m, [1.8, 30.2], 24);
      belt(m, 'belt', 2.4, 3.6, 24, YC, 24, 3.4);
    },
    parts: {
      arbor(m) {
        cone(m, 'cone', [24, 24], 0.4, 1.4, [3.4, 3, 2.6, 2.2]);
        m.prism('spindle', 'x', [24, 24], 0.9, 0.4, 16.6, 'bright', { split: '', ends: ['west'] });
        m.prism('arbor', 'x', [24, 24], 0.6, 16.6, 28, 'bright', { split: '' });
        m.disc('cutter', 'x', [24, 24], 3.4, 20.2, 21.8, 'cut', 'machine_shop/cutter', 'east');
        for (const x of [17.6, 23.4]) m.prism(`collar_${x}`, 'x', [24, 24], 1.1, x, x + 2.6, 'bright', { split: '' });
      },
      countershaft(m) { countershaft(m, 32, 24, 0.4, [2.2, 2.6, 3, 3.4]); },
    },
    shift: { countershaft: [0, -16, 0] },
  };

  // ------------------------------------------------------------------ shaper (x 0..32)
  const shaper = {
    static(m) {
      m.box('base', [3, 0, 2], [23, 1.6, 14], 'mp', { split: '' });
      m.box('column', [4, 1.6, 3], [21, 21, 13], 'mp');
      m.box('ram_ways', [4, 21, 4], [21, 22, 12], 'scr', { split: '' });
      // cross rail and table on the front (east) face, the vice and the work
      m.box('cross_rail', [21, 9, 1], [22.6, 12.4, 15], 'mp', { split: '' });
      m.box('table_apron', [22.6, 6, 3], [24, 12.4, 13], 'mp', { split: '' });
      m.box('table', [22.6, 12.4, 3], [30, 13, 13], 'ts', { split: '' });
      m.box('table_support', [26, 1.6, 6], [28, 12.4, 10], 'mp', { split: 'y' });
      m.box('vice', [24.4, 13, 5], [30, 14.2, 11], 'ci', { split: '' });
      m.box('work', [25, 13, 6.6], [29, 13.6, 9.4], 'scr', { split: '' });
      // bull-gear cover and the cone on the west side
      m.disc('bull_gear_cover', 'z', [12, 13], 5, 2.4, 3, 'mp', 'vver/flange', 'north');
      m.prism('cone_shaft', 'x', [16, 8], 0.5, 0, 4, 'bright', { split: '' });
      m.box('stroke_lever', [12, 8, 2], [12.8, 13, 2.6], 'st', { split: '' });
      hangers(m, [1.8, 30.2], 8);
      belt(m, 'belt', 1.6, 2.8, 16, YC, 8, 3);
    },
    parts: {
      ram(m) {
        m.box('ram', [2, 22, 5], [26, 25.4, 11], 'mp', { split: '' });
        m.box('ram_top', [2.6, 25.4, 5.6], [25.4, 26, 10.4], 'scr', { split: '' });
        m.box('head', [24, 15.6, 5.6], [28, 22, 10.4], 'mp', { split: '' });
        m.box('clapper', [25.4, 14.6, 6.6], [27, 15.6, 9.4], 'ci', { split: '' });
        m.box('tool', [25.8, 14, 7.6], [26.6, 15, 8.4], 'bright', { split: '' });
        m.box('head_screw', [25.6, 22, 7.6], [26.4, 24, 8.4], 'bright', { split: '' });
        m.box('head_handle', [25, 24, 7.6], [27.8, 24.6, 8.4], 'st', { split: '' });
      },
      cone(m) { cone(m, 'cone', [16, 8], 0, 1, [3, 2.6, 2.2, 1.8]); },
      countershaft(m) { countershaft(m, 32, 8, 0, [1.8, 2.2, 2.6, 3]); },
    },
    shift: { countershaft: [0, -16, 0] },
  };

  // ------------------------------------------------------------------ surface grinder (x 0..32)
  const grinder = {
    static(m) {
      m.box('bed', [2, 0, 2], [30, 11, 13], 'mp');
      m.box('bed_ways', [2, 11, 4], [30, 11.6, 10], 'scr', { split: 'x' });
      m.box('saddle', [10, 11.6, 3], [22, 12.4, 11], 'mp', { split: '' });
      m.disc('cross_wheel', 'z', [16, 6], 1.8, 1.4, 2, 'ci', 'vver/flange', 'north');
      m.box('reverse_dogs', [4, 10, 1.6], [28, 10.6, 2], 'st', { split: 'x' });
      // column at the back carrying the wheel head, guard over the wheel, the jack pulley on the head's west
      m.box('column', [12, 11.6, 11], [20, 32, 15.4], 'mp');
      m.box('wheel_head', [12.6, 17, 6], [19.4, 24, 11], 'mp', { split: '' });
      m.box('guard_top', [11.4, 23, 2], [20.6, 24, 6], 'mp', { split: '' });
      m.box('guard_w', [11.4, 17, 2], [12, 23, 6], 'mp', { split: '' });
      m.box('guard_e', [20, 17, 2], [20.6, 23, 6], 'mp', { split: '' });
      m.box('head_feed', [19.4, 25, 12], [21, 26, 13], 'st', { split: '' });
      m.prism('jack_shaft', 'x', [22, 8], 0.5, 9.6, 12.6, 'bright', { split: '' });
      m.prism('jack_pulley', 'x', [22, 8], 2.2, 9.6, 11.6, 'ci', { split: '', ends: ['east', 'west'] });
      hangers(m, [1.8, 30.2], 8);
      belt(m, 'belt', 10, 11.2, 22, YC, 8, 2.2);
    },
    parts: {
      wheel(m) {
        m.disc('wheel', 'z', [16, 19], 4, 2.8, 5.2, 'wh', 'machine_shop/wheel', 'north');
        m.prism('wheel_spindle', 'z', [16, 19], 0.6, 2.4, 6, 'bright', { split: '', ends: ['north'] });
      },
      table(m) {
        m.box('table', [1, 12.4, 3.6], [31, 13.6, 10.4], 'ts', { split: 'x' });
        m.box('chuck', [11, 13.6, 4.4], [21, 14.4, 9.6], 'ci', { split: '' });
        m.box('work', [12.4, 14.4, 5], [19.6, 15, 9], 'scr', { split: '' });
      },
      countershaft(m) { countershaft(m, 32, 8, 9.2, [2.2, 2.2, 2.2, 2.2]); },
    },
    shift: { countershaft: [0, -16, 0] },
  };

  const MACH = {
    machine_lathe_real: lathe, drill_press_real: drill, milling_machine_real: mill,
    shaping_machine_real: shaper, surface_grinder_real: grinder,
  };
  const DIR = 'machine_shop/real';
  function model(id) { const m = mk(); MACH[id].static(m); return m; }
  function partial(id, p) {
    const s = mk(); MACH[id].parts[p](s);
    const [dx, dy, dz] = (MACH[id].shift || {})[p] || [0, 0, 0];
    return mk().merge(s, dx, dy, dz, '');
  }
  function preview(id) { const m = model(id); for (const p of Object.values(MACH[id].parts)) { const s = mk(); p(s); m.merge(s, 0, 0, 0, ''); } return m; }

  window.MSRL = {
    ids: Object.keys(MACH), parts: (id) => Object.keys(MACH[id].parts), model, partial,
    build: (root, id) => { const m = PCVV.cut(model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, DIR),
    buildPartial: (root, id, p) => { const m = partial(id, p); PCART.realize(root, m, id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, id, p) => PCVV.savePartial(root, id, p, DIR),
    preview: (root, id) => { const m = PCVV.cut(preview(id)); PCART.realize(root, m, id + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
