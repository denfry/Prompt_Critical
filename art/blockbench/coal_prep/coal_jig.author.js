// Baum-type air-pulsated coal jig, hand-authored for Blockbench (replaces the spec_visual.py and
// spec_metallurgy.py placeholders), plus its panel board.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/coal_prep/coal_jig.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); JIG.paint('<repo>'); JIG.paintGui('<repo>')
//   await JIG.exportAll('<repo>')
//
// 2x2x1 blocks, model x 0..32, y 0..32, z 0..16, front to the north. A riveted tank on legs over a hutch.
// Lengthwise it is split into the closed air chamber (north half) and the jigging compartment (south half,
// open), where the bed lies on the screen plate under the pulsating water: refuse at the bottom, middlings,
// washed coal on top, which is what the jig is for. Rotary air valves sit on the air chamber along the
// pulsator shaft (the pulsator partial, spun about x through y 27, z 8). The Create shaft comes into the
// lower west cell at y 8, z 8 and drives the pulsator through a chain in the casing on the west end. Raw coal
// comes down the feed chute at the west end, washed coal overflows the east lip with the water, middlings and
// refuse leave through gates (east and front). Low-pressure air comes along the top, back water down the
// north side into the hutch.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, rivet, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/coal_jig';
  const T = {};
  const AY = 27, AZ = 8;

  // Tank plate: grey-blue paint over riveted seams every half block, wet and coal-black low down.
  T.tank = () => {
    const im = img(32, 32), r = grain(im, [82, 92, 100], 1801, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4;
      put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [30, 32, 34], Math.max(0, (y - 16) / 16) * 0.5));
    }
    for (let k = 1; k < 32; k += 4) { rivet(im, k, 1, [82, 92, 100]); rivet(im, 1, k, [82, 92, 100]); }
    return im;
  };
  // Bed strata seen at the open compartment, by depth inside a block: washed coal (black, glossy), middlings
  // (grey-brown), refuse shale (grey); used as a side texture with UV picking the band.
  T.bed = () => {
    const im = img(32, 32), r = rng(1811);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const c = y < 11 ? [26, 25, 26] : y < 21 ? [70, 62, 54] : [104, 102, 98];
      put(im, x, y, add(c, (r() - 0.5) * (y < 11 ? 30 : 18) + (r() < 0.05 ? 40 : 0)));
    }
    return im;
  };
  // Washed coal under the water surface: dark, a sheen and ripples.
  T.water = () => {
    const im = img(32, 32), r = rng(1821);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([30, 40, 46], Math.sin(x * 0.7 + Math.sin(y * 0.4) * 2) * 6 + (r() - 0.5) * 8));
    return im;
  };

  const MAT = {
    tank: { tex: 'coal_jig/tank', P: 16 }, bed: { tex: 'coal_jig/bed', P: 16 }, wat: { tex: 'coal_jig/water', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'coal_jig/tank' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function jig() {
    const m = mk();
    // legs and the hutch narrowing to the refuse gate
    for (const x of [1, 28]) for (const z of [1, 12.5]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 3, 9, z + 2.5], 'ci', { split: '' });
    m.box('hutch', [3, 9, 1.5], [29, 14, 14.5], 'tank', { split: 'x' });
    m.box('hutch_low', [8, 5, 3.5], [24, 9, 12.5], 'tank', { split: 'x' });
    m.box('refuse_gate', [13, 4, 0.4], [19, 7.4, 3.5], 'ci', { split: '' });
    m.box('refuse_gate_wheel', [15.6, 7.4, 0.2], [16.4, 9.4, 1], 'red', { split: '' });
    // tank walls; the air chamber (north) is closed by its roof, the compartment (south) is open
    m.box('wall_n', [0, 14, 0], [32, 26, 1], 'tank', { split: 'x' });
    m.box('wall_s', [0, 14, 15], [32, 26, 16], 'tank', { split: 'x' });
    m.box('wall_w', [0, 14, 1], [1, 26, 15], 'tank', { split: '' });
    m.box('wall_e', [31, 14, 1], [32, 23, 15], 'tank', { split: '' });
    m.box('divider', [1, 16, 7.6], [31, 26, 8.4], 'tank', { split: 'x' });
    m.box('air_roof', [1, 25.4, 1], [31, 26, 7.6], 'tank', { split: 'x' });
    m.box('rim', [0, 26, 0], [32, 26.6, 1], 'ci', { split: 'x' });
    m.box('rim_s', [0, 26, 15], [32, 26.6, 16], 'ci', { split: 'x' });
    // the bed in its strata on the screen plate, water over it
    m.box('screen_plate', [1, 17.6, 8.4], [31, 18, 15], 'st', { split: 'x' });
    m.box('bed_refuse', [1, 18, 8.4], [31, 19.2, 15], 'bed', { split: 'x', face: { up: 'bed' } });
    m.box('bed_middlings', [1, 19.2, 8.4], [31, 20.2, 15], 'bed', { split: 'x' });
    m.box('bed_clean', [1, 20.2, 8.4], [31, 21.6, 15], 'bed', { split: 'x' });
    m.box('water', [1, 22.8, 8.4], [31, 23, 15], 'wat', { split: 'x', hide: ['down', 'north', 'south', 'east', 'west'] });
    // three rotary air valves on the air chamber along the pulsator shaft, bearings at the ends
    for (const x of [6, 14, 22]) {
      m.box(`valve_${x}`, [x, 26, 4.6], [x + 4, 29.6, 10.4], 'ci', { split: '' });
      m.box(`valve_port_${x}`, [x + 1, 26, 2], [x + 3, 28, 4.6], 'ci', { split: '' });
    }
    for (const x of [2.6, 28.4]) m.box(`bearing_${x}`, [x, 26, 6], [x + 1.6, 28.6, 10], 'ci', { split: '' });
    // chain casing on the west end from the input shaft up to the pulsator shaft, input bearing
    m.box('chain_case', [0.2, 4.6, 5], [2.4, 30.4, 11], 'red', { split: '' });
    m.prism('input_bearing', 'x', [8, 8], 2.2, 2.4, 4.4, 'ci', { split: '', ends: ['east'] });
    // low-pressure air main along the top with drops to the valves; back water down the north side
    m.box('air_main', [4, 30.6, 1.8], [30, 32, 3.2], 'st', { split: 'x' });
    for (const x of [7.4, 15.4, 23.4]) m.box(`air_drop_${x}`, [x, 28, 2], [x + 1.2, 30.6, 3], 'st', { split: '' });
    m.box('water_main', [26, 9, -0.6], [27.4, 32, 0.8], 'st', { split: 'y' });
    m.box('water_valve', [25.4, 16, -1.4], [28, 17.4, -0.6], 'red', { split: '' });
    // feed chute at the west end, washed coal overflow lip and middlings gate at the east end
    m.box('feed_chute', [1, 24.6, 9], [9, 25.4, 15], 'ci', { split: '', rot: { axis: 'z', angle: -22.5, origin: [9, 25, 12] } });
    m.box('feed_chute_n', [1, 25.4, 8.6], [9, 27.4, 9], 'ci', { split: '', rot: { axis: 'z', angle: -22.5, origin: [9, 25, 12] } });
    m.box('feed_chute_s', [1, 25.4, 15], [9, 27.4, 15.4], 'ci', { split: '', rot: { axis: 'z', angle: -22.5, origin: [9, 25, 12] } });
    m.box('overflow_lip', [31, 21.6, 8.6], [32, 22.4, 15], 'ci', { split: '' });
    m.box('overflow_spout', [32, 20.6, 8.6], [34, 21.6, 15], 'ci', { split: '', rot: { axis: 'z', angle: -22.5, origin: [32, 21.6, 12] } });
    m.box('middlings_gate', [32, 14.4, 9.6], [33.2, 17.6, 14], 'ci', { split: '' });
    return m;
  }

  // Pulsator partial: shaft along x through (27, 8), the upper chain sprocket at the west end, and a cam disc
  // between valves showing the turn.
  function pulsator() {
    const m = mk('material/bright_steel');
    m.prism('shaft', 'x', [AY, AZ], 0.8, 0.6, 30, 'bright', { split: '', ends: ['east'] });
    m.prism('sprocket', 'x', [AY, AZ], 2.4, 0.6, 1.8, 'st', { split: '', ends: ['east', 'west'] });
    for (const x of [11, 19]) {
      m.prism(`cam_${x}`, 'x', [AY, AZ], 1.8, x, x + 1, 'st', { split: '', ends: ['east', 'west'] });
      m.box(`cam_lobe_${x}`, [x, AY + 1.4, AZ - 0.6], [x + 1, AY + 2.6, AZ + 0.6], 'st', { split: '' });
    }
    return m;
  }

  // Panel board for FoundryPanels.COAL_JIG (200x150): ring behind the shaft dial, plate under the bars and the
  // read-out, a strip for the feed slot and the three products.
  function guiJig() {
    const W = 200, H = 150, im = img(W, H), r = rng(1831);
    grain(im, [62, 70, 78], 1832, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    for (let a = 0; a < 360; a += 0.5) {
      const t = a * Math.PI / 180;
      put(im, 32 + 26 * Math.cos(t), 48 + 26 * Math.sin(t), [34, 40, 46]);
      put(im, 32 + 27 * Math.cos(t) + 0.5, 48 + 27 * Math.sin(t) + 0.5, [108, 118, 128]);
    }
    const plate = (x0, y0, x1, y1, base) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
        put(im, x, y, add(base, edge + (r() - 0.5) * 4));
      }
    };
    plate(62, 22, W - 6, 106, [52, 60, 68]);
    plate(8, 114, W - 6, 144, [46, 52, 58]);
    // the bed strata as a small sign on the plate: clean / middlings / refuse
    rect(im, 8, 104, 48, 3, [26, 25, 26]); rect(im, 8, 107, 48, 2, [70, 62, 54]); rect(im, 8, 109, 48, 2, [104, 102, 98]);
    return im;
  }

  function paintGui(root) {
    writePng(guiJig(), path.join(root, PCART.ASSETS, 'textures/gui/panel/coal_jig.png'));
    return 1;
  }

  const BUILDS = {
    coal_jig: { model: jig },
    'coal_jig/pulsator': { model: pulsator, java: 'coal_jig/partial/pulsator' },
  };

  window.JIG = {
    paint, paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'coal_prep', id, BUILDS[id].java),
    exportAll: (root) => PCART.exportAll(root, 'coal_prep', BUILDS),
    preview(root) { return PCART.realize(root, jig().merge(pulsator(), 0, 0, 0, 'p_'), 'coal_jig_preview'); },
  };
})();
