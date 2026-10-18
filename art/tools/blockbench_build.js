// Blockbench (desktop) build step: spec.json -> .bbmodel + Java JSON + preview PNG.
// Load inside Blockbench: eval(require('fs').readFileSync('<repo>/art/tools/blockbench_build.js', 'utf8'))
// then: pcRun('<repo>', '<spec.json>', { from: 0, count: 25 })   // batches keep the editor responsive
(function () {
  const fs = require('fs');
  const path = require('path');
  const AX = { x: 0, y: 1, z: 2 };
  const DIRS = ['north', 'south', 'east', 'west', 'up', 'down'];

  function build(root, spec) {
    const texRoot = path.join(root, 'pc-mod/src/main/resources/assets/promptcritical/textures');
    newProject(Formats.java_block);
    Project.name = spec.id.split('/').pop();
    const tm = {};
    for (const [k, rel] of Object.entries(spec.textures)) {
      const t = new Texture({ name: path.basename(rel) }).fromPath(path.join(texRoot, rel)).add(false);
      t.id = k;
      tm[k] = t;
    }
    for (const e of spec.elements) {
      const rot = [0, 0, 0];
      let origin = [8, 8, 8];
      if (e.rot) { rot[AX[e.rot.axis]] = e.rot.angle; origin = e.rot.origin; }
      const c = new Cube({ name: e.name, from: e.from, to: e.to, origin, rotation: rot, autouv: 1 }).init();
      for (const d of DIRS) c.faces[d].texture = tm[e.faces[d] || e.tex].uuid;
      c.mapAutoUV();
      if (e.full) { c.autouv = 0; for (const d of e.full) c.faces[d].uv = [0, 0, 16, 16]; }
    }
    // частицы разрушения — текстура первого элемента (обычно корпус)
    if (spec.elements.length) tm[spec.elements[0].tex].particle = true;
    Canvas.updateAll();
    return Project;
  }

  // Camera along the default diagonal, distance scaled to the model extent (at least one block).
  function frame(view, spec) {
    const lo = [Infinity, Infinity, Infinity], hi = [-Infinity, -Infinity, -Infinity];
    for (const e of spec.elements) {
      for (let k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], e.from[k]); hi[k] = Math.max(hi[k], e.to[k]); }
    }
    const c = [(lo[0] + hi[0]) / 2 - 8, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2 - 8];
    const k = Math.max(1, 0.6 * Math.max(hi[0] - lo[0], hi[1] - lo[1], hi[2] - lo[2]) / 16);
    if (k === 1) { c[0] = 0; c[1] = 12; c[2] = 0; }
    view.camera.position.set(c[0] - 40 * k, c[1] + 20 * k, c[2] - 40 * k);
    view.controls.target.set(c[0], c[1], c[2]);
    view.controls.update();
  }

  // Offscreen render with grid/ground hidden and a transparent background.
  function shot(view, file) {
    const hide = [three_grid, Canvas.ground_plane, Canvas.outlines, ...scene.children.filter((c) => c.name === 'grid_group')].filter(Boolean);
    const prev = hide.map((o) => o.visible);
    hide.forEach((o) => { o.visible = false; });
    const r = view.renderer, oc = r.getClearAlpha();
    r.setClearAlpha(0);
    view.render();
    const url = view.canvas.toDataURL('image/png');
    hide.forEach((o, i) => { o.visible = prev[i]; });
    r.setClearAlpha(oc);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
  }

  function save(root, spec, view) {
    const bb = path.join(root, 'art/blockbench', spec.cat, spec.id + '.bbmodel');
    fs.mkdirSync(path.dirname(bb), { recursive: true });
    fs.writeFileSync(bb, Codecs.project.compile());
    let j = Codecs.java_block.compile();
    if (typeof j === 'string') j = JSON.parse(j);
    delete j.format_version;
    for (const e of j.elements || []) {
      if ((e.name || '').startsWith('glow_')) e.neoforge_data = { block_light: 15, sky_light: 15 };
    }
    const jp = path.join(root, 'pc-mod/src/main/resources/assets/promptcritical/models', spec.java + '.json');
    fs.mkdirSync(path.dirname(jp), { recursive: true });
    fs.writeFileSync(jp, compileJSON(j));
    if (view) frame(view, spec);
    if (view) shot(view, path.join(root, 'art/preview/models', spec.cat, spec.id.replace(/\//g, '__') + '.png'));
  }

  // Phase 1: build a batch (textures load asynchronously). Phase 2: pcFlush() saves, renders and closes it.
  window.pcRun = function (root, specFile, { from = 0, count = 25 } = {}) {
    const all = JSON.parse(fs.readFileSync(specFile, 'utf8'));
    window.PC_PENDING = [];
    for (const spec of all.slice(from, from + count)) {
      const p = build(root, spec);
      PC_PENDING.push({ uuid: p.uuid, spec, root });
    }
    return { built: PC_PENDING.length, total: all.length, next: from + count < all.length ? from + count : null };
  };

  window.pcFlush = function (viewId = 'mcp_offscreen_pc') {
    const view = Preview.all.find((p) => p.id === viewId);
    const done = [];
    for (const item of PC_PENDING || []) {
      const p = ModelProject.all.find((x) => x.uuid === item.uuid);
      if (!p) continue;
      p.select();
      save(item.root, item.spec, view);
      p.close(true);
      done.push(item.spec.id);
    }
    window.PC_PENDING = [];
    return done.length;
  };

  // Whole range in one call: build a batch, wait for textures, flush; returns saved counts per batch.
  window.pcAll = async function (root, specFile, from, to, step = 20) {
    const out = [];
    for (let i = from; i < to; i += step) {
      pcRun(root, specFile, { from: i, count: Math.min(step, to - i) });
      await new Promise((res) => setTimeout(res, 1500));
      out.push(pcFlush());
    }
    return out;
  };
})();
