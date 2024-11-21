#!/bin/bash

set -euo pipefail

echo_error() {
    printf "Error: %s\n" "$*" >&2
}

echo_warning() {
    printf "Warning: %s\n" "$*" >&2
}

echo_info() {
    printf "%s\n" "$*"
}

check_dependencies() {
    local dependencies=("curl" "tar" "find" "rm" "mkdir" "cp" "rmdir")
    for cmd in "${dependencies[@]}"; do
        if ! command -v "$cmd" >/dev/null 2>&1; then
            echo_error "Required command '$cmd' is not installed. Please install it and try again."
            exit 1
        fi
    done
}

remove_tree() {
    local root="$1"
    local update_files="$root/update.files"

    if [[ ! -f "$update_files" ]]; then
        echo_warning "update.files not found at path: $update_files"
        return
    fi

    while IFS= read -r line || [[ -n "$line" ]]; do
        local relative_path
        relative_path="$(printf "%s" "$line" | xargs)"
        if [[ -n "$relative_path" ]]; then
            local file_path="$root/$relative_path"
            if [[ -e "$file_path" ]]; then
                rm -f "$file_path"
                echo_info "Removed file: $file_path"
            else
                echo_warning "File not found: $file_path"
            fi
        fi
    done < "$update_files"

    rm -f "$update_files"

    find "$root" -type d -empty -print0 | while IFS= read -r -d '' dir; do
        if [[ "$dir" != "$root" ]]; then
            rmdir "$dir" && echo_info "Removed empty directory: $dir" || true
        fi
    done

    local remaining_items
    remaining_items=$(find "$root" -mindepth 1)
    if [[ -n "$remaining_items" ]]; then
        echo_warning "Items not removed:"
        find "$root" -mindepth 1 -print0 | while IFS= read -r -d '' item; do
            echo_warning "Item not removed: $item"
        done
    fi
}

remove_directory_safe() {
    local path="$1"

    if [[ -d "$path" ]]; then
        if [[ -z "$(ls -A "$path" 2>/dev/null)" ]]; then
            rm -rf "$path"
            echo_info "Removed directory: $path"
        else
            echo_warning "Directory $path is not empty. Skipping removal."
        fi
    else
        echo_warning "Directory $path does not exist. Nothing to remove."
    fi
}

main() {
    check_dependencies

    if pgrep aibtra >/dev/null 2>&1; then
        echo_error "aibtra process is currently running. Please exit it before proceeding."
        read -r -p "Press Enter to exit"
        exit 1
    fi

    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

    local bundle_type_file="$script_dir/lib/app/aibtra.bundletype"
    local update_files="$script_dir/update.files"

    if [[ ! -f "$bundle_type_file" ]]; then
        echo_error "Bundle type file not found at path: $bundle_type_file"
        exit 1
    fi

    if [[ ! -f "$update_files" ]]; then
        echo_error "update.files not found at path: $update_files"
        exit 1
    fi

    local bundle_type_raw bundle_type
    bundle_type_raw="$(<"$bundle_type_file")"
    bundle_type="$(printf "%s" "$bundle_type_raw" | tr '[:lower:]' '[:upper:]' | xargs)"
    local tag
    case "$bundle_type" in
        STABLE)
            tag="stable"
            ;;
        LATEST)
            tag="latest"
            ;;
        EXPERIMENTAL)
            tag="experimental"
            ;;
        *)
            echo_error "Invalid bundle type: '$bundle_type'. Expected 'STABLE', 'LATEST', or 'EXPERIMENTAL'."
            exit 1
            ;;
    esac

    local download_urls=(
        "https://www.aibtra.dev/downloads/linux-$tag.tar.gz"
        "https://github.com/aibtra/aibtra/releases/download/$tag/aibtra-$tag-linux.tar.gz"
    )

    temp_dir="$(mktemp -d "/tmp/aibtra_update_XXXXXX")"
    echo_info "Using temp directory: $temp_dir"

    if [[ -n "$(ls -A "$temp_dir" 2>/dev/null)" ]]; then
        echo_error "Temp directory is not empty."
        exit 1
    fi

    cleanup() {
        if [[ -n "$temp_dir" && "$temp_dir" == /tmp/aibtra_update_* ]]; then
            rm -rf "$temp_dir"
        else
            echo_error "Skipping cleanup: temp_dir is invalid ($temp_dir)"
        fi
    }
    trap cleanup EXIT

    local download_success=false
    local archive_path="$temp_dir/aibtra-$tag-linux.tar.gz"

    for url in "${download_urls[@]}"; do
        echo_info "Attempting to download from: $url"
        if curl -L -o "$archive_path" "$url"; then
            echo_info "Download completed from: $url"
            download_success=true
            break
        else
            echo_warning "Failed to download from: $url"
            rm -f "$archive_path"
        fi
    done

    if [[ "$download_success" = false ]]; then
        echo_error "Failed to download the file from all provided URLs."
        exit 1
    fi

    echo_info "Starting extraction..."
    local extract_path="$temp_dir/extracted"
    mkdir -p "$extract_path"
    echo_info "Extracting archive to: $extract_path"
    if tar -xzf "$archive_path" -C "$extract_path"; then
        echo_info "Extraction completed."
    else
        echo_error "Failed to extract archive."
        exit 1
    fi

    local source_dir="$extract_path/aibtra"
    if [[ ! -d "$source_dir" ]]; then
        echo_error "Top-level 'aibtra' directory not found in the extracted archive."
        exit 1
    fi

    echo_info "Replacing content in $script_dir"

    remove_tree "$script_dir"

    echo_info "Copying new files..."
    while IFS= read -r -d '' item; do
        local rel_path="${item#"$source_dir"/}"
        local target_path="$script_dir/$rel_path"

        if [[ -d "$item" ]]; then
            if [[ ! -d "$target_path" ]]; then
                mkdir -p "$target_path"
                echo_info "Created directory: $target_path"
            fi
        elif [[ -f "$item" ]]; then
            mkdir -p "$(dirname "$target_path")"
            cp -f "$item" "$target_path"
            echo_info "Copied file: $target_path"
        fi
    done < <(find "$source_dir" -mindepth 1 -print0)
    echo_info "Update process completed successfully."
}

trap 'echo_error "An error occurred on line $LINENO."; exit 1' ERR

main
