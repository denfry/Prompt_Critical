// CANDU NPD (Nuclear Power Demonstration, Rolphton 1962, 92 MWt) at real scale (1 block = 1 m, 16 px = 1 m): the
// horizontal aluminium calandria (5.2 m x 4.6 m) with its two end shields, 132 end fittings with closure plugs on each
// fuelling face, a feeder for every fitting up to the feeder banks and the vertical inlet and outlet headers, the two
// saddles and the moderator lines; and the fuelling machine that serves one face (two are used, one at each end).
// Sheet: docs/research/2026-11-24-reactor-prototypes/candu_npd.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   NPD.build(repo, k); /* wait */ NPD.save(repo, k)            k = 'reactor' | 'fuelling_machine'
//   for (const p of NPD.parts(k)) { NPD.buildPartial(repo, k, p); NPD.savePartial(repo, k, p) }
//
// Reactor (candu_npd_reactor_real): controller (0, 0, 0) under the axis at the calandria middle, front north.
// Footprint x -3..3, y 0..6, z -3..3. Calandria axis along z through (x 8, y 56); fuelling faces north (z -48) and
// south (z 64). Ports: HT inlet header north end on the west face of (-3, 3, -3) at (-48, 56, -40); HT outlet header
// north end on the east face of (3, 3, -3) at (64, 56, -40); HT inlet header south end on the east face of (3, 3, 3)
// at (64, 56, 56); HT outlet header south end on the west face of (-3, 3, 3) at (-48, 56, 56); moderator in on the
// bottom face of (0, 0, 0) at (8, 0, 8); moderator out on the top face of (0, 6, 0) at (8, 112, 8).
// Partial closure_plug: the closure plug of the centre-most north channel; slides out -z 0..8 px when the machine
// takes it; stored (0, -40, +40), draw at (0, +40, -40).
// Fuelling machine (candu_npd_fuelling_machine_real): controller (0, 0, 0) at the bottom middle, front north;
// footprint x -3..3, y 0..6, z 0..1, set with its south side against a fuelling face (mirrored for the other face).
// Ports: D2O to the head on the west face of (-3, 0, 1) at (-48, 8, 24); power and control on the east face of
// (3, 0, 1) at (64, 8, 24); fuel in / spent out on the north face of (0, 0, 0) at (8, 8, 0).
// Partials: bridge_w and bridge_e (the bridge beam in two halves) slide together along y, stored at y 40 (beam
// y 40..48), range 0..56 px up; stored shifts bridge_w (+24, -24, 0), bridge_e (-24, -24, 0). carriage rides the
// bridge along x -40..40 px about x 8, stored on the beam at y 48, shift (0, -48, 0). head: magazine drum turning about
// z through (8, 66) by 1/8 turn per bundle position, shift (0, -48, 0). snout: slides +z 0..12 px onto the end fitting,
// shift (0, -48, 0). Carriage, head and snout move with the bridge.
(function () {
  const AX = [8, 56], RC = 41.6, Z0 = -29, Z1 = 45, P = 0.26035 * 16;

  // 132 channel positions on the square pitch, the nearest to the axis.
  function channels() {
    const pts = [];
    for (let i = -8; i < 8; i++) for (let j = -8; j < 8; j++) {
      const x = AX[0] + (i + 0.5) * P, y = AX[1] + (j + 0.5) * P;
      pts.push([x, y, Math.hypot(x - AX[0], y - AX[1])]);
    }
    pts.sort((a, b) => a[2] - b[2]);
    return pts.slice(0, 132).map(([x, y]) => [x, y]);
  }

  function calandria(m) {
    m.prism('calandria', 'z', AX, RC, Z0, Z1, 'clad', { split: 'z' });
    for (const z of [Z0 + 12, Z1 - 12]) m.ring(`band_${z}`, 'z', AX, RC + 0.4, 0.8, z, z + 1.2, 'clad');
    // end shields (tube sheet and shield box) at both ends
    m.prism('shield_n', 'z', AX, RC - 2, -37, Z0, 'forged', { split: '', ends: ['north'] });
    m.prism('shield_s', 'z', AX, RC - 2, Z1, 53, 'forged', { split: '', ends: ['south'] });
    // saddles
    for (const z of [-18, 34]) {
      m.box(`saddle_${z}`, [-26, 0, z - 3], [42, 18, z + 3], 'pnt', { split: '' });
      m.box(`saddle_base_${z}`, [-30, 0, z - 5], [46, 2, z + 5], 'pnt', { split: '' });
    }
    // moderator inlet below, outlet and cover gas line on top
    m.cylY('mod_in', [8, 8], 2, 0, 15, 'gw');
    m.cylY('mod_out', [8, 8], 2, 97, 111.9, 'gw');
    m.box('cover_gas', [10, 100, 7.4], [30, 101.2, 8.6], 'yel', { split: 'x' });
    m.decal('plate', [-2, 22, -29.6], [4, 25.6, -29], 'clad', 'north', 'pl.maker');
  }

  // One fuelling face: end fittings with closure plugs, a feeder from each fitting up to the feeder bank, the two
  // vertical headers. s = -1 north face, +1 south face.
  function face(m, s) {
    const zIn = s < 0 ? -37 : 53, zOut = s < 0 ? -45 : 61, n = s < 0 ? 'n' : 's';
    const za = Math.min(zIn, zOut), zb = Math.max(zIn, zOut), zf = s < 0 ? -40.5 : 56.5;
    const plug = s < 0 ? [zOut - 1, zOut] : [zOut, zOut + 1];
    channels().forEach(([x, y], i) => {
      m.box(`ef_${n}_${i}`, [x - 1.3, y - 1.3, za], [x + 1.3, y + 1.3, zb], 'forged', { split: '' });
      m.box(`plug_${n}_${i}`, [x - 1, y - 1, plug[0]], [x + 1, y + 1, plug[1]], 'st', { split: '' });
      // feeder from the fitting's side port up to the bank
      m.box(`feeder_${n}_${i}`, [x + 1.3, y, zf - 0.4], [x + 2.1, 102, zf + 0.4], i % 2 ? 'gs' : 'gw', { split: '' });
    });
    // feeder bank across the top, the two vertical headers at the sides (inlet green, outlet red)
    m.box(`bank_${n}`, [-40, 102, zf - 2], [56, 106, zf + 2], 'pnt', { split: 'x' });
    const inX = s < 0 ? -40 : 56, outX = s < 0 ? 56 : -40;
    m.cylY(`hdr_in_${n}`, [inX, zf], 3.2, 20, 106, 'gw');
    m.cylY(`hdr_out_${n}`, [outX, zf], 3.2, 20, 106, 'gs');
    for (const [x, mat] of [[inX, 'gw'], [outX, 'gs']]) {
      const x0 = x < 8 ? -48 : x + 3.2, x1 = x < 8 ? x - 3.2 : 64;
      m.box(`hdr_stub_${n}_${x}`, [x0 + (x < 8 ? 0.05 : 0), 53, zf - 2.4], [x1 - (x < 8 ? 0 : 0.05), 59, zf + 2.4], mat, { split: '' });
    }
  }

  function reactor() { const m = PCVV.mk(); calandria(m); face(m, -1); face(m, 1); return m; }
  function closurePlug() {
    const [x, y] = channels()[0], m = PCVV.mk('material/steel');
    m.box('closure_plug', [x - 1.1, y - 1.1, -46.2], [x + 1.1, y + 1.1, -45], 'bright', { split: '' });
    m.box('closure_lug', [x - 0.4, y - 0.4, -47], [x + 0.4, y + 0.4, -46.2], 'st', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ fuelling machine
  function fm() {
    const m = PCVV.mk();
    m.box('rail', [-48, 0, 4], [64, 2, 28], 'pnt', { split: 'x' });
    for (const x of [-48, 56]) {
      m.box(`column_${x}`, [x, 2, 6], [x + 8, 112, 26], 'pnt', { split: 'y' });
      m.box(`column_guide_${x}`, [x < 0 ? x + 8 : x - 0.8, 4, 12], [x < 0 ? x + 8.8 : x, 110, 20], 'bright', { split: 'y' });
    }
    m.box('top_beam', [-48, 104, 6], [64, 112, 26], 'pnt', { split: 'x' });
    for (const x of [-46, 58]) m.box(`hoist_${x}`, [x, 96, 2], [x + 4, 104, 6], 'motor', { split: '' });
    // hoses down the west column, cable chain down the east column
    m.box('hoses', [-44, 2, 26], [-40, 100, 30], 'dark', { split: 'y' });
    m.box('hose_port', [-48, 6, 22], [-44, 10, 30], 'ci', { split: '' });
    m.box('cable_chain', [58, 2, 26], [62, 100, 30], 'dark', { split: 'y' });
    m.box('cable_box', [60, 4, 20], [64, 12, 28], 'pnt', { split: '' });
    // transfer chute at the bottom, north
    m.box('chute', [2, 2, 0], [14, 10, 8], 'ci', { split: '' });
    m.box('chute_lip', [1, 10, 0], [15, 11, 9], 'yel', { split: '' });
    m.decal('plate', [-46, 50, 5.4], [-40, 53.6, 6], 'pnt', 'north', 'pl.maker');
    return m;
  }
  function bridge(x0, x1) {
    return () => {
      const m = PCVV.mk();
      m.box(`beam_${x0}`, [x0, 40, 4], [x1, 48, 28], 'yel', { split: '' });
      m.box(`beam_rail_${x0}`, [x0, 48, 8], [x1, 48.6, 10], 'bright', { split: '' });
      m.box(`beam_rail2_${x0}`, [x0, 48, 22], [x1, 48.6, 24], 'bright', { split: '' });
      return m;
    };
  }
  function carriage() {
    const m = PCVV.mk();
    m.box('carriage', [-4, 48.6, 4], [20, 54, 28], 'pnt', { split: '' });
    for (const x of [-2, 16]) m.box(`wheel_${x}`, [x, 48, 7], [x + 2, 50, 25], 'dark', { split: '' });
    m.box('cradle_n', [-2, 54, 6], [18, 58, 8], 'pnt', { split: '' });
    m.box('cradle_s', [-2, 54, 24], [18, 58, 26], 'pnt', { split: '' });
    return m;
  }
  function head() {
    const m = PCVV.mk();
    m.prism('drum', 'z', [8, 66], 10.5, 6, 26, 'clad', { split: '', ends: ['north', 'south'] });
    m.disc('drum_cover', 'z', [8, 66], 10.8, 5.4, 6, 'forged', 'vver/flange', 'north');
    for (let k = 0; k < 8; k++) {
      const a = k * Math.PI / 4, x = 8 + 7 * Math.cos(a), y = 66 + 7 * Math.sin(a);
      m.box(`port_${k}`, [x - 1.4, y - 1.4, 5], [x + 1.4, y + 1.4, 5.4], 'dark', { split: '' });
    }
    m.box('ram_drive', [4, 76, 4], [12, 80, 20], 'motor', { split: '' });
    return m;
  }
  function snout() {
    const m = PCVV.mk();
    m.prism('snout', 'z', [8, 66], 3.4, 26, 32, 'forged', { split: '', ends: ['south'] });
    m.ring('snout_lock', 'z', [8, 66], 4.4, 1, 29, 31, 'bright');
    return m;
  }

  const UNITS = {
    reactor: { id: 'candu_npd_reactor_real', draw: reactor, parts: { closure_plug: { draw: closurePlug, shift: [0, -40, 40] } } },
    fuelling_machine: {
      id: 'candu_npd_fuelling_machine_real', draw: fm, parts: {
        bridge_w: { draw: bridge(-40, 8), shift: [24, -24, 0] },
        bridge_e: { draw: bridge(8, 56), shift: [-24, -24, 0] },
        carriage: { draw: carriage, shift: [0, -48, 0] },
        head: { draw: head, shift: [0, -48, 0] },
        snout: { draw: snout, shift: [0, -48, 0] },
      },
    },
  };
  const partial = (k, p) => { const d = UNITS[k].parts[p]; const [dx, dy, dz] = d.shift; return PCVV.mk().merge(d.draw(), dx, dy, dz, ''); };
  window.NPD = {
    UNITS, channels, partial,
    parts: (k) => Object.keys(UNITS[k].parts),
    model: (k) => UNITS[k].draw(),
    build: (root, k) => { const m = PCVV.cut(UNITS[k].draw()); PCART.realize(root, m, UNITS[k].id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, k) => PCVV.save(root, UNITS[k].id, 'core/candu_npd'),
    buildPartial: (root, k, p) => { const m = partial(k, p); PCART.realize(root, m, UNITS[k].id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, k, p) => PCVV.savePartial(root, UNITS[k].id, p, 'core/candu_npd'),
    preview: (root) => {
      const m = reactor();
      const f = fm(); for (const d of Object.values(UNITS.fuelling_machine.parts)) f.merge(d.draw(), 0, 0, 0, 'p');
      m.merge(f, 0, 0, -80, 'fm_'); PCVV.cut(m); PCART.realize(root, m, 'candu_npd_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) };
    },
  };
})();
