@echo off
cd /d %~dp0
if not exist target\classes call build.bat
java -cp target\classes ez.factory.Main %*
