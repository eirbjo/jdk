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

import jdk.test.lib.zink.Cen;
import jdk.test.lib.zink.Loc;
import jdk.test.lib.zink.Zink;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;

/* @test
 * @bug 8184940 8188869
 * @summary JDK 9 rejects zip files where the modified day or month is 0
 *          or otherwise represent an invalid date, such as 1980-02-30 24:60:60
 * @author Liam Miller-Cushon
 * @enablePreview true
 * @library /test/lib
 * @run testng ZeroDate
 */
public class ZeroDate {

    // Byte array holding a single-entry ZIP file
    private byte[] smallZip;

    @BeforeTest
    public void setup() throws IOException {
        // create a zip file, and read it in as a byte array
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            ZipEntry e = new ZipEntry("x");
            zos.putNextEntry(e);
            zos.write((int) 'x');
        }
        smallZip = out.toByteArray();
    }

    @Test
    public void zeroDate() throws IOException {
        // year, month, day are zero
        testDate((short) (0 << 9 | 0 << 5 | 0), LocalDate.of(1979, 11, 30));
    }

    @Test
    public void yearIsZero() throws IOException {
        // only year is zero
        testDate((short) (0 << 9 | 4 << 5 | 5), LocalDate.of(1980, 4, 5));
    }

    @Test
    public void monthGreaterThan12() throws IOException {
        // month is greater than 12
        testDate((short) (0 << 9 | 13 << 5 | 1), LocalDate.of(1981, 1, 1));
    }

    @Test
    public void thirtiethOfFebruary() throws IOException {
        // 30th of February
        testDate((short) (0 << 9 | 2 << 5 | 30), LocalDate.of(1980, 3, 1));
    }

    public void afterMidnight() throws IOException {
        // 30th of February, 24:60:60
        testDateTime((short) (0 << 0 | 2 << 5 | 30),   (short) (24 << 11 | 60 << 5 | 60 >> 1),
                LocalDateTime.of(1980, 3, 2, 1, 1, 0));
    }

    private void testDate(short date, LocalDate expected) throws IOException {
        // Set date = 0, expecting start of day
        testDateTime(date, (short) 0, expected.atStartOfDay());
    }
    private void testDateTime(short date, short time, LocalDateTime expected) throws IOException {
        // Create a ZIP with modified date and time fields in the LOC and CEN headers
        Path path = Zink.stream(smallZip)
                .map(Loc.map( loc -> loc.time(time).date(date)))
                .map(Cen.map(cen -> cen.time(time).date(date)))
                .collect(Zink.toFile("out.zip"));

        // ensure that the archive is still readable, and the modified getLastModifiedTime is as expected
        try (ZipFile zf = new ZipFile(path.toFile())) {
            ZipEntry ze = zf.entries().nextElement();
            Instant actualInstant = ze.getLastModifiedTime().toInstant();
            Instant expectedInstant = expected.atZone(ZoneId.systemDefault()).toInstant();
            assertEquals(actualInstant, expectedInstant);
        } finally {
            Files.delete(path);
        }
    }
}
