@echo off
setlocal
if "%RHAPSODY_HOME%"=="" set "RHAPSODY_HOME=C:\Program Files\IBM\Rhapsody\10.0.2"
set "RHAPSODY_JAR=%RHAPSODY_HOME%\Share\JavaAPI\Rhapsody.jar"
if not exist "%RHAPSODY_JAR%" (
  echo ERROR: Rhapsody.jar not found at "%RHAPSODY_JAR%"
  echo Set RHAPSODY_HOME to the IBM Rhapsody 10.0.2 installation directory.
  exit /b 1
)
set "ROOT=%~dp0.."
set "CLASSES=%ROOT%\target\classes"
if exist "%ROOT%\target" rmdir /s /q "%ROOT%\target"
mkdir "%CLASSES%"
dir /s /b "%ROOT%\src\main\java\*.java" > "%ROOT%\target\sources.txt"
javac --release 17 -encoding UTF-8 -cp "%RHAPSODY_JAR%" -d "%CLASSES%" @"%ROOT%\target\sources.txt"
if errorlevel 1 exit /b 1
jar --create --file "%ROOT%\target\SafranChecker.jar" -C "%CLASSES%" .
copy /y "%ROOT%\safran-checkers.xml" "%ROOT%\target\safran-checkers.xml" >nul
copy /y "%ROOT%\SafranChecker.hep" "%ROOT%\target\SafranChecker.hep" >nul
echo Build completed: %ROOT%\target\SafranChecker.jar
endlocal
