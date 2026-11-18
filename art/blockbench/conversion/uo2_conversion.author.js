// UO2 conversion furnace (dry route: UF6 or ADU calcined and reduced in hydrogen in an electrically heated rotary
// tube), hand-authored for Blockbench. Replaces the borrowed uf6_autoclave model.
// Load inside Blockbench desktop, after pcart.js and plantkit.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/conversion/uo2_conversion.author.js', 'utf8'))
//   UO2.build('<repo>', id); UO2.save('<repo>', id)   for uo2_conversion, uo2_conversion_lit
//
// One block, facade north. The Inconel tube runs west to east through the insulated furnace on its frame; feed
// powder comes from the hopper over the west seal, hydrogen (yellow, red ring) enters the east seal against the
// powder, and the HF off-gas (orange, acid) leaves the west end for the scrubber. UO2 powder falls from the east
// seal into the product can. The three heater zones show glowing through their sight slots when lit.
// Points used by code (Uo2ConversionBlockEntity): off-gas (1.2, 16, 12); product can lid (12.5, 6, 6).
(function () {
  const mk = () => Object.assign(new PCART.Model(PLANT.MAT, { parts: PLANT.PARTS }), { particle: 'plant/light' });

  function furnace(lit) {
    const m = mk();
    m.box('base', [0.5, 0, 1.5], [15.5, 0.6, 14.5], 'frame', {});
    for (const x of [1.5, 9.5]) for (const z of [3, 12]) m.box(`leg_${x}_${z}`, [x, 0.6, z], [x + 1, 6, z + 1], 'frame', { split: '' });
    m.box('furnace', [2, 6, 3.5], [14, 12.5, 13], 'light', {});
    m.box('furnace_band', [1.8, 9, 3.3], [14.2, 9.6, 13.2], 'frame', { split: '' });
    for (const x of [5.5, 10]) m.box(`zone_joint_${x}`, [x, 6, 3.3], [x + 0.4, 12.5, 13.2], 'frame', { split: '' });
    // heater zones: sight slots and terminal boxes with conduits
    [3.4, 7.6, 11.8].forEach((x, i) => {
      m.decal(lit ? `glow_zone_${i}` : `zone_${i}`, [x, 7, 3.2], [x + 1.4, 8.6, 3.5], 'frame', 'north', lit ? 'peep_lit' : 'peep',
        { hide: ['south'] });
      m.box(`terminal_${i}`, [x - 0.4, 12.5, 5], [x + 1.8, 14, 8], 'green', { split: '' });
      m.box(`conduit_${i}`, [x + 0.4, 14, 7.4], [x + 1, 16, 8], 'st', { split: '' });
    });
    // the rotary tube ends and the two seal housings
    m.prism('tube_w', 'x', [9.4, 8.2], 1.6, 0, 2, 'bright', { ends: ['west'] });
    m.prism('tube_e', 'x', [9.4, 8.2], 1.6, 14, 16, 'bright', { ends: ['east'] });
    m.box('seal_w', [0.4, 7.4, 6.4], [1.4, 11.4, 10], 'st', { split: '' });
    m.box('seal_e', [14.6, 7.4, 6.4], [15.6, 11.4, 10], 'st', { split: '' });
    m.box('drive_ring', [14.2, 7.2, 6.2], [14.6, 11.6, 10.2], 'frame', { split: '' });
    // feed hopper over the west seal
    m.box('hopper', [0.6, 12.5, 5.6], [4, 15.4, 9.4], 'bright', { split: '' });
    m.box('hopper_neck', [1.4, 11.4, 6.6], [3, 12.5, 8.4], 'bright', { split: '' });
    m.box('hopper_lid', [0.4, 15.4, 5.4], [4.2, 15.8, 9.6], 'frame', { split: '' });
    // hydrogen in at the east end, HF off-gas out at the west end
    m.box('h2_in', [15, 11.4, 7.6], [16, 15, 8.6], 'p_gas', { split: '' });
    m.box('h2_band', [14.9, 13.2, 7.5], [16, 13.8, 8.7], 'p_steam', { split: '' });
    m.box('offgas_up', [0.6, 9, 10.8], [1.8, 16, 12], 'p_acid', { split: '' });
    m.box('offgas_neck', [0.6, 9, 10], [1.8, 10.2, 10.8], 'p_acid', { split: '' });
    // product can under the east seal, with its chute and lid clamp
    m.box('chute', [13.6, 6, 7.4], [15, 7.4, 9], 'bright', { split: '' });
    m.prismY('can', [12.5, 8.2], 2.6, 0.6, 5.4, 'bright', { ends: ['up'] });
    m.prismY('can_lid', [12.5, 8.2], 2.8, 5.4, 6, 'frame', { ends: ['up', 'down'] });
    m.decal('can_tag', [11.3, 2.4, 5.5], [13.7, 3.6, 5.6], 'bright', 'north', 'tag', { hide: ['south'] });
    m.decal('trefoil', [6, 2, 3.2], [8.6, 4.6, 3.4], 'frame', 'north', 'trefoil', { hide: ['south'] });
    m.box('sign_post', [7, 4.6, 3.4], [7.6, 6, 3.8], 'frame', { split: '' });
    m.decal('gauge', [8.4, 13.2, 9.6], [10.4, 15.2, 9.8], 'frame', 'north', 'gauge', { hide: ['south'] });
    m.box('gauge_stem', [9.1, 12.5, 9.8], [9.7, 13.4, 10.4], 'st', { split: '' });
    return m;
  }

  const BUILDS = { uo2_conversion: { model: () => furnace(false) }, uo2_conversion_lit: { model: () => furnace(true) } };
  window.UO2 = {
    build: (root, id) => PCART.realize(root, BUILDS[id].model(), id),
    save: (root, id) => PCART.save(root, 'conversion', id, BUILDS[id].java),
  };
})();
