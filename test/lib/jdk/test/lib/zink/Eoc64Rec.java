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
import java.nio.channels.WritableByteChannel;
import java.util.function.Function;
import java.util.stream.Stream;

import static jdk.test.lib.zink.Zink.getBytes;
import static jdk.test.lib.zink.Zink.parseExt;

/**
 * Represents the Zip64 End of Central Directory record in the ZIP file format
 */
public record Eoc64Rec(long size,
                       short version,
                       short extractVersion,
                       int thisDisk,
                       int startDisk,
                       long numEntries,
                       long totalEntries,
                       long cenSize,
                       long cenOff,
                       ExtField[] extra) implements ZRec {
    static final int SIG = 0x06064b50;
    private static final int SIZE = 56;

    public static Function<ZRec, ZRec> map(Function<Eoc64Rec, Eoc64Rec> mapper) {
        return r -> switch (r) {
            case Eoc64Rec eRec -> mapper.apply(eRec);
            default -> r;
        };
    }
    static ZRec read(Zink.LEInput input) {
        long size = input.getLong();
        short version = input.getShort();
        short extractVersion = input.getShort();
        int thisDisk = input.getInt();
        int startDisk = input.getInt();
        long numEntries = input.getLong();
        long totalEntries = input.getLong();
        long cenSize = input.getLong();
        long cenOff = input.getLong();

        long variableSize =  size +12 - SIZE;
        byte[] extData = getBytes(input, (int) variableSize);
        ExtField[] extra = parseExt(extData);
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(SIG);
        buf.putLong(size);
        buf.putShort(version);
        buf.putShort(extractVersion);
        buf.putInt(thisDisk);
        buf.putInt(startDisk);

        buf.putLong(numEntries);
        buf.putLong(totalEntries);
        buf.putLong(cenSize);
        buf.putLong(cenOff);

        for (ExtField e : extra) {
            byte[] data = e.data();
            buf.putShort(e.id());
            buf.putShort(e.dsize());
            buf.put(data);
        }

        out.write(buf.flip());
    }

    public static Eoc64Rec of(Eoc eoc) {
        return new Eoc64Rec(SIZE - 12, (short) 45, (short) 45, eoc.thisDisk(), eoc.startDisk(), eoc.diskEntries(), eoc.totalEntries(), eoc.cenSize(), eoc.cenOffset(), new ExtField[0]);
    }

    @Override
    public long sizeOf() {
        return SIZE + Stream.of(extra).mapToInt(e -> 4 + e.data().length).sum();
    }

    public Eoc64Rec cenOff(long cenOff) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec cenSize(long cenSize) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec size(long size) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec version(short version) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
    public Eoc64Rec extractVersion(short extractVersion) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
    public Eoc64Rec thisDisk(int thisDisk) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec startDisk(int startDisk) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
    public Eoc64Rec numEntries(long numEntries) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
    public Eoc64Rec totalEntries(long totalEntries) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
    public Eoc64Rec cenOff(int cenOff) {
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
    public Eoc64Rec extra(ExtField[] extra) {
        int sizeOfVariableData = Stream.of(extra).mapToInt(e -> 4 + e.data().length).sum();
        long size = SIZE + sizeOfVariableData - 12;
        return new Eoc64Rec(size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
}
