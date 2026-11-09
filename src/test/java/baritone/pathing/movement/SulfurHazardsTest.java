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

package baritone.pathing.movement;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PotentSulfurBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.PotentSulfurState;
import org.junit.BeforeClass;
import org.junit.Test;

import static baritone.pathing.precompute.Ternary.NO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 26.2 sulfur caves: sulfur spikes (0.4) and geysers (0.5).
 */
public class SulfurHazardsTest {

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BlockState sulfur(PotentSulfurState state) {
        return Blocks.POTENT_SULFUR.defaultBlockState().setValue(PotentSulfurBlock.STATE, state);
    }

    @Test
    public void sulfurSpikesAreLikeDripstone() {
        assertEquals(NO, MovementHelper.canWalkThroughBlockState(Blocks.POINTED_DRIPSTONE.defaultBlockState()));
        assertEquals(NO, MovementHelper.canWalkThroughBlockState(Blocks.SULFUR_SPIKE.defaultBlockState()));
        assertFalse(MovementHelper.isBlockNormalCube(Blocks.SULFUR_SPIKE.defaultBlockState()));
    }

    @Test
    public void onlyEruptingStatesAreGeysers() {
        assertFalse(MovementHelper.isGeyser(sulfur(PotentSulfurState.DRY)));
        assertFalse(MovementHelper.isGeyser(sulfur(PotentSulfurState.WET)));
        assertTrue(MovementHelper.isGeyser(sulfur(PotentSulfurState.DORMANT)));
        assertTrue(MovementHelper.isGeyser(sulfur(PotentSulfurState.ERUPTING)));
        assertTrue(MovementHelper.isGeyser(sulfur(PotentSulfurState.CONTINUOUS)));
        assertFalse(MovementHelper.isGeyser(Blocks.STONE.defaultBlockState()));
    }

    /**
     * @param column the states from the position downwards, column[0] = 1 below
     */
    private static boolean inColumn(BlockState... column) {
        return MovementHelper.inGeyserColumn(dy -> dy - 1 < column.length ? column[dy - 1] : null);
    }

    @Test
    public void waterColumnAboveGeyser() {
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState geyser = sulfur(PotentSulfurState.CONTINUOUS);
        assertTrue(inColumn(geyser));
        assertTrue(inColumn(water, water, water, geyser));
        // more water than a geyser allows can't be above one
        assertFalse(inColumn(water, water, water, water, geyser));
        // a block in between
        assertFalse(inColumn(water, Blocks.STONE.defaultBlockState(), geyser));
        assertFalse(inColumn(water, sulfur(PotentSulfurState.WET)));
        // bottom of the world
        assertFalse(inColumn(water, water));
    }
}
