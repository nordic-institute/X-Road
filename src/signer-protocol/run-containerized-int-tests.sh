#!/bin/bash

cwd="$(pwd)"

gradleModule="signer-protocol"
gradleArgs="intTest"

echo "Preparing container.."
docker build -t docker-compile "$XROAD_HOME/src/packages/docker-compile"  || errorExit "Error building image."


echo "Executing within container.."
OPTS=("--rm" "-v" "$XROAD_HOME/:/mnt" "-u" "$(id -u):$(id -g)" "-e" "HOME=/workspace/src/packages")


echo "Rebuilding signer locally.."
cd "$XROAD_HOME/src"
./gradlew assemble -p signer
./gradlew clean -p $gradleModule

echo "Running signer-protocol int tests.."
cd "$cwd" || exit
mkdir "build"
docker run "${OPTS[@]}" docker-compile sh -c "cd /mnt/src/ && ./gradlew $gradleArgs -p $gradleModule" > build/containerized-test-exec.log
