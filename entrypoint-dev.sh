#!/bin/bash
set -e

echo "Starting kleiner-driver"

# NOTE Current version of env-vars has bug where property values with space will generate bad
# property file; avoid spaces for now

lein var-file
ENV_VARS=$(cat .env-vars | sed 's/^/-D/')

exec java \
  ${ENV_VARS} \
  -XX:-OmitStackTraceInFastThrow \
  -jar target/kleiner-driver-0.1.0-standalone.jar \
  "$@"
