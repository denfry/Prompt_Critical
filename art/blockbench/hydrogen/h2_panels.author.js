// Panel boards of the hydrogen machines (b0, branch rs-hydrogen), in the style of the machine-shop boards
// (assembly_stand.author.js): enamel board in a brass frame, an etched ring behind every dial, a dark slot behind every
// level glass with a colour tab of the medium (hydrogen dark green, oxygen blue, after the Russian cylinder and pipe
// colours), troughs behind bars, windows behind read-outs, bezels behind lamps (the gas alarm in hazard stripes) and a
// recessed strip under the buttons. PanelLayout draws every element over it; the layout table below is the single
// source of the coordinates, and paint() also writes them to h2_panels.md next to this script.
//   h2_electrolyser.png  220 x 170     h2_compressor.png  220 x 130
//   h2_storage.png       220 x 120     (shared by the tube bank and the gasholder)
//   h2_fuel_cell.png     220 x 136
// Run headless: node art/tools/pcart_headless.js <repo> hydrogen/h2_panels.author.js H2P --paint
(function () {
  const path = require('path'), fs = require('fs');
  const { rng, img, put, add, mix, grain } = PCART;

  // kind: dial (x, y, size 64 large / 48 medium), glass (x, y, h; 12 wide), bar (x, y, w; 8 high, label 10 above),
  // readout (x, y, w; 13 high), lamp (x, y; 14 x 14, label to the right), button (x, y, w; 16 high).
  const H2 = [44, 96, 60], O2 = [52, 100, 170];
  const PANELS = {
    h2_electrolyser: { w: 220, h: 170, base: [70, 78, 82], seed: 63101, els: [
      ['dial', 'DC current', 10, 24, 64], ['dial', 'DC voltage', 84, 32, 48],
      ['glass', 'H2 separator level', 150, 26, 64, H2], ['glass', 'O2 separator level', 182, 26, 64, O2],
      ['bar', 'stack temperature', 12, 112, 128], ['readout', 'lye concentration', 12, 126, 128],
      ['lamp', 'H2 in O2 alarm', 150, 110, 'hazard'],
      ['button', 'start', 12, 146, 60], ['button', 'stop', 78, 146, 60], ['button', 'purge', 144, 146, 64],
    ] },
    h2_compressor: { w: 220, h: 130, base: [72, 80, 76], seed: 63111, els: [
      ['dial', 'suction pressure', 14, 26, 48], ['dial', 'discharge pressure', 78, 26, 48],
      ['bar', 'stage temperature', 138, 40, 70], ['lamp', 'run', 138, 58],
      ['readout', 'status', 12, 88, 196],
      ['button', 'start', 12, 108, 96], ['button', 'stop', 112, 108, 96],
    ] },
    h2_storage: { w: 220, h: 120, base: [66, 82, 70], seed: 63121, els: [
      ['dial', 'pressure', 12, 24, 64],
      ['bar', 'stored Nm3', 88, 40, 120], ['lamp', 'overpressure', 88, 54, 'hazard'],
      ['readout', 'status', 88, 76, 120], ['button', 'vent valve', 88, 96, 120],
    ] },
    h2_fuel_cell: { w: 220, h: 136, base: [70, 76, 84], seed: 63131, els: [
      ['dial', 'H2 pressure in', 12, 26, 48], ['dial', 'stack voltage', 70, 26, 48],
      ['bar', 'AC power out', 128, 36, 80], ['readout', 'water out', 128, 50, 80], ['lamp', 'fault', 128, 68],
      ['readout', 'status', 12, 94, 196],
      ['button', 'run', 12, 114, 96], ['button', 'stop', 112, 114, 96],
    ] },
  };

  function board(p) {
    const W = p.w, H = p.h, im = img(W, H), r = rng(p.seed), base = p.base;
    grain(im, base, p.seed + 1, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add([170, 134, 70], (d === 0 ? -50 : d === 1 ? 30 : d === 3 ? -30 : 0) + (r() - 0.5) * 8));
    }
    const inset = (x0, y0, x1, y1, c, k = 20) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const e = x === x0 || y === y0 ? -k : x === x1 - 1 || y === y1 - 1 ? k * 0.8 : 0;
        put(im, x, y, add(c, e + (r() - 0.5) * 3));
      }
    };
    for (const [x, y] of [[6, 6], [W - 8, 6], [6, H - 8], [W - 8, H - 8]]) {
      put(im, x, y, [110, 112, 108]); put(im, x + 1, y, [200, 202, 196]); put(im, x, y + 1, [200, 202, 196]); put(im, x + 1, y + 1, [80, 82, 78]);
    }
    // the recessed button strip runs the full width under the bottom row of buttons
    const btn = p.els.filter((e) => e[0] === 'button');
    if (btn.length) {
      const y0 = Math.min(...btn.map((e) => e[3])), x0 = Math.min(...btn.map((e) => e[2])), x1 = Math.max(...btn.map((e) => e[2] + e[4]));
      inset(x0 - 4, y0 - 4, x1 + 4, Math.min(y0 + 20, H - 4), add(base, -10), 16);
    }
    for (const e of p.els) {
      const [kind, , x, y, a, b] = e;
      if (kind === 'dial') {
        const s = a, cx = x + s / 2, cy = y + s / 2, R = s / 2 + 3;
        for (let t = 0; t < 360; t += 0.5) {
          const q = t * Math.PI / 180;
          put(im, cx + R * Math.cos(q), cy + R * Math.sin(q), add(base, -40));
          put(im, cx + (R + 1) * Math.cos(q) + 0.5, cy + (R + 1) * Math.sin(q) + 0.5, add(base, 50));
        }
      } else if (kind === 'glass') {
        inset(x - 2, y - 2, x + 14, y + a + 2, [30, 32, 34], 16);
        for (let xx = x - 2; xx < x + 14; xx++) for (const yy of [y - 5, y - 4]) put(im, xx, yy, yy === y - 5 ? add(b, 30) : b);
      } else if (kind === 'bar') {
        inset(x - 3, y - 12, x + a + 3, y + 11, add(base, -8), 14);
      } else if (kind === 'readout') {
        inset(x - 2, y - 2, x + a + 2, y + 15, add(base, -14), 14);
      } else if (kind === 'lamp') {
        if (a === 'hazard') for (let yy = y - 3; yy < y + 17; yy++) for (let xx = x - 3; xx < x + 17; xx++) {
          if (xx >= x - 1 && xx < x + 15 && yy >= y - 1 && yy < y + 15) continue;
          put(im, xx, yy, Math.floor((xx + yy) / 3) % 2 ? [30, 30, 32] : [220, 178, 44]);
        }
        inset(x - 1, y - 1, x + 15, y + 15, [36, 38, 40], 14);
      }
    }
    return im;
  }

  function doc() {
    const lines = ['# Hydrogen panel boards', '',
      'Generated by `h2_panels.author.js` (`node art/tools/pcart_headless.js . hydrogen/h2_panels.author.js H2P --paint`).',
      'Coordinates are the top-left of each PanelLayout element, in px from the top-left of the board. Sizes: dial 64 (large) or',
      '48 (medium); glass 12 wide; bar 8 high with its label 10 px above; readout 13 high; lamp 14 x 14 with its label to the',
      'right; button 16 high. The title plate is drawn by the code at y 5.', ''];
    for (const [id, p] of Object.entries(PANELS)) {
      lines.push(`## ${id}: textures/gui/panel/${id}.png, ${p.w} x ${p.h}`, '', '| element | kind | x | y | size |', '|---|---|---|---|---|');
      for (const [kind, name, x, y, a] of p.els) {
        const size = kind === 'dial' ? (a === 64 ? 'large' : 'medium') : kind === 'glass' ? `h ${a}` : kind === 'lamp' ? '14 x 14' : `w ${a}`;
        lines.push(`| ${name} | ${kind} | ${x} | ${y} | ${size} |`);
      }
      lines.push('');
    }
    lines.push('`h2_storage` serves both the tube bank and the gasholder.', '');
    return lines.join('\n');
  }

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/gui/panel');
    for (const [id, p] of Object.entries(PANELS)) PCART.writePng(board(p), path.join(dir, id + '.png'));
    fs.writeFileSync(path.join(root, 'art/blockbench/hydrogen/h2_panels.md'), doc());
    return Object.keys(PANELS).length;
  }

  window.H2P = { paint, ids: [], exportAll: () => ({}), PANELS };
})();
