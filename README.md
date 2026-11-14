# NEURA Linux

Distro repository for **NEURA Linux**, an x86-64 Linux distribution built with
Yocto Project / OpenEmbedded.

Role separation in this project:

| Repository | Role |
| --- | --- |
| <https://github.com/Fort-fog/AIlinux> | the **NEURA kernel source** (a Linux 7.4 tree). The kernel is *not* copied into this repository and is *not* replaced by `linux-yocto` or a vendor kernel. |
| <https://github.com/Fort-fog/NEURA-Linux> (this repository) | the **distro**: the `meta-neura` layer, distro/machine configuration, kernel recipe and configuration fragments, image recipes, build infrastructure. |

## Current stage: `NEURA 0.1 Boot` — achieved

The acceptance test below was run against the image built from this branch and
passed: UEFI/OVMF → grub-efi → AIlinux kernel `7.4.0-rc2-g9d66f26fefac` →
systemd on `multi-user.target` with `systemctl is-system-running` = `running`,
zero failed units, ext4 root on `sda2`, root shell on `ttyS0`, clean
`poweroff` (full console log:
`build-neura/neura-boot-test-01.log`).

Goal of this stage (and nothing beyond it):

* a bootable x86-64 image,
* running the kernel built from `Fort-fog/AIlinux` at a pinned commit,
* that mounts the root filesystem, starts systemd/udev and reaches a login
  prompt in QEMU with UEFI firmware (OVMF),
* reproducible from a clean build directory.

Not part of this stage (intentionally deferred, see [Roadmap](#roadmap)):
NetworkManager/Wi-Fi, Wayland desktop, audio, file managers, monitoring tools,
AI runtime, installer/ISO.

## Build baseline (pinned)

Yocto Project **6.0 LTS "wrynose"**. Since the poky repository was split up,
the release is assembled from three repositories; the `poky` repository itself
is not used.

| Component | Remote | Branch | Pinned commit |
| --- | --- | --- | --- |
| openembedded-core | `https://git.openembedded.org/openembedded-core` | `wrynose` | `33a6bd6cc8a9a976902081642414bd67adc74879` |
| meta-yocto (for `meta-yocto-bsp` only) | `https://git.yoctoproject.org/meta-yocto` | `wrynose` | `b60511544c41f5d29d290681e15c9e302f747efe` |
| bitbake | `https://git.openembedded.org/bitbake` | `2.18` | `6e260eb6ab1631607ea414d9ff8ede1155faa5d9` (bitbake 2.18.0) |
| NEURA kernel | `https://github.com/Fort-fog/AIlinux` | `master` | `9d66f26fefacebfeed15de31c1123451a17ff119` (Linux 7.4.0-rc2) |

`meta-yocto-bsp` is used only for its generic x86 machine includes
(`conf/machine/include/genericx86-common.inc`, `files/wic/genericx86.wks.in`).
`meta-poky` is **not** used: NEURA has its own distro configuration
(`neura.conf`), it does not inherit Poky's policies.

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
│   ├── distro/neura.conf                 # NEURA distro (own, does not require poky.conf)
│   ├── machine/neura-x86-64.conf         # x86-64 UEFI PC machine, core2-64 tuning
│   └── templates/default/                # build-directory templates
│       ├── bblayers.conf.sample
│       ├── local.conf.sample
│       └── conf-summary.txt
├── recipes-core/images/neura-image-minimal.bb
└── recipes-kernel/linux/
    ├── linux-neura_7.4.0-rc2.bb          # kernel from Fort-fog/AIlinux
    └── linux-neura/
        ├── neura-boot.cfg                # boot/systemd/UEFI/virtio config fragment
        └── neura-hardware.cfg            # NVMe, amdgpu, igc, uas
```

## Reproducible build

```bash
# 1. baseline (the three repositories, pinned branches)
mkdir -p ~/NEURA-build && cd ~/NEURA-build
git clone --branch wrynose https://git.openembedded.org/openembedded-core openembedded-core-wrynose
git clone --branch wrynose https://git.yoctoproject.org/meta-yocto meta-yocto-wrynose
git clone --branch 2.18   https://git.openembedded.org/bitbake bitbake

# 2. this repository
git clone https://github.com/Fort-fog/NEURA-Linux.git ~/NEURA-Linux
cd ~/NEURA-Linux && git checkout neura-boot-0.1

# 3. build directory from the NEURA template
cd ~/NEURA-build
TEMPLATECONF=~/NEURA-Linux/meta-neura/conf/templates/default \
    . ./openembedded-core-wrynose/oe-init-build-env build-neura

# 4. the minimal boot image
bitbake neura-image-minimal
```

`meta-neura/conf/templates/default/bblayers.conf.sample` lists the three
layers. `NEURA_BASE` there points at the NEURA-Linux checkout; adjust that one
line (or use `bitbake-layers add-layer`) if the repository lives elsewhere.

Resulting images land in
`build-neura/tmp/deploy/images/neura-x86-64/` (every artifact exists both under
a timestamped name and as an unsuffixed symlink, e.g.
`neura-image-minimal-neura-x86-64.rootfs.wic`):

* `neura-image-minimal-neura-x86-64.rootfs.wic` — partitioned GPT disk image
  (EFI system partition with grub-efi + ext4 root + swap), what the boot test
  boots; 460821504 bytes apparent, ~223 MB on disk (sparse file),
* `…rootfs.wic.zst` / `…rootfs.wic.bmap` / `…rootfs.manifest` (the 260 packages
  of the root filesystem) / `…rootfs.qemuboot.conf`,
* `bzImage-neura-x86-64.bin` (the deployed kernel; `bzImage` is the symlink),
  `modules-neura-x86-64.tgz`, `System.map-7.4.0-rc2-g9d66f26fefac`,
* OVMF firmware (`ovmf.qcow2`, `ovmf.code.qcow2`, `ovmf.vars.qcow2`) used by
  the boot test.

The kernel version carries the pinned `SRCREV` (`CONFIG_LOCALVERSION` is set by
the `kernel` class): `7.4.0-rc2-g9d66f26fefac`, which is what `uname -r` reports
in the running image.

Build evidence for this branch (log: `build-neura/neura-build-03.log`):

```
NOTE: Tasks Summary: Attempted 4614 tasks of which 2144 didn't need to be
                       rerun and all succeeded.
Summary: There were 3 WARNING messages.
```

`grep -cE '^ERROR'` on that log is `0`. The three warnings are the expected
`Failed to fetch URL … attempting MIRRORS` cases for `ca-certificates`,
`socat` and `libsdl2-native` described under [Build host](#build-host); all
three tarballs were recovered from the mirrors and the tasks succeeded.

## QEMU boot test (0.1 acceptance)

The image is a full GPT disk booted by its own grub-efi through OVMF, so it is
started the way a real x86-64 PC boots it - QEMU is not given `-kernel`:

```bash
cd ~/NEURA-build/build-neura
. ../openembedded-core-wrynose/oe-init-build-env .
runqemu neura-x86-64 nographic serial wic ovmf kvm slirp snapshot
```

`slirp` selects QEMU user-mode networking, which needs no root privilege;
without it `runqemu` sets up a tap device through `sudo runqemu-ifup` and fails
on a host without sudo (`Setting up tap device failed`). `snapshot` keeps the
deployed `.wic` pristine (QEMU `-snapshot`) and is also required for the
`.zst` variant. `kvm` is optional and only works where `/dev/kvm` is usable,
without it QEMU runs the image under emulation. `runqemu` takes the machine's
`QB_*` settings from the `.qemuboot.conf` deployed with the image and boots the
`ovmf.qcow2` firmware deployed by the `ovmf` recipe, which the machine pulls in
through `EXTRA_IMAGEDEPENDS`. The plain `.wic` is used on purpose: `runqemu`
refuses compressed `.zst` images unless snapshot mode is enabled. Exit the
session with `Ctrl-a x`.

`runqemu` can also be driven directly from the deployed configuration, which
avoids any machine-name ambiguity:

```bash
runqemu tmp/deploy/images/neura-x86-64/neura-image-minimal-neura-x86-64.rootfs.qemuboot.conf \
    nographic serial ovmf kvm slirp snapshot
```

Result of the acceptance run (console log kept on this build host as
`build-neura/neura-boot-test-01.log`, next to the build logs; the build
directory itself is not part of the repository):

```
NEURA Linux 0.1 neura-x86-64 ttyS0
root@neura-x86-64:~# uname -a
Linux neura-x86-64 7.4.0-rc2-g9d66f26fefac #1 SMP PREEMPT_DYNAMIC … x86_64 GNU/Linux
# /etc/os-release: ID=neura, NAME="NEURA Linux", VERSION="0.1 (boot)"
# /proc/cmdline:   BOOT_IMAGE=/bzImage root=PARTUUID=… rootwait rootfstype=ext4
#                  console=ttyS0,115200 console=tty0
systemctl get-default        -> multi-user.target
systemctl is-system-running  -> running
systemctl list-units --failed -> 0 loaded units listed
lsblk                        -> sda1 /boot (ESP), sda2 /, sda3 [SWAP]
poweroff                     -> systemd-shutdown: Powering off / reboot: Power down
```

12 services were running (systemd-udevd, systemd-networkd, systemd-resolved,
systemd-logind, systemd-timesyncd, dbus, two gettys, journal/klog/syslog).
Basic Ethernet DHCP already works in 0.1 through systemd-networkd and the stock
`/usr/lib/systemd/network/80-wired.network`; stage 2 replaces that with
NetworkManager and adds Wi-Fi. Boot warnings observed were benign and
documented: missing `regulatory.db` (a consequence of deferring
`linux-firmware`), no BPF/cgroup firewalling support in the 0.1 config,
`busybox-syslog` empty `OPTIONS`, and QEMU slirp "martian source" noise.

## Kernel: how AIlinux is integrated

`meta-neura/recipes-kernel/linux/linux-neura_7.4.0-rc2.bb`

* fetches `git://github.com/Fort-fog/AIlinux.git` at `SRCREV =
  9d66f26fefacebfeed15de31c1123451a17ff119` (branch `master`; the repository
  has no tags) and applies **no source patches**;
* base configuration: the tree's own `arch/x86/configs/x86_64_defconfig`;
* NEURA configuration: two `.cfg` fragments merged with the kernel's own
  `scripts/kconfig/merge_config.sh -m -O ${B}` in `do_configure:prepend()`,
  then resolved with `make olddefconfig` (the unmodified
  `KERNEL_CONFIG_COMMAND` of the `kernel` class);
* no driver is ever removed by editing source: driver selection happens only
  through the kernel configuration;
* `CONFIG_RUST`, module signing and `DEBUG_INFO_BTF` are switched off in the
  0.1 fragment to keep the required native toolchain minimal. The AIlinux
  `Makefile` uses clang/LLVM only when `LLVM=` is set, so the normal GCC cross
  toolchain of OpenEmbedded is used.

The `kernel-yocto` class is deliberately **not** inherited: it requires
`kern-tools-native` plus a `kernel-meta` metadata tree (`.scc` feature
descriptions) in the source repository, and AIlinux is a plain kernel tree
without `kernel-meta`. Adding that metadata tree to the kernel repository
would be an unnecessary change to `AIlinux`, so the kernel's own
defconfig + fragment mechanism is used instead.

Symbols used by the fragments were checked against the AIlinux tree at the
pinned commit: `NVME_CORE`, `BLK_DEV_NVME`, `NVME_MULTIPATH`, `DRM_AMDGPU`,
`IGC`, `USB_UAS`, `EFI*`, `EXT4_FS*`, `VFAT_FS`, `NLS*`, namespaces/cgroup/
seccomp/inotify symbols all exist in Linux 7.4.0-rc2.
(`DRM_AMDGPU_USERPRIVILEGE`, `RTL8821CE` and `CONFIG_WIRELESS` do **not**
exist there and are therefore not used.)

## Distro configuration notes (Yocto 6.0 specifics)

These are the 6.0-specific facts the NEURA configuration relies on, so future
changes do not have to be rediscovered:

* `DISTRO_FEATURES_DEFAULT`, `DISTRO_FEATURES_BACKFILL` and
  `DISTRO_FEATURES_BACKFILL_CONSIDERED` are obsolete in 6.0 (listed in
  `BB_RENAMED_VARIABLES`). The defaults now live in
  `DISTRO_FEATURES_DEFAULTS` (`conf/distro/include/default-distrovars.inc`)
  and are appended automatically by `conf/bitbake.conf`; opting out uses
  `DISTRO_FEATURES_OPTED_OUT`. `neura.conf` therefore leaves
  `DISTRO_FEATURES` unset.
* Poky 6.0 defaults its init to **sysvinit** (`POKY_INIT_MANAGER`), so a
  distro that wants systemd must set `INIT_MANAGER = "systemd"` itself.
* A custom distro conf must not re-require `conf/distro/include/defaultsetup.conf`:
  `conf/bitbake.conf` includes the distro conf first and `defaultsetup.conf`
  afterwards, which already brings in default providers, tclibc/tcmode,
  security flags, uninative and the init manager.
* `debug-tweaks` no longer exists as an image feature; it is split into
  `empty-root-password`, `allow-empty-password`, `allow-root-login` and
  `serial-autologin-root`. The 0.1 image uses those (development only - a
  release image must not).
* `UNPACKDIR` (not `${WORKDIR}`) is where `file://` sources land. `bitbake.conf`
  now sets `S = "${UNPACKDIR}/${BP}"` and `do_qa_unpack` in
  `meta/classes-global/insane.bbclass` fails the build for a recipe that still
  assigns `S = "${WORKDIR}/git"`; `linux-neura` therefore states
  `S = "${UNPACKDIR}/${BP}"` explicitly. The `kernel` class overrides `S` with
  `STAGING_KERNEL_DIR` and its `do_symlink_kernsrc` moves the unpacked tree
  there and symlinks `${S}` back - the supported route for a kernel recipe that
  does not inherit `kernel-yocto`.

## Roadmap

1. **0.1 Boot** — this stage, done (kernel from `AIlinux`, boots under
   QEMU/UEFI, systemd + login shell).
2. Networking: NetworkManager, Ethernet DHCP, Wi-Fi (iwd) + `linux-firmware`,
   saved-network auto-connect. Wi-Fi NIC drivers go into
   `neura-hardware.cfg`; `MACHINE_EXTRA_RRECOMMENDS:remove = "linux-firmware"`
   in the machine conf is dropped again at this stage. The
   `regulatory.db`/Wi-Fi-firmware warnings from the 0.1 boot disappear here.
3. Wayland desktop: wlroots/labwc, Mesa (amdgpu), Waybar, input, clipboard,
   screenshots, locker.
4. Audio and remaining user tools.
5. Installer / ISO.

## Known risks at 0.1

* AIlinux is a **7.4-rc2** kernel built with the toolchain and
  `linux-libc-headers` generated from the 6.0.4 baseline: UAPI header
  generation is the usual place where a too-new kernel trips up an older
  userpace baseline.
* `amdgpu` is configured as modules but its firmware blobs are not in the 0.1
  image (`linux-firmware` deferred), so no GPU is expected at this stage.
* The base is `x86_64_defconfig` expanded by `olddefconfig`, so the config is
  larger than a minimal `allnoconfig`-based one. That is the standard OE flow
  and is accepted for the boot checkpoint.
