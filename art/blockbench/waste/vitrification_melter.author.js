// HLW vitrification melter and the uranium ion-exchange column, hand-authored for Blockbench (replace the
// spec_fuelcycle.py placeholder and the borrowed hydrometallurgy/ion_exchange_column).
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (shared yellow,
// plates):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/waste/vitrification_melter.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); VIT.paint('<repo>')
//   build + save each model in turn (VIT.build / VIT.save, ids in VIT.ids)
//
// Both 1x1x1, front to the north.
// vitrification_melter: a joule-heated ceramic melter of the EP-500 kind at Mayak. A water-cooled steel box,
// lined with refractory, stands on legs. Three electrode bus bars come down through the lid with their
// clamps, and the liquid waste feed and the off-gas duct leave at the back. The front has a view port onto
// the glowing melt. Molten glass runs from the pour spout into the stainless canister standing between
// the legs.
// uranium_ion_exchange: a rubber-lined column of the uranium mill, where resin in the column takes uranium
// out of the leach liquor. It stands on its ring and legs, with flanged dished ends. A sight glass shows the
// resin bed, with the yellow band of uranium-loaded resin moving down it. Liquor (orange, acid) comes in at
// the top and the barren solution leaves at the bottom. The eluent line has its valve.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, grain, writePng } = PCART;
  const T = {};
  const C = [8, 8];

  // Melter casing: blue-grey enamel over welded steel, cooling jacket panels outlined.
  T['vitrification_melter/casing'] = () => {
    const im = img(32, 32), r = grain(im, [96, 108, 118], 4001, 3, 5, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      if (x % 16 === 1 || y % 16 === 1) put(im, x, y, add(get(im, x, y), 14));
      if (x % 16 === 14 || y % 16 === 14) put(im, x, y, add(get(im, x, y), -16));
    }
    return im;
  };
  // Molten borosilicate glass through the port: orange-white, a dark crust drifting on it.
  T['vitrification_melter/melt'] = () => {
    const im = img(16, 16), r = rng(4011);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const t = Math.sin(x * 0.8 + y * 0.3) * 0.5 + 0.5;
      put(im, x, y, add(mix([255, 214, 120], [236, 120, 40], t * 0.7 + y / 30), (r() - 0.5) * 16));
    }
    return im;
  };
  // Rubber-lined steel of the ion exchange column: near-black green enamel.
  T['uranium_ion_exchange/shell'] = () => {
    const im = img(32, 32), r = grain(im, [52, 66, 58], 4021, 3, 4, 8);
    for (let k = 0; k < 6; k++) put(im, r() * 32, r() * 32, [80, 92, 84]);
    return im;
  };
  // Resin bed in the sight glass: amber beads, the yellow uranium-loaded band and the barren resin below.
  T['uranium_ion_exchange/resin'] = () => {
    const im = img(16, 16), r = rng(4031);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const base = y < 3 ? [150, 186, 200] : y < 8 ? [214, 186, 40] : [150, 92, 40];
      put(im, x, y, add(base, (x + y) % 2 ? 14 : -10 + (r() - 0.5) * 10));
    }
    return im;
  };
  T['uranium_ion_exchange/acid'] = () => { const im = img(16, 16); grain(im, [196, 112, 40], 4041, 3, 5, 4); return im; };

  const MAT = {
    vc: { tex: 'vitrification_melter/casing', P: 16 }, melt: { tex: 'vitrification_melter/melt', P: 16 },
    ix: { tex: 'uranium_ion_exchange/shell', P: 16 }, rs: { tex: 'uranium_ion_exchange/resin', P: 16 },
    ac: { tex: 'uranium_ion_exchange/acid', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
  };
  const mk = (p) => Object.assign(new PCART.Model(MAT, {}), { particle: p });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function melter() {
    const m = mk('vitrification_melter/casing');
    const full = (t) => ({ tex: t, uv: [0, 0, 16, 16] });
    for (const x of [1, 13.6]) for (const z of [1.6, 13]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1.4, 5, z + 1.4], 'ci', { split: '' });
    m.box('casing', [1, 5, 1.6], [15, 13.4, 14.4], 'vc');
    m.box('lid', [0.6, 13.4, 1.2], [15.4, 14.4, 14.8], 'ci', { split: '' });
    // electrodes down through the lid, bus bars and clamps
    for (const x of [3.6, 7.4, 11.2]) {
      m.box(`electrode_${x}`, [x, 14.4, 6.4], [x + 1.2, 15.2, 9.6], 'st', { split: '' });
      m.box(`busbar_${x}`, [x + 0.2, 15.2, 7.4], [x + 1, 16, 15.4], 'br', { split: '' });
    }
    // feed and off-gas at the back
    m.box('feed', [11.6, 14.4, 11.6], [13, 16, 13], 'ac', { split: '' });
    m.box('offgas', [2, 14.4, 11.2], [6, 16, 16], 'st', { split: '' });
    // cooling water to the jacket panels (green)
    m.box('cw_e', [15, 6, 4], [15.8, 12.6, 4.8], 'green', { split: '' });
    m.box('cw_w', [0.2, 6, 11], [1, 12.6, 11.8], 'green', { split: '' });
    // view port onto the melt, its shutter hinged above
    m.box('glow_view', [5.6, 8.6, 1.2], [10.4, 11, 1.6], 'melt', { split: '', face: { north: full('vitrification_melter/melt') } });
    m.box('port_frame', [5, 8, 1.3], [11, 8.6, 1.6], 'ci', { split: '' });
    m.box('shutter', [5, 11, 1], [11, 12.4, 1.6], 'ci', { split: '' });
    // pour spout and the glass running into the canister between the legs
    m.box('spout', [7, 4, 1.2], [9, 5.4, 6], 'ci', { split: '' });
    m.box('glow_stream', [7.7, 3.2, 4.6], [8.3, 4, 5.2], 'melt', { split: '' });
    m.cylY('canister', [8, 6], 3, 0, 3.2, 'st', { cap: 'vitrification_melter/melt' });
    m.box('canister_hoop', [4.8, 2.4, 2.8], [11.2, 2.8, 9.2], 'ci', { split: '' });
    return m;
  }

  function ionExchange() {
    const m = mk('uranium_ion_exchange/shell');
    const full = (t) => ({ tex: t, uv: [0, 0, 16, 16] });
    for (const [x, z] of [[2.4, 2.4], [12.6, 2.4], [2.4, 12.6], [12.6, 12.6]]) m.box(`leg_${x}_${z}`, [x, 0, z], [x + 1, 3, z + 1], 'ci', { split: '' });
    m.ringY('support_ring', C, 6.4, 1, 2.4, 3.2, 'ci');
    m.cylY('bottom', C, 4.6, 2.4, 3.4, 'ix', { bottom: true });
    m.cylY('shell', C, 5.6, 3.4, 13.4, 'ix');
    m.cylY('flange_lo', C, 6.2, 3.4, 4.2, 'ci', { bottom: true, cap: 'material/cast_iron' });
    m.cylY('flange_hi', C, 6.2, 12.6, 13.4, 'ci', { bottom: true, cap: 'material/cast_iron' });
    m.cylY('top', C, 4.6, 13.4, 14.4, 'ix', { cap: 'material/cast_iron' });
    m.box('sight_glass', [6.2, 5, 2.2], [9.8, 11.6, 2.6], 'rs', { split: '', face: { north: full('uranium_ion_exchange/resin') } });
    m.box('sight_frame_l', [5.6, 4.6, 2.3], [6.2, 12, 2.8], 'st', { split: '' });
    m.box('sight_frame_r', [9.8, 4.6, 2.3], [10.4, 12, 2.8], 'st', { split: '' });
    // liquor in at the top from the east, barren solution out at the bottom to the west, eluent line
    m.box('inlet', [9, 14.4, 7.2], [16, 15.8, 8.6], 'ac', { split: '' });
    m.box('inlet_down', [9, 13.4, 7.2], [10.4, 15.8, 8.6], 'ac', { split: '' });
    m.box('inlet_valve', [12.6, 14, 6.6], [14.2, 16, 9.2], 'red', { split: '' });
    m.box('outlet', [0, 1, 7.2], [7, 2.4, 8.6], 'ac', { split: '' });
    m.box('eluent', [13.4, 3, 11], [14.6, 12, 12.2], 'yel', { split: '' });
    m.box('eluent_valve', [13, 6, 10.6], [15, 7.6, 12.6], 'red', { split: '' });
    m.box('vent', [7.4, 14.4, 4], [8.6, 16, 5.2], 'st', { split: '' });
    return m;
  }

  const BUILDS = {
    vitrification_melter: { model: melter, cat: 'waste' },
    uranium_ion_exchange: { model: ionExchange, cat: 'hydrometallurgy' },
  };

  window.VIT = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, BUILDS[id].cat, id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, melter().merge(ionExchange(), 20, 0, 0, 'i_'), 'vit_preview'); },
  };
})();
