/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.zink.Cen;
import jdk.test.lib.zink.ExtField;
import jdk.test.lib.zink.ExtTs;
import jdk.test.lib.zink.Zink;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @test
 * @bug 8255380 8257445
 * @summary Test that Zip FS can access the LOC offset from the Zip64 extra field
 * @library /test/lib
 * @enablePreview
 * @modules jdk.zipfs
 * @run testng TestLocOffsetFromZip64EF
 */
public class TestLocOffsetFromZip64EF {

    private static final String ZIP_FILE_NAME = "LargeZipTest.zip";

    /**
     * Create the files used by this test
     * @throws IOException if an error occurs
     */
    @BeforeClass
    public void setUp() throws IOException {
        cleanup();
        createZipWithZip64Ext();
    }

    /**
     * Delete files used by this test
     * @throws IOException if an error occurs
     */
    @AfterClass
    public void cleanup() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
    }

    /**
     * Create a Zip file that will result in a Zip64 Extra (EXT) header
     * being added to the CEN entry in order to find the LOC offset for
     * SMALL_FILE_NAME.
     */
    public void createZipWithZip64Ext() throws IOException {
        Path zip = Zink.stream(makeZipWithExtendedTimestamp())
                .flatMap(Zink.toZip64())
                .map(Cen.map(cen -> cen.extra(timestampFirst(cen.extra()))))
                .collect(Zink.toFile(ZIP_FILE_NAME));
    }

    private byte[] makeZipWithExtendedTimestamp() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry("entry");
            // Make ZipOutputStream produce 'Info-ZIP extended timestamp'
            entry.setLastModifiedTime(FileTime.from(Instant.now()));
            zo.putNextEntry(entry);
        }
        return out.toByteArray();
    }

    private static ExtField[] timestampFirst(ExtField[] extra) {
        return Stream.concat(
                // Move any ExtTs fields first
                Stream.of(extra).filter(e -> e instanceof ExtTs),
                // Followed by any other fields
                Stream.of(extra).filter(e -> !(e instanceof ExtTs))
        ).toArray(ExtField[]::new);
    }

    /*
     * DataProvider used to verify that a Zip file that contains a Zip64 Extra
     * (EXT) header can be traversed
     */
    @DataProvider(name = "zipInfoTimeMap")
    protected Object[][] zipInfoTimeMap() {
        return new Object[][]{
                {Map.of()},
                {Map.of("zipinfo-time", "False")},
                {Map.of("zipinfo-time", "true")},
                {Map.of("zipinfo-time", "false")}
        };
    }

    /**
     * Navigate through the Zip file entries using Zip FS
     * @param env Zip FS properties to use when accessing the Zip file
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "zipInfoTimeMap")
    public void walkZipFSTest(final Map<String, String> env) throws IOException {
        try (FileSystem fs =
                     FileSystems.newFileSystem(Paths.get(ZIP_FILE_NAME), env)) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes
                            attrs) throws IOException {
                        System.out.println(Files.readAttributes(file,
                                BasicFileAttributes.class).toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    /**
     * Navigate through the Zip file entries using ZipFile
     * @throws IOException if an error occurs
     */
    @Test
    public void walkZipFileTest() throws IOException {
        try (ZipFile zip = new ZipFile(ZIP_FILE_NAME)) {
            zip.stream().forEach(z -> System.out.printf("%s, %s, %s%n",
                    z.getName(), z.getMethod(), z.getLastModifiedTime()));
        }
    }
}
