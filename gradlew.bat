@ECHO OFF
SETLOCAL
SET APP_HOME=%~dp0
SET APP_BASE_NAME=%~n0
SET DIRNAME=%~dp0
IF "%DIRNAME%"=="" SET DIRNAME=.
SET DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
SET CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper-shared.jar;%APP_HOME%gradle\wrapper\gradle-cli.jar;%APP_HOME%gradle\wrapper\gradle-wrapper.jar
IF DEFINED JAVA_HOME (
	SET JAVA_EXE=%JAVA_HOME%\bin\java.exe
) ELSE (
	SET JAVA_EXE=java.exe
)
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=%APP_BASE_NAME% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
