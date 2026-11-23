// By-product coke oven battery at real scale (1 block = 1 m, 16 units = 1 m), as per-cell models for the free
// assembly of CokeBatteryShape: the coke_oven chamber cell (a 0.45 m slot between silica-brick wall halves), the
// coke_battery_wall heating-wall cell, and the gas_collector_main standpipe and collecting main on top. Cells pick
// their model by state: level bottom|middle|top, door none|neg|pos, axis x|z (by rotation). Dimensions and sources:
// docs/research/coke_oven-dimensions.md (disk only). Deliberate deviation: the 2 m chamber pitch of the grid.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   COKE.saveAll(repo)            every cell model and partial in turn (async; waits for textures)
//   COKE.preview(repo)            a 3-chamber, 4-long battery with its mains, for looking only
//
// Canonical frame of every cell model: the chamber runs along z, the door (or buckstay) end faces north (z = 0) and
// stands out to -z. Blockstate rotation: axis z door neg -> y 0, axis z door pos -> y 180, axis x door pos -> y 90,
// axis x door neg -> y 270; door none uses y 0 (axis z) or y 90 (axis x).
(function () {
  const S = [4.4, 11.6];                  // chamber slot across x (0.45 m)
  const LID = [8, 3.6], SP = [8, 10.4];   // charging hole [x, z] in the top oven cell; standpipe [x, z] in the main
  const MAT = Object.assign({}, PCVV.MAT, {
    dinas: { tex: 'steelmaking/dinas', P: 16 }, tar: { tex: 'steelmaking/tar_yellow', P: 16 },
  });
  const FIT = { slot_glow: [30, 10, 1, 5] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT },
  }), { particle: 'steelmaking/dinas' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  function glowBox(m, name, f, t) {
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', hide: [], face });
  }
  const o = { split: '' };

  // ------------------------------------------------------------------ coke_oven cell
  // The chamber is 2.7 m clear: sole 1 unit in the bottom cell, roof from y 12 in the top cell.
  function ovenBody(m, level) {
    const y1 = level === 'top' ? 15 : 16;
    m.box('wall_w', [0, 0, 0], [S[0], y1, 16], 'dinas');
    m.box('wall_e', [S[1], 0, 0], [16, y1, 16], 'dinas');
    const c0 = level === 'bottom' ? 1 : 0, c1 = level === 'top' ? 12 : 16;
    if (level === 'bottom') m.box('sole', [S[0], 0, 0], [S[1], 1, 16], 'dinas', o);
    m.box('charge', [S[0], c0, 0.02], [S[1], c1, 15.98], 'dark', { split: '', hide: ['east', 'west'] });
    if (level === 'top') {
      m.box('roof', [S[0], 12, 0], [S[1], 15, 16], 'dinas', o);
      m.box('deck', [0, 15, 0], [16, 16, 16], 'ashlar', { hide: ['down'] });
    }
  }
  function chargingHole(m, open) {
    m.ringY('hole_frame', LID, 3.4, 0.7, 15.8, 16.2, 'ci');
    if (open) m.cylY('hole', LID, 2.7, 15.9, 16.02, 'soot', o);
    else chargeLid(m);
  }
  function chargeLid(m) {
    m.cylY('lid', LID, 2.7, 16, 16.5, 'ci', o);
    m.ringY('lid_ring', [LID[0], LID[1]], 1.4, 0.3, 16.5, 17.1, 'bright');
  }
  // Door frame on the north face: jambs, lintel (top cell), sill (bottom cell).
  function doorFrame(m, level) {
    const y0 = 0, y1 = level === 'top' ? 13.4 : 16;
    m.box('jamb_w', [3, y0, -0.8], [S[0], y1, 0], 'ci', o);
    m.box('jamb_e', [S[1], y0, -0.8], [13, y1, 0], 'ci', o);
    if (level === 'top') m.box('lintel', [S[0], 12, -0.8], [S[1], 13.4, 0], 'ci', o);
    if (level === 'bottom') m.box('sill', [S[0], 0, -0.8], [S[1], 1, 0], 'ci', o);
    // latch hooks on the jambs at the latch bar height
    for (const x of [2.6, 12.8]) m.box(`hook_${x}`, [x, 7.2, -2.8], [x + 0.6, 9.2, -0.8], 'ci', o);
  }
  // One cell's slice of the door (dy = 0, 16, 32 for bottom, middle, top), in that cell's frame.
  function doorSlice(m, level) {
    const y0 = level === 'bottom' ? 1 : 0, y1 = level === 'top' ? 12.2 : 16;
    m.box('door', [4, y0, -1.6], [12, y1, -0.8], 'ci', o);
    for (const x of [5, 7.6, 10.2]) m.box(`door_rib_${x}`, [x, y0, -2.2], [x + 0.8, y1, -1.6], 'ci', o);
    m.box('latch_bar', [2.6, 7.6, -2.6], [13.4, 8.8, -2.2], 'st', o);
    if (level === 'top') {
      for (const x of [5, 10.2]) {
        m.box(`lug_${x}`, [x, 12.2, -1.6], [x + 0.8, 14.2, -0.8], 'st', o);
        m.box(`lug_eye_${x}`, [x - 0.2, 13.4, -1.8], [x + 1, 14.4, -0.6], 'st', o);
      }
    }
  }
  function oven(level, kind) {          // kind: '' | 'door' | 'frame' | 'open'
    const m = mk();
    ovenBody(m, level);
    if (kind === 'door' || kind === 'frame') doorFrame(m, level);
    if (kind === 'door') doorSlice(m, level);
    if (level === 'top' && (kind === '' || kind === 'open')) chargingHole(m, kind === 'open');
    return m;
  }

  // ------------------------------------------------------------------ coke_battery_wall cell
  function wall(level, end) {
    const m = mk();
    m.box('wall', [0, 0, 0], [16, level === 'top' ? 15 : 16, 16], 'dinas');
    if (level === 'top') {
      m.box('deck', [0, 15, 0], [16, 16, 16], 'ashlar', { hide: ['down'] });
      for (const z of [4, 12]) {
        m.ringY(`insp_frame_${z}`, [8, z], 1.8, 0.4, 15.8, 16.2, 'ci');
        m.cylY(`insp_lid_${z}`, [8, z], 1.4, 16, 16.4, 'ci', o);
        m.box(`insp_knob_${z}`, [7.6, 16.4, z - 0.4], [8.4, 17, z + 0.4], 'ci', o);
      }
      // cross tie rod over the wall, from buckstay to buckstay
      m.box('tie_rod', [7.6, 19, end ? -2.4 : 0], [8.4, 19.8, 16], 'st', { split: 'z' });
    }
    if (end) {
      // buckstay: rolled H 0.3 x 0.15 m standing against the wall end
      const y0 = 0, y1 = level === 'top' ? 22.4 : 16;
      m.box('buck_back', [5.6, y0, -0.6], [10.4, y1, 0], 'st', o);
      m.box('buck_web', [7.7, y0, -1.8], [8.3, y1, -0.6], 'st', o);
      m.box('buck_front', [5.6, y0, -2.4], [10.4, y1, -1.8], 'st', o);
      if (level === 'bottom') m.box('buck_foot', [4.8, 0, -3], [11.2, 0.8, 0], 'ci', o);
      if (level === 'top') {
        m.box('spring', [6.8, 18.2, -4.4], [9.2, 20.6, -2.4], 'red', o);
        m.box('spring_nut', [7, 18.4, -5], [9, 20.4, -4.4], 'ci', o);
        m.box('buck_cap', [5.4, 22.4, -2.6], [10.6, 23, 0], 'ci', o);
      }
      if (level === 'bottom') {
        // flue sight box beside the buckstay
        m.box('sight_box', [11.4, 4, -1], [14.6, 8, 0], 'ci', o);
        m.box('sight_peep', [12.4, 5.4, -1.1], [13.6, 6.6, -1], 'soot', o);
      }
    }
    return m;
  }

  // ------------------------------------------------------------------ gas_collector_main
  // Collecting main ⌀0.6 m on the battery top (centre y 9), standpipe ⌀0.35 m up from the chamber roof under it,
  // the ammonia liquor spray on top. Side arm runs to the north face (multipart rotates it).
  const MY = 9, MR = 4.8;
  function mainCore() {
    const m = mk();
    m.prism('main_x', 'x', [MY, 8], MR, 3.2, 12.8, 'st', { split: '', ends: ['east', 'west'] });
    m.prism('main_z', 'z', [8, MY], MR, 3.2, 12.8, 'st', { split: '', ends: ['north', 'south'] });
    m.cylY('standpipe', SP, 2.8, 0, MY - 2, 'st', o);
    m.ringY('standpipe_flange', SP, 3.4, 0.8, 0, 0.8, 'ci');
    m.ringY('standpipe_band', SP, 3, 0.4, 2.6, 3.2, 'tar');
    m.box('valve_box', [4.6, MY + MR - 1, 4.6], [11.4, MY + MR + 0.6, 11.4], 'ci', o);
    m.box('spray_pipe', [7.4, MY + MR + 0.6, 7.4], [8.6, 16, 8.6], 'bright', o);
    m.ringY('spray_wheel', [8, 8], 1.6, 0.4, 15.2, 15.6, 'red');
    return m;
  }
  function mainSide() {
    const m = mk();
    m.prism('arm', 'z', [8, MY], MR, 0, 3.2, 'st', o);
    m.ring('arm_band', 'z', [8, MY], MR + 0.1, 0.4, 0.6, 1.4, 'tar');
    return m;
  }

  // ------------------------------------------------------------------ partials
  // door: the full 2.75 m door of one chamber end in the bottom end cell's frame, shift (0, -16, 0) (whole y =
  // partial y + 16). Lift straight up 51 px (3.2 m) during a push.
  function doorPartial() {
    const m = mk();
    for (const [lv, dy] of [['bottom', 0], ['middle', 16], ['top', 32]]) { const s = mk(); doorSlice(s, lv); m.merge(s, 0, dy, 0, lv + '_'); }
    return m;
  }
  function cakeGlow() {
    const m = mk();
    glowBox(m, 'cake', [S[0] + 0.2, 1, -0.06], [S[1] - 0.2, 44, -0.01]);
    return m;
  }
  function chargeLidPartial() { const m = mk(); chargeLid(m); return m; }
  function wallGlow() {
    const m = mk();
    glowBox(m, 'peep', [12.5, 5.5, -1.16], [13.5, 6.5, -1.1]);
    for (const z of [4, 12]) glowBox(m, `insp_${z}`, [6.2, 16.02, z - 1.8], [9.8, 16.06, z + 1.8]);
    return m;
  }
  const PART = {
    'coke_oven/partial/door': { draw: doorPartial, shift: [0, -16, 0], note: 'bottom end cell; whole y = partial y + 16; lift 51 px +y' },
    'coke_oven/partial/cake_glow': { draw: cakeGlow, shift: [0, -16, 0], note: 'bottom end cell; whole y = partial y + 16; while the door is off' },
    'coke_oven/partial/charge_lid': { draw: chargeLidPartial, shift: [0, 0, 0], note: 'top cell; lift 8 px and slide 6 px +x while charging' },
    'coke_battery_wall/partial/wall_glow': { draw: wallGlow, shift: [0, 0, 0], note: 'peep: bottom end cell; insp: top cells; while hot' },
  };

  function builds() {
    const b = {};
    for (const lv of ['bottom', 'middle', 'top']) {
      b[`coke_oven_${lv}`] = { model: () => oven(lv, ''), java: `coke_oven/${lv}` };
      b[`coke_oven_${lv}_door`] = { model: () => oven(lv, 'door'), java: `coke_oven/${lv}_door` };
      b[`coke_oven_${lv}_frame`] = { model: () => oven(lv, 'frame'), java: `coke_oven/${lv}_frame` };
      b[`coke_battery_wall_${lv}`] = { model: () => wall(lv, false), java: `coke_battery_wall/${lv}` };
      b[`coke_battery_wall_${lv}_end`] = { model: () => wall(lv, true), java: `coke_battery_wall/${lv}_end` };
    }
    b.coke_oven_top_open = { model: () => oven('top', 'open'), java: 'coke_oven/top_open' };
    b.gas_collector_main_core = { model: mainCore, java: 'gas_collector_main/core' };
    b.gas_collector_main_arm = { model: mainSide, java: 'gas_collector_main/side' };
    for (const [java, p] of Object.entries(PART)) {
      b[java.replace(/\//g, '_')] = { model: () => { const s = p.draw(); const [dx, dy, dz] = p.shift; return mk().merge(s, dx, dy, dz, ''); }, java };
    }
    return b;
  }

  // A small battery for looking: walls at x = 0, 2, 4, 6; chambers at x = 1, 3, 5; length 4 along z; mains on top.
  function preview() {
    const m = mk();
    const put = (src, x, y, z, rotS) => {
      if (rotS) for (const e of src.els) {       // door at the south end: mirror z about the cell centre
        const f = e.from.slice(), t = e.to.slice();
        e.from = [f[0], f[1], 16 - t[2]]; e.to = [t[0], t[1], 16 - f[2]];
        if (e.rot) e.rot = Object.assign({}, e.rot, { origin: [e.rot.origin[0], e.rot.origin[1], 16 - e.rot.origin[2]] });
        if (e.face) {
          const nf = {};
          for (const [d, v] of Object.entries(e.face)) nf[d === 'north' ? 'south' : d === 'south' ? 'north' : d] = v;
          e.face = nf;
        }
        if (e.hide) e.hide = e.hide.map((d) => d === 'north' ? 'south' : d === 'south' ? 'north' : d);
      }
      m.merge(src, x * 16, y * 16, z * 16, `c${x}_${y}_${z}_`);
    };
    const lv = ['bottom', 'middle', 'top'];
    for (let x = 0; x < 7; x++) for (let y = 0; y < 3; y++) for (let z = 0; z < 4; z++) {
      const end = z === 0 || z === 3, isOven = x % 2 === 1;
      const src = isOven ? oven(lv[y], end ? 'door' : (y === 2 && z === 1 ? 'open' : '')) : wall(lv[y], end);
      put(src, x, y, z, z === 3);
    }
    // the main along x over the chambers (the arms of the real cells, drawn as one run here)
    for (const x of [1, 3, 5]) put(mainCore(), x, 3, 2, false);
    m.prism('preview_main', 'x', [48 + MY, 40], MR, 0, 112, 'st', { split: '', ends: ['east', 'west'] });
    return m;
  }

  window.COKE = {
    builds, PART, oven, wall, mainCore, mainSide, preview: (root) => { const m = preview(); PCART.realize(root, m, 'coke_oven_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    previewModel: preview,
    saveAll: (root) => PCART.exportAll(root, 'coke/coke_oven', builds()),
  };
})();
