// Hall-Heroult aluminium reduction pot at real scale (1 block = 1 m, 16 px = 1 m), a prebake pot of the 1950s at
// about 100 kA (08 §8.8.2): a ribbed steel shell 8 m by 3.5 m and 1.25 m high, the cathode collector bars out of both
// long sides into the side busbars, the alumina crust between 18 carbon anodes in two rows on their aluminium rods
// clamped to the anode beam, three portal frames carrying the beam jacks, the side hood panels, the fume duct along
// the top to the east end, and the anode effect lamp on the east portal.
// Variants (whole models): reduction_pot_real (cold, no RUNNING), reduction_pot_real_running (bath glowing in the
// crust holes), reduction_pot_real_alarm (running and the anode effect lamp lit, ALARM).
//
// Load after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for v in cold, running, alarm:
//   RPR.build(repo, v); /* wait */ RPR.save(repo, v)
//
// Controller block = (0, 0, 0). Footprint x -4..4, y 0..3, z -2..2; pot along x.
// Connections (px): DC in on the west face of (-4, 0, -2) at (-64, 7, -26), the north side busbar; DC out on the
// east face of (4, 0, 2) at (80, 7, 42), the south busbar; pot gas out on the east face of (4, 3, 0) at (80, 54, 8);
// alumina and metal tapping through the top of (0, 3, 0) (the crust is worked from above). No partials.
(function () {
  const XS = [-56, 72], ZS = [-20, 36], YS = [2, 20];     // shell
  const AX = [], AZ = [[-12, 4], [12, 28]];               // anode x centres, anode rows z spans
  for (let k = 0; k < 9; k++) AX.push(-46 + k * 14);
  const MAT = Object.assign({}, PCVV.MAT, {
    shell: { tex: 'material/steel', P: 16 }, alu: { tex: 'material/bright_steel', P: 16 }, frame: { tex: 'plant/structure', P: 16 },
    crust: { tex: 'vver/concrete', P: 16 }, carbon: { tex: 'material/soot', P: 16 }, fire: { tex: 'plant/yellow', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/steel' });

  function shell(m) {
    m.box('floor', [-64, 0, -32], [80, 2, 48], 'conc', { split: 'xz' });
    m.box('shell', [XS[0], YS[0], ZS[0]], [XS[1], YS[1], ZS[1]], 'shell', { split: 'xz', hide: ['up'] });
    m.box('crust', [XS[0] + 2, YS[1] - 0.6, ZS[0] + 2], [XS[1] - 2, YS[1], ZS[1] - 2], 'crust', { split: 'xz' });
    for (const [n, f, t] of [['n', [XS[0], YS[1] - 1, ZS[0]], [XS[1], YS[1], ZS[0] + 2]], ['s', [XS[0], YS[1] - 1, ZS[1] - 2], [XS[1], YS[1], ZS[1]]], ['w', [XS[0], YS[1] - 1, ZS[0] + 2], [XS[0] + 2, YS[1], ZS[1] - 2]], ['e', [XS[1] - 2, YS[1] - 1, ZS[0] + 2], [XS[1], YS[1], ZS[1] - 2]]]) m.box(`rim_${n}`, f, t, 'shell', { split: n === 'n' || n === 's' ? 'x' : '' });
    // cradles (vertical ribs) and collector bars along both long sides, into the side busbars
    for (let x = XS[0] + 8; x < XS[1]; x += 16) {
      for (const [z0, z1] of [[ZS[0] - 1.6, ZS[0]], [ZS[1], ZS[1] + 1.6]]) m.box(`cradle_${x}_${z0}`, [x - 1, YS[0], z0], [x + 1, YS[1], z1], 'frame', { split: '' });
      for (const [z0, z1] of [[-26, ZS[0]], [ZS[1], 42]]) m.box(`collector_${x}_${z0}`, [x + 3, 5.4, z0], [x + 6, 8.6, z1], 'alu', { split: '' });
    }
    for (const [n, z0, z1] of [['n', -30, -24], ['s', 40, 46]]) m.box(`busbar_${n}`, [-64, 3, z0], [80, 11, z1], 'alu', { split: 'x' });
  }

  function anodes(m, mode) {
    for (const x of AX) for (const [i, [z0, z1]] of AZ.entries()) {
      m.box(`anode_${x}_${i}`, [x - 5.5, YS[1] - 2, z0], [x + 5.5, YS[1] + 3, z1], 'carbon', { split: '' });
      const zr = (z0 + z1) / 2;
      m.box(`stub_${x}_${i}`, [x - 1.6, YS[1] + 3, zr - 1.6], [x + 1.6, YS[1] + 5, zr + 1.6], 'ci', { split: '' });
      m.box(`rod_${x}_${i}`, [x - 0.9, YS[1] + 5, zr - 0.9], [x + 0.9, 44, zr + 0.9], 'alu', { split: 'y' });
      m.box(`clamp_${x}_${i}`, [x - 1.6, 41, zr - 1.6], [x + 1.6, 44, zr + 1.6], 'ci', { split: '' });
      if (mode !== 'cold' && x < 60) m.box(`glow_hole_${x}_${i}`, [x + 5.8, YS[1], zr - 2], [x + 8.2, YS[1] + 0.05, zr + 2], 'fire', { split: '' });
    }
    // the anode beam: two aluminium bus beams over the rows, on the jacks of the portals
    for (const [i, [z0, z1]] of AZ.entries()) {
      const zr = (z0 + z1) / 2;
      m.box(`beam_${i}`, [XS[0], 44, zr - 2.4], [XS[1], 48, zr + 2.4], 'alu', { split: 'x' });
    }
    m.box('riser', [XS[0] - 4, 8, -26], [XS[0], 48, -22], 'alu', { split: 'y' });
    m.box('riser_top', [XS[0] - 4, 44, -22], [XS[0], 48, 22], 'alu', { split: 'z' });
  }

  function superstructure(m, mode) {
    for (const x of [XS[0] + 4, 8, XS[1] - 4]) {
      for (const z of [ZS[0] + 1, ZS[1] - 3]) m.box(`leg_${x}_${z}`, [x - 1.4, YS[1], z], [x + 1.4, 56, z + 2], 'frame', { split: 'y' });
      m.box(`cross_${x}`, [x - 1.4, 52, ZS[0] + 1], [x + 1.4, 56, ZS[1] - 1], 'frame', { split: 'z' });
      for (const [i, [z0, z1]] of AZ.entries()) {
        const zr = (z0 + z1) / 2;
        m.box(`jack_${x}_${i}`, [x - 2, 48, zr - 2], [x + 2, 52, zr + 2], 'pnt', { split: '' });
      }
    }
    // side hood panels leaning from the rim to the beam level
    for (let x = XS[0] + 2; x < XS[1] - 2; x += 16) {
      for (const [n, z0, z1] of [['n', ZS[0], ZS[0] + 1], ['s', ZS[1] - 1, ZS[1]]]) m.box(`hood_${n}_${x}`, [x + 0.3, YS[1], z0], [x + 15.7, YS[1] + 14, z1], 'frame', { split: '' });
    }
    // fume duct along the top to the east end
    m.prism('duct', 'x', [54, 8], 3, XS[0] + 4, 80, 'frame', { split: 'x' });
    m.disc('duct_flange', 'x', [54, 8], 4, 79.2, 79.9, 'ci', 'vver/flange', 'east');
    // anode effect lamp on the east portal
    const lx = XS[1] - 4;
    m.box('lamp_bracket', [lx - 0.4, 56, 6], [lx + 0.4, 58, 10], 'frame', { split: '' });
    m.box(mode === 'alarm' ? 'glow_ae_lamp' : 'ae_lamp', [lx - 1.6, 58, 6.4], [lx + 1.6, 61, 9.6], mode === 'alarm' ? 'fire' : 'clad', { split: '' });
    m.decal('plate', [-4, 10, ZS[0] - 1.7], [4, 14, ZS[0] - 1.6], 'shell', 'north', 'pl.maker');
  }

  function model(mode) { const m = mk(); shell(m); anodes(m, mode); superstructure(m, mode); return m; }
  const ID = { cold: 'reduction_pot_real', running: 'reduction_pot_real_running', alarm: 'reduction_pot_real_alarm' };
  window.RPR = {
    ID, model,
    build: (root, v) => { const m = PCVV.cut(model(v)); PCART.realize(root, m, ID[v]); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, v) => PCVV.save(root, ID[v], 'nonferrous'),
  };
})();
