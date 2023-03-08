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

/**
 * Represents the Data Decriptor record in the ZIP file format
 */
public record Desc(boolean signed, boolean zip64, int crc, long csize, long size) implements ZRec {

    public static final int SIZE = 4 * 3;
    static final int SIG = 0x8074b50;

    static Desc read(ReadableByteChannel channel, ByteBuffer buf, int crcOrSig, boolean signed, boolean zip64) throws IOException {
        channel.read(buf.limit(sizeOf(signed, zip64) - Integer.BYTES).rewind());
        buf.flip();

        int crc;
        if (signed) {
            crc = buf.getInt();
        } else {
            crc = crcOrSig;
        }
        long csize;
        long size;
        if (zip64) {
            csize = buf.getLong();
            size = buf.getLong();
        } else {
            csize = buf.getInt();
            size = buf.getInt();
        }
        return new Desc(signed, zip64, crc, csize, size);
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf())
                .order(ByteOrder.LITTLE_ENDIAN);
        if(signed) {
            buf.putInt(SIG);
        }
        buf.putInt(crc);
        if (zip64) {
            buf.putLong(csize);
            buf.putLong(size);
        } else {
            buf.putInt((int) csize);
            buf.putInt((int) size);
        }
        buf.flip();
        out.write(buf);
    }

    public static Function<ZRec, ZRec> map(Function<Desc, Desc> mapper) {
        return r -> switch (r) {
            case Desc desc -> mapper.apply(desc);
            default -> r;
        };
    }

    @Override
    public long sizeOf() {
        return sizeOf(signed, zip64);
    }

    private static int sizeOf(boolean signed, boolean zip64) {
        int size = Integer.BYTES; // CRC

        if (signed) {
            size += Integer.BYTES;
        }

        if (zip64) {
            size += 2 * Long.BYTES;
        } else {
            size += 2 * Integer.BYTES;
        }

        return size;
    }

    public Desc csize(long csize) {
        return new Desc(signed, zip64, crc, csize, size);
    }

    public Desc size(long size) {
        return new Desc(signed, zip64, crc, csize, size);
    }

    public Desc crc(int crc) {
        return new Desc(signed, zip64, crc, csize, size);
    }

    public Desc toZip64() {
        return new Desc(signed, true, crc, csize, size);
    }

    public Desc signed(boolean signed) {
        return new Desc(signed, zip64, crc, csize, size);
    }

    public Desc zip64(boolean zip64) {
        return new Desc(signed, zip64, crc, csize, size);
    }
}
