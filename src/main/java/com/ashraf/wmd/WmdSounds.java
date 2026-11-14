package com.ashraf.wmd;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WmdSounds {

    private WmdSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Wmd.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CIWS_START = SOUNDS.register("ciws_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ciws_start")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CIWS_LOOP = SOUNDS.register("ciws_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ciws_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CIWS_END = SOUNDS.register("ciws_end",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ciws_end")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_FILL = SOUNDS.register("tank_fill",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tank_fill")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_RUN = SOUNDS.register("pump_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pump_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DECOY_POP = SOUNDS.register("decoy_pop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "decoy_pop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DECOY_CRACKLE = SOUNDS.register("decoy_crackle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "decoy_crackle")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DECOY_FIZZ = SOUNDS.register("decoy_fizz",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "decoy_fizz")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HE_NEAR = SOUNDS.register("he_near",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "he_near")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HE_FAR = SOUNDS.register("he_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "he_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HE_DEBRIS = SOUNDS.register("he_debris",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "he_debris")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FRAG_CRACK = SOUNDS.register("frag_crack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "frag_crack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FRAG_WHIZZ = SOUNDS.register("frag_whizz",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "frag_whizz")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INCENDIARY_WHOOSH = SOUNDS.register("incendiary_whoosh",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "incendiary_whoosh")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INCENDIARY_ROAR = SOUNDS.register("incendiary_roar",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "incendiary_roar")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TBX_WHOOMP = SOUNDS.register("tbx_whoomp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tbx_whoomp")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BOMBLET_BANG = SOUNDS.register("bomblet_bang",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bomblet_bang")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BREACHER_PUNCH = SOUNDS.register("breacher_punch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "breacher_punch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAFF_HISS = SOUNDS.register("chaff_hiss",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chaff_hiss")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BUNKER_PUNCH = SOUNDS.register("bunker_punch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bunker_punch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BUNKER_DEEP = SOUNDS.register("bunker_deep",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bunker_deep")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_ZAP = SOUNDS.register("emp_zap",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "emp_zap")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NUKE_BOOM = SOUNDS.register("nuke_boom",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nuke_boom")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ROD_IMPACT = SOUNDS.register("rod_impact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rod_impact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_RUMBLE = SOUNDS.register("seismic_rumble",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seismic_rumble")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_WHUMP = SOUNDS.register("blast_whump",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "blast_whump")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TBX_DISPERSE = SOUNDS.register("tbx_disperse",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tbx_disperse")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FRAG_PATTER = SOUNDS.register("frag_patter",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "frag_patter")));

    public static final DeferredHolder<SoundEvent, SoundEvent> INC_POP = SOUNDS.register("inc_pop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "inc_pop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INC_STREAMERS = SOUNDS.register("inc_streamers",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "inc_streamers")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BREACHER_RATTLE = SOUNDS.register("breacher_rattle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "breacher_rattle")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_HUM = SOUNDS.register("emp_hum",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "emp_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CHAFF_CANISTERS = SOUNDS.register("chaff_canisters",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chaff_canisters")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BUNKER_HEAVE = SOUNDS.register("bunker_heave",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bunker_heave")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ROD_SPLASH = SOUNDS.register("rod_splash",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rod_splash")));

    public static final DeferredHolder<SoundEvent, SoundEvent> NUKE_ROLL = SOUNDS.register("nuke_roll",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nuke_roll")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SEISMIC_CRACK = SOUNDS.register("seismic_crack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seismic_crack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DESALTER_RUN = SOUNDS.register("desalter_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "desalter_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FURNACE_RUN = SOUNDS.register("furnace_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "furnace_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ADU_RUN = SOUNDS.register("adu_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "adu_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HDT_RUN = SOUNDS.register("hdt_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hdt_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FCC_RUN = SOUNDS.register("fcc_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fcc_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> REFORMER_RUN = SOUNDS.register("reformer_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "reformer_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VDU_RUN = SOUNDS.register("vdu_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "vdu_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SIREN = SOUNDS.register("siren",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "siren")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T1_IGNITE = SOUNDS.register("launch_t1_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t1_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T1_ASCENT = SOUNDS.register("launch_t1_ascent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t1_ascent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T2_IGNITE = SOUNDS.register("launch_t2_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t2_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T2_IGNITE_FAR = SOUNDS.register("launch_t2_ignite_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t2_ignite_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T2_ASCENT = SOUNDS.register("launch_t2_ascent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t2_ascent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T2_ASCENT_FAR = SOUNDS.register("launch_t2_ascent_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t2_ascent_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T3_IGNITE = SOUNDS.register("launch_t3_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t3_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T3_IGNITE_FAR = SOUNDS.register("launch_t3_ignite_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t3_ignite_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T3_ASCENT = SOUNDS.register("launch_t3_ascent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t3_ascent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T3_ASCENT_FAR = SOUNDS.register("launch_t3_ascent_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t3_ascent_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T4_IGNITE = SOUNDS.register("launch_t4_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t4_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T4_IGNITE_FAR = SOUNDS.register("launch_t4_ignite_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t4_ignite_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T4_ASCENT = SOUNDS.register("launch_t4_ascent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t4_ascent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_T4_ASCENT_FAR = SOUNDS.register("launch_t4_ascent_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "launch_t4_ascent_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_LAUNCH = SOUNDS.register("cruise_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cruise_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CRUISE_APPROACH = SOUNDS.register("cruise_approach",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cruise_approach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_T1_EJECT = SOUNDS.register("int_t1_eject",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_t1_eject")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_T1_BOOST = SOUNDS.register("int_t1_boost",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_t1_boost")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_T2_LAUNCH = SOUNDS.register("int_t2_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_t2_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_T34_LAUNCH = SOUNDS.register("int_t34_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_t34_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_T34_LAUNCH_FAR = SOUNDS.register("int_t34_launch_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_t34_launch_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> M270_SALVO = SOUNDS.register("m270_salvo",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "m270_salvo")));
    public static final DeferredHolder<SoundEvent, SoundEvent> M270_SALVO_FAR = SOUNDS.register("m270_salvo_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "m270_salvo_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERGUN_FIRE = SOUNDS.register("supergun_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "supergun_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SUPERGUN_FIRE_FAR = SOUNDS.register("supergun_fire_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "supergun_fire_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MORTAR_FIRE = SOUNDS.register("mortar_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mortar_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HOWITZER_FIRE = SOUNDS.register("howitzer_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "howitzer_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HOWITZER_FIRE_FAR = SOUNDS.register("howitzer_fire_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "howitzer_fire_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELL_WHISTLE = SOUNDS.register("shell_whistle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "shell_whistle")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REENTRY = SOUNDS.register("reentry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "reentry")));
    public static final DeferredHolder<SoundEvent, SoundEvent> REENTRY_FAR = SOUNDS.register("reentry_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "reentry_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TERMINAL_DIVE = SOUNDS.register("terminal_dive",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "terminal_dive")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM = SOUNDS.register("sonic_boom",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sonic_boom")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SONIC_BOOM_FAR = SOUNDS.register("sonic_boom_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sonic_boom_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLYBY_CRACK = SOUNDS.register("flyby_crack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flyby_crack")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CRUSHER_RUN = SOUNDS.register("crusher_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "crusher_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_FURNACE_RUN = SOUNDS.register("arc_furnace_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "arc_furnace_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_RUN = SOUNDS.register("centrifuge_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "centrifuge_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMPJACK_RUN = SOUNDS.register("pumpjack_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pumpjack_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_RUN = SOUNDS.register("diesel_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "diesel_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RADAR_RUN = SOUNDS.register("radar_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "radar_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RESEARCH_RUN = SOUNDS.register("research_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "research_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ROLLING_MILL_RUN = SOUNDS.register("rolling_mill_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rolling_mill_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SINTER_PRESS_RUN = SOUNDS.register("sinter_press_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sinter_press_run")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_START = SOUNDS.register("centrifuge_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "centrifuge_start")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_STOP = SOUNDS.register("centrifuge_stop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "centrifuge_stop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ARC_STRIKE = SOUNDS.register("arc_strike",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "arc_strike")));

    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CLICK = SOUNDS.register("ui_click",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ui_click")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_TAB = SOUNDS.register("ui_tab",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ui_tab")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_CONFIRM = SOUNDS.register("ui_confirm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ui_confirm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UI_DENY = SOUNDS.register("ui_deny",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ui_deny")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_MISS = SOUNDS.register("int_miss",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_miss")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HEMP_PULSE = SOUNDS.register("hemp_pulse",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hemp_pulse")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HEMP_PULSE_FAR = SOUNDS.register("hemp_pulse_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hemp_pulse_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ASAT_HIT = SOUNDS.register("asat_hit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asat_hit")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DOWN = SOUNDS.register("drone_down",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_down")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FAIRING_POP = SOUNDS.register("fairing_pop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fairing_pop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> INT_KILL = SOUNDS.register("int_kill",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_kill")));
    public static final DeferredHolder<SoundEvent, SoundEvent> INT_KILL_FAR = SOUNDS.register("int_kill_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "int_kill_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_WELD = SOUNDS.register("bay_weld",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_weld")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_PAINT = SOUNDS.register("bay_paint",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_paint")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_PRESS = SOUNDS.register("bay_press",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_press")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_RAIL = SOUNDS.register("bay_rail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_rail")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CURING_OVEN_RUN = SOUNDS.register("curing_oven_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "curing_oven_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DEFCON_3 = SOUNDS.register("defcon_3",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "defcon_3")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFCON_2 = SOUNDS.register("defcon_2",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "defcon_2")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEFCON_1 = SOUNDS.register("defcon_1",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "defcon_1")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CC_PING = SOUNDS.register("cc_ping",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_ping")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CC_ALERT = SOUNDS.register("cc_alert",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_alert")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CC_DOOR_OPEN = SOUNDS.register("cc_door_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_door_open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CC_DOOR_CLOSE = SOUNDS.register("cc_door_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_door_close")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAB_REACTOR_DONE = SOUNDS.register("lab_reactor_done",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lab_reactor_done")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_SCAN = SOUNDS.register("bay_scan",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_scan")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_CRYO = SOUNDS.register("bay_cryo",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_cryo")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_BOLT = SOUNDS.register("bay_bolt",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_bolt")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_SCANNER = SOUNDS.register("tool_scanner",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tool_scanner")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_DESIGNATOR = SOUNDS.register("tool_designator",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tool_designator")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_CARD = SOUNDS.register("tool_card",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tool_card")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BOMB_RELEASE = SOUNDS.register("bomb_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bomb_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CATAPULT_LATCH = SOUNDS.register("catapult_latch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "catapult_latch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CATAPULT_THROW = SOUNDS.register("catapult_throw",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "catapult_throw")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CHUTE_POP = SOUNDS.register("chute_pop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chute_pop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_GUN = SOUNDS.register("drone_gun",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_gun")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_JET_LOOP = SOUNDS.register("drone_jet_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_jet_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_JET_TAKEOFF = SOUNDS.register("drone_jet_takeoff",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_jet_takeoff")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_PROP_LOOP = SOUNDS.register("drone_prop_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_prop_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_QUAD_LOOP = SOUNDS.register("drone_quad_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_quad_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_TEST_SPINUP = SOUNDS.register("drone_test_spinup",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "drone_test_spinup")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASH_BANG = SOUNDS.register("flash_bang",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flash_bang")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASH_BANG_FAR = SOUNDS.register("flash_bang_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flash_bang_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSE_BEEP = SOUNDS.register("fuse_beep",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fuse_beep")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSE_DEFUSE = SOUNDS.register("fuse_defuse",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fuse_defuse")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_CLOUD_LOOP = SOUNDS.register("gas_cloud_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gas_cloud_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_RELEASE = SOUNDS.register("gas_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gas_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HANGAR_DOOR = SOUNDS.register("hangar_door",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hangar_door")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HIVE_LID = SOUNDS.register("hive_lid",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hive_lid")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_OFF = SOUNDS.register("laser_off",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "laser_off")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_ON = SOUNDS.register("laser_on",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "laser_on")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NEUTRON_BLAST = SOUNDS.register("neutron_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "neutron_blast")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NEUTRON_BLAST_FAR = SOUNDS.register("neutron_blast_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "neutron_blast_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGHT_LOCK = SOUNDS.register("sight_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sight_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGHT_RELEASE = SOUNDS.register("sight_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sight_release")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_RUN_LOOP = SOUNDS.register("fusion_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_COOLDOWN = SOUNDS.register("fusion_cooldown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_cooldown")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_COIL = SOUNDS.register("fusion_coil",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_coil")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_CHARGE = SOUNDS.register("fusion_charge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_charge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_IGNITE = SOUNDS.register("fusion_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_ARC = SOUNDS.register("fusion_arc",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_arc")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_DISCHARGE = SOUNDS.register("fusion_discharge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_discharge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_QUENCH = SOUNDS.register("fusion_quench",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_quench")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FUSION_SHUTDOWN = SOUNDS.register("fusion_shutdown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fusion_shutdown")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLASMA_BLAST = SOUNDS.register("plasma_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "plasma_blast")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PLASMA_BLAST_FAR = SOUNDS.register("plasma_blast_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "plasma_blast_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AM_COLLIDER_LOOP = SOUNDS.register("am_collider_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "am_collider_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AM_STORE_LOOP = SOUNDS.register("am_store_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "am_store_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AM_ALARM = SOUNDS.register("am_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "am_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNIHILATION = SOUNDS.register("annihilation",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "annihilation")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNIHILATION_FAR = SOUNDS.register("annihilation_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "annihilation_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RG_SHOT = SOUNDS.register("rg_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_shot")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_SHOT_FAR = SOUNDS.register("rg_shot_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_shot_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_ABORT = SOUNDS.register("rg_abort",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_abort")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_LOAD = SOUNDS.register("rg_load",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_load")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_COUNTDOWN = SOUNDS.register("rg_countdown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_countdown")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_CLAWS = SOUNDS.register("rg_claws",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_claws")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_VENT = SOUNDS.register("rg_vent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_vent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_SPARK = SOUNDS.register("rg_spark",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_spark")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_STRIKE = SOUNDS.register("rg_strike",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_strike")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_FLIGHT = SOUNDS.register("rg_flight",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_flight")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_SLEW_LOOP = SOUNDS.register("rg_slew_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_slew_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_IMPACT = SOUNDS.register("rg_impact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_impact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_IMPACT_FAR = SOUNDS.register("rg_impact_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_impact_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_CHARGE_LOOP = SOUNDS.register("rg_charge_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_charge_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RG_HUM_LOOP = SOUNDS.register("rg_hum_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_hum_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_TICK = SOUNDS.register("rg_tick",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_tick")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CR_BURST = SOUNDS.register("cr_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cr_burst")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CR_FROST_LOOP = SOUNDS.register("cr_frost_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cr_frost_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CR_THAW = SOUNDS.register("cr_thaw",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cr_thaw")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_ALARM = SOUNDS.register("dh_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_ARMED = SOUNDS.register("dh_armed",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_armed")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_CANCEL = SOUNDS.register("dh_cancel",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_cancel")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_DOOR = SOUNDS.register("dh_door",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_door")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_KEY = SOUNDS.register("dh_key",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_key")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_LAUNCH = SOUNDS.register("dh_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DH_TICK = SOUNDS.register("dh_tick",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dh_tick")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GR_OPEN = SOUNDS.register("gr_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gr_open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GR_PULL_LOOP = SOUNDS.register("gr_pull_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gr_pull_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GR_THROW = SOUNDS.register("gr_throw",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gr_throw")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GR_THROW_FAR = SOUNDS.register("gr_throw_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gr_throw_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LD_BEAM_LOOP = SOUNDS.register("ld_beam_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ld_beam_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LD_BURN = SOUNDS.register("ld_burn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ld_burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LD_CHARGE = SOUNDS.register("ld_charge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ld_charge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LD_COOL = SOUNDS.register("ld_cool",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ld_cool")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LD_SLEW_LOOP = SOUNDS.register("ld_slew_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ld_slew_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NA_DIE = SOUNDS.register("na_die",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "na_die")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NA_RELEASE = SOUNDS.register("na_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "na_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NA_SWARM_LOOP = SOUNDS.register("na_swarm_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "na_swarm_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SW_BLAST = SOUNDS.register("sw_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sw_blast")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SW_DIVE = SOUNDS.register("sw_dive",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sw_dive")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SW_DRONE_LOOP = SOUNDS.register("sw_drone_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sw_drone_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SW_HATCH = SOUNDS.register("sw_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sw_hatch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SW_LAUNCH = SOUNDS.register("sw_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sw_launch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_HIT = SOUNDS.register("lz_hit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_hit")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_HIT_FAR = SOUNDS.register("lz_hit_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_hit_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RB_BURN_START = SOUNDS.register("rb_burn_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rb_burn_start")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RB_BURN_STOP = SOUNDS.register("rb_burn_stop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rb_burn_stop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CG_RELEASE = SOUNDS.register("cg_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cg_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_ABORT = SOUNDS.register("rd_abort",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_abort")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ASAT_ALERT = SOUNDS.register("asat_alert",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asat_alert")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LP_ERECT = SOUNDS.register("lp_erect",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lp_erect")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LP_FUEL = SOUNDS.register("lp_fuel",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lp_fuel")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LP_LOCK = SOUNDS.register("lp_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lp_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LP_VENT = SOUNDS.register("lp_vent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lp_vent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LP_HOLD = SOUNDS.register("lp_hold",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lp_hold")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LP_COUNT = SOUNDS.register("lp_count",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lp_count")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MB_FORMED = SOUNDS.register("mb_formed",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mb_formed")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MB_FAIL = SOUNDS.register("mb_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mb_fail")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_START = SOUNDS.register("bay_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_start")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_GRAB = SOUNDS.register("bay_grab",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_grab")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_CLAMP = SOUNDS.register("bay_clamp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_clamp")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_ARCH = SOUNDS.register("bay_arch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_arch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_FROST = SOUNDS.register("bay_frost",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_frost")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WR_INSERT = SOUNDS.register("wr_insert",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wr_insert")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WR_STAGE = SOUNDS.register("wr_stage",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wr_stage")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WR_DOOR = SOUNDS.register("wr_door",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wr_door")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WR_ALARM = SOUNDS.register("wr_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wr_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WR_FAIL = SOUNDS.register("wr_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wr_fail")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LAB_DONE = SOUNDS.register("lab_done",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lab_done")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TU_SHOT = SOUNDS.register("tu_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tu_shot")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TU_HIT = SOUNDS.register("tu_hit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tu_hit")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TU_HATCH = SOUNDS.register("tu_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tu_hatch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RZ_CONTACT = SOUNDS.register("rz_contact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rz_contact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RZ_CLOSE = SOUNDS.register("rz_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rz_close")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RZ_COLLISION = SOUNDS.register("rz_collision",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rz_collision")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TS_CHUTES = SOUNDS.register("ts_chutes",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ts_chutes")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TS_BOOM = SOUNDS.register("ts_boom",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ts_boom")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TS_BOOM_FAR = SOUNDS.register("ts_boom_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ts_boom_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TS_ROLL = SOUNDS.register("ts_roll",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ts_roll")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WI_START = SOUNDS.register("wi_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wi_start")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WI_WIND_LOOP = SOUNDS.register("wi_wind_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wi_wind_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MV_SPLIT = SOUNDS.register("mv_split",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mv_split")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MV_LOAD = SOUNDS.register("mv_load",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mv_load")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_HUM_LOOP = SOUNDS.register("ff_hum_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_hum_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_BOOT = SOUNDS.register("ff_boot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_boot")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_PYLON = SOUNDS.register("ff_pylon",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_pylon")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_DOME_UP = SOUNDS.register("ff_dome_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_dome_up")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_OFF = SOUNDS.register("ff_off",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_off")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_HIT = SOUNDS.register("ff_hit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_hit")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_TOUCH = SOUNDS.register("ff_touch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_touch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_CRACK = SOUNDS.register("ff_crack",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_crack")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_CRACKLE = SOUNDS.register("ff_crackle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_crackle")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_SHATTER = SOUNDS.register("ff_shatter",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_shatter")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_SHATTER_FAR = SOUNDS.register("ff_shatter_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_shatter_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_GLASS_RAIN = SOUNDS.register("ff_glass_rain",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_glass_rain")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FF_DOWN = SOUNDS.register("ff_down",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ff_down")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_WHINE_DOWN = SOUNDS.register("rg_whine_down",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_whine_down")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_WHINE = SOUNDS.register("rd_whine",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_whine")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_HEARTBEAT = SOUNDS.register("rd_heartbeat_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_heartbeat_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RG_FINAL_LOOP = SOUNDS.register("rg_final_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rg_final_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OR_EVA_THRUST = SOUNDS.register("or_eva_thrust",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_eva_thrust")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_O2_POUR = SOUNDS.register("or_o2_pour",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_o2_pour")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_CAPSULE_REENTRY = SOUNDS.register("or_capsule_reentry",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_capsule_reentry")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_CAPSULE_REENTRY_FAR = SOUNDS.register("or_capsule_reentry_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_capsule_reentry_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_CAPSULE_TOUCHDOWN = SOUNDS.register("or_capsule_touchdown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_capsule_touchdown")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_CAPSULE_HATCH = SOUNDS.register("or_capsule_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_capsule_hatch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_SUIT_ALARM = SOUNDS.register("or_suit_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_suit_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_LOST_ALARM = SOUNDS.register("or_lost_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_lost_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_EVA_BREATH_LOOP = SOUNDS.register("or_eva_breath_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_eva_breath_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> OR_O2_STATION_LOOP = SOUNDS.register("or_o2_station_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "or_o2_station_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ST_DOCK_CLAMP = SOUNDS.register("st_dock_clamp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_dock_clamp")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PA_ROD_ARMED = SOUNDS.register("pa_rod_armed",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pa_rod_armed")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_ROD_AWAY = SOUNDS.register("pa_rod_away",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pa_rod_away")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_MIRROR = SOUNDS.register("pa_mirror",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pa_mirror")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_TUG = SOUNDS.register("pa_tug",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pa_tug")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_BREACH = SOUNDS.register("pa_breach",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pa_breach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_AIRLOCK = SOUNDS.register("st_airlock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_airlock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_AIR_RUSH = SOUNDS.register("st_air_rush",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_air_rush")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_BREACH_KLAXON = SOUNDS.register("st_breach_klaxon",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_breach_klaxon")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_WING_DEPLOY = SOUNDS.register("st_wing_deploy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_wing_deploy")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_HAB_HUM_LOOP = SOUNDS.register("st_hab_hum_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_hab_hum_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CR_BURN = SOUNDS.register("cr_burn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cr_burn")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SA_CONTACT = SOUNDS.register("sa_contact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_contact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_CHARGE = SOUNDS.register("lz_charge",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_charge")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_DRUM = SOUNDS.register("lz_drum",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_drum")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_SLEW = SOUNDS.register("lz_slew",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_slew")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_FIRE = SOUNDS.register("lz_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_FIRE_FAR = SOUNDS.register("lz_fire_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_fire_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_COOL = SOUNDS.register("lz_cool",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_cool")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_ABORT = SOUNDS.register("lz_abort",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_abort")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BD_ALARM = SOUNDS.register("bd_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bd_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_REBOOST_LOOP = SOUNDS.register("st_reboost_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_reboost_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LZ_ARMED_LOOP = SOUNDS.register("lz_armed_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lz_armed_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BD_CUTTER_LOOP = SOUNDS.register("bd_cutter_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bd_cutter_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AB_DOORS = SOUNDS.register("ab_doors",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ab_doors")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AB_VENT = SOUNDS.register("ab_vent",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ab_vent")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_ALARM = SOUNDS.register("ast_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_FALL = SOUNDS.register("ast_fall",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_fall")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_FALL_FAR = SOUNDS.register("ast_fall_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_fall_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_IMPACT = SOUNDS.register("ast_impact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_impact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_IMPACT_FAR = SOUNDS.register("ast_impact_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_impact_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_SPLIT = SOUNDS.register("ast_split",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_split")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AST_SPLIT_FAR = SOUNDS.register("ast_split_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ast_split_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CREW_COUNT = SOUNDS.register("crew_count",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "crew_count")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HATCH_OPEN = SOUNDS.register("hatch_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hatch_open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_CHIME = SOUNDS.register("lift_chime",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_chime")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_DOORS = SOUNDS.register("lift_doors",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_doors")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_MOTOR_LOOP = SOUNDS.register("lift_motor_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_motor_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_BEAM_LOOP = SOUNDS.register("mr_beam_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_beam_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_BURN_LOOP = SOUNDS.register("mr_burn_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_burn_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_DEPLOY = SOUNDS.register("mr_deploy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_deploy")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_FOCUS_OFF = SOUNDS.register("mr_focus_off",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_focus_off")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_FOCUS_ON = SOUNDS.register("mr_focus_on",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_focus_on")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_STEER_LOOP = SOUNDS.register("mr_steer_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_steer_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_ARM = SOUNDS.register("rd_arm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_arm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_CLAMP = SOUNDS.register("rd_clamp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_clamp")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_COOL = SOUNDS.register("rd_cool",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_cool")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_DOOR = SOUNDS.register("rd_door",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_door")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_DRUM = SOUNDS.register("rd_drum",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_drum")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_FALL = SOUNDS.register("rd_fall",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_fall")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_FALL_FAR = SOUNDS.register("rd_fall_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_fall_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_FINAL = SOUNDS.register("rd_final",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_final")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_HOIST_LOOP = SOUNDS.register("rd_hoist_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_hoist_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_IMPACT = SOUNDS.register("rd_impact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_impact")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_IMPACT_FAR = SOUNDS.register("rd_impact_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_impact_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_LOCK = SOUNDS.register("rd_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_RELEASE = SOUNDS.register("rd_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_RELEASE_FAR = SOUNDS.register("rd_release_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_release_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_SPIN_LOOP = SOUNDS.register("rd_spin_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_spin_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RD_SWING = SOUNDS.register("rd_swing",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rd_swing")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TUG_CLAWS = SOUNDS.register("tug_claws",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tug_claws")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TUG_ENGINE_LOOP = SOUNDS.register("tug_engine_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tug_engine_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TUG_LAUNCH = SOUNDS.register("tug_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tug_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TUG_NET = SOUNDS.register("tug_net",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tug_net")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WR_SEAL = SOUNDS.register("wr_seal",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wr_seal")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AM_BEAM_TICK = SOUNDS.register("am_beam_tick",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "am_beam_tick")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AM_CELL_DONE = SOUNDS.register("am_cell_done",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "am_cell_done")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AM_INJECT = SOUNDS.register("am_inject",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "am_inject")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_HATCH = SOUNDS.register("bay_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_hatch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_HOOD = SOUNDS.register("bay_hood",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_hood")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAFF_BURST = SOUNDS.register("chaff_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chaff_burst")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DESIGNATOR_LOCK = SOUNDS.register("designator_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "designator_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_FRONT = SOUNDS.register("emp_front",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "emp_front")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_WHINE = SOUNDS.register("emp_whine",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "emp_whine")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_BURST = SOUNDS.register("flare_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flare_burst")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NR_ALARM = SOUNDS.register("nr_alarm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nr_alarm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NR_DAMAGE = SOUNDS.register("nr_damage",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nr_damage")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NR_RODS = SOUNDS.register("nr_rods",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nr_rods")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NR_STEAM = SOUNDS.register("nr_steam",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nr_steam")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_ARM = SOUNDS.register("robot_arm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "robot_arm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_HIT = SOUNDS.register("robot_hit",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "robot_hit")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SCANNER_PING = SOUNDS.register("scanner_ping",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "scanner_ping")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_ARMS = SOUNDS.register("t4_arms",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_arms")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_CRANE_LOOP = SOUNDS.register("t4_crane_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_crane_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_FUEL_LOOP = SOUNDS.register("t4_fuel_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_fuel_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_HOLDDOWN = SOUNDS.register("t4_holddown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_holddown")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_STAGE_SET = SOUNDS.register("t4_stage_set",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_stage_set")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_WELD_LOOP = SOUNDS.register("t4_weld_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_weld_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_WINCH = SOUNDS.register("t4_winch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_winch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_ABORT = SOUNDS.register("tp_abort",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_abort")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_ARRIVE = SOUNDS.register("tp_arrive",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_arrive")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_CHARGE_LOOP = SOUNDS.register("tp_charge_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_charge_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_FIRE = SOUNDS.register("tp_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_TURN = SOUNDS.register("wrench_turn",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wrench_turn")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BAT_HUM_LOOP = SOUNDS.register("bat_hum_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bat_hum_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BF_BLOWER_LOOP = SOUNDS.register("bf_blower_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bf_blower_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BF_RUN_LOOP = SOUNDS.register("bf_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bf_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> C4_BEEP = SOUNDS.register("c4_beep",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "c4_beep")));
    public static final DeferredHolder<SoundEvent, SoundEvent> C4_BREACH = SOUNDS.register("c4_breach",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "c4_breach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> C4_PLACE = SOUNDS.register("c4_place",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "c4_place")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CAM_SERVO = SOUNDS.register("cam_servo",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cam_servo")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CCTV_SWITCH = SOUNDS.register("cctv_switch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cctv_switch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAY_BLAST = SOUNDS.register("clay_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "clay_blast")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAY_CLICK = SOUNDS.register("clay_click",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "clay_click")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CO_RUN_LOOP = SOUNDS.register("co_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "co_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DET_CLICK = SOUNDS.register("det_click",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "det_click")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EF_PULSE = SOUNDS.register("ef_pulse",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ef_pulse")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EF_ZAP = SOUNDS.register("ef_zap",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ef_zap")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLOOD_ON = SOUNDS.register("flood_on",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flood_on")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HACK_DONE = SOUNDS.register("hack_done",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hack_done")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HACK_LOOP = SOUNDS.register("hack_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hack_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JAM_DEPLOY = SOUNDS.register("jam_deploy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jam_deploy")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JAM_LOOP = SOUNDS.register("jam_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jam_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_ARM = SOUNDS.register("mine_arm",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mine_arm")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MINE_BLAST = SOUNDS.register("mine_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mine_blast")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PM_RUN_LOOP = SOUNDS.register("pm_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pm_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SD_ALERT = SOUNDS.register("sd_alert",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sd_alert")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SD_BURST = SOUNDS.register("sd_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sd_burst")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SD_SLEEP = SOUNDS.register("sd_sleep",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sd_sleep")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SD_WAKE = SOUNDS.register("sd_wake",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sd_wake")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SEIS_HOIST = SOUNDS.register("seis_hoist",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seis_hoist")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SEIS_RESULT = SOUNDS.register("seis_result",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seis_result")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SEIS_THUMP = SOUNDS.register("seis_thump",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seis_thump")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SENSOR_TRIP = SOUNDS.register("sensor_trip",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sensor_trip")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SG_RUN_LOOP = SOUNDS.register("sg_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sg_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SM_RUN_LOOP = SOUNDS.register("sm_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sm_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SM_STAMP = SOUNDS.register("sm_stamp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sm_stamp")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_ALERT = SOUNDS.register("st_alert",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_alert")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_BURST = SOUNDS.register("st_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_burst")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AG_RUN_LOOP = SOUNDS.register("ag_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ag_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CA_RUN_LOOP = SOUNDS.register("ca_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ca_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CBR_RUN_LOOP = SOUNDS.register("cbr_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cbr_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CC_SERVER_LOOP = SOUNDS.register("cc_server_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cc_server_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CHL_RUN_LOOP = SOUNDS.register("chl_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chl_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CK_RUN_LOOP = SOUNDS.register("ck_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ck_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CL_RUN_LOOP = SOUNDS.register("cl_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cl_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CT_RUN_LOOP = SOUNDS.register("ct_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ct_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DC_RUN_LOOP = SOUNDS.register("dc_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dc_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EL_RUN_LOOP = SOUNDS.register("el_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "el_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> EWA_RUN_LOOP = SOUNDS.register("ewa_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ewa_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FE_PULSE_LOOP = SOUNDS.register("fe_pulse_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fe_pulse_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FS_RUN_LOOP = SOUNDS.register("fs_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fs_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GC_RUN_LOOP = SOUNDS.register("gc_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gc_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GS_RUN_LOOP = SOUNDS.register("gs_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gs_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_RUN_LOOP = SOUNDS.register("gt_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_run_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_STARTER = SOUNDS.register("gt_starter",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_starter")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GT_IGNITE = SOUNDS.register("gt_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HF_RUN_LOOP = SOUNDS.register("hf_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hf_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> KR_RUN_LOOP = SOUNDS.register("kr_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "kr_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LT_RUN_LOOP = SOUNDS.register("lt_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lt_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MG_RUN_LOOP = SOUNDS.register("mg_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mg_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> MR_DRILL_LOOP = SOUNDS.register("mr_drill_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "mr_drill_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NR_RUN_LOOP = SOUNDS.register("nr_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nr_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NUC_RUN_LOOP = SOUNDS.register("nuc_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nuc_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RO_RUN_LOOP = SOUNDS.register("ro_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ro_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RP_RUN_LOOP = SOUNDS.register("rp_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SF_RUN_LOOP = SOUNDS.register("sf_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sf_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SP_RUN_LOOP = SOUNDS.register("sp_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sp_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ST_IDLE_LOOP = SOUNDS.register("st_idle_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "st_idle_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> STT_RUN_LOOP = SOUNDS.register("stt_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "stt_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WD_RUN_LOOP = SOUNDS.register("wd_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wd_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WI_RUN_LOOP = SOUNDS.register("wi_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wi_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CG_RUN_LOOP = SOUNDS.register("cg_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cg_run_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ASROC_DROP = SOUNDS.register("asroc_drop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asroc_drop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ASROC_IGNITE = SOUNDS.register("asroc_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asroc_ignite")));
    public static final DeferredHolder<SoundEvent, SoundEvent> ASROC_IGNITE_FAR = SOUNDS.register("asroc_ignite_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asroc_ignite_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BUOY_SPLASH = SOUNDS.register("buoy_splash",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "buoy_splash")));

    public static final DeferredHolder<SoundEvent, SoundEvent> NET_DEPLOY = SOUNDS.register("net_deploy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "net_deploy")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DC_RELEASE = SOUNDS.register("dc_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dc_release")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DC_THROW = SOUNDS.register("dc_throw",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dc_throw")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DECK_TRAIN_LOOP = SOUNDS.register("deck_train_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "deck_train_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FCC_FIRE = SOUNDS.register("fcc_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fcc_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FCC_ROOM_LOOP = SOUNDS.register("fcc_room_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fcc_room_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> HULL_BREACH = SOUNDS.register("hull_breach",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hull_breach")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LC_COLD_LAUNCH = SOUNDS.register("lc_cold_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lc_cold_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LC_COLD_LAUNCH_FAR = SOUNDS.register("lc_cold_launch_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lc_cold_launch_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LC_HATCH = SOUNDS.register("lc_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lc_hatch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NM_LOOP = SOUNDS.register("nm_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nm_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SON_PING = SOUNDS.register("son_ping",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "son_ping")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SON_PING_FAR = SOUNDS.register("son_ping_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "son_ping_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SON_ROOM_LOOP = SOUNDS.register("son_room_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "son_room_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SUB_IMPLODE = SOUNDS.register("sub_implode",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sub_implode")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SUB_IMPLODE_FAR = SOUNDS.register("sub_implode_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sub_implode_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_LAUNCH = SOUNDS.register("t4_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_LAUNCH_FAR = SOUNDS.register("t4_launch_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_launch_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> T4_RUN_LOOP = SOUNDS.register("t4_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "t4_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TL_FIT_LOOP = SOUNDS.register("tl_fit_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tl_fit_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TL_ROLL_LOOP = SOUNDS.register("tl_roll_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tl_roll_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TL_TANK = SOUNDS.register("tl_tank",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tl_tank")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_RACK_HOIST = SOUNDS.register("tp_rack_hoist",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_rack_hoist")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_RELOAD = SOUNDS.register("tp_reload",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_reload")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_RUN_LOOP = SOUNDS.register("tp_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_SEEKER = SOUNDS.register("tp_seeker",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_seeker")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TP_TUBE_FIRE = SOUNDS.register("tp_tube_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tp_tube_fire")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UUV_RUN_LOOP = SOUNDS.register("uuv_run_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uuv_run_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UW_BLAST_ABOVE = SOUNDS.register("uw_blast_above",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uw_blast_above")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UW_BLAST_ABOVE_FAR = SOUNDS.register("uw_blast_above_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uw_blast_above_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UW_BLAST_UNDER = SOUNDS.register("uw_blast_under",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uw_blast_under")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UW_BOIL = SOUNDS.register("uw_boil",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uw_boil")));
    public static final DeferredHolder<SoundEvent, SoundEvent> UW_COLUMN = SOUNDS.register("uw_column",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "uw_column")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREW_LOOP = SOUNDS.register("screw_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "screw_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREW_START = SOUNDS.register("screw_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "screw_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREW_STOP = SOUNDS.register("screw_stop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "screw_stop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREW_CAVITATE = SOUNDS.register("screw_cavitate",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "screw_cavitate")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SCREW_SERVO = SOUNDS.register("screw_servo",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "screw_servo")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_LEVER = SOUNDS.register("telegraph_lever",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "telegraph_lever")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_BELL = SOUNDS.register("telegraph_bell",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "telegraph_bell")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DUCTED_FAN_LOOP = SOUNDS.register("ducted_fan_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ducted_fan_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DUCTED_FAN_START = SOUNDS.register("ducted_fan_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ducted_fan_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JET1_LOOP = SOUNDS.register("jet1_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet1_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JET2_LOOP = SOUNDS.register("jet2_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet2_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JETS_LOOP = SOUNDS.register("jets_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jets_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_AB_LOOP = SOUNDS.register("jet_ab_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_ab_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_START = SOUNDS.register("jet_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_start")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_AB_LIGHT = SOUNDS.register("jet_ab_light",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_ab_light")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_SPOOL_UP = SOUNDS.register("jet_spool_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_spool_up")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JET_SHUTDOWN = SOUNDS.register("jet_shutdown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jet_shutdown")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOFAN_LOOP = SOUNDS.register("turbofan_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turbofan_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOFAN_START = SOUNDS.register("turbofan_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turbofan_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOFAN_SPOOL_UP = SOUNDS.register("turbofan_spool_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turbofan_spool_up")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOFAN_SPOOL_DOWN = SOUNDS.register("turbofan_spool_down",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turbofan_spool_down")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JATO_IGNITE = SOUNDS.register("jato_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jato_ignite")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JATO_LOOP = SOUNDS.register("jato_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jato_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JATO_BURNOUT = SOUNDS.register("jato_burnout",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jato_burnout")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JATO_RELEASE = SOUNDS.register("jato_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jato_release")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JATO_BOTTLE_LAND = SOUNDS.register("jato_bottle_land",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jato_bottle_land")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ASR_RUN = SOUNDS.register("asr_run",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asr_run")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ASR_SPIN_UP = SOUNDS.register("asr_spin_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asr_spin_up")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ASR_SPIN_DOWN = SOUNDS.register("asr_spin_down",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asr_spin_down")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ASR_SWITCH = SOUNDS.register("asr_switch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "asr_switch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_HUM = SOUNDS.register("core_hum",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "core_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_APU = SOUNDS.register("core_apu",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "core_apu")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_APU_START = SOUNDS.register("core_apu_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "core_apu_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_LOAD = SOUNDS.register("station_load",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "station_load")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PYLON_EJECT = SOUNDS.register("pylon_eject",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pylon_eject")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_DOOR_OPEN = SOUNDS.register("bay_door_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_door_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BAY_DOOR_CLOSE = SOUNDS.register("bay_door_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bay_door_close")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER_LOOP = SOUNDS.register("jammer_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jammer_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GPOD_SPIN = SOUNDS.register("gpod_spin_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gpod_spin_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GPOD_BURST = SOUNDS.register("gpod_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gpod_burst")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GPOD_RELOAD = SOUNDS.register("gpod_reload",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gpod_reload")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TPOD_CLICK = SOUNDS.register("tpod_click",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tpod_click")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TPOD_SLEW = SOUNDS.register("tpod_slew",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tpod_slew")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_CYCLE = SOUNDS.register("gear_cycle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gear_cycle")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_LOCK = SOUNDS.register("gear_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gear_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_TOUCHDOWN = SOUNDS.register("gear_touchdown",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gear_touchdown")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TOW_COUPLE = SOUNDS.register("tow_couple",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tow_couple")));
    public static final DeferredHolder<SoundEvent, SoundEvent> TOW_ROLL = SOUNDS.register("tow_roll",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tow_roll")));
    public static final DeferredHolder<SoundEvent, SoundEvent> NOSE_DRIVE = SOUNDS.register("nose_drive",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "nose_drive")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_MOVE = SOUNDS.register("hook_move",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hook_move")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRE_CATCH = SOUNDS.register("wire_catch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wire_catch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> WIRE_RESET = SOUNDS.register("wire_reset",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "wire_reset")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CAT_HOOK = SOUNDS.register("cat_hook",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cat_hook")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CAT_SHOT = SOUNDS.register("cat_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cat_shot")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CAT_RETURN = SOUNDS.register("cat_return",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cat_return")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_LOOP = SOUNDS.register("lift_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_CLUNK = SOUNDS.register("lift_clunk",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_clunk")));
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_RAIL = SOUNDS.register("lift_rail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_rail")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AAM_LAUNCH = SOUNDS.register("aam_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_launch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AAM_LAUNCH_FAR = SOUNDS.register("aam_launch_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_launch_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AAM_FLIGHT_LOOP = SOUNDS.register("aam_flight_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_flight_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AAM_BURST = SOUNDS.register("aam_burst",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_burst")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AAM_BURST_FAR = SOUNDS.register("aam_burst_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_burst_far")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AAM_FIZZLE = SOUNDS.register("aam_fizzle",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "aam_fizzle")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FLARE_POP = SOUNDS.register("flare_pop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "flare_pop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CHAFF_POP = SOUNDS.register("chaff_pop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chaff_pop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DISP_RELOAD = SOUNDS.register("disp_reload",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "disp_reload")));
    public static final DeferredHolder<SoundEvent, SoundEvent> JATO_LATCH = SOUNDS.register("jato_latch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jato_latch")));
    public static final DeferredHolder<SoundEvent, SoundEvent> SEAT_ROCKET = SOUNDS.register("seat_rocket",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "seat_rocket")));

    public static final DeferredHolder<SoundEvent, SoundEvent> JAMMER_SWITCH = SOUNDS.register("jammer_switch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "jammer_switch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SAM_SLEW = SOUNDS.register("sam_slew",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sam_slew")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SAM_LOCK = SOUNDS.register("sam_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sam_lock")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SAM_HATCH = SOUNDS.register("sam_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sam_hatch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SAM_CHIRP = SOUNDS.register("sam_chirp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sam_chirp")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SAM_LAUNCH = SOUNDS.register("sam_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sam_launch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_WHEEL = SOUNDS.register("telegraph_wheel",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "telegraph_wheel")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HELM_SEAT_IN = SOUNDS.register("helm_seat_in",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_seat_in")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HELM_SEAT_OUT = SOUNDS.register("helm_seat_out",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_seat_out")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HELM_STEP = SOUNDS.register("helm_step",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_step")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HELM_BOOT = SOUNDS.register("helm_boot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "helm_boot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COCKPIT_HUM = SOUNDS.register("cockpit_hum",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cockpit_hum")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CANOPY_OPEN = SOUNDS.register("canopy_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "canopy_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CANOPY_CLOSE = SOUNDS.register("canopy_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "canopy_close")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COCKPIT_HARNESS = SOUNDS.register("cockpit_harness",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cockpit_harness")));

    public static final DeferredHolder<SoundEvent, SoundEvent> COCKPIT_SWITCH = SOUNDS.register("cockpit_switch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "cockpit_switch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RWR_SEARCH = SOUNDS.register("rwr_search",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rwr_search")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RWR_LOCK = SOUNDS.register("rwr_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rwr_lock")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RWR_LAUNCH = SOUNDS.register("rwr_launch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rwr_launch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MSL_GROWL = SOUNDS.register("msl_growl",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "msl_growl")));

    public static final DeferredHolder<SoundEvent, SoundEvent> MSL_TONE = SOUNDS.register("msl_tone",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "msl_tone")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_PULL_UP = SOUNDS.register("voice_pull_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "voice_pull_up")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_MISSILE = SOUNDS.register("voice_missile",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "voice_missile")));

    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE_BINGO = SOUNDS.register("voice_bingo",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "voice_bingo")));

    public static final DeferredHolder<SoundEvent, SoundEvent> EJECT_BLAST = SOUNDS.register("eject_blast",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "eject_blast")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CHUTE_OPEN = SOUNDS.register("chute_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "chute_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_JET_LOOP = SOUNDS.register("pump_jet_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pump_jet_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_JET_START = SOUNDS.register("pump_jet_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pump_jet_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_JET_STOP = SOUNDS.register("pump_jet_stop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pump_jet_stop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_JET_SERVO = SOUNDS.register("pump_jet_servo",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pump_jet_servo")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_START = SOUNDS.register("diesel_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "diesel_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_FIRE = SOUNDS.register("diesel_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "diesel_fire")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_LOOP = SOUNDS.register("diesel_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "diesel_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_COUGH = SOUNDS.register("diesel_cough",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "diesel_cough")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DIESEL_STOP = SOUNDS.register("diesel_stop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "diesel_stop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SNORKEL_HATCH = SOUNDS.register("snorkel_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "snorkel_hatch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SNORKEL_HOIST = SOUNDS.register("snorkel_hoist",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "snorkel_hoist")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SNORKEL_SLAM = SOUNDS.register("snorkel_slam",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "snorkel_slam")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SNORKEL_AIR = SOUNDS.register("snorkel_air",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "snorkel_air")));

    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_FAN_LOOP = SOUNDS.register("lift_fan_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "lift_fan_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ROTOR_LOOP = SOUNDS.register("rotor_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rotor_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOSHAFT_LOOP = SOUNDS.register("turboshaft_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turboshaft_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TURBOPROP_LOOP = SOUNDS.register("turboprop_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "turboprop_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TILTROTOR_LOOP = SOUNDS.register("tiltrotor_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tiltrotor_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GAS_TURBINE_LOOP = SOUNDS.register("gas_turbine_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gas_turbine_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> WATERJET_LOOP = SOUNDS.register("waterjet_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "waterjet_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AZIMUTH_LOOP = SOUNDS.register("azimuth_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "azimuth_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> BOW_THRUSTER_LOOP = SOUNDS.register("bow_thruster_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bow_thruster_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> WINCH_LOOP = SOUNDS.register("winch_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "winch_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_LATCH = SOUNDS.register("hook_latch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hook_latch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOOK_RELEASE = SOUNDS.register("hook_release",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hook_release")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_CHAIN_OUT = SOUNDS.register("anchor_chain_out",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "anchor_chain_out")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_WEIGH = SOUNDS.register("anchor_weigh",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "anchor_weigh")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_DRAG = SOUNDS.register("anchor_drag",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "anchor_drag")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_BRAKE = SOUNDS.register("anchor_brake",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "anchor_brake")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_SPLASH = SOUNDS.register("anchor_splash",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "anchor_splash")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ANCHOR_HOME = SOUNDS.register("anchor_home",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "anchor_home")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HOSE_REEL_LOOP = SOUNDS.register("hose_reel_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hose_reel_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> REFUEL_CONTACT = SOUNDS.register("refuel_contact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "refuel_contact")));

    public static final DeferredHolder<SoundEvent, SoundEvent> FUEL_FLOW_LOOP = SOUNDS.register("fuel_flow_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fuel_flow_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_SLIDE = SOUNDS.register("probe_slide",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "probe_slide")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_JETTISON = SOUNDS.register("tank_jettison",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tank_jettison")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TAIL_ROTOR_LOOP = SOUNDS.register("tail_rotor_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tail_rotor_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_IMPACT = SOUNDS.register("tank_impact",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "tank_impact")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SA_HYDRAULIC_LOOP = SOUNDS.register("sa_hydraulic_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_hydraulic_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SA_LID = SOUNDS.register("sa_lid",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_lid")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SA_COUPLE = SOUNDS.register("sa_couple",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_couple")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SA_BREAKAWAY = SOUNDS.register("sa_breakaway",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_breakaway")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SA_DONE = SOUNDS.register("sa_done",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_done")));

    public static final DeferredHolder<SoundEvent, SoundEvent> SA_DOOR = SOUNDS.register("sa_door",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "sa_door")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AD_VENT_LOOP = SOUNDS.register("ad_vent_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ad_vent_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AD_DOORS_OPEN = SOUNDS.register("ad_doors_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ad_doors_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AD_DOORS_CLOSE = SOUNDS.register("ad_doors_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ad_doors_close")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AD_STOW = SOUNDS.register("ad_stow",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ad_stow")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OL_HYD_LOOP = SOUNDS.register("ol_hyd_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ol_hyd_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OL_HATCH = SOUNDS.register("ol_hatch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ol_hatch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OL_TABLE = SOUNDS.register("ol_table",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ol_table")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OL_HOIST = SOUNDS.register("ol_hoist",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ol_hoist")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OL_LATCH = SOUNDS.register("ol_latch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ol_latch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> OL_RAM = SOUNDS.register("ol_ram",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ol_ram")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_FIRE = SOUNDS.register("rp_fire",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_fire")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_RIPPLE = SOUNDS.register("rp_ripple",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_ripple")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_FLIGHT_LOOP = SOUNDS.register("rp_flight_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_flight_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_PETALS_OPEN = SOUNDS.register("rp_petals_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_petals_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_PETALS_CLOSE = SOUNDS.register("rp_petals_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_petals_close")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_TRAY_DOWN = SOUNDS.register("rp_tray_down",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_tray_down")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_TRAY_UP = SOUNDS.register("rp_tray_up",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_tray_up")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RP_LOAD = SOUNDS.register("rp_load",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "rp_load")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ASHM_IGNITE = SOUNDS.register("ashm_ignite",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ashm_ignite")));

    public static final DeferredHolder<SoundEvent, SoundEvent> RAMP_LOOP = SOUNDS.register("ramp_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ramp_loop")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RAMP_CLUNK = SOUNDS.register("ramp_clunk",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ramp_clunk")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RAMP_LOCK = SOUNDS.register("ramp_lock",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ramp_lock")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RAMP_TOES = SOUNDS.register("ramp_toes",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ramp_toes")));
    public static final DeferredHolder<SoundEvent, SoundEvent> RAMP_WIND = SOUNDS.register("ramp_wind",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ramp_wind")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PALLET_ROLL = SOUNDS.register("pallet_roll",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pallet_roll")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PALLET_CHUTE = SOUNDS.register("pallet_chute",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pallet_chute")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PALLET_LAND = SOUNDS.register("pallet_land",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pallet_land")));
    public static final DeferredHolder<SoundEvent, SoundEvent> PALLET_SMOKE = SOUNDS.register("pallet_smoke",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "pallet_smoke")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_DRIVE_LOOP = SOUNDS.register("gt_drive_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_drive_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_SHOT = SOUNDS.register("gt_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_shot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_SHOT_FAR = SOUNDS.register("gt_shot_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_shot_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_DEPLOY = SOUNDS.register("gt_deploy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_deploy")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GT_STOW = SOUNDS.register("gt_stow",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gt_stow")));

    public static final DeferredHolder<SoundEvent, SoundEvent> GN_BEEP = SOUNDS.register("gn_beep",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "gn_beep")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HG_SHOT = SOUNDS.register("hg_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hg_shot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HG_SHOT_FAR = SOUNDS.register("hg_shot_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hg_shot_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HG_BREECH = SOUNDS.register("hg_breech",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hg_breech")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HG_PORT = SOUNDS.register("hg_port",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hg_port")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DG_DRIVE_LOOP = SOUNDS.register("dg_drive_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dg_drive_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DG_SHOT = SOUNDS.register("dg_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dg_shot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DG_SHOT_FAR = SOUNDS.register("dg_shot_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dg_shot_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DG_BREECH = SOUNDS.register("dg_breech",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dg_breech")));

    public static final DeferredHolder<SoundEvent, SoundEvent> DG_CASE = SOUNDS.register("dg_case",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "dg_case")));

    public static final DeferredHolder<SoundEvent, SoundEvent> XL_DRIVE_LOOP = SOUNDS.register("xl_drive_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "xl_drive_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> XL_SHOT = SOUNDS.register("xl_shot",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "xl_shot")));

    public static final DeferredHolder<SoundEvent, SoundEvent> XL_SHOT_FAR = SOUNDS.register("xl_shot_far",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "xl_shot_far")));

    public static final DeferredHolder<SoundEvent, SoundEvent> XL_RAM = SOUNDS.register("xl_ram",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "xl_ram")));

    public static final DeferredHolder<SoundEvent, SoundEvent> XL_BREECH = SOUNDS.register("xl_breech",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "xl_breech")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_LOOP = SOUNDS.register("hd_loop",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_loop")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_LOOP_HEAVY = SOUNDS.register("hd_loop_heavy",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_loop_heavy")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_START = SOUNDS.register("hd_start",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_start")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_STOP_FLOOR = SOUNDS.register("hd_stop_floor",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_stop_floor")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_STOP_TOP = SOUNDS.register("hd_stop_top",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_stop_top")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_LATCH = SOUNDS.register("hd_latch",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_latch")));

    public static final DeferredHolder<SoundEvent, SoundEvent> AP_ENGAGE = SOUNDS.register("ap_engage",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ap_engage")));
    public static final DeferredHolder<SoundEvent, SoundEvent> AP_DISCONNECT = SOUNDS.register("ap_disconnect",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ap_disconnect")));
    public static final DeferredHolder<SoundEvent, SoundEvent> BAILOUT_BELL = SOUNDS.register("bailout_bell",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "bailout_bell")));
    public static final DeferredHolder<SoundEvent, SoundEvent> FDK_SEAT_SLIDE = SOUNDS.register("fdk_seat_slide",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "fdk_seat_slide")));

    public static final DeferredHolder<SoundEvent, SoundEvent> HD_SAFETY = SOUNDS.register("hd_safety",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "hd_safety")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CT_PAGE = SOUNDS.register("ct_page",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ct_page")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CT_LAMP = SOUNDS.register("ct_lamp",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ct_lamp")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CT_WAKE = SOUNDS.register("ct_wake",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Wmd.MODID, "ct_wake")));
}
