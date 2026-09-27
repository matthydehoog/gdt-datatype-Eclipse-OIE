#!/usr/bin/env bash
# Installs the Open Integration Engine jars this plugin compiles against into the local Maven
# repository. They are not in Maven Central, so they are taken from the engine's own release on
# GitHub, checked against the SHA-256 the engine publishes.
#
# Usage: install-oie-jars.sh <engine version, e.g. 4.6.0>
set -euo pipefail

version="${1:?engine version, e.g. 4.6.0}"
file="oie_unix_${version//./_}.tar.gz"
base="https://github.com/OpenIntegrationEngine/engine/releases/download/v${version}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

echo "Downloading ${file} (engine ${version})"
curl -fsSL -o "${work}/${file}" "${base}/${file}"
curl -fsSL -o "${work}/sha256sums" "${base}/sha256sums"

expected="$(grep "${file}" "${work}/sha256sums" | awk '{print $1}')"
actual="$(sha256sum "${work}/${file}" | awk '{print $1}')"
if [ -z "${expected}" ] || [ "${expected}" != "${actual}" ]; then
  echo "Checksum mismatch for ${file}: expected '${expected}', got '${actual}'" >&2
  exit 1
fi

tar -xzf "${work}/${file}" -C "${work}" \
  oie/server-lib/mirth-server.jar \
  oie/server-lib/donkey/donkey-server.jar \
  oie/server-lib/donkey/donkey-model.jar \
  oie/client-lib/mirth-client.jar \
  oie/client-lib/mirth-client-core.jar

install() {
  mvn -B -q install:install-file -Dfile="${work}/oie/$1" -DgroupId=com.mirth.connect -DartifactId="$2" -Dversion="${version}" -Dpackaging=jar
  echo "Installed com.mirth.connect:$2:${version}"
}
install server-lib/mirth-server.jar         server-api
install server-lib/donkey/donkey-server.jar donkey-server
install server-lib/donkey/donkey-model.jar  donkey-model
install client-lib/mirth-client.jar         client
install client-lib/mirth-client-core.jar    client-core
