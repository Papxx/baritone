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

package baritone.cache;

import baritone.api.cache.IWaypoint;
import baritone.api.cache.Waypoint;
import baritone.api.utils.BetterBlockPos;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class WaypointCollectionTest {

    private static final long WAYPOINT_MAGIC_VALUE = 121977993584L;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void waypointsSurviveReload() {
        Path dir = folder.getRoot().toPath().resolve("waypoints");
        WaypointCollection waypoints = new WaypointCollection(dir);
        Waypoint home = new Waypoint("home", IWaypoint.Tag.HOME, new BetterBlockPos(1, 64, -3), 1234L);
        Waypoint death = new Waypoint("death", IWaypoint.Tag.DEATH, new BetterBlockPos(-100, -60, 5000), 5678L);
        waypoints.addWaypoint(home);
        waypoints.addWaypoint(death);

        WaypointCollection reloaded = new WaypointCollection(dir);
        assertEquals(Set.of(home), reloaded.getByTag(IWaypoint.Tag.HOME));
        assertEquals(Set.of(death), reloaded.getByTag(IWaypoint.Tag.DEATH));
        assertEquals(1234L, reloaded.getByTag(IWaypoint.Tag.HOME).iterator().next().getCreationTimestamp());
        assertTrue(backups(dir, "home.mp4").isEmpty());
    }

    @Test
    public void corruptFileIsBackedUpAndReadableWaypointsAreKept() throws IOException {
        Path dir = folder.getRoot().toPath().resolve("waypoints");
        Files.createDirectories(dir);
        // the header promises two waypoints, but the file ends in the middle of the second one
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeLong(WAYPOINT_MAGIC_VALUE);
            out.writeLong(2);
            out.writeUTF("first");
            out.writeLong(1L);
            out.writeInt(1);
            out.writeInt(2);
            out.writeInt(3);
            out.writeUTF("second");
        }
        byte[] corrupt = bytes.toByteArray();
        Files.write(dir.resolve("home.mp4"), corrupt);

        WaypointCollection waypoints = new WaypointCollection(dir);
        Set<IWaypoint> home = waypoints.getByTag(IWaypoint.Tag.HOME);
        assertEquals(1, home.size());
        assertEquals("first", home.iterator().next().getName());

        // the broken file is kept unchanged, so nothing that couldn't be read is lost
        List<Path> backups = backups(dir, "home.mp4");
        assertEquals(1, backups.size());
        assertArrayEquals(corrupt, Files.readAllBytes(backups.get(0)));

        // and the readable part was saved again, so the next load is clean and doesn't create another backup
        WaypointCollection reloaded = new WaypointCollection(dir);
        assertEquals(home, reloaded.getByTag(IWaypoint.Tag.HOME));
        assertEquals(1, backups(dir, "home.mp4").size());
    }

    private static List<Path> backups(Path dir, String fileName) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith(fileName + ".corrupt-")).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
