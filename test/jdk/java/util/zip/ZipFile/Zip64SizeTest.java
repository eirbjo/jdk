/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.zink.Zink;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @test
 * @bug 8226530
 * @summary ZIP File System tests that leverage DirectoryStream
 * @enablePreview true
 * @library /test/lib
 * @compile Zip64SizeTest.java
 * @run testng Zip64SizeTest
 */
public class Zip64SizeTest {

    /**
     * Validate that if the size of a ZIP entry exceeds 0xFFFFFFFF, that the
     * correct size is returned from the ZIP64 Extended information.
     * @throws IOException
     */
    @Test
    private static void validateZip64EntrySize() throws IOException {
        Path zip64 = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .collect(Zink.toFile("zip64-file.zip"));
        try (ZipFile zip = new ZipFile(zip64.toFile())) {
            ZipEntry ze = zip.getEntry("entry");
            assertEquals(ze.getSize(), "hello".length());
        }
    }

    /**
     * Make a small ZIP file
     */
    private static byte[] smallZip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            zos.putNextEntry(new ZipEntry("entry"));
            zos.write("Hello".getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

}
