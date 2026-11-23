// 500 kVA three-phase alternator of about 1905 at real scale (1 block = 1 m): an open-frame revolving-field machine
// with a split cast iron stator frame on feet, sixteen salient poles on a spider (seen through the open ends), slip
// rings and brush gear for the field, two pedestal bearings with ring oiling on a cast iron bedplate, and the
// terminal box on the east. Dimensions and sources: docs/research/shaft_generator-dimensions.md (disk only).
//
// Footprint FP_CUBE3 (agreed with 8f): x -1..1, y 0..2, z -1..1 (model x -16..32, y 0..48, z -16..32). Shaft along z
// at (x 8, y 24): the Create shaft enters the south face of cell (0, 1, 1); generator_exciter sits against the north
// face of cell (0, 1, -1) on the same axis; terminal box on the east, cell (1, 0, 0).
//
// Static whole model `shaft_generator_real`; partial `shaft_generator_real/partial/rotor` turns about z through
// partial (8, 8): translate (0, 16, 0) px.
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then SGR.build(repo) /* wait */ SGR.save(repo);
// SGR.buildPart(repo, 'rotor') / SGR.savePart(repo, 'rotor').
(function () {
  const ID = 'shaft_generator_real';
  const AX = 8, AY = 24, RS = 17.6, RB = 14.6, Z0 = 1, Z1 = 15;   // axis, stator outer / bore radius, core length
  const EXTRA = { cu: { tex: 'disconnector/copper', P: 16 } };
  const mk = () => { const m = PCVV.mk('material/cast_iron'); Object.assign(m.mat, EXTRA); return m; };

  function model() {
    const m = mk();
    m.box('bedplate', [-12, 0, -14], [28, 4, 30], 'ci');
    for (let x = -10; x <= 26; x += 12) for (const z of [-13, 29]) m.box(`hd_bolt_${x}_${z}`, [x - 0.5, 4, z - 0.5], [x + 0.5, 4.8, z + 0.5], 'bright', { split: '' });
    // stator: split frame (two halves bolted at the axis height), laminated core at the bore, ribs, feet
    m.ring('frame', 'z', [AX, AY], RS, 2.6, Z0, Z1, 'green');
    m.ring('core', 'z', [AX, AY], RB + 0.8, 0.8, Z0 + 1, Z1 - 1, 'st');
    for (const s of [-1, 1]) {
      const xo = s > 0 ? AX + RS - 0.4 : AX - RS - 1.6;
      m.box(`split_flange_${s}`, [xo, AY - 1, Z0], [xo + 2, AY + 1, Z1], 'ci', { split: '' });
      for (const z of [3, 8, 13]) m.box(`split_bolt_${s}_${z}`, [xo + 0.4, AY + 1, z - 0.5], [xo + 1.6, AY + 1.8, z + 0.5], 'bright', { split: '' });
      const fx = s > 0 ? [17, 24] : [-8, -1];
      m.box(`foot_${s}`, [fx[0], 4, Z0 + 1], [fx[1], 13, Z1 - 1], 'green', { split: '' });
    }
    for (const z of [Z0, Z1 - 1.2]) m.ring(`rib_${z}`, 'z', [AX, AY], RS + 0.8, 1.2, z, z + 1.2, 'ci');
    // end windings of the stator coils, copper, at both ends of the core
    for (const [n, a, b] of [['n', Z0 - 0.6, Z0 + 1], ['s', Z1 - 1, Z1 + 0.6]]) m.ring(`end_winding_${n}`, 'z', [AX, AY], RB + 0.4, 1.4, a, b, 'cu');
    // pedestal bearings, ring oiled
    for (const [n, z0, z1] of [['n', -10, -6], ['s', 21, 25]]) {
      m.box(`pedestal_${n}`, [2, 4, z0], [14, AY - 2.8, z1], 'green', { split: '' });
      m.box(`brg_cap_${n}`, [2.6, AY + 2.8, z0], [13.4, AY + 5.4, z1], 'ci', { split: '' });
      m.box(`brg_cheek_w_${n}`, [2.6, AY - 2.8, z0], [5.2, AY + 2.8, z1], 'ci', { split: '' });
      m.box(`brg_cheek_e_${n}`, [10.8, AY - 2.8, z0], [13.4, AY + 2.8, z1], 'ci', { split: '' });
      m.box(`oil_cup_${n}`, [7, AY + 5.4, z0 + 1], [9, AY + 7, z1 - 1], 'br', { split: '' });
    }
    // brush gear on the slip rings (rings on the rotor at z -5..-2), carried from the north pedestal
    m.box('brush_arm', [10, AY + 2, -5.6], [11, AY + 8, -1.4], 'ci', { split: '' });
    for (const z of [-4.8, -2.6]) m.box(`brush_${z}`, [8.4, AY + 3, z], [10, AY + 4.4, z + 1], 'br', { split: '' });
    m.box('field_lead', [10.6, AY + 8, -4], [11.4, AY + 9, 20], 'cu', { split: 'z' });
    // terminal box on the east, cables down through the floor
    m.box('terminal_box', [26, 4, 3], [31, 16, 13], 'ci', { split: '' });
    m.box('terminal_lid', [31, 5, 4], [31.6, 15, 12], 'green', { split: '' });
    for (const z of [5, 8, 11]) m.box(`cable_${z}`, [27.5, 0, z - 0.8], [29.5, 4, z + 0.8], 'dark', { split: '' });
    m.box('leads', [24, 13, 6], [26, 15, 10], 'cu', { split: '' });
    m.decal('plate', [4, 8, 30.0], [12, 12.5, 30.2], 'ci', 'south', 'pl.maker');
    return m;
  }

  // Rotor in model coordinates: shaft, spider, 16 salient poles with field coils and shoes, slip rings, coupling.
  // A diametral box is two opposite poles; eight of them at 22.5 deg steps give sixteen.
  function rotorModel() {
    const m = mk(), C = [AX, AY], ROT = (a) => (a ? { axis: 'z', angle: a, origin: [AX, AY, 8] } : undefined);
    m.prism('shaft', 'z', C, 2.6, -16, 32, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('spider', 'z', C, 6, Z0 + 1, Z1 - 1, 'ci', { split: '', ends: ['north', 'south'] });
    const STEPS = [[0, 'x'], [22.5, 'x'], [45, 'x'], [-22.5, 'y'], [0, 'y'], [22.5, 'y'], [-45, 'x'], [-22.5, 'x']];
    STEPS.forEach(([a, d], k) => {
      const box = (n, r0, r1, w, z0, z1, mat) => {
        const f = d === 'x' ? [AX - r1, AY - w / 2, z0] : [AX - w / 2, AY - r1, z0];
        const t = d === 'x' ? [AX + r1, AY + w / 2, z1] : [AX + w / 2, AY + r1, z1];
        m.box(`${n}_${k}`, f, t, mat, { split: '', rot: ROT(a) });
      };
      box('pole', 0, RB - 1.6, 2.4, Z0 + 1.4, Z1 - 1.4, 'st');
      box('coil', 0, RB - 3.6, 3.8, Z0 + 2, Z1 - 2, 'cu');
      box('shoe', 0, RB - 0.4, 3.6, Z0 + 1.2, Z1 - 1.2, 'st');
    });
    // only the outer ends of coil/shoe boxes show; the middles are buried in the spider
    for (const z of [-5, -2.6]) m.prism(`slip_ring_${z}`, 'z', C, 3.4, z, z + 1.4, 'br', { split: '', ends: ['north', 'south'] });
    m.prism('coupling', 'z', C, 5.6, 28, 31.4, 'ci', { split: '', ends: ['north', 'south'] });
    return m;
  }
  const PARTS = { rotor: () => PCVV.mk('material/cast_iron').merge(rotorModel(), 0, -(AY - 8), 0, '') };

  window.SGR = {
    ID, PARTS, AX, AY,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'grid'),
    buildPart: (root, p) => { const m = PARTS[p](); Object.assign(m.mat, EXTRA); PCART.realize(root, m, p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePart: (root, p) => PCVV.savePartial(root, ID, p, 'grid'),
    preview: (root) => { const m = model().merge(rotorModel(), 0, 0, 0, 'rt_'); PCART.realize(root, m, 'generator_preview'); return m.els.length; },
  };
})();
