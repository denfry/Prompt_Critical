// Guibal main mine fan at real scale (1 block = 1 m): a 30 ft centrifugal fan on a horizontal axle, closed in a
// brick casing that sits 2 m deep in a pit, the drift from the upcast shaft entering the eye on the north face, the
// évasé chimney rising on the east side and widening upward, with Guibal's sliding shutter at its throat, and a
// single horizontal steam engine on a masonry bed driving a crank on the south end of the axle (steam on the crank,
// no gearing, as Guibal built them). The rotor runs hidden in the casing; what moves in sight is the engine's crank,
// connecting rod and crosshead, which reuse the winding engine's partials. Dimensions and sources:
// docs/research/guibal_fan-dimensions.md (disk only).
//
// Controller = (0, 0, 0) on the ground under the axle middle. Axle along z (the facing axis) through x 8, y 40.
// Front north = the eye (airway port). Create input: stub on the south end of the axle, south face of cell (0, 2, 3).
//
// Load after pcart.js, pcround.js, pcslice.js, vverkit.js and winding_engine.author.js (for the preview), then
// GUIBAL_FAN.build(repo) /* wait */ GUIBAL_FAN.save(repo).
(function () {
  const AX = 8, AY = 40, R = 72;            // axle along z through (8, 40); rotor radius 4.5 m (30 ft fan)
  const ZN = -11, ZS = 27, WT = 12;         // rotor between z -11 and 27 (2.4 m), walls 0.75 m thick
  const CASE = R + 8, PIT = AY - CASE;      // casing radius, pit floor (y -40)
  const ZE = 50, CRANK = 9.75, ROD = 40;    // engine plane (crank pin) and the winding engine's crank and rod
  const XH = AX - ROD, CYL = [AX - 82, AX - 54], CR = 7.2;   // crosshead mid stroke, cylinder x -74..-46 (as the winder)
  const TAN = Math.tan(Math.PI / 8);

  function model() {
    const m = PCVV.mk('material/brick');
    const z0 = ZN - WT, z1 = ZS + WT;

    // casing: brick body round the rotor, half of it below ground in the pit; ashlar coping on the arch
    m.box('case_low', [AX - CASE, PIT, z0 + 0.1], [AX + CASE, AY, z1 - 0.1], 'brick');
    m.prism('case_arch', 'z', [AX, AY], CASE, z0, z1, 'brick', { cullY: AY, ends: ['north', 'south'] });
    m.ring('coping', 'z', [AX, AY], CASE + 1.5, 2, z0 - 1, z1 + 1, 'ashlar');
    m.els = m.els.filter((e) => !e.name.startsWith('coping') || (e.from[1] + e.to[1]) / 2 > AY);   // the arch only
    m.box('plinth', [AX - CASE - 2, -1, z0 - 2], [AX + CASE + 2, 2, z1 + 2], 'ashlar');
    // pilasters on the north and south faces
    for (const x of [AX - CASE, AX - 40, AX + 40, AX + CASE - 8]) for (const [n, za, zb] of [['n', z0 - 2, z0], ['s', z1, z1 + 2]]) {
      m.box(`pilaster_${n}_${x}`, [x, 2, za], [x + 8, x === AX - 40 || x === AX + 40 ? AY + 64 : AY + 20, zb], 'ashlar');
    }

    // eye on the north face: cast iron ring round the drift mouth (the airway port) and the inlet cone
    m.ring('eye_ring', 'z', [AX, AY], 34, 4, z0 - 4, z0 - 2, 'ci');
    m.disc('eye', 'z', [AX, AY], 30, z0 - 3, z0 - 2.5, 'dark', 'vver/grating_disc', 'north');
    m.nutsZ('eye_bolt', [AX, AY], 32, 16, z0 - 5, z0 - 4, 1.2, 'bright');

    // évasé: chimney on the east side, vertical east wall, the west wall leaning out 22.5 degrees over the casing,
    // side walls stepped to follow it; Guibal's shutter at the throat
    const EX0 = AX + CASE - 4, EX1 = EX0 + 24, EY0 = 8, EY1 = 264;
    m.box('evase_e', [EX1, -1, z0], [EX1 + 10, EY1, z1], 'brick');
    // the west wall steps out 0.2 m per metre above the casing crown (corbelled brick), ~11 degrees
    const LY = AY + CASE, lean = (y) => EX0 - Math.max(0, y - LY) * 0.2;
    for (let y = EY0; y < EY1; y += 16) {
      const xl = lean(y + 16);
      for (const [n, za, zb] of [['n', z0, z0 + WT], ['s', z1 - WT, z1]]) m.box(`evase_${n}_${y}`, [xl - 10, y, za], [EX1, y + 16, zb], 'brick', { split: '' });
      if (y >= AY) m.box(`evase_w_${y}`, [xl - 10, y, z0 + WT], [xl, y + 16, z1 - WT], 'brick', { split: '' });
    }
    m.box('evase_cap_e', [EX1 - 2, EY1, z0 - 2], [EX1 + 12, EY1 + 3, z1 + 2], 'ashlar');
    m.box('evase_soot', [lean(EY1) + 2, EY1 - 3, z0 + WT], [EX1, EY1 - 2, z1 - WT], 'soot');
    // shutter: an iron plate sliding on guides at the throat, worked by a screw from a hand wheel on the east wall
    m.box('shutter', [EX0 + 1, AY + 30, z0 + WT + 1], [EX0 + 3, AY + 56, z1 - WT - 1], 'ci');
    m.box('shutter_screw', [EX0 + 3, AY + 42, (z0 + z1) / 2 - 0.6], [EX1 + 12, AY + 43.2, (z0 + z1) / 2 + 0.6], 'bright', { split: '' });
    m.wheelY('shutter_wheel', EX1 + 12, AY + 42.6, (z0 + z1) / 2, 3, 'red');
    m.box('shutter_wheel_brk', [EX1 + 10, AY + 38, (z0 + z1) / 2 - 1], [EX1 + 12, AY + 44, (z0 + z1) / 2 + 1], 'ci', { split: '' });

    // axle bearings on the walls, the south bearing pedestal and the outboard bearing past the crank
    m.prism('axle', 'z', [AX, AY], 3, z1, ZE + 14, 'bright', { split: '', ends: ['south'] });
    for (const [n, za, zb] of [['brg_s', z1 + 2, z1 + 8], ['brg_o', ZE + 6, ZE + 12]]) {
      m.box(`${n}_ped`, [AX - 8, 0, za - 1], [AX + 8, AY - 5, zb + 1], 'ashlar');
      m.box(n, [AX - 6, AY - 5, za], [AX + 6, AY + 5, zb], 'ci', { split: '' });
      m.box(`${n}_cap`, [AX - 4, AY + 5, za + 0.5], [AX + 4, AY + 6.5, zb - 0.5], 'ci', { split: '' });
    }

    // the engine: horizontal, running along x to the west of the axle, crank in the plane z = ZE
    m.box('eng_found', [CYL[0] - 6, 0, ZE - 9], [AX - 8, 22, ZE + 9], 'ashlar');
    m.box('eng_bed_n', [CYL[1], 22, ZE - 8], [AX - 10, AY - 4, ZE - 4], 'ci');
    m.box('eng_bed_s', [CYL[1], 22, ZE + 4], [AX - 10, AY - 4, ZE + 8], 'ci');
    m.box('eng_bed_floor', [CYL[1], 22, ZE - 8], [AX - 10, 25, ZE + 8], 'ci');
    m.box('guide_lo', [XH - 14, AY - 5, ZE - 3.5], [XH + 14, AY - 3.5, ZE + 3.5], 'bright');
    m.box('guide_hi', [XH - 14, AY + 3.5, ZE - 3.5], [XH + 14, AY + 5, ZE + 3.5], 'bright');
    for (const x of [XH - 16, XH + 14]) m.box(`guide_post_${x}`, [x, AY - 4, ZE - 4], [x + 2, AY + 4, ZE + 4], 'ci', { split: '' });
    m.prism('cyl', 'x', [AY, ZE], CR, CYL[0] + 2, CYL[1] - 2, 'wood', { split: '' });
    for (const x of [CYL[0] + 6, (CYL[0] + CYL[1]) / 2, CYL[1] - 6]) m.ring(`cyl_band_${x}`, 'x', [AY, ZE], CR + 0.3, 0.6, x, x + 1, 'br');
    m.prism('cyl_fc', 'x', [AY, ZE], CR + 0.8, CYL[1] - 2, CYL[1], 'ci', { split: '', ends: ['east'] });
    m.prism('cyl_bc', 'x', [AY, ZE], CR + 0.8, CYL[0], CYL[0] + 2, 'ci', { split: '', ends: ['west'] });
    m.disc('cyl_end', 'x', [AY, ZE], CR - 1, CYL[0] - 0.6, CYL[0], 'ci', 'vver/flange', 'west');
    m.prism('gland', 'x', [AY, ZE], 2.2, CYL[1], CYL[1] + 4, 'br', { split: '', ends: ['east'] });
    m.box('cyl_foot', [CYL[0] + 3, 22, ZE - 6], [CYL[1] - 3, AY - CR + 1, ZE + 6], 'ci');
    m.box('chest', [CYL[0] + 4, AY - 6, ZE + CR], [CYL[1] - 4, AY + 6, ZE + CR + 4], 'ci');
    m.box('chest_cover', [CYL[0] + 5, AY - 5, ZE + CR + 4], [CYL[1] - 5, AY + 5, ZE + CR + 4.6], 'ci');
    const MX = (CYL[0] + CYL[1]) / 2;
    m.box('steam_rise', [MX - 2, AY + 6, ZE + CR + 0.5], [MX + 2, AY + 14, ZE + CR + 3.5], 'ci', { split: '' });
    m.box('stopvalve', [MX - 2.5, AY + 14, ZE + CR - 0.5], [MX + 2.5, AY + 17, ZE + CR + 4.5], 'br', { split: '' });
    m.box('steam_pipe', [MX - 2, AY + 17, ZE + CR + 0.5], [MX + 2, AY + 34, ZE + CR + 3.5], 'felt', { split: '' });
    m.prism('steam_flange', 'z', [MX, AY + 34], 3.5, ZE + CR - 1, ZE + CR + 5, 'ci', { split: '', ends: ['north', 'south'] });
    m.box('exhaust', [CYL[0] + 8, 22, ZE + CR + 0.5], [CYL[0] + 12, AY - 6, ZE + CR + 3.5], 'ci', { split: '' });
    // governor on a bracket over the guides and the engineman's floor plates
    m.box('gov_post', [XH - 2, AY + 5, ZE + 5], [XH, AY + 20, ZE + 7], 'ci', { split: '' });
    m.box('gov_balls', [XH - 4, AY + 20, ZE + 3], [XH + 2, AY + 24, ZE + 9], 'br', { split: '' });
    m.box('floor', [CYL[0] - 6, 0, ZE + 9], [AX + 10, 1, ZE + 14], 'chequer');

    // Create input stub past the outboard bearing; the rotating crank and pin are drawn by the renderer
    m.prism('input_collar', 'z', [AX, AY], 3.6, ZE + 13, ZE + 14, 'br', { split: '', ends: ['north', 'south'] });
    m.decal('plate', [AX + 44, 6, z0 - 2.2], [AX + 54, 12, z0 - 2], 'ashlar', 'north', 'pl.maker');
    return m;
  }

  // The crank on the south end of the axle (web, pin) and the coupling stub: a partial about z through (8, 8, 8)
  // (the axle), pin at radius CRANK toward -x at angle 0, set at z = ZE.
  function crank() {
    const m = PCVV.mk('material/cast_iron');
    m.box('web', [8 - CRANK - 4, 4, 2], [12, 12, 6], 'ci', { split: '' });
    m.prism('pin', 'z', [8 - CRANK, 8], 1.8, 6, 10, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('boss', 'z', [8, 8], 5, 0, 6, 'ci', { split: '' });
    m.prism('stub', 'z', [8, 8], 3.2, 18, 22, 'st', { split: '', ends: ['south'] });
    return m;
  }

  const S = 28;   // the winding engine conrod's small-end pin in its partial
  window.GUIBAL_FAN = {
    model, crank, AXIS: [AX, AY], ZE, XH, CRANK, ROD,
    build: (root) => { const m = PCVV.cut(model()); PCART.realize(root, m, 'guibal_fan'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root) => PCVV.save(root, 'guibal_fan', 'mining'),
    buildCrank: (root) => { const m = crank(); PCART.realize(root, m, 'crank'); return m.els.length; },
    saveCrank: (root) => PCVV.savePartial(root, 'guibal_fan', 'crank', 'mining'),
    // preview with the crank, the winding engine's crosshead and connecting rod turned to run along x (shots only)
    preview: (root) => {
      const m = model().merge(crank(), AX - 8, AY - 8, ZE - 8, 'ck_');
      // a partial built along z, turned -90 degrees about y (its +z to world -x, its +x to world +z), with its pivot
      // (8, 8, pz) put on the crosshead pin; crank at angle 0, so the crosshead stands at the end of its stroke
      const W = AX - CRANK - ROD;
      const yaw = (other, pz, prefix) => {
        for (const e of other.els) {
          const f = e.from, t = e.to;
          m.els.push(Object.assign({}, e, { name: prefix + e.name, rot: undefined,
            from: [W - (t[2] - pz), f[1] + AY - 8, ZE + f[0] - 8], to: [W - (f[2] - pz), t[1] + AY - 8, ZE + t[0] - 8] }));
        }
      };
      yaw(WINDING_ENGINE.PARTS.crosshead(), 8, 'xh_');
      yaw(WINDING_ENGINE.PARTS.conrod(), S, 'cr_');
      PCART.realize(root, PCVV.cut(m), 'guibal_fan_preview');
      return m.els.length;
    },
  };
})();
