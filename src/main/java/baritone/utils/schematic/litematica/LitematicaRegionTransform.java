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

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps between the block container of one Litematica sub-region and the world, exactly like Litematica does it
 * when it pastes a placement ({@code SchematicPlacingUtils.placeBlocksWithinChunk}, checked against the sources of
 * Litematica 0.28.x for 26.2 and 0.29.x for 26.3).
 * <p>
 * Litematica places container index {@code i} at
 * {@code origin + T_main(regionPos) + T_sub(T_main(relStart + i))}, where {@code T} mirrors and then rotates
 * around the y axis and {@code relStart} is the offset of the region's minimum corner from its origin corner.
 * The combined linear part is a signed permutation of x and z, so the inverse is its transpose.
 */
public final class LitematicaRegionTransform {

    // container index -> position relative to the box minimum: rel = base + L(index)
    private final int baseX;
    private final int baseZ;
    // L(1, 0, 0) = (a, 0, b), L(0, 0, 1) = (c, 0, d)
    private final int a;
    private final int b;
    private final int c;
    private final int d;

    /**
     * Minimum corner of the transformed sub-region, relative to the placement origin.
     */
    public final int minX;
    public final int minY;
    public final int minZ;
    public final int widthX;
    public final int heightY;
    public final int lengthZ;

    private final Mirror stateMirrorMain;
    private final Mirror stateMirrorSub;
    private final Rotation stateRotation;

    /**
     * @param regionPos    sub-region origin relative to the placement origin, untransformed
     *                     ({@code SubRegionPlacement.getPos()})
     * @param areaSize     signed sub-region size ({@code LitematicaSchematic.getAreaSize(name)})
     * @param mainMirror   mirror of the whole placement
     * @param mainRotation rotation of the whole placement
     * @param subMirror    mirror of the sub-region
     * @param subRotation  rotation of the sub-region
     */
    public LitematicaRegionTransform(Vec3i regionPos, Vec3i areaSize, Mirror mainMirror, Rotation mainRotation, Mirror subMirror, Rotation subRotation) {
        if (areaSize.getX() == 0 || areaSize.getY() == 0 || areaSize.getZ() == 0) {
            throw new IllegalArgumentException("empty sub-region " + areaSize);
        }
        Vec3i ex = linear(new Vec3i(1, 0, 0), mainMirror, mainRotation, subMirror, subRotation);
        Vec3i ez = linear(new Vec3i(0, 0, 1), mainMirror, mainRotation, subMirror, subRotation);
        this.a = ex.getX();
        this.b = ex.getZ();
        this.c = ez.getX();
        this.d = ez.getZ();

        int sizeX = Math.abs(areaSize.getX());
        int sizeY = Math.abs(areaSize.getY());
        int sizeZ = Math.abs(areaSize.getZ());
        // offset of the minimum corner from the origin corner, untransformed
        int relStartX = areaSize.getX() >= 0 ? 0 : areaSize.getX() + 1;
        int relStartY = areaSize.getY() >= 0 ? 0 : areaSize.getY() + 1;
        int relStartZ = areaSize.getZ() >= 0 ? 0 : areaSize.getZ() + 1;

        Vec3i regionPosTransformed = transform(regionPos, mainMirror, mainRotation);
        int baseRelX = regionPosTransformed.getX() + a * relStartX + c * relStartZ;
        int baseRelY = regionPosTransformed.getY() + relStartY;
        int baseRelZ = regionPosTransformed.getZ() + b * relStartX + d * relStartZ;

        // L(size - 1), the opposite corner of the box
        int extX = a * (sizeX - 1) + c * (sizeZ - 1);
        int extZ = b * (sizeX - 1) + d * (sizeZ - 1);

        this.minX = baseRelX + Math.min(extX, 0);
        this.minY = baseRelY;
        this.minZ = baseRelZ + Math.min(extZ, 0);
        this.widthX = Math.abs(extX) + 1;
        this.heightY = sizeY;
        this.lengthZ = Math.abs(extZ) + 1;
        this.baseX = baseRelX - minX;
        this.baseZ = baseRelZ - minZ;

        // Litematica swaps the sub-region mirror for blockstates if the placement is rotated by 90 degrees
        Mirror stateSub = subMirror;
        if (stateSub != Mirror.NONE && (mainRotation == Rotation.CLOCKWISE_90 || mainRotation == Rotation.COUNTERCLOCKWISE_90)) {
            stateSub = stateSub == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.FRONT_BACK;
        }
        this.stateMirrorMain = mainMirror;
        this.stateMirrorSub = stateSub;
        this.stateRotation = mainRotation.getRotated(subRotation);
    }

    /**
     * @param x x relative to the box minimum, {@code 0 <= x < widthX}
     * @param z z relative to the box minimum, {@code 0 <= z < lengthZ}
     * @return x index in the Litematica container
     */
    public int containerX(int x, int z) {
        return a * (x - baseX) + b * (z - baseZ);
    }

    /**
     * @param x x relative to the box minimum, {@code 0 <= x < widthX}
     * @param z z relative to the box minimum, {@code 0 <= z < lengthZ}
     * @return z index in the Litematica container
     */
    public int containerZ(int x, int z) {
        return c * (x - baseX) + d * (z - baseZ);
    }

    /**
     * @return whether {@link #transformState(BlockState)} changes anything
     */
    public boolean transformsStates() {
        return stateMirrorMain != Mirror.NONE || stateMirrorSub != Mirror.NONE || stateRotation != Rotation.NONE;
    }

    /**
     * Applies mirror and rotation to a state in the same order as Litematica.
     */
    public BlockState transformState(BlockState state) {
        if (stateMirrorMain != Mirror.NONE) {
            state = state.mirror(stateMirrorMain);
        }
        if (stateMirrorSub != Mirror.NONE) {
            state = state.mirror(stateMirrorSub);
        }
        if (stateRotation != Rotation.NONE) {
            state = state.rotate(stateRotation);
        }
        return state;
    }

    private static Vec3i linear(Vec3i in, Mirror mainMirror, Rotation mainRotation, Mirror subMirror, Rotation subRotation) {
        return transform(transform(in, mainMirror, mainRotation), subMirror, subRotation);
    }

    /**
     * Same as Litematica's {@code PositionUtils.getTransformedBlockPos}: mirror first, then rotate.
     */
    static Vec3i transform(Vec3i in, Mirror mirror, Rotation rotation) {
        int x = in.getX();
        int y = in.getY();
        int z = in.getZ();
        if (mirror == Mirror.LEFT_RIGHT) {
            z = -z;
        } else if (mirror == Mirror.FRONT_BACK) {
            x = -x;
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
}
