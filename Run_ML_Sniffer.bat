@echo off
title Smart Home Firewall - Packet Sniffer
echo ====================================================
echo   SMART HOME FIREWALL - ML PACKET SNIFFER
echo ====================================================
echo.
echo Activating python virtual environment...
call "%~dp0smart-firewall\venv\Scripts\activate.bat"
echo.
echo Starting 3-Layer Machine Learning packet sniffer...
python "%~dp0smart-firewall\src\firewall.py"
echo.
echo Sniffer terminated.
pause
