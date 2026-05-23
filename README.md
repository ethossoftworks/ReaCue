# ReaCue

An app for IEM mixing over bluetooth

## Components
1. A MacOS Server application
2. An Android/iOS client application

## Prerequisites
1. Reaper needs to have a web interface running
2. Reaper needs to have an OSC instance running the configuration found in this project: `/reaperConfig/BleIem.ReaperOSC`
3. Install `reaperConfigs/BleIem.lua` into scripts and add `dofile(reaper.GetResourcePath() .. "/Scripts/BleIem.lua")` to `__startup.lua` or create new `__startup.lua` file.