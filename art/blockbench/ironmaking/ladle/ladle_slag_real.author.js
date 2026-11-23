// Two cast-house pieces at real scale (1 block = 1 m, 16 units = 1 m):
//   ladle_real            a 20 t hot-metal ladle of the 1900s: riveted tapered shell ⌀2.0 m at the top, 2.6 m deep,
//                         lined, with the pouring lip, the trunnion band and trunnions resting in a ladle stand of two
//                         cast pedestals, the worm and quadrant tilting gear on the east pedestal taking the Create
//                         shaft. After Johnson, "Blast-Furnace Construction in America" (1917): 15–25 t ladles
//                         7–8 ft across.
//   slag_granulator_real  wet slag granulation of the 1900s: the slag runner end over the water-jet box, a concrete
//                         granulation tank with the water in it, a vertical bucket elevator lifting the granulated
//                         slag to a chute over a wagon on the west, the water main with its valve.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in LSR.ids:
//   LSR.build(repo, id); LSR.save(repo, id); and per partial LSR.buildPartial(repo, id, p); LSR.savePartial(repo, id, p)
//
// ladle_real: controller (0, 0, 0) under the ladle axis (8, ., 8), front north (pouring lip). Footprint x -1..1, y 0..2,
//   z -1..1. Trunnion axis along x at (y 32, z 8). Metal in from above, (0, 2, 0) up; metal out over the lip on the
//   north of (0, 2, -1), lip (8, 44, -9). Create shaft into the tilt gear, east face of (1, 0, 0) at (32, 8, 8), axis x.
//   Partials, both saved shifted (0, -16, 0) (draw 16 px up), tilt together about x through (y 32, z 8), lip
//   forward (north) = negative angle about x:
//     body — shell, lining rim, lip, trunnion band and trunnions;   melt — full-bright iron surface, show when filled.
// slag_granulator_real: controller (0, 0, 0) at the runner end, front north. Footprint x -1..1, y 0..3, z 0..3.
//   Slag in on the north face of (0, 0, 0), runner lip (8, 12, 0); water in, east face of (1, 0, 1) at (32, 8, 24);
//   granulated slag out of the chute, west face of (-1, 3, 3) at (-16, 56, 56); Create shaft into the elevator head,
//   east face of (0, 3, 3) at (16, 56, 56), axis x.
//   Partials: head (head sprocket and buckets over it) turns about x through (y 56, z 52), saved shifted
//   (0, -32, -32): draw it 32 px up and 32 px south. glow (full-bright slag stream) unshifted, show while running.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
    coals: { tex: 'smithy/coals', P: 16 }, sand: { tex: 'sand_mold/sand', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/cast_iron' });

  // ------------------------------------------------------------------ ladle
  const C = [8, 8], YB = 4, YT = 45.6, RB = 13, RT = 16, TY = 32;
  const ladle = {
    static(m) {
      m.box('stand_base', [-16, 0, -4], [32, 1.4, 20], 'conc', { hide: ['down'] });
      for (const [n, x0, x1] of [['w', -16, -10], ['e', 26, 32]]) {
        m.box(`pedestal_${n}`, [x0, 1.4, 2], [x1, TY - 3, 14], 'ci', { split: 'y' });
        m.box(`saddle_${n}`, [x0, TY - 3, 4], [x1, TY - 1.6, 12], 'ci', { split: '' });
      }
      // tilting gear on the east pedestal: worm box, the quadrant shown fixed to the trunnion in the partial
      m.box('worm_box', [26, 10, -4], [32, 18, 2], 'gw', { split: '' });
      m.box('worm_shaft', [29.4, 6, -1.2], [30.6, 10, 0], 'bright', { split: '' });
      m.prism('input_boss', 'x', [8, 8], 2.4, 29.6, 32, 'ci', { split: '', ends: ['east'] });
      m.box('input_gear', [26.6, 1.4, 4.4], [29.6, 12, 11.6], 'gw', { split: '' });
    },
    parts: {
      body(m) {
        const n = 7;
        for (let i = 0; i < n; i++) {
          const r = RB + (RT - RB) * (i + 0.5) / n, y0 = YB + (YT - YB) * i / n, y1 = YB + (YT - YB) * (i + 1) / n;
          m.cylY(`shell_${i}`, C, r, y0, y1, 'plate', i === 0 ? { bottom: true } : {});
        }
        for (const y of [16, 28, 40]) m.ringY(`seam_${y}`, C, RB + (RT - RB) * (y - YB) / (YT - YB) + 0.3, 0.5, y - 0.5, y + 0.5, 'rivets');
        m.ringY('rim', C, RT + 0.4, 2.4, YT - 1, YT, 'ci');
        m.ringY('lining', C, RT - 1.6, 1.8, YT - 1.4, YT - 0.2, 'brick');
        m.box('lip', [4, YT - 3, C[1] - RT - 3], [12, YT - 1, C[1] - RT + 1], 'ci', { split: '' });
        m.box('lip_lining', [5, YT - 1, C[1] - RT - 3], [11, YT - 0.4, C[1] - RT + 1], 'brick', { split: '' });
        m.ringY('trunnion_band', C, RB + (RT - RB) * (TY - YB) / (YT - YB) + 0.6, 1.2, TY - 2.4, TY + 2.4, 'ci');
        m.prism('trunnion_w', 'x', [TY, C[1]], 2, -14, -7, 'bright', { split: '', ends: ['west'] });
        m.prism('trunnion_e', 'x', [TY, C[1]], 2, 23, 30, 'bright', { split: '', ends: ['east'] });
        m.disc('quadrant', 'x', [TY, C[1]], 7, 26.2, 27, 'gw', 'vver/flange', 'east');
      },
      melt(m) {
        m.box('glow_iron', [C[0] - RT + 2.4, YT - 2.6, C[1] - RT + 2.4], [C[0] + RT - 2.4, YT - 2.4, C[1] + RT - 2.4], 'coals', { split: '', hide: ['down'] });
      },
    },
    shift: { body: [0, -16, 0], melt: [0, -16, 0] },
    dir: 'ironmaking/ladle',
  };

  // ------------------------------------------------------------------ slag granulator
  const slag = {
    static(m) {
      // runner end on its trestle, the water-jet box under the lip
      m.box('runner', [4, 12, 0], [12, 15, 14], 'ci', { split: '' });
      m.box('runner_lining', [5, 15, 0], [11, 15.4, 14], 'soot', { split: '' });
      m.box('runner_trestle', [5, 0, 4], [11, 12, 6], 'ci', { split: '' });
      m.box('jet_box', [2, 4, 14], [14, 11, 18], 'ci', { split: '' });
      m.box('jet_pipe', [14, 6, 15], [32, 8, 17], 'gw', { split: 'x' });
      m.box('water_main', [30, 0, 15], [32, 8, 26], 'gw', { split: '' });
      m.prism('water_valve', 'z', [30.4, 10], 1.4, 20, 22, 'ci', { split: '' });
      m.box('valve_wheel', [29, 12, 20.6], [32, 12.6, 21.4], 'red', { split: '' });
      // granulation tank: concrete walls, water, the slope down to the elevator boot
      m.box('tank_w', [-14, 0, 16], [-12, 10, 58], 'conc', { split: 'z' });
      m.box('tank_e', [28, 0, 16], [30, 10, 58], 'conc', { split: 'z' });
      m.box('tank_n', [-12, 0, 16], [28, 10, 18], 'conc', { split: 'x' });
      m.box('tank_s', [-12, 0, 56], [-1, 10, 58], 'conc', { split: '' });
      m.box('tank_s2', [17, 0, 56], [28, 10, 58], 'conc', { split: '' });
      m.box('tank_floor', [-12, 0, 18], [28, 1, 56], 'conc', { hide: ['down'] });
      m.box('water', [-12, 7, 18], [28, 7.4, 48], 'gw', { split: 'z', hide: ['down'] });
      m.box('granules', [-12, 1, 36], [28, 6, 48], 'sand', { split: 'z' });
      // vertical bucket elevator: boot in the tank, casing up to the head, chute to the west over the wagon
      m.box('elevator_casing', [0, 0, 48], [16, 52, 56], 'st', { split: 'y' });
      m.box('elevator_head', [-1, 52, 47], [17, 60, 57], 'st', { split: '' });
      m.box('head_hood', [-1, 60, 49], [17, 62, 55], 'st', { split: '' });
      m.box('chute', [-16, 50, 50], [-1, 52, 56], 'st', { split: 'x' });
      m.box('chute_side_n', [-16, 52, 50], [-1, 53.4, 50.6], 'st', { split: 'x' });
      m.box('chute_side_s', [-16, 52, 55.4], [-1, 53.4, 56], 'st', { split: 'x' });
      m.prism('drive_boss', 'x', [56, 56], 2, 16, 16.8, 'ci', { split: '', ends: ['east'] });
      m.box('ladder_l', [17, 0, 46], [17.6, 52, 46.6], 'st', { split: 'y' });
      m.box('ladder_r', [20.4, 0, 46], [21, 52, 46.6], 'st', { split: 'y' });
      for (let y = 4; y < 52; y += 4.8) m.box(`rung_${Math.round(y * 10)}`, [17.6, y, 46.1], [20.4, y + 0.5, 46.5], 'st', { split: '' });
      // wagon track along the west under the chute
      for (const x of [-15.6, -5.6]) m.box(`rail_${x}`, [x, 0, 16], [x + 0.8, 1, 64], 'bright', { split: 'z' });
      m.box('wagon_body', [-16, 4, 40], [-5, 14, 62], 'st', { split: '' });
      for (const z of [44, 58]) m.prism(`wagon_wheel_${z}`, 'x', [2.6, z], 2.6, -15.4, -5.4, 'ci', { split: '', ends: ['east', 'west'] });
      m.box('wagon_load', [-15.4, 14, 41], [-5.6, 15, 61], 'sand', { split: '' });
    },
    parts: {
      head(m) {
        m.prism('head_shaft', 'x', [56, 52], 0.8, 0, 16, 'bright', { split: '', ends: ['east', 'west'] });
        m.prism('head_sprocket', 'x', [56, 52], 3.2, 4, 12, 'ci', { split: '', ends: ['east', 'west'] });
      },
      glow(m) {
        m.box('glow_slag', [6, 11, 12], [10, 15.6, 15], 'coals', { split: '' });
      },
    },
    shift: { head: [0, -32, -32], glow: [0, 0, 0] },
    dir: 'ironmaking/slag_granulator',
  };

  const MACH = { ladle_real: ladle, slag_granulator_real: slag };
  function model(id) { const m = mk(); MACH[id].static(m); return m; }
  function partial(id, p, raw) { const s = mk(); MACH[id].parts[p](s); const [dx, dy, dz] = raw ? [0, 0, 0] : MACH[id].shift[p]; return mk().merge(s, dx, dy, dz, ''); }
  window.LSR = {
    ids: Object.keys(MACH), parts: (id) => Object.keys(MACH[id].parts), model, partial,
    build: (root, id) => { const m = PCVV.cut(model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, MACH[id].dir),
    buildPartial: (root, id, p) => { const m = partial(id, p); PCART.realize(root, m, id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, id, p) => PCVV.savePartial(root, id, p, MACH[id].dir),
    preview: (root, id) => { const m = model(id); for (const p of Object.keys(MACH[id].parts)) m.merge(partial(id, p, true), 0, 0, 0, ''); PCART.realize(root, PCVV.cut(m), id + '_preview'); return PCVV.bounds(m); },
  };
})();
