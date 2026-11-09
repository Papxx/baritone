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

import baritone.api.schematic.AbstractSchematic;
import baritone.api.schematic.IStaticSchematic;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read-only view on one sub-region of a Litematica placement. Reads straight from the schematic data instead of
 * Litematica's preview world, which only contains chunks the client has loaded (and fills them asynchronously).
 * Nothing is copied, so large placements don't need extra memory.
 * <p>
 * Structure void counts as "not part of the schematic", like Litematica does when pasting.
 * Safe to call from the pathing thread.
 */
public final class LitematicaRegionSchematic extends AbstractSchematic implements IStaticSchematic {

    /**
     * Reads the untransformed state at a container index. Never returns null.
     */
    @FunctionalInterface
    public interface BlockReader {
        BlockState get(int x, int y, int z);
    }

    private final BlockReader reader;
    private final LitematicaRegionTransform transform;
    private final ConcurrentHashMap<BlockState, BlockState> transformedStates;

    public LitematicaRegionSchematic(BlockReader reader, LitematicaRegionTransform transform) {
        super(transform.widthX, transform.heightY, transform.lengthZ);
        this.reader = reader;
        this.transform = transform;
        this.transformedStates = transform.transformsStates() ? new ConcurrentHashMap<>() : null;
    }

    public LitematicaRegionTransform transform() {
        return transform;
    }

    private BlockState raw(int x, int y, int z) {
        return reader.get(transform.containerX(x, z), y, transform.containerZ(x, z));
    }

    private BlockState transformed(BlockState state) {
        if (transformedStates == null) {
            return state;
        }
        BlockState cached = transformedStates.get(state);
        if (cached == null) {
            cached = transform.transformState(state);
            transformedStates.put(state, cached);
        }
        return cached;
    }

    @Override
    public boolean inSchematic(int x, int y, int z, BlockState currentState) {
        return x >= 0 && x < widthX() && y >= 0 && y < heightY() && z >= 0 && z < lengthZ()
                && !raw(x, y, z).is(Blocks.STRUCTURE_VOID);
    }

    @Override
    public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
        return transformed(raw(x, y, z));
    }

    @Override
    public BlockState getDirect(int x, int y, int z) {
        BlockState state = raw(x, y, z);
        return state.is(Blocks.STRUCTURE_VOID) ? null : transformed(state);
    }
}
