package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

final class CcTweakedCompat {

    private CcTweakedCompat() {}

    static void register(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> t : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(t);
            if (id != null && Wmd.MODID.equals(id.getNamespace())) reg(event, t);
        }
    }

    private static <B extends BlockEntity> void reg(RegisterCapabilitiesEvent event, BlockEntityType<B> t) {
        event.registerBlockEntity(PeripheralCapability.get(), t, (be, side) -> peripheral(be));
    }

    private static IPeripheral peripheral(BlockEntity be) {
        if (be instanceof MachinePartBlockEntity part && part.getController() != null && be.getLevel() != null) be = be.getLevel().getBlockEntity(part.getController());
        if (be instanceof BlinkGatePieceBlockEntity piece && piece.core() != null && be.getLevel() != null) be = be.getLevel().getBlockEntity(piece.core());
        return be instanceof GuiMachine ? new Machine(be) : null;
    }

    public static final class Machine implements IPeripheral {
        private final BlockEntity be;
        private final GuiMachine m;

        Machine(BlockEntity be) { this.be = be; this.m = (GuiMachine) be; }

        @Override public String getType() { return "wmd_machine"; }
        @Override public Object getTarget() { return be; }
        @Override public boolean equals(IPeripheral other) { return other instanceof Machine o && o.be == be; }

        @LuaFunction(mainThread = true)
        public final String getName() { return BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath(); }

        @LuaFunction(mainThread = true)
        public final boolean isRunning() { return m.guiRunning(); }

        @LuaFunction(mainThread = true)
        public final String getStatus() {
            if (GuiMachine.lowPower(m.guiStatus())) return "low_power";
            return switch (m.guiStatus()) {
                case 1 -> "running"; case 2 -> "no_power"; case 3 -> "output_full"; case 4 -> "no_input"; case 5 -> "no_electrode"; case 6 -> "no_water";
                case 7 -> "no_impeller"; case 8 -> "no_deposit"; case 9 -> "wrong_bit"; case 10 -> "no_bit"; case 11 -> "no_blades"; case 12 -> "field_down"; default -> "idle";
            };
        }

        @LuaFunction(mainThread = true)
        public final Map<String, Object> getEnergy() {
            var e = m.guiEnergy();
            if (e == null) return null;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("stored", e.getEnergyStored());
            out.put("capacity", e.getMaxEnergyStored());
            out.put("usePerTick", m.guiFeUse());
            return out;
        }

        @LuaFunction(mainThread = true)
        public final List<Map<String, Object>> getTanks() {
            List<Map<String, Object>> out = new ArrayList<>();
            List<FluidTank> ts = m.guiTanks();
            List<net.minecraft.world.item.ItemStack> icons = m.guiSlotIcons();
            for (int i = 0; i < ts.size(); i++) {
                FluidTank t = ts.get(i);
                Map<String, Object> e = new LinkedHashMap<>();
                boolean item = i < icons.size() && !icons.get(i).isEmpty();
                if (item) e.put("item", BuiltInRegistries.ITEM.getKey(icons.get(i).getItem()).toString());
                else e.put("fluid", t.isEmpty() ? (i < m.guiTankFluids().size() ? BuiltInRegistries.FLUID.getKey(m.guiTankFluids().get(i)).toString() : "")
                        : BuiltInRegistries.FLUID.getKey(t.getFluid().getFluid()).toString());
                e.put("amount", t.getFluidAmount());
                e.put("capacity", t.getCapacity());
                e.put("output", item ? m.guiSlotOutput(i) : m.guiTankOutput(i));
                out.add(e);
            }
            return out;
        }

        @LuaFunction(mainThread = true)
        public final double getProgress() { int p = m.guiProgress(); return p < 0 ? -1 : p / 1000.0; }

        @LuaFunction(mainThread = true)
        public final Map<String, Object> getPulseEmp() {
            if (!(be instanceof PulseEmpBlockEntity e)) return null;
            Map<String, Object> o = new java.util.HashMap<>();
            String[] st = { "cold", "charging", "ready", "firing", "cooling" };
            o.put("state", st[Math.max(0, Math.min(st.length - 1, e.empState()))]);
            o.put("charge", e.chargePermille() / 1000.0);
            o.put("cooldown", e.coolSecondsLeft());
            o.put("contacts", e.contacts());
            o.put("inCone", e.inCone());
            o.put("rule", e.rule());
            return o;
        }

        @LuaFunction(mainThread = true)
        public final boolean firePulseEmp() { return be instanceof PulseEmpBlockEntity e && e.remoteFire(); }

        @LuaFunction(mainThread = true)
        public final Map<String, Object> getBlinkGate() {
            if (!(be instanceof BlinkGateCoreBlockEntity g)) return null;
            Map<String, Object> o = new java.util.HashMap<>();
            String[] st = { "off", "deploying", "powering", "forming", "open", "closing", "cooling" };
            String[] why = { "", "not_trusted", "does_not_fit", "partner_not_ready", "exit_blocked", "no_power" };
            o.put("state", st[Math.max(0, Math.min(st.length - 1, g.gateState()))]);
            o.put("status", g.gateStatus());
            o.put("formed", g.formed());
            o.put("size", g.size());
            if (g.partner() != null) { Map<String, Object> pp = new LinkedHashMap<>(); pp.put("x", g.partner().getX()); pp.put("y", g.partner().getY()); pp.put("z", g.partner().getZ()); o.put("partner", pp); }
            o.put("auto", g.auto());
            o.put("buffer", g.bufferPermille() / 1000.0);
            o.put("cooldown", g.coolSecondsLeft());
            o.put("jumps", g.jumpCount());
            o.put("lastRefusal", why[Math.max(0, Math.min(why.length - 1, g.lastWhy()))]);
            o.put("rfChannel", g.rfChannel());
            o.put("rfIn", g.rfRate());
            return o;
        }

        @LuaFunction(mainThread = true)
        public final boolean armBlinkGate() { return be instanceof BlinkGateCoreBlockEntity g && g.arm(); }

        @LuaFunction(mainThread = true)
        public final void closeBlinkGate() { if (be instanceof BlinkGateCoreBlockEntity g) g.close(); }

        @LuaFunction(mainThread = true)
        public final Map<String, Object> getStormGenerator() {
            if (!(be instanceof StormGeneratorBlockEntity sg)) return null;
            Map<String, Object> o = new java.util.HashMap<>();
            String[] st = { "no_owner", "no_target", "out_of_range", "charging", "ready", "building", "raining", "ending", "cooling" };
            String[] ph = { "idle", "build", "storm", "end", "cool" };
            o.put("status", st[Math.max(0, Math.min(st.length - 1, sg.stormStatus()))]);
            o.put("phase", ph[Math.max(0, Math.min(ph.length - 1, sg.phase()))]);
            o.put("charge", sg.chargePermille() / 1000.0);
            if (sg.hasTarget()) o.put("target", Map.of("x", sg.targetX(), "z", sg.targetZ()));
            o.put("distance", sg.hasTarget() ? sg.distance() : 0);
            o.put("bearing", sg.hasTarget() ? sg.bearing() : 0);
            o.put("radius", sg.radius());
            o.put("secondsLeft", sg.secondsLeft());
            return o;
        }

        @LuaFunction(mainThread = true)
        public final boolean setStormTarget(int x, int z) {
            if (!(be instanceof StormGeneratorBlockEntity sg)) return false;
            sg.setTarget(x, z);
            return true;
        }

        @LuaFunction(mainThread = true)
        public final boolean fireStorm() { return be instanceof StormGeneratorBlockEntity sg && sg.remoteFire(); }

        @LuaFunction(mainThread = true)
        public final List<Map<String, Object>> getUpgrades() {
            List<Map<String, Object>> out = new ArrayList<>();
            if (be instanceof ProcessUnitBlockEntity pu) for (UpgradeItem.Type t : UpgradeItem.Type.values()) {
                int l = pu.upgradeLevel(t);
                if (l > 0) { Map<String, Object> e = new LinkedHashMap<>(); e.put("type", t.id); e.put("level", l); out.add(e); }
            }
            return out;
        }

        @LuaFunction(mainThread = true)
        public final String getOwner() { return be instanceof Owned o ? o.getOwnerName() : ""; }
    }
}
