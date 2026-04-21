#
# Copyright (C) 2011 The Android Open-Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# setup dalvik vm configs
$(call inherit-product, frameworks/native/build/tablet-10in-xhdpi-2048-dalvik-heap.mk)

include $(LOCAL_PATH)/../vendor-package-ver.mk
ifeq ($(TARGET_USES_BOOT_HDR_V3), true)
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota/launch_with_vendor_ramdisk.mk)
else
$(call inherit-product, $(SRC_TARGET_DIR)/product/virtual_ab_ota.mk)
endif

# dlkm_loader
include device/linaro/dragonboard/shared/utils/dlkm_loader/device.mk
PRODUCT_COPY_FILES += \
    device/linaro/dragonboard/shared/utils/dlkm_loader/dlkm_loader.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/dlkm_loader.rc

# Build generic Audio HAL
PRODUCT_PACKAGES += audio.primary.db845c

# BootControl HAL
PRODUCT_PACKAGES += \
    android.hardware.boot@1.2-impl \
    android.hardware.boot@1.2-impl.recovery \
    android.hardware.boot@1.2-service

# Set BT address
PRODUCT_PACKAGES += bdaddr

# Install bdaddr script
PRODUCT_COPY_FILES += \
    device/linaro/dragonboard/shared/utils/bdaddr/set_bdaddr.sh:$(TARGET_COPY_OUT_VENDOR)/bin/set_bdaddr.sh

# Install scripts to set vendor.* properties
PRODUCT_COPY_FILES += \
    device/linaro/dragonboard/shared/utils/set_hw.sh:$(TARGET_COPY_OUT_VENDOR)/bin/set_hw.sh \
    device/linaro/dragonboard/shared/utils/set_udc.sh:$(TARGET_COPY_OUT_VENDOR)/bin/set_udc.sh

# Install scripts to set Ethernet MAC address
PRODUCT_COPY_FILES += \
    device/linaro/dragonboard/shared/utils/ethaddr/ethaddr.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/ethaddr.rc \
    device/linaro/dragonboard/shared/utils/ethaddr/set_ethaddr.sh:$(TARGET_COPY_OUT_VENDOR)/bin/set_ethaddr.sh

PRODUCT_VENDOR_PROPERTIES += ro.soc.manufacturer=Qualcomm
PRODUCT_VENDOR_PROPERTIES += ro.soc.model=SDM845

# Copy firmware files
$(call inherit-product-if-exists, vendor/linaro/db845c/$(EXPECTED_LINARO_VENDOR_VERSION)/device.mk)
$(call inherit-product-if-exists, vendor/linaro/rb5/$(EXPECTED_LINARO_VENDOR_VERSION)/device.mk)

TARGET_HARDWARE := db845c
TARGET_KERNEL_USE ?= 6.1

include device/linaro/dragonboard/device-common.mk
