@echo off
title Smart Home Firewall - API Gateway
echo ====================================================
echo   SMART HOME FIREWALL - API GATEWAY
echo ====================================================
echo.
echo Activating python virtual environment...
call "%~dp0smart-firewall\venv\Scripts\activate.bat"
echo.
echo Starting FastAPI secure gateway over HTTPS on port 8000...
python "%~dp0smart-firewall\src\api.py"
echo.
echo Server terminated.
pause
