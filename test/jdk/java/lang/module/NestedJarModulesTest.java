/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @enablePreview
 * @modules java.base/jdk.internal.module
 * @library /test/lib
 * @build MultiReleaseJarTest
 *        jdk.test.lib.util.ModuleInfoWriter
 * @run testng NestedJarModulesTest
 * @summary Basic test of modular JARs as multi-release JARs
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.ModuleInfoWriter;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.*;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;


@Test
public class NestedJarModulesTest {

    private static final String MODULE_INFO = "module-info.class";

    private static final int VERSION = Runtime.version().major();

    // are multi-release JARs enabled?
    private static final boolean MULTI_RELEASE;
    static {
        String s = System.getProperty("jdk.util.jar.enableMultiRelease");
        MULTI_RELEASE = (s == null || Boolean.parseBoolean(s));
    }

    /**
     * Basic test of nested module JARs.
     */
    public void testBasic() throws Exception {
        // Module m1 contains a library class uses by m2
        String m1 = "m1";

        ModuleDescriptor m1Desc = ModuleDescriptor.newModule(m1)
                .requires("java.base")
                .exports("m1")
                .build();

        byte[] libraryClass = makeLibraryClass("m1.Library");
        byte[] m1Jar = new JarBuilder(m1)
                .moduleInfo("module-info.class", m1Desc)
                .resource("m1/Library.class", libraryClass)
                .build();

        // Module m2 contain a main class using library module m1
        String m2 = "m2";

        ModuleDescriptor m2Desc = ModuleDescriptor.newModule(m2)
                .requires("java.base")
                .requires("m1")
                .mainClass("m2.Main")
                .build();

        // m2 JAR bundles m1 as a nested JAR
        byte[] m2Jar = new JarBuilder(m2)
                .moduleInfo("module-info.class", m2Desc)
                .resource("m2/Main.class", makeMainClass("m2.Main"))
                .module("META-INF/modules/m1.jar", m1Jar)
                .build();

        Path m2File = Paths.get("m2.jar");
        Files.write(m2File, m2Jar);

        // find the modules
        ModuleFinder finder = ModuleFinder.of(m2File);
        Set<String> foundNames = finder.findAll().stream().map(mr -> mr.descriptor().name()).collect(Collectors.toSet());
        assertEquals(2, foundNames.size());
        assertTrue(foundNames.contains("m1"));
        assertTrue(foundNames.contains("m2"));

        {
            // Inspect m1
            Optional<ModuleReference> omref = finder.find(m1);
            assertTrue((omref.isPresent()));
            ModuleReference mref = omref.get();

            // check module packages
            m1Desc = mref.descriptor();
            Set<String> packages = m1Desc.packages();
            assertTrue(packages.contains("m1"));

            try (ModuleReader reader = mref.open()) {
                Optional<ByteBuffer> mbuffer = reader.read("m1/Library.class");
                assertTrue(mbuffer.isPresent());
                ByteBuffer buffer = mbuffer.get();
                try {
                    byte[] contents = new byte[buffer.capacity()];
                    buffer.get(contents);
                    assertEquals(contents, libraryClass);
                } finally {
                    reader.release(mbuffer.get());
                }

                reader.open("m1/Library.class").ifPresent(is -> {
                    try {
                        assertEquals(is.readAllBytes(), libraryClass);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

                Optional<URI> ouri = reader.find(MODULE_INFO);
                assertTrue(ouri.isPresent());
                URI uri = ouri.get();
                assertEquals(uri, URI.create("jar:" + m2File.toAbsolutePath().toString() + "!/META-INF/modules/m1.jar!/module-info.class"));
            }
        }

        OutputAnalyzer a = ProcessTools.executeTestJava("-p", m2File.getFileName().toString(), "-m", "m2");
        a.shouldHaveExitValue(0);
        assertEquals(a.getOutput(), "Hello from m1\n");
    }

    private byte[] makeMainClass(String name) {
        MethodTypeDesc mainSignature = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String.arrayType());
        ClassDesc javaLangSystem = ClassDesc.of("java.lang.System");
        ClassDesc printStream = ClassDesc.of("java.io.PrintStream");
        ClassDesc libraryClassDesc = ClassDesc.of("m1.Library");
        MethodTypeDesc MTD_void_String = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String);
        return ClassFile.of().build(ClassDesc.of(name),
                clb -> clb.withMethod("main", mainSignature, ClassFile.ACC_PUBLIC +ClassFile.ACC_STATIC,
                        mb -> mb.withCode(cob -> cob.getstatic(javaLangSystem, "out", printStream)
                                .invokestatic(libraryClassDesc, "getMessage", MethodTypeDesc.of(ConstantDescs.CD_String))
                                .invokevirtual(printStream, "println", MTD_void_String)
                                .return_())
                )
        );
    }

    private byte[] makeLibraryClass(String name) {
        ClassDesc CD_Module = ClassDesc.of("java.lang.Module");
        return ClassFile.of().build(ClassDesc.of(name),
                clb -> clb.withMethod("getMessage", MethodTypeDesc.of(ConstantDescs.CD_String), ClassFile.ACC_PUBLIC +ClassFile.ACC_STATIC,
                        mb -> mb.withCode(cob -> cob.ldc(ClassDesc.of("m1.Library"))
                                .invokevirtual(ConstantDescs.CD_Class, "getModule", MethodTypeDesc.of(CD_Module))
                                .invokevirtual(CD_Module, "getName", MethodTypeDesc.of(ConstantDescs.CD_String))
                                .invokedynamic(DynamicCallSiteDesc.of(
                                        MethodHandleDesc.ofMethod(
                                                DirectMethodHandleDesc.Kind.STATIC,
                                                ClassDesc.of("java.lang.invoke.StringConcatFactory"),
                                                "makeConcatWithConstants",
                                                MethodTypeDesc.of(ClassDesc.of("java.lang.invoke.CallSite"),
                                                        ConstantDescs.CD_MethodHandles_Lookup,
                                                        ConstantDescs.CD_String,
                                                        ConstantDescs.CD_MethodType,
                                                        ConstantDescs.CD_String,
                                                        ConstantDescs.CD_Object.arrayType())
                                                        ),
                                        "makeConcatWithConstants",
                                        MethodTypeDesc.of(ConstantDescs.CD_String, ConstantDescs.CD_String),
                                        "Hello from \u0001"))
                                .areturn()
                        )
                )
        );

    }

    /**
     * A builder of multi-release JAR files.
     */
    static class JarBuilder {
        private String name;
        private Map<String, byte[]> resources = new HashMap<>();
        private Map<String, byte[]> modules = new HashMap<>();
        private Map<String, ModuleDescriptor> descriptors = new HashMap<>();
        private String mainClass;
        private boolean multiRelease;

        JarBuilder(String name) {
            this.name = name;
        }

        /**
         * Adds a module-info.class to the JAR file.
         */
        JarBuilder moduleInfo(String name, ModuleDescriptor descriptor) {
            descriptors.put(name, descriptor);
            return this;
        }

        JarBuilder multiRelease(boolean multiRelease) {
            this.multiRelease = multiRelease;
            return this;
        }

        /**
         * Adds a dummy resource to the JAR file.
         */
        JarBuilder resource(String name, byte[] data) {
            resources.put(name, data);
            return this;
        }

        /**
         * Adds a dummy library to the JAR file.
         */
        JarBuilder module(String name, byte[] data) {
            modules.put(name, data);
            return this;
        }


        /**
         * Create the multi-release JAR, returning its file path.
         */
        byte[] build() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            Manifest man = new Manifest();
            Attributes attrs = man.getMainAttributes();
            attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            if (multiRelease) {
                attrs.put(Attributes.Name.MULTI_RELEASE, "true");
            }
            if (mainClass != null) {
                attrs.put(Attributes.Name.MAIN_CLASS, mainClass);
            }
            if (!modules.isEmpty()) {
                attrs.putValue("Module-Path", String.join(" ", modules.keySet()));
            }
            try (var zo = new JarOutputStream(out, man)) {
                // write the module-info.class
                for (Map.Entry<String, ModuleDescriptor> e : descriptors.entrySet()) {
                    zo.putNextEntry(new ZipEntry(e.getKey()));
                    ModuleDescriptor descriptor = e.getValue();
                    ModuleInfoWriter.write(descriptor, zo);
                }

                // write the dummy resources
                for (String name : resources.keySet()) {
                    zo.putNextEntry(new ZipEntry(name));
                    zo.write(resources.get(name));
                }

                // write the dummy libraries
                for (String name : modules.keySet()) {
                    byte[] data = modules.get(name);
                    ZipEntry entry = new ZipEntry(name);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(data.length);
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    entry.setCrc(crc.getValue());
                    zo.putNextEntry(entry);
                    zo.write(data);
                }
            }
            return out.toByteArray();
        }

        public JarBuilder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }
    }
}
