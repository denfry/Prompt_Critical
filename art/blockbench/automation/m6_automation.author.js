// M6 automation not yet in the mod, hand-authored (replaces the spec_automation2.py and spec_content2.py
// placeholders): PLC rack modules (CPU and its mode key, comm, remote I/O adapter, AI/AO/DI/DO), fieldbus gateway,
// repeater and cable, data diode, cabinet cooler and its fan, telemetry master, historian server, UPS, training
// simulator cabinets, simulator console, instructor station, simulation workstation, core cartogram panels, the
// pick-and-place component assembler and its head, the ESD valve and its stem, the radio mast and its yagi.
// Run headless: node art/tools/pcart_headless.js <repo> automation/m6_automation.author.js M6A --paint
//
// Front to the north. Wall modules hang on the south wall (backs at z 16) like the placeholders: rack modules
// fronts at z 8.4 (CPU, I/O) or 7.5 (comm, adapter), DIN units at 9..9.4. Partials: PLC key turns about z at
// (12, 6) on the CPU front; cooler fan about z at (8, 7) z 8.8..10.4; assembler head moves in x/z; ESD stem turns
// about y through (8, 8); yagi turns about the mast axis (8, 8).
(function () {
  const { rng, img, put, get, add, mix, rect, grain } = PCART;
  const { P, lamp, cabinet, flange, motor, gauge } = FUTK;
  const D = 'm6auto/', T = {};

  T.plastic = () => { const im = img(16, 16); grain(im, [150, 162, 172], 33101, 2, 3, 8); return im; };
  T.dark = () => { const im = img(16, 16); grain(im, [46, 50, 54], 33102, 2, 3, 8); return im; };
  T.white = () => { const im = img(16, 16); grain(im, [226, 228, 224], 33103, 2, 3, 8); return im; };
  T.black = () => { const im = img(16, 16); grain(im, [28, 30, 32], 33104, 2, 2, 8); return im; };
  T.violet = () => { const im = img(16, 16), r = rng(33105); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([118, 66, 150], ((x + y) % 6 < 1 ? -24 : 0) + (r() - 0.5) * 8)); return im; };
  // Sixteen channel LEDs in two columns with their numbers (BER lights the channels).
  T.leds = () => {
    const im = img(16, 16); rect(im, 0, 0, 16, 16, [40, 44, 48]);
    for (let i = 0; i < 8; i++) for (const x of [2, 9]) { rect(im, x, 1 + i * 2, 2, 1, [30, 70, 40]); rect(im, x + 3, 1 + i * 2, 2, 1, [150, 156, 160]); }
    return im;
  };
  T.eth = () => { const im = img(8, 8); rect(im, 0, 0, 8, 8, [30, 30, 32]); rect(im, 1, 2, 6, 5, [12, 12, 14]); for (let x = 1; x < 7; x++) put(im, x, 2, [200, 170, 60]); put(im, 0, 0, [60, 200, 80]); put(im, 7, 0, [230, 170, 40]); return im; };
  for (const [n, c] of [['s_blue', [40, 80, 190]], ['s_cyan', [70, 170, 220]], ['s_orange', [230, 120, 30]], ['s_white', [220, 222, 220]], ['s_red', [200, 40, 34]], ['s_green', [40, 150, 70]]]) {
    T[n] = () => { const im = img(8, 8); grain(im, c, 33110 + n.length, 2, 3, 4); return im; };
  }
  // Server front: eight disk caddies a row, a handle and an activity LED each.
  T.disks = () => {
    const im = img(32, 32), r = rng(33121); rect(im, 0, 0, 32, 32, [24, 26, 28]);
    for (let row = 0; row < 4; row++) for (let k = 0; k < 4; k++) {
      const x = 1 + k * 8, y = 1 + row * 8;
      rect(im, x, y, 7, 6, [70, 74, 78]); rect(im, x + 1, y + 1, 5, 1, [110, 114, 118]);
      for (let i = 0; i < 5; i++) put(im, x + 1 + i, y + 3, [40, 42, 44]);
      put(im, x + 5, y + 5, [60, 200, 90]);
    }
    return im;
  };
  T.perf = () => { const im = img(16, 16); rect(im, 0, 0, 16, 16, [128, 132, 128]); for (let y = 1; y < 16; y += 2) for (let x = (y >> 1) % 2; x < 16; x += 2) put(im, x, y, [40, 42, 44]); return im; };
  T.cglass = () => { const im = img(16, 16), r = rng(33131); for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(im, x, y, add([38, 44, 48], (x + y === 6 || x + y === 7 ? 18 : 0) + (r() - 0.5) * 4)); return im; };
  T.legend = () => { const im = img(16, 4), r = rng(33132); rect(im, 0, 0, 16, 4, [230, 228, 210]); for (let x = 1; x < 15; x++) if (r() < 0.6) put(im, x, 1 + (x % 2), [30, 30, 30]); return im; };
  T.trainer = () => { const im = img(16, 4); rect(im, 0, 0, 16, 4, [228, 186, 30]); for (let x = 2; x < 14; x++) if (x % 3) put(im, x, 1 + (x % 2), [26, 26, 24]); return im; };
  T.keys = () => { const im = img(16, 8); rect(im, 0, 0, 16, 8, [40, 42, 44]); for (let y = 1; y < 8; y += 2) for (let x = 1; x < 15; x += 2) put(im, x, y, [150, 152, 150]); return im; };
  T.pcb = () => {
    const im = img(16, 16), r = rng(33141); grain(im, [36, 110, 60], 33142, 3, 4, 4);
    for (let k = 0; k < 7; k++) { const x = Math.floor(r() * 12), y = Math.floor(r() * 12); rect(im, x, y, 3, 2, [30, 30, 32]); }
    for (let y = 2; y < 16; y += 5) for (let x = 0; x < 16; x++) if (r() < 0.7) put(im, x, y, [190, 170, 90]);
    return im;
  };
  T.ups_disp = () => { const im = img(16, 8); rect(im, 0, 0, 16, 8, [20, 34, 30]); for (let x = 2; x < 12; x++) put(im, x, 3, [120, 230, 160]); for (let x = 2; x < 8; x++) put(im, x, 5, [120, 230, 160]); return im; };
  T.radio = () => { const im = img(16, 8), r = rng(33151); rect(im, 0, 0, 16, 8, [52, 56, 60]); rect(im, 1, 1, 6, 3, [24, 40, 30]); for (let x = 2; x < 6; x++) put(im, x, 2, [120, 230, 120]); for (let x = 9; x < 15; x += 2) put(im, x, 2, [140, 140, 140]); return im; };

  const MATX = {
    pl: { tex: D + 'plastic', P: 16 }, dk: { tex: D + 'dark', P: 16 }, wh: { tex: D + 'white', P: 16 }, bk: { tex: D + 'black', P: 16 },
    vio: { tex: D + 'violet', P: 16 }, eth: { tex: D + 'eth', P: 16 }, sb: { tex: D + 's_blue', P: 16 }, sc: { tex: D + 's_cyan', P: 16 },
    so: { tex: D + 's_orange', P: 16 }, sw: { tex: D + 's_white', P: 16 }, sr: { tex: D + 's_red', P: 16 }, sg: { tex: D + 's_green', P: 16 },
    perf: { tex: D + 'perf', P: 8 }, cgl: { tex: D + 'cglass', P: 16 }, pcb: { tex: D + 'pcb', P: 16 },
  };
  const mk = (p) => FUTK.mk(MATX, p);
  const paint = (root) => FUTK.paintSet(root, D, T);
  const full = (tex) => ({ tex: D + tex, uv: [0, 0, 16, 16] });

  // DIN rail across the back plate (every wall module sits on one).
  function rail(m) {
    P(m, 'backplate', [0, 0, 15.4], [16, 16, 16], 'galv');
    P(m, 'din', [0, 6.6, 14.6], [16, 8.6, 15.4], 'galv');
  }
  // Small status LED looking north.
  const led = (m, n, x, y, z, mat, on) => lamp(m, n, x, y, z, on ? mat : 'l_off', on, 0.35);

  // ---------------------------------------------------------------- PLC rack
  function cpu(mode, forced) {
    const m = mk(D + 'plastic');
    rail(m);
    P(m, 'body', [3, 1, 9.4], [13, 15, 14.6], 'pl');
    P(m, 'front', [3.4, 1.4, 8.4], [12.6, 14.6, 9.4], 'dk');
    P(m, 'label', [3.4, 12.6, 8.3], [12.6, 14.2, 8.4], 'sw');
    m.box('screen', [4.2, 9.4, 8.3], [9.8, 12, 8.4], 'dk', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 8] } } });
    led(m, 'run', 4.6, 8.2, 8.4, 'l_g', mode === 'run');
    led(m, 'stop', 6, 8.2, 8.4, 'l_a', mode === 'stop');
    led(m, 'fault', 7.4, 8.2, 8.4, 'l_r', mode === 'fault');
    led(m, 'force', 8.8, 8.2, 8.4, 'l_a', forced);
    P(m, 'lock', [11, 4.6, 8.2], [13, 7.4, 8.4], 'ci');
    P(m, 'card', [4.2, 3, 8.3], [7, 3.8, 8.4], 'bk');
    m.box('eth', [4.2, 4.6, 8.2], [6, 6.4, 8.4], 'dk', { split: '', face: { north: full('eth') } });
    m.box('eth2', [6.6, 4.6, 8.2], [8.4, 6.4, 8.4], 'dk', { split: '', face: { north: full('eth') } });
    P(m, 'psu', [13, 1, 10], [15.6, 15, 14.6], 'pl');
    led(m, 'psu_led', 14.3, 13, 10, 'l_g', mode !== 'off');
    return m;
  }
  function key() {
    const m = mk('material/brass');
    P(m, 'blade', [11.6, 5.6, 7.4], [12.4, 8, 8.2], 'br');
    P(m, 'bow', [11.2, 7, 7.4], [12.8, 8, 8.2], 'br');
    P(m, 'barrel', [11.4, 4.6, 8], [12.6, 6, 9], 'bright');
    return m;
  }
  // I/O module: narrow housing, coloured stripe by kind, the channel LED field, the terminal block under a cover.
  function io(stripe, fault) {
    const m = mk(D + 'plastic');
    rail(m);
    P(m, 'body', [4, 1, 9.4], [12, 15, 14.6], 'pl');
    P(m, 'front', [4.4, 5, 8.4], [11.6, 14.6, 9.4], 'dk');
    P(m, 'stripe', [4.4, 13.6, 8.3], [11.6, 14.4, 8.4], stripe);
    m.box('leds', [5, 7, 8.3], [11, 13, 8.4], 'dk', { split: '', face: { north: full('leds') } });
    P(m, 'terminals', [4.4, 1.4, 8.6], [11.6, 5, 9.4], 'sg');
    for (let x = 5; x < 11; x += 1.4) P(m, 'screw' + x, [x, 3.4, 8.5], [x + 0.6, 4, 8.6], 'bright');
    for (let x = 5; x < 11; x += 1.4) P(m, 'wire' + x, [x, 0, 9], [x + 0.6, 1.4, 9.6], x % 2.8 < 1.4 ? 'sb' : 'sr');
    led(m, 'bus', 10.6, 6.2, 8.4, fault ? 'l_r' : 'l_g', true);
    return m;
  }
  // Communication module (and the remote I/O adapter with its rotary address switches).
  function comm(bus, link, adapter) {
    const m = mk(D + 'plastic');
    rail(m);
    P(m, 'body', [4, 1, 8.5], [12, 15, 14.6], 'pl');
    P(m, 'front', [4.4, 1.4, 7.5], [11.6, 14.6, 8.5], 'dk');
    P(m, 'stripe', [4.4, 13.6, 7.4], [11.6, 14.4, 7.5], 'vio');
    P(m, 'db9', [5.4, 9, 7], [10.6, 11, 7.5], 'vio');
    m.box('eth', [5.4, 5.6, 7.2], [7.4, 7.6, 7.5], 'dk', { split: '', face: { north: full('eth') } });
    m.box('eth2', [8.6, 5.6, 7.2], [10.6, 7.6, 7.5], 'dk', { split: '', face: { north: full('eth') } });
    led(m, 'bus', 5.4, 12.6, 7.5, bus ? 'l_g' : 'l_r', true);
    led(m, 'link', 7, 12.6, 7.5, 'l_g', link);
    led(m, 'act', 8.6, 12.6, 7.5, 'l_a', link && bus);
    if (adapter) for (const x of [5.4, 8.4]) { m.prism('addr' + x, 'z', [x + 1, 3.4], 1, 7.1, 7.5, 'sw', { split: '', ends: ['north'] }); P(m, 'addr_slot' + x, [x + 0.8, 2.6, 7], [x + 1.2, 4.2, 7.1], 'bk'); }
    else P(m, 'cable', [5.8, 0, 7.6], [6.8, 9, 8.4], 'vio');
    return m;
  }
  function gateway(link, repeater) {
    const m = mk(D + 'plastic');
    rail(m);
    const [x0, x1] = repeater ? [5, 11] : [2.5, 13.5];
    P(m, 'body', [x0, 2, 10], [x1, 14, 14.6], 'pl');
    P(m, 'front', [x0 + 0.4, 2.4, 9], [x1 - 0.4, 13.6, 10], 'pl');
    P(m, 'label', [x0 + 0.4, 11.4, 8.9], [x1 - 0.4, 13, 9], 'sw');
    P(m, 'rs485_a', [x0 + 0.8, 2.6, 8.8], [x0 + 2.6, 5, 9], 'vio');
    if (repeater) P(m, 'rs485_b', [x1 - 2.6, 2.6, 8.8], [x1 - 0.8, 5, 9], 'vio');
    else m.box('eth', [x1 - 3, 2.6, 8.8], [x1 - 1, 4.6, 9], 'dk', { split: '', face: { north: full('eth') } });
    led(m, 'pwr', x0 + 1.4, 9.6, 9, 'l_g', true);
    led(m, 'link', x0 + 2.8, 9.6, 9, 'l_g', link);
    led(m, 'tx', x0 + 4.2, 9.6, 9, 'l_a', link);
    for (const xx of [x0 + 1.2, x1 - 1.8]) P(m, 'wire' + xx, [xx, 0, 9.4], [xx + 0.6, 2.6, 10], 'vio');
    return m;
  }
  // Fieldbus cable: a violet node and the twisted-pair run to each neighbour (the signal cable's layout).
  function cableNode() { const m = mk(D + 'violet'); P(m, 'node', [6.6, 6.6, 6.6], [9.4, 9.4, 9.4], 'vio'); P(m, 'band', [6.4, 7.4, 6.4], [9.6, 8.6, 9.6], 'bk'); return m; }
  function cableSide() { const m = mk(D + 'violet'); P(m, 'run', [7, 7, 0], [9, 9, 6.6], 'vio'); return m; }
  function diode(up) {
    const m = mk(D + 'dark');
    rail(m);
    P(m, 'half_a', [0.5, 1, 10.4], [8, 15, 15.4], 'sr');
    P(m, 'half_b', [8, 1, 10.4], [15.5, 15, 15.4], 'sg');
    P(m, 'face', [1, 1.4, 9.4], [15, 14.6, 10.4], 'dk');
    for (const [x, c] of [[2, 'sr'], [9, 'sg']]) P(m, 'tag' + x, [x, 11.4, 9.3], [x + 5, 13.4, 9.4], c);
    P(m, 'arrow', [4, 7.6, 9.2], [11.6, 8.4, 9.4], 'sw');
    P(m, 'arrow_h', [10.4, 6.8, 9.2], [12, 9.2, 9.4], 'sw', { rot: { axis: 'z', angle: 45, origin: [11.2, 8, 9.3] } });
    m.box('eth_a', [2, 3, 9.2], [4, 5, 9.4], 'dk', { split: '', face: { north: full('eth') } });
    m.box('eth_b', [12, 3, 9.2], [14, 5, 9.4], 'dk', { split: '', face: { north: full('eth') } });
    led(m, 'link', 8, 4, 9.4, 'l_g', up);
    return m;
  }
  function cooler(mode) {
    const m = mk(D + 'white');
    P(m, 'body', [2, 1, 10.4], [14, 15, 16], 'wh');
    P(m, 'top', [2, 11, 9.4], [14, 15, 10.4], 'wh');
    for (const [f, t] of [[[3, 2, 8.6], [13, 2.6, 9.4]], [[3, 11.4 - 0.4, 8.6], [13, 11, 9.4]], [[3, 2.6, 8.6], [3.6, 10.6, 9.4]], [[12.4, 2.6, 8.6], [13, 10.6, 9.4]]]) P(m, 'grille', f, t, 'wh');
    for (const y of [4.4, 6.8, 9]) P(m, 'bar' + y, [3.6, y, 8.5], [12.4, y + 0.3, 8.7], 'wh');
    P(m, 'filter_bg', [3.6, 2.6, 10.3], [12.4, 10.6, 10.4], 'bk');
    m.box('louvre', [3, 11.6, 9.3], [9, 14.4, 9.4], 'wh', { split: '', face: { north: { tex: 'grid_lines/louvre', uv: [0, 0, 16, 8] } } });
    led(m, 'mode', 11.4, 13, 9.4, mode === 'ac' ? 'l_b' : 'l_g', mode !== 'off');
    return m;
  }
  function coolerFan() {
    const m = mk('material/steel');
    m.prism('hub', 'z', [8, 7], 0.9, 8.82, 10.38, 'dk', { split: '', ends: ['north'] });
    for (const a of [0, 45, -45, 90]) {
      const f = a === 90 ? [7.5, 3.6, 9.4] : [4.6, 6.5, 9.4], t = a === 90 ? [8.5, 10.4, 9.8] : [11.4, 7.5, 9.8];
      P(m, 'blade' + a, f, t, 'st', { rot: a && a !== 90 ? { axis: 'z', angle: a, origin: [8, 7, 9.6] } : undefined });
    }
    return m;
  }
  function telemetry(link) {
    const m = mk('plant/light');
    for (const [x, z] of [[0.6, 2.6], [14.4, 2.6], [0.6, 13.4], [14.4, 13.4]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 7, z + 1], 'frame');
    P(m, 'table', [0, 7, 2], [16, 8, 15], 'light');
    P(m, 'rack', [2, 8, 5], [14, 17, 14], 'bk');
    for (const y of [9, 12.2]) m.box('unit' + y, [2.4, y, 4.8], [13.6, y + 2.8, 5], 'dk', { split: '', face: { north: { tex: D + 'radio', uv: [0, 0, 16, 8] } } });
    led(m, 'link', 12, 16, 5, 'l_g', link);
    led(m, 'rx', 10.6, 16, 5, 'l_a', link);
    P(m, 'handle_l', [2, 9, 4.4], [2.6, 15, 5], 'bright'); P(m, 'handle_r', [13.4, 9, 4.4], [14, 15, 5], 'bright');
    P(m, 'coax', [12, 17, 12], [13, 18, 13], 'bk');
    P(m, 'coax_run', [12, 17, 13], [13, 18, 15], 'bk');
    return m;
  }

  // ---------------------------------------------------------------- cabinets and racks
  function historian(busy) {
    const m = mk(D + 'black');
    P(m, 'rack', [0.5, 0, 0.6], [15.5, 32, 16], 'bk');
    P(m, 'plinth', [0.5, 0, -0.1], [15.5, 1.4, 0.6], 'bk');
    for (const [y0, n] of [[18, 'a'], [6, 'b']]) {
      m.box('array_' + n, [1.6, y0, 0.2], [14.4, y0 + 9, 0.6], 'dk', { split: '', face: { north: full('disks') } });
      for (let k = 0; k < 4; k++) for (let row = 0; row < 4; row++) if (busy && (k + row + y0) % 3 !== 0) P(m, `glow_act_${n}_${k}_${row}`, [1.6 + k * 3.2 + 2.2, y0 + 9 - row * 2.25 - 1.7, 0.1], [1.6 + k * 3.2 + 2.6, y0 + 9 - row * 2.25 - 1.3, 0.2], 'l_g');
    }
    P(m, 'switch', [1.6, 28.6, 0.2], [14.4, 30.4, 0.6], 'dk');
    for (let x = 2.4; x < 14; x += 1.2) P(m, 'port' + x, [x, 29.2, 0.1], [x + 0.8, 29.8, 0.2], 'bk');
    m.box('vent', [1.6, 2, 0.2], [14.4, 5, 0.6], 'bk', { split: '', face: { north: { tex: D + 'perf', uv: [0, 0, 16, 6] } } });
    led(m, 'pwr', 13.4, 31, 0.6, 'l_g', true);
    return m;
  }
  function ups(state) {
    const m = mk('m3grid/tank');
    cabinet(m, 'cab', [0, 0, 0.4], [16, 31, 16], 'tank', { plate: true });
    m.box('disp', [3, 22, 0], [9, 25, 0.2], 'dk', { split: '', face: { north: { tex: D + 'ups_disp', uv: [0, 0, 16, 8] } } });
    const lit = state === 'online' ? 5 : state === 'battery' ? 3 : 0;
    for (let i = 0; i < 5; i++) P(m, (i < lit ? 'glow_' : '') + 'charge' + i, [3 + i * 1.2, 20, 0.1], [4 + i * 1.2, 21, 0.2], i < lit ? (state === 'battery' ? 'l_a' : 'l_g') : 'l_off');
    led(m, 'online', 11, 24, 0.2, 'l_g', state === 'online');
    led(m, 'battery', 12.4, 24, 0.2, 'l_a', state === 'battery');
    led(m, 'fault', 13.8, 24, 0.2, 'l_r', state === 'fault');
    m.box('vent', [3, 2, 0], [13, 8, 0.2], 'tank', { split: '', face: { north: { tex: D + 'perf', uv: [0, 0, 16, 10] } } });
    return m;
  }
  function trainingCab(running) {
    const m = mk('m3grid/tank');
    cabinet(m, 'cab', [1, 0, 0.4], [15, 31, 15], 'tank', { plate: true });
    m.box('perf', [3, 8, 0], [13, 24, 0.2], 'tank', { split: '', face: { north: { tex: D + 'perf', uv: [0, 0, 16, 16] } } });
    for (let i = 0; i < 4; i++) led(m, 'lamp' + i, 4 + i * 2.6, 27, 0.2, i === 3 ? 'l_a' : 'l_g', running);
    P(m, 'top_vent', [3, 31, 3], [13, 31.4, 13], 'tank');
    return m;
  }
  // Operator desk 2x1 with monitors (screens are drawn by the client), keyboard, and the instructor's pause button.
  function desk(m, w) {
    for (const x of [0.4, w - 1.4]) P(m, 'side' + x, [x, 0, 3], [x + 1, 9, 13], 'dk');
    P(m, 'top', [0, 9, 2], [w, 10, 14], 'light');
    P(m, 'modesty', [1.4, 2, 12], [w - 1.4, 9, 13], 'dk');
    m.box('kbd', [w / 2 - 4, 10, 3.6], [w / 2 + 4, 10.4, 6.4], 'dk', { split: '', face: { up: { tex: D + 'keys', uv: [0, 0, 16, 8] } } });
  }
  function monitor(m, n, x0, x1, y0, y1, z) {
    P(m, n + '_stand', [(x0 + x1) / 2 - 0.6, 10, z + 0.6], [(x0 + x1) / 2 + 0.6, y0, z + 1.2], 'bk');
    P(m, n + '_foot', [(x0 + x1) / 2 - 2, 10, z - 0.4], [(x0 + x1) / 2 + 2, 10.3, z + 2], 'bk');
    m.box(n, [x0, y0, z], [x1, y1, z + 0.6], 'bk', { split: '', face: { north: { tex: 'electronics/screen', uv: [0, 0, 16, 10] } } });
  }
  function instructor(active) {
    const m = mk('plant/light');
    desk(m, 32);
    monitor(m, 'mon_a', 4, 14, 12, 19.6, 9);
    monitor(m, 'mon_b', 18, 28, 12, 19.6, 9);
    P(m, 'pause_base', [26, 10, 4], [29, 10.6, 7], 'yel');
    m.cylY((active ? 'glow_' : '') + 'pause', [27.5, 5.5], 1, 10.6, 11.4, active ? 'l_r' : 'red', { cap: active ? 'grid_lines/lamp_red' : 'material/paint_red' });
    P(m, 'phone', [2, 10, 4], [5, 11, 7], 'bk');
    return m;
  }
  function workstation() {
    const m = mk('plant/light');
    desk(m, 32);
    monitor(m, 'mon_a', 3, 15, 12, 19.6, 9);
    monitor(m, 'mon_b', 17, 29, 12, 19.6, 9);
    P(m, 'mouse', [22, 10, 4.4], [23, 10.4, 5.8], 'bk');
    P(m, 'pc', [26, 0, 4], [30, 8, 11], 'bk');
    led(m, 'pc_led', 28, 7, 4, 'l_g', true);
    return m;
  }
  function simConsole(active) {
    const m = mk('plant/light');
    P(m, 'base', [0, 0, 2], [16, 8, 16], 'light');
    P(m, 'desk', [0, 8, 0], [16, 9.4, 16], 'light', { rot: undefined });
    P(m, 'panel', [0, 9.4, 6], [16, 15.5, 16], 'light');
    m.box('stripe', [0.5, 6.4, 1.9], [15.5, 7.8, 2], 'light', { split: '', face: { north: { tex: D + 'trainer', uv: [0, 0, 16, 4] } } });
    P(m, 'slope', [0.6, 9.4, 5.4], [15.4, 15, 6], 'dk');
    for (let x = 2; x < 14; x += 2) P(m, 'key' + x, [x, 9.4, 1], [x + 1, 9.8, 2], x % 4 ? 'sg' : 'sr');
    led(m, 'active', 13.6, 13.6, 5.4, 'l_a', active);
    gauge(m, 'g1', 4, 12.2, 5.4, 1.2);
    gauge(m, 'g2', 8, 12.2, 5.4, 1.2);
    return m;
  }
  // Core cartogram panel: dark glass face in a RAL 7035 frame; the frame keeps only the free edges of a group.
  function cartogram(part, powered) {
    const m = mk('m6auto/cglass');
    P(m, 'back', [0, 0, 13.4], [16, 16, 16], 'light');
    P(m, 'glass', [0, 0, 12.8], [16, 16, 13.4], 'cgl');
    const top = part === 'single' || part === 'edge' || part === 'corner', left = part === 'single' || part === 'corner';
    const right = part === 'single', bottom = part === 'single';
    if (top) P(m, 'frame_t', [0, 14.6, 11.4], [16, 16, 12.8], 'light');
    if (bottom) P(m, 'frame_b', [0, 0, 11.4], [16, 1.4, 12.8], 'light');
    if (left) P(m, 'frame_w', [0, 0, 11.4], [1.4, 16, 12.8], 'light');
    if (right) P(m, 'frame_e', [14.6, 0, 11.4], [16, 16, 12.8], 'light');
    if (part === 'single') {
      m.box(powered ? 'glow_legend' : 'legend', [2, 1.6, 12.7], [14, 2.6, 12.8], 'light', { split: '', face: { north: { tex: D + 'legend', uv: [0, 0, 16, 4] } } });
      led(m, 'power', 13.4, 15.3, 11.4, 'l_g', powered);
    }
    return m;
  }

  // ---------------------------------------------------------------- electronics, piping, mast
  function assembler(working) {
    const m = mk('plant/yellow');
    for (const [x, z] of [[1, 1], [14, 1], [1, 14], [14, 14]]) P(m, `leg_${x}_${z}`, [x, 0, z], [x + 1, 6, z + 1], 'frame');
    P(m, 'table', [0, 6, 0], [16, 7, 16], 'wh');
    P(m, 'board', [3, 7, 3], [13, 7.3, 13], 'pcb');
    for (const x of [0.4, 14.6]) P(m, 'col' + x, [x, 7, 1], [x + 1, 15, 2], 'yel');
    P(m, 'gantry', [0.4, 14, 1], [15.6, 15, 15], 'yel');
    for (let i = 0; i < 4; i++) P(m, 'feeder' + i, [1 + i * 3.6, 7, -0.6], [3.6 + i * 3.6, 9, 2.4], 'sw');
    P(m, 'tray', [12.6, 7, 13], [15.6, 8, 15.6], 'bk');
    led(m, 'run', 14.8, 13.4, 1, 'l_g', working);
    return m;
  }
  function assemblerHead() {
    const m = mk('material/steel');
    P(m, 'carriage', [6, 13, 6], [10, 14.5, 10], 'yel');
    P(m, 'head', [7, 10, 7], [9, 13, 9], 'st');
    P(m, 'nozzle', [7.7, 9, 7.7], [8.3, 10, 8.3], 'bright');
    return m;
  }
  // ESD valve on a gas pipe along x: flanged ball valve, yellow quarter-turn actuator on a bracket, solenoid
  // pilot on the side, limit switch box with the position lamp (green open, red closed, amber travelling).
  function esd(pos) {
    const m = mk('plant/yellow');
    m.prism('pipe', 'x', [8, 8], 2, -0.01, 16.01, 'p_gas', { split: '' });
    for (const x of [3, 12.4]) m.prism('flange' + x, 'x', [8, 8], 3.6, x, x + 0.6, 'ci', { split: '', ends: ['west', 'east'] });
    m.prism('body', 'x', [8, 8], 3.2, 3.6, 12.4, 'ci', { split: '' });
    P(m, 'bonnet', [6.6, 11, 6.6], [9.4, 12, 9.4], 'ci');
    P(m, 'bracket', [5, 16, 5], [11, 17, 11], 'ci');
    P(m, 'actuator', [3, 17, 4], [13, 22, 12], 'yel');
    m.prism('spring_can', 'x', [19.5, 8], 2.2, 13, 16, 'yel', { split: '', ends: ['east'] });
    P(m, 'solenoid', [0.6, 18, 9], [3, 21, 11.4], 'blue');
    P(m, 'pilot_tube', [1.4, 21, 10], [2, 22.6, 10.6], 'br');
    P(m, 'pilot_run', [2, 22, 10], [4, 22.6, 10.6], 'br');
    P(m, 'switchbox', [5.4, 22, 5.4], [10.6, 24, 10.6], 'dk');
    const mat = pos === 'open' ? 'l_g' : pos === 'closed' ? 'l_r' : 'l_a';
    lamp(m, 'pos', 8, 23, 5.4, mat, true, 0.6);
    return m;
  }
  function esdStem() {
    const m = mk('plant/yellow');
    m.cylY('stem', [8, 8], 0.75, 12, 16, 'bright', { cap: 'material/bright_steel' });
    P(m, 'indicator', [5, 14.6, 7.25], [11, 15.4, 8.75], 'yel');
    P(m, 'tip', [10, 14.4, 7.6], [11, 15.6, 8.4], 'red');
    return m;
  }
  function mastBase() {
    const m = mk('disconnector/galvanised');
    P(m, 'found', [3, 0, 3], [13, 2, 13], 'conc');
    P(m, 'plate', [4.6, 2, 4.6], [11.4, 2.6, 11.4], 'galv');
    for (const [x, z] of [[5, 5], [10.4, 5], [5, 10.4], [10.4, 10.4]]) P(m, `bolt_${x}_${z}`, [x, 2.6, z], [x + 0.6, 3.2, z + 0.6], 'st');
    m.cylY('tube', [8, 8], 1.6, 2.6, 16, 'galv');
    P(m, 'earth', [7.6, 0, 3.6], [8.4, 3, 4.4], 'earth');
    P(m, 'coax', [9.6, 2.6, 7.6], [10.4, 16, 8.4], 'bk');
    return m;
  }
  function mastSection() {
    const m = mk('disconnector/galvanised');
    m.cylY('tube', [8, 8], 1.6, 0, 16, 'galv');
    for (let y = 2; y < 16; y += 4) P(m, 'peg' + y, [6, y, 7.7], [10, y + 0.6, 8.3], 'galv', { rot: { axis: 'y', angle: (y % 8) ? 45 : -45, origin: [8, 8, 8] } });
    P(m, 'coax', [9.6, 0, 7.6], [10.4, 16, 8.4], 'bk');
    return m;
  }
  function mastTop() {
    const m = mk('grid_lines/alu');
    m.cylY('tube', [8, 8], 1.6, 0, 8, 'galv', { cap: 'disconnector/galvanised' });
    P(m, 'clamp', [6, 6, 6], [10, 8, 10], 'galv');
    m.cylY('whip_base', [8, 8], 0.8, 8, 12, 'alu');
    m.cylY('whip', [8, 8], 0.35, 12, 31, 'alu', { cap: 'grid_lines/alu' });
    for (const a of [0, 90, 180, 270]) m.postY('radial' + a, [8, 8], 2.4, a, 0.4, 9, 9.4, 'alu', { split: '' });
    P(m, 'beacon', [7.4, 31, 7.4], [8.6, 32, 8.6], 'l_r');
    return m;
  }
  function yagi() {
    const m = mk('grid_lines/alu');
    P(m, 'boom', [7.6, 10, 0.5], [8.4, 10.8, 15.5], 'alu');
    P(m, 'clamp', [6.8, 9, 6.8], [9.2, 12, 9.2], 'galv');
    for (const [z, w] of [[1, 3.6], [4, 4], [7, 4.4], [11, 4.8], [14.6, 5]]) P(m, 'el' + z, [8 - w, 10.2, z], [8 + w, 10.6, z + 0.4], 'alu');
    return m;
  }

  const B = (model, cat, java) => ({ model, cat: cat || 'automation', java });
  const BUILDS = {
    plc_controller: B(() => cpu('off', false)), plc_controller_run: B(() => cpu('run', false)), plc_controller_stop: B(() => cpu('stop', false)),
    plc_controller_fault: B(() => cpu('fault', false)), plc_controller_forced: B(() => cpu('run', true)),
    'plc_controller/key': B(key, 'automation', 'plc_controller/partial/key'),
    plc_io_ai: B(() => io('sb', false)), plc_io_ai_fault: B(() => io('sb', true)), plc_io_ao: B(() => io('sc', false)), plc_io_ao_fault: B(() => io('sc', true)),
    plc_io_di: B(() => io('sw', false)), plc_io_di_fault: B(() => io('sw', true)), plc_io_do: B(() => io('so', false)), plc_io_do_fault: B(() => io('so', true)),
    plc_comm: B(() => comm(true, true, false)), plc_comm_fault: B(() => comm(false, true, false)), plc_comm_link_down: B(() => comm(true, false, false)),
    remote_io_adapter: B(() => comm(true, true, true)), remote_io_adapter_fault: B(() => comm(false, true, true)),
    fieldbus_gateway: B(() => gateway(true, false)), fieldbus_gateway_link_down: B(() => gateway(false, false)),
    fieldbus_repeater: B(() => gateway(true, true)), fieldbus_repeater_link_down: B(() => gateway(false, true)),
    fieldbus_cable: B(cableNode), fieldbus_cable_side: B(cableSide),
    data_diode: B(() => diode(true)), data_diode_link_down: B(() => diode(false)),
    cabinet_cooler: B(() => cooler('off')), cabinet_cooler_fan: B(() => cooler('fan')), cabinet_cooler_ac: B(() => cooler('ac')),
    'cabinet_cooler/fan': B(coolerFan, 'automation', 'cabinet_cooler/partial/fan'),
    telemetry_master: B(() => telemetry(true)), telemetry_master_link_down: B(() => telemetry(false)),
    historian_server: B(() => historian(false)), historian_server_busy: B(() => historian(true)),
    ups_cabinet: B(() => ups('online')), ups_cabinet_battery: B(() => ups('battery')), ups_cabinet_fault: B(() => ups('fault')),
    training_simulator: B(() => trainingCab(false), 'control'), training_simulator_running: B(() => trainingCab(true), 'control'),
    instructor_station: B(() => instructor(false), 'control'), instructor_station_active: B(() => instructor(true), 'control'),
    simulation_workstation: B(workstation),
    simulator_console: B(() => simConsole(false), 'control'), simulator_console_active: B(() => simConsole(true), 'control'),
    core_cartogram: B(() => cartogram('single', false), 'control'), core_cartogram_powered: B(() => cartogram('single', true), 'control'),
    core_cartogram_edge: B(() => cartogram('edge', false), 'control'), core_cartogram_corner: B(() => cartogram('corner', false), 'control'),
    core_cartogram_center: B(() => cartogram('center', false), 'control'),
    component_assembler: B(() => assembler(false), 'electronics'), component_assembler_working: B(() => assembler(true), 'electronics'),
    'component_assembler/head': B(assemblerHead, 'electronics', 'component_assembler/partial/head'),
    esd_valve: B(() => esd('open'), 'piping'), esd_valve_closed: B(() => esd('closed'), 'piping'), esd_valve_mid: B(() => esd('mid'), 'piping'),
    'esd_valve/stem': B(esdStem, 'piping', 'esd_valve/partial/stem'),
    radio_antenna: B(mastBase), radio_antenna_section: B(mastSection), radio_antenna_top: B(mastTop),
    'radio_antenna/yagi': B(yagi, 'automation', 'radio_antenna/partial/yagi'),
  };
  window.M6A = FUTK.machine(BUILDS, paint);
})();
