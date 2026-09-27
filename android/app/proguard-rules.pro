# ProGuard/R8 rules cho bản release.
# Hiện tại minifyEnabled = false nên file này chưa được dùng.
# Khi bật minify, cần giữ lại các model Gson (map JSON theo tên field):
# -keep class com.example.andemo.model.** { *; }
