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
import java.io.OutputStream;
import java.io.Writer;
import java.nio.channels.WritableByteChannel;

/**
 * Represents the File Data in the ZIP file format
 */
public record FileData(Writer writer, long size) implements ZRec {
    void write(WritableByteChannel out) throws IOException {
        writer.write(out);
    }

    @Override
    public long sizeOf() {
        return size;
    }

    public interface Writer {
        public void write(WritableByteChannel out) throws IOException;
    }

    public FileData writer(Writer writer) {
        return new FileData(writer, size);
    }

    public FileData size(long size) {
        return new FileData(writer, size);
    }
}
