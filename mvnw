#!/bin/sh
# Maven Wrapper Script
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.

set -e

MAVEN_WRAPPER_JAR=".mvn/wrapper/maven-wrapper.jar"
MAVEN_WRAPPER_PROPERTIES=".mvn/wrapper/maven-wrapper.properties"

# Detecta o diretório do script
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Lê a URL de distribuição do wrapper
DISTRIBUTION_URL=$(grep "distributionUrl" "$MAVEN_WRAPPER_PROPERTIES" | cut -d'=' -f2-)

# Verifica se o Maven já foi baixado
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-3.9.9"
MAVEN_BIN="$MAVEN_HOME/apache-maven-3.9.9/bin/mvn"

if [ ! -f "$MAVEN_BIN" ]; then
    echo "Baixando Apache Maven 3.9.9..."
    mkdir -p "$MAVEN_HOME"
    curl -s -L "$DISTRIBUTION_URL" -o "$MAVEN_HOME/maven.zip" && \
    unzip -q "$MAVEN_HOME/maven.zip" -d "$MAVEN_HOME" || {
        echo "Erro ao baixar Maven. Instale manualmente: https://maven.apache.org/download.cgi"
        exit 1
    }
fi

exec "$MAVEN_BIN" "$@"
