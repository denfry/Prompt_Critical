"""Write index.html of the overview film ("whole plant" tour) from the shot list below.

    python art/film/guide/tour/gen.py [ru|en]

Cuts land on bars of the music (High-Energy Adventure Theme, 130 BPM, downbeat at 0): one bar = 1.846 s.
Footage: FilmRecorder shots copied to assets/footage/<shot>.mp4 (python ../stage.py tour). Silent footage, music bed.
"""
import html
import json
import os
import sys

BAR = 4 * 60 / 130
HERE = os.path.dirname(os.path.abspath(__file__))

# shot, bars, media offset (s), overlay kind, overlay data per language
INTRO = ("tu01_aerial_in", 3, 1.0)
HEROES = [
    ("tu02_furnace_rise", 2, 0.6, ("I1", "Доменная печь", "Blast furnace"), ("кокс, агломерат, горячее дутьё", "coke, sinter, hot blast")),
    ("tu03_stoves", 1, 1.0, ("I1", "Воздухонагреватели", "Hot blast stoves"), None),
    ("tu04_rig", 2, 0.6, ("I2", "Буровая", "Drilling rig"), ("газопроявления, ПВО, глушение", "kicks, BOP stacks, well kill")),
    ("tu05_pumpjacks", 1, 1.0, ("I2", "Станки-качалки", "Pumpjacks"), None),
    ("tu06_enrich", 2, 0.6, ("I4", "Каскад центрифуг", "Centrifuge cascade"), ("обогащение UF₆", "UF₆ enrichment")),
    ("tu07_reactor", 2, 0.6, ("T2a", "ВВЭР", "VVER"), ("аварии вплоть до плавления зоны", "accidents up to core melt")),
    ("tu08_control", 1, 0.8, ("A4", "Щит управления", "Control room"), None),
]
DISTRICTS = [
    ("tu20_coal", "Уголь и пар", "Coal and steam", 14, "шахта · котлы · паровые машины", "mine · boilers · steam engines"),
    ("tu21_power", "Энергетика", "Power", 31, "генераторы · ЛЭП · релейная защита", "generators · power lines · protection relays"),
    ("tu22_metal", "Металлургия", "Metallurgy", 45, "домна · конвертер · прокат · цветмет", "blast furnace · converter · rolling · non-ferrous"),
    ("tu23_oil", "Нефть и газ", "Oil and gas", 30, "бурение · добыча · НПЗ · газопереработка", "drilling · production · refinery · gas plant"),
    ("tu24_chem", "Химия", "Chemistry", 23, "пиролиз · хлор · аммиак · метанол · кремний", "pyrolysis · chlorine · ammonia · methanol · silicon"),
    ("tu25_fuel", "Ядерное топливо", "Nuclear fuel", 27, "передел урана · обогащение · PUREX · РАО", "uranium · enrichment · PUREX · waste"),
    ("tu26_npp", "АЭС", "Nuclear power", 16, "АМ-1 · ВВЭР · CANDU · БН · TRIGA", "AM-1 · VVER · CANDU · fast reactor · TRIGA"),
    ("tu27_ic", "Автоматика", "Automation", 14, "реле · аналоговые машины · ПЛК · SCADA", "relays · analog computers · PLCs · SCADA"),
    ("tu28_ops", "Эксплуатация", "Operations", 21, "водоподготовка · очистка газов · биржа", "water treatment · flue gas cleaning · market"),
]
# finale: the plant at work; (shot, bars, offset, (ru, en) caption, (ru, en) note, optional (t, ru, en) alarm)
WORKS = [
    ("wk02_pyrolysis", 2, 0.4, ("Пиролиз", "Pyrolysis"), ("пропан → этилен и пропилен", "propane → ethylene and propylene"), None),
    ("wk03_combined", 2, 0.3, ("Парогазовый цикл", "Combined cycle"), ("выхлоп ГТУ греет котёл-утилизатор", "turbine exhaust heats the recovery boiler"), None),
    ("tr15_cage", 2, 0.8, ("Шахтный подъём", "Mine hoist"), ("клеть уходит в ствол", "the cage goes down the shaft"), None),
    ("wk04_drill_panel", 3, 0.3, ("Пульт бурильщика", "Driller's console"), ("приток газа: растёт объём в ёмкостях", "gas influx: the pits gain volume"),
     (2.5, "ПВО закрыт", "BOP closed")),
    ("wk05_bop", 2, 0.3, ("Устье скважины", "Wellhead"), ("газ прорвался к устью и горит", "gas reached the wellhead and burns"), None),
    ("wk06_reactor_panel", 3, 0.3, ("ВВЭР · 50 %", "VVER · 50 %"), ("ПГ не забирает тепло: давление растёт", "no steam generator load: pressure climbs"),
     (1.0, "АЗ: давление высокое", "SCRAM: high pressure")),
    ("wk07_reactor_hall", 2, 0.4, ("Реакторный зал", "Reactor hall"), None, None),
    ("wk08_cascade", 2, 0.4, ("Каскад центрифуг", "Centrifuge cascade"), ("обогащение UF₆ идёт", "enriching UF₆"), None),
        ("tr05_panel_boiler", 3, 0.3, ("Щит котла", "Boiler panel"), ("розжиг топки с щита", "lighting the furnace from the panel"), None),
]
OUTRO = ("wk10_aerial_out", 3, 0.5)
END_BARS = 2

T = {
    "ru": {"tag": "Minecraft 1.21.1 · NeoForge · Create · CC: Tweaked", "sub": "Весь завод. Все машины мода.",
           "count_word": "машин в одном мире", "end_sub": "Нужны: NeoForge 21.1, Create, CC: Tweaked",
           "end_next": "Дальше: видеогайд по эрам", "district": "участок", "works": "Завод в работе"},
    "en": {"tag": "Minecraft 1.21.1 · NeoForge · Create · CC: Tweaked", "sub": "The whole plant. Every machine in the mod.",
           "count_word": "machines in one world", "end_sub": "Requires NeoForge 21.1, Create, CC: Tweaked",
           "end_next": "Next: the guide, era by era", "district": "district", "works": "The plant at work"},
}


def ru_machines(n):
    if n % 10 == 1 and n % 100 != 11:
        return "машина"
    if 2 <= n % 10 <= 4 and not 12 <= n % 100 <= 14:
        return "машины"
    return "машин"


def esc(s):
    return html.escape(s, quote=True)


def build(lang):
    t = T[lang]
    L = 0 if lang == "ru" else 1
    clips, overlays, js = [], [], []
    at = 0.0
    n = 0

    def video(shot, bars, off):
        nonlocal at, n
        dur = bars * BAR
        vid = f"v{n}"
        clips.append(f'<div class="cam" id="cam{n}"><div class="whip" id="whip{n}"><video id="{vid}" class="clip" src="assets/footage/{shot}.mp4" '
                     f'data-start="{at:.4f}" data-duration="{dur:.4f}" data-media-start="{off}" '
                     f'data-track-index="{n % 2}" muted playsinline></video></div></div>')
        # slow push on every shot, a short whip settle on the cut
        js.append(f'tl.fromTo("#cam{n}", {{scale: 1.0}}, {{scale: 1.05, duration: {dur:.4f}, ease: "none"}}, {at:.4f});')
        if n:
            js.append(f'tl.fromTo("#whip{n}", {{xPercent: 6, filter: "blur(18px)"}}, '
                      f'{{xPercent: 0, filter: "blur(0px)", duration: 0.32, ease: "expo.out"}}, {at:.4f});')
        start = at
        at += dur
        n += 1
        return start, dur

    # intro
    s, d = video(*INTRO)
    overlays.append(f'''<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{d:.4f}" data-track-index="3">
  <div class="tag mono" id="i-tag">{esc(t["tag"])}</div>
  <h1 class="title" id="i-title"><span>PROMPT</span> <span>CRITICAL</span></h1>
  <div class="rule" id="i-rule"></div>
  <p class="sub" id="i-sub">{esc(t["sub"])}</p>
</section>''')
    js += [f'tl.from("#i-tag", {{opacity: 0, y: -20, duration: 0.5, ease: "power2.out"}}, {s + 0.3:.3f});',
           f'tl.from("#i-title span", {{yPercent: 110, opacity: 0, duration: 0.7, ease: "power4.out", stagger: 0.12}}, {s + 0.5:.3f});',
           f'tl.from("#i-rule", {{scaleX: 0, duration: 0.6, ease: "expo.out"}}, {s + 1.1:.3f});',
           f'tl.from("#i-sub", {{opacity: 0, x: -30, duration: 0.5, ease: "power3.out"}}, {s + 1.4:.3f});',
           f'tl.to("#i-title, #i-sub, #i-tag, #i-rule", {{opacity: 0, duration: 0.3}}, {s + d - 0.35:.3f});']

    # hero shots of the hand-built sets
    for i, (shot, bars, off, label, note) in enumerate(HEROES):
        s, d = video(shot, bars, off)
        code, name = label[0], label[1 + L]
        sub = f'<p class="cap-sub">{esc(note[L])}</p>' if note else ""
        overlays.append(f'''<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{d:.4f}" data-track-index="3">
  <div class="cap" id="h{i}"><span class="code mono">{esc(code)}</span><div><p class="cap-name">{esc(name)}</p>{sub}</div></div>
</section>''')
        js += [f'tl.from("#h{i}", {{clipPath: "inset(0 100% 0 0)", duration: 0.45, ease: "expo.out"}}, {s + 0.12:.3f});',
               f'tl.from("#h{i} .code", {{scale: 1.6, opacity: 0, duration: 0.35, ease: "back.out(2)"}}, {s + 0.12:.3f});']

    # districts of the plant
    total = sum(dd[3] for dd in DISTRICTS)
    for i, (shot, ru, en, cnt, ru_sub, en_sub) in enumerate(DISTRICTS):
        s, d = video(shot, 2, 0.4)
        name, sub = (ru, ru_sub) if lang == "ru" else (en, en_sub)
        word = ru_machines(cnt) if lang == "ru" else "machines"
        overlays.append(f'''<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{d:.4f}" data-track-index="3">
  <div class="dist" id="d{i}">
    <div class="d-top mono"><span class="d-idx">{i + 1:02d}<em>/{len(DISTRICTS):02d}</em></span><span class="d-word">{esc(t["district"])}</span></div>
    <p class="d-name">{esc(name)}</p>
    <div class="rule d-rule"></div>
    <p class="d-sub mono">{esc(sub)}</p>
  </div>
  <div class="cnt" id="c{i}"><span class="cnt-n mono" data-n="{cnt}">{cnt}</span><span class="cnt-w">{esc(word)}</span></div>
</section>''')
        js += [f'tl.from("#d{i} .d-top", {{opacity: 0, x: -24, duration: 0.35, ease: "power3.out"}}, {s + 0.1:.3f});',
               f'tl.from("#d{i} .d-name", {{yPercent: 100, opacity: 0, duration: 0.55, ease: "power4.out"}}, {s + 0.18:.3f});',
               f'tl.from("#d{i} .d-rule", {{scaleX: 0, duration: 0.5, ease: "expo.out"}}, {s + 0.4:.3f});',
               f'tl.from("#d{i} .d-sub", {{opacity: 0, y: 14, duration: 0.4, ease: "power2.out"}}, {s + 0.55:.3f});',
               f'tl.from("#c{i}", {{opacity: 0, y: 30, duration: 0.4, ease: "power3.out"}}, {s + 0.3:.3f});',
               f'tl.from("#c{i} .cnt-n", {{textContent: 0, duration: 0.9, ease: "power2.out", snap: {{textContent: 1}}}}, {s + 0.3:.3f});',
               f'tl.to("#d{i}, #c{i}", {{opacity: 0, duration: 0.2}}, {s + d - 0.22:.3f});']

    # the plant at work: section title over the aerial, then the running units
    s, d = video("wk01_aerial", 2, 0.5)
    overlays.append(f'''<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{d:.4f}" data-track-index="3">
  <h2 class="works-title" id="w-title">{esc(t["works"])}</h2>
</section>''')
    js += [f'tl.from("#w-title", {{clipPath: "inset(0 100% 0 0)", duration: 0.6, ease: "expo.out"}}, {s + 0.15:.3f});']
    for i, (shot, bars, off, cap, note, alarm) in enumerate(WORKS):
        s, d = video(shot, bars, off)
        sub = f'<p class="cap-sub">{esc(note[L])}</p>' if note else ""
        al = f'<div class="alarm" id="wa{i}">{esc(alarm[1 + L])}</div>' if alarm else ""
        overlays.append(f'''<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{d:.4f}" data-track-index="3">
  <div class="cap" id="w{i}"><span class="code mono">●</span><div><p class="cap-name">{esc(cap[L])}</p>{sub}</div></div>
  {al}
</section>''')
        js += [f'tl.from("#w{i}", {{clipPath: "inset(0 100% 0 0)", duration: 0.45, ease: "expo.out"}}, {s + 0.12:.3f});',
               f'tl.fromTo("#w{i} .code", {{opacity: 1}}, {{opacity: 0.25, duration: 0.46, repeat: {int(d / 0.92)}, yoyo: true, ease: "none"}}, {s:.3f});']
        if alarm:
            ta = s + alarm[0]
            js += [f'tl.from("#wa{i}", {{scale: 1.4, opacity: 0, duration: 0.25, ease: "back.out(2)"}}, {ta:.3f});',
                   f'tl.fromTo("#wa{i}", {{backgroundColor: "#e5402a"}}, {{backgroundColor: "#7a1d12", duration: 0.23, repeat: {int((s + d - ta) / 0.46)}, yoyo: true, ease: "none"}}, {ta + 0.25:.3f});']

    # outro aerial with the total, then the end card
    s, d = video(*OUTRO)
    word = (ru_machines(total) if lang == "ru" else "machines")
    overlays.append(f'''<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{d:.4f}" data-track-index="3">
  <div class="total" id="tot"><span class="tot-n mono">{total}</span><span class="tot-w">{esc(word + " " + t["count_word"].split(" ", 1)[1] if lang == "ru" else t["count_word"])}</span></div>
</section>''')
    js += [f'tl.from("#tot", {{opacity: 0, scale: 0.92, duration: 0.6, ease: "power3.out"}}, {s + 0.3:.3f});',
           f'tl.from("#tot .tot-n", {{textContent: 0, duration: 1.6, ease: "power2.out", snap: {{textContent: 1}}}}, {s + 0.3:.3f});']
    end_s, end_d = at, END_BARS * BAR
    overlays.append(f'''<section id="end{n}" class="clip end" data-start="{end_s:.4f}" data-duration="{end_d:.4f}" data-track-index="4">
  <div class="grid-bg"></div>
  <img id="e-logo" src="assets/logo.png" alt="">
  <h2 class="e-title" id="e-title">PROMPT CRITICAL <span class="mono">1.0.0-rc.1</span></h2>
  <p class="e-sub mono" id="e-sub">{esc(t["end_sub"])}</p>
  <p class="e-next" id="e-next">{esc(t["end_next"])}</p>
</section>''')
    js += [f'tl.from("#e-logo", {{scale: 0.6, opacity: 0, duration: 0.6, ease: "back.out(1.6)"}}, {end_s + 0.05:.3f});',
           f'tl.from("#e-title", {{y: 40, opacity: 0, duration: 0.5, ease: "power3.out"}}, {end_s + 0.25:.3f});',
           f'tl.from("#e-sub", {{opacity: 0, duration: 0.4}}, {end_s + 0.55:.3f});',
           f'tl.from("#e-next", {{opacity: 0, x: -20, duration: 0.4, ease: "power2.out"}}, {end_s + 0.8:.3f});',
           f'tl.fromTo(".grid-bg", {{backgroundPosition: "0px 0px"}}, {{backgroundPosition: "0px 80px", duration: {end_d:.3f}, ease: "none"}}, {end_s:.3f});']
    total_d = end_s + end_d
    fade = {"version": 1, "lanes": [{"target": "volume", "points": [
        {"t": 0, "v": 0.9}, {"t": round(total_d - 2.2, 3), "v": 0.9}, {"t": round(total_d, 3), "v": 0}]}]}
    music = (f"<audio id=\"music\" src=\"assets/music.ogg\" data-start=\"0\" data-duration=\"{total_d:.4f}\" "
             f"data-track-index=\"9\" data-volume=\"0.9\" data-automation='{json.dumps(fade)}'></audio>")
    page = TEMPLATE.replace("%LANG%", lang).replace("%DUR%", f"{total_d:.4f}")
    page = page.replace("%CLIPS%", "\n      ".join(clips)).replace("%OVERLAYS%", "\n".join(overlays))
    page = page.replace("%MUSIC%", music).replace("%JS%", "\n      ".join(js))
    with open(os.path.join(HERE, "index.html"), "w", encoding="utf-8") as fh:
        fh.write(page)
    print(f"{lang}: {n} shots, {total_d:.2f} s")


TEMPLATE = """<!doctype html>
<html lang="%LANG%">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1920, height=1080" />
    <title>Prompt Critical: the whole plant</title>
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
    <style>
      :root { --bg: #14110d; --fg: #efe6d6; --accent: #f2b705; --muted: #cfc5b3; }
      * { margin: 0; padding: 0; box-sizing: border-box; }
      html, body { width: 1920px; height: 1080px; overflow: hidden; background: var(--bg); }
      #root { position: relative; width: 100%; height: 100%; overflow: hidden; background: var(--bg);
              font-family: "Oswald", sans-serif; color: var(--fg); }
      .mono { font-family: "IBM Plex Mono", monospace; }
      .cam, .whip { position: absolute; inset: 0; overflow: hidden; }
      .cam video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
      .ov { position: absolute; inset: 0; }
      .ov::before { content: ""; position: absolute; inset: 0;
                    background: radial-gradient(130% 100% at 0% 100%, rgba(20,17,13,0.92) 0%, rgba(20,17,13,0.55) 45%, rgba(20,17,13,0) 75%); }
      .rule { height: 4px; background: var(--accent); transform-origin: left center; }
      /* intro */
      .tag { position: absolute; left: 110px; top: 90px; font-size: 26px; letter-spacing: 0.08em; color: var(--fg);
             text-transform: uppercase; background: rgba(20,17,13,0.7); padding: 10px 18px; }
      .title { position: absolute; left: 104px; bottom: 250px; font-size: 190px; line-height: 1.2; font-weight: 700;
               letter-spacing: 0.01em; background: rgba(20,17,13,0.78); padding: 0 30px; }
      .title span { display: inline-block; }
      .title span:last-child { color: var(--accent); }
      #i-rule { position: absolute; left: 110px; bottom: 214px; width: 760px; }
      .sub { position: absolute; left: 110px; bottom: 130px; font-size: 52px; font-weight: 500; }
      /* hero caption */
      .cap { position: absolute; left: 110px; bottom: 110px; display: flex; align-items: center; gap: 30px;
             background: rgba(20,17,13,0.86); padding: 22px 40px 24px 22px; border-left: 0; }
      .code { display: block; background: var(--accent); color: var(--bg); font-size: 44px; font-weight: 600;
              padding: 10px 18px; }
      .cap-name { font-size: 72px; font-weight: 700; line-height: 1.25; text-transform: uppercase; }
      .cap-sub { font-size: 32px; color: var(--muted); margin-top: 8px; font-family: "IBM Plex Mono", monospace; }
      /* district */
      .dist { position: absolute; left: 110px; bottom: 105px; width: 1100px; }
      .d-top { display: flex; align-items: baseline; gap: 24px; font-size: 30px; line-height: 1.4; text-transform: uppercase;
               letter-spacing: 0.1em; color: var(--accent); margin-bottom: 12px; }
      .d-idx { font-size: 44px; font-weight: 600; line-height: 1.3; }
      .d-idx em { font-style: normal; color: var(--muted); font-size: 30px; }
      .d-word { color: var(--fg); }
      .d-name { font-size: 150px; font-weight: 700; line-height: 1.3; text-transform: uppercase; }
      .d-rule { width: 520px; margin: 6px 0 22px; }
      .d-sub { font-size: 32px; color: var(--fg); background: rgba(20,17,13,0.72); display: inline-block; padding: 8px 16px; }
      .cnt { position: absolute; right: 110px; bottom: 110px; text-align: right; background: rgba(20,17,13,0.86);
             padding: 18px 34px 22px; }
      .cnt-n { display: block; font-size: 120px; font-weight: 600; line-height: 1.3; color: var(--accent); }
      .cnt-w { display: block; font-size: 34px; line-height: 1.3; text-transform: uppercase; letter-spacing: 0.06em; }
      /* outro */
      .total { position: absolute; left: 110px; bottom: 120px; display: flex; align-items: baseline; gap: 34px;
               background: rgba(20,17,13,0.82); padding: 24px 44px; }
      .tot-n { font-size: 200px; font-weight: 600; line-height: 1.25; color: var(--accent); }
      .tot-w { font-size: 64px; font-weight: 600; text-transform: uppercase; max-width: 760px; line-height: 1.05; }
      /* works */
      .works-title { position: absolute; left: 110px; bottom: 120px; font-size: 170px; font-weight: 700; line-height: 1.25;
                     text-transform: uppercase; background: var(--accent); color: var(--bg); padding: 0 40px; }
      .alarm { position: absolute; right: 110px; top: 100px; font-family: "IBM Plex Mono", monospace; font-size: 54px;
               font-weight: 600; color: #fff6ec; background: #e5402a; padding: 16px 34px; text-transform: uppercase;
               line-height: 1.3; }
      /* end card */
      .end { position: absolute; inset: 0; background: var(--bg); }
      .grid-bg { position: absolute; inset: 0; opacity: 0.18;
                 background-image: linear-gradient(rgba(242,183,5,0.5) 2px, transparent 2px),
                                   linear-gradient(90deg, rgba(242,183,5,0.5) 2px, transparent 2px);
                 background-size: 80px 80px; }
      #e-logo { position: absolute; left: 110px; top: 170px; width: 300px; height: 300px; object-fit: contain;
                image-rendering: pixelated; }
      .e-title { position: absolute; left: 110px; top: 500px; font-size: 140px; font-weight: 700; line-height: 1.25; }
      .e-title span { font-size: 54px; color: var(--accent); vertical-align: middle; }
      .e-sub { position: absolute; left: 114px; top: 700px; font-size: 36px; color: var(--muted); }
      .e-next { position: absolute; left: 114px; top: 800px; font-size: 56px; font-weight: 600; color: var(--accent);
                text-transform: uppercase; }
    </style>
  </head>
  <body>
    <div id="root" data-composition-id="main" data-start="0" data-duration="%DUR%" data-width="1920" data-height="1080">
      %CLIPS%
%OVERLAYS%
      %MUSIC%
    </div>
    <script>
      const tl = gsap.timeline({ paused: true });
      %JS%
      window.__timelines["main"] = tl;
    </script>
  </body>
</html>
"""

if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "ru")
