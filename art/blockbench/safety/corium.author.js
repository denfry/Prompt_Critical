// Corium: the molten core after it has left the vessel. Uranium-zirconium oxide melt at ~2500 C glows yellow-white
// where it is open and runs; within minutes it skins over with a dark oxide crust that cracks as the melt under it
// moves, the cracks glowing orange (photographs of the KROTOS/FARO and VULCANO spreading tests, and the corium "lava"
// of Chernobyl unit 4 before it set). Two blocks:
//   corium       — the melt front in the concrete (MCCI): a crusted lump whose open top still runs;
//   corium_pool  — the melt spread over the cavity floor: a layer about 15 cm deep (2.5 px) of running melt with
//                  floating plates of crust on it.
// Textures (32 px): corium/melt (8-frame strip, flowing, loops) and corium/crust (static, glowing cracks). Every
// element is a glow_ element: the melt and its cracks light themselves (the block light is 15 as well).
//
// Load after pcart.js, then CORIUM.paint(repo); CORIUM.build(repo, 'corium') /* wait */ CORIUM.save(repo, 'corium');
// the same for 'corium_pool'.
(function () {
  const fs = require('fs');
  const path = require('path');
  const P = PCART;
  const FRAMES = 8, S = 32;

  // temperature ramp of an oxide melt seen in the open: crust, cherry, orange, yellow, white
  const RAMP = [[34, 26, 24], [92, 22, 10], [168, 40, 10], [226, 92, 18], [252, 170, 50], [255, 226, 140], [255, 246, 214]];
  function ramp(t) {
    t = Math.max(0, Math.min(1, t)) * (RAMP.length - 1);
    const i = Math.min(RAMP.length - 2, Math.floor(t));
    return P.mix(RAMP[i], RAMP[i + 1], t - i);
  }

  // melt: two noise layers scrolled across each other (4 px a frame, so 8 frames make one 32 px period and loop),
  // a slow crust net where both are low
  function melt() {
    const im = P.img(S, S * FRAMES), r = P.rng(2501);
    const a = P.vnoise(r, S, S, 8), b = P.vnoise(r, S, S, 4), c = P.vnoise(r, S, S, 16);
    for (let f = 0; f < FRAMES; f++) {
      const sh = f * (S / FRAMES);
      for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
        const n1 = a(x, (y + sh) % S), n2 = b((x + sh) % S, y), n3 = c((x + S - sh) % S, (y + sh) % S);
        let t = 0.55 * n1 + 0.3 * n2 + 0.15 * n3;
        t = (t - 0.25) / 0.6;
        if (t < 0.18) t *= 0.4;                           // skins of crust where the melt runs coolest
        P.put(im, x, f * S + y, ramp(t));
      }
    }
    return im;
  }

  // crust: dark oxide, grain and scale, cracks along the noise's mid line glowing with the melt under them
  function crust() {
    const im = P.img(S, S), r = P.rng(2502), n = P.vnoise(r, S, S, 8), m = P.vnoise(r, S, S, 4);
    for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) {
      const v = n(x, y), crack = Math.abs(v - 0.5);
      let col = P.add([46, 38, 36], (m(x, y) - 0.5) * 22 + (r() - 0.5) * 8);
      if (crack < 0.035) col = ramp(0.62 - crack * 6);
      else if (crack < 0.06) col = P.mix(col, ramp(0.35), 0.6);
      P.put(im, x, y, col);
    }
    return im;
  }

  function paint(root) {
    const dir = path.join(root, P.ASSETS, 'textures/block/corium');
    fs.mkdirSync(dir, { recursive: true });
    P.writePng(melt(), path.join(dir, 'melt.png'));
    fs.writeFileSync(path.join(dir, 'melt.png.mcmeta'),
      JSON.stringify({ animation: { frametime: 6, interpolate: true } }, null, '\t') + '\n');
    P.writePng(crust(), path.join(dir, 'crust.png'));
    return [FRAMES];
  }

  const MAT = { melt: { tex: 'corium/melt', P: 16 }, crust: { tex: 'corium/crust', P: 16 } };

  function model(id) {
    const m = Object.assign(new P.Model(MAT), { particle: 'corium/crust' });
    if (id === 'corium') {
      // a crusted lump filling the block, its open top still running
      m.box('glow_body', [0, 0, 0], [16, 16, 16], 'crust', { split: '', face: { up: 'melt' } });
      return m;
    }
    // the spread layer: running melt 2.5 px deep, plates of crust floating on it (fixed layout, rotated per block
    // by the blockstate)
    m.box('glow_melt', [0, 0, 0], [16, 2.5, 16], 'melt', { split: '', hide: ['down'] });
    const r = P.rng(2503);
    const plates = [[1, 2, 5, 4], [9, 1, 5, 3], [3, 9, 4, 5], [10, 8, 4, 6], [6, 5, 3, 2], [12, 13, 3, 2], [1, 14, 4, 2]];
    plates.forEach(([x, z, w, d], i) => {
      const h = 2.5 + 0.4 + r() * 0.6;
      m.box(`glow_plate_${i}`, [x, 2.2, z], [x + w, h, z + d], 'crust', { split: '', hide: ['down'] });
    });
    return m;
  }

  window.CORIUM = {
    paint,
    model,
    build: (root, id) => { const m = model(id); P.realize(root, m, id); return m.els.length; },
    save: (root, id) => P.save(root, 'safety', id),
  };
})();
