// VVER-1000 pressurizer (KD, 79 m3) at real scale (1 block = 1 m): insulated vertical vessel 3.3 m across and
// 15.6 m tall between the heads, standing on a cylindrical skirt. Electric heater blocks enter the lower shell
// through flanged nozzles (24 shown of 28); the surge line leaves the bottom head, turns inside the skirt and goes
// out east; on top the spray nozzle, the two lines to the pilot-operated safety valves, the manhole and the
// service platform with its rail, reached by a caged ladder on the front. Level gauge lines run down the east
// side. Dimensions and sources: docs/research/vver-dimensions.md (disk only).
// Load after vverkit.js, then VVER_KD.build(repo); /* wait */ VVER_KD.save(repo).
//
// Controller block = (0, 0, 0) inside the skirt, under the vessel axis (x = z = 8). Front north.
// Connections (ends on block faces, axes on block centres):
//   surge line (to a hot leg): east face of cell (2, 0, 0)
//   spray line (from a cold leg): up face of cell (0, 17, 0)
(function () {
  const C = [8, 8], R = 26;
  const Y0 = 38, Y1 = 260;                 // cylindrical shell between the head tangent lines

  function model() {
    const m = PCVV.mk();
    // skirt with its base ring and door, the vessel
    m.cylY('base', C, 26, 0, 2, 'pnt', { bottom: true, cap: 'vver/flange' });
    m.cylY('skirt', C, 24, 2, 26, 'pnt');
    m.box('door', [3, 2, -16.6], [13, 18, -15.7], 'dark', { split: '' });
    m.headDown('bot', C, R, Y0, 14, 4, 'clad');
    m.cylY('shell', C, R, Y0, Y1, 'clad');
    m.headUp('top', C, R, Y1, 14, 4, 'clad');

    // heater blocks: three rows of eight flanged nozzles with terminal covers
    for (const [row, y] of [[0, 46], [1, 56], [2, 66]]) {
      for (let k = 0; k < 8; k++) {
        const a = k * 45 + (row % 2 ? 22.5 : 0);
        if (Math.abs(((a % 360) + 360) % 360 - 270) < 1) continue;   // the ladder side keeps a gap
        m.radial(`ten_${row}_${k}`, C, 24, 29, a, 4.4, y - 2.2, y + 2.2, 'forged');
        m.radial(`ten_cov_${row}_${k}`, C, 29, 31.5, a, 3.2, y - 1.6, y + 1.6, 'dark');
      }
    }

    // surge line: down from the bottom head, elbow inside the skirt, out east (hot water: red band)
    m.cylY('surge_v', C, 3.6, 12, 26, 'clad');
    m.box('surge_elbow', [4.4, 4.4, 4.4], [12.4, 12, 11.6], 'clad', { split: '' });
    m.prism('surge_h', 'x', [8, 8], 3.6, 12.4, 38, 'clad', { split: '' });
    m.prism('surge_collar', 'x', [8, 8], 5, 30, 34, 'pnt', { split: '' });
    m.nozzle('surge', 'x', [8, 8], 36, 48, 4.4, 3.6, 'gs', { nuts: 8, safe: 2 });

    // top: spray nozzle on the axis, safety valve lines, manhole
    m.cylY('spray', C, 2.6, 272, 285.93, 'clad');
    m.cylY('spray_band', C, 2.9, 279, 280.6, 'gw');
    m.cylY('spray_flange', C, 4, 286, 287.93, 'forged', { cap: 'vver/flange', bottom: true });
    m.nutsY('spray_nut', C, 3.4, 8, 284.8, 1.2, 0.9, 'bright');
    for (const x of [-6, 22]) {
      m.cylY(`pv_${x}`, [x, 8], 2.8, 268, 280, 'clad');
      m.cylY(`pv_band_${x}`, [x, 8], 3.1, 274, 275.6, 'gs');
      m.box(`pv_body_${x}`, [x - 3, 280, 4], [x + 3, 285, 12], 'pnt', { split: '' });
      m.box(`pv_out_${x}`, [x - 2, 281, 12], [x + 2, 284, 18], 'pnt', { split: '' });
      m.wheelY(`pv_wheel_${x}`, x, 286.4, 8, 2.4, 'red');
    }
    m.cylY('mh_neck', [8, 24], 5, 266, 276, 'forged');
    m.cylY('mh_cover', [8, 24], 6.2, 276, 278, 'forged', { cap: 'vver/flange' });
    m.nutsY('mh_nut', [8, 24], 5.4, 12, 278, 1, 1, 'bright');

    // service platform at the upper head with its rail
    m.cylY('deck', C, 36, 262, 264, 'pnt', { cap: 'vver/grating_disc', bottom: true });
    m.railY('rail', C, 35.7, 264, 'yel');
    for (let k = 0; k < 8; k++) m.radial(`deck_brk_${k}`, C, 25, 35, k * 45 + 22.5, 1.6, 254, 262, 'pnt');

    // caged ladder up the front from the floor to the platform
    m.ladderZ('ladder', 4, 12, -22, -1, 0, 262, 'yel', 3);

    // level gauge lines down the east side, taps into the shell
    for (const z of [5, 11]) {
      m.box(`lvl_${z}`, [36, 40, z - 0.6], [37.2, 250, z + 0.6], 'st', { split: 'y' });
      for (const y of [48, 244]) m.box(`lvl_tap_${z}_${y}`, [33, y - 0.6, z - 0.6], [37.2, y + 0.6, z + 0.6], 'st', { split: '' });
      m.box(`lvl_valve_${z}`, [35.4, 120, z - 1.4], [38, 123, z + 1.4], 'gw', { split: '' });
    }
    // plates: on the back (the ladder covers the front) and the skirt
    m.decal('sign', [3, 196, 34], [13, 206, 34.7], 'clad', 'south', 'pl.sign');
    m.decal('name', [3, 208, 34], [13, 210.6, 34.7], 'clad', 'south', 'pl.kd_name');
    m.decal('sign_skirt', [5, 10, 32], [11, 16, 32.7], 'pnt', 'south', 'pl.sign_s');
    m.decal('maker', [5, 18, 32], [11, 21, 32.7], 'pnt', 'south', 'pl.maker');
    return m;
  }

  window.VVER_KD = {
    model,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'vver_pressurizer'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'vver_pressurizer'),
  };
})();
