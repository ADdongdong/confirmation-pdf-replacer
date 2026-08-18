@echo off
chcp 65001 >nul
cd /d "e:\13_dingdian\z999_归档的文件\06_workbuddy_dingdian\投行产品设计师\hanzheng_pdf_tool_java"
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_301
set CP=target\classes;lib\pdfbox-2.0.27.jar;lib\fontbox-2.0.27.jar;lib\commons-logging-1.2.jar
"%JAVA_HOME%\bin\java" -Xmx1024m -Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -cp "%CP%" com.hanzheng.WebServer
pause
