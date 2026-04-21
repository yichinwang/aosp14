#!/bin/bash

export EXPECTED_LINARO_VENDOR_VERSION=20230510
#make sure to use sha512sum here
export EXPECTED_LINARO_VENDOR_SHA=37aebd6fef4294226293ebe1945bd7a327b2653aaa8b294578d5a00141c1717f3aeb9b1b1832157d894193dd836e8c783b3fe0b4674aa9bd69fee3555d4ec0c3
export VND_PKG_URL=https://releases.linaro.org/android/aosp-linaro-vendor-package/extract-linaro_devices-20230510.tgz

if [ "$1" = "url" ]; then
 echo $VND_PKG_URL
elif [ "$1" = "ver" ]; then
 echo $EXPECTED_LINARO_VENDOR_VERSION
elif [ "$1" = "sha" ]; then
 echo $EXPECTED_LINARO_VENDOR_SHA
fi
