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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public record ExtZip64(short dsize, long size, long csize, long locOff, int diskStart) implements ExtField {

    static final int ID = 0x1;

    static ExtField read(short dsize, ByteBuffer buffer) {

        short rem = dsize;
        long size = -1;
        long csize = -1;
        long locOff = -1;
        int diskStart = -1;

        if (rem >= Long.BYTES) {
            size = buffer.getLong();
            rem -= Long.BYTES;
        }
        if (rem >= Long.BYTES) {
            csize = buffer.getLong();
            rem -= Long.BYTES;
        }
        if (rem >= Long.BYTES) {
            locOff = buffer.getLong();
            rem -= Long.BYTES;
        }
        if (rem >= Integer.BYTES) {
            diskStart = buffer.getInt();
        }

        return new ExtZip64(dsize, size, csize, locOff, diskStart);
    }

    @Override
    public short id() {
        return ID;
    }


    @Override
    public byte[] data() {
        byte[] data = new byte[dsize];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if(buffer.remaining() >= Long.BYTES) {
            buffer.putLong(this.size());
        }
        if(buffer.remaining() >= Long.BYTES) {
            buffer.putLong(this.csize());
        }
        if(buffer.remaining() >= Long.BYTES) {
            buffer.putLong(this.locOff());
        }
        if(buffer.remaining() >= Integer.BYTES) {
            buffer.putInt(this.diskStart());
        }
        return data;
    }

    public static ExtZip64 ofLoc(Loc loc) {
        short dsize = 8 + 8;
        return new ExtZip64(dsize, loc.size(), loc.csize(), -1, -1);
    }

    public static ExtZip64 ofCen(Cen cen) {
        short dsize = 8 + 8 +8 +4;
        return new ExtZip64(dsize, cen.size(), cen.csize(), cen.locOff(), cen.diskStart());
    }



    public ExtZip64 size(long size) {
        return new ExtZip64(dsize, size, csize, locOff, diskStart);
    }

    public ExtZip64 csize(long csize) {
        return new ExtZip64(dsize, size, csize, locOff, diskStart);
    }

    public ExtZip64 locOff(long locOff) {
        return new ExtZip64(dsize, size, csize, locOff, diskStart);
    }

    public ExtZip64 diskStart(int diskStart) {
        return new ExtZip64(dsize, size, csize, locOff, diskStart);
    }

    interface Parser {
        ExtZip64 parse(ByteBuffer buffer);
    }
}
