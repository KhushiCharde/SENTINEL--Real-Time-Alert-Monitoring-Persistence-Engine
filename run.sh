#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -cp 'sentinel.jar:lib/*' sentinel.Main "$@"
