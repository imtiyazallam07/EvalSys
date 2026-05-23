@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: run_evaluator.bat
:: Called automatically by Main.java after the test ends.
:: Main.java passes the working directory as %1 (the CBT path).
::
:: Can also be run manually:
::   run_evaluator.bat
::   run_evaluator.bat "C:\some\other\path"
:: ============================================================

:: The folder containing this .bat file is always the evaluator dir
set EVAL_DIR=%~dp0

:: CBT folder = same directory (single working directory layout)
:: If Main.java (or the user) passes a path as %1, use that instead
set CBT_DIR=%EVAL_DIR%
if not "%~1"=="" set CBT_DIR=%~1

:: Strip any trailing backslash so paths stay clean
if "%CBT_DIR:~-1%"=="\" set CBT_DIR=%CBT_DIR:~0,-1%

echo.
echo === CBT Evaluator ===
echo CBT folder   : %CBT_DIR%
echo Evaluator dir: %EVAL_DIR%
echo.

:: ── Python resolution ────────────────────────────────────────
:: 1. Portable interpreter next to this bat
set PYTHON_EXE=%EVAL_DIR%Python\python.exe

if not exist "%PYTHON_EXE%" (
    echo Portable Python not found at %PYTHON_EXE%
    echo Trying system Python...
    where python >nul 2>nul
    if !errorlevel! equ 0 (
        set PYTHON_EXE=python
        echo Using system Python.
    ) else (
        echo.
        echo [ERROR] No Python interpreter found!
        echo Please install Python 3.x or place a portable Python interpreter
        echo in the "Python" folder next to this script.
        echo.
        pause
        exit /b 1
    )
)

:: ── Check evaluator.py exists ────────────────────────────────
if not exist "%EVAL_DIR%evaluator.py" (
    echo ERROR: evaluator.py not found in %EVAL_DIR%
    pause
    exit /b 1
)

:: ── Run evaluator ────────────────────────────────────────────
"%PYTHON_EXE%" "%EVAL_DIR%evaluator.py" "%CBT_DIR%"

echo.
pause