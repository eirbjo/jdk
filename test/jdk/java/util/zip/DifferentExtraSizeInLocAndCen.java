/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.zink.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;

/* @test
 * @summary ZIP files allows the CEN and LOC to have different sized extra fields,
 * event though ZipOutputStream never produces such files. This test verifies that
 * ZipFile will not trust the extra length from the CEN when calculating the
 * offset of the file data.
 * @enablePreview true
 * @library /test/lib
 * @run testng DifferentExtraSizeInLocAndCen
 */
public class DifferentExtraSizeInLocAndCen {

    // A ZIP file with differing CEN and LOC extra field sizes
    private Path zipFile;

    @BeforeTest
    public void setup() throws IOException {
        // Make a small ZIP file
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("entry"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        // Append an extra generic data field in the CEN to make
        // the CEN extra length different from the LOC extra length
        zipFile = Zink.stream(out.toByteArray())
                .map(Cen.map(cen -> cen.extra(Stream.concat(
                                Stream.of(new ExtGeneric((short) 0x1234, (short) 1, new byte[1])),
                                Stream.of(cen.extra()))
                        .toArray(ExtField[]::new))
                ))
                .collect(Zink.toFile("differing-extra-sizes.zip"));
    }

    /**
     * Verify that ZipInputStream can parse a data descriptor correctly,
     * also when it does not have the recommended leading signature
     */
    @Test
    public void shouldReadZipWithDifferingCenAndLocExtraSizes() throws IOException {
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            ZipEntry entry = zf.getEntry("entry");
            byte[] content = zf.getInputStream(entry).readAllBytes();
            assertEquals(content, "hello".getBytes(StandardCharsets.UTF_8));
        }
    }
}
