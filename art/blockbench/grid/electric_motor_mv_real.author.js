// 1 MW 6 kV induction motor at real scale (1 block = 1 m): a drip-proof cast frame Ø 1.6 m with ventilation louvres,
// end shields, two pedestal sleeve bearings with oil rings on a bedplate, the 6 kV terminal box with its cable glands
// on the east, lifting lugs and the nameplate. Dimensions and sources: docs/research/electric_motor-dimensions.md
// (disk only).
//
// Footprint FP_CUBE3 (agreed with 8f, new block electric_motor_mv): x -1..1, y 0..2, z -1..1 (model x -16..32,
// y 0..48, z -16..32). Shaft along z at (x 8, y 24) out through the north face of cell (0, 1, -1) (front shaft);
// terminal box east, cell (1, 0, 0), cables down through the floor.
//
// Static whole model `electric_motor_mv_real` (the id electric_motor_mv is the old 1-block 6 kV model);
// partial `electric_motor_mv_real/partial/shaft` (shaft end and coupling half) turns about z through partial (8, 8):
// translate (0, 16, 0) px.
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then EMV.build(repo) /* wait */ EMV.save(repo);
// EMV.buildPart(repo, 'shaft') / EMV.savePart(repo, 'shaft').
(function () {
  const ID = 'electric_motor_mv_real';
  const AX = 8, AY = 24, R = 12.8, Z0 = -5, Z1 = 21;          // axis, frame radius, frame length (1.6 m)
  const EXTRA = { me: { tex: 'electric_motor/enamel', P: 16 } };
  const mk = () => { const m = PCVV.mk('electric_motor/enamel'); Object.assign(m.mat, EXTRA); return m; };

  function model() {
    const m = mk();
    m.box('bedplate', [-10, 0, -14], [26, 3, 30], 'ci');
    for (const x of [-8, 24]) for (const z of [-12, 0, 16, 28]) m.box(`hd_bolt_${x}_${z}`, [x - 0.5, 3, z - 0.5], [x + 0.5, 3.8, z + 0.5], 'bright', { split: '' });
    // frame: drum with ribs, louvres on the sides, end shields with the bearing bosses, feet
    m.prism('frame', 'z', [AX, AY], R, Z0, Z1, 'me', { split: '' });
    for (const z of [Z0, (Z0 + Z1) / 2 - 0.6, Z1 - 1.2]) m.ring(`frame_ring_${z}`, 'z', [AX, AY], R + 0.8, 1.6, z, z + 1.2, 'me');
    for (const s of [-1, 1]) {
      const x0 = s > 0 ? AX + R - 0.2 : AX - R - 0.2;
      for (const z of [Z0 + 3, Z1 - 9]) m.box(`louvre_${s}_${z}`, [x0, AY - 5, z], [x0 + 0.4, AY + 5, z + 6], 'grate', { split: '' });
      const fx = s > 0 ? [AX + 6, AX + 14] : [AX - 14, AX - 6];
      m.box(`foot_${s}`, [fx[0], 3, Z0 + 1], [fx[1], AY - 9, Z1 - 1], 'me', { split: 'z' });
    }
    m.disc('shield_n', 'z', [AX, AY], R - 0.4, Z0 - 1.2, Z0, 'me', 'electric_motor/enamel', 'north');
    m.disc('shield_s', 'z', [AX, AY], R - 0.4, Z1, Z1 + 1.2, 'me', 'electric_motor/enamel', 'south');
    // pedestal sleeve bearings, ring oiled, with sight glasses
    for (const [n, z0, z1] of [['n', -12, -7], ['s', 23, 28]]) {
      m.box(`pedestal_${n}`, [2, 3, z0], [14, AY - 3, z1], 'me', { split: '' });
      m.box(`brg_cap_${n}`, [2.6, AY + 3, z0], [13.4, AY + 6, z1], 'ci', { split: '' });
      m.box(`brg_cheek_w_${n}`, [2.6, AY - 3, z0], [5, AY + 3, z1], 'ci', { split: '' });
      m.box(`brg_cheek_e_${n}`, [11, AY - 3, z0], [13.4, AY + 3, z1], 'ci', { split: '' });
      m.box(`sight_glass_${n}`, [14, AY - 7, z0 + 1.5], [14.4, AY - 5, z1 - 1.5], 'br', { split: '' });
    }
    m.prism('shaft_s', 'z', [AX, AY], 2, Z1 + 1.2, 28.4, 'bright', { split: '', ends: ['south'] });
    // 6 kV terminal box on the east: cast box, bolted lid, three glands and the cables down
    m.box('tb_neck', [AX + R - 1, AY - 4, 4], [24, AY + 2, 12], 'me', { split: '' });
    m.box('terminal_box', [24, 6, 1], [31, 22, 15], 'ci', { split: '' });
    m.box('tb_lid', [31, 7, 2], [31.6, 21, 14], 'me', { split: '' });
    for (const y of [9, 13, 17]) for (const z of [2.4, 13]) m.box(`tb_bolt_${y}_${z}`, [31.6, y, z], [32, y + 0.8, z + 0.6], 'bright', { split: '' });
    for (const z of [4, 8, 12]) {
      m.box(`gland_${z}`, [26, 4.6, z - 1.2], [28.4, 6, z + 1.2], 'br', { split: '' });
      m.box(`cable_${z}`, [26.4, 0, z - 0.8], [28, 4.6, z + 0.8], 'dark', { split: '' });
    }
    m.box('hv_sign', [28.6, 16, 0.6], [30.6, 19, 1], 'yel', { split: '' });
    // lifting lugs and the nameplate
    for (const z of [Z0 + 2, Z1 - 4]) m.box(`lug_${z}`, [AX - 1, AY + R + 0.4, z], [AX + 1, AY + R + 3, z + 1.2], 'st', { split: '' });
    m.decal('plate', [AX - 4, AY - 3, Z0 - 1.4], [AX + 4, AY + 1.5, Z0 - 1.2], 'me', 'north', 'pl.maker');
    return m;
  }

  // shaft end out of the north bearing and the coupling half, in model coordinates
  function shaftModel() {
    const m = mk();
    m.prism('shaft', 'z', [AX, AY], 2, -16, -6.2, 'bright', { split: '', ends: ['north'] });
    m.prism('coupling', 'z', [AX, AY], 5.2, -15.6, -13.4, 'ci', { split: '', ends: ['north', 'south'] });
    for (const [dx, dy] of [[3.6, 0], [-3.6, 0], [0, 3.6], [0, -3.6]]) m.box(`cpl_bolt_${dx}_${dy}`, [AX + dx - 0.5, AY + dy - 0.5, -16], [AX + dx + 0.5, AY + dy + 0.5, -15.6], 'bright', { split: '' });
    return m;
  }
  const PARTS = { shaft: () => mk().merge(shaftModel(), 0, -(AY - 8), 0, '') };

  window.EMV = {
    ID, PARTS,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'grid'),
    buildPart: (root, p) => { const m = PARTS[p](); PCART.realize(root, m, p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePart: (root, p) => PCVV.savePartial(root, ID, p, 'grid'),
    preview: (root) => { const m = model().merge(shaftModel(), 0, 0, 0, 'sh_'); PCART.realize(root, m, 'motor_preview'); return m.els.length; },
  };
})();
