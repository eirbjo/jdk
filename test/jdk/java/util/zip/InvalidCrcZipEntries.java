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

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * @test
 * @summary Test behaviour of ZipInputStream, ZipFile, and ZipFileSystem APIs
 * when reading contents from stored and deflated entries with invalid CRC
 * values in the Loc, Descriptor or Cen headers.
 * @run testng InvalidCrcZipEntries
 */

public class InvalidCrcZipEntries {

    /**
     * A sample zip file with one STORED and one DEFLATED entry.
     *
     * Structure:
     *
     * ------  Local File Header  ------
     * 000000  signature          0x04034b50
     * 000004  version            10
     * 000006  flags              0x0800
     * 000008  method             0              Stored (no compression)
     * 000010  time               0x69f4         13:15:40
     * 000012  date               0x5657         2023-02-23
     * 000014  crc                0x3610a686
     * 000018  csize              5
     * 000022  size               5
     * 000026  nlen               6
     * 000028  elen               0
     * 000030  name               6 bytes        'stored'
     *
     * ------  File Data  ------
     * 000036  ext data           5 bytes
     *
     * ------  Local File Header  ------
     * 000041  signature          0x04034b50
     * 000045  version            20
     * 000047  flags              0x0808
     * 000049  method             8              Deflated
     * 000051  time               0x69f4         13:15:40
     * 000053  date               0x5657         2023-02-23
     * 000055  crc                0x00000000
     * 000059  csize              0
     * 000063  size               0
     * 000067  nlen               8
     * 000069  elen               0
     * 000071  name               8 bytes        'deflated'
     *
     * ------  File Data  ------
     * 000079  ext data           7 bytes
     *
     * ------  Data Desciptor  ------
     * 000086  signature          0x08074b50
     * 000090  crc                0x3610a686
     * 000094  csize              7
     * 000098  size               5
     *
     * ------  Central Directory File Header  ------
     * 000102  signature          0x02014b50
     * 000106  made by version    10
     * 000108  extract version    10
     * 000110  flags              0x0800
     * 000112  method             0              Stored (no compression)
     * 000114  time               0x69f4         13:15:40
     * 000116  date               0x5657         2023-02-23
     * 000118  crc                0x3610a686
     * 000122  csize              5
     * 000126  size               5
     * 000130  diskstart          0
     * 000132  nlen               6
     * 000134  elen               0
     * 000136  clen               0
     * 000138  iattr              0x00
     * 000140  eattr              0x0000
     * 000144  loc offset         0
     * 000148  name               6 bytes        'stored'
     *
     * ------  Central Directory File Header  ------
     * 000154  signature          0x02014b50
     * 000158  made by version    20
     * 000160  extract version    20
     * 000162  flags              0x0808
     * 000164  method             8              Deflated
     * 000166  time               0x69f4         13:15:40
     * 000168  date               0x5657         2023-02-23
     * 000170  crc                0x3610a686
     * 000174  csize              7
     * 000178  size               5
     * 000182  diskstart          0
     * 000184  nlen               8
     * 000186  elen               0
     * 000188  clen               0
     * 000190  iattr              0x00
     * 000192  eattr              0x0000
     * 000196  loc offset         41
     * 000200  name               8 bytes        'deflated'
     *
     * ------  End of Central Directory  ------
     * 000208  signature          0x06054b50
     * 000212  this disk          0
     * 000214  cen disk           0
     * 000216  entries disk       2
     * 000218  entries total      2
     * 000220  cen size           106
     * 000224  cen offset         102
     * 000228  clen               0
     */
     static byte[] ZIP_TEMPLATE = HexFormat.of().parseHex("""
            504b03040a0000080000f469575686a61036050000000500000006000000
            73746f72656468656c6c6f504b0304140008080800f46957560000000000
            00000000000000080000006465666c61746564cb48cdc9c90700504b0708
            86a610360700000005000000504b01020a000a0000080000f469575686a6
            103605000000050000000600000000000000000000000000000000007374
            6f726564504b01021400140008080800f469575686a61036070000000500
            00000800000000000000000000000000290000006465666c61746564504b
            050600000000020002006a000000660000000000
            """.replaceAll("\n",""));

    private byte[] copy;

    @BeforeMethod
    public void setup() {
        copy = Arrays.copyOf(ZIP_TEMPLATE, ZIP_TEMPLATE.length);
    }

    // Invalidate the CRC value at the specified position by storing 42
    private void invalidateCrc(int pos) {
        ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(pos,42);
    }

    /**
     * ZipInputStream should detect and reject a STORED entry
     * with an invalid CRC in the LOC.
     */
    @Test
    public void zipInputStreamShouldRejectInvalidLocCrc() {
        // Invalidate the CRC in the Loc record of the STORED entry
        invalidateCrc(14);
        ZipException ze = expectThrows(ZipException.class, () -> {
            readZipInputStream();
        });
        assertTrue(ze.getMessage().contains("invalid entry CRC (expected 0x"));
    }

    /**
     * ZipInputStream should detect and reject a DEFLATED entry
     * with an invalid CRC in the Descriptor.
     */
    @Test
    public void zipInputStreamShouldRejectInvalidDescriptorCrc() {
        // Invalidate the CRC in the Descriptor record of the DEFLATED entry
        invalidateCrc(90);
        ZipException ze = expectThrows(ZipException.class, () -> {
            readZipInputStream();
        });
        assertTrue(ze.getMessage().contains("invalid entry CRC (expected 0x"));
    }

    /**
     * ZipFile and ZipFileSystem should accept a STORED entry
     * with an invalid CRC in the Cen.
     */
    @Test
    public void zipFileAndZipfsShouldAcceptInvalidStoredCrc() throws IOException {
        // Invalidate the CRC in the CEN record of the STORED entry
        invalidateCrc(118);
        readZipFile();
        readZipFs();
    }

    /**
     * ZipFile and ZipFileSystem should accept a DEFLATED entry
     * with an invalid CRC in the Cen.
     */
    @Test
    public void zipFileAndZipfsShouldAcceptInvalidDeflatedCrc() throws IOException {
        // Invalidate the CRC in the CEN record of the DEFLATED entry
        invalidateCrc(170);
        readZipFile();
        readZipFs();
    }

    /**
     * Consume input streams of all entries using the ZipFile API.
     */
    private void readZipFile() throws IOException {
        Path zipFile = Path.of("zip.zip");
        Files.write(zipFile, copy);
        try (ZipFile zf = new ZipFile(zipFile.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zf.entries());
            for (ZipEntry entry : entries) {
                try (InputStream in = zf.getInputStream(entry)) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
            }
        }
    }

    /**
     * Consume input streams of all entries using the ZipFileSystem API.
     */
    private void readZipFs() throws IOException {
        Path zipFile = Path.of("zip.zip");
        Files.write(zipFile, copy);
        try (FileSystem fs =
                     FileSystems.newFileSystem(zipFile, Map.of())) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes
                            attrs) throws IOException {

                        try (InputStream in = Files.newInputStream(file)) {
                            in.transferTo(OutputStream.nullOutputStream());
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    /**
     * Consume input streams of all entries using the ZipInputStream API.
     */
    private void readZipInputStream() throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(copy))) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }
}
