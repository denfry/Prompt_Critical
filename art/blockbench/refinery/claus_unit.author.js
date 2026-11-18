// Claus sulphur recovery unit on a one-block skid, hand-authored for Blockbench; replaces the borrowed
// claus_reaction_furnace models, which spilled two blocks past the cell. The machine touches the hydrotreaters on
// its sides, so the model keeps to its own cell and only rises above it. Along the north half lie the reaction
// furnace (refractory-lined, burner and peep sight on the west end) and the waste heat boiler; along the south half
// stand the three catalytic converters on the sulphur condenser header, with the sulphur rundown into a seal pit,
// and the tail gas incinerator stack rises in the south-east corner. Load inside Blockbench desktop:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/refinery/claus_unit.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); CLAUS.paint('<repo>')
//   CLAUS.build('<repo>', 'claus_unit'); PCART.save('<repo>', 'refinery', 'claus_unit')   — one model per call
// Points used by code (ClausUnitBlockEntity.clientTick): stack top (13.5, 32, 13.5), peep sight (-0.4, 9.6, 8.6),
// sulphur rundown (6, 1.5, 15.2).
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/claus_unit';
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  const T = {};

  // Reaction furnace shell: red-brown heat-resistant paint with rain streaks and a hot band discoloured to grey.
  T.furnace = () => {
    const im = img(32, 32), r = rng(501), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([116, 58, 44], (n(x, y) - 0.5) * 18 + (r() - 0.5) * 6);
      if (x > 20 && x < 26) c = mix(c, [96, 90, 86], 0.5);
      put(im, x, y, c);
    }
    for (let k = 0; k < 8; k++) { const x = r() * 32; for (let y = 0; y < 10 + r() * 14; y++) put(im, x, y, add([100, 48, 38], -8)); }
    return im;
  };
  // Converters, condenser and boiler: aluminium-clad insulation with overlapping sheets.
  T.clad = () => {
    const im = img(32, 32), r = rng(511), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([184, 186, 186], (n(x, y) - 0.5) * 16 + (r() - 0.5) * 6);
      if (y % 16 === 0) c = [138, 140, 142];
      if (y % 16 === 1) c = [208, 210, 212];
      put(im, x, y, c);
    }
    // sulphur bloom where the jacket weeps
    for (let k = 0; k < 6; k++) put(im, r() * 32, 20 + r() * 12, [200, 186, 80]);
    return im;
  };
  // Skid and saddles: dark primer steel.
  T.steel = () => {
    const im = img(32, 32), r = grain(im, [58, 60, 58], 521, 4, 6, 8);
    for (let k = 0; k < 10; k++) put(im, r() * 32, r() * 32, [98, 68, 46]);
    return im;
  };
  // Seal pit: solidified sulphur crust round the liquid surface.
  T.sulfur = () => {
    const im = img(32, 32), r = rng(531), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(mix([222, 196, 60], [170, 130, 40], n(x, y)), (r() - 0.5) * 10));
    return im;
  };
  // Atlas (32 px, 2 px per uv unit): peep sight cold/lit, the furnace end with its burner register, a nameplate.
  const A = { peep: [0, 0, 6, 6], peep_lit: [6, 0, 6, 6], plate: [12, 0, 12, 6], end: [0, 8, 16, 16] };
  T.parts = () => {
    const im = img(32, 32), r = rng(541);
    const peep = (ox, lit) => {
      for (let y = 0; y < 6; y++) for (let x = 0; x < 6; x++) {
        const d = Math.hypot(x + 0.5 - 3, y + 0.5 - 3);
        let c = d > 2.6 ? [70, 70, 68] : d > 1.8 ? [150, 150, 146] : lit ? mix([255, 230, 150], [255, 120, 40], d / 1.8) : [30, 34, 40];
        put(im, ox + x, y, c);
      }
    };
    peep(0, false); peep(6, true);
    for (let y = 0; y < 6; y++) for (let x = 0; x < 12; x++) {
      let c = add([170, 172, 168], (r() - 0.5) * 6);
      if (x === 0 || y === 0 || x === 11 || y === 5) c = [92, 94, 92];
      if (y > 1 && y < 4 && x > 1 && x < 10 && r() < 0.5) c = [42, 44, 46];
      put(im, 12 + x, y, c);
    }
    // furnace end: dished head, a ring of bolts round the burner flange
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x + 0.5 - 8, y + 0.5 - 8) / 8;
      let c = add([110, 54, 42], (r() - 0.5) * 6 - d * 14);
      if (d < 0.42) c = add([72, 74, 74], (r() - 0.5) * 6);
      if (Math.abs(d - 0.48) < 0.06) c = [150, 150, 146];
      put(im, x, 8 + y, c);
    }
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    furnace: { tex: 'claus_unit/furnace', P: 16 }, clad: { tex: 'claus_unit/clad', P: 16 },
    cstl: { tex: 'claus_unit/steel', P: 16 }, sulfur: { tex: 'claus_unit/sulfur', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT, { p: { tex: 'claus_unit/parts', size: 32, rects: A } }),
    { particle: 'claus_unit/furnace' });

  const TAN16 = Math.tan(Math.PI / 16);
  // Upright 16-gon cut at block lines (P = 16 texture), as in the BWR script.
  function cylY(m, name, c, r, y0, y1, mat, o = {}) {
    const h = r * TAN16, w = 2 * h, tex = m.mat[mat].tex, cuts = [y0];
    for (let y = Math.floor(y0 / 16) * 16 + 16; y < y1; y += 16) cuts.push(y);
    cuts.push(y1);
    let facet = o.seed || 0, n = 0;
    for (let s = 0; s + 1 < cuts.length; s++) {
      const a = cuts[s], b = cuts[s + 1], base = Math.floor(a / 16) * 16, last = s + 2 === cuts.length;
      [[true, 0], [true, 22.5], [true, 45], [true, -22.5], [true, -45], [false, 0], [false, 22.5], [false, -22.5]].forEach(([wide, ang]) => {
        const f = wide ? [c[0] - r, a, c[1] - h] : [c[0] - h, a, c[1] - r];
        const t = wide ? [c[0] + r, b, c[1] + h] : [c[0] + h, b, c[1] + r];
        const sides = wide ? ['east', 'west'] : ['north', 'south'];
        const face = {}, hide = [];
        for (const d of DIRS) {
          if (sides.includes(d)) {
            const u0 = ((facet++ * 0.6180339) % 1) * (16 - w);
            face[d] = { tex, uv: [u0, 16 - (b - base), u0 + w, 16 - (a - base)] };
          } else if (!(d === 'up' && last && o.top)) hide.push(d);
        }
        m.els.push({ name: `${name}_${n++}`, from: f, to: t, mat, hide, face,
          rot: ang ? { axis: 'y', angle: ang, origin: [c[0], 8, c[1]] } : undefined });
      });
    }
  }

  function claus(running) {
    const m = mk();
    m.box('skid', [0, 0, 0], [16, 1, 16], 'cstl');
    // reaction furnace and waste heat boiler along x, on two saddles
    for (const x of [2, 12]) m.box(`saddle_${x}`, [x, 1, 2.5], [x + 1.4, 3.4, 9.5], 'cstl', { split: '' });
    m.prism('furnace', 'x', [7.4, 6], 4, 1, 9.5, 'furnace');
    m.prism('whb', 'x', [7.4, 6], 3.4, 9.5, 15.6, 'clad', { ends: ['east'] });
    m.prism('whb_flange', 'x', [7.4, 6], 4.2, 9.2, 10, 'cstl');
    m.disc('furnace_end', 'x', [7.4, 6], 4, 0.4, 1, 'furnace', 'claus_unit/parts', 'west');
    // the disc maps the whole atlas; give it the end rectangle instead
    m.els.slice(-8).forEach((e) => {
      const u = e.face.west.uv, k = 0.5;
      e.face.west = { tex: 'claus_unit/parts', uv: [u[0] * k, 4 + u[1] * k, u[2] * k, 4 + u[3] * k] };
    });
    m.prism('burner', 'x', [7.4, 6], 1.4, -1, 0.4, 'cstl', { ends: ['west'] });
    m.box('air_inlet', [-0.6, 7.6, 5.2], [0.4, 12.5, 6.8], 'cstl', { split: '' });
    m.box('acid_gas_inlet', [-0.4, 2, 5.4], [0.4, 6.4, 6.6], 'clad', { split: '' });
    m.decal(running ? 'glow_peep' : 'peep', [-0.2, 8.6, 7.6], [0.4, 10.6, 9.6], 'cstl', 'west', running ? 'p.peep_lit' : 'p.peep', { hide: ['east'] });
    m.decal('nameplate', [4, 4.8, 1.7], [10, 7.6, 2], 'cstl', 'north', 'p.plate', { hide: ['south'] });
    m.box('steam_drum_riser', [12, 11, 5.4], [13.2, 14.2, 6.6], 'clad', { split: '' });
    m.prism('steam_drum', 'x', [15.2, 6], 1.3, 10, 15.6, 'clad', { ends: ['west', 'east'] });
    // three catalytic converters on the condenser header along the south half
    m.prism('condenser', 'x', [3, 13], 1.8, 0.5, 15.5, 'clad', { ends: ['west', 'east'] });
    [2.6, 6.8, 11].forEach((x, i) => {
      cylY(m, `converter_${i}`, [x, 13], 1.9, 4.8, 12 - i, 'clad', { top: true, seed: i * 3 });
      m.box(`converter_nozzle_${i}`, [x - 0.5, 12 - i, 12.5], [x + 0.5, 13.4 - i, 13.5], 'cstl', { split: '' });
      m.box(`reheater_${i}`, [x - 0.4, 3.6, 10.6], [x + 0.4, 4.8, 11.4], 'cstl', { split: '' });
    });
    m.box('converter_link', [2, 13.4, 12.6], [12, 14, 13.4], 'clad', { split: '' });
    // sulphur rundown into the seal pit at the south edge
    m.box('rundown', [5.6, 1, 13.4], [6.4, 3, 15.2], 'cstl', { split: '' });
    m.box('seal_pit', [4, 1, 14.2], [8, 1.6, 16], 'cstl', { split: '', face: { up: 'sulfur' } });
    // tail gas incinerator and its stack in the south-east corner
    cylY(m, 'incinerator', [13.5, 13.5], 2.3, 1, 9, 'furnace', { seed: 9 });
    cylY(m, 'stack', [13.5, 13.5], 1.4, 9, 32, 'cstl', { seed: 2 });
    m.box('stack_lip', [12, 31.2, 12], [15, 32, 15], 'cstl', { split: '' });
    for (const y of [16, 24]) m.box(`stack_guy_${y}`, [12.3, y, 12.3], [14.7, y + 0.6, 14.7], 'cstl', { split: '' });
    return m;
  }

  const BUILDS = {
    claus_unit: { model: () => claus(false) },
    claus_unit_running: { model: () => claus(true) },
  };
  window.CLAUS = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
