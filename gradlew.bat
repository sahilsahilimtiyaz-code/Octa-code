@rem Minimal Gradle wrapper launcher for Octa Code (M0).
@echo off
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%CLASSPATH%" (
  echo gradle-wrapper.jar not found. Open the project once in Android Studio/AndroidIDE
  echo or run 'gradle wrapper --gradle-version 8.7' to generate it.
  exit /b 1
)
java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
