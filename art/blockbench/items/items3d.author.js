// 3D item models, hand-authored for Blockbench (replaces the spec_*.py item placeholders): miner's cap lamp,
// flame safety lamp, casting, fuel assembly, graphite and green electrodes, pump impeller, radiography camera,
// seamless and welded pipes, steel beam, steel billet, spare tuyere. Each gets display transforms (the
// placeholders had none, so they showed flat from the front in the inventory).
// Load inside Blockbench desktop, after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcround.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/items/items3d.author.js', 'utf8'))
//   ITEMS3D.exportAll('<repo>')   // writes art/blockbench/items/<id>.bbmodel and models/item/<id>.json
(function () {
  const fs = require('fs');
  const path = require('path');
  const MAT = Object.assign({}, PLANT.MAT, {
    gph: { tex: 'steelmaking/graphite', P: 16 }, hot: { tex: 'rolling/hot', P: 16 }, ss: { tex: 'electronics/ss', P: 16 },
    glass: { tex: 'electronics/glass', P: 16 }, roll: { tex: 'rolling/roll', P: 16 }, rods: { tex: 'reactor/rack', P: 8 },
    l_w: { tex: 'grid_lines/lamp_white', P: 16 }, fglow: { tex: 'rolling/furnace_glow', P: 16 }, cu: { tex: 'disconnector/copper', P: 16 },
    gal: { tex: 'disconnector/galvanised', P: 16 }, mblue: { tex: 'rolling/mill_blue', P: 16 },
  });
  const mk = (particle) => Object.assign(new PCART.Model(MAT, {}), { particle });
  const B = (m, n, f, t, mat, o) => m.box(n, f, t, mat, Object.assign({ split: '' }, o || {}));
  const oct = (m, n, x, z, r, y0, y1, mat) => {
    const h = r * Math.tan(Math.PI / 8);
    B(m, `${n}_a`, [x - r, y0, z - h], [x + r, y1, z + h], mat);
    B(m, `${n}_b`, [x - h, y0 + 0.01, z - r], [x + h, y1 - 0.01, z + r], mat);
    for (const [k, a, d] of [['c', 45, 0.02], ['d', -45, 0.03]]) B(m, `${n}_${k}`, [x - r, y0 + d, z - h], [x + r, y1 - d, z + h], mat, { rot: { axis: 'y', angle: a, origin: [x, 8, z] } });
  };
  const octX = (m, n, y, z, r, x0, x1, mat) => {
    const h = r * Math.tan(Math.PI / 8);
    B(m, `${n}_a`, [x0, y - r, z - h], [x1, y + r, z + h], mat);
    B(m, `${n}_b`, [x0 + 0.01, y - h, z - r], [x1 - 0.01, y + h, z + r], mat);
    for (const [k, a, d] of [['c', 45, 0.02], ['d', -45, 0.03]]) B(m, `${n}_${k}`, [x0 + d, y - r, z - h], [x1 - d, y + r, z + h], mat, { rot: { axis: 'x', angle: a, origin: [8, y, z] } });
  };

  // Miner's cap lamp: the headpiece with reflector and glowing lens, the cable, the battery on its belt clip.
  function capLamp() {
    const m = mk('plant/yellow');
    B(m, 'battery', [4, 0, 6], [12, 7, 10], 'rubber');
    B(m, 'battery_cap', [4.4, 7, 6.4], [11.6, 8, 9.6], 'yel');
    B(m, 'clip', [6, 2, 10], [10, 5, 10.6], 'st');
    B(m, 'cable', [7.6, 8, 7.6], [8.4, 11, 8.4], 'rubber');
    oct(m, 'head', 8, 8, 2.6, 11, 14, 'yel');
    B(m, 'reflector', [5.6, 11.4, 5], [10.4, 13.6, 5.6], 'bright');
    B(m, 'glow_lens', [6.2, 11.8, 4.8], [9.8, 13.2, 5], 'l_w');
    B(m, 'switch', [10.4, 12, 7.4], [11, 13, 8.6], 'red');
    return m;
  }
  // Flame safety lamp (Davy type): brass oil vessel, the glass, the gauze chimney in its pillars, the hook.
  function flameLamp() {
    const m = mk('material/brass');
    oct(m, 'vessel', 8, 8, 3, 0, 4, 'br');
    oct(m, 'glass', 8, 8, 2.2, 4, 8, 'glass');
    B(m, 'glow_flame', [7.4, 4.6, 7.4], [8.6, 6.4, 8.6], 'fglow');
    for (const [x, z] of [[5.2, 5.2], [10.2, 5.2], [5.2, 10.2], [10.2, 10.2]]) B(m, `pillar_${x}_${z}`, [x, 4, z], [x + 0.6, 12, z + 0.6], 'br');
    oct(m, 'gauze', 8, 8, 2, 8, 12, 'gal');
    oct(m, 'bonnet', 8, 8, 2.6, 12, 13, 'br');
    B(m, 'hook', [7.6, 13, 7.6], [8.4, 15, 8.4], 'br');
    return m;
  }
  // Casting: a grey-iron flanged pipe fitting (tee) fresh from the sand, with sand scale.
  function casting() {
    const m = mk('material/cast_iron');
    octX(m, 'run', 8, 8, 3, 2, 14, 'ci');
    for (const x of [2, 13]) octX(m, `flange_${x}`, 8, 8, 4.2, x, x + 1, 'ci');
    oct(m, 'branch', 8, 8, 2.6, 8, 13, 'ci');
    oct(m, 'branch_flange', 8, 8, 3.6, 13, 14, 'ci');
    return m;
  }
  // Fuel assembly (short-scale): bottom nozzle, a square bundle of rods with spacer grids, top nozzle and handle.
  function fuelAssembly() {
    const m = mk('reactor/rack');
    B(m, 'bottom_nozzle', [5, 0, 5], [11, 2, 11], 'ss');
    B(m, 'bundle', [5.4, 2, 5.4], [10.6, 14, 10.6], 'gal', { face: { up: 'rods', down: 'rods' } });
    for (const y of [4, 8, 12]) B(m, `grid_${y}`, [5.1, y, 5.1], [10.9, y + 0.8, 10.9], 'br');
    B(m, 'top_nozzle', [5, 14, 5], [11, 15.4, 11], 'ss');
    B(m, 'handle', [7, 15.4, 5.6], [9, 16, 10.4], 'ss');
    return m;
  }
  // Graphite electrode with its threaded socket; the green (unbaked) electrode is dull black-brown and plain.
  function electrode(baked) {
    const m = mk('steelmaking/graphite');
    oct(m, 'body', 8, 8, 3.2, baked ? 0 : 1, baked ? 13 : 15, baked ? 'gph' : 'soot');
    if (baked) {
      oct(m, 'socket_rim', 8, 8, 3.4, 12, 13, 'gph');
      oct(m, 'nipple', 8, 8, 1.8, 13, 15, 'gph');
      for (const y of [13.4, 14.2]) oct(m, `thread_${y}`, 8, 8, 2, y, y + 0.3, 'st');
    }
    return m;
  }
  // Pump impeller: back shroud, curved vanes (as turned bars), front shroud ring, the hub with keyway.
  function impeller() {
    const m = mk('material/brass');
    m.prism('back', 'z', [8, 8], 6, 9, 10, 'br', { split: '', ends: ['north', 'south'] });
    for (const [n, a] of [['a', 0], ['b', 45], ['c', -45], ['d', 22.5], ['e', -22.5]]) B(m, `vane_${n}`, [2.4, 7.6, 6.4], [13.6, 8.4, 9], 'br', { rot: a ? { axis: 'z', angle: a, origin: [8, 8, 8] } : undefined });
    B(m, 'vane_f', [7.6, 2.4, 6.4], [8.4, 13.6, 9], 'br');
    m.ring('shroud', 'z', [8, 8], 6, 2.2, 6, 6.4, 'br');
    m.prism('hub', 'z', [8, 8], 1.8, 5, 10.6, 'br', { split: '', ends: ['north', 'south'] });
    B(m, 'bore', [7.3, 7.3, 4.9], [8.7, 8.7, 5], 'soot');
    return m;
  }
  // Radiography camera: the yellow shielded source container with its trefoil, the crank-out cable and the
  // guide tube connector, the carrying handle.
  function camera() {
    const m = mk('plant/yellow');
    B(m, 'body', [3, 2, 4], [13, 10, 12], 'yel');
    B(m, 'shield_band', [2.8, 5, 3.8], [13.2, 7, 12.2], 'st');
    B(m, 'handle_l', [4, 10, 7.4], [5, 13, 8.6], 'st');
    B(m, 'handle_r', [11, 10, 7.4], [12, 13, 8.6], 'st');
    B(m, 'handle_top', [4, 12.4, 7.4], [12, 13.4, 8.6], 'rubber');
    B(m, 'connector', [13, 5, 6.6], [15, 7, 9.4], 'bright');
    B(m, 'lock', [6, 4, 3.6], [8, 6, 4], 'red');
    B(m, 'trefoil', [9, 3.6, 3.7], [12, 6.4, 3.8], 'rubber');
    return m;
  }
  // Pipes: a length lying along y in the item, seamless (bright, plain) or welded (grey with the weld seam).
  function pipe(welded) {
    const m = mk(welded ? 'plant/structure' : 'rolling/roll');
    const h = 3.6 * Math.tan(Math.PI / 8);
    for (const [k, a] of [['a', 0], ['c', 45], ['d', -45]]) B(m, `wall_${k}`, [4.4, 0, 8 - h], [11.6, 16, 8 + h], welded ? 'frame' : 'roll', { hide: ['up', 'down'], rot: a ? { axis: 'y', angle: a, origin: [8, 8, 8] } : undefined });
    B(m, 'wall_b', [8 - h, 0.01, 4.4], [8 + h, 15.99, 11.6], welded ? 'frame' : 'roll', { hide: ['up', 'down'] });
    B(m, 'bore_top', [6, 15.9, 6], [10, 16, 10], 'soot');
    B(m, 'bore_bot', [6, 0, 6], [10, 0.1, 10], 'soot');
    if (welded) B(m, 'seam', [7.7, 0, 4.2], [8.3, 16, 4.4], 'hot');
    return m;
  }
  // Steel I-beam along z, and a square billet with its hot-sheared ends darkened.
  function beam() {
    const m = mk('plant/structure');
    B(m, 'flange_t', [5, 12, 0], [11, 13, 16], 'frame');
    B(m, 'flange_b', [5, 3, 0], [11, 4, 16], 'frame');
    B(m, 'web', [7.5, 4, 0], [8.5, 12, 16], 'frame');
    return m;
  }
  function billet() {
    const m = mk('plant/structure');
    B(m, 'billet', [5, 5, 0], [11, 11, 16], 'frame', { face: { north: 'soot', south: 'soot' } });
    return m;
  }
  // Spare tuyere: the copper nose cone with its water connections.
  function tuyere() {
    const m = mk('disconnector/copper');
    for (const [r, z0, z1] of [[1.8, 2, 4], [2.4, 4, 7], [3, 7, 10], [3.4, 10, 12]]) {
      const h = r * Math.tan(Math.PI / 8);
      B(m, `cone_${z0}_a`, [8 - r, 8 - h, z0], [8 + r, 8 + h, z1], 'cu');
      B(m, `cone_${z0}_b`, [8 - h, 8 - r, z0 + 0.01], [8 + h, 8 + r, z1 - 0.01], 'cu');
      B(m, `cone_${z0}_c`, [8 - r, 8 - h, z0 + 0.02], [8 + r, 8 + h, z1 - 0.02], 'cu', { rot: { axis: 'z', angle: 45, origin: [8, 8, 8] } });
      B(m, `cone_${z0}_d`, [8 - r, 8 - h, z0 + 0.03], [8 + r, 8 + h, z1 - 0.03], 'cu', { rot: { axis: 'z', angle: -45, origin: [8, 8, 8] } });
    }
    B(m, 'bore', [7, 7, 1.9], [9, 9, 2], 'soot');
    for (const x of [5, 10]) B(m, `water_${x}`, [x, 10.6, 11], [x + 1, 13, 12], 'p_water');
    return m;
  }

  const DISPLAY = {
    gui: { rotation: [30, 225, 0], scale: [0.625, 0.625, 0.625] },
    ground: { translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
    fixed: { scale: [0.5, 0.5, 0.5] },
    thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
    firstperson_righthand: { rotation: [0, 45, 0], scale: [0.4, 0.4, 0.4] },
    firstperson_lefthand: { rotation: [0, 225, 0], scale: [0.4, 0.4, 0.4] },
  };
  const BUILDS = {
    cap_lamp: capLamp, flame_safety_lamp: flameLamp, casting, fuel_assembly: fuelAssembly,
    graphite_electrode: () => electrode(true), green_electrode: () => electrode(false), pump_impeller: impeller,
    radiography_camera: camera, seamless_pipe: () => pipe(false), welded_pipe: () => pipe(true), steel_beam: beam,
    steel_billet: billet, tuyere_spare: tuyere,
  };

  function save(root, id) {
    const bb = path.join(root, 'art/blockbench/items', id + '.bbmodel');
    fs.mkdirSync(path.dirname(bb), { recursive: true });
    fs.writeFileSync(bb, Codecs.project.compile());
    let j = Codecs.java_block.compile();
    if (typeof j === 'string') j = JSON.parse(j);
    delete j.format_version;
    for (const e of j.elements || []) if ((e.name || '').startsWith('glow_')) e.neoforge_data = { block_light: 15, sky_light: 15 };
    j.display = DISPLAY;
    fs.writeFileSync(path.join(root, PCART.ASSETS, 'models/item', id + '.json'), compileJSON(j));
    return (j.elements || []).length;
  }
  window.ITEMS3D = {
    ids: Object.keys(BUILDS),
    build: (root, id) => PCART.realize(root, BUILDS[id](), id),
    preview(root, ids, dx) {
      const m = BUILDS[ids[0]]();
      ids.slice(1).forEach((id, i) => m.merge(BUILDS[id](), (i + 1) * dx, 0, 0, `m${i}_`));
      return PCART.realize(root, m, 'preview');
    },
    exportAll(root, ids) {
      const out = {};
      for (const id of ids || Object.keys(BUILDS)) {
        ModelProject.all.slice().forEach((p) => p.close(true));
        PCART.realize(root, BUILDS[id](), id);
        out[id] = save(root, id);
        while (Dialog.open) Dialog.open.close();
      }
      return out;
    },
  };
})();
