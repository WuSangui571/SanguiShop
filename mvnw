#!/bin/sh
set -eu

MAVEN_PROJECTBASEDIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
WRAPPER_DIR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper"
WRAPPER_PROPS="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$WRAPPER_PROPS" ]; then
  echo "Missing Maven Wrapper properties: $WRAPPER_PROPS" >&2
  exit 1
fi

distribution_url=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS")
distribution_sha512=$(sed -n 's/^distributionSha512Sum=//p' "$WRAPPER_PROPS")
maven_version=$(sed -n 's/^mavenVersion=//p' "$WRAPPER_PROPS")
maven_version=${maven_version:-3.9.9}

if [ -n "${MAVEN_USER_HOME:-}" ]; then
  wrapper_cache="$MAVEN_USER_HOME/wrapper/dists/apache-maven-$maven_version"
else
  wrapper_cache="$HOME/.m2/wrapper/dists/apache-maven-$maven_version"
fi

maven_home="$wrapper_cache/apache-maven-$maven_version"
if [ ! -x "$maven_home/bin/mvn" ]; then
  if command -v mvn >/dev/null 2>&1; then
    installed_version=$(mvn -v 2>/dev/null | sed -n 's/^Apache Maven \([^ ]*\).*/\1/p' | head -n 1)
    if [ "$installed_version" = "$maven_version" ]; then
      echo "Using globally installed Apache Maven $maven_version because the wrapper distribution is not cached." >&2
      exec mvn "$@"
    fi
  fi

  if [ -z "$distribution_url" ]; then
    echo "distributionUrl is missing in $WRAPPER_PROPS" >&2
    exit 1
  fi

  mkdir -p "$wrapper_cache"
  archive="$wrapper_cache/apache-maven-$maven_version-bin.zip"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$distribution_url" -o "$archive"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$distribution_url" -O "$archive"
  else
    echo "Neither curl nor wget is available to download Apache Maven $maven_version." >&2
    exit 1
  fi

  if [ -n "$distribution_sha512" ]; then
    if command -v sha512sum >/dev/null 2>&1; then
      actual_sha512=$(sha512sum "$archive" | awk '{print $1}')
    elif command -v shasum >/dev/null 2>&1; then
      actual_sha512=$(shasum -a 512 "$archive" | awk '{print $1}')
    else
      echo "No SHA-512 tool is available to verify the Maven distribution." >&2
      exit 1
    fi
    if [ "$actual_sha512" != "$distribution_sha512" ]; then
      echo "Maven distribution checksum mismatch." >&2
      exit 1
    fi
  fi

  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$archive" -d "$wrapper_cache"
  else
    echo "unzip is required to extract Apache Maven $maven_version." >&2
    exit 1
  fi
fi

exec "$maven_home/bin/mvn" "$@"
