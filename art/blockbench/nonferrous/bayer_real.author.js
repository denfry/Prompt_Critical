// Bayer process vessels at real scale (1 block = 1 m, 16 px = 1 m):
// - bayer_digester_real: a vertical steam-heated autoclave of the 1950s battery type, 2.5 m across and about 9 m
//   high over its heads, clad in lagging with bands, on a skirt over a concrete plinth; the slurry inlet and the
//   safety valve and pressure gauge on the top head, the live steam line (GOST red) up the north side into the
//   heating nozzle, the discharge pipe from the top down the west side to the next vessel, the caged ladder up the
//   east side and the ring platform with its handrail at the top.
// - alumina_precipitator_real: a mechanically agitated decomposer, 6 m across and 9 m high, of welded rings on a
//   ring foundation, the bridge across the top carrying the agitator drive (motor, worm reducer), the liquor feed
//   and the overflow launder at the rim, the hydrate slurry draw-off at the foot, the stair cage up the side.
//   Partial `drive` (alumina_precipitator_real/partial/drive): the agitator shaft top with the coupling and hub,
//   turns about y through (x 8, z 8); stored at y -128 (model y 128..160), draw it at +128 y.
//
// Load after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then BAYR.build(repo, k) /* wait */
// BAYR.save(repo, k), k = digester | precipitator; BAYR.buildPartial(repo) / BAYR.savePartial(repo) for the drive.
//
// digester: controller (0, 0, 0); footprint x -1..1, y 0..9, z -1..1. Ports (px): slurry in on the top face of
// (0, 9, 0) at (8, 160, 8); slurry out on the west face of (-1, 1, 0) at (-16, 24, 8); live steam in on the north
// face of (0, 1, -1) at (8, 24, -16).
// precipitator: controller (0, 0, 0); footprint x -3..3, y 0..9, z -3..3. Ports (px): green liquor in on the top
// face of (0, 9, -2) at (8, 160, -24); hydrate slurry out on the west face of (-3, 0, 0) at (-48, 8, 8); overflow out
// on the east face of (3, 8, 0) at (64, 140, 8).
(function () {
  const C = [8, 8];
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, shell: { tex: 'material/steel', P: 16 } });
  const mk = (p) => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: p || 'vver/clad' });

  function digester() {
    const m = mk(), R = 20, Y0 = 18, Y1 = 140;
    m.box('plinth', [-16, 0, -16], [32, 3, 32], 'conc', { split: 'xz' });
    m.cylY('skirt', C, R - 2, 3, Y0, 'shell');
    m.headDown('bottom', C, R, Y0 + 4, 4, 2, 'clad');
    m.cylY('shell', C, R, Y0 + 4, Y1, 'clad');
    for (let y = Y0 + 16; y < Y1; y += 24) m.ringY(`band_${y}`, C, R + 0.3, 0.4, y, y + 0.8, 'st');
    m.headUp('top', C, R, Y1, 8, 4, 'clad', 'vver/cap');
    // top fittings: slurry inlet, safety valve, gauge
    m.cylY('inlet', C, 2.4, Y1 + 6, 160, 'shell');
    m.ringY('inlet_flange', C, 3.4, 1, 159, 160, 'ci');
    m.cylY('sv_branch', [16, 2], 1.2, Y1 + 4, 154, 'gs');
    m.box('sv', [14.4, 154, 0.4], [17.6, 157.6, 3.6], 'red', { split: '' });
    m.cylY('pg_branch', [0, 14], 0.6, Y1 + 4, 152, 'st');
    m.prism('pg', 'z', [0, 154], 1.6, 13, 14, 'st', { split: '', ends: ['north', 'south'] });
    // live steam up the north side into the heating nozzle at the foot
    m.prism('steam_in', 'z', [8, 24], 1.6, -16, C[1] - R + 1, 'gs', { split: '' });
    m.disc('steam_flange', 'z', [8, 24], 2.6, -15.9, -15.2, 'ci', 'vver/flange', 'north');
    m.box('steam_valve', [5.4, 21.4, -13], [10.6, 26.6, -9], 'gm', { split: '' });
    m.wheelY('steam_wheel', 8, 28, -11, 1.8, 'red');
    // discharge pipe from the top head down the west side to the west face low down
    m.prism('dis_top', 'x', [Y1 + 4, 8], 1.8, -14, C[0] - 2, 'shell', { split: 'x' });
    m.cylY('dis_down', [-14, 8], 1.8, 24, Y1 + 5.8, 'shell');
    m.prism('dis_out', 'x', [24, 8], 1.8, -16, -12.2, 'shell', { split: '' });
    m.disc('dis_flange', 'x', [24, 8], 2.8, -15.9, -15.2, 'ci', 'vver/flange', 'west');
    for (let y = 40; y < Y1; y += 32) m.box(`dis_clip_${y}`, [-14, y, 7], [C[0] - R, y + 1, 9], 'frame', { split: '' });
    // caged ladder up the east side and the ring platform at the top
    m.ladderZ('ladder', 4, 12, C[1] + R + 0.6, 1.4, 3, Y1, 'st');
    m.ringY('platform', C, R + 4, 4, Y1, Y1 + 0.8, 'grate');
    for (const a of [45, 135, 225, 315]) m.radial(`bracket_${a}`, C, R, R + 4, a, 0.8, Y1 - 4, Y1, 'frame');
    m.railY('rail', C, R + 3.6, Y1 + 0.8, 'yel');
    m.decal('plate', [2, 40, C[1] - R - 0.4], [10, 44, C[1] - R - 0.3], 'clad', 'north', 'pl.maker');
    return m;
  }

  function precipitator() {
    const m = mk('material/steel'), R = 46, Y1 = 136;
    m.ringY('foundation', C, R + 2, 8, 0, 4, 'conc');
    m.cylY('foundation_core', C, R - 6, 0, 4, 'conc');
    m.cylY('tank', C, R, 4, Y1, 'shell');
    for (let y = 28; y < Y1; y += 24) m.ringY(`seam_${y}`, C, R + 0.2, 0.3, y, y + 0.4, 'frame');
    m.ringY('stiff_top', C, R + 1, 1.4, Y1 - 2, Y1, 'frame');
    // overflow launder round the rim on the east half, the overflow out to the east face
    m.ringY('launder', C, R + 4, 3, Y1 - 6, Y1 - 4, 'shell');
    m.prism('overflow', 'x', [140, 8], 2.4, C[0] + R + 2, 64, 'shell', { split: '' });
    m.disc('overflow_flange', 'x', [140, 8], 3.4, 63.2, 63.9, 'ci', 'vver/flange', 'east');
    // roof plate and the bridge across the top with the agitator drive
    m.cylY('roof', C, R, Y1, Y1 + 1, 'shell', { cap: 'vver/cap' });
    m.box('bridge', [C[0] - R, Y1 + 1, C[1] - 5], [C[0] + R, Y1 + 5, C[1] + 5], 'frame', { split: 'x' });
    m.box('bridge_grating', [C[0] - R, Y1 + 5, C[1] - 6], [C[0] + R, Y1 + 5.6, C[1] + 6], 'grate', { split: 'x' });
    m.box('drive_base', [C[0] - 7, Y1 + 5.6, C[1] - 7], [C[0] + 7, Y1 + 8, C[1] + 7], 'ci', { split: '' });
    m.box('reducer', [C[0] - 6, Y1 + 8, C[1] - 6], [C[0] + 6, Y1 + 16, C[1] + 6], 'pnt', { split: '' });
    m.prism('motor', 'x', [Y1 + 12, C[1]], 4, C[0] + 6, C[0] + 20, 'pnt', { split: '', ends: ['east'] });
    for (const x of [C[0] - R + 2, C[0] + R - 2]) m.box(`bridge_rail_${x}`, [x - 0.4, Y1 + 5.6, C[1] - 6], [x + 0.4, Y1 + 12, C[1] - 5.6], 'yel', { split: '' });
    m.box('rail_n', [C[0] - R, Y1 + 11, C[1] - 6], [C[0] + R, Y1 + 11.6, C[1] - 5.6], 'yel', { split: 'x' });
    m.box('rail_s', [C[0] - R, Y1 + 11, C[1] + 5.6], [C[0] + R, Y1 + 11.6, C[1] + 6], 'yel', { split: 'x' });
    // liquor feed down to the roof on the north side
    m.cylY('feed', [8, -24], 2.4, Y1 + 1, 160, 'shell');
    m.ringY('feed_flange', [8, -24], 3.4, 1, 159, 160, 'ci');
    // hydrate slurry draw-off at the foot, out to the west face
    m.prism('draw', 'x', [8, 8], 2.4, -48, C[0] - R + 1, 'shell', { split: 'x' });
    m.disc('draw_flange', 'x', [8, 8], 3.4, -47.9, -47.2, 'ci', 'vver/flange', 'west');
    m.box('draw_valve', [-44, 4, 4], [-38, 12, 12], 'gm', { split: '' });
    // stair: a caged ladder up the south side to the bridge
    m.ladderZ('ladder', 4, 12, C[1] + R + 0.6, 1.6, 4, Y1 + 5, 'st', 5);
    m.decal('plate', [2, 30, C[1] - R - 0.4], [14, 36, C[1] - R - 0.3], 'shell', 'north', 'pl.maker');
    return m;
  }
  function drive() {
    const m = mk('material/bright_steel'), Y1 = 136;
    m.cylY('shaft', C, 1.6, Y1 - 8, Y1 + 8, 'bright');
    m.cylY('hub', C, 3, Y1 - 4, Y1 - 1, 'ci');
    m.cylY('coupling', C, 3.2, Y1 + 5.6, Y1 + 8, 'forged');
    const out = mk('material/bright_steel'); return out.merge(m, 0, -128, 0, '');
  }

  const ID = { digester: 'bayer_digester_real', precipitator: 'alumina_precipitator_real' };
  const B = { digester, precipitator };
  window.BAYR = {
    ID,
    build: (root, k) => { const m = PCVV.cut(B[k]()); PCART.realize(root, m, ID[k]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, ID[k], 'nonferrous'),
    buildPartial: (root) => { const m = drive(); PCART.realize(root, m, 'alumina_precipitator_real_drive'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root) => PCVV.savePartial(root, 'alumina_precipitator_real', 'drive', 'nonferrous'),
  };
})();
