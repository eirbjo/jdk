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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static jdk.test.lib.zink.Zink.*;

/**
 * Represents the Central Directory Header in the ZIP file format
 */
public record Cen(short version,
                  short extractVersion,
                  short flags,
                  short method,
                  short time,
                  short date,
                  int crc,
                  int csize,
                  int size,
                  short nlen,
                  short elen,
                  short clen,
                  short diskStart,
                  short internalAttr,
                  int externalAttr,
                  int locOff,
                  byte[] name,
                  ExtField[] extra,
                  byte[] comment) implements ZRec {
    // The four byte signature of the Central Directory Header
    public static final int SIG = 0x02014b50;
    // The total size of fixed-size fields in the Central Directory Header
    private static final int SIZE = 46;

    // Value used in size and csize fields when the entry is in Zip64 format
    static final int ZIP64_SIZE_32 = 0xFFFFFFFF;
    // Value used in locOff field when the entry is in Zip64 format
    private static final short ZIP64_SIZE_16 = (short) 0xFFFF;

    // Read a Cen record from a ByteBuffer
    static Cen read(long index, LEInput input) {
        short version = input.getShort();
        short extractVersion = input.getShort();
        short flags = input.getShort();
        short method = input.getShort();
        short time = input.getShort();
        short date = input.getShort();
        int crc = input.getInt();
        int csize = input.getInt();
        int size = input.getInt();
        short nlen = input.getShort();
        short elen = input.getShort();
        short clen = input.getShort();
        short diskStart = input.getShort();
        short internalAttr = input.getShort();
        int externalAttr = input.getInt();
        int locOff = input.getInt();

        byte[] name = getBytes(input, nlen);
        byte[] extra = getBytes(input, elen);
        byte[] comment = getBytes(input, clen);

        ExtField[] extFields = parseExt(extra);

        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extFields, comment);
    }

    // Write this record to an OutputStream
    void write(Zink.LEOutputStream out) throws IOException {
        out.writeInt(SIG);
        out.writeShorts(version, extractVersion, flags, method, time, date);
        out.writeInts(crc, csize, size);
        out.writeShorts(nlen, elen, clen, diskStart, internalAttr);
        out.writeInts(externalAttr, locOff);
        out.write(name);
        for (ExtField e : extra) {
            out.writeShort(e.id());
            out.writeShort(e.dsize());
            out.write(e.data());
        }
        out.write(comment);
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

    public static Function<ZRec, ZRec> named(String name, Function<Cen, Cen> mapper) {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        return r -> switch (r) {
            case Cen cen when cen.isNamed(nameBytes)-> mapper.apply(cen);
            default -> r;
        };
    }

    private short sizeOf(ExtField[] extra) {
        return (short) Stream.of(extra).mapToInt(e -> e.data().length + 4).sum();
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

    public <T extends ExtField> Optional<T> extra(Class<T> type) {
        return Stream.of(extra)
                .filter(e -> type.isAssignableFrom(e.getClass()))
                .map(e -> (T)e)
                .findAny();
    }

    public Cen name(byte[] name) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, (short) name.length, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen comment(byte[] comment) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, (short) comment.length, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    @Override
    public long sizeOf() {
        return SIZE + name.length + sizeOf(extra) + comment.length;
    }

    public Cen locOff(int locOff) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen toZip64() {

        int size = ZIP64_SIZE_32;
        int csize = ZIP64_SIZE_32;
        short dstart = ZIP64_SIZE_16;
        int locOff = ZIP64_SIZE_32;

        ExtField[] extra = Stream.concat(Stream.of(ExtZip64.ofCen(this)),
                        Stream.of(this.extra).filter(e -> e.id() != 0x1))
                .toArray(ExtField[]::new);


        return size(size)
                .csize(csize)
                .diskStart(dstart)
                .locOff(locOff)
                .extra(extra);
    }

    public static Function<ZRec, ZRec> map(Function<Cen, Cen> mapper) {
        return r -> switch (r) {
            case Cen cen -> mapper.apply(cen);
            default -> r;
        };
    }

    public Cen csize(int csize) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen size(int size) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen extra(ExtField[] extra) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, sizeOf(extra), clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen mapExtra(Function<ExtField, ExtField> mapper) {
        ExtField[] extra = Stream.of(extra())
                .map(mapper)
                .toArray(ExtField[]::new);
        return extra(extra);
    }

    public Cen crc(int crc) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen version(short version) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen extractVersion(short extractVersion) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen flags(short flags) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen method(short method) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen time(short time) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen date(short date) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen diskStart(short diskStart) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen internalAttr(short internalAttr) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen externalAttr(int externalAttr) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public boolean isNamed(byte[] name) {
        return Arrays.equals(this.name, name);
    }

    public Cen nlen(short nlen) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen elen(short elen) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }

    public Cen clen(short clen) {
        return new Cen(version, extractVersion, flags, method, time, date, crc, csize, size, nlen, elen, clen, diskStart, internalAttr, externalAttr, locOff, name, extra, comment);
    }
}
