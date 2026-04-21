/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.server.adservices;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.os.Process;

import com.android.server.adservices.AdServicesShellCommand.Injector;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class AdServicesShellCommandTest {

    private static final String[] ALL_COMMANDS = new String[] {"help", "is-system-service-enabled"};

    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    @Rule public final Expect expect = Expect.create();

    private final StringWriter mOutStringWriter = new StringWriter();
    private final StringWriter mErrStringWriter = new StringWriter();

    private final PrintWriter mOut = new PrintWriter(mOutStringWriter);
    private final PrintWriter mErr = new PrintWriter(mErrStringWriter);

    @Mock private Flags mFlags;

    private AdServicesShellCommand mShellCmd;

    @Before
    public void setFixtures() {
        mShellCmd =
                new AdServicesShellCommand(
                        new Injector() {
                            @Override
                            int getCallingUid() {
                                return Process.SHELL_UID;
                            }
                        },
                        mFlags) {
                    @Override
                    public PrintWriter getOutPrintWriter() {
                        return mOut;
                    }

                    @Override
                    public PrintWriter getErrPrintWriter() {
                        return mErr;
                    }
                };
    }

    @After
    public void closeStreams() {
        try {
            mOut.close();
        } finally {
            mErr.close();
        }
    }

    @Test
    public void testOnHelp() {
        mShellCmd.onHelp();

        expectHelpOutputHasAllCommands(getOut());
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testOnCommand_wrongUid() {
        SecurityException e =
                assertThrows(
                        SecurityException.class,
                        () ->
                                new AdServicesShellCommand(
                                                new Injector() {
                                                    @Override
                                                    int getCallingUid() {
                                                        return 42;
                                                    }
                                                },
                                                mFlags)
                                        .onCommand("D'OH"));
        assertThat(e)
                .hasMessageThat()
                .isEqualTo(String.format(AdServicesShellCommand.WRONG_UID_TEMPLATE, 42));
    }

    @Test
    public void testExec_nullShowsHelp() {
        int result = runCmd((String[]) null);
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_emptyShowsHelp() {
        int result = runCmd("");
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_help() {
        int result = runCmd("help");
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_helpShort() {
        int result = runCmd("-h");
        expect.withMessage("result").that(result).isEqualTo(0);

        expectHelpOutputHasAllCommands(getOut());
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_invalidCommand() {
        int result = runCmd("D'OH!");
        expect.withMessage("result").that(result).isEqualTo(-1);

        expect.withMessage("out").that(getOut()).isEmpty();
        String err = getErr();
        expectHelpOutputHasAllCommands(err);
        expectHelpOutputHasMessages(err, "D'OH!");
    }

    @Test
    public void testExec_isSystemServiceEnabled_true() {
        mockAdServicesSystemServiceEnabled(true);
        int result = runCmd("is-system-service-enabled");
        expect.withMessage("result").that(result).isEqualTo(0);

        expect.withMessage("output of is-system-service-enabled")
                .that(getOut())
                .isEqualTo("true\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_false() {
        mockAdServicesSystemServiceEnabled(false);
        int result = runCmd("is-system-service-enabled");
        expect.withMessage("result").that(result).isEqualTo(0);

        expect.withMessage("output of is-system-service-enabled")
                .that(getOut())
                .isEqualTo("false\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_true_verbose() {
        mockAdServicesSystemServiceEnabled(true);
        int result = runCmd("is-system-service-enabled", "--verbose");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("true\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*true.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_false_verbose() {
        mockAdServicesSystemServiceEnabled(false);
        int result = runCmd("is-system-service-enabled", "--verbose");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("false\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*false.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_true_shortVerbose() {
        mockAdServicesSystemServiceEnabled(true);
        int result = runCmd("is-system-service-enabled", "-v");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("true\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*true.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_false_shortVerbose() {
        mockAdServicesSystemServiceEnabled(false);
        int result = runCmd("is-system-service-enabled", "-v");
        expect.withMessage("result").that(result).isEqualTo(0);

        String out = getOut();
        expect.withMessage("output of is-system-service-enabled").that(out).isNotEqualTo("false\n");
        expect.withMessage("output of is-system-service-enabled")
                .that(out)
                .containsMatch(
                        ".*Enabled.*false.*Default.*"
                                + PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*DeviceConfig.*"
                                + PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED
                                + ".*\n");
        expect.withMessage("err").that(getErr()).isEmpty();
    }

    @Test
    public void testExec_isSystemServiceEnabled_invalidOption() {
        int result = runCmd("is-system-service-enabled", "--D'OH!");
        expect.withMessage("result").that(result).isEqualTo(-1);

        expect.withMessage("out").that(getOut()).isEmpty();
        String err = getErr();
        expectHelpOutputHasAllCommands(err);
        expectHelpOutputHasMessages(err, "Invalid option");
        expectHelpOutputHasMessages(err, "--D'OH!");
    }

    private void expectHelpOutputHasAllCommands(String helpOutput) {
        expectHelpOutputHasMessages(helpOutput, ALL_COMMANDS);
    }

    private void expectHelpOutputHasMessages(String helpOutput, String... messages) {
        for (String msg : messages) {
            expect.withMessage("help output").that(helpOutput).contains(msg);
        }
    }

    private int runCmd(String... args) {
        return mShellCmd.exec(
                /* target= */ null,
                FileDescriptor.in,
                FileDescriptor.out,
                FileDescriptor.err,
                args);
    }

    private String getOut() {
        mOut.flush();
        return mOutStringWriter.toString();
    }

    private String getErr() {
        mErr.flush();
        return mErrStringWriter.toString();
    }

    // TODO(b/294423183): use AdServicesFlagSetter (if / when it supports mocking unit tests)
    private void mockAdServicesSystemServiceEnabled(boolean value) {
        when(mFlags.getAdServicesSystemServiceEnabled()).thenReturn(value);
    }
}
