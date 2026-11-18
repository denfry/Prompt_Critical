-- pcui.units: SI to operator units (GDD 03c §3c.4.2). Devices report SI (03b);
-- screens show MPa, degC, %, t/h, pcm and uSv/h.
local units = {}

function units.MPa(p) return p / 1e6 end
function units.kPa(p) return p / 1e3 end
function units.degC(T) return T - 273.15 end
function units.pct(x) return x * 100 end
function units.tph(mdot) return mdot * 3.6 end
function units.pcm(rho) return rho * 1e5 end
function units.uSvh(H) return H * 3.6e9 end

local PREFIX = { [-4] = "p", [-3] = "n", [-2] = "u", [-1] = "m", [0] = "", [1] = "k", [2] = "M", [3] = "G", [4] = "T" }

-- Engineering notation: si(15480000, "Pa", 3) -> "15.5 MPa".
function units.si(x, unit, digits)
  unit = unit or ""
  digits = digits or 3
  if x == nil or x ~= x then return "----" .. (unit ~= "" and " " .. unit or "") end
  if x == 0 then return "0" .. (unit ~= "" and " " .. unit or "") end
  local e = math.floor(math.log(math.abs(x)) / math.log(10) / 3 + 1e-9)
  if e < -4 then e = -4 elseif e > 4 then e = 4 end
  local m = x / 10 ^ (3 * e)
  local int = #tostring(math.floor(math.abs(m)))
  local dec = math.max(0, digits - int)
  return string.format("%." .. dec .. "f", m) .. " " .. PREFIX[e] .. unit
end

return units
