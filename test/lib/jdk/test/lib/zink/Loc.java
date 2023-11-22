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
import java.time.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static jdk.test.lib.zink.Zink.*;

/**
 * Represents the Local File Header in the ZIP file format.
 * @param sig     local file header signature, 4 bytes
 * @param version version needed to extract, 2 bytes
 * @param flags   general purpose bit flag, 2 bytes
 * @param method  compression method, 2 bytes
 * @param time    last mod file time, 2 bytes
 * @param date    last mod file date, 2 bytes
 * @param crc     crc-32, 4 bytes
 * @param csize   compressed size, 4 bytes
 * @param size    uncompressed size, 4 bytes
 * @param nlen    file name length, 2 bytes
 * @param elen    extra field length, 2 bytes
 * @param name    file name (variable size)
 * @param extra   extra field (variable size)
 */
public record Loc(long sig,
                  int version,
                  int flags,
                  int method,
                  int time,
                  int date,
                  long crc,
                  long csize,
                  long size,
                  int nlen,
                  int elen,
                  byte[] name,
                  ExtField[] extra) implements ZRec {
    public Loc {
        sig = u32(sig);
        version = u16(version);
        flags = u16(flags);
        method = u16(method);
        time = u16(time);
        date = u16(date);
        crc = u32(crc);
        csize = u32(csize);
        size = u32(size);
        nlen = u16(nlen);
        elen = u16(elen);
    }
    public static final long SIG = 0x04034b50L;   // "PK\003\004";
    private static final int VER_45 = 45;
    private static final int VER_20 = 20;
    private static final int VER_10 = 10;

    private static final int STORED = 0;
    static final int DEFLATE = 8;
    public static final byte[] EMPTY_BYTES = new byte[0];
    private static final long ZIP64_SIZE = 0xFFFFFFFFL;
    private static final int SIZE = 30;

    static Loc read(ReadableByteChannel channel, ByteBuffer buf) throws IOException {
        channel.read(buf.limit(SIZE - Integer.BYTES).rewind());
        buf.flip();

        int version = Short.toUnsignedInt(buf.getShort());
        int flags = Short.toUnsignedInt(buf.getShort());
        int method = Short.toUnsignedInt(buf.getShort());
        int time = Short.toUnsignedInt(buf.getShort());
        int date = Short.toUnsignedInt(buf.getShort());
        long crc = Integer.toUnsignedLong(buf.getInt());
        long csize = Integer.toUnsignedLong(buf.getInt());
        long size = Integer.toUnsignedLong(buf.getInt());
        int nlen = Short.toUnsignedInt(buf.getShort());
        int elen = Short.toUnsignedInt(buf.getShort());

        byte[] name = getBytes(channel, nlen);
        byte[] extraBytes = getBytes(channel, elen);

        ExtField[] extra = parseExt(extraBytes);

        Loc loc = new Loc(
                SIG,
                version,
                flags,
                method,
                time,
                date,
                crc,
                csize,
                size,
                nlen,
                elen,
                name,
                extra);

        if(loc.elen() != elen) {
            throw new IllegalStateException("Unexpected elen");
        }
        return loc;
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt((int) sig);
        buf.putShort((short) version);
        buf.putShort((short) flags);
        buf.putShort((short) method);
        buf.putShort((short) time);
        buf.putShort((short) date);
        buf.putInt((int) crc);
        buf.putInt((int) csize);
        buf.putInt((int) size);
        buf.putShort((short) nlen);
        buf.putShort((short) elen);
        buf.put(name);

        for (ExtField e : extra) {
            byte[] data = e.data();
            buf.putShort((short) e.id());
            buf.putShort((short) e.dsize());
            buf.put(data);
        }
        out.write(buf.flip());
    }

    public static Predicate<? super ZRec> filter(Predicate<Loc> predicate) {
        return new Predicate<ZRec>() {
            Loc currentLoc;

            @Override
            public boolean test(ZRec zRec) {
                return switch (zRec) {
                    case Loc loc -> {
                        currentLoc = loc;
                        yield predicate.test(currentLoc);
                    }
                    case Desc desc -> predicate.test(currentLoc);
                    case FileData fileData -> predicate.test(currentLoc);
                    default -> true;
                };
            }
        };
    }

    public static Function<ZRec, ZRec> map(Function<Loc, Loc> mapper) {
        return map(loc -> true, mapper);
    }

    public static Function<ZRec, ZRec> map(Predicate<Loc> locPredicate, Function<Loc, Loc> mapper) {
        return r -> switch (r) {
            case Loc loc when locPredicate.test(loc) -> mapper.apply(loc);
            default -> r;
        };
    }

    public static Predicate<? super ZRec> remove(Predicate<Loc> predicate) {
        return filter(predicate.negate());
    }

    public static Function<ZRec, ZRec> rename(Function<String, String> renamer) {
        return rename(renamer, StandardCharsets.UTF_8);
    }

    public static Function<ZRec, ZRec> rename(Function<String, String> renamer, Charset charset) {
        return r -> switch (r) {
            case Loc loc -> {
                String name = new String(loc.name);
                String newName = renamer.apply(name);
                yield name.equals(newName) ? loc : loc.name(newName.getBytes(charset));
            }
            default -> r;
        };
    }

    public static Function<ZRec, ZRec> renameLocAndCen(Function<String, String> renamer) {
        return rename(renamer).andThen(Cen.rename(renamer));
    }
    public static Predicate<Loc> named(String name) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        return loc -> Arrays.equals(loc.name, nameBytes);
    }

    public boolean isZip64() {
        return size == 0xFFFFFFFFL;
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

    public Loc utf8(boolean useUtf8) {
        int flags = this.flags;
        if (useUtf8) {
            flags |= 0x800;
        } else {
            flags &= ~0x800;
        }
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    private int sizeOf(ExtField[] extra) {
        return Stream.of(extra).mapToInt(e -> e.dsize() + 4).sum();
    }

    public <T extends ExtField> Optional<T> extra(Class<T> type) {
        return Stream.of(extra)
                .filter(e -> type.isAssignableFrom(e.getClass()))
                .map(e -> (T)e)
                .findAny();
    }

    public Loc toZip64() {
        ExtField[] extra = Stream.concat(Stream.of(ExtZip64.ofLoc(this)),
                        Stream.of(this.extra).filter(e -> e.id() != ExtZip64.ID))
                .toArray(ExtField[]::new);

        return version(VER_45)
                .size(ZIP64_SIZE)
                .csize(ZIP64_SIZE)
                .extra(extra);
    }

    @Override
    public long sizeOf() {
        return SIZE + name.length + sizeOf(extra);
    }

    public Loc sig(long sig) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc version(int version) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc flags(int flags) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc method(int method) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc time(int time) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc date(int date) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc crc(long crc) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc csize(long csize) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc size(long size) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc nlen(int nlen) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc elen(int elen) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc name(byte[] name) {
        int nlen = name.length;
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name.clone(), extra);
    }

    public Loc extra(ExtField[] extra) {
        int elen = sizeOf(extra);
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra.clone());
    }
}
