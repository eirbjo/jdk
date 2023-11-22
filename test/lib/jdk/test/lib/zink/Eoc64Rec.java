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
 * Represents the Zip64 End of Central Directory record in the ZIP file format
 *
 * @param sig zip64 end of central dir signature, 4 bytes (0x06064b50)
 * @param size size of zip64 end of central directory record, 8 bytes
 * @param version version made by, 2 bytes
 * @param extractVersion version needed to extract, 2 bytes
 * @param thisDisk  number of this disk, 4 bytes
 * @param startDisk number of the disk with the start of the central directory, 4 bytes
 * @param numEntries total number of entries in the central directory on this disk, 8 bytes
 * @param totalEntries total number of entries in the central directory, 8 bytes
 * @param cenSize  size of the central directory, 8 bytes
 * @param cenOff  offset of start of central directory with respect to the starting
 *                disk number, 8 bytes
 * @param extra zip64 extensible data sector (variable size)
 */
public record Eoc64Rec(long sig,
                       long size,
                       int version,
                       int extractVersion,
                       long thisDisk,
                       long startDisk,
                       long numEntries,
                       long totalEntries,
                       long cenSize,
                       long cenOff,
                       ExtField[] extra) implements ZRec {
    public Eoc64Rec {
        sig = u32(sig);
        version = u16(version);
        extractVersion = u16(extractVersion);
        thisDisk = u32(thisDisk);
        startDisk = u32(startDisk);
    }
    static final int SIG = 0x06064b50;
    private static final int SIZE = 56;

    public static Function<ZRec, ZRec> map(Function<Eoc64Rec, Eoc64Rec> mapper) {
        return r -> switch (r) {
            case Eoc64Rec eRec -> mapper.apply(eRec);
            default -> r;
        };
    }

    static ZRec read(ReadableByteChannel channel, ByteBuffer buf) throws IOException {
        channel.read(buf.limit(SIZE - Integer.BYTES).rewind());
        buf.flip();

        long size = buf.getLong();
        int version = Short.toUnsignedInt(buf.getShort());
        int extractVersion = Short.toUnsignedInt(buf.getShort());
        long thisDisk = Integer.toUnsignedLong(buf.getInt());
        long startDisk = Integer.toUnsignedLong(buf.getInt());
        long numEntries = buf.getLong();
        long totalEntries = buf.getLong();
        long cenSize = buf.getLong();
        long cenOff = buf.getLong();

        long variableSize =  size +12 - SIZE;
        byte[] extData = getBytes(channel, (int) variableSize);
        ExtField[] extra = parseExt(extData);
        return new Eoc64Rec(SIG, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    void write(WritableByteChannel out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate((int) sizeOf()).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt((int) sig);
        buf.putLong(size);
        buf.putShort((short) version);
        buf.putShort((short) extractVersion);
        buf.putInt((int) thisDisk);
        buf.putInt((int) startDisk);

        buf.putLong(numEntries);
        buf.putLong(totalEntries);
        buf.putLong(cenSize);
        buf.putLong(cenOff);

        for (ExtField e : extra) {
            byte[] data = e.data();
            buf.putShort((short) e.id());
            buf.putShort((short) e.dsize());
            buf.put(data);
        }

        out.write(buf.flip());
    }

    public static Eoc64Rec of(Eoc eoc) {
        return new Eoc64Rec(SIG, SIZE - 12,45, 45, eoc.thisDisk(), eoc.startDisk(), eoc.diskEntries(), eoc.totalEntries(), eoc.cenSize(), eoc.cenOffset(), new ExtField[0]);
    }

    @Override
    public long sizeOf() {
        return SIZE + Stream.of(extra).mapToInt(e -> 4 + e.data().length).sum();
    }

    public Eoc64Rec sig(long sig) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec size(long size) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec version(int version) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec extractVersion(int extractVersion) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec thisDisk(long thisDisk) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec startDisk(long startDisk) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec numEntries(long numEntries) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec totalEntries(long totalEntries) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec cenSize(long cenSize) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec cenOff(long cenOff) {
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }

    public Eoc64Rec extra(ExtField[] extra) {
        int sizeOfVariableData = Stream.of(extra).mapToInt(e -> 4 + e.data().length).sum();
        long size = SIZE + sizeOfVariableData - 12;
        return new Eoc64Rec(sig, size, version, extractVersion, thisDisk, startDisk, numEntries, totalEntries, cenSize, cenOff, extra);
    }
}
