@echo off
chcp 65001 >nul
REM ============================================================
REM  函证 PDF 头部替换工具 - 启动 Java 版 Web 服务
REM  端口: 8889  |  访问: http://localhost:8889
REM ============================================================

REM 切换到脚本所在目录的 java 子目录（兼容双击与命令行运行）
set "ROOT=%~dp0"
cd /d "%ROOT%java"

set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_301"

REM 检查 Java 是否可用
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [错误] 未找到 JDK，请检查 JAVA_HOME: %JAVA_HOME%
    pause
    exit /b 1
)

echo ========================================
echo   Starting Java Web Server
echo   URL    : http://localhost:8889
echo   Memory : 1024MB max heap
echo ========================================
echo.

"%JAVA_HOME%\bin\java" -Xmx1024m -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "target\classes;lib\pdfbox-2.0.27.jar;lib\fontbox-2.0.27.jar;lib\commons-logging-1.2.jar" com.hanzheng.WebServer

echo.
echo 服务已停止。
pause
