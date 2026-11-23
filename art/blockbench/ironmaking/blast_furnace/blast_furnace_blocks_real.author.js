// Blast furnace blocks at real scale (1 block = 1 m, 16 units = 1 m, 32 px per block): the taphole cell of the
// hearth jacket (a cast-iron taphole frame with the clay-gun seat, the clay plug, the head of the iron trough and the
// water-cooled cinder notch above) and the furnace controller as a blowing-room instrument cabinet (blast pressure,
// hot blast pyrometer and top gas pressure dials, the stove changeover flags, a bell and a pilot lamp). New ids
// (*_real) so the HQ generators (art/tools/hq_blastfurnace.py, spec_metallurgy.py) don't overwrite them; the
// blockstate switches to them. Sizes: docs/research/blast_furnace-dimensions.md (disk only).
//
// Load in Blockbench after pcart.js, pcround.js, pcslice.js and core/vver/vverkit.js, then BFB.saveAll(repo) (async).
// Every model faces north: the front is z = 0 and its fittings stand out to -z.
(function () {
  const MAT = Object.assign({}, PCVV.MAT, {
    plate: { tex: 'fire_tube_boiler/plate', P: 32 }, rivets: { tex: 'fire_tube_boiler/rivets', P: 16 },
  });
  const FIT = { slot_glow: [30, 10, 1, 5] };
  const mk = () => Object.assign(new PCART.Model(MAT, {
    fit: { tex: 'fire_tube_boiler/fittings', size: 64, rects: FIT },
  }), { particle: 'material/cast_iron' });
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];
  function glowBox(m, name, f, t, mat) {
    if (mat) { m.box('glow_' + name, f, t, mat, { split: '' }); return; }
    const k = 16 / 64, [x, y, w, h] = FIT.slot_glow, uv = [x * k, y * k, (x + w) * k, (y + h) * k];
    const face = {};
    for (const d of DIRS) face[d] = { tex: 'fire_tube_boiler/fittings', uv };
    m.els.push({ name: 'glow_' + name, from: f, to: t, mat: 'ci', hide: [], face });
  }
  const o = { split: '' };

  // ------------------------------------------------------------------ taphole
  function taphole(open) {
    const m = mk();
    // hearth jacket: riveted plate over the brick, two horizontal bands
    m.box('jacket', [0, 0, 0], [16, 16, 16], 'plate');
    for (const y of [0.6, 14.6]) m.box(`band_${y}`, [0, y, -0.4], [16, y + 0.8, 0], 'rivets', o);
    // cast-iron taphole frame (breast plate) with its bolts
    m.box('frame', [3, 1.4, -1.2], [13, 11, 0], 'ci', o);
    for (const [x, y] of [[3.6, 2], [11.8, 2], [3.6, 9.8], [11.8, 9.8]]) m.box(`frame_bolt_${x}_${y}`, [x, y, -1.5], [x + 0.6, y + 0.6, -1.2], 'bright', o);
    // clay-gun seat: a machined ring the gun nozzle presses against
    m.ring('gun_seat', 'z', [8, 5.6], 2.8, 0.7, -1.8, -1.2, 'bright');
    if (open) {
      m.prism('hole', 'z', [8, 5.6], 1.6, -1.3, -1.2, 'soot', { split: '', ends: ['north'] });
    } else {
      m.prism('clay', 'z', [8, 5.6], 2.3, -1.6, -1.2, 'brick', { split: '', ends: ['north'] });
      m.box('clay_daub', [6.6, 2.4, -1.5], [9.4, 4, -1.2], 'brick', o);
    }
    // head of the iron trough (the runner block continues it to the north)
    m.box('trough', [4, 0, -4], [12, 1.6, 0], 'brick', o);
    m.box('trough_channel', [6, 1.2, -4], [10, 1.62, -1.2], 'soot', o);
    m.box('trough_lip', [3.6, 1.6, -4], [4.4, 2.6, -1.2], 'brick', o);
    m.box('trough_lip_e', [11.6, 1.6, -4], [12.4, 2.6, -1.2], 'brick', o);
    // water-cooled cinder notch, offset above, with its supply and return
    m.ring('cinder_cooler', 'z', [12.4, 13], 1.6, 0.8, -1, 0, 'br');
    m.prism('cinder_plug', 'z', [12.4, 13], 0.9, -1.2, 0, 'ci', { split: '', ends: ['north'] });
    m.box('water_in', [14.6, 11, -0.9], [15.2, 16, -0.3], 'bright', o);
    m.box('water_in_elbow', [13.2, 12.7, -0.9], [15.2, 13.3, -0.3], 'bright', o);
    m.box('water_out', [9.6, 13.6, -0.9], [11.4, 14.2, -0.3], 'bright', o);
    m.box('water_out_riser', [9.6, 14.2, -0.9], [10.2, 16, -0.3], 'bright', o);
    if (open) {
      glowBox(m, 'jet', [7, 3.6, -2.6], [9, 6.2, -1.3]);
      glowBox(m, 'stream', [6.2, 1.3, -4], [9.8, 1.66, -1.2]);
      glowBox(m, 'bore', [6.8, 4.4, -1.31], [9.2, 6.8, -1.29]);
    }
    return m;
  }

  // ------------------------------------------------------------------ controller
  function dial(m, n, c, r, z) {
    m.ring(`${n}_bezel`, 'z', c, r + 0.3, 0.6, z - 0.6, z, 'br');
    m.plate(`${n}_face`, [c[0] - r, c[1] - r, z - 0.2], [c[0] + r, c[1] + r, z], 'bright', 'north', 'material/gauge_dial');
  }
  function controller(alarm) {
    const m = mk();
    m.box('plinth', [0.4, 0, 2.4], [15.6, 1, 15.6], 'ci', o);
    m.box('cabinet', [1, 1, 3], [15, 15, 15], 'green', o);
    m.box('cornice', [0.6, 15, 2.6], [15.4, 16, 15.4], 'ci', o);
    // door frame strips and rivets on the front
    for (const [n, f, t] of [['l', [1, 1, 2.6], [1.8, 15, 3]], ['r', [14.2, 1, 2.6], [15, 15, 3]], ['b', [1.8, 1, 2.6], [14.2, 1.8, 3]], ['t', [1.8, 14.2, 2.6], [14.2, 15, 3]]]) m.box(`door_${n}`, f, t, 'ci', o);
    for (const x of [2.2, 13.4]) for (const y of [2.2, 13.4]) m.box(`rivet_${x}_${y}`, [x, y, 2.4], [x + 0.4, y + 0.4, 2.6], 'bright', o);
    m.box('hinge_a', [1, 3, 2.2], [1.6, 5, 2.6], 'bright', o);
    m.box('hinge_b', [1, 11, 2.2], [1.6, 13, 2.6], 'bright', o);
    m.box('handle', [13.6, 6.6, 2], [14.2, 9, 2.6], 'bright', o);
    // dials: blast pressure (large), hot blast pyrometer, top gas pressure
    dial(m, 'blast', [5.6, 10.4], 2.8, 3);
    dial(m, 'pyrometer', [11, 11.6], 1.8, 3);
    dial(m, 'topgas', [11, 7.8], 1.4, 3);
    m.box('label_blast', [3.8, 6.8, 2.8], [7.4, 7.4, 3], 'br', o);
    // stove changeover board: four windows with gas (red) or blast (green) flags
    m.box('board', [2.4, 3, 2.7], [13.6, 5.6, 3], 'dark', o);
    for (let i = 0; i < 4; i++) {
      const x = 3 + i * 2.7;
      m.box(`flag_${i}`, [x, 3.5, 2.6], [x + 1.8, 5.1, 2.7], i % 2 ? 'green' : 'red', o);
    }
    // logbook shelf
    m.box('shelf', [2, 2, 0.8], [14, 2.6, 3], 'wood', o);
    m.box('logbook', [3, 2.6, 1.2], [7, 3.2, 2.8], 'red', o);
    // bell and lamp on top
    m.cylY('bell', [4.4, 9], 1.4, 16, 17.4, 'br', { cap: 'material/brass' });
    m.box('bell_post', [4.1, 17.4, 8.7], [4.7, 18, 9.3], 'ci', o);
    m.cylY('lamp_base', [11.6, 9], 1.2, 16, 16.6, 'ci', { cap: 'material/cast_iron' });
    if (alarm) glowBox(m, 'alarm_lamp', [10.8, 16.6, 8.2], [12.4, 18.4, 9.8], 'red');
    else glowBox(m, 'pilot_lamp', [11, 16.6, 8.4], [12.2, 17.6, 9.6]);
    return m;
  }

  function builds() {
    return {
      blast_furnace_taphole_real: { model: () => taphole(false), java: 'blast_furnace_taphole_real' },
      blast_furnace_taphole_real_open: { model: () => taphole(true), java: 'blast_furnace_taphole_real_open' },
      blast_furnace_controller_real: { model: () => controller(false), java: 'blast_furnace_controller_real' },
      blast_furnace_controller_real_alarm: { model: () => controller(true), java: 'blast_furnace_controller_real_alarm' },
    };
  }
  async function saveAll(root) {
    const out = {};
    for (const [id, b] of Object.entries(builds())) {
      ModelProject.all.slice().forEach((p) => p.close(true));
      PCART.realize(root, b.model(), id);
      await new Promise((r) => setTimeout(r, 1500));
      out[id] = PCART.save(root, 'ironmaking/blast_furnace', id, b.java);
    }
    return out;
  }
  window.BFB = { builds, taphole, controller, saveAll };
})();
