// MES server: a 42U 19-inch rack, hand-authored for Blockbench. Replaces the borrowed historian_server model.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/automation/mes_server.author.js', 'utf8'))
//   MES.paint('<repo>'); MES.build('<repo>', id); MES.save('<repo>', id)   for mes_server, mes_server_busy
//
// 1x2x1 (local y 0..32), controller below, facade north. Grey enamel cabinet on a plinth with a perforated front
// door, seen through it: patch panel, network switch, database and application servers, the KVM drawer, a tape
// library and the UPS at the bottom; cable tray on the roof. While the system runs the rack is lit from within.
(function () {
  const path = require('path');
  const { rng, img, put, add, rect, writePng } = PCART;
  const mk = () => Object.assign(new PCART.Model(Object.assign({}, PLANT.MAT, { rack: { tex: 'mes_server/cabinet', P: 16 } }), {
    parts: PLANT.PARTS, own: { tex: 'mes_server/front', size: 64, rects: { idle: [0, 0, 24, 56], busy: [32, 0, 24, 56] } },
  }), { particle: 'mes_server/cabinet' });

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/block/mes_server');
    const cab = img(32, 32), r = rng(1701);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(cab, x, y, add([122, 126, 128], (r() - 0.5) * 6));
    for (let y = 2; y < 32; y += 4) for (let x = 3; x < 29; x += 3) put(cab, x, y, [70, 72, 74]);   // side vents
    writePng(cab, path.join(dir, 'cabinet.png'));
    // rack front, 24 x 56 px (12 x 28 units; 1U is about 1.4 px), idle at x 0 and busy at x 32
    const fr = img(64, 64);
    for (const busy of [0, 1]) {
      const q = rng(1702), ox = busy * 32;
      const led = (x, y, on, col) => put(fr, ox + x, y, on ? col : [40, 44, 40]);
      rect(fr, ox, 0, 24, 56, [22, 23, 25]);
      for (let y = 0; y < 56; y++) { put(fr, ox, y, [92, 94, 96]); put(fr, ox + 23, y, [92, 94, 96]); }   // rails
      let y = 1;
      const unit = (h, base, fn) => { rect(fr, ox + 1, y, 22, h, base); for (let x = 1; x < 23; x++) put(fr, ox + x, y, add(base, 20)); fn(y, h); y += h + 1; };
      unit(2, [52, 54, 58], (y0) => { for (let x = 3; x < 21; x += 2) put(fr, ox + x, y0 + 1, [190, 190, 190]); });          // patch panel
      unit(2, [40, 46, 60], (y0) => { for (let x = 3; x < 19; x += 2) led(x, y0 + 1, busy && q() < 0.8, q() < 0.7 ? [90, 255, 110] : [255, 190, 60]); });
      for (let k = 0; k < 6; k++) unit(3, [70, 72, 76], (y0) => {                                                             // 2U servers
        for (let x = 2; x < 16; x += 3) rect(fr, ox + x, y0 + 1, 2, 1, [46, 48, 50]);
        led(19, y0 + 1, busy, [90, 160, 255]); led(21, y0 + 1, busy && q() < 0.6, [90, 255, 110]);
      });
      unit(2, [30, 30, 32], (y0) => { rect(fr, ox + 8, y0 + 1, 8, 1, [60, 62, 64]); });                                     // KVM drawer
      unit(6, [64, 66, 70], (y0) => {                                                                                         // tape library
        rect(fr, ox + 3, y0 + 1, 12, 4, [26, 28, 30]); for (let x = 4; x < 14; x += 2) rect(fr, ox + x, y0 + 2, 1, 2, [150, 120, 40]);
        led(18, y0 + 2, busy, [90, 255, 110]);
      });
      while (y < 45) unit(1, [44, 46, 48], () => {});                                                                       // blanks
      unit(9, [46, 48, 50], (y0) => {                                                                                         // UPS
        rect(fr, ox + 4, y0 + 2, 8, 3, busy ? [120, 200, 150] : [40, 60, 50]);
        for (let x = 4; x < 12; x++) if (busy && q() < 0.5) put(fr, ox + x, y0 + 3, [30, 60, 40]);
        led(15, y0 + 3, true, busy ? [90, 255, 110] : [255, 190, 60]);
        for (let x = 2; x < 22; x += 2) put(fr, ox + x, y0 + 7, [30, 30, 32]);
      });
      // perforated door: a light hex mesh over everything, the handle
      for (let yy = 0; yy < 56; yy++) for (let x = 0; x < 24; x++) if ((x + (yy % 2)) % 3 === 0 && (yy % 2 === 0)) put(fr, ox + x, yy, add(get3(fr, ox + x, yy), 26));
      rect(fr, ox + 21, 24, 1, 6, [170, 172, 174]);
    }
    writePng(fr, path.join(dir, 'front.png'));
    return 2;
  }
  const get3 = (im, x, y) => { const i = (y * im.w + x) * 4; return [im.d[i], im.d[i + 1], im.d[i + 2]]; };

  function rack(busy) {
    const m = mk();
    m.box('plinth', [1, 0, 1.5], [15, 1, 14.5], 'frame', {});
    m.box('cabinet', [1, 1, 1.5], [15, 30.5, 14.5], 'rack', {});
    m.box('top', [0.8, 30.5, 1.3], [15.2, 31, 14.7], 'frame', {});
    m.box('door_frame', [1, 1, 1.2], [15, 30.5, 1.5], 'frame', { hide: ['south'] });
    m.decal(busy ? 'glow_front' : 'front', [2, 1.6, 1.1], [14, 29.6, 1.2], 'frame', 'north', busy ? 'own.busy' : 'own.idle',
      { hide: ['south', 'east', 'west', 'up', 'down'] });
    for (const y of [4, 26]) m.box(`hinge_${y}`, [0.7, y, 1.2], [1.1, y + 1.4, 1.8], 'st', { split: '' });
    m.decal('tag', [5, 30.6, 1.2], [11, 31.1, 1.3], 'frame', 'north', 'tag', { hide: ['south'] });
    // cable tray on the roof with the bundle dropping in at the back
    m.box('tray', [3, 31, 3], [13, 31.6, 13], 'frame', { split: '' });
    for (const x of [3, 12.6]) m.box(`tray_side_${x}`, [x, 31.6, 3], [x + 0.4, 32, 13], 'frame', { split: '' });
    m.box('cables', [4.5, 31.6, 4], [11, 32, 12.4], 'blue', { split: '' });
    m.box('cable_drop', [6, 30.5, 12.4], [10, 32, 13.6], 'rubber', { split: '' });
    m.box('earth_bar', [13, 2, 14.5], [14.4, 4, 14.8], 'p_water', { split: '' });
    return m;
  }

  const BUILDS = { mes_server: { model: () => rack(false) }, mes_server_busy: { model: () => rack(true) } };
  window.MES = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'automation', id, BUILDS[id].java),
  };
})();
