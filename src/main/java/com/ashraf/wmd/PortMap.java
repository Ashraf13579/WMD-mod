package com.ashraf.wmd;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;

public final class PortMap {
    public static final int FLUID = 0, ITEM = 1, POWER = 2;

    public record Port(int x, int y, int z, Direction side, int kind, boolean out, String fluidId) {}

    public final int minX, maxX, depth;
    public final List<Port> ports = new ArrayList<>();

    public int minZ;

    public PortMap(int minX, int maxX, int depth) { this.minX = minX; this.maxX = maxX; this.depth = depth; }

    public PortMap fromZ(int z) { minZ = z; return this; }

    public PortMap fluid(int x, int y, int z, Direction side, String fluid, boolean out) { ports.add(new Port(x, y, z, side, FLUID, out, fluid)); return this; }
    public PortMap item(int x, int y, int z, Direction side, boolean out) { ports.add(new Port(x, y, z, side, ITEM, out, "")); return this; }
    public PortMap power(int x, int y, int z, Direction side, boolean out) { ports.add(new Port(x, y, z, side, POWER, out, "")); return this; }
}
