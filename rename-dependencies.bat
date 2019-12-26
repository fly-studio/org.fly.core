@echo off
cd /D %~dp0
java -jar "jarjar-1.4.jar" process rename-dependencies.txt "build\libs\core-1.0.0.jar" "build\libs\core-renamed-1.0.0.jar"