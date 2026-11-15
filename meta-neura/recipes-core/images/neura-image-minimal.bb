SUMMARY = "NEURA Linux image: AIlinux kernel + systemd + networking + live ISO"
DESCRIPTION = "Stage 0.2 (NEURA 0.2 hw-net): a bootable x86-64 image that \
starts the NEURA kernel from the AIlinux tree, brings up wired and wireless \
networking with the stock Yocto stack (NetworkManager + iwd) and is published \
as both an installed disk image (.wic) and a live medium (.iso/.hddimg) whose \
root filesystem is a read-only image on the medium with a temporary writable \
overlay.  No graphics stack, no desktop environment - those are later stages \
(see README)."
LICENSE = "MIT"

require recipes-core/images/core-image-minimal.bb

# Reachable login for the boot checkpoint.  Yocto 6.0 split the old
# "debug-tweaks" feature into the explicit features below (see the comments in
# meta/classes-recipe/core-image.bbclass of the oe-core layer):
#   empty-root-password   - root has no password set
#   allow-empty-password  - sshd accepts accounts with an empty password
#   allow-root-login      - sshd accepts root logins
#   serial-autologin-root - autologin root on the serial console (needs
#                           empty-root-password), which is what the
#                           headless runqemu boot test uses
# Development only: none of these may appear in an installable NEURA image.
IMAGE_FEATURES += "empty-root-password allow-empty-password allow-root-login serial-autologin-root"

# Base userspace requested for NEURA (in addition to packagegroup-core-boot,
# which brings systemd, udev, base-passwd, busybox, init scripts, ...).
IMAGE_INSTALL += " \
    bash \
    coreutils \
    util-linux \
    procps \
    iproute2 \
    iputils-ping \
    curl \
    wget \
    git \
    openssh-sshd \
    openssh-sftp-server \
    kernel-modules \
"

# --- Stage 2: wired and wireless networking --------------------------------
# The stock Yocto/OE stack, no NEURA-specific network code:
#   networkmanager-daemon - NetworkManager itself (wired + wireless, its own
#                           internal DHCP client, NetworkManager.service)
#   networkmanager-wifi   - Wi-Fi device plugin and the iwd enable-iwd.conf
#                           drop-in; RDEPENDS iwd because the distro enables
#                           the "iwd" PACKAGECONFIG (see conf/distro/neura.conf)
#   networkmanager-nmcli  - command line client used to create/join networks
#   iwd / iw              - Wi-Fi daemon and the nl80211 CLI tool
#   wireless-regdb-static - regulatory.db, which iwd recommends
# The meta package "networkmanager" is deliberately not used: it is an empty
# package that only weakly recommends its parts and does not even recommend
# ${PN}-daemon, so the three parts above are installed explicitly.
# systemd-networkd is not installed: the distro drops it from systemd's
# PACKAGECONFIG so it cannot compete with NetworkManager (conf/distro/neura.conf).
IMAGE_INSTALL += " \
    networkmanager-daemon \
    networkmanager-wifi \
    networkmanager-nmcli \
    iwd \
    iw \
    wireless-regdb-static \
"

# --- Stage 2: hardware firmware --------------------------------------------
# The package list is machine-scoped (conf/machine/neura-x86-64.conf) and
# installed here explicitly because OE-Core delivers MACHINE_EXTRA_RRECOMMENDS
# through packagegroup-machine-base, a sub-package of packagegroup-base, and an
# image built on core-image.bbclass installs packagegroup-core-boot only.  It
# covers the AMD GPU blobs, Realtek wired-NIC firmware and - gated on the
# 'wifi' distro feature - the firmware for every Wi-Fi driver enabled in
# recipes-kernel/linux/linux-neura, instead of the linux-firmware metapackage
# that would drag in every blob in the tree.
IMAGE_INSTALL += " ${NEURA_FIRMWARE_PACKAGES}"

# --- Stage 2: live medium artifacts ----------------------------------------
# "live" makes image-live.bbclass (inherited automatically for the live/iso/
# hddimg fstypes) build both a hybrid ISO and a hddimg from this image: the
# NEURA kernel, the initrd below and the ext4 root filesystem as rootfs.img,
# plus grub-efi (EFI) and syslinux (BIOS/MBR) boot catalogs.  The .wic disk
# image from the machine's IMAGE_FSTYPES is produced next to it and stays the
# artifact for installing NEURA on a disk.
IMAGE_FSTYPES += "live"

# OE-Core's own live initramfs (meta/recipes-core/initrdscripts/files/
# init-live.sh): it finds rootfs.img on the medium, loop-mounts it and, because
# the medium is read-only, boots it through a tmpfs-backed overlay union mount,
# i.e. the running system is temporary and nothing is written to the medium.
INITRD_IMAGE_LIVE = "neura-live-initramfs"

# Only the live session entry.  The image-live.bbclass default is
# "boot install", which would add an install-to-disk menu entry and, together
# with the initramfs' installer modules, copy the image onto the first disk.
# There is no NEURA installer yet, so it is disabled at the label level.
LABELS_LIVE = "boot"

# rootfs.img inside the ISO.  ext4 (not squashfs) is used because init-live.sh
# handles the read-only case itself: it mounts the image, notices that it is not
# writable and builds the overlay on top of it, which keeps /var/lib/NetworkManager
# and /etc/NetworkManager/system-connections writable for the running session.
LIVE_ROOTFS_TYPE = "ext4"
