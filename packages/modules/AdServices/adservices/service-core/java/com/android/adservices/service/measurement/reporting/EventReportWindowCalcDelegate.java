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

package com.android.adservices.service.measurement.reporting;

import static com.android.adservices.service.measurement.PrivacyParams.EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
import static com.android.adservices.service.measurement.PrivacyParams.MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS;
import static com.android.adservices.service.measurement.PrivacyParams.NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;

import android.annotation.NonNull;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.PrivacyParams;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerSpec;
import com.android.adservices.service.measurement.TriggerSpecs;
import com.android.adservices.service.measurement.util.UnsignedLong;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Does event report window related calculations, e.g. count, reporting time. */
public class EventReportWindowCalcDelegate {
    private static final String EARLY_REPORTING_WINDOWS_CONFIG_DELIMITER = ",";

    private final Flags mFlags;

    public EventReportWindowCalcDelegate(@NonNull Flags flags) {
        mFlags = flags;
    }

    /**
     * Max reports count based on conversion destination type and installation state.
     *
     * @param isInstallCase is app installed
     * @return maximum number of reports allowed
     */
    public int getMaxReportCount(@NonNull Source source, boolean isInstallCase) {
        // TODO(b/290101531): Cleanup flags
        if (mFlags.getMeasurementFlexLiteApiEnabled() && source.getMaxEventLevelReports() != null) {
            return source.getMaxEventLevelReports();
        }
        if (source.getSourceType() == Source.SourceType.EVENT
                && mFlags.getMeasurementEnableVtcConfigurableMaxEventReports()) {
            // Max VTC event reports are configurable
            int configuredMaxReports = mFlags.getMeasurementVtcConfigurableMaxEventReportsCount();
            // Additional report essentially for first open + 1 post install conversion. If there
            // is already more than 1 report allowed, no need to have that additional report.
            if (isInstallCase && configuredMaxReports == PrivacyParams.EVENT_SOURCE_MAX_REPORTS) {
                return PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS;
            }
            return configuredMaxReports;
        }

        if (isInstallCase) {
            return source.getSourceType() == Source.SourceType.EVENT
                    ? PrivacyParams.INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS
                    : PrivacyParams.INSTALL_ATTR_NAVIGATION_SOURCE_MAX_REPORTS;
        }
        return source.getSourceType() == Source.SourceType.EVENT
                ? PrivacyParams.EVENT_SOURCE_MAX_REPORTS
                : PrivacyParams.NAVIGATION_SOURCE_MAX_REPORTS;
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time, {@link Source}'s expiry and
     * trigger destination type.
     *
     * @return the reporting time
     */
    public long getReportingTime(
            @NonNull Source source, long triggerTime, @EventSurfaceType int destinationType) {
        if (triggerTime < source.getEventTime()) {
            return -1;
        }

        // Cases where source could have both web and app destinations, there if the trigger
        // destination is an app, and it was installed, then installState should be considered true.
        boolean isAppInstalled = isAppInstalled(source, destinationType);
        List<Pair<Long, Long>> earlyReportingWindows =
                getEarlyReportingWindows(source, isAppInstalled);
        for (Pair<Long, Long> window : earlyReportingWindows) {
            if (isWithinWindow(triggerTime, window)) {
                return window.second + mFlags.getMeasurementMinEventReportDelayMillis();
            }
        }
        Pair<Long, Long> finalWindow = getFinalReportingWindow(source, earlyReportingWindows);
        if (isWithinWindow(triggerTime, finalWindow)) {
            return finalWindow.second + mFlags.getMeasurementMinEventReportDelayMillis();
        }
        return -1;
    }

    private boolean isWithinWindow(long time, Pair<Long, Long> window) {
        return window.first <= time && time < window.second;
    }

    /**
     * Return reporting time by index for noising based on the index
     *
     * @param windowIndex index of the reporting window for which
     * @return reporting time in milliseconds
     */
    public long getReportingTimeForNoising(
            @NonNull Source source, int windowIndex, boolean isInstallCase) {
        List<Pair<Long, Long>> earlyWindows = getEarlyReportingWindows(source, isInstallCase);
        Pair<Long, Long> finalWindow = getFinalReportingWindow(source, earlyWindows);
        return windowIndex < earlyWindows.size()
                ? earlyWindows.get(windowIndex).second
                        + mFlags.getMeasurementMinEventReportDelayMillis()
                : finalWindow.second + mFlags.getMeasurementMinEventReportDelayMillis();
    }

    private Pair<Long, Long> getFinalReportingWindow(
            Source source, List<Pair<Long, Long>> earlyWindows) {
        if (mFlags.getMeasurementFlexLiteApiEnabled() && source.hasManualEventReportWindows()) {
            List<Pair<Long, Long>> windowList = source.parsedProcessedEventReportWindows();
            return windowList.get(windowList.size() - 1);
        }
        long secondToLastWindowEnd =
                !earlyWindows.isEmpty() ? earlyWindows.get(earlyWindows.size() - 1).second : 0;
        if (source.getProcessedEventReportWindow() != null) {
            return new Pair<>(secondToLastWindowEnd, source.getProcessedEventReportWindow());
        }
        return new Pair<>(secondToLastWindowEnd, source.getExpiryTime());
    }

    /**
     * Returns effective, i.e. the ones that occur before {@link
     * Source#getProcessedEventReportWindow()}, event reporting windows count for noising cases.
     *
     * @param source source for which the count is requested
     * @param isInstallCase true of cool down window was specified
     */
    public int getReportingWindowCountForNoising(@NonNull Source source, boolean isInstallCase) {
        // Early Count + lastWindow
        return getEarlyReportingWindows(source, isInstallCase).size() + 1;
    }

    /**
     * Returns reporting time for noising with flex event API.
     *
     * @param windowIndex window index corresponding to which the reporting time should be returned
     * @param triggerDataIndex trigger data state index
     * @param triggerSpecs flex event trigger specs
     */
    public long getReportingTimeForNoisingFlexEventApi(
            int windowIndex, int triggerDataIndex, TriggerSpecs triggerSpecs) {
        for (TriggerSpec triggerSpec : triggerSpecs.getTriggerSpecs()) {
            triggerDataIndex -= triggerSpec.getTriggerData().size();
            if (triggerDataIndex < 0) {
                return triggerSpec.getEventReportWindowsEnd().get(windowIndex)
                        + mFlags.getMeasurementMinEventReportDelayMillis();
            }
        }
        return 0;
    }

    /**
     * Calculates the reporting time based on the {@link Trigger} time for flexible event report API
     *
     * @param triggerSpecs the report specification to be processed
     * @param sourceRegistrationTime source registration time
     * @param triggerTime trigger time
     * @param triggerData the trigger data
     * @return the reporting time
     */
    public long getFlexEventReportingTime(
            TriggerSpecs triggerSpecs,
            long sourceRegistrationTime,
            long triggerTime,
            UnsignedLong triggerData) {
        if (triggerTime < sourceRegistrationTime) {
            return -1L;
        }
        if (triggerTime
                < triggerSpecs.findReportingStartTimeForTriggerData(triggerData)
                        + sourceRegistrationTime) {
            return -1L;
        }

        List<Long> reportingWindows = triggerSpecs.findReportingEndTimesForTriggerData(triggerData);
        for (Long window : reportingWindows) {
            if (triggerTime <= window + sourceRegistrationTime) {
                return sourceRegistrationTime + window
                        + mFlags.getMeasurementMinEventReportDelayMillis();
            }
        }
        return -1L;
    }

    private boolean isAppInstalled(Source source, int destinationType) {
        return destinationType == EventSurfaceType.APP && source.isInstallAttributed();
    }

    /**
     * If the flag is enabled and the specified report windows are valid, picks from flag controlled
     * configurable early reporting windows. Otherwise, falls back to the statical {@link
     * com.android.adservices.service.measurement.PrivacyParams} values. It curtails the windows
     * that occur after {@link Source#getProcessedEventReportWindow()} because they would
     * effectively be unusable.
     */
    private List<Pair<Long, Long>> getEarlyReportingWindows(Source source, boolean installState) {
        // TODO(b/290221611) Remove early reporting windows from code, only use them for flags.
        if (mFlags.getMeasurementFlexLiteApiEnabled() && source.hasManualEventReportWindows()) {
            List<Pair<Long, Long>> windows = source.parsedProcessedEventReportWindows();
            // Select early windows only i.e. skip the last element
            return windows.subList(0, windows.size() - 1);
        }
        List<Long> earlyWindows;
        List<Long> defaultEarlyWindows =
                getDefaultEarlyReportingWindows(source.getSourceType(), installState);
        earlyWindows =
                getConfiguredOrDefaultEarlyReportingWindows(
                        source.getSourceType(), defaultEarlyWindows, true);
        // Add source event time to windows
        earlyWindows =
                earlyWindows.stream()
                        .map((x) -> source.getEventTime() + x)
                        .collect(Collectors.toList());

        List<Pair<Long, Long>> windowList = new ArrayList<>();
        long windowStart = 0;
        Pair<Long, Long> finalWindow =
                getFinalReportingWindow(source, createStartEndWindow(earlyWindows));
        for (long windowEnd : earlyWindows) {
            if (finalWindow.second <= windowEnd) {
                continue;
            }
            windowList.add(new Pair<>(windowStart, windowEnd));
            windowStart = windowEnd;
        }
        return ImmutableList.copyOf(windowList);
    }

    /**
     * Returns the default early reporting windows
     *
     * @param sourceType Source's Type
     * @param installState Install State of the source
     * @return a list of windows
     */
    public static List<Long> getDefaultEarlyReportingWindows(
            Source.SourceType sourceType, boolean installState) {
        long[] earlyWindows;
        if (installState) {
            earlyWindows =
                    sourceType == Source.SourceType.EVENT
                            ? INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                            : INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        } else {
            earlyWindows =
                    sourceType == Source.SourceType.EVENT
                            ? EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS
                            : NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;
        }
        return asList(earlyWindows);
    }

    private List<Pair<Long, Long>> createStartEndWindow(List<Long> windowEnds) {
        List<Pair<Long, Long>> windows = new ArrayList<>();
        long start = 0;
        for (Long end : windowEnds) {
            windows.add(new Pair<>(start, end));
            start = end;
        }
        return windows;
    }

    /**
     * Returns default or configured (via flag) early reporting windows for the SourceType
     *
     * @param sourceType Source's Type
     * @param defaultEarlyWindows default value for early windows
     * @param checkEnableFlag set true if configurable window flag should be checked
     * @return list of windows
     */
    public List<Long> getConfiguredOrDefaultEarlyReportingWindows(
            Source.SourceType sourceType, List<Long> defaultEarlyWindows, boolean checkEnableFlag) {
        // TODO(b/290101531): Cleanup flags
        if (checkEnableFlag && !mFlags.getMeasurementEnableConfigurableEventReportingWindows()) {
            return defaultEarlyWindows;
        }

        String earlyReportingWindowsString = pickEarlyReportingWindowsConfig(mFlags, sourceType);

        if (earlyReportingWindowsString == null) {
            LoggerFactory.getMeasurementLogger()
                    .d("Invalid configurable early reporting windows; null");
            return defaultEarlyWindows;
        }

        if (earlyReportingWindowsString.isEmpty()) {
            // No early reporting windows specified. It needs to be handled separately because
            // splitting an empty string results into an array containing a single element,
            // i.e. "". We want to handle it as an array having no element.

            if (Source.SourceType.EVENT.equals(sourceType)) {
                // We need to add a reporting window at 2d for post-install case. Non-install case
                // has no early reporting window by default.
                return defaultEarlyWindows;
            }
            return Collections.emptyList();
        }

        ImmutableList.Builder<Long> earlyWindows = new ImmutableList.Builder<>();
        String[] split =
                earlyReportingWindowsString.split(EARLY_REPORTING_WINDOWS_CONFIG_DELIMITER);
        if (split.length > MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS) {
            LoggerFactory.getMeasurementLogger()
                    .d(
                            "Invalid configurable early reporting window; more than allowed size: "
                                    + MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS);
            return defaultEarlyWindows;
        }

        for (String window : split) {
            try {
                earlyWindows.add(TimeUnit.SECONDS.toMillis(Long.parseLong(window)));
            } catch (NumberFormatException e) {
                LoggerFactory.getMeasurementLogger()
                        .d(e, "Configurable early reporting window parsing failed.");
                return defaultEarlyWindows;
            }
        }
        return earlyWindows.build();
    }

    private String pickEarlyReportingWindowsConfig(Flags flags, Source.SourceType sourceType) {
        return sourceType == Source.SourceType.EVENT
                ? flags.getMeasurementEventReportsVtcEarlyReportingWindows()
                : flags.getMeasurementEventReportsCtcEarlyReportingWindows();
    }

    private static List<Long> asList(long[] values) {
        final List<Long> list = new ArrayList<>();
        for (Long value : values) {
            list.add(value);
        }
        return list;
    }
}
