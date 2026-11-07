# WMD Changelog

## 1.0.1 (7 Nov 2026)

### New
- **Gasoline Engine** (4 x 3 x 3): a turbocharged V8 generator, 1,250 FE/t from piped gasoline or reformate.
- **Free-Piston Generator** (5 x 3 x 3): 2,000 FE/t from reformate.
- Both need no research, have a field manual page and show their fuels in JEI. Reformate finally has a use.
- **BlueMap**: WMD machines can show on the web map. Put `wmd_bluemap.zip` in `config/bluemap/packs/` and re-render.

### Shaders (Iris / Oculus)
- Explosion smoke, mushroom clouds, shockwaves, fireballs and the EMP now look right with a shaderpack (client option `shaderSafeEffects`).
- Cinematics and the orbit dimension pause the shaderpack while they run and turn it back on afterwards (`shaderPauseCinematics`, `shaderPauseOrbit`).
- Fixed lag and a crash when looking at explosions with a shaderpack on.

### Balance
- The gas turbine now burns gasoline, its best fuel (3,500 FE per mB).

### Fixes
- Magnesium electrolyser: says how much magnesium chloride it still needs instead of "Needs Brine".
- Solar panels: every panel of an array shows the array's real output and sky.
- Drone swarm launcher: shows its reach (1,500 blocks) and warns when a target is too far.
- Missile bay: any part of a module opens the bay, and the screen no longer closes right away when opened away from the core. The rack screen stays open along the whole rack.
- Command center: the CCTV monitor and Iron Dome desk moved so the racks are free (existing ones move on their own).
- Rod of Death: everyone sees the countdown from the moment it is armed.
- Missile cam: no green patches over Distant Horizons land; drone feeds show "DRONE #1 BAT 80%".
- Big machines no longer vanish at some camera angles; command center walls no longer turn see-through from inside.
- Cinematics: no strip of sky between near and distant land.
- Big blasts: breaking far-away glass no longer freezes the server for a moment.

### Known issues
- BSL shaders are not supported (explosion smoke drops the frame rate). Use Complementary Reimagined / Unbound or turn shaders off.
