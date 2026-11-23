// Na-cation softening filter of the I1 boiler-house water treatment at real scale (1 block = 1 m): a painted steel
// vessel Ø 0.9 m (r 7 px), 4 m tall over all, on legs, with dished heads, an upper manhole and a lower hand-hole, the front
// valve nest (service in and out, backwash in and out, brine) on two headers with hand wheels, pressure gauges top and
// bottom and a sampling cock. Pipes in GOST 14202 colours: water green, brine (other liquids) brown. Dimensions and
// sources: docs/research/ion_exchange_column-dimensions.md (disk only).
//
// Footprint (agreed with 8f): 1 x 4 x 1, Footprint.tiled(0, 0, 0, 3, 0, 0) (model x 0..16, y 0..64, z 0..16),
// controller (0, 0, 0) at the bottom, front north. Water in at the top face of cell (0, 3, 0) at (8, 64, 8.6); treated
// water out of the north face of cell (0, 0, 0) at (y 3, x 12). Saved as a whole model `ion_exchange_column_real`
// (4 blocks tall); the old 1-block `ion_exchange_column` model is left in place.
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then IXR.build(repo) /* wait */ IXR.save(repo).
(function () {
  const ID = 'ion_exchange_column_real';
  const C = [8, 8.6], R = 7, Y0 = 9, Y1 = 52;   // vessel axis (x, z), shell radius, cylindrical shell
  // prismY maps its side texture inside one block: build tall courses per block and lift them
  function course(m, name, r, y0, y1, mat, o) {
    let k = 0;
    for (let a = y0; a < y1 - 1e-6; ) {
      const b = Math.min(y1, (Math.floor(a / 16 + 1e-6) + 1) * 16), off = Math.floor(a / 16 + 1e-6) * 16, n0 = m.els.length;
      m.prismY(`${name}_${k++}`, C, r, a - off, b - off, mat, o || {});
      for (const e of m.els.slice(n0)) {
        e.from = [e.from[0], e.from[1] + off, e.from[2]]; e.to = [e.to[0], e.to[1] + off, e.to[2]];
        if (e.rot) e.rot = Object.assign({}, e.rot, { origin: [e.rot.origin[0], e.rot.origin[1] + off, e.rot.origin[2]] });
      }
      a = b;
    }
  }
  const pipeY = (m, n, x, z, y0, y1, mat) => m.box(n, [x - 0.6, y0, z - 0.6], [x + 0.6, y1, z + 0.6], mat, { split: 'y' });
  const pipeZ = (m, n, x, y, z0, z1, mat) => m.box(n, [x - 0.6, y - 0.6, z0], [x + 0.6, y + 0.6, z1], mat, { split: '' });
  function valve(m, n, x, y, z) {
    m.box(`${n}_body`, [x - 1, y - 1, z - 1], [x + 1, y + 1, z + 1], 'ci', { split: '' });
    m.box(`${n}_stem`, [x - 0.2, y - 0.2, z - 2.4], [x + 0.2, y + 0.2, z - 1], 'bright', { split: '' });
    m.disc(`${n}_wheel`, 'z', [x, y], 1.4, z - 2.8, z - 2.4, 'red', 'material/paint_red', 'north');
  }

  function model() {
    const m = PCVV.mk('vver/paint');
    // legs on base plates, bottom head, shell, top head
    for (const [x, z] of [[2.2, 3.2], [13.8, 3.2], [2.2, 14.2], [13.8, 14.2]]) {
      m.box(`leg_${x}_${z}`, [x - 0.7, 0.4, z - 0.7], [x + 0.7, 8, z + 0.7], 'st', { split: '' });
      m.box(`leg_plate_${x}_${z}`, [x - 1.2, 0, z - 1.2], [x + 1.2, 0.4, z + 1.2], 'st', { split: '' });
    }
    m.prismY('head_bot_1', C, R - 2.4, 5.6, 7.2, 'pnt', { ends: ['down'] });
    m.prismY('head_bot_2', C, R - 0.8, 7.2, Y0, 'pnt', {});
    course(m, 'shell', R, Y0, Y1, 'pnt');
    m.ringY('girth_seam', C, R + 0.15, 0.3, 30, 30.6, 'pnt');
    m.prismY('head_top_1', C, R - 0.8, Y1, Y1 + 1.8, 'pnt', {});
    m.prismY('head_top_2', C, R - 2.4, Y1 + 1.8, Y1 + 3.2, 'pnt', { ends: ['up'] });
    // inlet nozzle up to the top face, distributor flange
    m.prismY('top_nozzle', C, 1.6, Y1 + 3.2, Y1 + 5, 'gw', {});
    m.ringY('top_flange', C, 2.4, 1.2, Y1 + 5, Y1 + 6, 'ci');
    course(m, 'inlet', 1.4, Y1 + 6, 64, 'gw', { ends: ['up'] });
    m.box('air_vent', [10.6, Y1 + 3.2, 8.4], [11.6, Y1 + 5.4, 9.4], 'br', { split: '' });
    // manhole (upper) and hand-hole (lower) on the front, bolted covers
    m.box('manhole_neck', [4, 38, 1.2], [12, 46, 2.6], 'pnt', { split: '' });
    m.box('manhole_cover', [3.4, 37.4, 0.6], [12.6, 46.6, 1.2], 'ci', { split: '' });
    for (const [x, y] of [[4, 38], [12, 38], [4, 46], [12, 46], [8, 46.2], [8, 37.8]]) m.box(`mh_bolt_${x}_${y}`, [x - 0.4, y - 0.4, 0.2], [x + 0.4, y + 0.4, 0.6], 'bright', { split: '' });
    m.box('handhole', [5.6, 12, 1.4], [10.4, 16, 2.4], 'ci', { split: '' });
    // front valve nest: service header (left) and wash header (right), outlet under the vessel to the north face
    pipeY(m, 'hdr_service', 4, 1, 3, 50, 'gw');
    pipeY(m, 'hdr_wash', 12, 1, 3, 50, 'gw');
    pipeZ(m, 'conn_top_l', 4, 50, 1, 3, 'gw'); pipeZ(m, 'conn_top_r', 12, 50, 1, 3, 'gw');
    m.box('outlet_run', [3.4, 2.4, 0.4], [12.6, 3.6, 1.6], 'gw', { split: '' });
    pipeZ(m, 'bottom_stub', 8, 3, 1.6, 9, 'gw');
    m.box('outlet_flange', [10.6, 1.6, 0], [13.4, 4.4, 0.4], 'ci', { split: '' });
    valve(m, 'v_service_in', 4, 44, 3.6);
    valve(m, 'v_service_out', 4, 10, 3.6);
    valve(m, 'v_wash_in', 12, 10, 3.6);
    valve(m, 'v_wash_out', 12, 44, 3.6);
    // brine line from the salt dissolver: brown, down the east side into the top of the bed, its valve
    pipeY(m, 'brine', 15, 6, 6, 48, 'oil');
    pipeZ(m, 'brine_in', 15, 48, 6, 9, 'oil');
    m.box('brine_valve', [14, 26, 5], [16, 28, 7], 'ci', { split: '' });
    m.disc('brine_wheel', 'x', [27, 6], 1.2, 15.1, 15.5, 'red', 'material/paint_red', 'east');
    // gauges top and bottom, sampling cock, sight glass on the bed level, maker's plate
    for (const y of [48, 6.6]) {
      m.box(`gauge_pipe_${y}`, [7.6, y, 1.6], [8.4, y + 2, 2.4], 'br', { split: '' });
      m.disc(`gauge_${y}`, 'z', [8, y + 3.2], 1.4, 1.2, 1.8, 'br', 'material/gauge_dial', 'north');
    }
    m.box('sample_cock', [7.6, 20, 1.2], [8.4, 21, 2.4], 'br', { split: '' });
    m.box('sight_glass', [6.8, 24, 1.6], [9.2, 32, 2], 'bright', { split: '' });
    m.decal('plate', [5.6, 34, 1.8], [10.4, 36.4, 2], 'pnt', 'north', 'pl.maker');
    return m;
  }

  window.IXR = {
    ID,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'water_treatment'),
    preview: (root) => { const m = model(); PCART.realize(root, m, 'ix_preview'); return m.els.length; },
  };
})();
