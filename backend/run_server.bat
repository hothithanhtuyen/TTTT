@echo off
echo ========================================================
echo   Starting Cinema Backend & VNPay Gateway Server...
echo ========================================================
cd /d "%~dp0"
python -m uvicorn server:app --host 0.0.0.0 --port 8000 --reload
pause
