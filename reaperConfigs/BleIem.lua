function ble_iem()
  local null, project_fn = reaper.EnumProjects(-1)
  reaper.SetExtState("BleIem", "ProjectName", project_fn, false)
  reaper.defer(ble_iem)
end

ble_iem()