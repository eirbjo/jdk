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

public record ExtTs(byte flag, int modtime, int actime, int crtime) implements ExtField {

    public static final short ID = 0x5455;

    public static ExtTs read(ByteBuffer buffer) {
        byte flag = buffer.get();
        int modtime = -1;
        int actime = -1;
        int crtime = -1;
        if(buffer.remaining() >= 4) {
            modtime = buffer.getInt();
        }
        if(buffer.remaining() >= 4) {
            actime = buffer.getInt();
        }
        if(buffer.remaining() >= 4) {
            crtime = buffer.getInt();
        }
        return new ExtTs(flag, modtime, actime, crtime);
    }

    public static ExtTs of() {
        return new ExtTs((byte) 0, -1, -1 , -1);
    }
    public ExtTs lastModified(long time, TimeUnit unit) {
        return new ExtTs((byte) (flag | 0x1), (int) unit.toSeconds(time), actime, crtime);
    }

    public ExtTs lastAccessed(long time, TimeUnit unit) {
        return new ExtTs((byte) (flag | 0x2), modtime, (int) unit.toSeconds(time), crtime);
    }

    public ExtTs created(long time, TimeUnit unit) {
        return new ExtTs((byte) (flag | 0x4), modtime, actime, (int) unit.toSeconds(time));
    }

    @Override
    public short id() {
        return ID;
    }

    @Override
    public byte[] data() {
        ByteBuffer buffer = ByteBuffer.allocate(dsize()).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(flag);
        if(modtime != -1) {
            buffer.putInt(modtime);
        }
        if(actime != -1) {
            buffer.putInt(actime);
        }
        if(crtime != -1) {
            buffer.putInt(crtime);
        }

        return buffer.array();
    }

    @Override
    public short dsize() {
        short size = 1;
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
