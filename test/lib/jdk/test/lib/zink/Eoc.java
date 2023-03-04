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
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import static jdk.test.lib.zink.Zink.getBytes;

/**
 * Represents the End of Central Directory record in the ZIP file format
 */
public record Eoc(short thisDisk,
                  short startDisk,
                  short diskEntries,
                  short totalEntries,
                  int cenSize,
                  int cenOffset,
                  byte[] comment) implements ZRec {
    static final int SIG = 0x06054b50;
    private static final int SIZE = 22;

    static ZRec read(ReadableByteChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(SIZE - Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        channel.read(buf);
        buf.flip();

        short thisDisk = buf.getShort();
        short startDisk = buf.getShort();
        short diskEntries = buf.getShort();
        short totalEntries = buf.getShort();
        int cenSize = buf.getInt();
        int cenOffset = buf.getInt();
        short clen = buf.getShort();
        byte[] comment = getBytes(channel, clen);

        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(SIG);
        buf.putShort(thisDisk);
        buf.putShort(startDisk);
        buf.putShort(diskEntries);
        buf.putShort(totalEntries);
        buf.putInt(cenSize);
        buf.putInt(cenOffset);
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

    public Eoc thisDisk(short thisDisk) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    public Eoc startDisk(short startDisk) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    public Eoc diskEntries(short diskEntries) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    public Eoc totalEntries(short totalEntries) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    public Eoc cenSize(int cenSize) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    public Eoc cenOffset(int cenOffset) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, comment);
    }

    public Eoc comment(byte[] comment) {
        return new Eoc(thisDisk, startDisk, diskEntries, totalEntries, cenSize, cenOffset, Arrays.copyOf(comment, comment.length));
    }

    @Override
    public long sizeOf() {
        return SIZE + comment.length;
    }

    public short clen() {
        return (short) comment.length;
    }
}
