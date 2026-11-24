// Section of a four-leg drilling derrick at real scale (1 block = 1 m, 16 px = 1 m): a 3 x 3 x 3 m lattice
// section of the 1950s-60s Soviet bolted derrick (VM-41 class, here built as a straight mast so sections stack):
// four angle-steel legs, girts every 1.5 m, X-bracing on every face, a caged ladder up the east face. Sections stack
// over the rotary table; the well axis runs up the middle column.
// Variants: derrick_segment_real (any middle section), _bottom (leg shoes, the V-door opening on the north face for
// pipe from the catwalk), _top (water table with the crown beams, grating deck and handrail, aviation red band).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   DRK.build(repo, v); /* wait */ DRK.save(repo, v)      v = '' | 'bottom' | 'top'
//   DRK.preview(repo)                                    the three sections stacked bottom, middle, top
//
// Controller block = (0, 0, 0) in the middle of the section's lowest layer, front north. Footprint x -1..1,
// y 0..2, z -1..1 (FP_CUBE3). Well axis vertical through (8, y, 8). No ports, no partials: the traveling block,
// hook, drill line and kelly are drawn by the rotary table and drawworks renderers through the hollow middle.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, grid: { tex: 'vver/grating', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'plant/structure' });
  const LO = -16, HI = 32, W = 3, C = [LO + W / 2, HI - W / 2];    // leg centres at -14.5 and 30.5
  const SPAN = C[1] - C[0];                                         // 45 px between leg centres

  function legs(m, y0, y1, mat) {
    for (const x of C) for (const z of C) {
      // angle steel: two flanges, the open side facing the middle
      const sx = x < 8 ? 1 : -1, sz = z < 8 ? 1 : -1;
      m.box(`leg_a_${x}_${z}_${y0}`, [x - W / 2, y0, z - W / 2], [x + W / 2, y1, z + W / 2], mat, { split: 'y', hide: [] });
      m.box(`leg_lip_${x}_${z}_${y0}`, [Math.min(x, x + sx * 3.5), y0, z - 0.5], [Math.max(x, x + sx * 3.5), y1, z + 0.5], mat, { split: 'y' });
      m.box(`leg_lip2_${x}_${z}_${y0}`, [x - 0.5, y0, Math.min(z, z + sz * 3.5)], [x + 0.5, y1, Math.max(z, z + sz * 3.5)], mat, { split: 'y' });
    }
  }
  // Horizontal girt ring at height y on all four faces.
  function girt(m, y, mat, skipNorth) {
    const h = 1.4, t = 1;
    if (!skipNorth) m.box(`girt_n_${y}`, [C[0], y, LO + 1], [C[1], y + h, LO + 1 + t], mat, { split: 'x' });
    m.box(`girt_s_${y}`, [C[0], y, HI - 1 - t], [C[1], y + h, HI - 1], mat, { split: 'x' });
    m.box(`girt_w_${y}`, [LO + 1, y, C[0]], [LO + 1 + t, y + h, C[1]], mat, { split: 'z' });
    m.box(`girt_e_${y}`, [HI - 1 - t, y, C[0]], [HI - 1, y + h, C[1]], mat, { split: 'z' });
  }
  // X-bracing in a bay y0..y0+24 on one face: two panels of 22.5 x 24 px, each crossed by two 45 deg flats.
  // Each flat is three short pieces so that no unrotated box leaves the -16..32 chunk (the slicer bins rotated
  // boxes by their unrotated extent; one long flat would push the section into four chunks).
  function bay(m, face, y0, mat) {
    const L = Math.hypot(SPAN / 2, 24) * 0.9, n = 3, l = L / n + 0.3, h = 0.8, yc = y0 + 12, d = L / n / Math.SQRT2;
    for (let k = 0; k < 2; k++) {
      const u = C[0] + SPAN / 4 + k * SPAN / 2;
      for (const a of [45, -45]) for (let j = -1; j <= 1; j++) {
        const nm = `brace_${face}_${y0}_${k}_${a}_${j + 1}`, cu = u + j * d, cy = yc + j * d * Math.sign(a);
        if (face === 'n' || face === 's') {
          const z0 = face === 'n' ? LO + 0.6 : HI - 1.2;
          m.box(nm, [cu - l / 2, cy - h / 2, z0], [cu + l / 2, cy + h / 2, z0 + 0.6], mat, { rot: { axis: 'z', angle: a, origin: [cu, cy, z0] } });
        } else {
          // about x a positive angle lifts +z ends; mirror the sign so the pattern matches the n/s faces
          const x0 = face === 'w' ? LO + 0.6 : HI - 1.2, cz = u + j * d, cyz = yc - j * d * Math.sign(a);
          m.box(nm, [x0, cyz - h / 2, cz - l / 2], [x0 + 0.6, cyz + h / 2, cz + l / 2], mat, { rot: { axis: 'x', angle: a, origin: [x0, cyz, cz] } });
        }
      }
      // gusset where the flats cross
      if (face === 'n' || face === 's') {
        const z0 = face === 'n' ? LO + 0.4 : HI - 1.4;
        m.box(`gusset_${face}_${y0}_${k}`, [u - 1.2, yc - 1.2, z0], [u + 1.2, yc + 1.2, z0 + 1], 'ci', { split: '' });
      } else {
        const x0 = face === 'w' ? LO + 0.4 : HI - 1.4;
        m.box(`gusset_${face}_${y0}_${k}`, [x0, yc - 1.2, u - 1.2], [x0 + 1, yc + 1.2, u + 1.2], 'ci', { split: '' });
      }
    }
  }
  // Caged ladder inside the east face between z 17 and 23.
  function ladder(m, y0, y1) {
    const x0 = HI - 3.2, z0 = 17, z1 = 23;
    m.box(`ladder_rail_a_${y0}`, [x0, y0, z0], [x0 + 0.6, y1, z0 + 0.6], 'st', { split: 'y' });
    m.box(`ladder_rail_b_${y0}`, [x0, y0, z1 - 0.6], [x0 + 0.6, y1, z1], 'st', { split: 'y' });
    for (let y = y0 + 2; y < y1; y += 5) m.box(`rung_${y}`, [x0, y, z0], [x0 + 0.5, y + 0.5, z1], 'st', { split: '' });
    // cage hoops every 1.5 m (inside the section, opening toward the face)
    for (let y = y0 + 6; y < y1; y += 24) {
      m.box(`hoop_a_${y}`, [x0 - 11, y, z0 - 1.2], [x0, y + 0.6, z0 - 0.6], 'st', { split: '' });
      m.box(`hoop_b_${y}`, [x0 - 11, y, z1 + 0.6], [x0, y + 0.6, z1 + 1.2], 'st', { split: '' });
      m.box(`hoop_c_${y}`, [x0 - 11.6, y, z0 - 1.2], [x0 - 11, y + 0.6, z1 + 1.2], 'st', { split: '' });
    }
    for (const z of [z0 - 0.9, z1 + 0.9]) m.box(`cage_bar_${z}_${y0}`, [x0 - 11.4, y0 + 6, z - 0.3], [x0 - 10.8, y1, z + 0.3], 'st', { split: 'y' });
  }

  function middle(m) {
    legs(m, 0, 48, 'frame');
    for (const y of [0, 24]) girt(m, y, 'frame');
    for (const f of ['n', 's', 'w', 'e']) for (const y of [0, 24]) bay(m, f, y, 'frame');
    ladder(m, 0, 48);
  }

  function bottom(m) {
    legs(m, 2, 48, 'frame');
    for (const x of C) for (const z of C) {
      m.box(`shoe_${x}_${z}`, [x - 4, 0, z - 4].map((v, i) => Math.max(v, [LO, 0, LO][i])), [x + 4, 2, z + 4].map((v, i) => Math.min(v, [HI, 2, HI][i])), 'ci', { split: '' });
      for (const [dx, dz] of [[-2.6, -2.6], [2.6, -2.6], [-2.6, 2.6], [2.6, 2.6]]) {
        const bx = x + dx, bz = z + dz;
        if (bx < LO + 0.5 || bx > HI - 0.5 || bz < LO + 0.5 || bz > HI - 0.5) continue;
        m.box(`bolt_${x}_${z}_${dx}_${dz}`, [bx - 0.5, 2, bz - 0.5], [bx + 0.5, 2.8, bz + 0.5], 'st', { split: '' });
      }
    }
    girt(m, 2, 'frame', true);
    girt(m, 24, 'frame');
    // V-door: no bracing in the lower north bay, a header girt and two knee braces carry the opening
    m.box('vdoor_header', [C[0], 22, LO + 1], [C[1], 24, LO + 2.4], 'frame', { split: 'x' });
    for (const [x, a] of [[C[0] + 6, -45], [C[1] - 6, 45]]) {
      m.box(`vdoor_knee_${x}`, [x - 6, 18.6, LO + 1], [x + 6, 19.4, LO + 1.6], 'frame', { rot: { axis: 'z', angle: a, origin: [x, 19, LO + 1] } });
    }
    m.decal('vdoor_plate', [3, 26, LO + 0.4], [9, 29.6, LO + 1], 'pnt', 'north', 'pl.maker');
    for (const f of ['s', 'w', 'e']) bay(m, f, 0, 'frame');
    for (const f of ['n', 's', 'w', 'e']) bay(m, f, 24, 'frame');
    ladder(m, 2, 48);
  }

  function top(m) {
    legs(m, 0, 30, 'frame');
    // aviation obstruction band on the top 1.5 m of the legs
    for (const x of C) for (const z of C) m.box(`band_${x}_${z}`, [x - W / 2 - 0.05, 22, z - W / 2 - 0.05], [x + W / 2 + 0.05, 30, z + W / 2 + 0.05], 'gs', { split: '' });
    girt(m, 0, 'frame');
    for (const f of ['n', 's', 'w', 'e']) bay(m, f, 0, 'frame');
    ladder(m, 0, 32);
    // water table: perimeter beams, two crown beams across along z either side of the axis, grating deck
    m.box('wt_beam_n', [LO, 30, LO], [HI, 32, LO + 3], 'frame', { split: 'x' });
    m.box('wt_beam_s', [LO, 30, HI - 3], [HI, 32, HI], 'frame', { split: 'x' });
    m.box('wt_beam_w', [LO, 30, LO + 3], [LO + 3, 32, HI - 3], 'frame', { split: 'z' });
    m.box('wt_beam_e', [HI - 3, 30, LO + 3], [HI, 32, HI - 3], 'frame', { split: 'z' });
    for (const x of [1, 15]) m.box(`crown_beam_${x}`, [x - 1, 30, LO + 3], [x + 1, 33, HI - 3], 'pnt', { split: 'z' });
    m.box('deck_w', [LO + 3, 31.4, LO + 3], [0, 31.8, HI - 3], 'grid', { split: 'xz' });
    m.box('deck_e', [16, 31.4, LO + 3], [HI - 3, 31.8, HI - 3], 'grid', { split: 'xz' });
    // handrail 1 m, mid rail, toe board; gap at the ladder head
    for (const [n, f, t] of [['n', [LO, 32, LO], [HI, 48, LO + 0.6]], ['s', [LO, 32, HI - 0.6], [HI, 48, HI]], ['w', [LO, 32, LO], [LO + 0.6, 48, HI]]]) {
      m.box(`rail_top_${n}`, [f[0], 46.8, f[2]], [t[0], 47.6, t[2]], 'yel', { split: n === 'w' ? 'z' : 'x' });
      m.box(`rail_mid_${n}`, [f[0], 39.6, f[2]], [t[0], 40.2, t[2]], 'yel', { split: n === 'w' ? 'z' : 'x' });
      m.box(`toe_${n}`, [f[0], 32, f[2]], [t[0], 33.6, t[2]], 'yel', { split: n === 'w' ? 'z' : 'x' });
    }
    m.box('rail_top_e_a', [HI - 0.6, 46.8, LO], [HI, 47.6, 15], 'yel', { split: 'z' });
    m.box('rail_mid_e_a', [HI - 0.6, 39.6, LO], [HI, 40.2, 15], 'yel', { split: 'z' });
    m.box('toe_e_a', [HI - 0.6, 32, LO], [HI, 33.6, 15], 'yel', { split: 'z' });
    m.box('rail_top_e_b', [HI - 0.6, 46.8, 25], [HI, 47.6, HI], 'yel', { split: '' });
    for (const x of [LO + 0.3, 0, 16, HI - 0.3]) for (const z of [LO + 0.3, HI - 0.3]) {
      m.box(`post_${x}_${z}`, [x - 0.3, 32, z - 0.3], [x + 0.3, 47.6, z + 0.3], 'yel', { split: '' });
    }
    for (const z of [LO + 0.3, 8, 15, 25, HI - 0.3]) m.box(`post_e_${z}`, [HI - 0.6, 32, z - 0.3], [HI, 47.6, z + 0.3], 'yel', { split: '' });
    for (const z of [8]) m.box(`post_w_${z}`, [LO, 32, z - 0.3], [LO + 0.6, 47.6, z + 0.3], 'yel', { split: '' });
    // obstruction light on the north-west corner post
    m.box('glow_obstruction', [LO + 0.2, 47.6, LO + 0.2], [LO + 2, 49.4 - 1.4, LO + 2], 'gs', { split: '' });
  }

  const DRAW = { '': middle, bottom, top };
  const id = (v) => 'derrick_segment_real' + (v ? '_' + v : '');
  function model(v) { const m = mk(); DRAW[v || ''](m); return m; }
  window.DRK = {
    id, model,
    build: (root, v) => { const m = PCVV.cut(model(v)); PCART.realize(root, m, id(v)); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, id(v), 'drilling'),
    preview: (root) => {
      const m = mk();
      m.merge(model('bottom'), 0, 0, 0, 'b_'); m.merge(model(''), 0, 48, 0, 'm_'); m.merge(model('top'), 0, 96, 0, 't_');
      PCVV.cut(m); PCART.realize(root, m, 'derrick_segment_real_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) };
    },
  };
})();
