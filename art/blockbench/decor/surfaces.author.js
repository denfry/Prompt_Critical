// Hand-painted surfaces at 32 px (2 px per model unit, as the authored machines) for the full-block decor, the
// ores and the oil-field strata (replaces the tex_decor.py, tex_zdecor.py, tex_metallurgy*.py, tex_oilgas.py and
// tex_fuelcycle.py placeholders of the same names). Models stay as they were: decor and strata are full cubes,
// metal ores are the host stone with an overlay (alpha) of the mineral.
// Load inside Blockbench desktop (or any Electron/Node with a canvas), after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/decor/surfaces.author.js', 'utf8'))
//   SURF.paint('<repo>')
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const T = {};
  const S = 32;
  const clamp = (v) => Math.max(0, Math.min(255, v));
  const putA = (im, x, y, c, a) => {
    x = Math.round(x); y = Math.round(y);
    if (x < 0 || y < 0 || x >= im.w || y >= im.h) return;
    const i = (y * im.w + x) * 4;
    im.d[i] = clamp(c[0]); im.d[i + 1] = clamp(c[1]); im.d[i + 2] = clamp(c[2]); im.d[i + 3] = a;
  };

  // ---------------------------------------------------------------- room finishes
  // Painted concrete: roller-laid paint over board-marked concrete, chips showing grey, faint roller streaks.
  const paintedConcrete = (base, seed) => () => {
    const im = img(S, S), r = rng(seed), n = vnoise(r, S, S, 8);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) put(im, x, y, add(base, (n(x, y) - 0.5) * 10 + ((x + Math.floor(y / 3)) % 7 === 0 ? -4 : 0) + (r() - 0.5) * 5));
    for (let k = 0; k < 7; k++) { const x = r() * S, y = r() * S; put(im, x, y, [128, 126, 120]); if (r() < 0.5) put(im, x + 1, y, [140, 138, 132]); }
    for (let x = 0; x < S; x++) put(im, x, 31, add(get(im, x, 31), -12));
    return im;
  };
  T['decor/painted_concrete'] = paintedConcrete([170, 172, 166], 16101);
  T['decor/painted_concrete_red'] = paintedConcrete([150, 52, 42], 16102);
  T['decor/painted_concrete_yellow'] = paintedConcrete([212, 172, 52], 16103);
  // Two-tone wall of Soviet plant rooms: whitewash above, oil paint dado below, a dark dividing line.
  const twoTone = (dado, seed) => () => {
    const im = img(S, S), r = rng(seed), n = vnoise(r, S, S, 8);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const c = y < 17 ? [222, 222, 214] : dado;
      put(im, x, y, add(c, (n(x, y) - 0.5) * (y < 17 ? 6 : 10) + (r() - 0.5) * 4));
    }
    for (let x = 0; x < S; x++) { put(im, x, 17, [40, 42, 44]); put(im, x, 18, add(dado, -18)); }
    for (let k = 0; k < 4; k++) put(im, r() * S, 19 + r() * 12, add(dado, 24));
    return im;
  };
  T['decor/painted_concrete_two_tone'] = twoTone([62, 110, 84], 16111);
  T['decor/painted_concrete_two_tone_beige'] = twoTone([196, 172, 128], 16112);
  T['decor/painted_concrete_two_tone_blue'] = twoTone([70, 108, 150], 16113);
  // Glazed ceramic wall tiles, 8 px, grout lines, a gloss highlight on each tile.
  const glazed = (base, grout, seed, big) => () => {
    const im = img(S, S), r = rng(seed), t = big || 8;
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const tx = x % t, ty = y % t, joint = tx === t - 1 || ty === t - 1;
      const tile = Math.floor(x / t) * 7 + Math.floor(y / t) * 13;
      let c = joint ? grout : add(base, ((tile * 37) % 9) - 4 + (r() - 0.5) * 4);
      if (!joint && tx === 1 && ty < t - 3 && ty > 0) c = add(c, 22);
      if (!joint && ty === 1 && tx > 0 && tx < t - 3) c = add(c, 14);
      put(im, x, y, c);
    }
    return im;
  };
  T['decor/tile_white'] = glazed([226, 228, 224], [170, 170, 164], 16121);
  T['decor/tile_blue'] = glazed([130, 168, 204], [170, 176, 178], 16122);
  T['decor/tile_green'] = glazed([132, 184, 150], [170, 176, 172], 16123);
  T['decor/porcelain_tile'] = glazed([150, 152, 150], [96, 98, 96], 16124, 16);
  T['decor/porcelain_tile_beige'] = glazed([196, 180, 150], [130, 120, 100], 16125, 16);
  // Linoleum: marbled sheet with fine speckle and a seam every block; parquet: oak herringbone.
  const linoleum = (base, vein, seed) => () => {
    const im = img(S, S), r = rng(seed), n = vnoise(r, S, S, 6);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const v = Math.sin((x + n(x, y) * 14) * 0.4) * 0.5 + 0.5;
      put(im, x, y, add(mix(base, vein, v * 0.35), (r() - 0.5) * 8));
    }
    for (let y = 0; y < S; y++) put(im, 0, y, add(get(im, 0, y), -16));
    return im;
  };
  T['decor/linoleum_control'] = linoleum([120, 128, 120], [160, 168, 160], 16131);
  T['decor/linoleum_control_beige'] = linoleum([196, 176, 140], [222, 206, 172], 16132);
  T['decor/linoleum_control_parquet'] = () => {
    const im = img(S, S), r = rng(16133);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const k = Math.floor((x + y) / 8), dir = k % 2, along = dir ? (x - y + 64) % 16 : (x + y) % 16;
      const plank = Math.floor((dir ? x + y : x - y + 64) / 4);
      const base = [[150, 104, 62], [168, 118, 72], [138, 94, 56], [176, 128, 80]][plank % 4];
      let c = add(base, Math.sin(along * 0.9 + plank) * 6 + (r() - 0.5) * 8);
      if ((x + y) % 8 === 0) c = add(c, -30);
      put(im, x, y, c);
    }
    return im;
  };
  // Mineral-fibre ceiling tile in its T-bar grid: off-white, fissures and pinholes, the grid at the edges.
  T['decor/ceiling_tile'] = () => {
    const im = img(S, S), r = grain(im, [232, 230, 222], 16141, 3, 3, 8);
    for (let k = 0; k < 70; k++) put(im, r() * S, r() * S, [196, 194, 186]);
    for (let k = 0; k < 6; k++) { let x = r() * S, y = r() * S; for (let j = 0; j < 5; j++) { put(im, x, y, [204, 200, 190]); x += r() * 2 - 0.5; y += r() - 0.5; } }
    for (let i = 0; i < S; i++) { put(im, i, 0, [176, 178, 178]); put(im, 0, i, [176, 178, 178]); put(im, i, 1, [214, 216, 216]); put(im, 1, i, [214, 216, 216]); }
    return im;
  };
  // Corrugated sheet: 8 px pitch with lit crests and shaded troughs, fastener rows top and bottom, weathering.
  const corrugated = (base, seed) => () => {
    const im = img(S, S), r = rng(seed), n = vnoise(r, S, S, 8);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const p = x % 8, shade = [22, 14, 4, -6, -16, -22, -10, 8][p];
      put(im, x, y, add(base, shade + (n(x, y) - 0.5) * 8 + (r() - 0.5) * 4));
    }
    for (const y of [3, 28]) for (let x = 2; x < S; x += 8) { put(im, x, y, [60, 60, 62]); put(im, x + 1, y, add(base, 30)); }
    for (let k = 0; k < 5; k++) { const x = Math.floor(r() * S); for (let y = 29; y < 32; y++) put(im, x, y, add(get(im, x, y), -18)); }
    return im;
  };
  T['decor/corrugated_steel'] = corrugated([150, 156, 158], 16151);
  T['decor/corrugated_steel_blue'] = corrugated([62, 98, 150], 16152);
  T['decor/corrugated_steel_green'] = corrugated([70, 124, 90], 16153);
  T['decor/corrugated_steel_red'] = corrugated([160, 58, 46], 16154);
  T['decor/corrugated_steel_white'] = corrugated([214, 216, 212], 16155);
  // Glass blocks: four blocks in mortar; the glass is translucent with a ribbed ripple, frosted is milkier.
  const glassBlock = (frosted, seed) => () => {
    const im = img(S, S), r = rng(seed);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const tx = x % 16, ty = y % 16;
      if (tx < 1 || ty < 1 || tx > 14 || ty > 14) { putA(im, x, y, add([176, 176, 170], (r() - 0.5) * 10), 255); continue; }
      const edge = tx < 3 || ty < 3 || tx > 12 || ty > 12;
      const rib = Math.sin(tx * 1.2) * 10;
      const c = frosted ? add([226, 234, 236], rib * 0.4 + (r() - 0.5) * 8) : add([184, 214, 222], rib + (edge ? -14 : 0));
      putA(im, x, y, c, frosted ? (edge ? 236 : 214) : (edge ? 200 : 120));
    }
    return im;
  };
  T['decor/glass_block'] = glassBlock(false, 16161);
  T['decor/glass_block_frosted'] = glassBlock(true, 16162);

  // ---------------------------------------------------------------- rock and ore
  // Host rocks: grey stone with a fine grain, darker deepslate with a faint foliation.
  const stone = (base, seed, foliate) => {
    const im = img(S, S), r = rng(seed), n = vnoise(r, S, S, 6), n2 = vnoise(r, S, S, 3);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      let v = (n(x, y) - 0.5) * 20 + (n2(x, y) - 0.5) * 12 + (r() - 0.5) * 8;
      if (foliate) v += Math.sin((y + n(x, y) * 4) * 1.6) * 6;
      put(im, x, y, add(base, v));
    }
    for (let k = 0; k < 10; k++) { const x = r() * S, y = r() * S; put(im, x, y, add(base, -30)); }
    return im;
  };
  // Mineral grains: clusters of small shaded crystals in a palette [dark, mid, light, spark].
  function grains(im, r, pal, clusters, size, alpha) {
    for (let c = 0; c < clusters; c++) {
      const cx = 2 + r() * (S - 4), cy = 2 + r() * (S - 4), n = 3 + Math.floor(r() * 5);
      for (let k = 0; k < n; k++) {
        const x = cx + (r() - 0.5) * size * 2.6, y = cy + (r() - 0.5) * size * 2.6, s = 1 + Math.floor(r() * (size + 1));
        for (let j = 0; j < s; j++) for (let i = 0; i < s; i++) {
          const col = i === 0 && j === 0 ? pal[2] : (i === s - 1 || j === s - 1) ? pal[0] : pal[1];
          alpha ? putA(im, x + i, y + j, col, 255) : put(im, x + i, y + j, col);
        }
        if (r() < 0.4) alpha ? putA(im, x, y, pal[3], 255) : put(im, x, y, pal[3]);
        if (alpha) for (const [dx, dy] of [[s, 0], [0, s]]) if (r() < 0.6) putA(im, x + dx, y + dy, add(pal[0], -20), 200);
      }
    }
  }
  const STONE = [128, 128, 126], DEEP = [70, 70, 76];
  const fullOre = (host, seed, pal, clusters, size) => () => {
    const im = stone(host, seed, host === DEEP);
    grains(im, rng(seed + 7), pal, clusters, size, false);
    return im;
  };
  T.galena_ore = fullOre(STONE, 16201, [[70, 74, 82], [150, 156, 166], [214, 220, 228], [255, 255, 255]], 7, 3);
  T.deepslate_galena_ore = fullOre(DEEP, 16202, [[60, 64, 72], [140, 146, 156], [206, 212, 222], [255, 255, 255]], 7, 3);
  T.pyrite_ore = fullOre(STONE, 16203, [[120, 96, 30], [200, 168, 64], [244, 222, 120], [255, 248, 200]], 8, 2);
  T.deepslate_pyrite_ore = fullOre(DEEP, 16204, [[110, 88, 28], [196, 164, 60], [240, 216, 116], [255, 248, 200]], 8, 2);
  T.uraninite_ore = () => {
    const im = stone(STONE, 16205);
    grains(im, rng(16206), [[16, 16, 18], [40, 40, 44], [70, 72, 76], [90, 92, 96]], 7, 3, false);
    grains(im, rng(16207), [[150, 170, 30], [200, 220, 60], [236, 250, 120], [250, 255, 170]], 6, 1, false);
    return im;
  };
  T.deepslate_uraninite_ore = () => {
    const im = stone(DEEP, 16208, true);
    grains(im, rng(16209), [[12, 12, 14], [34, 34, 38], [60, 62, 66], [80, 82, 86]], 7, 3, false);
    grains(im, rng(16210), [[150, 170, 30], [200, 220, 60], [236, 250, 120], [250, 255, 170]], 6, 1, false);
    return im;
  };
  T.fluorite_ore = () => {
    const im = stone(STONE, 16211);
    grains(im, rng(16212), [[70, 30, 110], [140, 70, 190], [200, 150, 240], [240, 220, 255]], 5, 3, false);
    grains(im, rng(16213), [[30, 100, 70], [70, 170, 120], [150, 230, 180], [220, 255, 230]], 3, 2, false);
    return im;
  };
  T.barite_ore = () => {
    const im = stone([150, 146, 138], 16214);
    const r = rng(16215);
    for (let k = 0; k < 9; k++) { const x = r() * 28, y = r() * 30, w = 3 + r() * 4; for (let i = 0; i < w; i++) { put(im, x + i, y, [236, 234, 226]); put(im, x + i, y + 1, [214, 210, 200]); put(im, x + i, y + 2, [176, 172, 164]); } }
    return im;
  };
  T.bauxite_ore = () => {
    const im = img(S, S), r = rng(16216), n = vnoise(r, S, S, 6);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) put(im, x, y, add(mix([150, 76, 42], [186, 112, 66], n(x, y)), (r() - 0.5) * 12));
    for (let k = 0; k < 26; k++) { const x = r() * S, y = r() * S; put(im, x, y, [214, 150, 96]); put(im, x + 1, y, [120, 58, 32]); put(im, x, y + 1, [130, 64, 36]); }
    return im;
  };
  T.fireclay = () => {
    const im = img(S, S), r = rng(16217), n = vnoise(r, S, S, 8);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) put(im, x, y, add(mix([196, 190, 172], [214, 208, 190], n(x, y)), (r() - 0.5) * 8));
    for (let k = 0; k < 7; k++) { let x = r() * S, y = r() * S; for (let j = 0; j < 7; j++) { put(im, x, y, [150, 144, 128]); x += r() * 2 - 0.4; y += r() * 2 - 1; } }
    return im;
  };
  T.vein_quartz = () => {
    const im = stone(STONE, 16218);
    const r = rng(16219);
    for (let y = 0; y < S; y++) { const c = 14 + Math.sin(y * 0.3) * 4; for (let x = Math.floor(c - 6 + r() * 2); x < c + 6; x++) put(im, x, y, add([232, 232, 226], (r() - 0.5) * 16 + (x % 5 === 0 ? -14 : 0))); }
    return im;
  };
  T['common/metal_host_stone'] = () => stone(STONE, 16220);
  // Strata: laminated cap rock shale, brown reservoir sandstone stained by oil, with gas (paler, dry) and water
  // (grey-blue, wet) variants.
  T.cap_rock_shale = () => {
    const im = img(S, S), r = rng(16231), n = vnoise(r, S, S, 8);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) put(im, x, y, add([66, 66, 70], Math.sin((y + n(x, y) * 3) * 2.2) * 8 + (r() - 0.5) * 8));
    for (let k = 0; k < 4; k++) { const y = Math.floor(r() * S); for (let x = 0; x < S; x++) if (r() < 0.8) put(im, x, y, [44, 44, 48]); }
    return im;
  };
  const sandstone = (base, stain, seed) => () => {
    const im = img(S, S), r = rng(seed), n = vnoise(r, S, S, 8);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      let c = add(base, Math.sin((y + n(x, y) * 5) * 0.9) * 8 + (r() - 0.5) * 14);
      if (stain && n(x * 1.3, y) > 0.6) c = mix(c, stain, 0.5);
      put(im, x, y, c);
    }
    for (let k = 0; k < 40; k++) put(im, r() * S, r() * S, add(base, 30));
    return im;
  };
  T.reservoir_sandstone = sandstone([150, 118, 80], [52, 40, 30], 16241);
  T.reservoir_sandstone_gas = sandstone([186, 160, 118], null, 16242);
  T.reservoir_sandstone_water = sandstone([132, 132, 126], [96, 116, 128], 16243);
  // Overlays for the metal ores (alpha): mineral grains only, laid over metal_host_stone by the ore models.
  const overlay = (seed, pal, clusters, size, extra) => () => {
    const im = img(S, S), r = rng(seed);
    grains(im, r, pal, clusters, size, true);
    if (extra) extra(im, r);
    return im;
  };
  T.hematite_ore_overlay = overlay(16301, [[90, 30, 26], [150, 52, 40], [196, 96, 80], [230, 200, 200]], 8, 3);
  T.magnetite_ore_overlay = overlay(16302, [[14, 14, 16], [44, 44, 48], [96, 98, 104], [200, 204, 210]], 8, 3);
  T.chalcopyrite_ore_overlay = overlay(16303, [[120, 92, 30], [196, 160, 56], [236, 210, 110], [120, 80, 200]], 8, 2,
    (im, r) => { for (let k = 0; k < 8; k++) putA(im, r() * S, r() * S, [80 + r() * 60, 60, 180], 255); });
  T.chromite_ore_overlay = overlay(16304, [[16, 18, 18], [36, 46, 40], [70, 96, 80], [130, 160, 140]], 8, 3);
  T.cassiterite_ore_overlay = overlay(16305, [[30, 20, 14], [70, 46, 30], [120, 90, 60], [230, 220, 200]], 7, 2);
  T.coal_seam_overlay = () => {
    const im = img(S, S), r = rng(16306);
    for (let y = 10; y < 24; y++) for (let x = 0; x < S; x++) {
      const edge = (y === 10 || y === 23) ? r() < 0.6 : true;
      if (edge) putA(im, x, y + Math.round(Math.sin(x * 0.3) * 1.2), add(y % 3 === 0 ? [44, 44, 46] : [22, 22, 24], (r() - 0.5) * 8 + (r() < 0.04 ? 60 : 0)), 255);
    }
    return im;
  };
  T.molybdenite_ore_overlay = overlay(16307, [[60, 64, 70], [120, 126, 136], [176, 184, 196], [230, 236, 244]], 6, 2,
    (im, r) => { for (let k = 0; k < 6; k++) { const x = r() * 28, y = r() * 30; for (let i = 0; i < 4; i++) putA(im, x + i, y, [150, 156, 168], 255); } });
  T.pentlandite_ore_overlay = overlay(16308, [[110, 90, 40], [176, 150, 80], [220, 200, 130], [250, 240, 200]], 8, 2);
  T.pyrolusite_ore_overlay = () => {
    const im = img(S, S), r = rng(16309);
    for (let k = 0; k < 6; k++) {
      let x = r() * S, y = r() * S;
      for (let j = 0; j < 9; j++) { putA(im, x, y, [20, 20, 22], 255); putA(im, x + 1, y, [44, 42, 46], 255); x += (r() - 0.5) * 3; y += r() * 1.6; if (r() < 0.3) putA(im, x + 2, y - 1, [30, 30, 32], 255); }
    }
    return im;
  };
  T.sperrylite_ore_overlay = overlay(16310, [[120, 124, 130], [200, 204, 210], [240, 242, 246], [255, 255, 255]], 6, 1);
  T.sphalerite_ore_overlay = overlay(16311, [[70, 42, 20], [140, 92, 40], [200, 150, 70], [250, 220, 150]], 8, 2);

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }
  window.SURF = { paint, names: Object.keys(T) };
})();
