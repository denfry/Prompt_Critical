// Shared process-plant materials for the hand-authored chemical, refinery and nuclear machines
// (textures/block/plant/*, 32 px per block like pcart.js). Load after pcart.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/plantkit.js', 'utf8'))
//   PLANT.paint('<repo>')
// Then add PLANT.MAT to a machine's Model materials. Pipe colours follow GOST 14202: water green, steam red,
// air blue, gases yellow, acids orange, alkalis violet, liquids brown.
(function () {
  const path = require('path');
  const { rng, img, put, get, add, mix, rect, vnoise, grain, writePng } = PCART;
  const T = {};

  const painted = (base, seed, rust) => () => {
    const im = img(32, 32), r = grain(im, base, seed, 4, 6, 8);
    for (let k = 0; k < 12; k++) put(im, r() * 32, r() * 32, add(base, -22));
    for (let k = 0; k < (rust || 4); k++) put(im, r() * 32, r() * 32, [110, 84, 60]);
    return im;
  };
  // Aluminium-sheet cladding over hot insulation: lapped seams every 16 px, screws, a strap every 32 px.
  T.cladding = () => {
    const im = img(32, 32), r = rng(901), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add([156, 160, 162], (n(x, y) - 0.5) * 10 + (r() - 0.5) * 7 + ((x + y * 3) % 5 === 0 ? 4 : 0)));
    }
    for (const sx of [0, 16]) for (let y = 0; y < 32; y++) {
      put(im, sx, y, add(get(im, sx, y), -30)); put(im, sx + 1, y, add(get(im, sx + 1, y), 16));
    }
    for (let x = 0; x < 32; x++) {
      put(im, x, 30, [176, 180, 184]); put(im, x, 31, [112, 116, 120]);
      if (r() < 0.25) for (let k = 1; k < 2 + r() * 6; k++) put(im, x, 30 - k - 1, add(get(im, x, 30 - k - 1), -12));
    }
    for (const sx of [2, 18]) for (let y = 3; y < 30; y += 7) { put(im, sx, y, [96, 98, 100]); put(im, sx, y + 1, [190, 192, 194]); }
    return im;
  };
  // Heat-resistant silver paint on furnace casing plate: welded panels, heat tint low down, rust at the welds.
  T.furnace_casing = () => {
    const im = img(32, 32), r = rng(911), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([150, 152, 150], (n(x, y) - 0.5) * 16 + (r() - 0.5) * 6);
      c = mix(c, [128, 112, 92], Math.max(0, (y - 20) / 40));
      put(im, x, y, c);
    }
    for (let i = 0; i < 32; i++) {
      put(im, i, 15, add(get(im, i, 15), -26)); put(im, i, 16, add(get(im, i, 16), 10));
      put(im, 15, i, add(get(im, 15, i), -26)); put(im, 16, i, add(get(im, 16, i), 10));
      if (r() < 0.2) put(im, i, 17, [120, 92, 66]);
    }
    return im;
  };
  // Concrete for plinths and foundations: board marks, tie holes.
  T.concrete = () => {
    const im = img(32, 32), r = grain(im, [134, 132, 124], 921, 6, 7, 8);
    for (let x = 0; x < 32; x++) { put(im, x, 15, add(get(im, x, 15), -14)); put(im, x, 31, add(get(im, x, 31), -14)); }
    for (const [x, y] of [[7, 7], [23, 7], [7, 23], [23, 23]]) { rect(im, x, y, 2, 2, [80, 78, 74]); put(im, x, y, [62, 60, 58]); }
    for (let k = 0; k < 18; k++) put(im, r() * 32, r() * 32, [100, 98, 92]);
    return im;
  };
  // Acid-proof ceramic tile lining (cells, acid tanks): 8 px tiles, dark joints.
  T.acid_tile = () => {
    const im = img(32, 32), r = rng(931);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      const t = ((x >> 3) * 7 + (y >> 3) * 3) % 5;
      let c = add([168, 150, 116], t * 3 - 6 + (r() - 0.5) * 8);
      if (x % 8 === 7 || y % 8 === 7) c = [76, 70, 62];
      put(im, x, y, c);
    }
    return im;
  };
  T.structure = painted([66, 72, 76], 941, 8);    // structural steel, dark grey
  T.green = painted([78, 92, 84], 942);            // Soviet machinery grey-green
  T.light = painted([150, 156, 150], 943);         // light grey enamel (cabinets, motors)
  T.blue = painted([60, 86, 120], 944);            // blue machinery enamel
  T.yellow = painted([206, 168, 40], 945);         // handrails, handling gear
  T.pipe_water = painted([56, 112, 64], 951);
  T.pipe_steam = painted([150, 44, 36], 952);
  T.pipe_air = painted([62, 96, 156], 953);
  T.pipe_gas = painted([196, 160, 44], 954);
  T.pipe_acid = painted([200, 112, 40], 955);
  T.pipe_alkali = painted([112, 72, 140], 956);
  T.pipe_liquid = painted([110, 70, 42], 957);
  T.rubber = painted([40, 40, 42], 958, 0);        // rubber or ebonite lining, hoses
  T.frp = () => {                                  // glass-fibre reinforced plastic: wound fibres, translucent green-grey
    const im = img(32, 32), r = rng(961);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add([150, 160, 138], Math.sin((x + y * 0.5) * 1.2) * 5 + (r() - 0.5) * 8));
    }
    for (let y = 0; y < 32; y += 8) for (let x = 0; x < 32; x++) put(im, x, y, add(get(im, x, y), -18));
    return im;
  };
  T.hazard = () => {
    const im = img(32, 32), r = rng(971);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      put(im, x, y, add(((x + y) >> 3) % 2 ? [30, 30, 30] : [212, 172, 36], (r() - 0.5) * 10));
    }
    return im;
  };
  // Atlas of small common parts (64 px, 4 px per uv unit).
  T.parts = () => {
    const im = img(64, 64), r = rng(981);
    // white tag plate with black lettering (0,0,12,6)
    for (let y = 0; y < 6; y++) for (let x = 0; x < 12; x++) put(im, x, y, y === 0 || y === 5 ? [150, 150, 146] : [232, 232, 226]);
    for (let x = 2; x < 10; x++) if (r() < 0.6) put(im, x, 2 + (x % 2), [30, 30, 30]);
    // maker's plate, dark anodised (12,0,24,12)
    for (let y = 0; y < 12; y++) for (let x = 0; x < 24; x++) {
      put(im, 12 + x, y, x === 0 || y === 0 || x === 23 || y === 11 ? [178, 182, 186] : add([44, 46, 50], (r() - 0.5) * 5));
    }
    for (const [y, a, b] of [[3, 3, 20], [5, 3, 15], [8, 3, 18]]) for (let x = a; x <= b; x++) if (r() < 0.62) put(im, 12 + x, y, [196, 200, 204]);
    // lamps 4x4: green, amber, red (36,0) (40,0) (44,0)
    [[160, 255, 150, 40, 170, 60], [255, 230, 140, 220, 140, 30], [255, 150, 130, 200, 30, 20]].forEach((c, i) => {
      for (let y = 0; y < 4; y++) for (let x = 0; x < 4; x++) {
        const e = Math.hypot(x + 0.5 - 2, y + 0.5 - 2) / 2;
        put(im, 36 + i * 4 + x, y, mix(c.slice(0, 3), c.slice(3), e));
      }
    });
    // pressure gauge face 12x12 (48,0)
    for (let y = 0; y < 12; y++) for (let x = 0; x < 12; x++) {
      const d = Math.hypot(x + 0.5 - 6, y + 0.5 - 6);
      put(im, 48 + x, y, d > 5.5 ? [40, 40, 40] : d > 5 ? [150, 150, 150] : add([232, 228, 214], (r() - 0.5) * 5));
    }
    for (let k = 0; k <= 8; k++) {
      const a = (225 - k * 270 / 8) * Math.PI / 180;
      put(im, 48 + 5.5 + 4 * Math.cos(a), 5.5 - 4 * Math.sin(a), k === 6 ? [190, 30, 26] : [36, 34, 32]);
    }
    for (let s = 0; s < 3.5; s += 0.5) put(im, 48 + 5.5 + s * Math.cos(1.2), 5.5 - s * Math.sin(1.2), [20, 20, 20]);
    // furnace peep door cold (0,12,8,10) and lit (8,12,8,10)
    for (const lit of [0, 1]) for (let y = 0; y < 10; y++) for (let x = 0; x < 8; x++) {
      let c = add([58, 60, 62], (r() - 0.5) * 8);
      if (x === 0 || y === 0) c = add(c, 14);
      if (x === 7 || y === 9) c = add(c, -16);
      const d = Math.hypot(x + 0.5 - 4, y + 0.5 - 4.5);
      if (d < 2.2) c = lit ? mix([255, 240, 170], [255, 120, 30], d / 2.2) : [22, 20, 20];
      put(im, lit * 8 + x, 12 + y, c);
    }
    // radiation sign (16,12,16,16)
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const dx = x + 0.5 - 8, dy = y + 0.5 - 8, d = Math.hypot(dx, dy), a = (Math.atan2(dy, dx) * 180 / Math.PI + 450) % 120;
      let c = [222, 184, 30];
      if (x === 0 || y === 0 || x === 15 || y === 15 || d < 1.5 || (d > 2.3 && d < 6.4 && a > 30 && a < 90)) c = [28, 28, 26];
      put(im, 16 + x, 12 + y, add(c, (r() - 0.5) * 6));
    }
    // warning triangle, corrosive / toxic (32,12,16,14)
    for (let y = 0; y < 14; y++) for (let x = 0; x < 16; x++) {
      const inside = y >= 1 && Math.abs(x + 0.5 - 8) <= (y - 0.5) * 0.6 + 0.4;
      const edge = inside && Math.abs(x + 0.5 - 8) > (y - 0.5) * 0.6 - 1.2;
      let c = inside ? (edge || y > 12 ? [28, 28, 26] : [222, 184, 30]) : [0, 0, 0, 0];
      if (inside && !edge && y > 4 && y < 12 && Math.abs(x + 0.5 - 8) < 0.9 && y !== 10) c = [28, 28, 26];
      put(im, 32 + x, 12 + y, c);
    }
    // louvred vent (48,12,8,8)
    for (let y = 0; y < 8; y++) for (let x = 0; x < 8; x++) put(im, 48 + x, 12 + y, y % 2 ? [40, 42, 44] : [140, 146, 150]);
    return im;
  };

  const MAT = {
    clad: { tex: 'plant/cladding', P: 16 }, casing: { tex: 'plant/furnace_casing', P: 16 }, conc: { tex: 'plant/concrete', P: 16 },
    tile: { tex: 'plant/acid_tile', P: 16 }, frame: { tex: 'plant/structure', P: 16 }, green: { tex: 'plant/green', P: 16 },
    light: { tex: 'plant/light', P: 16 }, blue: { tex: 'plant/blue', P: 16 }, yel: { tex: 'plant/yellow', P: 16 },
    p_water: { tex: 'plant/pipe_water', P: 16 }, p_steam: { tex: 'plant/pipe_steam', P: 16 }, p_air: { tex: 'plant/pipe_air', P: 16 },
    p_gas: { tex: 'plant/pipe_gas', P: 16 }, p_acid: { tex: 'plant/pipe_acid', P: 16 }, p_alk: { tex: 'plant/pipe_alkali', P: 16 },
    p_liq: { tex: 'plant/pipe_liquid', P: 16 }, rubber: { tex: 'plant/rubber', P: 16 }, frp: { tex: 'plant/frp', P: 16 },
    haz: { tex: 'plant/hazard', P: 16 },
  };
  const PARTS = {
    tex: 'plant/parts', size: 64,
    rects: {
      tag: [0, 0, 12, 6], plate: [12, 0, 24, 12], lamp_green: [36, 0, 4, 4], lamp_amber: [40, 0, 4, 4], lamp_red: [44, 0, 4, 4],
      gauge: [48, 0, 12, 12], peep: [0, 12, 8, 10], peep_lit: [8, 12, 8, 10], trefoil: [16, 12, 16, 16], warning: [32, 12, 16, 14],
      vent: [48, 12, 8, 8],
    },
  };

  // Common fittings drawn the same way on every machine.
  function railing(m, name, a, b, y, z, axis) {   // handrail along x (axis 'x') or z at deck height y
    const len = b - a, posts = Math.max(2, Math.round(len / 10) + 1);
    for (let i = 0; i < posts; i++) {
      const p = a + (len - 0.6) * i / (posts - 1);
      if (axis === 'x') m.box(`${name}_post${i}`, [p, y, z], [p + 0.6, y + 6, z + 0.6], 'yel', { split: '' });
      else m.box(`${name}_post${i}`, [z, y, p], [z + 0.6, y + 6, p + 0.6], 'yel', { split: '' });
    }
    for (const [h, t] of [[5.4, 0.6], [2.8, 0.5]]) {
      if (axis === 'x') m.box(`${name}_rail${h}`, [a, y + h, z], [b, y + h + t, z + 0.6], 'yel', { split: 'x' });
      else m.box(`${name}_rail${h}`, [z, y + h, a], [z + 0.6, y + h + t, b], 'yel', { split: 'z' });
    }
  }
  function ladder(m, name, x, z, y0, y1, along) {  // stiles 2.4 apart along x (along 'x') or z
    for (const s of [0, 2.4]) {
      if (along === 'x') m.box(`${name}_stile${s}`, [x + s, y0, z], [x + s + 0.6, y1, z + 0.6], 'yel', { split: 'y' });
      else m.box(`${name}_stile${s}`, [x, y0, z + s], [x + 0.6, y1, z + s + 0.6], 'yel', { split: 'y' });
    }
    for (let y = y0 + 2; y < y1 - 0.5; y += 2) {
      if (along === 'x') m.box(`${name}_rung${y}`, [x + 0.6, y, z + 0.1], [x + 2.4, y + 0.4, z + 0.5], 'frame', { split: '' });
      else m.box(`${name}_rung${y}`, [x + 0.1, y, z + 0.6], [x + 0.5, y + 0.4, z + 2.4], 'frame', { split: '' });
    }
  }
  // Valve on a horizontal pipe along x or z: body, bonnet up, a handwheel about y.
  function valve(m, name, x, y, z, s, mat) {
    m.box(`${name}_body`, [x - s, y - s, z - s], [x + s, y + s, z + s], mat || 'green', { split: '' });
    m.box(`${name}_bonnet`, [x - s * 0.5, y + s, z - s * 0.5], [x + s * 0.5, y + s * 2.2, z + s * 0.5], 'frame', { split: '' });
    m.wheelY(`${name}_wheel`, x, y + s * 2.4, z, s * 1.3, 'p_steam');
  }

  window.PLANT = {
    MAT, PARTS, railing, ladder, valve,
    paint(root) {
      for (const n of Object.keys(T)) writePng(T[n](), path.join(root, PCART.ASSETS, 'textures/block/plant', n + '.png'));
      return Object.keys(T).length;
    },
  };
})();
