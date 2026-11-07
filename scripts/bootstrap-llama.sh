#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$repo_root/third_party/llama.cpp"
commit="cd959bae99ff4bed22bef2604f45241c09bbb8c8"
tag="v0.9.0"

if [[ ! -d "$source_dir/.git" ]]; then
  mkdir -p "$repo_root/third_party"
  git -c http.version=HTTP/1.1 clone --depth 1 --filter=blob:none --branch "$tag" \
    https://github.com/ggml-org/llama.cpp.git "$source_dir"
fi

actual="$(git -C "$source_dir" rev-parse HEAD)"
if [[ "$actual" != "$commit" ]]; then
  if [[ -n "$(git -C "$source_dir" status --porcelain)" ]]; then
    echo "llama.cpp checkout is modified; refusing to replace it" >&2
    exit 1
  fi
  git -C "$source_dir" -c http.version=HTTP/1.1 fetch --depth 1 origin tag "$tag"
  git -C "$source_dir" checkout --detach "$commit"
fi

actual="$(git -C "$source_dir" rev-parse HEAD)"
[[ "$actual" == "$commit" ]] || { echo "Unexpected llama.cpp commit: $actual" >&2; exit 1; }
[[ -f "$source_dir/LICENSE" ]] || { echo "llama.cpp LICENSE is missing" >&2; exit 1; }
echo "llama.cpp pinned at $actual"
