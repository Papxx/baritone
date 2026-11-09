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

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collects blocks and property values of a schematic file that this Minecraft version doesn't know, e.g. blocks
 * from a newer version. Unknown blocks are left out of the schematic (the positions are not touched) instead of
 * silently becoming air, unknown property values keep the block's default value.
 */
public final class UnknownBlocks {

    /**
     * Implemented by schematic formats that report unknown blocks.
     */
    public interface Source {
        UnknownBlocks getUnknownBlocks();
    }

    private final Map<String, Long> blocks = new HashMap<>();
    private final Set<String> properties = new LinkedHashSet<>();

    /**
     * @return the block registered under {@code id}, or {@code null} if there is none
     */
    public static Block lookup(Identifier id) {
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.get(id).map(Holder.Reference::value).orElse(null);
    }

    /**
     * Records {@code count} positions with the unknown block {@code name}.
     */
    public void addBlock(String name, long count) {
        blocks.merge(name, count, Long::sum);
    }

    /**
     * Records a property value that couldn't be applied, e.g. {@code minecraft:oak_stairs[shape=new_shape]}.
     */
    public void addProperty(String description) {
        properties.add(description);
    }

    public boolean isEmpty() {
        return blocks.isEmpty() && properties.isEmpty();
    }

    public Map<String, Long> getBlocks() {
        return blocks;
    }

    public Set<String> getProperties() {
        return properties;
    }

    /**
     * @param max maximum number of entries to list per category
     * @return something like {@code 2 unknown block types (125 blocks): minecraft:poplar_planks x120, ...}
     */
    public String summary(int max) {
        StringBuilder sb = new StringBuilder();
        if (!blocks.isEmpty()) {
            long total = blocks.values().stream().mapToLong(Long::longValue).sum();
            sb.append(blocks.size()).append(" unknown block type").append(blocks.size() == 1 ? "" : "s")
                    .append(" (").append(total).append(" block").append(total == 1 ? "" : "s").append("): ");
            sb.append(blocks.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()).thenComparing(Map.Entry.comparingByKey()))
                    .limit(max)
                    .map(e -> e.getKey() + " x" + e.getValue())
                    .collect(Collectors.joining(", ")));
            if (blocks.size() > max) {
                sb.append(", ...");
            }
        }
        if (!properties.isEmpty()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(properties.size()).append(" unknown property value").append(properties.size() == 1 ? "" : "s")
                    .append(" (default used): ");
            sb.append(properties.stream().limit(max).collect(Collectors.joining(", ")));
            if (properties.size() > max) {
                sb.append(", ...");
            }
        }
        return sb.toString();
    }
}
