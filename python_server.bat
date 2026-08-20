@echo off
chcp 65001 >nul
REM ============================================================
REM  函证 PDF 头部替换工具 - 启动 Python 版 Web 服务
REM  端口: 8888  |  访问: http://localhost:8888
REM ============================================================

REM 切换到脚本所在目录的 python 子目录
set "ROOT=%~dp0"
cd /d "%ROOT%python"

REM 优先使用 conda pytorch 环境的 Python
set "PY_EXE=E:\08_Anaconda3\Anaconda3\envs\pytorch\python.exe"

if not exist "%PY_EXE%" (
    echo [提示] 未找到 conda 环境 python，尝试使用系统 python...
    where python >nul 2>nul
    if errorlevel 1 (
        echo [错误] 未找到 python，请先安装并配置 conda 环境 pytorch
        pause
        exit /b 1
    )
    set "PY_EXE=python"
)

echo ========================================
echo   Starting Python Web Server
echo   URL    : http://localhost:8888
echo   Python : %PY_EXE%
echo ========================================
echo.

"%PY_EXE%" web_form_server.py

echo.
echo 服务已停止。
pause
