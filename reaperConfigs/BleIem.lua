local last_project = nil

function ble_iem()
  local _, project_fn = reaper.EnumProjects(-1)

  if last_project ~= project_fn then
    last_project = project_fn
    reaper.SetExtState("BleIem", "ProjectName", project_fn, false)
  end

  reaper.defer(ble_iem)
end

ble_iem()