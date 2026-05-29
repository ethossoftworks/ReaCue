local last_project = nil

function reacue_fader_info()
  local curve = -1.0
  local max_db = 12.0
  local min_db = -72.0

  local ini_path = reaper.get_ini_file()
  if not ini_path or ini_path == "" then
    reaper.SetExtState("ReaCue", "FaderInfo", string.format("%f;%f;%f", shape, min_db, max_db), false)
    return
  end

  local f = io.open(ini_path, "r")
  if not f then return end

  for line in f:lines() do
    local key, val = line:match("^%s*([^=]+)%s*=%s*(.-)%s*$")
    if not key or not val then goto continue end

    if key == "slidershex" then curve = tonumber(val) or -1.0
    elseif key == "sliderminv" then min_db = tonumber(val) or -72.0
    elseif key == "slidermaxv" then max_db = tonumber(val) or 12.0
    end
    ::continue::
  end
  f:close()

  reaper.SetExtState("ReaCue", "FaderInfo", string.format("%f;%f;%f", curve, min_db, max_db), false)
end


function reacue_loop()
  local _, project_fn = reaper.EnumProjects(-1)

  if last_project ~= project_fn then
    last_project = project_fn
    reaper.SetExtState("ReaCue", "ProjectName", project_fn, false)
  end

  reaper.defer(reacue_loop)
end

reacue_fader_info()
reacue_loop()