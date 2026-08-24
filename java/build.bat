@echo off
chcp 65001 >nul
cd /d "%~dp0"
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_301
set CP=lib\pdfbox-2.0.27.jar;lib\fontbox-2.0.27.jar;lib\commons-logging-1.2.jar
echo === Compiling Java sources ===
"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 -cp "%CP%" -d target\classes src\main\java\com\hanzheng\HanzhengPdfTool.java src\main\java\com\hanzheng\WebServer.java src\main\java\com\hanzheng\core\CjkWrapper.java src\main\java\com\hanzheng\core\ConfigManager.java src\main\java\com\hanzheng\core\FormatExtractor.java src\main\java\com\hanzheng\core\PdfProcessor.java src\main\java\com\hanzheng\core\PdfPreviewUtil.java src\main\java\com\hanzheng\core\RecipientExtractor.java src\main\java\com\hanzheng\model\BatchJob.java src\main\java\com\hanzheng\model\HanzhengRequest.java src\main\java\com\hanzheng\model\PdfFormat.java
if errorlevel 1 (
  echo === COMPILE FAILED ===
  exit /b 1
)
echo === COMPILE OK ===
