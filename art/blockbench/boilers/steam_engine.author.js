// Horizontal single-cylinder mill engine, hand-authored for Blockbench (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/steam_engine.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); SEN.paint('<repo>')
//   await SEN.exportAll('<repo>')        // cylinder and frame segments, moving parts as partial models
//   SEN.preview('<repo>')                // whole engine with the moving parts at crank angle 0, for looking only
//
// Dimensions follow the simulated reference engine (SteamEngineUnit.Spec.reference): bore 0.4 m, stroke 0.6 m
// (crank radius 5 units), connecting rod 1.25 m (20 units), flywheel rim radius about 1.2 m (19 units).
// Cylinder segment (controller): local z -16..32, cylinder at the north end, axis x = 8, y = 4.
// Frame segment: anchor two blocks south (local z = cylinder z - 32); crankshaft axis y = 4, z = 8, along x.
// Kinematics in SteamEngineRenderer: crank pin at angle t (y = 4 + 5 cos t, z = 8 + 5 sin t); eccentric 1.2 units
// at t + 120 deg on x 13.9..15.6; eccentric rod 17 units to the valve rod pin at y = 4, rest z = 23 (cylinder);
// crosshead rest z = 16; governor spindle x = 15, z = -4.5, arm pivots at y = 25, arms 4 units.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/steam_engine';
  const T = {};

  // Cylinder cover: machined face, a ring of studs and nuts, a raised boss. Radial, 32 px.
  T.cyl_cover = () => {
    const im = img(32, 32), r = rng(301);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([58, 60, 62], (r() - 0.5) * 6);
      if (d > 0.93) c = add(c, -20);
      else if (d > 0.88) c = add(c, 14);
      if (d < 0.42) c = add([66, 68, 70], (r() - 0.5) * 4);
      if (Math.abs(d - 0.42) < 0.035) c = [90, 92, 94];
      put(im, x, y, c);
    }
    for (let k = 0; k < 12; k++) {
      const a = k / 12 * Math.PI * 2, x = Math.floor(15.5 + 12.2 * Math.cos(a)), y = Math.floor(15.5 + 12.2 * Math.sin(a));
      put(im, x, y, [150, 152, 156]); put(im, x + 1, y, [120, 122, 126]);
      put(im, x, y + 1, [104, 106, 110]); put(im, x + 1, y + 1, [44, 45, 46]);
    }
    return im;
  };
  // Atlas (64 px): maker's plate, lubricator sight glass, oil cup lid.
  const A = { nameplate: [0, 0, 14, 7], sight: [14, 0, 2, 6] };
  T.fittings = () => {
    const im = img(64, 64), r = rng(311);
    grain(im, [44, 72, 56], 312, 3, 4, 8);
    for (let y = 0; y < 7; y++) for (let x = 0; x < 14; x++) {
      const e = ((x + 0.5 - 7) / 7) ** 2 + ((y + 0.5 - 3.5) / 3.5) ** 2;
      if (e <= 1) put(im, x, y, e > 0.62 ? (y < 3 ? [208, 172, 100] : [132, 100, 52]) : add([176, 140, 74], (r() - 0.5) * 10));
    }
    for (const [y, a, b] of [[2, 3, 10], [4, 4, 9]]) for (let x = a; x <= b; x++) if (r() < 0.5) put(im, x, y, [134, 102, 54]);
    for (let y = 0; y < 6; y++) {
      put(im, 14, y, y < 2 ? [220, 230, 232] : [120, 92, 40]);   // oil level in the sight glass
      put(im, 15, y, y < 2 ? [170, 184, 188] : [96, 72, 30]);
    }
    return im;
  };

  const MAT = {};
  const ATLAS = { eng: { tex: 'steam_engine/fittings', size: 64, rects: A } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATLAS), { particle: particle || 'material/paint_green' });

  // Local panel background, 240x206, drawn for BoilerPanels.ENGINE: a mahogany engine-room board with brass
  // beading, brass rings behind the dials and a holder for the indicator card. Keep in step with the layout.
  function guiEngine() {
    const W = 240, H = 206, im = img(W, H), r = rng(321);
    for (let y = 0; y < H; y++) {
      const plank = Math.floor(y / 26), tone = [[92, 42, 26], [100, 46, 28], [86, 40, 24], [96, 44, 27]][plank % 4];
      for (let x = 0; x < W; x++) {
        let c = add(tone, Math.sin(x * 0.11 + plank * 1.7 + Math.sin(y * 0.3) * 0.6) * 7 + (r() - 0.5) * 6);
        if (y % 26 === 25) c = add(c, -30);
        put(im, x, y, c);
      }
    }
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    const ring = (cx, cy, r0, r1) => {
      for (let y = cy - r1 - 1; y <= cy + r1 + 1; y++) for (let x = cx - r1 - 1; x <= cx + r1 + 1; x++) {
        const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
        if (d >= r0 && d <= r1) put(im, x, y, add([176, 140, 74], (y < cy ? 24 : -24) * (d - r0) / (r1 - r0) + (r() - 0.5) * 8));
        else if (d > r1 && d <= r1 + 1.5) put(im, x + 1, y + 1, [40, 18, 10]);
      }
    };
    ring(40, 56, 32, 35);
    ring(104, 48, 24, 26.5);
    // shadow of the card holder and a brass clip above it
    for (let y = 25; y < 86; y++) put(im, 232, y, [40, 18, 10]);
    for (let x = 137; x < 233; x++) put(im, x, 85, [40, 18, 10]);
    for (let y = 18; y < 25; y++) for (let x = 176; x < 192; x++) put(im, x, y, add([176, 140, 74], (y === 18 ? 30 : 0) + (r() - 0.5) * 8));
    // shelf for the controls
    for (let y = 184; y < H - 4; y++) for (let x = 4; x < W - 4; x++) put(im, x, y, add([60, 28, 16], (y === 184 ? 24 : 0) + (r() - 0.5) * 6));
    return im;
  }

  function paintGui(root) {
    writePng(guiEngine(), path.join(root, PCART.ASSETS, 'textures/gui/panel/steam_engine.png'));
    return 1;
  }

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // ------------------------------------------------------------------ geometry
  const AX = 8, AY = 4;                          // cylinder and crankshaft axis height, cylinder axis x
  const VX = 14.75;                              // valve spindle / eccentric line
  const GX = 15, GZ = -4.5, GPIVOT = 25;         // governor spindle and arm pivots

  function cylinder() {
    const m = mk();
    // stone foundation and the Tangye girder bed
    m.box('plinth', [-3, -16, -14], [20, -13, 32], 'ashlar', { hide: ['down', 'south'] });
    m.box('bed_cylinder', [2, -13, -10], [14, -1, 3], 'green', { hide: ['down'] });
    m.box('girder_web', [2, -13, 3], [4.5, 9.5, 32], 'green', { hide: ['down', 'south'] });
    m.box('girder_beam', [11.5, -13, 3], [14, -1.5, 32], 'green', { hide: ['down', 'south'] });
    m.box('guide_top', [4.5, 7.5, 6], [11.5, 9, 30], 'bright');
    m.box('guide_bottom', [4.5, -1, 6], [11.5, 0.5, 30], 'bright');
    for (const z of [6, 28.5]) m.box(`guide_bracket_${z}`, [11.5, -1.5, z], [13, 9, z + 1.5], 'green', { split: '' });
    m.decal('nameplate', [1.8, -1, 12], [2, 2.5, 19], 'br', 'west', 'eng.nameplate', { hide: ['east'] });

    // cylinder: mahogany lagging in brass bands, covers with studs, stuffing box
    m.prism('barrel', 'z', [AX, AY], 5.2, -9, 1, 'wood');
    for (const z of [-8.8, 0.2]) m.prism(`band_${z}`, 'z', [AX, AY], 5.45, z, z + 0.6, 'br');
    m.disc('cover_front', 'z', [AX, AY], 5.8, -11, -9, 'ci', 'steam_engine/cyl_cover', 'north');
    m.prism('cover_boss', 'z', [AX, AY], 2.4, -12.2, -11, 'ci', { ends: ['north'] });
    m.disc('cover_back', 'z', [AX, AY], 5.8, 1, 3, 'ci', 'steam_engine/cyl_cover', 'south');
    m.box('gland', [6.6, 2.6, 3], [9.4, 5.4, 5.4], 'br', { split: '' });
    m.box('gland_flange', [5.8, 3.4, 4.6], [10.2, 4.6, 5.2], 'ci', { split: '' });
    for (const z of [-7.8, -0.6]) {
      m.box(`drain_cock_${z}`, [7.4, -2.2, z], [8.6, -1.2, z + 1], 'br', { split: '' });
      m.box(`drain_lever_${z}`, [8.6, -2, z + 0.3], [10, -1.6, z + 0.7], 'st', { split: '' });
      m.box(`indicator_cock_${z}`, [7.5, 9.1, z], [8.5, 10.4, z + 1], 'br', { split: '' });
    }

    // slide-valve chest on the east side, valve spindle gland, valve rod guide
    m.box('valve_chest', [12.5, -0.5, -8.6], [16.5, 8.5, 0.6], 'green', { split: '' });
    m.box('chest_cover', [16.5, 0, -8], [17.2, 8, 0], 'ci', { split: '' });
    for (const y of [0.6, 7]) for (const z of [-7.4, -4.4, -1.4]) {
      m.box(`chest_nut_${y}_${z}`, [17.2, y, z], [17.6, y + 0.8, z + 0.8], 'st', { split: '' });
    }
    m.box('valve_gland', [14, 3.25, 0.6], [15.5, 4.75, 2.2], 'br', { split: '' });
    m.box('valve_guide_post', [13.9, -1.5, 16], [15.6, 3.2, 19], 'green', { split: '' });
    m.box('valve_guide', [13.9, 3.2, 16], [15.6, 4.8, 19], 'bright', { split: '' });

    // throttle valve worked by the governor, steam main from the boiler with stop valve, lubricator
    m.box('throttle', [12.5, 8.5, -9], [17, 12, -3], 'gm', { split: '' });
    m.box('steam_main', [-16, 22, -10], [16, 25, -7], 'felt', { hide: ['west'] });
    m.box('steam_drop', [13, 12, -10], [16, 22, -7], 'felt');
    for (const [n, f, t] of [['flange_w', [-14, 21.5, -10.5], [-13.4, 25.5, -6.5]], ['flange_e', [12, 21.5, -10.5], [12.6, 25.5, -6.5]],
      ['flange_drop', [12.5, 12, -10.5], [16.5, 12.6, -6.5]]]) m.box(n, f, t, 'ci', { split: '' });
    m.box('stop_valve', [4, 21.5, -10.5], [8, 25.5, -6.5], 'ci', { split: '' });
    m.box('stop_bonnet', [5, 25.5, -9.5], [7, 27, -7.5], 'ci', { split: '' });
    m.box('stop_spindle', [5.7, 27, -8.8], [6.3, 28.4, -8.2], 'bright', { split: '' });
    m.wheelY('stop_wheel', 6, 28.8, -8.5, 2.2, 'red');
    m.box('lubricator', [16, 16, -9.5], [17.6, 20, -7.5], 'br', { split: '' });
    m.decal('lubricator_glass', [17.6, 16.6, -9.1], [17.7, 19.6, -8.1], 'br', 'east', 'eng.sight', { hide: ['west'] });
    m.box('lubricator_cap', [16.3, 20, -9.2], [17.3, 20.8, -7.8], 'st', { split: '' });

    // exhaust from the chest straight up through the roof
    m.box('exhaust_branch', [16.5, 1, 0], [18, 3.5, 3], 'ci', { split: '' });
    m.box('exhaust_pipe', [18, 0.5, 0], [21, 32, 3], 'ci', { hide: ['up'] });
    for (const y of [3, 18]) m.box(`exhaust_flange_${y}`, [17.6, y, -0.4], [21.4, y + 0.6, 3.4], 'ci', { split: '' });

    // governor column on the throttle; the spinning head and arms are partial models
    m.box('gov_bracket', [13.5, 12, -6], [16.5, 13, -3], 'ci', { split: '' });
    m.box('gov_gearbox', [14, 13, -5.5], [16, 14.5, -3.5], 'gm', { split: '' });
    m.box('gov_spindle', [GX - 0.3, 14.5, GZ - 0.3], [GX + 0.3, GPIVOT + 1.6, GZ + 0.3], 'bright', { split: '' });
    m.box('gov_sleeve', [GX - 0.7, 19.5, GZ - 0.7], [GX + 0.7, 21, GZ + 0.7], 'br', { split: '' });
    m.box('gov_lever', [GX - 0.2, 20, GZ + 0.7], [GX + 0.2, 20.4, GZ + 3], 'st', { split: '' });
    return m;
  }

  function frame() {
    const m = mk();
    m.box('plinth', [-3, -16, 0], [21, -13, 30], 'ashlar', { hide: ['down', 'north'] });
    m.box('plinth_outboard', [27, -16, -2], [31.5, -13, 18], 'ashlar', { hide: ['down'] });
    m.box('girder_web', [2, -13, 0], [4.5, 9.5, 2], 'green', { hide: ['down', 'north'] });
    m.box('girder_beam', [11.5, -13, 0], [14, -1.5, 2], 'green', { hide: ['down', 'north'] });
    m.box('crank_pit', [-1.5, -13, 0], [20.5, -9, 16], 'green', { hide: ['down', 'north'] });
    const bearing = (n, x0, x1) => {
      m.box(`${n}_pedestal`, [x0, -13, 2], [x1, 2.4, 14], 'green', { hide: ['down'], split: '' });
      m.box(`${n}_housing`, [x0, 2.4, 3], [x1, 5.6, 13], 'green', { split: '' });
      m.box(`${n}_cap`, [x0 - 0.3, 5.6, 3.5], [x1 + 0.3, 7.4, 12.5], 'ci', { split: '' });
      for (const z of [4, 11]) for (const x of [x0 + 0.5, x1 - 1.5]) {
        m.box(`${n}_nut_${x}_${z}`, [x, 7.4, z], [x + 1, 8.4, z + 1], 'st', { split: '' });
      }
      m.box(`${n}_oil_cup`, [(x0 + x1) / 2 - 0.5, 7.4, 7.5], [(x0 + x1) / 2 + 0.5, 9, 8.5], 'br', { split: '' });
    };
    bearing('main_w', -1.5, 4);
    bearing('main_e', 16.5, 20.5);
    m.box('outboard_pedestal', [27.5, -13, 3], [31, 2.4, 13], 'green', { hide: ['down'], split: '' });
    m.box('outboard_housing', [27.5, 2.4, 3], [31, 5.6, 13], 'green', { split: '' });
    m.box('outboard_cap', [27.2, 5.6, 3.5], [31.3, 7.4, 12.5], 'ci', { split: '' });
    // railing on the open side of the flywheel pit
    for (const z of [-0.5, 13, 26.5]) m.box(`rail_post_${z}`, [20.6, -13, z], [21.2, -4, z + 0.6], 'st', { split: '' });
    m.box('rail', [20.6, -5, -0.5], [21.2, -4.4, 27.1], 'st', { split: '' });
    return m;
  }

  // Moving parts. Crank and flywheel turn about x at (y 4, z 8) in frame coordinates; at angle 0 the pin is on top.
  function crank() {
    const m = mk('material/bright_steel');
    m.prism('shaft', 'x', [AY, 8], 1.6, -1.5, 32, 'bright', { split: '' });
    for (const [x0, x1] of [[4.6, 6], [10, 11.4]]) {
      m.box(`web_${x0}`, [x0, 1, 5.6], [x1, 10.5, 10.4], 'st', { split: '' });
      m.box(`counterweight_${x0}`, [x0, -1.8, 4.4], [x1, 1, 11.6], 'st', { split: '' });
    }
    m.prism('pin', 'x', [AY + 5, 8], 1, 6, 10, 'bright', { split: '' });
    const a = 120 * Math.PI / 180;
    m.prism('eccentric', 'x', [AY + 1.2 * Math.cos(a), 8 + 1.2 * Math.sin(a)], 2.8, 13.9, 15.6, 'ci', { split: '', ends: ['east', 'west'] });
    return m;
  }
  function flywheel() {
    const m = mk('material/cast_iron');
    m.ring('rim', 'x', [AY, 8], 19, 3, 22, 26, 'ci');
    m.prism('hub', 'x', [AY, 8], 2.8, 21.5, 26.5, 'ci', { split: '', ends: ['east', 'west'] });
    const arm = (n, rot) => m.box(n, [23.2, AY - 16.5, 7.2], [24.8, AY + 16.5, 8.8], 'ci',
      { split: '', rot: rot ? { axis: 'x', angle: rot, origin: [8, AY, 8] } : undefined });
    arm('arm_v');
    arm('arm_d1', 45);
    arm('arm_d2', -45);
    m.box('arm_h', [23.2, AY - 0.8, 8 - 16.5], [24.8, AY + 0.8, 8 + 16.5], 'ci', { split: '' });
    return m;
  }
  // Connecting rod with the crank pin at the origin, small end 20 units along +z.
  function conRod() {
    const m = mk('material/bright_steel');
    m.box('big_end', [6.4, -2.2, -2.2], [9.6, 2.2, 2.2], 'br', { split: '' });
    for (const x of [5.8, 9.6]) m.box(`big_end_bolt_${x}`, [x, -2.6, -0.4], [x + 0.6, 2.6, 0.4], 'st', { split: '' });
    m.box('rod', [7.2, -0.9, 2.2], [8.8, 0.9, 12], 'bright', { split: '' });
    m.box('rod_taper', [7.4, -0.7, 12], [8.6, 0.7, 17.6], 'bright', { split: '' });
    m.box('small_end', [6.4, -1.3, 17.6], [9.6, 1.3, 21], 'bright', { split: '' });
    return m;
  }
  // Piston rod and crosshead in cylinder coordinates, crosshead centre at rest z = 16.
  function pistonRod() {
    const m = mk('material/bright_steel');
    m.box('rod', [7.5, AY - 0.5, -9], [8.5, AY + 0.5, 14], 'bright', { split: '' });
    m.box('crosshead', [5.5, 1.5, 14], [10.5, 6.5, 18], 'st', { split: '' });
    m.box('slipper_top', [5, 6.5, 13.5], [11, 7.5, 18.5], 'gm', { split: '' });
    m.box('slipper_bottom', [5, 0.5, 13.5], [11, 1.5, 18.5], 'gm', { split: '' });
    m.box('crosshead_pin', [5, AY - 0.5, 15.5], [11, AY + 0.5, 16.5], 'bright', { split: '' });
    return m;
  }
  // Eccentric rod with the sheave centre at the origin, valve-rod pin 17 units along +z.
  function eccRod() {
    const m = mk('material/brass');
    m.ring('strap', 'x', [0, 0], 3.4, 0.6, 13.9, 15.6, 'br');
    m.box('rod', [VX - 0.35, -0.35, 3.3], [VX + 0.35, 0.35, 16], 'bright', { split: '' });
    m.box('fork', [VX - 0.75, -0.8, 16], [VX + 0.75, 0.8, 18], 'bright', { split: '' });
    return m;
  }
  // Valve spindle in cylinder coordinates, pin at rest z = 23.
  function valveRod() {
    const m = mk('material/bright_steel');
    m.box('spindle', [VX - 0.35, AY - 0.35, 1], [VX + 0.35, AY + 0.35, 22], 'bright', { split: '' });
    m.box('rod_block', [VX - 0.75, AY - 0.8, 22], [VX + 0.75, AY + 0.8, 24], 'st', { split: '' });
    return m;
  }
  // Governor head and one arm, both about the origin (the arm hangs along -y from its pivot).
  function govHead() {
    const m = mk('material/brass');
    m.box('collar', [-0.9, -0.8, -0.9], [0.9, 0.8, 0.9], 'br', { split: '' });
    m.box('lugs', [-1.1, -0.3, -0.25], [1.1, 0.3, 0.25], 'gm', { split: '' });
    m.box('finial', [-0.4, 0.8, -0.4], [0.4, 1.6, 0.4], 'br', { split: '' });
    return m;
  }
  function govArm() {
    const m = mk('material/brass');
    m.box('arm', [-0.2, -4, -0.2], [0.2, 0, 0.2], 'bright', { split: '' });
    m.box('ball', [-0.9, -4.9, -0.9], [0.9, -3.1, 0.9], 'br', { split: '' });
    return m;
  }

  const P = 'steam_engine_cylinder/partial/';
  const BUILDS = {
    steam_engine_cylinder: { model: cylinder },
    steam_engine_cylinder_frame: { model: frame },
    steam_engine_cylinder__crank: { model: crank, java: P + 'crank' },
    steam_engine_cylinder__flywheel: { model: flywheel, java: P + 'flywheel' },
    steam_engine_cylinder__con_rod: { model: conRod, java: P + 'con_rod' },
    steam_engine_cylinder__piston_rod: { model: pistonRod, java: P + 'piston_rod' },
    steam_engine_cylinder__ecc_rod: { model: eccRod, java: P + 'ecc_rod' },
    steam_engine_cylinder__valve_rod: { model: valveRod, java: P + 'valve_rod' },
    steam_engine_cylinder__gov_head: { model: govHead, java: P + 'gov_head' },
    steam_engine_cylinder__gov_arm: { model: govArm, java: P + 'gov_arm' },
  };

  // Whole engine at crank angle 0 for inspection: frame and crank moved 32 units south, rods placed by hand.
  function preview() {
    const m = cylinder();
    m.merge(frame(), 0, 0, 32, 'frame_');
    m.merge(crank(), 0, 0, 32, 'crank_');
    m.merge(flywheel(), 0, 0, 32, 'fly_');
    const pinZ = 40, pinY = AY + 5, cross = pinZ - Math.sqrt(400 - 25);
    m.merge(pistonRod(), 0, 0, cross - 16, 'piston_');
    const rod = conRod();                         // laid flat along -z from the pin for the preview
    for (const e of rod.els) {
      const f = [e.from[0], pinY + e.from[1], pinZ - e.to[2]], t = [e.to[0], pinY + e.to[1], pinZ - e.from[2]];
      m.els.push(Object.assign({}, e, { name: 'rod_' + e.name, from: f, to: t, rot: undefined }));
    }
    m.merge(valveRod(), 0, 0, 0, 'valve_');
    for (const s of [1, -1]) {
      const a = 40 * Math.PI / 180, bx = GX + s * (0.8 + 4 * Math.sin(a)), by = GPIVOT - 4 * Math.cos(a);
      m.box(`gov_ball_${s}`, [bx - 0.9, by - 0.9, GZ - 0.9], [bx + 0.9, by + 0.9, GZ + 0.9], 'br', { split: '' });
    }
    m.merge(govHead(), GX, GPIVOT, GZ, 'gov_');
    return m;
  }

  window.SEN = {
    paint,
    paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'boilers', BUILDS),
    preview: (root) => PCART.realize(root, preview(), 'steam_engine_preview'),
  };
})();
