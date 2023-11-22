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
import java.util.concurrent.TimeUnit;

import static jdk.test.lib.zink.Zink.u32;

/**
 * Represents an extended timestamp field with time values in standard Unix
 * signed-long format, indicating the number of seconds since 1 January 1970 00:00:00.
 *
 * @param flag bit 0           if set, modification time is present
 *             bit 1           if set, access time is present
 *             bit 2           if set, creation time is present
 *             bits 3-7        reserved for additional timestamps; not set
 * @param modtime time of last modification, 4 bytes (optional)
 * @param actime  time of last access, 4 bytes (optional)
 * @param crtime  time of original creation, 4 bytes (optional)
 */
public record ExtTs(byte flag, long modtime, long actime, long crtime) implements ExtField {

    public ExtTs {
        if (modtime != -1) {
            modtime = u32(modtime);
        }
        if (actime != -1) {
            actime = u32(actime);
        }
        if (crtime != -1) {
            crtime = u32(crtime);
        }
    }

    public static final int ID = 0x5455;

    public static ExtTs read(int dsize, ByteBuffer buffer) {
        int rem = dsize;
        byte flag = buffer.get();
        rem -=1;
        long modtime = -1;
        long actime = -1;
        long crtime = -1;
        if(rem >= 4) {
            modtime = Integer.toUnsignedLong(buffer.getInt());
            rem -= Integer.BYTES;
        }
        if(rem >= 4) {
            actime = Integer.toUnsignedLong(buffer.getInt());
            rem -= Integer.BYTES;
        }
        if(rem >= 4) {
            crtime = Integer.toUnsignedLong(buffer.getInt());
            rem -= Integer.BYTES;
        }
        return new ExtTs(flag, modtime, actime, crtime);
    }

    public static ExtTs of() {
        return new ExtTs((byte) 0, -1, -1 , -1);
    }
    public ExtTs lastModified(long time, TimeUnit unit) {
        return new ExtTs((byte) (flag | 0x1), unit.toSeconds(time), actime, crtime);
    }

    public ExtTs lastAccessed(long time, TimeUnit unit) {
        return new ExtTs((byte) (flag | 0x2), modtime, unit.toSeconds(time), crtime);
    }

    public ExtTs created(long time, TimeUnit unit) {
        return new ExtTs((byte) (flag | 0x4), modtime, actime, unit.toSeconds(time));
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public byte[] data() {
        ByteBuffer buffer = ByteBuffer.allocate(dsize()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(flag);
        if(modtime != -1) {
            buffer.putInt((int) modtime);
        }
        if(actime != -1) {
            buffer.putInt((int) actime);
        }
        if(crtime != -1) {
            buffer.putInt((int) crtime);
        }

        return buffer.array();
    }

    @Override
    public int dsize() {
        int size = 1;
        if(modtime != -1) {
            size += Integer.BYTES;
        }
        if(actime != -1) {
            size += Integer.BYTES;
        }
        if(crtime != -1) {
            size += Integer.BYTES;
        }
        return size;
    }
}
