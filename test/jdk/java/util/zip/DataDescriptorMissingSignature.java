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

import jdk.test.lib.zink.Desc;
import jdk.test.lib.zink.Zink;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/* @test
 * @summary Verify that ZipInputStream can read Data Descriptors
 * without the recommended (but optional) signature.
 * @enablePreview true
 * @library /test/lib
 * @run testng DataDescriptorMissingSignature
 */
public class DataDescriptorMissingSignature {

    // A valid, small template ZIP file
    private byte[] zipFile;

    @BeforeTest
    public void setup() throws IOException {
        // Make a small sample ZIP
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("entry"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        zipFile = out.toByteArray();
    }

    /**
     * Verify that ZipInputStream can parse a data descriptor correctly,
     * also when it does not have the recommended leading signature
     */
    @Test
    public void shouldParseSignatureLessDescriptor() throws IOException {
        byte[] zip = Zink.stream(zipFile)
                .map(Desc.map(desc -> desc.signed(false)))
                .collect(Zink.toByteArray());

        readZipInputStream(zip);
    }

    /**
     * Verify that ZipInputStream can parse a Zip64-formatted data descriptor correctly,
     * also when it does not have the recommended leading signature
     */
    @Test
    @Ignore // awaiting PR #12524
    public void shouldParseSignatureLessZip64Descriptor() throws IOException {
        byte[] zip = Zink.stream(zipFile)
                .flatMap(Zink.toZip64())
                .map(Desc.map(desc -> desc.signed(false)))
                .collect(Zink.toByteArray());

        readZipInputStream(zip);
    }

    /**
     * Fully consume all entry streams in a ZipInputStream
     * @param zip
     * @throws IOException
     */
    private static void readZipInputStream(byte[] zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }
}
