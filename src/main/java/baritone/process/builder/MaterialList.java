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

import baritone.api.schematic.ISchematic;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Counts the items a schematic needs and compares them with what the player carries. All orientations and other
 * states of a block are merged into its item.
 */
public final class MaterialList {

    /**
     * Properties whose value is the number of items in the block (candles, sea pickles, ...).
     */
    private static final IntegerProperty[] AMOUNT_PROPERTIES = {
            BlockStateProperties.CANDLES, BlockStateProperties.PICKLES, BlockStateProperties.EGGS,
            BlockStateProperties.LAYERS, BlockStateProperties.FLOWER_AMOUNT, BlockStateProperties.SEGMENT_AMOUNT
    };

    public static final class Entry {
        public final Item item;
        public final long needed;
        public final long inInventory;
        public final long inShulkers;

        Entry(Item item, long needed, long inInventory, long inShulkers) {
            this.item = item;
            this.needed = needed;
            this.inInventory = inInventory;
            this.inShulkers = inShulkers;
        }

        public long missing() {
            return Math.max(0, needed - inInventory - inShulkers);
        }
    }

    private final Object2LongOpenHashMap<Item> needed = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<Block> withoutItem = new Object2LongOpenHashMap<>();

    /**
     * @return how many items placing {@code state} takes: 0 for air and the second half of doors, beds and tall
     * plants, 2 for double slabs, the amount for candles, sea pickles, turtle eggs, snow layers, petals and leaf litter
     */
    public static int itemCount(BlockState state) {
        if (state.isAir()) {
            return 0;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return 0;
        }
        if (state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return 0;
        }
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) {
            return 2;
        }
        for (IntegerProperty amount : AMOUNT_PROPERTIES) {
            if (state.hasProperty(amount)) {
                return state.getValue(amount);
            }
        }
        return 1;
    }

    /**
     * Adds what placing {@code state} once takes.
     */
    public void add(BlockState state) {
        int count = itemCount(state);
        if (count == 0) {
            return;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            // water, lava, fire, potted plants, ...
            withoutItem.addTo(state.getBlock(), 1);
        } else {
            needed.addTo(item, count);
        }
    }

    /**
     * Adds every block of {@code schematic}.
     */
    public void addAll(ISchematic schematic) {
        for (int x = 0; x < schematic.widthX(); x++) {
            for (int y = 0; y < schematic.heightY(); y++) {
                for (int z = 0; z < schematic.lengthZ(); z++) {
                    if (schematic.inSchematic(x, y, z, null)) {
                        add(schematic.desiredState(x, y, z, null, Collections.emptyList()));
                    }
                }
            }
        }
    }

    public Object2LongMap<Item> getNeeded() {
        return needed;
    }

    /**
     * @return blocks in the schematic that have no item (the builder can't place them)
     */
    public Object2LongMap<Block> getWithoutItem() {
        return withoutItem;
    }

    public long totalNeeded() {
        long total = 0;
        for (long n : needed.values()) {
            total += n;
        }
        return total;
    }

    /**
     * Counts the stacks, including the contents of shulker boxes (and other containers) among them.
     */
    public static void countItems(Iterable<ItemStack> stacks, Object2LongOpenHashMap<Item> direct, Object2LongOpenHashMap<Item> inContainers) {
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            direct.addTo(stack.getItem(), stack.getCount());
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents != null) {
                contents.nonEmptyItemCopyStream().forEach(inner -> inContainers.addTo(inner.getItem(), inner.getCount()));
            }
        }
    }

    /**
     * @return one entry per needed item, most missing first
     */
    public List<Entry> compare(Object2LongMap<Item> inventory, Object2LongMap<Item> inShulkers) {
        List<Entry> entries = new ArrayList<>();
        for (Object2LongMap.Entry<Item> e : needed.object2LongEntrySet()) {
            Item item = e.getKey();
            entries.add(new Entry(item, e.getLongValue(), inventory.getLong(item), inShulkers.getLong(item)));
        }
        entries.sort(Comparator.comparingLong(Entry::missing).reversed()
                .thenComparing(Comparator.comparingLong((Entry entry) -> entry.needed).reversed())
                .thenComparing(entry -> entry.item.getDescriptionId()));
        return entries;
    }
}
