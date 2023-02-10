/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/*
 * This benchmark naively explores String::startsWith performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class StringComparisons {

    public String longString = new String("jkljayeiksdhsdkjkdjkldfnbmnbdgfaddfflsdhbdkj");
    public String equallyLongString = new String("jkljayeiksdhsdkjkdjkldfnbmnbdgfaddfflsdhbdkj");

    public String longerString = new String("jkljayeiksdhsdkjkdjkldfnbmnbdgfaddfflsdhbdkj_");

    public String endsWithA= new String("jkljayeiksdhsdkjkdjkldfnbmnbdgfaddfflsdhbdkjA");
    public String endsWithB= new String("jkljayeiksdhsdkjkdjkldfnbmnbdgfaddfflsdhbdkjB");

    @Benchmark
    public void startsWith(Blackhole blackhole) {
        blackhole.consume(longerString.startsWith(longString));
    }

    @Benchmark
    public void compareTo(Blackhole blackhole) {
        blackhole.consume(endsWithA.compareTo(endsWithB));
    }

    @Benchmark
    public void regionMatches(Blackhole blackhole) {
        blackhole.consume(endsWithA.regionMatches( 0, endsWithB, 0, endsWithB.length()));
    }

    @Benchmark
    public void stringEquals(Blackhole blackhole) {
        blackhole.consume(endsWithA.equals( equallyLongString));
    }
}

