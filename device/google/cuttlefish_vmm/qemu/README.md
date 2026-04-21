# Experimental QEMU8 build system

This is a build of QEMU8 from scratch on Linux, using AOSP-specific compiler
toolchain and sysroot (based on an old glibc-2.17 to ensure the generated
binaries run on a vast number of distributions).

## Getting the sources and prebuilts:

The source tree is composed of ~50 multiple git containing source and prebuilt
tools. This take tens of minutes since it downloads large prebuilts like the
Clang toolchain or the GCC sysroot.

```sh
mkdir cuttlefish_vmm
cd cuttlefish_vmm
repo init --manifest-url https://android.googlesource.com/device/google/cuttlefish_vmm \
  --manifest-name=qemu/manifest.xml
repo sync -j 12
```

The `qemu/manifest.xml` enumerates the dependencies. For each of them it
reproduces the hierarchy of git submodules. See below how to regenerate this
file from git metadata.

## Local build (Linux only):

Just call `qemu/scripts/rebuild.sh`, specifying a build directory
where all build outputs will be placed. In case of success,
the qemu static binaries will be under
`$BUILD_DIR/qemu-portable.tar.gz`.

```sh
qemu/scripts/rebuild.sh --build-dir /tmp/qemu-build
```

The `--run-tests` option can be used to run the QEMU test
suite just after the build. Note that this currently hangs.

##  Container build (Linux only):

Ensure podman or docker is installed (podman is preferred
since it will allow you to run containers without being
root). See Annex A for important configuration information.

The build has been tested with a small Debian10 image e.g.:

```sh
mkdir /tmp/qemu-build
podman run --replace --pids-limit=-1 \
  --interactive --tty \
  --name qemu-build \
  --volume .:/src:O \
  --volume /tmp/qemu-build:/out \
  docker.io/debian:10-slim
apt-get update
apt-get -qy install autoconf libtool texinfo libgbm-dev

/src/qemu/third_party/python/bin/python3 /src/qemu/scripts/rebuild.py --build-dir /out --run-tests
```

Note: `/src` is mounted with a file overlay so that cargo can write
`third_party/crossvm/rutabaga_gfx/ffi/Cargo.lock` file. We didn't find
a way to prevent cargo from writing it.

## Clone the repository with git submodules

The alternateway to get the source and dependencies is to rely on submodules.

```sh
git clone sso://experimental-qemu-build-internal.googlesource.com/qemu-build qemu
cd qemu
git submodule update --init --depth 1 --recursive --jobs 4
```

This makes possible to upvert a dependency such as `qemu` and
regenerate the repo manifest from the submodule tree.

```sh
python3 qemu/scripts/genrepo.py . --repo_manifest qemu/manifest.xml
```

## Check your code before submit

The following script run pytype and pyformat.

```sh
scripts/check.sh
```

## Regenerate Cargo crates list

Cargo crates are checked-in as part of the source tree and enumerated by
`qemu/third_party/.cargo/config.toml`. This file hase be regenerated when
`qemu/third_party/rust/crate` changes with the following command line:

```sh
ls qemu/third_party/rust/crates | awk '
  BEGIN {print "[patch.crates-io]"}
  {print $1 " = { path = \"rust/crates/" $1 "\" }"}' > qemu/third_party/.cargo/config.toml
```