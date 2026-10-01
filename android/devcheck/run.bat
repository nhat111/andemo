@echo off
REM Chay tu thu muc android\ :  devcheck\run.bat
REM Bien dich lop quy tac (Java thuan) + file check, roi chay main. Khong can Gradle / internet.
REM Neu bao khong tim thay javac: sua JAVA_HOME ben duoi theo cho cai Android Studio.
chcp 65001 >nul
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "SRC=app\src\main\java"
set "OUT=build\devcheck"
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
"%JAVA_HOME%\bin\javac" -encoding UTF-8 -d "%OUT%" "%SRC%\com\example\andemo\rules\DisposalRules.java" devcheck\DisposalRulesCheck.java
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\java" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "%OUT%" DisposalRulesCheck
exit /b %errorlevel%
