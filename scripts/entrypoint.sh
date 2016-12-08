#!/bin/bash
set -e

echo "Starting kleiner-driver JVM"

exec java \
  -XX:-OmitStackTraceInFastThrow \
  -XX:+UseG1GC \
  -Xmx1g \
  -server \
  -jar /app/target/kleiner-driver-0.1.0-standalone.jar \
  "$@"
