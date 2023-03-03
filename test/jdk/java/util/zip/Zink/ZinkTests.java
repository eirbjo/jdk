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
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.*;

import static jdk.test.lib.Asserts.*;
import static org.testng.Assert.assertEquals;

/**
 * @test
 * @summary Implementation tests for the Zink test lib
 * @enablePreview true
 * @library /test/lib
 * @build jdk.test.lib.zink.Zink
 * @run testng/othervm ZinkTests
 */
public class ZinkTests {

    public static final LocalDateTime DATE_TIME = LocalDateTime.of(2020, 1, 1, 13, 42, 34);
    public static final int MAX_CEN_SIZE = Integer.MAX_VALUE -22 -1;

    private static final byte[] INVALID_UTF8_BYTES = {(byte) 0xF0, (byte) 0xA4, (byte) 0xAD};

    @Test
    public void streamAndCollectShouldProduceSameBytes() throws IOException {

        byte[] zip = smallZip();

        byte[] transformed = Zink.stream(zip)
                .collect(Zink.toByteArray());

        assertEquals(transformed, zip);

    }

    @Test
    public void streamFromFileShouldProduceSameBytes() throws IOException, NoSuchAlgorithmException {

        Path zip = Path.of("bigzip.zip");

        byte[] fileData = new byte[1024];
        ThreadLocalRandom.current().nextBytes(fileData);

        try (ZipOutputStream zo = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip)))) {
            for (int i = 0; i < 10; i++) {
                zo.putNextEntry(new ZipEntry("entry_" + i));
                zo.write(fileData);
            }
        }

        try (var stream = Zink.stream(zip)) {
            Path transformed = stream
                    .collect(Zink.toFile("bigzip-transformed.zip"));
            assertEquals(digest(transformed), digest(zip));
        }
    }

    private byte[] digest(Path zip) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = MessageDigest.getInstance("SHA256");
        try (InputStream in = new DigestInputStream(Files.newInputStream(zip), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    @Test
    public void shouldCollectEmptyEntryStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("entry"));
        }

        Zink.stream(out.toByteArray())
                .collect(Zink.toByteArray());
    }

    @Test
    public void shouldConcatStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("a"));
            zo.write("a".getBytes(StandardCharsets.UTF_8));
            zo.putNextEntry(new ZipEntry("b"));
            zo.write("b".getBytes(StandardCharsets.UTF_8));
        }

        ByteArrayOutputStream out2 = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out2)) {
            zo.putNextEntry(new ZipEntry("c"));
            zo.write("c".getBytes(StandardCharsets.UTF_8));
            zo.putNextEntry(new ZipEntry("d"));
            zo.write("d".getBytes(StandardCharsets.UTF_8));
        }

        Path collected = Zink.concat(
                        Zink.stream(out.toByteArray()),
                        Zink.stream(out2.toByteArray())
                                .filter(r -> switch (r) {
                                    case Loc loc when loc.isNamed("c".getBytes(StandardCharsets.UTF_8)) -> false;
                                    case Cen cen when cen.isNamed("c".getBytes(StandardCharsets.UTF_8)) -> false;
                                    default -> true;
                                })
        ).collect(Zink.collect()
                .trace()
                .toFile("concat.zip"));

        try (ZipFile zf = new ZipFile(collected.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zf.entries());
            assertEquals(entries.size(), 3);
            assertEntry(zf, "a");
            assertEntry(zf, "b");
            assertEntry(zf, "d");
        }
    }

    @Test
    public void shouldUpdateEocCount() throws IOException {
        final byte[] name = "entry".getBytes(StandardCharsets.UTF_8);

        Path zip = Zink.stream(smallZip())
                // Filter out Loc, Desc, FileData for "entry"
                .filter(Loc.filter(loc -> !loc.isNamed(name)))
                // Filter out Cen for "entry"
                .filter(Cen.filter(cen -> !cen.isNamed(name)))
                .collect(Zink.collect().trace().toFile("filtered.zip"));

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            assertEquals(zf.size(), 1);
            // "entry" should be removed
            assertNull(zf.getEntry("entry"));
            // "uncompressed" should remain
            assertEntry(zf, "uncompressed");
        }

        Eoc eoc = Zink.stream(zip)
                .peek(r -> System.out.println(r))
                .flatMap(Eoc.match())
                .findAny().orElseThrow();
        assertEquals(eoc.diskEntries(), 1);
        assertEquals(eoc.totalEntries(), 1);
    }

    private void assertEntry(ZipFile zf, String name) throws IOException {
        ZipEntry entry = zf.getEntry(name);
        assertNotNull(entry);
        try (InputStream in = zf.getInputStream(entry)) {
            assertEquals(in.readAllBytes(), name.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void identityTransformsShouldProduceSameBytes() throws IOException {

        byte[] zip = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .collect(Zink.toByteArray());

        byte[] transformed = Zink.stream(zip)
                .map(r -> switch(r) {
                    case Loc loc -> loc.sig(loc.sig())
                            .crc(loc.crc())
                            .version(loc.version())
                            .flags(loc.flags())
                            .method(loc.method())
                            .time(loc.time())
                            .date(loc.date())
                            .crc(loc.crc())
                            .csize(loc.csize())
                            .size(loc.size())
                            .nlen(loc.nlen())
                            .elen(loc.elen())
                            .name(loc.name())
                            .extra(loc.extra());
                    case Desc desc -> desc
                            .signed(desc.signed())
                            .zip64(desc.zip64())
                            .csize(desc.csize())
                            .size(desc.size())
                            .crc(desc.crc());
                    case Cen cen -> cen.sig(cen.sig())
                            .version(cen.version())
                            .extractVersion(cen.extractVersion())
                            .flags(cen.flags())
                            .method(cen.method())
                            .time(cen.time())
                            .date(cen.date())
                            .crc(cen.crc())
                            .csize(cen.csize())
                            .size(cen.size())
                            .nlen(cen.nlen())
                            .elen(cen.elen())
                            .clen(cen.clen())
                            .diskStart(cen.diskStart())
                            .internalAttr(cen.internalAttr())
                            .externalAttr(cen.externalAttr())
                            .locOff(cen.locOff())
                            .name(cen.name())
                            .extra(cen.extra())
                            .comment(cen.comment());
                    case Eoc64Rec rec -> rec.size(rec.size())
                            .version(rec.version())
                            .extractVersion(rec.extractVersion())
                            .thisDisk(rec.thisDisk())
                            .numEntries(rec.numEntries())
                            .totalEntries(rec.totalEntries())
                            .cenSize(rec.cenSize())
                            .cenOff(rec.cenOff())
                            .extra(rec.extra());
                    case Eoc eoc -> eoc.thisDisk(eoc.thisDisk())
                            .startDisk(eoc.startDisk())
                            .diskEntries(eoc.diskEntries())
                            .totalEntries(eoc.totalEntries())
                            .cenSize(eoc.cenSize())
                            .cenOffset(eoc.cenOffset())
                            .comment(eoc.comment());
                    default -> r;

                }).collect(Zink.toByteArray());

        assertEquals(transformed, zip);

    }


    @Test
    public void shouldTransformIntoZip64() throws IOException {

        Path zip64 = Zink.stream(smallZip())
                .flatMap(Zink.toZip64())
                .collect(Zink.toFile("zip-64.zip"));

        try (ZipFile z = new ZipFile(zip64.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(z.entries());

            assertEquals(entries.size(), 2);

            for (ZipEntry e : entries) {
                try (InputStream in = z.getInputStream(z.getEntry("entry"))) {
                    assertEquals(in.readAllBytes(), "hello".getBytes(StandardCharsets.UTF_8));
                }
                try (InputStream in = z.getInputStream(z.getEntry("uncompressed"))) {
                    assertEquals(in.readAllBytes(), "uncompressed".getBytes(StandardCharsets.UTF_8));
                }
            }
        }

    }

    @Test
    public void shouldRoundtripExtraTimestamp() throws IOException {
        Instant now = Instant.now();

        // Extended timeZone field to add
        ExtField[] extra = ExtField.of(ExtTs.of()
                .lastModified(now.getEpochSecond(), TimeUnit.SECONDS)
                .lastAccessed(now.getEpochSecond() + 1, TimeUnit.SECONDS)
                .created(now.getEpochSecond() - 1, TimeUnit.SECONDS));

                // Inject ExtTs field into extra of Loc and Cen
        Path zip = Zink.stream(smallZip())
                .map(Loc.named("entry", loc -> loc.extra(extra)))
                .map(Cen.named("entry", cen -> cen.extra(extra)))
                .collect(Zink
                        .collect().trace()
                        .toFile(Path.of("extended-ts.zip")));

        // Asserts that ZipEntry contains time fields from the extTs above
        Consumer<ZipEntry> verifier = entry -> {
            assertEquals(entry.getLastModifiedTime(), FileTime.from(now.getEpochSecond(), TimeUnit.SECONDS));
            assertEquals(entry.getLastAccessTime(), FileTime.from(now.getEpochSecond() +1, TimeUnit.SECONDS));
            assertEquals(entry.getCreationTime(), FileTime.from(now.getEpochSecond() -1, TimeUnit.SECONDS));
        };

        // ZipFile reads extra from Cen
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry("entry");
            verifier.accept(entry);
        }
        // ZipInputStream reads extra from Loc
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry = z.getNextEntry();
            verifier.accept(entry);
        }
    }

    @Test
    public void shouldRenameEntries() throws IOException {

        Path zip = Zink.stream(smallZip())
                .map(Loc.renameLocAndCen(name -> switch (name) {
                    case "entry" -> name +".txt";
                    default -> name;
                }))
                .collect(Zink.toFile("renamed.zip"));

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            assertNull(zf.getEntry("entry"));
            assertNotNull(zf.getEntry("entry.txt"));
            assertNotNull(zf.getEntry("uncompressed"));
        }

        try (ZipInputStream zi = new ZipInputStream(Files.newInputStream(zip))) {
            Set<String> names = new HashSet<>();
            ZipEntry e;
            while ( (e = zi.getNextEntry()) != null) {
                names.add(e.getName());
            }
            assertEquals(names.size(), 2);
            assertFalse(names.contains("entry"));
            assertTrue(names.contains("entry.txt"));
            assertTrue(names.contains("uncompressed"));

        }



    }

    @Test
    public void shouldRoundtripWinNtTimestamp() throws IOException {
        Instant now = Instant.now();

        long micros = TimeUnit.MILLISECONDS.toMicros(now.toEpochMilli());

        // WinNT extra field to add (resulution is 10th of micro)
        ExtField[] extra = ExtField.of(ExtWinNT.of(micros,
                micros + 1,
                micros - 1,
                TimeUnit.MICROSECONDS));

        // Asserts that ZipEntry contains time fields from the WinNt above
        Consumer<ZipEntry> verifier = entry -> {
            assertEquals(entry.getLastModifiedTime(), FileTime.from(micros, TimeUnit.MICROSECONDS));
            assertEquals(entry.getLastAccessTime(), FileTime.from(micros + 1, TimeUnit.MICROSECONDS));
            assertEquals(entry.getCreationTime(), FileTime.from(micros - 1, TimeUnit.MICROSECONDS));
        };

        // Inject the WinNT extra field into Loc and Cen
        Path withTs = Zink.stream(smallZip())
                .map(Loc.named("entry", loc -> loc.extra(extra)))
                .map(Cen.named("entry", cen -> cen.extra(extra)))
                .collect(Zink.collect()
                        .trace()
                        .toFile(Path.of("extended-winnt.zip")));

        // ZipFile reads extra from Cen
        try (ZipFile zf = new ZipFile(withTs.toFile())) {
            ZipEntry entry = zf.getEntry("entry");
            verifier.accept(entry);
        }
        // ZipInputStream reads extra from Loc
        try (ZipInputStream z = new ZipInputStream(Files.newInputStream(withTs))) {
            ZipEntry entry = z.getNextEntry();
            verifier.accept(entry);
        }
    }


    @Test
    public void shouldParseSmallZip() throws IOException {

        // Use ZipOutputStream to create a ZIP with two entries, one compressed, one uncompressed
        var zip = smallZip();

        // Values expected to be found in first entry
        String entryNameText = "entry";
        String entryCommentText = "A comment";
        String entryContentText = "hello";
        byte[] entryName = entryNameText.getBytes(StandardCharsets.UTF_8);
        byte[] entryComment = entryCommentText.getBytes(StandardCharsets.UTF_8);
        byte[] entryContent = entryContentText.getBytes(StandardCharsets.UTF_8);
        byte[] deflated = deflate(entryContent);

        // Values expected to be found in second entry
        String uncompressedNameText = "uncompressed";
        String uncompressedEntryContentText = "uncompressed";
        byte[] uncompressedEntryName = uncompressedNameText.getBytes(StandardCharsets.UTF_8);
        byte[] uncompressedEntryContent = uncompressedEntryContentText.getBytes(StandardCharsets.UTF_8);

        // Calculate the offset and size of the CEN
        int cenOff = 0, cenSize = 0, off = 0;

        // Collect records into a list
        List<ZRec> recs = Zink.stream(zip).collect(Collectors.toList());
        // Find CEN offset and size
        for (var rec : recs) {
            if(rec instanceof Cen && cenOff == 0) {
                cenOff = off; // First CEN record
            } else if (rec instanceof Eoc) {
                cenSize = off - cenOff; // End of CEN, find size
            }
            // Track updated offset
            off += rec.sizeOf();
        }


        // Records should have the expected types and order
        assertEquals(recs.stream().map(Object::getClass).collect(Collectors.toList()),
                List.of(Loc.class,
                        FileData.class,
                        Desc.class,
                        Loc.class,
                        FileData.class,
                        Cen.class,
                        Cen.class,
                        Eoc.class));

        // Check the records

        // Local file header
        checkLoc(recs.get(0), 8, 0x8 | 0x800, entryName, 0, 0, 0);

        //  FileData (deflated)
        {
            FileData data = (FileData) recs.get(1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            data.writer().write(out);
            byte[] buf = out.toByteArray();
            int mismatch = Arrays.mismatch(deflated, 0, deflated.length,
                    buf, 0, buf.length);
            assertEquals(mismatch, -1);
        }

        // Data descriptor
        {
            Desc desc = (Desc) recs.get(2);
            assertTrue(desc.signed());
            assertEquals(desc.crc(), (int) getCrc(entryContent));
            assertEquals(desc.csize(), deflated.length);
            assertEquals(desc.size(), entryContentText.length());
        }

        // Local file header
        {
            checkLoc(recs.get(3), 0,  0x800, uncompressedEntryName, (int) getCrc(uncompressedEntryContent),
                    uncompressedEntryContent.length, uncompressedEntryContent.length);

        }
        // FileData, uncompressed
        {
            FileData data = (FileData) recs.get(4);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            data.writer().write(out);
            byte[] buf = out.toByteArray();
            assertEquals(new String(buf, StandardCharsets.UTF_8),
                    uncompressedEntryContentText);
        }

        // (No data descriptor for uncompressed file)

        // Central directory file header
        checkCen((Cen) recs.get(5), entryName, entryComment, deflated.length, entryContent.length, 0x8 | 0x800, 20, 20, 8, (int) getCrc(entryContent), 0);

        // Central directory file header
        checkCen((Cen) recs.get(6), uncompressedEntryName, new byte[0], uncompressedEntryContent.length, uncompressedEntryContent.length, 0x800, 10, 10, 0, (int) getCrc(uncompressedEntryContent), 67);

        // Check End of central directory
        checkEoc(recs.get(7), 2, cenOff, cenSize);
    }

    private void checkEoc(ZRec zRec, int entries, int cenOff, int cenSize) {
        Eoc eoc = (Eoc) zRec;
        assertEquals(eoc.thisDisk(), 0);
        assertEquals(eoc.startDisk(), 0);
        assertEquals(eoc.diskEntries(), entries);
        assertEquals(eoc.totalEntries(), entries);
        assertEquals(eoc.cenSize(), cenSize);
        assertEquals(eoc.cenOffset(), cenOff);
        assertEquals(eoc.clen(), 0);
        assertEquals(eoc.comment(), new byte[0]);
    }

    private void checkCen(ZRec rec, byte[] name, byte[] comment, int csize, int size, int flags, int version, int extractVersion, int method, int crc, int locOff) {
        Cen cen = (Cen) rec;
        assertEquals(cen.version() >> 8, 0); // Java
        assertEquals(cen.version() & 0XFF, version); // 30 = version 3.0
        assertEquals(cen.extractVersion(), extractVersion); // 45 = version 4.0
        assertEquals(cen.flags(), flags); // data descriptor | utf-8
        assertEquals(cen.method(), method);
        assertEquals(cen.localDate(), DATE_TIME.toLocalDate());
        assertEquals(cen.localTime(), DATE_TIME.toLocalTime());
        assertEquals(cen.crc(), crc);
        assertEquals(cen.csize(), csize);
        assertEquals(cen.size(), size);
        assertEquals(cen.nlen(), name.length);
        assertEquals(cen.elen(), 2 + 2 + 1 + 4);
        assertEquals(cen.clen(), comment.length);
        assertEquals(cen.internalAttr(), 0);
        assertEquals(cen.externalAttr(), 0);
        assertEquals(cen.locOff(), locOff);
        assertEquals(cen.name(), name);
        assertEquals(cen.extra().length, 1);
        // Parse extended timestamp
        ExtTs ts = cen.extra(ExtTs.class).orElseThrow();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(FileTime.from(ts.modtime(), TimeUnit.SECONDS).toInstant(), ZoneId.systemDefault());
        assertEquals(localDateTime, DATE_TIME);
        // Comment
        assertEquals(cen.comment(), comment);
    }

    private byte[] deflate(byte[] entryContent) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
        def.setInput(entryContent);
        def.finish();
        byte[] deflate = new byte[entryContent.length * 2];
        int size = def.deflate(deflate);
        byte[] result = new byte[size];
        System.arraycopy(deflate, 0, result, 0, size);
        return result;
    }

    private void checkLoc(ZRec zRec, int method, int flags, byte[] entryName, int crc, int csize, int size) {
        Loc loc = (Loc) zRec;
        assertEquals(loc.version(), method == 8 ? 20 : 10);
        assertEquals(loc.flags(), flags);
        assertEquals(loc.method(), method);
        assertEquals(loc.localDate(), DATE_TIME.toLocalDate());
        assertEquals(loc.localTime(), DATE_TIME.toLocalTime());

        assertEquals(loc.crc(), crc);
        assertEquals(loc.csize(), csize);
        assertEquals(loc.size(), size);

        assertEquals(loc.nlen(), entryName.length);


        assertEquals(loc.extra().length, 1);

        ExtTs extendedTs = loc.extra(ExtTs.class).orElseThrow();

        LocalDateTime localDateTime = LocalDateTime.ofInstant(FileTime.from(extendedTs.modtime(), TimeUnit.SECONDS).toInstant(), ZoneId.systemDefault());
        assertEquals(localDateTime, DATE_TIME);
        assertEquals(loc.name(), entryName);
    }


    @Test
    public void shouldParseZip64() throws IOException {

        // ZIP64 created by running: echo "HELLO" | zip > hello.zip
        var zip64 = "504b03042d000000000028a844566ed7acfdffffffffffffffff01001400"+
                "2d010010000600000000000000060000000000000048454c4c4f0a504b01"+
                "021e032d000000000028a844566ed7acfd06000000060000000100000000"+
                "00000001000000b011000000002d504b06062c000000000000001e032d00"+
                "0000000000000000010000000000000001000000000000002f0000000000"+
                "00003900000000000000504b060700000000680000000000000001000000"+
                "504b050600000000010001002f000000390000000000".trim();

        // Expected values
        String filenameText = "-";
        String contentText = "HELLO\n";
        byte[] filename = filenameText.getBytes(StandardCharsets.UTF_8);
        byte[] content = contentText.getBytes(StandardCharsets.UTF_8);
        int crc = (int) getCrc(content);
        LocalDate date = LocalDate.of(2023, 2, 4);
        LocalTime time = LocalTime.of(21, 1, 16);

        // Collect records into a list
        List<ZRec> recs = Zink.stream(HexFormat.of().parseHex(zip64))
                .collect(Collectors.toList());

        // Check that records have the expected types and order
        assertEquals(recs.stream().map(Object::getClass).collect(Collectors.toList()),
                List.of(Loc.class, FileData.class, Cen.class, Eoc64Rec.class, Eoc64Loc.class, Eoc.class));

        // Check Local file header
        {
            Loc loc = (Loc) recs.get(0);
            assertEquals(loc.version(), 45);
            assertEquals(loc.flags(), 0);
            assertEquals(loc.method(), 0);
            assertEquals(loc.localDate(), date);
            assertEquals(loc.localTime(), time);

            assertEquals(loc.crc(), crc);

            assertEquals(loc.csize(), -1);
            assertEquals(loc.size(), -1);

            assertEquals(loc.nlen(), filename.length);
            assertEquals(loc.elen(), 20);

            assertEquals(loc.name(), filename);

            ExtZip64 z64 = loc.extra(ExtZip64.class).orElseThrow();
            assertEquals(z64.csize(), 6);
            assertEquals(z64.size(), 6);
        }
        // Check FileData
        {
            FileData data = (FileData) recs.get(1);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            data.writer().write(out);
            byte[] buf = out.toByteArray();
            assertEquals(new String(buf, StandardCharsets.UTF_8), contentText);
        }
        // Check Central directory file header
        {
            Cen cen = (Cen) recs.get(2);
            assertEquals(cen.version() >> 8, 3); // 3 = Unix
            assertEquals(cen.version() & 0XFF, 30); // 30 = version 3.0
            assertEquals(cen.extractVersion(), 45); // 45 = version 4.0
            assertEquals(cen.flags(), 0);
            assertEquals(cen.method(), 0);
            assertEquals(cen.localDate(), date);
            assertEquals(cen.localTime(), time);
            assertEquals(cen.crc(), crc);
            assertEquals(cen.csize(), content.length);
            assertEquals(cen.size(), content.length);
            assertEquals(cen.nlen(), filename.length);
            assertEquals(cen.elen(), 0);
            assertEquals(cen.clen(), 0);
            assertEquals(cen.internalAttr(), 0x1); // ASCII text
            assertEquals(cen.externalAttr() >> 16, 0x11b0);
            assertEquals(cen.externalAttr() & 0xFF, 0);
            assertEquals(cen.locOff(), 0);
            assertEquals(cen.name(), filename);
            assertEquals(cen.extra(), new byte[0]);
            assertEquals(cen.comment(), new byte[0]);
        }

        // Check ZIP64 end of central directory record
        {
            Eoc64Rec rec = (Eoc64Rec) recs.get(3);
            assertEquals(rec.size(), 56-12);
            assertEquals(rec.version() >> 8, 3); // 3 = Unix
            assertEquals(rec.version() & 0XFF, 30); // 30 = version 3.0
            assertEquals(rec.thisDisk(), 0);
            assertEquals(rec.startDisk(), 0);
            assertEquals(rec.numEntries(), 1L);
            assertEquals(rec.totalEntries(), 1L);
            assertEquals(rec.cenSize(), 47L);
            assertEquals(rec.cenOff(), 57L);
            assertEquals(rec.extra(), new ExtField[0]);
        }

        // Check ZIP64 end of central directory locator
        {
            Eoc64Loc rec = (Eoc64Loc) recs.get(4);
            assertEquals(rec.eocDisk(), 0);
            assertEquals(rec.eocOff(), 104);
            assertEquals(rec.totalDisks(), 1L);
        }

        // Check End of central directory
        {
            checkEoc(recs.get(5), 1, 57, 47);
        }

    }

    private long getCrc(byte[] content) {
        CRC32 crc32 = new CRC32();
        crc32.update(content);
        return crc32.getValue();
    }

    private byte[] smallZip() throws IOException {


        var fileTime = FileTime.from(DATE_TIME.toInstant(ZoneId.systemDefault().getRules().getOffset(DATE_TIME)));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();

        try (ZipOutputStream out = new ZipOutputStream(bout)) {
            {
                ZipEntry entry = new ZipEntry("entry");
                entry.setLastModifiedTime(fileTime);
                entry.setComment("A comment");
                out.putNextEntry(entry);
                out.write("hello".getBytes(StandardCharsets.UTF_8));
            }
            {
                byte[] content = "uncompressed".getBytes(StandardCharsets.UTF_8);
                ZipEntry entry = new ZipEntry("uncompressed");
                entry.setLastModifiedTime(fileTime);
                entry.setMethod(0);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(getCrc(content));
                out.putNextEntry(entry);
                out.write(content);
            }
        }
        return bout.toByteArray();
    }

    private String formatHex(byte[] bytes) throws IOException {
        HexFormat format = HexFormat.of();
        StringBuilder sb = new StringBuilder("");
        for(int i = 0; i < bytes.length; i++) {
            if(i % 16 == 0) {
                sb.append("\n");
            } else {
                sb.append(" ");
            }
            sb.append(format.formatHex(bytes, i, i+1));
        }
        return sb.toString();
    }
}
