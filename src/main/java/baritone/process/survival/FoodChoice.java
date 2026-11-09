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

package baritone.process.survival;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;

import java.util.Collection;
import java.util.List;

/**
 * Picks what to eat.
 */
public final class FoodChoice {

    /**
     * Slot number for the offhand, the others are inventory slots (0-8 hotbar, 9-35 main inventory).
     */
    public static final int OFFHAND = -1;

    public static final class Candidate {
        public final int slot;
        public final int nutrition;
        public final float saturation;

        public Candidate(int slot, int nutrition, float saturation) {
            this.slot = slot;
            this.nutrition = nutrition;
            this.saturation = saturation;
        }

        boolean inHand() {
            return slot == OFFHAND || (slot >= 0 && slot < 9);
        }
    }

    private FoodChoice() {}

    /**
     * Food that can be eaten without harm: it has no random teleport (chorus fruit), no harmful status effect, not
     * even a chance of one (pufferfish, spider eye, rotten flesh, raw chicken, poisonous potato), isn't suspicious
     * stew (unknown effect) and isn't on the blacklist (golden apples by default, too valuable).
     *
     * @return the food properties, or {@code null} if the stack shouldn't be eaten
     */
    public static FoodProperties safeFood(ItemStack stack, Collection<Item> blacklist) {
        if (stack.isEmpty() || blacklist.contains(stack.getItem())) {
            return null;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || stack.has(DataComponents.SUSPICIOUS_STEW_EFFECTS)) {
            return null;
        }
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) {
            return null;
        }
        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (effect instanceof TeleportRandomlyConsumeEffect) {
                return null;
            }
            if (effect instanceof ApplyStatusEffectsConsumeEffect) {
                for (MobEffectInstance instance : ((ApplyStatusEffectsConsumeEffect) effect).effects()) {
                    if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                        return null;
                    }
                }
            }
        }
        return food;
    }

    /**
     * Prefers food that fills the missing hunger without waste and gives much saturation, and food that is
     * already in a hand slot (no inventory move).
     *
     * @param missing missing hunger points (20 - food level)
     * @return the best candidate, or {@code null} if there is none
     */
    public static Candidate best(List<Candidate> candidates, int missing) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Candidate c : candidates) {
            if (c.nutrition <= 0) {
                continue;
            }
            int useful = Math.min(c.nutrition, missing);
            int wasted = Math.max(0, c.nutrition - missing);
            // wasted points weigh heavily, so a steak isn't eaten for 1 missing point, but much saturation still
            // wins when it pays off (golden carrot when hurt)
            double score = useful + c.saturation - 2.5 * wasted + (c.inHand() ? 0.5 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }
}
