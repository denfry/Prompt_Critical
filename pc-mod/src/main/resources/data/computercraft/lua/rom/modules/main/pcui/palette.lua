-- pcui.palette: ISA-101 palette for the 16 CC colours (Prompt Critical GDD 03c §3c.3.2).
-- Grey background, muted static, colour only for deviations and alarms.
local palette = {}

palette.roles = {
  bg = colors.lightGray,      -- background of every screen
  static = colors.gray,       -- pipes, outlines, frames
  text = colors.black,        -- labels
  accent = colors.white,      -- selected element, dialog background
  value = colors.blue,        -- live process values
  normal = colors.lightBlue,  -- normal range band
  setpoint = colors.cyan,     -- operator setpoints
  p1 = colors.red,            -- priority 1
  p2 = colors.orange,         -- priority 2
  p3 = colors.yellow,         -- priority 3 (priority 4 is the journal, no colour)
  bad = colors.magenta,       -- BAD or stale data
  manual = colors.purple,     -- device in LOCAL / MAINTENANCE
}

palette.rgb = {
  [colors.lightGray] = 0xC4C4C4,
  [colors.gray] = 0x8C8C8C,
  [colors.black] = 0x1E1E1E,
  [colors.white] = 0xF4F4F4,
  [colors.blue] = 0x2B4C7E,
  [colors.lightBlue] = 0x9DB4CC,
  [colors.cyan] = 0x007C80,
  [colors.red] = 0xE00000,
  [colors.orange] = 0xFF8C00,
  [colors.yellow] = 0xF2D200,
  [colors.magenta] = 0xB000B0,
  [colors.purple] = 0x5A3E8C,
  [colors.green] = 0x5E7F5E,
}

-- Priority colour and marker shape: red/orange/yellow also differ by shape
-- (triangle, diamond, dot in the CC font) and by the digit, for colour-blind players.
palette.priority = { [1] = colors.red, [2] = colors.orange, [3] = colors.yellow }
palette.marker = { [1] = "\30", [2] = "\4", [3] = "\7" }

local HEX = "0123456789abcdef"

-- Blit character of a colour (colours.toBlit without depending on its presence).
function palette.hex(c)
  local n = 0
  local v = c
  while v > 1 do
    v = v / 2
    n = n + 1
  end
  return HEX:sub(n + 1, n + 1)
end

-- Set the ISA-101 RGB values on a terminal or monitor.
function palette.apply(t)
  if not t.setPaletteColour then return end
  for c, rgb in pairs(palette.rgb) do
    t.setPaletteColour(c, rgb)
  end
end

return palette
