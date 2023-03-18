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
 *
 */

import jdk.test.lib.zink.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * @test
 * @summary Basic tests for ZipWriter test lib
 * @enablePreview true
 * @library /test/lib
 * @run testng ZinkSamples
 */
public class ZinkSamples {

    public static final int MAX_CEN_SIZE = Integer.MAX_VALUE -22 -1;

    private static final byte[] INVALID_UTF8_BYTES = {(byte) 0xF0, (byte) 0xA4, (byte) 0xAD};

    @Test
    public void shouldReadZip64() throws IOException {
        Path zip = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("zip-64.zip")));

        readZip(zip);
        readZipFs(zip);
        expectThrows(ZipException.class, () -> {
            readZipInputStream(zip);
        });
    }

    @Test
    public void shouldRejectInvalidCenCommentLength() throws IOException {

        // Make the comment length in the CEN overflow into the next CEN
        Path zip = Zink.stream(twoEntryZip())
                .map(Cen.map(Cen.named("entry1"), cen -> cen.clen((short) (42))))
                .collect(Zink.toFile("invalid-cen-comment-length.zip"));

        // Check ZipFile
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
            }
        });
        assertEquals(ex.getMessage(), "invalid CEN header (bad entry name or comment)");
    }

    @Test
    public void shouldRejectInvalidCenExtraLength() throws IOException {

        // Make the extra length in the CEN overflow into the next CEN
        Path zip = Zink.stream(twoEntryZip())
                .map(Cen.map(Cen.named("entry1"), cen -> cen.elen((short) (42))))
                .collect(Zink.toFile("invalid-cen-extra-length.zip"));

        // Check ZipFile
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
            }
        });
        assertEquals(ex.getMessage(), "invalid CEN header (bad header size)");
    }

    @Test
    public void shouldRejectInvalidCenNameLength() throws IOException {

        // Make the name length in the CEN overflow into the next CEN
        Path zip = Zink.stream(twoEntryZip())
                .map(Cen.map(Cen.named("entry1"), cen -> cen.nlen((short) (42))))
                .collect(Zink.toFile("invalid-cen-name-length.zip"));

        // Check ZipFile
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
            }
        });
        assertEquals(ex.getMessage(), "invalid CEN header (bad entry name or comment)");
    }

    @Test
    public void shouldRejectInvalidLocNameLength() throws IOException {

        // Make the name length in the CEN overflow into the next CEN
        Path zip = Zink.stream(twoEntryZip())
                .map(Loc.map(Loc.named("entry1"), loc -> loc.nlen((short) (loc.nlen()-1))))
                .collect(Zink.toFile("invalid-loc-name-length.zip"));

        // Check ZipInputStream
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry e;
                while ((e = in.getNextEntry()) != null) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
            }
        });
        assertEquals(ex.getMessage(), "invalid stored block lengths");
    }

    @Test
    public void shouldRejectInvalidLocExtraLength() throws IOException {

        // Make the extra length in the LOC overflow into the next File data
        Path zip = Zink.stream(twoEntryZip())
                .map(Loc.map(Loc.named("entry1"), loc -> loc.elen((short) (42))))
                .collect(Zink.toFile("invalid-loc-extra-length.zip"));

        // Check ZipFile
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry e;
                while ((e = in.getNextEntry()) != null) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
            }
        });
        assertEquals(ex.getMessage(), "invalid stored block lengths");
    }

    @Test
    public void shouldRejectInvalidLocSig() throws IOException {

        // Replace the LOC sig with an invalid one
        Path zip = Zink.stream(twoEntryZip())
                .map(Loc.map(Loc.named("entry1"), loc -> loc.sig(0xCAFEBABE)))
                .collect(Zink.toFile("invalid-loc-sig.zip"));

        // Check ZipFile
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    try (InputStream in = zf.getInputStream(entry)) {
                        in.transferTo(OutputStream.nullOutputStream());
                    }
                }
            }
        });
        assertEquals(ex.getMessage(), "ZipFile invalid LOC header (bad signature)");
    }

    @Test
    public void shouldRejectInvalidCenSig() throws IOException {

        // Replace the CEN sig with an invalid one
        Path zip = Zink.stream(twoEntryZip())
                .map(Cen.map(Cen.named("entry1"), cen -> cen.sig(0xCAFEBABE)))
                .collect(Zink.toFile("invalid-cen-sig.zip"));

        // Check ZipFile
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    try (InputStream in = zf.getInputStream(entry)) {
                        in.transferTo(OutputStream.nullOutputStream());
                    }
                }
            }
        });
        assertEquals(ex.getMessage(), "invalid CEN header (bad signature)");
    }

    private static byte[] twoEntryZip() throws IOException {
        // Make a zip with two entries
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("entry1"));
            zo.write("Hello".getBytes(StandardCharsets.UTF_8));
            zo.putNextEntry(new ZipEntry("entry2"));
            zo.write("World".getBytes(StandardCharsets.UTF_8));
        }
        byte[] twoEntryZip = out.toByteArray();
        return twoEntryZip;
    }

    @Test
    public void shouldRejectCenTooLarge() throws IOException {

        // Template for the transformation
        var template = smallZip();

        // CEN size of the template ZIP
        int cenSize = Zink.stream(template)
                .flatMap(Eoc.match())
                .findFirst().orElseThrow()
                .cenSize();

        // CEN size of the target ZIP, just exceeding the max limit
        int adjustedCenSize = MAX_CEN_SIZE + 1;

        // To fake a big CEN, we zero-pad it with this many bytes
        int padding = adjustedCenSize - cenSize;

        // Inject padding before the Eoc and modify the Eoc CEN size
        Path zip = Zink.stream(template)
                .flatMap(Eoc.flatMap(eoc ->
                        Stream.of(Skip.of(padding), eoc.cenSize(adjustedCenSize)))
                ).collect(Zink.toFile("cen-size-too-large.zip"));

        // Should reject ZIP because the CEN size is > limit
        ZipException ex = expectThrows(ZipException.class, () -> {
            readZip(zip);
        });

        assertEquals(ex.getMessage(), "invalid END header (central directory size too large)");
    }

    @Test
    public void shouldRejectInvalidCenSize() throws IOException {

        // Modify the Eoc such that its CEN size exceeds the file size
        Path zip = Zink.stream(smallZip())
                .map(Eoc.map(eoc -> eoc.cenSize(MAX_CEN_SIZE)))
                .collect(Zink.collect()
                        .disableOffsetFixing() // Avoid fixing invalid CEN size
                        .toFile(Path.of("cen-size-invalid.zip")));

        // Should reject ZIP because the CEN size is > file size
        ZipException ex = expectThrows(ZipException.class, () -> {
            readZip(zip);
        });

        assertEquals(ex.getMessage(), "invalid END header (bad central directory size)");
    }

    @Test
    public void shouldRejectInvalidCenOffset() throws IOException {

        // Template for the transformation
        var template = smallZip();

        // Modify the Eoc such that its CEN offset exceeds the file size
        Path zip = Zink.stream(template)
                .map(Eoc.map(eoc -> eoc.cenOffset(Integer.MAX_VALUE)))
                .collect(Zink.collect()
                        .disableOffsetFixing() // Avoid fixing invalid cenOffset
                        .toFile(Path.of("cen-bad-offset.zip")));

        // Should reject ZIP because the CEN offset is > file size
        ZipException ex = expectThrows(ZipException.class, () -> {
            readZip(zip);
        });

        assertEquals(ex.getMessage(), "invalid END header (bad central directory offset)");
    }

    @Test
    public void shouldRejectInvalidName() throws IOException {
        // Template for the transformation
        var template = smallZip();

        // Put an invalid bytes sequence at the start of the file name
        Path zip = Zink.stream(template)
                .map(Cen.map(cen -> cen.name(invalidate(cen.name()))))
                .collect(Zink.toFile("cen-bad-name.zip"));

        // Should reject ZIP because the CEN offset is > file size
        ZipException ex = expectThrows(ZipException.class, () -> {
            readZip(zip);
        });

        assertEquals(ex.getMessage(), "invalid CEN header (bad entry name or comment)");
    }

    @Test
    public void shouldRejectInvalidComment() throws IOException {
        // Template for the transformation
        var template = smallZip();

        // Put invalid byte sequence into comment
        Path zip = Zink.stream(template)
                .map(Cen.map(cen -> cen.comment(invalidate(cen.comment()))))
                .collect(Zink.toFile("cen-bad-name.zip"));

        // Should reject ZIP because the CEN offset is > file size
        ZipException ex = expectThrows(ZipException.class, () -> {
            readZip(zip);
        });

        assertEquals(ex.getMessage(), "invalid CEN header (bad entry name or comment)");
    }

    @DataProvider(name = "zipInfoTimeMap")
    protected Object[][] zipInfoTimeMap() {
        return new Object[][]{
                {Map.of()},
                {Map.of("zipinfo-time", "False")},
                {Map.of("zipinfo-time", "true")},
                {Map.of("zipinfo-time", "false")}
        };
    }
    @Test(dataProvider = "zipInfoTimeMap")
    public void shouldReadLocOffsetFromZip64EF(Map<String, String> env) throws IOException {
        Path zip64 = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .collect(Zink.collect().trace()
                        .toFile(Path.of("zip-64-loc.zip")));

        readZipFs(zip64);
        expectThrows(ZipException.class, () -> {
            readZipInputStream(zip64);
        });
        readZip(zip64);
    }

    @Test
    public void shouldRejectNegativeCsize() throws IOException {

        Path zip = Zink.stream(smallZip())
                .map(Loc.map(loc -> loc.csize(-1)))
                .map(Cen.map(cen -> cen.csize(-1)))
                .collect(Zink.toFile("negative-ext-size.zip"));

        readZip(zip); // csize not validated
        readZipFs(zip); // csize not validated
        readZipInputStream(zip); // csize not validated

    }

    @Test
    public void shouldRejectNegativeSizeUncompressed() throws IOException {

        Path zip = Zink.stream(smallUncompressedZip())
                .map(Loc.map(loc -> loc.size(-1)))
                .map(Cen.map( cen -> cen.size(-1)))
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("negative-size.zip")));

        readZip(zip);
        if (false) {
            readZipInputStream(zip); // .ZipException: unexpected EOF
        }
        readZipFs(zip);
    }

    @Test
    public void shouldRejectNegativeCSizeUncompressed() throws IOException {

        Path zip = Zink.stream(smallUncompressedZip())
                .map(Loc.map(loc -> loc.csize(-1)))
                .map(Cen.map( cen -> cen.csize(-1)))
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("negative-size.zip")));

        if (false) {
            readZip(zip); // Reads rest of file
        }
        if (false) {
            readZipFs(zip); // Reads rest of file
        }

        readZipInputStream(zip);  // Uses size, not csize

    }

    @Test
    public void shouldRejectNegativeLocOff() throws IOException {

        Path zip = Zink.stream(smallZip())
                .map(Cen.map(cen -> cen.locOff(-2)))
                .collect(Zink.collect()
                        .disableOffsetFixing()
                        .toFile(Path.of("negative-loc-offset.zip")));

        expectThrows(EOFException.class, () -> {
            readZip(zip); // EOFException, should validate?
        });

        expectThrows(ZipException.class, () -> {
            readZipFs(zip); // invalid loc 4294967294 for entry reading
        });

        readZipInputStream(zip); // Reads Loc, not Cen

    }

    @Test
    public void shouldRejectInvalidCrc() throws IOException {

        Path zip = Zink.stream(smallZip())
                .map(Cen.map(cen -> cen.crc(42)))
                .map(Loc.map(loc -> loc.crc(42)))
                .map(Desc.map(desc -> desc.crc(42)))
                .collect(Zink.toFile("invalid-crc.zip"));

        expectThrows(ZipException.class, () -> {
            readZipInputStream(zip);
        });

        if (false) { // Should ZipFile and ZipFs throw exception?
            expectThrows(ZipException.class, () -> {
                readZip(zip);
            });
            expectThrows(ZipException.class, () -> {
                readZipFs(zip);
            });
        }
    }

    @Test
    public void shouldHandleSignatureLessDescriptor() throws IOException {
        Path zip = Zink.stream(smallZip())
                .map(Desc.map(d -> d.signed(false)))
                .collect(Zink.toFile("signature-less-desc.zip"));

        readZip(zip);
        readZipInputStream(zip);
        readZipFs(zip);
    }

    @Test
    public void shouldHandleCrcMatchingDescriptorSignature() throws IOException {

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        String content = "-4226737993155020914";

        try (ZipOutputStream out = new ZipOutputStream(bout)) {
            out.putNextEntry( new ZipEntry("entry"));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.putNextEntry( new ZipEntry("other"));
            out.write("other".getBytes(StandardCharsets.UTF_8));
        }

        Path zip = Zink.stream(bout.toByteArray())
                .map(Desc.map(d -> new Desc(false, d.zip64(), d.crc(), d.csize(), d.size())))
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("spooky-crc.zip")));

        var contents = Map.of("entry", content);
        readZipFs(zip, contents);
        readZip(zip, contents);
        if (true) {
            expectThrows(ZipException.class, () -> {
                // invalid entry size (expected 67324752 but got 20 bytes)
                readZipInputStream(zip, contents);
            });
        } else {
            readZipInputStream(zip, contents);
        }
    }

    @Test
    public void shouldHandleEoc64WithExtraField() throws IOException {

        ExtField[] extra = ExtField.of(new ExtGeneric((short) 123, (short) 3, new byte[] {1,2,3}));

        Path zip = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .map(Eoc64Rec.map(e64 -> e64.extra(extra)))
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("eoc64-extfield.zip")));

        readZip(zip);
        readZipFs(zip);
        expectThrows(ZipException.class, () -> {
            readZipInputStream(zip);
        });

    }

    @Test
    public void shouldHandleZip648ByteDesc() throws IOException {

        Path zip = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .map(Desc.map(d -> d.zip64(true)))
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("8-byte-ext64-desc.zip")));

        readZip(zip); // Uses CEN
        readZipFs(zip); // Uses CEN
        if (false) {
            readZipInputStream(zip); // ZipException: invalid entry size (expected 0 but got 5 bytes)
        }

    }

    private void readZipFs(Path zip) throws IOException {
        readZipFs(zip, Map.of("entry", "hello"));
    }
    private void readZipFs(Path zip, Map<String, String> contents) throws IOException {
        try (FileSystem fs =
                     FileSystems.newFileSystem(zip, Map.of())) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes
                            attrs) throws IOException {

                        try (InputStream in = Files.newInputStream(file)) {

                            String fileName = file.getFileName().toString();
                            byte[] bytes = in.readAllBytes();

                            if (contents.containsKey(fileName)) {
                                assertEquals(bytes, contents.get(fileName).getBytes(StandardCharsets.UTF_8));
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    private static void readZipInputStream(Path zip) throws IOException {
        readZipInputStream(zip, Map.of("entry", "hello"));
    }
    private static void readZipInputStream(Path zip, Map<String, String> contents) throws IOException {
        try (ZipInputStream zi = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ( (e = zi.getNextEntry()) != null) {
                byte[] bytes = zi.readAllBytes();
                if (contents.containsKey(e.getName())) {
                    assertEquals(bytes, contents.get(e.getName()).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static void readZip(Path zip) throws IOException {
        readZip(zip, Map.of("entry", "hello"));
    }
    private static void readZip(Path zip, Map<String, String> contents) throws IOException {
        try (ZipFile z = new ZipFile(zip.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(z.entries());

            for (ZipEntry e : entries) {
                try (InputStream in = z.getInputStream(e)) {
                    byte[] bytes = in.readAllBytes();
                    if (contents.containsKey(e.getName())) {
                        assertEquals(bytes, contents.get(e.getName()).getBytes(StandardCharsets.UTF_8));
                    }
                }
            }
        }
    }

    private static byte[] invalidate(byte[] bytes) {
        byte[] copy = Arrays.copyOf(bytes, bytes.length);
        ByteBuffer.wrap(copy).put(0, INVALID_UTF8_BYTES);
        return copy;
    }

    private byte[] smallZip() throws IOException {


        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bout)) {
            ZipEntry entry = new ZipEntry("entry");
            entry.setComment("A comment");
            out.putNextEntry(entry);
            out.write("hello".getBytes(StandardCharsets.UTF_8));
        }
        return bout.toByteArray();
    }
    private byte[] smallUncompressedZip() throws IOException {


        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bout)) {
            byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
            ZipEntry entry = new ZipEntry("entry");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            CRC32 crc32 = new CRC32();
            crc32.update(content);
            entry.setCrc(crc32.getValue());
            entry.setComment("A comment");
            out.putNextEntry(entry);
            out.write(content);
        }
        return bout.toByteArray();
    }

}
