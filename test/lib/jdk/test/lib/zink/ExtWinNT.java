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

public record ExtWinNT(int reserved, long mtime, long atime, long ctime) implements ExtField {

    public static final short ID = 0x000a;
    public static final short TIMETAG = 0x1;
    public static final short TIMETAG_SIZE = 24;

    private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;

    public static ExtWinNT read(ByteBuffer buffer) {
        int reserved = buffer.getInt();

        long mtime = -1;
        long atime = -1;
        long ctime = -1;

        while (buffer.remaining() > 0) {
            short tag = buffer.getShort();
            short size = buffer.getShort();
            if(tag == TIMETAG) {
                mtime = buffer.getLong();
                atime = buffer.getLong();
                ctime = buffer.getLong();
            }
        }
        return new ExtWinNT(reserved, mtime, atime, ctime);
    }

    public static ExtWinNT of(long mtime, long atime, long ctime, TimeUnit unit) {
        return new ExtWinNT(0, toWinNt(mtime, unit), toWinNt(atime, unit), toWinNt(ctime, unit));
    }

    public static long toWinNt(long jTime, TimeUnit unit) {
        return (unit.toMicros(jTime) - WINDOWS_EPOCH_IN_MICROSECONDS) * 10;
    }

    public static long toJava(long wtime) {
        return TimeUnit.MICROSECONDS.toMillis(wtime / 10 + WINDOWS_EPOCH_IN_MICROSECONDS);
    }

    public ExtWinNT lastModified(long time, TimeUnit unit) {
        return new ExtWinNT(reserved, toWinNt(time, unit), atime, ctime);
    }

    public ExtWinNT lastAccessed(long time, TimeUnit unit) {
        return new ExtWinNT(reserved, mtime, toWinNt(time, unit), ctime);
    }

    public ExtWinNT created(long time, TimeUnit unit) {
        return new ExtWinNT(reserved, mtime, atime, toWinNt(time, unit));
    }

    @Override
    public short id() {
        return ID;
    }
    @Override
    public byte[] data() {
        ByteBuffer buffer = ByteBuffer.allocate(dsize()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(reserved);
        buffer.putShort(TIMETAG);
        buffer.putShort(TIMETAG_SIZE);
        buffer.putLong(mtime);
        buffer.putLong(atime);
        buffer.putLong(ctime);

        return buffer.array();
    }

    @Override
    public short dsize() {
        return  Integer.BYTES // reserved
                + Short.BYTES // TIMETAG
                + Short.BYTES // TIMETAG_SIZE
                + Long.BYTES // mtime
                + Long.BYTES // atime
                + Long.BYTES; // ctime
    }
}
