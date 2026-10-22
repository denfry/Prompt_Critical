"""Озвучка через ElevenLabs: громкая связь мода и дикторский текст роликов.

Ключ берётся из ELEVENLABS_API_KEY или из .env.local в корне репозитория.
Ответы API кэшируются в art/voice/.cache по хэшу (модель, голос, настройки, текст):
повторный запуск платит только за изменённые фразы.

  python art/voice/generate.py pa [--force]
      Фразы из PaLine.java -> assets/promptcritical/sounds/pa/<язык>/*.ogg и sounds.json.
      Голос пропущен через фильтр громкоговорителя, перед фразой гонг или сигнал тревоги.
  python art/voice/generate.py narrate script.txt -o voice.mp3 [--lang en_us] [--voice ID]
      Дикторский текст: абзацы через пустую строку, между абзацами пауза.
  python art/voice/generate.py samples --lang ru_ru --voices ID1,ID2 --text "..." -o DIR [--pa]
      Одна фраза разными голосами, чтобы выбрать голос.
  python art/voice/generate.py voices
      Голоса аккаунта и остаток символов.
"""

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
CACHE = HERE / ".cache"
CONFIG = json.loads((HERE / "voice.json").read_text(encoding="utf-8"))
PA_LINE = ROOT / "pc-mod/src/main/java/dev/denfry/promptcritical/mod/pa/PaLine.java"
ASSETS = ROOT / "pc-mod/src/main/resources/assets/promptcritical"
API = "https://api.elevenlabs.io"

# Громкоговоритель: узкая полоса, компрессия, короткое эхо цеха.
PA_FILTER = ("aresample=44100,aformat=channel_layouts=mono,highpass=f=280,lowpass=f=3800,"
             "acompressor=threshold=-20dB:ratio=4:attack=5:release=80,"
             "aecho=0.8:0.6:45|90:0.22|0.12")
# Гонг «динь-дон» перед объявлением и прерывистый сигнал перед тревогой.
GONG = "aevalsrc='0.5*sin(2*PI*t*if(lt(t,0.45),880,698))*exp(-3.5*mod(t,0.45))':s=44100:d=0.9"
ALARM = "aevalsrc='0.4*if(lt(mod(t,0.2),0.14),sin(2*PI*t*1050),0)':s=44100:d=0.6"
PA_VERSION = "1"


def api_key():
    key = os.environ.get("ELEVENLABS_API_KEY")
    env = ROOT / ".env.local"
    if not key and env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.strip().startswith("ELEVENLABS_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"')
    if not key:
        sys.exit("ELEVENLABS_API_KEY не задан (переменная окружения или .env.local)")
    return key


def request(path, body=None):
    req = urllib.request.Request(API + path, method="POST" if body is not None else "GET",
                                 headers={"xi-api-key": api_key(), "Content-Type": "application/json"},
                                 data=json.dumps(body).encode() if body is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        sys.exit(f"ElevenLabs {e.code}: {e.read().decode(errors='replace')[:400]}")


def tts(text, voice, model=None, prev=None, nxt=None):
    """MP3 фразы из кэша или из API."""
    model = model or CONFIG["model"]
    settings = CONFIG["settings"]
    h = hashlib.sha1(json.dumps([model, voice, settings, text, prev, nxt], ensure_ascii=False).encode()).hexdigest()
    path = CACHE / f"{h}.mp3"
    if not path.exists():
        body = {"text": text, "model_id": model, "voice_settings": settings}
        if prev:
            body["previous_text"] = prev
        if nxt:
            body["next_text"] = nxt
        print(f"  tts {len(text):4d} симв.: {text[:60]}")
        data = request(f"/v1/text-to-speech/{voice}?output_format=mp3_44100_128", body)
        CACHE.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)
    return path


def ffmpeg(*args):
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *args], capture_output=True, text=True)
    if r.returncode:
        sys.exit("ffmpeg: " + r.stderr[-800:])


def pa_render(mp3, out, alarm):
    out.parent.mkdir(parents=True, exist_ok=True)
    ffmpeg("-f", "lavfi", "-i", ALARM if alarm else GONG, "-i", str(mp3), "-filter_complex",
           f"[1:a]{PA_FILTER},apad=pad_dur=0.5[v];[0:a]aformat=channel_layouts=mono,apad=pad_dur=0.15[c];"
           "[c][v]concat=n=2:v=0:a=1,loudnorm=I=-16:TP=-1.5,aresample=44100[o]",
           "-map", "[o]", "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", str(out))


def pa_lines():
    """(key, alarm, {язык: текст}) из PaLine.java."""
    rx = re.compile(r'^\s+[A-Z_]+\("([a-z0-9_.]+)", (true|false), "((?:[^"\\]|\\.)*)", "((?:[^"\\]|\\.)*)"\)[,;]', re.M)
    lines = [(m[1], m[2] == "true", {"en_us": m[3], "ru_ru": m[4]}) for m in rx.finditer(PA_LINE.read_text(encoding="utf-8"))]
    if not lines:
        sys.exit("в PaLine.java не найдено ни одной фразы")
    return lines


def cmd_pa(args):
    sounds = {}
    for key, alarm, texts in pa_lines():
        for lang, text in texts.items():
            say = CONFIG["say"].get(lang, {}).get(key, text)
            voice = CONFIG["voices"][lang]
            mp3 = tts(say, voice)
            rel = f"pa/{lang}/{key.replace('.', '/')}"
            out = ASSETS / "sounds" / f"{rel}.ogg"
            stamp = CACHE / "pa" / f"{lang}.{key}.stamp"
            mark = f"{mp3.stem} {alarm} {PA_VERSION}"
            if args.force or not out.exists() or not stamp.exists() or stamp.read_text() != mark:
                pa_render(mp3, out, alarm)
                stamp.parent.mkdir(parents=True, exist_ok=True)
                stamp.write_text(mark)
            sounds[f"pa.{lang}.{key}"] = {"sounds": [{"name": f"promptcritical:{rel}"}],
                                          "subtitle": f"subtitles.promptcritical.pa.{key}"}
    (ASSETS / "sounds.json").write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"готово: {len(sounds)} звуков")


def cmd_narrate(args):
    paragraphs = [" ".join(p.split()) for p in re.split(r"\n\s*\n", Path(args.script).read_text(encoding="utf-8")) if p.strip()]
    voice = args.voice or CONFIG["voices"][args.lang]
    parts = [tts(p, voice, args.model, paragraphs[i - 1] if i else None,
                 paragraphs[i + 1] if i + 1 < len(paragraphs) else None) for i, p in enumerate(paragraphs)]
    with tempfile.TemporaryDirectory() as tmp:
        inputs, chain = [], ""
        for i, p in enumerate(parts):
            inputs += ["-i", str(p)]
            chain += f"[{i}:a]aresample=44100,apad=pad_dur={args.pause if i + 1 < len(parts) else 0}[a{i}];"
        chain += "".join(f"[a{i}]" for i in range(len(parts))) + f"concat=n={len(parts)}:v=0:a=1[o]"
        ffmpeg(*inputs, "-filter_complex", chain, "-map", "[o]", args.out)
    print(f"готово: {args.out} ({len(parts)} абзацев)")


def cmd_samples(args):
    out = Path(args.out)
    for voice in args.voices.split(","):
        mp3 = tts(args.text, voice, args.model)
        name = f"{args.lang}_{voice}_{args.model or CONFIG['model']}"
        if args.pa:
            pa_render(mp3, out / f"{name}.ogg", args.alarm)
        else:
            out.mkdir(parents=True, exist_ok=True)
            (out / f"{name}.mp3").write_bytes(mp3.read_bytes())
    print(f"готово: {out}")


def cmd_voices(_):
    sub = json.loads(request("/v1/user/subscription"))
    print(f"тариф {sub['tier']}: {sub['character_count']}/{sub['character_limit']} символов")
    for v in json.loads(request("/v2/voices?page_size=100"))["voices"]:
        lb = v.get("labels", {})
        print(v["voice_id"], v["name"], "|", lb.get("gender"), lb.get("accent"), lb.get("use_case"))


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sp = p.add_subparsers(dest="cmd", required=True)
    pa = sp.add_parser("pa")
    pa.add_argument("--force", action="store_true", help="перерендерить все ogg из кэша")
    n = sp.add_parser("narrate")
    n.add_argument("script")
    n.add_argument("-o", "--out", required=True)
    n.add_argument("--lang", default="en_us")
    n.add_argument("--voice")
    n.add_argument("--model")
    n.add_argument("--pause", type=float, default=0.45, help="пауза между абзацами, с")
    s = sp.add_parser("samples")
    s.add_argument("--lang", default="ru_ru")
    s.add_argument("--voices", required=True)
    s.add_argument("--text", required=True)
    s.add_argument("-o", "--out", required=True)
    s.add_argument("--model")
    s.add_argument("--pa", action="store_true", help="через фильтр громкоговорителя")
    s.add_argument("--alarm", action="store_true", help="с сигналом тревоги вместо гонга")
    sp.add_parser("voices")
    args = p.parse_args()
    {"pa": cmd_pa, "narrate": cmd_narrate, "samples": cmd_samples, "voices": cmd_voices}[args.cmd](args)


if __name__ == "__main__":
    main()
