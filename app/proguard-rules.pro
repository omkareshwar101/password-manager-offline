# OfflineVault keeps release minification disabled by default so the native
# SQLCipher bridge and Compose reflection-free code can be validated first.
# If you enable R8, keep the SQLCipher JNI bridge classes:
-keep class net.sqlcipher.** { *; }
-keep class net.zetetic.** { *; }
-dontwarn net.sqlcipher.**
