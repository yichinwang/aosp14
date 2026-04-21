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

package com.android.adservices.common;

import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_ECHO;
import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_HELP;
import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_IS_ALLOWED_ATTRIBUTION_ACCESS;
import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.adservices.common.AdServicesShellCommandHandler.CMD_SHORT_HELP;
import static com.android.adservices.common.AdServicesShellCommandHandler.ERROR_EMPTY_COMMAND;
import static com.android.adservices.common.AdServicesShellCommandHandler.ERROR_TEMPLATE_INVALID_ARGS;
import static com.android.adservices.common.AdServicesShellCommandHandler.HELP_ECHO;
import static com.android.adservices.common.AdServicesShellCommandHandler.HELP_IS_ALLOWED_ATTRIBUTION_ACCESS;
import static com.android.adservices.common.AdServicesShellCommandHandler.HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS;
import static com.android.adservices.common.AdServicesShellCommandHandler.HELP_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.content.Context;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.truth.Expect;

import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

@SpyStatic(AppManifestConfigHelper.class)
public final class AdServicesShellCommandHandlerTest extends AdServicesExtendedMockitoTestCase {

    private static final String PKG_NAME = "d.h.a.r.m.a";
    private static final String ENROLLMENT_ID = "42";
    private static final String USES_SDK = "true";

    // mCmd is used on most tests methods, excepted those that runs more than one command
    private final OneTimeCommand mCmd = new OneTimeCommand(expect);

    @Test
    public void testInvalidConstructor() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> new AdServicesShellCommandHandler(mCmd.context, (PrintWriter) null));
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdServicesShellCommandHandler(
                                /* context= */ null, mock(PrintWriter.class)));
    }

    @Test
    public void testRun_invalidArgs() throws Exception {
        assertThrows(NullPointerException.class, () -> mCmd.run(/* args...= */ (String[]) null));
        assertThrows(IllegalArgumentException.class, () -> mCmd.run(/* args...= */ new String[0]));
    }

    @Test
    public void testRunHelp() throws Exception {
        String result = mCmd.runInvalid(CMD_HELP);

        assertHelpContents(result);
    }

    @Test
    public void testRunHelpShort() throws Exception {
        String result = mCmd.runInvalid(CMD_SHORT_HELP);

        assertHelpContents(result);
    }

    @Test
    public void testRun_noCommand() throws Exception {
        String result = mCmd.runInvalid("");

        expect.withMessage("result of ''").that(result).isEqualTo(ERROR_EMPTY_COMMAND + "\n");
    }

    @Test
    public void testRun_invalidCommand() throws Exception {
        String cmd = "I can't believe this command is valid";
        String result = mCmd.runInvalid(cmd);

        expect.withMessage("result of '%s'", cmd).that(result).contains("Unknown command: " + cmd);
    }

    @Test
    public void testRunEcho_invalid() throws Exception {
        // no args
        expectInvalidArgument(HELP_ECHO, CMD_ECHO);
        // empty message
        expectInvalidArgument(HELP_ECHO, CMD_ECHO, "");
        // more than 1 arg
        expectInvalidArgument(HELP_ECHO, CMD_ECHO, "4", "8", "15", "16", "23", "42");
    }

    @Test
    public void testRunEcho_valid() throws Exception {
        String result = mCmd.runValid(CMD_ECHO, "108");

        expect.withMessage("result of '%s 108'", CMD_ECHO).that(result).isEqualTo("108\n");
    }

    @Test
    public void testRunIsAllowedAttributionAccess_invalid() throws Exception {
        // no args
        expectInvalidArgument(
                HELP_IS_ALLOWED_ATTRIBUTION_ACCESS, CMD_IS_ALLOWED_ATTRIBUTION_ACCESS);
        // missing id
        expectInvalidArgument(
                HELP_IS_ALLOWED_ATTRIBUTION_ACCESS, CMD_IS_ALLOWED_ATTRIBUTION_ACCESS, PKG_NAME);
        // empty pkg
        expectInvalidArgument(
                HELP_IS_ALLOWED_ATTRIBUTION_ACCESS,
                CMD_IS_ALLOWED_ATTRIBUTION_ACCESS,
                "",
                ENROLLMENT_ID);
        // empty id
        expectInvalidArgument(
                HELP_IS_ALLOWED_ATTRIBUTION_ACCESS,
                CMD_IS_ALLOWED_ATTRIBUTION_ACCESS,
                PKG_NAME,
                "");
    }

    @Test
    public void testRunIsAllowedAttributionAccess_valid() throws Exception {
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedAttributionAccess(
                                        PKG_NAME, ENROLLMENT_ID));

        expect.withMessage(
                        "result of %s %s %s",
                        CMD_IS_ALLOWED_ATTRIBUTION_ACCESS, PKG_NAME, ENROLLMENT_ID)
                .that(mCmd.runValid(CMD_IS_ALLOWED_ATTRIBUTION_ACCESS, PKG_NAME, ENROLLMENT_ID))
                .isEqualTo("true\n");
    }

    @Test
    public void testRunIsAllowedCustomAudiencesAccess_invalid() throws Exception {
        // no args
        expectInvalidArgument(
                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS, CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS);
        // missing id
        expectInvalidArgument(
                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                PKG_NAME);
        // empty pkg
        expectInvalidArgument(
                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                "",
                ENROLLMENT_ID);
        // empty id
        expectInvalidArgument(
                HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                PKG_NAME,
                "");
    }

    @Test
    public void testRunIsAllowedCustomAudiencesAccess_valid() throws Exception {
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedCustomAudiencesAccess(
                                        PKG_NAME, ENROLLMENT_ID));

        expect.withMessage(
                        "result of %s %s %s",
                        CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS, PKG_NAME, ENROLLMENT_ID)
                .that(
                        mCmd.runValid(
                                CMD_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS, PKG_NAME, ENROLLMENT_ID))
                .isEqualTo("true\n");
    }

    @Test
    public void testRunIsAllowedTopicsAccess_invalid() throws Exception {
        // no args
        expectInvalidArgument(HELP_IS_ALLOWED_TOPICS_ACCESS, CMD_IS_ALLOWED_TOPICS_ACCESS);
        // missing id
        expectInvalidArgument(
                HELP_IS_ALLOWED_TOPICS_ACCESS, CMD_IS_ALLOWED_TOPICS_ACCESS, PKG_NAME);
        // missing sdk
        expectInvalidArgument(
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                PKG_NAME,
                ENROLLMENT_ID);
        // empty pkg
        expectInvalidArgument(
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                "",
                ENROLLMENT_ID,
                USES_SDK);
        // empty id
        expectInvalidArgument(
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                PKG_NAME,
                "",
                USES_SDK);
        // empty sdk
        expectInvalidArgument(
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                PKG_NAME,
                ENROLLMENT_ID,
                "");
        // non-boolean sdk
        expectInvalidArgument(
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                PKG_NAME,
                ENROLLMENT_ID,
                "D'OH!");
    }

    @Test
    public void testRunIsAllowedTopicsAudiencesAccess_valid() throws Exception {
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedTopicsAccess(
                                        /* useSandboxCheck= */ true,
                                        PKG_NAME,
                                        ENROLLMENT_ID));

        expect.withMessage(
                        "result of %s %s %s %s",
                        CMD_IS_ALLOWED_TOPICS_ACCESS, PKG_NAME, ENROLLMENT_ID, USES_SDK)
                .that(
                        mCmd.runValid(
                                CMD_IS_ALLOWED_TOPICS_ACCESS, PKG_NAME, ENROLLMENT_ID, USES_SDK))
                .isEqualTo("true\n");
    }

    private void assertHelpContents(String help) {
        expect.withMessage("help")
                .that(help.split("\n"))
                .asList()
                .containsExactly(
                        HELP_ECHO,
                        HELP_IS_ALLOWED_ATTRIBUTION_ACCESS,
                        HELP_IS_ALLOWED_CUSTOM_AUDIENCES_ACCESS,
                        HELP_IS_ALLOWED_TOPICS_ACCESS);
    }

    private void expectInvalidArgument(String syntax, String... args) throws IOException {
        OneTimeCommand cmd = new OneTimeCommand(expect);

        String expectedResult =
                String.format(ERROR_TEMPLATE_INVALID_ARGS, Arrays.toString(args), syntax) + "\n";
        String actualResult = cmd.runInvalid(args);

        expect.withMessage("result of %s", Arrays.toString(args))
                .that(actualResult)
                .isEqualTo(expectedResult);
    }

    /** Helper to run a command and get the output. */
    private static final class OneTimeCommand {
        private final StringWriter mOutStringWriter = new StringWriter();
        private final PrintWriter mOut = new PrintWriter(mOutStringWriter);
        public final Expect expect;

        public final Context context = mock(Context.class);
        public final AdServicesShellCommandHandler cmd =
                new AdServicesShellCommandHandler(context, mOut);

        private boolean mOutCalled;

        private OneTimeCommand(Expect expect) {
            this.expect = expect;
        }

        /**
         * Runs a command that is expected to return a positive result.
         *
         * @return command output
         */
        String runValid(String... args) throws IOException {
            int result = run(args);
            expect.withMessage("result of run(%s)", Arrays.toString(args))
                    .that(result)
                    .isAtLeast(0);

            return getOut();
        }

        /**
         * Runs a command that is expected to return a negative result.
         *
         * @return command output
         */
        String runInvalid(String... args) throws IOException {
            int result = run(args);
            expect.withMessage("result of run(%s)", Arrays.toString(args))
                    .that(result)
                    .isLessThan(0);

            return getOut();
        }

        /**
         * Runs the given command.
         *
         * @return command result
         */
        int run(String... args) throws IOException {
            return cmd.run(args);
        }

        /**
         * Returns the output of the command.
         *
         * <p>Can only be called once per test, as there is no way to reset it, which could cause
         * confusion for the test developer.
         */
        String getOut() throws IOException {
            if (mOutCalled) {
                throw new IllegalStateException("getOut() already called");
            }
            mOut.flush();
            String out = mOutStringWriter.toString();
            mOut.close();
            mOutCalled = true;
            return out;
        }
    }
}
