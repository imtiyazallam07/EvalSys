@echo off
setlocal enabledelayedexpansion

:: Get the directory of this batch file
set "BASE_DIR=%~dp0"
cd /d "%BASE_DIR%"

:: ── Java check ───────────────────────────────────────────────
where java >nul 2>nul
if !errorlevel! neq 0 (
    echo.
    echo [ERROR] Java is not installed or not found in PATH!
    echo.
    echo Please install Java 21 JDK from:
    echo   https://www.oracle.com/in/java/technologies/downloads/#jdk21-windows
    echo.
    echo After installing, restart this script.
    echo.
    pause
    exit /b 1
)

:: ── Python resolution ────────────────────────────────────────
:: 1. Portable interpreter in the project's Python\ folder
if exist "%BASE_DIR%Python\python.exe" (
    set "PYTHON_EXE=%BASE_DIR%Python\python.exe"
    echo Using portable Python: !PYTHON_EXE!
) else (
    :: 2. Fall back to system-wide Python
    where python >nul 2>nul
    if !errorlevel! equ 0 (
        set "PYTHON_EXE=python"
        echo Using system Python.
    ) else (
        echo.
        echo [ERROR] Python interpreter not found!
        echo.
        echo Please do one of the following:
        echo   A) Place a portable Python interpreter under the "Python" folder
        echo      Download: https://sourceforge.net/projects/portable-python/
        echo   B) Install Python 3.x globally: https://www.python.org/downloads/
        echo.
        pause
        exit /b 1
    )
)

:: ── Run the loader ───────────────────────────────────────────
"%PYTHON_EXE%" load_data.py %*

endlocal
