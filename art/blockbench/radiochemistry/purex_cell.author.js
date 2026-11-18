// PUREX cell face, hand-authored for Blockbench. Replaces the borrowed hot_cell model.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/radiochemistry/purex_cell.author.js', 'utf8'))
//   PUREX.paint('<repo>'); PUREX.build('<repo>', id); PUREX.save('<repo>', id)   for purex_cell, purex_cell_running
//
// One block of the canyon wall seen from the operating gallery, facade north: heavy concrete with a decontaminable
// enamel dado, an oil-filled lead-glass window in its steel frame, two master-slave manipulators through the wall
// above it with their arms and grips hanging on the gallery side, a stainless drip tray, the "cell occupied" lamp
// and the intercom. While the line runs the cell lights are on behind the glass and show the pulsed column.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, writePng } = PCART;
  const mk = () => Object.assign(new PCART.Model(PLANT.MAT, {
    parts: PLANT.PARTS, own: { tex: 'purex_cell/parts', size: 32, rects: { glass: [0, 0, 16, 12], glass_lit: [16, 0, 16, 12] } },
  }), { particle: 'plant/concrete' });

  // Lead glass, 20 px by 14 model units: deep yellow-green, dark when the cell is unlit; lit, the warm cell
  // light with the pulsed column and a pipe rack in silhouette.
  function paint(root) {
    const im = img(32, 32), r = rng(1401);
    for (const lit of [0, 1]) for (let y = 0; y < 12; y++) for (let x = 0; x < 16; x++) {
      let c = lit ? mix([224, 214, 140], [150, 150, 84], y / 12) : mix([46, 58, 40], [26, 32, 24], y / 12);
      if (lit && (x === 5 || x === 6) && y > 1) c = [70, 72, 60];                 // pulsed column
      if (lit && x >= 4 && x <= 7 && (y === 3 || y === 8)) c = [84, 86, 72];     // its pulse leg flanges
      if (lit && y === 10) c = [96, 96, 80];                                      // cell floor
      if (lit && x > 10 && x < 15 && y > 5 && y < 10 && (x + y) % 3 === 0) c = [110, 110, 90];
      if (x === 1 && y > 0 && y < 6) c = add(c, lit ? 26 : 14);                   // reflection
      put(im, lit * 16 + x, y, add(c, (r() - 0.5) * 5));
    }
    writePng(im, path.join(root, PCART.ASSETS, 'textures/block/purex_cell/parts.png'));
    return 1;
  }

  function cell(running) {
    const m = mk();
    m.box('wall', [0, 2.5, 0], [16, 16, 16], 'conc', { hide: ['south'] });
    m.box('dado', [0, 0, -0.2], [16, 2.5, 16], 'light', { hide: ['south'] });
    m.box('frame', [2.6, 5.6, -0.8], [13.4, 12.4, 0], 'frame', {});
    m.decal(running ? 'glow_glass' : 'glass', [3.4, 6.4, -0.9], [12.6, 11.6, -0.8], 'frame', 'north', running ? 'own.glass_lit' : 'own.glass',
      { hide: ['south', 'east', 'west', 'up', 'down'] });
    for (const [x, y] of [[3, 6], [12.6, 6], [3, 11.6], [12.6, 11.6]]) m.box(`frame_bolt_${x}_${y}`, [x, y, -1], [x + 0.4, y + 0.4, -0.8], 'st', { split: '' });
    // master-slave manipulators: wall sleeves over the window, master arms, grips
    for (const x of [4.6, 10.2]) {
      m.box(`sleeve_${x}`, [x, 13, -1.2], [x + 1.6, 14.6, 0], 'frame', { split: '' });
      m.box(`through_tube_${x}`, [x + 0.3, 13.3, -3.6], [x + 1.3, 14.3, -1.2], 'bright', { split: '' });
      m.box(`elbow_${x}`, [x + 0.2, 12.6, -4.2], [x + 1.4, 14.6, -3.4], 'st', { split: '' });
      m.box(`arm_${x}`, [x + 0.45, 7.2, -4], [x + 1.15, 12.6, -3.6], 'bright', { split: '' });
      m.box(`grip_${x}`, [x + 0.2, 5.8, -4.3], [x + 1.4, 7.2, -3.3], 'rubber', { split: '' });
      m.box(`trigger_${x}`, [x + 0.5, 6.2, -4.6], [x + 1.1, 6.8, -4.3], 'p_steam', { split: '' });
    }
    m.box('drip_tray', [2, 0, -4.8], [14, 0.6, 0], 'bright', { split: '' });
    m.box('tray_lip', [2, 0.6, -4.8], [14, 1, -4.5], 'bright', { split: '' });
    // occupied lamp, intercom, the radiation sign and the cell tag
    m.box('lamp_box', [0.8, 13, -0.6], [2.4, 14.6, 0], 'light', { split: '' });
    m.decal(running ? 'glow_lamp' : 'lamp', [1.2, 13.4, -0.7], [2, 14.2, -0.6], 'light', 'north', running ? 'lamp_amber' : 'lamp_green',
      { hide: ['south'] });
    m.box('intercom', [13.8, 8.4, -0.6], [15.4, 11, 0], 'light', { split: '' });
    m.decal('intercom_grille', [14, 9.6, -0.7], [15.2, 10.8, -0.6], 'light', 'north', 'vent', { hide: ['south'] });
    m.decal('trefoil', [13.8, 12.6, -0.2], [15.6, 14.4, 0], 'conc', 'north', 'trefoil', { hide: ['south'] });
    m.decal('cell_tag', [5.6, 3.4, -0.3], [10.4, 4.8, -0.2], 'light', 'north', 'tag', { hide: ['south'] });
    return m;
  }

  const BUILDS = { purex_cell: { model: () => cell(false) }, purex_cell_running: { model: () => cell(true) } };
  window.PUREX = {
    paint,
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'radiochemistry', id, BUILDS[id].java),
  };
})();
