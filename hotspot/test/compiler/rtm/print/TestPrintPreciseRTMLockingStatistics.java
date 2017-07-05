/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8031320
 * @summary Verify that rtm locking statistics contain proper information
 *          on overall aborts and locks count and count of aborts of
 *          different types. Test also verify that VM output does not
 *          contain rtm locking statistics when it should not.
 * @library /testlibrary /../../test/lib /compiler/testlibrary
 * @modules java.base/sun.misc
 *          java.management
 * @build TestPrintPreciseRTMLockingStatistics
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestPrintPreciseRTMLockingStatistics
 */

import java.util.*;

import jdk.test.lib.*;
import jdk.test.lib.cli.CommandLineOptionTest;
import jdk.test.lib.cli.predicate.AndPredicate;
import rtm.*;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

/**
 * Test verifies that VM output does not contain RTM locking statistics when it
 * should not (when PrintPreciseRTMLockingStatistics is off) and that with
 * -XX:+PrintPreciseRTMLockingStatistics locking statistics contains sane
 * total locks and aborts count as well as for specific abort types.
 */
public class TestPrintPreciseRTMLockingStatistics
        extends CommandLineOptionTest {
    private TestPrintPreciseRTMLockingStatistics() {
        super(new AndPredicate(new SupportedCPU(), new SupportedVM()));
    }

    @Override
    public void runTestCases() throws Throwable {
        verifyNoStatistics();
        verifyStatistics();
    }

    // verify that VM output does not contain
    // rtm locking statistics
    private void verifyNoStatistics() throws Throwable {
        verifyNoStatistics(AbortType.XABORT);

        verifyNoStatistics(AbortType.XABORT,
                "-XX:-PrintPreciseRTMLockingStatistics");

        verifyNoStatistics(AbortType.XABORT, "-XX:-UseRTMLocking",
                "-XX:+PrintPreciseRTMLockingStatistics");
    }

    // verify that rtm locking statistics contain information
    // about each type of aborts
    private void verifyStatistics() throws Throwable {
        verifyAbortsCount(AbortType.XABORT);
        verifyAbortsCount(AbortType.MEM_CONFLICT);
        verifyAbortsCount(AbortType.BUF_OVERFLOW);
        verifyAbortsCount(AbortType.NESTED_ABORT);
    }

    private void verifyNoStatistics(AbortType abortProvokerType,
            String... vmOpts) throws Throwable {
        AbortProvoker provoker = abortProvokerType.provoker();
        List<String> finalVMOpts = new LinkedList<>();
        Collections.addAll(finalVMOpts, vmOpts);
        Collections.addAll(finalVMOpts, AbortProvoker.class.getName(),
                abortProvokerType.toString());

        OutputAnalyzer outputAnalyzer = RTMTestBase.executeRTMTest(provoker,
                finalVMOpts.toArray(new String[finalVMOpts.size()]));

        outputAnalyzer.shouldHaveExitValue(0);

        List<RTMLockingStatistics> statistics = RTMLockingStatistics.fromString(
                outputAnalyzer.getOutput());

        Asserts.assertEQ(statistics.size(), 0, "VM output should not contain "
                + "any RTM locking statistics");
    }

    private void verifyAbortsCount(AbortType abortType) throws Throwable {
        AbortProvoker provoker = abortType.provoker();

        OutputAnalyzer outputAnalyzer = RTMTestBase.executeRTMTest(
                provoker,
                "-XX:+PrintPreciseRTMLockingStatistics",
                AbortProvoker.class.getName(),
                abortType.toString());

        outputAnalyzer.shouldHaveExitValue(0);

        List<RTMLockingStatistics> statistics = RTMLockingStatistics.fromString(
                provoker.getMethodWithLockName(),outputAnalyzer.getOutput());

        Asserts.assertGT(statistics.size(), 0, "VM output should contain one "
                + "rtm locking statistics entry for method "
                + provoker.getMethodWithLockName());

        RTMLockingStatistics lock = statistics.get(0);

        Asserts.assertGT(lock.getTotalAborts(), 0L,
                "RTM locking statistics should contain non zero total aborts "
                + "count");

        Asserts.assertGT(lock.getAborts(abortType), 0L, String.format(
                "RTM locking statistics should contain non zero aborts count "
                + "for abort reason %s", abortType));
    }

    public static void main(String args[]) throws Throwable {
        new TestPrintPreciseRTMLockingStatistics().test();
    }
}
