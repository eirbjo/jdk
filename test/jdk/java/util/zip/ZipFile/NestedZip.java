/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 */

/* @test
   @bug 4241361
   @summary Make sure we can read a nested zip file.
   @run junit NestedZip
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.jar.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

public class NestedZip {

    // ZIP file produced during tests
    private Path singleJar = Path.of("single.jar");

    // Create multi-release enabled library JAR
    String classFilePath = "com/example/HelloWorld.class";
    byte[] classData = "classdata_base".getBytes(StandardCharsets.UTF_8);
    byte[] classData17 = "classdata_17".getBytes(StandardCharsets.UTF_8);
    byte[] libraryBytes = createLibraryJar(classFilePath, classData, 17, classData17);

    // Package the library inside a single-jar file
    String libpath = "META-INF/lib/library.jar";

    // URL to the single-jar file
    String fileUrlPath = "file:" + singleJar.toFile().getPath();
    // URL to the nested library JAR file
    String libUrlPath = "jar:" + fileUrlPath + "!/" + libpath;
    // URL to the classfile, inside the library jar, inside the single-jar
    String classFileUrlPath = libUrlPath + "!/" + classFilePath;



    /**
     * Delete the ZIP file produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(singleJar);
    }

    /**
     * Verify reading of an entry form a JAR file nested within another JAR file
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void readEntryFromNestedJar() throws IOException {
        singleJar = createSingleJar(libpath, libraryBytes);

        // Open the single-jar file
        try (var zf = new ZipFile(singleJar.toFile())) {
            // Open the nested library ZIP file
            try (var nestedZf = new ZipFile(zf, libpath)) {
                // Verify that we can find the entry inside the nested library
                ZipEntry entry = nestedZf.getEntry(classFilePath);
                assertNotNull(entry);
                // Also verify the contents of the class file
                try (var in = nestedZf.getInputStream(entry)) {
                    assertArrayEquals(classData, in.readAllBytes());
                }
            }
        }
    }

    /**
     * Verify opening a multi-release nested JAR file using the JarFile API
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void multiReleaseNestedJar() throws IOException {
        createSingleJar(libpath, libraryBytes);
        try (var zf = new JarFile(singleJar.toFile())) {
            try (var nested = new JarFile(zf, libpath, true, JarFile.runtimeVersion())) {
                assertTrue(nested.isMultiRelease());
                var entry = nested.getJarEntry(classFilePath);
                assertEquals(classFilePath, entry.getName());
                assertEquals("META-INF/versions/17/" + classFilePath, entry.getRealName());
            }
        }
    }

    /**
     * Verify reading of an entry form a JAR file nested within another JAR file
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void rejectNestedZeroLength() throws IOException {
        createSingleJar(libpath, new byte[0]);

        try (var zf = new ZipFile(singleJar.toFile())) {
            ZipException exception = assertThrows(ZipException.class, () -> {
                try (var nested = new ZipFile(zf, libpath)) {
                }
            });
            assertEquals("zip file is empty", exception.getMessage());
        }
    }

    /**
     * Verify reading of an entry form a JAR file nested within another JAR file
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void rejectNestedEmptyZIP() throws IOException {
        ByteArrayOutputStream lib = new ByteArrayOutputStream();

        try (ZipOutputStream out = new ZipOutputStream(lib)) {
            // Only END header will be output
        }

        createSingleJar(libpath, lib.toByteArray());

        try (var zf = new ZipFile(singleJar.toFile())) {
            try (var nested = new ZipFile(zf, libpath)) {
                assertEquals(0, nested.size());
            }
        }
    }

    /**
     * Verify a null entry param is rejected with a NPE when opening
     * a nested ZipFile entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void constructorNullEntry() throws IOException {
        createSingleJar(libpath, libraryBytes);
        try (var zf = new ZipFile(singleJar.toFile())) {
            assertThrows(NullPointerException.class, () -> {
                try (var nested = new ZipFile(zf, null)) {

                }
            });
        }
    }

    /**
     * Verify a null entry param is rejected with a NPE when opening
     * a nested ZipFile entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void nonStoredNestedEntry() throws IOException {
        createSingleJar(libpath, libraryBytes);
        try (var zf = new ZipFile(singleJar.toFile())) {
            assertThrows(ZipException.class, () -> {
                try (var nested = new ZipFile(zf, "deflated.txt")) {
                }
            });
        }
    }

    /**
     * Verify a null entry param is rejected with a NPE when opening
     * a nested ZipFile entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void nonExistingEntry() throws IOException {
        createSingleJar(libpath, libraryBytes);
        try (var zf = new ZipFile(singleJar.toFile())) {
            assertThrows(ZipException.class, () -> {
                try (var nested = new ZipFile(zf, "not-found")) {

                }
            });
        }
    }

    /**
     * Verify a null entry param is rejected with a ISE when opening
     * a nested ZipFile entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void closedParentZip() throws IOException {
        createSingleJar(libpath, libraryBytes);
        ZipFile closed;
        try (var zf = new ZipFile(singleJar.toFile())) {
            closed = zf;
        }
        assertThrows(IllegalStateException.class, () -> {
            try (var nested = new ZipFile(closed, libpath)) {

            }
        });
    }

    private Path createSingleJar(String libpath, byte[] lib) throws IOException {
        // Create the single-jar including the nested library
        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (var zo = new JarOutputStream(new FileOutputStream(singleJar.toFile()), man)) {
            var entry = new ZipEntry(libpath);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(lib.length);
            CRC32 crc32 = new CRC32();
            crc32.update(lib);
            entry.setCrc(crc32.getValue());
            zo.putNextEntry(entry);
            zo.write(lib);

            zo.putNextEntry(new ZipEntry("deflated.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        return singleJar;
    }

    private static byte[] createLibraryJar(String classFilePath, byte[] classData,  int version, byte[] versionedClassData) {
        ByteArrayOutputStream library = new ByteArrayOutputStream();

        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        man.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        try (var zo = new JarOutputStream(library, man)) {
            zo.putNextEntry(new ZipEntry("META-INF/versions/" + version + "/"+ classFilePath));
            zo.write(versionedClassData);
            zo.putNextEntry(new ZipEntry(classFilePath));
            zo.write(classData);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return library.toByteArray();
    }
}