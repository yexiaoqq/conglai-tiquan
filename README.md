# 聪来提权（conglai-tiquan）· S23 适配版

> 📘 **构建 / 版本维护完整教程见 [`docs/BUILD_GUIDE.md`](docs/BUILD_GUIDE.md)**
>
> 🎯 **版本号（32525 / 32653…）的来源、修正配方与生效条件，只看 [`docs/KSU_VERSION_FIX.md`](docs/KSU_VERSION_FIX.md)**

> 一款基于 **DFRoot / DFReroot**（DirtyFrag 漏洞利用链）的 Android 触发式提权应用，
> 面向 **Samsung Galaxy S23 (SM-S9110)** 适配，通过 late-load 方式加载 **KernelSU v3.2.5 (32525)**。

---

## 一、软件来源

本应用**不是原创提权链**，而是对既有开源项目在特定机型 / 固件上的**适配与界面重制版**：

| 来源项目 | 地址 | 作用 |
| --- | --- | --- |
| **DFRoot** | https://github.com/diabl0w/DFRoot | 提权主流程（DirtyFrag 漏洞利用 + LKM + ksud late-load） |
| **DFReroot** | https://github.com/polygraphene/DFReroot | 重获 root 的参考实现（SELinux permissive 内核模块思路） |
| **Root My Galaxy** | https://github.com/BuSung-dev/Root-My-Galaxy | 三星机型 feed / payload 组织方式参考 |
| KernelSU | https://github.com/tiann/KernelSU | 提供内核级 root 框架（本应用内嵌 `ksud` 3.2.5） |

> 说明：本项目下达指令时给定的来源仓库名 `DFreroot/DFroot` 经 GitHub API 核查**不存在（404）**，
> 实际存在的上游为 `diabl0w/DFRoot` 与 `polygraphene/DFReroot`，故此处超链接指向后者。

## 二、用途

- 在**已解锁可加载未签名内核模块**环境下，向目标设备提供一次**触发式提权**入口；
- 通过界面按钮一键执行漏洞利用链，成功后完成 KernelSU 的 **late-load** 注入，使设备获得内核级 root；
- 主要用于**本人自用设备的系统维护 / 研究**场景。

## 三、适用机型与版本号

| 项目 | 值 |
| --- | --- |
| 机型 | Samsung Galaxy S23 **SM-S9110** |
| 内核版本 | `5.15.189-android13-8-3251900-abS9110ZCS8FZI1` |
| KMI | `android13-5.15` |
| 固件基线 | `S9110ZCS8FZI1` |
| KernelSU | **v3.2.5 (versionCode 32525)** |
| 应用版本 | `versionName 2.0` / `versionCode 2` |
| 包名 | `df.root` |
| minSdk / targetSdk | 32 / 36 |

## 四、操作原理（简述）

1. **漏洞触发**：内嵌 `libexp.so` 利用内核漏洞（DirtyFrag 链）完成物理读写原语（pipe physrw）；
2. **打补丁 / 覆盖**：patch 供应商库，绕过校验；
3. **加载 LKM**：加载 `dirtyfrag.ko`，将 SELinux 置为 **permissive**；
4. **拉起守护**：经 `call_usermodehelper` 调起内嵌 `ksud`；
5. **late-load**：`ksud` 完成 **KernelSU** 的 late-load 注入 → 设备获得内核级 root。

> 界面改造仅涉及 `res/`（布局 / 图标 / 颜色）与 `assets/`（`ksud`）、`lib/`（`libexp.so`），
> 未改动业务 `dex`，逻辑与原上游一致。

## 五、署名

- 创建者：**夜枭YeXiao / 我独何人 / 聪**
- 辅助：**DeepSeek-flash**
- 基于上游开源项目二次适配（见「软件来源」）

## 六、免责声明

本项目仅供**授权设备**上的安全研究与自用维护。使用者需自行承担一切风险；
因使用本工具造成的任何设备损坏、数据丢失或法律后果，作者不承担责任。
