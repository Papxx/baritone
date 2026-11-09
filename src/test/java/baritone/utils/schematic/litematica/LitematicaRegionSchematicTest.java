/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic.litematica;

import baritone.api.schematic.IStaticSchematic;
import baritone.api.utils.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.Half;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks the direct Litematica reader against a reimplementation of what Litematica itself pastes
 * ({@code SchematicPlacingUtils.placeBlocksWithinChunk} and {@code PositionUtils} of Litematica 0.28.x / 0.29.x).
 * The reference below deliberately doesn't use any production code.
 */
public class LitematicaRegionSchematicTest {

    private static final Vec3i[] SIZES = {
            new Vec3i(3, 2, 4), new Vec3i(-3, 2, 4), new Vec3i(3, -2, -4), new Vec3i(-2, -3, -5), new Vec3i(1, 1, 1), new Vec3i(5, 1, 2)
    };
    private static final Vec3i[] REGION_POSITIONS = {
            new Vec3i(0, 0, 0), new Vec3i(5, -2, 7), new Vec3i(-4, 3, -6)
    };
    private static final Vec3i ORIGIN = new Vec3i(100, 64, -200);

    private static List<BlockState> palette;

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        palette = new ArrayList<>();
        palette.add(Blocks.AIR.defaultBlockState());
        palette.add(Blocks.STONE.defaultBlockState());
        palette.add(Blocks.STRUCTURE_VOID.defaultBlockState());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            palette.add(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.TOP));
            palette.add(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing));
            palette.add(Blocks.REPEATER.defaultBlockState().setValue(RepeaterBlock.FACING, facing));
            palette.add(Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing).setValue(ChestBlock.TYPE, ChestType.LEFT));
        }
        for (Direction facing : Direction.values()) {
            palette.add(Blocks.OBSERVER.defaultBlockState().setValue(ObserverBlock.FACING, facing));
        }
        for (Direction.Axis axis : Direction.Axis.values()) {
            palette.add(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis));
        }
    }

    // ---- reference implementation of Litematica ----

    private static Vec3i refTransform(Vec3i pos, Mirror mirror, Rotation rotation) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        switch (mirror) {
            case LEFT_RIGHT:
                z = -z;
                break;
            case FRONT_BACK:
                x = -x;
                break;
            default:
        }
        switch (rotation) {
            case CLOCKWISE_90:
                return new Vec3i(-z, y, x);
            case COUNTERCLOCKWISE_90:
                return new Vec3i(z, y, -x);
            case CLOCKWISE_180:
                return new Vec3i(-x, y, -z);
            default:
                return new Vec3i(x, y, z);
        }
    }

    private static Vec3i relEnd(Vec3i size) {
        return new Vec3i(
                size.getX() >= 0 ? size.getX() - 1 : size.getX() + 1,
                size.getY() >= 0 ? size.getY() - 1 : size.getY() + 1,
                size.getZ() >= 0 ? size.getZ() - 1 : size.getZ() + 1);
    }

    private static final class Region {
        final Vec3i pos;
        final Vec3i size;
        final Mirror mirror;
        final Rotation rotation;
        final BlockState[][][] blocks; // [x][y][z], untransformed

        Region(Vec3i pos, Vec3i size, Mirror mirror, Rotation rotation, Random rand) {
            this.pos = pos;
            this.size = size;
            this.mirror = mirror;
            this.rotation = rotation;
            this.blocks = new BlockState[Math.abs(size.getX())][Math.abs(size.getY())][Math.abs(size.getZ())];
            for (BlockState[][] plane : blocks) {
                for (BlockState[] column : plane) {
                    for (int i = 0; i < column.length; i++) {
                        column[i] = palette.get(rand.nextInt(palette.size()));
                    }
                }
            }
        }

        BlockState get(int x, int y, int z) {
            return blocks[x][y][z];
        }
    }

    /**
     * What Litematica pastes for one sub-region, keyed by world position.
     */
    private static void refPaste(Map<BlockPos, BlockState> world, Vec3i origin, Mirror mainMirror, Rotation mainRotation, Region region) {
        Vec3i regionPos = region.pos;
        Vec3i posEndRel = relEnd(region.size).offset(regionPos);
        Vec3i posMinRel = new Vec3i(Math.min(regionPos.getX(), posEndRel.getX()), Math.min(regionPos.getY(), posEndRel.getY()), Math.min(regionPos.getZ(), posEndRel.getZ()));
        Vec3i regionPosTransformed = refTransform(regionPos, mainMirror, mainRotation);
        Rotation rotationCombined = mainRotation.getRotated(region.rotation);
        Mirror mirrorSub = region.mirror;
        if (mirrorSub != Mirror.NONE && (mainRotation == Rotation.CLOCKWISE_90 || mainRotation == Rotation.COUNTERCLOCKWISE_90)) {
            mirrorSub = mirrorSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }
        for (int x = 0; x < region.blocks.length; x++) {
            for (int y = 0; y < region.blocks[x].length; y++) {
                for (int z = 0; z < region.blocks[x][y].length; z++) {
                    BlockState state = region.get(x, y, z);
                    if (state.getBlock() == Blocks.STRUCTURE_VOID) {
                        continue;
                    }
                    Vec3i rel = new Vec3i(posMinRel.getX() - regionPos.getX() + x, posMinRel.getY() - regionPos.getY() + y, posMinRel.getZ() - regionPos.getZ() + z);
                    Vec3i pos = refTransform(refTransform(rel, mainMirror, mainRotation), region.mirror, region.rotation)
                            .offset(regionPosTransformed).offset(origin);
                    if (mainMirror != Mirror.NONE) {
                        state = state.mirror(mainMirror);
                    }
                    if (mirrorSub != Mirror.NONE) {
                        state = state.mirror(mirrorSub);
                    }
                    if (rotationCombined != Rotation.NONE) {
                        state = state.rotate(rotationCombined);
                    }
                    world.put(new BlockPos(pos), state);
                }
            }
        }
    }

    // ---- helpers ----

    private static LitematicaRegionSchematic view(Mirror mainMirror, Rotation mainRotation, Region region) {
        LitematicaRegionTransform transform = new LitematicaRegionTransform(region.pos, region.size, mainMirror, mainRotation, region.mirror, region.rotation);
        return new LitematicaRegionSchematic(region::get, transform);
    }

    /**
     * Every position in the bounding box must match the reference: same state where Litematica pastes,
     * "not in schematic" everywhere else.
     */
    private static void assertMatches(String msg, Map<BlockPos, BlockState> expected, Pair<IStaticSchematic, Vec3i> actual) {
        IStaticSchematic schematic = actual.first();
        Vec3i corner = actual.second();
        int found = 0;
        for (int x = 0; x < schematic.widthX(); x++) {
            for (int y = 0; y < schematic.heightY(); y++) {
                for (int z = 0; z < schematic.lengthZ(); z++) {
                    BlockPos world = new BlockPos(corner.getX() + x, corner.getY() + y, corner.getZ() + z);
                    BlockState want = expected.get(world);
                    if (want == null) {
                        assertFalse(msg + " extra block at " + world, schematic.inSchematic(x, y, z, null));
                        assertNull(msg + " getDirect at " + world, schematic.getDirect(x, y, z));
                    } else {
                        found++;
                        assertTrue(msg + " missing block at " + world, schematic.inSchematic(x, y, z, null));
                        assertEquals(msg + " at " + world, want, schematic.desiredState(x, y, z, null, Collections.emptyList()));
                        assertEquals(msg + " getDirect at " + world, want, schematic.getDirect(x, y, z));
                    }
                }
            }
        }
        assertEquals(msg + " blocks outside of the bounding box", expected.size(), found);
    }

    // ---- tests ----

    @Test
    public void matchesLitematicaForAllTransforms() {
        Random rand = new Random(42);
        int cases = 0;
        for (Mirror mainMirror : Mirror.values()) {
            for (Rotation mainRotation : Rotation.values()) {
                for (Mirror subMirror : Mirror.values()) {
                    for (Rotation subRotation : Rotation.values()) {
                        for (Vec3i size : SIZES) {
                            for (Vec3i regionPos : REGION_POSITIONS) {
                                Region region = new Region(regionPos, size, subMirror, subRotation, rand);
                                Map<BlockPos, BlockState> expected = new HashMap<>();
                                refPaste(expected, ORIGIN, mainMirror, mainRotation, region);
                                Pair<IStaticSchematic, Vec3i> actual = LitematicaHelper.assemble("test", ORIGIN, Collections.singletonList(view(mainMirror, mainRotation, region)));
                                String msg = mainMirror + "/" + mainRotation + " sub " + subMirror + "/" + subRotation + " size " + size + " pos " + regionPos;
                                assertMatches(msg, expected, actual);
                                cases++;
                            }
                        }
                    }
                }
            }
        }
        assertEquals(3 * 4 * 3 * 4 * SIZES.length * REGION_POSITIONS.length, cases);
    }

    @Test
    public void matchesLitematicaForSeveralSubRegions() {
        Random rand = new Random(7);
        for (Mirror mainMirror : Mirror.values()) {
            for (Rotation mainRotation : Rotation.values()) {
                // non-overlapping regions, one with a negative size and its own transform
                Region a = new Region(new Vec3i(0, 0, 0), new Vec3i(4, 3, 5), Mirror.NONE, Rotation.NONE, rand);
                Region b = new Region(new Vec3i(12, 1, -3), new Vec3i(-3, 2, 4), Mirror.FRONT_BACK, Rotation.CLOCKWISE_90, rand);
                Region c = new Region(new Vec3i(-9, -4, 10), new Vec3i(2, -2, -3), Mirror.LEFT_RIGHT, Rotation.COUNTERCLOCKWISE_90, rand);
                Map<BlockPos, BlockState> expected = new HashMap<>();
                List<LitematicaRegionSchematic> views = new ArrayList<>();
                for (Region r : new Region[]{a, b, c}) {
                    refPaste(expected, ORIGIN, mainMirror, mainRotation, r);
                    views.add(view(mainMirror, mainRotation, r));
                }
                assertMatches(mainMirror + "/" + mainRotation, expected, LitematicaHelper.assemble("test", ORIGIN, views));
            }
        }
    }

    /**
     * The bounding box must be the same one the previous implementation (reading Litematica's preview world)
     * computed, so placements end up exactly where they were built before.
     */
    @Test
    public void boundingBoxMatchesPreviousImplementation() {
        for (Mirror mainMirror : Mirror.values()) {
            for (Rotation mainRotation : Rotation.values()) {
                for (Mirror subMirror : Mirror.values()) {
                    for (Rotation subRotation : Rotation.values()) {
                        for (Vec3i size : SIZES) {
                            for (Vec3i regionPos : REGION_POSITIONS) {
                                Vec3i pos = refTransform(regionPos, mainMirror, mainRotation);
                                Vec3i oldSize = refTransform(refTransform(size, mainMirror, mainRotation), subMirror, subRotation);
                                int mx = Math.min(oldSize.getX() + 1, 0);
                                int my = Math.min(oldSize.getY() + 1, 0);
                                int mz = Math.min(oldSize.getZ() + 1, 0);
                                LitematicaRegionTransform t = new LitematicaRegionTransform(regionPos, size, mainMirror, mainRotation, subMirror, subRotation);
                                String msg = mainMirror + "/" + mainRotation + " sub " + subMirror + "/" + subRotation + " size " + size + " pos " + regionPos;
                                assertEquals(msg, pos.getX() + mx, t.minX);
                                assertEquals(msg, pos.getY() + my, t.minY);
                                assertEquals(msg, pos.getZ() + mz, t.minZ);
                                assertEquals(msg, Math.abs(oldSize.getX()), t.widthX);
                                assertEquals(msg, Math.abs(oldSize.getY()), t.heightY);
                                assertEquals(msg, Math.abs(oldSize.getZ()), t.lengthZ);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Hand-computed case, independent of the reference: a 2x1x1 row of stairs facing north, rotated clockwise.
     */
    @Test
    public void rotatedStairsHandComputed() {
        BlockState first = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState second = Blocks.STONE.defaultBlockState();
        BlockState[] row = {first, second};
        LitematicaRegionTransform t = new LitematicaRegionTransform(Vec3i.ZERO, new Vec3i(2, 1, 1), Mirror.NONE, Rotation.CLOCKWISE_90, Mirror.NONE, Rotation.NONE);
        LitematicaRegionSchematic view = new LitematicaRegionSchematic((x, y, z) -> row[x], t);
        // x axis becomes +z: the row now runs from z=0 to z=1 at x=0
        assertEquals(0, t.minX);
        assertEquals(0, t.minZ);
        assertEquals(1, view.widthX());
        assertEquals(2, view.lengthZ());
        assertEquals(Direction.EAST, view.getDirect(0, 0, 0).getValue(StairBlock.FACING));
        assertEquals(second, view.getDirect(0, 0, 1));
    }

    @Test
    public void structureVoidIsNotPartOfTheSchematic() {
        BlockState[] row = {Blocks.STRUCTURE_VOID.defaultBlockState(), Blocks.STONE.defaultBlockState()};
        LitematicaRegionTransform t = new LitematicaRegionTransform(Vec3i.ZERO, new Vec3i(2, 1, 1), Mirror.NONE, Rotation.NONE, Mirror.NONE, Rotation.NONE);
        LitematicaRegionSchematic view = new LitematicaRegionSchematic((x, y, z) -> row[x], t);
        assertFalse(view.inSchematic(0, 0, 0, null));
        assertNull(view.getDirect(0, 0, 0));
        assertTrue(view.inSchematic(1, 0, 0, null));
        assertFalse(view.inSchematic(2, 0, 0, null));
    }

    @Test
    public void noRegionsGivesNull() {
        assertNull(LitematicaHelper.assemble("empty", ORIGIN, Collections.emptyList()));
    }
}
