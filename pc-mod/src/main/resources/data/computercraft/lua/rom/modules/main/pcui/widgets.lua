-- pcui widgets (GDD 03c §3c.4.2). Every widget is a table with
--   bind()          -> list of {dev, tag} it reads (the app batches them per device)
--   draw(app, buf)  -> paints into the frame buffer
--   touch(app, x, y) -> true if the touch was its own (optional)
-- Values come from app:value(dev, tag): nil means BAD/stale and is shown in magenta.
local palette = require("pcui.palette")

local R = palette.roles
local W = {}

local function new(kind, o)
  o = o or {}
  o.kind = kind
  return o
end

local function inside(w, x, y, ww, hh)
  return x >= w.x and x < w.x + ww and y >= w.y and y < w.y + hh
end

local function binds(self)
  if self.dev and self.tag then return { { self.dev, self.tag } } end
  return {}
end

local function clock()
  return os.clock()
end

-- Label{x, y, text, fg}
function W.Label(o)
  local w = new("Label", o)
  w.bind = function() return {} end
  w.draw = function(self, app, b) b:text(self.x, self.y, self.text, self.fg or R.text) end
  return w
end

-- Value{x, y, w, tag, dev, fmt, unit, lo, hi, label}: a live value; outside lo..hi it turns orange.
function W.Value(o)
  local w = new("Value", o)
  w.bind = binds
  w.draw = function(self, app, b)
    local x = self.x
    if self.label then
      b:text(x, self.y, self.label, R.text)
      x = x + #self.label + 1
    end
    local v = app:value(self.dev, self.tag)
    local width = self.w or 8
    local s, fg
    if type(v) ~= "number" then
      s, fg = "BAD", R.bad
    else
      s = string.format(self.fmt or "%.2f", v)
      fg = ((self.lo and v < self.lo) or (self.hi and v > self.hi)) and R.p2 or R.value
    end
    if #s < width then s = string.rep(" ", width - #s) .. s end
    b:text(x, self.y, s, fg)
    if self.unit then b:text(x + #s + 1, self.y, self.unit, R.static) end
  end
  return w
end

-- Bar{x, y, w, h, tag, dev, lo, hi, normal={a, b}, setpoint=}: ISA-101 moving analog,
-- vertical bar with the normal range band and a setpoint mark.
function W.Bar(o)
  local w = new("Bar", o)
  w.bind = binds
  w.draw = function(self, app, b)
    local width, h = self.w or 2, self.h or 8
    local lo, hi = self.lo or 0, self.hi or 1
    local v = app:value(self.dev, self.tag)
    local function row(val)
      local f = (val - lo) / (hi - lo)
      if f < 0 then f = 0 elseif f > 1 then f = 1 end
      return math.floor(f * h + 0.5)
    end
    if type(v) ~= "number" then
      b:fill(self.x, self.y, width, h, R.bad, "?", R.accent)
      return
    end
    local filled = row(v)
    local outside = self.normal and (v < self.normal[1] or v > self.normal[2])
    local na, nb = -1, -1
    if self.normal then na, nb = row(self.normal[1]), row(self.normal[2]) end
    for k = 1, h do
      local y = self.y + h - k
      local bg = R.accent
      if k > na and k <= nb then bg = R.normal end
      if k <= filled then bg = outside and R.p2 or R.value end
      b:fill(self.x, y, width, 1, bg)
    end
    if self.setpoint then
      local k = math.max(1, row(self.setpoint))
      b:text(self.x + width, self.y + h - k, "\17", R.setpoint)
    end
  end
  return w
end

-- Trend{x, y, w, h, pens={{tag, dev, lo, hi}}, span=s, period=s}: 1-4 pens, one column per sample.
local PEN = { colors.blue, colors.black, colors.cyan, colors.purple }

function W.Trend(o)
  local w = new("Trend", o)
  w.samples = {}
  w.last = nil
  w.bind = function(self)
    local out = {}
    for _, p in ipairs(self.pens or {}) do out[#out + 1] = { p.dev, p.tag } end
    return out
  end
  w.draw = function(self, app, b)
    local width, h = self.w or 20, self.h or 6
    local cols = width - 1
    local period = self.period or math.max(0.5, (self.span or 600) / cols)
    local now = clock()
    if self.last == nil or now - self.last >= period then
      self.last = now
      for i, p in ipairs(self.pens or {}) do
        local s = self.samples[i] or {}
        s[#s + 1] = app:value(p.dev, p.tag) or false
        while #s > cols do table.remove(s, 1) end
        self.samples[i] = s
      end
    end
    for yy = 0, h - 1 do b:text(self.x, self.y + yy, "\149", R.static) end
    b:text(self.x, self.y + h - 1, "\141" .. string.rep("\140", cols), R.static)
    for i, p in ipairs(self.pens or {}) do
      local s = self.samples[i] or {}
      local lo, hi = p.lo or 0, p.hi or 1
      for k = 1, #s do
        local v = s[k]
        local x = self.x + cols - #s + k
        if v then
          local f = (v - lo) / (hi - lo)
          if f < 0 then f = 0 elseif f > 1 then f = 1 end
          b:text(x, self.y + (h - 2) - math.floor(f * (h - 2) + 0.5), "\7", PEN[(i - 1) % #PEN + 1])
        else
          b:text(x, self.y + h - 2, "?", R.bad)
        end
      end
    end
  end
  return w
end

-- Button{x, y, w, label, onPress, confirm=bool}: confirm makes it select-before-operate:
-- the first touch arms it for 5 s, the second executes.
function W.Button(o)
  local w = new("Button", o)
  w.armed = nil
  w.bind = function() return {} end
  w.draw = function(self, app, b)
    local width = self.w or (#self.label + 2)
    local armed = self.armed and clock() - self.armed < 5
    if not armed then self.armed = nil end
    local text = armed and "CONFIRM?" or self.label
    if #text > width - 2 then text = text:sub(1, width - 2) end
    local pad = width - 2 - #text
    b:text(self.x, self.y, "[" .. text .. string.rep(" ", pad) .. "]", armed and R.text or R.text, armed and R.p2 or R.accent)
  end
  w.touch = function(self, app, x, y)
    if not inside(self, x, y, self.w or (#self.label + 2), 1) then return false end
    local armed = self.armed and clock() - self.armed < 5
    if self.confirm and not armed then
      self.armed = clock()
      return true
    end
    self.armed = nil
    if self.onPress then self.onPress(app) end
    return true
  end
  return w
end

-- Status{x, y, w, label, get=function(app) return text, role end}: a status tile of the overview (L1).
function W.Status(o)
  local w = new("Status", o)
  w.bind = function(self) return self.binds or {} end
  w.draw = function(self, app, b)
    local text, role = "", nil
    if self.get then text, role = self.get(app) end
    local s = (self.label and self.label .. " " or "") .. tostring(text or "")
    if self.w and #s > self.w then s = s:sub(1, self.w) end
    b:text(self.x, self.y, s, role and R[role] or R.text)
  end
  return w
end

-- AlarmList{x, y, w, h, ann, filter}: ISA-18.2 alarm list from a pc_annunciator.
-- Touching a row acknowledges it. Unacknowledged rows flash in the priority colour.
function W.AlarmList(o)
  local w = new("AlarmList", o)
  w.rows, w.stats, w.fetched = {}, nil, nil
  w.bind = function() return {} end
  w.refresh = function(self)
    if not self.ann then return end
    local ok, list = pcall(self.ann.getAlarms, self.filter)
    if ok and type(list) == "table" then
      local rows = {}
      for _, a in ipairs(list) do
        if a.state ~= "NORMAL" then rows[#rows + 1] = a end
      end
      self.rows = rows
    end
    local ok2, st = pcall(self.ann.getStatistics)
    if ok2 then self.stats = st end
    self.fetched = clock()
  end
  w.draw = function(self, app, b)
    if self.fetched == nil or app.alarmsDirty or clock() - self.fetched >= 1 then
      self:refresh()
      app.alarmsDirty = false
    end
    local width, h = self.w or 40, self.h or 6
    local counts = { 0, 0, 0, 0 }
    for _, a in ipairs(self.rows) do counts[a.priority] = (counts[a.priority] or 0) + 1 end
    local head = string.format("ALARMS P1:%d P2:%d P3:%d", counts[1], counts[2], counts[3])
    if self.stats then
      head = head .. string.format("  10 min: %d  shelved: %d", self.stats.rate10min or 0, self.stats.shelved or 0)
      if self.stats.flood then head = head .. "  FLOOD" end
    end
    if not self.ann then head = "NO pc_annunciator" end
    b:fill(self.x, self.y, width, 1, R.accent)
    b:text(self.x, self.y, head:sub(1, width), self.stats and self.stats.flood and R.p1 or R.text, R.accent)
    for i = 1, h - 1 do
      local a = self.rows[i]
      local y = self.y + i
      if a then
        local pc = palette.priority[a.priority]
        local marker = (palette.marker[a.priority] or " ") .. (a.priority or "")
        local value = a.value and string.format("%.1f", a.value) or ""
        local line = string.format("%-3s %-6s %s", marker, a.state, a.message or a.tag or "")
        line = line:sub(1, width - #value - 1)
        line = line .. string.rep(" ", width - #line - #value) .. value
        local flash = a.state == "UNACK" and app.frameNo % 2 == 0
        if a.state == "UNACK" or a.state == "ACKED" then
          b:text(self.x, y, line, flash and R.text or (pc or R.text), flash and pc or nil)
        else
          b:text(self.x, y, line, R.static)
        end
        a._y = y
      end
    end
  end
  w.touch = function(self, app, x, y)
    if not inside(self, x, y, self.w or 40, self.h or 6) then return false end
    for _, a in ipairs(self.rows) do
      if a._y == y and self.ann then
        pcall(self.ann.acknowledge, a.id)
        self.fetched = nil
        return true
      end
    end
    return true
  end
  return w
end

return W
