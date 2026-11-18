-- pc/alarm_screen: an overview screen (ISA-101 level 1) from the annunciator next to
-- the computer: the first machine tags as values and the alarm list (GDD 03c §3c.5.2).
-- Usage: /rom/programs/pc/alarm_screen [max tags]
local pcui = require("pcui")
local args = { ... }
local ann = peripheral.find("pc_annunciator")
local mon = peripheral.find("monitor")
if not ann then error("needs a pc_annunciator (annunciator panel) next to the computer", 0) end
if not mon then error("needs a monitor next to the computer", 0) end

local app = pcui.app({ monitor = mon, title = "STATION", fps = 2, annunciator = ann })
local w, h = app.w, app.h
local ov = app:page("overview", { level = 1, title = "OVERVIEW", hotkey = "1" })
local alarms = app:page("alarms", { level = 2, title = "ALARMS", hotkey = "9", parent = "overview" })

local tags = {}
for tag in pairs(ann.getTags()) do tags[#tags + 1] = tag end
table.sort(tags)
local maxTags = tonumber(args[1]) or 40
local listH = math.max(4, math.floor(h / 3))
local rows = h - 4 - listH
local colW = 39
local cols = math.max(1, math.floor((w - 2) / colW))
local n = math.min(#tags, maxTags, rows * cols)
for i = 1, n do
  local tag = tags[i]
  local col = (i - 1) % cols
  local row = math.floor((i - 1) / cols)
  local label = tag:gsub("^([%w_]+)@[^/]*/", "%1 ")
  if #label > colW - 12 then label = label:sub(1, colW - 12) end
  ov:add(pcui.Label{ x = 2 + col * colW, y = 3 + row, text = label })
  ov:add(pcui.Value{ x = 2 + col * colW + colW - 11, y = 3 + row, w = 9, dev = ann, tag = tag, fmt = "%9.2f" })
end
ov:add(pcui.AlarmList{ x = 2, y = h - listH, w = w - 2, h = listH, ann = ann })
alarms:add(pcui.AlarmList{ x = 2, y = 3, w = w - 2, h = h - 4, ann = ann })
alarms:add(pcui.Button{ x = 2, y = h - 1, w = 12, label = "ACK ALL", confirm = true,
  onPress = function() ann.acknowledge("ALL") end })
print(("pcui overview: %d of %d tags; press Ctrl+T to stop"):format(n, #tags))
app:run()
