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
 * Represents the Zip64 End of Central Directory locator in the ZIP file format
 */
public record Eoc64Loc(int eocDisk, long eocOff, int totalDisks) implements ZRec {
    static final int SIG = 0x07064b50;
    private static final int SIZE = 20;

    static ZRec read(ReadableByteChannel channel, ByteBuffer buf) throws IOException {
        channel.read(buf.limit(SIZE - Integer.BYTES).rewind());
        buf.flip();

        int eocDisk = buf.getInt();
        long cenOff = buf.getLong();
        int totalDisks = buf.getInt();

        return new Eoc64Loc(eocDisk, cenOff, totalDisks);
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(SIG);
        buf.putInt(eocDisk);
        buf.putLong(eocOff);
        buf.putInt(totalDisks);

        out.write(buf.flip());
    }

    public static Function<ZRec, ZRec> map(Function<Eoc64Loc, Eoc64Loc> mapper) {
        return r -> switch (r) {
            case Eoc64Loc eLoc -> mapper.apply(eLoc);
            default -> r;
        };
    }

    public static Eoc64Loc of(Eoc eoc) {
        return new Eoc64Loc(eoc.startDisk(), 0, 1);
    }

    @Override
    public long sizeOf() {
        return SIZE;
    }

    public Eoc64Loc eocOff(long eocOff) {
        return new Eoc64Loc(eocDisk, eocOff, totalDisks);
    }
}
