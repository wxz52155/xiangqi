发布签名证书（源码仓库为私有仓库，证书随仓库保存，保证每个版本签名一致、可以覆盖安装）。

- qilian-tv.jks：证书文件
- keystore.properties：证书密码与别名（Gradle 自动读取）

**不要把本仓库改为公开**，否则证书会泄露。若要换用 GitHub Secrets，配置 ANDROID_KEYSTORE_BASE64 等 4 项后会优先使用 Secrets。
