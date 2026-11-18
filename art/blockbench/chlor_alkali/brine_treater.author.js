// Brine purification unit (3x2x3), hand-authored for Blockbench; replaces the borrowed water_clarifier model and
// its rake. Raw brine from the salt saturator takes caustic soda (Mg(OH)2) and soda ash, here made in place by
// flue-gas CO2 (CaCO3), and the precipitate settles in a round clarifier; the scraper rake turns slowly and pushes
// the mud to the centre cone, clear brine overflows the peripheral launder. It is a peripheral-drive clarifier: the half bridge turns
// with the rake under it, carried on the centre pier and driven by a wheeled carriage running on the wall top. Corners: the salt saturator with its
// hopper (north-west), the caustic dosing tank (north-east), the flue gas carbonation sparger line (south-west) and
// the clear brine pump (south-east). Load in Blockbench desktop after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/chlor_alkali/brine_treater.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); BRINE.paint('<repo>')
//   BRINE.build('<repo>', 'brine_treater'); PCART.save('<repo>', 'chlor_alkali', 'brine_treater')
//   BRINE.build('<repo>', 'rake'); PCART.save('<repo>', 'chlor_alkali', 'brine_treater/rake', 'brine_treater/partial/rake')
// Local x, z -16..32 (anchor in the middle), y 0..32. The rake partial (half bridge, carriage, skimmer, rake) turns
// about the vertical axis through (8, 8) (FoundryMachineRenderer SWEEP_Y in ChlorClient). Points used by code (BrineTreaterBlockEntity.clientTick):
// the launder lip at radius 14.6, y 16.6; bubbles over the carbonation sparger (0, 16.7, 19).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/brine_treater';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Clarifier wall: rubber-lined steel outside painted chlor-plant grey-blue, salt crust creeping down from the lip.
  T.wall = () => {
    const im = img(32, 32), r = rng(801), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([104, 120, 128], (n(x, y) - 0.5) * 14 + (r() - 0.5) * 6);
      if (y === 15 || y === 31) c = add(c, -18);
      put(im, x, y, c);
    }
    for (let k = 0; k < 10; k++) { const x = r() * 32; for (let y = 0; y < 2 + r() * 9; y++) put(im, x, y, [214, 214, 206]); }
    return im;
  };
  // Brine surface seen from above: radial, clear at the rim, turbid round the feed well, foam on the launder.
  T.brine = () => {
    const im = img(64, 64), r = rng(811), n = vnoise(r, 64, 64, 8);
    for (let y = 0; y < 64; y++) for (let x = 0; x < 64; x++) {
      const d = Math.hypot(x + 0.5 - 32, y + 0.5 - 32) / 32;
      let c = mix([150, 146, 120], [64, 104, 116], Math.min(1, d * 1.4));
      c = add(c, (n(x, y) - 0.5) * 14 + (r() - 0.5) * 4);
      if (d > 0.93) c = add([206, 208, 200], (r() - 0.5) * 12);
      put(im, x, y, c);
    }
    return im;
  };
  // Concrete apron and plinths.
  T.concrete = () => {
    const im = img(32, 32), r = rng(821), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([148, 146, 138], (n(x, y) - 0.5) * 18 + (r() - 0.5) * 8);
      if (n(x, y) > 0.7) c = mix(c, [210, 210, 202], 0.4);
      put(im, x, y, c);
    }
    return im;
  };
  // Bridge, drive and rake steel: black epoxy.
  T.steel = () => {
    const im = img(32, 32), r = grain(im, [52, 56, 58], 831, 4, 6, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [210, 210, 200]);
    return im;
  };
  // Saturator and dosing tanks: FRP, translucent cream with a darker liquid line.
  T.frp = () => {
    const im = img(32, 32), r = rng(841), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([196, 186, 150], (n(x, y) - 0.5) * 12 + (r() - 0.5) * 6 + Math.sin(x * 1.3) * 3);
      if (y > 10) c = add(c, -22);
      put(im, x, y, c);
    }
    return im;
  };
  // Process piping, GOST 14202: alkalis and salt solutions violet, combustion gas yellow.
  T.alkali = () => {
    const im = img(32, 32), r = grain(im, [112, 66, 128], 851, 5, 6, 8);
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [190, 188, 180]);
    return im;
  };
  T.gas = () => grainTex([196, 160, 40], 861);
  T.yellow = () => grainTex([206, 168, 34], 871);
  function grainTex(c, seed) {
    const im = img(32, 32), r = grain(im, c, seed, 5, 8, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [96, 90, 80]);
    return im;
  }
  // Salt in the saturator hopper: wet grey-white crystals.
  T.salt = () => {
    const im = img(32, 32), r = rng(881);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(r() < 0.15 ? [236, 236, 230] : [196, 196, 190], (r() - 0.5) * 18));
    return im;
  };
  // Atlas (32 px, 2 px per uv unit): drive nameplate, running lamp off/on, pump motor fan cover.
  const A = { plate: [0, 0, 10, 4], lamp_off: [10, 0, 2, 2], lamp_on: [12, 0, 2, 2], fan: [16, 0, 8, 8] };
  T.parts = () => {
    const im = img(32, 32), r = rng(891);
    for (let y = 0; y < 4; y++) for (let x = 0; x < 10; x++) {
      let c = add([174, 176, 172], (r() - 0.5) * 6);
      if (x === 0 || y === 0 || x === 9 || y === 3) c = [90, 92, 90];
      if (y > 0 && y < 3 && x > 1 && x < 8 && r() < 0.5) c = [40, 42, 44];
      put(im, x, y, c);
    }
    rect(im, 10, 0, 2, 2, [40, 70, 44]); rect(im, 12, 0, 2, 2, [90, 240, 110]);
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) {
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4);
      put(im, 16 + x, y, d > 3.8 ? [60, 90, 70] : (x + y) % 2 ? [40, 44, 42] : [84, 120, 96]);
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    wall: { tex: 'brine_treater/wall', P: 16 }, concrete: { tex: 'brine_treater/concrete', P: 16 }, bst: { tex: 'brine_treater/steel', P: 16 },
    frp: { tex: 'brine_treater/frp', P: 16 }, alkali: { tex: 'brine_treater/alkali', P: 16 }, gas: { tex: 'brine_treater/gas', P: 16 },
    yellow: { tex: 'brine_treater/yellow', P: 16 }, salt: { tex: 'brine_treater/salt', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'brine_treater/parts', size: 32, rects: A } }),
    { particle: 'brine_treater/wall' });

  const TAN16 = Math.tan(Math.PI / 16);
  function cylY(m, name, c, r, y0, y1, mat, o = {}) {
    const h = r * TAN16, w = 2 * h, k = 16 / (2 * r), tex = m.mat[mat].tex, cuts = [y0];
    for (let y = Math.floor(y0 / 16) * 16 + 16; y < y1; y += 16) cuts.push(y);
    cuts.push(y1);
    let facet = o.seed || 0, n = 0;
    for (let s = 0; s + 1 < cuts.length; s++) {
      const a = cuts[s], b = cuts[s + 1], base = Math.floor(a / 16) * 16, last = s + 2 === cuts.length;
      [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]].forEach(([wide, ang], i) => {
        const top = b + (last && o.cap ? i * 0.01 : 0);
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, top, c[1] + h] : [c[0] + h, top, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else if (d === 'up' && last && o.cap) {
            face.up = { tex: o.cap, uv: [(f[0] - c[0] + r) * k, (f[2] - c[1] + r) * k, (t[0] - c[0] + r) * k, (t[2] - c[1] + r) * k] };
          } else if (d === 'up' && last && o.topMat) {
            face.up = { tex, uv: [0, 0, w, w] };
          } else hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }

  const C = [8, 8], R = 15;
  function treater(running) {
    const m = mk();
    m.box('apron', [-16, 0, -16], [32, 1, 32], 'concrete', { hide: ['down'] });
    // clarifier: wall, brine to just under the launder lip, the launder outlet box, the feed well and bridge
    cylY(m, 'wall', C, R, 1, 17.4, 'wall', { seed: 1 });
    cylY(m, 'brine', C, R - 0.6, 1, 16.6, 'wall', { cap: 'brine_treater/brine', seed: 3 });
    m.box('launder_box', [21.6, 10, 5.5], [24.4, 17, 10.5], 'wall', { split: '' });
    m.box('overflow_pipe', [24.4, 11, 7], [27, 13, 9], 'alkali', { split: '' });
    m.box('overflow_drop', [25, 1, 7], [27, 11, 9], 'alkali', { split: '' });
    cylY(m, 'centre_pier', C, 2.2, 16.6, 19, 'bst', { seed: 5 });
    cylY(m, 'feed_well', C, 3.4, 15.4, 17.4, 'bst', { seed: 6 });
    m.box('pier_bearing', [6, 19, 6], [10, 20, 10], 'bst', { split: '' });
    m.decal('pier_plate', [6.5, 17.6, 5.4], [9.5, 18.8, 5.6], 'bst', 'north', 'p.plate', { hide: ['south'] });
    // local control post by the pump, with the drive running lamp
    m.box('control_post', [19, 1, 29.5], [20, 12, 30.5], 'bst', { split: '' });
    m.box('control_box', [18, 10, 28.6], [21, 14, 29.5], 'bst', { split: '' });
    m.decal(running ? 'glow_lamp' : 'lamp', [19, 12.4, 28.3], [20, 13.4, 28.6], 'bst', 'north', running ? 'p.lamp_on' : 'p.lamp_off', { hide: ['south'] });
    // raw brine comes in along the apron and up through the centre pier into the feed well
    m.box('feed', [-16, 1, 7.1], [-6.8, 2.8, 8.9], 'alkali', { split: '' });
    // north-west: salt saturator with its hopper of salt
    m.box('saturator_plinth', [-15, 1, -15], [-5, 2, -5], 'concrete');
    cylY(m, 'saturator', [-10, -10], 4.6, 2, 18, 'frp', { seed: 7, cap: 'brine_treater/brine' });
    m.box('hopper', [-14, 18, -14], [-6, 22, -6], 'bst', { hide: ['up'] });
    m.box('hopper_salt', [-13.4, 18, -13.4], [-6.6, 21.4, -6.6], 'salt', { split: '' });
    m.box('saturator_out', [-6, 4, -11], [-1.5, 5.6, -9.4], 'alkali', { split: '' });
    // north-east: caustic dosing tank, dosing pump, line to the feed
    m.box('caustic_bund', [22, 1, -15], [31, 2.2, -6], 'concrete');
    cylY(m, 'caustic', [26.5, -10.5], 3.6, 2.2, 14, 'frp', { seed: 9, topMat: true });
    m.box('caustic_label', [25, 9, -14.3], [28, 10.5, -14.1], 'yellow', { split: '' });
    m.box('dosing_pump', [22.4, 2.2, -8.4], [25, 4.4, -6.4], 'alkali', { split: '' });
    m.box('dosing_line', [23.2, 4.4, -6.8], [23.8, 6, -6.2], 'alkali', { split: '' });
    m.box('dosing_run', [11.5, 5.4, -6.8], [23.8, 6, -6.2], 'alkali', { split: '' });
    // south-west: flue gas carbonation line through the wall to the sparger in the brine
    m.box('flue_riser', [-12, 1, 18], [-10, 8, 20], 'gas', { split: '' });
    m.box('flue_run', [-10, 6, 18], [-2, 8, 20], 'gas', { split: '' });
    m.box('flue_valve', [-12.6, 2.5, 17.4], [-9.4, 5.5, 20.6], 'bst', { split: '' });
    // south-east: clear brine pump on its plinth, motor fan cover facing east
    m.box('pump_plinth', [21, 1, 22], [31, 2.4, 29], 'concrete');
    m.box('pump_casing', [22, 2.4, 23.5], [25.4, 6.4, 27.5], 'alkali', { split: '' });
    m.box('pump_motor', [25.4, 2.8, 23.8], [30.4, 6, 27.2], 'bst', { split: '' });
    m.decal('pump_fan', [30.4, 3, 24], [30.8, 5.8, 27], 'bst', 'east', 'p.fan', { hide: ['west'] });
    m.box('pump_suction', [22.8, 6.4, 24.8], [24.6, 8, 26.2], 'alkali', { split: '' });
    return m;
  }

  // Turning part: half bridge from the centre pier to the wall with handrails, the drive carriage on the wall top,
  // the scum skimmer on the surface and, under the brine, the rake arm with blades set to push mud to the centre.
  function rake() {
    const m = mk();
    m.box('turntable', [5.6, 20, 5.6], [10.4, 21, 10.4], 'bst', { split: '' });
    m.box('bridge', [8, 20, 6.4], [24, 21.4, 9.6], 'bst', { split: '' });
    for (const z of [6.4, 9]) {
      m.box(`rail_${z}`, [9, 24.4, z], [23.4, 24.9, z + 0.6], 'yellow', { split: '' });
      for (const x of [9, 16, 23]) m.box(`post_${z}_${x}`, [x - 0.3, 21.4, z], [x + 0.3, 24.9, z + 0.6], 'yellow', { split: '' });
    }
    // carriage on the wall: frame, wheel, gear motor
    m.box('carriage', [21, 17.4, 5.4], [25, 20, 10.6], 'bst', { split: '' });
    m.box('carriage_wheel', [22.2, 16.6, 6.4], [23.8, 18.6, 9.6], 'bst', { split: '' });
    m.box('carriage_motor', [24, 20, 6.6], [27, 22.6, 9.4], 'alkali', { split: '' });
    // skimmer arm floating on the surface, hung from the bridge
    for (const x of [12, 19]) m.box(`skimmer_hanger_${x}`, [x - 0.2, 16.6, 9.2], [x + 0.2, 20, 9.6], 'bst', { split: '' });
    m.box('skimmer', [10, 16.4, 9.2], [21, 17.2, 10.2], 'bst', { split: '', rot: { axis: 'y', angle: -22.5, origin: [15.5, 16.8, 9.7] } });
    // the rake itself, under the brine (seen when the clarifier is drained or the surface is clipped)
    m.box('rake_drop', [21.4, 2.6, 7.6], [22.2, 16.6, 8.4], 'bst', { split: '' });
    m.box('rake_arm', [9, 2.6, 7.4], [22.2, 3.4, 8.6], 'bst', { split: '' });
    for (let k = 1; k <= 4; k++) {
      const x = 8 + k * 3;
      m.box(`blade_${k}`, [x - 1.4, 1.4, 7.6], [x + 1.4, 2.6, 8.4], 'bst', { split: '', rot: { axis: 'y', angle: 22.5, origin: [x, 2, 8] } });
    }
    return m;
  }

  const BUILDS = {
    brine_treater: { model: () => treater(false) },
    brine_treater_running: { model: () => treater(true) },
    rake: { model: rake, java: 'brine_treater/partial/rake' },
  };
  window.BRINE = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
