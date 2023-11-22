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

package jdk.test.lib.zink;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static jdk.test.lib.zink.Zink.*;

/**
 * Represents the Central Directory Header in the ZIP file format.
 *
 * @param sig     central file header signature, 4 bytes
 * @param version version made by, 2 bytes
 * @param extractVersion version needed to extract, 2 bytes
 * @param flags   general purpose bit flag made by, 2 bytes
 * @param method  compression method, 2 bytes
 * @param flags   general purpose bit flag, 2 bytes
 * @param method  compression method, 2 bytes
 * @param time    last mod file time, 2 bytes
 * @param date    last mod file date, 2 bytes
 * @param crc     crc-32, 4 bytes
 * @param csize   compressed size, 4 bytes
 * @param size    uncompressed size, 4 bytes
 * @param nlen    file name length, 2 bytes
 * @param elen    extra field length, 2 bytes
 * @param clen    file comment length, 2 bytes
 * @param diskStart disk number start, 2 bytes
 * @param internalAttr internal file attributes, 2 bytes
 * @param externalAttr external file attributes, 4 bytes
 * @param locOff relative offset of local header, 4 bytes
 * @param name file name (variable size)
 * @param extra extra field (variable size)
 * @param name file comment (variable size)
 */
public record Cen(long sig,
                  int version,
                  int extractVersion,
                  int flags,
                  int method,
                  int time,
                  int date,
                  long crc,
                  long csize,
                  long size,
                  int nlen,
                  int elen,
                  int clen,
                  int diskStart,
                  int internalAttr,
                  long externalAttr,
                  long locOff,
                  byte[] name,
                  ExtField[] extra,
                  byte[] comment) implements ZRec {
    public Cen {
        sig = u32(sig);
        version = u16(version);
        extractVersion = u16(extractVersion);
        flags = u16(flags);
        method = u16(method);
        time = u16(time);
        date = u16(date);

        crc = u32(crc);
        csize = u32(csize);
        size = u32(size);
        nlen = u16(nlen);
        elen = u16(elen);
        clen = u16(clen);
        diskStart = u16(diskStart);
        internalAttr = u16(internalAttr);
        externalAttr = u32(externalAttr);
        locOff = u32(locOff);
    }
    // The four byte signature of the Central Directory Header
    public static final long SIG = 0x02014b50L;
    // The total size of fixed-size fields in the Central Directory Header
    private static final int SIZE = 46;
    // Version 45, required for Zip64
    private static final int VER_45 = 45;
    // Value used in size and csize fields when the entry is in Zip64 format
    static final long ZIP64_SIZE_32 = 0xFFFFFFFFL;
    // Value used in locOff field when the entry is in Zip64 format
    private static final int ZIP64_SIZE_16 = 0xFFFF;

    // Read a Cen record from a ByteBuffer
    static Cen read(ReadableByteChannel channel, ByteBuffer buf) throws IOException {
        channel.read(buf.limit(SIZE - Integer.BYTES).rewind());
        buf.flip();

        int version = Short.toUnsignedInt(buf.getShort());
        int extractVersion = Short.toUnsignedInt(buf.getShort());
        int flags = Short.toUnsignedInt(buf.getShort());
        int method = Short.toUnsignedInt(buf.getShort());
        int time = Short.toUnsignedInt(buf.getShort());
        int date = Short.toUnsignedInt(buf.getShort());
        long crc = Integer.toUnsignedLong(buf.getInt());
        long csize = Integer.toUnsignedLong(buf.getInt());
        long size = Integer.toUnsignedLong(buf.getInt());
        int nlen = Short.toUnsignedInt(buf.getShort());
        int elen = Short.toUnsignedInt(buf.getShort());
        int clen = Short.toUnsignedInt(buf.getShort());
        int diskStart = Short.toUnsignedInt(buf.getShort());
        int internalAttr = Short.toUnsignedInt(buf.getShort());
        long externalAttr = Integer.toUnsignedLong(buf.getInt());
        long locOff = Integer.toUnsignedLong(buf.getInt());

        byte[] name = getBytes(channel, nlen);
        byte[] extra = getBytes(channel, elen);
        byte[] comment = getBytes(channel, clen);

        ExtField[] extFields = parseExt(extra);

        return new Cen(SIG, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extFields, comment);
    }


    // Write this record to an OutputStream
    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt((int) sig);
        buf.putShort((short) version);
        buf.putShort((short) extractVersion);
        buf.putShort((short) flags);
        buf.putShort((short) method);
        buf.putShort((short) time);
        buf.putShort((short) date);
        buf.putInt((int) crc);
        buf.putInt((int) csize);
        buf.putInt((int) size);
        buf.putShort((short) nlen);
        buf.putShort((short) elen);
        buf.putShort((short) clen);
        buf.putShort((short) diskStart);
        buf.putShort((short) internalAttr);
        buf.putInt((int) externalAttr);
        buf.putInt((int) locOff);
        buf.put(name);
        for (ExtField e : extra) {
            byte[] data = e.data();
            buf.putShort((short) e.id());
            buf.putShort((short) e.dsize());
            buf.put(data);
        }
        buf.put(comment);
        out.write(buf.flip());
    }

    public static Predicate<? super ZRec> filter(Predicate<Cen> predicate) {
        return r -> switch (r) {
            case Cen cen -> predicate.test(cen);
            default -> true;
        };
    }

    public static Function<ZRec, ZRec> map(Function<Cen, Cen> mapper) {
        return r -> switch (r) {
            case Cen cen -> mapper.apply(cen);
            default -> r;
        };
    }

    public static Predicate<? super ZRec> remove(Predicate<Cen> predicate) {
        return filter(predicate.negate());
    }

    public static Function<ZRec, ZRec> rename(Function<String, String> renamer) {
        return rename(renamer, StandardCharsets.UTF_8);
    }

    public static Function<ZRec, ZRec> rename(Function<String, String> renamer, Charset charset) {
        return r -> switch (r) {
            case Cen cen -> {
                String name = new String(cen.name);
                String newName = renamer.apply(name);
                yield name.equals(newName) ? cen : cen.name(newName.getBytes(charset));
            }
            default -> r;
        };
    }

    public static Function<ZRec, ZRec> map(Predicate<Cen> cenPredicate, Function<Cen, Cen> mapper) {
        return r -> switch (r) {
            case Cen cen when cenPredicate.test(cen) -> mapper.apply(cen);
            default -> r;
        };
    }

    public Cen mapExtra(Function<ExtField, ExtField> mapper) {
        ExtField[] extra = Stream.of(extra())
                .map(mapper)
                .toArray(ExtField[]::new);
        return extra(extra);
    }

    public static Predicate<Cen> named(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        return c -> Arrays.equals(c.name, nameBytes);
    }

    private int sizeOf(ExtField[] extra) {
        return Stream.of(extra).mapToInt(e -> e.data().length + 4).sum();
    }

    public LocalDate localDate() {
        return getLocalDate((short) date);
    }

    public LocalTime localTime() {
        return getLocalTime((short) time);
    }

    public LocalDateTime localDateTime() {
        return getLocalDateTime(localDate(), localTime());
    }

    @Override
    public long sizeOf() {
        return SIZE + name.length + sizeOf(extra) + comment.length;
    }

    public <T extends ExtField> Optional<T> extra(Class<T> type) {
        return Stream.of(extra)
                .filter(e -> type.isAssignableFrom(e.getClass()))
                .map(e -> (T) e)
                .findAny();
    }

    public Cen toZip64() {

        long size = ZIP64_SIZE_32;
        long csize = ZIP64_SIZE_32;
        int dstart = ZIP64_SIZE_16;
        long locOff = ZIP64_SIZE_32;

        ExtField[] extra = Stream.concat(Stream.of(ExtZip64.ofCen(this)),
                        Stream.of(this.extra).filter(e -> e.id() != 0x1))
                .toArray(ExtField[]::new);


        return version(VER_45)
                .extractVersion(VER_45)
                .size(size)
                .csize(csize)
                .diskStart(dstart)
                .locOff(locOff)
                .extra(extra);
    }

    public Cen sig(long sig) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen version(int version) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen extractVersion(int extractVersion) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen flags(int flags) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen method(int method) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen time(int time) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen date(int date) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen crc(long crc) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen csize(long csize) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen size(long size) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen nlen(int nlen) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen elen(int elen) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen clen(int clen) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen diskStart(int diskStart) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen internalAttr(int internalAttr) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen externalAttr(long externalAttr) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen locOff(long locOff) {
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen name(byte[] name) {
        int nlen = name.length;
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen extra(ExtField[] extra) {
        int elen = sizeOf(extra);
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen comment(byte[] comment) {
        int clen = comment.length;
        return new Cen(sig, version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }
}
