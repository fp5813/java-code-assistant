@echo off
chcp 65001 >nul
title Java Code Assistant

echo.
echo [JCA] 启动 Java Code Assistant...
echo.

:: 检测 Java
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [错误] 未找到 Java，请确保 JDK 21+ 已安装并配置 PATH
    pause
    exit /b 1
)

:: 检测 JAR
if not exist "target\java-code-assistant.jar" (
    echo [错误] 未找到 target\java-code-assistant.jar
    echo        请先执行 mvn package -DskipTests
    pause
    exit /b 1
)

:: 运行
java -jar target\java-code-assistant.jar
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [错误] 程序异常退出，错误码: %ERRORLEVEL%
    pause
)
