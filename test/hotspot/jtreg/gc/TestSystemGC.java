/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

package gc;

/*
 * @test TestSystemGCSerial
 * @requires vm.gc.Serial
 * @summary Runs System.gc() with different flags.
 * @run main/othervm -XX:+UseSerialGC gc.TestSystemGC
 */

/*
 * @test TestSystemGCParallel
 * @requires vm.gc.Parallel
 * @summary Runs System.gc() with different flags.
 * @run main/othervm -XX:+UseParallelGC gc.TestSystemGC
 */

/*
 * @test TestSystemGCG1
 * @requires vm.gc.G1
 * @summary Runs System.gc() with different flags.
 * @run main/othervm -XX:+UseG1GC gc.TestSystemGC
 * @run main/othervm -XX:+UseG1GC -XX:+ExplicitGCInvokesConcurrent gc.TestSystemGC
 * @run main/othervm -XX:+UseLargePages gc.TestSystemGC
  */

/*
 * @test TestSystemGCShenandoah
 * @requires vm.gc.Shenandoah & !vm.graal.enabled
 * @summary Runs System.gc() with different flags.
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC gc.TestSystemGC
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseShenandoahGC -XX:+ExplicitGCInvokesConcurrent gc.TestSystemGC
 */
public class TestSystemGC {
  public static void main(String args[]) throws Exception {
    System.gc();
  }
}
