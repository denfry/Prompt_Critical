// Containment of the PWR (04c, M10), hand-authored for Blockbench: the containment wall block (replaces the
// spec_base.py cube) and the passive autocatalytic hydrogen recombiner (replaces the spec_plant.py box).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/containment.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CNT.paint('<repo>')
//   build + save each model in turn (CNT.build / CNT.save, ids in CNT.ids)
//
// containment_wall: a full block of prestressed concrete, laid by the player in walls of any size, so every
// face carries the same 16-unit tile. It shows the formwork board marks, a tie hole and the sheath of a
// prestressing tendon running across. The steel liner is on the inside of a real containment and is not
// shown on a block that may face either way.
// hydrogen_recombiner: a PAR of the type fitted to VVER containments after Fukushima (AREVA/Framatome FR90,
// the Russian RVK). It is an open-bottomed stainless box, and the catalyst plates stand in it on edge, seen
// through the inlet. The hydrogen-laden air rises through the plates, recombines with no power at all and
// leaves hot through the louvred hood. The box hangs from a wall bracket at the back, front to the north.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, grain, writePng } = PCART;
  const T = {};

  // Containment concrete, 16-unit tile: board marks every 4 px, a tie hole, a tendon sheath across the middle.
  T['containment_wall/concrete'] = () => {
    const im = img(32, 32), r = grain(im, [150, 148, 140], 3901, 5, 6, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = get(im, x, y);
      if (y % 8 === 7) c = add(c, -10);
      if (y % 8 === 0) c = add(c, 5);
      if (x === 0 || y === 0) c = add(c, -14);
      put(im, x, y, c);
    }
    for (let x = 0; x < 32; x++) if (r() < 0.5) put(im, x, 15, mix(get(im, x, 15), [120, 116, 108], 0.4));
    put(im, 8, 4, [70, 68, 64]); put(im, 9, 4, [96, 94, 88]); put(im, 24, 20, [70, 68, 64]); put(im, 25, 20, [96, 94, 88]);
    for (let k = 1; k < 6; k++) if (r() < 0.7) put(im, 24, 20 + k, mix(get(im, 24, 20 + k), [120, 92, 70], 0.35));
    return im;
  };
  // Recombiner housing: brushed stainless with the rating plate.
  T['hydrogen_recombiner/housing'] = () => {
    const im = img(32, 32), r = rng(3911);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([170, 174, 178], Math.sin(y * 1.7) * 3 + (r() - 0.5) * 8));
    return im;
  };
  // Catalyst plates on edge, platinum/palladium on a steel sheet: dark grey, seen end-on between gaps.
  T['hydrogen_recombiner/plates'] = () => {
    const im = img(16, 16), r = rng(3921);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, x % 3 === 0 ? add([64, 66, 70], (r() - 0.5) * 10) : [22, 22, 24]);
    return im;
  };
  // Outlet louvres.
  T['hydrogen_recombiner/louvre'] = () => {
    const im = img(16, 16), r = rng(3931);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 === 0 ? [40, 40, 42] : y % 4 === 1 ? [196, 200, 204] : add([160, 164, 168], (r() - 0.5) * 8));
    return im;
  };
  // Plate: type, the warning not to paint or cover it.
  T['hydrogen_recombiner/plate'] = () => {
    const im = img(16, 8), r = rng(3941);
    for (let y = 0; y < 8; y++) for (let x = 0; x < 16; x++) put(im, x, y, x === 0 || y === 0 || x === 15 || y === 7 ? [40, 40, 40] : [222, 214, 190]);
    for (const y of [2, 4]) for (let x = 2; x < 14; x++) if (r() < 0.7) put(im, x, y, [60, 58, 54]);
    for (let x = 2; x < 8; x++) put(im, x, 6, [180, 40, 30]);
    return im;
  };

  const MAT = {
    cw: { tex: 'containment_wall/concrete', P: 16 }, hs: { tex: 'hydrogen_recombiner/housing', P: 16 },
    cat: { tex: 'hydrogen_recombiner/plates', P: 16 }, lv: { tex: 'hydrogen_recombiner/louvre', P: 16 },
  };
  const mk = (p) => Object.assign(new PCART.Model(MAT, {}), { particle: p });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function wall() {
    const m = mk('containment_wall/concrete');
    m.box('wall', [0, 0, 0], [16, 16, 16], 'cw');
    return m;
  }

  function recombiner() {
    const m = mk('hydrogen_recombiner/housing');
    const full = (t) => ({ tex: t, uv: [0, 0, 16, 16] });
    // wall bracket and its back plate
    m.box('back_plate', [4, 3, 15], [12, 15, 16], 'ci', { split: '' });
    m.box('bracket_top', [6, 12, 13], [10, 13, 15], 'ci', { split: '' });
    m.box('bracket_bottom', [6, 5, 13], [10, 6, 15], 'ci', { split: '' });
    // housing: four walls, open at the bottom; catalyst plates inside seen from below and through the inlet
    m.box('wall_n', [2, 4, 3], [14, 13, 3.6], 'hs', { split: '' });
    m.box('wall_s', [2, 4, 12.4], [14, 13, 13], 'hs', { split: '' });
    m.box('wall_w', [2, 4, 3.6], [2.6, 13, 12.4], 'hs', { split: '' });
    m.box('wall_e', [13.4, 4, 3.6], [14, 13, 12.4], 'hs', { split: '' });
    m.box('inlet_lip', [1.6, 3.4, 2.6], [14.4, 4, 13.4], 'hs', { split: '', hide: ['up'] });
    m.box('catalyst', [2.6, 5, 3.6], [13.4, 9.6, 12.4], 'cat', { split: '', face: { down: full('hydrogen_recombiner/plates'), north: full('hydrogen_recombiner/plates') } });
    m.box('inlet_window', [3.4, 4.4, 2.9], [12.6, 6.4, 3.0], 'cat', { split: '', hide: ['up', 'down', 'south', 'east', 'west'], face: { north: full('hydrogen_recombiner/plates') } });
    // hood with louvres to the front, sides and back
    m.box('hood', [1.6, 13, 2.6], [14.4, 15.4, 13.4], 'lv', { split: '', face: { up: 'hs', down: 'hs' } });
    m.box('hood_top', [1.2, 15.4, 2.2], [14.8, 16, 13.8], 'hs', { split: '' });
    m.box('rating_plate', [5, 9.6, 2.8], [11, 12.6, 3.0], 'hs', { split: '', face: { north: { tex: 'hydrogen_recombiner/plate', uv: [0, 0, 16, 16] } } });
    return m;
  }

  const BUILDS = {
    containment_wall: { model: wall, cat: 'core' },
    hydrogen_recombiner: { model: recombiner, cat: 'safety' },
  };

  window.CNT = {
    paint, ids: Object.keys(BUILDS),
    build(root, id) {
      const p = PCART.realize(root, BUILDS[id].model(), id);
      // walls are built of many blocks: faces on the block boundary cull against the neighbour
      if (id === 'containment_wall') for (const c of Cube.all) for (const d of Object.keys(c.faces)) c.faces[d].cullface = d;
      return p;
    },
    save: (root, id) => PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, wall().merge(recombiner(), 20, 0, 0, 'r_'), 'containment_preview'); },
  };
})();
