// Rotary table at real scale (1 block = 1 m, 16 px = 1 m) of the 1950s-60s Soviet R-560 class (560 mm opening,
// ~1 m turntable, bevel-gear drive): the cast-steel housing set flush into the rig floor, the turntable with the
// master bushing, the pinion shaft housing running south to the input coupling, the floor plates around it.
// The renderer adds the turning parts and the hoisted string through the derrick above.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then
//   ROT.build(repo); /* wait */ ROT.save(repo)
//   ROT.buildPartial(repo, p); ROT.savePartial(repo, p)   for turntable, kelly_bushing, kelly, swivel, traveling_block,
//                                                          drill_stand
//   ROT.preview(repo)
//
// Controller block = (0, 0, 0), front north. Footprint x -1..1, y 0, z 0..1. Well axis vertical through (8, y, 8),
// table top at y 12. Connections (px): Create shaft in on the south face of (0, 0, 1) at (8, 6, 32) (pinion shaft
// along z); the string goes down the well below (0, 0, 0) and up into the derrick above it.
// Partials (rotary_table_real/partial/*), all about the well axis (8, 8):
// - turntable: table and master bushing; turns about y at (8, 8) with the shaft (ratio set by the renderer).
// - kelly_bushing: square drive bushing; turns with the table, rides on the kelly: y 0..5 as stored, seated on the
//   table at y 12 (draw at +12 y).
// - kelly: one metre of square kelly (2.6 px across, y 0..16), repeated up the derrick; turns with the table.
// - swivel: swivel with bail and gooseneck, y 0..24 (the kelly hangs from y 0); does not turn.
// - traveling_block: hook and five-sheave traveling block, y 0..40 (the hook eye at y 1); slides along y; stored -16 y,
//   draw at +16 y.
// - drill_stand: one metre of drill pipe 127 mm with a tool joint at the bottom, y 0..16, repeated for a stand.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, { frame: { tex: 'plant/structure', P: 16 } });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/cast_iron' });
  const AX = [8, 8], TOP = 12;

  function floor(m) {
    // skid frame and floor plates around the housing, flush with the table top
    m.box('skid_w', [-16, 0, 1], [-12, 4, 31], 'frame', { split: 'z' });
    m.box('skid_e', [28, 0, 1], [32, 4, 31], 'frame', { split: 'z' });
    m.box('beam_n', [-12, 0, 0], [28, 3, 2], 'frame', { split: 'x' });
    m.box('beam_s', [-12, 0, 30], [28, 3, 32], 'frame', { split: 'x' });
    m.box('plate_w', [-16, 11, 0], [-3, 12, 32], 'chequer', { split: 'xz' });
    m.box('plate_e', [19, 11, 0], [32, 12, 32], 'chequer', { split: 'xz' });
    m.box('plate_s', [-3, 11, 20], [19, 12, 32], 'chequer', { split: 'xz', hide: [] });
    for (const x of [-12, 24]) m.box(`post_${x}`, [x, 4, 12], [x + 4, 11, 16], 'frame', { split: '' });
    // hatch over the pinion housing in the south plate
    m.box('hatch', [3, 12, 21], [13, 12.3, 29], 'st', { split: '' });
    m.box('hatch_handle', [7, 12.3, 27], [9, 12.8, 27.6], 'st', { split: '' });
  }

  function housing(m) {
    // cast housing: square body with the turntable well, flanged foot, oil sump drain
    m.box('foot', [-3, 0, 0], [19, 2, 20], 'ci', { split: '' });
    m.box('body_w', [-3, 2, 0], [0, 11, 20], 'ci', { split: '' });
    m.box('body_e', [16, 2, 0], [19, 11, 20], 'ci', { split: '' });
    m.box('body_n', [0, 2, 0], [16, 11, 1], 'ci', { split: '' });
    m.box('body_s', [0, 2, 16], [16, 11, 20], 'ci', { split: '' });
    m.box('body_base', [0, 2, 1], [16, 3, 16], 'ci', { split: '' });
    m.box('rim', [-3, 11, 0], [19, 12, 1], 'ci', { split: '' });
    m.box('rim_s', [-3, 11, 16], [19, 12, 20], 'ci', { split: '' });
    m.box('rim_w', [-3, 11, 1], [-1, 12, 16], 'ci', { split: '' });
    m.box('rim_e', [17, 11, 1], [19, 12, 16], 'ci', { split: '' });
    m.ringY('table_seat', AX, 8, 1.2, 8.4, 9, 'dark');
    m.box('drain', [17, 3, 17], [19.6, 4.4, 18.4], 'oil', { split: '' });
    m.box('lock_lever', [-3.6, 8, 4], [-3, 9, 10], 'red', { split: '' });
    m.decal('plate', [19, 4, 6], [19.1, 7.6, 12], 'pnt', 'east', 'pl.maker');
  }

  function drive(m) {
    // pinion shaft housing to the south, bearing cap and the input coupling at the south face
    m.box('pinion_case', [2, 2, 20], [14, 10, 27], 'ci', { split: '' });
    m.prism('pinion_bearing', 'z', [8, 6], 4, 27, 29, 'ci', { split: '', ends: ['south'] });
    m.prism('input_shaft', 'z', [8, 6], 1.6, 29, 30.9, 'bright', { split: '' });
    m.disc('coupling', 'z', [8, 6], 3.6, 30.9, 31.9, 'ci', 'vver/flange', 'south');
    m.box('oil_filler', [10, 10, 22], [12, 11, 24], 'oil', { split: '' });
  }

  // ------------------------------------------------------------------ partials
  function turntable(m) {
    m.cylY('table', AX, 8, 9, 12, 'st', { cap: 'vver/flange' });
    // master bushing: two halves with the square drive recess and the 560 mm bore
    m.box('mb_w', [2, 12, 2], [6.6, 12.4, 14], 'bright', { split: '' });
    m.box('mb_e', [9.4, 12, 2], [14, 12.4, 14], 'bright', { split: '' });
    m.box('mb_n', [6.6, 12, 2], [9.4, 12.4, 6.6], 'bright', { split: '' });
    m.box('mb_s', [6.6, 12, 9.4], [9.4, 12.4, 14], 'bright', { split: '' });
    for (const [x, z] of [[3, 3], [12, 3], [3, 12], [12, 12]]) m.box(`pin_hole_${x}_${z}`, [x, 12.4, z], [x + 1, 12.45, z + 1], 'dark', { split: '' });
  }
  function kellyBushing(m) {
    m.box('kb_body', [3, 1, 3], [13, 5, 13], 'red', { split: '' });
    for (const [x, z] of [[3, 3], [12, 3], [3, 12], [12, 12]]) m.box(`kb_pin_${x}_${z}`, [x, 0, z], [x + 1, 1, z + 1], 'st', { split: '' });
    m.box('kb_rollers', [5.6, 5, 5.6], [10.4, 5.6, 10.4], 'ci', { split: '' });
  }
  function kelly(m) {
    m.box('kelly', [6.7, 0, 6.7], [9.3, 16, 9.3], 'bright', { split: 'y' });
  }
  function swivel(m) {
    m.cylY('sub', AX, 1.8, 0, 3, 'st');
    m.cylY('body', AX, 4, 3, 13, 'pnt');
    m.cylY('cap', AX, 3, 13, 15, 'ci');
    // bail up to the hook
    m.box('bail_w', [2.6, 10, 7.4], [3.4, 24, 8.6], 'st', { split: '' });
    m.box('bail_e', [12.6, 10, 7.4], [13.4, 24, 8.6], 'st', { split: '' });
    m.box('bail_top', [2.6, 23, 7.4], [13.4, 24, 8.6], 'st', { split: '' });
    // gooseneck with the rotary hose union
    m.box('gooseneck_up', [7, 15, 7], [9, 19, 9], 'gw', { split: '' });
    m.box('gooseneck_arc', [7, 19, 7], [9, 21, 14], 'gw', { split: '' });
    m.box('hose_union', [6.6, 15, 13], [9.4, 20, 15.6], 'ci', { split: '' });
  }
  function travelingBlock(m) {
    // hook with the eye at y 1, shank, spring housing, then the block with its five sheaves on a shaft along x
    m.box('hook_tip', [4, 0, 7], [6, 4, 9], 'st', { split: '' });
    m.box('hook_bowl', [4, 0, 7], [12, 2, 9], 'st', { split: '' });
    m.box('hook_back', [10, 0, 7], [12, 8, 9], 'st', { split: '' });
    m.cylY('hook_shank', AX, 1.4, 8, 11, 'bright');
    m.cylY('hook_spring', AX, 3.6, 11, 18, 'yel');
    m.box('clevis', [5, 18, 6], [11, 20, 10], 'ci', { split: '' });
    m.box('block_cheek_n', [0, 20, 4], [16, 38, 5], 'yel', { split: '' });
    m.box('block_cheek_s', [0, 20, 11], [16, 38, 12], 'yel', { split: '' });
    for (let i = 0; i < 5; i++) {
      const z = 5.2 + i * 1.2;
      m.prism(`sheave_${i}`, 'z', [8, 30], 7, z, z + 1, 'st', { split: '' });
    }
    m.prism('sheave_shaft', 'z', [8, 30], 1.2, 3.6, 12.4, 'bright', { split: '', ends: ['north', 'south'] });
    m.box('guard_top', [0.6, 38, 4], [15.4, 40, 12], 'yel', { split: '' });
    m.box('guard_band', [0, 20, 5], [1, 30, 11], 'yel', { split: '' });
    m.box('guard_band_e', [15, 20, 5], [16, 30, 11], 'yel', { split: '' });
  }
  function drillStand(m) {
    m.cylY('pipe', AX, 1, 2.6, 16, 'dark');
    m.cylY('tool_joint', AX, 1.4, 0, 2.6, 'st');
  }

  const PART = {
    turntable: { draw: turntable, shift: [0, 0, 0], note: 'turns about y at (8, 8)' },
    kelly_bushing: { draw: kellyBushing, shift: [0, 0, 0], note: 'turns with the table; seated: draw at +12 y' },
    kelly: { draw: kelly, shift: [0, 0, 0], note: 'one metre, repeated up the derrick' },
    swivel: { draw: swivel, shift: [0, 0, 0], note: 'hangs from the hook, kelly below y 0' },
    traveling_block: { draw: travelingBlock, shift: [0, -16, 0], note: 'slides along y; stored -16 y (Java range), draw at +16 y' },
    drill_stand: { draw: drillStand, shift: [0, 0, 0], note: 'one metre of pipe, repeated' },
  };
  function model() { const m = mk(); floor(m); housing(m); drive(m); return m; }
  function partial(name) { const src = mk(); PART[name].draw(src); const [dx, dy, dz] = PART[name].shift; return mk().merge(src, dx, dy, dz, ''); }
  const ID = 'rotary_table_real';
  window.ROT = {
    ID, model, partial, PART,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'drilling'),
    buildPartial: (root, name) => { const m = partial(name); PCART.realize(root, m, ID + '_' + name); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, name) => PCVV.savePartial(root, ID, name, 'drilling'),
    // the table with the bushing seated, the kelly, swivel and block hoisted above it
    preview: (root) => {
      const m = model(), add = (n, dy) => { const s = mk(); PART[n].draw(s); m.merge(s, 0, dy, 0, n + dy + '_'); };
      add('turntable', 0); add('kelly_bushing', 12); for (let y = 12; y < 60; y += 16) add('kelly', y); add('swivel', 60); add('traveling_block', 84);
      PCVV.cut(m); PCART.realize(root, m, ID + '_preview'); return { elements: m.els.length, bounds: PCVV.bounds(m) };
    },
  };
})();
