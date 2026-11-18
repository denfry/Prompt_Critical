// Plant fittings of the decor set, hand-authored for Blockbench (replace the hq_decor.py and hq_lighting.py
// models): the ShR distribution cabinet, the steel conduit (along and up the wall), the I-beam column, the gas
// cylinder rack, the first-aid box and the NPP "plate" bulkhead light (lit and off).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/decor/plant_fittings.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLF.paint('<repo>')
//   build + save each model in turn (PLF.build / PLF.save, ids in PLF.ids)
//
// 1x1x1, front to the north; wall fittings sit against the south face (z 16) as the old models did, so the
// blocks keep their placement: cabinet z 0.5..16, conduit axis z 15, first-aid box z 14..16, lamp base z 14.5..16.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};
  const ink = [28, 28, 30];

  // Cabinet enamel: Soviet electrical grey (RAL 7032-like), a little grimy at the bottom of each tile.
  T['plant_fittings/enamel'] = () => {
    const im = img(32, 32), r = grain(im, [168, 170, 158], 3801, 3, 4, 8);
    for (let k = 0; k < 8; k++) put(im, r() * 32, 24 + r() * 8, [120, 118, 108]);
    return im;
  };
  // Cabinet doors (32 px = 16 units): two leaves with the seam, louvres top and bottom, latches, the yellow
  // "high voltage" triangle with the black arrow and the white schedule plate.
  T['plant_fittings/cabinet_door'] = () => {
    const im = img(32, 32), r = grain(im, [168, 170, 158], 3811, 3, 4, 8);
    for (let i = 0; i < 32; i++) { put(im, i, 0, [200, 202, 192]); put(im, 0, i, [200, 202, 192]); put(im, i, 31, [110, 110, 102]); put(im, 31, i, [110, 110, 102]); }
    for (let y = 1; y < 31; y++) { put(im, 15, y, [96, 96, 90]); put(im, 16, y, [200, 202, 192]); }
    for (const [x0, y0] of [[4, 3], [20, 3], [4, 22], [20, 22]]) for (let k = 0; k < 3; k++) {
      rect(im, x0, y0 + k * 2, 8, 1, [70, 70, 66]); rect(im, x0, y0 + k * 2 + 1, 8, 1, [196, 198, 188]);
    }
    rect(im, 12, 14, 1, 5, ink); rect(im, 19, 14, 1, 5, ink);
    for (let k = 0; k < 7; k++) { rect(im, 5 + k * 0.5, 17 - k, 7 - k, 1, [228, 196, 40]); }
    for (let k = 0; k < 7; k++) { put(im, 5 + k * 0.5, 17 - k, ink); put(im, 11 - k * 0.5, 17 - k, ink); }
    rect(im, 5, 17, 7, 1, ink); put(im, 8, 13, ink); put(im, 9, 14, ink); put(im, 8, 15, ink); put(im, 9, 16, ink);
    rect(im, 21, 13, 8, 5, [234, 232, 220]); for (let y = 14; y < 17; y += 2) rect(im, 22, y, 6, 1, [90, 90, 90]);
    return im;
  };
  // Galvanised conduit and fixings.
  T['plant_fittings/zinc'] = () => {
    const im = img(16, 16), r = grain(im, [150, 156, 160], 3821, 5, 6, 4);
    for (let k = 0; k < 10; k++) put(im, r() * 16, r() * 16, [190, 196, 198]);
    return im;
  };
  // Rolled steel of the column: dark mill-scale primer, a hazard band low down (yellow-black, 2 px stripes).
  T['plant_fittings/beam'] = () => {
    const im = img(32, 32), r = grain(im, [84, 70, 62], 3831, 3, 6, 8);
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [110, 70, 46]);
    return im;
  };
  T['plant_fittings/beam_hazard'] = () => {
    const im = img(32, 32), r = grain(im, [84, 70, 62], 3841, 3, 6, 8);
    for (let y = 26; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(((x + y) >> 2) % 2 ? [30, 30, 30] : [222, 180, 40], (r() - 0.5) * 8));
    for (let y = 2; y < 24; y += 8) { rect(im, 3, y, 2, 2, [112, 98, 88]); rect(im, 27, y, 2, 2, [112, 98, 88]); }
    return im;
  };
  // Gas cylinders by GOST 949 colours: oxygen sky blue, acetylene white, propane red; shoulder lettering band.
  for (const [n, c] of [['oxygen', [92, 150, 206]], ['acetylene', [226, 226, 220]], ['propane', [176, 40, 34]]]) {
    T[`plant_fittings/cyl_${n}`] = () => {
      const im = img(16, 16), r = grain(im, c, 3851 + c[0], 3, 4, 4);
      rect(im, 0, 2, 16, 2, n === 'acetylene' ? [180, 40, 34] : [234, 234, 230]);
      for (let k = 0; k < 4; k++) put(im, r() * 16, 8 + r() * 8, add(c, -40));
      return im;
    };
  }
  // First-aid box: white enamel with the red cross.
  T['plant_fittings/first_aid'] = () => {
    const im = img(16, 16), r = grain(im, [232, 232, 226], 3861, 2, 3, 4);
    rect(im, 6, 3, 4, 10, [196, 36, 30]); rect(im, 3, 6, 10, 4, [196, 36, 30]);
    for (let i = 0; i < 16; i++) { put(im, i, 15, [180, 180, 174]); put(im, 15, i, [180, 180, 174]); }
    return im;
  };
  // Lamp glass seen end on (radial), prismatic rings; warm when lit.
  for (const [n, c] of [['glass_lit', [255, 236, 170]], ['glass_off', [176, 178, 168]]]) {
    T[`plant_fittings/${n}`] = () => {
      const im = img(16, 16), r = rng(3871);
      for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
        const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8);
        put(im, x, y, add(c, (Math.round(d) % 3 === 0 ? -24 : 0) + (r() - 0.5) * 6 + (d < 3 ? 12 : 0)));
      }
      return im;
    };
  }
  // Cast lamp base, black.
  T['plant_fittings/cast_black'] = () => { const im = img(16, 16); grain(im, [40, 40, 42], 3881, 4, 4, 4); return im; };

  const MAT = {
    en: { tex: 'plant_fittings/enamel', P: 16 }, zn: { tex: 'plant_fittings/zinc', P: 16 }, bm: { tex: 'plant_fittings/beam', P: 16 },
    bmh: { tex: 'plant_fittings/beam_hazard', P: 16 }, fa: { tex: 'plant_fittings/first_aid', P: 16 }, cb: { tex: 'plant_fittings/cast_black', P: 16 },
    ox: { tex: 'plant_fittings/cyl_oxygen', P: 16 }, ac: { tex: 'plant_fittings/cyl_acetylene', P: 16 }, pr: { tex: 'plant_fittings/cyl_propane', P: 16 },
    gl1: { tex: 'plant_fittings/glass_lit', P: 16 }, gl0: { tex: 'plant_fittings/glass_off', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function cabinet() {
    const m = mk('plant_fittings/enamel');
    m.box('plinth', [0, 0, 0.5], [16, 1, 15.5], 'soot');
    m.plate('body', [0, 1, 0.5], [16, 16, 16], 'en', 'north', 'plant_fittings/cabinet_door');
    for (const y of [3, 12]) m.box(`hinge_${y}`, [15.6, y, 0.2], [16, y + 2, 0.5], 'st', { split: '' });
    m.box('handle_w', [6.2, 7.2, 0], [6.8, 9.6, 0.5], 'soot', { split: '' });
    m.box('handle_e', [9.2, 7.2, 0], [9.8, 9.6, 0.5], 'soot', { split: '' });
    m.box('gland_plate', [3, 15.6, 6], [13, 16, 12], 'ci', { split: '' });
    return m;
  }

  function conduit(vertical) {
    const m = mk('plant_fittings/zinc');
    if (vertical) {
      m.prismY('pipe', [8, 15], 0.8, 0, 16, 'zn');
      for (const y of [2, 13]) m.box(`clip_${y}`, [6.5, y, 14.5], [9.5, y + 1, 16], 'st', { split: '' });
      m.box('coupling', [7, 7.4, 14], [9, 8.6, 16], 'zn', { split: '' });
    } else {
      m.prism('pipe', 'x', [8, 15], 0.8, 0, 16, 'zn');
      for (const x of [2, 13]) m.box(`clip_${x}`, [x, 6.5, 14.5], [x + 1, 9.5, 16], 'st', { split: '' });
      m.box('coupling', [7.4, 7, 14], [8.6, 9, 16], 'zn', { split: '' });
    }
    return m;
  }

  function column() {
    const m = mk('plant_fittings/beam');
    for (const z of [3, 12]) m.box(`flange_${z}`, [3, 0, z], [13, 16, z + 1], 'bm', { face: { north: 'bmh', south: 'bmh' } });
    m.box('web', [7.5, 0, 4], [8.5, 16, 12], 'bm');
    m.box('stiffener', [4, 8, 4], [12, 8.5, 12], 'bm', { split: '' });
    m.box('base_plate', [2, 0, 2], [14, 0.6, 14], 'ci', { split: '' });
    for (const [x, z] of [[2.6, 2.6], [12.4, 2.6], [2.6, 12.4], [12.4, 12.4]]) m.box(`anchor_${x}_${z}`, [x - 0.4, 0.6, z - 0.4], [x + 0.4, 1.2, z + 0.4], 'st', { split: '' });
    return m;
  }

  function cylinders() {
    const m = mk('plant_fittings/zinc');
    m.box('back', [0, 0, 14.5], [16, 15, 16], 'zn');
    m.box('floor', [0, 0, 7], [16, 0.5, 14.5], 'zn', { split: '' });
    for (const [x, mat] of [[3, 'ox'], [8, 'ac'], [13, 'pr']]) {
      m.prismY(`cyl_${mat}`, [x, 10.5], 2, 0.5, 12, mat);
      m.prismY(`shoulder_${mat}`, [x, 10.5], 1.4, 12, 13, mat, { cap: 'plant_fittings/glass_off' });
      m.prismY(`valve_${mat}`, [x, 10.5], 0.5, 13, 14.5, 'br');
    }
    m.box('chain', [0.5, 9, 8.2], [15.5, 9.4, 8.5], 'st', { split: '' });
    return m;
  }

  function firstAid() {
    const m = mk('plant_fittings/first_aid');
    m.plate('box', [4, 5, 14], [12, 11, 16], 'en', 'north', 'plant_fittings/first_aid', { face: { up: 'fa', down: 'fa', east: 'fa', west: 'fa' } });
    m.box('catch', [7.6, 10.4, 13.8], [8.4, 11, 14], 'st', { split: '' });
    return m;
  }

  function bulkhead(lit) {
    const m = mk('plant_fittings/cast_black');
    m.prism('base', 'z', [8, 8], 3.2, 14.5, 16, 'cb', { ends: ['north'] });
    m.prism(`${lit ? 'glow_' : ''}glass`, 'z', [8, 8], 2.6, 13, 14.5, lit ? 'gl1' : 'gl0');
    m.disc(`${lit ? 'glow_' : ''}glass_face`, 'z', [8, 8], 2.6, 12.95, 13.05, lit ? 'gl1' : 'gl0', `plant_fittings/${lit ? 'glass_lit' : 'glass_off'}`, 'north');
    m.box('bar_h', [4.5, 7.6, 12.6], [11.5, 8.4, 13], 'cb', { split: '' });
    m.box('bar_v', [7.6, 4.5, 12.6], [8.4, 11.5, 13], 'cb', { split: '' });
    return m;
  }

  const BUILDS = {
    electrical_cabinet: { model: cabinet },
    conduit: { model: () => conduit(false) },
    conduit_vertical: { model: () => conduit(true) },
    column_i_beam: { model: column },
    gas_cylinders: { model: cylinders },
    first_aid_box: { model: firstAid },
    lamp_bulkhead: { model: () => bulkhead(true) },
    lamp_bulkhead_off: { model: () => bulkhead(false) },
  };

  window.PLF = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'decor', id, BUILDS[id].java),
    preview(root) {
      let m = cabinet(), x = 0;
      for (const id of Object.keys(BUILDS).slice(1)) m = m.merge(BUILDS[id].model(), (x += 18), 0, 0, id + '_');
      return PCART.realize(root, m, 'plant_fittings_preview');
    },
  };
})();
