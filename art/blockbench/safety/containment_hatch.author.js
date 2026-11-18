// Containment personnel hatch (one cell), hand-authored for Blockbench; replaces the spec_plant.py placeholder and
// its unused door partial. A hermetic door of the Soviet NPP pattern set in the containment wall: a thick steel
// leaf on two heavy hinges, a central handwheel driving the wedge bolts (кремальера) round the edge, an embedded
// steel frame and liner running back through the concrete. Open, the leaf stands at 45 degrees off the wall (the
// most a block model may turn) and the dark tunnel of the liner shows. The block has no facing: the door is on
// the north face, the liner runs through to the south face.
// Load in Blockbench desktop after the shared toolkit:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/safety/containment_hatch.author.js', 'utf8'))
//   PCART.paintMaterials('<repo>'); HATCH.paint('<repo>')
//   HATCH.build('<repo>', 'containment_hatch'); PCART.save('<repo>', 'safety', 'containment_hatch')
(function () {
  const path = require('path');
  const { rng, img, put, add, mix, rect, vnoise, grain, writePng } = PCART;
  const TEX_REL = PCART.ASSETS + '/textures/block/containment_hatch';
  const T = {};

  // Containment concrete, smooth and painted with decontaminable pale paint near the opening.
  T.concrete = () => {
    const im = img(32, 32), r = rng(1301), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([168, 166, 158], (n(x, y) - 0.5) * 14 + (r() - 0.5) * 6));
    for (let k = 0; k < 14; k++) put(im, r() * 32, r() * 32, [132, 130, 124]);
    return im;
  };
  // Door leaf: steel in the blue-grey of Soviet hermetic doors, painted edges worn by the wedges.
  T.leaf = () => {
    const im = img(32, 32), r = rng(1311), n = vnoise(r, 32, 32, 16);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) {
      let c = add([92, 110, 120], (n(x, y) - 0.5) * 10 + (r() - 0.5) * 5);
      if (x < 2 || y < 2 || x > 29 || y > 29) c = add([150, 152, 150], (r() - 0.5) * 10);
      put(im, x, y, c);
    }
    // stencilled number band
    for (let x = 6; x < 26; x++) for (let y = 4; y < 7; y++) put(im, x, y, (x + y) % 5 === 0 ? [40, 40, 40] : [214, 206, 180]);
    return im;
  };
  // Frame and wedge bolts: bare machined steel with grease.
  T.frame = () => {
    const im = img(32, 32), r = rng(1321), n = vnoise(r, 32, 32, 8);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add([118, 120, 122], (n(x, y) - 0.5) * 22 + (r() - 0.5) * 6));
    return im;
  };
  // Liner tunnel: dark steel.
  T.liner = () => { const im = img(32, 32); grain(im, [40, 42, 44], 1331, 4, 4, 8); return im; };
  // Handwheel and the warning stripes on the frame: red and yellow-black.
  T.red = () => { const im = img(32, 32); grain(im, [168, 34, 30], 1341, 5, 6, 8); return im; };
  T.stripe = () => {
    const im = img(32, 32), r = rng(1351);
    for (let y = 0; y < 32; y++) for (let x = 0; x < 32; x++) put(im, x, y, add(((x + y) >> 3) % 2 ? [22, 22, 22] : [222, 180, 30], (r() - 0.5) * 8));
    return im;
  };

  function paint(root) {
    for (const n of Object.keys(T)) writePng(T[n](), path.join(root, TEX_REL, n + '.png'));
    return Object.keys(T).length;
  }

  const MAT = {
    concrete: { tex: 'containment_hatch/concrete', P: 16 }, leaf: { tex: 'containment_hatch/leaf', P: 16 },
    hframe: { tex: 'containment_hatch/frame', P: 16 }, liner: { tex: 'containment_hatch/liner', P: 16 },
    hred: { tex: 'containment_hatch/red', P: 16 }, stripe: { tex: 'containment_hatch/stripe', P: 16 },
  };
  const mk = () => Object.assign(new PCART.Model(MAT), { particle: 'containment_hatch/concrete' });

  // Door leaf with its fittings, built closed in the opening; `rot` swings the whole leaf about the hinge line.
  function leaf(m, rot) {
    const o = rot ? { rot, split: '' } : { split: '' };
    m.box('leaf', [3, 2.5, -0.6], [13, 13.5, 1], 'leaf', Object.assign({}, o, { face: { north: { tex: 'containment_hatch/leaf', uv: [0, 0, 16, 16] } } }));
    m.box('wheel_hub', [7.2, 7.2, -1.4], [8.8, 8.8, -0.6], 'hred', o);
    m.box('wheel_spoke_x', [4.5, 7.7, -1.2], [11.5, 8.3, -0.9], 'hred', o);
    m.box('wheel_spoke_y', [7.7, 4.7, -1.2], [8.3, 11.3, -0.9], 'hred', o);
    for (const [f, t] of [[[4.5, 4.6, -1.3], [11.5, 5.2, -0.8]], [[4.5, 10.8, -1.3], [11.5, 11.4, -0.8]],
      [[4.4, 4.6, -1.3], [5, 11.4, -0.8]], [[11, 4.6, -1.3], [11.6, 11.4, -0.8]]]) m.box(`wheel_rim_${f[0]}_${f[1]}`, f, t, 'hred', o);
    // wedge bolt rods from the hub to the four edges
    m.box('bolt_rod_v', [7.8, 3, -0.8], [8.2, 13, -0.6], 'hframe', o);
    m.box('bolt_rod_h', [3.5, 7.8, -0.8], [12.5, 8.2, -0.6], 'hframe', o);
    for (const [x, y] of [[8, 3], [8, 13], [3.5, 8], [12.5, 8]]) m.box(`wedge_${x}_${y}`, [x - 0.6, y - 0.6, -0.9], [x + 0.6, y + 0.6, -0.5], 'hframe', o);
  }

  function hatch(open) {
    const m = mk();
    // wall round the opening
    m.box('lintel', [0, 14, 0], [16, 16, 16], 'concrete');
    m.box('sill', [0, 0, 0], [16, 1.6, 16], 'concrete');
    m.box('jamb_w', [0, 1.6, 0], [2.2, 14, 16], 'concrete');
    m.box('jamb_e', [13.8, 1.6, 0], [16, 14, 16], 'concrete');
    // embedded frame with warning stripes, the liner through to the south face
    m.box('frame_t', [2.2, 13.4, -0.4], [13.8, 14, 2], 'stripe', { split: '' });
    m.box('frame_b', [2.2, 1.6, -0.4], [13.8, 2.2, 2], 'stripe', { split: '' });
    m.box('frame_w', [2.2, 2.2, -0.4], [2.8, 13.4, 2], 'hframe', { split: '' });
    m.box('frame_e', [13.2, 2.2, -0.4], [13.8, 13.4, 2], 'hframe', { split: '' });
    m.box('liner_t', [2.8, 13, 2], [13.2, 13.4, 16], 'liner', { split: '' });
    m.box('liner_b', [2.8, 2.2, 2], [13.2, 2.6, 16], 'liner', { split: '' });
    m.box('liner_w', [2.8, 2.6, 2], [3.2, 13, 16], 'liner', { split: '' });
    m.box('liner_e', [12.8, 2.6, 2], [13.2, 13, 16], 'liner', { split: '' });
    // hinges on the east side
    for (const y of [3.5, 10.5]) {
      m.box(`hinge_knuckle_${y}`, [13.2, y, -1.6], [14.6, y + 2, -0.2], 'hframe', { split: '' });
      m.box(`hinge_strap_${y}`, [11, y + 0.4, -1.1], [13.2, y + 1.6, -0.6], 'hframe',
        open ? { split: '', rot: { axis: 'y', angle: -45, origin: [13.9, 8, -0.9] } } : { split: '' });
    }
    if (open) {
      // the far end of the tunnel stays shut by the inner door of the airlock
      m.box('inner_door', [3.2, 2.6, 15], [12.8, 13, 15.6], 'leaf', { split: '' });
      leaf(m, { axis: 'y', angle: -45, origin: [13.9, 8, -0.9] });
    } else {
      leaf(m);
      m.box('inner_door', [3.2, 2.6, 15], [12.8, 13, 15.6], 'leaf', { split: '' });
    }
    return m;
  }

  const BUILDS = {
    containment_hatch: { model: () => hatch(false) },
    containment_hatch_open: { model: () => hatch(true) },
  };
  window.HATCH = { paint, BUILDS, build: (root, id) => PCART.realize(root, BUILDS[id].model(), id) };
})();
