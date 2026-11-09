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

import baritone.utils.schematic.StaticSchematic;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MaterialListTest {

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void itemCounts() {
        assertEquals(0, MaterialList.itemCount(Blocks.AIR.defaultBlockState()));
        assertEquals(1, MaterialList.itemCount(Blocks.STONE.defaultBlockState()));
        assertEquals(1, MaterialList.itemCount(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)));
        assertEquals(0, MaterialList.itemCount(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        assertEquals(0, MaterialList.itemCount(Blocks.TALL_GRASS.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
        assertEquals(1, MaterialList.itemCount(Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT)));
        assertEquals(0, MaterialList.itemCount(Blocks.BED.red().defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD)));
        assertEquals(2, MaterialList.itemCount(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE)));
        assertEquals(1, MaterialList.itemCount(Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP)));
        assertEquals(3, MaterialList.itemCount(Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3)));
    }

    @Test
    public void statesMergeIntoItems() {
        MaterialList list = new MaterialList();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            list.add(Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facing));
        }
        list.add(Blocks.WALL_TORCH.defaultBlockState());
        list.add(Blocks.TORCH.defaultBlockState());
        list.add(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        list.add(Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        list.add(Blocks.WATER.defaultBlockState());
        list.add(Blocks.AIR.defaultBlockState());
        assertEquals(4, list.getNeeded().getLong(Items.OAK_STAIRS));
        assertEquals(2, list.getNeeded().getLong(Items.TORCH));
        assertEquals(1, list.getNeeded().getLong(Items.OAK_DOOR));
        assertEquals(3, list.getNeeded().size());
        assertEquals(7, list.totalNeeded());
        assertEquals(1, list.getWithoutItem().getLong(Blocks.WATER));
    }

    @Test
    public void addAllSkipsPositionsOutsideTheSchematic() {
        BlockState stone = Blocks.STONE.defaultBlockState();
        // null = not part of the schematic
        StaticSchematic schematic = new StaticSchematic(new BlockState[][][]{{{stone, null}}, {{stone, Blocks.AIR.defaultBlockState()}}});
        MaterialList list = new MaterialList();
        list.addAll(schematic);
        assertEquals(2, list.getNeeded().getLong(Items.STONE));
    }

    /**
     * Counting the stacks themselves ({@code countItems}, including shulker contents) needs item components, which
     * are only bound once the registries are loaded in game, so it is checked in game.
     */
    @Test
    public void comparesMostMissingFirst() {
        Object2LongOpenHashMap<Item> direct = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<Item> inShulkers = new Object2LongOpenHashMap<>();
        direct.put(Items.OAK_PLANKS, 20);
        inShulkers.put(Items.OAK_PLANKS, 74);
        inShulkers.put(Items.GLASS, 5);

        MaterialList list = new MaterialList();
        for (int i = 0; i < 100; i++) {
            list.add(Blocks.OAK_PLANKS.defaultBlockState());
        }
        for (int i = 0; i < 30; i++) {
            list.add(Blocks.GLASS.defaultBlockState());
        }
        list.add(Blocks.STONE.defaultBlockState());
        List<MaterialList.Entry> entries = list.compare(direct, inShulkers);
        assertEquals(Items.GLASS, entries.get(0).item);
        assertEquals(25, entries.get(0).missing());
        assertEquals(Items.OAK_PLANKS, entries.get(1).item);
        assertEquals(6, entries.get(1).missing());
        assertEquals(Items.STONE, entries.get(2).item);
        assertEquals(1, entries.get(2).missing());
    }
}
