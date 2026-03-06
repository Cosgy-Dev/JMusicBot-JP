pipeline {
  agent any
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Build and Test') {
      steps {
        sh 'mvn --version'
        sh 'mvn --batch-mode --update-snapshots clean verify'
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
  environment {
    MAVEN_OPTS = '-Xmx3200m'
  }
  post {
    always {
      junit(testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true)
      archiveArtifacts(artifacts: 'target/*.jar', fingerprint: true, onlyIfSuccessful: true)
    }

  }
  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '30'))
  }
}