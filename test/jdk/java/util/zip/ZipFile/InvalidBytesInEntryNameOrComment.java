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
 *
 */

import jdk.test.lib.zink.Cen;
import jdk.test.lib.zink.Zink;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * @test
 * @summary Validate that opening ZIP files files with invalid UTF-8
 * byte sequences in the name or comment fields fails with ZipException
 * @enablePreview true
 * @library /test/lib
 * @run testng InvalidBytesInEntryNameOrComment
 */
public class InvalidBytesInEntryNameOrComment {

    // Example invalid UTF-8 byte sequence
    private static final byte[] INVALID_UTF8_BYTE_SEQUENCE = {(byte) 0xF0, (byte) 0xA4, (byte) 0xAD};

    // Expected ZipException message
    private static final String BAD_ENTRY_NAME_OR_COMMENT = "invalid CEN header (bad entry name or comment)";

    // ZIP file with invalid name field
    private Path invalidName;

    // ZIP file with invalid comment field
    private Path invalidComment;

    @BeforeTest
    public void setup() throws IOException {
        // Create a ZIP file with valid name and comment fields
        byte[] templateZip = templateZIP();

        // Create a ZIP with a CEN name field containing an invalid byte sequence
        invalidName = invalidName("invalid-name.zip", templateZip);

        // Create a ZIP with a CEN comment field containing an invalid byte sequence
        invalidComment = invalidComment("invalid-comment.zip", templateZip);
    }

    /**
     * Opening a ZipFile with an invalid UTF-8 byte sequence in
     * the name field of a CEN file header should throw a
     * ZipException with "bad entry name or comment"
     */
    @Test
    public void shouldRejectInvalidName() throws IOException {
        ZipException ex = expectThrows(ZipException.class, () -> {
            new ZipFile(invalidName.toFile());
        });
        assertEquals(ex.getMessage(), BAD_ENTRY_NAME_OR_COMMENT);
    }

    /**
     * Opening a ZipFile with an invalid UTF-8 byte sequence in
     * the comment field of a CEN file header should throw a
     * ZipException with "bad entry name or comment"
     */
    @Test
    public void shouldRejectInvalidComment() throws IOException {
        ZipException ex = expectThrows(ZipException.class, () -> {
            new ZipFile(invalidComment.toFile());
        });
        assertEquals(ex.getMessage(), BAD_ENTRY_NAME_OR_COMMENT);
    }

    /**
     * Make a valid ZIP file used as a template for invalid files
     */
    private byte[] templateZIP() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(bout)) {
            ZipEntry commentEntry = new ZipEntry("file");
            commentEntry.setComment("Comment");
            zo.putNextEntry(commentEntry);
        }
        return bout.toByteArray();
    }

    /**
     * Make a ZIP with invalid bytes in the CEN name field
     */
    private Path invalidName(String name, byte[] template) throws IOException {
        return Zink.stream(template)
                .map(Cen.map(cen -> cen.name(invalidate(cen.name()))))
                .collect(Zink.toFile(name));
    }


    /**
     * Make a ZIP with invalid bytes in the CEN comment field
     */
    private Path invalidComment(String name, byte[] template) throws IOException {
        return Zink.stream(template)
                .map(Cen.map(cen -> cen.comment(invalidate(cen.comment()))))
                .collect(Zink.toFile(name));
    }

    /**
     * Returns a copy of the byte array starting with an invalid UTF-8 byte sequence
      */
    private byte[] invalidate(byte[] bytes) {
        byte[] copy = bytes.clone();
        System.arraycopy(INVALID_UTF8_BYTE_SEQUENCE, 0,
                copy, 0, INVALID_UTF8_BYTE_SEQUENCE.length);
        return copy;
    }
}
