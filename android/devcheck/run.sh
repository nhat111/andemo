#!/bin/sh
# Chạy từ thư mục android/:  sh devcheck/run.sh
# 1) ProjectCheck: soát manifest / layout / id / adapter (đọc file text)
# 2) DisposalRulesCheck: test quy tắc Java thuần bằng main
# Không cần Gradle / internet.
set -e
J="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dstdout.encoding=UTF-8"
echo "== 1. Soát project =="
java $J devcheck/ProjectCheck.java
echo
echo "== 2. Test quy tắc =="
SRC=app/src/main/java
OUT=build/devcheck
rm -rf "$OUT" && mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$SRC/com/example/andemo/rules/DisposalRules.java" \
  devcheck/DisposalRulesCheck.java
java $J -cp "$OUT" DisposalRulesCheck
