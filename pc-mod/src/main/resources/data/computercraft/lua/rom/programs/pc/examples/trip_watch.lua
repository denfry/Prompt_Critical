-- Trip watch: subscribes to one tag of a reactor and requests a trip when it stays above the limit.
-- Usage: trip_watch <tag> <limit>     (list the tags with: lua peripheral.find("pc_reactor").listTags())
local args = { ... }
local reactor = peripheral.find("pc_reactor")
if not reactor then error("no pc_reactor on the network", 0) end
local tag, limit = args[1], tonumber(args[2])
if not tag or not limit then
  print("Usage: trip_watch <tag> <limit>")
  for _, t in ipairs(reactor.listTags()) do print("  " .. t) end
  return
end

-- 2 % deadband, 1 s on-delay: a short spike does not trip the reactor
local watch = reactor.watch(tag, -math.huge, limit, math.abs(limit) * 0.02, 1)
print(("Watching %s > %g (watch %d)"):format(tag, limit, watch))

while true do
  local event, side, p = os.pullEvent()
  if event == "pc_setpoint_crossed" and p.watchId == watch and p.direction == "HIGH" then
    local ok, ticket = pcall(reactor.requestTrip, tag .. " high")
    print(ok and ("Trip requested, ticket " .. ticket) or ("Trip refused: " .. ticket))
  elseif event == "pc_command_result" then
    print(("Ticket %d: %s %s"):format(p.ticket, p.state, p.err or ""))
  elseif event == "pc_scram" then
    print(("SCRAM at t = %.1f s, source %s"):format(p.t, p.source))
  elseif event == "pc_event_overflow" then
    print("Events lost, reading the state again")
    print(textutils.serialize(reactor.getStatus()))
  end
end
