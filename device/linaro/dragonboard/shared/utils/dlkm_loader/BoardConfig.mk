# system_dlkm partition
BOARD_USES_SYSTEM_DLKMIMAGE := true
BOARD_SYSTEM_DLKMIMAGE_FILE_SYSTEM_TYPE := ext4 # system_dlkm.img prebuilt from ci.android.com is ext4
TARGET_COPY_OUT_SYSTEM_DLKM := system_dlkm
BOARD_DB_DYNAMIC_PARTITIONS_PARTITION_LIST := system vendor system_ext product system_dlkm
