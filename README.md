# NEURA Linux

Distro repository for **NEURA Linux**, an x86-64 Linux distribution built with
Yocto Project / OpenEmbedded.

Role separation in this project:

| Repository | Role |
| --- | --- |
| <https://github.com/Fort-fog/AIlinux> | the **NEURA kernel source** (a Linux 7.4 tree). The kernel is *not* copied into this repository and is *not* replaced by `linux-yocto` or a vendor kernel. |
| <https://github.com/Fort-fog/NEURA-Linux> (this repository) | the **distro**: the `meta-neura` layer, distro/machine configuration, kernel recipe and configuration fragments, image recipes, build infrastructure. |

## Current stage: `NEURA 0.2 hw-net` — achieved

Two acceptance boots were run against images built from branch
`neura-stage-2-hw-net`, both headless under QEMU with UEFI firmware (OVMF),
and both passed.

* The installed disk image (`.wic`), via `runqemu`
  (console log `build-neura/neura-boot-test-04.log`):

  ```
  systemctl is-system-running   -> running
  systemctl list-units --failed -> 0 loaded units listed
  systemctl is-enabled  iwd NetworkManager -> enabled, enabled
  systemctl is-active   iwd NetworkManager -> active,  active
  nmcli -t -f DEVICE,TYPE,STATE device     -> eth0:ethernet:connected
  ip -4 -o addr show dev eth0   -> 10.0.2.15/24 (QEMU slirp DHCP)
  networkctl                    -> command not found (systemd-networkd absent)
  journalctl -b -p 3            -> No entries (zero err-level messages this boot)
  /lib/firmware/regulatory.db(.p7s) present; amdgpu 675 blobs; iwlwifi 190 blobs
  iw dev                        -> empty: the QEMU VM has no WLAN device
  ```

* The **live ISO booted with no disk attached at all**
  (console log `build-neura/neura-live-test-01.log`) - the read-only medium
  plus a temporary writable session, all via stock OE-Core live plumbing:

  ```
  mount (root)      -> overlay on / type overlay (rw, lowerdir=/rootfs.ro,
                       upperdir=/rootfs.rw/upperdir, workdir=/rootfs.rw/work)
  /proc/mounts      -> /dev/sr0 /media/realroot iso9660 ro,relatime
  ls /dev/sr0 /dev/sd* /dev/vd* -> /dev/sr0 only: there is no disk, nothing written
  touch /root-live-write-test   -> write-ok (lands in the tmpfs upper layer)
  systemctl is-system-running   -> running; --failed -> 0 units
  systemctl is-active iwd NetworkManager -> active, active
  nmcli device                  -> eth0:ethernet:connected (DHCP)
  ```

Goals of this stage (and nothing beyond them):

* real hardware support: AMD GPU (`amdgpu`) firmware and firmware for the
  common Wi-Fi / USB-NIC device families in the image, with no unexplained
  missing-firmware errors at boot,
* networking from **stock OE/Yocto components only**: NetworkManager
  (meta-networking) with the **iwd** Wi-Fi backend, Ethernet DHCP +
  auto-connect, saved Wi-Fi profiles,
* a **stock Yocto live image** (`image-live.bbclass`, standard
  `IMAGE_FSTYPES`): UEFI-bootable `.iso` (Ventoy-suitable hybrid MBR), the
  ordinary `.wic` keeps working, live rootfs read-only + overlay via the
  stock OE mechanism.

Not part of this stage (intentionally deferred, see [Roadmap](#roadmap)):
Wayland desktop, audio, file managers, monitoring tools, AI runtime,
installer (the live ISO delivers the system, but there is no
install-to-disk program yet).

### Previous checkpoint: `NEURA 0.1 Boot` — achieved

The tag/branch `neura-boot-0.1` keeps its original meaning: the tree at that
reference still builds the 0.1 image below, and nothing on it was changed
while building 0.2. Its acceptance test (log
`build-neura/neura-boot-test-01.log`): UEFI/OVMF → grub-efi → AIlinux kernel
`7.4.0-rc2-g9d66f26fefac` → systemd on `multi-user.target` with `systemctl
is-system-running` = `running`, zero failed units, ext4 root on `sda2`, root
shell on `ttyS0`, clean `poweroff`.

Goal of that stage (and nothing beyond it): a bootable x86-64 image running
the kernel built from `Fort-fog/AIlinux` at a pinned commit, mounting the
root filesystem, starting systemd/udev and reaching a login prompt in QEMU
with UEFI firmware (OVMF), reproducible from a clean build directory. Not
part of it: NetworkManager/Wi-Fi (now done in 0.2), Wayland desktop, audio,
file managers, monitoring tools, AI runtime, installer/ISO (ISO done in 0.2).

## Build baseline (pinned)

Yocto Project **6.0 LTS "wrynose"**. Since the poky repository was split up,
the release is assembled from four repositories; the `poky` repository itself
is not used.

| Component | Remote | Branch | Pinned commit |
| --- | --- | --- | --- |
| openembedded-core | `https://git.openembedded.org/openembedded-core` | `wrynose` | `33a6bd6cc8a9a976902081642414bd67adc74879` |
| meta-yocto (for `meta-yocto-bsp` only) | `https://git.yoctoproject.org/meta-yocto` | `wrynose` | `b60511544c41f5d29d290681e15c9e302f747efe` |
| meta-openembedded (for `meta-oe`, `meta-python`, `meta-networking`) | `https://git.openembedded.org/meta-openembedded` | `wrynose` | `c0fcd7cca3760ef24e087ac4a31b8f357cb204da` |
| bitbake | `https://git.openembedded.org/bitbake` | `2.18` | `6e260eb6ab1631607ea414d9ff8ede1155faa5d9` (bitbake 2.18.0) |
| NEURA kernel | `https://github.com/Fort-fog/AIlinux` | `master` | `9d66f26fefacebfeed15de31c1123451a17ff119` (Linux 7.4.0-rc2) |

`meta-yocto-bsp` is used only for its generic x86 machine includes
(`conf/machine/include/genericx86-common.inc`, `files/wic/genericx86.wks.in`).
`meta-poky` is **not** used: NEURA has its own distro configuration
(`neura.conf`), it does not inherit Poky's policies. `meta-networking` (from
meta-openembedded) provides NetworkManager and iwd; `meta-oe` provides their
dependencies.

`oe-init-build-env` requires bitbake to be reachable as a **sibling directory
named exactly `bitbake`** next to the openembedded-core checkout
(`$OEROOT/bitbake` or `$OEROOT/../bitbake`).

## Build host

Ubuntu 24.04 (x86-64), as documented by the Yocto Project
(`docs.yoctoproject.org` → "Support Build Package"). Verified present on the
NEURA host: `git tar diffstat make patch sed info gzip bzip2 xz zstd cpio file
find chrpath socat python3 gcc g++ cmake ninja pkg-config bc gawk patchutils
desktop-file-utils`, and the Python modules `git jinja2 pexpect`.
`flex`, `bison` and `meson` are *not* needed on the host: OpenEmbedded builds
its own `*-native` versions (`bison-native` is already a dependency of the
kernel class; the AIlinux kconfig parser is shipped pre-generated, so `flex`
is not used by the kernel build).

Memory: the build host has 64 GB RAM, 32 GB swap, 16 threads and usually runs
other interactive workloads, so `BB_NUMBER_THREADS`/`PARALLEL_MAKE` are capped
to 4 in the build template. Raise them when the host has free memory
(`free -m`).

Network note: on the current NEURA build host `downloads.yoctoproject.org` and
`sources.yoctoproject.org` are not reachable, so the **uninative** tarball
cannot be downloaded and BitBake logs
`WARNING: Failed to fetch URL http://downloads.yoctoproject.org/releases/uninative/...`.
That is not fatal - OpenEmbedded simply continues without the uninative
preload, which only reduces shared-state reuse. `git://git.yoctoproject.org/`
*is* reachable, so `pseudo` and `patch` are fetched from their git sources.
Other upstreams that are unreachable from this host (`gmplib.org`,
`download.samba.org`, `sourceware.org`, `libarchive.org`) are covered by the
`MIRRORS` table of OpenEmbedded, which redirects them to reachable mirrors;
each of these still logs a `WARNING: Failed to fetch URL ..., attempting
MIRRORS`. A build log full of those warnings on this host is expected and is
not an error - a genuinely missing download shows up as an `ERROR` and a failed
`do_fetch` instead.

## Layout

```
meta-neura/
├── conf/
│   ├── layer.conf
│   ├── distro/neura.conf                 # NEURA distro (own, does not require poky.conf);
│   │                                     #   NM/iwd selection lives here
│   ├── machine/neura-x86-64.conf         # x86-64 UEFI PC machine, core2-64 tuning,
│   │                                     #   curated firmware package lists, runqemu QB_*
│   └── templates/default/                # build-directory templates
│       ├── bblayers.conf.sample
│       ├── local.conf.sample
│       └── conf-summary.txt
├── recipes-core/images/
│   ├── neura-image-minimal.bb            # the image: .wic + live ISO/hddimg
│   └── neura-live-initramfs.bb           # initramfs for the live session
└── recipes-kernel/linux/
    ├── linux-neura_7.4.0-rc2.bb          # kernel from Fort-fog/AIlinux
    └── linux-neura/
        ├── neura-boot.cfg                # boot/systemd/UEFI/virtio config fragment
        ├── neura-hardware.cfg            # NVMe, amdgpu, igc, uas, wired NICs
        ├── neura-wifi.cfg                # Wi-Fi drivers: iwlwifi, mt76, rtw88/rtw89, ath10k/ath11k
        ├── neura-live.cfg                # CONFIG_OVERLAY_FS for the live session
        └── neura-iwd.cfg                 # AF_ALG crypto symbols iwd needs
```

## Reproducible build

```bash
# 1. baseline (the four repositories, pinned branches)
mkdir -p ~/NEURA-build && cd ~/NEURA-build
git clone --branch wrynose https://git.openembedded.org/openembedded-core openembedded-core-wrynose
git clone --branch wrynose https://git.yoctoproject.org/meta-yocto meta-yocto-wrynose
git clone --branch wrynose https://git.openembedded.org/meta-openembedded meta-openembedded-wrynose
git clone --branch 2.18   https://git.openembedded.org/bitbake bitbake

# 2. this repository
git clone https://github.com/Fort-fog/NEURA-Linux.git ~/NEURA-Linux
cd ~/NEURA-Linux && git checkout neura-stage-2-hw-net

# 3. build directory from the NEURA template
cd ~/NEURA-build
git -C openembedded-core-wrynose checkout 33a6bd6cc8a9a976902081642414bd67adc74879
git -C meta-yocto-wrynose      checkout b60511544c41f5d29d290681e15c9e302f747efe
git -C meta-openembedded-wrynose checkout c0fcd7cca3760ef24e087ac4a31b8f357cb204da
git -C bitbake                 checkout 6e260eb6ab1631607ea414d9ff8ede1155faa5d9
TEMPLATECONF=~/NEURA-Linux/meta-neura/conf/templates/default \
    source openembedded-core-wrynose/oe-init-build-env build-neura

# 4. build
cd ~/NEURA-build/build-neura
bitbake neura-image-minimal
```

The template's `meta-neura/conf/templates/default/bblayers.conf.sample` lists
the five layer directories: oe-core's `meta`, `meta-yocto-bsp`, the three
meta-openembedded sublayers (`meta-oe`, `meta-python`, `meta-networking`) and
`meta-neura`. `local.conf.sample` sets `MACHINE = "neura-x86-64"`,
`DISTRO = "neura"` and the parallelism caps described above.

Resulting images land in `tmp/deploy/images/neura-x86-64/`, stamped with the
build timestamp (the 0.2 checkpoint is
`neura-image-minimal-neura-x86-64.rootfs-20261115134103.*`):

* `…rootfs.wic` — partitioned disk image, 1504529408 bytes apparent (sparse,
  ~750 MB actually on disk), written with `bmap-tools` (`…rootfs.wic.bmap`,
  3617 bytes) or `dd`,
* `…rootfs.wic.zst` — compressed copy of the same, 257329975 bytes,
* `…rootfs.ext4` — bare root filesystem (1095208960 bytes; also the
  intermediate that `wic` partitions and the ISO embeds as `rootfs.img`),
* `…rootfs.iso` — **live ISO**, 1150287872 bytes (~1.07 GiB, fully
  allocated), UEFI + BIOS via `isohybrid -u` (see the live boot test),
* `…rootfs.hddimg` — BIOS/syslinux hybrid disk image from the same live
  build, 1140006912 bytes (kept; see known issues),
* `neura-live-initramfs-neura-x86-64.cpio.gz` — the live-session initrd,
  10130015 bytes,
* `bzImage-neura-x86-64.bin`, modules tarball, `*.qemuboot.conf`,
  `*.manifest` (415 packages), OVMF `ovmf.code.qcow2`/`ovmf.vars.qcow2`,
* `SHA256SUMS` covering the published artifacts (`.wic`, `.wic.zst`, `.iso`,
  `.hddimg`, `.wic.bmap`); the 0.1 sums are kept as `SHA256SUMS.0.1-boot`.

Build evidence from the current tree (`bitbake neura-image-minimal`,
log `build-neura/neura-build-08.log`):

```
NOTE: Tasks Summary: Attempted 5090 tasks of which 5043 didn't need to be
                       rerun and all succeeded.
```

That log contains zero `ERROR` and zero `WARNING` lines (the download
warnings from the first 0.1 build did not recur because the downloads were
already in `DL_DIR`; a from-scratch 0.2 build runs the same task graph and,
on this host, logs only the expected MIRRORS warnings described above).

Build evidence from the 0.1 tree (`bitbake neura-image-minimal`, log
`build-neura/neura-build-03.log`, 4614 tasks):

```
WARNING: Failed to fetch URL https://gmplib.org/..., attempting MIRRORS if available
WARNING: Failed to fetch URL https://www.mosfet.org/..., attempting MIRRORS if available
WARNING: Failed to fetch URL https://www.greenwoodsoftware.com/libsoc..., attempting MIRRORS if available
NOTE: Tasks Summary: Attempted 4614 tasks of which 2144 didn't need to be rerun and all succeeded.
```

The three warnings are the expected unreachable-upstream/MIRRORS warnings for
`ca-certificates-native`, `socat-native` and `libsdl2-native`; all three
sources were fetched successfully from the mirrors.

## QEMU boot test (0.1 acceptance, kept for history)

The `runqemu` script ships with oe-core. The machine config sets the
`QB_*` variables, so the standard invocation works:

```bash
cd ~/NEURA-build/build-neura
source ../openembedded-core-wrynose/oe-init-build-env .   # puts runqemu on PATH
runqemu neura-x86-64 nographic serial wic ovmf kvm slirp snapshot
```

`nographic` puts the whole session (firmware, grub, kernel, getty) on the
host's stdio, and `serial-autologin-root` means a root shell appears on
`ttyS0` without a password prompt — the whole test is driveable over a pipe.
`slirp` is required here because `tap` mode needs `sudo`/`/dev/net/tun` setup
the NEURA host does not provide; `snapshot` prevents any write to the image.
`ovmf` boots via UEFI: the firmware variables are copied to a temp dir, grub
shows `NEURA Linux (neura-x86-64) (UEFI x86_64)`.

`runqemu` can also be pointed directly at the artifact plus its sidecar
config — this is what the scripted test uses, so it boots the exact stamped
file rather than whatever the glob in `build-neura/tmp/deploy` resolves to
(the glob picks `*rootfs*`, which would also match the `.ext4`):

```bash
runqemu tmp/deploy/images/neura-x86-64/neura-image-minimal-neura-x86-64.rootfs-<stamp>.wic \
        tmp/deploy/images/neura-x86-64/neura-image-minimal-neura-x86-64.wic.qemuboot.conf \
        nographic serial ovmf kvm slirp snapshot
```

Result (0.1, full console log `build-neura/neura-boot-test-01.log`; the
guest is powered off from inside the session, so QEMU exits by itself):

```
Linux version 7.4.0-rc2-g9d66f26fefac (oe-user@oe-host) (x86_64-oe-linux-gcc 15.2.0), ld GNU 2.45.0
systemd[1]: Started NEURA Linux 0.1 Boot (neura-x86-64) ttyS0.
NEURA Linux 0.1 Boot neura-x86-64 ttyS0

neura-x86-64:~# uname -a
Linux neura-x86-64 7.4.0-rc2-g9d66f26fefac #1 SMP PREEMPT_DYNAMIC <date> x86_64 GNU/Linux
neura-x86-64:~# cat /etc/os-release    # ID=neura, NAME="NEURA Linux", VERSION="0.1 Boot (boot)"
neura-x86-64:~# systemctl is-system-running
running
neura-x86-64:~# systemctl list-units --failed
0 loaded units listed
neura-x86-64:~# lsblk                  # sda: sda1 (EFI 512M) + sda2 (root ext4 2G)
neura-x86-64:~# df -h /                # overlay on /  (root mounted rw via overlay, sda2 lowerdir)
neura-x86-64:~# poweroff               # "reached target Power-Off", QEMU exits with code 0
```

Basic Ethernet DHCP worked in 0.1 through systemd-networkd and the stock
`/usr/lib/systemd/network/80-wired.network`; the 0.2 stage replaced that
with NetworkManager and added Wi-Fi (see *Networking (0.2)* below). The
error-level journal entries that the 0.1 boot logged were all expected at
that time (busybox-syslog advice, missing BPF/cgroup firewalling, missing
`regulatory.db` — all resolved or moot in 0.2).

## QEMU boot test (0.2 acceptance)

Same invocation as above (against the 0.2 build; the `.wic` is still the
installed-system artifact). Full console log:
`build-neura/neura-boot-test-04.log`; the scripted driver is
`build-neura/boot-test-04.sh`. Observed on the 0.2 image:

```
NEURA Linux 0.2 hw-net neura-x86-64 ttyS0
systemctl is-system-running   -> running
systemctl list-units --failed -> 0 loaded units listed
systemctl is-enabled  iwd NetworkManager     -> enabled, enabled
systemctl is-active   iwd NetworkManager     -> active,  active
busctl list | grep iwd                       -> net.connman.iwd (iwd's D-Bus name)
nmcli -t -f DEVICE,TYPE,STATE device         -> eth0:ethernet:connected
nmcli -t -f IP4.ADDRESS dev show eth0        -> 10.0.2.15/24 (internal DHCP client)
networkctl                                   -> not found (systemd-networkd not installed)
journalctl -b -p 3                           -> No entries
find /lib/firmware/amdgpu | wc -l            -> 675 blobs (all generations)
find /lib/firmware/iwlwifi | wc -l           -> 190 blobs
ls /lib/firmware/regulatory.db*              -> regulatory.db + regulatory.db.p7s
iw dev                                       -> empty: QEMU has no WLAN device
```

Two firmware-related messages remain in the log, both understood, neither a
missing blob:

* one `dmesg` line at ~0.65 s: `regulatory: Direct firmware load for
  regulatory.db failed with error -2` — CFG80211 is built into the kernel,
  so it asks for the file before the root filesystem is mounted; the file is
  in the image and the later load succeeds. A boot-order artifact of the
  `builtin CFG80211 + root-on-disk` combination, documented rather than
  fixed.
* iwd logs `EAP_MSCHAPv2 init: MD4 support not found, skipping` — EAP
  (enterprise Wi-Fi) is optional and iwd degrades gracefully. Enabling
  `CONFIG_CRYPTO_MD4` is an open decision (see known issues).

## Live ISO boot test (0.2 acceptance)

The live artifacts come from `IMAGE_FSTYPES += "live"`
(`image-live.bbclass`, `INITRD_IMAGE_LIVE = "neura-live-initramfs"`,
`LABELS_LIVE = "boot"`, `LIVE_ROOTFS_TYPE = "ext4"`). The ISO contains
`/bzImage`, `/initrd`, `/rootfs.img`, `/efi.img`, `/EFI/BOOT/BOOTX64.EFI` +
grub.cfg and the isolinux catalog — one El Torito image, MBR/UEFI bootable.
Hybrid-MBR evidence on the final artifact:

```
isoinfo -d          -> Volume id: boot        (LABELS_LIVE)
fdisk -l <iso>      -> Disklabel type: dos
                       iso1 *  0   2246655  1.1G  Empty     (whole-medium boot entry)
                       iso2    120   53615   26M  EFI (FAT-12/16/32)   (from /efi.img)
hexdump @MBR 0x1be  -> entry1 0x80 bootable, entry2 type 0xEF  -> classic "isohybrid -u"
```

That is exactly the layout Ventoy chainloads (bootable partition table +
El Torito + ESP partition), and `image-live.bbclass` only runs
`isohybrid -u` while `pcbios` is in `MACHINE_FEATURES` — do not drop it.

Boot test: QEMU q35/KVM with the same OVMF pflash pair, the ISO as `-cdrom`
and **no hard disk attached**; grub's generated live config
(`timeout=10`, default entry `boot`, `serial --unit=0 --speed=115200`) means
the session boots by itself onto ttyS0. The scripted driver is
`build-neura/live-test-01.sh`; its QEMU command line:

```bash
ISO=tmp/deploy/images/neura-x86-64/neura-image-minimal-neura-x86-64.rootfs.iso
VARS=$(mktemp /tmp/live-vars.XXXX).qcow2
cp tmp/deploy/images/neura-x86-64/ovmf.vars.qcow2 $VARS
qemu-system-x86_64 -machine q35 -enable-kvm -m 2048 -smp 2 -vga none -nographic \
    -drive file=tmp/deploy/images/neura-x86-64/ovmf.code.qcow2,if=pflash,format=qcow2,unit=0,readonly=on \
    -drive file=$VARS,if=pflash,format=qcow2,unit=1 \
    -cdrom $ISO \
    -netdev user,id=net0,hostfwd=tcp:127.0.0.1:2223-:22 -device e1000,netdev=net0
```

Inside the session (full console log `build-neura/neura-live-test-01.log`):

```
uname -a / cat /etc/os-release -> same 0.2 kernel/build, booted LABEL=boot
mount                          -> overlay on / type overlay (rw, lowerdir=/rootfs.ro,
                                 upperdir=/rootfs.rw/upperdir, workdir=/rootfs.rw/work)
/proc/mounts                   -> /dev/sr0 /media/realroot iso9660 ro,relatime
ls /dev/sr0 /dev/sd* /dev/vd*  -> /dev/sr0 only: no disk exists, nothing written
touch /root-live-write-test    -> write-ok; df -h / shows ~985M (tmpfs-backed upper)
systemctl is-system-running    -> running;  systemctl list-units --failed -> 0
systemctl is-active iwd NetworkManager -> active, active
nmcli device                   -> eth0:ethernet:connected (DHCP 10.0.2.15/24)
```

OE-Core's stock `init-live.sh` (shipped by the `initramfs-live-boot` package
used by `neura-live-initramfs`) loop-mounts `/media/realroot/rootfs.img`
read-only and, because the medium is not writable, mounts the session root
as an overlay with a tmpfs upper layer — the "temporary root filesystem"
requirement is met without any NEURA-specific initramfs code.

Two testing notes worth keeping: with QEMU user-mode networking the
**host-side TCP connect to the forwarded port succeeds before the guest
stack is up** — the test waits for actual `SSH-` banner bytes instead
(port 2223 to stay clear of `runqemu`'s 2222); and the guest's sshd (from
the debug features in the image) drops the pexpect session straight into a
root shell, which the driver handles as a dual-branch expectation.

## Networking (0.2)

Everything is stock Yocto; NEURA only *selects* components, it implements no
network code:

* **NetworkManager 1.56.0** (meta-networking), installed as the three parts
  `networkmanager-daemon` + `networkmanager-wifi` + `networkmanager-nmcli`
  (the `networkmanager` meta package is empty and does not even recommend
  `${PN}-daemon`), with its own internal DHCP client. Ethernet: NM takes the
  device, DHCPs it and auto-connects — verified connected in both boot
  tests above.
* **iwd 3.12 as the Wi-Fi backend**, selected in `conf/distro/neura.conf`
  via meta-networking's own switches:
  `WIRELESS_DAEMON = "iwd"` (OE-Core's packagegroup switch) plus
  `PACKAGECONFIG:append:pn-networkmanager = " iwd"` (adds `-Diwd=true` and
  the `enable-iwd.conf` drop-in shipped by `networkmanager-wifi`; the
  recipe's `get_wifi_deps()` then RDEPENDS iwd instead of wpa-supplicant,
  so wpa-supplicant stays out of the image).
* **systemd-networkd is removed distro-wide**:
  `PACKAGECONFIG:remove:pn-systemd = "networkd"` drops both the package and
  the stock `80-wired.network` unit that grabbed every interface in the 0.1
  build, leaving NM as the only interface handler (`networkctl` absent in
  the image).
* **Saved Wi-Fi profiles / auto-connect** are the default behaviour of the
  stack: NM persists connections (`/var/lib/NetworkManager` keyfiles) and
  with the iwd backend the provisioned pre-shared keys land in iwd's known
  networks under `/var/lib/iwd`; both components reconnect saved networks
  at start. **This has not been exercised end-to-end**: QEMU has no WLAN
  device (`iw dev` is empty), so a real adapter + real network join is a
  pending item for the first hardware bring-up. `iw` and `nmcli` are in the
  image for that.

The biggest trap of this stage was kernel-side, and is documented in
`neura-iwd.cfg`: iwd's bundled ELL probes crypto support **exclusively via
AF_ALG sockets**, so without `CONFIG_CRYPTO_USER_API_{HASH,SKCIPHER,RNG,AEAD}`
built-in it fails its startup self-check (`No HMAC(SHA1) support found …
CONFIG_CRYPTO_USER_API_HASH CONFIG_CRYPTO_USER_API_SKCIPHER
CONFIG_KEY_DH_OPERATIONS …`) and `iwd.service` exits 1. The fragment sets
those plus `CRYPTO_MD5`/`CRYPTO_DES` (iwd hard-fails on them),
`KEY_DH_OPERATIONS` (Diffie-Hellman) and `PKCS8_PRIVATE_KEY_PARSER`
(which also clears systemd-modules-load's `pkcs8_key_parser` error by making
the module built-in). Boot test 04 confirms iwd enabled/active and
`journalctl -b -p 3` empty.

## Hardware firmware (0.2)

The `linux-firmware` metapackage is removed from the machine's recommends
(`MACHINE_EXTRA_RRECOMMENDS:remove`) and replaced by a curated,
machine-scoped list (`NEURA_FIRMWARE_PACKAGES` in
`conf/machine/neura-x86-64.conf`) that the image installs directly — the
metapackage would drag in every blob in the tree:

* `linux-firmware-amdgpu` — AMD GPU firmware (all generations, 675 blobs in
  the image) + the Firmware-amdgpu licence package,
* `linux-firmware-rtl-nic` — `rtl_nic/*.fw*` for Realtek r8168/r8125 wired
  NICs,
* gated on the `wifi` DISTRO_FEATURE, the Wi-Fi families that
  `neura-wifi.cfg` actually enables: `iwlwifi`, `mediatek` (MT7921/MT7925
  blobs live in `firmware/mediatek/`), `ath10k`, `ath11k`, and Realtek
  `rtl8723/8761/8821/8822/8851/8852/8922` (rtw88/rtw89; Realtek firmware has
  no aggregate package, so each family is named).

`wireless-regdb-static` (2026.09.03) ships `/lib/firmware/regulatory.db` +
`.p7s`, which iwd wants. The rootfs manifest lists 415 packages (0.1: 260);
iw 6.17, iwd 3.12, NetworkManager 1.56.0. Boot test 04's audit found no
unexplained missing-firmware errors (the single `regulatory.db` message is
the documented boot-order artifact).

## Kernel: how AIlinux is integrated

`recipes-kernel/linux/linux-neura_7.4.0-rc2.bb` fetches the pinned AIlinux
commit (see the baseline table) via `git://github.com/Fort-fog/AIlinux`;
`S = "${UNPACKDIR}/${BP}"` (Yocto 6.0 unpack layout). The kernel is **never**
vendored into this repository.

NEURA configuration: **five** `.cfg` fragments in
`recipes-kernel/linux/linux-neura/`, merged in SRC_URI order by
`do_configure:prepend`:

```
${S}/scripts/kconfig/merge_config.sh -m -O ${B} \
    ${UNPACKDIR}/neura-boot.cfg ${UNPACKDIR}/neura-hardware.cfg \
    ${UNPACKDIR}/neura-wifi.cfg ${UNPACKDIR}/neura-live.cfg \
    ${UNPACKDIR}/neura-iwd.cfg
make olddefconfig
```

on top of `x86_64_defconfig` (`defconfig` in SRC_URI). `merge_config.sh`
warns loudly when a fragment line is ignored, so fragment typos do not go
silent. Two fragment rules learned in this stage, recorded in the fragment
comments:

* a symbol **without a prompt** (`CRYPTO_USER_API`, `RTW88_CORE`,
  `MT76_CORE`, …) cannot be set from a fragment — set its prompt-bearing
  children (or the device entries that `select` it) instead;
* a device entry is only visible when its **prompt-bearing parent** is on
  (`RTW88`, `RTW89`, `ATH10K`, `ATH11K`), otherwise `make olddefconfig`
  drops every child — the first 0.2 build silently lost the Realtek and
  Atheros drivers before this rule was applied.

Symbols added in 0.2 (verified in the built `.config`): `OVERLAY_FS=y`
(live sessions); `IWLWIFI/IWLDVM/IWLMVM`, `MT7921E/U`, `RTW88*`, `RTW89*`,
`ATH10K(_PCI)`, `ATH11K(_PCI)` as modules; the nine iwd crypto symbols above
(plus `CRYPTO_DH`/`CRYPTO_KDF800108_CTR`, auto-selected). As at 0.1, symbol
names were checked against the pinned AIlinux tree (`git show
<SRCREV>:path`); symbols that do **not** exist in 7.4 and are therefore not
referenced: `DRM_AMDGPU_USERPRIVILEGE`, `RTL8821CE` (staging driver; the in-tree
`RTW88_8821CE` is used), `CONFIG_WIRELESS`.

## Distro configuration notes (Yocto 6.0 specifics)

These choices are what make the build work on the 6.0 "wrynose" release and
are easy to regress; see the comments in `conf/distro/neura.conf`:

* Yocto 6.0 introduced `DISTRO_FEATURES_DEFAULTS` /
  `DISTRO_FEATURES_OPTED_OUT`; `neura.conf` keeps the defaults and opts out
  of nothing beyond `x11`/`xattr-compat`-style removals already listed there.
* The init manager is `systemd` (set in `local.conf.sample`).
* The build template follows the `defaultsetup.conf` pattern rather than
  Poky's; `INHERIT_DISTRO` is explicit (`devshell sstate license
  remove-libtool buildstats uninative`).
* Yocto 6.0 split the old `debug-tweaks` image feature into
  `empty-root-password` / `allow-empty-password` / `allow-root-login` /
  `serial-autologin-root` — the image recipe lists them explicitly and they
  must not ship in a released image.
* Recipes unpack into `${UNPACKDIR}`; `S = "${UNPACKDIR}/${BP}"` is required
  for git-tarball/kernel-style unpacks.
* `IMAGE_FSTYPES += "live"` auto-inherits `image-live.bbclass`, which builds
  the ISO *and* the hddimg from the same image: kernel + `INITRD_IMAGE_LIVE`
  initramfs + `LIVE_ROOTFS_TYPE` rootfs image, grub-efi and isolinux
  catalogs, and runs `isohybrid -u` only while `pcbios` is in
  `MACHINE_FEATURES`. `LABELS_LIVE = "boot"` (default `"boot install"`) is
  what keeps the initramfs from offering to dd the image onto the first
  disk — restore the `install` label when a real installer exists.
* `rootfs-postcommands.bbclass` bakes `PermitEmptyPasswords yes` /
  `PermitRootLogin yes` into sshd config and the serial getty autologin
  when the debug image features are on — development-only conveniences that
  the live/`.wic` boot tests rely on.

## Roadmap

1. `NEURA 0.1 Boot` — **done** (tag `neura-boot-0.1`; kernel boot, systemd,
   headless QEMU acceptance).
2. `NEURA 0.2 hw-net` — **done** on `neura-stage-2-hw-net` (firmware,
   NetworkManager + iwd, live ISO). QEMU-verified; the real-hardware pass
   (actual Wi-Fi adapter, AMD GPU) is pending hardware availability.
3. `NEURA 0.3 Wayland desktop` — next: wlroots-based compositor, labwc or
   similar, Mesa for amdgpu, terminal, bar, input method, locker.
4. Audio (PipeWire) and the remaining user tools.
5. Installer: the live ISO is the delivery medium already; an install-to-disk
   program would plug back into `LABELS_LIVE` and the initramfs modules.

## Known issues and open decisions (0.2)

* The single boot-time `regulatory.db` firmware-load failure line (boot-order
  artifact of built-in CFG80211); harmless, file present, later load
  succeeds. A `CFG80211=m` rework would remove it but is not worth the
  regression risk this stage.
* iwd skips `EAP_MSCHAPv2` without `CONFIG_CRYPTO_MD4` (enterprise Wi-Fi
  only, graceful). Open decision: add `CRYPTO_MD4` when enterprise networks
  matter.
* Wi-Fi and GPU paths are **shipped but not hardware-exercised** — QEMU has
  no WLAN and emulates no AMD GPU. First real-machine bring-up is the next
  verification step.
* `IMAGE_FSTYPES += "live"` also produces `.hddimg` (BIOS/syslinux hybrid,
  ~1.14 GB). Kept for now as a byproduct; filtering it out later is a
  one-line `IMAGE_FSTYPES` adjustment if it is noise.
* Size growth: rootfs 260 → 415 packages, rootfs image ~1.05 GB, ISO ~1.07
  GiB. The largest block is `linux-firmware-amdgpu` (all generations). If
  NEURA later targets specific GPUs, narrow to the concrete
  `linux-firmware-amdgpu-<gen>` subpackages.
* Not yet shipped (open decision): `ath12k`/`ath12k` firmware (Qualcomm Wi-Fi
  7), USB RTL8152/RTL8153 wired adapters (`CONFIG_USB_RTL8152`) — each is a
  fragment line + firmware package when needed.
* Both artifacts carry the development login conveniences (empty root
  password, root ssh login, serial autologin). Removing them is a hard
  requirement for any released image.

## Known risks at 0.1 (kept)

* Kernel 7.4.0-rc2 is an RC: fine for development, not a release basis yet.
* OE 6.0.4 + kernel headers 7.4.0-rc2: userspace headers lag the kernel;
  nothing in 0.1/0.2 userspace notices.
* `x86_64_defconfig` is broad; trimming it is deferred. (AMD GPU firmware,
  deferred at 0.1, shipped in 0.2.)
