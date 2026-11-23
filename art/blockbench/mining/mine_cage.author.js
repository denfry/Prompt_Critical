// Mine cage at real scale (1 block = 1 m): a single-deck cage of riveted steel angle for one tub, as the cages of
// the 1870s-1900s coal shafts. Deck 2 x 2 m, 2.2 m clear under the bonnet, a bar gate on the facade, barred sides
// over 1 m sheet panels, guide shoes on the side faces, and the bridle (cross beam, sling plates, rope cap) over the
// bonnet. Dimensions and sources: docs/research/mine_cage-dimensions.md (disk only).
//
// The cage stays the mine_cage block (it rides the shaft as a Create contraption), so this is a plain Java block
// model, not a sliced whole model. Footprint Segment.box(0, 1, -1, 1, 0, 1): x 0..32, y -16..32, z 0..32 about the
// controller (the middle row). Facade north (z = 0). The built-in track (cage_track block) lies in the column x 0..16
// on the floor row along z, so the deck there is a thin plate under the rail and nothing stands on it.
//
// Load after pcart.js, pcround.js and vverkit.js, then MINE_CAGE.build(repo, open); /* wait */ MINE_CAGE.save(repo, open).
(function () {
  const FLOOR = -16, DECK = -15.5, ROOF = 20.5, TOP = 32;

  function model(open) {
    const m = PCVV.mk('material/steel');

    // deck: thin chequer plate (the rail of the track block lies on it), sole channels round the edge
    m.box('deck', [0, FLOOR, 0], [32, DECK, 32], 'chequer');
    m.box('sole_w', [0, DECK, 1.5], [1.5, DECK + 1, 30.5], 'st');
    m.box('sole_e', [30.5, DECK, 1.5], [32, DECK + 1, 30.5], 'st');
    m.box('sole_s', [1.5, DECK, 30.5], [30.5, DECK + 1, 32], 'st');
    // tub stops at the back on the track side, painted red: the tub rolls in from the facade and stops on them
    for (const x of [4, 11]) m.box(`stop_${x}`, [x - 0.6, DECK, 28], [x + 0.6, DECK + 1.6, 29.2], 'red', { split: '' });

    // corner posts of angle steel and the top frame under the bonnet
    for (const [x, z] of [[0, 0], [30.5, 0], [0, 30.5], [30.5, 30.5]]) m.box(`post_${x}_${z}`, [x, DECK, z], [x + 1.5, ROOF, z + 1.5], 'st', { split: 'y' });
    m.box('frame_n', [1.5, ROOF - 1.5, 0], [30.5, ROOF, 1.2], 'st');
    m.box('frame_s', [1.5, ROOF - 1.5, 30.8], [30.5, ROOF, 32], 'st');
    m.box('frame_w', [0, ROOF - 1.5, 1.5], [1.2, ROOF, 30.5], 'st');
    m.box('frame_e', [30.8, ROOF - 1.5, 1.5], [32, ROOF, 30.5], 'st');

    // sides and back: 1 m sheet panels, a rail on top of them, then vertical bars to the top frame
    const PANEL = DECK + 16;
    m.box('panel_w', [0.2, DECK + 1, 1.5], [0.7, PANEL, 30.5], 'dark');
    m.box('panel_e', [31.3, DECK + 1, 1.5], [31.8, PANEL, 30.5], 'dark');
    m.box('panel_s', [1.5, DECK + 1, 31.3], [30.5, PANEL, 31.8], 'dark');
    m.box('rail_w', [0, PANEL, 1.5], [1.2, PANEL + 1, 30.5], 'st');
    m.box('rail_e', [30.8, PANEL, 1.5], [32, PANEL + 1, 30.5], 'st');
    m.box('rail_s', [1.5, PANEL, 30.8], [30.5, PANEL + 1, 32], 'st');
    for (let a = 4; a < 30; a += 3) {
      m.box(`bar_w_${a}`, [0.3, PANEL + 1, a - 0.3], [0.9, ROOF - 1.5, a + 0.3], 'st', { split: '' });
      m.box(`bar_e_${a}`, [31.1, PANEL + 1, a - 0.3], [31.7, ROOF - 1.5, a + 0.3], 'st', { split: '' });
      m.box(`bar_s_${a}`, [a - 0.3, PANEL + 1, 31.1], [a + 0.3, ROOF - 1.5, 31.7], 'st', { split: '' });
    }
    // grab rail inside along the east side for the men riding the cage
    m.box('grab', [29.6, DECK + 22, 3], [30.2, DECK + 22.6, 29], 'bright', { split: '' });
    for (const z of [3, 29]) m.box(`grab_${z}`, [30.2, DECK + 22, z - 0.3], [30.8, DECK + 22.6, z + 0.3], 'bright', { split: '' });

    // facade gate: horizontal bars between two stiles; closed it hangs over the opening, open it is lifted
    // up under the top frame (the onsetter lifts it before the tub runs out)
    const lift = open ? 23 : 0, g0 = DECK + 2 + lift, g1 = Math.min(DECK + 26 + lift, ROOF - 1.6);
    for (const x of [1.6, 29.6]) m.box(`gate_stile_${x}`, [x, g0, 0.2], [x + 0.8, open ? ROOF - 1.6 : g1, 1.0], 'st', { split: '' });
    const bars = open ? [ROOF - 2.6, ROOF - 3.8] : [0, 4, 8, 12, 16, 20, 24].map((k) => g0 + k);
    bars.forEach((y, i) => m.box(`gate_bar_${i}`, [2.4, y, 0.4], [29.6, y + 0.8, 0.9], i === 0 ? 'yel' : 'st', { split: '' }));
    m.box('gate_guide_w', [0.5, DECK + 1, 1.2], [1.5, ROOF - 1.5, 1.6], 'st', { split: 'y' });
    m.box('gate_guide_e', [30.5, DECK + 1, 1.2], [31.5, ROOF - 1.5, 1.6], 'st', { split: 'y' });

    // guide shoes: the cage runs on two guides in the shaft, one at each side face
    for (const y of [DECK + 1, ROOF - 4]) {
      m.box(`shoe_w_${y}`, [0, y, 14], [0.8, y + 3, 18], 'ci', { split: '' });
      m.box(`shoe_e_${y}`, [31.2, y, 14], [32, y + 3, 18], 'ci', { split: '' });
    }

    // bonnet: a pitched steel roof against falling stones, ridge along x, and its ridge plate
    m.box('bonnet_floor', [0, ROOF, 0], [32, ROOF + 0.5, 32], 'dark');
    m.box('bonnet_n', [0, ROOF + 0.5, 1], [32, ROOF + 1, 16], 'dark', { rot: { axis: 'x', angle: -22.5, origin: [16, ROOF + 0.5, 1] } });
    m.box('bonnet_s', [0, ROOF + 0.5, 16], [32, ROOF + 1, 31], 'dark', { rot: { axis: 'x', angle: 22.5, origin: [16, ROOF + 0.5, 31] } });

    // bridle: sling plates from the side posts to a cross beam, the rope cap and the shackle on the beam
    for (const x of [1, 29]) {
      m.box(`sling_${x}`, [x, ROOF + 0.5, 14.5], [x + 2, TOP - 4, 17.5], 'st', { split: '' });
    }
    m.box('crossbeam', [1, TOP - 4, 14], [31, TOP - 2, 18], 'st');
    m.box('cap_socket', [14, TOP - 2, 14.5], [18, TOP - 0.5, 17.5], 'ci', { split: '' });
    m.box('cap_shackle', [15, TOP - 0.5, 15.5], [17, TOP, 16.5], 'bright', { split: '' });
    // maker's plate on the west facade post
    m.decal('plate', [2.4, DECK + 30, 0], [6, DECK + 32.2, 0.2], 'st', 'north', 'pl.maker');
    return m;
  }

  window.MINE_CAGE = {
    model,
    build: (root, open) => { const m = model(open); PCART.realize(root, m, open ? 'mine_cage_open' : 'mine_cage'); return { elements: m.els.length, bounds: PCVV.bounds(m) }; },
    save: (root, open) => PCART.save(root, 'mining', open ? 'mine_cage_open' : 'mine_cage'),
  };
})();
