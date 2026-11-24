// Panel boards of the I2 oilfield machines (b0), same pipeline as hydrogen/h2_panels.author.js and
// core/reactor_panels.author.js: one layout table drives the painted board and the coordinate table oilfield_panels.md
// next to this script; PanelLayout draws every element. Style: 1950s Soviet oilfield instrument boards, grey-blue
// enamel in a riveted steel frame, thick black bezels with a chrome lip round the dials (the drawworks weight
// indicator gets the wide bezel of a Martin-Decker type drum gauge), colour tabs over the level glasses by medium.
//   drawworks.png  mud_pump.png  pumpjack.png  separator.png  oil_tank.png
//   mainline_pump.png  (shared by the mainline pump and the line heater)
// Run headless: node art/tools/pcart_headless.js <repo> oil_production/oilfield_panels.author.js OFP --paint
(function () {
  const path = require('path'), fs = require('fs');
  const { rng, img, put, add, grain } = PCART;

  // kinds: dial (x, y, 64 large / 48 medium), glass (x, y, h, tab colour; 12 wide), bar (x, y, w), readout (x, y, w),
  // lamp (x, y [, 'hazard']), button (x, y, w), area (x, y, w, h: blank chart paper the code draws into).
  const OIL = [150, 110, 40], WATER = [60, 110, 170], MUD = [120, 96, 70];
  const PANELS = {
    // the drawworks and the mud pump have no start keys: the drawworks follows the driller's console, the pump its shaft
    drawworks: { w: 220, h: 124, seed: 65101, els: [
      ['dial', 'hook load (weight indicator)', 12, 24, 64, 'wide'],
      ['bar', 'block height', 92, 40, 116], ['lamp', 'brake', 92, 56],
      ['readout', 'status', 12, 102, 196],
    ] },
    mud_pump: { w: 220, h: 116, seed: 65111, els: [
      ['dial', 'standpipe pressure', 12, 26, 48], ['readout', 'strokes per minute', 72, 40, 96], ['lamp', 'running', 72, 58],
      ['glass', 'mud pit level', 184, 26, 64, MUD],
      ['readout', 'status', 12, 94, 160],
    ] },
    pumpjack: { w: 220, h: 134, seed: 65121, els: [
      ['area', 'dynamometer card', 12, 24, 100, 50],
      ['readout', 'SPM', 124, 28, 84], ['bar', 'polished-rod load', 124, 58, 84],
      ['button', 'wellhead valve', 12, 110, 96], ['button', 'echo sounder', 112, 110, 96],
    ] },
    separator: { w: 220, h: 104, seed: 65131, els: [
      ['dial', 'BS&W in the oil', 12, 26, 48],
      ['glass', 'oil level', 82, 26, 56, OIL], ['glass', 'water level', 108, 26, 56, WATER],
      ['readout', 'status (gas to flare)', 132, 30, 76], ['lamp', 'carryover', 132, 52],
    ] },
    mainline_pump: { w: 220, h: 130, seed: 65141, els: [
      ['dial', 'suction pressure', 12, 26, 48], ['dial', 'discharge pressure', 70, 26, 48],
      ['bar', 'flow', 130, 40, 78], ['readout', 'oil temperature', 130, 54, 78], ['lamp', 'run', 130, 74],
      ['button', 'start', 12, 106, 96], ['button', 'stop', 112, 106, 96],
    ] },
    oil_tank: { w: 220, h: 100, seed: 65151, els: [
      ['bar', 'product level', 12, 36, 196], ['bar', 'floating roof position', 12, 58, 196],
      ['readout', 'product temperature', 12, 74, 120], ['lamp', 'vapour', 146, 73, 'hazard'],
    ] },
  };
  const BASE = [100, 116, 132], FRAME = [58, 64, 72];

  function board(p) {
    const W = p.w, H = p.h, im = img(W, H), r = rng(p.seed);
    grain(im, BASE, p.seed + 1, 3, 6, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d < 4) put(im, x, y, add(FRAME, (d === 0 ? -24 : d === 1 ? 36 : d === 3 ? -18 : 0) + (r() - 0.5) * 6));
    }
    // round-head rivets along the frame every 24 px
    const rivet = (x, y) => { put(im, x, y, [150, 156, 162]); put(im, x + 1, y, [110, 114, 120]); put(im, x, y + 1, [110, 114, 120]); put(im, x + 1, y + 1, [40, 44, 48]); };
    for (let x = 10; x < W - 10; x += 24) { rivet(x, 1); rivet(x, H - 3); }
    for (let y = 10; y < H - 10; y += 24) { rivet(1, y); rivet(W - 3, y); }
    const inset = (x0, y0, x1, y1, c, k = 20) => {
      for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
        const e = x === x0 || y === y0 ? -k : x === x1 - 1 || y === y1 - 1 ? k * 0.8 : 0;
        put(im, x, y, add(c, e + (r() - 0.5) * 3));
      }
    };
    const btn = p.els.filter((e) => e[0] === 'button');
    if (btn.length) {
      const y0 = Math.min(...btn.map((e) => e[3])), x0 = Math.min(...btn.map((e) => e[2])), x1 = Math.max(...btn.map((e) => e[2] + e[4]));
      inset(x0 - 4, y0 - 4, x1 + 4, Math.min(y0 + 20, H - 4), add(BASE, -14), 16);
    }
    for (const e of p.els) {
      const [kind, , x, y, a, b] = e;
      if (kind === 'dial') {
        const cx = x + a / 2, cy = y + a / 2, R = a / 2 + 1, n = b === 'wide' ? 6 : 4;
        for (let t = 0; t < 360; t += 0.4) {
          const q = t * Math.PI / 180;
          for (let k = 0; k < n; k++) put(im, cx + (R + k) * Math.cos(q), cy + (R + k) * Math.sin(q), k === n - 1 ? [176, 182, 188] : add([28, 28, 30], k === 0 ? 16 : 0));
        }
      } else if (kind === 'glass') {
        inset(x - 2, y - 2, x + 14, y + a + 2, [30, 32, 34], 16);
        for (let xx = x - 2; xx < x + 14; xx++) { put(im, xx, y - 5, add(b, 30)); put(im, xx, y - 4, b); }
      } else if (kind === 'bar') {
        inset(x - 3, y - 12, x + a + 3, y + 11, add(BASE, -10), 14);
      } else if (kind === 'readout') {
        inset(x - 2, y - 2, x + a + 2, y + 15, add(BASE, -18), 14);
      } else if (kind === 'lamp') {
        if (a === 'hazard') for (let yy = y - 3; yy < y + 17; yy++) for (let xx = x - 3; xx < x + 17; xx++) {
          if (xx >= x - 1 && xx < x + 15 && yy >= y - 1 && yy < y + 15) continue;
          put(im, xx, yy, Math.floor((xx + yy) / 3) % 2 ? [30, 30, 32] : [220, 178, 44]);
        }
        inset(x - 1, y - 1, x + 15, y + 15, [28, 28, 30], 14);
      } else if (kind === 'area') {
        // chart paper in a black frame: cream with a faint blue grid every 10 px, a stronger axis on the left and bottom
        inset(x - 2, y - 2, x + a + 2, y + b + 2, [28, 28, 30], 14);
        for (let yy = y; yy < y + b; yy++) for (let xx = x; xx < x + a; xx++) {
          let c = [228, 222, 200];
          if ((xx - x) % 10 === 0 || (y + b - 1 - yy) % 10 === 0) c = [196, 204, 214];
          if (xx === x || yy === y + b - 1) c = [90, 100, 130];
          put(im, xx, yy, add(c, (r() - 0.5) * 4));
        }
      }
    }
    return im;
  }

  function doc() {
    const lines = ['# Oilfield panel boards', '',
      'Generated by `oilfield_panels.author.js` (`node art/tools/pcart_headless.js . oil_production/oilfield_panels.author.js OFP --paint`).',
      'Coordinates are the top-left of each PanelLayout element, in px from the top-left of the board. Sizes: dial 64 (large) or',
      '48 (medium), label under it at y+size+2; glass 12 wide, short label under it centred at x+6 (about 20 px of room);',
      'bar 8 high with its label 10 px above; readout 13 high; lamp 14 x 14 with its label to the right; button 16 high;',
      '`area` is blank chart paper (grid every 10 px from the bottom-left corner) for the code to draw into.',
      'The title plate is drawn by the code at y 5. `mainline_pump` serves both the mainline pump and the line heater.', ''];
    for (const [id, p] of Object.entries(PANELS)) {
      lines.push(`## ${id}: textures/gui/panel/${id}.png, ${p.w} x ${p.h}`, '', '| element | kind | x | y | size |', '|---|---|---|---|---|');
      for (const [kind, name, x, y, a, b] of p.els) {
        const size = kind === 'dial' ? (a === 64 ? 'large' : 'medium') : kind === 'glass' ? `h ${a}` : kind === 'lamp' ? '14 x 14'
          : kind === 'area' ? `${a} x ${b}` : `w ${a}`;
        lines.push(`| ${name} | ${kind} | ${x} | ${y} | ${size} |`);
      }
      lines.push('');
    }
    return lines.join('\n');
  }

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/gui/panel');
    for (const [id, p] of Object.entries(PANELS)) PCART.writePng(board(p), path.join(dir, id + '.png'));
    fs.writeFileSync(path.join(root, 'art/blockbench/oil_production/oilfield_panels.md'), doc());
    return Object.keys(PANELS).length;
  }

  window.OFP = { paint, ids: [], exportAll: () => ({}), PANELS };
})();
