#!/usr/bin/env bash
# Copy a NUL-delimited Git file list without interpreting filenames as shell code.
# Deleted working-tree files are skipped. Symlink targets are scanned as text, never followed.
copy_scan_files() {
  local source_root="$1" destination="$2" list="$3" file parent
  SCAN_COPIED_FILES=0
  mkdir -p "$destination" || return
  while IFS= read -r -d '' file; do
    [[ -f "$source_root/$file" || -L "$source_root/$file" ]] || continue
    parent="${file%/*}"
    [[ "$parent" != "$file" ]] || parent=.
    mkdir -p "$destination/$parent" || return
    if [[ -L "$source_root/$file" ]]; then
      readlink "$source_root/$file" > "$destination/$file" || return
    else
      cp "$source_root/$file" "$destination/$file" || return
    fi
    SCAN_COPIED_FILES=$((SCAN_COPIED_FILES + 1))
  done < "$list"
}
