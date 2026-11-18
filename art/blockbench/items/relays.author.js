// Protection relay item icons, hand-painted at 16 px (replaces the identical round icons of tex_grid.py RELAYS).
// Each relay shows its protection function code (ANSI / GOST style) in its marking colour, drawn in the case of its
// generation: A1 the round electromechanical case (RT-80 type) with the induction disc behind glass, A2 the static
// relay module on its board, A3 the digital terminal with its screen and keypad.
// Load inside Blockbench desktop after pcart.js:
//   eval(require('fs').readFileSync('<repo>/art/blockbench/lib/pcart.js', 'utf8'))
//   eval(require('fs').readFileSync('<repo>/art/blockbench/items/relays.author.js', 'utf8'))
//   RELAYICONS.paint('<repo>')
// The paths are listed in art/hand_textures.json so build.py does not overwrite them.
(function () {
  const path = require('path');
  const { img, writePng } = PCART;
  const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
  const P = (im, x, y, c, a) => { if (x < 0 || y < 0 || x > 15 || y > 15) return; const i = (y * 16 + x) * 4; im.d[i] = c[0]; im.d[i + 1] = c[1]; im.d[i + 2] = c[2]; im.d[i + 3] = a === undefined ? 255 : a; };
  const R = (im, x0, y0, x1, y1, c) => { for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) P(im, x, y, c); };
  // 3x5 glyphs
  const G = {
    0: ['111', '101', '101', '101', '111'], 1: ['010', '110', '010', '010', '111'], 2: ['111', '001', '111', '100', '111'],
    3: ['111', '001', '011', '001', '111'], 4: ['101', '101', '111', '001', '001'], 5: ['111', '100', '111', '001', '111'],
    7: ['111', '001', '010', '010', '010'], 8: ['111', '101', '111', '101', '111'], 9: ['111', '101', '111', '001', '111'],
    B: ['110', '101', '110', '101', '110'], F: ['111', '100', '110', '100', '100'], A: ['010', '101', '111', '101', '101'],
    Ch: ['101', '101', '111', '001', '001'],
  };
  const text = (im, s, x, y, c) => { for (const ch of s) { const g = G[ch]; for (let j = 0; j < 5; j++) for (let i = 0; i < 3; i++) if (g[j][i] === '1') P(im, x + i, y + j, c); x += 4; } };
  const RELAYS = {
    relay_overcurrent: ['#c0392b', ['a1', 'a2', 'a3'], ['5', '1']],
    relay_differential: ['#e0b51e', ['a1', 'a2', 'a3'], ['8', '7']],
    relay_distance: ['#2f6fb3', ['a2', 'a3'], ['2', '1']],
    relay_frequency: ['#2b8a3e', ['a1', 'a2', 'a3'], ['8', '1']],
    relay_voltage: ['#8e44ad', ['a1', 'a2', 'a3'], ['5', '9']],
    relay_reverse_power: ['#d35400', ['a1', 'a2', 'a3'], ['3', '2']],
    relay_loss_of_field: ['#16a085', ['a2', 'a3'], ['4', '0']],
    relay_out_of_step: ['#7f8c8d', ['a2', 'a3'], ['7', '8']],
    relay_sync_check: ['#f1eee4', ['a1', 'a2', 'a3'], ['2', '5']],
    relay_breaker_failure: ['#c0392b', ['a1', 'a2', 'a3'], ['B', 'F']],
    relay_autoreclose: ['#27ae60', ['a1', 'a2', 'a3'], ['7', '9']],
    relay_ufls: ['#e67e22', ['a1', 'a2', 'a3'], ['A', 'Ch']],
    relay_tap_changer: ['#3498db', ['a1', 'a2', 'a3'], ['9', '0']],
    relay_thermal: ['#e74c3c', ['a1', 'a2', 'a3'], ['4', '9']],
  };
  const OUT = [20, 20, 22], DK = [40, 40, 44], MID = [70, 72, 78], HI = [130, 134, 140], GLASS = [150, 170, 180], WHITE = [232, 230, 220];

  // A1: round black bakelite case on a square base with the glass window over the aluminium disc, the nameplate
  // with the code below it.
  function a1(col, code) {
    const im = img(16, 16);
    for (let y = 0; y < 11; y++) for (let x = 0; x < 16; x++) {
      const d = Math.hypot(x - 7.5, y - 5.2);
      if (d < 5.8) P(im, x, y, d > 4.9 ? OUT : d > 4 ? DK : (x + y < 10 ? [190, 206, 214] : GLASS));
    }
    for (let x = 5; x <= 10; x++) P(im, x, 6, [200, 202, 206]);
    P(im, 7, 5, [220, 40, 30]); P(im, 8, 5, [220, 40, 30]);
    R(im, 2, 10, 13, 10, col);
    R(im, 2, 11, 13, 15, OUT); R(im, 3, 11, 12, 15, WHITE);
    text(im, code, 4, 11, [20, 20, 20]);
    return im;
  }
  // A2: static relay module: a dark green board with components, a front plate in the marking colour carrying the
  // code, a status LED.
  function a2(col, code) {
    const im = img(16, 16);
    R(im, 1, 2, 14, 14, OUT); R(im, 2, 3, 13, 13, [34, 90, 52]);
    for (const [x, y] of [[3, 4], [6, 4], [9, 5], [11, 4], [4, 7]]) R(im, x, y, x + 1, y, [20, 20, 22]);
    R(im, 2, 8, 13, 13, hex(col)); R(im, 2, 8, 13, 8, OUT);
    text(im, code, 4, 9, col === '#f1eee4' ? [20, 20, 20] : [245, 245, 240]);
    P(im, 12, 4, [90, 240, 110]);
    R(im, 0, 5, 0, 7, HI); R(im, 15, 5, 15, 7, HI);
    return im;
  }
  // A3: digital terminal: grey case, green LCD with the code, a row of keys, the coloured function strip.
  function a3(col, code) {
    const im = img(16, 16);
    R(im, 1, 1, 14, 14, OUT); R(im, 2, 2, 13, 13, [176, 180, 182]);
    R(im, 3, 3, 12, 9, [20, 30, 24]); text(im, code, 4, 4, [120, 240, 140]);
    for (let i = 0; i < 4; i++) P(im, 3 + i * 3, 11, i === 3 ? [220, 40, 30] : MID);
    R(im, 2, 13, 13, 13, hex(col));
    P(im, 13, 3, [90, 240, 110]);
    return im;
  }
  function paint(root) {
    const out = [];
    for (const [id, [col, gens, code]] of Object.entries(RELAYS)) {
      gens.forEach((g, i) => {
        const name = i === 0 ? id : `${id}_${g}`;
        const im = g === 'a1' ? a1(hex(col), code) : g === 'a2' ? a2(col, code) : a3(col, code);
        writePng(im, path.join(root, PCART.ASSETS, 'textures/item', name + '.png'));
        out.push('item/' + name + '.png');
      });
    }
    return out;
  }
  window.RELAYICONS = { paint };
})();
