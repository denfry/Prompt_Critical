// Prompt Critical site: pc-sim charts and the in-browser reactor desk.
const I18N = JSON.parse(document.getElementById("i18n").textContent);
const LANG = document.documentElement.lang;
const T = I18N.chart;
const DATA_ROOT = new URL("../data/", import.meta.url);
const NS = "http://www.w3.org/2000/svg";
const css = name => getComputedStyle(document.documentElement).getPropertyValue(name).trim();
const COLOR = { power: css("--s-power"), blue: css("--s-blue"), amber: css("--s-amber") };

const nf = (digits, opts = {}) => new Intl.NumberFormat(LANG, { maximumFractionDigits: digits, minimumFractionDigits: opts.min ?? 0, ...opts });
const fmtSig = (v, sig = 3) => new Intl.NumberFormat(LANG, { maximumSignificantDigits: sig }).format(v);
const fill = (s, vars) => s.replace(/\{(\w+)\}/g, (_, k) => vars[k]);

function el(parent, tag, attrs = {}, text) {
  const e = document.createElementNS(NS, tag);
  for (const k in attrs) e.setAttribute(k, attrs[k]);
  if (text != null) e.textContent = text;
  parent.appendChild(e);
  return e;
}

async function load(name) {
  const r = await fetch(new URL(name + ".json", DATA_ROOT));
  if (!r.ok) throw new Error(name + ": HTTP " + r.status);
  return r.json();
}

// ---------- line chart ----------

function lineChart(fig, cfg) {
  const legend = document.createElement("div");
  legend.className = "legend";
  for (const s of cfg.series) {
    const span = document.createElement("span");
    const sw = document.createElement("i");
    sw.style.borderTopColor = s.color;
    if (s.dash) sw.style.borderTopStyle = "dashed";
    span.append(sw, s.name);
    legend.append(span);
  }
  const box = document.createElement("div");
  box.className = "chart";
  const tip = document.createElement("div");
  tip.className = "tip";
  tip.setAttribute("aria-hidden", "true");
  box.append(tip);
  fig.append(legend, box, dataTable(cfg));

  let svg, geo, cursor = -1;
  const xs = cfg.series[0].data.map(p => p[0]);

  function draw() {
    const W = Math.max(280, box.clientWidth);
    const H = Math.round(Math.min(420, Math.max(220, W * (cfg.aspect ?? 0.52))));
    const ends = W >= 560 && cfg.endLabels !== false;
    const m = { l: 56, r: ends ? 132 : 14, t: 12, b: 38 };
    const iw = W - m.l - m.r, ih = H - m.t - m.b;
    const { x, y } = cfg;
    const sx = v => m.l + (v - x.min) / (x.max - x.min) * iw;
    const sy = y.log
      ? v => m.t + ih - (Math.log10(v) - Math.log10(y.min)) / (Math.log10(y.max) - Math.log10(y.min)) * ih
      : v => m.t + ih - (v - y.min) / (y.max - y.min) * ih;
    const clampY = v => y.log ? Math.min(y.max, Math.max(y.min, v)) : Math.min(y.max, Math.max(y.min, v));
    geo = { sx, sy, m, iw, ih, W, H };

    if (svg) svg.remove();
    svg = el(box, "svg", { viewBox: `0 0 ${W} ${H}`, width: W, height: H, role: "img", "aria-label": cfg.label, tabindex: 0 });
    box.insertBefore(svg, tip);
    const grid = el(svg, "g", { class: "grid" }), axis = el(svg, "g", { class: "axis" });
    for (const v of y.ticks) {
      el(grid, "line", { x1: m.l, x2: m.l + iw, y1: sy(v), y2: sy(v) });
      el(axis, "text", { x: m.l - 8, y: sy(v) + 4, "text-anchor": "end" }, y.fmt(v));
    }
    const every = Math.ceil(Math.max(...x.ticks.map(v => String(x.fmt(v)).length)) * 7.5 + 12) > iw / (x.ticks.length - 1) ? 2 : 1;
    x.ticks.forEach((v, i) => { if (i % every === 0) el(axis, "text", { x: sx(v), y: m.t + ih + 18, "text-anchor": "middle" }, x.fmt(v)); });
    el(axis, "text", { x: m.l + iw, y: m.t + ih + 34, "text-anchor": "end" }, x.label);
    el(svg, "line", { x1: m.l, x2: m.l + iw, y1: m.t + ih, y2: m.t + ih, stroke: "#3a464d" });

    (cfg.markers ?? []).forEach((mk, i) => {
      const px = sx(mk.x);
      el(svg, "line", { x1: px, x2: px, y1: m.t, y2: m.t + ih, stroke: mk.color, "stroke-dasharray": "4 5", "stroke-width": 1.5, opacity: .8 });
      const right = px + 6 + mk.label.length * 7.5 > W - 4;
      el(svg, "text", { x: right ? px - 6 : px + 6, y: m.t + 12 + i * 16, class: "endlabel", "text-anchor": right ? "end" : "start" }, mk.label);
    });
    for (const s of cfg.series) {
      const d = s.data.filter(p => p[1] != null && (!y.log || p[1] > 0))
        .map((p, i) => (i ? "L" : "M") + sx(p[0]).toFixed(1) + " " + sy(clampY(p[1])).toFixed(1)).join("");
      const attrs = { d, fill: "none", stroke: s.color, "stroke-width": 2, "stroke-linejoin": "round" };
      if (s.dash) attrs["stroke-dasharray"] = "7 5";
      el(svg, "path", attrs);
    }
    if (ends) {
      const placed = [];
      for (const s of cfg.series) {
        const last = [...s.data].reverse().find(p => p[1] != null && (!y.log || p[1] > 0));
        let ly = sy(clampY(last[1])) + 4;
        for (const p of placed) if (Math.abs(p - ly) < 15) ly = p + (ly >= p ? 15 : -15);
        placed.push(ly);
        el(svg, "text", { x: sx(last[0]) + 8, y: ly, class: "endlabel" }, s.name);
      }
    }
    geo.cross = el(svg, "line", { y1: m.t, y2: m.t + ih, stroke: "#b9c2bd", "stroke-width": 1, opacity: 0 });
    geo.dots = cfg.series.map(s => el(svg, "circle", { r: 4.5, fill: s.color, stroke: "#10161a", "stroke-width": 2, opacity: 0 }));
    svg.addEventListener("pointermove", e => {
      const r = svg.getBoundingClientRect();
      const px = (e.clientX - r.left) * (W / r.width);
      const xv = x.min + (px - m.l) / iw * (x.max - x.min);
      show(nearest(xv));
    });
    svg.addEventListener("pointerleave", hide);
    svg.addEventListener("blur", hide);
    svg.addEventListener("keydown", e => {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      e.preventDefault();
      const step = Math.max(1, Math.round(xs.length / 60));
      show(Math.min(xs.length - 1, Math.max(0, (cursor < 0 ? 0 : cursor) + (e.key === "ArrowRight" ? step : -step))));
    });
    if (cursor >= 0) show(cursor);
  }

  function nearest(xv) {
    let lo = 0, hi = xs.length - 1;
    while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (xs[mid] < xv) lo = mid; else hi = mid; }
    return Math.abs(xs[lo] - xv) <= Math.abs(xs[hi] - xv) ? lo : hi;
  }

  function show(i) {
    cursor = i;
    const { sx, sy, W } = geo, xv = xs[i];
    geo.cross.setAttribute("x1", sx(xv)); geo.cross.setAttribute("x2", sx(xv)); geo.cross.setAttribute("opacity", .6);
    let html = `<b>${cfg.x.tip(xv)}</b>`;
    cfg.series.forEach((s, k) => {
      const v = s.data[i]?.[1];
      const ok = v != null && (!cfg.y.log || v > 0);
      geo.dots[k].setAttribute("opacity", ok ? 1 : 0);
      if (ok) { geo.dots[k].setAttribute("cx", sx(s.data[i][0])); geo.dots[k].setAttribute("cy", sy(Math.min(cfg.y.max, Math.max(cfg.y.min, v)))); }
      html += `<span><i style="border-top-color:${s.color}${s.dash ? ";border-top-style:dashed" : ""}"></i>${s.name}: ${ok ? cfg.y.tip(v) : "—"}</span><br>`;
    });
    tip.innerHTML = html;
    tip.classList.add("on");
    const scale = box.clientWidth / W, px = sx(xv) * scale;
    const tw = tip.offsetWidth;
    tip.style.left = (px + 14 + tw > box.clientWidth ? Math.max(0, px - 14 - tw) : px + 14) + "px";
    tip.style.top = "8px";
  }

  function hide() {
    cursor = -1;
    tip.classList.remove("on");
    if (!geo) return;
    geo.cross.setAttribute("opacity", 0);
    geo.dots.forEach(d => d.setAttribute("opacity", 0));
  }

  draw();
  let w = box.clientWidth;
  new ResizeObserver(() => { if (Math.abs(box.clientWidth - w) > 2) { w = box.clientWidth; draw(); } }).observe(box);
}

function dataTable(cfg) {
  const det = document.createElement("details");
  const sum = document.createElement("summary");
  sum.textContent = T.table;
  const wrap = document.createElement("div");
  wrap.className = "tbl";
  det.append(sum, wrap);
  det.addEventListener("toggle", () => {
    if (!det.open || wrap.firstChild) return;
    const tbl = document.createElement("table");
    const head = tbl.createTHead().insertRow();
    for (const h of [cfg.x.label, ...cfg.series.map(s => s.name)]) {
      const th = document.createElement("th"); th.textContent = h; head.append(th);
    }
    const body = tbl.createTBody();
    const every = Math.max(1, Math.ceil(cfg.series[0].data.length / 120));
    cfg.series[0].data.forEach((p, i) => {
      if (i % every) return;
      const r = body.insertRow();
      r.insertCell().textContent = cfg.x.tip(p[0]);
      for (const s of cfg.series) { const v = s.data[i]?.[1]; r.insertCell().textContent = v == null ? "—" : cfg.y.tip(v); }
    });
    wrap.append(tbl);
  });
  return det;
}

const pctTick = v => (v >= 1 ? nf(0).format(v) : fmtSig(v, 1)) + " %";

const CHARTS = {
  async inhour(fig) {
    const k = await load("kinetics");
    lineChart(fig, {
      label: fig.querySelector("strong").textContent,
      series: [
        { name: T.withDelayed, color: COLOR.blue, data: k.inhour.map(r => [r.d, r.withDelayed]) },
        { name: T.promptOnly, color: COLOR.power, dash: true, data: k.inhour.map(r => [r.d, r.promptOnly]) },
      ],
      x: { min: 0, max: 1.2, ticks: [0, .2, .4, .6, .8, 1, 1.2], fmt: v => nf(1).format(v) + " $", label: T.rho, tip: v => "ρ = " + nf(2).format(v) + " $" },
      y: { min: 1e-3, max: 1e3, log: true, ticks: [1e-3, 1e-2, 1e-1, 1, 10, 100, 1000], fmt: v => fmtSig(v, 1) + " s", label: T.period, tip: v => fmtSig(v, 3) + " s" },
      markers: [{ x: 1, label: "1 $ = β", color: COLOR.power }],
      endLabels: false,
    });
  },
  async lof(fig) {
    const d = await load("lof"), c = n => d.cols.indexOf(n);
    const ser = (n, name, color, dash) => ({ name, color, dash, data: d.rows.map(r => [r[0], r[c(n)] * 100]) });
    lineChart(fig, {
      label: fig.querySelector("strong").textContent,
      series: [ser("power", T.power, COLOR.power), ser("flowA", T.flowA, COLOR.blue), ser("flowB", T.flowB, COLOR.amber, true)],
      x: { min: 0, max: 60, ticks: [0, 10, 20, 30, 40, 50, 60], fmt: v => v, label: T.time, tip: v => "t = " + nf(1, { min: 1 }).format(v) + " s" },
      y: { min: -25, max: 125, ticks: [-25, 0, 25, 50, 75, 100, 125], fmt: v => v + " %", label: T.pct, tip: v => nf(1).format(v) + " %" },
      markers: [{ x: d.pumpTrip, label: T.pumpTrip, color: COLOR.amber }, { x: d.tripAt, label: T.trip, color: COLOR.power }],
      aspect: 0.42,
    });
    bind("lof.tripAt", nf(2, { min: 2 }).format(d.tripAt) + " s");
  },
  async scram(fig) {
    const d = await load("scram"), c = n => d.cols.indexOf(n);
    lineChart(fig, {
      label: fig.querySelector("strong").textContent,
      series: [
        { name: T.fission, color: COLOR.power, data: d.rows.map(r => [r[0], r[c("fission")] * 100]) },
        { name: T.decay, color: COLOR.amber, dash: true, data: d.rows.map(r => [r[0], r[c("decay")] > 0 ? r[c("decay")] * 100 : null]) },
      ],
      x: { min: 0, max: 300, ticks: [0, 60, 120, 180, 240, 300], fmt: v => v, label: T.time, tip: v => "t = " + nf(1).format(v) + " s" },
      y: { min: 0.01, max: 100, log: true, ticks: [0.01, 0.1, 1, 10, 100], fmt: pctTick, label: T.pctFull, tip: v => fmtSig(v, 3) + " %" },
      markers: [{ x: d.tripAt, label: "SCRAM", color: COLOR.power }],
      endLabels: false,
      aspect: 0.62,
    });
  },
  async water(fig) {
    const d = await load("water");
    lineChart(fig, {
      label: fig.querySelector("strong").textContent,
      series: [{ name: T.tsat, color: COLOR.blue, data: d.rows.map(r => [r[0], r[1]]) }],
      x: { min: 0, max: 22, ticks: [0, 5, 10, 15, 20], fmt: v => v, label: T.pressure, tip: v => nf(1).format(v) + " MPa" },
      y: { min: 0, max: 400, ticks: [0, 100, 200, 300, 400], fmt: v => v + " °C", label: T.tempC, tip: v => nf(1).format(v) + " °C" },
      markers: [{ x: 15.5, label: T.pwr, color: COLOR.amber }],
      endLabels: false,
      aspect: 0.62,
    });
  },
};

function bind(key, text) {
  for (const n of document.querySelectorAll(`[data-bind="${key}"]`)) n.textContent = text;
}

async function perf() {
  const p = await load("perf");
  bind("perf.mean", nf(3, { min: 3 }).format(p.meanMs));
  document.querySelector("#perf .used").style.setProperty("--w", (p.meanMs / p.budgetMs * 100).toFixed(2) + "%");
  const note = document.querySelector("#perf .perf-note");
  note.textContent = fill(I18N.perfNote, {
    n: nf(0).format(p.ticks), p50: nf(3).format(p.p50Ms), p95: nf(3).format(p.p95Ms), p99: nf(3).format(p.p99Ms),
  });
}

function initCharts() {
  for (const fig of document.querySelectorAll("[data-chart]")) {
    const msg = document.createElement("p");
    msg.className = "chart-msg"; msg.textContent = T.loading;
    fig.append(msg);
    CHARTS[fig.dataset.chart](fig).then(() => msg.remove(), err => { msg.textContent = T.error; console.error(err); });
  }
  perf().catch(err => console.error(err));
}

// ---------- reactor desk ----------

const S = I18N.sim;
const BETA = 0.0065, GEN = 2e-5;
const YIELD = [0.033, 0.219, 0.196, 0.395, 0.115, 0.042];
const LAMBDA = [0.0124, 0.0305, 0.111, 0.301, 1.14, 3.01];
const B = YIELD.map(a => a * BETA);
const DT = 1e-3, FEEDBACK = 0.6, TAU_T = 6, ROD_SPEED = 0.02, DROP_TIME = 1.2, N0 = 1e-4;
const HOLD = 15, WINDOW = 120;
// Integral rod worth in dollars: an S-curve from −2.5 $ (all in) to +1.2 $ (all out).
const rodWorth = x => -2.5 + 3.7 * (x - Math.sin(2 * Math.PI * x) / (2 * Math.PI));
const SOURCE = N0 * -rodWorth(0) * BETA / GEN;

function initSim() {
  const $ = id => document.getElementById(id);
  const canvas = $("sim-canvas"), ctx = canvas.getContext("2d");
  const rod = $("rod"), rodOut = $("rod-out"), statusEl = $("sim-status"), bypass = $("bypass");
  const anns = Object.fromEntries([...document.querySelectorAll("[data-ann]")].map(a => [a.dataset.ann, a]));
  const out = { power: $("r-power"), period: $("r-period"), rho: $("r-rho"), rods: $("r-rods"), time: $("r-time") };
  let st, speed = 4, best = null;
  try { best = Number(localStorage.getItem("pc.best")) || null; } catch { /* storage unavailable */ }

  function reset() {
    st = { n: N0, C: B.map((b, i) => b * N0 / (GEN * LAMBDA[i])), temp: 0, x: 0, target: 0, t: 0, omega: 0,
      scram: false, trip: null, damage: false, hold: 0, done: false, hist: [], nextSample: 0, tripT: null };
    rod.value = 0;
    setStatus(S.st_ready);
  }

  function rho$() { return rodWorth(st.x) - FEEDBACK * st.temp; }

  function trip(why) {
    if (st.trip) return;
    st.trip = why; st.scram = true; st.tripT = st.t;
    setStatus(fill(S.st_trip, { why: S["why_" + why] }), "bad");
  }

  function step() {
    if (st.scram) st.x = Math.max(0, st.x - DT / DROP_TIME);
    else {
      const dx = st.target - st.x, max = ROD_SPEED * DT;
      st.x += Math.abs(dx) < max ? dx : Math.sign(dx) * max;
    }
    const rho = rho$() * BETA;
    let num = st.n + DT * SOURCE, den = 1 - DT * (rho - BETA) / GEN;
    const a = LAMBDA.map(l => 1 / (1 + l * DT));
    for (let i = 0; i < 6; i++) { num += DT * LAMBDA[i] * a[i] * st.C[i]; den -= DT * LAMBDA[i] * a[i] * DT * B[i] / GEN; }
    const n = num / den;
    for (let i = 0; i < 6; i++) st.C[i] = (st.C[i] + DT * B[i] / GEN * n) * a[i];
    const w = Math.log(n / st.n) / DT;
    st.omega += (w - st.omega) * DT / 0.5;
    st.n = n;
    st.temp += DT * (n - st.temp) / TAU_T;
    st.t += DT;

    const period = 1 / st.omega;
    if (!bypass.checked && !st.trip) {
      if (period > 0 && period < 5) trip("period");
      else if (st.n > 1.18) trip("high");
    }
    if (bypass.checked && st.temp > 1.5 && !st.damage) {
      st.damage = true;
      setStatus(S.st_damage, "bad");
    }
    if (!st.trip && !st.damage && st.n >= 0.95 && st.n <= 1.05) {
      st.hold += DT;
      if (st.hold >= HOLD && !st.done) {
        st.done = true;
        const reached = st.t - HOLD;
        if (best == null || reached < best) {
          best = reached;
          try { localStorage.setItem("pc.best", String(best)); } catch { /* storage unavailable */ }
        }
        setStatus(fill(S.st_done, { t: clock(reached) }) + " " + fill(S.st_best, { t: clock(best) }), "good");
      }
    } else st.hold = 0;
    if (st.t >= st.nextSample) {
      st.hist.push([st.t, st.n, st.x]);
      st.nextSample = st.t + 0.1;
      if (st.hist.length > WINDOW * 10 + 20) st.hist.shift();
    }
  }

  let lastStatus = "";
  function setStatus(text, kind = "") {
    if (text === lastStatus) return;
    lastStatus = text;
    statusEl.textContent = text;
    statusEl.className = "status " + kind;
  }

  const clock = s => { const m = Math.floor(s / 60), r = Math.floor(s % 60); return m + ":" + String(r).padStart(2, "0"); };
  function fmtPower(n) {
    const p = n * 100;
    if (p >= 10) return nf(1, { min: 1 }).format(p) + " %";
    if (p >= 0.1) return nf(2, { min: 2 }).format(p) + " %";
    return fmtSig(p, 2) + " %";
  }
  function fmtPeriod() {
    const p = 1 / st.omega;
    if (!isFinite(p) || Math.abs(p) > 999) return "∞";
    return (p < 0 ? "−" : "") + nf(Math.abs(p) < 10 ? 1 : 0).format(Math.abs(p)) + " s";
  }
  const text = (node, v) => { if (node.textContent !== v) node.textContent = v; };

  function updateDom() {
    const r = rho$();
    text(out.power, fmtPower(st.n));
    text(out.period, fmtPeriod());
    text(out.rho, (r >= 0 ? "+" : "−") + nf(3, { min: 3 }).format(Math.abs(r)) + " $");
    text(out.rods, nf(1, { min: 1 }).format(st.x * 100) + " %");
    text(out.time, clock(st.t));
    text(rodOut, nf(1, { min: 1 }).format(st.target * 100) + " %");
    const per = 1 / st.omega;
    const lit = {
      source: st.n < 1e-3, period: per > 0 && per < 20, high: st.n > 1.1, prompt: r >= 1,
      band: st.n >= 0.95 && st.n <= 1.05 && !st.trip, bypass: bypass.checked, trip: !!st.trip, damage: st.damage,
    };
    for (const k in lit) {
      if (anns[k].classList.contains("on") !== lit[k]) anns[k].setAttribute("aria-label", anns[k].textContent + ": " + (lit[k] ? S.annOn : S.annOff));
      anns[k].classList.toggle("on", lit[k]);
      anns[k].classList.toggle("blink", lit[k] && (k === "trip" || k === "damage" || k === "prompt"));
    }
    if (!st.trip && !st.damage && !st.done) {
      if (st.hold > 0) setStatus(fill(S.st_hold, { s: nf(1, { min: 1 }).format(st.hold) }), "good");
      else if (st.x === 0) setStatus(S.st_ready);
      else if (r > 0.01) setStatus(S.st_critical);
      else if (r < -0.01) setStatus(S.st_sub);
    }
  }

  function draw() {
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    const l = 64, r = 12, top = 10, split = Math.round(h * 0.72), bot = h - 22;
    const t1 = Math.max(WINDOW, st.t), t0 = t1 - WINDOW;
    const sx = t => l + (t - t0) / WINDOW * (w - l - r);
    const LMIN = -5, LMAX = 1;
    const sy = n => top + (LMAX - Math.log10(Math.min(10, Math.max(1e-5, n)))) / (LMAX - LMIN) * (split - 10 - top);
    const ry = x => split + 14 + (1 - x) * (bot - split - 14);
    ctx.font = "500 11px 'JetBrains Mono', monospace";
    ctx.textAlign = "right"; ctx.textBaseline = "middle";
    // target band
    ctx.fillStyle = "rgba(63,209,107,0.16)";
    ctx.fillRect(l, sy(1.05), w - l - r, sy(0.95) - sy(1.05));
    for (let e = LMIN; e <= LMAX; e++) {
      const y = sy(10 ** e);
      ctx.strokeStyle = "#1e272c"; ctx.lineWidth = 1;
      ctx.beginPath(); ctx.moveTo(l, y + .5); ctx.lineTo(w - r, y + .5); ctx.stroke();
      ctx.fillStyle = "#8a9a93";
      ctx.fillText(pctTick(10 ** e * 100), l - 8, y);
    }
    ctx.strokeStyle = "#2a3338";
    ctx.beginPath(); ctx.moveTo(l, split + 2.5); ctx.lineTo(w - r, split + 2.5); ctx.stroke();
    ctx.fillStyle = "#8a9a93";
    ctx.fillText("100 %", l - 8, ry(1)); ctx.fillText("0 %", l - 8, ry(0));
    ctx.textAlign = "left";
    for (let t = Math.ceil(t0 / 30) * 30; t <= t1; t += 30) if (sx(t) + 36 < w) ctx.fillText(clock(t), sx(t) + 3, h - 10);
    if (st.tripT != null && st.tripT >= t0) {
      ctx.strokeStyle = COLOR.power; ctx.setLineDash([4, 5]);
      ctx.beginPath(); ctx.moveTo(sx(st.tripT) + .5, top); ctx.lineTo(sx(st.tripT) + .5, bot); ctx.stroke();
      ctx.setLineDash([]);
    }
    const pts = st.hist.filter(p => p[0] >= t0);
    pts.push([st.t, st.n, st.x]);
    ctx.lineWidth = 2; ctx.lineJoin = "round";
    ctx.strokeStyle = COLOR.blue; ctx.setLineDash([6, 4]);
    ctx.beginPath(); pts.forEach((p, i) => i ? ctx.lineTo(sx(p[0]), ry(p[2])) : ctx.moveTo(sx(p[0]), ry(p[2]))); ctx.stroke();
    ctx.setLineDash([]);
    ctx.strokeStyle = COLOR.power;
    ctx.beginPath(); pts.forEach((p, i) => i ? ctx.lineTo(sx(p[0]), sy(p[1])) : ctx.moveTo(sx(p[0]), sy(p[1]))); ctx.stroke();
  }

  let last = null, raf = 0;
  function frame(now) {
    const real = last == null ? 0 : Math.min(0.1, (now - last) / 1000);
    last = now;
    if (!st.damage) {
      const steps = Math.round(real * speed / DT);
      for (let i = 0; i < steps; i++) step();
    }
    updateDom();
    draw();
    raf = requestAnimationFrame(frame);
  }

  rod.addEventListener("input", () => { if (!st.scram) st.target = rod.value / 100; });
  document.querySelectorAll("[data-nudge]").forEach(b => b.addEventListener("click", () => {
    if (st.scram) return;
    st.target = Math.min(1, Math.max(0, st.target + Number(b.dataset.nudge) / 100));
    rod.value = (st.target * 100).toFixed(1);
  }));
  document.querySelectorAll('input[name="speed"]').forEach(r => r.addEventListener("change", () => { speed = Number(r.value); }));
  $("scram").addEventListener("click", () => { trip("manual"); rod.value = 0; st.target = 0; });
  $("reset").addEventListener("click", () => { reset(); last = null; });

  reset();
  // Run only while the desk is on screen.
  new IntersectionObserver(([e]) => {
    if (e.isIntersecting && !raf) { last = null; raf = requestAnimationFrame(frame); }
    else if (!e.isIntersecting && raf) { cancelAnimationFrame(raf); raf = 0; }
  }).observe(canvas);
  updateDom(); draw();
}

initSim();
initCharts();
