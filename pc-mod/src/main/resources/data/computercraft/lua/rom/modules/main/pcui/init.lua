-- pcui: operator screens for Prompt Critical (GDD 03c §3c.4, ISA-101 high-performance HMI).
--
--   local pcui = require("pcui")
--   local app = pcui.app({ monitor = peripheral.find("monitor"), title = "UNIT 1", fps = 2 })
--   local ov = app:page("overview", { level = 1, title = "OVERVIEW", hotkey = "1" })
--   local ann = peripheral.find("pc_annunciator")
--   ov:add(pcui.Value{ x = 2, y = 3, dev = ann, tag = "crude_column@10,64,5/prod.outlet", unit = "degC", fmt = "%5.1f" })
--   ov:add(pcui.AlarmList{ x = 2, y = 20, w = 76, h = 8, ann = ann })
--   app:run()
--
-- A device ("dev") is anything with readMany(tags) -> {tag = value}: pc_annunciator reads
-- every machine tag within 16 blocks; pcui.rtu(rtu, machineId) adapts a pc_rtu gateway;
-- pcui.source(fn) wraps a function. Each frame the app reads every device of the page
-- once, with all the page's tags (§3c.4.2), and writes only the changed screen rows.
local palette = require("pcui.palette")
local draw = require("pcui.draw")
local units = require("pcui.units")
local widgets = require("pcui.widgets")

local unpack = table.unpack or unpack

local pcui = {
  VERSION = "1.0",
  palette = palette,
  units = units,
  draw = draw,
  fmt = { si = units.si },
  config = { maxFps = 4, maxFpsLarge = 2, staleAfter = 2 },
}
for k, v in pairs(widgets) do pcui[k] = v end

-- ------------------------------------------------------------------ devices

local sources = setmetatable({}, { __mode = "k" })
local nextKey = 0

-- A data source from a function(tags) -> {tag = value}, or from a peripheral with readMany.
function pcui.source(f, key)
  if type(f) == "function" then
    nextKey = nextKey + 1
    return { key = key or ("fn" .. nextKey), readMany = f, pcuiSource = true }
  end
  return pcui.asSource(f)
end

-- A pc_rtu gateway and one of its machines as a data source: tags are the point names.
function pcui.rtu(rtu, machineId)
  return { key = "rtu:" .. tostring(machineId), pcuiSource = true, readMany = function()
    return rtu.read(machineId)
  end }
end

function pcui.asSource(dev)
  if dev == nil then return nil end
  if type(dev) == "string" then dev = peripheral.wrap(dev) end
  if type(dev) ~= "table" then return nil end
  if dev.pcuiSource then return dev end
  local s = sources[dev]
  if s then return s end
  if type(dev.readMany) ~= "function" then return nil end
  local name = peripheral.getName and select(2, pcall(peripheral.getName, dev))
  nextKey = nextKey + 1
  s = { key = type(name) == "string" and name or ("dev" .. nextKey), pcuiSource = true, readMany = dev.readMany }
  sources[dev] = s
  return s
end

-- Find a device by its technological label (03b §3b.1.2) or by peripheral name.
function pcui.dev(label)
  if peripheral.isPresent(label) then return peripheral.wrap(label) end
  for _, name in ipairs(peripheral.getNames()) do
    local ok, info = pcall(peripheral.call, name, "getDeviceInfo")
    if ok and type(info) == "table" and info.label == label then return peripheral.wrap(name) end
  end
  return nil
end

-- ------------------------------------------------------------------ app and pages

local App, Page = {}, {}
App.__index = App
Page.__index = Page

-- opts = {monitor=, fps=, title=, station=, annunciator=, clock=true, textScale=0.5}
function pcui.app(opts)
  opts = opts or {}
  local mon = opts.monitor or term.current()
  if mon.setTextScale then mon.setTextScale(opts.textScale or 0.5) end
  palette.apply(mon)
  local w, h = mon.getSize()
  local maxFps = (w * h > 79 * 38) and pcui.config.maxFpsLarge or pcui.config.maxFps
  local app = setmetatable({
    mon = mon, title = opts.title or "", station = opts.station, clock = opts.clock ~= false,
    fps = math.min(opts.fps or 2, maxFps), pages = {}, order = {}, values = {}, errors = {},
    frameNo = 0, reported = {}, alarmsDirty = true, w = w, h = h,
  }, App)
  app.buf = draw.new(w, h)
  app.ann = opts.annunciator
  if app.ann == nil and peripheral.find then app.ann = peripheral.find("pc_annunciator") end
  return app
end

-- opts = {level=1..4, parent=, title=, hotkey=}: level is the ISA-101 screen level (§3c.5.1).
function App:page(id, opts)
  opts = opts or {}
  local p = setmetatable({ id = id, level = opts.level or 1, parent = opts.parent, title = opts.title or id,
    hotkey = opts.hotkey, widgets = {} }, Page)
  self.pages[id] = p
  self.order[#self.order + 1] = p
  if not self.current then self.current = p end
  return p
end

function Page:add(w)
  self.widgets[#self.widgets + 1] = w
  return w
end

function App:show(id)
  local p = self.pages[id]
  if p and p ~= self.current then
    self.current = p
    self.alarmsDirty = true
  end
end

-- One readMany per device for every tag of the visible page; returns the number of distinct tags.
function App:read()
  local bySrc, order = {}, {}
  for _, w in ipairs(self.current.widgets) do
    for _, bnd in ipairs(w:bind()) do
      local src = pcui.asSource(bnd[1])
      if src and bnd[2] then
        local e = bySrc[src.key]
        if not e then
          e = { src = src, tags = {}, seen = {} }
          bySrc[src.key] = e
          order[#order + 1] = e
        end
        if not e.seen[bnd[2]] then
          e.seen[bnd[2]] = true
          e.tags[#e.tags + 1] = bnd[2]
        end
      end
    end
  end
  local n = 0
  for _, e in ipairs(order) do
    n = n + #e.tags
    local ok, res = pcall(e.src.readMany, e.tags)
    if ok and type(res) == "table" then
      self.values[e.src.key] = res
      self.errors[e.src.key] = nil
    else
      self.values[e.src.key] = {}
      self.errors[e.src.key] = res
    end
  end
  return n
end

function App:value(dev, tag)
  local src = pcui.asSource(dev)
  if not src then return nil end
  local v = self.values[src.key]
  return v and v[tag]
end

-- The station learns about its HMI: an L1 overview with its tag count (achievement "hmi", 11 §11.2.2).
function App:report(n)
  local p = self.current
  if p.level ~= 1 or not self.ann or self.reported[p.id] == n then return end
  self.reported[p.id] = n
  if self.ann.reportScreen then pcall(self.ann.reportScreen, p.level, n) end
end

local function header(app, b)
  local p = app.current
  b:fill(1, 1, app.w, 1, palette.roles.static)
  b:text(2, 1, (app.title ~= "" and app.title .. "  " or "") .. p.title, palette.roles.accent, palette.roles.static)
  local right = "[OVERVIEW]"
  if p.level > 1 and p.parent then right = "[<] " .. right end
  if app.clock and textutils and textutils.formatTime then
    right = textutils.formatTime(os.time(), true) .. "  " .. right
  end
  b:text(app.w - #right, 1, right, palette.roles.accent, palette.roles.static)
end

local function footer(app, b)
  local x = 2
  b:fill(1, app.h, app.w, 1, palette.roles.static)
  for _, p in ipairs(app.order) do
    if p.hotkey then
      local t = "[" .. p.hotkey .. "]" .. p.title .. " "
      b:text(x, app.h, t, p == app.current and palette.roles.text or palette.roles.accent,
        p == app.current and palette.roles.accent or palette.roles.static)
      p._tab = { x, x + #t - 1 }
      x = x + #t
    end
  end
end

-- Draw one frame: batch read, widgets, flush of the changed rows.
function App:render()
  self.frameNo = self.frameNo + 1
  local n = self:read()
  self:report(n)
  local b = self.buf
  b:clear()
  header(self, b)
  for _, w in ipairs(self.current.widgets) do w:draw(self, b) end
  footer(self, b)
  return b:flush(self.mon)
end

function App:overview()
  for _, p in ipairs(self.order) do
    if p.level == 1 then return p end
  end
  return self.order[1]
end

function App:touch(x, y)
  if y == 1 then
    if x >= self.w - 10 then
      self:show(self:overview().id)
    elseif self.current.parent and x >= self.w - 14 then
      self:show(self.current.parent)
    end
    return
  end
  if y == self.h then
    for _, p in ipairs(self.order) do
      if p._tab and x >= p._tab[1] and x <= p._tab[2] then self:show(p.id) end
    end
    return
  end
  for _, w in ipairs(self.current.widgets) do
    if w.touch and w:touch(self, x, y) then return end
  end
end

-- Event loop: frames on a timer, touches, hotkeys 1-9, alarm events refresh the list.
function App:run()
  self.running = true
  self:render()
  local timer = os.startTimer(1 / self.fps)
  while self.running do
    local ev = { os.pullEvent() }
    local name = ev[1]
    if name == "timer" and ev[2] == timer then
      self:render()
      timer = os.startTimer(1 / self.fps)
    elseif name == "monitor_touch" or name == "mouse_click" then
      self:touch(ev[3], ev[4])
      self:render()
    elseif name == "char" then
      for _, p in ipairs(self.order) do
        if p.hotkey == ev[2] then self:show(p.id) end
      end
    elseif name == "pc_alarm" or name == "pc_alarm_clear" or name == "pc_alarm_ack" or name == "pc_alarm_flood" then
      self.alarmsDirty = true
    elseif name == "monitor_resize" or name == "term_resize" then
      self.w, self.h = self.mon.getSize()
      self.buf = draw.new(self.w, self.h)
    end
    if self.onEvent then self.onEvent(unpack(ev)) end
  end
end

function App:stop()
  self.running = false
end

return pcui
