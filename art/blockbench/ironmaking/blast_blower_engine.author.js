// Horizontal blowing engine for a small 1890s blast furnace at real scale (1 block = 1 m): one double-acting blowing
// tub (air cylinder) with grid flap valves on both covers and the delivery valves in a chest on top, a crosshead in
// bored guides and a connecting rod to an overhung disc crank with a flywheel. The Create shaft comes in along x and
// turns the crankshaft (along z) through a bevel pair. Dimensions and sources: docs/research/blast_blower-dimensions.md
// (disk only).
//
// Footprint (agreed with 8f): x 0..6, y 0..2, z -1..1 (model x 0..112, y 0..48, z -16..32), controller (0, 0, 0) at
// the crank end. Create shaft into the west face of cell (0, 1, 0) at (y 24, z 8); cold blast main out of the top of
// cell (6, 2, 0) at (x 104, z 12). Crankshaft axis (x 28, y 24) along z; engine centre plane z 16.
//
// Static whole model `blast_blower_engine`; partials (`blast_blower_engine/partial/*`, theta = crank angle, 0 = pin
// towards the cylinder):
// - `flywheel`: crankshaft, flywheel, bevel wheel, disc crank. Turns about z through partial (8, 8); translate
//   (20, 16, 0) px. Crank pin at model (28 + 8 cos theta, 24 + 8 sin theta, 16).
// - `pinion`: input shaft with the bevel pinion. Turns about x through partial (y 8, z 8); translate (0, 16, 0) px;
//   3 x the crank speed, the Create shaft speed (theta_pinion = -3 theta).
// - `crosshead`: crosshead, piston rod and piston. Pin at partial (-4, 8, 16); translate (Xc + 4, 16, 0) px with
//   Xc = 28 + 8 cos theta + sqrt(40^2 - (8 sin theta)^2) (60..76).
// - `conrod`: small end at partial (28, 8, 16), big end at partial (-12, 8, 16); translate (Xc - 28, 16, 0) px and
//   rotate about z through the small end by -asin(8 sin theta / 40).
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then BBE.build(repo) /* wait */ BBE.save(repo);
// BBE.buildPart(repo, p) / BBE.savePart(repo, p) for p in flywheel, pinion, crosshead, conrod.
(function () {
  const ID = 'blast_blower_engine';
  const CX = 28, CY = 24, CR = 8, ROD = 40, ZE = 16;   // crank axis, crank radius, rod length, engine plane
  const CYL = [86, 110], RC = 9.6;                    // air cylinder covers and bore radius
  const PIN0 = 'material/cast_iron';

  // ------------------------------------------------------------------ static
  function model() {
    const m = PCVV.mk(PIN0);
    // foundation: a bed under the engine and a pit for the flywheel (north of the crank)
    m.box('bed', [12, 0, -2], [112, 6, 30], 'conc');
    m.box('pit_floor', [4, 0, -16], [54, 1.5, -2], 'conc');
    m.box('pit_wall_w', [2, 0, -16], [6, 8, -2], 'conc', { split: '' });
    m.box('pit_wall_e', [50, 0, -16], [54, 8, -2], 'conc', { split: '' });
    m.box('bedplate', [18, 6, 4], [112, 8, 28], 'ci');
    // crankshaft bearings either side of the bevel wheel, the input shaft bearing
    for (const [n, z0, z1] of [['brg_n', -4, 0], ['brg_s', 7, 11]]) {
      m.box(`${n}_pedestal`, [21, 6, z0], [35, CY - 2.4, z1], 'green');
      m.box(`${n}_cap`, [22, CY + 2.4, z0], [34, CY + 5, z1], 'ci', { split: '' });
      m.box(`${n}_cheek_w`, [22, CY - 2.4, z0], [25.6, CY + 2.4, z1], 'ci', { split: '' });
      m.box(`${n}_cheek_e`, [30.4, CY - 2.4, z0], [34, CY + 2.4, z1], 'ci', { split: '' });
    }
    m.box('in_pedestal', [4, 0, 4], [10, CY - 2, 12], 'green', { split: '' });
    m.box('in_cap', [4, CY + 2, 4.5], [10, CY + 4, 11.5], 'ci', { split: '' });
    m.box('in_cheeks', [4, CY - 2, 4.5], [10, CY + 2, 5.8], 'ci', { split: '' });
    m.box('in_cheeks_s', [4, CY - 2, 10.2], [10, CY + 2, 11.5], 'ci', { split: '' });
    // bored guides for the crosshead (top and bottom bars), on a trunk frame to the cylinder
    m.box('guide_top', [50, 30, ZE - 5], [84, 32.5, ZE + 5], 'bright', { split: 'x' });
    m.box('guide_bot', [50, 15.5, ZE - 5], [84, 18, ZE + 5], 'bright', { split: 'x' });
    for (const s of [-1, 1]) m.box(`trunk_${s}`, [48, 8, ZE + s * 5 - (s > 0 ? 0 : 2)], [86, 18, ZE + s * 5 + (s > 0 ? 2 : 0)], 'green', { split: 'x' });
    for (const x of [48, 82]) for (const s of [-1, 1]) m.box(`guide_post_${x}_${s}`, [x, 18, s > 0 ? ZE + 5 : ZE - 7], [x + 2, 30, s > 0 ? ZE + 7 : ZE - 5], 'green', { split: '' });
    m.box('trunk_foot', [48, 8, ZE - 8], [86, 15.5, ZE + 8], 'green', { split: 'x' });
    m.box('stuffing_box', [82, CY - 3, ZE - 3], [86, CY + 3, ZE + 3], 'br', { split: '' });
    // the blowing tub: green cylinder, cast iron flanges and covers, grid flap valves on both covers
    m.prism('cylinder', 'x', [CY, ZE], RC, CYL[0], CYL[1], 'green', { split: 'x' });
    for (const [n, a, b] of [['flange_c', CYL[0], CYL[0] + 2], ['flange_h', CYL[1] - 2, CYL[1]], ['band', 97, 99]])
      m.prism(n, 'x', [CY, ZE], RC + 1.2, a, b, 'ci', { split: '', ends: ['east', 'west'] });
    m.disc('cover_c', 'x', [CY, ZE], RC + 0.6, CYL[0] - 1.4, CYL[0], 'ci', 'material/cast_iron', 'west');
    m.disc('cover_h', 'x', [CY, ZE], RC + 0.6, CYL[1], CYL[1] + 1.4, 'ci', 'material/cast_iron', 'east');
    m.box('grid_h', [CYL[1] + 1.4, CY - 6, ZE - 6], [CYL[1] + 1.8, CY + 6, ZE + 6], 'grate', { split: '' });
    for (const s of [-1, 1]) m.box(`grid_c_${s}`, [CYL[0] - 1.8, CY - 6, ZE + s * 4.5 - 2], [CYL[0] - 1.4, CY + 6, ZE + s * 4.5 + 2], 'grate', { split: '' });
    for (const x of [90, 104]) m.box(`cyl_foot_${x}`, [x, 8, ZE - 7], [x + 4, CY - RC + 1, ZE + 7], 'ci', { split: '' });
    // delivery valve chest on top and the cold blast main rising out of the footprint
    m.box('chest', [88, CY + RC - 1, ZE - 7], [108, CY + RC + 4.6, ZE + 7], 'ci', { split: 'x' });
    m.box('chest_lid', [87.4, CY + RC + 4.6, ZE - 7.6], [108.6, CY + RC + 5.4, ZE + 7.6], 'ci', { split: 'x' });
    for (let x = 90; x <= 106; x += 4) m.box(`chest_bolt_${x}`, [x - 0.4, CY + RC + 5.4, ZE - 7], [x + 0.4, CY + RC + 6, ZE - 6.2], 'bright', { split: '' });
    m.cylY('main', [104, 12], 3.6, CY + RC + 5.4, 48, 'st');
    m.ringY('main_flange', [104, 12], 4.6, 1.2, 46, 48, 'ci');
    m.ringY('main_foot', [104, 12], 4.6, 1.2, CY + RC + 5.4, CY + RC + 6.6, 'ci');
    // blast gauge and the maker's plate on the guide trunk
    m.box('gauge_pipe', [100, CY + RC + 5.4, 7.2], [100.8, 42, 8], 'br', { split: '' });
    m.disc('gauge', 'z', [100.4, 43], 2, 6.6, 7.4, 'br', 'material/brass', 'north');
    m.decal('plate', [60, 22, ZE - 7.2], [68, 26.5, ZE - 7], 'green', 'north', 'pl.maker');
    return m;
  }

  // ------------------------------------------------------------------ partials (built in model coordinates)
  function flywheelModel() {
    const m = PCVV.mk(PIN0), C = [CX, CY];
    m.prism('shaft', 'z', C, 2.2, -13, 12, 'bright', { split: '', ends: ['north', 'south'] });
    m.ring('rim', 'z', C, 20.8, 3, -12, -6, 'ci');
    m.prism('hub', 'z', C, 4, -12, -6, 'ci', { split: '', ends: ['north', 'south'] });
    for (const a of [0, 45, -45, 90]) {
      const f = a === 90 ? [CX - 1.2, CY - 18.4, -10] : [CX - 18.4, CY - 1.2, -10];
      const t = a === 90 ? [CX + 1.2, CY + 18.4, -8] : [CX + 18.4, CY + 1.2, -8];
      m.box(`spoke_${a}`, f, t, 'ci', { split: '', rot: a && a !== 90 ? { axis: 'z', angle: a, origin: [CX, CY, -9] } : undefined });
    }
    // bevel wheel: two steps and a band of teeth, apex towards +z
    m.prism('bevel_back', 'z', C, 8, 2, 4, 'ci', { split: '', ends: ['north', 'south'] });
    m.prism('bevel_face', 'z', C, 6.6, 4, 6, 'bright', { split: '', ends: ['south'] });
    // disc crank with counterweight opposite the pin (pin at theta = 0: +x)
    m.prism('crank_disc', 'z', C, 10, 12, 14, 'ci', { split: '', ends: ['north', 'south'] });
    m.box('counterweight', [CX - 9.6, CY - 5, 11.6], [CX - 4, CY + 5, 14.4], 'ci', { split: '' });
    m.prism('crank_pin', 'z', [CX + CR, CY], 2, 14, 19, 'bright', { split: '', ends: ['south'] });
    return m;
  }
  function pinionModel() {
    const m = PCVV.mk(PIN0);
    m.prism('shaft', 'x', [CY, 8], 1.6, 0, 22, 'bright', { split: '', ends: ['west', 'east'] });
    m.prism('collar', 'x', [CY, 8], 2.4, 0, 1, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('pinion_back', 'x', [CY, 8], 3, 16, 19, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('pinion_face', 'x', [CY, 8], 4, 19, 22, 'bright', { split: '', ends: ['east'] });
    return m;
  }
  function crosshead() {
    const m = PCVV.mk(PIN0);
    m.box('block', [-10, 3, ZE - 4], [2, 13, ZE + 4], 'ci', { split: '' });
    m.box('slipper_top', [-11, 13, ZE - 4.6], [3, 14, ZE + 4.6], 'br', { split: '' });
    m.box('slipper_bot', [-11, 2, ZE - 4.6], [3, 3, ZE + 4.6], 'br', { split: '' });
    m.prism('pin', 'z', [-4, 8], 1.5, ZE - 4.4, ZE + 4.4, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('rod', 'x', [8, ZE], 1.2, 2, 24, 'bright', { split: '', ends: ['east'] });
    m.prism('piston', 'x', [8, ZE], RC - 0.4, 24, 28, 'ci', { split: '', ends: ['west', 'east'] });
    return m;
  }
  function conrod() {
    const m = PCVV.mk(PIN0);
    m.prism('small_end', 'z', [28, 8], 2.8, ZE - 2, ZE + 2, 'ci', { split: '', ends: ['north', 'south'] });
    m.box('shank', [-9, 6.4, ZE - 1.2], [26, 9.6, ZE + 1.2], 'bright', { split: 'x' });
    m.box('big_end', [-15, 3.4, ZE - 2], [-9, 12.6, ZE + 2], 'ci', { split: '' });
    m.box('strap', [-15.6, 4.6, ZE - 2.2], [-15, 11.4, ZE + 2.2], 'bright', { split: '' });
    for (const y of [4.4, 11.6]) m.box(`bolt_${y}`, [-10, y - 0.5, ZE - 2.6], [-8.4, y + 0.5, ZE + 2.6], 'bright', { split: '' });
    return m;
  }
  const shift = (src, dx, dy, dz) => PCVV.mk(PIN0).merge(src, dx, dy, dz, '');
  const PARTS = {
    flywheel: () => shift(flywheelModel(), -(CX - 8), -(CY - 8), 0),
    pinion: () => shift(pinionModel(), 0, -(CY - 8), 0),
    crosshead, conrod,
  };
  const xc = (th) => CX + CR * Math.cos(th) + Math.sqrt(ROD * ROD - Math.pow(CR * Math.sin(th), 2));

  window.BBE = {
    ID, PARTS, CX, CY, CR, ROD, ZE, xc,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, ID); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, ID, 'ironmaking'),
    buildPart: (root, p) => { const m = PARTS[p](); PCART.realize(root, m, p); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    savePart: (root, p) => PCVV.savePartial(root, ID, p, 'ironmaking'),
    // everything in place at theta = 0 (outer dead centre, rod straight) for the scale shot, never saved
    preview: (root) => {
      const X = xc(0);
      const m = model().merge(flywheelModel(), 0, 0, 0, 'fw_').merge(pinionModel(), 0, 0, 0, 'pn_')
        .merge(crosshead(), X + 4, CY - 8, 0, 'xh_').merge(conrod(), X - 28, CY - 8, 0, 'cr_');
      PCART.realize(root, m, 'blower_preview');
      return m.els.length;
    },
  };
})();
