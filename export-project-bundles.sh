#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="${1:-.}"
OUTPUT_DIR="${2:-project-context}"
MAX_FILE_BYTES="${MAX_FILE_BYTES:-500000}"

cd "$ROOT_DIR"
ROOT_DIR="$(pwd)"
OUTPUT_DIR="$(realpath -m "$OUTPUT_DIR")"

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

is_excluded() {
  local path="$1"
  case "$path" in
    ./.git/*|*/.git/*|*/.gradle/*|*/build/*|*/target/*|*/node_modules/*|*/dist/*|*/__pycache__/*|*/.venv/*|*/venv/*|*/project-context/*|*/context-upload/*)
      return 0
      ;;
  esac
  return 1
}

is_allowed() {
  local path="$1"
  case "$path" in
    *.java|*.kt|*.kts|*.groovy|*.py|*.js|*.jsx|*.ts|*.tsx|*.json|*.yaml|*.yml|*.properties|*.xml|*.sql|*.sh|*.md|*.gradle|*.toml|*.ini|Dockerfile|*.dockerfile|docker-compose.yml|docker-compose.yaml|.gitignore|.env.example)
      return 0
      ;;
  esac
  return 1
}

language_for() {
  case "$1" in
    *.java|*.kt|*.kts|*.groovy) printf 'java' ;;
    *.py) printf 'python' ;;
    *.js|*.jsx) printf 'javascript' ;;
    *.ts|*.tsx) printf 'typescript' ;;
    *.json) printf 'json' ;;
    *.yaml|*.yml) printf 'yaml' ;;
    *.properties|*.toml|*.ini) printf 'properties' ;;
    *.xml) printf 'xml' ;;
    *.sql) printf 'sql' ;;
    *.sh) printf 'bash' ;;
    *.md) printf 'markdown' ;;
    *.gradle) printf 'groovy' ;;
    Dockerfile|*.dockerfile) printf 'dockerfile' ;;
    *) printf 'text' ;;
  esac
}

bundle_for() {
  case "$1" in
    README.md|.gitignore|.env.example|docs/*)
      printf '00-project-overview.md' ;;
    infra/*|.github/*)
      printf '01-infrastructure-and-ci.md' ;;
    services/catalog-service/*)
      printf '02-catalog-service.md' ;;
    services/cart-service/*)
      printf '03-cart-service.md' ;;
    services/auth-service/*)
      printf '04-auth-service.md' ;;
    services/inventory-service/*)
      printf '05-inventory-service.md' ;;
    services/order-service/*|services/payment-service/*|services/notification-service/*)
      printf '06-checkout-services.md' ;;
    services/search-service/*|services/wishlist-service/*|services/review-service/*)
      printf '07-discovery-services.md' ;;
    services/api-gateway/*)
      printf '08-api-gateway.md' ;;
    frontend/*)
      printf '09-frontend.md' ;;
    *)
      printf '10-other-project-files.md' ;;
  esac
}

for bundle in \
  00-project-overview.md \
  01-infrastructure-and-ci.md \
  02-catalog-service.md \
  03-cart-service.md \
  04-auth-service.md \
  05-inventory-service.md \
  06-checkout-services.md \
  07-discovery-services.md \
  08-api-gateway.md \
  09-frontend.md \
  10-other-project-files.md; do
  : > "$OUTPUT_DIR/$bundle"
  printf '# %s\n\n' "${bundle%.md}" >> "$OUTPUT_DIR/$bundle"
done

manifest="$OUTPUT_DIR/manifest.md"
{
  printf '# Project Context Bundles\n\n'
  printf 'Generated: `%s`\n\n' "$(date -Iseconds)"
  printf 'Each Markdown file combines source code from one logical project area.\n\n'
  printf '## Bundles\n\n'
  for bundle in "$OUTPUT_DIR"/*.md; do
    name="$(basename "$bundle")"
    [[ "$name" == "manifest.md" ]] || printf -- '- `%s`\n' "$name"
  done
} > "$manifest"

count=0
skipped=0

while IFS= read -r -d '' source; do
  relative="${source#"$ROOT_DIR/"}"
  relative="./$relative"

  if is_excluded "$relative" || ! is_allowed "$relative"; then
    continue
  fi

  size="$(stat -c '%s' "$source")"
  if (( size > MAX_FILE_BYTES )); then
    skipped=$((skipped + 1))
    printf -- '\n## Skipped file: `%s`\n\nFile size: `%s` bytes.\n' "${relative#./}" "$size" >> "$manifest"
    continue
  fi

  relative_no_prefix="${relative#./}"
  bundle="$(bundle_for "$relative_no_prefix")"
  output="$OUTPUT_DIR/$bundle"
  language="$(language_for "$relative_no_prefix")"

  {
    printf '\n---\n\n## File: `%s`\n\n' "$relative_no_prefix"
    printf '```%s\n' "$language"
    cat "$source"
    printf '\n```\n'
  } >> "$output"

  printf -- '- `%s` → `%s`\n' "$relative_no_prefix" "$bundle" >> "$manifest"
  count=$((count + 1))
done < <(find "$ROOT_DIR" -type f -print0 | sort -z)

{
  printf '\n## Summary\n\n'
  printf -- '- Source files bundled: `%s`\n' "$count"
  printf -- '- Oversized files skipped: `%s`\n' "$skipped"
  printf -- '- Maximum source file size: `%s` bytes\n' "$MAX_FILE_BYTES"
} >> "$manifest"

printf 'Created grouped Markdown context in: %s\n' "$OUTPUT_DIR"
printf 'Source files bundled: %s\n' "$count"
printf 'Upload up to 11 files from that directory.\n'
