#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
if ! command -v javac >/dev/null 2>&1; then
    echo 'Install JDK 17 or newer (java and javac are required).' >&2
    exit 1
fi
mkdir -p build
javac --release 17 -d build src/bank/*.java test/bank/*.java
case "${1:-run}" in
    run) shift "$(( $# > 0 ? 1 : 0 ))"; exec java -cp build bank.Main "$@" ;;
    simulate) exec java -cp build bank.Simulation ;;
    test) exec java -cp build bank.BankTest ;;
    *) echo 'Usage: ./bank.sh [run [config-file] | simulate | test]' >&2; exit 1 ;;
esac
