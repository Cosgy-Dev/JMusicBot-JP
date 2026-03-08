pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    environment {
        MAVEN_OPTS = '-Xmx3200m'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build and Test') {
            steps {
                sh '''
                    set -eu

                    download_file() {
                      URL="$1"
                      OUT="$2"
                      if command -v curl >/dev/null 2>&1; then
                        curl -fsSL "$URL" -o "$OUT"
                      elif command -v wget >/dev/null 2>&1; then
                        wget -q "$URL" -O "$OUT"
                      else
                        echo "Neither curl nor wget is available to download: $URL" >&2
                        exit 1
                      fi
                    }

                    JAVA_MAJOR="$(java -version 2>&1 | awk -F[\\".] '/version/ {print $2; exit}' || true)"
                    if [ "${JAVA_MAJOR:-0}" -lt 25 ]; then
                      JDK_BASE="$WORKSPACE/.jdk"
                      JDK_DIR="$JDK_BASE/temurin-25"
                      JDK_ARCHIVE="OpenJDK25U-jdk_x64_linux_hotspot_25.0.2_10.tar.gz"
                      JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/$JDK_ARCHIVE"

                      mkdir -p "$JDK_BASE"
                      if [ ! -x "$JDK_DIR/bin/java" ]; then
                        download_file "$JDK_URL" "$JDK_BASE/$JDK_ARCHIVE"
                        mkdir -p "$JDK_DIR"
                        tar -xzf "$JDK_BASE/$JDK_ARCHIVE" -C "$JDK_DIR" --strip-components=1
                      fi

                      export JAVA_HOME="$JDK_DIR"
                      export PATH="$JAVA_HOME/bin:$PATH"
                    fi

                    if command -v mvn >/dev/null 2>&1; then
                      MVN_CMD="mvn"
                    else
                      MAVEN_VERSION="3.9.12"
                      MAVEN_BASE="$WORKSPACE/.maven"
                      MAVEN_DIR="$MAVEN_BASE/apache-maven-$MAVEN_VERSION"
                      MAVEN_ARCHIVE="apache-maven-$MAVEN_VERSION-bin.tar.gz"
                      MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/$MAVEN_ARCHIVE"

                      mkdir -p "$MAVEN_BASE"
                      if [ ! -x "$MAVEN_DIR/bin/mvn" ]; then
                        download_file "$MAVEN_URL" "$MAVEN_BASE/$MAVEN_ARCHIVE"
                        tar -xzf "$MAVEN_BASE/$MAVEN_ARCHIVE" -C "$MAVEN_BASE"
                      fi
                      MVN_CMD="$MAVEN_DIR/bin/mvn"
                    fi

                    java -version
                    "$MVN_CMD" --version
                    "$MVN_CMD" --batch-mode --update-snapshots clean verify
                '''
            }
        }

        stage('Publish Artifacts') {
            steps {
                sh '''
                    set -eu
                    ARTIFACTS="$(ls -1 target/*.jar 2>/dev/null || true)"
                    if [ -z "$ARTIFACTS" ]; then
                      echo "No artifact found in target/*.jar"
                      exit 1
                    fi

                    if [ -n "${PUBLISH_DIR:-}" ]; then
                      mkdir -p "$PUBLISH_DIR"
                      cp -f target/*.jar "$PUBLISH_DIR"/
                      echo "Published to $PUBLISH_DIR"
                    else
                      echo "PUBLISH_DIR is not set. Skipping filesystem publish."
                      echo "Use Jenkins archived artifacts URL for download."
                    fi
                '''
            }
        }

    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, onlyIfSuccessful: true
        }
    }
}
