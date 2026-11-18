"""Звуки машин -> assets/promptcritical/sounds/sfx/<key>.ogg и записи sfx.* в sounds.json.

  python art/sfx/generate.py            собрать всё
  python art/sfx/generate.py --check    только проверить готовые .ogg (длительность, уровень, тишина, шов петли)

Два вида источников:
  * RECIPES — синтез ffmpeg (lavfi) по рецепту: сирена, гул электродвигателя и трансформатора;
  * art/sfx/sources.json — записи CC0 (Freesound и др.): скрипт скачивает превью в git-ignored .cache, сверяет
    sha256, режет отрезок, пропускает через фильтры, у петли сшивает конец с началом наложением (acrossfade хвоста на
    начало, без щелчка), нормализует громкость постоянным усилением, моно 44,1 кГц, Vorbis.
Происхождение каждого файла — art/sfx/SOURCES.md; брать только CC0 или public domain, лицензию проверять на странице
самого звука.

Ключи и субтитры — в PcSfx.java; у каждого ключа должен быть рецепт или источник, и наоборот. Записи громкой связи
(pa.*) в sounds.json не трогаются: их пишет art/voice/generate.py.
"""

import hashlib
import json
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
HERE = Path(__file__).resolve().parent
CACHE = HERE / ".cache"
ASSETS = ROOT / "pc-mod/src/main/resources/assets/promptcritical"
OUT = ASSETS / "sounds/sfx"
PC_SFX = ROOT / "pc-mod/src/main/java/dev/denfry/promptcritical/mod/sound/PcSfx.java"
RATE = 44100
TARGET_RMS, PEAK_CEIL = -18.0, -1.0

# Эхо выработки: короткие отражения от крепи, быстро гаснут.
ROADWAY = "aecho=0.8:0.5:35|80:0.3|0.15"

# Гул сети 50 Гц: магнитострикция стали даёт 100 Гц и гармоники, у двигателя ещё 50 Гц, зубцовый тон и вентилятор.
# Длительность — целое число периодов всех тонов (2 с), поэтому петля сходится без шва.
MOTOR = ("0.18*sin(2*PI*50*t)+0.45*sin(2*PI*100*t)+0.2*sin(2*PI*200*t)+0.12*sin(2*PI*300*t)"
         "+0.05*sin(2*PI*1250*t)+0.08*(2*random(0)-1)")
TRANSFORMER = "0.55*sin(2*PI*100*t)+0.25*sin(2*PI*200*t)+0.15*sin(2*PI*300*t)+0.08*sin(2*PI*400*t)+0.03*(2*random(0)-1)"

RECIPES = {
    # Сирена газовой защиты: прямоугольная волна (жёсткий тембр электромеханического ревуна) 660/880 Гц,
    # смена тона каждые 0,25 с; срез выше 3 кГц, эхо выработки. 2 с — один цикл, шкаф повторяет его каждые 2 с.
    "agz_siren": ("aevalsrc='0.3*sgn(sin(2*PI*t*if(lt(mod(t,0.5),0.25),660,880)))':s=44100:d=2",
                  f"lowpass=f=3000,{ROADWAY},afade=t=in:d=0.02,afade=t=out:st=1.97:d=0.03", False),
    "electric_motor": (f"aevalsrc='{MOTOR}':s=44100:d=2", "lowpass=f=2500", True),
    "transformer_hum": (f"aevalsrc='{TRANSFORMER}':s=44100:d=2", "lowpass=f=1200", True),
    # Щелчок счётчика СБМ-20 в динамике дозиметра: разряд — короткий спад шума и звон корпуса 3,2 кГц, 30 мс.
    # Частоту щелчков задаёт дозиметр (пуассоновский поток ∝ мощности дозы), здесь — один щелчок.
    "geiger_click": ("aevalsrc='0.8*exp(-t*2500)*(2*random(0)-1)+0.5*exp(-t*600)*sin(2*PI*3200*t)':s=44100:d=0.03",
                     "highpass=f=500,afade=t=out:st=0.024:d=0.006", False),
}


def keys_in_java():
    keys = re.findall(r'^\s+[A-Z_]+\("([a-z0-9_]+)", "', PC_SFX.read_text(encoding="utf-8"), re.M)
    if not keys:
        sys.exit("в PcSfx.java не найдено ни одного звука")
    return keys


def sources():
    return {s["key"]: s for s in json.loads((HERE / "sources.json").read_text(encoding="utf-8"))}


def ffmpeg(args, what):
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *args], capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"ffmpeg {what}: " + r.stderr[-800:])


def finish(key, wav):
    """Нормализация постоянным усилением (RMS к -18 dBFS, пик не выше -1 dBFS) и кодирование в Vorbis.

    Усиление одно на весь файл, а не следящее, как у loudnorm в один проход: иначе громкость конца петли отличалась
    бы от начала, и на шве была бы ступенька."""
    import numpy as np
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(wav), "-f", "s16le", "-ac", "1", "-"],
                         capture_output=True).stdout
    x = np.frombuffer(raw, np.int16).astype(np.float64) / 32768
    rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
    peak = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
    gain = min(TARGET_RMS - rms, PEAK_CEIL - peak)
    ffmpeg(["-i", str(wav), "-af", f"volume={gain:.2f}dB", "-ar", str(RATE), "-ac", "1", "-c:a", "libvorbis", "-q:a", "5",
            str(OUT / f"{key}.ogg")], key)
    wav.unlink()


def tmp(key):
    CACHE.mkdir(exist_ok=True)
    return CACHE / f"{key}.render.wav"


def render_recipe(key, src, chain, loop):
    if not loop:
        ffmpeg(["-f", "lavfi", "-i", src, "-af", f"{chain},aresample={RATE}", "-ac", "1", str(tmp(key))], key)
        finish(key, tmp(key))
        return
    # петля синтеза: рендер втрое длиннее, берётся средний период (фильтры в установившемся режиме) и сшивается
    # наложением — шумовая составляющая не периодична
    d = float(re.search(r":d=([0-9.]+)", src).group(1))
    raw = CACHE / f"{key}.synth.wav"
    CACHE.mkdir(exist_ok=True)
    ffmpeg(["-f", "lavfi", "-i", re.sub(r":d=[0-9.]+", f":d={3 * d}", src), "-af", f"{chain},aresample={RATE}", "-ac", "1",
            str(raw)], key)
    loop_crossfade(key, raw, d, d, 0.25, "anull")
    raw.unlink()


def loop_crossfade(key, path, start, dur, x, chain):
    """Петля из отрезка [start, start + dur + x]: хвост x накладывается с затуханием на нарастающее начало; конец петли
    (момент dur) переходит в её начало так же, как в записи момент dur переходил в dur + x — шва нет."""
    graph = (f"[0:a]aresample={RATE},aformat=channel_layouts=mono,{chain},asplit=2[a][b];"
             f"[a]atrim=0:{dur},asetpts=PTS-STARTPTS,afade=t=in:d={x}:curve=qsin[head];"
             f"[b]atrim={dur}:{dur + x},asetpts=PTS-STARTPTS,afade=t=out:d={x}:curve=qsin,apad=whole_dur={dur}[tail];"
             f"[head][tail]amix=inputs=2:duration=first:normalize=0")
    ffmpeg(["-ss", str(start), "-t", str(dur + x + 0.05), "-i", str(path), "-filter_complex", graph, str(tmp(key))], key)
    finish(key, tmp(key))


def fetch(s):
    """Превью из кэша или из сети; хэш обязан совпасть с sources.json."""
    CACHE.mkdir(exist_ok=True)
    path = CACHE / s["preview"].rsplit("/", 1)[1]
    if not path.exists():
        req = urllib.request.Request(s["preview"], headers={"User-Agent": "Mozilla/5.0"})
        path.write_bytes(urllib.request.urlopen(req, timeout=60).read())
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != s["sha256"]:
        sys.exit(f"{s['key']}: sha256 {digest} != {s['sha256']} — файл на сервере изменился, проверьте лицензию заново")
    return path


def render_source(s):
    path = fetch(s)
    start, dur, chain = s["start"], s["duration"], s["chain"]
    if not s["loop"]:
        ffmpeg(["-ss", str(start), "-t", str(dur), "-i", str(path), "-af",
                f"aresample={RATE},aformat=channel_layouts=mono,{chain},afade=t=in:d=0.005", str(tmp(s["key"]))], s["key"])
        finish(s["key"], tmp(s["key"]))
        return
    loop_crossfade(s["key"], path, start, dur, s["crossfade"], chain)


def check(key, loop, steady=True):
    """Длительность, уровень, тишина, скачок на шве петли."""
    import numpy as np
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(OUT / f"{key}.ogg"), "-f", "s16le", "-ac", "1", "-"],
                         capture_output=True).stdout
    x = np.frombuffer(raw, np.int16).astype(np.float64) / 32768
    rms = np.sqrt(np.mean(x ** 2))
    win = int(RATE * 0.05)
    frames = [np.sqrt(np.mean(x[i:i + win] ** 2)) for i in range(0, len(x) - win, win)]
    silent = sum(1 for f in frames if f < rms * 0.03)
    seam = ""
    problems = []
    if loop:
        # скачок отсчёта через шов против типичного скачка соседних отсчётов внутри петли
        jump = abs(x[0] - x[-1])
        typical = np.percentile(np.abs(np.diff(x)), 99.9)
        edge = np.sqrt(np.mean(x[:win] ** 2)) / (np.sqrt(np.mean(x[-win:] ** 2)) + 1e-12)
        seam = f" шов {jump:.3f} (p99.9 {typical:.3f}), уровень на стыке {20 * np.log10(edge):+.1f} дБ"
        if jump > 2 * typical:
            problems.append("щелчок на шве")
        if steady and abs(20 * np.log10(edge)) > 3:
            problems.append("ступенька уровня на шве")
    if silent > len(frames) * 0.2 and loop:
        problems.append("много тишины")
    print(f"{key:20s} {len(x) / RATE:5.2f} с  RMS {20 * np.log10(rms + 1e-12):6.1f} dBFS  "
          f"пик {20 * np.log10(np.max(np.abs(x)) + 1e-12):5.1f}  тихих окон {silent}/{len(frames)}{seam}"
          + (f"  !! {', '.join(problems)}" if problems else ""))
    return not problems


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    keys = keys_in_java()
    src = sources()
    both = set(RECIPES) & set(src)
    missing, extra = set(keys) - set(RECIPES) - set(src), (set(RECIPES) | set(src)) - set(keys)
    if missing or extra or both:
        sys.exit(f"нет рецепта: {sorted(missing)}; нет в PcSfx: {sorted(extra)}; два источника: {sorted(both)}")
    loops = {k: RECIPES[k][2] if k in RECIPES else src[k]["loop"] for k in keys}
    if "--check" not in sys.argv:
        OUT.mkdir(parents=True, exist_ok=True)
        for key in keys:
            if key in RECIPES:
                render_recipe(key, *RECIPES[key])
            else:
                render_source(src[key])
        sounds_json = ASSETS / "sounds.json"
        sounds = json.loads(sounds_json.read_text(encoding="utf-8")) if sounds_json.exists() else {}
        sounds = {k: v for k, v in sounds.items() if not k.startswith("sfx.")}
        for key in keys:
            sounds[f"sfx.{key}"] = {"sounds": [{"name": f"promptcritical:sfx/{key}"}], "subtitle": f"subtitles.promptcritical.sfx.{key}"}
        sounds_json.write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    steady = {k: src[k].get("steady", True) if k in src else True for k in keys}
    ok = all([check(k, loops[k], steady[k]) for k in keys])
    print(f"готово: {len(keys)} звуков" if ok else "есть замечания")
    if not ok:
        sys.exit(1)


if __name__ == "__main__":
    main()
