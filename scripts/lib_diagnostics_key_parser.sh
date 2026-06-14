#!/usr/bin/env bash

diagnostic_extract_key() {
  local file="$1"
  local key="$2"
  awk -v wanted="$key" '
    {
      line = $0
      pattern = "(^|[[:space:]])" wanted "="
      if (match(line, pattern)) {
        value = substr(line, RSTART + RLENGTH)
        sub(/[[:space:]].*$/, "", value)
        sub(/\r$/, "", value)
        sub(/,$/, "", value)
        print value
        exit
      }
    }
  ' "$file"
}

diagnostic_value_or_unavailable() {
  local value="$1"
  if [[ -n "$value" ]]; then
    printf '%s\n' "$value"
  else
    printf 'unavailable\n'
  fi
}

diagnostic_get_key_or_unavailable() {
  local file="$1"
  local key="$2"
  diagnostic_value_or_unavailable "$(diagnostic_extract_key "$file" "$key")"
}
