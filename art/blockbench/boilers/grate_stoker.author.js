// Chain-grate stoker, hand-authored for Blockbench (replaces the spec_visual.py placeholder).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/grate_stoker.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); GST.paint('<repo>'); GST.paintGui('<repo>')
//   await GST.exportAll('<repo>')
//
// One block, front (hopper) to the north, the fuel bed travels south into the boiler furnace. The Create
// shaft comes in from the west at (y 8, z 8) into a worm reduction box; the half shaft Create draws inside
// stays between the upper and the return run of the grate, behind the side cheeks and the front apron.
// The grate is a partial model moved along z by GrateStokerRenderer with a 4 px key pitch (offset -2..2).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/grate_stoker';
  const T = {};

  // Grate keys seen from above: cast-iron keys 2 px wide with 1 px air gaps, burnt at the edges.
  T.grate_bars = () => {
    const im = img(32, 32), r = rng(401);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const gap = x % 3 === 2;
      put(im, x, y, gap ? [14, 12, 11] : add(mix([60, 58, 56], [92, 70, 52], r() < 0.12 ? 1 : 0), (r() - 0.5) * 10 + (x % 3 === 0 ? 8 : -4)));
    }
    return im;
  };
  // Fuel bed, 24x20 px over the 12x10 units it covers (2 px per unit), fresh coal at the gate (top rows),
  // ignited under the arch, burning through toward the boiler. The lit version glows; the cold one is coal.
  const bed = (lit) => () => {
    const im = img(32, 32), r = rng(lit ? 411 : 412);
    for (let y = 0; y < 20; y++) for (let x = 0; x < 24; x++) {
      const t = y / 19, lump = r();
      let c = add([30, 29, 28], (lump - 0.5) * 22);
      if (lump > 0.86) c = [70, 68, 66];
      if (lit) {
        const heat = Math.max(0, Math.min(1, (t - 0.18) * 1.6 + (lump - 0.5) * 0.5));
        if (heat > 0.05) c = mix(c, heat > 0.75 ? [255, 214, 120] : heat > 0.45 ? [255, 140, 40] : [190, 60, 20], Math.min(1, heat * 1.4));
        if (t > 0.85 && lump < 0.3) c = mix(c, [150, 146, 140], 0.6); // ash at the tail
      }
      put(im, x, y, c);
    }
    return im;
  };
  T.bed = bed(false);
  T.bed_lit = bed(true);
  // Buff firebrick for the ignition arch.
  T.firebrick = () => {
    const im = img(32, 32), r = rng(421);
    for (let row = 0; row < 8; row++) for (let k = 0; k < 5; k++) {
      const c = add([196, 168, 120], (r() - 0.5) * 24), x0 = (row % 2) * 4 + k * 8 - 8;
      for (let j = 0; j < 3; j++) for (let i = 0; i < 7; i++) put(im, ((x0 + i) % 32 + 32) % 32, row * 4 + j, add(c, (r() - 0.5) * 10));
      for (let j = 0; j < 4; j++) put(im, ((x0 + 7) % 32 + 32) % 32, row * 4 + j, [150, 134, 108]);
      for (let i = 0; i < 8; i++) put(im, ((x0 + i) % 32 + 32) % 32, row * 4 + 3, [150, 134, 108]);
    }
    return im;
  };
  // Underside of the arch, white-hot when the bed burns.
  T.arch_glow = () => {
    const im = img(16, 16), r = rng(431);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, mix([255, 196, 110], [255, 120, 40], r() * 0.6));
    return im;
  };
  T.coal = () => {
    const im = img(32, 32), r = rng(441);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const v = r();
      put(im, x, y, v > 0.9 ? [76, 74, 72] : add([26, 25, 24], (v - 0.5) * 20));
    }
    return im;
  };
  // Front apron casting with the maker's name raised on it (32 px over 12x10 units).
  T.apron = () => {
    const a = img(32, 32);
    grain(a, [52, 54, 56], 451, 5, 4, 8);
    for (let y = 0; y < 20; y++) for (let x = 0; x < 24; x++) {
      if (x === 0 || y === 0) put(a, x, y, [74, 76, 78]);
      if (x === 23 || y === 19) put(a, x, y, [32, 33, 34]);
    }
    for (let x = 4; x < 20; x++) for (const y of [8, 10]) if ((x * 7 + y) % 3) put(a, x, y, [86, 88, 90]);
    for (const [x, y] of [[2, 2], [21, 2], [2, 17], [21, 17]]) { put(a, x, y, [120, 122, 126]); put(a, x + 1, y + 1, [30, 31, 32]); }
    return a;
  };

  const MAT = {
    grate: { tex: 'grate_stoker/grate_bars', P: 16 }, coal: { tex: 'grate_stoker/coal', P: 16 },
    firebrick: { tex: 'grate_stoker/firebrick', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'material/cast_iron' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  // ------------------------------------------------------------------ geometry
  function stoker(running) {
    const m = mk();
    // side cheeks and the front apron hide the drive inside
    m.box('cheek_w', [2, 0, 0], [3.5, 11.5, 16], 'ci');
    m.box('cheek_e', [12.5, 0, 0], [14, 11.5, 16], 'ci');
    m.box('sole_w', [1.5, 0, 0], [4, 1, 16], 'ci', { hide: ['down'] });
    m.box('sole_e', [12, 0, 0], [14.5, 1, 16], 'ci', { hide: ['down'] });
    m.box('apron', [3.5, 1, 0], [12.5, 11, 0.6], 'ci', { split: '', face: { north: { tex: 'grate_stoker/apron', uv: [0, 0, 12, 10] } } });
    // side seals that keep the bed on the grate
    m.box('seal_w', [3.5, 11.5, 6], [4.5, 14, 16], 'ci');
    m.box('seal_e', [11.5, 11.5, 6], [12.5, 14, 16], 'ci');
    // worm reduction box on the shaft side
    m.box('gearbox', [0, 4.5, 4.5], [2, 11.5, 11.5], 'ci', { split: '' });
    m.box('gearbox_cover', [0, 11.5, 5], [2, 12.2, 11], 'ci', { split: '' });
    m.box('gearbox_filler', [0.6, 12.2, 7.5], [1.4, 13, 8.5], 'br', { split: '' });
    m.box('worm_bulge', [0, 2.5, 6], [2, 4.5, 10], 'ci', { split: '' });
    for (const [y, z] of [[5, 5], [5, 10.4], [10.4, 5], [10.4, 10.4]]) m.box(`gearbox_bolt_${y}_${z}`, [-0.01, y, z], [0.3, y + 0.6, z + 0.6], 'st', { split: '' });
    // hopper over the front with coal in it and the guillotine gate setting the bed depth
    m.box('hopper_front', [2, 11.5, 0], [14, 16, 0.8], 'ci', { split: '' });
    m.box('hopper_w', [2, 11.5, 0.8], [3, 16, 6.8], 'ci', { split: '' });
    m.box('hopper_e', [13, 11.5, 0.8], [14, 16, 6.8], 'ci', { split: '' });
    m.box('hopper_lip_n', [1.6, 16, -0.4], [14.4, 16.6, 0.8], 'ci', { split: '' });
    m.box('hopper_lip_w', [1.6, 16, 0.8], [3, 16.6, 7.2], 'ci', { split: '' });
    m.box('hopper_lip_e', [13, 16, 0.8], [14.4, 16.6, 7.2], 'ci', { split: '' });
    m.box('hopper_coal', [3, 12, 0.8], [13, 15.4, 6], 'coal', { split: '' });
    m.box('gate', [3, 13.6, 6], [13, 16.4, 6.8], 'ci', { split: '' });
    for (const x of [3.6, 11.8]) m.box(`gate_rack_${x}`, [x, 16.4, 6.15], [x + 0.6, 18.5, 6.65], 'st', { split: '' });
    m.box('gate_shaft', [3.2, 18, 6.6], [16, 18.6, 7.2], 'st', { split: '' });
    m.box('gate_crank', [15.4, 16, 6.6], [16, 18.6, 7.2], 'st', { split: '' });
    m.box('gate_handle', [15.4, 15.4, 7.2], [16, 16, 8.6], 'red', { split: '' });
    // fuel bed and the firebrick ignition arch over its front part
    m.box(running ? 'glow_bed' : 'bed', [4.5, 12, 6.8], [11.5, 13.6, 16], 'coal',
      { split: '', hide: ['down', 'south'], face: { up: { tex: running ? 'grate_stoker/bed_lit' : 'grate_stoker/bed', uv: [0, 0, 12, 10] } } });
    m.box('arch', [3.5, 14.6, 7], [12.5, 16.2, 12], 'firebrick', { split: '' });
    m.box('arch_skew_w', [2, 11.5, 6.8], [3.5, 16.2, 12.4], 'firebrick', { split: '' });
    m.box('arch_skew_e', [12.5, 11.5, 6.8], [14, 16.2, 12.4], 'firebrick', { split: '' });
    if (running) {
      m.box('glow_arch', [3.6, 14.55, 7.1], [12.4, 14.6, 11.9], 'firebrick',
        { split: '', hide: ['north', 'south', 'east', 'west', 'up'], face: { down: { tex: 'grate_stoker/arch_glow', uv: [0, 0, 16, 16] } } });
    }
    return m;
  }

  // The moving grate: three key rows on the upper run and three on the return run, 4 px pitch, z 2..14 at rest.
  function chain() {
    const m = mk('grate_stoker/grate_bars');
    for (let i = 0; i < 3; i++) {
      const z = 2 + i * 4;
      m.box(`keys_top_${i}`, [3.5, 11, z + 0.3], [12.5, 12.1, z + 3.7], 'grate', { split: '' });
      m.box(`keys_return_${i}`, [3.5, 1.4, z + 2.3], [12.5, 2.5, z + 5.7], 'grate', { split: '' });
      for (const x of [3.5, 12]) m.box(`link_${i}_${x}`, [x, 10.4, z], [x + 0.5, 11.2, z + 4], 'st', { split: '' });
    }
    return m;
  }

  // Local panel background, 176x104, for BoilerPanels.STOKER: a cast-iron plate with ribs and studs.
  function guiStoker() {
    const W = 176, H = 104, im = img(W, H);
    grain(im, [50, 52, 54], 462, 4, 5, 16);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([62, 64, 66], d === 0 ? -40 : d === 1 ? 22 : d === 3 ? -18 : 0));
    }
    for (const [x, y] of [[6, 6], [W - 9, 6], [6, H - 9], [W - 9, H - 9]]) {
      put(im, x, y, [130, 132, 136]); put(im, x + 1, y, [100, 102, 106]); put(im, x, y + 1, [90, 92, 96]); put(im, x + 1, y + 1, [30, 31, 32]);
    }
    for (const [cx, cy] of [[32, 48], [88, 48]]) for (let a = 0; a < 360; a++) {
      const t = a * Math.PI / 180;
      put(im, cx + 25 * Math.cos(t) + 1, cy + 25 * Math.sin(t) + 1, [26, 27, 28]);
    }
    for (let x = 6; x < W - 6; x++) put(im, x, 83, [30, 31, 32]);
    for (let x = 6; x < W - 6; x++) put(im, x, 84, [70, 72, 74]);
    return im;
  }

  function paintGui(root) {
    writePng(guiStoker(), path.join(root, PCART.ASSETS, 'textures/gui/panel/grate_stoker.png'));
    return 1;
  }

  const BUILDS = {
    grate_stoker: { model: () => stoker(false) },
    grate_stoker_running: { model: () => stoker(true) },
    'grate_stoker/chain': { model: chain, java: 'grate_stoker/partial/chain' },
  };

  window.GST = {
    paint,
    paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'boilers', BUILDS),
    preview(root, running = true) {
      const m = stoker(running).merge(chain(), 0, 0, 0, 'chain_');
      return PCART.realize(root, m, 'grate_stoker_preview');
    },
  };
})();
