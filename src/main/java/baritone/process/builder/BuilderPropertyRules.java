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

import com.google.common.collect.ImmutableSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.Set;

/**
 * Which block state properties the builder may ignore when it compares the world with the schematic.
 * <p>
 * Properties are matched by identity, never by name: many blocks share property objects with unrelated meaning
 * (fence connections and vine faces are both {@code BlockStateProperties.NORTH}), so neighbour dependent
 * properties are only ignored for the block classes listed in {@link #isDerived}.
 */
public final class BuilderPropertyRules {

    /**
     * Properties that change on their own (growth, decay, signals, hydration) or that placing a block can't
     * produce (natural leaves, natural blocks). Comparing them strictly makes the builder break and replace the
     * block forever.
     */
    private static final Set<Property<?>> DYNAMIC = ImmutableSet.of(
            // growth
            BlockStateProperties.AGE_1, BlockStateProperties.AGE_2, BlockStateProperties.AGE_3, BlockStateProperties.AGE_4,
            BlockStateProperties.AGE_5, BlockStateProperties.AGE_7, BlockStateProperties.AGE_15, BlockStateProperties.AGE_25,
            BlockStateProperties.STAGE, BlockStateProperties.HATCH, BlockStateProperties.BERRIES,
            BlockStateProperties.LEVEL_HONEY, BlockStateProperties.DRIED_GHAST_HYDRATION_LEVELS,
            // world state
            BlockStateProperties.MOISTURE, BlockStateProperties.SNOWY,
            LeavesBlock.PERSISTENT, LeavesBlock.DISTANCE, ScaffoldingBlock.DISTANCE,
            // signals and transient states
            BlockStateProperties.POWER, BlockStateProperties.SCULK_SENSOR_PHASE, BlockStateProperties.BLOOM,
            BlockStateProperties.SHRIEKING, BlockStateProperties.CAN_SUMMON,
            BlockStateProperties.CREAKING_HEART_STATE, BlockStateProperties.NATURAL
    );

    private BuilderPropertyRules() {}

    /**
     * @return whether {@code property} changes on its own, independent of the block
     */
    public static boolean isDynamic(Property<?> property) {
        return DYNAMIC.contains(property);
    }

    /**
     * Properties Minecraft derives from the neighbours ({@code updateShape}), so they are only right once the
     * neighbours are built: connections of fences, panes, bars, walls and redstone, stair shapes, mushroom faces,
     * fence gates in walls, note block instruments, scaffolding bottoms and dripstone/sulfur spike thickness.
     *
     * @return whether {@code property} of {@code block} depends on the neighbouring blocks
     */
    public static boolean isDerived(Block block, Property<?> property) {
        if (property == BlockStateProperties.SPELEOTHEM_THICKNESS) {
            return true;
        }
        if (block instanceof CrossCollisionBlock) {
            return CrossCollisionBlock.PROPERTY_BY_DIRECTION.containsValue(property);
        }
        if (block instanceof WallBlock) {
            return property == WallBlock.UP || WallBlock.PROPERTY_BY_DIRECTION.containsValue(property);
        }
        if (block instanceof RedStoneWireBlock) {
            return RedStoneWireBlock.PROPERTY_BY_DIRECTION.containsValue(property);
        }
        if (block instanceof StairBlock) {
            return property == StairBlock.SHAPE;
        }
        if (block instanceof HugeMushroomBlock) {
            return property == HugeMushroomBlock.NORTH || property == HugeMushroomBlock.EAST || property == HugeMushroomBlock.SOUTH
                    || property == HugeMushroomBlock.WEST || property == HugeMushroomBlock.UP || property == HugeMushroomBlock.DOWN;
        }
        if (block instanceof FenceGateBlock) {
            return property == FenceGateBlock.IN_WALL;
        }
        if (block instanceof NoteBlock) {
            return property == NoteBlock.INSTRUMENT;
        }
        if (block instanceof ScaffoldingBlock) {
            return property == ScaffoldingBlock.BOTTOM;
        }
        return false;
    }

    /**
     * Compares two states of the builder's point of view.
     *
     * @param orientationProps properties ignored if {@code ignoreDirection} is set
     * @param ignoredNames     property names ignored for every block ({@code buildIgnoreProperties})
     * @param ignoreDynamic    ignore {@link #isDynamic dynamic} properties
     * @param ignoreDerived    ignore {@link #isDerived neighbour dependent} properties
     */
    public static boolean sameState(BlockState first, BlockState second, boolean ignoreDirection, Set<Property<?>> orientationProps,
                                    Collection<String> ignoredNames, boolean ignoreDynamic, boolean ignoreDerived) {
        if (first.getBlock() != second.getBlock()) {
            return false;
        }
        if (first == second) {
            return true;
        }
        if (!ignoreDirection && ignoredNames.isEmpty() && !ignoreDynamic && !ignoreDerived) {
            return false; // same block, different state, nothing ignored
        }
        Block block = first.getBlock();
        for (Property<?> prop : first.getProperties()) {
            if (first.getValue(prop) != second.getValue(prop)
                    && !(ignoreDirection && orientationProps.contains(prop))
                    && !(ignoreDynamic && isDynamic(prop))
                    && !(ignoreDerived && isDerived(block, prop))
                    && !ignoredNames.contains(prop.getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Decides whether {@code updated}, the desired state after {@code Block.updateFromNeighbourShapes} at the
     * target position, may stand in for the desired state. Only same-block results count: {@code updateShape} turns
     * blocks that can't survive (half doors, torches without wall) into air, which must not make air "correct".
     */
    public static boolean usableNeighbourUpdate(BlockState current, BlockState desired, BlockState updated) {
        return current.getBlock() == desired.getBlock() && updated.getBlock() == desired.getBlock() && updated != desired;
    }
}
