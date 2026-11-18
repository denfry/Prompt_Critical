// Vitrified high-level waste store (one cell), hand-authored for Blockbench; replaces the borrowed
// near_surface_vault. Modelled on the vitrified product stores at Sellafield and Mayak: a thick concrete monolith
// whose charge face carries a grid of cast steel shield plugs over the storage tubes, each tube holding a stack of
// glass canisters; forced air comes in through the inlet grilles low on the sides and leaves warm by the outlet
// stack. The block has no facing; the outlet stack stands at the north-east corner.
// Load in Blockbench desktop after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/waste/hlw_vault.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); VAULT.paint('<repo>')
//   VAULT.build('<repo>', 'hlw_vault'); PCART.save('<repo>', 'waste', 'hlw_vault')
// Point used by code (HlwVaultBlockEntity.clientTick): outlet stack top (13, 23.2, 3).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/hlw_vault';
  const T = {};

  // Heavy concrete, smooth-formed, with the painted decontaminable band low down (epoxy, pale yellow).
  T.concrete = () => {
    const im = img(32, 32), r = rng(1001), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([158, 156, 148], (n(x, y) - 0.5) * 16 + (r() - 0.5) * 6);
      if (y >= 24) c = add([196, 186, 132], (r() - 0.5) * 6);
      if (y === 24) c = [120, 116, 92];
      put(im, x, y, c);
    }
    for (let k = 0; k < 20; k++) put(im, r() * 32, r() * 24, [124, 122, 116]);
    return im;
  };
  // Charge face: steel floor plate between the plugs, painted grey, worn by the charge machine's wheels.
  T.floor = () => {
    const out = img(32, 32), q = rng(1011), n = vnoise(q, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([112, 118, 120], (n(x, y) - 0.5) * 12 + (q() - 0.5) * 6);
      if ((x > 6 && x < 10) || (x > 22 && x < 26)) c = add(c, 12);
      put(out, x, y, c);
    }
    return out;
  };
  // Shield plug seen from above: machined steel disc, lifting pintle boss, a painted tube number.
  T.plug = () => {
    const im = img(32, 32), r = rng(1021);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x + 0.5 - 16, y + 0.5 - 16) / 16;
      let c = add([138, 142, 146], (r() - 0.5) * 6 + Math.sin(d * 40) * 3);
      if (d > 0.9) c = [70, 72, 74];
      if (d < 0.28) c = add([96, 100, 104], (r() - 0.5) * 6);
      if (d > 0.5 && d < 0.75 && y > 20 && y < 24 && x > 10 && x < 22 && (x % 3) !== 0) c = [222, 196, 50];
      put(im, x, y, c);
    }
    return im;
  };
  // Inlet and outlet grilles: heavy bars over a dark duct.
  T.grille = () => {
    const im = img(32, 32), r = rng(1031);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = [22, 22, 24];
      if (x % 6 < 2) c = add([84, 88, 90], (r() - 0.5) * 8);
      if (y < 2 || y > 29) c = [96, 100, 102];
      put(im, x, y, c);
    }
    return im;
  };
  // Trefoil sign on a yellow plate (GOST 17925): 32 px over 4 units.
  T.trefoil = () => {
    const im = img(32, 32);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx) * 180 / Math.PI;
      let c = [232, 196, 40];
      const blade = ((a + 90 + 360) % 120) < 60;
      if (d < 3) c = [20, 20, 20];
      else if (d > 4.5 && d < 13 && blade) c = [20, 20, 20];
      if (x < 1 || y < 1 || x > 30 || y > 30) c = [20, 20, 20];
      put(im, x, y, c);
    }
    return im;
  };
  T.steel = () => {
    const im = img(32, 32);
    grain(im, [90, 94, 96], 1041, 4, 6, 8);
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    concrete: { tex: 'hlw_vault/concrete', P: 16 }, floor: { tex: 'hlw_vault/floor', P: 16 },
    grille: { tex: 'hlw_vault/grille', P: 16 }, vst: { tex: 'hlw_vault/steel', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT), { particle: 'hlw_vault/concrete' });

  function vault() {
    const m = mk();
    m.box('monolith', [0, 0, 0], [16, 14, 16], 'concrete', { hide: ['up'], face: {} });
    m.box('charge_face', [0, 14, 0], [16, 14.5, 16], 'floor', { hide: ['down'] });
    // 3 x 3 shield plugs with pintles; the north-east one gives way to the outlet stack
    for (const x of [3, 8, 13]) for (const z of [3, 8, 13]) {
      if (x === 13 && z === 3) continue;
      m.plate(`plug_${x}_${z}`, [x - 2, 14.5, z - 2], [x + 2, 15, z + 2], 'vst', 'up', 'hlw_vault/plug', { hide: ['down'] });
      m.box(`pintle_${x}_${z}`, [x - 0.5, 15, z - 0.5], [x + 0.5, 15.8, z + 0.5], 'vst', { split: '' });
    }
    // inlet grilles low on the four sides
    for (const [d, f, t] of [['north', [3, 2, -0.2], [13, 6, 0]], ['south', [3, 2, 16], [13, 6, 16.2]],
      ['west', [-0.2, 2, 3], [0, 6, 13]], ['east', [16, 2, 3], [16.2, 6, 13]]]) {
      m.plate(`inlet_${d}`, f, t, 'vst', d, 'hlw_vault/grille', { hide: [{ north: 'south', south: 'north', east: 'west', west: 'east' }[d]] });
    }
    // trefoil signs above the grilles on north and south
    m.plate('sign_n', [6, 8, -0.1], [10, 12, 0], 'vst', 'north', 'hlw_vault/trefoil', { hide: ['south'] });
    m.plate('sign_s', [6, 8, 16], [10, 12, 16.1], 'vst', 'south', 'hlw_vault/trefoil', { hide: ['north'] });
    // outlet stack: concrete stub with a grille cap
    m.box('outlet', [11, 14.5, 1], [15, 22, 5], 'concrete');
    m.plate('outlet_grille_n', [11.5, 19, 0.8], [14.5, 21.5, 1], 'vst', 'north', 'hlw_vault/grille', { hide: ['south'] });
    m.plate('outlet_grille_e', [15, 19, 1.5], [15.2, 21.5, 4.5], 'vst', 'east', 'hlw_vault/grille', { hide: ['west'] });
    m.box('outlet_cap', [10.5, 22, 0.5], [15.5, 23, 5.5], 'vst', { split: '' });
    // radiation monitor head on the outlet (the stack is sampled)
    m.box('monitor', [9.4, 18, 2], [11, 20, 4], 'vst', { split: '' });
    return m;
  }

  const BUILDS = { hlw_vault: { model: vault } };
  window.VAULT = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
