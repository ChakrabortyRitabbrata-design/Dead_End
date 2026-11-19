@echo off
cd /d "%~dp0"
echo Compiling Dead End with MySQL JDBC...
javac -cp "lib/*" -d bin src\module-info.java src\animation\*.java src\main\*.java src\entities\*.java src\tile\*.java src\Object\*.java src\inventory\*.java src\db\*.java
if %ERRORLEVEL% neq 0 (
    echo.
    echo Compilation FAILED. See errors above.
    pause
    exit /b 1
)
echo Compilation OK. Launching game...
java -cp "bin;lib/*" main.Window
