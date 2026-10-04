# KernelSU 版本号溯源与修正配方（KSU_VERSION_FIX）

> 目的：**终结"每次扫盘找原因"**。本文件给出「聪来提权」中驱动上报版本号（如 32653 / 32525）
> 的**唯一定义位置、唯一查法、唯一替换点、唯一生效条件**。下次遇到版本问题，只看本文即可。
>
> 维护约定：只描述「当前状态」，新结论直接改写，不做流水追加。最后更新：2026-10-04。

---

## 0. 一句话结论

驱动上报的 `Kernel Version` = 编译进 ksud 内嵌 `kernelsu.ko` 的一个整型常量 `KSU_VERSION`，
**= 30000 + (构建该 ko 时的 KernelSU git 提交总数) − 7**。

- 要 **32525** → 用**官方 KernelSU v3.2.5** 的 `libksud.so`（内嵌 ko 编于提交数 2532）。
- 上报 **32653** → 用的是 **fork 版 ksud**（编于提交数 2660）。
- 修复 = **把 APK 里的 `assets/ksud` 换成官方 3.2.5 那个文件**（一个文件），不用改 dex / 界面。

---

## 1. 版本号到底定义在哪（源码钉死）

| 位置 | 内容 |
| --- | --- |
| `kernel/Kbuild:119` | `KSU_GIT_VERSION := $(shell cd $(GIT_ROOT) && git rev-list --count HEAD)` |
| `kernel/Kbuild:132` | `KSU_VERSION = $(shell expr 30000 + $(KSU_GIT_VERSION) - 7)` |
| `kernel/Kbuild:134` | `ccflags-y += -DKSU_VERSION=$(KSU_VERSION)` |
| `kernel/include/ksu.h:8` | `#define KERNEL_SU_VERSION KSU_VERSION` |
| `supercall/dispatch.c` | `do_get_info` / `do_get_info_legacy` 以 `.version = KERNEL_SU_VERSION` 应答 `ksu_get_info_cmd` |

- 设备端读取路径：`ksud debug version` / `ksud debug info`；KernelSU 管理器走 ioctl `KSU_IOCTL_GET_INFO`。
- 计算式：**版本 = 30000 + 提交总数 − 7**
  - 官方 3.2.5 → 提交 2532 → `30000 + 2532 - 7` = **32525**
  - fork 版 → 提交 2660 → `30000 + 2660 - 7` = **32653**
- ⚠️ 该值是**编译期立即数，不是字符串** → 在 ko 里 `strings` 搜 `32525` / `32653` 都是 0 条，别白费力气。

---

## 2. 不加反编译，直接查任意 ksud / ko 的版本号（三选一）

**方法 A — 设备上最快：抽出内嵌 ko**
```sh
/data/local/tmp/ksud debug extract-binary android13-5.15_kernelsu.ko /data/local/tmp/out.ko
```

**方法 B — 离线反汇编，最权威**
```sh
aarch64-linux-gnu-nm -n out.ko | grep do_get_info            # 定位符号地址
aarch64-linux-gnu-objdump -d --start-address=<do_get_info地址> --stop-address=<+~0x100> out.ko
```
在 `do_get_info` 开头找构造版本号的 `mov` 立即数：
- `mov w8, #0x7f0d` = **32525**（官方 3.2.5 的 ko）
- `mov w10, #0x7f8d` = **32653**（fork 的 ko）

**方法 C — 运行时**
```sh
su -c '/data/user_de/0/df.root/ksud debug version'          # Kernel Version: XXXXX
```
⚠️ 这读的是**当前已加载的内核模块**的版本，不是磁盘上 ksud 文件的版本（见 §5）。

---

## 3. ksud 哈希基准表（唯一权威）

| 名称 | 大小(B) | md5 | 上报版本 | 用途 |
| --- | --- | --- | --- | --- |
| 官方 KernelSU **v3.2.5** | 4879560 | `fa6378c07089ef6223da75cdba632896` | **32525** ✓ | 目标版本 |
| 官方 KernelSU 3.3.0 | 4892712 | `de059ed8ffd896129a0a4bae338c946a` | 3.3.0 系 | 备用 |
| fork 版（uapi:4） | 6028928 | `a53980a72d41e616f1026cea365124e1` | **32653** ✗ | 早期调试，曾误入 |

来源文件：官方 manager APK 的 `lib/arm64-v8a/libksud.so`。

---

## 4. 修正配方（改哪里 + 怎么打包）

1. **唯一替换点**：`assets/ksud` ← 覆盖为官方 3.2.5 的 `libksud.so`（见 §3 哈希）。
2. **libexp.so 参数中和（极易漏！）**：非原机 ksud 不接受 DFRoot 调用串里的
   `--ro-partitions` 与 `--soft-reboot`，需在 `lib/arm64-v8a/libexp.so` 内把这两串**等长替换为空格**
   （`--ro-partitions`、`--soft-reboot` 各由出现 7 次降为 6 次 = 已中和）。
   - 校验办法：在 so 二进制里计数字符串出现次数。
3. **重打包对齐**：
   - `lib/arm64-v8a/*.so` 用 **STORED（不压缩）+ 16384 页对齐**；
   - `resources.arsc`、`AndroidManifest.xml` 各 4 字节对齐；
   - 丢弃 `META-INF/*.RSA / *.SF / *.MF` 旧签名。
4. **重签**：用 `apksigner`（keystore 证书 `CN=dfroot`），v1/v2/v3 全开。
   - 证书必须与设备在装版本一致，才能 `pm install -r` 覆盖升级。
5. **成品基准**（本轮）：`conglai-final-32525-signed.apk`
   - 15982076 B，sha256 `edda60bd4bc1ca86a4f5cad71ea888526d99fc8f85064091aaa8c18074269dd2`
   - 914 项 / 17 图标；`libexp.so` md5 `55762fef27648da60a2fbbf1e52e0b41`（ro-part=6，已中和）；
     `ksud` md5 `fa6378c07089ef6223da75cdba632896`（**32525**）。

---

## 5. ★生效条件（最容易踩的坑）

`ksud` 的 `late_load.rs` 逻辑：
```
if has_kernelsu() { info!("KernelSU already loaded, skip loading ko"); }
```
即：**内核里已经加载了 KernelSU 时，再跑 late-load 会直接跳过，不换模块**。

因此：
- ❌ 热替换磁盘上的 `ksud` 文件**不会**改变驱动上报的版本。
- ✅ 只有**重新加载 ko** 才生效，两条路径：
  1. **重启手机** → `/dev/df`（tmpfs）消失 → `BootReceiver`（监听 `BOOT_COMPLETED`，
     且仅当 `/dev/df` 不存在时才跑）自动用新包重跑 exploit → 加载新 ko；
  2. 卸载 / 清除提权环境后重新提权。
- ❌ **不要用 `ksud unload`**：它会 `stop` / `start` 全部 Android 服务并杀掉所有 su 进程，极度 disrupt。

**验证**：
```sh
su -c '/data/user_de/0/df.root/ksud debug version'   # 期望：Kernel Version: 32525
```

---

## 6. 系统化排查入口（下次照做，不要扫盘）

遇到"版本不对"三步走：
1. 看 §3 基准表 —— 确认目标 ksud 是哪一个（哈希对得上就对）。
2. 看 §4 —— 确认 `assets/ksud` 已替换 + `libexp.so` 参数已中和。
3. 看 §5 —— 确认是**重启 / 重跑后**验证，而不是热替换后直接跑 `debug version`。

**总共只涉及三个文件**：`assets/ksud`、`lib/arm64-v8a/libexp.so`、以及它们的来源哈希。

---

## 7. 变更记录（仅结论）

- **2026-10-04**：本文件首版。确证版本号 = `30000 + 提交数 − 7`（源码钉死）；确证 `strings` 无效、
  必须读反汇编立即数；确证"已加载则跳过"导致必须重启 / 重跑才生效；产出 32525 修正包。

---

*本文档由 AI 辅助生成与维护。*
