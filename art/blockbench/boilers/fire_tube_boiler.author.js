// Lancashire fire-tube boiler, hand-authored for Blockbench (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/fire_tube_boiler.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); FTB.paint('<repo>'); FTB.paintGui('<repo>')
//   await FTB.exportAll('<repo>')        // every variant: .bbmodel + models/block JSON
//   FTB.preview('<repo>', 'lit')         // whole boiler in one project, for looking only
//
// Front segment (controller): local z -16..32, firing front faces north at z = -14.
// Rear segment: the next three blocks, local z -16..32, chimney uptake under block (0, +2, +1).
// Points used by code: water glasses x 0..1 / 15..16, y 11..18, z -14.9 (FireTubeBoilerRenderer);
// pressure gauge centre (8, 21.5, -15.55); safety valve escape (12, 30.5, 6); fire doors y 3.5, z -15.6.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/fire_tube_boiler';

  const PLATE = [60, 63, 66];
  const T = {};

  // Boiler plate in black boiler paint, worn: mottling, pits, a little rust bloom, the odd scuff.
  T.plate = () => {
    const im = img(64, 64), r = grain(im, PLATE, 11, 3, 6, 16);
    for (let k = 0; k < 70; k++) put(im, r() * 64, r() * 64, add(get(im, 0, 0), -14));
    for (let k = 0; k < 40; k++) put(im, r() * 64, r() * 64, [94, 70, 52]);
    for (let k = 0; k < 6; k++) { const x = r() * 60, y = r() * 64; for (let i = 0; i < 4; i++) put(im, x + i, y, add(PLATE, 12)); }
    return im;
  };
  // Lap seam: heads on a 4 px pitch both ways, so the band reads the same on every cylinder face.
  T.rivets = () => {
    const im = img(32, 32), base = add(PLATE, -6);
    grain(im, base, 12, 3, 3);
    for (let j = 0; j < 8; j++) for (let i = 0; i < 8; i++) rivet(im, i * 4 + 1, j * 4 + 1, base);
    return im;
  };
  // Front end plate, radial so every box of the 16-gon shows the same pattern: flanged edge, one rivet circle.
  T.end_plate = () => {
    const im = img(64, 64), r = rng(101), base = add(PLATE, 3);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      let c = add(base, (r() - 0.5) * 6);
      if (d > 0.935 && d <= 0.965) c = add(c, 16);
      else if (d > 0.965) c = add(c, -18 - (d - 0.965) * 200);
      else if (Math.abs(d - 0.875) < 0.03) c = add(c, -5);
      put(im, x, y, c);
    }
    for (let k = 0; k < 44; k++) {
      const a = k / 44 * Math.PI * 2;
      rivet(im, Math.floor(31.5 + 28 * Math.cos(a)), Math.floor(31.5 + 28 * Math.sin(a)), base);
    }
    return im;
  };
  // Furnace mouth: flanged ring riveted to the end plate around the cast-iron firing front.
  T.flue_mouth = () => {
    const im = img(32, 32), r = rng(111);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = d < 0.74 ? [42, 43, 44] : [70, 72, 74];
      if (d >= 0.74 && d < 0.79) c = [92, 94, 96];
      if (d > 0.95) c = [46, 48, 50];
      put(im, x, y, add(c, (r() - 0.5) * 8));
    }
    for (let k = 0; k < 14; k++) {
      const a = k / 14 * Math.PI * 2;
      rivet(im, Math.floor(15.5 + 13.6 * Math.cos(a)), Math.floor(15.5 + 13.6 * Math.sin(a)), [70, 72, 74]);
    }
    return im;
  };

  // Atlas of one-off parts (64 px, 4 px per uv unit). Rectangles in px, used by the model below.
  const A = {
    door: [0, 0, 12, 9], door_wrecked: [12, 0, 12, 9], nameplate: [24, 0, 14, 7], dial: [40, 0, 10, 10],
    glass: [52, 0, 2, 14], glass_soot: [54, 0, 2, 14], mudhole: [0, 10, 12, 5], soot_door: [12, 10, 8, 6],
    back_door: [20, 10, 10, 8], slot_glow: [30, 10, 1, 5], pit: [32, 24, 12, 12], pit_glow: [44, 24, 12, 12],
  };
  function fireDoor(im, ox, oy, wrecked) {
    const r = rng(wrecked ? 131 : 121), ci = [50, 52, 54];
    for (let y = 0; y < 9; y++) for (let x = 0; x < 12; x++) {
      let c = add(ci, (r() - 0.5) * 8);
      if (x === 0 || y === 0) c = add(c, 14);
      if (x === 11 || y === 8) c = add(c, -16);
      if (x >= 2 && x <= 9 && y >= 1 && y <= 7) c = add(c, 6);          // sliding air register plate
      if (wrecked) c = mix(c, r() < 0.5 ? [28, 26, 24] : [112, 70, 44], 0.45);
      put(im, ox + x, oy + y, c);
    }
    for (const x of [3, 5, 7, 9]) for (let y = 2; y <= 6; y++) put(im, ox + x, oy + y, wrecked && r() < 0.4 ? [70, 50, 40] : [18, 16, 15]);
    for (const y of [1, 2, 6, 7]) put(im, ox, oy + y, [88, 90, 92]);  // hinge straps
    put(im, ox + 10, oy + 4, [150, 152, 156]); put(im, ox + 11, oy + 4, [120, 122, 126]); // latch keeper
  }
  T.fittings = () => {
    const im = img(64, 64), r = rng(141);
    fireDoor(im, 0, 0, false);
    fireDoor(im, 12, 0, true);
    // maker's plate: oval cast brass, raised rim, three lines of lettering
    for (let y = 0; y < 7; y++) for (let x = 0; x < 14; x++) {
      const e = ((x + 0.5 - 7) / 7) ** 2 + ((y + 0.5 - 3.5) / 3.5) ** 2;
      let c = add(PLATE, (r() - 0.5) * 6);
      if (e <= 1) c = e > 0.62 ? (y < 3 ? [208, 172, 100] : [132, 100, 52]) : add([176, 140, 74], (r() - 0.5) * 10);
      put(im, 24 + x, y, c);
    }
    for (const [y, a, b] of [[2, 4, 9], [4, 3, 10]]) for (let x = a; x <= b; x++) if (r() < 0.5) put(im, 24 + x, y, [134, 102, 54]);
    // Bourdon gauge dial, 0..1.6 MPa over 270 degrees, red mark at the 1.2 MPa working pressure
    for (let y = 0; y < 10; y++) for (let x = 0; x < 10; x++) {
      const d = Math.hypot(x + 0.5 - 5, y + 0.5 - 5);
      put(im, 40 + x, y, d > 4.6 ? [168, 132, 70] : add([228, 222, 204], (r() - 0.5) * 6));
    }
    for (let k = 0; k <= 8; k++) {
      const a = (225 - k * 270 / 8) * Math.PI / 180;
      put(im, 40 + 4.5 + 3.6 * Math.cos(a), 4.5 - 3.6 * Math.sin(a), k === 6 ? [190, 30, 26] : [36, 34, 32]);
    }
    put(im, 44, 4, [40, 40, 40]); put(im, 45, 4, [40, 40, 40]); put(im, 44, 5, [40, 40, 40]); put(im, 45, 5, [40, 40, 40]);
    put(im, 44, 7, [150, 146, 136]); put(im, 45, 7, [150, 146, 136]);
    // gauge glass tube (the water column is drawn by the renderer over it)
    for (let y = 0; y < 14; y++) { put(im, 52, y, [206, 222, 226]); put(im, 53, y, [150, 168, 174]); }
    for (let y = 0; y < 14; y++) { put(im, 54, y, [44, 40, 36]); put(im, 55, y, [30, 28, 26]); }
    // mud-hole door: oval cast cover
    for (let y = 0; y < 5; y++) for (let x = 0; x < 12; x++) {
      const e = ((x + 0.5 - 6) / 6) ** 2 + ((y + 0.5 - 2.5) / 2.5) ** 2;
      put(im, x, 10 + y, e <= 1 ? add(e > 0.6 ? [70, 72, 74] : [52, 54, 56], (r() - 0.5) * 8) : add(PLATE, (r() - 0.5) * 6));
    }
    // flue cleaning doors in the brickwork
    const smallDoor = (ox, oy, w, h) => {
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        let c = add([50, 52, 54], (r() - 0.5) * 8);
        if (x === 0 || y === 0) c = add(c, 14);
        if (x === w - 1 || y === h - 1) c = add(c, -16);
        put(im, ox + x, oy + y, c);
      }
      put(im, ox + w - 2, oy + (h >> 1), [148, 150, 154]);
    };
    smallDoor(12, 10, 8, 6);
    smallDoor(20, 10, 10, 8);
    for (let y = 0; y < 5; y++) put(im, 30, 10 + y, mix([255, 236, 170], [255, 140, 40], Math.abs(y - 2) / 2));
    // ash pit seen through the front: underside of the fire bars, darkness, an ash heap
    const pit = (ox, oy, lit) => {
      for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) {
        let c;
        if (y < 3) c = x % 2 === 0 ? [40, 38, 36] : (lit ? mix([255, 120, 30], [255, 196, 90], r()) : [12, 10, 10]);
        else {
          const heap = 11 - 2.6 * Math.cos((x - 5.5) / 6);
          c = y > heap - 1 ? add([92, 88, 84], (r() - 0.5) * 18) : (lit ? mix([62, 26, 10], [20, 12, 10], (y - 3) / 6) : [14, 12, 12]);
          if (lit && y > heap - 1 && r() < 0.18) c = [255, 150, 50];
        }
        put(im, ox + x, oy + y, add(c, (r() - 0.5) * 4));
      }
    };
    pit(32, 24, false);
    pit(44, 24, true);
    return im;
  };

  const MAT = {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: A } }),
    { particle: 'fire_tube_boiler/plate' });

  // Local panel background, 240x202, drawn for BoilerPanels.BOILER: instruments are drawn by the game on top,
  // so this only holds the plate they are mounted on. Keep in step with the layout coordinates.
  function guiBoiler() {
    const W = 240, H = 202, im = img(W, H), r = rng(171), n = vnoise(r, W, H, 24);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      put(im, x, y, add([40, 43, 46], (n(x, y) - 0.5) * 8 + (r() - 0.5) * 5 + (1 - y / H) * 6));
    }
    const bevel = (x0, y0, w, h, base, hi, lo) => {
      for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) {
        let c = add(base, (r() - 0.5) * 6);
        if (x === x0 || y === y0) c = add(base, hi);
        if (x === x0 + w - 1 || y === y0 + h - 1) c = add(base, lo);
        put(im, x, y, c);
      }
    };
    const head = (x, y, base) => {
      put(im, x, y, add(base, 60)); put(im, x + 1, y, add(base, 30));
      put(im, x, y + 1, add(base, 18)); put(im, x + 1, y + 1, add(base, -18));
      put(im, x + 2, y + 2, add(base, -14));
    };
    // flanged frame with a rivet row, like the angle ring of the end plate
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d === 0) put(im, x, y, [18, 19, 20]);
      else if (d < 6) put(im, x, y, add([60, 64, 68], (d === 1 ? 18 : d === 5 ? -16 : 0) + (r() - 0.5) * 6));
    }
    for (let x = 8; x < W - 6; x += 10) { head(x, 2, [60, 64, 68]); head(x, H - 5, [60, 64, 68]); }
    for (let y = 12; y < H - 8; y += 10) { head(2, y, [60, 64, 68]); head(W - 5, y, [60, 64, 68]); }
    // cast-iron firing front around the peep hole, four studs
    bevel(100, 19, 88, 70, [50, 52, 54], 14, -18);
    for (const [x, y] of [[103, 22], [183, 22], [103, 84], [183, 84]]) head(x, y, [50, 52, 54]);
    // shadows the instruments throw on the plate
    for (let a = 0; a < 360; a += 1) {
      const t = a * Math.PI / 180;
      put(im, 40 + 33 * Math.cos(t) + 1, 56 + 33 * Math.sin(t) + 1, [24, 25, 26]);
      put(im, 208 + 25 * Math.cos(t) + 1, 128 + 25 * Math.sin(t) + 1, [24, 25, 26]);
    }
    for (let y = 25; y < 89; y++) put(im, 223, y, [24, 25, 26]);
    for (let x = 199; x < 223; x++) put(im, x, 89, [24, 25, 26]);
    // brass mounting strip behind the gauge glass
    bevel(79, 21, 18, 82, [138, 108, 58], 30, -34);
    // cast-iron shelf for the stoking controls
    bevel(6, 179, W - 12, H - 185, [52, 54, 56], 18, -20);
    for (let x = 6; x < W - 6; x++) put(im, x, 178, [22, 23, 24]);
    return im;
  }

  function paintGui(root) {
    writePng(guiBoiler(), path.join(root, 'pc-mod/src/main/resources/assets/promptcritical/textures/gui/panel/fire_tube_boiler.png'));
    return 1;
  }

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // ------------------------------------------------------------------ the boiler
  const SHELL_R = 15, CX = 8, CY = 8, FURNACES = [1.5, 14.5], FY = 3, GLASSES = [0.5, 15.5];

  // The front segment shows the wall ends at z0; the rear segment hides both ends (front segment / back wall).
  function setting(m, z0, z1, buckstays, sootDoorZ, showFront) {
    const ends = showFront ? ['south'] : ['south', 'north'];
    m.box('setting_w', [-15, -16, z0], [-7, 6, z1], 'brick', { hide: ['east', 'down', ...ends] });
    m.box('setting_e', [23, -16, z0], [31, 6, z1], 'brick', { hide: ['west', 'down', ...ends] });
    m.box('coping_w', [-15.5, 6, z0 - (showFront ? 0.5 : 0)], [-6.5, 7, z1], 'ci', { hide: ['down', ...ends] });
    m.box('coping_e', [22.5, 6, z0 - (showFront ? 0.5 : 0)], [31.5, 7, z1], 'ci', { hide: ['down', ...ends] });
    for (const z of buckstays) {
      m.box(`buckstay_w${z}`, [-16, -16, z], [-15, 7, z + 1.5], 'ci', { hide: ['east', 'down'] });
      m.box(`buckstay_e${z}`, [31, -16, z], [32, 7, z + 1.5], 'ci', { hide: ['west', 'down'] });
    }
    m.decal('soot_door_w', [-16, -11, sootDoorZ], [-15, -8, sootDoorZ + 4], 'ci', 'west', 'soot_door', { hide: ['east'] });
    m.decal('soot_door_e', [31, -11, sootDoorZ], [32, -8, sootDoorZ + 4], 'ci', 'east', 'soot_door', { hide: ['west'] });
  }

  function seam(m, name, z0, z1, cull) {
    m.prism(name, 'z', [CX, CY], SHELL_R + 0.3, z0, z1, 'rivets', { cullY: cull });
  }

  function front(state) {
    const m = mk(), lit = state === 'lit', wrecked = state === 'exploded';
    const shell = wrecked ? 'torn' : 'plate';
    m.prism('shell', 'z', [CX, CY], SHELL_R, -13.4, 32, shell, { cullY: 6.5 });
    seam(m, 'seam_a', -0.8, 0.8, 6.5);
    seam(m, 'seam_b', 15.2, 16.8, 6.5);
    seam(m, 'seam_c', 31.2, 32, 6.5);
    m.disc('end_plate', 'z', [CX, CY], 15.6, -14.2, -13.2, shell, wrecked ? 'material/torn' : 'fire_tube_boiler/end_plate', 'north');
    setting(m, -14, 32, [-12, 8, 26], 10, true);

    // front brickwork below the shell with the two ash-pit openings
    const W = (n, f, t, hide) => m.box(n, f, t, 'brick', { hide: (hide || []).concat(['south']) });
    W('front_pier_w', [-7, -16, -14], [-1.5, -7, -5.5], ['down']);
    W('front_pier_m', [4.5, -16, -14], [11.5, -7, -5.5], ['down']);
    W('front_pier_e', [17.5, -16, -14], [23, -7, -5.5], ['down']);
    for (const c of FURNACES) {
      W(`pit_sill_${c}`, [c - 3, -16, -14], [c + 3, -14, -5.5], ['down', 'east', 'west']);
      W(`pit_head_${c}`, [c - 3, -8, -14], [c + 3, -7, -5.5], ['up', 'east', 'west']);
      m.decal(lit ? `glow_pit_${c}` : `pit_${c}`, [c - 3, -14, -6], [c + 3, -8, -5.5], 'soot', 'north', lit ? 'pit_glow' : 'pit',
        { hide: ['south', 'east', 'west', 'up', 'down'] });
    }
    m.box('front_bearer', [-15.5, -7, -14.6], [31.5, -6, -11], 'ci', { hide: ['down', 'south'] });
    // brick closing the corners between the round end plate and the side walls, set back behind the plate
    m.box('front_fill', [-7, -6, -13.2], [23, 6, -11], 'brick', { hide: ['south', 'down', 'east', 'west'] });

    // firing fronts
    FURNACES.forEach((c, i) => {
      m.disc(`flue_mouth_${i}`, 'z', [c, FY], 5.5, -14.7, -14.2, 'ci', 'fire_tube_boiler/flue_mouth', 'north');
      m.box(`firing_front_${i}`, [c - 4, -0.5, -15.1], [c + 4, 7, -14.7], 'ci', { hide: ['south'], split: '' });
      const swing = wrecked && i === 1;
      m.decal(`fire_door_${i}`, [c - 3, 1.5, -15.6], [c + 3, 6, -15.1], 'ci', 'north', wrecked ? 'door_wrecked' : 'door',
        { hide: ['south'], rot: swing ? { axis: 'y', angle: 45, origin: [c + 3, 3.5, -15.35] } : undefined });
      if (!swing) {
        m.box(`door_hinge_${i}a`, [c + 3, 2, -15.7], [c + 3.6, 2.8, -15], 'ci', { split: '' });
        m.box(`door_hinge_${i}b`, [c + 3, 4.7, -15.7], [c + 3.6, 5.5, -15], 'ci', { split: '' });
        m.box(`door_latch_${i}`, [c - 3.2, 3.4, -16], [c - 2.2, 4.1, -15.6], 'st', { split: '' });
      }
      m.box(`dead_plate_${i}`, [c - 3.5, 0.7, -16], [c + 3.5, 1.3, -15.1], 'ci', { split: '' });
      if (lit) {
        for (const p of [3, 5, 7, 9]) {
          const x1 = c + 3 - p / 2, x0 = x1 - 0.5;
          m.decal(`glow_slot_${i}_${p}`, [x0, 2.5, -15.64], [x1, 5, -15.6], 'ci', 'north', 'slot_glow',
            { hide: ['south', 'east', 'west', 'up', 'down'] });
        }
      }
    });

    // water gauges: two, as the rules require; the water column is drawn by FireTubeBoilerRenderer
    GLASSES.forEach((g, i) => {
      m.decal(`glass_${i}`, [g - 0.5, 11, -14.9], [g + 0.5, 18, -14.5], 'br', 'north', wrecked ? 'glass_soot' : 'glass', { hide: ['south'] });
      for (const [k, y] of [['top', 18], ['bottom', 9]]) {
        m.box(`glass_cock_${k}_${i}`, [g - 1, y, -15.3], [g + 1, y + 2, -14.2], 'br', { hide: ['south'], split: '' });
        m.box(`glass_handle_${k}_${i}`, [g + 1, y + 0.7, -15.1], [g + 2.6, y + 1.3, -14.7], 'st', { split: '' });
      }
      m.box(`glass_guard_${i}a`, [g - 1, 11, -15.2], [g - 0.75, 18, -15], 'br', { split: '' });
      m.box(`glass_guard_${i}b`, [g + 0.75, 11, -15.2], [g + 1, 18, -15], 'br', { split: '' });
    });

    // pressure gauge on its syphon, maker's plate, mud-hole door
    m.box('gauge_syphon', [7.6, 13.6, -14.8], [8.4, 18.2, -14.2], 'br', { split: '' });
    m.box('gauge_cock', [7.1, 15.6, -15.1], [8.9, 16.8, -14.2], 'gm', { split: '' });
    if (!wrecked) {
      m.disc('gauge_case', 'z', [8, 21.5], 3.5, -15.4, -14.4, 'br', 'material/brass_disc', 'north');
      m.plate('gauge_dial', [5.5, 19, -15.55], [10.5, 24, -15.5], 'br', 'north', 'material/gauge_dial',
        { hide: ['south', 'east', 'west', 'up', 'down'] });
    }
    m.decal('nameplate', [4.5, 10, -14.4], [11.5, 13.5, -14.2], 'br', 'north', 'nameplate', { hide: ['south'] });
    m.decal('mudhole', [5, -5.5, -14.5], [11, -3, -14.2], 'ci', 'north', 'mudhole', { hide: ['south'] });
    m.box('mudhole_dog', [4.5, -4.6, -14.9], [11.5, -3.9, -14.5], 'st', { split: '' });
    m.box('mudhole_nut', [7.5, -4.8, -15.2], [8.5, -3.7, -14.9], 'st', { split: '' });

    if (!wrecked) {
      // manhole on the crown
      m.box('manhole_ring', [3, 22, -11.5], [13, 23.4, -2.5], 'plate', { hide: ['down'], split: '' });
      m.box('manhole_cover', [4, 23.4, -10.5], [12, 24.2, -3.5], 'ci', { hide: ['down'], split: '' });
      for (const z of [-9, -5.7]) {
        m.box(`manhole_dog_${z}`, [3.5, 24.2, z], [12.5, 25, z + 0.7], 'st', { split: '' });
        m.box(`manhole_nut_${z}`, [7.5, 25, z - 0.15], [8.5, 25.8, z + 0.85], 'st', { split: '' });
      }
      // lever safety valve: seating, gunmetal body, escape pipe, lever on a fulcrum strut, weight at the end
      m.box('sv_seating', [4.5, 22, 2], [11.5, 23.6, 10], 'ci', { hide: ['down'], split: '' });
      m.box('sv_body', [5.5, 23.6, 3.5], [10.5, 27, 8.5], 'gm', { split: '' });
      m.box('sv_spindle', [7.5, 27, 5.5], [8.5, 28, 6.5], 'st', { split: '' });
      m.box('sv_branch', [10.5, 24.6, 5.2], [12, 26, 6.8], 'gm', { split: '' });
      m.box('sv_escape', [11.2, 24.6, 5.2], [12.8, 30.5, 6.8], 'br', { split: '' });
      m.box('sv_strut', [7.4, 23.6, 2.2], [8.6, 28.8, 3.2], 'ci', { split: '' });
      m.box('sv_lever', [7.6, 28, 2], [8.4, 28.8, 22], 'st', { split: '' });
      m.box('sv_weight', [6, 24.5, 18], [10, 28, 21.5], 'ci', { split: '' });
    } else {
      m.box('rupture', [3, 22.9, -8], [13, 23.3, 26], 'soot', { hide: ['down'], split: '' });
      m.box('flap_w', [0, 22, -6], [3, 30, 24], 'torn', { rot: { axis: 'z', angle: 22.5, origin: [2, 23, 8] } });
      m.box('flap_e', [13, 22, -6], [16, 30, 24], 'torn', { rot: { axis: 'z', angle: -22.5, origin: [14, 23, 8] } });
    }
    // junction (stop) valve to the steam main
    m.box('jv_seating', [3, 22, 23], [13, 23.8, 32], 'ci', { hide: ['down', 'south'], split: '' });
    m.box('jv_body', [4.5, 23.8, 24], [11.5, 28, 31], 'ci', { split: '' });
    m.box('jv_outlet', [5, 24.2, 31], [11, 27.6, 32], 'ci', { hide: ['south'], split: '' });
    m.box('jv_bonnet', [5.5, 28, 25], [10.5, 29.5, 30], 'ci', { split: '' });
    m.box('jv_spindle', [7.6, 29.5, 27.1], [8.4, 31, 27.9], 'st', { split: '' });
    m.wheelY('jv_wheel', 8, 31.4, 27.5, 3, 'red');
    return m;
  }

  function rear(state) {
    const m = mk(), wrecked = state === 'exploded', shell = wrecked ? 'torn' : 'plate';
    m.prism('shell', 'z', [CX, CY], SHELL_R, -16, 26, shell, { cullY: 6.5 });
    seam(m, 'seam_a', -16, -15.2, 6.5);
    seam(m, 'seam_b', -0.8, 0.8, 6.5);
    seam(m, 'seam_c', 15.2, 16.8, 6.5);
    setting(m, -16, 27, [4, 20], 12);
    m.box('back_wall', [-15, -16, 27], [31, 7, 31], 'brick', { hide: ['down', 'north'] });
    m.box('back_bridge', [-8, 7, 26], [24, 24, 31], 'brick', { hide: ['down'] });
    const up = wrecked ? { rot: { axis: 'x', angle: 22.5, origin: [8, 24, 29] }, split: '' } : {};
    m.box('uptake', [0, 24, 18], [16, 31, 31], 'brick', Object.assign({ hide: ['down'] }, up));
    m.box('uptake_flange', [-0.5, 31, 17.5], [16.5, 32, 31.5], 'ci', Object.assign({}, up, { split: '' }));
    if (!wrecked) {
      m.box('damper_frame', [16, 25, 20], [16.6, 30, 29], 'ci', { hide: ['west'], split: '' });
      m.box('damper_lever', [16.6, 29, 19], [17.2, 29.6, 30], 'st', { split: '' });
      m.box('damper_weight', [16.6, 27, 28], [18.2, 29, 30], 'ci', { split: '' });
    }
    m.decal('flue_door_w', [0, -13, 31], [5, -9, 32], 'ci', 'south', 'back_door', { hide: ['north'] });
    m.decal('flue_door_e', [11, -13, 31], [16, -9, 32], 'ci', 'south', 'back_door', { hide: ['north'] });
    m.box('blowdown_cock', [6.5, -14, 31], [9.5, -11.5, 32], 'gm', { hide: ['north'], split: '' });
    m.box('blowdown_key', [9.5, -12.9, 31.3], [12, -12.4, 31.8], 'st', { split: '' });
    if (wrecked) {
      m.box('rupture', [3, 22.9, -16], [13, 23.3, 20], 'soot', { hide: ['down'], split: '' });
      return m;
    }
    // high-steam low-water valve on the crown
    m.box('hslw_seating', [4.5, 22, 1.5], [11.5, 23.6, 8.5], 'ci', { hide: ['down'], split: '' });
    m.box('hslw_body', [5.5, 23.6, 2.5], [10.5, 26.5, 7.5], 'gm', { split: '' });
    m.box('hslw_cap', [7, 26.5, 4], [9, 27.5, 6], 'st', { split: '' });
    m.box('hslw_lever', [7.6, 27, 5], [8.4, 27.6, 13], 'st', { split: '' });
    m.box('hslw_weight', [6.8, 25, 11], [9.2, 27, 13], 'ci', { split: '' });
    // feed check valve on the shell side, feed pipe down into the setting
    m.box('feed_check', [21.5, 12, 2], [25.5, 16, 7], 'gm', { split: '' });
    m.box('feed_bonnet', [22.5, 16, 3.5], [24.5, 17.5, 5.5], 'gm', { split: '' });
    m.box('feed_spindle', [23.2, 17.5, 4.2], [23.8, 18.6, 4.8], 'st', { split: '' });
    m.box('feed_wheel_a', [22, 18.6, 4.2], [25, 19.2, 4.8], 'red', { split: '' });
    m.box('feed_wheel_b', [23.2, 18.6, 3], [23.8, 19.2, 6], 'red', { split: '' });
    m.box('feed_pipe', [23, 7, 4], [24, 12, 5], 'gm', { split: '' });
    return m;
  }

  // ------------------------------------------------------------------ export
  const BUILDS = {
    fire_tube_boiler: { model: () => front('cold') },
    fire_tube_boiler_lit: { model: () => front('lit') },
    fire_tube_boiler_exploded: { model: () => front('exploded') },
    fire_tube_boiler_rear: { model: () => rear('cold') },
    fire_tube_boiler_rear_exploded: { model: () => rear('exploded') },
  };

  window.FTB = {
    paint,
    paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    async exportAll(root) {
      const out = await PCART.exportAll(root, 'boilers', BUILDS);
      // low water changes only the glass, which the renderer draws, so these reuse the normal models
      PCART.writeAlias(root, 'fire_tube_boiler_low_water', 'fire_tube_boiler');
      PCART.writeAlias(root, 'fire_tube_boiler_lit_low_water', 'fire_tube_boiler_lit');
      return out;
    },
    // Whole boiler for inspection only (never exported): rear segment moved three blocks back.
    preview(root, state = 'cold') {
      const m = front(state).merge(rear(state === 'exploded' ? 'exploded' : 'cold'), 0, 0, 48, 'rear_');
      return PCART.realize(root, m, 'fire_tube_boiler_preview');
    },
  };
})();
