// Shared kit for the hand-authored models of content that is in the GDD art registry but not yet in the mod
// (art/blockbench/<cat>/*.author.js of the "future" wave). Load after pcart.js, pcround.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/futurekit.js', 'utf8'))
//
// It bundles the shared materials of earlier hand-drawn machines (plant enamels, porcelain, lamps, grating,
// glass, stainless) under short keys and the fittings drawn the same way everywhere (cabinets, vessels, motors,
// lamps, flanges, gauges, ladders), and makes a machine script's window object from its BUILDS map.
// Front to the north unless a script says otherwise. Exports are synchronous: Blockbench throttles timers when
// minimised, and an async export loop can later save whatever project is open under the wrong id.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, writePng } = PCART;

  const MAT = Object.assign({}, PLANT.MAT, {
    ss: { tex: 'electronics/ss', P: 16 }, glass: { tex: 'electronics/glass', P: 16 }, frost: { tex: 'electronics/frost', P: 16 },
    scr: { tex: 'electronics/screen', P: 16 }, vglow: { tex: 'electronics/view_glow', P: 16 }, vdark: { tex: 'electronics/view_dark', P: 16 },
    grat: { tex: 'steelmaking/grating', P: 8 }, graph: { tex: 'steelmaking/graphite', P: 16 }, melt: { tex: 'steelmaking/melt', P: 16 },
    dinas: { tex: 'steelmaking/dinas', P: 16 }, eafp: { tex: 'steelmaking/eaf_panel', P: 16 }, wconc: { tex: 'steelmaking/wet_concrete', P: 16 },
    porc: { tex: 'disconnector/porcelain', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 }, galv: { tex: 'disconnector/galvanised', P: 16 },
    en: { tex: 'switchgear_cell/enamel', P: 16 }, door: { tex: 'switchgear_cell/door', P: 16 },
    tx: { tex: 'distribution_transformer/enamel', P: 16 }, fin: { tex: 'distribution_transformer/fins', P: 16 },
    louv: { tex: 'grid_lines/louvre', P: 16 }, alu: { tex: 'grid_lines/alu', P: 16 }, cable: { tex: 'grid_lines/cable', P: 16 },
    gravel: { tex: 'grid_lines/gravel', P: 16 }, warn: { tex: 'grid_lines/warn', P: 16 }, label: { tex: 'grid_lines/label', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 },
    l_w: { tex: 'grid_lines/lamp_white', P: 16 }, l_off: { tex: 'grid_lines/lamp_off', P: 16 },
    fglow: { tex: 'rolling/furnace_glow', P: 16 }, hot: { tex: 'rolling/hot', P: 16 }, mblue: { tex: 'rolling/mill_blue', P: 16 },
    oil: { tex: 'rolling/oil', P: 16 }, roll: { tex: 'rolling/roll', P: 16 }, water: { tex: 'utilities/water', P: 16 },
    cwhite: { tex: 'utilities/cryo_white', P: 16 }, disp: { tex: 'utilities/display', P: 16 }, hvs: { tex: 'utilities/hv_sign', P: 16 },
    rw: { tex: 'utilities/red_white', P: 16 }, frpg: { tex: 'fuel/frp_green', P: 16 }, monel: { tex: 'fuel/monel', P: 16 },
  });

  // Paints a script's own textures (T: name -> () => img) into textures/block/<dir>/.
  function paintSet(root, dir, T) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', dir, n + '.png'));
    return Object.keys(T).length;
  }
  // A painted plate with soft blotches and a few darker flecks (and rust specks if `rust`).
  function enamel(base, seed, rust) {
    return () => {
      const im = img(32, 32), r = grain(im, base, seed, 4, 6, 8);
      for (let k = 0; k < 12; k++) put(im, r() * 32, r() * 32, add(base, -22));
      for (let k = 0; k < (rust || 0); k++) put(im, r() * 32, r() * 32, [110, 84, 60]);
      return im;
    };
  }

  // ------------------------------------------------------------ fittings
  const P = (m, n, f, t, mat, o) => m.box(n, f, t, mat, Object.assign({ split: '' }, o || {}));
  // Signal lamp on a face: dir is the face it looks out of ('north', 'south', 'east', 'west', 'up').
  function lamp(m, n, x, y, z, mat, glow, k, dir) {
    k = k || 0.6; dir = dir || 'north';
    const g = (glow ? 'glow_' : '') + n;
    if (dir === 'north') { P(m, n + '_bz', [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber'); P(m, g, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat); }
    else if (dir === 'south') { P(m, n + '_bz', [x - k - 0.2, y - k - 0.2, z], [x + k + 0.2, y + k + 0.2, z + 0.15], 'rubber'); P(m, g, [x - k, y - k, z + 0.15], [x + k, y + k, z + 0.35], mat); }
    else if (dir === 'east') { P(m, n + '_bz', [x, y - k - 0.2, z - k - 0.2], [x + 0.15, y + k + 0.2, z + k + 0.2], 'rubber'); P(m, g, [x + 0.15, y - k, z - k], [x + 0.35, y + k, z + k], mat); }
    else if (dir === 'west') { P(m, n + '_bz', [x - 0.15, y - k - 0.2, z - k - 0.2], [x, y + k + 0.2, z + k + 0.2], 'rubber'); P(m, g, [x - 0.35, y - k, z - k], [x - 0.15, y + k, z + k], mat); }
    else { P(m, n + '_bz', [x - k - 0.2, y, z - k - 0.2], [x + k + 0.2, y + 0.15, z + k + 0.2], 'rubber'); P(m, g, [x - k, y + 0.15, z - k], [x + k, y + 0.35, z + k], mat); }
  }
  // Enclosure with a door on the north face: body, door leaf proud by 0.2, hinge side west, handle, a vent.
  function cabinet(m, n, f, t, mat, o = {}) {
    P(m, n, f, t, mat || 'light');
    const dm = o.door || mat || 'light', i = o.inset || 0.8;
    P(m, n + '_door', [f[0] + i, f[1] + i, f[2] - 0.2], [t[0] - i, t[1] - i, f[2]], dm);
    P(m, n + '_handle', [t[0] - i - 1.2, (f[1] + t[1]) / 2 - 1, f[2] - 0.5], [t[0] - i - 0.6, (f[1] + t[1]) / 2 + 1, f[2] - 0.2], 'ci');
    for (const y of [f[1] + i + 1, t[1] - i - 2]) P(m, n + '_hinge' + y, [f[0] + i - 0.3, y, f[2] - 0.4], [f[0] + i + 0.2, y + 1, f[2] - 0.1], 'ci');
    if (o.plate !== false) m.decal(n + '_plate', [f[0] + i + 0.8, t[1] - i - 2.6, f[2] - 0.3], [f[0] + i + 3.8, t[1] - i - 1.1, f[2] - 0.2], 'light', 'north', 'parts.tag');
  }
  // Upright vessel with dished heads: shell r, from y0 to y1 (straight part), heads as stepped short cylinders.
  function vessel(m, n, c, r, y0, y1, mat, o = {}) {
    const cap = o.cap || m.mat[mat].tex;
    m.cylY(n, c, r, y0, y1, mat, { seed: o.seed, cap: o.top === false ? cap : undefined });
    if (o.top !== false) { m.cylY(n + '_head', c, r * 0.82, y1, y1 + r * 0.22, mat, { cap }); m.cylY(n + '_crown', c, r * 0.48, y1 + r * 0.22, y1 + r * 0.36, mat, { cap }); }
    if (o.bottom) { m.cylY(n + '_bhead', c, r * 0.82, y0 - r * 0.22, y0, mat, { bottom: true }); }
  }
  // Horizontal vessel along x or z with heads.
  function drum(m, n, axis, c, r, a0, a1, mat) {
    m.prism(n, axis, c, r, a0, a1, mat, { split: axis });
    const ends = axis === 'x' ? ['west', 'east'] : ['north', 'south'];
    m.prism(n + '_h0', axis, c, r * 0.8, a0 - r * 0.2, a0, mat, { split: '', ends: [ends[0]] });
    m.prism(n + '_h1', axis, c, r * 0.8, a1, a1 + r * 0.2, mat, { split: '', ends: [ends[1]] });
  }
  // Pipe flange ring round a z, x or y pipe of radius r at position a.
  function flange(m, n, axis, c, r, a, mat) {
    if (axis === 'y') m.cylY(n, c, r + 0.6, a, a + 0.6, mat || 'ci', { cap: null });
    else m.prism(n, axis, c, r + 0.6, a, a + 0.6, mat || 'ci', { split: '', ends: axis === 'x' ? ['east', 'west'] : ['north', 'south'] });
  }
  // TEFC induction motor along x or z: finned frame, end shields, terminal box on top, feet.
  function motor(m, n, axis, c, r, a0, a1, mat, o = {}) {
    mat = mat || 'blue';
    m.prism(n + '_frame', axis, c, r, a0 + 0.8, a1 - 0.8, mat, { split: '' });
    m.prism(n + '_shield0', axis, c, r * 0.86, a0, a0 + 0.8, mat, { split: '', ends: axis === 'x' ? ['west'] : ['north'] });
    m.prism(n + '_shield1', axis, c, r * 0.86, a1 - 0.8, a1, mat, { split: '', ends: axis === 'x' ? ['east'] : ['south'] });
    const mid = (a0 + a1) / 2;
    if (axis === 'x') {          // c = [y, z]
      const [y, z] = c;
      for (const s of [-1, 1]) P(m, n + '_fin' + s, [a0 + 1.2, y - 0.2, z - r - 0.3], [a1 - 1.2, y + 0.2, z + r + 0.3], mat, { rot: { axis: 'x', angle: 45 * s, origin: [8, y, z] } });
      P(m, n + '_tbox', [mid - 1.6, y + r - 0.4, z - 1.4], [mid + 1.6, y + r + 1.6, z + 1.4], mat);
      if (o.feet !== false) for (const x of [a0 + 1, a1 - 2.6]) P(m, n + '_foot' + x, [x, y - r - 0.4, z - r * 0.8], [x + 1.6, y - r * 0.6, z + r * 0.8], mat);
    } else {                     // c = [x, y]
      const [x, y] = c;
      for (const s of [-1, 1]) P(m, n + '_fin' + s, [x - r - 0.3, y - 0.2, a0 + 1.2], [x + r + 0.3, y + 0.2, a1 - 1.2], mat, { rot: { axis: 'z', angle: 45 * s, origin: [x, y, 8] } });
      P(m, n + '_tbox', [x - 1.4, y + r - 0.4, mid - 1.6], [x + 1.4, y + r + 1.6, mid + 1.6], mat);
      if (o.feet !== false) for (const z of [a0 + 1, a1 - 2.6]) P(m, n + '_foot' + z, [x - r * 0.8, y - r - 0.4, z], [x + r * 0.8, y - r * 0.6, z + 1.6], mat);
    }
  }
  // Bourdon gauge on a face looking north (or `dir`), dial from plant/parts.
  function gauge(m, n, x, y, z, k, dir) {
    k = k || 1.2; dir = dir || 'north';
    if (dir === 'north') { m.decal(n, [x - k, y - k, z - 0.5], [x + k, y + k, z], 'ci', 'north', 'parts.gauge'); P(m, n + '_stem', [x - 0.25, y - k - 1, z - 0.4], [x + 0.25, y - k, z], 'br'); }
    else if (dir === 'south') { m.decal(n, [x - k, y - k, z], [x + k, y + k, z + 0.5], 'ci', 'south', 'parts.gauge'); P(m, n + '_stem', [x - 0.25, y - k - 1, z], [x + 0.25, y - k, z + 0.4], 'br'); }
    else if (dir === 'east') { m.decal(n, [x, y - k, z - k], [x + 0.5, y + k, z + k], 'ci', 'east', 'parts.gauge'); P(m, n + '_stem', [x, y - k - 1, z - 0.25], [x + 0.4, y - k, z + 0.25], 'br'); }
    else { m.decal(n, [x - 0.5, y - k, z - k], [x, y + k, z + k], 'ci', 'west', 'parts.gauge'); P(m, n + '_stem', [x - 0.4, y - k - 1, z - 0.25], [x, y - k, z + 0.25], 'br'); }
  }
  // Ladder on the north (dz=-1) or other face; stiles along y.
  function ladder(m, n, x, z, y0, y1, along) {
    for (const d of [0, 2.2]) {
      if (along === 'z') P(m, n + '_s' + d, [x, y0, z + d], [x + 0.4, y1, z + d + 0.4], 'yel');
      else P(m, n + '_s' + d, [x + d, y0, z], [x + d + 0.4, y1, z + 0.4], 'yel');
    }
    for (let y = y0 + 1; y < y1 - 0.3; y += 2) {
      if (along === 'z') P(m, n + '_r' + y, [x, y, z], [x + 0.4, y + 0.3, z + 2.6], 'yel');
      else P(m, n + '_r' + y, [x, y, z], [x + 2.6, y + 0.3, z + 0.4], 'yel');
    }
  }
  // Skid base frame: two channel runners and cross members, on the floor.
  function skid(m, n, f, t, mat) {
    mat = mat || 'frame';
    P(m, n + '_r0', [f[0], 0, f[1]], [t[0], 1.4, f[1] + 1.2], mat);
    P(m, n + '_r1', [f[0], 0, t[1] - 1.2], [t[0], 1.4, t[1]], mat);
    for (let x = f[0] + 1; x < t[0] - 1; x += 8) P(m, n + '_x' + x, [x, 0.2, f[1] + 1.2], [x + 1, 1.2, t[1] - 1.2], mat);
    P(m, n + '_deck', [f[0] + 0.4, 1.2, f[1] + 0.4], [t[0] - 0.4, 1.4, t[1] - 0.4], 'chequer');
  }

  // Porcelain or polymer insulator column about a vertical axis: core, sheds every `pitch`, metal caps.
  // Sheds are octagons (a square and the same square turned 45 degrees) to keep tall bushings light.
  function octY(m, n, c, r, y0, y1, mat) {
    const h = r * 0.8284 / 2 + r * 0.5858 / 2; // half side of the square whose 45-degree twin makes a regular octagon
    for (const a of [0, 45]) m.box(n + '_' + a, [c[0] - h, y0, c[1] - h], [c[0] + h, y1, c[1] + h], mat, { split: '', rot: a ? { axis: 'y', angle: a, origin: [c[0], 8, c[1]] } : undefined });
  }
  function insulatorY(m, n, c, r, y0, h, mat, o = {}) {
    const pitch = o.pitch || 1.6, cap = o.cap || 'ci';
    octY(m, n + '_core', c, r * 0.55, y0, y0 + h, mat);
    for (let y = y0 + 0.8, i = 0; y < y0 + h - 0.6; y += pitch, i++) octY(m, `${n}_shed${i}`, c, (i % 2 && o.alt) ? r * 0.8 : r, y, y + Math.min(0.5, pitch * 0.4), mat);
    octY(m, n + '_flange0', c, r * 0.72, y0 - 0.6, y0, cap);
    octY(m, n + '_flange1', c, r * 0.72, y0 + h, y0 + h + 0.6, cap);
  }
  // Window object for a script: build, preview (side by side, dx apart), synchronous exportAll.
  function machine(BUILDS, paint) {
    return {
      paint, ids: Object.keys(BUILDS),
      build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
      preview(root, ids, dx, dz) {
        const m = BUILDS[ids[0]].model();
        ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * (dx || 0), 0, (i + 1) * (dz || 0), `m${i}_`));
        return PCART.realize(root, m, 'preview');
      },
      exportAll(root, ids) {
        const out = {};
        for (const id of ids || Object.keys(BUILDS)) {
          ModelProject.all.slice().forEach((p) => p.close(true));
          PCART.realize(root, BUILDS[id].model(), id);
          out[id] = PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java);
        }
        ModelProject.all.slice().forEach((p) => p.close(true));
        return out;
      },
    };
  }

  window.FUTK = { MAT, paintSet, enamel, P, lamp, cabinet, vessel, drum, flange, motor, gauge, ladder, skid, machine, insulatorY, octY,
    mk: (MATX, particle) => Object.assign(new PCART.Model(Object.assign({}, MAT, MATX || {}), { parts: PLANT.PARTS }), { particle }) };
})();
