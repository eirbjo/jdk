/*
 * Copyright (c) 2024, 20255, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.ModuleInfoWriter;
import tests.JImageGenerator;

import java.io.BufferedOutputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_void;

/*
 * @test
 * @summary Make sure that ~20000 packages in a uber jar can be linked using jlink. Now that
 *          pagination is in place, the limitation is on the constant pool size, not number
 *          of packages.
 * @bug 8321413
 * @library ../lib /test/lib
 * @modules java.base/jdk.internal.jimage
 *          java.base/jdk.internal.module
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.jlink/jdk.tools.jlink.plugin
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.jlink/jdk.tools.jimage
 * @build tests.*
 * @run main/othervm -Xlog:init=debug -XX:+UnlockDiagnosticVMOptions -XX:+BytecodeVerificationLocal JLink20000Packages
 */
public class JLink20000Packages {

    // Name of the module used in this test
    private static final String moduleName = "bug8321413x";
    // Package name of the module main class
    private static final String testPackage = "testpackage";
    // Name of the module main class
    private static final String testClass = "JLink20000PackagesTest";

    public static void main(String[] args) throws Exception {
        Path src = Paths.get(moduleName);
        Files.createDirectories(src);
        Path jarPath = src.resolve(moduleName +".jar");
        Path imageDir = src.resolve("out-jlink");

        // Generate module with 20000 classes in unique packages
        try (JarOutputStream out = new JarOutputStream(new BufferedOutputStream(Files.newOutputStream(jarPath)))) {
            ModuleDescriptor.Builder mdesc = ModuleDescriptor.newModule(moduleName);
            Set<String> packageNames = new HashSet<>();
            for (int i = 0; i < 20_000; i++) {
                String packageName = "p" + i;
                packageNames.add(packageName);
                // Add export for this package
                mdesc.exports(packageName);

                // Generate a class file for this package
                String className = "C" + i;
                byte[] classData = ClassFile.of().build(ClassDesc.of(packageName, className), cb -> {});
                out.putNextEntry(new JarEntry(packageName + "/" + className +".class"));
                out.write(classData);
            }

            // Write the main class
            out.putNextEntry(new JarEntry(testPackage +"/" + testClass +".class"));
            out.write(generateMainClass());
            packageNames.add(testPackage);

            // Write the module descriptor
            mdesc.packages(packageNames);
            out.putNextEntry(new JarEntry("module-info.class"));
            ModuleInfoWriter.write(mdesc.build(), out);
        }

        JImageGenerator.getJLinkTask()
                .output(imageDir)
                .addJars(jarPath)
                .addMods(moduleName)
                .call()
                .assertSuccess();

        Path binDir = imageDir.resolve("bin").toAbsolutePath();
        Path bin = binDir.resolve("java");

        ProcessBuilder processBuilder = new ProcessBuilder(bin.toString(),
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+BytecodeVerificationLocal",
                "-m", moduleName + "/" + testPackage +"." + testClass);
        processBuilder.inheritIO();
        processBuilder.directory(binDir.toFile());
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if (exitCode != 0)
             throw new AssertionError("JLink20000PackagesTest failed to launch");
    }

    private static byte[] generateMainClass() {
        return ClassFile.of().build(ClassDesc.of(testPackage, testClass),
                cb -> {
                    cb.withMethod("main", MethodTypeDesc.of(CD_void, CD_String.arrayType()),
                            ACC_PUBLIC | ACC_STATIC, mb -> {
                                mb.withCode(cob -> cob.return_()
                                );
                            });
                });
    }
}
