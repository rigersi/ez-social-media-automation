@echo off
cd /d %~dp0
if not exist target\classes mkdir target\classes
dir /s /b src\main\java\*.java > target\sources.txt
javac -encoding UTF-8 -nowarn -d target\classes @target\sources.txt
if errorlevel 1 (echo BUILD FAILED & exit /b 1)
echo BUILD OK -^> target\classes
