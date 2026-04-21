AB_OTA_PARTITIONS += system_dlkm
PRODUCT_PACKAGES += dlkm_loader

# List of modules that should not load automatically
PRODUCT_COPY_FILES += \
    device/linaro/dragonboard/shared/utils/dlkm_loader/vendor_ramdisk.modules.blocklist:$(TARGET_COPY_OUT_VENDOR_RAMDISK)/lib/modules/modules.blocklist
