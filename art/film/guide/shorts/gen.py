"""Write index.html of a vertical short (1080x1920) from the plant world footage.

    python art/film/guide/shorts/gen.py <a|b> [ru|en]

a: the plant from the coal mine to the reactor, one shot per era.
b: the rig drills into a gas cap: the kick on the driller's console, then the blowout at the wellhead.
c: a VVER-70 loses its hot leg and station power: the core melts through the vessel, hydrogen burns, the containment
   holds; the same plant with no roof lets the release go downwind; a teaser for the RBMK (footage of
   art/film/shots_accident.py in pc-mod/run-film/film/accident).

Cuts land on bars of the music (High-Energy Adventure Theme, main loop, 130 BPM, downbeat at 0): one bar = 1.846 s.
Footage: the FilmRecorder shots from `shots_plant.py --shorts` (pc-mod/run-film/film/shorts); this script copies the
ones it uses to assets/footage. Text stays inside x 130..930, y 330..1250 (`hyperframes check` safe zones of
Shorts, Reels and TikTok): the players cover the top bar, the right column of buttons and the caption at the bottom.
"""
import html
import json
import os
import shutil
import sys

BAR = 4 * 60 / 130
HERE = os.path.dirname(os.path.abspath(__file__))
FILM = os.path.join(HERE, "..", "..", "..", "..", "pc-mod", "run-film", "film")
ROOT = os.path.join(HERE, "..", "..", "..", "..")
END_BARS = 2

# shot, bars, media offset (s), era code or None, (ru, en) caption, (ru, en) note or None
SHORTS = {
    "a": {
        "hook": (("Завод от угольной шахты", "до атомной станции"), ("From a coal mine", "to a nuclear plant")),
        "hook_sub": ("в одном мире Minecraft", "in one Minecraft world"),
        "shots": [
            ("sa01_aerial", 3, 0.3, None, None, None),
            ("sa02_mine", 1, 0.8, "I0", ("Угольная шахта", "Coal mine"), ("метан и вентиляция", "methane and ventilation")),
            ("sa03_boilers", 1, 0.5, "I0", ("Котельная", "Boiler house"), None),
            ("sa04_furnace", 2, 0.6, "I1", ("Доменная печь", "Blast furnace"), ("кокс, агломерат, горячее дутьё", "coke, sinter, hot blast")),
            ("sa05_rig", 2, 0.6, "I2", ("Буровая", "Drilling rig"), ("газопроявления и ПВО", "kicks and BOP stacks")),
            ("sa06_refinery", 1, 1.0, "I2", ("НПЗ", "Refinery"), None),
            ("sa07_chem", 1, 0.8, "I3", ("Пиролиз", "Pyrolysis"), ("этилен и пропилен", "ethylene and propylene")),
            ("sa08_npp", 2, 0.6, "I4", ("АЭС с ВВЭР", "VVER nuclear plant"), None),
            ("sa09_reactor", 2, 0.2, "I4", ("Активная зона", "Reactor core"), ("аварии вплоть до плавления", "accidents up to core melt")),
            ("sa10_dusk", 2, 1.0, None, ("Всё считается по-настоящему", "Everything is simulated for real"), None),
        ],
        "alarms": {},
    },
    "b": {
        "hook": (("Что будет, если", "пробурить газовую шапку?"), ("What happens when you", "drill into a gas cap?")),
        "hook_sub": ("Minecraft, мод Prompt Critical", "Minecraft, the Prompt Critical mod"),
        "shots": [
            ("sb01_derrick", 2, 0.8, None, None, None),
            ("sb02_rotary", 1, 0.6, "I2", ("Ротор крутит колонну", "The rotary turns the string"), ("под долотом газовый пласт", "a gas layer below the bit")),
            ("sb03_console", 4, 0.2, "I2", ("Пульт бурильщика", "Driller's console"), ("выход раствора растёт: в скважину идёт газ", "mud returns climb: gas is entering the well")),
            ("sb04_blowout", 5, 0.5, None, ("Газ дошёл до устья", "The gas reached the wellhead"), ("и горит", "and burns")),
            ("sb05_after", 2, 0.5, None, ("Всё считается по-настоящему", "Everything is simulated for real"), None),
        ],
        # shot -> (seconds into the shot, ru, en): a red alarm plate
        "alarms": {"sb03_console": (1.8, "ГНВП", "KICK"), "sb04_blowout": (0.6, "ОТКРЫТЫЙ ФОНТАН", "BLOWOUT")},
    },
    "c": {
        "footage": "accident",
        "shade": "light",   # dark interiors and a plume in the sky: a lighter scrim behind the text
        "hook": (("Рвём главную трубу", "работающего реактора"), ("We tear the main pipe", "off a running reactor")),
        "hook_sub": ("и отключаем всё питание станции", "and cut all station power"),
        "shots": [
            ("a01_hall", 3, 0.3, None, None, None),
            ("a02_break", 2, 0.4, None, ("Течь 380 см²", "A 380 cm² break"), ("вода вскипает и уходит паром", "the water flashes to steam")),
            ("a03_steam", 2, 2.0, None, ("Зона оголилась", "The core is bare"), ("оболочки твэлов лопаются", "fuel cladding bursts")),
            ("a04_pour", 3, 17.8, None, ("Корпус проплавлен", "The vessel melts through"), ("кориум, около 2500 °C", "corium at about 2500 °C")),
            ("a05_burn", 2, 17.0, None, ("Горит водород", "Hydrogen burns"), ("из циркония оболочек", "from the zirconium cladding")),
            ("a06_corium", 2, 23.0, None, ("Кориум ест бетон", "Corium eats the concrete"), ("и даёт ещё водород", "and gives off more hydrogen")),
            ("a07_held", 2, 26.0, None, ("Гермооболочка выдержала", "The containment held"), ("наружу ничего не вышло", "nothing got out")),
            ("a10_open", 2, 20.0, None, ("А если крыши нет?", "And with no roof?"), ("как у первых станций", "like the first plants")),
            ("a11_plume", 3, 24.0, None, ("Выброс уходит по ветру", "The release goes downwind"), ("и цезий-137 оседает на землю", "and caesium-137 settles on the ground")),
            ("a90_teaser", 3, 30.0, None, ("У РБМК полной оболочки не было", "The RBMK had no full containment"), ("он следующий", "it is next")),
        ],
        "alarms": {"a02_break": (0.3, "РАЗРЫВ ТРУБЫ", "PIPE BREAK"), "a04_pour": (0.4, "ПРОПЛАВЛЕНИЕ", "MELT-THROUGH")},
    },
}

T = {
    "ru": {"end_sub": "Нужны: NeoForge 21.1, Create, CC: Tweaked", "end_cta": "Ссылка в описании"},
    "en": {"end_sub": "Requires NeoForge 21.1, Create, CC: Tweaked", "end_cta": "Link in the description"},
}
ERAS = ["I0", "I1", "I2", "I3", "I4"]


def esc(s):
    return html.escape(s, quote=True)


def version():
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as fh:
        for line in fh:
            if line.startswith("mod_version="):
                return line.split("=", 1)[1].strip()
    return ""


def stage(shots, footage):
    """Copy the footage this short uses next to the composition (the render reads it from assets/footage)."""
    dst = os.path.join(HERE, "assets", "footage")
    os.makedirs(dst, exist_ok=True)
    for shot in shots:
        src = os.path.join(FILM, footage, shot + ".mp4")
        out = os.path.join(dst, shot + ".mp4")
        if not os.path.exists(src):
            sys.exit(f"missing footage {src}: record it (shots_plant.py --shorts, shots_accident.py)")
        if not os.path.exists(out) or os.path.getmtime(out) < os.path.getmtime(src):
            shutil.copy2(src, out)


def build(which, lang):
    spec, t = SHORTS[which], T[lang]
    L = 0 if lang == "ru" else 1
    stage([s[0] for s in spec["shots"]], spec.get("footage", "shorts"))
    clips, overlays, js = [], [], []
    at = 0.0
    eras = which == "a"
    for n, (shot, bars, off, code, cap, note) in enumerate(spec["shots"]):
        dur = bars * BAR
        s = at
        clips.append(f'<div class="cam" id="cam{n}"><div class="whip" id="whip{n}"><video id="v{n}" class="clip" '
                     f'src="assets/footage/{shot}.mp4" data-start="{s:.4f}" data-duration="{dur:.4f}" '
                     f'data-media-start="{off}" data-track-index="{n % 2}" muted playsinline></video></div></div>')
        js.append(f'tl.fromTo("#cam{n}", {{scale: 1.0}}, {{scale: 1.06, duration: {dur:.4f}, ease: "none"}}, {s:.4f});')
        if n:
            js.append(f'tl.fromTo("#whip{n}", {{yPercent: -5, filter: "blur(16px)"}}, '
                      f'{{yPercent: 0, filter: "blur(0px)", duration: 0.3, ease: "expo.out"}}, {s:.4f});')
        body = []
        if n == 0:
            h1, h2 = spec["hook"][L]
            body.append(f'<div class="hook" id="hook"><p class="h-line">{esc(h1)}</p>'
                        f'<p class="h-line h-accent">{esc(h2)}</p><div class="rule" id="h-rule"></div>'
                        f'<p class="h-sub mono">{esc(spec["hook_sub"][L])}</p></div>')
            js += [f'tl.from("#hook .h-line", {{yPercent: 60, opacity: 0, duration: 0.5, ease: "power4.out", stagger: 0.18}}, {s + 0.1:.3f});',
                   f'tl.from("#h-rule", {{scaleX: 0, duration: 0.5, ease: "expo.out"}}, {s + 0.6:.3f});',
                   f'tl.from("#hook .h-sub", {{opacity: 0, duration: 0.4}}, {s + 0.8:.3f});',
                   f'tl.to("#hook", {{opacity: 0, duration: 0.25}}, {s + dur - 0.3:.3f});']
        if eras and code:
            k = ERAS.index(code)
            cells = "".join(f'<span class="era{" on" if i == k else ""}{" past" if i < k else ""}">{e}</span>'
                            for i, e in enumerate(ERAS))
            body.append(f'<div class="eras mono" id="eras{n}">{cells}</div>')
            js.append(f'tl.from("#eras{n} .on", {{scale: 1.5, duration: 0.35, ease: "back.out(2)"}}, {s + 0.05:.3f});')
        if cap:
            badge = f'<span class="code mono">{esc(code)}</span>' if code else ""
            sub = f'<p class="cap-sub mono">{esc(note[L])}</p>' if note else ""
            cls = "cap big" if not code else "cap"
            body.append(f'<div class="{cls}" id="c{n}">{badge}<p class="cap-name">{esc(cap[L])}</p>{sub}</div>')
            js.append(f'tl.from("#c{n}", {{clipPath: "inset(0 100% 0 0)", duration: 0.45, ease: "expo.out"}}, {s + 0.12:.3f});')
        alarm = spec["alarms"].get(shot)
        if alarm:
            ta = s + alarm[0]
            body.append(f'<div class="alarm mono" id="a{n}">{esc(alarm[1 + L])}</div>')
            js += [f'tl.from("#a{n}", {{scale: 1.5, opacity: 0, duration: 0.25, ease: "back.out(2)"}}, {ta:.3f});',
                   f'tl.fromTo("#a{n}", {{backgroundColor: "#e5402a"}}, {{backgroundColor: "#7a1d12", duration: 0.23, '
                   f'repeat: {int((s + dur - ta) / 0.46)}, yoyo: true, ease: "none"}}, {ta + 0.25:.3f});']
        if body:
            overlays.append(f'<section id="ov{n}" class="clip ov" data-start="{s:.4f}" data-duration="{dur:.4f}" '
                            f'data-track-index="3">\n  ' + "\n  ".join(body) + '\n</section>')
        at += dur

    end_s, end_d = at, END_BARS * BAR
    overlays.append(f'''<section id="end" class="clip end" data-start="{end_s:.4f}" data-duration="{end_d:.4f}" data-track-index="4">
  <div class="grid-bg"></div>
  <img id="e-logo" src="assets/logo.png" alt="">
  <h2 class="e-title" id="e-title">PROMPT<br>CRITICAL</h2>
  <p class="e-ver mono" id="e-ver">{esc(version())}</p>
  <p class="e-sub mono" id="e-sub">{esc(t["end_sub"])}</p>
  <p class="e-cta" id="e-cta">{esc(t["end_cta"])}</p>
</section>''')
    js += [f'tl.from("#e-logo", {{scale: 0.6, opacity: 0, duration: 0.5, ease: "back.out(1.6)"}}, {end_s + 0.05:.3f});',
           f'tl.from("#e-title, #e-ver", {{y: 40, opacity: 0, duration: 0.5, ease: "power3.out", stagger: 0.1}}, {end_s + 0.2:.3f});',
           f'tl.from("#e-sub, #e-cta", {{opacity: 0, duration: 0.4, stagger: 0.15}}, {end_s + 0.5:.3f});',
           f'tl.fromTo(".grid-bg", {{backgroundPosition: "0px 0px"}}, {{backgroundPosition: "0px 80px", duration: {end_d:.3f}, ease: "none"}}, {end_s:.3f});']
    total = end_s + end_d
    fade = {"version": 1, "lanes": [{"target": "volume", "points": [
        {"t": 0, "v": 0.9}, {"t": round(total - 1.5, 3), "v": 0.9}, {"t": round(total, 3), "v": 0}]}]}
    music = (f"<audio id=\"music\" src=\"assets/music.ogg\" data-start=\"0\" data-duration=\"{total:.4f}\" "
             f"data-track-index=\"9\" data-volume=\"0.9\" data-automation='{json.dumps(fade)}'></audio>")
    page = (TEMPLATE.replace("%LANG%", lang).replace("%SHADE%", spec.get("shade", "")).replace("%DUR%", f"{total:.4f}")
            .replace("%CLIPS%", "\n      ".join(clips)).replace("%OVERLAYS%", "\n".join(overlays))
            .replace("%MUSIC%", music).replace("%JS%", "\n      ".join(js)))
    with open(os.path.join(HERE, "index.html"), "w", encoding="utf-8") as fh:
        fh.write(page)
    print(f"short {which} {lang}: {len(spec['shots'])} shots, {total:.2f} s")


TEMPLATE = """<!doctype html>
<html lang="%LANG%">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=1080, height=1920" />
    <title>Prompt Critical short</title>
    <script src="https://cdn.jsdelivr.net/npm/gsap@3.14.2/dist/gsap.min.js"></script>
    <style>
      :root { --bg: #14110d; --fg: #efe6d6; --accent: #f2b705; --muted: #cfc5b3; }
      * { margin: 0; padding: 0; box-sizing: border-box; }
      html, body { width: 1080px; height: 1920px; overflow: hidden; background: var(--bg); }
      #root { position: relative; width: 100%; height: 100%; overflow: hidden; background: var(--bg);
              font-family: "Oswald", sans-serif; color: var(--fg); }
      .mono { font-family: "IBM Plex Mono", monospace; }
      .cam, .whip { position: absolute; inset: 0; overflow: hidden; }
      .cam video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
      .ov { position: absolute; inset: 0; }
      .ov::before { content: ""; position: absolute; inset: 0;
                    background: linear-gradient(180deg, rgba(20,17,13,0.35) 0%, rgba(20,17,13,0.7) 14%, rgba(20,17,13,0.6) 36%, rgba(20,17,13,0.75) 62%, rgba(20,17,13,0) 82%); }
      .light .ov::before { background: linear-gradient(180deg, rgba(20,17,13,0.2) 0%, rgba(20,17,13,0.5) 30%, rgba(20,17,13,0.5) 55%, rgba(20,17,13,0) 75%); }
      .rule { height: 6px; width: 420px; background: var(--accent); transform-origin: left center; margin: 26px 0 22px; }
      /* hook: upper-middle, clear of the Shorts top bar */
      .hook { position: absolute; left: 130px; right: 150px; top: 340px; }
      .h-line { font-size: 86px; font-weight: 700; line-height: 1.12; text-transform: uppercase;
                text-shadow: 0 4px 18px rgba(0,0,0,0.75); }
      .h-accent { color: var(--accent); }
      .h-sub { font-size: 38px; color: var(--fg); background: rgba(20,17,13,0.78); display: inline-block; padding: 8px 18px; }
      /* era strip */
      .eras { position: absolute; left: 130px; top: 330px; display: flex; gap: 14px; }
      .era { font-size: 38px; font-weight: 600; padding: 6px 16px; color: var(--muted); background: rgba(20,17,13,0.7);
             line-height: 1.3; }
      .era.past { color: var(--fg); }
      .era.on { color: var(--bg); background: var(--accent); }
      /* caption */
      .cap { position: absolute; left: 130px; right: 150px; top: 780px; }
      .cap.big { top: 700px; }
      .code { display: inline-block; background: var(--accent); color: var(--bg); font-size: 42px; font-weight: 600;
              padding: 4px 18px; margin-bottom: 16px; line-height: 1.3; }
      .cap-name { font-size: 92px; font-weight: 700; line-height: 1.1; text-transform: uppercase;
                  text-shadow: 0 4px 18px rgba(0,0,0,0.75); }
      .cap.big .cap-name { color: var(--accent); }
      .cap-sub { font-size: 40px; color: var(--fg); margin-top: 18px; background: rgba(20,17,13,0.78);
                 display: inline-block; padding: 8px 18px; line-height: 1.35; }
      .alarm { position: absolute; left: 130px; top: 560px; font-size: 76px; font-weight: 600; color: #fff6ec;
               background: #e5402a; padding: 14px 34px; line-height: 1.3; }
      /* end card */
      .end { position: absolute; inset: 0; background: var(--bg); }
      .grid-bg { position: absolute; inset: 0; opacity: 0.18;
                 background-image: linear-gradient(rgba(242,183,5,0.5) 2px, transparent 2px),
                                   linear-gradient(90deg, rgba(242,183,5,0.5) 2px, transparent 2px);
                 background-size: 80px 80px; }
      #e-logo { position: absolute; left: 130px; top: 330px; width: 260px; height: 260px; object-fit: contain;
                image-rendering: pixelated; }
      .e-title { position: absolute; left: 130px; top: 620px; font-size: 130px; font-weight: 700; line-height: 1.05; }
      .e-ver { position: absolute; left: 136px; top: 920px; font-size: 50px; color: var(--accent); }
      .e-sub { position: absolute; left: 136px; width: 600px; top: 1000px; font-size: 34px; color: var(--muted); line-height: 1.4; }
      .e-cta { position: absolute; left: 136px; top: 1130px; font-size: 56px; font-weight: 600; color: var(--accent);
               text-transform: uppercase; }
    </style>
  </head>
  <body>
    <div id="root" class="%SHADE%" data-composition-id="main" data-start="0" data-duration="%DUR%" data-width="1080" data-height="1920">
      %CLIPS%
%OVERLAYS%
      %MUSIC%
    </div>
    <script>
      window.__timelines = window.__timelines || {};
      const tl = gsap.timeline({ paused: true });
      %JS%
      window.__timelines["main"] = tl;
    </script>
  </body>
</html>
"""

if __name__ == "__main__":
    build(sys.argv[1] if len(sys.argv) > 1 else "a", sys.argv[2] if len(sys.argv) > 2 else "ru")
