-- pcui.draw: an off-screen character buffer. Widgets draw into it every frame;
-- flush() writes only the rows that changed since the last frame, so a static
-- page costs no terminal writes at all (GDD 03c §3c.3.1, test T-3c-08).
local palette = require("pcui.palette")

local draw = {}
local Buffer = {}
Buffer.__index = Buffer

function draw.new(w, h)
  local b = setmetatable({ w = w, h = h, t = {}, f = {}, g = {}, front = {}, writes = 0 }, Buffer)
  b:clear()
  return b
end

function Buffer:clear(bg)
  local hb = palette.hex(bg or palette.roles.bg)
  local hf = palette.hex(palette.roles.text)
  for y = 1, self.h do
    local t, f, g = {}, {}, {}
    for x = 1, self.w do
      t[x] = " "
      f[x] = hf
      g[x] = hb
    end
    self.t[y], self.f[y], self.g[y] = t, f, g
  end
end

-- Text at (x, y); fg/bg are CC colours, nil keeps what is there.
function Buffer:text(x, y, s, fg, bg)
  if y < 1 or y > self.h then return end
  s = tostring(s)
  local hf = fg and palette.hex(fg)
  local hb = bg and palette.hex(bg)
  local row, frow, grow = self.t[y], self.f[y], self.g[y]
  for i = 1, #s do
    local cx = x + i - 1
    if cx >= 1 and cx <= self.w then
      row[cx] = s:sub(i, i)
      if hf then frow[cx] = hf end
      if hb then grow[cx] = hb end
    end
  end
end

-- Rectangle of background colour bg (optionally filled with character ch in fg).
function Buffer:fill(x, y, w, h, bg, ch, fg)
  local line = string.rep(ch or " ", math.max(0, w))
  for yy = y, y + h - 1 do
    self:text(x, yy, line, fg, bg)
  end
end

-- Write the changed rows to the terminal; returns the number of rows written.
function Buffer:flush(term)
  local n = 0
  for y = 1, self.h do
    local t, f, g = table.concat(self.t[y]), table.concat(self.f[y]), table.concat(self.g[y])
    local fr = self.front[y]
    if not fr or fr[1] ~= t or fr[2] ~= f or fr[3] ~= g then
      term.setCursorPos(1, y)
      term.blit(t, f, g)
      self.front[y] = { t, f, g }
      n = n + 1
    end
  end
  self.writes = self.writes + n
  return n
end

-- Forget what is on screen (after a resize or a foreign write): next flush redraws everything.
function Buffer:invalidate()
  self.front = {}
end

return draw
