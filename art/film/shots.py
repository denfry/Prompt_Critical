"""Shot list for the M2 trailer (1920x1080) and the vertical short (1080x1920): writes film.json for FilmRecorder.

    python art/film/shots.py pc-mod/run-film/film.json
"""
import json
import sys

FFMPEG = ("C:/Users/dabin/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/"
          "ffmpeg-8.1-full_build/bin/ffmpeg.exe")
DUSK = 12700
WIDE = [1920, 1080]
TALL = [1080, 1920]


def key(t, pos, look, fov=55, roll=0):
    return {"t": t, "pos": pos, "look": look, "fov": fov, "roll": roll}


def move(name, dur, a, b, look_a, look_b=None, fov=(55, 55), size=WIDE, ease="inOut", time=DUSK, **kw):
    s = {"name": name, "duration": dur, "size": size, "scale": size, "time": time, "ease": ease, "gamma": 0.6,
         "setup": ["function filmset:refresh"],
         "keys": [key(0, a, look_a, fov[0]), key(dur, b, look_b or look_a, fov[1])]}
    s.update(kw)
    return s


def panel(name, dur, pos, eye, look, block, events, size=WIDE, gui=4):
    return move(name, dur, eye, [eye[0], eye[1], eye[2] + 0.4], look, size=size, mode="creative", guiScale=gui, blur=6,
                events=[{"t": 0.15, "panel": block}] + events)


def trailer():
    return [
        move("tr01_yard", 6.0, [44, -50, 16], [34, -54.5, 27], [10, -48, 52], [8, -50, 50], fov=(50, 45)),
        move("tr02_facade", 5.0, [12, -59.0, 33], [12, -58.0, 36], [12, -56, 40], [9, -38, 53], fov=(60, 60)),
        move("tr03_firing_floor", 4.0, [1.5, -58.6, 44.0], [14.5, -58.6, 44.0], [5, -59.2, 48], [16, -59.2, 48], fov=(60, 60), ease="linear"),
        move("tr04_stoker", 3.0, [10.2, -58.9, 44.6], [9.2, -59.1, 45.2], [9, -59.4, 47.5], fov=(50, 45)),
        panel("tr05_panel_boiler", 6.0, None, [9.0, -58.4, 44.2], [9.0, -59.0, 48.0], [9, -59, 49], [
            {"t": 1.0, "at": [26, 36], "dur": 0.8},
            {"t": 2.6, "cursor": "Fire", "dur": 0.7},
            {"t": 3.6, "click": True},
            {"t": 4.4, "at": [112, 36], "dur": 0.7}]),
        move("tr06_engine", 4.0, [17, -57.6, 45.5], [17.5, -57.8, 51.5], [12, -59, 49.5], fov=(55, 55)),
        panel("tr07_panel_engine", 5.0, None, [12.0, -58.4, 44.4], [12.0, -59.0, 48.0], [12, -59, 49], [
            {"t": 1.0, "at": [24, 40], "dur": 0.8},
            {"t": 2.6, "at": [80, 36], "dur": 0.7}]),
        move("tr08_foundry_aisle", 4.0, [33.5, -57.5, 43.5], [55, -57.5, 43.5], [38, -59.5, 49], [59, -59.5, 49], fov=(60, 60), ease="linear"),
        move("tr09_cupola", 3.0, [39.0, -58.0, 45.5], [38.2, -57.6, 46.0], [36, -58.5, 49], fov=(50, 50)),
        move("tr10_lathe", 3.0, [55.5, -59.0, 46.3], [53.0, -59.0, 46.0], [53.5, -59.8, 49], fov=(50, 50)),
        panel("tr11_panel_lathe", 5.0, None, [53.5, -58.4, 45.0], [53.5, -59.5, 49.0], [53, -60, 49], [
            {"t": 1.0, "at": [30, 40], "dur": 0.8},
            {"t": 2.8, "at": [100, 40], "dur": 0.8}]),
        move("tr12_drill", 3.0, [60.5, -58.8, 46.0], [59.0, -58.8, 45.6], [58, -59.5, 49], fov=(50, 50)),
        move("tr13_outro", 7.0, [12, -57.5, 30], [30, -46, 6], [8, -50, 50], [8, -46, 50], fov=(50, 55)),
    ]


def short():
    return [
        panel("sh01_panel_boiler", 6.0, None, [9.0, -58.4, 44.4], [9.0, -59.0, 48.0], [9, -59, 49], [
            {"t": 0.6, "at": [26, 36], "dur": 0.6},
            {"t": 2.0, "cursor": "Fire", "dur": 0.6},
            {"t": 2.9, "click": True},
            {"t": 3.8, "at": [112, 36], "dur": 0.6}], size=TALL),
        move("sh02_firing_floor", 3.0, [2.5, -58.6, 44.0], [10.5, -58.6, 44.0], [6, -59.2, 48], [12, -59.2, 48], fov=(70, 70), size=TALL, ease="linear"),
        move("sh03_stoker", 3.0, [10.0, -58.9, 44.8], [9.2, -59.1, 45.4], [9, -59.4, 47.5], fov=(60, 55), size=TALL),
        move("sh04_engine", 3.0, [16, -57.6, 46], [16.5, -57.8, 51], [12, -59, 49.5], fov=(65, 65), size=TALL),
        panel("sh05_panel_engine", 5.0, None, [12.0, -58.4, 44.6], [12.0, -59.0, 48.0], [12, -59, 49], [
            {"t": 0.8, "at": [24, 40], "dur": 0.6},
            {"t": 2.4, "at": [80, 36], "dur": 0.6}], size=TALL),
        move("sh06_foundry", 3.0, [34, -57.5, 43.5], [46, -57.5, 43.5], [38, -59.5, 49], [50, -59.5, 49], fov=(70, 70), size=TALL, ease="linear"),
        move("sh07_cupola", 3.0, [38.8, -58.0, 45.5], [38.0, -57.4, 46.0], [36, -58.2, 49], fov=(60, 60), size=TALL),
        panel("sh08_panel_cupola", 5.0, None, [36.0, -58.0, 45.0], [36.0, -58.5, 49.0], [36, -59, 49], [
            {"t": 0.8, "at": [30, 40], "dur": 0.6},
            {"t": 2.4, "at": [100, 40], "dur": 0.6}], size=TALL),
        move("sh09_chimneys", 5.0, [14, -58, 33], [16, -50, 30], [8, -48, 50], [7, -36, 53], fov=(65, 60), size=TALL),
    ]


def main(path):
    warm = {"name": "warm", "duration": 0.1, "fps": 10, "stills": True, "size": [640, 360], "scale": [640, 360],
            "time": DUSK, "settle": 400, "setup": ["function filmset:refresh", "tick sprint 2400"],
            "keys": [key(0, [12, -55, 30], [8, -50, 50])]}
    plan = {"ffmpeg": FFMPEG, "out": "film/m2", "quit": True, "fps": 60, "crf": 14,
            "shots": [warm] + trailer() + short()}
    with open(path, "w") as fh:
        json.dump(plan, fh, indent=1)
    print(len(plan["shots"]), "shots")


if __name__ == "__main__":
    main(sys.argv[1])
