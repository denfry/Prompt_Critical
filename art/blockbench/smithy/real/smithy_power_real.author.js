// Powered smithy at real scale (1 block = 1 m, 16 units = 1 m), GDD 18 §18.6–18.7, machines of about 1900–1920:
//   crank_press_real        60 t open-back inclinable gap press (Bliss No. 21 class): C-frame 2.6 m, bed at 0.8 m,
//                           the ram on its gibs, the crankshaft through the head and the flywheel ⌀1.0 m at the back.
//   wire_mill_real          single-hole wire drawing bench (bull block): bench 2 m, the swift with the rod coil and the
//                           die box with its soap box at the front, the vertical capstan drum ⌀0.6 m at the back driven
//                           through the bevel gear under the bench.
//   sheet_mill_real         two-high cold sheet mill, rolls ⌀0.35 x 0.7 m in cast housings 1.4 m high with the screw-
//                           downs, the pinion box and wobbler spindles on the west, entry and exit tables with sheet.
//                           Keeps the old block's contract: Create shaft in on the west face of the controller cell.
//   heating_furnace_real    coal-fired smith's reheating furnace: brick chamber 1.6 x 1.2 m on a firebox, cast door
//                           raised by a counterweighted lever, the iron stack to 4 m.
//   hardening_furnace_real  muffle hardening furnace with the quench tank (water/oil) beside it on the east.
// After Bliss and Toledo press catalogues (1910s), Bernhard, "Der Drahtzug" (1912), Roberts, "Sheet Mill Practice"
// (1916) and Richardson, "Practical Blacksmithing" (1889).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in SPR.ids:
//   SPR.build(repo, id); SPR.save(repo, id); per partial SPR.buildPartial(repo, id, p); SPR.savePartial(repo, id, p)
//
// All: controller (0, 0, 0), front north; model px; partials unshifted unless a shift is given.
//   crank_press   footprint x 0..0, y 0..2, z 0..1. Work on the bed (8, 13, 6) from the north. Create shaft in, south
//                 face of (0, 2, 1) at (8, 40, 32), axis z. ram slides along y, down up to 3 px (stroke 0.2 m) from
//                 as drawn; flywheel (crankshaft, flywheel) turns about z, axis (x 8, y 40), saved shifted
//                 (0, -16, -16): draw it 16 px up and 16 px south.
//   wire_mill     footprint x 0..0, y 0..1, z 0..1. Rod in at the swift on the north; die at (8, 14, 10). Create shaft
//                 in, west face of (0, 0, 1) at (0, 8, 24), axis x. drum (capstan drum and wire turns) turns about y,
//                 axis (x 8, z 23).
//   sheet_mill    footprint x 0..1, y 0..1, z -1..1. Pass along z at y 16 between the rolls, sheet in from the
//                 north table, out on the south. Create shaft in, west face of (0, 0, 0) at (0, 8, 8), axis x (as the
//                 old block). roll_top turns about x, axis (y 19, z 8); roll_bottom about x, axis (y 13, z 8),
//                 opposite way.
//   heating_furnace footprint x 0..1, y 0..3, z 0..1. Work in and out through the door on the north of (0, 0, 0),
//                 sill (12, 12, 1.2). Smoke out of the stack top, up face of (1, 3, 1) at (25, 62, 25). door slides
//                 along y, up to 8 px open; glow (door slot and fire) full-bright while lit. Both saved shifted (0, 0, 2)
//                 already in place (the furnace front stands 2 px back from the cell face).
//   hardening_furnace footprint x 0..1, y 0..1, z 0..1. Door on the north of (0, 0, 0), sill (8, 12, 1.2); quench tank
//                 top at (24, 12, 10) in (1, 0, 0). door slides along y, up to 6 px; glow full-bright while lit.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    mp: { tex: 'machine_shop/paint', P: 16 }, coals: { tex: 'smithy/coals', P: 16 }, dead: { tex: 'smithy/coals_dead', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/cast_iron' });

  // ------------------------------------------------------------------ crank press
  const press = {
    static(m) {
      m.box('plinth', [0, 0, 0], [16, 1, 32], 'conc', { hide: ['down'] });
      m.box('base', [1, 1, 2], [15, 6, 30], 'mp', { split: '' });
      m.box('bed', [2, 6, 1], [14, 11, 13], 'mp', { split: '' });
      m.box('bolster', [3, 11, 2], [13, 12.4, 11], 'scr' in MAT ? 'st' : 'st', { split: '' });
      m.box('die_lower', [5, 12.4, 4], [11, 13, 9], 'bright', { split: '' });
      // C-frame: the back column and the head over the bed, ribbed sides
      m.box('column', [2, 6, 18], [14, 36, 30], 'mp');
      for (const x of [1.4, 14]) m.box(`rib_${x}`, [x, 6, 13], [x + 0.6, 34, 30], 'mp', { split: 'y' });
      m.box('throat_back', [2, 11, 13], [14, 22, 18], 'mp', { split: '' });
      m.box('head', [1.6, 30, 2], [14.4, 44, 30], 'mp');
      m.box('gibs', [3, 20, 2.6], [13, 30, 3.4], 'st', { split: '' });
      // clutch and treadle
      m.box('clutch_box', [12, 36, 28], [15, 42, 31], 'st', { split: '' });
      m.box('treadle', [4, 1, 0], [12, 1.8, 2], 'ci', { split: '' });
      m.box('treadle_rod', [12.6, 1.8, 0.6], [13.4, 36, 1.4], 'st', { split: 'y' });
      m.decal('maker_plate', [5, 38, 1.6], [11, 41, 2], 'mp', 'north', 'pl.maker');
    },
    parts: {
      ram(m) {
        m.box('ram', [4, 18, 3.4], [12, 30, 11], 'st', { split: '' });
        m.box('die_upper', [5, 16.6, 4], [11, 18, 9], 'bright', { split: '' });
        m.box('pitman', [6.6, 30, 5.6], [9.4, 32, 8.4], 'ci', { split: '' });
      },
      flywheel(m) {
        m.prism('crankshaft', 'z', [8, 40], 1.4, 4, 32, 'bright', { split: '', ends: ['north', 'south'] });
        m.disc('flywheel', 'z', [8, 40], 7.6, 30.4, 32, 'ci', 'vver/flange', 'south');
        m.prism('flywheel_rim', 'z', [8, 40], 7.8, 30.2, 32, 'ci', { split: '' });
      },
    },
    shift: { flywheel: [0, -16, -16] },
    dir: 'smithy/crank_press',
  };

  // ------------------------------------------------------------------ wire mill
  const wire = {
    static(m) {
      m.box('bench', [1, 9, 1], [15, 11, 31], 'wood', { split: 'z' });
      for (const [x, z] of [[1.6, 1.6], [12.8, 1.6], [1.6, 28.8], [12.8, 28.8]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1.6, 9, z + 1.6], 'ci', { split: '' });
      m.box('gear_case', [3, 2, 17], [13, 9, 29], 'gw', { split: '' });
      m.prism('input', 'x', [8, 24], 1.2, 0, 3, 'bright', { split: '', ends: ['west'] });
      m.prism('input_boss', 'x', [8, 24], 2, 0, 1, 'ci', { split: '', ends: ['west'] });
      // swift with the rod coil at the front, the die box and soap box
      m.cylY('swift_post', [8, 4], 0.8, 11, 16, 'ci');
      m.cylY('swift', [8, 4], 3.4, 16, 17, 'wood', { bottom: true });
      m.ringY('rod_coil', [8, 4], 3, 1, 17, 19, 'dark');
      m.box('soap_box', [5, 11, 8], [11, 14, 11], 'wood', { split: '' });
      m.box('die_box', [6, 11, 11], [10, 17, 13], 'ci', { split: '' });
      m.box('die', [7.2, 13.4, 10.8], [8.8, 15, 11], 'bright', { split: '' });
      m.box('wire_run', [7.8, 14.2, 13], [8.2, 14.6, 20], 'bright', { split: '' });
    },
    parts: {
      drum(m) {
        m.cylY('drum_spindle', [8, 23], 1, 11, 22, 'bright');
        m.cylY('drum', [8, 23], 4.8, 11, 19, 'ci', { bottom: true, cap: 'vver/flange' });
        m.ringY('wire_turns', [8, 23], 5, 0.4, 12, 17, 'bright');
        m.cylY('drum_flange', [8, 23], 5.6, 11, 12, 'ci');
      },
    },
    shift: {},
    dir: 'smithy/wire_mill',
  };

  // ------------------------------------------------------------------ sheet mill
  const sheet = {
    static(m) {
      m.box('foundation', [0, 0, -16], [32, 1, 32], 'conc', { hide: ['down'] });
      // pinion box and the input on the west, wobbler spindles to the rolls
      m.box('pinion_box', [0.6, 1, 2], [8, 22, 14], 'gw', { split: '' });
      m.prism('input_boss', 'x', [8, 8], 2.4, 0, 0.6, 'ci', { split: '', ends: ['west'] });
      for (const y of [13, 19]) m.box(`spindle_${y}`, [8, y - 1.2, 6.8], [12, y + 1.2, 9.2], 'st', { split: '' });
      // housings west and east of the rolls with screw-downs and the chocks
      for (const [n, x0, x1] of [['w', 12, 15], ['e', 27, 30]]) {
        for (const [z0, z1] of [[2, 4.6], [11.4, 14]]) m.box(`post_${n}_${z0}`, [x0, 1, z0], [x1, 26, z1], 'ci', { split: 'y' });
        m.box(`cap_${n}`, [x0, 26, 2], [x1, 28, 14], 'ci', { split: '' });
        m.box(`chock_top_${n}`, [x0, 16.6, 4.6], [x1, 22.6, 11.4], 'ci', { split: '' });
        m.box(`chock_bot_${n}`, [x0, 9.4, 4.6], [x1, 15.4, 11.4], 'ci', { split: '' });
        m.cylY(`screw_${n}`, [(x0 + x1) / 2, 8], 0.9, 22.6, 30, 'bright');
        m.box(`screw_wheel_${n}`, [(x0 + x1) / 2 - 2.4, 30, 5.6], [(x0 + x1) / 2 + 2.4, 30.6, 10.4], 'red', { split: '' });
        m.box(`sill_${n}`, [x0 - 1, 1, 1], [x1 + 1, 3, 15], 'ci', { split: '' });
      }
      // entry and exit tables with a sheet each
      for (const [n, z0, z1] of [['in', -15, 2], ['out', 14, 31]]) {
        m.box(`table_${n}`, [14, 14.6, z0], [28, 15.4, z1], 'st', { split: 'z' });
        for (const z of [z0 + 1, z1 - 2]) m.box(`table_leg_${n}_${z}`, [15, 1, z], [27, 14.6, z + 1], 'st', { split: '' });
      }
      m.box('sheet_in', [16, 15.4, -12], [26, 15.6, 0], 'bright', { split: '' });
      m.box('sheet_out', [16, 15.4, 17], [26, 15.6, 29], 'bright', { split: '' });
    },
    parts: {
      roll_top(m) { m.prism('roll_top', 'x', [19, 8], 2.8, 12, 30, 'bright', { split: '', ends: ['east', 'west'] }); },
      roll_bottom(m) { m.prism('roll_bottom', 'x', [13, 8], 2.8, 12, 30, 'bright', { split: '', ends: ['east', 'west'] }); },
    },
    shift: {},
    dir: 'smithy/sheet_mill',
  };

  // ------------------------------------------------------------------ furnaces
  function chamber(m, w, d, h, sill, stackTop) {
    // brick chamber on a stone base, iron binding, the door frame on the north
    m.box('base', [0, 0, 0], [w, 4, d], 'ashlar', { hide: ['down'] });
    m.box('chamber', [0.6, 4, 0.6], [w - 0.6, h, d - 0.6], 'brick');
    m.box('arch', [1.4, h, 1.4], [w - 1.4, h + 2, d - 1.4], 'brick', { split: '' });
    for (const x of [0.2, w - 0.8]) for (const z of [0.2, d - 0.8]) m.box(`buckstay_${x}_${z}`, [x, 4, z], [x + 0.6, h + 2, z + 0.6], 'ci', { split: '' });
    for (const y of [8, h]) m.box(`tie_n_${y}`, [0, y - 0.6, 0], [w, y, 0.4], 'ci', { split: '' });
    m.box('door_frame', [sill[0] - 6, sill[1] - 0.6, 0], [sill[0] + 6, sill[1] + 8.6, 0.6], 'ci', { split: '' });
    m.box('door_mouth', [sill[0] - 4.6, sill[1], 0.55], [sill[0] + 4.6, sill[1] + 7.4, 0.6], 'dark', { split: '' });
    m.box('ash_door', [sill[0] - 3, 1, -0.2], [sill[0] + 3, 3.6, 0.4], 'ci', { split: '' });
    if (stackTop) {
      m.box('stack_base', [w - 12, h + 2, d - 12], [w - 2, h + 4, d - 2], 'ci', { split: '' });
      m.cylY('stack', [w - 7, d - 7], 3.2, h + 4, stackTop, 'st');
      m.cylY('stack_cap', [w - 7, d - 7], 3.8, stackTop - 1, stackTop, 'st', { cap: 'vver/dark' });
    }
  }
  function door(m, sill, open) {
    m.box('door', [sill[0] - 5, sill[1] - 0.2, -0.8], [sill[0] + 5, sill[1] + 8, 0], 'ci', { split: '' });
    m.box('door_peep', [sill[0] - 0.8, sill[1] + 4, -0.9], [sill[0] + 0.8, sill[1] + 5.4, -0.8], 'dark', { split: '' });
    m.box('door_chain', [sill[0] - 0.3, sill[1] + 8, -0.6], [sill[0] + 0.3, sill[1] + 8 + open, -0.2], 'ci', { split: '' });
  }
  const heating = {
    static(m0) {
      const m = mk();
      const S = [12, 12];
      chamber(m, 32, 30, 24, S, 62);
      // counterweighted lever over the door on a post beside it
      m.box('lever_post', [1, 4, -1], [2.6, 36, 1], 'ci', { split: 'y' });
      m.box('lever', [1, 34, -1.2], [20, 35, -0.4], 'st', { split: '' });
      m.box('counterweight', [0, 30, -1.6], [3.6, 34, 0], 'ci', { split: '' });
      m.box('coal_heap', [24, 0, 0], [31, 2, 6], 'dead', { split: '' });
      m.box('tool_rack', [26, 4, 0], [31.4, 5, 1], 'wood', { split: '' });
      m0.merge(m, 0, 0, 2, '');
    },
    parts: {
      door(m) { door(m, [12, 12], 8); },
      glow(m) {
        m.box('glow_slot', [7.4, 11.6, -0.95], [16.6, 12.2, -0.85], 'coals', { split: '' });
        m.box('glow_peep', [11.4, 16.1, -0.95], [12.6, 17.3, -0.9], 'coals', { split: '' });
        m.box('glow_grate', [9, 1.2, -0.3], [15, 3.4, -0.25], 'coals', { split: '' });
      },
    },
    shift: { door: [0, 0, 2], glow: [0, 0, 2] },
    dir: 'smithy/heating_furnace',
  };
  const hardening = {
    static(m0) {
      const m = mk();
      const S = [8, 12];
      chamber(m, 16, 30, 22, S, 30);
      // quench tank in the east cell, with water, a basket and the drain
      m.box('tank', [17, 0, 1], [31, 12, 15], 'st', { split: '' });
      m.box('tank_water', [17.6, 11, 1.6], [30.4, 11.4, 14.4], 'gw', { split: '', hide: ['down'] });
      m.box('basket_bail', [23, 11.4, 7.6], [25, 16, 8.4], 'ci', { split: '' });
      m.box('tank_drain', [30.4, 1, 13], [32, 2.6, 14.6], 'ci', { split: '' });
      m.box('tongs', [18, 12, 16], [30, 12.6, 17], 'soot', { split: '' });
      m0.merge(m, 0, 0, 2, '');
    },
    parts: {
      door(m) { door(m, [8, 12], 6); },
      glow(m) { m.box('glow_slot', [3.4, 11.6, -0.95], [12.6, 12.2, -0.85], 'coals', { split: '' }); m.box('glow_peep', [7.4, 16.1, -0.95], [8.6, 17.3, -0.9], 'coals', { split: '' }); },
    },
    shift: { door: [0, 0, 2], glow: [0, 0, 2] },
    dir: 'smithy/hardening_furnace',
  };

  const MACH = { crank_press_real: press, wire_mill_real: wire, sheet_mill_real: sheet, heating_furnace_real: heating, hardening_furnace_real: hardening };
  function model(id) { const m = mk(); MACH[id].static(m); return m; }
  function partial(id, p, raw) { const s = mk(); MACH[id].parts[p](s); const [dx, dy, dz] = raw ? [0, 0, 0] : (MACH[id].shift[p] || [0, 0, 0]); return mk().merge(s, dx, dy, dz, ''); }
  window.SPR = {
    ids: Object.keys(MACH), parts: (id) => Object.keys(MACH[id].parts), model, partial, dir: (id) => MACH[id].dir,
    build: (root, id) => { const m = PCVV.cut(model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, MACH[id].dir),
    buildPartial: (root, id, p) => { const m = partial(id, p); PCART.realize(root, m, id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, id, p) => PCVV.savePartial(root, id, p, MACH[id].dir),
    preview: (root, id) => { const m = model(id); for (const p of Object.keys(MACH[id].parts)) m.merge(partial(id, p, !/furnace/.test(id)), 0, 0, 0, ''); PCART.realize(root, PCVV.cut(m), id + '_preview'); return PCVV.bounds(m); },
  };
})();
