@echo off
chcp 65001 > nul
cd /d "%~dp0"
if not exist target\c-parser.jar call mvn -q -DskipTests package
java -jar target\c-parser.jar %*
