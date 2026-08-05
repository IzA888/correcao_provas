@echo off
setlocal
cd /d %~dp0
if exist target\correcao_provas-1.0.0-jar-with-dependencies.jar (
  echo Arquivo JAR com dependencias encontrado. Executando JAR...
  java -jar target\correcao_provas-1.0.0-jar-with-dependencies.jar
  set EXIT_CODE=%ERRORLEVEL%
  pause
  exit /b %EXIT_CODE%
) else if exist target\correcao_provas\correcao_provas.exe (
  echo JAR nao encontrado. Executando EXE...
  target\correcao_provas\correcao_provas.exe
  set EXIT_CODE=%ERRORLEVEL%
  pause
  exit /b %EXIT_CODE%
) else (
  echo Arquivo JAR e EXE nao encontrados. Execute mvn clean package primeiro para gerar o artefato.
  pause
  exit /b 1
)
