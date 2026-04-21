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

#include "ExynosDisplayDrmInterfaceModule.h"
#include "ExynosPrimaryDisplayModule.h"

using namespace zuma;

//////////////////////////////////////////////////// ExynosPrimaryDisplayDrmInterfaceModule //////////////////////////////////////////////////////////////////
ExynosPrimaryDisplayDrmInterfaceModule::ExynosPrimaryDisplayDrmInterfaceModule(ExynosDisplay *exynosDisplay)
  : gs201::ExynosPrimaryDisplayDrmInterfaceModule(exynosDisplay)
{
            ExynosPrimaryDisplayModule* display = (ExynosPrimaryDisplayModule*)mExynosDisplay;
            const std::string& sysfs = display->getPanelSysfsPath();
            std::string panelModel;

            if (sysfs.empty()) {
                return;
            }

            if (readLineFromFile(sysfs + "/panel_model", panelModel, '\n') != OK) {
                return;
            }

            /*
             * VESA EDID Standard requires monitor descriptor data to terminate
             * with '\n' character and to pad with ' ' characters if < 13 bytes.
             */
            if (panelModel.size() < MONITOR_DESCRIPTOR_DATA_LENGTH) {
                panelModel += '\n';
                panelModel.append(MONITOR_DESCRIPTOR_DATA_LENGTH - panelModel.size(), ' ');
            }

            if (panelModel.size() > MONITOR_DESCRIPTOR_DATA_LENGTH) {
                ALOGE("Panel model longer than maximum %u bytes", MONITOR_DESCRIPTOR_DATA_LENGTH);
                return;
            }

            std::memcpy(mMonitorDescription.data(), panelModel.c_str(), mMonitorDescription.size());
}

// TODO: b/295990513 - Remove the if defined after kernel prebuilts are merged.
#if defined(EXYNOS_HISTOGRAM_CHANNEL_REQUEST)
int32_t ExynosPrimaryDisplayDrmInterfaceModule::sendHistogramChannelIoctl(HistogramChannelIoctl_t control, uint8_t channelId) const {
    struct exynos_drm_histogram_channel_request histogramRequest;

    histogramRequest.crtc_id = mDrmCrtc->id();
    histogramRequest.hist_id = channelId;

    if (control == HistogramChannelIoctl_t::REQUEST) {
        ATRACE_NAME(String8::format("requestIoctl #%u", channelId).c_str());
        return mDrmDevice->CallVendorIoctl(DRM_IOCTL_EXYNOS_HISTOGRAM_CHANNEL_REQUEST,
                                           (void *)&histogramRequest);
    } else if (control == HistogramChannelIoctl_t::CANCEL) {
        ATRACE_NAME(String8::format("cancelIoctl #%u", channelId).c_str());
        return mDrmDevice->CallVendorIoctl(DRM_IOCTL_EXYNOS_HISTOGRAM_CHANNEL_CANCEL,
                                           (void *)&histogramRequest);
    } else {
        ALOGE("%s: unknown control %d", __func__, (int)control);
        return BAD_VALUE;
    }
}
#endif
