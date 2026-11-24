// Vertical steel stock tank at real scale (1 block = 1 m, 16 px = 1 m), the RVS-1000 (GOST 31385, 10.43 m inside
// diameter, 11.92 m shell in eight courses): the shell on its concrete ring wall, the self-supporting cone roof with
// the edge handrail, the spiral stair up the outside to a landing and the roof walkway, the receive and dispense
// nozzles with their gate valves, the siphon water drain, the breather valve with its flame arrester, the gauge
// hatch, the shell manhole and the grounding lugs. Floating-roof variant (oil_tank_floating_real): open top with the
// wind girder walkway and its handrail, the pontoon deck and the rolling ladder as partials.
// Sheet: docs/research/2026-11-24-i2-batch2/oil_tank_real.md.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   TNK.build(repo, floating); /* wait */ TNK.save(repo, floating)
//   for (const p of TNK.partNames()) { TNK.buildPartial(repo, p); TNK.savePartial(repo, p) }   (floating variant only)
//   TNK.preview(repo, floating)
//
// Controller block = (0, 0, 0) at the tank centre on the ground, front north. Footprint x -6..6, y 0..13, z -6..6
// (13 x 14 x 13, hollow; the corner cells hold only the stair). Tank axis vertical through (8, y, 8); shell inside
// radius 83.4 (a 16-sided wall of tangent panels), y 5..196; roof rises to y 200; handrail to y 218.
// Connections (px): receive in on the west face of (-6, 0, 0) at (-96, 8, 8); dispense out on the east face of
// (6, 0, 0) at (112, 8, 8); bottom water drain on the south face of (0, 0, 6) at (8, 3, 112); vapour (breather) on
// the top face of (2, 12, 0) at (40, 208, 8); gauge mount (tank_gauge) on the top face of (-2, 12, 0) at (-24, 208, 8).
// Climbing: stair treads, landing and walkway are named stair_* / ladder_* so the slicer leaves them out of the
// collision cells and the player climbs them like ladders.
// Partials (oil_tank_floating_real/partial/*, floating variant only):
// - floating_roof_<i>_<k>: the pontoon deck in 48 px tiles, tile (i, k) covering x 48 (i - 2) .. + 48 and the same
//   in z; all slide along y together (deck top at y 8 as stored, up to y 186 full); tile (i, k) is stored shifted
//   (-48 (i - 2) - 16, 0, -48 (k - 2) - 16) and drawn at minus that.
// - roof_ladder_<n>: the rolling ladder from the hinge at (8, 196, -68) to the deck, in four 2.5 m pieces drawn
//   hanging straight down from the hinge (piece n spans y 156 - 40 n .. 196 - 40 n); they turn together about x
//   through the hinge, the angle from the deck height; piece n is stored shifted (0, -164 + 40 n, +68) and drawn at
//   (0, 164 - 40 n, -68).
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 }, shell: { tex: 'vver/clad', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'vver/clad' });
  const C = [8, 8], RI = 83.4, Y0 = 5, Y1 = 196, TAN = Math.tan(Math.PI / 16);

  // A wall panel tangent to the 16-gon of apothem `apo` at polar angle th (deg, counter-clockwise from east seen from
  // above, 90 = north), `len` along the tangent, `t` thick outward, y0..y1, turned about the tank axis. Java allows
  // only +-22.5 and +-45, so the panel is laid on the east, west, north or south side and turned from there.
  function panel(m, name, apo, th, len, t, y0, y1, mat, o = {}) {
    let a = ((th % 360) + 360) % 360, f, to, ang;
    if (a <= 45 || a >= 315) { f = [C[0] + apo, y0, C[1] - len / 2]; to = [C[0] + apo + t, y1, C[1] + len / 2]; ang = a > 180 ? a - 360 : a; }
    else if (a >= 135 && a <= 225) { f = [C[0] - apo - t, y0, C[1] - len / 2]; to = [C[0] - apo, y1, C[1] + len / 2]; ang = a - 180; }
    else if (a < 180) { f = [C[0] - len / 2, y0, C[1] - apo - t]; to = [C[0] + len / 2, y1, C[1] - apo]; ang = a - 90; }
    else { f = [C[0] - len / 2, y0, C[1] + apo]; to = [C[0] + len / 2, y1, C[1] + apo + t]; ang = a - 270; }
    m.box(name, f, to, mat, Object.assign({ split: '' }, o, { rot: ang ? { axis: 'y', angle: -ang, origin: [C[0], 8, C[1]] } : undefined }));
  }
  // A small box centred at polar (th, r) from the axis, long `L` radially and `w` tangentially, turned about its own
  // centre to the nearest allowed angle (treads, posts, brackets).
  function radial(m, name, th, r, L, w, y0, y1, mat) {
    const t = th * Math.PI / 180, px = C[0] + r * Math.cos(t), pz = C[1] - r * Math.sin(t);
    const q = ((Math.round(th / 22.5) * 22.5) % 360 + 360) % 360;
    let f, to, ang;
    if (q <= 45 || q >= 315 || (q >= 135 && q <= 225)) {
      f = [px - L / 2, y0, pz - w / 2]; to = [px + L / 2, y1, pz + w / 2]; ang = q <= 45 ? q : q >= 315 ? q - 360 : q - 180;
    } else {
      f = [px - w / 2, y0, pz - L / 2]; to = [px + w / 2, y1, pz + L / 2]; ang = q < 180 ? q - 90 : q - 270;
    }
    m.box(name, f, to, mat, { split: '', rot: ang ? { axis: 'y', angle: -ang, origin: [px, 8, pz] } : undefined });
  }
  const FACETS = [...Array(16).keys()].map((i) => i * 22.5);
  const LEN = (apo) => 2 * apo * TAN + 0.3;

  // Horizontal disc of axis-aligned strips (no rotation, so the collision cells follow it) of radius r at y0..y1.
  function disc(m, name, r, y0, y1, mat, step) {
    step = step || 8;
    for (let z = -r; z < r - 1e-6; z += step) {
      const z1 = Math.min(z + step, r), zz = Math.max(Math.abs(z), Math.abs(z1));
      const hx = Math.sqrt(Math.max(r * r - zz * zz, 0));
      if (hx < 1) continue;
      m.box(`${name}_${Math.round(z)}`, [C[0] - hx, y0, C[1] + z], [C[0] + hx, y1, C[1] + z1], mat, { split: '' });
    }
  }

  function foundation(m) {
    for (const th of FACETS) panel(m, `ringwall_${th}`, RI - 2, th, LEN(RI + 4), 6, 0, 5, 'conc');
    disc(m, 'bottom', RI, 4.4, 5, 'dark', 16);
    // grounding lugs at four points
    for (const th of [45, 135, 225, 315]) radial(m, `ground_lug_${th}`, th, RI + 2.4, 3, 1.4, 5, 7, 'st');
  }

  function shell(m, floating) {
    const top = floating ? 190 : Y1;
    for (let c = 0; c < 8; c++) {
      const y0 = Y0 + c * 23.8 - (c ? 0.1 : 0), y1 = Math.min(Y0 + (c + 1) * 23.8, top);
      if (y0 >= top) break;
      for (const th of FACETS) panel(m, `shell_${c}_${th}`, RI, th, LEN(RI), 1.2, y0, y1, 'shell');
    }
    // course weld lines as slight outset bands
    for (let c = 1; c < 8; c++) {
      const y = Y0 + c * 23.8;
      if (y > top) break;
      for (const th of FACETS) panel(m, `weld_${c}_${th}`, RI + 1.2, th, LEN(RI + 1.2), 0.15, y - 0.3, y + 0.3, 'pnt');
    }
    // top angle (stiffening ring) round the roof edge
    for (const th of FACETS) panel(m, `top_angle_${th}`, RI + 1.2, th, LEN(RI + 1.2), 1.4, top - 2, top, 'pnt');
    // shell manhole and light nozzles in the first course, at the south-east
    radial(m, 'manhole_neck', -45, RI + 2.6, 5, 10, 8, 18, 'shell');
    radial(m, 'manhole_cover', -45, RI + 5.4, 0.8, 12, 7, 19, 'ci');
    m.decal('plate', [2, 20, C[1] - RI - 1.6], [8, 23.6, C[1] - RI - 1.3], 'pnt', 'north', 'pl.maker');
  }

  function roof(m) {
    // self-supporting cone 1:20 in three terraces, the edge under the top angle
    disc(m, 'roof_a', RI + 0.6, 196, 197.4, 'shell');
    disc(m, 'roof_b', 58, 197.4, 198.8, 'shell');
    disc(m, 'roof_c', 30, 198.8, 200, 'shell');
    // edge handrail: posts every facet, top and mid rails of tangent panels, toe board
    for (const th of FACETS) {
      panel(m, `rail_top_${th}`, RI + 0.4, th, LEN(RI + 0.4), 0.6, 216.8, 217.6, 'yel');
      panel(m, `rail_mid_${th}`, RI + 0.4, th, LEN(RI + 0.4), 0.6, 207, 207.6, 'yel');
      panel(m, `toe_${th}`, RI + 0.4, th, LEN(RI + 0.4), 0.6, 196, 199.4, 'yel');
      radial(m, `rail_post_${th}`, th + 11.25, RI + 0.8, 0.8, 0.8, 196, 217.6, 'yel');
    }
    // walkway from the north landing to the hatches at the centre, grating on the roof
    m.box('ladder_walk', [2, 200, C[1] - RI], [14, 200.6, C[1] - 4], 'grate', { split: '' });
    // gauge hatch (tank_gauge seat) and the breather valve with its flame arrester
    m.cylY('gauge_hatch', [-24, 8], 4, 200, 205, 'pnt');
    m.cylY('gauge_hatch_cover', [-24, 8], 4.6, 205, 206, 'ci', { cap: 'vver/flange' });
    m.cylY('arrester', [40, 8], 3, 200, 203.4, 'pnt');
    m.box('breather', [36, 203.4, 4], [44, 207, 12], 'gs', { split: '' });
    m.cylY('breather_hood', [40, 8], 3.4, 207, 208, 'gs');
    // light hatch and a foam chamber stub on the roof edge
    m.box('light_hatch', [56, 198.8, -24], [68, 201, -12], 'pnt', { split: '' });
  }

  function floatingTop(m) {
    // wind girder: an outside walkway ring at y 186 with its handrail; the gauge seat on the girder
    for (const th of FACETS) {
      panel(m, `ladder_girder_${th}`, RI + 1.2, th, LEN(RI + 12), 10, 185, 186.4, 'grate');
      panel(m, `girder_rail_top_${th}`, RI + 10.6, th, LEN(RI + 10.6), 0.6, 202.6, 203.4, 'yel');
      panel(m, `girder_rail_mid_${th}`, RI + 10.6, th, LEN(RI + 10.6), 0.6, 194, 194.6, 'yel');
      radial(m, `girder_post_${th}`, th + 11.25, RI + 11, 0.8, 0.8, 186.4, 203.4, 'yel');
    }
    m.box('ladder_landing', [0, 186, C[1] - RI - 10], [16, 187.4, C[1] - RI], 'grate', { split: '' });
    m.cylY('gauge_still_well', [-24, C[1] - RI - 6], 2.4, 186.4, 200, 'st');
    m.box('hinge_bracket', [4, 190, C[1] - RI - 0.6], [12, 196, C[1] - RI + 6], 'pnt', { split: '' });
  }

  function nozzles(m) {
    // receive (west) and dispense (east), DN 200 at y 8 through a reinforcing pad, gate valve and flange
    for (const [s, n, ax] of [[-1, 'receive', 'west'], [1, 'dispense', 'east']]) {
      const x0 = C[0] + s * (RI + 1.2), x1 = C[0] + s * 103.9;
      m.box(`${n}_pad`, s < 0 ? [x0 - 0.6, 3.6, 0] : [x0, 3.6, 0], s < 0 ? [x0, 13, 16] : [x0 + 0.6, 13, 16], 'shell', { split: '' });
      m.prism(`${n}_pipe`, 'x', [8, 8], 3.4, Math.min(x0, x1), Math.max(x0, x1), 'oil', { split: 'x' });
      const v = C[0] + s * 96;
      m.box(`${n}_valve`, [v - 3, 3, 3], [v + 3, 13, 13], 'ci', { split: '' });
      m.box(`${n}_stem`, [v - 0.3, 13, 7.7], [v + 0.3, 18, 8.3], 'st', { split: '' });
      m.wheelY(`${n}_wheel`, v, 18.4, 8, 3, 'red');
      m.disc(`${n}_flange`, 'x', [8, 8], 4.4, s < 0 ? -95.9 : 111.4, s < 0 ? -95.4 : 111.9, 'ci', 'vver/flange', ax);
    }
    // siphon water drain to the south face, green, with its valve
    m.box('drain_out', [6.4, 6.4, C[1] + RI + 1.2], [9.6, 9.6, 100], 'gw', { split: 'z' });
    m.box('drain_down', [6.4, 1.4, 97], [9.6, 9.6, 100], 'gw', { split: '' });
    m.box('drain_run', [6.4, 1.4, 100], [9.6, 4.6, 111.6], 'gw', { split: 'z' });
    m.box('drain_valve', [5, 0.6, 104], [11, 6, 108], 'red', { split: '' });
  }

  // Spiral stair: 61 treads of 3.2 px rise on mean radius 89.5, climbing clockwise seen from above from the south-
  // west (225 deg) to the north landing (about 100 deg), an outer post on every third tread with a short rail.
  function stair(m, floating) {
    const topY = floating ? 186 : 196, n = Math.round((topY - 5) / 3.2), dth = (3.2 / 89.5) * 180 / Math.PI;
    for (let i = 0; i < n; i++) {
      const th = 225 - i * dth, y = 5 + (i + 1) * 3.2;
      radial(m, `stair_tread_${i}`, th, 89.4, 11.4, 4.4, y - 0.6, y, 'grate');
      if (i % 3 === 0) {
        radial(m, `stair_post_${i}`, th, 95.2, 0.8, 0.8, y, y + 17, 'yel');
        radial(m, `stair_rail_${i}`, th - dth, 95.2, 0.7, 10.4, y + 16.4, y + 17, 'yel');
        radial(m, `stair_bracket_${i}`, th, 88.6, 9.6, 1, y - 3, y - 0.6, 'frame');
      }
    }
    // landing at the top on the north, outside the shell
    m.box('stair_landing', [-6, topY - 0.6, C[1] - RI - 12], [18, topY, C[1] - RI - 0.5], 'grate', { split: '' });
    for (const x of [-6, 17.2]) m.box(`stair_landing_post_${x}`, [x, topY, C[1] - RI - 12], [x + 0.8, topY + 17, C[1] - RI - 11.2], 'yel', { split: '' });
    m.box('stair_landing_rail', [-6, topY + 16.4, C[1] - RI - 12], [18, topY + 17, C[1] - RI - 11.4], 'yel', { split: '' });
  }

  // ------------------------------------------------------------------ floating variant partials
  function deck(m) {
    disc(m, 'deck', RI - 2.6, 6, 8, 'shell', 8);
    // pontoon ring and the rim seal (16 tangent panels)
    for (const th of FACETS) {
      panel(m, `pontoon_${th}`, RI - 14, th, LEN(RI - 14), 11.4, 6, 11, 'pnt');
      panel(m, `seal_${th}`, RI - 2.6, th, LEN(RI - 2.6), 2.4, 7, 10, 'dark');
    }
    m.box('ladder_runway', [2, 8, C[1] - RI + 12], [14, 9, C[1] + 20], 'grate', { split: '' });
    m.cylY('deck_drain', [8, 8], 3, 8, 9, 'ci', { cap: 'vver/flange' });
  }
  const TILE = 48, TI = [-2, -1, 0, 1];
  // The deck cut on the 48 px chunk grid: turned panels are split there by PCVV.cut, straight strips are clipped to
  // the tile, so every tile fits one Java model once shifted to -16..32.
  function tiles() {
    const src = PCVV.cut((() => { const d = mk(); deck(d); return d; })());
    const out = {};
    for (const i of TI) for (const k of TI) {
      const lo = [i * TILE, k * TILE], hi = [lo[0] + TILE, lo[1] + TILE], m = mk();
      for (const e of src.els) {
        if (e.rot && e.rot.angle) {
          const cx = (e.from[0] + e.to[0]) / 2, cz = (e.from[2] + e.to[2]) / 2;
          if (cx >= lo[0] && cx < hi[0] && cz >= lo[1] && cz < hi[1]) m.els.push(Object.assign({}, e));
          continue;
        }
        const f = [Math.max(e.from[0], lo[0]), e.from[1], Math.max(e.from[2], lo[1])];
        const t = [Math.min(e.to[0], hi[0]), e.to[1], Math.min(e.to[2], hi[1])];
        if (t[0] - f[0] < 1e-3 || t[2] - f[2] < 1e-3) continue;
        const hide = e.hide.slice();
        if (f[0] > e.from[0]) hide.push('west');
        if (t[0] < e.to[0]) hide.push('east');
        if (f[2] > e.from[2]) hide.push('north');
        if (t[2] < e.to[2]) hide.push('south');
        m.els.push(Object.assign({}, e, { from: f, to: t, hide }));
      }
      if (m.els.length) out[`floating_roof_${i + 2}_${k + 2}`] = { m, shift: [-lo[0] - 16, 0, -lo[1] - 16] };
    }
    return out;
  }
  function ladderPiece(n) {
    // ladder hanging straight down from the hinge (8, 196, -76): two stiles and rungs, piece n spans 40 px
    const m = mk(), yTop = 196 - 40 * n, yBot = yTop - 40, z = C[1] - RI + 7.4;
    for (const x of [3, 13]) m.box(`ladder_stile_${n}_${x}`, [x, yBot, z - 0.6], [x + 1, yTop, z + 0.6], 'st', { split: '' });
    for (let y = yBot + 3; y < yTop; y += 5) m.box(`ladder_rung_${n}_${y}`, [4, y, z - 0.4], [13, y + 0.6, z + 0.4], 'st', { split: '' });
    if (n === 3) for (const x of [2, 12]) m.prism(`ladder_wheel_${x}`, 'x', [yBot + 1.6, z], 1.6, x, x + 2, 'dark', { split: '', ends: ['west', 'east'] });
    return { m, shift: [0, -yBot - 8, -z] };
  }
  function partials() {
    const out = tiles();
    for (let n = 0; n < 4; n++) out[`roof_ladder_${n}`] = ladderPiece(n);
    return out;
  }

  function model(floating) {
    const m = mk(); foundation(m); shell(m, !!floating); if (floating) floatingTop(m); else roof(m); nozzles(m); stair(m, !!floating);
    return m;
  }
  const id = (floating) => floating ? 'oil_tank_floating_real' : 'oil_tank_real';
  const shifted = (p) => { const [dx, dy, dz] = p.shift; return mk().merge(p.m, dx, dy, dz, ''); };
  window.TNK = {
    model, partials, id,
    partNames: () => Object.keys(partials()),
    build: (root, floating) => { const m = PCVV.cut(model(floating)); PCART.realize(root, m, id(floating)); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, floating) => PCVV.save(root, id(floating), 'storage'),
    buildPartial: (root, name) => { const m = shifted(partials()[name]); PCART.realize(root, m, 'oil_tank_floating_real_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, 'oil_tank_floating_real', name, 'storage'),
    preview: (root, floating) => {
      const m = model(floating);
      if (floating) { const d = mk(); deck(d); m.merge(d, 0, 90, 0, 'deck_'); }
      PCVV.cut(m); PCART.realize(root, m, id(floating) + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) };
    },
  };
})();
