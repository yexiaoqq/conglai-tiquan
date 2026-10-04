# 聪来提权 · 构建与版本维护完全教程（BUILD_GUIDE）

> 本文件是「聪来提权（conglai-tiquan）」这套 APK 的**完整可复现构建手册**，
> 同时作为 AI / 维护者的**下次开工入口**：包含工具链、八步构建、版本号替换、
> 新系统（新固件 / 新内核）验证、发布流程、哈希速查与已知坑。
>
> 维护约定：**本教程只描述「当前状态」，不做过程流水追加**；新结论直接改写对应章节。
> 最后更新：2026-10-04（双版本：KernelSU 3.2.5 / 3.3.0）。

---

## 0. 速览（TL;DR）

- **本 App 是 DFRoot / DFReroot 的机型适配 + 界面重制版**，不是原创提权链。
- **唯一需要改版本的地方 = `assets/ksud`**：`ExploitRunner.stageKsud()` 从 `assets/ksud`
  读流写盘并 `setExecutable`，所以切换 KernelSU 版本**只需替换这一个文件**，无需改 dex。
- 构建链：`apktool d` 反编译基线 → 替换 `assets/ksud` → `javac` 编译 Kotlin 重写的
  `MainActivity` → `d8` → `baksmali` → 回灌 `dec/smali_classes3` 与 `dec/res` →
  `apktool b` → `zipalign` → `apksigner`。
- 发布：**单一 Release `v2.0-md3` 内放两个资产**，用资产名区分 ksud 版本。

---

## 1. 环境与工具链

### 1.1 运行环境

| 环节 | 环境 | 说明 |
| --- | --- | --- |
| 反编译 / 编译 / 打包 / 签名 | **Ubuntu（proot 终端）** | `super_admin:terminal`，路径用 Linux 格式 |
| 运行 bionic 二进制（`ksud -V` 等） | **Android shell** | `super_admin:shell`（Root/Shizuku）。proot **跑不了** bionic 二进制 |
| 文件中转 | `/sdcard/Download` ↔ `/data/local/tmp` | proot 对 `/data/local/tmp` 部分文件无读权，二进制走 sdcard 中转 |

> ⚠️ 禁止在脚本里用 `set -e` / `set -o errexit`（会直接退出终端会话卡死）。
> 多行内联命令易失效 → **写脚本文件再 `sh` 执行**。

### 1.2 工具链与固定路径

| 工具 | 路径 |
| --- | --- |
| android.jar | `/home/rmg/android-sdk/platforms/android-36/android.jar` |
| d8 | `/home/rmg/android-sdk/build-tools/34.0.0/d8` |
| apksigner | `/home/rmg/android-sdk/build-tools/34.0.0/apksigner` |
| apktool（arm64） | `/home/rmg/work/apktool-arm64.jar` |
| baksmali | `/root/rmg2/baksmali.jar` |
| 签名 keystore | `/home/rmg/build-libexp/ks.keystore`（`--ks-pass pass:android --key-pass pass:android --ks-key-alias k`） |
| zipalign | `/home/rmg/ui/zipalign.py`（Python 实现，非 build-tools 的 zipalign） |
| 反编译基线工作区 | `/home/rmg/work/dec` |
| 构建产物目录 | `/home/rmg/ui` |

### 1.3 需预装

```sh
# JDK（javac/java）—— baksmali 与 apktool 都是 jar
java -version && javac -version
# android-sdk（platforms/android-36 + build-tools/34.0.0）
# python3（zipalign.py、发布脚本）
```

---

## 2. 工作区结构

```
/home/rmg/work/
├── apktool-arm64.jar            # apktool（arm64 版）
├── dec/                         # ★ 反编译基线（apktool d 的产物）
│   ├── AndroidManifest.xml
│   ├── apktool.yml
│   ├── assets/ksud              # ★★ 内嵌 ksud —— 版本切换唯一替换点
│   ├── lib/arm64-v8a/libexp.so  # 漏洞利用链（DirtyFrag）；机型相关，重编见 §6
│   ├── res/                     # 界面资源（MD3 重制版注入处）
│   ├── smali/ smali_classes2/   # 原 dex
│   └── smali_classes3/df/root/  # ★ 注入的 MainActivity 重写 smali
└── (build 日志)

/home/rmg/ui/
├── build_ui_ksu330.sh           # 构建 3.3.0 版（自包含，第0步自动换 ksud）
├── build_ui_ksu325.sh           # 构建 3.2.5 版
├── build_ui.sh                  # 基版（内嵌 ksud 由 dec 现状决定）
├── publish_ksu330.py / merge_release.py   # 发布 / 合并脚本
├── _ksud330.bin                 # ksud 3.3.0（4892712 B）
├── _ksud325.bin                 # ksud 3.2.5（4879560 B）
├── _ksud_fork_backup.bin        # fork 版备份（6028928 B）
├── src/  res/  stub/            # 重写 MainActivity 的 Java / 资源 / 桩
└── zipalign.py
```

仓库内 `source/` 存放可公开的构建源（`build_ui.sh`、`src/`、`res/`、`stub/`、`zipalign.py`）。

---

## 3. 一次性准备：反编译基线

若 `dec/` 不存在或需要重新对齐上游：

```sh
cd /home/rmg/work
java -jar apktool-arm64.jar d <原始APK> -o dec
```

要点：
- 原始 APK = 待适配的 DFRoot/DFReroot 个人版基底。
- 反编译后确认 `dec/assets/ksud`、`dec/lib/arm64-v8a/libexp.so` 存在。
- `libexp.so` 与机型/内核强相关（见 §6），换机型必须重编。

---

## 4. 构建流程（八步）

以 3.3.0 版为例，脚本 `build_ui_ksu330.sh` 完整流程：

| 步 | 动作 | 命令 / 要点 |
| --- | --- | --- |
| **[0]** | **换内嵌 ksud** | `cp _ksud330.bin dec/assets/ksud`；校验 md5 |
| **[1]** | 编译桩 | `javac -cp android.jar -d cp stub/... src/df/root/{IReporter,ExploitRunner,BootReceiver}.java` |
| **[2]** | 编译 MainActivity | `javac -encoding UTF-8 -cp android.jar:cp -d out src/df/root/MainActivity.java` |
| **[3]** | d8 → dex | `d8 --release --lib android.jar --min-api 33 --output dexo out/**/MainActivity*.class` |
| **[4]** | baksmali → smali | `java -jar baksmali.jar disassemble dexo/classes.dex -o dexs` |
| **[5]** | 回灌 smali | 覆盖 `dec/smali_classes3/df/root/MainActivity*.smali`（先删旧 + 删 `$$ExternalSyntheticLambda` / `databinding`） |
| **[5b]** | 回灌资源 | 覆盖 `dec/res/{drawable,color,menu,layout}` |
| **[6]** | apktool b | `java -jar apktool-arm64.jar b dec -o ui-build-k330.apk --use-aapt2` |
| **[7]** | zipalign | `python3 zipalign.py ui-build-k330.apk ui-aligned-k330.apk 4` |
| **[8]** | 签名 | `apksigner sign --ks ks.keystore ... --out conglai-md3-ui-ksu330-signed.apk ui-aligned-k330.apk` |

一键执行：

```sh
cd /home/rmg/ui && sh build_ui_ksu330.sh      # 出 3.3.0 版
cd /home/rmg/ui && sh build_ui_ksu325.sh      # 出 3.2.5 版
```

成功标志：脚本末尾打印 `== BUILD_OK ==` 并 `ls -l` 产物。

---

## 5. 更新版本号（替换内嵌 ksud）

> ⚠️ **版本号的真正来源是内嵌 ko 的编译常量（`30000 + git提交数 − 7`），且“已加载则跳过”导致必须重启/重跑才生效。完整溯源与修正配方见 [`KSU_VERSION_FIX.md`](KSU_VERSION_FIX.md)。**

**核心原则：换 KernelSU 版本 = 换 `assets/ksud` 一个文件。**

### 5.1 从 KernelSU 管理器 APK 取 ksud

```sh
python3 -c "import zipfile; z=zipfile.ZipFile('<ksu-manager.apk>'); \
open('/home/rmg/ui/_ksudNEW.bin','wb').write(z.read('lib/arm64-v8a/libksud.so'))"
```

### 5.2 验证版本（必须在 Android shell，proot 跑不了）

```sh
# 经 sdcard 中转
cp /home/rmg/ui/_ksudNEW.bin /sdcard/Download/_ksudNEW.bin
# Android shell：
cp /sdcard/Download/_ksudNEW.bin /data/local/tmp/_ksudNEW.bin
chmod 755 /data/local/tmp/_ksudNEW.bin
/data/local/tmp/_ksudNEW.bin -V      # 期望形如：ksud 3.3.0 (uapi: 2)
```

### 5.3 构建联动（改哪几个地方）

1. 把新 ksud 放进 `/home/rmg/ui/_ksudNEW.bin`；
2. 复制一份构建脚本，改两处：**第 [0] 步的源文件** 与 **输出 APK 名**；
3. 校验 md5（脚本里 `echo "expect md5 ..."`）；
4. 运行脚本出包。

> 版本号还体现在 release 命名与资产名，见 §8。

### 5.4 已知 ksud 哈希对照（唯一基准）

| 版本 | 大小(B) | md5 | 来源 |
| --- | --- | --- | --- |
| KernelSU 3.2.5 (32525) | 4879560 | `fa6378c07089ef6223da75cdba632896` | KernelSU_v3.2.5 APK `lib/arm64-v8a/libksud.so` |
| KernelSU 3.3.0 (330) | 4892712 | `de059ed8ffd896129a0a4bae338c946a` | ksu-manager-330.apk `lib/arm64-v8a/libksud.so` |
| fork 版（uapi:4，非官方） | 6028928 | `a53980a72d41e616f1026cea365124e1` | 早期调试用，非发布版 |

---

## 6. 新系统（新固件 / 新内核）验证流程

**适配新机型或新固件的关键不是 UI，而是内核侧匹配。** 按以下顺序核验：

### 6.1 采集目标设备指纹

```sh
# Android shell
uname -r                                   # 如 5.15.189-android13-8-3251900-abS9110ZCS8FZI1
getprop ro.product.model                   # SM-S9110
getprop ro.build.display.id                # 固件基线，如 S9110ZCS8FZI1
cat /proc/version
```

三要素决定 payload / LKM 是否可用：**KMI（android13-5.15）+ 内核 release 前三段 + 固件基线**。

### 6.2 内核加载约束（务必确认）

| 配置 | 含义 | 影响 |
| --- | --- | --- |
| `CONFIG_MODULE_SIG_FORCE` | 未设置 → 签名闸门是开的 | 可加载未签名模块 |
| `CONFIG_MODVERSIONS=y` | 符号版本校验开 | **外部 .ko 必须针对目标内核重编**，泛用版不行 |
| `CONFIG_STRICT_MODULE_RWX=y` | W^X | 运行时改模块代码段受限 |

> 检查方式：读目标内核的 `config` / `Module.symvers`（构建 LKM 需 `Module.symvers` 对齐 CRC）。

### 6.3 `libexp.so`（DirtyFrag 链）重编

- `libexp.so` 内嵌针对特定内核构建的 `dirtyfrag.ko` 槽位；**槽位大小对齐**，超长无法原地覆盖。
- 重编流程（参考）：`clang`(18) + NDK 作 sysroot，`--target=aarch64-linux-android35`，
  `-fuse-ld=lld --rtlib=compiler-rt`；先编 splicehelper（freestanding 静态），再编 `libexp.so`。
- 重编后必须用目标内核的 **vermagic** 烘焙，且用目标内核 `Module.symvers` 校 CRC。

### 6.4 验证分级（避免一次进红区）

1. **仅静态验证（零风险）**：解包 APK，核对 `assets/ksud` md5、`libexp.so` 大小、vermagic 串、
   签名证书 `CN=dfroot` 一致。
2. **可加载性验证**：用「入口首指令改返回错误」的 noop 版 `.ko` 做对照实验——
   `errno` 会精确跟随补丁返回值（EINVAL/EOPNOTSUPP），可证明内核是否通过全部加载期校验。
3. **完整功能验证（红区，需授权）**：真实跑 exploit 链，风险=内核 panic / 重启 / Shizuku 掉线。

### 6.5 装机冒烟测试

```sh
# Android shell
cp <apk> /data/local/tmp/x.apk      # pm install 不能直接喂 /sdcard（fuse 挂载被拒）
pm install -r /data/local/tmp/x.apk
am force-stop <pkg>; logcat -c
monkey -p <pkg> -c android.intent.category.LAUNCHER 1
sleep 3; pidof <pkg>                # 有 pid 且 logcat 无崩溃即通过
```

> 降级安装（versionCode 变小时）需 `pm install -r -d`。

---

## 7. 发布到 GitHub（双版本同 Release）

### 7.1 目标形态

**单一 Release**（tag `v2.0-md3`）内两个资产，靠资产名区分：

| 资产名 | 内嵌 ksud | 适用 |
| --- | --- | --- |
| `conglai-md3-ksu32525-signed.apk` | KernelSU 3.2.5 (32525) | 驱动上报 32525 的设备（默认/稳定） |
| `conglai-md3-ksu330-signed.apk` | KernelSU 3.3.0 (330) | 驱动上报 3.3.0 的设备 |

### 7.2 用 API 合并（脚本 `merge_release.py`）

```
1) PATCH /repos/{owner}/{repo}/releases/assets/{old_asset_id}   # 重命名旧资产
2) POST  https://uploads.github.com/repos/{owner}/{repo}/releases/{release_id}/assets?name=...
3) PATCH /repos/{owner}/{repo}/releases/{release_id}            # 改 release 名 + 说明（双版本对照表）
4) DELETE /repos/{owner}/{repo}/releases/{extra_release_id}     # 删冗余 release（资产随之删除）
   DELETE /repos/{owner}/{repo}/git/refs/tags/{extra_tag}
5) GET   /repos/{owner}/{repo}/releases                         # 回读终态
```

### 7.3 发布后校验（必做）

```sh
curl -sL -H "Authorization: token $TOK" -o a.apk \
  -H "Accept: application/octet-stream" \
  "https://api.github.com/repos/yexiaoqq/conglai-tiquan/releases/assets/<id>"
md5sum a.apk        # 必须与本地构建产物逐字节一致
```

### 7.4 管理要求

- **同一 Release 内所有资产必须同名规则**：`conglai-md3-ksu<版本>-signed.apk`。
- 发新版 = 上传新资产 + 更新对照表；旧版资产保留以便回退。
- ⚠️ 不要把 PAT 写进仓库内文件；脚本中的 token 用环境变量或一次性使用后删除。

---

## 8. 关键哈希与路径速查

```
# 本机构建脚本
/home/rmg/ui/build_ui_ksu330.sh     → conglai-md3-ui-ksu330-signed.apk (16829948 B, md5 a3d09f17…)
/home/rmg/ui/build_ui_ksu325.sh     → conglai-md3-ui-ksu325-signed.apk (15896060 B)
# 替换点
/home/rmg/work/dec/assets/ksud      ← 唯一版本切换点
# ksud 缓存
/home/rmg/ui/_ksud330.bin (4892712)  /home/rmg/ui/_ksud325.bin (4879560)
# 设备内核基线（SM-S9110 / FZI1）
5.15.189-android13-8-3251900-abS9110ZCS8FZI1   KMI=android13-5.15
# 包名 / 版本
df.root   versionName 2.0 / versionCode 2   minSdk 32 / targetSdk 36
```

---

## 9. 已知坑与约束

1. **proot 跑不了 bionic 二进制** → `ksud -V`、`.ko` 加载实验都必须在 Android shell。
2. **`pm install` 不能直接喂 `/sdcard`**（`u:object_r:fuse:s0` 被 system_server 拒）→ 先 `cp` 到 `/data/local/tmp`。
3. **Operit 危险命令拦截**：`rm -rf <目录>` 会被挡 → 用精确文件名 `rm -f` + `rmdir`。
4. **软重启掉 Shizuku binder**（UI 自动化同依赖）→ root 通道优先用 KernelSU。
5. **apktool b 必须 `--use-aapt2`**，否则资源编译失败。
6. **签名证书固定 `CN=dfroot`**：改版 APK 与原版签名不同，**无法覆盖安装**，需先卸载原版。
7. **版本错配是大坑**：KernelSU 管理器版本必须与驱动上报版本一致（本机 late-load 驱动固定上报 32525，
   `ksud` 3.3.0 与管理器 3.3.0 须成对）。发版时明确标注适配的驱动版本。

---

## 10. 变更记录（仅记「当前状态」结论）

- **2026-10-04**：确立双版本发布形态（同一 Release `v2.0-md3` 两资产）；构建脚本已自包含（第0步自动换 ksud）；
  教程首版落地。
- 历史：曾误内置 fork 版 ksud（uapi:4），已改回官方 3.2.5；首次适配成功经 Root My Galaxy payload
  在 SM-S9110/FZI1 上取得临时 root，后按用户要求手机还原为 32525 版。

---

*本文档由 AI 辅助生成与维护，随构建链变更持续改写。*
