#　JMusicBot JP Docker container configuration file
#  Maintained by CyberRex (CyberRex0)

FROM eclipse-temurin:25-jdk

# DO NOT EDIT UNDER THIS LINE
RUN mkdir -p /opt/jmusicbot

WORKDIR /opt/jmusicbot

RUN \
    echo "JMusicBot-JP Docker Container Builder v1.1\nMaintained by CyberRex (CyberRex0)"; \
    echo "Preconfiguring apt..." & apt-get update > /dev/null; \
    echo "Installing packages..." & apt-get install -y ffmpeg wget curl jq > /dev/null; \
    echo "Downloading latest version of JMusicBot-JP..."; \
    wget $(curl https://api.github.com/repos/Cosgy-Dev/JMusicBot-JP/releases/latest | jq -r '.assets[] | select(.browser_download_url | contains(".jar")) | .browser_download_url') -O /opt/jmusicbot/jmusicbot.jar; \
    echo "cd /opt/jmusicbot && java --enable-native-access=ALL-UNNAMED -Dnogui=true -jar jmusicbot.jar" > /opt/jmusicbot/execute.bash; \
    echo "Build Completed."

CMD ["bash", "/opt/jmusicbot/execute.bash"]
