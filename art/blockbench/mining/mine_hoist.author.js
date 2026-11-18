// Single-drum mine winder, hand-authored for Blockbench (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/mine_hoist.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); HST.paint('<repo>'); HST.paintGui('<repo>')
//   await HST.exportAll('<repo>')
//
// 3x2x3 blocks, model x -16..32, y -16..16, z -16..32, the driver's side to the north. The drum turns about x
// on the line y 0, z 8 (HoistRenderer pivots there); the Create shaft comes in at the east bearing, x 32. The
// drum is 28 units across the barrel (1.75 m; the simulated Ts-2.5 drum is 2.5 m, which does not fit two
// blocks of height), with bright brake paths at both ends gripped by timber-lined post brakes front and back.
// The posts stand on a cross shaft driven by a weight-loaded brake engine: the weight sets the brake, the
// cylinder lifts it off. On the driver's side: the vertical depth indicator with its travelling cursor (a
// partial moved by the renderer from the cage depth), the signal hammer and bell, and the driver's stand with
// the controller and brake levers and the speed indicator.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, rect, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/mine_hoist';
  const T = {};
  const AY = 0, AZ = 8;          // drum axis
  const RD = 14, RB = 15;        // barrel and brake-path radius
  const X0 = -10, X1 = 26;       // barrel ends; brake paths -12..-10 and 26..28
  const SCALE = [-10, 12];       // depth indicator scale, y bottom..top (the cursor travels down from the top)

  // Turned drum barrel with the rope grooves round it (constant x = constant u), oiled steel.
  T.drum = () => {
    const im = img(32, 32), r = rng(701);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([112, 114, 116], (r() - 0.5) * 8 + (x % 2 === 0 ? 10 : -12));
      if (r() < 0.02) c = mix(c, [120, 70, 40], 0.5);
      put(im, x, y, c);
    }
    return im;
  };
  // Rope in turns on the drum: one turn per 2 px, the strands laid diagonally, dull galvanised and greased.
  T.rope = () => {
    const im = img(32, 32), r = rng(702);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const edge = x % 2 === 1, lay = (y + (x >> 1) * 2) % 4;
      let c = edge ? add([70, 66, 58], (r() - 0.5) * 6) : add([156, 152, 142], [10, 2, -8, 2][lay] + (r() - 0.5) * 8);
      c = mix(c, [80, 64, 40], 0.18);
      put(im, x, y, c);
    }
    return im;
  };
  // Drum end, painted red oxide: bright edge of the brake path, a ring of bolts, the boss and key.
  T.drum_end = () => {
    const im = img(32, 32), r = rng(703);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy) / 16;
      let c = add([112, 46, 32], (r() - 0.5) * 10);
      if (d > 0.93) c = add([176, 178, 180], (r() - 0.5) * 12);
      else if (d > 0.88) c = [70, 30, 22];
      else if (d < 0.22) c = add([60, 62, 64], (r() - 0.5) * 8);
      else if (d < 0.27) c = [40, 20, 16];
      const a = Math.atan2(dy, dx);
      for (let k = 0; k < 16; k++) {
        const b = k * Math.PI / 8;
        if (Math.hypot(dx - 12.2 * Math.cos(b), dy - 12.2 * Math.sin(b)) < 1) c = [150, 152, 150];
      }
      if (Math.abs(a) < 0.1 && d < 0.2) c = [30, 30, 30];
      put(im, x, y, c);
    }
    return im;
  };
  // Speed indicator, 0..12 m/s on 270 degrees, red from the 10 m/s ceiling; the needle at rest.
  T.speed_dial = () => {
    const im = img(32, 32), r = rng(704), C = 16;
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - C, y + 0.5 - C);
      let c = add([226, 220, 202], (r() - 0.5) * 5);
      if (d > 14.6) c = [60, 62, 64];
      else if (d > 13.6) c = [30, 30, 30];
      put(im, x, y, c);
    }
    const ray = (k, n, r0, r1, col) => {
      const a = (225 - k * 270 / n) * Math.PI / 180;
      for (let s = r0; s <= r1; s += 0.35) put(im, C - 0.5 + s * Math.cos(a), C - 0.5 - s * Math.sin(a), col);
    };
    for (let k = 0; k <= 12; k++) ray(k, 12, k % 2 ? 12 : 10.8, 12.9, [40, 38, 36]);
    for (let k = 10; k <= 12; k += 0.25) ray(k, 12, 11.6, 12.9, [196, 34, 28]);
    ray(0, 12, 0, 10, [30, 28, 26]);
    rect(im, 15, 15, 2, 2, [30, 28, 26]);
    return im;
  };
  // Atlas (64 px): depth scale of the indicator (enamel, landings marked by the driver in chalk), the maker's
  // plate, and the bell.
  const A = { scale: [0, 0, 8, 44], plate: [8, 0, 20, 8], bell: [8, 8, 8, 8] };
  T.fittings = () => {
    const im = img(64, 64), r = rng(705);
    grain(im, [44, 72, 56], 706, 3, 4, 8);
    for (let y = 0; y < 44; y++) for (let x = 0; x < 8; x++) {
      let c = add([222, 216, 196], (r() - 0.5) * 6);
      if (x === 0 || x === 7) c = [60, 58, 54];
      if (x >= 1 && x <= 3 && y % 4 === 1) c = [40, 38, 36];
      if (x >= 1 && x <= 5 && y % 20 === 1) c = [40, 38, 36];
      if (x === 6 && y > 2 && y < 41) c = [150, 40, 30];   // the travel limit strip
      put(im, x, y, c);
    }
    for (const y of [12, 27]) for (let x = 2; x < 7; x++) put(im, x, y, [240, 240, 236]);   // chalk marks
    for (let y = 0; y < 8; y++) for (let x = 8; x < 28; x++) {
      let c = add([176, 140, 74], (r() - 0.5) * 10);
      if (y === 0 || x === 8) c = [208, 172, 100];
      if (y === 7 || x === 27) c = [120, 92, 46];
      if (y >= 2 && y <= 5 && x >= 11 && x <= 24 && (x * 5 + y * 3) % 4 < 2) c = [110, 82, 40];
      put(im, x, y, c);
    }
    for (let y = 8; y < 16; y++) for (let x = 8; x < 16; x++) {
      const t = (x - 8) / 7;
      put(im, x, y, add([186, 150, 80], Math.sin(t * Math.PI) * 30 - 20 + (r() - 0.5) * 8));
    }
    return im;
  };

  const MAT = {
    drum: { tex: 'mine_hoist/drum', P: 16 }, rope: { tex: 'mine_hoist/rope', P: 16 },
  };
  const ATLAS = { h: { tex: 'mine_hoist/fittings', size: 64, rects: A } };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, ATLAS), { particle: particle || 'material/paint_green' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // ------------------------------------------------------------------ frame
  // Cast-iron A pedestal with the main bearing on the drum axis, a gunmetal cap and an oil cup.
  function pedestal(m, n, x0, x1) {
    m.box(`${n}_sole`, [x0 - 0.5, -15, AZ - 7], [x1 + 0.5, -14, AZ + 7], 'green', { hide: ['down'] });
    m.box(`${n}_body`, [x0, -14, AZ - 6], [x1, -8, AZ + 6], 'green');
    m.box(`${n}_neck`, [x0, -8, AZ - 4.5], [x1, -3, AZ + 4.5], 'green');
    m.box(`${n}_housing`, [x0, -3, AZ - 4], [x1, 2.5, AZ + 4], 'green', { split: '' });
    m.box(`${n}_cap`, [x0 + 0.3, 2.5, AZ - 3.5], [x1 - 0.3, 3.6, AZ + 3.5], 'gm', { split: '' });
    for (const z of [AZ - 3, AZ + 2.4]) m.box(`${n}_capbolt_${z}`, [x0 + 1, 3.6, z], [x0 + 1.6, 4.2, z + 0.6], 'st', { split: '' });
    m.box(`${n}_oilcup`, [(x0 + x1) / 2 - 0.6, 3.6, AZ - 0.6], [(x0 + x1) / 2 + 0.6, 5.2, AZ + 0.6], 'br', { split: '' });
    for (const z of [AZ - 6.5, AZ + 5.9]) for (const x of [x0, x1 - 0.6]) m.box(`${n}_hd_${x}_${z}`, [x, -14, z], [x + 0.6, -13.4, z + 0.6], 'st', { split: '' });
  }

  // Post brake at one brake path: timber shoe in three segments round the path, the post behind it, front (s=-1)
  // or back (s=1).
  function postBrake(m, n, x0, x1, s) {
    const zc = AZ + s * (RB + 0.5);
    for (const a of [0, 22.5, -22.5]) {
      m.box(`${n}_shoe_${a}`, [x0, AY - 3, zc - 0.5], [x1, AY + 3, zc + 0.5], 'wood',
        { split: '', rot: a ? { axis: 'x', angle: a, origin: [8, AY, AZ] } : undefined });
    }
    const zp = AZ + s * (RB + 2.2);
    m.box(`${n}_post`, [x0, -14, zp - 1], [x1, 8, zp + 1], 'green', { split: '' });
    m.box(`${n}_holder`, [x0 + 0.2, -3, Math.min(zc, zp) - 0.2], [x1 - 0.2, 3, Math.max(zc, zp) + 0.2], 'green', { split: '' });
    m.box(`${n}_pivot`, [x0 - 0.5, -15, zp - 1.5], [x1 + 0.5, -12.5, zp + 1.5], 'ci', { split: '' });
  }

  function hoist() {
    const m = mk();
    // stone foundation; the drum sits in its pit between the pedestals
    m.box('found_w', [-16, -16, -16], [-12, -15, 32], 'ashlar', { hide: ['down'] });
    m.box('found_e', [28, -16, -16], [32, -15, 32], 'ashlar', { hide: ['down'] });
    m.box('found_n', [-12, -16, -16], [28, -15, -8], 'ashlar', { hide: ['down'] });
    m.box('found_s', [-12, -16, 24], [28, -15, 32], 'ashlar', { hide: ['down'] });
    m.box('pit', [-12, -16, -8], [28, -15.6, 24], 'soot', { hide: ['down'] });
    m.box('floor_plate', [-16, -15, -16], [32, -14.6, -10.5], 'chequer', { hide: ['down'] });
    pedestal(m, 'bearing_w', -16, -12.5);
    pedestal(m, 'bearing_e', 28.5, 32);
    // post brakes, front and back of both paths, on a cross shaft along the front
    postBrake(m, 'brake_wn', -12, -10, -1);
    postBrake(m, 'brake_ws', -12, -10, 1);
    postBrake(m, 'brake_en', 26, 28, -1);
    postBrake(m, 'brake_es', 26, 28, 1);
    // the front posts sit on the brake cross shaft, which the brake engine turns through a crank at its west end
    m.prism('brake_shaft', 'x', [-13.6, AZ - RB - 2.2], 0.8, -14.4, 29, 'st', { split: '' });
    m.box('brake_crank', [-14.2, -13.6, -9.6], [-13.4, -5, -8.8], 'st', { split: '' });
    // weight-loaded brake engine at the west of the stand: the weight on the lever sets the brake, the cylinder
    // under the lever lifts it off
    m.box('brake_cyl_foot', [-16, -14.6, -14.6], [-11.6, -13.4, -10.4], 'ci', { split: '' });
    m.prismY('brake_cyl', [-13.8, -12.5], 1.8, -13.4, -7.2, 'ci', { ends: ['up'] });
    m.box('brake_piston_rod', [-14.1, -7.2, -12.8], [-13.5, -5.4, -12.2], 'bright', { split: '' });
    m.box('brake_lever', [-14.3, -5.4, -16], [-13.3, -4.4, -8.6], 'st', { split: '' });
    m.box('brake_weight', [-15.6, -9.6, -16], [-12, -6.4, -14.2], 'ci', { split: '' });
    m.box('brake_weight_hook', [-14.1, -6.4, -15.4], [-13.5, -5.4, -14.8], 'st', { split: '' });

    // depth indicator: cast column with the enamel scale behind glass, the screw in a slot beside it
    m.box('ind_base', [-7, -14.6, -16], [1, -13, -11], 'green', { split: '' });
    m.box('ind_column', [-6, -13, -15.4], [0, 13, -11.5], 'green', { split: '' });
    m.decal('ind_scale', [-5, SCALE[0] - 0.5, -15.5], [-1, SCALE[1] + 0.5, -15.4], 'ci', 'north', 'h.scale', { hide: ['south'] });
    m.box('ind_screw', [-0.9, SCALE[0], -15.9], [-0.4, SCALE[1], -15.4], 'bright', { split: '' });
    m.box('ind_cap', [-6.6, 13, -16], [0.6, 14, -11], 'green', { split: '' });
    m.decal('nameplate', [-5.5, -12.5, -15.5], [-0.5, -10.5, -15.4], 'ci', 'north', 'h.plate', { hide: ['south'] });
    // signal hammer and bell on a bracket over the indicator: the onsetter's knocks come up the shaft as rings
    m.box('bell_bracket', [-3.4, 14, -14], [-2.6, 16, -13.2], 'st', { split: '' });
    m.prism('bell', 'z', [-3, 15.2], 1.4, -15.6, -14.2, 'br', { split: '', ends: ['north'] });
    m.box('bell_hammer_arm', [-1.6, 13.8, -15.4], [0.2, 14.3, -14.9], 'st', { split: '' });
    m.box('bell_hammer', [-1.8, 14.3, -15.5], [-1.1, 15.2, -14.8], 'ci', { split: '' });

    // driver's stand: desk with the controller lever (left) and the brake lever (right) on quadrants
    m.box('stand', [8, -14.6, -15], [24, -6, -11], 'green');
    m.box('stand_top', [7.6, -6, -15.4], [24.4, -5.2, -10.6], 'ci', { split: '' });
    m.plate('speed_dial', [13.5, -12, -15.2], [18.5, -7, -15], 'ci', 'north', 'mine_hoist/speed_dial', { hide: ['south'] });
    for (const [n, x, col] of [['controller', 10, 'red'], ['brake', 21, 'st']]) {
      m.box(`${n}_quadrant`, [x - 1.2, -5.2, -14.4], [x + 1.2, -4.4, -11.6], 'ci', { split: '' });
      m.box(`${n}_lever`, [x - 0.3, -4.4, -13.3], [x + 0.3, 4, -12.7], 'bright', { split: '' });
      m.box(`${n}_grip`, [x - 0.5, 4, -13.5], [x + 0.5, 5.6, -12.5], col, { split: '' });
    }
    m.box('stand_rail', [8, -2, -16], [24, -1.4, -15.4], 'bright', { split: '' });
    for (const x of [8, 23.4]) m.box(`stand_rail_post_${x}`, [x, -5.2, -16], [x + 0.6, -1.4, -15.4], 'bright', { split: '' });
    return m;
  }

  // The drum, turned by HoistRenderer about (y 0, z 8).
  function drum() {
    const m = mk('mine_hoist/drum');
    m.prism('barrel', 'x', [AY, AZ], RD, X0, X1, 'drum');
    m.prism('rope', 'x', [AY, AZ], RD + 0.6, X0 + 0.5, 9, 'rope');
    m.disc('end_w', 'x', [AY, AZ], RB, -12, X0, 'bright', 'mine_hoist/drum_end', 'west');
    m.disc('end_e', 'x', [AY, AZ], RB, X1, 28, 'bright', 'mine_hoist/drum_end', 'east');
    // inner faces of the flanges, seen as a ring over the barrel
    m.disc('flange_w', 'x', [AY, AZ], RB, X0 - 0.5, X0, 'bright', 'mine_hoist/drum_end', 'east');
    m.disc('flange_e', 'x', [AY, AZ], RB, X1, X1 + 0.5, 'bright', 'mine_hoist/drum_end', 'west');
    m.prism('shaft', 'x', [AY, AZ], 1.6, -16, 32, 'bright', { split: '' });
    for (const x of [-12.6, 28]) m.prism(`boss_${x}`, 'x', [AY, AZ], 2.6, x, x + 0.6, 'ci', { split: '', ends: ['east', 'west'] });
    return m;
  }

  // Indicator cursor at the top of its travel; HoistRenderer lowers it by depth / shaft depth over the scale.
  function cursor() {
    const m = mk('material/brass');
    m.box('cursor', [-5.6, SCALE[1] - 0.4, -15.9], [-0.2, SCALE[1] + 0.4, -15.55], 'br', { split: '' });
    m.box('cursor_tip', [-6.2, SCALE[1] - 0.2, -15.85], [-5.6, SCALE[1] + 0.2, -15.6], 'red', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ panel
  // Local panel background for HoistScreen: a dark green winding-engine board, brass beading, the slot of the
  // depth column at the left, a brass ring behind the speed dial. Keep in step with HoistScreen.
  const GUI = { W: 256, H: 224, colX: 10, colY: 26, colW: 26, colH: 180, dials: [[76, 58, 32], [144, 58, 24]], landX: 180 };
  function guiHoist() {
    const { W, H } = GUI, im = img(W, H), r = rng(711);
    grain(im, [38, 62, 48], 712, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    // the slot of the depth column: shadow inside, light edge below and right
    const { colX, colY, colW, colH } = GUI;
    for (let y = colY - 2; y < colY + colH + 2; y++) for (let x = colX - 2; x < colX + colW + 2; x++) {
      const inside = x >= colX && x < colX + colW && y >= colY && y < colY + colH;
      if (!inside) put(im, x, y, x < colX || y < colY ? [18, 30, 22] : [70, 100, 82]);
    }
    // brass rings behind the speed indicator and the wattmeter
    for (const [cx, cy, r0] of GUI.dials) {
      const r1 = r0 + 3;
      for (let y = cy - r1 - 2; y <= cy + r1 + 2; y++) for (let x = cx - r1 - 2; x <= cx + r1 + 2; x++) {
        const d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy);
        if (d >= r0 && d <= r1) put(im, x, y, add([176, 140, 74], (y < cy ? 24 : -24) * (d - r0) / 3 + (r() - 0.5) * 8));
        else if (d > r1 && d <= r1 + 1.5) put(im, x + 1, y + 1, [16, 26, 20]);
      }
    }
    // a recessed board for the landing buttons, and a shelf under the control buttons
    for (let y = 22; y < H - 14; y++) for (let x = GUI.landX - 4; x < W - 4; x++) {
      put(im, x, y, add([30, 50, 38], x === GUI.landX - 4 || y === 22 ? -12 : (r() - 0.5) * 4));
    }
    for (let y = 196; y < H - 4; y++) for (let x = 40; x < GUI.landX - 6; x++) put(im, x, y, add([28, 44, 34], (y === 196 ? 20 : 0) + (r() - 0.5) * 4));
    return im;
  }

  function paintGui(root) {
    writePng(guiHoist(), path.join(root, PCART.ASSETS, 'textures/gui/panel/mine_hoist.png'));
    return 1;
  }

  const BUILDS = {
    mine_hoist: { model: hoist },
    'mine_hoist/drum': { model: drum, java: 'mine_hoist/partial/drum' },
    'mine_hoist/cursor': { model: cursor, java: 'mine_hoist/partial/cursor' },
  };

  window.HST = {
    paint,
    paintGui,
    GUI,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) {
      return PCART.realize(root, hoist().merge(drum(), 0, 0, 0, 'drum_').merge(cursor(), 0, 0, 0, 'cur_'), 'mine_hoist_preview');
    },
  };
})();
