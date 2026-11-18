"""Синтезированные звуки машин: ffmpeg по рецепту -> assets/promptcritical/sounds/sfx/<key>.ogg и записи sfx.* в sounds.json.

  python art/sfx/generate.py

Ключи и субтитры — в PcSfx.java; у каждого ключа там должен быть рецепт здесь, и наоборот. Записи громкой связи
(pa.*) в sounds.json не трогаются: их пишет art/voice/generate.py.
"""

import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
ASSETS = ROOT / "pc-mod/src/main/resources/assets/promptcritical"
PC_SFX = ROOT / "pc-mod/src/main/java/dev/denfry/promptcritical/mod/sound/PcSfx.java"

# Эхо выработки: короткие отражения от крепи, быстро гаснут.
ROADWAY = "aecho=0.8:0.5:35|80:0.3|0.15"

RECIPES = {
    # Сирена газовой защиты: прямоугольная волна (жёсткий тембр электромеханического ревуна) 660/880 Гц,
    # смена тона каждые 0,25 с; срез выше 3 кГц, эхо выработки. 2 с — один цикл, шкаф повторяет его каждые 2 с.
    "agz_siren": ("aevalsrc='0.3*sgn(sin(2*PI*t*if(lt(mod(t,0.5),0.25),660,880)))':s=44100:d=2",
                  f"lowpass=f=3000,{ROADWAY},afade=t=in:d=0.02,afade=t=out:st=1.97:d=0.03"),
}


def keys_in_java():
    keys = re.findall(r'^\s+[A-Z_]+\("([a-z0-9_]+)", "', PC_SFX.read_text(encoding="utf-8"), re.M)
    if not keys:
        sys.exit("в PcSfx.java не найдено ни одного звука")
    return keys


def render(key, src, chain):
    out = ASSETS / "sounds/sfx" / f"{key}.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi", "-i", src, "-af",
                        f"{chain},loudnorm=I=-18:TP=-2,aresample=44100", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", str(out)],
                       capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"ffmpeg {key}: " + r.stderr[-800:])


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    keys = keys_in_java()
    missing, extra = set(keys) - set(RECIPES), set(RECIPES) - set(keys)
    if missing or extra:
        sys.exit(f"нет рецепта: {sorted(missing)}; нет в PcSfx: {sorted(extra)}")
    sounds_json = ASSETS / "sounds.json"
    sounds = json.loads(sounds_json.read_text(encoding="utf-8")) if sounds_json.exists() else {}
    sounds = {k: v for k, v in sounds.items() if not k.startswith("sfx.")}
    for key in keys:
        render(key, *RECIPES[key])
        sounds[f"sfx.{key}"] = {"sounds": [{"name": f"promptcritical:sfx/{key}"}], "subtitle": f"subtitles.promptcritical.sfx.{key}"}
    sounds_json.write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"готово: {len(keys)} звуков")


if __name__ == "__main__":
    main()
