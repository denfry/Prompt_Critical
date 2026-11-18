## A boiler house mimic

This program draws every boiler on the gateway on a monitor: pressure, gauge glass level, flue gas temperature and alarms. Once a minute it appends a line to the **trend.csv** archive. Put the monitor next to the computer.

```
local rtu = peripheral.find("pc_rtu")
local mon = peripheral.find("monitor")
mon.setTextScale(0.5)

local function boilers()
  local out = {}
  for _, m in ipairs(rtu.machines()) do
    if m.type == "fire_tube_boiler" then
      out[#out + 1] = m.id
    end
  end
  return out
end

local function bar(x, y, frac, color)
  local n = math.floor(frac * 10 + 0.5)
  mon.setCursorPos(x, y)
  mon.setBackgroundColor(color)
  mon.write(string.rep(" ", n))
  mon.setBackgroundColor(colors.gray)
  mon.write(string.rep(" ", 10 - n))
  mon.setBackgroundColor(colors.black)
end

local last = 0
while true do
  mon.clear()
  for i, id in ipairs(boilers()) do
    local v = rtu.read(id)
    local y = i * 4 - 2
    mon.setCursorPos(1, y)
    mon.write(("%s  %.2f MPa  %d C"):format(
      id, v["boiler.pressure"], v["boiler.flue"]))
    bar(1, y + 1, v["boiler.glass"], colors.blue)
    if v["boiler.low_water"] then
      mon.setCursorPos(13, y + 1)
      mon.setTextColor(colors.red)
      mon.write("LOW WATER")
      mon.setTextColor(colors.white)
    end
    if os.clock() - last >= 60 then
      local f = fs.open("trend.csv", "a")
      f.writeLine(("%d,%s,%.3f,%.3f"):format(
        os.epoch("utc"), id,
        v["boiler.pressure"], v["boiler.glass"]))
      f.close()
    end
  end
  if os.clock() - last >= 60 then last = os.clock() end
  sleep(rtu.cycle())
end
```

The panel's instrument names are in **rtu.describe(id).points**. Buttons are in **rtu.describe(id).buttons**, and you press one like this: **rtu.press(id, "boiler.drain")**.
