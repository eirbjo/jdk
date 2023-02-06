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
import java.util.function.Function;

/**
 * Represents the Data Decriptor record in the ZIP file format
 */
public record Desc(boolean signed, boolean zip64, int crc, long csize, long size) implements ZRec {

    public static final int SIZE = 4 * 3;
    static final int SIG = 0x8074b50;

    static Desc read(Zink.LEInput input, int crcOrSig, boolean signed, boolean zip64) {
        int crc;
        if (signed) {
            crc = input.getInt();
        } else {
            crc = crcOrSig;
        }
        long csize;
        long size;
        if (zip64) {
            csize = input.getLong();
            size = input.getLong();
        } else {
            csize = input.getInt();
            size = input.getInt();
        }
        return new Desc(signed, zip64, crc, csize, size);
    }

    void write(Zink.LEOutputStream out) throws IOException {
        if(signed) {
            out.writeInt(SIG);
        }
        out.writeInt(crc);
        if (zip64) {
            out.writeLongs(csize, size);
        } else {
            out.writeInts((int) csize, (int) size);
        }
    }

    public static Function<ZRec, ZRec> map(Function<Desc, Desc> mapper) {
        return r -> switch (r) {
            case Desc desc -> mapper.apply(desc);
            default -> r;
        };
    }

    @Override
    public long sizeOf() {
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
