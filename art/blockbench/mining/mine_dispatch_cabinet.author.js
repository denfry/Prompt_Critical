// Mine gas protection and dispatch cabinet (AGZ "Metan" type), hand-authored for Blockbench (replaces the
// spec_metallurgy.py placeholder), plus its dispatcher panel board.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/mining/mine_dispatch_cabinet.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); MDC.paint('<repo>'); MDC.paintGui('<repo>')
//   await MDC.exportAll('<repo>')
//
// 1x2x1 blocks, model x 0..16, y 0..32, z 0..16, front to the north. A floor-standing steel cabinet on a
// plinth: the upper door carries the gas board behind glass (methane, carbon monoxide and oxygen read-outs
// over a mimic of the workings), the lower door is louvred for the power supply. The alarm beacon sits on the
// roof and the horn on the side; in the alarm model the beacon glows and the board shows the methane trip.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/mine_dispatch_cabinet';
  const T = {};

  // Cabinet enamel, light grey-blue as control gear is painted, scuffed round the handles.
  T.enamel = () => {
    const im = img(32, 32), r = grain(im, [150, 160, 166], 1701, 3, 4, 8);
    for (let k = 0; k < 10; k++) put(im, Math.floor(r() * 32), Math.floor(r() * 32), [104, 108, 110]);
    return im;
  };
  // Seven-segment digit at (x, y), 3x5, lit segments in `on`.
  const SEG = { 0: 'abcdef', 1: 'bc', 2: 'abdeg', 3: 'abcdg', 4: 'bcfg', 5: 'acdfg', 6: 'acdefg', 7: 'abc', 8: 'abcdefg', 9: 'abcdfg' };
  function digit(im, x, y, d, on, off) {
    const s = SEG[d], seg = (k, pts) => pts.forEach(([i, j]) => put(im, x + i, y + j, s.includes(k) ? on : off));
    seg('a', [[0, 0], [1, 0], [2, 0]]); seg('d', [[0, 4], [1, 4], [2, 4]]); seg('g', [[0, 2], [1, 2], [2, 2]]);
    seg('f', [[0, 1]]); seg('b', [[2, 1]]); seg('e', [[0, 3]]); seg('c', [[2, 3]]);
  }
  // Gas board 32x32 behind the glass: three read-outs (CH4 %, CO ppm, O2 %) with their lamps, and a mimic of
  // a mine (shaft, main roadways, faces) with sensor points. `alarm` puts the methane over the trip and lights
  // the red lamp and the sensor in the face.
  function board(alarm) {
    const im = img(32, 32), r = rng(alarm ? 1712 : 1711);
    grain(im, [36, 40, 44], 1713, 2, 2, 8);
    const rows = [[alarm ? [1, 3, 2] : [0, 6, 4], [255, 70, 50]], [[0, 0, 7], [255, 170, 40]], [[2, 0, 9], [110, 240, 120]]];
    rows.forEach(([digits, col], k) => {
      const y = 2 + k * 7;
      rect(im, 2, y - 1, 15, 7, [14, 12, 12]);
      digits.forEach((d, i) => digit(im, 3 + i * 4, y, d, col, mix(col, [14, 12, 12], 0.93)));
      put(im, 6, y + 4, col);                                                  // decimal point
      rect(im, 19, y + 1, 3, 3, k === 0 && alarm ? [255, 60, 40] : [70, 30, 28]); // lamp
      rect(im, 24, y + 1, 6, 3, [180, 178, 170]);                              // label plate
    });
    // mimic: shaft down the left, roadways and two faces, sensor points
    const ln = [200, 196, 186];
    rect(im, 3, 22, 1, 8, ln); rect(im, 3, 29, 26, 1, ln); rect(im, 3, 24, 22, 1, ln);
    rect(im, 24, 24, 1, 6, [220, 180, 60]); rect(im, 14, 24, 1, 6, [220, 180, 60]);
    for (const [x, y] of [[8, 24], [19, 29], [24, 26]]) put(im, x, y, [110, 240, 120]);
    put(im, 14, 27, alarm ? [255, 60, 40] : [110, 240, 120]);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) if ((x + y) % 11 === 0 && r() < 0.5) {
      const i = (y * 32 + x) * 4; put(im, x, y, add([im.d[i], im.d[i + 1], im.d[i + 2]], 18));          // glass glints
    }
    return im;
  }
  T.board = () => board(false);
  T.board_alarm = () => board(true);
  // Louvres of the power supply door.
  T.louvre = () => {
    const im = img(32, 32), r = grain(im, [150, 160, 166], 1721, 3, 4, 8);
    for (let y = 2; y < 32; y += 4) { rect(im, 3, y, 26, 1, [60, 64, 66]); rect(im, 3, y + 1, 26, 1, [196, 204, 208]); }
    return im;
  };
  // Beacon lens, red; dark when quiet, bright when the alarm is on (drawn bright: the alarm element glows).
  T.lens = () => { const im = img(16, 16); grain(im, [210, 40, 30], 1731, 4, 6, 4); for (let x = 0; x < 16; x += 4) rect(im, x, 0, 1, 16, [255, 120, 100]); return im; };
  T.lens_off = () => { const im = img(16, 16); grain(im, [96, 26, 22], 1741, 3, 4, 4); for (let x = 0; x < 16; x += 4) rect(im, x, 0, 1, 16, [120, 50, 44]); return im; };

  const MAT = {
    en: { tex: 'mine_dispatch_cabinet/enamel', P: 16 }, lv: { tex: 'mine_dispatch_cabinet/louvre', P: 16 },
    lens: { tex: 'mine_dispatch_cabinet/lens', P: 16 }, loff: { tex: 'mine_dispatch_cabinet/lens_off', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'mine_dispatch_cabinet/enamel' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  function cabinet(alarm) {
    const m = mk();
    m.box('plinth', [0.4, 0, 2.4], [15.6, 1.2, 15.6], 'ci', { split: '' });
    m.box('body', [0, 1.2, 2], [16, 30, 16], 'en');
    m.box('roof', [-0.3, 30, 1.7], [16.3, 30.6, 16.3], 'en', { split: '' });
    // upper door with the gas board behind glass, hinges and the cylinder lock
    m.box('door_upper', [0.6, 15, 1.4], [15.4, 29.4, 2], 'en', { split: '' });
    m.plate('board', [2, 16.4, 1.3], [14, 28.4, 1.4], 'en', 'north', alarm ? 'mine_dispatch_cabinet/board_alarm' : 'mine_dispatch_cabinet/board', { hide: ['south'] });
    for (const [n, a, b, c, d] of [['t', 1.6, 28.4, 14.4, 28.8], ['b', 1.6, 16, 14.4, 16.4]]) m.box(`bezel_${n}`, [a, b, 1.1], [c, d, 1.4], 'ci', { split: '' });
    for (const [n, a, c] of [['l', 1.6, 2], ['r', 14, 14.4]]) m.box(`bezel_${n}`, [a, 16.4, 1.1], [c, 28.4, 1.4], 'ci', { split: '' });
    // lower door, louvred, with a handle and a lock
    m.box('door_lower', [0.6, 1.8, 1.4], [15.4, 14.4, 2], 'lv', { split: '', face: { north: { tex: 'mine_dispatch_cabinet/louvre', uv: [0, 0, 16, 16] } } });
    m.box('handle', [13, 6, 0.6], [13.8, 10.4, 1.4], 'bright', { split: '' });
    m.box('lock_upper', [13, 21.6, 0.9], [13.8, 22.4, 1.4], 'br', { split: '' });
    for (const y of [3, 12, 17, 27]) m.box(`hinge_${y}`, [0.2, y, 1.2], [0.8, y + 1.4, 2], 'st', { split: '' });
    // cable glands along the bottom of the east side
    for (const z of [5, 8, 11]) m.prism(`gland_${z}`, 'x', [3.4, z], 0.8, 16, 16.8, 'br', { split: '', ends: ['east'] });
    // horn on the west side, a short trumpet facing front
    m.box('horn_body', [-1.6, 24, 6], [0, 26.4, 9], 'en', { split: '' });
    m.prism('horn_bell', 'z', [-0.8, 25.2], 1.6, 3.6, 6, 'en', { split: '', ends: ['north'] });
    // beacon on the roof: base, red lens in a guard
    m.prismY('beacon_base', [8, 9], 1.8, 30.6, 31.2, 'st', { ends: ['up'] });
    m.prismY(alarm ? 'glow_beacon' : 'beacon', [8, 9], 1.4, 31.2, 33.6, alarm ? 'lens' : 'loff', { ends: ['up'] });
    for (const [dx, dz] of [[1.6, 0], [-1.6, 0], [0, 1.6], [0, -1.6]]) {
      m.box(`beacon_guard_${dx}_${dz}`, [8 + dx - 0.15, 31.2, 9 + dz - 0.15], [8 + dx + 0.15, 33.8, 9 + dz + 0.15], 'st', { split: '' });
    }
    return m;
  }

  // Dispatcher panel board for MinePanels.CABINET (230x156): rings behind the three gas dials, a niche for the
  // sump glass, a lamp strip and a strip for the two read-outs.
  function guiCabinet() {
    const W = 230, H = 156, im = img(W, H), r = rng(1751);
    grain(im, [58, 66, 76], 1752, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    const ring = (cx, cy, R) => { for (let a = 0; a < 360; a += 0.4) { const t = a * Math.PI / 180; put(im, cx + R * Math.cos(t), cy + R * Math.sin(t), [30, 36, 44]); put(im, cx + (R + 1) * Math.cos(t) + 0.5, cy + (R + 1) * Math.sin(t) + 0.5, [108, 120, 134]); } };
    ring(40, 56, 34); ring(104, 48, 26); ring(160, 48, 26);
    const plate = (x0, y0, x1, y1, base) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const edge = x === x0 || y === y0 ? -22 : x === x1 - 1 || y === y1 - 1 ? 18 : 0;
        put(im, x, y, add(base, edge + (r() - 0.5) * 4));
      }
    };
    plate(198, 20, W - 6, 102, [46, 52, 60]);       // sump glass niche
    plate(4 + 72, 84, 196, 118, [48, 56, 64]);       // lamp strip
    plate(6, 100, 70, 118, [48, 56, 64]);            // link lamp
    plate(6, 120, W - 6, 152, [40, 46, 52]);         // read-outs
    return im;
  }

  function paintGui(root) {
    writePng(guiCabinet(), path.join(root, PCART.ASSETS, 'textures/gui/panel/mine_dispatch_cabinet.png'));
    return 1;
  }

  const BUILDS = {
    mine_dispatch_cabinet: { model: () => cabinet(false) },
    mine_dispatch_cabinet_alarm: { model: () => cabinet(true) },
  };

  window.MDC = {
    paint, paintGui,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    exportAll: (root) => PCART.exportAll(root, 'mining', BUILDS),
    preview(root) { return PCART.realize(root, cabinet(false).merge(cabinet(true), 20, 0, 0, 'a_'), 'mine_dispatch_cabinet_preview'); },
  };
})();
