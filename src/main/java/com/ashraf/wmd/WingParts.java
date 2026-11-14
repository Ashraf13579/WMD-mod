package com.ashraf.wmd;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class WingParts {

    private WingParts() {}

    public static final float WING_T = 3.52F, MID_LOW = 6.24F;

    public enum Shape implements StringRepresentable {
        PANEL("panel"), LEADING("leading"), LEAD45("lead45"), LEAD12A("lead12a"), LEAD12B("lead12b"), TRAILING("trailing"), TIP("tip");
        private final String id;
        Shape(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }

        public boolean handed() { return this == LEAD45 || this == LEAD12A || this == LEAD12B || this == TIP; }

        public int index() { return ordinal(); }
    }

    public enum Role implements StringRepresentable {
        AILERON("aileron"), ELEVATOR("elevator"), RUDDER("rudder"), ELEVON("elevon"), FLAP("flap");
        private final String id;
        Role(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }
    }

    public enum Level implements StringRepresentable {
        LOW("low"), MID("mid"), HIGH("high");
        private final String id;
        Level(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }

        public float shift() { return (ordinal() - 1) * MID_LOW / 16.0F; }
    }

    public enum Thick implements StringRepresentable {
        THIN("thin", WING_T), L6("l6", 6.0F), THICK("thick", 8.0F), L10("l10", 10.0F), L12("l12", 12.0F), L14("l14", 14.0F), FULL("full", 16.0F);
        private final String id;
        public final float px;
        Thick(String id, float px) { this.id = id; this.px = px; }
        @Override public String getSerializedName() { return id; }

        public Thick next() { return ordinal() < THICK.ordinal() ? THICK : ordinal() < FULL.ordinal() ? FULL : THIN; }

        public Thick addLayer() { return values()[Math.min(ordinal() + 1, values().length - 1)]; }

        public String pxText() { return px == Math.round(px) ? Integer.toString(Math.round(px)) : String.format(java.util.Locale.ROOT, "%.1f", px); }
    }

    public static final DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty FIN = BooleanProperty.create("fin");
    public static final BooleanProperty MIRROR = BooleanProperty.create("mirror");
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final EnumProperty<Role> ROLE = EnumProperty.create("role", Role.class);
    public static final EnumProperty<Level> LEVEL = EnumProperty.create("level", Level.class);
    public static final EnumProperty<Thick> THICK = EnumProperty.create("thick", Thick.class);

    public enum Grow implements StringRepresentable {
        BOTH("both"), UP("up"), DOWN("down");
        private final String id;
        Grow(String id) { this.id = id; }
        @Override public String getSerializedName() { return id; }
    }
    public static final EnumProperty<Grow> GROW = EnumProperty.create("grow", Grow.class);
    public static final IntegerProperty POWER = net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER;

    static BlockState placed(BlockState base, BlockPlaceContext ctx) {
        boolean fin = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        Level lv = Level.MID;
        if (!fin) {
            Direction face = ctx.getClickedFace();
            if (face == Direction.UP) lv = Level.LOW;
            else if (face == Direction.DOWN) lv = Level.HIGH;
            else {
                double fy = ctx.getClickLocation().y - ctx.getClickedPos().getY();
                lv = fy < 1.0 / 3.0 ? Level.LOW : fy > 2.0 / 3.0 ? Level.HIGH : Level.MID;
            }
        }
        return base.setValue(FACING, ctx.getHorizontalDirection()).setValue(FIN, fin).setValue(LEVEL, lv);
    }

    public static float[] place(float x, float y, float z, Direction facing, boolean fin, boolean mirror, Level level) {
        return place(x, y, z, facing, fin, mirror, level, Thick.THIN);
    }

    static float midShift(Level level, Thick t, boolean fin) { return midShift(level, t, Grow.BOTH, fin); }

    static float midShift(Level level, Thick t, Grow grow, boolean fin) {
        if (fin) return level.shift();
        float h = t.px / 32.0F;
        if (grow != Grow.BOTH) {
            float h0 = WING_T / 32.0F;
            float sh = grow == Grow.UP ? level.shift() - h0 + h : level.shift() + h0 - h;
            return Math.max(h - 0.5F, Math.min(0.5F - h, sh));
        }
        Level lv = level;
        return lv == Level.LOW ? h - 0.5F : lv == Level.HIGH ? 0.5F - h : 0.0F;
    }

    public static float[] place(float x, float y, float z, Direction facing, boolean fin, boolean mirror, Level level, Thick thick) {
        return place(x, y, z, facing, fin, mirror, level, thick, Grow.BOTH);
    }

    public static float[] place(float x, float y, float z, Direction facing, boolean fin, boolean mirror, Level level, Thick thick, Grow grow) {
        if (mirror) x = -x;
        y = 0.5F + (y - 0.5F) * (thick.px / WING_T);
        y += midShift(level, thick, grow, fin);
        if (fin) { float nx = -(y - 0.5F), ny = x + 0.5F; x = nx; y = ny; }
        float rx, rz;
        switch (facing) {
            case SOUTH -> { rx = -x; rz = -z; }
            case EAST -> { rx = -z; rz = x; }
            case WEST -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return new float[] { rx + 0.5F, y, rz + 0.5F };
    }

    static VoxelShape shape(Direction facing, boolean fin, Level level) { return shape(facing, fin, level, Thick.THIN); }

    static VoxelShape shape(Direction facing, boolean fin, Level level, Thick thick) { return shape(facing, fin, level, thick, Grow.BOTH); }

    static VoxelShape shape(Direction facing, boolean fin, Level level, Thick thick, Grow grow) {
        float t = thick.px;
        if (!fin) {
            float y0 = 8.0F - t / 2.0F + midShift(level, thick, grow, false) * 16.0F;
            return Block.box(0, y0, 0, 16, y0 + t, 16);
        }
        float a = 8.0F - t / 2.0F, b = 8.0F + t / 2.0F;
        return facing.getAxis() == Direction.Axis.Z ? Block.box(a, 0, 0, b, 16, 16) : Block.box(0, 0, a, 16, 16, b);
    }

    public static Direction normal(BlockState s) {
        if (!s.getValue(FIN)) return Direction.UP;
        Direction.Axis span = s.getValue(FACING).getClockWise().getAxis();
        return Direction.get(Direction.AxisDirection.POSITIVE, span);
    }

    static Vec3 forward(BlockState s) { Direction f = s.getValue(FACING); return new Vec3(f.getStepX(), 0, f.getStepZ()); }
}
