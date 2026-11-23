// Rolling and electrical shop machines at real scale (1 block = 1 m, 16 units = 1 m):
//   heat_treatment_furnace_real  gas-fired car-bottom furnace of the 1950s: steel-cased brick chamber 3 x 2.6 m outside,
//                                4.4 m long, the lift door on the north hung from a counterweighted frame, the car on
//                                rails, two rows of burners on the gas main along the west, the flue to the stack.
//   pipe_welder_real             small HF induction (ERW) tube mill of the 1960s: strip entry guide, six forming stands,
//                                fin pass, the induction coil with its HF output transformer, squeeze-roll stand, the
//                                outside scarfing tool and the cooling trough to the exit, the HF generator cabinet on
//                                the east, the drive gearbox on the west.
//   notching_press_real          high-speed lamination notching press (Schuler type, 1950s): gap frame 2.2 m, flywheel at
//                                the back on the Create shaft, the ram over the die and the rotary index table in front
//                                with a half-notched lamination; keeps the old contract (shaft into the back of the
//                                controller).
//   winding_machine_real         stator winding lathe: bed 2 m, headstock on the west on the Create shaft (old contract),
//                                faceplate with the stator core and its end turns, the wire spool stand and tensioner on
//                                the east.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in RSR.ids:
//   RSR.build(repo, id); RSR.save(repo, id); per partial RSR.buildPartial(repo, id, p); RSR.savePartial(repo, id, p)
//
// All: controller (0, 0, 0), front north; model px; partials unshifted unless given.
//   heat_treatment_furnace footprint x -1..1, y 0..3, z 0..4. Work in and out on the car through the door on the north of
//                 (0, 0, 0); gas in, west face of (-1, 1, 2) at (-16, 24, 40); flue out, up face of (1, 3, 3) at
//                 (24, 63.6, 56). door (door leaf and its sheaves) slides up along y by up to 24 px (state open); glow
//                 (full-bright burner flames and door seal) while lit.
//   pipe_welder   footprint x -1..1, y 0..1, z 0..5, pass line along z at (x 8, y 24). Strip in, north face of (0, 1, 0)
//                 at (8, 24, 0); tube out, south face of (0, 1, 5) at (8, 24, 96); Create shaft into the drive, west face
//                 of (-1, 0, 1) at (-16, 8, 24); power in to the HF cabinet, east face of (1, 0, 2) at (32, 8, 40).
//                 forming_rolls (the six forming stands' rolls, static multipart as before), saved shifted (0, 0, -16):
//                 draw 16 px south;
//                 glow (weld vee, full-bright while welding), saved shifted (0, 0, -32): draw 32 px south.
//   notching_press footprint x 0..0, y 0..1, z -1..0. Create shaft into the back of the controller, south face of
//                 (0, 0, 0) at (8, 8, 16) (old contract). Lamination on the index table at (8, 13, -8). ram slides down
//                 along y up to 1 px per stroke; index (rotary table with the lamination) turns about y, axis (x 8,
//                 z -8); flywheel turns about z, axis (x 8, y 26).
//   winding_machine footprint x 0..1, y 0..1, z 0..0. Create shaft in, west face of (0, 0, 0) at (0, 8, 8) (old contract).
//                 faceplate (faceplate, stator core, end turns) turns about x, axis (y 18, z 8); spool turns about x,
//                 axis (y 22, z 8).
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, coals: { tex: 'smithy/coals', P: 16 },
    mp: { tex: 'machine_shop/paint', P: 16 }, cu: { tex: 'smithy/copper', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/steel' });

  // ------------------------------------------------------------------ car-bottom furnace
  const htf = {
    static(m) {
      m.box('pit_rails_w', [-6, 0, -0], [-5, 1, 72], 'bright', { split: 'z' });
      m.box('pit_rails_e', [21, 0, -0], [22, 1, 72], 'bright', { split: 'z' });
      m.box('casing', [-15, 0, 4], [31, 40, 72], 'plate');
      m.box('roof', [-15.6, 40, 3.4], [31.6, 42, 72], 'st', { split: 'z' });
      for (let z = 8; z < 72; z += 12) for (const x of [-15.8, 31]) m.box(`buckstay_${x}_${z}`, [x, 0, z], [x + 0.8, 40, z + 1.2], 'st', { split: 'y' });
      // door frame on the north and the counterweight frame over it
      m.box('door_frame', [-12, 0, 2.4], [28, 34, 4], 'brick');
      m.box('door_opening', [-6, 2, 2.3], [22, 30, 2.4], 'dark', { split: '' });
      for (const x of [-15, 29]) m.box(`gallows_${x}`, [x, 40, 0], [x + 2, 62, 2], 'st', { split: 'y' });
      m.box('gallows_beam', [-15, 60, 0], [31, 62, 2], 'st', { split: 'x' });
      for (const x of [-15.4, 29.4]) m.box(`counterweight_${x}`, [x - 0.6, 42, 2], [x + 2.6, 50, 4], 'ci', { split: '' });
      // the car standing in, its front plate and pulling eye
      m.box('car_front', [-6, 1, 1.6], [22, 6, 4], 'ci', { split: '' });
      m.box('car_eye', [6, 2, 0.4], [10, 4, 1.6], 'ci', { split: '' });
      // gas main and burners along the west, the flue and stack at the back
      m.prism('gas_main', 'z', [-16 + 1.6, 24], 1.4, 8, 68, 'yel', { split: '' });
      for (const z of [14, 30, 46, 62]) {
        m.box(`burner_${z}`, [-15.6, 22.6, z - 1.4], [-14, 25.4, z + 1.4], 'ci', { split: '' });
        m.box(`burner_valve_${z}`, [-15.8, 26, z - 0.6], [-14.6, 28, z + 0.6], 'red', { split: '' });
      }
      m.box('gas_in', [-16, 23.2, 38.6], [-14.2, 24.8, 41.4], 'yel', { split: '' });
      m.box('flue_box', [18, 42, 50], [30, 48, 62], 'st', { split: '' });
      m.cylY('stack', [24, 56], 3.6, 48, 63.6, 'st', { cap: 'vver/dark' });
      m.decal('plate', [24, 20, 2.2], [28, 24, 2.4], 'st', 'north', 'pl.maker');
    },
    parts: {
      door(m) {
        m.box('door', [-7, 1.6, 0.6], [23, 31, 2.3], 'brick', { split: '' });
        m.box('door_frame_steel', [-7.6, 1, 0.2], [23.6, 31.6, 0.6], 'st', { split: '' });
        for (const x of [-4, 20]) m.box(`door_chain_${x}`, [x, 31.6, 1], [x + 0.6, 32, 1.6], 'ci', { split: '' });
      },
      glow(m) {
        for (const z of [14, 30]) m.box(`glow_burner_${z}`, [-14, 23.2, z - 0.8], [-13.6, 24.8, z + 0.8], 'coals', { split: '' });
        m.box('glow_seal', [-6, 30, 2.2], [22, 30.6, 2.3], 'coals', { split: '' });
      },
    },
    shift: {},
    dir: 'rolling/heat_treatment_furnace',
  };

  // ------------------------------------------------------------------ HF tube mill
  const PL = 24;
  const welder = {
    static(m) {
      m.box('bed', [-6, 0, 0], [22, 10, 96], 'mp', { split: 'z' });
      m.box('bed_top', [-6.6, 10, 0], [22.6, 11, 96], 'st', { split: 'z' });
      m.box('entry_guide', [2, 11, PL - 13], [14, 21, 4], 'st', { split: '' });
      // six forming stands (housings only; the rolls are the partial), fin pass, squeeze stand
      for (let i = 0; i < 6; i++) {
        const z = 8 + i * 6;
        for (const x of [0, 13]) m.box(`form_housing_${i}_${x}`, [x, 11, z], [x + 3, 32, z + 3], 'mp', { split: '' });
        m.box(`form_cap_${i}`, [0, 30, z], [16, 32, z + 3], 'mp', { split: '' });
      }
      for (const [n, z] of [['fin', 46], ['squeeze', 62]]) {
        for (const x of [-2, 15]) m.box(`${n}_housing_${x}`, [x, 11, z], [x + 3, 32, z + 4], 'ci', { split: '' });
        m.box(`${n}_cap`, [-2, 30, z], [18, 32, z + 4], 'ci', { split: '' });
        for (const [nn, x] of [['w', 4], ['e', 12]]) m.prism(`${n}_roll_${nn}`, 'z', [x, PL], 3.6, z + 0.6, z + 3.4, 'bright', { split: '', ends: ['north', 'south'] });
      }
      // induction coil before the squeeze stand, its output transformer and the HF cabinet on the east
      m.ring('coil', 'z', [8, PL], 2.6, 1, 55, 60, 'cu');
      m.box('output_tx', [14, 14, 52], [24, 24, 62], 'mp', { split: '' });
      m.box('hf_leads', [10, 22, 56], [14, 23, 58], 'cu', { split: '' });
      m.box('hf_cabinet', [22, 0, 32], [32, 30, 48], 'mp', { split: 'y' });
      m.decal('hf_sign', [31.9, 20, 37], [32, 26, 43], 'mp', 'east', 'pl.sign');
      // scarfing tool and the cooling trough, the tube running out
      m.box('scarf_post', [6, 30, 68], [10, 32, 72], 'ci', { split: '' });
      m.box('scarf_tool', [7.4, PL + 2, 69], [8.6, 30, 70], 'bright', { split: '' });
      m.box('trough', [2, 14, 74], [14, 22, 94], 'st', { split: '' });
      m.box('trough_water', [2.6, 20.6, 74.6], [13.4, 21, 93.4], 'gw', { split: '', hide: ['down'] });
      m.prism('tube', 'z', [8, PL], 2, 55, 96, 'bright', { split: '', ends: ['south'] });
      m.box('strip', [3, PL - 0.4, 0], [13, PL, 8], 'bright', { split: '' });
      // drive gearbox on the west and the line shaft along the stands
      m.box('gearbox', [-16, 1, 16], [-6, 16, 32], 'gw', { split: '' });
      m.prism('input_boss', 'x', [8, 24], 2.2, -16, -15, 'ci', { split: '', ends: ['west'] });
      m.box('line_shaft', [-6, 14, 8], [-4, 16, 64], 'bright', { split: 'z' });
    },
    parts: {
      forming_rolls(m) {
        for (let i = 0; i < 6; i++) {
          const z = 8 + i * 6, w = 5.4 - i * 0.6;
          m.prism(`top_${i}`, 'x', [PL + 3.4, z + 1.5], 2.6, 3, 13, 'bright', { split: '', ends: ['east', 'west'] });
          m.prism(`bottom_${i}`, 'x', [PL - 3.4, z + 1.5], 2.6, 8 - w, 8 + w, 'bright', { split: '', ends: ['east', 'west'] });
        }
      },
      glow(m) { m.box('glow_vee', [7.4, PL + 1.6, 58], [8.6, PL + 2.2, 63], 'coals', { split: '' }); },
    },
    shift: { forming_rolls: [0, 0, -16], glow: [0, 0, -32] },
    dir: 'rolling/pipe_welder',
  };

  // ------------------------------------------------------------------ notching press
  const notch = {
    static(m) {
      m.box('base', [1, 0, -15], [15, 6, 15], 'mp', { split: '' });
      m.box('column', [2, 6, 2], [14, 30, 15], 'mp', { split: 'y' });
      m.box('head', [2, 24, -12], [14, 32, 15], 'mp', { split: '' });
      m.box('bolster', [3, 6, -14], [13, 10, -2], 'mp', { split: '' });
      m.box('die', [5, 10, -11], [11, 11, -5], 'bright', { split: '' });
      m.box('coupling', [6, 6, 15], [10, 10, 16], 'st', { split: '' });
      m.box('index_drive', [11, 6, -5], [15, 10, -1], 'gw', { split: '' });
      m.box('stack_post', [1.4, 6, -15], [2.4, 16, -14], 'st', { split: '' });
      m.box('lamination_stack', [0.4, 6, -12], [4, 9, -8.4], 'bright', { split: '' });
      m.decal('maker_plate', [5, 26, -12.4], [11, 29, -12], 'mp', 'north', 'pl.maker');
    },
    parts: {
      ram(m) { m.box('ram', [4.6, 14, -10.6], [11.4, 24, -5.4], 'st', { split: '' }); m.box('punch', [7, 12.6, -9], [9, 14, -7], 'bright', { split: '' }); },
      index(m) { m.cylY('index_table', [8, -8], 5, 11, 12, 'ci', { bottom: true, cap: 'vver/flange' }); m.cylY('lamination', [8, -8], 4, 12, 12.4, 'bright', { cap: 'vver/flange' }); },
      flywheel(m) { m.disc('flywheel', 'z', [8, 26], 5.6, 13, 15, 'ci', 'vver/flange', 'south'); m.prism('fly_rim', 'z', [8, 26], 5.8, 12.8, 15.2, 'ci', { split: '' }); },
    },
    shift: {},
    dir: 'smithy/notching_press',
  };

  // ------------------------------------------------------------------ winding machine
  const wind = {
    static(m) {
      m.box('bed', [0.6, 0, 3], [31.4, 10, 13], 'mp', { split: 'x' });
      m.box('bed_ways', [0.6, 10, 4], [31.4, 10.6, 12], 'st', { split: 'x' });
      m.box('headstock', [0.6, 10.6, 2.6], [8, 24, 13.4], 'mp', { split: '' });
      m.box('coupling', [0, 6, 6], [1, 10, 10], 'st', { split: '' });
      m.box('pedal', [8, 0, 0.6], [12, 0.8, 3], 'ci', { split: '' });
      // spool stand and tensioner on the east
      m.box('spool_stand', [26, 10.6, 5], [30, 18, 11], 'mp', { split: '' });
      m.box('tensioner', [21, 18, 7], [24, 26, 9], 'st', { split: '' });
      m.box('wire', [12, 21.6, 7.8], [24, 22, 8.2], 'cu', { split: '' });
      m.box('counter', [3, 24, 5], [6, 27, 9], 'br', { split: '' });
    },
    parts: {
      faceplate(m) {
        m.disc('faceplate', 'x', [18, 8], 6, 8, 9.4, 'ci', 'vver/flange', 'east');
        m.prism('stator_core', 'x', [18, 8], 5.4, 9.4, 17, 'bright', { split: '' });
        m.ring('end_turns_w', 'x', [18, 8], 4.6, 1.6, 9.4, 11, 'cu');
        m.ring('end_turns_e', 'x', [18, 8], 4.6, 1.6, 15.4, 17, 'cu');
      },
      spool(m) { m.prism('spool', 'x', [22, 8], 3, 26.6, 29.4, 'cu', { split: '', ends: ['east', 'west'] }); m.prism('spool_axle', 'x', [22, 8], 0.6, 25.6, 30.4, 'bright', { split: '' }); },
    },
    shift: {},
    dir: 'smithy/winding_machine',
  };

  const MACH = { heat_treatment_furnace_real: htf, pipe_welder_real: welder, notching_press_real: notch, winding_machine_real: wind };
  function model(id) { const m = mk(); MACH[id].static(m); return m; }
  function partial(id, p, raw) { const s = mk(); MACH[id].parts[p](s); const [dx, dy, dz] = raw ? [0, 0, 0] : (MACH[id].shift[p] || [0, 0, 0]); return mk().merge(s, dx, dy, dz, ''); }
  window.RSR = {
    ids: Object.keys(MACH), parts: (id) => Object.keys(MACH[id].parts), model, partial, dir: (id) => MACH[id].dir,
    build: (root, id) => { const m = PCVV.cut(model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, MACH[id].dir),
    buildPartial: (root, id, p) => { const m = partial(id, p); PCART.realize(root, m, id + '_' + p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePartial: (root, id, p) => PCVV.savePartial(root, id, p, MACH[id].dir),
    preview: (root, id) => { const m = model(id); for (const p of Object.keys(MACH[id].parts)) m.merge(partial(id, p, true), 0, 0, 0, ''); PCART.realize(root, PCVV.cut(m), id + '_preview'); return PCVV.bounds(m); },
  };
})();
