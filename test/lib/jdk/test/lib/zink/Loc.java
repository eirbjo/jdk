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
 * Represents the Local File Header in the ZIP file format
 */
public record Loc(int sig,
                  short version,
                  short flags,
                  short method,
                  short time,
                  short date,
                  int crc,
                  int csize,
                  int size,
                  short nlen,
                  short elen,
                  byte[] name,
                  ExtField[] extra) implements ZRec {
    public static final int SIG = 0x04034b50;   // "PK\003\004";
    private static final short VER_45 = 45;
    private static final short VER_20 = 20;
    private static final short VER_10 = 10;

    private static final short STORED = 0;
    static final short DEFLATE = 8;
    public static final byte[] EMPTY_BYTES = new byte[0];
    private static final int ZIP64_SIZE = 0xFFFFFFFF;
    private static final int SIZE = 30;

    static Loc read(ReadableByteChannel channel, ByteBuffer buf) throws IOException {
        channel.read(buf.limit(SIZE - Integer.BYTES).rewind());
        buf.flip();

        short version = buf.getShort();
        short flags = buf.getShort();
        short method = buf.getShort();
        short time = buf.getShort();
        short date = buf.getShort();
        int crc = buf.getInt();
        int csize = buf.getInt();
        int size = buf.getInt();
        short nlen = buf.getShort();
        short elen = buf.getShort();

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
        buf.putInt(sig);
        buf.putShort(version);
        buf.putShort(flags);
        buf.putShort(method);
        buf.putShort(time);
        buf.putShort(date);
        buf.putInt(crc);
        buf.putInt(csize);
        buf.putInt(size);
        buf.putShort(nlen);
        buf.putShort(elen);
        buf.put(name);

        for (ExtField e : extra) {
            byte[] data = e.data();
            buf.putShort(e.id());
            buf.putShort(e.dsize());
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
        return size == -1;
    }

    public LocalDate localDate() {
        return getLocalDate(date);
    }

    public LocalTime localTime() {
        return getLocalTime(time);
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
        return new Loc(sig, version, (short) flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    private short sizeOf(ExtField[] extra) {
        return (short) Stream.of(extra).mapToInt(e -> e.dsize() + 4).sum();
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

    public Loc sig(int sig) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc version(short version) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc flags(short flags) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc method(short method) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc time(short time) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc date(short date) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc crc(long value) {
        int crc = (int) value & 0XFFFFFFFF;
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc csize(int csize) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc size(int size) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc nlen(short nlen) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc elen(short elen) {
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra);
    }

    public Loc name(byte[] name) {
        short nlen = (short) name.length;
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name.clone(), extra);
    }

    public Loc extra(ExtField[] extra) {
        short elen = sizeOf(extra);
        return new Loc(sig, version, flags, method, time, date, crc, csize, size, nlen, elen, name, extra.clone());
    }
}
