// The I1 water-tube boiler (07 §7.9.5, TP-35 / BKZ-75 type), hand-authored for Blockbench (replaces the
// spec_grid.py and spec_plant.py placeholders): the boiler control board with its BMS lamps, the furnace wall
// (outer cladding, the membrane water wall and the slagged wall), the burner (pulverised coal and gas, lit and
// dark), the convective surfaces (pendant superheater platens, economiser coils, tubular air heater), the forced
// draught fan and the induced draught fan with their impellers, and the steam drum.
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/boilers/water_tube_boiler.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); PLANT.paint('<repo>'); WTB.paint('<repo>')
//   await WTB.exportAll('<repo>')
//
// Front to the north; a placed block faces the player, so the north face is the one seen from the boiler house.
// Contracts with the code (pc-mod powerhouse):
//   boiler_wall and boiler_burner are full blocks of the furnace shell: faces on the block boundary cull against
//     a solid neighbour. The burner's north side is the operator side (windbox, register, peephole); its south
//     side, inside the furnace, shows the refractory quarl and the flame while lit.
//   forced_draft_fan / induced_draft_fan: the Create shaft comes in at the north face; the impeller partial spins
//     about z through x 8, y 8 (FoundryMachineRenderer SPIN_Z). It carries the shaft coupling (seen between the
//     shaft and the bearing) and the impeller seen through the inlet at the south.
//   steam_drum: drums in a row join along x; the downcomer goes down into the roof wall below.
(function () {
  const fs = require('fs');
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX = 'boilers/';
  const T = {};

  // ---------------------------------------------------------------- textures (32 px per block)
  // Outer cladding of the furnace in RAL 7004 signal grey: trapezoid profiled sheet, ribs every 8 px, a lap and
  // screws at the bottom of each sheet.
  T.wall_clad = () => {
    const im = img(32, 32), r = rng(4101), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const k = x % 8, rib = k === 0 ? 18 : k === 1 ? 8 : k === 6 ? -6 : k === 7 ? -16 : 0;
      put(im, x, y, add([150, 151, 153], rib + (n(x, y) - 0.5) * 10 + (r() - 0.5) * 5));
    }
    for (let x = 0; x < 32; x++) { put(im, x, 30, [116, 117, 119]); put(im, x, 31, [176, 177, 179]); }
    for (let x = 4; x < 32; x += 8) put(im, x, 29, [86, 86, 88]);
    for (let k = 0; k < 6; k++) { const x = Math.floor(r() * 32); for (let j = 0; j < 4 + r() * 8; j++) put(im, x, 31 - j, add(get(im, x, 31 - j), -10)); }
    return im;
  };
  // Membrane water wall seen from the furnace: vertical tubes 4 px apart, fin between, oxide; slagged: glassy
  // brown-black slag runs and buff ash on the tubes.
  const waterwall = (slag) => () => {
    const im = img(32, 32), r = rng(slag ? 4112 : 4111), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const k = x % 4, sh = [-34, 4, 16, 2][k];
      put(im, x, y, add(mix([72, 70, 68], [96, 72, 54], n(x, y) * 0.6), sh + (r() - 0.5) * 6));
    }
    if (slag) {
      for (let k = 0; k < 9; k++) {
        const x0 = Math.floor(r() * 32), y0 = Math.floor(r() * 24), w = 3 + Math.floor(r() * 6), h = 4 + Math.floor(r() * 10);
        for (let j = 0; j < h; j++) for (let i = 0; i < w - j * 0.3; i++) {
          const c = r() < 0.5 ? [52, 38, 30] : [78, 56, 40];
          put(im, x0 + i, y0 + j, add(c, (r() - 0.5) * 12 + (j === 0 ? 30 : 0)));
        }
      }
      for (let k = 0; k < 60; k++) put(im, r() * 32, r() * 32, add([186, 170, 140], (r() - 0.5) * 20));
    }
    return im;
  };
  T.waterwall = waterwall(false);
  T.waterwall_slag = waterwall(true);
  // Fireclay brick of the burner quarl: buff, 8x4 px, thin joints.
  T.firebrick = () => {
    const im = img(32, 32), r = rng(4121);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const row = Math.floor(y / 4), joint = y % 4 === 3 || (x + (row % 2) * 4) % 8 === 7;
      put(im, x, y, joint ? add([150, 136, 112], (r() - 0.5) * 8) : add([206, 178, 132], (r() - 0.5) * 16 - (row % 3) * 4));
    }
    for (let k = 0; k < 20; k++) put(im, r() * 32, r() * 32, [70, 56, 44]);
    return im;
  };
  // Burner swirl register: radial vanes round the core, dark steel.
  T.register = () => {
    const im = img(32, 32), r = rng(4131);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const dx = x - 15.5, dy = y - 15.5, d = Math.hypot(dx, dy), a = Math.atan2(dy, dx) + d * 0.08;
      let c = add([70, 72, 74], Math.cos(a * 12) * 14 + (r() - 0.5) * 6);
      if (d < 6) c = add([40, 40, 42], (r() - 0.5) * 6);
      if (d > 14.5) c = [58, 60, 62];
      put(im, x, y, c);
    }
    return im;
  };
  // Pulverised-coal flame, four frames (animated by a .mcmeta): white-yellow core, orange, red rim, flickering.
  T.flame = () => {
    const F = 4, im = img(32, 32 * F), r = rng(4141), n = vnoise(r, 32, 32, 6);
    for (let f = 0; f < F; f++) for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const d = Math.hypot(x - 15.5, y - 15.5) / 16, t = n(x + f * 8, y + f * 5) * 0.5 + d;
      const c = t < 0.35 ? [255, 250, 210] : t < 0.6 ? [255, 206, 80] : t < 0.85 ? [250, 132, 30] : [196, 60, 20];
      put(im, x, y + f * 32, add(c, (r() - 0.5) * 10));
    }
    return im;
  };
  // Boiler control board mimic: beige board, the furnace and convective pass in black, drum, steam line red,
  // feed water green, air blue, flue gas yellow, burner positions orange.
  T.mimic = () => {
    const im = img(32, 32), r = grain(im, [204, 198, 176], 4151, 2, 2, 8);
    const ink = [28, 28, 30];
    rect(im, 4, 9, 10, 18, ink); rect(im, 5, 10, 8, 16, [204, 198, 176]);   // furnace
    rect(im, 16, 9, 6, 18, ink); rect(im, 17, 10, 4, 16, [204, 198, 176]);  // convective pass
    rect(im, 6, 4, 14, 3, ink); rect(im, 7, 5, 12, 1, [70, 120, 200]);     // drum
    for (const y of [16, 21]) { rect(im, 3, y, 3, 2, [240, 130, 30]); }     // burners
    rect(im, 19, 2, 1, 3, [190, 40, 30]); rect(im, 19, 2, 11, 1, [190, 40, 30]); // steam out
    rect(im, 22, 24, 8, 1, [40, 140, 70]); rect(im, 29, 6, 1, 19, [40, 140, 70]); // feed water
    rect(im, 0, 28, 8, 1, [50, 90, 190]); rect(im, 0, 18, 1, 11, [50, 90, 190]);  // air
    rect(im, 22, 12, 6, 1, [220, 180, 30]);                                   // flue gas
    for (let x = 0; x < 32; x++) { put(im, x, 0, [120, 116, 104]); put(im, x, 31, [120, 116, 104]); }
    return im;
  };
  // Drum water level glass: steel frame, dark window, water in the lower half, a red mark at normal level.
  T.level = () => {
    const im = img(16, 16), r = rng(4161);
    rect(im, 0, 0, 16, 16, [70, 72, 76]);
    for (let y = 1; y < 15; y++) for (let x = 5; x < 11; x++) put(im, x, y, y > 8 ? add([70, 130, 170], (r() - 0.5) * 10) : add([34, 38, 44], (r() - 0.5) * 6));
    rect(im, 4, 8, 8, 1, [200, 40, 30]);
    return im;
  };
  // Boiler tubes: dark steel with oxide (economiser), and temper-tinted superheater steel (straw to purple-blue).
  T.tube = () => {
    const im = img(32, 32), r = grain(im, [74, 72, 70], 4171, 5, 8, 8);
    for (let k = 0; k < 30; k++) put(im, r() * 32, r() * 32, [104, 76, 54]);
    return im;
  };
  T.tube_hot = () => {
    const im = img(32, 32), r = rng(4181), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = n(x, y), c = t < 0.35 ? [96, 84, 120] : t < 0.6 ? [84, 98, 132] : t < 0.8 ? [130, 104, 70] : [92, 86, 84];
      put(im, x, y, add(c, (r() - 0.5) * 10));
    }
    return im;
  };
  // Tube sheet of the tubular air heater: a grid of tube ends with dark bores and sooty rims.
  T.tubesheet = () => {
    const im = img(32, 32), r = grain(im, [96, 94, 90], 4191, 4, 4, 8);
    for (let y = 2; y < 32; y += 4) for (let x = 2; x < 32; x += 4) {
      rect(im, x - 1, y - 1, 3, 3, [130, 126, 120]); rect(im, x, y, 1, 1, [16, 14, 14]); put(im, x + 1, y, [40, 36, 34]);
    }
    return im;
  };
  // Soot-streaked dark grey paint of the induced draught fan.
  T.id_paint = () => {
    const im = img(32, 32), r = grain(im, [74, 78, 80], 4201, 4, 6, 8);
    for (let k = 0; k < 7; k++) { const x = Math.floor(r() * 32), y0 = Math.floor(r() * 16); for (let j = 0; j < 8 + r() * 12; j++) put(im, x + (j > 6 ? 1 : 0), y0 + j, add(get(im, x, y0 + j), -22)); }
    return im;
  };
  // Rotation arrow decal for the fan casings (red on the paint).
  T.arrow = () => {
    const im = img(16, 16);
    rect(im, 0, 0, 16, 16, [210, 206, 196]);
    rect(im, 3, 7, 8, 2, [190, 30, 24]);
    for (let k = 0; k < 4; k++) rect(im, 10 + k, 4 + k, 1, 8 - 2 * k, [190, 30, 24]);
    return im;
  };

  const MAT = Object.assign({}, PLANT.MAT, {
    wclad: { tex: TEX + 'wall_clad', P: 16 }, ww: { tex: TEX + 'waterwall', P: 16 }, wws: { tex: TEX + 'waterwall_slag', P: 16 },
    fb: { tex: TEX + 'firebrick', P: 16 }, fire: { tex: TEX + 'flame', P: 16 }, tube: { tex: TEX + 'tube', P: 16 },
    thot: { tex: TEX + 'tube_hot', P: 16 }, tsheet: { tex: TEX + 'tubesheet', P: 16 }, idp: { tex: TEX + 'id_paint', P: 16 },
    en: { tex: 'switchgear_cell/enamel', P: 16 }, gal: { tex: 'disconnector/galvanised', P: 16 },
    l_g: { tex: 'grid_lines/lamp_green', P: 16 }, l_r: { tex: 'grid_lines/lamp_red', P: 16 },
    l_w: { tex: 'grid_lines/lamp_white', P: 16 }, l_a: { tex: 'grid_lines/lamp_amber', P: 16 }, l_off: { tex: 'grid_lines/lamp_off', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/block', TEX);
    for (const n of Object.keys(T)) writePng(T[n](), path.join(dir, n + '.png'));
    fs.writeFileSync(path.join(dir, 'flame.png.mcmeta'), JSON.stringify({ animation: { frametime: 3, interpolate: true } }, null, '\t') + '\n');
    return Object.keys(T).length;
  }

  function lamp(m, n, x, y, z, mat, glow, r) {
    const k = r || 0.6;
    m.box(`${n}_bezel`, [x - k - 0.2, y - k - 0.2, z - 0.15], [x + k + 0.2, y + k + 0.2, z], 'rubber', { split: '' });
    m.box((glow ? 'glow_' : '') + n, [x - k, y - k, z - 0.35], [x + k, y + k, z - 0.15], mat, { split: '', hide: ['south'] });
  }

  // ---------------------------------------------------------------- control board
  // Standing board against the furnace: mimic, drum level glass, the BMS lamps (flame green, trip red, ready
  // white), steam pressure gauge, ignition key and the mushroom emergency stop on its yellow collar.
  function board(state) {
    const m = mk('switchgear_cell/enamel');
    m.box('plinth', [0.5, 0, 2.4], [15.5, 1, 16], 'rubber', { split: '' });
    m.box('body', [0.5, 1, 2.4], [15.5, 15.4, 16], 'en', { split: 'y' });
    m.box('top', [0.2, 15.4, 2], [15.8, 16, 16], 'en', { split: '' });
    m.box('front', [1, 1.4, 2], [15, 15, 2.4], 'en', { split: '' });
    m.plate('mimic', [1.6, 8.4, 1.85], [10.6, 14.4, 2], 'en', 'north', TEX + 'mimic', { hide: ['south'] });
    m.box('level_case', [11.8, 8.2, 1.6], [14.2, 14.6, 2], 'rubber', { split: '' });
    m.plate('level', [12.1, 8.5, 1.45], [13.9, 14.3, 1.6], 'rubber', 'north', TEX + 'level', { hide: ['south'] });
    lamp(m, 'lamp_flame', 3, 6.6, 2, state === 'firing' ? 'l_g' : 'l_off', state === 'firing');
    lamp(m, 'lamp_trip', 5.6, 6.6, 2, state === 'trip' ? 'l_r' : 'l_off', state === 'trip');
    lamp(m, 'lamp_ready', 8.2, 6.6, 2, state === 'standby' ? 'l_w' : 'l_off', state === 'standby');
    m.box('gauge_case', [10.4, 2.6, 1.4], [14.6, 6.8, 2], 'rubber', { split: '' });
    m.plate('gauge', [10.8, 3, 1.3], [14.2, 6.4, 1.4], 'rubber', 'north', 'material/gauge_dial', { hide: ['south'] });
    m.box('key_plate', [2.2, 2.6, 1.9], [4.2, 4.6, 2], 'ci', { split: '' });
    m.box('key', [3, 3.2, 1.4], [3.4, 4.2, 1.9], 'br', { split: '' });
    m.box('estop_collar', [6, 2.4, 1.85], [9, 5.4, 2], 'yel', { split: '' });
    m.prism('estop', 'z', [7.5, 3.9], 1.1, 0.9, 1.85, 'red', { split: '', ends: ['north'] });
    for (const x of [1.4, 14.6]) m.box(`handle_${x}`, [x - 0.2, 9, 1.6], [x + 0.2, 13, 2], 'st', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- furnace shell
  function wall(kind) {
    const m = mk(TEX + (kind === 'outer' ? 'wall_clad' : kind === 'slag' ? 'waterwall_slag' : 'waterwall'));
    m.box('block', [0, 0, 0], [16, 16, 16], kind === 'outer' ? 'wclad' : kind === 'slag' ? 'wws' : 'ww', { split: '' });
    return Object.assign(m, { cull: true });
  }
  // Burner in a wall block. Operator side (north): windbox with the air duct from above, the swirl register round
  // the core (glowing while lit), peephole, igniter, the fuel line (coal: the pulverised-coal pipe in from the
  // east; gas: the yellow gas line with its shut-off valve). Furnace side (south): the firebrick quarl and the
  // animated flame while lit.
  function burner(gas, lit) {
    const m = mk(TEX + 'wall_clad');
    m.box('wall', [0, 0, 0], [16, 16, 16], 'wclad', { split: '', face: { south: 'ww' } });
    // quarl on the furnace side
    m.box('quarl_t', [3, 11.4, 16], [13, 13, 16.6], 'fb', { split: '' });
    m.box('quarl_b', [3, 3, 16], [13, 4.6, 16.6], 'fb', { split: '' });
    m.box('quarl_w', [3, 4.6, 16], [4.6, 11.4, 16.6], 'fb', { split: '' });
    m.box('quarl_e', [11.4, 4.6, 16], [13, 11.4, 16.6], 'fb', { split: '' });
    m.box((lit ? 'glow_' : '') + 'throat', [4.6, 4.6, 16], [11.4, 11.4, 16.3], lit ? 'fire' : 'soot', { split: '', hide: ['north'] });
    if (lit) m.box('glow_flame', [5.4, 5.4, 16.3], [10.6, 10.6, 18.4], 'fire', { split: '', hide: ['north'] });
    // windbox and register on the operator side
    m.box('windbox', [2.4, 2.4, -3.6], [13.6, 13.6, 0], 'casing', { split: '' });
    m.box('windbox_flange', [2, 2, -0.6], [14, 14, 0], 'frame', { split: '' });
    m.box('air_duct', [5, 13.6, -3.2], [11, 16, -0.6], 'p_air', { split: '' });
    m.box('air_flange', [4.6, 15.4, -3.6], [11.4, 16, -0.2], 'frame', { split: '' });
    m.box('air_damper', [11, 14.4, -2.4], [12.6, 14.8, -1.4], 'red', { split: '' });
    m.disc('register', 'z', [8, 8], 4.6, -4.2, -3.6, 'st', TEX + 'register', 'north');
    m.box((lit ? 'glow_' : '') + 'core', [6.6, 6.6, -4.4], [9.4, 9.4, -4.2], lit ? 'fire' : 'soot', { split: '', hide: ['south'] });
    m.box('core_ring', [6.2, 6.2, -4.3], [9.8, 9.8, -4.25], 'st', { split: '', hide: ['south', 'north'] });
    m.box('peephole', [3.2, 10.4, -4.4], [5, 12.2, -3.6], 'ci', { split: '' });
    m.box((lit ? 'glow_' : '') + 'peep_glass', [3.6, 10.8, -4.55], [4.6, 11.8, -4.4], lit ? 'fire' : 'soot', { split: '', hide: ['south'] });
    m.box('igniter', [10.8, 10.8, -6], [11.4, 11.4, -3.6], 'bright', { split: '' });
    m.box('igniter_head', [10.6, 10.6, -6.6], [11.6, 11.6, -6], 'ci', { split: '' });
    m.box('igniter_cable', [11, 11.4, -6.4], [11.2, 13.6, -6.2], 'rubber', { split: '' });
    if (gas) {
      m.box('gas_line', [13.6, 7.4, -2.4], [16, 8.6, -1.2], 'p_gas', { split: '' });
      m.box('gas_valve', [14, 6.8, -3], [15.4, 9.2, -0.6], 'p_gas', { split: '' });
      m.box('gas_valve_handle', [14.4, 9.2, -4.6], [15, 9.6, -1.8], 'red', { split: '' });
      m.box('gas_gauge', [12.6, 3.6, -3.4], [13.6, 4.6, -2.4], 'ci', { split: '' });
    } else {
      m.prism('coal_pipe', 'x', [7, -2], 1.8, 13.6, 16, 'frame', { split: '', ends: [] });
      m.box('coal_flange', [13.6, 4.8, -4.2], [14.2, 9.2, 0.2], 'ci', { split: '' });
      m.box('coal_gate', [14.4, 9, -2.8], [15.6, 10.6, -1.2], 'ci', { split: '' });
    }
    return Object.assign(m, { cull: true });
  }

  // ---------------------------------------------------------------- convective surfaces
  function frame(m, mat) {
    for (const [x, z] of [[0, 0], [15, 0], [0, 15], [15, 15]]) m.box(`post_${x}_${z}`, [x, 0, z], [x + 1, 16, z + 1], mat, { split: '' });
    for (const y of [0, 15]) {
      m.box(`rail_n_${y}`, [1, y, 0], [15, y + 1, 1], mat, { split: '' });
      m.box(`rail_s_${y}`, [1, y, 15], [15, y + 1, 16], mat, { split: '' });
    }
  }
  // Pendant superheater: four platens of five hanging tubes with bends at the bottom, two outlet headers along z
  // under the roof (steam, red).
  function superheater() {
    const m = mk(TEX + 'tube_hot');
    frame(m, 'frame');
    for (const [p, z] of [[0, 3], [1, 6.3], [2, 9.6], [3, 12.9]]) {
      for (let i = 0; i < 5; i++) {
        const x = 2.6 + i * 2.6;
        m.box(`tube_${p}_${i}`, [x - 0.45, 2, z - 0.45], [x + 0.45, 14, z + 0.45], 'thot', { split: '' });
      }
      m.box(`bend_${p}`, [2.15, 1.4, z - 0.45], [13.45, 2.3, z + 0.45], 'thot', { split: '' });
    }
    for (const x of [4.6, 11.4]) {
      m.prism(`header_${x}`, 'z', [x, 14.6], 1.2, 0, 16, 'p_steam', { split: '' });
      m.box(`header_band_${x}`, [x - 1.3, 13.3, 7.4], [x + 1.3, 15.9, 8.6], 'yel', { split: '' });
    }
    return m;
  }
  // Economiser: three serpentine coils of horizontal tubes with return bends at alternate ends, inlet header low
  // and outlet header high (water, green).
  function economiser() {
    const m = mk(TEX + 'tube');
    frame(m, 'frame');
    const rows = [2.4, 4.6, 6.8, 9, 11.2, 13.4];
    for (const z of [4, 8, 12]) {
      rows.forEach((y, i) => m.box(`tube_${z}_${i}`, [1.6, y - 0.45, z - 0.45], [14.4, y + 0.45, z + 0.45], 'tube', { split: '' }));
      for (let i = 0; i < rows.length - 1; i++) {
        const x = i % 2 === 0 ? 14.4 : 0.8;
        m.box(`bend_${z}_${i}`, [x, rows[i] - 0.45, z - 0.45], [x + 0.8, rows[i + 1] + 0.45, z + 0.45], 'tube', { split: '' });
      }
    }
    m.box('header_in', [0.6, 1, 1], [2.4, 2.6, 15], 'p_water', { split: '' });
    m.box('header_out', [13.6, 13.6, 1], [15.4, 15.2, 15], 'p_water', { split: '' });
    return m;
  }
  // Tubular air heater: closed casing, tube sheets top and bottom (flue gas goes down the tubes), air duct
  // flanges north and south (blue), corner angles.
  function airHeater() {
    const m = mk(TEX + 'tubesheet');
    m.box('casing', [0.5, 0.6, 0.5], [15.5, 15.4, 15.5], 'wclad', { split: '', face: { up: 'tsheet', down: 'tsheet' } });
    m.box('sheet_top', [0.2, 15.4, 0.2], [15.8, 16, 15.8], 'tsheet', { split: '' });
    m.box('sheet_bot', [0.2, 0, 0.2], [15.8, 0.6, 15.8], 'tsheet', { split: '' });
    for (const [n, z0, z1] of [['n', -0.4, 0.5], ['s', 15.5, 16.4]]) {
      m.box(`duct_${n}_t`, [2, 12, z0], [14, 13.4, z1], 'p_air', { split: '' });
      m.box(`duct_${n}_b`, [2, 2.6, z0], [14, 4, z1], 'p_air', { split: '' });
      m.box(`duct_${n}_w`, [2, 4, z0], [3.4, 12, z1], 'p_air', { split: '' });
      m.box(`duct_${n}_e`, [12.6, 4, z0], [14, 12, z1], 'p_air', { split: '' });
    }
    for (const [x, z] of [[0, 0], [15, 0], [0, 15], [15, 15]]) m.box(`angle_${x}_${z}`, [x - 0.1, 0.6, z - 0.1], [x + 1.1, 15.4, z + 1.1], 'frame', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- draught fans
  // Centrifugal fan on a base frame: shaft in from the north through the bearing, the volute about z with its
  // tangential outlet up on the east side (marked air blue or flue gas yellow), the inlet cone and the square
  // inlet flange on the south. The FD fan is blue, the ID fan dark grey and sooty.
  function fan(id) {
    const body = id ? 'idp' : 'blue', mark = id ? 'p_gas' : 'p_air';
    const m = mk(id ? TEX + 'id_paint' : 'plant/blue');
    m.box('base', [1.4, 0, 0.6], [14.6, 1, 15.4], 'frame', { split: '' });
    m.box('saddle', [3.6, 1, 4], [12.4, 2.2, 10], body, { split: '' });
    m.box('bearing_pedestal', [5.8, 1, 1], [10.2, 6, 3], 'ci', { split: '' });
    m.prism('bearing', 'z', [8, 8], 2.2, 1.2, 3, 'ci', { split: '', ends: ['north', 'south'] });
    m.box('bearing_cap', [6.6, 10, 1.4], [9.4, 10.6, 2.8], 'ci', { split: '' });
    m.prism('volute', 'z', [8, 8], 6.5, 3.2, 10, body, { split: '', ends: ['north'] });
    m.box('scroll', [8, 8, 3.2], [14.8, 14.6, 10], body, { split: '' });
    m.box('outlet', [8.6, 8, 3.2], [15, 15.4, 10], body, { split: '', face: { up: 'soot' } });
    m.box('outlet_flange', [8.2, 15.4, 2.8], [15.4, 16, 10.4], 'frame', { split: '', face: { up: 'soot' } });
    m.box('mark', [8.4, 12, 3], [15.2, 13, 10.2], mark, { split: '' });
    m.ring('back', 'z', [8, 8], 6.5, 3.2, 10, 10.6, body);
    m.ring('inlet_cone', 'z', [8, 8], 4.2, 1, 10.6, 13.6, body);
    m.box('inlet_t', [2.6, 12.2, 13.6], [13.4, 13.4, 14.4], 'frame', { split: '' });
    m.box('inlet_b', [2.6, 2.6, 13.6], [13.4, 3.8, 14.4], 'frame', { split: '' });
    m.box('inlet_w', [2.6, 3.8, 13.6], [3.8, 12.2, 14.4], 'frame', { split: '' });
    m.box('inlet_e', [12.2, 3.8, 13.6], [13.4, 12.2, 14.4], 'frame', { split: '' });
    m.plate('arrow', [3, 11.4, 3.05], [5.4, 13.8, 3.2], body, 'north', TEX + 'arrow', { hide: ['south'] });
    for (const x of [2.2, 13.2]) m.box(`bolt_${x}`, [x, 1, 2], [x + 0.6, 1.4, 2.6], 'st', { split: '' });
    return m;
  }
  // Impeller partial: the shaft coupling between the Create shaft and the bearing, the shaft, the back plate,
  // sixteen blades, the front shroud and the hub nose seen through the inlet.
  function impeller() {
    const m = mk('material/steel');
    m.prism('coupling', 'z', [8, 8], 2, 0, 1.1, 'st', { split: '', ends: ['north', 'south'] });
    for (const [x, y] of [[8, 9.4], [8, 6.6], [6.6, 8], [9.4, 8]]) m.box(`coupling_bolt_${x}_${y}`, [x - 0.3, y - 0.3, -0.15], [x + 0.3, y + 0.3, 0], 'bright', { split: '' });
    m.prism('shaft', 'z', [8, 8], 0.7, 1.1, 9, 'bright', { split: '' });
    m.prism('back_plate', 'z', [8, 8], 5.8, 4, 4.6, 'gal', { split: '', ends: ['south'] });
    for (const [n, a] of [['a', 0], ['b', 45], ['c', -45], ['d', 22.5], ['e', -22.5]]) {
      m.box(`blade_${n}`, [2.4, 7.75, 4.6], [13.6, 8.25, 9.6], 'gal', { split: '', rot: a ? { axis: 'z', angle: a, origin: [8, 8, 8] } : undefined });
    }
    for (const [n, a] of [['f', 0], ['g', 22.5], ['h', -22.5]]) {
      m.box(`blade_${n}`, [7.75, 2.4, 4.6], [8.25, 13.6, 9.6], 'gal', { split: '', rot: a ? { axis: 'z', angle: a, origin: [8, 8, 8] } : undefined });
    }
    m.ring('shroud', 'z', [8, 8], 5.8, 1.6, 9.6, 10, 'gal');
    m.prism('hub', 'z', [8, 8], 1.5, 8.4, 11.2, 'st', { split: '', ends: ['south'] });
    m.box('hub_nose', [7.3, 7.3, 11.2], [8.7, 8.7, 11.8], 'st', { split: '' });
    return m;
  }

  // ---------------------------------------------------------------- steam drum
  // Drum segment along x in aluminium cladding with straps, on two saddles; the downcomer (water, green) down
  // into the roof, the steam outlet (red) and the safety valve on top, the water gauge glass on the north side.
  function drum() {
    const m = mk('plant/cladding');
    for (const x of [2.6, 11.4]) m.box(`saddle_${x}`, [x, 0, 3], [x + 2, 3.2, 13], 'frame', { split: '' });
    m.prism('shell', 'x', [8.6, 8], 6.4, 0, 16, 'clad', { split: '', ends: ['east', 'west'] });
    for (const x of [4.2, 11.2]) m.ring(`strap_${x}`, 'x', [8.6, 8], 6.6, 0.3, x, x + 0.6, 'gal');
    m.prismY('downcomer', [8, 8], 1.2, 0, 2.6, 'p_water');
    m.prismY('steam_out', [5, 8], 1, 14.6, 16, 'p_steam', { ends: ['up'] });
    m.box('steam_flange', [3.6, 15.6, 6.6], [6.4, 16, 9.4], 'frame', { split: '' });
    m.box('safety_valve', [10.6, 14.6, 7], [12.6, 16.6, 9], 'red', { split: '' });
    m.box('safety_lever', [12.6, 16, 7.7], [15.4, 16.4, 8.3], 'st', { split: '' });
    m.box('safety_weight', [14.4, 15.4, 7.4], [15.4, 16.6, 8.6], 'ci', { split: '' });
    m.box('gauge_upper', [3.6, 12.4, 1.2], [4.6, 13.2, 2.4], 'st', { split: '' });
    m.box('gauge_lower', [3.6, 4.4, 1.2], [4.6, 5.2, 2.4], 'st', { split: '' });
    m.plate('gauge_glass', [3.4, 5.2, 0.8], [4.8, 12.4, 1.6], 'st', 'north', TEX + 'level');
    return m;
  }

  const BUILDS = {
    water_tube_boiler: { model: () => board('standby') },
    water_tube_boiler_firing: { model: () => board('firing') },
    water_tube_boiler_trip: { model: () => board('trip') },
    boiler_wall: { model: () => wall('outer') },
    boiler_wall_inner: { model: () => wall('inner') },
    boiler_wall_slagged: { model: () => wall('slag') },
    boiler_burner: { model: () => burner(false, false) },
    boiler_burner_lit: { model: () => burner(false, true) },
    boiler_burner_gas: { model: () => burner(true, false) },
    boiler_burner_gas_lit: { model: () => burner(true, true) },
    boiler_superheater: { model: superheater },
    boiler_economizer: { model: economiser },
    air_preheater: { model: airHeater },
    forced_draft_fan: { model: () => fan(false) },
    induced_draft_fan: { model: () => fan(true) },
    'forced_draft_fan/impeller': { model: impeller, java: 'forced_draft_fan/partial/impeller' },
    'induced_draft_fan/impeller': { model: impeller, java: 'induced_draft_fan/partial/impeller' },
    steam_drum: { model: drum, cat: 'core' },
  };

  // Faces on the block boundary of a full-block model cull against a solid neighbour.
  function cullBoundary() {
    for (const c of Cube.all) for (const [d, a, v] of [['north', 2, 0], ['south', 2, 16], ['west', 0, 0], ['east', 0, 16], ['down', 1, 0], ['up', 1, 16]]) {
      const on = (v === 0 ? c.from[a] : c.to[a]) === v && !c.rotation.some((r) => r);
      const inside = [0, 1, 2].every((i) => c.from[i] >= 0 && c.to[i] <= 16);
      if (on && inside && c.faces[d].texture !== null) c.faces[d].cullface = d;
    }
  }
  function realize(root, id) {
    const m = BUILDS[id].model();
    PCART.realize(root, m, id);
    if (m.cull) cullBoundary();
    return m;
  }

  window.WTB = {
    paint, ids: Object.keys(BUILDS),
    build: realize,
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]].model();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id].model(), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    async exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        realize(root, id);
        await new Promise((r) => setTimeout(r, 1200));
        out[id] = PCART.save(root, BUILDS[id].cat || 'boilers', id, BUILDS[id].java);
      }
      return out;
    },
  };
})();
