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

package baritone.process.builder;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.RedstoneSide;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BuilderPropertyRulesTest {

    private static final Set<Property<?>> NO_ORIENTATION = Collections.emptySet();

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static boolean strict(BlockState a, BlockState b) {
        return BuilderPropertyRules.sameState(a, b, false, NO_ORIENTATION, Collections.emptyList(), false, false);
    }

    private static boolean dynamic(BlockState a, BlockState b) {
        return BuilderPropertyRules.sameState(a, b, false, NO_ORIENTATION, Collections.emptyList(), true, false);
    }

    private static boolean derived(BlockState a, BlockState b) {
        return BuilderPropertyRules.sameState(a, b, false, NO_ORIENTATION, Collections.emptyList(), false, true);
    }

    private static boolean both(BlockState a, BlockState b) {
        return BuilderPropertyRules.sameState(a, b, false, NO_ORIENTATION, Collections.emptyList(), true, true);
    }

    @Test
    public void growthIsDynamic() {
        BlockState young = Blocks.CACTUS.defaultBlockState();
        BlockState old = young.setValue(CactusBlock.AGE, 9);
        assertFalse(strict(young, old));
        assertTrue(dynamic(young, old));
        assertFalse(derived(young, old));
        BlockState wheat = Blocks.WHEAT.defaultBlockState();
        assertTrue(dynamic(wheat, wheat.setValue(BlockStateProperties.AGE_7, 7)));
        BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState();
        assertTrue(dynamic(sapling, sapling.setValue(BlockStateProperties.STAGE, 1)));
    }

    @Test
    public void naturalLeavesMatchPlacedLeaves() {
        BlockState natural = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 3);
        BlockState placed = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true).setValue(LeavesBlock.DISTANCE, 7);
        assertFalse(strict(natural, placed));
        assertTrue(dynamic(natural, placed));
        // waterlogged is real
        assertFalse(dynamic(natural, placed.setValue(LeavesBlock.WATERLOGGED, true)));
    }

    @Test
    public void worldStateIsDynamic() {
        // FarmlandBlock and SnowyBlock were renamed in 26.x, the shared property constants still apply
        BlockState dry = Blocks.FARMLAND.defaultBlockState();
        assertTrue(dynamic(dry, dry.setValue(BlockStateProperties.MOISTURE, 7)));
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        assertTrue(dynamic(grass, grass.setValue(BlockStateProperties.SNOWY, true)));
        BlockState scaffolding = Blocks.SCAFFOLDING.defaultBlockState();
        assertTrue(dynamic(scaffolding, scaffolding.setValue(BlockStateProperties.STABILITY_DISTANCE, 5)));
        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        assertTrue(dynamic(wire, wire.setValue(RedStoneWireBlock.POWER, 15)));
    }

    @Test
    public void connectionsAreDerivedForFencesPanesWallsAndRedstone() {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        assertFalse(strict(fence, fence.setValue(CrossCollisionBlock.NORTH, true)));
        assertTrue(derived(fence, fence.setValue(CrossCollisionBlock.NORTH, true).setValue(CrossCollisionBlock.WEST, true)));
        assertFalse(derived(fence, fence.setValue(CrossCollisionBlock.WATERLOGGED, true)));

        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        assertTrue(derived(pane, pane.setValue(CrossCollisionBlock.EAST, true)));
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        assertTrue(derived(bars, bars.setValue(CrossCollisionBlock.SOUTH, true)));

        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState();
        assertTrue(derived(wall, wall.setValue(WallBlock.NORTH, WallSide.TALL).setValue(WallBlock.UP, false)));

        BlockState wire = Blocks.REDSTONE_WIRE.defaultBlockState();
        assertTrue(derived(wire, wire.setValue(RedStoneWireBlock.NORTH, RedstoneSide.UP)));
    }

    /**
     * Vines use the same {@code north} property object as fences, but for them it is a real face.
     */
    @Test
    public void vineFacesAreNotDerived() {
        BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true);
        assertFalse(both(vine, vine.setValue(VineBlock.NORTH, true)));
        BlockState lichen = Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.DOWN, true);
        assertFalse(both(lichen, lichen.setValue(BlockStateProperties.NORTH, true)));
    }

    @Test
    public void stairShapeIsDerivedButFacingIsNot() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        assertTrue(derived(stairs, stairs.setValue(StairBlock.SHAPE, StairsShape.INNER_LEFT)));
        assertFalse(derived(stairs, stairs.setValue(StairBlock.FACING, Direction.EAST)));
        assertFalse(both(stairs, stairs.setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)));
    }

    @Test
    public void mushroomFacesAreDerived() {
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        assertTrue(derived(stem, stem.setValue(HugeMushroomBlock.NORTH, false).setValue(HugeMushroomBlock.UP, false)));
    }

    @Test
    public void otherBlocksAndPropertiesStayStrict() {
        assertFalse(both(Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()));
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        assertFalse(both(door, door.setValue(DoorBlock.OPEN, true)));
        assertFalse(both(door, door.setValue(DoorBlock.FACING, Direction.SOUTH)));
        BlockState repeater = Blocks.REPEATER.defaultBlockState();
        assertFalse(both(repeater, repeater.setValue(BlockStateProperties.DELAY, 3)));
        BlockState lever = Blocks.LEVER.defaultBlockState();
        assertFalse(both(lever, lever.setValue(BlockStateProperties.POWERED, true)));
    }

    @Test
    public void ignoredNamesAndDirectionStillWork() {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        BlockState turned = stairs.setValue(StairBlock.FACING, Direction.WEST);
        assertTrue(BuilderPropertyRules.sameState(stairs, turned, false, NO_ORIENTATION, Collections.singletonList("facing"), false, false));
        assertTrue(BuilderPropertyRules.sameState(stairs, turned, true, Collections.singleton(StairBlock.FACING), Collections.emptyList(), false, false));
    }

    @Test
    public void neighbourUpdateNeverMakesAirCorrect() {
        BlockState lowerDoor = Blocks.OAK_DOOR.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        // updateShape turns a door without its upper half into air
        assertFalse(BuilderPropertyRules.usableNeighbourUpdate(air, lowerDoor, air));
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        BlockState connected = fence.setValue(CrossCollisionBlock.NORTH, true);
        assertTrue(BuilderPropertyRules.usableNeighbourUpdate(fence, connected, fence));
        assertFalse(BuilderPropertyRules.usableNeighbourUpdate(Blocks.STONE.defaultBlockState(), connected, fence));
        assertFalse(BuilderPropertyRules.usableNeighbourUpdate(fence, connected, connected));
    }
}
