// Control-room boards of the reactor prototypes, same pipeline as hydrogen/h2_panels.author.js: one layout table drives
// the painted board and the coordinate table reactor_panels.md next to this script. PanelLayout draws every element.
//   vver70_bpu  unit control board (BPU) of the VVER-70 prototype: Soviet green-grey enamel in a black bakelite frame,
//               bakelite bezels round the dials, the AZ (scram) key under a red-and-white hinged guard.
//   candu_npd   NPD reactivity board: cream enamel with AECL-blue trim and channel tabs, the SDS trip under its guard.
//   bor60       BOR-60 sodium plant board: green-grey and bakelite as the VVER, sodium-leak lamp in hazard stripes.
// Run headless: node art/tools/pcart_headless.js <repo> core/reactor_panels.author.js RPB --paint
(function () {
  const path = require('path'), fs = require('fs');
  const { rng, img, put, add, grain } = PCART;

  // kinds as h2_panels: dial (x, y, 64 large / 48 medium), glass (x, y, h; 12 wide), bar (x, y, w), readout (x, y, w),
  // lamp (x, y [, 'hazard']), button (x, y, w), guard = a button under a hinged guard (x, y, w).
  const SOVIET = { base: [122, 138, 126], frame: 'bakelite', accent: [30, 28, 26] };
  const CANADA = { base: [214, 206, 182], frame: 'blue', accent: [40, 84, 150] };
  const PANELS = {
    vver70_bpu: { w: 220, h: 252, seed: 64101, style: SOVIET, els: [
      ['dial', 'reactor power, log %N', 12, 24, 48], ['dial', 'period', 66, 24, 48], ['dial', 'primary pressure 0-16 MPa', 120, 24, 48],
      ['glass', 'pressuriser level', 184, 24, 56],
      ['bar', 'ARK group 1', 12, 104, 92], ['bar', 'ARK group 2', 116, 104, 92],
      ['bar', 'ARK group 3', 12, 124, 92], ['bar', 'ARK group 4', 116, 124, 92],
      ['bar', 'ARK group 5', 12, 144, 92], ['bar', 'ARK group 6', 116, 144, 92],
      ['readout', 'loop 1 hot/cold', 12, 158, 62], ['readout', 'loop 2 hot/cold', 79, 158, 62], ['readout', 'loop 3 hot/cold', 146, 158, 62],
      ['readout', 'boron concentration', 12, 176, 96], ['lamp', 'PZ-1', 116, 175], ['lamp', 'PZ-2', 162, 175],
      ['button', 'MCP-1 start', 12, 198, 30], ['button', 'MCP-1 stop', 46, 198, 30],
      ['button', 'MCP-2 start', 80, 198, 30], ['button', 'MCP-2 stop', 114, 198, 30],
      ['button', 'MCP-3 start', 148, 198, 30], ['button', 'MCP-3 stop', 182, 198, 30],
      ['guard', 'AZ (scram)', 80, 226, 60],
    ] },
    candu_npd: { w: 220, h: 172, seed: 64111, style: CANADA, els: [
      ['glass', 'zone 1', 14, 26, 48], ['glass', 'zone 2', 36, 26, 48], ['glass', 'zone 3', 58, 26, 48],
      ['glass', 'zone 4', 80, 26, 48], ['glass', 'zone 5', 102, 26, 48], ['glass', 'zone 6', 124, 26, 48],
      ['glass', 'moderator level', 178, 26, 60],
      ['bar', 'adjuster rods', 12, 108, 196],
      ['readout', 'D2O isotopic purity', 12, 122, 96], ['readout', 'fuelling machine', 112, 122, 96],
      ['guard', 'SDS trip', 72, 146, 76],
    ] },
    bor60: { w: 220, h: 200, seed: 64121, style: SOVIET, els: [
      ['dial', 'core outlet temperature', 12, 24, 64], ['dial', 'argon cover gas pressure', 92, 32, 48],
      ['lamp', 'sodium leak', 154, 40, 'hazard'],
      ['bar', 'primary sodium flow', 12, 114, 196], ['bar', 'secondary sodium flow', 12, 134, 196],
      ['readout', 'IHX delta-T', 12, 148, 196],
      ['guard', 'AZ (scram)', 72, 174, 76],
    ] },
  };

  function board(p) {
    const W = p.w, H = p.h, im = img(W, H), r = rng(p.seed), base = p.style.base, acc = p.style.accent;
    grain(im, base, p.seed + 1, 3, 5, 12);
    for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
      const d = Math.min(x, y, W - 1 - x, H - 1 - y);
      if (d >= 4) continue;
      const c = p.style.frame === 'blue' ? acc : [36, 32, 28];
      put(im, x, y, add(c, (d === 0 ? -20 : d === 1 ? 40 : d === 3 ? -14 : 0) + (r() - 0.5) * 6));
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
    const btn = p.els.filter((e) => e[0] === 'button');
    if (btn.length) {
      const y0 = Math.min(...btn.map((e) => e[3])), x0 = Math.min(...btn.map((e) => e[2])), x1 = Math.max(...btn.map((e) => e[2] + e[4]));
      inset(x0 - 4, y0 - 4, x1 + 4, Math.min(y0 + 20, H - 4), add(base, -12), 16);
    }
    let glassTab = 0;
    for (const e of p.els) {
      const [kind, , x, y, a, b] = e;
      if (kind === 'dial') {
        const cx = x + a / 2, cy = y + a / 2, R = a / 2 + 1;
        for (let t = 0; t < 360; t += 0.4) {
          const q = t * Math.PI / 180;
          for (let k = 0; k < 4; k++) {
            const rr = R + k, sh = p.style.frame === 'blue' ? (k === 3 ? 50 : -30) : (k === 0 ? 20 : k === 3 ? 34 : 0);
            put(im, cx + rr * Math.cos(q), cy + rr * Math.sin(q), add(p.style.frame === 'blue' ? base : [34, 30, 26], sh));
          }
        }
      } else if (kind === 'glass') {
        inset(x - 2, y - 2, x + 14, y + a + 2, [30, 32, 34], 16);
        const tab = p.style.frame === 'blue' ? acc : [[200, 160, 40], [60, 110, 70]][glassTab++ % 2];
        for (let xx = x - 2; xx < x + 14; xx++) { put(im, xx, y - 5, add(tab, 30)); put(im, xx, y - 4, tab); }
      } else if (kind === 'bar') {
        inset(x - 3, y - 12, x + a + 3, y + 11, add(base, -10), 14);
      } else if (kind === 'readout') {
        inset(x - 2, y - 2, x + a + 2, y + 15, add(base, -16), 14);
      } else if (kind === 'lamp') {
        if (a === 'hazard') for (let yy = y - 3; yy < y + 17; yy++) for (let xx = x - 3; xx < x + 17; xx++) {
          if (xx >= x - 1 && xx < x + 15 && yy >= y - 1 && yy < y + 15) continue;
          put(im, xx, yy, Math.floor((xx + yy) / 3) % 2 ? [30, 30, 32] : [220, 178, 44]);
        }
        inset(x - 1, y - 1, x + 15, y + 15, [28, 26, 24], 14);
      } else if (kind === 'guard') {
        // hinged guard: red-and-white hatched frame 6 px round the key, the hinge knuckles along its top edge
        for (let yy = y - 6; yy < y + 22; yy++) for (let xx = x - 6; xx < x + a + 6; xx++) {
          if (xx >= x - 1 && xx < x + a + 1 && yy >= y - 1 && yy < y + 17) continue;
          const edge = xx === x - 6 || yy === y - 6 || xx === x + a + 5 || yy === y + 21;
          put(im, xx, yy, edge ? [40, 20, 18] : Math.floor((xx - yy + 400) / 4) % 2 ? [236, 232, 222] : [196, 36, 30]);
        }
        for (let k = 0; k < 4; k++) { const hx = x + Math.round((a - 12) * k / 3); for (let xx = hx; xx < hx + 12; xx++) { put(im, xx, y - 8, [170, 172, 168]); put(im, xx, y - 7, [96, 98, 94]); } }
        inset(x - 1, y - 1, x + a + 1, y + 17, [40, 20, 18], 14);
      }
    }
    return im;
  }

  function doc() {
    const lines = ['# Reactor control-room boards', '',
      'Generated by `reactor_panels.author.js` (`node art/tools/pcart_headless.js . core/reactor_panels.author.js RPB --paint`).',
      'Coordinates are the top-left of each PanelLayout element, in px from the top-left of the board. Sizes: dial 64 (large) or',
      '48 (medium), label under it at y+size+2; glass 12 wide, short label under it centred at x+6 (about 20 px of room);',
      'bar 8 high with its label 10 px above; readout 13 high; lamp 14 x 14 with its label to the right; button 16 high.',
      '`guard` is an ordinary button drawn under the painted hinged guard; give it a confirm-twice or hold-to-trip action in code.',
      'The title plate is drawn by the code at y 5.', ''];
    for (const [id, p] of Object.entries(PANELS)) {
      lines.push(`## ${id}: textures/gui/panel/${id}.png, ${p.w} x ${p.h}`, '', '| element | kind | x | y | size |', '|---|---|---|---|---|');
      for (const [kind, name, x, y, a] of p.els) {
        const size = kind === 'dial' ? (a === 64 ? 'large' : 'medium') : kind === 'glass' ? `h ${a}` : kind === 'lamp' ? '14 x 14' : `w ${a}`;
        lines.push(`| ${name} | ${kind === 'guard' ? 'button (guarded)' : kind} | ${x} | ${y} | ${size} |`);
      }
      lines.push('');
    }
    return lines.join('\n');
  }

  function paint(root) {
    const dir = path.join(root, PCART.ASSETS, 'textures/gui/panel');
    for (const [id, p] of Object.entries(PANELS)) PCART.writePng(board(p), path.join(dir, id + '.png'));
    fs.writeFileSync(path.join(root, 'art/blockbench/core/reactor_panels.md'), doc());
    return Object.keys(PANELS).length;
  }

  window.RPB = { paint, ids: [], exportAll: () => ({}), PANELS };
})();
