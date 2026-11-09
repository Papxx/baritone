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

package baritone.api.utils;

import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Replaces files without ever leaving a truncated or half written file behind.
 * <p>
 * The new content is written to a temporary file next to the target, synced to disk and then renamed over the
 * target. If the game crashes or is killed while saving, the previous version of the file stays intact.
 */
public final class AtomicFileWriter {

    /**
     * On Windows the rename fails while another process (virus scanner, search indexer, cloud sync, ...) has the
     * target open, so retry a few times before giving up.
     */
    private static final int MAX_MOVE_ATTEMPTS = 5;

    private AtomicFileWriter() {}

    @FunctionalInterface
    public interface IOConsumer<T> {
        void accept(T t) throws IOException;
    }

    /**
     * Atomically replaces {@code target} with the bytes written by {@code writer}. If {@code writer} throws, the
     * target is left untouched.
     *
     * @param target The file to write. Symbolic links are followed, so the file they point to gets replaced.
     * @param writer Writes the new content. It may wrap and close the given stream, and has to flush or close any
     *               buffering stream it wraps around it before returning.
     * @throws IOException If writing or replacing the file failed
     */
    public static void write(Path target, IOConsumer<OutputStream> writer) throws IOException {
        if (Files.exists(target)) {
            target = target.toRealPath(); // replace the file a symlink points to instead of the symlink itself
        }
        Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), target.getFileName().toString() + ".", ".tmp");
        try {
            try (FileOutputStream fileOut = new FileOutputStream(tmp.toFile())) {
                writer.accept(new FilterOutputStream(fileOut) {
                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        out.write(b, off, len); // FilterOutputStream would write byte by byte otherwise
                    }

                    @Override
                    public void close() throws IOException {
                        flush(); // the real stream gets synced and closed below
                    }
                });
                fileOut.getFD().sync();
            }
            moveIntoPlace(tmp, target);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void moveIntoPlace(Path tmp, Path target) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                if (attempt >= MAX_MOVE_ATTEMPTS) {
                    throw e;
                }
                try {
                    Thread.sleep(10L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException interrupted = new InterruptedIOException("Interrupted while replacing " + target);
                    interrupted.addSuppressed(e);
                    throw interrupted;
                }
            }
        }
    }
}
