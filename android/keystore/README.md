发布签名证书：所有版本用它签名，保证可以直接覆盖安装。

- qilian-tv.jks：证书文件
- keystore.properties：证书密码与别名（Gradle 自动读取）

本仓库是公开的，证书也随之公开，这是有意的取舍（个人使用的小应用，换取不需要额外配置）。
若要换用私密证书：在仓库 Secrets 配置 ANDROID_KEYSTORE_BASE64、ANDROID_KEYSTORE_PASSWORD、ANDROID_KEY_ALIAS、ANDROID_KEY_PASSWORD，工作流会优先使用（换证书后需卸载重装一次）。
