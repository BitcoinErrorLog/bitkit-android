#!/bin/bash

# Script to pull translations from Transifex and clean up empty files/directories

set -e

RES_DIR="app/src/main/res"

# Helper function to rename or merge directories
rename_or_merge() {
    local src="$1"
    local dst="$2"
    local src_name=$(basename "$src")
    local dst_name=$(basename "$dst")
    
    if [ ! -d "$dst" ]; then
        echo "  Renaming: $src_name -> $dst_name"
        mv "$src" "$dst" && return 0
    else
        echo "  Merging: $src_name -> $dst_name"
        find "$src" -mindepth 1 -maxdepth 1 -exec mv {} "$dst/" \; 2>/dev/null || true
        rmdir "$src" 2>/dev/null && return 0 || return 1
    fi
}

echo "Pulling translations from Transifex..."
tx pull -a

echo ""
echo "Renaming and cleaning up directories..."

RENAMED_COUNT=0
REMOVED_COUNT=0

# Process all values-* directories
while IFS= read -r dir; do
    dir_name=$(basename "$dir")
    dir_path=$(dirname "$dir")
    
    case "$dir_name" in
        values-arb)
            rename_or_merge "$dir" "$dir_path/values-ar" && RENAMED_COUNT=$((RENAMED_COUNT + 1))
            ;;
        values-es_419)
            rename_or_merge "$dir" "$dir_path/values-b+es+419" && RENAMED_COUNT=$((RENAMED_COUNT + 1))
            ;;
        values-es_ES)
            rename_or_merge "$dir" "$dir_path/values-es" && RENAMED_COUNT=$((RENAMED_COUNT + 1))
            ;;
        values-b+es+419|values-es|values-pt)
            # Keep these as-is
            ;;
        values-b+pt+PT|values-pt_PT)
            echo "  Removing: $dir_name"
            rm -rf "$dir" && REMOVED_COUNT=$((REMOVED_COUNT + 1))
            ;;
        values-b+pt+*|values-pt_*)
            # Convert Brazilian Portuguese to values-pt
            rename_or_merge "$dir" "$dir_path/values-pt" && RENAMED_COUNT=$((RENAMED_COUNT + 1))
            ;;
        values-b+*)
            # Convert other BCP 47 formats to underscore format
            new_name=$(echo "$dir_name" | sed 's/values-b+\([a-z][a-z]*\)+\([A-Z0-9][A-Z0-9]*\)/values-\1_\2/')
            if [ "$new_name" != "$dir_name" ]; then
                rename_or_merge "$dir" "$dir_path/$new_name" && RENAMED_COUNT=$((RENAMED_COUNT + 1))
            fi
            ;;
    esac
done < <(find "$RES_DIR" -type d -name "values-*" | sort)

echo "  Renamed $RENAMED_COUNT directories"
echo "  Removed $REMOVED_COUNT directories"

echo ""
echo "Normalizing XML formatting..."

# Normalize XML
NORMALIZED_COUNT=0
while IFS= read -r file; do
    awk '
        {
            gsub(/[[:space:]]+$/, "")
            if ($0 ~ /^[[:space:]]*<\/resources>[[:space:]]*$/) {
                print "</resources>"
                saw_resources = 1
                next
            }
            if (saw_resources && $0 == "") next
            if (saw_resources && $0 != "") saw_resources = 0
            print
        }
    ' "$file" > "$file.tmp" && mv "$file.tmp" "$file" 2>/dev/null && NORMALIZED_COUNT=$((NORMALIZED_COUNT + 1))
done < <(find "$RES_DIR" -type f -path "*/values-*/strings.xml" 2>/dev/null)

echo "  Normalized $NORMALIZED_COUNT files"

echo ""
echo "Cleaning up empty files and directories..."

EMPTY_COUNT=0
DELETED_DIRS=0
declare -a dirs_to_check

# Delete empty files and collect their directories
while IFS= read -r file; do
    line_count=$(wc -l < "$file" 2>/dev/null | tr -d ' ' || echo "0")
    string_count=$(grep -c '<string name=' "$file" 2>/dev/null || echo "0")
    
    if [ "$line_count" -le 4 ] || [ "$string_count" -eq 0 ]; then
        echo "  Deleting empty file: $file"
        dirs_to_check+=("$(dirname "$file")")
        rm -f "$file"
        EMPTY_COUNT=$((EMPTY_COUNT + 1))
    fi
done < <(find "$RES_DIR" -type f -path "*/values-*/strings.xml")

# Remove empty directories
for dir in $(printf '%s\n' "${dirs_to_check[@]}" | sort -u); do
    [ -d "$dir" ] || continue
    file_count=$(find "$dir" -type f ! -name '.gitkeep' ! -name '.DS_Store' 2>/dev/null | wc -l | tr -d ' ')
    if [ "$file_count" -eq 0 ]; then
        echo "  Deleting empty directory: $dir"
        rmdir "$dir" 2>/dev/null && DELETED_DIRS=$((DELETED_DIRS + 1))
    fi
done

echo ""
echo "Complete!"
echo "  Renamed: $RENAMED_COUNT, Removed: $REMOVED_COUNT"
echo "  Normalized: $NORMALIZED_COUNT"
echo "  Deleted files: $EMPTY_COUNT, Deleted dirs: $DELETED_DIRS"
