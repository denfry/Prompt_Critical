// EDC cracking furnace (one cell), hand-authored for Blockbench; replaces the tube_furnace_controller models it
// borrowed from the delayed coker. Dichloroethane cracks to vinyl chloride and HCl at 480-530 C in a coil hung in
// a box firebox heated by rows of radiant-wall burners (B. F. Goodrich and Hoechst furnaces fire the side walls,
// not the floor). The firebox sits on a steel frame; the convection section, where the feed preheats, is above it,
// with the stack on top. Feed and fuel come to the side faces, so the side walls carry the burner rows and their
// fuel gas manifolds; the coil outlet drops to the quench below the north-east corner. Load in Blockbench:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/chlor_alkali/edc_cracker.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); EDC.paint('<repo>')
//   EDC.build('<repo>', 'edc_cracker'); PCART.save('<repo>', 'chlor_alkali', 'edc_cracker')   — one model per call
// States: edc_cracker (cold), _lit (cracking: burner cups and peep glow), _coked (coil coked up: amber lamp).
// Points used by code (EdcCrackerBlockEntity.clientTick): stack top (8, 32, 8), peep door (8, 7, 1.4),
// burner rows x = 0.8 / 15.2, y 3..11, z 4..12.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/edc_cracker';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Firebox casing: blue-grey enamel of the chlor plant over welded panels, chloride rust weeping from the seams.
  T.casing = () => {
    const im = img(32, 32), r = rng(601), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([96, 116, 128], (n(x, y) - 0.5) * 16 + (r() - 0.5) * 6);
      if (x === 0 || y === 0) c = add(c, -26);
      if (x === 1 || y === 1) c = add(c, 12);
      put(im, x, y, c);
    }
    for (let k = 0; k < 6; k++) { const x = r() * 32, y0 = r() * 8; for (let y = y0; y < y0 + 6 + r() * 10; y++) put(im, x, y, [128, 86, 58]); }
    return im;
  };
  // Frame and stack: black primer steel.
  T.frame = () => {
    const im = img(32, 32), r = grain(im, [48, 50, 52], 611, 4, 6, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [92, 64, 46]);
    return im;
  };
  // Convection section cladding, aluminium sheet.
  T.clad = () => {
    const im = img(32, 32), r = rng(621), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([182, 184, 186], (n(x, y) - 0.5) * 14 + (r() - 0.5) * 6);
      if (x % 16 === 0) c = [136, 138, 140];
      put(im, x, y, c);
    }
    return im;
  };
  // Fuel gas manifold: yellow, GOST 14202.
  T.gas = () => {
    const im = img(32, 32), r = grain(im, [198, 160, 42], 631, 5, 8, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, r() * 32, [112, 96, 60]);
    return im;
  };
  // Atlas (32 px, 2 px per uv unit): burner cup cold and lit, peep door cold and lit, lamps.
  const A = { cup: [0, 0, 4, 4], cup_lit: [4, 0, 4, 4], peep: [8, 0, 6, 6], peep_lit: [14, 0, 6, 6],
    lamp_off: [20, 0, 2, 2], lamp_amber: [22, 0, 2, 2], lamp_green: [24, 0, 2, 2], plate: [0, 8, 12, 4] };
  T.parts = () => {
    const im = img(32, 32), r = rng(641);
    const cup = (ox, lit) => {
      for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) {
        const d = Math.hypot(x + 0.5 - 2, y + 0.5 - 2);
        put(im, ox + x, y, d > 1.6 ? [66, 66, 64] : lit ? mix([255, 220, 140], [255, 110, 30], d / 1.6) : [40, 34, 30]);
      }
    };
    cup(0, false); cup(4, true);
    const peep = (ox, lit) => {
      for (let y = 0; y < 6; y++) for (let x = 0; x < 6; x++) {
        let c = add([60, 60, 58], (r() - 0.5) * 6);
        if (x === 0 || y === 0) c = add(c, 18);
        if (x === 5 || y === 5) c = add(c, -18);
        if (x > 1 && x < 4 && y > 1 && y < 4) c = lit ? (x === 2 ? [160, 50, 24] : [255, 190, 90]) : [26, 24, 22];
        put(im, ox + x, y, c);
      }
    };
    peep(8, false); peep(14, true);
    rect(im, 20, 0, 2, 2, [60, 50, 40]); rect(im, 22, 0, 2, 2, [255, 170, 40]); rect(im, 24, 0, 2, 2, [80, 230, 100]);
    for (let y = 0; y < 4; y++) for (let x = 0; x < 12; x++) {
      let c = add([176, 178, 174], (r() - 0.5) * 6);
      if (x === 0 || y === 0 || x === 11 || y === 3) c = [92, 94, 92];
      if (y > 0 && y < 3 && x > 1 && x < 10 && r() < 0.5) c = [40, 42, 44];
      put(im, x, 8 + y, c);
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    casing: { tex: 'edc_cracker/casing', P: 16 }, frame: { tex: 'edc_cracker/frame', P: 16 },
    clad: { tex: 'edc_cracker/clad', P: 16 }, gas: { tex: 'edc_cracker/gas', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'edc_cracker/parts', size: 32, rects: A } }),
    { particle: 'edc_cracker/casing' });

  const TAN16 = Math.tan(Math.PI / 16);
  function cylY(m, name, c, r, y0, y1, mat) {
    const h = r * TAN16, w = 2 * h, tex = m.mat[mat].tex, cuts = [y0];
    for (let y = Math.floor(y0 / 16) * 16 + 16; y < y1; y += 16) cuts.push(y);
    cuts.push(y1);
    let facet = 0, n = 0;
    for (let s = 0; s + 1 < cuts.length; s++) {
      const a = cuts[s], b = cuts[s + 1], base = Math.floor(a / 16) * 16;
      [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]].forEach(([wide, ang]) => {
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, b, c[1] + h] : [c[0] + h, b, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }

  function cracker(state) {
    const m = mk(), lit = state === 'lit';
    for (const [x, z] of [[1.5, 2.5], [13.5, 2.5], [1.5, 12.5], [13.5, 12.5]]) m.box(`leg_${x}_${z}`, [x - 1, 0, z - 1], [x + 1, 2, z + 1], 'frame', { split: '' });
    m.box('floor_beam', [0.5, 2, 1.5], [15.5, 3, 14.5], 'frame');
    // firebox with burner rows on both side walls and their fuel manifolds
    m.box('firebox', [1.5, 3, 2], [14.5, 14, 14], 'casing');
    for (const [s, x, dir] of [['w', 1.5, 'west'], ['e', 14.5, 'east']]) {
      const out = s === 'w' ? -0.4 : 0.4;
      for (const y of [4.5, 8.5, 12]) for (const z of [4, 8, 12]) {
        m.decal(lit ? `glow_cup_${s}_${y}_${z}` : `cup_${s}_${y}_${z}`, [Math.min(x, x + out), y - 1, z - 1], [Math.max(x, x + out), y + 1, z + 1],
          'frame', dir, lit ? 'p.cup_lit' : 'p.cup', { hide: [s === 'w' ? 'east' : 'west'] });
      }
      const mx = s === 'w' ? 0.4 : 15.6;
      m.box(`manifold_${s}`, [mx - 0.4, 3, 2.6], [mx + 0.4, 13.4, 3.4], 'gas', { split: '' });
      for (const y of [4.5, 8.5, 12]) m.box(`manifold_row_${s}_${y}`, [mx - 0.35, y + 1.1, 3.4], [mx + 0.35, y + 1.7, 13], 'gas', { split: '' });
    }
    for (const z of [2, 14]) for (const y of [3, 13.4]) m.box(`buck_${z}_${y}`, [1, y, z - 0.3], [15, y + 0.6, z + 0.3], 'frame', { split: '' });
    m.decal(lit ? 'glow_peep' : 'peep', [6.5, 5.5, 1.4], [9.5, 8.5, 2], 'frame', 'north', lit ? 'p.peep_lit' : 'p.peep', { hide: ['south'] });
    m.decal('nameplate', [3, 10.5, 1.7], [9, 12.5, 2], 'casing', 'north', 'p.plate', { hide: ['south'] });
    m.decal(state === 'coked' ? 'glow_lamp_coke' : 'lamp_coke', [11, 10.5, 1.6], [12, 11.5, 2], 'frame', 'north',
      state === 'coked' ? 'p.lamp_amber' : 'p.lamp_off', { hide: ['south'] });
    m.decal(lit ? 'glow_lamp_run' : 'lamp_run', [12.6, 10.5, 1.6], [13.6, 11.5, 2], 'frame', 'north',
      lit ? 'p.lamp_green' : 'p.lamp_off', { hide: ['south'] });
    // coil outlet to the quench, dropping past the north-east leg
    m.box('outlet', [12, 12.5, 0.6], [13.2, 13.7, 2], 'clad', { split: '' });
    m.box('outlet_drop', [12, 0, 0.6], [13.2, 12.5, 1.8], 'clad', { split: '' });
    // convection section with its feed crossover, hood and the stack
    m.box('roof', [1, 14, 1.5], [15, 15, 14.5], 'frame');
    m.box('convection', [3.5, 15, 3.5], [12.5, 22, 12.5], 'clad');
    m.box('crossover', [12.5, 17, 6.8], [14.4, 18.4, 9.2], 'clad', { split: '' });
    m.box('crossover_down', [13, 14, 7.2], [14.4, 17, 8.8], 'clad', { split: '' });
    m.box('hood', [5, 22, 5], [11, 24, 11], 'frame');
    cylY(m, 'stack', [8, 8], 1.8, 24, 32, 'frame');
    m.box('stack_lip', [6, 31.3, 6], [10, 32, 10], 'frame', { split: '' });
    m.box('damper_rod', [9.8, 26, 7.7], [11.5, 26.6, 8.3], 'frame', { split: '' });
    return m;
  }

  const BUILDS = {
    edc_cracker: { model: () => cracker('cold') },
    edc_cracker_lit: { model: () => cracker('lit') },
    edc_cracker_coked: { model: () => cracker('coked') },
  };
  window.EDC = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
