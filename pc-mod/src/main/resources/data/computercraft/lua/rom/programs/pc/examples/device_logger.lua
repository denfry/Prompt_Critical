-- Device logger: appends every analog tag of every Prompt Critical device to devices.csv once a minute.
local file = "devices.csv"

local function line(name, dev)
  local tags = {}
  for _, t in ipairs(dev.listTags()) do
    if dev.getTagInfo(t).kind == "ANALOG" then tags[#tags + 1] = t end
    if #tags == 64 then break end
  end
  if #tags == 0 then return nil end
  local values = dev.readMany(tags)
  local out = { name }
  for _, t in ipairs(tags) do out[#out + 1] = t .. "=" .. tostring(values[t].v) end
  return table.concat(out, ";")
end

while true do
  local h = fs.open(file, "a")
  for _, name in ipairs(peripheral.getNames()) do
    if peripheral.hasType(name, "pc_device") then
      local ok, text = pcall(line, name, peripheral.wrap(name))
      if ok and text then h.writeLine(os.epoch("utc") .. ";" .. text) end
    end
  end
  h.close()
  sleep(60)
end
