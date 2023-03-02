/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

import jdk.test.lib.zink.Cen;
import jdk.test.lib.zink.Zink;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.testng.Assert.*;

/**
 * @test
 * @bug 8280404
 * @summary Validate that Zip/JarFile will throw a ZipException when the CEN
 * comment length field contains an incorrect value
 * @enablePreview true
 * @library /test/lib
 * @run testng InvalidCommentLengthTest
 */
public class InvalidCommentLengthTest {

    // Name used to create a JAR with an invalid comment length
    public static final Path INVALID_CEN_COMMENT_LENGTH_JAR =
            Path.of("Invalid-CEN-Comment-Length.jar");
    // Name used to create a JAR with a valid comment length
    public static final Path VALID_CEN_COMMENT_LENGTH_JAR =
            Path.of("Valid-CEN-Comment-Length.jar");
    // Zip/Jar CEN file header entry that will be modified
    public static final String META_INF_MANIFEST_MF = "META-INF/MANIFEST.MF";
    // Expected ZipException message when the comment length corrupts the
    // Zip/Jar file
    public static final String INVALID_CEN_HEADER_BAD_ENTRY_NAME_OR_COMMENT =
            "invalid CEN header (bad entry name or comment)";

    /**
     * Create Jar files used by the tests.
     * The {@code byte} array {@code VALID_ZIP_WITH_NO_COMMENTS_BYTES} is written
     * to disk to create the jar file: {@code Valid-CEN-Comment-Length.jar}.
     *
     * The jar file {@code InValid-CEN-Comment-Length.jar} is created by copying
     * the {@code byte} array {@code VALID_ZIP_WITH_NO_COMMENTS_BYTES} and modifying
     * the CEN file header comment length entry for "META-INF/MANIFEST.MF" so that
     * new comment length will forward the CEN to a subsequent CEN file header
     * entry.
     *
     * For {@code InValid-CEN-Comment-Length.jar}, the comment length is changed
     * from {@code 0x0} to the {@code 0x37}.
     *
     * @throws IOException If an error occurs
     */
    @BeforeTest
    public void setup() throws IOException {
        Files.deleteIfExists(VALID_CEN_COMMENT_LENGTH_JAR);
        Files.deleteIfExists(INVALID_CEN_COMMENT_LENGTH_JAR);
        // Create the valid jar
        createValidJar();
        // Now create an invalid jar
        Zink.stream(VALID_CEN_COMMENT_LENGTH_JAR)
                // Change CEN file Header comment length so that the length will
                // result in the offset pointing to a subsequent CEN file header
                // resulting in an invalid comment
                .map(Cen.named("META-INF/MANIFEST.MF", cen -> cen.clen((short) 55)))
                .collect(Zink.toFile(INVALID_CEN_COMMENT_LENGTH_JAR));
    }

    /**
     * Create a valid Jar file with a META-INF/MANIFEST.MF entry
     * and one additional regular entry 'Tennis.txt'
     * @throws IOException
     */
    private static void createValidJar() throws IOException {
        Manifest man = new Manifest();
        man.getMainAttributes().putValue("Version", "1.0");
        try (JarOutputStream jo = new JarOutputStream(
                Files.newOutputStream(VALID_CEN_COMMENT_LENGTH_JAR),
                man)) {
            jo.putNextEntry(new ZipEntry("Tennis.txt"));
            jo.write("Smash".getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Clean up after the test run
     *
     * @throws IOException If an error occurs
     */
    @AfterTest
    public static void cleanup() throws IOException {
        Files.deleteIfExists(VALID_CEN_COMMENT_LENGTH_JAR);
        Files.deleteIfExists(INVALID_CEN_COMMENT_LENGTH_JAR);
    }

    /**
     * Validate that the original(valid) Jar file can be opened by {@code ZipFile}
     * and the expected Zip entry can be found
     * @throws IOException If an error occurs
     */
    @Test
    public static void ZipFileValidCommentLengthTest() throws IOException {
        try (ZipFile jf = new ZipFile(VALID_CEN_COMMENT_LENGTH_JAR.toFile())) {
            ZipEntry ze = jf.getEntry(META_INF_MANIFEST_MF);
            assertNotNull(ze);
            assertEquals(ze.getName(), META_INF_MANIFEST_MF);
        }
    }

    /**
     * Validate that the original(valid) Jar file can be opened by {@code JarFile}
     * and the expected Zip entry can be found
     * @throws IOException If an error occurs
     */
    @Test
    public static void JarFileValidCommentLengthTest() throws IOException {
        try (JarFile jf = new JarFile(VALID_CEN_COMMENT_LENGTH_JAR.toFile())) {
            ZipEntry ze = jf.getEntry(META_INF_MANIFEST_MF);
            assertNotNull(ze);
            assertEquals(ze.getName(), META_INF_MANIFEST_MF);
        }
    }

    /**
     * Validate that a ZipException is thrown when the CEN file header comment
     * length is non-zero and the CEN entry does not contain a comment when
     * the Jar file is opened by {@code ZipFile}
     */
    @Test
    public static void ZipFileInValidCommentLengthTest() {
        var ex= expectThrows(ZipException.class,
                () -> new ZipFile(INVALID_CEN_COMMENT_LENGTH_JAR.toFile()));
        assertEquals(ex.getMessage(), INVALID_CEN_HEADER_BAD_ENTRY_NAME_OR_COMMENT);
    }

    /**
     * Validate that a ZipException is thrown when the CEN file header comment
     * length is non-zero and the CEN entry does not contain a comment when
     * the Jar file is opened by  {@code JarFile}
     */
    @Test
    public static void JarFileInValidCommentLengthTest()  {
        var ex= expectThrows(ZipException.class,
                () -> new JarFile(INVALID_CEN_COMMENT_LENGTH_JAR.toFile()));
        assertEquals(ex.getMessage(), INVALID_CEN_HEADER_BAD_ENTRY_NAME_OR_COMMENT);
    }
}
