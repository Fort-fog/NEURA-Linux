SUMMARY = "NEURA live-session initramfs (OE-Core init-live.sh)"
DESCRIPTION = "Initramfs used by the NEURA live ISO/hddimg artifacts \
(IMAGE_FSTYPES \"live\", INITRD_IMAGE_LIVE in recipes-core/images/\
neura-image-minimal.bb).  It loop-mounts the ext4 rootfs.img from the live \
media and sets up the tmpfs-backed union mount that makes the running system \
writable without touching the media.  Everything it contains comes from \
OE-Core recipes; no custom init script is written in this layer."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

# initramfs-live-boot is OE-Core's live-boot package: it installs
# meta/recipes-core/initrdscripts/files/init-live.sh as /init and RDEPENDS udev
# plus udev-extraconf.  udev-extraconf's automount rule is what puts the live
# medium at /run/media/<volume-label>, which is the directory init-live.sh scans
# for rootfs.img, so this combination - not busybox-mdev - is required here.
#
# The image-live.bbclass default INITRD_IMAGE_LIVE is core-image-minimal-initramfs,
# which is built from initramfs-framework modules (initramfs-module-setup-live,
# initramfs-module-install, initramfs-module-install-efi).  Those /init.d modules
# and init-live.sh's /init are mutually exclusive, and the installer modules
# would put an "install to disk" step in front of NEURA, which this stage must
# not have.  So the initramfs is assembled from initramfs-live-boot instead.
#
# No kernel modules are installed: the devices the live medium can be attached
# to (USB xhci/ehci/ohci/uhci + usb-storage, ATAPI/AHCI, CD-ROM, virtio-blk) are
# built into the NEURA kernel (see arch/x86/configs/x86_64_defconfig and
# recipes-kernel/linux/linux-neura/neura-hardware.cfg), so udev has nothing to
# load before rootfs.img is found.
IMAGE_INSTALL = "initramfs-live-boot ${VIRTUAL-RUNTIME_base-utils} base-passwd kmod ${ROOTFS_BOOTSTRAP_INSTALL}"

IMAGE_FEATURES = ""
IMAGE_LINGUAS = ""
PACKAGE_EXCLUDE = "kernel-image-*"

# cpio.gz (INITRAMFS_FSTYPES), which is what image-live.bbclass expects:
# INITRD_LIVE = ${DEPLOY_DIR_IMAGE}/${INITRD_IMAGE_LIVE}${IMAGE_MACHINE_SUFFIX}.${first INITRAMFS_FSTYPES}
IMAGE_FSTYPES = "${INITRAMFS_FSTYPES}"

# Explicit inherit: since the poky split there is no automatic class
# classification, so an image recipe only becomes an image through
# core-image.bbclass ('inherit image' inside it) - exactly what
# OE-Core's own recipes-core/images/core-image-minimal-initramfs.bb does.
inherit core-image

# initramfs images must not carry the ".rootfs" name suffix, otherwise the
# INITRD_LIVE path above would not match the deployed file.
IMAGE_NAME_SUFFIX ?= ""

IMAGE_ROOTFS_SIZE = "8192"
IMAGE_ROOTFS_EXTRA_SPACE = "0"
