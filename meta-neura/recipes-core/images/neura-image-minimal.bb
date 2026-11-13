SUMMARY = "Minimal NEURA Linux image: AIlinux kernel + systemd + login shell"
DESCRIPTION = "Stage 0.1 (NEURA 0.1 Boot): a bootable x86-64 image that \
starts the NEURA kernel from the AIlinux tree, mounts the root filesystem, \
starts systemd/udev and reaches a login prompt.  No graphics stack and no \
network manager yet - those are the next stages (see README)."
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
