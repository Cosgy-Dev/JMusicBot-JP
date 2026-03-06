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
                        curl -fsSL "$MAVEN_URL" -o "$MAVEN_BASE/$MAVEN_ARCHIVE"
                        tar -xzf "$MAVEN_BASE/$MAVEN_ARCHIVE" -C "$MAVEN_BASE"
                      fi
                      MVN_CMD="$MAVEN_DIR/bin/mvn"
                    fi

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
