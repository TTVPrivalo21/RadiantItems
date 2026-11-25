#!/usr/bin/env bash
# Validación estricta fuera del servidor: YAML, referencias, round-trip del editor y pack determinista.
# Requiere haber compilado antes con ./build-local.sh (usa target/local-build/classes).
set -euo pipefail
PROJ="$(cd "$(dirname "$0")" && pwd)"; SRC="$PROJ/tools/validator"; S="$PROJ/target/validator"
M2="${HOME}/.m2/repository"
latest() { ls -d "$M2/$1"/*/ 2>/dev/null | sort -V | tail -1; }
pick() { local dir; dir="$(latest "$1")"; [ -n "$dir" ] && ls "$dir"*.jar 2>/dev/null | grep -v -E '(sources|javadoc)\.jar$' | head -1; }
DEPS=(io/papermc/paper/paper-api net/kyori/adventure-api net/kyori/adventure-key net/kyori/adventure-text-minimessage
  net/kyori/adventure-text-serializer-gson net/kyori/adventure-text-serializer-json net/kyori/adventure-text-serializer-legacy
  net/kyori/adventure-text-serializer-plain net/kyori/adventure-text-serializer-commons net/kyori/examination-api
  net/kyori/examination-string net/kyori/option com/google/code/gson/gson com/google/guava/guava org/jetbrains/annotations
  org/jspecify/jspecify net/md-5/bungeecord-chat org/joml/joml it/unimi/dsi/fastutil com/mojang/brigadier org/yaml/snakeyaml
  org/slf4j/slf4j-api org/apache/logging/log4j/log4j-api)
CP="$(cygpath -w "$PROJ/target/local-build/classes")"
for d in "${DEPS[@]}"; do j="$(pick "$d" || true)"; [ -n "${j:-}" ] && CP+=";$(cygpath -w "$j")"; done
mkdir -p "$S/out"
javac --release 21 -proc:none -encoding UTF-8 -cp "$CP" -d "$(cygpath -w "$S/out")" "$(cygpath -w "$SRC/ValidateYaml.java")"
# message keys referenced from the code
KEYS="$(grep -rhoE 'messages\(\)\.send\([^,]+, "[a-z-]+"' "$PROJ/src/main/java" | sed -E 's/.*"([a-z-]+)"/\1/' ; \
        grep -rhoE 'message\(ctx, "[a-z-]+"' "$PROJ/src/main/java" | sed -E 's/.*"([a-z-]+)"/activator.\1/')"
KEYS="$(echo "$KEYS" | sort -u | tr '\n' ',' | sed 's/,$//')"
java -cp "$(cygpath -w "$S/out");$CP" ValidateYaml "$(cygpath -w "$PROJ/src/main/resources")" "$KEYS"
javac --release 21 -proc:none -encoding UTF-8 -cp "$CP" -d "$(cygpath -w "$S/out")" "$(cygpath -w "$SRC/ExtraChecks.java")"
rm -rf "$S/data"; mkdir -p "$S/data"
java -cp "$(cygpath -w "$S/out");$CP" ExtraChecks "$(cygpath -w "$PROJ/src/main/resources")" "$(cygpath -w "$S/data")"
