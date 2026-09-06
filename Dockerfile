#　JMusicBot JP Docker container configuration file
#  Maintained by Kosugi_kun (kosugikun)
#  The original version of this file was contributed by CyberRex (CyberRex0)

FROM eclipse-temurin:25-jdk

# 取得するビルドの種類
#   release … GitHub のリリース（既定）
#   beta    … Jenkins (ci.cosgy.dev) の最新ビルド
ARG BUILD_TYPE=release

# BUILD_TYPE=release のときに取得するタグ。latest で最新リリース
ARG RELEASE_TAG=latest
# BUILD_TYPE=beta のときに取得するブランチ
# スラッシュを含むブランチもそのまま指定してよい (例: hotfix/0.11.2)
# マルチブランチジョブの URL ではブランチ名が二重に URL エンコードされるため、内部で変換する
ARG BETA_BRANCH=develop

# 取得元。フォークや自前の Jenkins を使う場合に変更する
ARG GITHUB_REPO=Cosgy-Dev/JMusicBot-JP
ARG JENKINS_URL=https://ci.cosgy.dev
ARG JENKINS_JOB=JMusicBot-JP

LABEL dev.cosgy.jmusicbot.build-type="${BUILD_TYPE}"

# DO NOT EDIT UNDER THIS LINE
RUN mkdir -p /opt/jmusicbot

WORKDIR /opt/jmusicbot

RUN set -eu; \
    echo "JMusicBot-JP Docker Container Builder v1.2"; \
    echo "Maintained by Kosugi_kun (kosugikun)"; \
    echo "Original version contributed by CyberRex (CyberRex0)"; \
    echo "Preconfiguring apt..."; \
    apt-get update > /dev/null; \
    echo "Installing packages..."; \
    apt-get install -y --no-install-recommends ca-certificates ffmpeg curl jq > /dev/null; \
    rm -rf /var/lib/apt/lists/*; \
    \
    if [ "$BUILD_TYPE" = "beta" ]; then \
        echo "Resolving the latest beta build of JMusicBot-JP ($JENKINS_JOB / $BETA_BRANCH)..."; \
        BRANCH_PATH="$(printf '%s' "$BETA_BRANCH" | jq -sRr '@uri | @uri')"; \
        BUILD_URL="$JENKINS_URL/job/$JENKINS_JOB/job/$BRANCH_PATH/lastSuccessfulBuild"; \
        ARTIFACT="$(curl -fsSL "$BUILD_URL/api/json?tree=artifacts%5BrelativePath%5D" \
            | jq -r '[.artifacts[].relativePath | select(endswith(".jar"))] as $all \
                     | ($all | map(select(endswith("-All.jar"))) | first) // ($all | first) // empty')"; \
        if [ -z "$ARTIFACT" ]; then \
            echo "No jar artifact was found in $BUILD_URL" >&2; \
            exit 1; \
        fi; \
        DOWNLOAD_URL="$BUILD_URL/artifact/$ARTIFACT"; \
    elif [ "$BUILD_TYPE" = "release" ]; then \
        echo "Resolving the $RELEASE_TAG release of JMusicBot-JP ($GITHUB_REPO)..."; \
        if [ "$RELEASE_TAG" = "latest" ]; then \
            REDIRECT="$(curl -fsS -o /dev/null -w '%{redirect_url}' "https://github.com/$GITHUB_REPO/releases/latest")"; \
            TAG="$(printf '%s' "$REDIRECT" | sed -n 's|.*/releases/tag/||p')"; \
        else \
            TAG="$RELEASE_TAG"; \
        fi; \
        if [ -z "$TAG" ]; then \
            echo "No release was found in https://github.com/$GITHUB_REPO/releases" >&2; \
            exit 1; \
        fi; \
        ASSETS="$(curl -fsSL "https://github.com/$GITHUB_REPO/releases/expanded_assets/$TAG" \
            | grep -oE "/$GITHUB_REPO/releases/download/[^\"]+\.jar" | sort -u)"; \
        ASSET="$(printf '%s\n' "$ASSETS" | grep -e '-All\.jar$' | head -n 1)"; \
        if [ -z "$ASSET" ]; then \
            ASSET="$(printf '%s\n' "$ASSETS" | head -n 1)"; \
        fi; \
        if [ -z "$ASSET" ]; then \
            echo "No jar asset was found in the $TAG release of $GITHUB_REPO" >&2; \
            exit 1; \
        fi; \
        DOWNLOAD_URL="https://github.com$ASSET"; \
    else \
        echo "Unknown BUILD_TYPE: $BUILD_TYPE (expected 'release' or 'beta')" >&2; \
        exit 1; \
    fi; \
    \
    echo "Downloading $DOWNLOAD_URL ..."; \
    curl -fL --retry 3 --retry-delay 5 -o /opt/jmusicbot/jmusicbot.jar "$DOWNLOAD_URL"; \
    test -s /opt/jmusicbot/jmusicbot.jar; \
    \
    echo "cd /opt/jmusicbot && java --enable-native-access=ALL-UNNAMED -Dnogui=true -jar jmusicbot.jar" > /opt/jmusicbot/execute.bash; \
    echo "Build Completed."

CMD ["bash", "/opt/jmusicbot/execute.bash"]
