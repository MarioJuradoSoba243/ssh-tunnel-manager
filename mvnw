#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
properties="$script_dir/.mvn/wrapper/maven-wrapper.properties"
distribution_url=$(sed -n 's/^distributionUrl=//p' "$properties")
distribution_name=${distribution_url##*/}
distribution_base_name=${distribution_name%.zip}
distribution_base_name=${distribution_base_name%-bin}
maven_user_home=${MAVEN_USER_HOME:-${HOME}/.m2}
distribution_hash=$(printf '%s' "$distribution_url" | sha256sum | cut -d ' ' -f 1)
maven_home="$maven_user_home/wrapper/dists/$distribution_base_name/$distribution_hash"

if [ ! -x "$maven_home/bin/mvn" ]; then
  temp_dir=$(mktemp -d)
  trap 'rm -rf "$temp_dir"' EXIT HUP INT TERM
  archive="$temp_dir/$distribution_name"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$distribution_url" -o "$archive"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$distribution_url" -O "$archive"
  else
    echo "Se necesita curl o wget para descargar Maven." >&2
    exit 1
  fi
  unzip -q "$archive" -d "$temp_dir"
  extracted=$(find "$temp_dir" -mindepth 1 -maxdepth 1 -type d -name 'apache-maven-*' | head -n 1)
  mkdir -p "$(dirname "$maven_home")"
  mv "$extracted" "$maven_home"
fi

exec "$maven_home/bin/mvn" "$@"
