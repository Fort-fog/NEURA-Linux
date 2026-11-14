SUMMARY = "NEURA Linux kernel (Linux 7.4.0-rc2) built from the AIlinux tree"
DESCRIPTION = "The NEURA kernel is the upstream-style Linux source of \
https://github.com/Fort-fog/AIlinux, pinned to one commit. Configuration is \
the kernel tree's own arch/x86/configs/x86_64_defconfig plus NEURA .cfg \
fragments merged with the kernel's own scripts/kconfig/merge_config.sh; no \
kernel source is carried in this layer."
HOMEPAGE = "https://github.com/Fort-fog/AIlinux"
SECTION = "Kernel"

LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

inherit kernel

# kernel-yocto is deliberately NOT inherited: it hard-depends on
# kern-tools-native plus a kernel-meta metadata tree (.scc feature descriptions
# under ${UNPACKDIR}/kernel-meta), and AIlinux is a plain kernel tree without
# kernel-meta.  Instead the configuration uses the mechanism the kernel itself
# uses for x86_64_defconfig: a base defconfig plus .cfg fragments merged by
# scripts/kconfig/merge_config.sh, then resolved with `make olddefconfig`
# (the default KERNEL_CONFIG_COMMAND in kernel.bbclass).
#
# No kernel source patches are applied: the tree is built as-is.

KBRANCH = "master"

# Pinned commit of Fort-fog/AIlinux (branch master; the repository has no
# tags).  Linux 7.4.0-rc2 per the Makefile at this commit.
SRCREV = "9d66f26fefacebfeed15de31c1123451a17ff119"
LINUX_VERSION = "7.4.0-rc2"

# Yocto 6.0 unpacks a git source into ${UNPACKDIR}/${BB_GIT_DEFAULT_DESTSUFFIX}
# and bitbake.conf sets S = "${UNPACKDIR}/${BP}", which is exactly that
# directory, so the old `S = "${WORKDIR}/git"` assignment is now rejected
# outright by do_qa_unpack (meta/classes-global/insane.bbclass).  Stating
# ${UNPACKDIR}/${BP} keeps the recipe independent of the default value:
# kernel.bbclass' do_symlink_kernsrc then moves the tree to
# ${STAGING_KERNEL_DIR} (a per-recipe directory, because
# KERNEL_PACKAGE_NAME != PN here) and symlinks ${S} back at it - the supported
# route for kernel recipes that do not inherit kernel-yocto.
S = "${UNPACKDIR}/${BP}"

# Base configuration, taken from the kernel tree itself.
KBASE_CONFIG = "x86_64_defconfig"

SRC_URI = "git://github.com/Fort-fog/AIlinux.git;protocol=https;branch=${KBRANCH}"
SRC_URI += " \
    file://neura-boot.cfg \
    file://neura-hardware.cfg \
"

# arch/x86 objtool links against libelf (same native dependency the OE x86
# kernel recipe declares for x86/arm64/powerpc).
DEPENDS += '${@bb.utils.contains_any("ARCH", [ "x86", "arm64", "powerpc" ], "elfutils-native", "", d)}'

# Build the .config before kernel_do_configure runs.  ${B} already exists
# (do_configure[dirs] = "${B}" in base.bbclass), which merge_config.sh -O
# requires.  The result is a sparse .config which KERNEL_CONFIG_COMMAND
# (make olddefconfig) expands into a full configuration.
do_configure:prepend() {
    bbnote "NEURA kernel config: ${S}/arch/x86/configs/${KBASE_CONFIG} + NEURA fragments"
    ${S}/scripts/kconfig/merge_config.sh -m -O ${B} \
        ${S}/arch/x86/configs/${KBASE_CONFIG} \
        ${UNPACKDIR}/neura-boot.cfg \
        ${UNPACKDIR}/neura-hardware.cfg
}
