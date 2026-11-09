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

import baritone.Baritone;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Eats when hungry (or hurt and not full): pauses pathing at a safe moment, holds the best safe food until it is
 * eaten, then lets the paused process continue.
 * <p>
 * Eating is started with {@code useItem} directly, which never interacts with the block in the crosshair, and kept
 * going by cancelling {@code releaseUsingItem} while {@link #isHoldingUse()} (see {@code MixinPlayerController}),
 * because Minecraft releases the item every tick the use key isn't held.
 */
public final class AutoEatProcess extends BaritoneProcessHelper {

    private static final int MAX_EATING_TICKS = 100; // 32 for normal food, dried kelp is faster
    private static final float CRITICAL_HEALTH = 6;

    private static volatile boolean holdUse;

    private boolean eating;
    private int eatingTicks;
    private int previousSlot = -1;
    private boolean eatRequested;
    private boolean warnedNoFood;

    public AutoEatProcess(Baritone baritone) {
        super(baritone);
    }

    /**
     * @return whether Baritone is eating and Minecraft must not release the used item
     */
    public static boolean isHoldingUse() {
        return holdUse;
    }

    /**
     * Eat once as soon as possible, even below the thresholds and with {@code autoEat} off.
     */
    public void eatNow() {
        eatRequested = true;
        warnedNoFood = false;
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        if (eating) {
            return true;
        }
        if (!Baritone.settings().autoEat.value && !eatRequested) {
            return false;
        }
        return wantsToEat();
    }

    private boolean wantsToEat() {
        LocalPlayer p = ctx.player();
        if (p.getAbilities().invulnerable) {
            return false; // creative and spectator
        }
        int food = p.getFoodData().getFoodLevel();
        if (food >= 20) {
            eatRequested = false;
            return false;
        }
        return eatRequested
                || food < Baritone.settings().autoEatHungerThreshold.value
                || p.getHealth() < Baritone.settings().autoEatHealthThreshold.value;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        LocalPlayer p = ctx.player();
        if (eating) {
            eatingTicks++;
            if (!p.isUsingItem() || eatingTicks > MAX_EATING_TICKS) {
                finish();
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        if (!canStartEating(isSafeToCancel)) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        FoodChoice.Candidate best = FoodChoice.best(collectFood(), 20 - p.getFoodData().getFoodLevel());
        if (best == null) {
            if (!warnedNoFood) {
                logDirect("Hungry, but there is no food that is safe to eat");
                warnedNoFood = true;
            }
            eatRequested = false;
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        warnedNoFood = false;
        if (best.slot >= 9) {
            // main inventory (only with allowInventory), move it to the hotbar first and wait for it
            baritone.getInventoryBehavior().attemptToPutOnHotbar(best.slot, slot -> false);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        InteractionHand hand = best.slot == FoodChoice.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (hand == InteractionHand.MAIN_HAND && p.getInventory().getSelectedSlot() != best.slot) {
            previousSlot = p.getInventory().getSelectedSlot();
            p.getInventory().setSelectedSlot(best.slot);
        }
        holdUse = true;
        InteractionResult result = ctx.playerController().processRightClick(p, ctx.world(), hand);
        if (!result.consumesAction() || !p.isUsingItem()) {
            logDebug("Could not start eating: " + result);
            finish();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        eating = true;
        eatingTicks = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private boolean canStartEating(boolean isSafeToCancel) {
        LocalPlayer p = ctx.player();
        if (p.containerMenu != p.inventoryMenu || p.isFallFlying()) {
            return false;
        }
        boolean critical = p.getHealth() <= CRITICAL_HEALTH;
        if (critical) {
            return true;
        }
        // not in the middle of a jump or bridge, not while swimming
        return isSafeToCancel && p.onGround() && !p.isInWater() && !p.isInLava();
    }

    private List<FoodChoice.Candidate> collectFood() {
        List<FoodChoice.Candidate> candidates = new ArrayList<>();
        List<Item> blacklist = Baritone.settings().autoEatBlacklist.value;
        Inventory inv = ctx.player().getInventory();
        addCandidate(candidates, inv.getItem(Inventory.SLOT_OFFHAND), FoodChoice.OFFHAND, blacklist);
        int slots = Baritone.settings().allowInventory.value ? 36 : 9;
        for (int i = 0; i < slots; i++) {
            addCandidate(candidates, inv.getNonEquipmentItems().get(i), i, blacklist);
        }
        return candidates;
    }

    private static void addCandidate(List<FoodChoice.Candidate> candidates, ItemStack stack, int slot, List<Item> blacklist) {
        FoodProperties food = FoodChoice.safeFood(stack, blacklist);
        if (food != null) {
            candidates.add(new FoodChoice.Candidate(slot, food.nutrition(), food.saturation()));
        }
    }

    private void finish() {
        holdUse = false;
        LocalPlayer p = ctx.player();
        if (p != null) {
            if (p.isUsingItem() && ctx.minecraft().gameMode != null) {
                ctx.minecraft().gameMode.releaseUsingItem(p);
            }
            if (previousSlot >= 0) {
                p.getInventory().setSelectedSlot(previousSlot);
            }
            if (p.getFoodData().getFoodLevel() >= 20) {
                eatRequested = false;
            }
        }
        previousSlot = -1;
        eating = false;
    }

    @Override
    public void onLostControl() {
        if (eating || holdUse) {
            finish();
        }
    }

    @Override
    public String displayName0() {
        return "Eating";
    }

    @Override
    public double priority() {
        // below the inventory pauser (5.1), which has to pause first when food is moved to the hotbar
        return 5.05;
    }

    @Override
    public boolean isTemporary() {
        return true;
    }
}
