#!/usr/bin/env bash
# Installs the Open Integration Engine jars this plugin compiles against into the local Maven
# repository. They are not in Maven Central, so they are taken from the engine's own release on
# GitHub, checked against the SHA-256 the engine publishes (see download-oie.sh).
#
# Usage: install-oie-jars.sh <engine version, e.g. 4.6.0> [download folder]
# Environment: MVN_OPTS, extra options for mvn (for example -Dmaven.repo.local=...).
set -euo pipefail

version="${1:?engine version, e.g. 4.6.0}"
downloads="${2:-${HOME}/.cache/oie}"
tarball="$(bash "$(dirname "$0")/download-oie.sh" "${version}" "${downloads}")"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

tar -xzf "${tarball}" -C "${work}" \
  oie/server-lib/mirth-server.jar \
  oie/server-lib/donkey/donkey-server.jar \
  oie/server-lib/donkey/donkey-model.jar \
  oie/client-lib/mirth-client.jar \
  oie/client-lib/mirth-client-core.jar

install() {
  mvn -B -q ${MVN_OPTS:-} install:install-file -Dfile="${work}/oie/$1" -DgroupId=com.mirth.connect -DartifactId="$2" -Dversion="${version}" -Dpackaging=jar
  echo "Installed com.mirth.connect:$2:${version}"
}
install server-lib/mirth-server.jar         server-api
install server-lib/donkey/donkey-server.jar donkey-server
install server-lib/donkey/donkey-model.jar  donkey-model
install client-lib/mirth-client.jar         client
install client-lib/mirth-client-core.jar    client-core
