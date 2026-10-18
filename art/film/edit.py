"""Cut the M2 trailer and the vertical short from the FilmRecorder shots with ffmpeg.

    python art/film/edit.py <shots dir> <music dir> <out dir>

Trailer: intro of the score (0-10.9 s) + its climax (76.3-125 s); cuts land on beats (99 BPM, 0.6 s).
Short: short score, the drop at 8.1 s.
"""
import os
import subprocess
import sys

FONT = "C\\:/Windows/Fonts/seguibl.ttf"
FONT2 = "C\\:/Windows/Fonts/segoeuib.ttf"
LOGO = os.path.join(os.path.dirname(__file__), "..", "..", "pc-mod", "src", "main", "resources", "promptcritical_logo.png")


def esc(t):
    return t.replace("\\", "\\\\").replace(":", "\\:").replace("'", "’").replace("%", "\\%")


def text(t, a, b, size, y, x="(w-text_w)/2", font=FONT, color="white", box=False):
    fade = f"if(lt(t,{a}+0.25),(t-{a})/0.25,if(gt(t,{b}-0.25),({b}-t)/0.25,1))"
    boxs = ":box=1:boxcolor=black@0.45:boxborderw=18" if box else ""
    return (f"drawtext=fontfile='{font}':text='{esc(t)}':fontsize={size}:fontcolor={color}:x={x}:y={y}"
            f":shadowcolor=black@0.7:shadowx=3:shadowy=3{boxs}:alpha='{fade}':enable='between(t,{a},{b})'")


def run(args):
    print(" ".join(args[:6]), "...")
    subprocess.run(args, check=True)


def cut(shots_dir, parts, size, texts, audio_filter, audio_in, out, end_card):
    """parts: [(shot, start, dur)]; end_card: seconds of logo card appended."""
    w, h = size
    args = ["ffmpeg", "-y", "-v", "error"]
    fl = []
    for i, (shot, start, dur) in enumerate(parts):
        args += ["-ss", str(start), "-t", str(dur), "-i", os.path.join(shots_dir, shot + ".mp4")]
        fl.append(f"[{i}:v]scale={w}:{h},setsar=1,fps=60,format=yuv420p[v{i}]")
    n = len(parts)
    # end card: logo on black
    args += ["-loop", "1", "-t", str(end_card), "-i", LOGO]
    logo_w = int(min(w, h) * 0.38)
    fl.append(f"color=c=0x0d0f12:s={w}x{h}:r=60:d={end_card}[bg];[{n}:v]scale={logo_w}:-1[lg];"
              f"[bg][lg]overlay=(W-w)/2:(H-h)/2-{int(h * 0.06)},format=yuv420p[v{n}]")
    args += ["-i", audio_in]
    fl.append("".join(f"[v{i}]" for i in range(n + 1)) + f"concat=n={n + 1}:v=1:a=0[cat]")
    total = sum(p[2] for p in parts) + end_card
    vf = ",".join(texts + [f"fade=t=in:st=0:d=0.6", f"fade=t=out:st={total - 0.8}:d=0.8"])
    fl.append(f"[cat]{vf}[vout]")
    fl.append(f"[{n + 1}:a]{audio_filter},afade=t=out:st={total - 1.5}:d=1.5,atrim=0:{total}[aout]")
    args += ["-filter_complex", ";".join(fl), "-map", "[vout]", "-map", "[aout]",
             "-c:v", "libx264", "-preset", "slow", "-crf", "17", "-pix_fmt", "yuv420p",
             "-colorspace", "bt709", "-color_primaries", "bt709", "-color_trc", "bt709",
             "-c:a", "aac", "-b:a", "320k", "-movflags", "+faststart", out]
    run(args)


def trailer(shots, music, out):
    parts = [("tr01_yard", 0.0, 6.0), ("tr06_engine", 0.0, 4.9),
             ("tr03_firing_floor", 0.0, 4.0), ("tr04_stoker", 0.3, 2.4), ("tr05_panel_boiler", 0.0, 6.0),
             ("tr07_panel_engine", 0.0, 5.0), ("tr08_foundry_aisle", 0.0, 4.0),
             ("tr09_cupola", 0.3, 2.4), ("tr10_lathe", 0.3, 2.4), ("tr11_panel_lathe", 0.2, 4.8),
             ("tr12_drill", 0.3, 2.4),
             ("tr14_hill", 0.0, 4.8), ("tr15_cage", 0.0, 6.0), ("tr16_drift", 0.0, 6.0), ("tr17_longwall", 0.2, 4.8),
             ("tr18_shearer", 0.2, 3.6), ("tr19_panel_cabinet", 0.0, 4.8), ("tr20_fan", 0.3, 2.4)]
    t = 0
    starts = {}
    for name, _, d in parts:
        starts[name] = t
        t += d
    s = starts
    texts = [
        text("PROMPT CRITICAL", 0.8, 5.6, 120, "(h-text_h)/2-40"),
        text("an industrial complex for Minecraft", 1.3, 5.6, 46, "(h-text_h)/2+80", font=FONT2),
        text("ERA I0  ·  COAL & STEAM", 6.6, 10.7, 64, "h*0.78"),
        text("Lancashire boilers on a chain grate stoker", s["tr03_firing_floor"] + 0.3, s["tr04_stoker"] + 2.3, 44, "h*0.86", x="80", font=FONT2, box=True),
        text("Local instrument panels on every machine", s["tr05_panel_boiler"] + 0.4, s["tr05_panel_boiler"] + 5.8, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("Live gauges, lamps and buttons", s["tr07_panel_engine"] + 0.3, s["tr07_panel_engine"] + 4.6, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("Foundry and machine shop", s["tr08_foundry_aisle"] + 0.3, s["tr09_cupola"] + 2.3, 44, "h*0.86", x="80", font=FONT2, box=True),
        text("Cupola, moulds, lathe, drill press", s["tr10_lathe"] + 0.2, s["tr11_panel_lathe"] + 4.6, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("THE COAL MINE", s["tr14_hill"] + 0.4, s["tr14_hill"] + 4.6, 96, "(h-text_h)/2"),
        text("Headframe and hoist over a working shaft", s["tr15_cage"] + 0.4, s["tr15_cage"] + 5.8, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("Timbered drifts", s["tr16_drift"] + 2.0, s["tr16_drift"] + 5.8, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("Powered roof supports and a longwall shearer", s["tr17_longwall"] + 0.3, s["tr18_shearer"] + 3.4, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("Mine dispatch: methane, ventilation, water", s["tr19_panel_cabinet"] + 0.3, s["tr19_panel_cabinet"] + 4.6, 44, "h*0.88", x="80", font=FONT2, box=True),
        text("PROMPT CRITICAL", t + 0.3, t + 6.8, 72, "h*0.72"),
        text("0.1.0-alpha  ·  NeoForge 1.21.1", t + 0.6, t + 6.8, 38, "h*0.72+90", font=FONT2, color="0xE0B040"),
        text("Available on CurseForge and Modrinth", t + 0.9, t + 6.8, 46, "h*0.72+150", font=FONT2),
    ]
    # score: intro 0-10.9, then from 42.9 so the mine starts on the climax at 76.3; short crossfade at the join
    af = ("asplit=2[m1][m2];[m1]atrim=0:11.2,asetpts=PTS-STARTPTS[i1];[m2]atrim=42.6,asetpts=PTS-STARTPTS[i2];"
          "[i1][i2]acrossfade=d=0.3")
    cut(shots, parts, (1920, 1080), texts, af, os.path.join(music, "trailer.wav"),
        os.path.join(out, "PromptCritical_M2_trailer.mp4"), 7.0)


def short(shots, music, out):
    parts = [("sh01_panel_boiler", 0.0, 6.0), ("sh02_firing_floor", 0.3, 2.1), ("sh03_stoker", 0.0, 3.0),
             ("sh04_engine", 0.0, 3.0), ("sh05_panel_engine", 0.0, 5.0), ("sh06_foundry", 0.0, 3.0),
             ("sh07_cupola", 0.0, 3.0), ("sh08_panel_cupola", 0.0, 5.0), ("sh09_mine", 0.0, 5.0)]
    t = 0
    s = {}
    for name, _, d in parts:
        s[name] = t
        t += d
    texts = [
        text("This Minecraft mod has", 0.2, 5.9, 74, "h*0.08"),
        text("REAL boiler gauges", 0.2, 5.9, 96, "h*0.08+95", color="0xFFC040"),
        text("Coal-fired boilers", s["sh02_firing_floor"], s["sh03_stoker"] + 2.9, 72, "h*0.12"),
        text("Steam engine", s["sh04_engine"], s["sh04_engine"] + 2.9, 72, "h*0.12"),
        text("Every machine has a panel", s["sh05_panel_engine"], s["sh05_panel_engine"] + 4.9, 64, "h*0.08"),
        text("Foundry", s["sh06_foundry"], s["sh07_cupola"] + 2.9, 72, "h*0.12"),
        text("Cupola furnace panel", s["sh08_panel_cupola"], s["sh08_panel_cupola"] + 4.9, 64, "h*0.08"),
        text("PROMPT CRITICAL", s["sh09_mine"] + 0.4, s["sh09_mine"] + 4.9, 92, "h*0.10"),
        text("alpha out now", s["sh09_mine"] + 0.8, s["sh09_mine"] + 4.9, 56, "h*0.10+110", font=FONT2, color="0xFFC040"),
        text("PROMPT CRITICAL", t + 0.2, t + 3.4, 84, "h*0.68"),
        text("Available on", t + 0.4, t + 3.4, 52, "h*0.68+120", font=FONT2),
        text("CurseForge and Modrinth", t + 0.4, t + 3.4, 64, "h*0.68+190", color="0xFFC040"),
    ]
    cut(shots, parts, (1080, 1920), texts, "anull", os.path.join(music, "short.wav"),
        os.path.join(out, "PromptCritical_M2_short.mp4"), 3.5)


if __name__ == "__main__":
    shots_dir, music_dir, out_dir = sys.argv[1:4]
    os.makedirs(out_dir, exist_ok=True)
    which = sys.argv[4] if len(sys.argv) > 4 else "both"
    if which in ("both", "trailer"):
        trailer(shots_dir, music_dir, out_dir)
    if which in ("both", "short"):
        short(shots_dir, music_dir, out_dir)
