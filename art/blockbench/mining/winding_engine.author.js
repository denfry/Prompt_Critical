// Steam winding engine at real scale (1 block = 1 m): a pair of horizontal direct-acting engines, 28 in x 4 ft, on
// cast iron bed frames over ashlar, driving cranks at 90 degrees on the two ends of the drum shaft, the 15 ft drum
// with timber cleading between cast iron flanges, a post brake on the west brake ring, and the driver's stand on the
// east side with the reversing and brake levers and a vertical depth indicator. Prototype: the horizontal pair of
// Parkfield Colliery (28 in cylinders, 4 ft stroke, 15 ft drum). Dimensions and sources:
// docs/research/winding_engine-dimensions.md (disk only).
//
// Parts: the static engine `winding_engine` (whole model); the drum with its shaft, cranks and crank pins
// `winding_engine_drum` (a second whole model that turns about the shaft axis, too big for one partial); partials
// `winding_engine/partial/crosshead` (crosshead and piston rod, slides along z) and `winding_engine/partial/conrod`
// (connecting rod, 2.5 m, small-end pin at the partial's (8, 8, 28), big end toward -z). Both engines share them.
//
// Controller = (0, 0, 0) on the floor under the drum shaft middle (x = 8). Front north: the rope leaves the drum top
// toward the headframe to the north. Cylinders behind the drum to the south. Create shaft input: stub on the west
// end of the drum shaft, west face of cell (-2, 2, 0).
//
// Load after pcart.js, pcround.js, pcslice.js and vverkit.js, then WINDING_ENGINE.build(repo) /* wait */
// WINDING_ENGINE.save(repo); the same with buildDrum/saveDrum, buildPart('crosshead'|'conrod')/savePart(...).
(function () {
  const AY = 40, AZ = 8, CX = 8;            // drum shaft axis along x through y 40, z 8
  const DR = 36.5, DW = [-6.4, 22.4];       // drum radius (15 ft), barrel from x -6.4 to 22.4 (1.8 m)
  const CRANK = 9.75, ROD = 40;             // crank radius (2 ft), connecting rod centres (2.5 m)
  const EX = [-22, 38];                     // engine centre lines (crank pin planes): west and east
  const XH = AZ + ROD;                      // crosshead pin at mid stroke
  const CYL = [62, 90], CR = 7.2;           // cylinder from z 68 to 96, lagged radius 0.45 m

  function engine() {
    const m = PCVV.mk('material/cast_iron');
    // ashlar engine beds under each engine and the main bearing pedestals either side of the drum
    for (const x of EX) {
      m.box(`found_${x}`, [x - 9, 0, AZ - 8], [x + 9, 22, CYL[1] + 4], 'ashlar');
      // cast iron girder bed: main bearing to cylinder, a box girder with a slot for the connecting rod
      m.box(`bed_${x}_o`, [x + (x < CX ? -8 : 4), 22, AZ - 6], [x + (x < CX ? -4 : 8), AY - 4, CYL[0]], 'ci');
      m.box(`bed_${x}_i`, [x + (x < CX ? 4 : -8), 22, AZ + 8], [x + (x < CX ? 8 : -4), AY - 4, CYL[0]], 'ci');
      m.box(`bed_${x}_floor`, [x - 8, 22, AZ + 8], [x + 8, 25, CYL[0]], 'ci');
      // crosshead guides: upper and lower bars over the slide
      m.box(`guide_${x}_lo`, [x - 3.5, AY - 5, XH - 14], [x + 3.5, AY - 3.5, XH + 14], 'bright');
      m.box(`guide_${x}_hi`, [x - 3.5, AY + 3.5, XH - 14], [x + 3.5, AY + 5, XH + 14], 'bright');
      for (const z of [XH - 14, XH + 12]) m.box(`guide_${x}_post${z}`, [x - 4, AY - 4, z], [x + 4, AY + 4, z + 2], 'ci', { split: '' });
      // cylinder: lagged barrel in timber strips with brass bands, covers, stuffing box, valve chest on the side
      m.prism(`cyl_${x}`, 'z', [x, AY], CR, CYL[0] + 2, CYL[1] - 2, 'wood', { split: '' });
      for (const z of [CYL[0] + 6, (CYL[0] + CYL[1]) / 2, CYL[1] - 6]) m.ring(`cyl_band_${x}_${z}`, 'z', [x, AY], CR + 0.3, 0.6, z, z + 1, 'br');
      m.prism(`cyl_fc_${x}`, 'z', [x, AY], CR + 0.8, CYL[0], CYL[0] + 2, 'ci', { split: '', ends: ['north'] });
      m.prism(`cyl_bc_${x}`, 'z', [x, AY], CR + 0.8, CYL[1] - 2, CYL[1], 'ci', { split: '', ends: ['south'] });
      m.disc(`cyl_end_${x}`, 'z', [x, AY], CR - 1, CYL[1], CYL[1] + 0.6, 'ci', 'vver/flange', 'south');
      m.prism(`gland_${x}`, 'z', [x, AY], 2.2, CYL[0] - 4, CYL[0], 'br', { split: '', ends: ['north'] });
      const vx = x < CX ? x - CR - 4 : x + CR;   // valve chest on the outer side
      m.box(`chest_${x}`, [vx, AY - 6, CYL[0] + 4], [vx + 4, AY + 6, CYL[1] - 4], 'ci');
      m.box(`chest_cover_${x}`, [x < CX ? vx - 0.6 : vx + 4, AY - 5, CYL[0] + 5], [x < CX ? vx : vx + 4.6, AY + 5, CYL[1] - 5], 'ci');
      // cylinder feet on the bed
      m.box(`cyl_foot_${x}`, [x - 6, 22, CYL[0] + 3], [x + 6, AY - CR + 1, CYL[1] - 3], 'ci');
      // steam pipe from the stop valve on top of the chest, lagged; exhaust down into the floor
      m.prism(`steam_${x}`, 'x', [AY + 14, (CYL[0] + CYL[1]) / 2], 2.2, Math.min(vx, x) - 2, Math.max(vx + 4, x) + 2, 'felt', { split: '' });
      m.box(`steam_rise_${x}`, [vx + 0.5, AY + 6, (CYL[0] + CYL[1]) / 2 - 2], [vx + 3.5, AY + 12, (CYL[0] + CYL[1]) / 2 + 2], 'ci', { split: '' });
      m.box(`stopvalve_${x}`, [vx - 0.5, AY + 16, (CYL[0] + CYL[1]) / 2 - 2.5], [vx + 4.5, AY + 19, (CYL[0] + CYL[1]) / 2 + 2.5], 'br', { split: '' });
      m.box(`exhaust_${x}`, [vx + 0.5, 22, CYL[1] - 10], [vx + 3.5, AY - 6, CYL[1] - 6], 'ci', { split: '' });
    }
    // steam main between the two stop valves, up to a flange over the engine (steam inlet, y 58..66 at x 8.5, z 82)
    const SZ = (CYL[0] + CYL[1]) / 2;
    m.prism('steam_main', 'x', [AY + 14, SZ], 2.6, EX[0] - CR - 6, EX[1] + CR + 6, 'felt', { split: '' });
    m.box('steam_up', [6, AY + 16, SZ - 2.5], [11, 62, SZ + 2.5], 'felt', { split: '' });
    m.prism('steam_flange', 'z', [8.5, 62], 4, SZ - 3, SZ + 3, 'ci', { split: '', ends: ['north', 'south'] });

    // main bearings on pedestals either side of the drum and outboard of the cranks
    for (const [x0, x1] of [[-15, -11], [25, 30], [-32, -27], [43, 48]]) {
      m.box(`ped_${x0}`, [x0 - 1, 0, AZ - 7], [x1 + 1, AY - 4, AZ + 7], 'ashlar');
      m.box(`brg_${x0}`, [x0, AY - 4, AZ - 5], [x1, AY + 4, AZ + 5], 'ci', { split: '' });
      m.box(`brg_cap_${x0}`, [x0 + 0.5, AY + 4, AZ - 3.5], [x1 - 0.5, AY + 5.5, AZ + 3.5], 'ci', { split: '' });
    }
    // drum pit edge and floor plates round the drum, chequer walkway between the engines
    m.box('floor_mid', [-6, 0, AZ + 40], [22, 1, CYL[1] + 4], 'chequer');

    // post brake on the west brake ring: two timber-lined posts hugging the ring, a weighted lever behind
    for (const s of [-1, 1]) {
      const z = AZ + s * (DR + 4.5);
      m.box(`brake_post_${s}`, [-10, 0, z - 2.5], [-5, AY + 8, z + 2.5], 'ci', { split: 'y' });
      m.box(`brake_shoe_${s}`, [-9.5, AY - 12, s < 0 ? z + 2.5 : z - 3.5], [-5.5, AY + 12, s < 0 ? z + 3.5 : z - 2.5], 'wood', { split: '' });
    }
    m.box('brake_beam', [-9, AY + 8, AZ - DR - 7], [-6, AY + 11, AZ + DR + 7], 'ci');
    m.box('brake_weight', [-11, 10, AZ + DR + 8], [-4, 20, AZ + DR + 15], 'ci', { split: '' });

    // driver's stand on the east: platform, rail, reversing lever, brake lever, steam throttle, depth indicator
    const SX0 = 50, SX1 = 62, SZ0 = AZ + 18, SZ1 = AZ + 50;
    m.box('stand', [SX0, 0, SZ0], [SX1, 12, SZ1], 'ashlar');
    m.box('stand_deck', [SX0, 12, SZ0], [SX1, 13, SZ1], 'chequer');
    for (const z of [SZ0, SZ1 - 0.8]) m.box(`stand_rail_${z}`, [SX0, 27, z], [SX1, 27.8, z + 0.8], 'bright', { split: '' });
    m.box('stand_rail_e', [SX1 - 0.8, 27, SZ0], [SX1, 27.8, SZ1], 'bright', { split: '' });
    for (const [x, z] of [[SX0, SZ0], [SX1 - 0.8, SZ0], [SX0, SZ1 - 0.8], [SX1 - 0.8, SZ1 - 0.8]]) m.box(`stand_post_${x}_${z}`, [x, 13, z], [x + 0.8, 27, z + 0.8], 'bright', { split: '' });
    m.box('stand_steps', [SX0 + 2, 0, SZ1], [SX1 - 2, 6, SZ1 + 6], 'ashlar');
    m.box('lever_quadrant', [SX0 + 1, 13, SZ0 + 8], [SX0 + 3, 20, SZ0 + 14], 'ci', { split: '' });
    m.box('lever_reverse', [SX0 + 1.5, 13, SZ0 + 10.5], [SX0 + 2.5, 31, SZ0 + 11.5], 'bright', { split: '', rot: { axis: 'x', angle: -22.5, origin: [SX0 + 2, 18, SZ0 + 11] } });
    m.box('lever_brake', [SX0 + 1.5, 13, SZ0 + 18.5], [SX0 + 2.5, 30, SZ0 + 19.5], 'red', { split: '', rot: { axis: 'x', angle: 22.5, origin: [SX0 + 2, 18, SZ0 + 19] } });
    m.box('throttle', [SX0 + 1, 22, SZ0 + 24], [SX0 + 3, 24, SZ0 + 28], 'br', { split: '' });
    // depth indicator: a cast column with a scale; the pointer is the `cursor` partial
    m.box('indicator_col', [SX1 - 6, 13, SZ0 + 2], [SX1 - 2, 48, SZ0 + 6], 'ci', { split: 'y' });
    m.box('indicator_scale', [SX1 - 6.6, 16, SZ0 + 2.5], [SX1 - 6, 46, SZ0 + 5.5], 'bright', { split: '' });
    m.box('indicator_cap', [SX1 - 7, 48, SZ0 + 1], [SX1 - 1, 50, SZ0 + 7], 'br', { split: '' });

    // Create input: coupling stub on the west end of the shaft is part of the drum; here only its bearing collar
    m.prism('input_collar', 'x', [AY, AZ], 3, -32, -31, 'br', { split: '', ends: ['west', 'east'] });
    m.decal('plate', [SX0 - 0.2, 4, SZ0 + 4], [SX0, 9, SZ0 + 13], 'ashlar', 'west', 'pl.maker');
    return m;
  }

  // Drum, shaft, crank webs and pins; everything turns about the shaft axis (x through y AY, z AZ).
  function drum() {
    const m = PCVV.mk('material/lagging_wood');
    m.prism('barrel', 'x', [AY, AZ], DR, DW[0] + 1.5, DW[1] - 1.5, 'wood', { split: '' });
    for (const [n, a0, a1] of [['fl_w', DW[0] - 1, DW[0] + 1.5], ['fl_e', DW[1] - 1.5, DW[1] + 1]]) {
      m.ring(n, 'x', [AY, AZ], DR + 3, 4.5, a0, a1, 'ci');
      m.disc(`${n}_web`, 'x', [AY, AZ], DR, a0 + 0.6, a1 - 0.6, 'ci', 'vver/flange', n === 'fl_w' ? 'east' : 'west');
    }
    m.ring('brake_ring', 'x', [AY, AZ], DR + 1.5, 2.5, DW[0] - 3, DW[0] - 1, 'bright');
    // a few turns of rope on the barrel next to the east flange
    for (let k = 0; k < 4; k++) m.ring(`rope_${k}`, 'x', [AY, AZ], DR + 0.6, 0.6, DW[1] - 3 - k * 1.1, DW[1] - 2.1 - k * 1.1, 'dark');
    // shaft through the bearings to both cranks and the input stub
    m.prism('shaft', 'x', [AY, AZ], 2.6, -32, 48, 'bright', { split: '', ends: ['west', 'east'] });
    m.prism('boss_w', 'x', [AY, AZ], 5, DW[0] - 4, DW[0] - 1, 'ci', { split: '' });
    m.prism('boss_e', 'x', [AY, AZ], 5, DW[1] + 1, DW[1] + 4, 'ci', { split: '' });
    // crank webs: west pin toward the cylinders (+z), east pin 90 degrees ahead (up)
    for (const [x, dy, dz] of [[EX[0], 0, CRANK], [EX[1], CRANK, 0]]) {
      const w0 = x < CX ? x + 2 : x - 4, w1 = w0 + 2;
      m.box(`web_${x}`, [w0, AY - 4 + Math.min(0, dy), AZ - 4 + Math.min(0, dz)], [w1, AY + 4 + Math.max(0, dy), AZ + 4 + Math.max(0, dz)], 'ci', { split: '' });
      m.prism(`pin_${x}`, 'x', [AY + dy, AZ + dz], 1.8, x - 2, x + 2, 'bright', { split: '', ends: ['west', 'east'] });
    }
    // Create coupling stub at the west face of cell (-2, 2, 0)
    m.prism('stub', 'x', [AY, AZ], 3.2, -32, -29, 'st', { split: '', ends: ['west'] });
    return m;
  }

  // Crosshead with slippers and the piston rod to the gland, at mid stroke: crosshead pin at the partial's (8, 8, 8),
  // rod toward +z. Slides along z by +-CRANK.
  function crosshead() {
    const m = PCVV.mk('material/bright_steel');
    m.box('block', [5, 5, 4], [11, 11, 12], 'ci', { split: '' });
    m.box('slipper_lo', [4.6, 3.6, 3], [11.4, 5, 13], 'br', { split: '' });
    m.box('slipper_hi', [4.6, 11, 3], [11.4, 12.4, 13], 'br', { split: '' });
    m.prism('pin', 'x', [8, 8], 1.4, 4, 12, 'bright', { split: '', ends: ['west', 'east'] });
    m.prism('rod', 'z', [8, 8], 1.1, 12, 32, 'bright', { split: '' });
    return m;
  }
  // Connecting rod, horizontal: small-end pin at the partial's (8, 8, S), big end ROD units toward -z (fits -16..32).
  const S = 28;
  function conrod() {
    const m = PCVV.mk('material/bright_steel');
    m.box('shank', [6.8, 6.2, S - ROD + 4], [9.2, 9.8, S - 4], 'bright', { split: '' });
    m.box('small_end', [6, 5.5, S - 4], [10, 10.5, S + 3], 'ci', { split: '' });
    m.box('big_end', [5.5, 4.5, S - ROD - 3], [10.5, 11.5, S - ROD + 4], 'ci', { split: '' });
    m.box('big_end_cap', [5.5, 4, S - ROD - 4], [10.5, 12, S - ROD - 3], 'br', { split: '' });
    return m;
  }
  // Depth indicator pointer: slides along y on the indicator column (partial's (8, 8, 8) = its top position).
  function cursor() {
    const m = PCVV.mk('material/brass');
    m.box('slider', [6.5, 6.5, 6], [9.5, 9.5, 10], 'br', { split: '' });
    m.box('needle', [4, 7.6, 7.6], [6.5, 8.4, 8.4], 'red', { split: '' });
    return m;
  }

  const PARTS = { crosshead, conrod, cursor };
  window.WINDING_ENGINE = {
    engine, drum, PARTS, CONROD_PIN: [8, 8, S], AXIS: [CX, AY, AZ], EX, XH, CRANK, ROD,
    build: (root) => { const m = PCVV.cut(engine()); PCART.realize(root, m, 'winding_engine'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'winding_engine', 'mining'),
    buildDrum: (root) => { const m = PCVV.cut(drum()); PCART.realize(root, m, 'winding_engine_drum'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    saveDrum: (root) => PCVV.save(root, 'winding_engine_drum', 'mining'),
    // preview with drum and both engines' moving parts in place (scale shots only, never saved)
    preview: (root) => {
      const m = engine().merge(drum(), 0, 0, 0, 'd_');
      // west crank at 0 degrees (pin toward the cylinder), east crank 90 degrees ahead (pin up), rods tilted to suit
      for (const [x, up] of [[EX[0], false], [EX[1], true]]) {
        const zx = up ? AZ + Math.sqrt(ROD * ROD - CRANK * CRANK) : AZ + CRANK + ROD;
        m.merge(crosshead(), x - 8, AY - 8, zx - 8, `xh${x}_`);
        const n0 = m.els.length;
        m.merge(conrod(), x - 8, AY - 8, zx - S, `cr${x}_`);
        if (up) for (const e of m.els.slice(n0)) e.rot = { axis: 'x', angle: Math.asin(CRANK / ROD) * 180 / Math.PI, origin: [x, AY, zx] };
      }
      PCART.realize(root, PCVV.cut(m), 'winding_engine_preview');
      return m.els.length;
    },
    buildPart: (root, p) => { const m = PARTS[p](); PCART.realize(root, m, p); return m.els.length; },
    savePart: (root, p) => PCVV.savePartial(root, 'winding_engine', p, 'mining'),
  };
})();
