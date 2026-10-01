@echo off
REM Chay tu thu muc android\ :  devcheck\run.bat
REM 1) ProjectCheck: soat manifest / layout / id / adapter (doc file text)
REM 2) DisposalRulesCheck: test quy tac Java thuan bang main
REM Khong can Gradle / internet. Neu bao khong tim thay javac: sua JAVA_HOME ben duoi.
chcp 65001 >nul
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "J=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dstdout.encoding=UTF-8"
echo == 1. Soat project ==
"%JAVA_HOME%\bin\java" %J% devcheck\ProjectCheck.java
if errorlevel 1 exit /b 1
echo.
echo == 2. Test quy tac ==
set "SRC=app\src\main\java"
set "OUT=build\devcheck"
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
"%JAVA_HOME%\bin\javac" -encoding UTF-8 -d "%OUT%" "%SRC%\com\example\andemo\rules\DisposalRules.java" devcheck\DisposalRulesCheck.java
if errorlevel 1 exit /b 1
"%JAVA_HOME%\bin\java" %J% -cp "%OUT%" DisposalRulesCheck
exit /b %errorlevel%
