#!/bin/sh
# Chạy từ thư mục android/:  sh devcheck/run.sh
# Biên dịch lớp quy tắc (Java thuần) + file check, rồi chạy main. Không cần Gradle / internet.
set -e
SRC=app/src/main/java
OUT=build/devcheck
rm -rf "$OUT" && mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$SRC/com/example/andemo/rules/DisposalRules.java" \
  devcheck/DisposalRulesCheck.java
java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" DisposalRulesCheck
