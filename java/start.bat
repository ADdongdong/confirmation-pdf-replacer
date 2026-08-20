@echo off
chcp 65001 >nul
cd /d "%~dp0"
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_301
set CP=target\classes;lib\pdfbox-2.0.27.jar;lib\fontbox-2.0.27.jar;lib\commons-logging-1.2.jar

echo ========================================
echo   停止旧的 Java Web 服务（端口 8889）
echo ========================================
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8889 ^| findstr LISTENING') do (
    echo   杀掉占用 8889 端口的进程 PID: %%a
    taskkill /F /PID %%a >nul 2>&1
)
timeout /t 1 /nobreak >nul

echo ========================================
echo   Starting Java Web Server (port 8889)
echo   URL: http://localhost:8889
echo ========================================
"%JAVA_HOME%\bin\java" -Xmx1024m -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "%CP%" com.hanzheng.WebServer
pause
