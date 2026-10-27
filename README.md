# 时笺

**日有所记，心有所安。** 一个清爽、离线可用的 Android 日历记录应用。

[下载最新 APK](https://github.com/MrKuangker/shijian-calendar/releases/latest/download/shijian.apk) · [版本发布](https://github.com/MrKuangker/shijian-calendar/releases) · [构建状态](https://github.com/MrKuangker/shijian-calendar/actions)

## 使用

安装 `shijian.apk` 后，点击日历中的任意一天，再点「记一件事」。事件名和内容至少写一项，其余均可选：事项时间、截止时间（DDL）、提醒、分类、优先级。事项可以编辑、完成、删除并撤销删除。

「事项」中可搜索全部记录，并筛选待完成、已完成、已逾期。「我的」中可切换周起始日和明暗主题，以及导入、导出 JSON 备份。备份导入采用合并规则：按事项编号去重，同编号保留编辑时间较新的记录。

支持 Android 8.0 及以上。通知提醒需要通知权限，可能因省电、后台限制、强行停止应用而延后或不显示；重要 DDL 请同时使用系统闹钟。无需登录，不包含广告、统计、云端日程同步。网络仅用于用户主动检查、下载更新。

## 更新发布

**提交并推送到 `main` 后，GitHub Actions 会自动测试、构建、签名并发布新 APK。** 成功后会生成递增的版本号与 GitHub Release，无需手动上传 APK。失败构建不会发布。

手机里进入「我的 → 检查更新 → 下载更新 → 安装更新」。安装前会校验文件大小、SHA-256、应用包名、版本号与签名；安卓系统会要求用户确认安装。首次更新可能需要允许时笺安装应用。使用同一签名覆盖安装会保留日程，定期备份仍很重要。

版本名取 `version.properties` 的主版本和次版本，再附加 Actions 运行编号，例如 `1.0.1`；版本码为 `100 + github.run_number`。本地基础版本码为 100。请保留本仓库和工作流历史，以保持版本码递增。

Actions 需要以下仓库 Secrets：

- `ANDROID_KEYSTORE_BASE64`：发布签名 PKCS12 文件的 Base64。
- `ANDROID_STORE_PASSWORD`、`ANDROID_KEY_PASSWORD`：签名密码。
- `ANDROID_KEY_ALIAS`：签名别名。

**本机 `.private/` 中保存原始发布签名和配置，请安全备份整个目录。它已被 Git 忽略，不得提交。丢失签名后无法给已安装的应用发布覆盖更新。**

## 开发

界面在 `app/src/main/assets/`，原生 Android 功能在 `app/src/main/java/`。原生层提供原子文件保存、系统文件选择、通知、更新校验和安装，界面完全随 APK 打包，无远程网页依赖。原生桥仅向应用内嵌来源开放。

```sh
npm ci
npm test
npm run preview
```

预览地址为 `http://127.0.0.1:4173`。网页预览使用浏览器本地存储；通知和系统安装更新需要 Android 安装版。界面回归测试默认使用 Windows 上已安装的 Edge：`npm run test:ui`。

标准构建：JDK 17、Android SDK 35、Gradle 8.11.1，运行 `gradle assembleDebug`。发布构建需通过环境变量提供签名配置，可参考工作流。

Windows 已有 SDK 时，也可直接运行 `scripts/build-local.ps1 -SdkPath 'D:\Android\sdk'`。此脚本使用 SDK 34 平台与 35.0.0 构建工具，临时在英文路径中编译以兼容中文项目目录，输出到 `dist/`，并继续使用 `.private/` 中的发布签名。

## 数据说明

记录仅保存于应用私有空间。更新保留数据；卸载应用、清除应用数据、更换手机不会自动恢复。操作前请在「我的」导出备份，并把备份另存到可靠位置。仓库、GitHub Releases 和构建日志不包含个人日程。
