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

package baritone.utils.pathing;

import baritone.Baritone;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.BlockUtils;
import baritone.api.utils.IPlayerContext;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Avoidance {

    private final int centerX;
    private final int centerY;
    private final int centerZ;
    private final double coefficient;
    private final int radius;
    private final int radiusSq;

    public Avoidance(BlockPos center, double coefficient, int radius) {
        this(center.getX(), center.getY(), center.getZ(), coefficient, radius);
    }

    public Avoidance(int centerX, int centerY, int centerZ, double coefficient, int radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.coefficient = coefficient;
        this.radius = radius;
        this.radiusSq = radius * radius;
    }

    public double coefficient(int x, int y, int z) {
        int xDiff = x - centerX;
        int yDiff = y - centerY;
        int zDiff = z - centerZ;
        return xDiff * xDiff + yDiff * yDiff + zDiff * zDiff <= radiusSq ? coefficient : 1.0D;
    }

    public static List<Avoidance> create(IPlayerContext ctx) {
        if (!Baritone.settings().avoidance.value) {
            return Collections.emptyList();
        }
        List<Avoidance> res = new ArrayList<>();
        double mobSpawnerCoeff = Baritone.settings().mobSpawnerAvoidanceCoefficient.value;
        double mobCoeff = Baritone.settings().mobAvoidanceCoefficient.value;
        if (mobSpawnerCoeff != 1.0D) {
            // the cache stores names from BlockUtils.blockToString ("spawner" since 1.13, not "mob_spawner")
            ctx.worldData().getCachedWorld().getLocationsOf(BlockUtils.blockToString(Blocks.SPAWNER), 1, ctx.playerFeet().x, ctx.playerFeet().z, 2)
                    .forEach(mobspawner -> res.add(new Avoidance(mobspawner, mobSpawnerCoeff, Baritone.settings().mobSpawnerAvoidanceRadius.value)));
        }
        if (mobCoeff != 1.0D) {
            @SuppressWarnings("deprecation")
            boolean dark = ctx.player().getLightLevelDependentMagicValue() < 0.5;
            ctx.entitiesStream()
                    .filter(entity -> isDangerous(entity, dark))
                    .forEach(entity -> res.add(new Avoidance(entity.blockPosition(), mobCoeff, Baritone.settings().mobAvoidanceRadius.value)));
        }
        return res;
    }

    /**
     * Hostile mobs, neutral mobs only while they attack, and sulfur cubes that burn (magma) or are about to explode.
     * Passive mobs like cows and sheep don't count.
     *
     * @param dark whether the player is in the dark (spiders are only hostile in the dark)
     */
    public static boolean isDangerous(Entity entity, boolean dark) {
        if (!(entity instanceof Mob)) {
            return false;
        }
        Mob mob = (Mob) entity;
        if (entity instanceof SulfurCube) {
            return isDangerousSulfurCube((SulfurCube) entity);
        }
        if (entity instanceof NeutralMob) {
            // the anger timer of some mobs (zombified piglins) isn't sent to the client, but attacking is
            if (entity instanceof EnderMan && ((EnderMan) entity).isCreepy()) {
                return true;
            }
            if (entity instanceof ZombifiedPiglin && mob.getLastHurtByMob() != null) {
                return true;
            }
            return ((NeutralMob) entity).isAngry() || mob.isAggressive();
        }
        if (!(entity instanceof Enemy)) {
            return false;
        }
        return !(entity instanceof Spider) || dark || mob.isAggressive();
    }

    private static boolean isDangerousSulfurCube(SulfurCube cube) {
        if (cube.isPrimed()) {
            return true;
        }
        ItemStack body = cube.getItemBySlot(EquipmentSlot.BODY);
        if (body.isEmpty()) {
            return false;
        }
        try {
            // the archetypes are a synchronized registry, "hot" (magma) hurts on contact, "explosive" (tnt) explodes
            return cube.matchingArchetypes(body).stream().anyMatch(a -> a.contactDamage().isPresent() || a.explosion().isPresent());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void applySpherical(Long2DoubleOpenHashMap map) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z <= radius * radius) {
                        long hash = BetterBlockPos.longHash(centerX + x, centerY + y, centerZ + z);
                        map.put(hash, map.get(hash) * coefficient);
                    }
                }
            }
        }
    }
}
