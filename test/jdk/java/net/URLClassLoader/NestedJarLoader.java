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
   @summary Verify URLClassLoader support for nested JAR files
   @run junit NestedJarLoader
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class NestedJarLoader {

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
     * Verify reading of an entry form a URL to a JAR entry nested within another JAR file
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void openStreamFromURL() throws IOException {
        singleJar = createSingleJar(libpath, libraryBytes);
        // Verify that we can open a URL to the class file and read its data
        try (var in = new URL(classFileUrlPath).openStream()) {
            assertArrayEquals(classData, in.readAllBytes());
        }
    }

    /**
     * Verify that URLClassLoader can find a resource located in a JAR file nested within
     * another JAR file.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void nestedClassLoader() throws IOException {
        singleJar = createSingleJar(libpath, libraryBytes);
        // Verify that we can use the library jar URL as input to URLClassLoader
        try (var classLoader = new URLClassLoader(new URL[]{new URL(libUrlPath)})) {
            try (var in = classLoader.getResourceAsStream(classFilePath)) {
                assertArrayEquals(classData17, in.readAllBytes());
            }
            // JarFile should be cached now
            try (var in = classLoader.getResourceAsStream(classFilePath)) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }

    /**
     * Verify that closing a URLClassLoader with a nested JAR URL also closed the nested
     * JarFile instance
     *
     * @throws IOException if an unexpected IOException occurs
     */

    /**
     * Verify that URLClassLoader honors nested Class-Path attribute values and
     * finds resources located in a nested JAR
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void singleJarClassLoader() throws IOException {
        singleJar = createSingleJar(libpath, libraryBytes);
        // Verify that URLClassLoader with the single jar also finds resources from the library JAR
        try (var classLoader = new URLClassLoader(new URL[] {new URL(fileUrlPath)})) {
            try (var in = classLoader.getResourceAsStream(classFilePath)) {
                assertArrayEquals(classData17, in.readAllBytes());
            }
        }
    }

    private Path createSingleJar(String libpath, byte[] lib) throws IOException {
        // Create the single-jar including the nested library
        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        man.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        man.getMainAttributes().put(Attributes.Name.CLASS_PATH, "jar:" + libpath);

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

    private static byte[] createLibraryJar(String classFilePath, byte[] classData, int version, byte[] versionedClassData) {
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