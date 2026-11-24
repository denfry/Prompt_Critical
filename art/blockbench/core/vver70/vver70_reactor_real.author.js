// VVER-70 (Gidropress V-2, Rheinsberg NPP) reactor at real scale (1 block = 1 m, 16 px = 1 m): the 2.85 m vessel with
// its elliptical bottom, the thicker nozzle ring with six DN 500 main nozzles in one row (three loops), the flange
// pair with the nickel wedge-gasket joint, the semi-elliptical head, the support ring on six columns, and the upper
// block with nineteen rack-and-pinion control rod drives, the cable deck and the lifting traverse, 18.7 m in all.
// Sheet: docs/research/2026-11-24-reactor-prototypes/vver70_reactor_real.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   V70R.build(repo); /* wait */ V70R.save(repo)
//   V70R.buildRods(repo); V70R.saveRods(repo)      partial vver70_reactor_real/partial/rods
//   V70R.preview(repo)
//
// Controller block = (0, 0, 0) under the axis, front north. Footprint x -2..2, y 0..18, z -2..2 (5 x 19 x 5). Vessel
// axis vertical through (8, y, 8); inside r 22.8, outside r 25; main joint at y 164; top of the traverse y 299.6.
// Main nozzles at y 120 (ends on block faces): loop 1 hot north (8, 120, -32), loop 1 cold west (-32, 120, -8), loop 2
// hot west (-32, 120, 24), loop 2 cold south (8, 120, 48), loop 3 hot east (48, 120, 24), loop 3 cold east
// (48, 120, -8). Drive cables up through the top face of (0, 18, 0).
// Partial rods: the 19 rod position indicators over the drive caps, drawn at the controller + (0, 17, 0) blocks;
// local y 12..19 with the rods in, sliding up +y by up to 8 px as the rods come out.
(function () {
  const C = [8, 8], RO = 25, PITCH = 7.2;
  const HEAD_Y = 164, HEAD_D = 13;

  function drives() {
    const out = [];
    for (let q = -2; q <= 2; q++) for (let r = -2; r <= 2; r++) {
      if (Math.max(Math.abs(q), Math.abs(r), Math.abs(q + r)) > 2) continue;
      out.push([C[0] + PITCH * (q + r / 2), C[1] + PITCH * r * Math.sqrt(3) / 2]);
    }
    return out;
  }
  const headTop = (x, z) => {
    const rr = Math.min(RO, Math.hypot(x - C[0], z - C[1]));
    return HEAD_Y + 2 + HEAD_D * Math.sqrt(1 - Math.pow(rr / RO, 2));
  };

  function vessel(m) {
    m.headDown('bot', C, RO, 18, 16, 5, 'clad');
    m.cylY('shell', C, RO, 18, 110, 'clad');
    m.cylY('nzone', C, RO + 1.5, 110, 132, 'clad', { seed: 2, bottom: true });
    m.cylY('shell_up', C, RO, 132, 154, 'clad', { seed: 5 });
    m.cylY('vflange', C, 29, 154, 160, 'forged', { cap: 'vver/flange', bottom: true });
    m.cylY('hflange', C, 29, 160.2, 166, 'forged', { cap: 'vver/flange', seed: 4 });
    m.nutsY('nut', C, 27, 36, 166, 2.6, 2.2, 'bright');
    m.nutsY('stud', C, 27, 36, 168.6, 1.4, 1.1, 'st');
    m.headUp('uhead', C, RO, HEAD_Y + 2, HEAD_D, 5, 'clad');
    // gasket leak-off line from the joint down the front
    m.box('leakoff', [7.5, 132, -21.6], [8.5, 160, -20.6], 'st', { split: 'y' });
    m.box('leakoff_valve', [6.8, 140, -22.4], [9.2, 142.4, -20.4], 'gw', { split: '' });
    // support ring under the flange on six columns to the shaft floor
    m.ringY('sup_ring', C, 33, 4, 148, 154, 'pnt');
    for (let k = 0; k < 6; k++) {
      const a = k * 60 + 30;
      m.postY(`sup_col_${k}`, C, 32, a, 3.2, 1.5, 148, 'pnt');
      m.postY(`sup_foot_${k}`, C, 32, a, 5.4, 0, 1.5, 'conc', { split: '' });
    }
    // plates on the nozzle ring
    m.decal('sign', [3, 134, -18.6], [13, 144, -18], 'clad', 'north', 'pl.sign');
    m.decal('maker', [5, 90, -17.6], [11, 93.5, -17], 'clad', 'north', 'pl.maker');
  }

  // Six DN 500 main nozzles in one row: hot legs take the red band, cold legs the green one (GOST 14202).
  function nozzles(m) {
    const Y = 120, rb = 7, rp = 5, o = { nuts: 10 };
    m.nozzle('n1_hot', 'z', [8, Y], -12, -32, rb, rp, 'gs', o);
    m.nozzle('n1_cold', 'x', [Y, -8], -10, -32, rb, rp, 'gw', o);
    m.nozzle('n2_hot', 'x', [Y, 24], -10, -32, rb, rp, 'gs', o);
    m.nozzle('n2_cold', 'z', [8, Y], 28, 48, rb, rp, 'gw', o);
    m.nozzle('n3_hot', 'x', [Y, 24], 26, 48, rb, rp, 'gs', o);
    m.nozzle('n3_cold', 'x', [Y, -8], 26, 48, rb, rp, 'gw', o);
    m.decal('loopno_n', [2, 128.6, -28.6], [7, 133, -28], 'clad', 'north', 'pl.n1');
    m.decal('loopno_w', [-28.6, 128.6, 13], [-28, 133, 18], 'clad', 'west', 'pl.n2');
    m.decal('loopno_e', [44, 128.6, 0], [44.6, 133, 5], 'clad', 'east', 'pl.n3');
  }

  function upperBlock(m) {
    // six posts on the head, two bands, the cable deck with its rail and trays
    for (let k = 0; k < 6; k++) m.postY(`uu_post_${k}`, C, 21, k * 60 + 30, 2.4, 172, 270, 'pnt');
    m.ringY('uu_band_0', C, 22.6, 2, 212, 214.5, 'pnt');
    m.ringY('uu_band_1', C, 22.6, 2, 244, 246.5, 'pnt');
    m.cylY('deck', C, 25, 270, 272, 'pnt', { cap: 'vver/grating_disc', bottom: true });
    m.railY('deck_rail', C, 24.6, 272, 'yel');
    m.box('tray_n', [-2, 272, -15], [18, 273.6, -13], 'ci', { split: '' });
    m.box('tray_up', [6, 273.6, -15], [10, 304, -13], 'ci', { split: 'y' });
    // the 19 drive housings: tube from the head, rack-and-pinion gear case with its motor, indicator cap and plug
    drives().forEach(([x, z], i) => {
      const y0 = Math.floor(headTop(x, z) - 1);
      m.box(`crd_${i}`, [x - 1.2, y0, z - 1.2], [x + 1.2, 280, z + 1.2], 'st', { split: 'y' });
      m.box(`crd_gear_${i}`, [x - 2, 232, z - 2], [x + 2, 242, z + 2], 'drv', { split: '' });
      m.box(`crd_motor_${i}`, [x - 1.5, 242, z - 1.5], [x + 1.5, 248, z + 1.5], 'motor', { split: '' });
      m.box(`crd_cap_${i}`, [x - 1.5, 280, z - 1.5], [x + 1.5, 283, z + 1.5], 'pnt', { split: '' });
      m.box(`crd_plug_${i}`, [x - 0.7, 283, z - 0.7], [x + 0.7, 284, z + 0.7], 'dark', { split: '' });
    });
    // ladder from the flange up the north side to the deck
    m.ladderZ('ladder', 4, 12, -24, -1, 168, 272, 'yel', 3);
    // lifting traverse: three ties to a hub with the crane eye (top 18.7 m)
    for (const a of [90, 210, 330]) {
      m.postY(`tie_${a}`, C, 16, a, 1.4, 272, 293, 'yel', { split: '' });
      m.radial(`beam_${a}`, C, 4, 17, a === 90 ? 90 : a === 210 ? 202.5 : 337.5, 1.6, 293, 295.4, 'yel');
    }
    m.box('hub', [1, 293, 1], [15, 296.4, 15], 'yel', { split: '' });
    m.box('eye', [6, 296.4, 7], [10, 299.6, 9], 'st', { split: '' });
  }

  function model() { const m = PCVV.mk(); vessel(m); nozzles(m); upperBlock(m); return m; }
  function rods() {
    const m = PCVV.mk('material/bright_steel');
    drives().forEach(([x, z], i) => {
      m.box(`rod_${i}`, [x - 0.45, 12, z - 0.45], [x + 0.45, 18, z + 0.45], 'bright', { split: '' });
      m.box(`rod_tip_${i}`, [x - 0.7, 18, z - 0.7], [x + 0.7, 19, z + 0.7], 'red', { split: '' });
    });
    return m;
  }
  const ID = 'vver70_reactor_real';
  window.V70R = {
    ID, model, rods, drives,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'core/vver70'),
    buildRods: (root) => { const m = rods(); PCART.realize(root, m, ID + '_rods'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    saveRods: (root) => PCVV.savePartial(root, ID, 'rods', 'core/vver70'),
    preview: (root) => { const m = model(); m.merge(rods(), 0, 272, 0, 'rods_'); PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
  };
})();
