// Steel back-legged headframe at real scale (1 block = 1 m): a four-post shaft tower of riveted box columns round
// the 3 x 3 shaft opening, X-braced in 3 m panels above the landing, a grating sheave deck at 15 m with a hand rail,
// two back struts at 22.5 degrees from the vertical down to the engine side, ground ties from the tower to the strut
// feet, ashlar plinths, and a hooped ladder up the west side to the deck. The 3.0 m sheave turns on two pillow blocks
// over the back posts; it is the partial `headframe_steel/partial/sheave`. Dimensions and sources:
// docs/research/headframe_steel-dimensions.md (disk only).
//
// Controller = (0, 0, 0): the middle cell of the 3 x 3 shaft column, in the row the old headframe anchor stood in.
// HoistGeometry puts the cage top one row below that row, so the landing (bank) floor is y = -64 (cell row -4); the
// legs stand on it and the lowest panel is left open for the landings. Front north; the engine is to the south (+z)
// and the struts point to it. The column x -16..32, z -16..32 stays free all the way up for the cage and the rope.
//
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then HEADFRAME_STEEL.build(repo) /* wait */
// HEADFRAME_STEEL.save(repo); HEADFRAME_STEEL.buildSheave(repo) /* wait */ HEADFRAME_STEEL.saveSheave(repo).
(function () {
  const BANK = -64, DECK = 176, AXIS = [8, 192, 32], R = 24;   // sheave axis along x at y 192, z 32 (units)
  const P0 = -24, P1 = 32, PW = 8;                              // tower posts: x/z from P0..P0+8 and P1..P1+8
  const LEVELS = [-16, 32, 80, 128];                             // girder rows above the landing panel
  const TAN = Math.tan(Math.PI / 8), STRUT_FOOT = 140;

  function model() {
    const m = PCVV.mk('vver/gost_brown');
    const posts = [P0, P1];

    // ashlar plinths and the four tower posts
    for (const x of posts) for (const z of posts) {
      m.box(`plinth_${x}_${z}`, [x - 2, BANK, z - 2], [x + PW + 2, BANK + 6, z + PW + 2], 'ashlar');
      m.box(`post_${x}_${z}`, [x, BANK + 6, z], [x + PW, DECK, z + PW], 'oil');
    }
    // girders on the four faces at each level, a sill on the east and west faces at the bank
    for (const y of [...LEVELS, DECK - 6]) {
      for (const z of posts) m.box(`gird_x_${y}_${z}`, [P0 + PW, y, z + 1], [P1, y + 5, z + PW - 1], 'oil');
      for (const x of posts) m.box(`gird_z_${y}_${x}`, [x + 1, y, P0 + PW], [x + PW - 1, y + 5, P1], 'oil');
    }
    for (const x of posts) m.box(`sill_${x}`, [x + 1, BANK + 6, P0 + PW], [x + PW - 1, BANK + 10, P1], 'oil');

    // X bracing: 48 x 48 panels between the girders, 45 degrees; the lowest panel stays open on north and south
    const span = P1 - (P0 + PW), mid = (P0 + PW + P1) / 2, L = span * Math.SQRT2 - 2;
    const panels = [[BANK + 10, -16], [-11, 32], [37, 80], [85, 128], [133, DECK - 6]];
    panels.forEach(([y0, y1], i) => {
      const cy = (y0 + y1) / 2;
      for (const s of [45, -45]) {
        if (i > 0) for (const z of posts) {
          const zc = z + PW / 2;
          m.box(`brace_ns_${i}_${z}_${s}`, [mid - L / 2, cy - 0.8, zc - 1], [mid + L / 2, cy + 0.8, zc + 1], 'oil',
            { rot: { axis: 'z', angle: s, origin: [mid, cy, zc] } });
        }
        for (const x of posts) {
          const xc = x + PW / 2;
          m.box(`brace_ew_${i}_${x}_${s}`, [xc - 1, cy - 0.8, mid - L / 2], [xc + 1, cy + 0.8, mid + L / 2], 'oil',
            { rot: { axis: 'x', angle: s, origin: [xc, cy, mid] } });
        }
      }
    });

    // back struts: from plinths at z ~ STRUT_FOOT up to the pillow-block beams, 22.5 degrees off the vertical
    const SL = (DECK - 6 - BANK - 6) / Math.cos(Math.PI / 8);
    for (const x of posts) {
      m.box(`strut_plinth_${x}`, [x - 2, BANK, STRUT_FOOT - 8], [x + PW + 2, BANK + 6, STRUT_FOOT + 8], 'ashlar');
      m.box(`strut_${x}`, [x, BANK + 6, STRUT_FOOT - 4], [x + PW, BANK + 6 + SL, STRUT_FOOT + 4], 'oil',
        { rot: { axis: 'x', angle: -22.5, origin: [x + PW / 2, BANK + 6, STRUT_FOOT] } });
      m.box(`tie_ground_${x}`, [x + 1, BANK + 6, P1 + PW], [x + PW - 1, BANK + 11, STRUT_FOOT - 6], 'oil');
    }
    // cross ties between the struts and their diagonals in the strut plane
    for (const y of [0, 64, 128]) {
      const z = STRUT_FOOT - (y - BANK - 6) * TAN;
      m.box(`strut_tie_${y}`, [P0 + PW, y, z - 2.5], [P1, y + 4, z + 2.5], 'oil');
    }

    // sheave deck: grating round a slot for the sheave, extended west to the ladder head; hand rail on the edge
    const D0 = DECK - 4, X0 = P0 - 4, X1 = P1 + PW + 4, Z0 = P0 - 4, Z1 = P1 + PW + 12;
    m.box('deck_w', [X0, D0, Z0], [3, DECK, Z1], 'grate');
    m.box('deck_e', [13, D0, Z0], [X1, DECK, Z1], 'grate');
    m.box('deck_n', [3, D0, Z0], [13, DECK, 6], 'grate');
    m.box('deck_ladder', [-48, D0, -6], [X0, DECK, 22], 'grate');
    const rail = (n, f, t) => {
      m.box(`${n}_top`, [f[0], DECK + 15, f[1]], [t[0], DECK + 15.8, t[1]], 'yel', { split: '' });
      m.box(`${n}_knee`, [f[0], DECK + 7.5, f[1]], [t[0], DECK + 8.2, t[1]], 'yel', { split: '' });
    };
    rail('rail_n', [X0, Z0], [X1, Z0 + 0.8]);
    rail('rail_s', [X0, Z1 - 0.8], [X1, Z1]);
    rail('rail_e', [X1 - 0.8, Z0], [X1, Z1]);
    rail('rail_wn', [X0, Z0], [X0 + 0.8, -6]);
    rail('rail_ws', [X0, 22], [X0 + 0.8, Z1]);
    rail('rail_ln', [-48, -6], [X0, -5.2]);
    rail('rail_ls', [-48, 21.2], [X0, 22]);
    for (const [x, z] of [[X0, Z0], [X1 - 0.8, Z0], [X0, Z1 - 0.8], [X1 - 0.8, Z1 - 0.8], [-48, -6], [-48, 21.2]]) {
      m.box(`rail_post_${x}_${z}`, [x, DECK, z], [x + 0.8, DECK + 15, z + 0.8], 'yel', { split: '' });
    }

    // pillow-block beams along z over the back posts and the two bearings of the sheave axle
    for (const x of [-6, 18]) {
      m.box(`brg_beam_${x}`, [x, DECK, P0 + 2], [x + 4, DECK + 6, Z1 - 2], 'oil');
      m.box(`brg_ped_${x}`, [x - 0.5, DECK + 6, AXIS[2] - 5], [x + 4.5, AXIS[1] - 2, AXIS[2] + 5], 'ci', { split: '' });
      m.box(`brg_cap_${x}`, [x - 0.5, AXIS[1] + 2, AXIS[2] - 4], [x + 4.5, AXIS[1] + 4, AXIS[2] + 4], 'ci', { split: '' });
      m.box(`brg_bolt_${x}`, [x + 1, AXIS[1] + 4, AXIS[2] - 3], [x + 3, AXIS[1] + 5, AXIS[2] + 3], 'bright', { split: '' });
    }
    // rope guide: a timber fender under the sheave where the rope drops into the shaft
    m.box('fender', [2, DECK - 10, 4], [14, DECK - 6, 12], 'wood');

    // ladder in cell x = -3 up the west side, hooped above 2.5 m, brackets to the tower (all ladder_* : walk-in)
    const LX = -38, LZ0 = 2, LZ1 = 14;
    m.box('ladder_sl', [LX, BANK, LZ0], [LX + 1, DECK + 16, LZ0 + 1], 'yel', { split: 'y' });
    m.box('ladder_sr', [LX, BANK, LZ1 - 1], [LX + 1, DECK + 16, LZ1], 'yel', { split: 'y' });
    let k = 0;
    for (let y = BANK + 4; y < DECK + 14; y += 5) m.box(`ladder_rung${k++}`, [LX + 0.1, y, LZ0 + 1], [LX + 0.7, y + 0.5, LZ1 - 1], 'st', { split: '' });
    for (let y = BANK + 40, h = 0; y < DECK - 4; y += 24, h++) {
      m.box(`ladder_hoop${h}_w`, [-47, y, LZ0 - 3], [-46.4, y + 0.6, LZ1 + 3], 'yel', { split: '' });
      m.box(`ladder_hoop${h}_n`, [-47, y, LZ0 - 3], [LX, y + 0.6, LZ0 - 2.4], 'yel', { split: '' });
      m.box(`ladder_hoop${h}_s`, [-47, y, LZ1 + 2.4], [LX, y + 0.6, LZ1 + 3], 'yel', { split: '' });
    }
    for (const y of [BANK + 20, 40, 120]) m.box(`ladder_brk_${y}`, [LX + 1, y, 7], [P0, y + 2, 9], 'st', { split: '' });

    // maker's plate on the north landing girder
    m.decal('plate', [2, -14, P0 - 0.2], [8, -10, P0], 'oil', 'north', 'pl.maker');
    return m;
  }

  // The 3.0 m sheave about the x axis through the partial's (8, 8, 8): two flanged rims with the rope groove
  // between them, eight spokes, a cast hub and the axle to the bearings (x -2..18).
  function sheave() {
    const m = PCVV.mk('material/steel');
    m.ring('rim_w', 'x', [8, 8], R, 2.2, 5.6, 6.6, 'st');
    m.ring('rim_e', 'x', [8, 8], R, 2.2, 9.4, 10.4, 'st');
    m.ring('groove', 'x', [8, 8], R - 1.2, 1.6, 6.6, 9.4, 'dark');
    for (let a = 0; a < 3; a++) {
      m.box(`spoke_${a}`, [7.2, 8 - R + 2, 7.2], [8.8, 8 + R - 2, 8.8], 'st',
        { rot: a ? { axis: 'x', angle: [0, 45, -45][a], origin: [8, 8, 8] } : undefined, split: '' });
    }
    m.box('spoke_h', [7.2, 7.2, 8 - R + 2], [8.8, 8.8, 8 + R - 2], 'st', { split: '' });
    m.prism('hub', 'x', [8, 8], 3.2, 4.5, 11.5, 'ci', { split: '', ends: ['east', 'west'] });
    m.prism('axle', 'x', [8, 8], 1.6, -2, 18, 'bright', { split: '', ends: ['east', 'west'] });
    return m;
  }

  const toAxis = [AXIS[0] - 8, AXIS[1] - 8, AXIS[2] - 8];
  window.HEADFRAME_STEEL = {
    model, sheave, AXIS, R,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'headframe_steel'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'headframe_steel', 'mining'),
    // preview with the sheave in place (scale shots only, never saved)
    preview: (root) => { const m = PCVV.cut(model().merge(sheave(), ...toAxis, 'sv_')); PCART.realize(root, m, 'headframe_steel_preview'); return m.els.length; },
    buildSheave: (root) => { const m = sheave(); PCART.realize(root, m, 'sheave'); return m.els.length; },
    saveSheave: (root) => PCVV.savePartial(root, 'headframe_steel', 'sheave', 'mining'),
  };
})();
