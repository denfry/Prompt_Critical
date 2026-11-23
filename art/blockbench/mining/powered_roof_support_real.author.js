// Two-leg shield powered roof support at real scale (1 block = 1 m, 16 px = 1 m), one section of the longwall face
// for the 2 m seam of longwall_shearer_real: 2.0 m pitch (Joy/Glinik-type 2.05 m shields), working height 2.0 m
// (yield range about 1.4..3.0 m), canopy 1.85 m wide and 4.0 m long with a 0.35 m thick tip over the conveyor, two
// 0.32 m legs, two base pontoons with the relay bar and advancing ram between them, lemniscate links carrying the
// caving shield on the goaf side, and the control valve bank with its leg pressure gauge on the walkway.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   PRSR.build(repo); /* wait */ PRSR.save(repo)
//   PRSR.buildPartial(repo, name); PRSR.savePartial(repo, name)    for canopy_n, canopy_s, ram
//   PRSR.preview(repo)                                             everything in place, for looking only
//
// Controller block = (0, 0, 0), north (z 0) faces the coal and the conveyor. Footprint x 0..1, y 0..1, z 0..3.
// Connections (px): relay bar head out of the north face of (0, 0, 0) at (16, 3, 0) to the face conveyor
// (between cells (0, 0, 0) and (1, 0, 0)); emulsion pressure line in on the west face of (0, 0, 2) at (0, 5, 40),
// return line out on the east face of (1, 0, 2) at (32, 5, 40).
// Partials (drawn at their shift back, see PART):
// - canopy_n: canopy front half with the tip and the leg inner stages; translate -y by the leg retraction (whole
//   section released: about 2 px) and back up when set.
// - canopy_s: canopy back half with the caving shield top; same translation as canopy_n, draw at +32 z.
// - ram: relay bar with its head; slides -z (towards the face) by the advance step when the conveyor is pushed,
//   +z when the section is drawn up to it.
(function () {
  const PX = [1, 31], PT = [26.4, 32];          // canopy x span, canopy y (0.35 m thick)
  const PON = [[2, 12], [20, 30]];              // base pontoons x spans
  const LEGS = [7, 25], LZ = 26;                // leg centres x, z
  const RAM = [13, 19];                         // relay bar x span
  const MAT = Object.assign({}, PCVV.MAT, {
    prs: { tex: 'powered_roof_support/paint', P: 16 }, hose: { tex: 'powered_roof_support/hose', P: 16 },
    gauge: { tex: 'powered_roof_support/gauge', P: 16 }, lwd: { tex: 'longwall_shearer/dark', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'powered_roof_support/paint' });

  // ------------------------------------------------------------------ static: base, legs, links, valves
  function base(m) {
    PON.forEach(([x0, x1], i) => {
      m.box(`pontoon_${i}`, [x0, 0, 14], [x1, 5, 60], 'prs', { split: 'z' });
      m.box(`pontoon_toe_${i}`, [x0, 0, 8], [x1, 3, 14], 'prs', { split: '' });
      m.box(`pontoon_toe_top_${i}`, [x0 + 0.5, 3, 10], [x1 - 0.5, 4, 14], 'prs', { split: '' });
      m.box(`pontoon_rib_${i}`, [x0 - 0.4, 0.6, 18], [x0, 4.4, 58], 'lwd', { split: 'z' });
      m.box(`pontoon_rib_b_${i}`, [x1, 0.6, 18], [x1 + 0.4, 4.4, 58], 'lwd', { split: 'z' });
      // leg sockets on the pontoons
      const cx = LEGS[i];
      m.cylY(`leg_socket_${i}`, [cx, LZ], 3.4, 5, 7, 'lwd');
      // lemniscate link lugs at the back
      m.box(`lug_${i}`, [x0 + 2, 5, 52], [x1 - 2, 9, 58], 'prs', { split: '' });
      m.prism(`lug_pin_${i}`, 'x', [8, 54], 1.2, x0 + 1.4, x1 - 1.4, 'bright', { split: '', ends: ['west', 'east'] });
    });
    // rear bridge joining the pontoons, the relay ram anchor in it
    m.box('bridge', [PON[0][1], 0, 46], [PON[1][0], 6, 60], 'prs', { split: '' });
    m.box('bridge_lip', [PON[0][1], 6, 56], [PON[1][0], 7, 60], 'lwd', { split: '' });
    // advancing ram: cylinder from the bridge forward between the pontoons
    m.prism('ram_cyl', 'z', [16, 3], 2.2, 22, 46, 'prs', { split: '', ends: ['north'] });
    m.prism('ram_gland', 'z', [16, 3], 2.6, 21, 22.6, 'lwd', { split: '', ends: ['north', 'south'] });
    m.box('ram_hose_port', [17.6, 4.6, 40], [19, 6, 42], 'bright', { split: '' });
  }

  function legs(m) {
    LEGS.forEach((cx, i) => {
      m.cylY(`leg_outer_${i}`, [cx, LZ], 2.6, 7, 18.4, 'prs');
      m.cylY(`leg_collar_${i}`, [cx, LZ], 3, 17.4, 18.6, 'lwd');
      m.box(`leg_valve_${i}`, [cx - 1, 9, LZ - 4.2], [cx + 1, 12, LZ - 2.4], 'bright', { split: '' });
      // pressure hose from the valve bank to the leg foot
      const hx = cx < 16 ? [cx + 1, 13] : [19, cx - 1];
      m.box(`leg_hose_${i}`, [hx[0], 9.6, LZ - 3.6], [hx[1], 10.6, LZ - 2.6], 'hose', { split: '' });
    });
  }

  // Lemniscate links from the base lugs to the caving shield foot, at 45 degrees up and back.
  function links(m) {
    PON.forEach(([x0, x1], i) => {
      const xa = x0 + 2.4, xb = x1 - 2.4;
      for (const [k, y0, z0] of [['lo', 5.6, 54], ['hi', 8, 52]]) {
        m.box(`link_${k}_${i}`, [xa, y0, z0], [xb, y0 + 2, z0 + 8], 'lwd', { split: '', rot: { axis: 'x', angle: 45, origin: [16, y0 + 1.2, z0] } });
      }
    });
  }

  // Control valve bank on a bracket over the relay ram, facing the walkway (west), with the leg pressure gauge.
  function valves(m) {
    m.box('valve_bracket', [RAM[0] - 0.4, 5.2, 28], [RAM[1] + 0.4, 6, 36], 'lwd', { split: '' });
    m.box('valve_bank', [RAM[0], 6, 29], [RAM[1], 11, 35], 'st', { split: '' });
    for (let k = 0; k < 4; k++) {
      const z = 29.8 + k * 1.4;
      m.box(`lever_${k}`, [RAM[0] - 2.4, 10, z], [RAM[0], 10.6, z + 0.6], 'red', { split: '' });
      m.box(`lever_knob_${k}`, [RAM[0] - 3, 9.6, z - 0.1], [RAM[0] - 2.2, 11, z + 0.7], 'dark', { split: '' });
    }
    m.box('gauge_case', [RAM[0] - 0.6, 11, 31], [RAM[0] + 1, 14, 34], 'st', { split: '' });
    m.plate('gauge_face', [RAM[0] - 0.7, 11.2, 31.2], [RAM[0] - 0.6, 13.8, 33.8], 'st', 'west', 'powered_roof_support/gauge');
    // emulsion pressure and return lines along the walkway to the side ports
    m.box('line_p', [0, 4.4, 39.4], [RAM[0], 5.6, 40.6], 'oil', { split: 'x' });
    m.box('line_p_up', [RAM[0] - 1.2, 5.6, 35], [RAM[0], 6.8, 40.6], 'oil', { split: '' });
    m.box('line_r', [RAM[1], 4.4, 39.4], [32, 5.6, 40.6], 'hose', { split: 'x' });
    m.box('coupling_p', [0, 4, 39], [0.8, 6, 41], 'bright', { split: '' });
    m.box('coupling_r', [31.2, 4, 39], [32, 6, 41], 'bright', { split: '' });
    m.decal('plate', [PON[0][0] - 0.5, 1, 30], [PON[0][0] - 0.4, 3.6, 36], 'prs', 'west', 'pl.maker');
  }

  // ------------------------------------------------------------------ partials
  // Canopy: main plate with the hinge bosses at the back, the tip tapering over the conveyor, side shields.
  function canopy(m, z0, z1) {
    const lo = Math.max(z0, 6), hi = Math.min(z1, 50);
    if (z0 < 6) {
      m.box('tip', [PX[0], PT[0] + 2.4, 0], [PX[1], PT[1], 6], 'prs', { split: '' });
      m.box('tip_under', [PX[0] + 1, PT[0] + 1, 2], [PX[1] - 1, PT[0] + 2.4, 6], 'prs', { split: '' });
    }
    if (lo < hi) {
      m.box(`canopy_${z0}`, [PX[0], PT[0], lo], [PX[1], PT[1], hi], 'prs', { split: '', hide: ['up'] });
      m.box(`canopy_top_${z0}`, [PX[0], PT[1] - 0.01, lo], [PX[1], PT[1], hi], 'lwd', { split: '' });
      for (const x of [PX[0], PX[1] - 0.6]) m.box(`side_shield_${z0}_${x}`, [x, PT[0] - 2, lo], [x + 0.6, PT[0], hi], 'prs', { split: '' });
      for (const z of [14, 30, 46]) if (z >= lo && z < hi) m.box(`web_${z}`, [PX[0] + 1, PT[0] - 1.2, z], [PX[1] - 1, PT[0], z + 1], 'lwd', { split: '' });
    }
    if (z1 > 50) {
      m.box('canopy_heel', [PX[0] + 1, PT[0] + 1, 50], [PX[1] - 1, PT[1] - 0.6, 53], 'prs', { split: '' });
      m.prism('hinge_pin', 'x', [PT[0] + 2, 51.5], 1.4, PX[0] + 0.5, PX[1] - 0.5, 'bright', { split: '', ends: ['west', 'east'] });
      // caving shield hanging down and back from the hinge at 22.5 degrees, its foot on the links
      m.box('caving_shield', [PX[0] + 0.5, 6, 51], [PX[1] - 0.5, PT[0] + 2, 54], 'prs', { split: '', rot: { axis: 'x', angle: -22.5, origin: [16, PT[0] + 2, 51.5] } });
      m.box('shield_rib', [15, 7, 54], [17, PT[0] + 1, 55], 'lwd', { split: '', rot: { axis: 'x', angle: -22.5, origin: [16, PT[0] + 2, 51.5] } });
    }
    // leg inner stages and the canopy sockets
    if (LZ >= z0 && LZ < z1) for (const [i, cx] of LEGS.entries()) {
      m.cylY(`leg_inner_${i}`, [cx, LZ], 2, 18.6, PT[0] - 1, 'bright');
      m.cylY(`leg_head_${i}`, [cx, LZ], 3, PT[0] - 1.2, PT[0], 'lwd');
    }
  }
  function ram(m) {
    m.prism('ram_rod', 'z', [16, 3], 1.2, 6, 22, 'bright', { split: '' });
    m.box('relay_bar', [RAM[0] + 0.6, 0.4, 2], [RAM[1] - 0.6, 2, 21], 'lwd', { split: '' });
    m.box('bar_head', [RAM[0] - 0.4, 0.4, 0], [RAM[1] + 0.4, 5.6, 6], 'prs', { split: '' });
    m.cylY('head_pin', [16, 3], 1, 5.6, 6.6, 'bright');
  }

  const PART = {
    canopy_n: { draw: (m) => canopy(m, 0, 32), shift: [0, 0, 0], note: 'translate -y by the leg retraction' },
    canopy_s: { draw: (m) => canopy(m, 32, 64), shift: [0, 0, -32], note: 'as canopy_n; draw at +32 z' },
    ram: { draw: ram, shift: [0, 0, 0], note: 'slides along z by the advance step' },
  };
  function model() { const m = mk(); base(m); legs(m); links(m); valves(m); return m; }
  function partial(name) {
    const src = mk(); PART[name].draw(src);
    const [dx, dy, dz] = PART[name].shift;
    return mk().merge(src, dx, dy, dz, '');
  }
  function preview() {
    const m = model();
    for (const p of Object.values(PART)) { const s = mk(); p.draw(s); m.merge(s, 0, 0, 0, ''); }
    return m;
  }
  const ID = 'powered_roof_support_real';
  window.PRSR = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'mining'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'mining'),
    preview: (root) => { const m = PCVV.cut(preview()); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
