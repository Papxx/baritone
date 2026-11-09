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

import baritone.api.schematic.CompositeSchematic;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.utils.Pair;
import fi.dy.masa.litematica.Litematica;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.schematic.container.LitematicaBlockStateContainer;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SubRegionPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Helper class that provides access or processes data related to Litmatica schematics.
 * <p>
 * All Litematica classes are compiled against stubs, so every call into this class can throw a
 * {@link LinkageError} if the installed Litematica version changed its API.
 *
 * @author rycbar
 * @since 28.09.2022
 */
public final class LitematicaHelper {

    /**
     * @return if Litmatica is installed.
     */
    public static boolean isLitematicaPresent() {
        try {
            Class.forName(Litematica.class.getName());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            return false;
        }
    }

    /**
     * @return the number of schematic placements
     */
    public static int getPlacementCount() {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().size();
    }

    /**
     * @return if {@code i} is a valid placement index
     */
    public static boolean hasLoadedSchematic(int i) {
        return 0 <= i && i < getPlacementCount();
    }

    private static SchematicPlacement getPlacement(int i) {
        return DataManager.getSchematicPlacementManager().getAllSchematicsPlacements().get(i);
    }

    /**
     * Reads the placement straight from the schematic data (all enabled sub-regions, with the placement's and
     * the sub-regions' rotation and mirror applied). Unlike Litematica's preview world this also covers chunks
     * the client hasn't loaded.
     *
     * @param i index of the Schematic in the schematic placement list.
     * @return The transformed schematic and the position of its minimum corner, or {@code null} if the placement
     * has no readable enabled sub-region
     */
    public static Pair<IStaticSchematic, Vec3i> getSchematic(int i) {
        SchematicPlacement placement = getPlacement(i);
        LitematicaSchematic schematic = placement.getSchematic();
        List<LitematicaRegionSchematic> regions = new ArrayList<>();
        for (Map.Entry<String, SubRegionPlacement> entry : placement.getEnabledRelativeSubRegionPlacements().entrySet()) {
            String name = entry.getKey();
            SubRegionPlacement subPlacement = entry.getValue();
            LitematicaBlockStateContainer container = schematic.getSubRegionContainer(name);
            BlockPos areaSize = schematic.getAreaSize(name);
            if (container == null || areaSize == null) {
                continue;
            }
            Vec3i containerSize = container.getSize();
            if (containerSize.getX() != Math.abs(areaSize.getX()) || containerSize.getY() != Math.abs(areaSize.getY()) || containerSize.getZ() != Math.abs(areaSize.getZ())) {
                throw new IllegalStateException("Sub-region '" + name + "' has size " + areaSize + " but its block data has size " + containerSize);
            }
            LitematicaRegionTransform transform = new LitematicaRegionTransform(subPlacement.getPos(), areaSize, placement.getMirror(), placement.getRotation(), subPlacement.getMirror(), subPlacement.getRotation());
            LitematicaRegionSchematic region = new LitematicaRegionSchematic(container::get, transform);
            // read one block now, so an incompatible Litematica fails here and not later in the pathing thread
            region.getDirect(0, 0, 0);
            regions.add(region);
        }
        return assemble(placement.getName(), placement.getOrigin(), regions);
    }

    /**
     * Combines the sub-regions of one placement.
     *
     * @param origin  the placement origin
     * @param regions the transformed sub-regions
     * @return the combined schematic and the world position of its minimum corner, or {@code null} if there are no
     * regions
     */
    static Pair<IStaticSchematic, Vec3i> assemble(String name, Vec3i origin, List<LitematicaRegionSchematic> regions) {
        if (regions.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (LitematicaRegionSchematic region : regions) {
            minX = Math.min(minX, region.transform().minX);
            minY = Math.min(minY, region.transform().minY);
            minZ = Math.min(minZ, region.transform().minZ);
        }
        LitematicaPlacementSchematic composite = new LitematicaPlacementSchematic(name);
        for (LitematicaRegionSchematic region : regions) {
            LitematicaRegionTransform t = region.transform();
            composite.put(region, t.minX - minX, t.minY - minY, t.minZ - minZ);
        }
        return new Pair<>(composite, origin.offset(minX, minY, minZ));
    }

    private static class LitematicaPlacementSchematic extends CompositeSchematic implements IStaticSchematic {
        private final String name;

        public LitematicaPlacementSchematic(String name) {
            super(0, 0, 0);
            this.name = name;
        }

        @Override
        public BlockState getDirect(int x, int y, int z) {
            if (inSchematic(x, y, z, null)) {
                return desiredState(x, y, z, null, Collections.emptyList());
            }
            return null;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
