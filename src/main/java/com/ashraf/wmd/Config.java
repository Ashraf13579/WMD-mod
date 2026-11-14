package com.ashraf.wmd;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    @Deprecated static final java.util.function.BooleanSupplier LOG_DIRT_BLOCK = () -> false;
    @Deprecated static final java.util.function.Supplier<String> MAGIC_NUMBER_INTRODUCTION = () -> "WMD config ready, schema v";
    @Deprecated static final java.util.function.IntSupplier MAGIC_NUMBER = () -> 2;
    @Deprecated static final java.util.function.Supplier<List<String>> ITEM_STRINGS = List::of;

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> GUIDANCE_MISS_RADIUS = BUILDER
            .comment("Max miss radius in blocks for guidance grade 1, 2, 3, 4. Most shots land nearer the centre (half of them within half the radius).")
            .defineList("guidanceMissRadius", List.of(30, 20, 9, 3), () -> 10, o -> o instanceof Integer i && i >= 0 && i <= 500);

    public static final ModConfigSpec.IntValue GRID_CONVERTER_MAX = BUILDER
            .comment("Grid converter: the most FE per tick one converter hands to the WMD busbar grid (after the loss).")
            .defineInRange("gridConverterMaxFePerTick", 5_000, 0, 10_000_000);
    public static final ModConfigSpec.IntValue GRID_CONVERTER_EFFICIENCY = BUILDER
            .comment("Grid converter: percent of the foreign FE that comes out (50 = half is lost).")
            .defineInRange("gridConverterEfficiency", 50, 1, 100);

    public static final ModConfigSpec.BooleanValue SHOCKWAVE_BREAKS_GLASS = BUILDER
            .comment("Big explosions (tier 3+ and nukes): the pressure wave shatters glass out to 60% of its reach and tears leaves off trees out to 40% (loaded chunks only; blast glass resists).")
            .define("shockwaveBreaksGlass", true);

    public static boolean shockwaveBreaksGlass() { return !SPEC.isLoaded() || SHOCKWAVE_BREAKS_GLASS.get(); }

    static final MissileConfig.Values MISSILES = MissileConfig.define(BUILDER);

    public static final ModConfigSpec.BooleanValue SALVAGE_SITES = BUILDER
            .comment("World generation: rare salvage sites (abandoned radar station, decommissioned silo, crashed satellite), guarded by turrets. false = no new sites in newly generated chunks (sites already built stay).")
            .define("salvageSites", true);

    public static boolean salvageSites() { return !SPEC.isLoaded() || SALVAGE_SITES.get(); }

    static final java.util.List<String> ORE_IDS = java.util.List.of("tin", "lead", "silver", "platinum", "bauxite", "titanium", "tungsten", "nitrate", "rutile_deposit", "wolframite_deposit", "uraninite_deposit", "oil");
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_ORE_GEN = BUILDER
            .comment("World generation: WMD ores that are NOT generated in new chunks (for modpacks that already have them): tin, lead, silver, platinum, bauxite, titanium, tungsten, nitrate, rutile_deposit, wolframite_deposit, uraninite_deposit, oil.")
            .defineListAllowEmpty("disabledOreGen", List.of(), () -> "tin", o -> o instanceof String s && ORE_IDS.contains(s));

    public static boolean oreGen(String id) { return !SPEC.isLoaded() || !DISABLED_ORE_GEN.get().contains(id); }

    public static final ModConfigSpec.BooleanValue REACTOR_MELTDOWN = BUILDER
            .comment("Nuclear reactor: true = an overheated reactor (heat 1000) melts down and explodes like a small nuke (no radiation). false = it SCRAMs and is DAMAGED instead (fix it with a reactor repair kit).")
            .define("reactorMeltdown", true);

    public static boolean reactorMeltdown() { return !SPEC.isLoaded() || REACTOR_MELTDOWN.get(); }

    public static final ModConfigSpec.BooleanValue COCKPIT_VOICE = AirGate.AVAILABLE ? BUILDER
            .comment("Jet cockpit: spoken warnings (\"PULL UP\", \"MISSILE\") in your headset. false = warning tones only. Only this computer.")
            .define("cockpitVoice", true) : null;

    public static boolean cockpitVoice() { return COCKPIT_VOICE == null || !SPEC.isLoaded() || COCKPIT_VOICE.get(); }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double missRadius(int grade) {
        List<? extends Integer> l = GUIDANCE_MISS_RADIUS.get();
        int i = Math.max(1, Math.min(4, grade)) - 1;
        return i < l.size() ? l.get(i) : 0;
    }

    public static final class Server {
        private Server() {}

        private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

        static { B.comment("Explosions and what they do to the world.").push("explosions"); }
        public static final ModConfigSpec.BooleanValue EXPLOSION_BLOCK_DAMAGE = B
                .comment("Warheads, shells, rockets and the super gun change blocks (craters, fire, molten rock, fissures, broken glass, cave-ins). false = they only hurt people; the world stays as it is.")
                .define("explosionBlockDamage", true);
        public static final ModConfigSpec.IntValue MAX_CRATER_RADIUS = B
                .comment("The largest crater radius in blocks any blast may carve (a bigger one is cut down to this). The biggest warheads carve about 150.")
                .defineInRange("maxCraterRadius", 256, 1, 512);
        public static final ModConfigSpec.IntValue PROTECT_SPAWN_RADIUS = B
                .comment("Blocks within this many blocks (horizontal) of the overworld spawn are never changed by WMD blasts (0 = off). People there are still hurt.")
                .defineInRange("protectSpawnRadius", 0, 0, 100_000);
        public static final ModConfigSpec.BooleanValue RESPECT_CLAIMS = B
                .comment("WMD blasts leave land alone where a claims mod protects it from explosions (Open Parties and Claims, FTB Chunks, ... each mod's own explosion setting decides). People there are still hurt. false = blasts ignore claims.")
                .define("respectClaims", true);
        public static final ModConfigSpec.BooleanValue RESPECT_CLAIMS_PLACING = B
                .comment("Big WMD machines (radar, silos, command center hall, every multi-block machine) may not reach into land that a claims mod (Open Parties and Claims, FTB Chunks, ...) protects from the placing player: each chunk they reach into is asked with the normal block-place event. false = only the clicked block is checked (the old behaviour).")
                .define("respectClaimsPlacing", true);
        public static final ModConfigSpec.BooleanValue OFFLINE_PROTECTION = B
                .comment("PvP server rule: when a base's owner and everyone they trust are offline (for offlineProtectionGraceMinutes), WMD blasts change no blocks "
                        + "within offlineProtectionRadius of their command center buildings, and launches / artillery aimed there are refused. The owner is told on login "
                        + "how many strikes were blocked. false = bases can be hit any time.")
                .define("offlineProtection", false);
        public static final ModConfigSpec.IntValue OFFLINE_RADIUS = B
                .comment("Blocks round each command center building that offline protection covers (horizontal).")
                .defineInRange("offlineProtectionRadius", 128, 16, 2048);
        public static final ModConfigSpec.IntValue OFFLINE_GRACE = B
                .comment("Minutes after the last member of a base's team logs out before offline protection starts (no logging out in the middle of an attack).")
                .defineInRange("offlineProtectionGraceMinutes", 15, 0, 1440);
        public static final ModConfigSpec.BooleanValue TANK_DAMAGE = B
                .comment("Fluid tanks take damage: missiles, bullets and explosions crack the glass Storage Tank (it shatters when it fails) and dent the aircraft fuel tanks, drop tanks and the power core; a damaged tank leaks, burns if its contents burn, and finally explodes or bursts depending on the liquid (fuel fireballs, gas fireballs, oil fires, steam, poison clouds). Craters and fires follow explosionBlockDamage, spawn protection and claims. false = tanks are never damaged.")
                .define("tankDamage", true);

        static { B.comment("EXPERIMENTAL explosion features (2-3 Nov 2026): damage rings, building collapse, the moving shockwave and the lingering aftermath. "
                + "OFF by default outside the development game: set one to true to try it (they can be switched while the server runs). They change a lot of blocks "
                + "after a big blast: try them on a copy of your world first.").push("experimental"); }
        public static final ModConfigSpec.BooleanValue DAMAGE_RINGS = B
                .comment("EXPERIMENTAL. Rings of extra damage round a warhead crater: rubble next to it (buildings knocked down, gravel), a burn ring (charred wood, fires), a stripped ring (leaves, grass and crops gone). false = only the crater, as before.")
                .define("damageRings", !net.neoforged.fml.loading.FMLEnvironment.production);
        public static final ModConfigSpec.BooleanValue AFTERMATH = B
                .comment("EXPERIMENTAL. A warhead crater keeps smoking for minutes: smoke plumes, glowing embers early on, ash drifting down near it. Visual only. false = the blast show ends after its usual seconds, as before.")
                .define("aftermath", !net.neoforged.fml.loading.FMLEnvironment.production);
        public static final ModConfigSpec.BooleanValue SHOCKWAVE = B
                .comment("EXPERIMENTAL. A blast's pressure wave travels outward and shoves dropped items, falling blocks, boats, minecarts, airships and people beyond the damage range (no extra damage). false = only the instant knockback inside the damage range, as before.")
                .define("shockwave", !net.neoforged.fml.loading.FMLEnvironment.production);
        public static final ModConfigSpec.BooleanValue COLLAPSE = B
                .comment("EXPERIMENTAL. After a warhead crater (and its damage rings), parts of buildings round it that lost their support fall down: a roof, floor or ledge reaching too far from a solid column below it (wood 4, stone 6, concrete 8, steel 10 blocks), or resting on a support too thin for its weight. false = nothing falls, as before.")
                .define("collapse", !net.neoforged.fml.loading.FMLEnvironment.production);
        public static final ModConfigSpec.BooleanValue SHIP_BREAKUP = B
                .comment("EXPERIMENTAL. A hard-hit Sable airship breaks apart: a big blast (or a quarter of its hull gone) cracks it in two, the broken parts burn "
                        + "(smoke and flames following them) and a falling wreck that hits the ground goes off like a bomb its size. false = ships only get blast holes, as before.")
                .define("shipBreakup", !net.neoforged.fml.loading.FMLEnvironment.production);
        static { B.pop(); }
        public static final ModConfigSpec.BooleanValue DEBRIS = B
                .comment("Every WMD blast with a crater (missile warheads, shells, rockets, bomblets, super gun, meltdown) tears chunks of ground and whole trees out and throws them. With Sable installed they are real physics bodies that tumble, land and vanish after debrisLifetimeSeconds; without Sable single falling blocks. Needs explosionBlockDamage. false = no debris.")
                .define("explosionDebris", true);
        public static final ModConfigSpec.IntValue DEBRIS_LIFETIME = B
                .comment("Seconds a debris piece lies around before it vanishes (each piece +-25 %).")
                .defineInRange("debrisLifetimeSeconds", 40, 3, 600);
        public static final ModConfigSpec.IntValue DEBRIS_MAX_LIVE = B
                .comment("Debris pieces (Sable bodies) at once per dimension; a new blast makes the oldest vanish early. 0 = Sable debris off.")
                .defineInRange("debrisMaxLive", 1500, 0, 8192);
        public static final ModConfigSpec.IntValue DEBRIS_PER_BLAST = B
                .comment("At most this many pieces per blast (mostly 1-3 block chips; a mortar shell throws about 6, an HE missile 18, a nuke 182, a thermonuclear warhead up to this).")
                .defineInRange("debrisPiecesPerBlast", 500, 1, 2048);
        public static final ModConfigSpec.IntValue DEBRIS_PER_TICK = B
                .comment("Debris pieces made per server tick per dimension (a big blast's pieces are spread over ticks; its crater waits for them). Lower if a nuke makes the server stutter.")
                .defineInRange("debrisAssemblePerTick", 25, 1, 512);
        public static final ModConfigSpec.BooleanValue DEBRIS_HURTS = B
                .comment("Flying debris hurts and shoves players and mobs it hits.")
                .define("debrisHurts", true);
        public static final ModConfigSpec.DoubleValue DEBRIS_SPEED = B
                .comment("Multiplies how fast debris is thrown (1 = default; lower if pieces fly too far, higher for more drama).")
                .defineInRange("debrisSpeedMultiplier", 1.0, 0.0, 5.0);
        static { B.pop(); B.comment("Which weapons may be fired on this server. Disallowed ones can still be crafted; firing them is refused with a message.").push("weapons"); }
        public static final ModConfigSpec.BooleanValue ALLOW_NUKES = B
                .comment("Tactical nuke, thermonuclear and antimatter warheads may be launched.")
                .define("allowNukes", true);
        public static final ModConfigSpec.BooleanValue NANITES = B
                .comment("v0.8 nanite swarm warhead: true = the swarm eats blocks; false = it only fizzles (no swarm).")
                .define("nanitesEnabled", true);
        public static final ModConfigSpec.BooleanValue NANITES_CLAIMS = B
                .comment("The nanite swarm never eats claimed land, even when blasts ignore claims (respectClaims=false).")
                .define("nanitesRespectClaims", true);
        public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_WARHEADS = B
                .comment("More warhead types that may not be launched, by id: he, incendiary, frag, decoy, thermobaric, cluster, breacher, chaff, bunker, emp, nuke, thermo, rod, hemp, seismic, antimatter.")
                .defineListAllowEmpty("disabledWarheads", List.of(), () -> "rod", o -> o instanceof String id && validWarhead(id));
        public static final ModConfigSpec.BooleanValue ALLOW_SUPER_GUN = B
                .comment("The super gun may fire.")
                .define("allowSuperGun", true);
        public static final ModConfigSpec.IntValue ART_MAX_SHELLS = B
                .comment("Artillery shells and rockets in the air at once per dimension (more = the gun waits: Sky full).")
                .defineInRange("artilleryMaxShellsInFlight", Balance.ART_MAX_SHELLS, 1, 4096);
        static { B.pop(); B.comment("Server load: chunk loading and the recon fill.").push("performance"); }
        public static final ModConfigSpec.BooleanValue LOAD_GUARD = B
                .comment("While the server struggles (its average tick over loadGuardBusyMs), WMD scales its own heavy blast work down: fewer collapse pieces, "
                        + "less thrown debris, fewer smoke zones; over loadGuardOverloadMs much more (no new physics pieces, no airship fractures). Back to normal when the server recovers. false = always full effects.")
                .define("loadGuard", true);
        public static final ModConfigSpec.IntValue LOAD_GUARD_BUSY_MS = B
                .comment("Average server tick (ms) from which the load guard scales WMD's blast work down (50 ms = the game starts to lag).")
                .defineInRange("loadGuardBusyMs", 40, 10, 500);
        public static final ModConfigSpec.IntValue LOAD_GUARD_OVERLOAD_MS = B
                .comment("Average server tick (ms) from which the load guard cuts WMD's blast work hard.")
                .defineInRange("loadGuardOverloadMs", 70, 20, 1000);
        public static final ModConfigSpec.BooleanValue CHUNK_LOADING = B
                .comment("Missiles, shells and rockets force-load the chunks round their impact point shortly before they land, so the crater is carved far from players. false = nothing is force-loaded; a crater in unloaded land is only carved where chunks are loaded.")
                .define("chunkLoadingForImpacts", true);
        public static final ModConfigSpec.IntValue RECON_FILL_IN_FLIGHT = B
                .comment("Recon fill (maps the target square like Chunky): chunks being loaded / generated at the same time.")
                .defineInRange("reconFillChunksInFlight", Balance.RECON_FILL_IN_FLIGHT, 1, 64);
        public static final ModConfigSpec.IntValue RECON_FILL_PER_SECOND = B
                .comment("Recon fill: at most this many new chunks loaded / generated per second (0 = no limit besides the chunks in flight).")
                .defineInRange("reconFillChunksPerSecond", 0, 0, 1_000);
        public static final ModConfigSpec.IntValue RECON_FILL_MAX_MSPT = B
                .comment("Recon fill: no new chunk while the server's average tick takes longer than this (ms; above 50 the server is behind).")
                .defineInRange("reconFillMaxMspt", Balance.RECON_FILL_MAX_MSPT, 5, 1_000);
        static { B.pop(); B.comment("Other mods.").push("compat"); }
        public static final ModConfigSpec.BooleanValue OPEN_ITEM_PORTS = B
                .comment("WMD machine item ports work with vanilla hoppers and other mods' pipes (the old behaviour). false (default) = only WMD ducts, robots, the machines' own output chutes and players move items in and out.")
                .define("openItemPorts", false);
        public static final ModConfigSpec.BooleanValue ENABLE_SABLE_BRIDGE = B
                .comment("Sable / Create Aeronautics ships (only when Sable is installed): WMD machines standing on a ship measure ranges from the ship's real position; later also radar contacts, hits and collateral damage on ships. false = WMD ignores Sable completely (as if it were not installed).")
                .define("enableSableBridge", true);
        public static final ModConfigSpec.IntValue LARGE_SHIP_MIN_BLOCKS = B
                .comment("Sable / Aeronautics ships: a ship counts as LARGE from this many blocks on (block count only, mass is not used). Only large ships are radar contacts, early-warning / warning board lines and recon \"airship\" icons; smaller boats and debris stay invisible. Read real ships with /wmd dev ships.")
                .defineInRange("largeShipMinBlocks", 350, 1, 10_000_000);
        public static final ModConfigSpec.BooleanValue RELINK_ON_ASSEMBLY = B
                .comment("Sable ships: when machines are assembled into a ship or a ship is disassembled, WMD moves their saved positions along (strike plans, multiblock parts, command center modules, network cards). false = links to moved machines break and must be re-bound by hand.")
                .define("relinkOnAssembly", true);
        public static final ModConfigSpec.IntValue AIR_CEILING = B
                .comment("Sable air (overworld): the height where the air ends. Wings, flaps and jet engines work up to here and get weaker the higher they go; "
                        + "aircraft with more thrust and more wing climb closer to it (default 1100: one jet reaches about 600, two big jets about 1000). "
                        + "NOTE: this changes the air for EVERY Sable object, not only WMD aircraft: Create Aeronautics balloons, propellers and ships "
                        + "can also go this high. 0 = leave Sable's own air (it ends at the build limit, y 320). A datapack with its own Sable "
                        + "dimension_physics file for the overworld still wins. Takes effect on server start or /reload.")
                .defineInRange("airCeiling", 1100, 0, 4000);
        public static final ModConfigSpec.BooleanValue CRASH_DAMAGE = B
                .comment("Aircraft crashes: hitting the ground, a building or another craft fast (about 15 b/s and more; a normal landing never counts) breaks blocks off the aircraft round the impact and damages its engines there, more the faster it hit. false = aircraft bounce off undamaged.")
                .define("crashDamage", true);
        public static final ModConfigSpec.BooleanValue CRASH_DAMAGES_WORLD = B
                .comment("Aircraft crashes also break the blocks they hit (like an explosion: only where explosionBlockDamage, spawn protection and claims allow; never containers). false = buildings and ground stay untouched.")
                .define("crashDamagesWorld", true);
        public static final ModConfigSpec.BooleanValue CRASH_HURTS_CREW = B
                .comment("Aircraft crashes hurt everyone aboard, more the faster the impact: a scrape stings, a full crash (about 55 b/s) kills. Ejecting first saves you. false = the crew is never hurt.")
                .define("crashHurtsCrew", true);
        public static final ModConfigSpec.BooleanValue HARD_LANDING_BREAKS_GEAR = B
                .comment("Landing gear breaks off when an aircraft touches down sinking too fast (more than 12 b/s). false = gear never breaks.")
                .define("hardLandingBreaksGear", true);
        public static final ModConfigSpec.BooleanValue SHIP_FRIENDLY_FIRE = B
                .comment("Sable / Aeronautics ships: WMD missiles, shells, rockets and blasts also hit a ship that carries only your own or trusted machines and players (blast damage in reach and projectiles striking it in flight). false = such a ship is spared; a ship with no machines and no players aboard is always hit.")
                .define("shipFriendlyFire", true);

        public static final ModConfigSpec.BooleanValue ENABLE_DEEP_SEAS_BRIDGE = B
                .comment("Create: Deep Seas submarines (only when it is installed): WMD reads a submarine's depth, sealing and breaches, and a big hit can implode it. false = WMD ignores Deep Seas (submarines are plain Sable ships).")
                .define("enableDeepSeasBridge", true);
        public static final ModConfigSpec.BooleanValue NAVAL_SUB_HOLES = B
                .comment("WMD blasts that reach a SUBMERGED submarine (or any submerged Sable ship) blow holes in its hull (its own flooding then reacts). false = blasts only shake a submerged ship, no blocks break.")
                .define("navalSubHoles", true);
        public static final ModConfigSpec.BooleanValue NAVAL_SUB_IMPLOSION = B
                .comment("A heavy WMD blast close to a SUBMERGED Deep Seas submarine can IMPLODE it (Deep Seas' own crush sequence). The deeper the sub, the smaller the blast that does it. false = no implosions from WMD hits (holes only).")
                .define("navalSubImplosion", true);
        public static final ModConfigSpec.BooleanValue NAVAL_RADAR_SEES_SUBMERGED = B
                .comment("Radar, recon and the command center also see SUBMERGED ships / submarines. false (default, the v1.1 rule) = only sonar finds them; a surfaced sub is a normal ship contact.")
                .define("navalRadarSeesSubmerged", false);

        public static final ModConfigSpec.BooleanValue NAVAL_DEEP_LINK_LOSS = B
                .comment("A submarine deeper than periscope depth (hull top more than 10 blocks under the surface) loses the radio link of its fire control / sonar consoles to everything outside its own hull until its communications buoy floats. false = the nets link at any depth.")
                .define("navalDeepLinkLoss", true);
        public static final ModConfigSpec.BooleanValue DEEP_HULL_RATINGS = B
                .comment("Create: Deep Seas: WMD sets the crush depth ratings of its deep-rated hull blocks (manganese steel 200, superalloy 400, deep viewport 200) and of its hull fittings (torpedo tube, launch cell, periscope, escape pod, comms buoy, towed array winch, sonar dome, hydrophone array, machine parts: 400) in Deep Seas' hull table at every server start. false = Deep Seas' own config/submarine_hull.json decides for these blocks.")
                .define("deepHullRatings", true);
        public static final ModConfigSpec.BooleanValue NODULE_RETROGEN = B
                .comment("Worlds generated before 1.1 have no manganese nodule fields (they are placed when a chunk is first generated). true = every older deep-ocean chunk gets the same nodule roll the first time it loads after the update (once per chunk, never forcing chunks to load). false = only new chunks get nodules.")
                .define("noduleRetrogen", true);

        public static final ModConfigSpec.BooleanValue ENABLE_BLUEMAP = B
                .comment("BlueMap web map (only when the BlueMap mod is installed; without it WMD does nothing here). Master switch for the three WMD layers below. NOTE: a BlueMap marker is visible to EVERYONE who can open the map page; layers B and C are off by default for that reason.")
                .define("enableBlueMap", true);
        public static final ModConfigSpec.BooleanValue BLUEMAP_HISTORY = B
                .comment("BlueMap layer A: a marker for every blast that left a crater (warhead, size, time).")
                .define("blueMapHistory", true);
        public static final ModConfigSpec.BooleanValue BLUEMAP_ZONES = B
                .comment("BlueMap layer A: rings for active zones while they last (EMP / HEMP field, seismic zone, fire, fuel cloud, chaff, cluster carpet, a falling rod).")
                .define("blueMapZones", true);
        public static final ModConfigSpec.BooleanValue BLUEMAP_BORDER = B
                .comment("BlueMap layer A: outline of the world border.")
                .define("blueMapBorder", true);
        public static final ModConfigSpec.BooleanValue BLUEMAP_SHOW_OWNERS = B
                .comment("Name the player who fired in the impact history (layer A). false = the popup says nothing about who.")
                .define("blueMapShowOwners", false);
        public static final ModConfigSpec.BooleanValue BLUEMAP_ADMIN_LAYER = B
                .comment("BlueMap layer B (admin): EVERY owned machine with owner and real type (decoys and camouflaged ones too), every missile in flight with its path. Starts hidden in the map menu but is NOT secret: anyone who can open the map page can switch it on. Only turn it on when the web page is behind a login / only on your own network.")
                .define("blueMapAdminLayer", false);
        public static final ModConfigSpec.BooleanValue BLUEMAP_WAR_LAYER = B
                .comment("BlueMap layer C (war map): what a recon satellite would show EVERYONE: owned machines (decoys look real, camouflaged ones are hidden), missiles in flight, large airships. Breaks the hidden-information design of WMD on purpose: only for servers that want an open war map.")
                .define("blueMapWarLayer", false);
        public static final ModConfigSpec.IntValue BLUEMAP_HISTORY_SIZE = B
                .comment("How many craters the impact history keeps (oldest drop off).")
                .defineInRange("blueMapHistorySize", 300, 10, 2000);
        public static final ModConfigSpec.IntValue BLUEMAP_UPDATE_SECONDS = B
                .comment("How often the BlueMap markers are refreshed (seconds). Machines are re-scanned over several ticks, never in one go.")
                .defineInRange("blueMapUpdateSeconds", 3, 1, 60);
        static { B.pop(); B.comment("Commands.").push("commands"); }

        public static final ModConfigSpec.BooleanValue TEST_COMMANDS = B
                .comment("Test commands for operators (/wmd art, cc, cruise, mlrs, sat add, station, orbit, reactor, salvage, missile, detonate, pad, enemy, dev probes ...): "
                        + "they spawn machines, missiles, satellites and stations, fake enemy launches and skip timers. false = off (a normal server); true = a test server. "
                        + "The server admin tools (/wmd admin ...) are always there. Players see a change after they log in again.")
                .define("testCommands", false);
        static { B.pop(); }

        public static final ModConfigSpec SPEC = B.build();

        static boolean validWarhead(String id) {
            for (WarheadType t : WarheadType.values()) if (t.id.equals(id)) return true;
            return false;
        }

        private static boolean on() { return SPEC.isLoaded(); }

        public static boolean blockDamage() { return !on() || EXPLOSION_BLOCK_DAMAGE.get(); }
        public static boolean damageRings() { return on() && DAMAGE_RINGS.get(); }

        public static boolean deepSeasBridge() { return !on() || ENABLE_DEEP_SEAS_BRIDGE.get(); }
        public static boolean navalSubHoles() { return !on() || NAVAL_SUB_HOLES.get(); }
        public static boolean navalSubImplosion() { return !on() || NAVAL_SUB_IMPLOSION.get(); }
        public static boolean navalRadarSeesSubmerged() { return on() && NAVAL_RADAR_SEES_SUBMERGED.get(); }
        public static boolean navalDeepLinkLoss() { return !on() || NAVAL_DEEP_LINK_LOSS.get(); }
        public static boolean deepHullRatings() { return !on() || DEEP_HULL_RATINGS.get(); }
        public static boolean noduleRetrogen() { return !on() || NODULE_RETROGEN.get(); }
        public static boolean shipBreakup() { return on() && SHIP_BREAKUP.get(); }
        public static boolean collapse() { return on() && COLLAPSE.get(); }
        public static boolean shockwave() { return on() && SHOCKWAVE.get(); }
        public static boolean aftermath() { return on() && AFTERMATH.get(); }
        public static double maxCraterRadius() { return on() ? MAX_CRATER_RADIUS.get() : 256.0; }
        public static int protectSpawnRadius() { return on() ? PROTECT_SPAWN_RADIUS.get() : 0; }

        public static boolean respectClaims() { return !on() || RESPECT_CLAIMS.get(); }

        public static boolean respectClaimsPlacing() { return !on() || RESPECT_CLAIMS_PLACING.get(); }

        public static boolean openItemPorts() { return on() && OPEN_ITEM_PORTS.get(); }
        public static boolean debris() { return !on() || DEBRIS.get(); }
        public static int debrisLifetimeSeconds() { return on() ? DEBRIS_LIFETIME.get() : 40; }
        public static int debrisMaxLive() { return on() ? DEBRIS_MAX_LIVE.get() : 1500; }
        public static int debrisPerBlast() { return on() ? DEBRIS_PER_BLAST.get() : 500; }
        public static int debrisPerTick() { return on() ? DEBRIS_PER_TICK.get() : 25; }
        public static boolean debrisHurts() { return !on() || DEBRIS_HURTS.get(); }
        public static double debrisSpeed() { return on() ? DEBRIS_SPEED.get() : 1.0; }
        public static boolean allowSuperGun() { return !on() || ALLOW_SUPER_GUN.get(); }
        public static int artMaxShells() { return on() ? ART_MAX_SHELLS.get() : Balance.ART_MAX_SHELLS; }
        public static boolean chunkLoading() { return !on() || CHUNK_LOADING.get(); }
        public static boolean loadGuard() { return !on() || LOAD_GUARD.get(); }
        public static boolean offlineProtection() { return on() && OFFLINE_PROTECTION.get(); }
        public static int offlineRadius() { return on() ? OFFLINE_RADIUS.get() : 128; }
        public static int offlineGraceMinutes() { return on() ? OFFLINE_GRACE.get() : 15; }
        public static int loadGuardBusyMs() { return on() ? LOAD_GUARD_BUSY_MS.get() : 40; }
        public static int loadGuardOverloadMs() { return on() ? Math.max(LOAD_GUARD_OVERLOAD_MS.get(), LOAD_GUARD_BUSY_MS.get() + 5) : 70; }
        public static int reconFillInFlight() { return on() ? RECON_FILL_IN_FLIGHT.get() : Balance.RECON_FILL_IN_FLIGHT; }
        public static int reconFillPerSecond() { return on() ? RECON_FILL_PER_SECOND.get() : 0; }
        public static int reconFillMaxMspt() { return on() ? RECON_FILL_MAX_MSPT.get() : Balance.RECON_FILL_MAX_MSPT; }
        public static boolean sableBridge() { return !on() || ENABLE_SABLE_BRIDGE.get(); }

        public static int largeShipMinBlocks() { return on() ? LARGE_SHIP_MIN_BLOCKS.get() : 350; }
        public static boolean relinkOnAssembly() { return !on() || RELINK_ON_ASSEMBLY.get(); }

        public static boolean crashDamage() { return !on() || CRASH_DAMAGE.get(); }

        public static boolean tankDamage() { return !on() || TANK_DAMAGE.get(); }
        public static boolean crashDamagesWorld() { return !on() || CRASH_DAMAGES_WORLD.get(); }
        public static boolean crashHurtsCrew() { return !on() || CRASH_HURTS_CREW.get(); }
        public static boolean hardLandingBreaksGear() { return !on() || HARD_LANDING_BREAKS_GEAR.get(); }
        public static int airCeiling() { return on() ? AIR_CEILING.get() : Balance.AIR_CEILING_DEFAULT; }

        public static boolean shipFriendlyFire() { return !on() || SHIP_FRIENDLY_FIRE.get(); }

        public static boolean blueMap() { return !on() || ENABLE_BLUEMAP.get(); }
        public static boolean blueMapHistory() { return !on() || BLUEMAP_HISTORY.get(); }
        public static boolean blueMapZones() { return !on() || BLUEMAP_ZONES.get(); }
        public static boolean blueMapBorder() { return !on() || BLUEMAP_BORDER.get(); }
        public static boolean blueMapShowOwners() { return on() && BLUEMAP_SHOW_OWNERS.get(); }
        public static boolean blueMapAdminLayer() { return on() && BLUEMAP_ADMIN_LAYER.get(); }
        public static boolean blueMapWarLayer() { return on() && BLUEMAP_WAR_LAYER.get(); }
        public static int blueMapHistorySize() { return on() ? BLUEMAP_HISTORY_SIZE.get() : 300; }
        public static int blueMapUpdateSeconds() { return on() ? BLUEMAP_UPDATE_SECONDS.get() : 3; }

        public static boolean nanites() { return !on() || NANITES.get(); }

        public static boolean naniteMayEat(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos p) {
            if (!canBreak(level, p)) return false;
            return !(on() && NANITES_CLAIMS.get() && level instanceof net.minecraft.server.level.ServerLevel sl && ClaimGuard.protectedAt(sl, p));
        }

        public static boolean warheadAllowed(String id) {
            if (!on()) return true;
            WarheadType t = WarheadType.of(id);
            if ((t == WarheadType.NUKE || t == WarheadType.THERMO || t == WarheadType.ANTIMATTER || t == WarheadType.TSAR) && !ALLOW_NUKES.get()) return false;
            return !DISABLED_WARHEADS.get().contains(t.id);
        }

        public static net.minecraft.network.chat.Component refuse(MissileBuild b) {
            if (b == null || b.isInterceptor() || b.warheadType() == null || b.warheadType().isEmpty()) return null;
            if (warheadAllowed(b.warheadType())) return null;
            return net.minecraft.network.chat.Component.translatable("message.wmd.server.warhead_disabled",
                    net.minecraft.network.chat.Component.translatable("warhead.wmd." + WarheadType.of(b.warheadType()).id));
        }

        public static boolean bombAllowed(BombType t) { return t == null || warheadAllowed(t.blast.id); }

        public static net.minecraft.network.chat.Component refuseBomb(BombType t) {
            if (bombAllowed(t)) return null;
            return net.minecraft.network.chat.Component.translatable("message.wmd.server.warhead_disabled", net.minecraft.network.chat.Component.translatable("item.wmd." + t.id));
        }

        public static boolean canBreak(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos p) {
            if (!blockDamage()) return false;
            int r = protectSpawnRadius();
            if (r > 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                net.minecraft.core.BlockPos s = level.getSharedSpawnPos();
                double dx = p.getX() - s.getX(), dz = p.getZ() - s.getZ();
                if (dx * dx + dz * dz <= (double) r * r) return false;
            }
            if (OfflineGuard.protects(level, p)) return false;

            return !(respectClaims() && level instanceof net.minecraft.server.level.ServerLevel sl && ClaimGuard.protectedAt(sl, p));
        }

        public static boolean canBreakShip(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos p) {
            if (OfflineGuard.protects(level, p)) return false;
            if (!blockDamage()) return false;
            int r = protectSpawnRadius();
            if (r > 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                net.minecraft.core.BlockPos s = level.getSharedSpawnPos();
                double dx = p.getX() - s.getX(), dz = p.getZ() - s.getZ();
                if (dx * dx + dz * dz <= (double) r * r) return false;
            }
            return true;
        }
    }
}
