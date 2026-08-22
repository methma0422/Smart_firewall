@echo off
title Smart Home Firewall - Setup Environment
echo ====================================================
echo   SMART HOME FIREWALL - ENVIRONMENT SETUP
echo ====================================================
echo.

set "VENV_DIR=%~dp0smart-firewall\venv"

if exist "%VENV_DIR%" (
    echo Deleting existing, invalid virtual environment...
    rmdir /s /q "%VENV_DIR%"
)

echo Creating a new python virtual environment...
python -m venv "%VENV_DIR%"
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Failed to create virtual environment. Make sure Python is installed and added to your PATH.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Activating new virtual environment and installing dependencies...
call "%VENV_DIR%\Scripts\activate.bat"
python -m pip install --upgrade pip
pip install -r "%~dp0smart-firewall\requirements.txt"
if %ERRORLEVEL% neq 0 (
    echo.
    echo ERROR: Failed to install requirements.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ====================================================
echo   SETUP COMPLETED SUCCESSFULLY!
echo   You can now run Run_API_Gateway.bat and Run_ML_Sniffer.bat
echo ====================================================
echo.
pause
