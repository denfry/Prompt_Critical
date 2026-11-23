// I2 steel line at real scale (1 block = 1 m, 16 units = 1 m):
//   electric_arc_furnace_real  a 6 t three-phase Héroult furnace: riveted shell ⌀2.75 m on two rockers over the rails
//                              (tilting to the spout on the north), the brick-domed roof on its ring, the charging door
//                              on the east, three graphite electrodes ⌀0.35 m in their copper clamps on arms from three
//                              masts on the west, the flexible cables from the transformer house. Replaces the 3x3x3
//                              shell + roof multiblock look with one whole model. After Stansfield, "The Electric
//                              Furnace" (1914) and Robiette, "Electric Melting Practice" (1955).
//   ingot_mold_real            a 4 t big-end-down ingot mould of cast iron, 0.65 m square tapering to 0.55 m, 1.8 m high,
//                              lugs and lifting trunnions, on its stool plate.
//   ladle_station_real         a ladle treatment station of the 1960s: a 40 t teeming ladle on its transfer car over the
//                              rails under the water-cooled hood with the fume duct, the alloy bunkers with the weigh
//                              hopper and chute, the argon lance on its hoist frame and the wire feeder.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in SI2.ids:
//   SI2.build(repo, id); SI2.save(repo, id); per partial SI2.buildPartial(repo, id, p); SI2.savePartial(repo, id, p)
//
// All: controller (0, 0, 0) under the vessel axis (8, ., 8), front north; model px.
//   electric_arc_furnace  footprint x -1..1, y 0..4, z -1..1. Tap on the north over the spout, lip (8, 34, -16);
//                         charge through the door on the east of (1, 1, 0) at (32, 32, 8); power in on the west of
//                         (-1, 3, 0) at (-16, 56, 8). Shell tilts about x on the rockers (axis y 12, z 8), spout down
//                         north, not a partial. Partials: electrodes (three electrodes, arms, clamps) translate along y
//                         (raise up to 8 px, slip down as they burn), saved shifted (0, -48, 0): draw 48 px up; glow
//                         (door slot and arc light round the electrode holes) full-bright while arcing, saved
//                         shifted (0, -20, 0): draw 20 px up.
//   ingot_mold            footprint x 0..0, y 0..1, z 0..0. Pour in from above at (8, 32, 8). Partial melt (full-bright
//                         top of the steel) unshifted, show when filled.
//   ladle_station         footprint x -1..1, y 0..4, z -1..1. Ladle in and out on the car along z (rails, north and south
//                         faces of (0, 0, ±1)); alloys in at the bunker tops, up face of (1, 4, 1) at (24, 80, 24); fume
//                         out of the duct, up face of (-1, 4, 1) at (-8, 80, 24); argon in, west face of (-1, 3, 0) at
//                         (-16, 56, 8). Partial lance (lance and its crosshead) translates down along y up to 24 px into
//                         the melt, saved shifted (0, -48, 0): draw 48 px up.
(function () {
  const C = [8, 8];
  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
    coals: { tex: 'smithy/coals', P: 16 }, mp: { tex: 'machine_shop/paint', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/steel' });

  // ------------------------------------------------------------------ arc furnace
  const R = 22, YS = 14, YR = 42;
  const EL = [0, 120, 240].map((a) => [C[0] + 6 * Math.cos(a * Math.PI / 180), C[1] + 6 * Math.sin(a * Math.PI / 180)]);
  const eaf = {
    static(m) {
      for (const [n, x0, x1] of [['w', -14, -8], ['e', 24, 30]]) {
        m.box(`rail_bed_${n}`, [x0, 0, -12], [x1, 8, 28], 'conc', { hide: ['down'] });
        m.box(`rail_${n}`, [x0 + 1.6, 8, -12], [x1 - 1.6, 9, 28], 'bright', { split: 'z' });
        m.box(`rocker_${n}`, [x0 + 1, 9, -8], [x1 - 1, 14, 24], 'ci', { split: 'z' });
      }
      m.cylY('bowl', C, R - 4, 10, YS, 'plate', { bottom: true });
      m.cylY('shell', C, R, YS, YR, 'plate', { bottom: true });
      for (const y of [YS + 0.6, 28, YR - 0.6]) m.ringY(`seam_${y}`, C, R + 0.3, 0.5, y - 0.6, y + 0.6, 'rivets');
      // spout on the north, lined
      m.box('spout', [3, 32, -16], [13, 34, C[1] - R + 1], 'ci', { split: '' });
      m.box('spout_lining', [4.6, 34, -16], [11.4, 34.6, C[1] - R + 1], 'brick', { split: '' });
      for (const x of [3, 12.4]) m.box(`spout_wall_${x}`, [x, 34, -16], [x + 0.6, 36, C[1] - R + 1], 'ci', { split: '' });
      // charging door on the east with its frame and lifting chain
      m.box('door_frame', [C[0] + R - 1, 24, 0], [32, 38, 16], 'ci', { split: '' });
      m.box('door', [31, 25.4, 1.6], [32, 36.6, 14.4], 'ci', { split: '' });
      m.box('door_slot', [31.9, 30.6, 3], [32, 31.4, 13], 'dark', { split: '' });
      // roof: ring and the stepped brick dome with the electrode holes' collars
      m.ringY('roof_ring', C, R + 0.4, 2.4, YR, YR + 2, 'ci');
      m.cylY('dome_0', C, R - 2, YR, YR + 3, 'brick');
      m.cylY('dome_1', C, R - 8, YR + 3, YR + 6, 'brick');
      m.cylY('dome_2', C, R - 13, YR + 6, YR + 8, 'brick');
      for (const [i, [x, z]] of EL.entries()) m.cylY(`collar_${i}`, [x, z], 4, YR + 8, YR + 9, 'gw', { cap: 'vver/dark' });
      // masts on the west, the flexible cables to the transformer house wall
      for (const [i, [, z]] of EL.entries()) {
        m.box(`mast_${i}`, [-14, YS, z - 1.6], [-11, 76, z + 1.6], 'mp', { split: 'y' });
        m.box(`cable_${i}`, [-16, 56 + i * 2, z - 0.8], [-14, 57.6 + i * 2, z + 0.8], 'dark', { split: '' });
      }
      m.box('mast_tie', [-14.6, 74, EL[2][1] - 2.4], [-10.4, 76, EL[1][1] + 2.4], 'mp', { split: '' });
      m.decal('plate', [4, 20, C[1] - R - 0.6], [12, 24.8, C[1] - R - 0.4], 'st', 'north', 'pl.maker');
    },
    parts: {
      electrodes(m) {
        for (const [i, [x, z]] of EL.entries()) {
          m.cylY(`electrode_${i}`, [x, z], 2.8, 44, 76, 'dark', { cap: 'vver/dark' });
          m.box(`clamp_${i}`, [x - 3.6, 66, z - 3.6], [x + 3.6, 70, z + 3.6], 'br', { split: '' });
          m.box(`arm_${i}`, [-11, 67, z - 1.4], [x - 3.6, 69.6, z + 1.4], 'mp', { split: 'x' });
          m.box(`arm_carriage_${i}`, [-11, 64, z - 2], [-9, 72, z + 2], 'mp', { split: '' });
        }
      },
      glow(m) {
        m.box('glow_door', [31.92, 30.6, 3], [32, 31.4, 13], 'coals', { split: '' });
        for (const [i, [x, z]] of EL.entries()) m.ringY(`glow_hole_${i}`, [x, z], 3.4, 0.5, YR + 9, YR + 9.2, 'coals');
      },
    },
    shift: { electrodes: [0, -48, 0], glow: [0, -20, 0] },
    dir: 'steelworks/electric_arc_furnace',
  };

  // ------------------------------------------------------------------ ingot mould
  const mold = {
    static(m) {
      m.box('stool', [1, 0, 1], [15, 3, 15], 'ci', { split: '' });
      const n = 6;
      for (let i = 0; i < n; i++) {
        const h = (5.2 - 4.4) * (i / (n - 1)), w0 = 5.2 - h;
        const y0 = 3 + 28.8 * i / n, y1 = 3 + 28.8 * (i + 1) / n;
        m.box(`mold_${i}`, [8 - w0, y0, 8 - w0], [8 + w0, y1, 8 + w0], 'ci', { split: '' });
      }
      m.box('mold_mouth', [4, 31.6, 4], [12, 31.8, 12], 'dark', { split: '' });
      for (const [n2, f, t] of [['n', [6, 22, 1.6], [10, 26, 2.8]], ['s', [6, 22, 13.2], [10, 26, 14.4]]]) m.box(`lug_${n2}`, f, t, 'ci', { split: '' });
      for (const [n2, f, t] of [['w', [1.6, 26, 7], [2.8, 28, 9]], ['e', [13.2, 26, 7], [14.4, 28, 9]]]) m.box(`trunnion_${n2}`, f, t, 'ci', { split: '' });
    },
    parts: { melt(m) { m.box('glow_steel', [4.2, 31.2, 4.2], [11.8, 31.7, 11.8], 'coals', { split: '', hide: ['down'] }); } },
    shift: {},
    dir: 'steelworks/ingot_mold',
  };

  // ------------------------------------------------------------------ ladle treatment station
  const LR = 22;
  const station = {
    static(m) {
      // rails along z, the transfer car, the ladle on it
      for (const x of [-8, 23.2]) m.box(`rail_${x}`, [x, 0, -16], [x + 0.8, 1, 32], 'bright', { split: 'z' });
      m.box('car', [-10, 1, -14], [26, 6, 30], 'ci', { split: '' });
      for (const z of [-10, 26]) for (const x of [-9.6, 22.8]) m.prism(`wheel_${x}_${z}`, 'x', [3, z], 2.6, x, x + 2.8, 'ci', { split: '', ends: ['east', 'west'] });
      const n = 6;
      for (let i = 0; i < n; i++) m.cylY(`ladle_${i}`, C, LR - 3 + 3 * (i + 0.5) / n, 6 + 52 * i / n, 6 + 52 * (i + 1) / n, 'plate', i === 0 ? { bottom: true } : {});
      m.ringY('ladle_rim', C, LR + 0.4, 2, 56, 58, 'ci');
      m.prism('trunnion_w', 'x', [44, 8], 2, C[0] - LR - 2, C[0] - LR + 1, 'bright', { split: '', ends: ['west'] });
      m.prism('trunnion_e', 'x', [44, 8], 2, C[0] + LR - 1, C[0] + LR + 2, 'bright', { split: '', ends: ['east'] });
      // water-cooled hood on its frame, the fume duct up
      for (const [x, z] of [[-15, -15], [29, -15], [-15, 29], [29, 29]]) m.box(`frame_${x}_${z}`, [x, 0, z], [x + 2, 72, z + 2], 'st', { split: 'y' });
      m.box('frame_top_n', [-15, 70, -15], [31, 72, -13], 'st', { split: 'x' });
      m.box('frame_top_s', [-15, 70, 29], [31, 72, 31], 'st', { split: 'x' });
      m.cylY('hood', C, LR + 1, 60, 64, 'gw', { bottom: true, cap: 'vver/flange' });
      m.cylY('hood_top', C, LR - 6, 64, 66, 'gw', { cap: 'vver/flange' });
      m.box('duct', [-12, 64, 18], [-4, 80, 30], 'st', { split: 'y' });
      m.box('duct_elbow', [-12, 64, 10], [-4, 70, 18], 'st', { split: '' });
      // alloy bunkers, weigh hopper and chute through the hood
      for (const x of [17, 25]) m.box(`bunker_${x}`, [x - 3.6, 70, 18], [x + 3.6, 80, 30], 'mp', { split: '' });
      m.box('weigh_hopper', [16, 66, 18], [28, 70, 26], 'mp', { split: '' });
      m.box('chute', [14, 62, 14], [18, 66, 22], 'st', { split: '' });
      // argon line and the wire feeder on the west
      m.box('argon_line', [-16, 55.2, 7], [-12, 56.8, 9], 'yel', { split: '' });
      m.box('wire_feeder', [-14, 66, -6], [-6, 72, 2], 'mp', { split: '' });
      m.box('lance_guide', [5, 66, 5], [11, 72, 11], 'st', { split: '' });
    },
    parts: {
      lance(m) {
        m.cylY('lance', [8, 8], 1.2, 34, 78, 'conc');
        m.box('crosshead', [4, 74, 6], [12, 78, 10], 'st', { split: '' });
        m.box('argon_hose', [-6, 75, 7.2], [4, 76.6, 8.8], 'yel', { split: '' });
      },
    },
    shift: { lance: [0, -48, 0] },
    dir: 'steelworks/ladle_station',
  };

  const MACH = { electric_arc_furnace_real: eaf, ingot_mold_real: mold, ladle_station_real: station };
  function model(id) { const m = mk(); MACH[id].static(m); return m; }
  function partial(id, p, raw) { const s = mk(); MACH[id].parts[p](s); const [dx, dy, dz] = raw ? [0, 0, 0] : (MACH[id].shift[p] || [0, 0, 0]); return mk().merge(s, dx, dy, dz, ''); }
  window.SI2 = {
    ids: Object.keys(MACH), parts: (id) => Object.keys(MACH[id].parts), model, partial, dir: (id) => MACH[id].dir,
    build: (root, id) => { const m = PCVV.cut(model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, MACH[id].dir),
    buildPartial: (root, id, p) => { const m = partial(id, p); PCART.realize(root, m, id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, id, p) => PCVV.savePartial(root, id, p, MACH[id].dir),
    preview: (root, id) => { const m = model(id); for (const p of Object.keys(MACH[id].parts)) m.merge(partial(id, p, true), 0, 0, 0, ''); PCART.realize(root, PCVV.cut(m), id + '_preview'); return PCVV.bounds(m); },
  };
})();
