#!/bin/sh
cd "$(dirname "$0")"
[ -f target/c-parser.jar ] || mvn -q -DskipTests package
exec java -jar target/c-parser.jar "$@"
