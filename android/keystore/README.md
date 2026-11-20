本目录用于本地构建时放签名证书（qilian-tv.jks + keystore.properties），这两个文件已被 .gitignore 排除，不会提交。
CI 使用仓库 Secrets：ANDROID_KEYSTORE_BASE64 / ANDROID_KEYSTORE_PASSWORD / ANDROID_KEY_ALIAS / ANDROID_KEY_PASSWORD。
