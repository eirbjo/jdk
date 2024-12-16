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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8346272
 * @summary verify that --no-compress-ext disables compression for specified file extensions
 * @run junit NoCompressTest
 */
public class NoCompressTest {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() ->
                    new RuntimeException("jar tool not found")
            );

    private static final byte[] FILE_CONTENT = "Hello".getBytes(StandardCharsets.UTF_8);

    // Directory with file structure to add using the jar command
    private Path scratchDir;

    // JAR archive file created by the jar command
    private Path jar = Path.of("no-compress.jar");


    @BeforeEach
    public void createTestFiles() throws Exception {
        scratchDir = Files.createDirectory(Path.of("no-compress-files"));
        Files.write(scratchDir.resolve("file.zip"), FILE_CONTENT);
        Files.write(scratchDir.resolve("file.jpg"), FILE_CONTENT);
        Files.createDirectories(scratchDir.resolve("d1"));
        Files.write(scratchDir.resolve("d1/file.JPG"), FILE_CONTENT);
        Files.write(scratchDir.resolve("file.txt"), FILE_CONTENT);
    }

    @AfterEach
    public void cleanup() throws IOException {
        // Delete scratch dir recursively
        Files.walkFileTree(scratchDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Stream<Arguments> arguments() {
        return Stream.of(
                // All files compressed by default
                Arguments.of("", Set.of()),
                // Also all files compressed
                Arguments.of("--no-compress-ext=", Set.of()),
                // Disable one extension
                Arguments.of("--no-compress-ext=.jpg", Set.of("file.jpg")),
                // Simple postfix match, dot is optional
                Arguments.of("--no-compress-ext=jpg", Set.of("file.jpg")),
                Arguments.of("--no-compress-ext=e.jpg", Set.of("file.jpg")),
                // Disable two extensions, separated by ":"
                Arguments.of("--no-compress-ext=.jpg:.zip", Set.of("file.zip", "file.jpg")));
    }

    /**
     * Verify that a adding a given command line option disables compression for
     * the given set of file paths.
     * @param option the option to add, if any
     * @param expectStored the set of non-compressed file paths to expect
     *
     * @throws IOException if an unexpected exception occurs
     */
    @ParameterizedTest
    @MethodSource("arguments")
    public void verifyNoCompression(String option, Set<String> expectStored) throws IOException {

        // Build the jar command line arguments
        StringBuilder command = new StringBuilder();
        command.append("--create --file ").append(jar).append(" ");
        if (command != null) {
            command.append(option).append(" ");
        }
        command.append("-C ").append(scratchDir.toAbsolutePath()).append(" ").append(".");

        // Run the jar tool
        int exitCode = JAR_TOOL.run(System.out, System.err, command.toString().split(" +"));
        if (exitCode != 0) {
            fail("JAR tool failed with exit code " + exitCode);
        }

        // Find the set of non-compressed file paths in the JAR
        Set<String> actualStored;
        try (var zf = new ZipFile(jar.toFile())) {
            actualStored = zf.stream()
                    .filter(e -> !e.isDirectory() && e.getMethod() == ZipEntry.STORED)
                    .map(ZipEntry::getName)
                    .collect(Collectors.toSet());
        }

        // Verify stored paths match expected
        assertEquals(expectStored, actualStored);
    }
}