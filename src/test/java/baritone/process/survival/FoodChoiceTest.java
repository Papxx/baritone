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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Values are the 26.2 food components: steak 8/12.8, bread 5/6.0, golden carrot 6/14.4, cookie 2/0.4, dried kelp 1/0.6.
 * Which food is safe ({@code safeFood}) needs bound item components and is checked in game.
 */
public class FoodChoiceTest {

    private static FoodChoice.Candidate steak(int slot) {
        return new FoodChoice.Candidate(slot, 8, 12.8f);
    }

    private static FoodChoice.Candidate bread(int slot) {
        return new FoodChoice.Candidate(slot, 5, 6.0f);
    }

    private static FoodChoice.Candidate carrot(int slot) {
        return new FoodChoice.Candidate(slot, 6, 14.4f);
    }

    private static FoodChoice.Candidate cookie(int slot) {
        return new FoodChoice.Candidate(slot, 2, 0.4f);
    }

    @Test
    public void nothingToEat() {
        assertNull(FoodChoice.best(Collections.emptyList(), 10));
    }

    @Test
    public void veryHungryPrefersFillingFood() {
        assertEquals(1, FoodChoice.best(Arrays.asList(cookie(0), steak(1), bread(2)), 12).slot);
        assertEquals(3, FoodChoice.best(Arrays.asList(cookie(0), bread(2), carrot(3)), 12).slot);
    }

    @Test
    public void slightlyHungryAvoidsWaste() {
        // 3 missing: a steak would waste 5 points, a cookie wastes nothing but gives little, bread wastes 2
        FoodChoice.Candidate best = FoodChoice.best(Arrays.asList(steak(0), cookie(1), bread(2)), 3);
        assertEquals(2, best.slot);
        // 1 missing: the cookie
        assertEquals(1, FoodChoice.best(Arrays.asList(steak(0), cookie(1), bread(2)), 1).slot);
    }

    @Test
    public void muchSaturationStillWinsWhenItPaysOff() {
        // eating because of low health with 2 missing points: the golden carrot heals much better than a cookie
        assertEquals(3, FoodChoice.best(Arrays.asList(cookie(1), carrot(3)), 2).slot);
    }

    @Test
    public void prefersFoodThatNeedsNoInventoryMove() {
        // same food in the main inventory (slot 20) and the offhand
        assertEquals(FoodChoice.OFFHAND, FoodChoice.best(Arrays.asList(bread(20), bread(FoodChoice.OFFHAND)), 10).slot);
        assertEquals(4, FoodChoice.best(Arrays.asList(bread(20), bread(4)), 10).slot);
        // but better food in the inventory still wins
        assertEquals(20, FoodChoice.best(Arrays.asList(steak(20), cookie(4)), 12).slot);
    }
}
