# Ubuntu Rootfs 离线预置包说明

将对应架构的 Ubuntu rootfs 压缩包放在此目录，App 安装时将优先使用本地包，
避免从网络下载。

文件命名规则：
  ubuntu-rootfs-aarch64.tar.xz   → ARM64 手机（绝大多数现代手机）
  ubuntu-rootfs-arm.tar.xz       → 32 位 ARM 手机（较老机型）
  ubuntu-rootfs-x86_64.tar.xz    → x86_64 模拟器

下载地址（Ubuntu rootfs 来自 Ubuntu 官方 CDN，不是 GitHub）：

  ARM64（aarch64）— 绝大多数现代手机：
    https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz

  国内加速镜像（推荐）：
    https://mirrors.tuna.tsinghua.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz
    https://mirrors.ustc.edu.cn/ubuntu-cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz

  下载后改名为 ubuntu-rootfs-aarch64.tar.xz（即使原文件是 .tar.gz 也可以，
  只要 apply_local_rootfs 里 SHA256 被清空，proot-distro 不检验格式名称）。

  注意：若 proot-distro 内置的 ubuntu.sh 指向的是 .tar.xz 格式，请下载对应格式。
  可在设备 Termux 中运行 cat $PREFIX/etc/proot-distro/ubuntu.sh 确认实际 URL。

注意：
  - 文件较大（约 70~100 MB），不建议提交到 git，可加入 .gitignore
  - 本地包不做 SHA256 校验（信任开发者打包），安装失败会自动回退到网络镜像
  - 若此目录无匹配文件，自动走网络安装（国内镜像加速）
