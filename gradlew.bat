@echo off
setlocal
set "APP_HOME=%~dp0"

rem Honor an explicitly configured JDK; otherwise use Android Studio's bundled runtime.
if defined JAVA_HOME goto configuredJava
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_EXE=%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe"
    goto execute
)
set "JAVA_EXE=java.exe"
goto execute

:configuredJava
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if exist "%JAVA_EXE%" goto execute
echo JAVA_HOME does not point to a valid JDK. Configure JDK 17 or newer.
exit /b 1

:execute
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
set "BUILD_RESULT=%ERRORLEVEL%"
endlocal & exit /b %BUILD_RESULT%
