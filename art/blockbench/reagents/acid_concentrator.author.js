// Nitric acid concentrator (Pauling process, sulphuric acid as the water-taking agent), hand-authored for
// Blockbench. It replaces the borrowed reagents/fractionating_column, which the methanol column keeps.
// Load inside Blockbench desktop, after the shared toolkit, the round shapes and the PWR (shared yellow):
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/core/pwr_reactor.author.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/reagents/acid_concentrator.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PWR.paint('<repo>'); ACN.paint('<repo>')
//   build + save each model in turn (ACN.build / ACN.save, ids in ACN.ids)
//
// 1x3x1 about the middle block (x, z 0..16, y -16..32), front to the north. The controller is the bottom block.
// The column is built of flanged sections of acid-resisting silicon iron (ferrosilid), dark and dull, bolted
// up from a brick-lined base. In the base the spent sulphuric acid boils off its last nitric acid over the
// steam heater, and a sight glass shows it glowing orange when lit (_lit). Weak nitric acid and oleum or
// strong sulphuric acid come in part way up (orange, acids by GOST 14202). The strong nitric vapour leaves at
// the top for the bleacher and the glass condenser coil, which hangs on the north side and drains strong acid
// back down. Steam (red) comes in to the base heater and condensate leaves it.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, grain, writePng } = PCART;
  const T = {};
  const C = [8, 8];

  // Silicon iron castings: dark grey, granular, a white acid bloom running down from the flanges.
  T['acid_concentrator/ferrosilid'] = () => {
    const im = img(32, 32), r = grain(im, [70, 72, 74], 3601, 6, 5, 4);
    for (let k = 0; k < 10; k++) { const x = Math.floor(r() * 32); for (let y = 0; y < 3 + r() * 8; y++) put(im, x, y, mix(get(im, x, y), [196, 192, 170], 0.35)); }
    return im;
  };
  // Acid brick lining of the base, light acid-proof brick and dark joints.
  T['acid_concentrator/acid_brick'] = () => {
    const im = img(32, 32), r = rng(3611);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const row = Math.floor(y / 4), xx = x + (row % 2) * 4;
      const joint = y % 4 === 3 || xx % 8 === 7;
      put(im, x, y, joint ? [70, 66, 60] : add([182, 168, 140], (r() - 0.5) * 14));
    }
    return im;
  };
  // Orange acid pipe (GOST 14202: acids).
  T['acid_concentrator/acid_pipe'] = () => { const im = img(16, 16); grain(im, [196, 112, 40], 3621, 3, 5, 4); return im; };
  // Glass condenser coil: pale green glass over the darker coil.
  T['acid_concentrator/glass'] = () => {
    const im = img(16, 16), r = rng(3631);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, y % 4 < 2 ? add([150, 186, 170], (r() - 0.5) * 10) : [190, 214, 204]);
    return im;
  };
  T['acid_concentrator/sight'] = () => { const im = img(16, 16); grain(im, [46, 40, 34], 3641, 3, 4, 4); return im; };
  T['acid_concentrator/sight_hot'] = () => {
    const im = img(16, 16), r = rng(3651);
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add(mix([255, 196, 90], [214, 96, 30], y / 15), (r() - 0.5) * 24));
    return im;
  };

  const MAT = {
    fs: { tex: 'acid_concentrator/ferrosilid', P: 16 }, ab: { tex: 'acid_concentrator/acid_brick', P: 16 },
    ap: { tex: 'acid_concentrator/acid_pipe', P: 16 }, gl: { tex: 'acid_concentrator/glass', P: 16 },
    sg: { tex: 'acid_concentrator/sight', P: 16 }, sgH: { tex: 'acid_concentrator/sight_hot', P: 16 }, yel: { tex: 'pwr_reactor/yellow', P: 8 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, {}), { particle: 'acid_concentrator/ferrosilid' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  function column(lit) {
    const m = mk();
    const full = (t) => ({ tex: t, uv: [0, 0, 16, 16] });
    // brick-lined base on a plinth, sight glass, steam heater nozzles
    m.box('plinth', [0.5, -16, 0.5], [15.5, -14.6, 15.5], 'ci', { split: '' });
    m.box('base', [1.5, -14.6, 1.5], [14.5, -4, 14.5], 'ab');
    m.box('base_band_lo', [1.2, -13, 1.2], [14.8, -12.2, 14.8], 'st', { split: '' });
    m.box('base_band_hi', [1.2, -6, 1.2], [14.8, -5.2, 14.8], 'st', { split: '' });
    m.box(`${lit ? 'glow_' : ''}sight`, [6, -10.4, 1], [10, -7.6, 1.5], lit ? 'sgH' : 'sg', { split: '', face: { north: full(lit ? 'acid_concentrator/sight_hot' : 'acid_concentrator/sight') } });
    m.box('sight_frame', [5.4, -11, 1.2], [10.6, -10.4, 1.6], 'ci', { split: '' });
    m.box('steam_in', [14.5, -12, 6.6], [16, -10.4, 8.2], 'red', { split: '' });
    m.box('condensate', [14.5, -14.4, 10], [16, -13.4, 11], 'green', { split: '' });
    m.box('spent_acid', [7.2, -16, 14.5], [8.8, -13, 16], 'ap', { split: '' });
    // column sections with flanges
    const flanges = [-4, 2, 8, 14, 20, 26];
    m.cylY('column', C, 4.6, -4, 28, 'fs', { cap: 'acid_concentrator/ferrosilid' });
    for (const y of flanges) {
      m.cylY(`flange_${y}`, C, 5.8, y, y + 1.2, 'ci', { cap: 'material/cast_iron', bottom: true });
      for (let k = 0; k < 8; k++) {
        const a = (k + 0.5) * Math.PI / 4, x = 8 + 5.3 * Math.cos(a), z = 8 + 5.3 * Math.sin(a);
        m.box(`bolt_${y}_${k}`, [x - 0.3, y - 0.4, z - 0.3], [x + 0.3, y + 1.4, z + 0.3], 'st', { split: '' });
      }
    }
    // feeds: weak nitric acid and sulphuric acid part way up from the east
    m.box('feed_hno3', [12.4, 10, 7.2], [16, 11.4, 8.8], 'ap', { split: '' });
    m.box('feed_h2so4', [12.4, 16, 3.6], [16, 17.4, 5.2], 'ap', { split: '' });
    m.box('feed_h2so4_ring', [14, 15.9, 3.5], [14.6, 17.5, 5.3], 'red', { split: '' });
    // vapour off the top to the glass condenser on the north side, strong acid back down
    m.box('vapour_up', [7, 28, 7], [9, 31, 9], 'fs', { split: '' });
    m.box('vapour_over', [7, 29.4, 1], [9, 31.4, 9], 'fs', { split: '' });
    m.box('condenser_coil', [6, 14, 0.4], [10, 29.4, 3.4], 'gl', { split: 'y' });
    for (const y of [15, 21, 27]) m.box(`coil_clamp_${y}`, [5.6, y, 0.2], [10.4, y + 0.6, 3.6], 'ci', { split: '' });
    m.box('return', [7.4, 4, 1.4], [8.6, 14, 2.6], 'ap', { split: 'y' });
    m.box('return_in', [7.4, 4, 1.4], [8.6, 5.2, 3.6], 'ap', { split: '' });
    // cooling water to the condenser jacket
    m.box('cw_line', [12, 20, 1.2], [13, 32, 2.2], 'green', { split: 'y' });
    m.box('cw_branch', [10, 27, 1.2], [13, 28, 2.2], 'green', { split: '' });
    // ladder up the back
    for (const x of [6, 9.6]) m.box(`ladder_${x}`, [x, -4, 13.6], [x + 0.4, 30, 14], 'yel', { split: 'y' });
    for (let y = -2; y < 30; y += 3) m.box(`rung_${y}`, [6, y, 13.6], [10, y + 0.3, 14], 'yel', { split: '' });
    return m;
  }

  const BUILDS = {
    acid_concentrator: { model: () => column(false) },
    acid_concentrator_lit: { model: () => column(true) },
  };

  window.ACN = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'reagents', id, BUILDS[id].java),
    preview(root) { return PCART.realize(root, column(false).merge(column(true), 20, 0, 0, 'l_'), 'acid_concentrator_preview'); },
  };
})();
