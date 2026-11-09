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

import baritone.Baritone;
import baritone.api.cache.IWorldProvider;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Pair;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * @author Brady
 * @since 8/4/2018
 */
public class WorldProvider implements IWorldProvider {

    private static final Map<Path, WorldData> worldCache = new HashMap<>();

    /**
     * Worlds that nobody uses anymore and that are being written to disk before they get dropped from RAM.
     * Guarded by {@link #worldCache}.
     */
    private static final Map<Path, CompletableFuture<Void>> closingWorlds = new HashMap<>();

    private final Baritone baritone;
    private final IPlayerContext ctx;
    private WorldData currentWorld;

    /**
     * This lets us detect a broken load/unload hook.
     * @see #detectAndHandleBrokenLoading()
     */
    private Level mcWorld;

    public WorldProvider(Baritone baritone) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
    }

    @Override
    public final WorldData getCurrentWorld() {
        this.detectAndHandleBrokenLoading();
        return this.currentWorld;
    }

    /**
     * Called when a new world is initialized to discover the
     *
     * @param world The new world
     */
    public final void initWorld(Level world) {
        this.getSaveDirectories(world).ifPresent(dirs -> {
            final Path worldDir = dirs.first();
            final Path readmeDir = dirs.second();

            try {
                // lol wtf is this baritone folder in my minecraft save?
                // good thing we have a readme
                Files.createDirectories(readmeDir);
                Files.write(
                        readmeDir.resolve("readme.txt"),
                        "https://github.com/cabaletta/baritone\n".getBytes(StandardCharsets.US_ASCII)
                );
            } catch (IOException ignored) {}

            // We will actually store the world data in a subfolder: "DIM<id>"
            final Path worldDataDir = this.getWorldDataDirectory(worldDir, world);
            try {
                Files.createDirectories(worldDataDir);
            } catch (IOException ignored) {}

            System.out.println("Baritone world data dir: " + worldDataDir);
            this.currentWorld = acquireWorld(worldDataDir, () -> new WorldData(worldDataDir, world.dimensionType(), world.dimension()));
            this.mcWorld = ctx.world();
        });
    }

    public final void closeWorld() {
        WorldData world = this.currentWorld;
        this.currentWorld = null;
        this.mcWorld = null;
        if (world == null) {
            return;
        }
        synchronized (worldCache) {
            world.users--;
        }
        Baritone.getExecutor().execute(() -> {
            System.out.println("Started saving the world in a new thread");
            CompletableFuture<Void> closing = new CompletableFuture<>();
            boolean unload;
            synchronized (worldCache) {
                // Keep the world in RAM if it was joined again in the meantime, or if its cache only exists in RAM
                unload = world.users <= 0 && Baritone.settings().chunkCaching.value && worldCache.get(world.directory) == world;
                if (unload) {
                    worldCache.remove(world.directory);
                    closingWorlds.put(world.directory, closing);
                }
            }
            if (!unload) {
                world.cache.save();
                return;
            }
            try {
                world.cache.close(); // the next visit loads everything back from disk
            } finally {
                synchronized (worldCache) {
                    closingWorlds.remove(world.directory, closing);
                }
                closing.complete(null);
            }
        });
    }

    /**
     * Returns the world in this directory, creating it if it isn't in RAM. If it's still being written to disk after
     * the last visit, waits for that first, so that we don't load outdated data.
     */
    private static WorldData acquireWorld(Path worldDataDir, Supplier<WorldData> constructor) {
        for (boolean waited = false; ; waited = true) {
            CompletableFuture<Void> closing;
            synchronized (worldCache) {
                closing = closingWorlds.get(worldDataDir);
                if (closing == null || waited) {
                    WorldData data = worldCache.computeIfAbsent(worldDataDir, d -> constructor.get());
                    data.users++;
                    return data;
                }
            }
            try {
                closing.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                System.out.println("Previous visit of " + worldDataDir + " is still being saved, loading anyway");
            }
        }
    }

    private Path getWorldDataDirectory(Path parent, Level world) {
        Identifier dimId = world.dimension().identifier();
        int height = world.dimensionType().logicalHeight();
        return parent.resolve(dimId.getNamespace()).resolve(dimId.getPath() + "_" + height);
    }

    /**
     * @param world The world
     * @return An {@link Optional} containing the world's baritone dir and readme dir, or {@link Optional#empty()} if
     *         the world isn't valid for caching.
     */
    private Optional<Pair<Path, Path>> getSaveDirectories(Level world) {
        Path worldDir;
        Path readmeDir;

        // If there is an integrated server running (Aka Singleplayer) then do magic to find the world save file
        if (ctx.minecraft().hasSingleplayerServer()) {
            worldDir = ctx.minecraft().getSingleplayerServer().getWorldPath(LevelResource.ROOT);

            // Gets the "depth" of this directory relative to the game's run directory, 2 is the location of the world
            if (worldDir.relativize(ctx.minecraft().gameDirectory.toPath()).getNameCount() != 2) {
                // subdirectory of the main save directory for this world
                worldDir = worldDir.getParent();
            }

            worldDir = worldDir.resolve("baritone");
            readmeDir = worldDir;
        } else { // Otherwise, the server must be remote...
            String folderName;
            final ServerData serverData = ctx.minecraft().getCurrentServer();
            if (serverData != null) {
                folderName = serverData.isRealm() ? "realms" : serverData.ip;
            } else {
                //replaymod causes null currentServer and false singleplayer.
                System.out.println("World seems to be a replay. Not loading Baritone cache.");
                currentWorld = null;
                mcWorld = ctx.world();
                return Optional.empty();
            }
            if (SystemUtils.IS_OS_WINDOWS) {
                folderName = folderName.replace(":", "_");
            }
            // TODO: This should probably be in "baritone/servers"
            worldDir = baritone.getDirectory().resolve(folderName);
            // Just write the readme to the baritone directory instead of each server save in it
            readmeDir = baritone.getDirectory();
        }

        return Optional.of(new Pair<>(worldDir, readmeDir));
    }

    /**
     * Why does this exist instead of fixing the event? Some mods break the event. Lol.
     */
    private void detectAndHandleBrokenLoading() {
        if (this.mcWorld != ctx.world()) {
            if (this.currentWorld != null) {
                System.out.println("mc.world unloaded unnoticed! Unloading Baritone cache now.");
                closeWorld();
            }
            if (ctx.world() != null) {
                System.out.println("mc.world loaded unnoticed! Loading Baritone cache now.");
                initWorld(ctx.world());
            }
        } else if (this.currentWorld == null && ctx.world() != null && (ctx.minecraft().hasSingleplayerServer() || ctx.minecraft().getCurrentServer() != null)) {
            System.out.println("Retrying to load Baritone cache");
            initWorld(ctx.world());
        }
    }
}
