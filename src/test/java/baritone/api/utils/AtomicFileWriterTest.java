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

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNoException;

public class AtomicFileWriterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void createsNewFile() throws IOException {
        Path target = folder.getRoot().toPath().resolve("new.txt");
        AtomicFileWriter.write(target, out -> out.write(bytes("hello")));
        assertEquals("hello", read(target));
        assertNoTempFilesLeft();
    }

    @Test
    public void replacesExistingFile() throws IOException {
        Path target = folder.getRoot().toPath().resolve("settings.txt");
        Files.write(target, bytes("a much longer old content"));
        AtomicFileWriter.write(target, out -> out.write(bytes("new")));
        assertEquals("new", read(target));
        assertNoTempFilesLeft();
    }

    @Test
    public void failingWriterKeepsOldContent() throws IOException {
        Path target = folder.getRoot().toPath().resolve("waypoints.mp4");
        Files.write(target, bytes("old"));
        try {
            AtomicFileWriter.write(target, out -> {
                out.write(bytes("half writ"));
                throw new IOException("simulated failure");
            });
            fail("exception from the writer should be rethrown");
        } catch (IOException e) {
            assertEquals("simulated failure", e.getMessage());
        }
        assertEquals("old", read(target));
        assertNoTempFilesLeft();
    }

    @Test
    public void uncheckedExceptionKeepsOldContent() throws IOException {
        Path target = folder.getRoot().toPath().resolve("settings.txt");
        Files.write(target, bytes("old"));
        try {
            AtomicFileWriter.write(target, out -> {
                out.write(bytes("half writ"));
                throw new IllegalStateException("simulated failure");
            });
            fail("exception from the writer should be rethrown");
        } catch (IllegalStateException e) {
            assertEquals("simulated failure", e.getMessage());
        }
        assertEquals("old", read(target));
        assertNoTempFilesLeft();
    }

    @Test
    public void writerMayCloseWrappingStreams() throws IOException {
        // the same way CachedRegion writes its files: gzip + data stream, closed by try-with-resources
        Path target = folder.getRoot().toPath().resolve("r.0.0.bcr");
        byte[] payload = new byte[1 << 20];
        new Random(42).nextBytes(payload);
        AtomicFileWriter.write(target, fileOut -> {
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(fileOut, 16384))) {
                out.writeInt(payload.length);
                out.write(payload);
            }
        });
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(target)))) {
            byte[] read = new byte[in.readInt()];
            in.readFully(read);
            assertArrayEquals(payload, read);
            assertEquals(-1, in.read());
        }
        assertNoTempFilesLeft();
    }

    @Test
    public void replacesTheFileASymlinkPointsTo() throws IOException {
        Path real = folder.newFolder("shared").toPath().resolve("settings.txt");
        Files.write(real, bytes("old"));
        Path link = folder.getRoot().toPath().resolve("settings.txt");
        try {
            Files.createSymbolicLink(link, real);
        } catch (IOException | UnsupportedOperationException e) {
            assumeNoException("symlinks can't be created here (e.g. Windows without developer mode)", e);
        }
        AtomicFileWriter.write(link, out -> out.write(bytes("new")));
        assertTrue("the symlink must not be replaced by a regular file", Files.isSymbolicLink(link));
        assertEquals("new", read(real));
    }

    private void assertNoTempFilesLeft() throws IOException {
        try (Stream<Path> files = Files.walk(folder.getRoot().toPath())) {
            List<Path> tmp = files.filter(p -> p.getFileName().toString().endsWith(".tmp")).collect(Collectors.toList());
            assertTrue("leftover temp files: " + tmp, tmp.isEmpty());
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
