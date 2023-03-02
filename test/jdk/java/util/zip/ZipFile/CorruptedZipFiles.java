/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4770745 6218846 6218848 6237956
 * @summary test for correct detection and reporting of corrupted zip files
 * @author Martin Buchholz
 * @enablePreview true
 * @library /test/lib
 */

import jdk.test.lib.zink.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.*;
import java.io.*;
import static java.lang.System.*;
import static java.util.zip.ZipFile.*;

public class CorruptedZipFiles {
    static int passed = 0, failed = 0;

    static void fail(String msg) {
        failed++;
        err.println(msg);
    }

    static void unexpected(Throwable t) {
        failed++;
        t.printStackTrace();
    }

    public static void main(String[] args) throws Exception {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out))
        {
            ZipEntry e = new ZipEntry("x");
            zos.putNextEntry(e);
            zos.write((int)'x');
        }

        byte[] good = out.toByteArray();

        err.println("corrupted ENDSIZ");

        checkZipException(good,
                s -> s.map(Eoc.map(eoc -> eoc.cenSize(0xFF00))),
                ".*bad central directory size.*");

        err.println("corrupted ENDOFF");
        checkZipException(good,
                s -> s.map(Eoc.map(eoc -> eoc.cenOffset(Short.MAX_VALUE))),
                ".*bad central directory offset.*");

        err.println("corrupted CENSIG");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.sig(cen.sig()+1))),
                ".*bad signature.*");

        err.println("corrupted CENFLG");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.flags((short) (cen.flags() | 1)))),
                ".*encrypted entry.*");

        err.println("corrupted CENNAM 1");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.nlen((short) (cen.nlen()+1)))),
                ".*bad header size.*");

        err.println("corrupted CENNAM 2");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.nlen((short) (cen.nlen()-1)))),
                ".*bad header size.*");

        err.println("corrupted CENNAM 3");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.nlen((short) 0xfdfd))),
                ".*bad header size.*");

        err.println("corrupted CENEXT 1");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.elen((short) (cen.elen()+1)))),
                ".*bad header size.*");

        err.println("corrupted CENEXT 2");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.elen((short) 0xfdfd))),
                ".*bad header size.*");

        err.println("corrupted CENCOM");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.clen((short) (cen.clen()+1)))),
                ".*bad header size.*");

        err.println("corrupted CENHOW");
        checkZipException(good,
                s -> s.map(Cen.map(cen -> cen.method((short) 2))),
                ".*bad compression method.*");


        err.println("corrupted LOCSIG");
        checkZipExceptionInGetInputStream(good,
                s -> s.map(Loc.map(loc -> loc.sig(loc.sig()+1))),
                ".*bad signature.*");


        System.out.printf("passed = %d, failed = %d%n", passed, failed);
        if (failed > 0) throw new Exception("Some tests failed");
    }

    static int uniquifier = 432;

    static void checkZipExceptionImpl(byte[] data,
                                      Function<Stream<ZRec>, Stream<ZRec>> modifier,
                                      String msgPattern,
                                      boolean getInputStream) {
        String zipName = "bad" + (uniquifier++) + ".zip";
        try {
            Path zip = (Path) modifier.apply(Zink.stream(data)).collect(Zink.collect().disableOffsetFixing().toFile(zipName));
            try (ZipFile zf = new ZipFile(zipName)) {
                if (getInputStream) {
                    InputStream is = zf.getInputStream(new ZipEntry("x"));
                    is.read();
                }
            }
            fail("Failed to throw expected ZipException");
        } catch (ZipException e) {
            if (e.getMessage().matches(msgPattern))
                passed++;
            else
                unexpected(e);
        } catch (Throwable t) {
            unexpected(t);
        } finally {
            new File(zipName).delete();
        }
    }

    static void checkZipException(byte[] data, Function<Stream<ZRec>, Stream<ZRec>> modifier, String msgPattern) {
        checkZipExceptionImpl(data, modifier, msgPattern, false);
    }

    static void checkZipExceptionInGetInputStream(byte[] data, Function<Stream<ZRec>, Stream<ZRec>> modifier, String msgPattern) {
        checkZipExceptionImpl(data, modifier, msgPattern, true);
    }
}
