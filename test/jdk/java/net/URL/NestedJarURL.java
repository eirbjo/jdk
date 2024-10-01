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
   @summary Verify URL support for nested JAR files
   @run junit NestedJarURL
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class NestedJarURL {

    // ZIP file produced during tests
    private Path singleJar = Path.of("single.jar");

    // Create multi-release enabled library JAR
    String classFilePath = "com/example/HelloWorld.class";
    byte[] classData = "classdata".getBytes(StandardCharsets.UTF_8);
    String doubleNestedLibPath = "META-INF/lib/double-nested.jar";
    String doubleNestedLibEntry = "double-nested.txt";
    byte[] libraryBytes = createLibraryJar(classFilePath, classData, doubleNestedLibPath, doubleNestedLibEntry);

    // Package the library inside a single-jar file
    String libpath = "META-INF/lib/library.jar";

    // URL to the single-jar file
    String fileUrlPath = "file:" + singleJar.toFile().getPath();
    // URL to the nested library JAR file
    String libUrlPath = "jar:" + fileUrlPath + "!/" + libpath;
    // URL to the classfile, inside the library jar, inside the single-jar
    String classFileUrlPath = libUrlPath + "!/" + classFilePath;
    String doubleNestedUrlPath = "jar:" + fileUrlPath + "!/" + libpath +"!/" + doubleNestedLibPath + "!/" + doubleNestedLibEntry;



    /**
     * Delete the ZIP file produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(singleJar);
    }

    /**
     * Verify reading of an entry form a URL to a JAR entry nested within another JAR file
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void openStreamFromURL() throws IOException {
        singleJar = createSingleJar(libpath, libraryBytes);
        // Verify that we can open a URL to the nested class file and read its data
        try (var in = new URL(classFileUrlPath).openStream()) {
            assertArrayEquals(classData, in.readAllBytes());
        }
    }

    /**
     * Verify that reading from a double-nested JAR fails with FileNotFoundException
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void openStreamFromDoubleNestedURL() throws IOException {
        singleJar = createSingleJar(libpath, libraryBytes);
        // Verify that opening a double-nested JAR URL fails
        FileNotFoundException exception = assertThrows(FileNotFoundException.class, () -> {
            try (var in = new URL(doubleNestedUrlPath).openStream()) {
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
        }
        return singleJar;
    }

    private static byte[] createLibraryJar(String classFilePath, byte[] classData, String libPath, String libEntry) {
        ByteArrayOutputStream library = new ByteArrayOutputStream();
        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (var zo = new JarOutputStream(library, man)) {
            zo.putNextEntry(new ZipEntry(classFilePath));
            zo.write(classData);
            zo.putNextEntry(new ZipEntry(libPath));
            var zo2 = new ZipOutputStream(zo);
            zo2.putNextEntry(new ZipEntry(libEntry));
            zo2.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return library.toByteArray();
    }
}