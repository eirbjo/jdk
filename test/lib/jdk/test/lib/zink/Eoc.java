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
import java.util.function.Function;
import java.util.stream.Stream;

import static jdk.test.lib.zink.Zink.*;

/**
 * Represents the End of Central Directory record in the ZIP file format
 */
public record Eoc(long sig,
                  int thisDisk,
                  int startDisk,
                  int diskEntries,
                  int totalEntries,
                  long cenSize,
                  long cenOffset,
                  int clen,
                  byte[] comment) implements ZRec {
    public Eoc {
        sig = u32(sig);
        thisDisk = u16(thisDisk);
        startDisk = u16(startDisk);
        diskEntries = u16(diskEntries);
        totalEntries = u16(totalEntries);
        cenSize = u32(cenSize);
        cenOffset = u32(cenOffset);
        clen = u16(clen);
    }
    static final long SIG = 0x06054b50L;
    private static final int SIZE = 22;
    private static final int ZIP64_16 = 0xFFFF;
    private static final long ZIP64_32 = 0xFFFFFFFFL;

    static ZRec read(ReadableByteChannel channel, ByteBuffer buf) throws IOException {
        channel.read(buf.limit(SIZE - Integer.BYTES).rewind());
        buf.flip();

        int thisDisk = Short.toUnsignedInt(buf.getShort());
        int startDisk = Short.toUnsignedInt(buf.getShort());
        int diskEntries = Short.toUnsignedInt(buf.getShort());
        int totalEntries = Short.toUnsignedInt(buf.getShort());
        long cenSize = Integer.toUnsignedLong(buf.getInt());
        long cenOffset = Integer.toUnsignedLong(buf.getInt());
        int clen = Short.toUnsignedInt(buf.getShort());
        byte[] comment = getBytes(channel, clen);

        return new Eoc(SIG, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt((int) sig);
        buf.putShort((short) thisDisk);
        buf.putShort((short) startDisk);
        buf.putShort((short) diskEntries);
        buf.putShort((short) totalEntries);
        buf.putInt((int) cenSize);
        buf.putInt((int) cenOffset);
        buf.putShort((short) comment.length);
        buf.put(comment);
        out.write(buf.flip());
    }


    public static Function<ZRec, ZRec> map(Function<Eoc, Eoc> mapper) {
        return r -> switch (r) {
            case Eoc eoc -> mapper.apply(eoc);
            default -> r;
        };
    }

    public static Function<ZRec, Stream<Eoc>> match() {
        return r -> switch (r) {
            case Eoc e -> Stream.of(e);
            default -> Stream.empty();
        };
    }

    public static Function<ZRec, Stream<ZRec>> flatMap(Function<Eoc, Stream<ZRec>> mapper) {
        return r -> switch (r) {
            case Eoc eoc -> mapper.apply(eoc);
            default -> Stream.of(r);
        };
    }

    @Override
    public long sizeOf() {
        return SIZE + comment.length;
    }

    public Eoc toZip64() {
        return new Eoc(sig, ZIP64_16, ZIP64_16, ZIP64_16, ZIP64_16, ZIP64_32, ZIP64_32, clen, comment);
    }

    public boolean isZip64() {
        return thisDisk == ZIP64_16;
    }

    public Eoc thisDisk(int thisDisk) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc startDisk(int startDisk) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc diskEntries(int diskEntries) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc totalEntries(int totalEntries) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc cenSize(long cenSize) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc cenOffset(long cenOffset) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc clen(int clen) {
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment);
    }

    public Eoc comment(byte[] comment) {
        int clen = comment.length;
        return new Eoc(sig, thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, clen, comment.clone());
    }
}
