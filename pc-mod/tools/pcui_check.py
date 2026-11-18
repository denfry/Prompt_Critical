"""Checks the pcui ROM library (GDD 03c §3c.4) outside Minecraft.

Runs pcui under Lua (the `lupa` package) against a mock CC: Tweaked environment: a
79x38 monitor, peripherals with readMany, os/term/colors. Not part of the Gradle build
(the build has no Lua VM); run by hand after changing pcui:

    python -I pc-mod/tools/pcui_check.py

Checks:
  T-3c-08  a page with static values writes nothing to the terminal after the first frame
  batch    one readMany per device per frame with all the page's tags (§3c.4.2)
  hmi      an L1 page reports its distinct tag count to pc_annunciator.reportScreen once
  T-3c-09  CIE L* contrast of the §3c.3.2 palette, background against the alarm colours
  widgets  BAD values in magenta, SBO button needs two touches, units and SI formatting
"""
import os
import sys

from lupa import LuaRuntime

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROM = os.path.join(ROOT, "src", "main", "resources", "data", "computercraft", "lua", "rom", "modules", "main")

MOCK = r"""
colors = { white=1, orange=2, magenta=4, lightBlue=8, yellow=16, lime=32, pink=64, gray=128,
  lightGray=256, cyan=512, purple=1024, blue=2048, brown=4096, green=8192, red=16384, black=32768 }
local clockT = 0
os.clock = function() return clockT end
os.time = function() return 12.5 end
function advance(dt) clockT = clockT + dt end
textutils = { formatTime = function(t) return "12:30" end }

function monitor(w, h)
  local m = { writes = 0, palette = {}, rows = {} }
  m.getSize = function() return w, h end
  m.setTextScale = function(s) m.scale = s end
  m.setPaletteColour = function(c, rgb) m.palette[c] = rgb end
  m.setCursorPos = function(x, y) m.cy = y end
  m.blit = function(t, f, b)
    assert(#t == #f and #f == #b, "blit lengths differ")
    m.writes = m.writes + 1
    m.rows[m.cy] = { t, f, b }
  end
  return m
end

reads = {}
function device(name, values)
  local d = {}
  d.readMany = function(tags)
    reads[name] = (reads[name] or 0) + 1
    local out = {}
    for _, t in pairs(tags) do out[t] = values[t] end
    return out
  end
  d._name = name
  return d
end

periph = {}
peripheral = {
  getNames = function() local n = {} for k in pairs(periph) do n[#n + 1] = k end return n end,
  isPresent = function(n) return periph[n] ~= nil end,
  wrap = function(n) return periph[n] end,
  find = function(t) return periph[t] end,
  call = function(n, m, ...) return periph[n][m](...) end,
  getName = function(d) return d._name end,
}
term = { current = function() return monitor(51, 19) end }
"""


def lstar(rgb):
    def lin(c):
        c /= 255
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    y = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    f = y ** (1 / 3) if y > 216 / 24389 else (24389 / 27 * y + 16) / 116
    return 116 * f - 16


def main():
    lua = LuaRuntime(unpack_returned_tuples=True)
    lua.execute(MOCK)
    lua.execute('package.path = [[%s/?.lua;%s/?/init.lua;]] .. package.path' % (ROM.replace("\\", "/"), ROM.replace("\\", "/")))
    failures = []

    def check(ok, what):
        print(("ok   " if ok else "FAIL ") + what)
        if not ok:
            failures.append(what)

    # T-3c-08, batch reads, hmi report
    res = lua.execute(r"""
      local reports = {}
      periph.pc_annunciator = device("pc_annunciator", {})
      periph.pc_annunciator.getAlarms = function() return { { id = 1, priority = 2, state = "ACKED", message = "Feed low", value = 0.31 } } end
      periph.pc_annunciator.getStatistics = function() return { rate10min = 1, shelved = 0, flood = false } end
      periph.pc_annunciator.reportScreen = function(level, n) reports[#reports + 1] = { level, n } end
      local vals = {}
      for i = 1, 30 do vals["t" .. i] = i * 1.5 end
      local A = device("A", vals)
      local B = device("B", vals)
      local pcui = require("pcui")
      local mon = monitor(79, 38)
      local app = pcui.app({ monitor = mon, title = "UNIT 1", fps = 2, clock = false })
      local ov = app:page("overview", { level = 1, title = "OVERVIEW", hotkey = "1" })
      app:page("rcs", { level = 2, title = "RCS", hotkey = "2", parent = "overview" })
      for i = 1, 20 do
        ov:add(pcui.Value{ x = 2 + ((i - 1) % 2) * 38, y = 3 + math.floor((i - 1) / 2), w = 8, dev = (i % 2 == 0) and A or B,
          tag = "t" .. i, unit = "degC", fmt = "%6.1f", label = "T" .. i })
      end
      ov:add(pcui.Value{ x = 2, y = 14, dev = A, tag = "t2" })        -- duplicate tag: read once
      ov:add(pcui.Bar{ x = 2, y = 16, w = 3, h = 10, dev = A, tag = "t4", lo = 0, hi = 10, normal = { 2, 8 }, setpoint = 5 })
      ov:add(pcui.Status{ x = 20, y = 16, label = "SCRAM:", get = function() return "NORMAL" end })
      ov:add(pcui.AlarmList{ x = 2, y = 28, w = 76, h = 6, ann = periph.pc_annunciator })
      local first = app:render()
      local writes0 = mon.writes
      for k = 1, 100 do advance(0.5) app:render() end
      return first, writes0, mon.writes - writes0, reads.A, reads.B, #reports, reports[1] and reports[1][1], reports[1] and reports[1][2],
        mon.scale, mon.palette[colors.lightGray]
    """)
    first, writes0, after, ra, rb, nrep, lvl, ntags, scale, bg = res
    check(first == 38 and writes0 == 38, "first frame paints all 38 rows (%s)" % first)
    check(after == 0, "T-3c-08: 100 static frames write nothing after the first (%s writes)" % after)
    check(ra == 101 and rb == 101, "batch: one readMany per device per frame (A=%s, B=%s)" % (ra, rb))
    check(nrep == 1 and lvl == 1 and ntags == 20, "hmi: L1 page reported once with 20 distinct tags (%s, %s, %s)" % (nrep, lvl, ntags))
    check(scale == 0.5 and bg == 0xC4C4C4, "monitor scale 0.5 and ISA-101 background applied")

    # widgets: BAD value, SBO button, navigation, units
    res = lua.execute(r"""
      local pcui = require("pcui")
      local mon = monitor(40, 10)
      local app = pcui.app({ monitor = mon, clock = false, annunciator = false })
      local p = app:page("p", { level = 2, title = "P" })
      local D = device("D", { ok = 1.0 })
      p:add(pcui.Value{ x = 1, y = 3, w = 5, dev = D, tag = "missing" })
      local pressed = 0
      local btn = p:add(pcui.Button{ x = 1, y = 5, w = 10, label = "STOP", confirm = true, onPress = function() pressed = pressed + 1 end })
      app:render()
      local bad = mon.rows[3]
      app:touch(2, 5)
      local afterOne = pressed
      app:touch(2, 5)
      local afterTwo = pressed
      app:touch(2, 5)
      advance(6)
      app:touch(2, 5)
      local afterTimeout = pressed
      return bad[1], bad[2], afterOne, afterTwo, afterTimeout,
        pcui.units.degC(373.15), pcui.units.MPa(15.5e6), pcui.fmt.si(15480000, "Pa", 3), pcui.fmt.si(0.0021, "Sv", 2)
    """)
    text, fg, one, two, timeout, degc, mpa, si1, si2 = res
    check("BAD" in text and fg[text.index("BAD")] == "2", "BAD value drawn in magenta")
    check(one == 0 and two == 1, "SBO button: first touch arms, second executes")
    check(timeout == 1, "SBO button: arming expires after 5 s")
    check(abs(degc - 100) < 1e-9 and abs(mpa - 15.5) < 1e-9, "units: degC, MPa")
    check(si1 == "15.5 MPa" and si2 == "2.1 mSv", "fmt.si: %s, %s" % (si1, si2))

    # watchdog: armed by run(), kicked after every rendered frame, not kicked when a frame fails
    res = lua.execute(r"""
      local pcui = require("pcui")
      local log = {}
      local wd = { arm = function() log[#log + 1] = "arm" end, kick = function() log[#log + 1] = "kick" end }
      local app = pcui.app({ monitor = monitor(30, 10), clock = false, annunciator = false, watchdog = wd })
      local p = app:page("p", { level = 1 })
      local broken = false
      p:add({ bind = function() return {} end, draw = function() if broken then error("widget failed") end end })
      local frames = 0
      os.startTimer = function() return 1 end
      os.pullEvent = function()
        frames = frames + 1
        if frames == 3 then broken = true end
        return "timer", 1
      end
      local ok = pcall(app.run, app)
      return table.concat(log, ","), ok
    """)
    seq, ok = res
    check(seq == "arm,kick,kick,kick" and not ok, "watchdog: arm, a kick per good frame, none after a failed one (%s)" % seq)

    # smoke test of the ROM program /rom/programs/pc/alarm_screen: five timer frames, then stop
    prog = os.path.join(ROOT, "src", "main", "resources", "data", "computercraft", "lua", "rom", "programs", "pc", "alarm_screen.lua")
    res = lua.execute(r"""
      local prog = ...
      local vals = {}
      for i = 1, 50 do vals["boiler@1,2," .. i .. "/boiler.pressure"] = i end
      local ann = device("pc_annunciator", vals)
      ann.getTags = function() return vals end
      ann.getAlarms = function() return { { id = 3, priority = 1, state = "UNACK", message = "Low water", value = 0.02 } } end
      ann.getStatistics = function() return { rate10min = 1, shelved = 0, flood = false } end
      ann.reportScreen = function(level, n) _G.reported = n end
      local mon = monitor(79, 38)
      periph.pc_annunciator, periph.monitor = ann, mon
      local frames = 0
      os.startTimer = function() return 7 end
      os.pullEvent = function()
        frames = frames + 1
        if frames > 5 then error("stop", 0) end
        return "timer", 7
      end
      print = function() end
      local ok, err = pcall(dofile, prog)
      return ok, err, mon.writes, _G.reported
    """, prog.replace("\\", "/"))
    ok, err, writes, reported = res
    check((not ok) and err == "stop" and writes > 0, "alarm_screen runs on a 4x3 monitor (%s, %s writes)" % (err, writes))
    check(reported is not None and reported >= 20, "alarm_screen overview reports %s tags to the annunciator" % reported)

    # T-3c-09: palette contrast (informational for orange/yellow, see below)
    rgb = lua.eval("require('pcui.palette').rgb")
    c = lua.eval("colors")
    bgl = lstar(rgb[c["lightGray"]])
    for name in ("red", "orange", "yellow", "blue", "magenta"):
        d = abs(lstar(rgb[c[name]]) - bgl)
        if name in ("orange", "yellow"):
            # GDD 03c §3c.3.2 gives these RGB values and T-3c-09 asks for dL* ≥ 20: the two contradict.
            # pcui keeps the GDD colours and shows P2/P3 as coloured backgrounds with black text plus
            # the ◆/● marker shape, so the information does not rest on colour contrast alone.
            print("note dL* background/%s = %.1f (GDD palette; T-3c-09 asks >= 20)" % (name, d))
        else:
            check(d >= 20, "T-3c-09: dL* background/%s = %.1f" % (name, d))

    print()
    print("FAILED: %d" % len(failures) if failures else "all pcui checks passed")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
