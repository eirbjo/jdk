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

/**
 * @test
 * @bug 8302819
 * @modules java.base/sun.security.x509
 * @modules java.base/sun.security.tools.keytool
 * @summary Make sure jars with the legacy 'JAR Index' files are handled properly by
 * JarFile verification and JarInputStream
 * @run junit JarIndexLegacyJars
 */

import jdk.security.jarsigner.JarSigner;
import org.junit.jupiter.api.Test;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.jar.*;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;


public class JarIndexLegacyJars {


    /*
     * Validate that JarInputStream.getNextJarEntry correctly hides
     * the META-INF/MANIFEST.MF entry, even when the JAR is indexed.
     */
    @Test
    public void hasManifest() throws IOException {
        Path jar = createJar("manifest-and-index.jar", true);
        createIndex(jar);

        try (JarInputStream in = new JarInputStream(Files.newInputStream(jar))) {
            JarEntry first = in.getNextJarEntry();
            JarEntry second = in.getNextJarEntry();
            JarEntry third = in.getNextJarEntry();

            assertEquals("META-INF/INDEX.LIST", first.getName());
            assertEquals("HelloWorld.class", second.getName());
            assertNull(third);
            assertNotNull(in.getManifest());
        }
    }

    /*
     * Validate that JarInputStream.getNextJarEntry produces the expected
     * result for an indexed JAR with no manifest file
     */
    @Test
    public void noManifest() throws IOException {
        Path jar = createJar("no-manifest.jar", false);
        createIndex(jar);

        try (JarInputStream in = new JarInputStream(Files.newInputStream(jar))) {
            JarEntry first = in.getNextJarEntry();
            JarEntry second = in.getNextJarEntry();
            JarEntry third = in.getNextJarEntry();

            assertEquals("META-INF/INDEX.LIST", first.getName());
            assertEquals("HelloWorld.class", second.getName());
            assertNull(third);
            assertNull(in.getManifest());
        }
    }

    /*
     * Verify that a signed, indexed JAR have entries signed with the expected
     * certificates
     */
    @Test
    public void signedIndexedJar() throws Exception {
        Path jar = createJar("unsigned.jar", true);
        createIndex(jar);

        Path signed = signJar("signed.jar", jar, keyEntry("cn=duke"));

        try (JarFile jf = new JarFile(signed.toFile(), true)) {
            assertNotNull(jf.getManifest());
            List<JarEntry> entries = Collections.list(jf.entries());
            for (JarEntry entry : entries) {
                try (InputStream in = jf.getInputStream(entry)) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
                if (!isSignatureRelated(entry)) {
                    Certificate[] certificates = entry.getCertificates();
                    assertEquals(1, certificates.length);
                }
            }
        }
    }


    /*
     * Return true if the given JarEntry is 'signature related'
     */
    private static boolean isSignatureRelated(JarEntry entry) {
        return entry.getName().endsWith(".SF") || entry.getName().endsWith(".RSA");
    }

    /*
     * Create a JAR file with a given name, optionally adding a Manifest file
     */
    private Path createJar(String name, boolean hasManifest) throws IOException {
        Path jar = Path.of(name);

        // Create the JAR file, optionally with a Manifest file
        try (JarOutputStream jo = hasManifest ?
                new JarOutputStream(java.nio.file.Files.newOutputStream(jar), new Manifest())
                : new JarOutputStream(java.nio.file.Files.newOutputStream(jar))) {

            // Add a basic entry to the JAR
            jo.putNextEntry(new JarEntry("HelloWorld.class"));
            jo.write("hello".getBytes(StandardCharsets.UTF_8));
        }

        return jar;
    }

    /*
     * Run 'jar -i' on the given jar file
     */
    private void createIndex(Path jar) {
        // Add the JAR Index
        ToolProvider jarTool = ToolProvider.findFirst("jar").orElseThrow();
        jarTool.run(System.out, System.err, "-i", jar.toAbsolutePath().toString());
    }

    /*
     * Return a signed version of the given jar file
     */
    private static Path signJar(String name, Path jar, KeyStore.PrivateKeyEntry entry) throws Exception {
        Path s = Path.of(name);

        JarSigner signer = new JarSigner.Builder(entry)
                .signerName("zigbert")
                .digestAlgorithm("SHA-256")
                .signatureAlgorithm("SHA256withRSA")
                .build();

        try (ZipFile zip = new ZipFile(jar.toFile());
             OutputStream out = Files.newOutputStream(s)) {
            signer.sign(zip, out);
        }

        return s;
    }

    /*
     * Produce a PrivateKeyEntry useful for signing a JAR file
     */
    private static KeyStore.PrivateKeyEntry keyEntry(String dname) throws Exception {

        CertAndKeyGen gen = new CertAndKeyGen("RSA", "SHA256withRSA");

        gen.generate(1048); // Small key size makes test run faster

        var oneDay = TimeUnit.DAYS.toSeconds(1);
        Certificate cert = gen.getSelfCertificate(new X500Name(dname), oneDay);

        return new KeyStore.PrivateKeyEntry(gen.getPrivateKey(),
                new Certificate[] {cert});
    }
}
