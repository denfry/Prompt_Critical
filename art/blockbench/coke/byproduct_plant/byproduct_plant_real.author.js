// Coke-oven by-product plant at real scale (1 block = 1 m, 16 units = 1 m), one whole model per `kind` of
// byproduct_plant (08 §8.4.4), each a vertical vessel of a 1910s Koppers / Otto plant for a battery of some 40 ovens
// (Lunge, "Coal-Tar and Ammonia" 1916; Wilson & Wells, "Coal, Coke and Coal Chemicals" 1950):
//   byproduct_plant_cooler_real        primary gas cooler: riveted tubular cooler ⌀2.5 m, shell 8 m on a skirt,
//                                      cooling water up through the tubes, gas down round them, tar seal pot below.
//   byproduct_plant_precipitator_real  Cottrell pipe-type tar precipitator ⌀2 m, 5.5 m shell on four legs, the
//                                      insulator houses on the roof with the high-tension lead in, tar cone below.
//   byproduct_plant_saturator_real     lead-lined saturator ⌀2.5 m with the cracker pipe dipping into the acid bath,
//                                      cone bottom to the salt ejector and the drain table, acid tank beside.
//   byproduct_plant_scrubber_real      benzol scrubber tower ⌀2.2 m, 15 m high, packed with wooden hurdles, the wash
//                                      oil sprays at the top, platforms and a ladder.
// GOST 14202 colours: gas yellow, water green, acid/alkali (sulfuric acid) olive is shown as gost_brown, oils brown.
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then for each id in BPR.ids:
//   BPR.build(repo, id); BPR.save(repo, id)
// No partials.
//
// All: controller (0, 0, 0) under the axis, front north, axis (8, ., 8), footprint x -1..1, z -1..1 (model x -16..32,
// z -16..32), y 0..top. Ports (cell, face, point), gas comes from the battery's collecting main on the north:
//   cooler        y 0..9.  gas in  (0, 8, -1) north (8, 136, -16); gas out (0, 1, 1) south (8, 24, 32);
//                 water in (1, 0, 0) east (32, 8, 8); water out (1, 8, 0) east (32, 136, 8); tar out (-1, 0, 0) west
//                 (-16, 8, 8).
//   precipitator  y 0..8.  gas in  (0, 1, -1) north (8, 24, -16); gas out (0, 7, 1) south (8, 120, 32);
//                 tar out (-1, 0, 0) west (-16, 8, 8); power in from the roof, (0, 8, 0) up (8, 128, 8).
//   saturator     y 0..3.  gas in  (0, 3, -1) north (8, 56, -16); gas out (0, 3, 1) south (8, 56, 32);
//                 acid in (1, 2, 0) east (32, 40, 8); ammonium sulfate out on the drain table, (-1, 0, 0) west
//                 (-16, 8, 8).
//   scrubber      y 0..15. gas in  (0, 1, -1) north (8, 24, -16); gas out (0, 14, 1) south (8, 232, 32);
//                 wash oil in (1, 14, 0) east (32, 232, 8); benzolised oil out (1, 0, 0) east (32, 8, 8).
(function () {
  const C = [8, 8];
  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const mk = () => Object.assign(new PCART.Model(MAT, { pl: PCVV.ATLAS.pl }), { particle: 'material/steel' });

  // Shell from y0 to y1 with riveted seams every `seam`, a stepped dome on top.
  function shell(m, R, y0, y1, mat, seam) {
    m.cylY('shell', C, R, y0, y1, mat, { bottom: true });
    for (let y = y0 + seam; y < y1 - 2; y += seam) m.ringY(`seam_${y}`, C, R + 0.3, 0.5, y - 0.5, y + 0.5, 'rivets');
    m.cylY('dome_0', C, R - 2, y1, y1 + 2, mat);
    m.cylY('dome_1', C, R - 7, y1 + 2, y1 + 4, mat, { cap: 'fire_tube_boiler/end_plate' });
  }
  // Horizontal nozzle from the shell out to a face: dir 'n' | 's' | 'e' | 'w' at height y, radius r, pipe mat.
  function nozzle(m, name, dir, y, R, r, mat) {
    const ax = dir === 'n' || dir === 's' ? 'z' : 'x', out = dir === 'n' || dir === 'w' ? -16 : 32;
    const from = out < 0 ? out + 1 : 8 + R - 1, to = out < 0 ? 8 - R + 1 : out - 1;
    const c = [8, y];
    m.prism(`${name}_pipe`, ax, ax === 'z' ? c : [y, 8], r, from, to, mat, { split: '' });
    const f0 = out < 0 ? out : out - 1, f1 = f0 + 1;
    m.prism(`${name}_flange`, ax, ax === 'z' ? c : [y, 8], r + 1, f0, f1, 'ci', { split: '', ends: [ax === 'z' ? (out < 0 ? 'north' : 'south') : (out < 0 ? 'west' : 'east')] });
  }
  // Ladder up the south-west, platforms (grating rings) at the given heights.
  function access(m, R, yTop, decks) {
    m.box('ladder_l', [-15.4, 0, 22], [-14.8, yTop, 22.6], 'st', { split: 'y' });
    m.box('ladder_r', [-11.6, 0, 22], [-11, yTop, 22.6], 'st', { split: 'y' });
    for (let y = 4; y < yTop; y += 4.8) m.box(`rung_${Math.round(y * 10)}`, [-14.8, y, 22.1], [-11.6, y + 0.5, 22.5], 'st', { split: '' });
    for (const y of decks) {
      m.ringY(`deck_${y}`, C, Math.min(R + 6, 23.6), Math.min(6, 23.6 - R), y - 1, y, 'grate');
      m.ringY(`deck_rail_${y}`, C, 23, 0.6, y + 14, y + 14.6, 'yel');
      for (const a of [45, 135, 225, 315]) m.postY(`deck_post_${y}_${a}`, C, 23, a, 0.6, y, y + 14.6, 'yel');
    }
  }
  function skirt(m, R, h) {
    m.cylY('skirt', C, R - 1, 0, h, 'conc', { bottom: true });
    m.box('skirt_door', [6, 1, C[1] - R + 0.8], [10, 9, C[1] - R + 0.9], 'dark', { split: '' });
  }
  function legs(m, R, h) {
    for (const a of [45, 135, 225, 315]) {
      m.postY(`leg_${a}`, C, R - 1, a, 2, 0, h, 'st');
      m.postY(`leg_foot_${a}`, C, R - 1, a, 3.4, 0, 1, 'conc');
    }
  }

  const KINDS = {
    byproduct_plant_cooler_real(m) {
      const R = 20;
      skirt(m, R, 16);
      shell(m, R, 16, 144, 'plate', 32);
      nozzle(m, 'gas_in', 'n', 136, R, 5.4, 'yel');
      nozzle(m, 'gas_out', 's', 24, R, 5.4, 'yel');
      nozzle(m, 'water_in', 'e', 8, R - 1, 2.4, 'gw');
      nozzle(m, 'water_out', 'e', 136, R, 2.4, 'gw');
      // tar seal pot on the west with its overflow
      m.cylY('seal_pot', [-9, 8], 4, 0, 12, 'st', { cap: 'vver/flange' });
      m.box('seal_drop', [-6, 12, 7], [-4, 18, 9], 'oil', { split: '' });
      m.box('seal_run', [-6, 18, 7], [-1, 20, 9], 'oil', { split: '' });
      nozzle(m, 'tar_out', 'w', 8, 3.8, 1.6, 'oil');
      m.box('thermometer', [7, 40, C[1] - R - 1.4], [9, 44, C[1] - R - 0.4], 'br', { split: '' });
      access(m, R, 152, [72, 144]);
      m.decal('plate', [4, 60, C[1] - R - 0.6], [12, 64.8, C[1] - R - 0.4], 'st', 'north', 'pl.maker');
    },
    byproduct_plant_precipitator_real(m) {
      const R = 16;
      legs(m, R, 30);
      for (let i = 0; i < 4; i++) m.cylY(`cone_${i}`, C, R - 3.4 * (i + 1), 30 - 4 * (i + 1), 30 - 4 * i, 'st', { bottom: i === 3 });
      shell(m, R, 30, 118, 'st', 22);
      nozzle(m, 'gas_in', 'n', 24, 6, 4.6, 'yel');
      m.cylY('gas_in_riser', C, 6, 14, 30, 'yel');
      nozzle(m, 'gas_out', 's', 120, R - 6, 4.6, 'yel');
      nozzle(m, 'tar_out', 'w', 8, 1, 1.4, 'oil');
      m.box('tar_drop', [7, 8, 7], [9, 14, 9], 'oil', { split: '' });
      // insulator houses on the roof, the high-tension bus between them and the lead-in
      for (const [x, z] of [[2, 2], [14, 2], [2, 14], [14, 14]]) {
        m.box(`ins_house_${x}_${z}`, [x - 3, 122, z - 3], [x + 3, 127, z + 3], 'st', { split: '' });
        m.box(`ins_roof_${x}_${z}`, [x - 3.4, 127, z - 3.4], [x + 3.4, 127.6, z + 3.4], 'st', { split: '' });
      }
      m.box('ht_bus', [2, 125, 7.4], [14, 126, 8.6], 'bright', { split: '' });
      m.cylY('lead_in', C, 1.2, 126, 128, 'br', { cap: 'vver/dark' });
      m.decal('danger', [4, 80, C[1] - R - 0.6], [10, 86, C[1] - R - 0.4], 'st', 'north', 'pl.sign');
      access(m, R, 128, [64, 118]);
    },
    byproduct_plant_saturator_real(m) {
      const R = 20;
      // cone bottom on a ring of columns, the bath shell, the dome and the cracker pipe
      legs(m, R, 16);
      for (let i = 0; i < 4; i++) m.cylY(`cone_${i}`, C, R - 4 * (i + 1), 16 - 2 * (i + 1), 16 - 2 * i, 'dark', { bottom: i === 3 });
      shell(m, R, 16, 48, 'dark', 16);
      m.cylY('cracker_pipe', C, 5, 52, 60, 'yel');
      nozzle(m, 'gas_in', 'n', 56, 5, 4.4, 'yel');
      nozzle(m, 'gas_out', 's', 56, R - 7, 4.4, 'yel');
      m.box('gas_out_riser', [5, 50, 18], [11, 56, 26], 'yel', { split: '' });
      // acid tank on the east, its feed pipe
      m.cylY('acid_tank', [26, -6], 5, 0, 30, 'oil', { cap: 'vver/flange' });
      nozzle(m, 'acid_in', 'e', 40, R, 1.4, 'oil');
      m.box('acid_feed', [24, 30, -7], [27, 41, -5], 'oil', { split: '' });
      // salt ejector down to the drain table on the west
      m.box('ejector', [6.6, 0, 6.6], [9.4, 8, 9.4], 'st', { split: '' });
      m.box('salt_line', [-8, 6, 7], [6.6, 8, 9], 'st', { split: 'x' });
      m.box('drain_table', [-15, 0, -4], [-5, 6, 20], 'wood', { split: '' });
      m.box('salt_heap', [-14, 6, -2], [-6, 8, 18], 'pnt', { split: '' });
      access(m, R, 52, [48]);
    },
    byproduct_plant_scrubber_real(m) {
      const R = 18;
      skirt(m, R, 12);
      shell(m, R, 12, 228, 'plate', 24);
      nozzle(m, 'gas_in', 'n', 24, R, 5, 'yel');
      nozzle(m, 'gas_out', 's', 232, 5, 5, 'yel');
      m.cylY('gas_out_neck', C, 5, 232, 236, 'yel', { cap: 'fire_tube_boiler/end_plate' });
      nozzle(m, 'oil_in', 'e', 232, 4, 1.8, 'oil');
      nozzle(m, 'oil_out', 'e', 8, R - 1, 1.8, 'oil');
      // manholes for the hurdles, the oil spray header on the top
      for (const y of [40, 88, 136, 184]) m.radial(`manhole_${y}`, C, R, R + 1.2, 270, 5, y, y + 6, 'st');
      m.ringY('spray_header', C, 8, 1.2, 229, 230.4, 'oil');
      access(m, R, 236, [80, 160, 228]);
      m.decal('plate', [4, 60, C[1] - R - 0.6], [12, 64.8, C[1] - R - 0.4], 'st', 'north', 'pl.maker');
    },
  };

  window.BPR = {
    ids: Object.keys(KINDS),
    model: (id) => { const m = mk(); KINDS[id](m); return m; },
    build: (root, id) => { const m = PCVV.cut(window.BPR.model(id)); PCART.realize(root, m, id); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, id) => PCVV.save(root, id, 'coke/byproduct_plant'),
  };
})();
