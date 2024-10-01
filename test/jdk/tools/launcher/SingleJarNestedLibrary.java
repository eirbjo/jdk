/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4241361
   @enablePreview
   @library /test/lib
   @summary Verify that java -jar with a main class in a nested library
   @run junit SingleJarNestedLibrary
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SingleJarNestedLibrary {

    // ZIP file produced during tests
    private Path singleJar = Path.of("single.jar");

    /**
     * Delete the ZIP file produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(singleJar);
    }

    @Test
    public void javaDashJarWithNestedLibrary() throws Exception {

        String mainClassName = "com.example.HelloWorld";
        byte[] mainClass17 = makeMainClass(mainClassName, 17);
        byte[] mainClassBase = makeMainClass(mainClassName, JarFile.baseVersion().feature());
        byte[] library = makeLibraryWithMainClass(mainClassName, mainClassName, mainClassBase, 17, mainClass17);
        byte[] singleJar = bundleLibraryInSingleJar(mainClassName, "META-INF/lib/library.jar", library);


        Files.write(this.singleJar, singleJar);

        OutputAnalyzer a = ProcessTools.executeTestJava("-jar", this.singleJar.getFileName().toString());
        a.shouldHaveExitValue(0);
        assertEquals(a.getOutput(), "Hello 17\n");
    }

    private byte[] makeMainClass(String mainClassName, int version) {
        MethodTypeDesc mainSignature = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String.arrayType());
        ClassDesc javaLangSystem = ClassDesc.of("java.lang.System");
        ClassDesc printStream = ClassDesc.of("java.io.PrintStream");
        MethodTypeDesc MTD_void_String = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
        return ClassFile.of().build(ClassDesc.of(mainClassName),
                clb -> clb.withMethod("main", mainSignature, ClassFile.ACC_PUBLIC +ClassFile.ACC_STATIC,
                        mb -> mb.withCode(cob -> cob.getstatic(javaLangSystem, "out", printStream)
                                .ldc("Hello " + version)
                                .invokevirtual(printStream, "println", MTD_void_String)
                                .return_())
                )
        );
    }

    private byte[] makeLibraryWithMainClass(String mainClassName, String className, byte[] mainClass, int version, byte[] versionedClass) throws IOException {
        var internalName = mainClassName.replace('.', '/') + ".class";
        var out = new ByteArrayOutputStream();
        var man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        man.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        try (var zo = new JarOutputStream(out, man)) {
            zo.putNextEntry(new ZipEntry("META-INF/versions/" + version + "/" + internalName));
            zo.write(versionedClass);
            zo.putNextEntry(new ZipEntry(internalName));
            zo.write(mainClass);
        }
        return out.toByteArray();
    }

    private byte[] bundleLibraryInSingleJar(String mainClassName, String libraryPath, byte[] library) throws IOException {
        // Create the single-jar including the nested library
        Manifest man = new Manifest();
        man.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        man.getMainAttributes().put(Attributes.Name.MULTI_RELEASE, "true");
        man.getMainAttributes().put(Attributes.Name.CLASS_PATH, "jar:" + libraryPath);
        man.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClassName);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (var zo = new JarOutputStream(out, man)) {
            var entry = new ZipEntry(libraryPath);
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(library.length);
            CRC32 crc32 = new CRC32();
            crc32.update(library);
            entry.setCrc(crc32.getValue());
            zo.putNextEntry(entry);
            zo.write(library);
        }
        return out.toByteArray();
    }
}