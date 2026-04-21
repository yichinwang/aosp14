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

import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;

import android.os.Binder;
import android.os.Process;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BasicShellCommandHandler;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Implementation of {@code cmd adservices_manager}.
 *
 * <p>Currently, it only provides functionalities that can be provided locally by the AdServices
 * system server classes. But eventually it will require a connection with the AdServices process,
 * which in turn might require giving more permissions to Shell.
 */
// NOTE: not final because it's extended by unit test to set streams
class AdServicesShellCommand extends BasicShellCommandHandler {

    @VisibleForTesting
    static final String WRONG_UID_TEMPLATE =
            AD_SERVICES_SYSTEM_SERVICE + " shell cmd is only callable by ADB (called by %d)";

    private final Injector mInjector;
    private final Flags mFlags;

    AdServicesShellCommand() {
        this(new Injector(), PhFlags.getInstance());
    }

    @VisibleForTesting
    AdServicesShellCommand(Injector injector, Flags flags) {
        mInjector = Objects.requireNonNull(injector);
        mFlags = Objects.requireNonNull(flags);
    }

    @Override
    public int onCommand(String cmd) {
        int callingUid = mInjector.getCallingUid();
        if (callingUid != Process.ROOT_UID && callingUid != Process.SHELL_UID) {
            throw new SecurityException(String.format(WRONG_UID_TEMPLATE, callingUid));
        }

        if (cmd == null || cmd.isEmpty() || cmd.equals("-h") || cmd.equals("help")) {
            onHelp();
            return 0;
        }
        switch (cmd) {
            case "is-system-service-enabled":
                return runIsSystemServiceEnabled();
            default:
                // Cannot use handleDefaultCommands() because it doesn't show help
                return showError("Unsupported commmand: %s", cmd);
        }
    }

    private int runIsSystemServiceEnabled() {
        PrintWriter pw = getOutPrintWriter();
        boolean verbose = false;

        String opt;
        if ((opt = getNextOption()) != null) {
            switch (opt) {
                case "-v":
                case "--verbose":
                    verbose = true;
                    break;
                default:
                    return showError("Invalid option: %s", opt);
            }
        }

        boolean enabled = mFlags.getAdServicesSystemServiceEnabled();

        if (!verbose) {
            // NOTE: must always print just the boolean, as it might be used by tests.
            pw.println(enabled);
            return 0;
        }

        // Here it's ok to print whatever we want...
        pw.printf(
                "Enabled: %b Default value: %b DeviceConfig key: %s\n",
                enabled,
                PhFlags.ADSERVICES_SYSTEM_SERVICE_ENABLED,
                PhFlags.KEY_ADSERVICES_SYSTEM_SERVICE_ENABLED);
        return 0;
    }

    @Override
    public void onHelp() {
        PrintWriter pw = getOutPrintWriter();
        pw.println("AdServices (adservices_manager) commands: ");
        showValidCommands(pw);
    }

    @FormatMethod
    private int showError(@FormatString String fmt, Object... args) {
        PrintWriter pw = getErrPrintWriter();
        String error = String.format(fmt, args);
        pw.printf("%s. Valid commands are: \n", error);
        showValidCommands(pw);
        return -1;
    }

    private static void showValidCommands(PrintWriter pw) {
        pw.println("help: ");
        pw.println("    Prints this help text.");
        pw.println();
        pw.println("is-system-service-enabled [-v || --verbose]");
        pw.println(
                "    Returns a boolean indicating whether the AdServices System Service is"
                        + "enabled.");
        pw.println("    Use [-v || --verbose] to also show the default value");
        pw.println();
    }

    // Needed because Binder.getCallingUid() is native and cannot be mocked
    @VisibleForTesting
    static class Injector {
        int getCallingUid() {
            return Binder.getCallingUid();
        }
    }
}
