// Rotating machines of the I1 grid, hand-authored for Blockbench (replace the spec_grid.py placeholders): the
// shaft-driven synchronous generator with its rotor, the DC exciter, and the induction motor (0.4 kV and 6 kV)
// with its shaft.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/grid/rotating_machines.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); RMC.paint('<repo>')
//   build + save each model in turn (RMC.build / RMC.save, ids in RMC.ids)
//
// All 1x1x1, front to the north, axis along z through (8, 8): GridClient spins the generator rotor and the motor
// shaft (SPIN_Z about x 8, y 8).
// Generator: a cast stator frame on feet with ventilating ducts, end shields whose spokes leave open windows
// (cutout) so the rotor's fans are seen turning inside, the terminal box on top with three copper leads, lifting
// eyes and a nameplate. The Create shaft comes in from the south through the coupling half on the rotor.
// Exciter: a small DC machine, frame with brush gear over the commutator end and a forcing lamp.
// Motor: totally enclosed fan-cooled, ribbed frame, flange at the shaft end (north), fan cowl at the back; the
// 6 kV variant has a taller frame and a bigger terminal box.
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, grain, writePng } = PCART;
  const T = {};

  // Generator enamel: Elektrosila dark green, oil-darkened low down.
  T['shaft_generator/enamel'] = () => {
    const im = img(32, 32), r = grain(im, [52, 82, 64], 2401, 3, 4, 8);
    for (let y = 22; y < 32; y++) for (let x = 0; x < 32; x++) {
      const i = (y * 32 + x) * 4; put(im, x, y, mix([im.d[i], im.d[i + 1], im.d[i + 2]], [30, 36, 32], (y - 22) / 20));
    }
    return im;
  };
  // Stator core seen through the frame: laminations with dark ventilating ducts every 4 px.
  T['shaft_generator/ducts'] = () => {
    const im = img(32, 32), r = grain(im, [52, 82, 64], 2411, 3, 4, 8);
    for (let x = 2; x < 32; x += 4) rect(im, x, 3, 1, 26, [26, 40, 32]);
    return im;
  };
  // End shield: six spokes and a hub, open between them (transparent), rim round the edge.
  T['shaft_generator/shield'] = () => {
    const im = img(32, 32), r = rng(2421);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x + 0.5 - 16, dy = y + 0.5 - 16, d = Math.hypot(dx, dy) / 16, a = Math.atan2(dy, dx);
      const spoke = Math.abs(Math.sin(a * 3)) < 0.22;
      if (d > 1) continue;
      if (d > 0.82 || d < 0.32 || spoke) put(im, x, y, add(d > 0.82 ? [46, 74, 58] : [56, 86, 68], (r() - 0.5) * 8));
    }
    return im;
  };
  // Copper: leads and the rotor's fan blades.
  T['shaft_generator/copper'] = () => { const im = img(16, 16); grain(im, [186, 110, 60], 2431, 6, 8, 4); return im; };
  // Rotor: salient poles with field coils, dark varnish.
  T['shaft_generator/rotor'] = () => {
    const im = img(32, 32), r = grain(im, [60, 52, 44], 2441, 4, 5, 8);
    for (let y = 0; y < 32; y += 8) rect(im, 0, y, 32, 3, [150, 90, 50]);
    return im;
  };
  // Motor enamel: RAL 5010 gentian blue.
  T['electric_motor/enamel'] = () => { const im = img(32, 32); grain(im, [34, 74, 122], 2451, 3, 4, 8); return im; };
  // Fan cowl grille: steel louvres over the fan.
  T['electric_motor/cowl'] = () => {
    const im = img(16, 16), r = grain(im, [34, 74, 122], 2461, 3, 3, 4);
    for (let y = 2; y < 15; y += 2) rect(im, 2, y, 12, 1, [14, 20, 28]);
    return im;
  };
  // Exciter brush gear: carbon brushes in brass holders over the copper commutator.
  T['generator_exciter/brushes'] = () => {
    const im = img(32, 32), r = rng(2471);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, x % 4 === 0 ? [90, 54, 30] : add([186, 110, 60], (r() - 0.5) * 14));
    for (let x = 2; x < 32; x += 6) { rect(im, x, 4, 3, 10, [200, 168, 92]); rect(im, x + 0.5, 6, 2, 6, [30, 30, 32]); }
    return im;
  };

  const MAT = {
    ge: { tex: 'shaft_generator/enamel', P: 16 }, duct: { tex: 'shaft_generator/ducts', P: 16 },
    cu: { tex: 'shaft_generator/copper', P: 16 }, rot: { tex: 'shaft_generator/rotor', P: 16 },
    me: { tex: 'electric_motor/enamel', P: 16 },
  };
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle: particle || 'shaft_generator/enamel' });

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block', n + '.png'));
    return Object.keys(T).length;
  }

  // Radial bar about the z axis through (8, 8) at angle a (multiple of 22.5; 0 = up), radius r0..r1, width w,
  // z0..z1 (same trick as the shearer drum: nearest quarter, then the rest as a rotation within +-45).
  function radialZ(m, name, z0, z1, r0, r1, w, a, mat) {
    const q = Math.round(a / 90), rest = a - q * 90, dir = ((q % 4) + 4) % 4;
    let f, t;
    if (dir === 0) { f = [8 - w / 2, 8 + r0, z0]; t = [8 + w / 2, 8 + r1, z1]; }
    else if (dir === 1) { f = [8 + r0, 8 - w / 2, z0]; t = [8 + r1, 8 + w / 2, z1]; }
    else if (dir === 2) { f = [8 - w / 2, 8 - r1, z0]; t = [8 + w / 2, 8 - r0, z1]; }
    else { f = [8 - r1, 8 - w / 2, z0]; t = [8 - r0, 8 + w / 2, z1]; }
    m.box(name, f, t, mat, { split: '', rot: rest ? { axis: 'z', angle: rest, origin: [8, 8, 8] } : undefined });
  }

  function generator() {
    const m = mk();
    m.box('feet_w', [1, 0, 2.6], [4.4, 2, 13.4], 'ge', { split: '' });
    m.box('feet_e', [11.6, 0, 2.6], [15, 2, 13.4], 'ge', { split: '' });
    m.prism('stator', 'z', [8, 8], 6.7, 4, 12, 'duct', { split: '' });
    for (const [z0, z1] of [[2.6, 4], [12, 13.4]]) m.prism(`frame_ring_${z0}`, 'z', [8, 8], 6.8, z0, z1, 'ge', { split: '' });
    // end shields: open spokes (cutout texture) on both faces, bearing housings in the middle
    m.disc('shield_n', 'z', [8, 8], 6.2, 2.6, 2.8, 'ge', 'shaft_generator/shield', 'north');
    m.disc('shield_s', 'z', [8, 8], 6.2, 13.2, 13.4, 'ge', 'shaft_generator/shield', 'south');
    m.prism('bearing_n', 'z', [8, 8], 1.8, 1.4, 2.6, 'ge', { split: '', ends: ['north'] });
    m.prism('bearing_s', 'z', [8, 8], 1.8, 13.4, 14.6, 'ge', { split: '', ends: ['south'] });
    // terminal box with three copper leads, lifting eyes, nameplate
    m.box('terminal_box', [5, 14, 5.6], [11, 16, 10.6], 'ge', { split: '' });
    for (const x of [5.6, 7.6, 9.6]) m.box(`lead_${x}`, [x, 15, 10.6], [x + 0.8, 15.8, 12.4], 'cu', { split: '' });
    for (const z of [3.2, 12.2]) m.box(`eye_${z}`, [7.4, 14.2, z], [8.6, 15.4, z + 0.6], 'st', { split: '' });
    m.box('nameplate', [13.6, 6, 6], [14.8, 9, 10], 'br', { split: '' });
    return m;
  }

  // Generator rotor partial: shaft right through, salient-pole body, a fan behind each shield, coupling half at
  // the south end.
  function genRotor() {
    const m = mk('shaft_generator/rotor');
    m.prism('shaft', 'z', [8, 8], 0.9, 0, 16, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('body', 'z', [8, 8], 4.2, 4.2, 11.8, 'rot', { split: '' });
    for (const z of [3, 12.2]) for (let k = 0; k < 8; k++) radialZ(m, `fan_${z}_${k}`, z, z + 0.8, 1, 5.4, 0.5, k * 45, 'cu');
    m.prism('coupling', 'z', [8, 8], 2.4, 14.8, 16, 'ci', { split: '', ends: ['north', 'south'] });
    return m;
  }

  // DC exciter at real size (frame Ø 0.8 m, 1 m long) on a cast pedestal: 1x2x1, the controller on the generator
  // axis (8, 8) 1.5 m up, the pedestal in the cell below (y -16..0). The coupling faces south onto the generator's
  // shaft; the commutator and its brush gear are at the outboard (north) end, with the forcing lamp and terminal box.
  function exciter() {
    const m = mk();
    m.box('ped_base', [1, -16, 1], [15, -14, 15], 'ci', { split: '' });
    m.box('ped_column', [3, -14, 3], [13, 0, 13], 'ge', { split: '' });
    for (const [x0, x1, z0, z1] of [[2, 3, 6, 10], [13, 14, 6, 10], [6, 10, 2, 3], [6, 10, 13, 14]]) m.box(`ped_rib_${x0}_${z0}`, [x0, -14, z0], [x1, -2, z1], 'ge', { split: '' });
    for (const x of [1.8, 13.2]) for (const z of [1.8, 13.2]) m.box(`ped_bolt_${x}_${z}`, [x, -14, z], [x + 1, -13.4, z + 1], 'st', { split: '' });
    m.box('ped_top', [2, -0.4, 2], [14, 0, 14], 'ci', { split: '' });
    m.box('feet', [2.4, 0, 3], [13.6, 1.8, 14], 'ge', { split: '' });
    m.prism('frame', 'z', [8, 8], 6.4, 3.4, 14.4, 'ge', { split: '', ends: ['north', 'south'] });
    for (const z of [3.4, 13.4]) m.prism(`yoke_ring_${z}`, 'z', [8, 8], 6.8, z, z + 1, 'ge', { split: '' });
    // commutator end: copper commutator, four brush arms on a rocker ring, cover
    m.prism('commutator', 'z', [8, 8], 3.2, 0.6, 3.4, 'cu', { split: '', ends: ['north'] });
    m.prism('rocker', 'z', [8, 8], 4.6, 2.2, 2.8, 'ge', { split: '' });
    for (const [x0, y0, x1, y1] of [[7.2, 11.2, 8.8, 13], [7.2, 3, 8.8, 4.8], [11.2, 7.2, 13, 8.8], [3, 7.2, 4.8, 8.8]]) m.box(`brush_${x0}_${y0}`, [x0, y0, 1], [x1, y1, 2.4], 'br', { split: '' });
    m.box('brush_gear', [3.4, 14.4, 4], [12.6, 15, 9], 'ge', { split: '', face: { up: { tex: 'generator_exciter/brushes', uv: [0, 0, 16, 16] } } });
    m.prism('shaft', 'z', [8, 8], 1.1, 0, 16, 'bright', { split: '', ends: ['north', 'south'] });
    m.prism('coupling', 'z', [8, 8], 2.8, 14.4, 16, 'ci', { split: '', ends: ['south'] });
    m.box('terminal_box', [5, 14.4, 9.4], [11, 16, 13], 'ge', { split: '' });
    m.box('lamp_forcing', [11.8, 12.4, 2.4], [13.2, 13.8, 3.2], 'red', { split: '' });
    m.box('rating_plate', [14.4, 5, 6], [14.8, 8, 10], 'br', { split: '' });
    return m;
  }

  // TEFC induction motor; mv = the 6 kV variant (taller frame, bigger terminal box).
  function motor(mv) {
    const m = mk('electric_motor/enamel');
    const r = mv ? 5.6 : 5;
    m.box('feet_w', [1.6, 0, 3], [4.6, 1.6, 13], 'me', { split: '' });
    m.box('feet_e', [11.4, 0, 3], [14.4, 1.6, 13], 'me', { split: '' });
    m.box('foot_web_w', [3.4, 1.6, 4], [5.4, 9 - r, 12], 'me', { split: '' });
    m.box('foot_web_e', [10.6, 1.6, 4], [12.6, 9 - r, 12], 'me', { split: '' });
    m.prism('frame', 'z', [8, 8], r, 2.6, 13, 'me', { split: '', ends: ['north', 'south'] });
    // cooling ribs all round except under the feet (the 100 kW frame is ribbed on every side)
    const ribs = mv ? [-3, -2, -1, 0, 1, 2, 3, -4, 4] : [-6, -5, -4, -3, -2, -1, 0, 1, 2, 3, 4, 5, 6];
    for (const k of ribs) radialZ(m, `rib_${k}`, 2.8, 13, r - 0.2, r + 0.9, 0.5, k * 22.5, 'me');
    if (!mv) {
      // slide rails under the feet (belt or coupling alignment), earthing stud, lifting eye, conduit to the floor
      for (const x of [1.1, 11.1]) m.box(`rail_${x}`, [x, 0, 1.6], [x + 3.8, 0.6, 14.4], 'st', { split: '' });
      for (const x of [2.4, 12.2]) for (const z of [2, 13.4]) m.box(`rail_bolt_${x}_${z}`, [x, 0.6, z], [x + 1, 1.1, z + 0.6], 'bright', { split: '' });
      m.box('eye', [7.4, 8 + r + 0.6, 6.6], [8.6, 8 + r + 2, 7.2], 'st', { split: '' });
      m.box('earth_stud', [14.4, 1.6, 11], [15, 2.4, 12], 'br', { split: '' });
      m.box('conduit', [11.8, 0, 7.4], [12.8, 12.6, 8.6], 'st', { split: 'y' });
    }
    m.prism('flange', 'z', [8, 8], r - 0.6, 1.4, 2.6, 'me', { split: '', ends: ['north'] });
    m.prism('cowl', 'z', [8, 8], r + 0.4, 13, 15.4, 'me', { split: '' });
    m.disc('cowl_grille', 'z', [8, 8], r + 0.2, 15.4, 15.6, 'me', 'electric_motor/cowl', 'south');
    const tb = mv ? [4.4, 13.6, 4.6, 11.6, 16, 11.4] : [5, 12.6, 5.6, 11, 15, 10.6];
    m.box('terminal_box', [tb[0], tb[1], tb[2]], [tb[3], tb[4], tb[5]], 'me', { split: '' });
    m.box('gland', [tb[3], tb[1] + 0.6, 7.4], [tb[3] + 1.2, tb[1] + 1.8, 8.6], 'br', { split: '' });
    m.box('rating_plate', [13.8, 4.6, 6], [14.4, 6.8, 10], 'br', { split: '' });
    return m;
  }

  // Motor shaft partial: shaft end at the flange with the key.
  function motorShaft() {
    const m = mk('material/bright_steel');
    m.prism('shaft', 'z', [8, 8], 1, 0, 1.4, 'bright', { split: '', ends: ['north'] });
    m.box('key', [7.75, 8.8, 0], [8.25, 9.3, 1.4], 'st', { split: '' });
    return m;
  }

  const BUILDS = {
    shaft_generator: { model: generator },
    'shaft_generator/rotor': { model: genRotor, java: 'shaft_generator/partial/rotor' },
    generator_exciter: { model: exciter },
    electric_motor: { model: () => motor(false) },
    electric_motor_mv: { model: () => motor(true) },
    'electric_motor/shaft': { model: motorShaft, java: 'electric_motor/partial/shaft' },
  };

  window.RMC = {
    paint, ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'grid', id, BUILDS[id].java),
    preview(root) {
      return PCART.realize(root, generator().merge(genRotor(), 0, 0, 0, 'r_').merge(exciter(), 20, 0, 0, 'x_')
        .merge(motor(false), 40, 0, 0, 'm_').merge(motorShaft(), 40, 0, 0, 's_').merge(motor(true), 60, 0, 0, 'v_'), 'rotating_preview');
    },
  };
})();
