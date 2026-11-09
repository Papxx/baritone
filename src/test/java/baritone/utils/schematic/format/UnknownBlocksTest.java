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

package baritone.utils.schematic.format;

import baritone.utils.schematic.format.defaults.LitematicaSchematic;
import baritone.utils.schematic.format.defaults.SpongeSchematic;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UnknownBlocksTest {

    // a 26.3 block, unknown in 26.2
    private static final String UNKNOWN = "minecraft:poplar_planks";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static CompoundTag sponge() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Version", 2);
        nbt.putInt("Width", 4);
        nbt.putInt("Height", 1);
        nbt.putInt("Length", 1);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:stone", 0);
        palette.putInt(UNKNOWN, 1);
        palette.putInt("minecraft:oak_stairs[facing=sideways]", 2);
        palette.putInt("minecraft:structure_void", 3);
        nbt.put("Palette", palette);
        nbt.putByteArray("BlockData", new byte[]{0, 1, 2, 3});
        return nbt;
    }

    @Test
    public void spongeLeavesOutUnknownBlocks() {
        SpongeSchematic schematic = new SpongeSchematic(sponge());
        assertTrue(schematic.inSchematic(0, 0, 0, null));
        assertEquals(Blocks.STONE.defaultBlockState(), schematic.desiredState(0, 0, 0, null, Collections.emptyList()));
        // the unknown block is not part of the schematic instead of being air
        assertFalse(schematic.inSchematic(1, 0, 0, null));
        assertNull(schematic.getDirect(1, 0, 0));
        // unknown property value keeps the default
        assertEquals(Direction.NORTH, schematic.getDirect(2, 0, 0).getValue(StairBlock.FACING));
        // structure void means "don't care"
        assertFalse(schematic.inSchematic(3, 0, 0, null));

        UnknownBlocks unknown = schematic.getUnknownBlocks();
        assertEquals(Collections.singletonMap(UNKNOWN, 1L), unknown.getBlocks());
        assertEquals(Collections.singleton("minecraft:oak_stairs[facing=sideways]"), unknown.getProperties());
        assertEquals("1 unknown block type (1 block): minecraft:poplar_planks x1\n"
                + "1 unknown property value (default used): minecraft:oak_stairs[facing=sideways]", unknown.summary(5));
    }

    private static CompoundTag region(int x, int y, int z, int sizeX, int sizeY, int sizeZ, String[] palette, long packed) {
        CompoundTag region = new CompoundTag();
        CompoundTag pos = new CompoundTag();
        pos.putInt("x", x);
        pos.putInt("y", y);
        pos.putInt("z", z);
        region.put("Position", pos);
        CompoundTag size = new CompoundTag();
        size.putInt("x", sizeX);
        size.putInt("y", sizeY);
        size.putInt("z", sizeZ);
        region.put("Size", size);
        ListTag paletteTag = new ListTag();
        for (String name : palette) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", name);
            paletteTag.add(entry);
        }
        region.put("BlockStatePalette", paletteTag);
        region.putLongArray("BlockStates", new long[]{packed});
        return region;
    }

    @Test
    public void litematicLeavesOutUnknownBlocks() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("Version", 7);
        CompoundTag regions = new CompoundTag();
        // 2 bits per entry, values 1, 2, 0, 2: stone, unknown, air, unknown
        regions.put("main", region(0, 0, 0, 4, 1, 1, new String[]{"minecraft:air", "minecraft:stone", UNKNOWN}, 1L | 2L << 2 | 0L << 4 | 2L << 6));
        // a second region with a gap at x = 4
        regions.put("second", region(5, 0, 0, 1, 1, 1, new String[]{"minecraft:air", "minecraft:structure_void"}, 1L));
        nbt.put("Regions", regions);

        LitematicaSchematic schematic = new LitematicaSchematic(nbt);
        assertEquals(6, schematic.widthX());
        assertEquals(Blocks.STONE.defaultBlockState(), schematic.getDirect(0, 0, 0));
        assertFalse(schematic.inSchematic(1, 0, 0, null));
        assertTrue(schematic.inSchematic(2, 0, 0, null));
        assertEquals(Blocks.AIR.defaultBlockState(), schematic.getDirect(2, 0, 0));
        assertFalse(schematic.inSchematic(3, 0, 0, null));
        // gap between the regions: used to throw IllegalStateException
        assertNull(schematic.getDirect(4, 0, 0));
        // structure void
        assertFalse(schematic.inSchematic(5, 0, 0, null));
        assertNull(schematic.getDirect(5, 0, 0));

        assertEquals(Collections.singletonMap(UNKNOWN, 2L), schematic.getUnknownBlocks().getBlocks());
        assertTrue(schematic.getUnknownBlocks().getProperties().isEmpty());
    }

    @Test
    public void knownBlocksReportNothing() {
        CompoundTag nbt = sponge();
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:stone", 0);
        palette.putInt("minecraft:dirt", 1);
        palette.putInt("minecraft:oak_stairs[facing=east]", 2);
        palette.putInt("minecraft:glass", 3);
        nbt.put("Palette", palette);
        SpongeSchematic schematic = new SpongeSchematic(nbt);
        assertTrue(schematic.getUnknownBlocks().isEmpty());
        assertEquals(Direction.EAST, schematic.getDirect(2, 0, 0).getValue(StairBlock.FACING));
    }
}
