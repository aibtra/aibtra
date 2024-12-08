#!/bin/bash

# Exit immediately if a command exits with a non-zero status
set -e

# Function to display error messages
error() {
    echo "Error: $1" >&2
    exit 1
}

# Check if exactly one parameter is provided
if [ "$#" -ne 1 ]; then
    error "Usage: $0 <path_to_repository>"
fi

# Store the original repository path as provided by the user
ORIGINAL_REPO_PATH="$1"
ORIGINAL_REPO_PATH="${ORIGINAL_REPO_PATH%/}"
ORIGINAL_REPO_PATH="${ORIGINAL_REPO_PATH//\\//}"

# Get the absolute path to the repository
REPO_PATH=$(realpath "$ORIGINAL_REPO_PATH")

# Generate a random prefix to prevent concurrent issues
RANDOM_PREFIX=$(mktemp -u XXXXXX)

# Define the temp directory inside the Git repository with the random prefix
OUTPUT_DIR="$REPO_PATH/.git/aibtra/$RANDOM_PREFIX"

# Check if the repository path exists and is a directory
if [ ! -d "$REPO_PATH" ]; then
    error "The path '$REPO_PATH' does not exist or is not a directory."
fi

# Check if the tmp directory exists; if not, attempt to create it
if [ ! -d "$OUTPUT_DIR" ]; then
    mkdir -p "$OUTPUT_DIR" || error "Failed to create tmp directory '$OUTPUT_DIR'."
fi

# Check if the provided path is a Git repository
if ! git -C "$REPO_PATH" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    error "The path '$REPO_PATH' is not a Git repository."
fi

# Get the absolute path to the repository root
REPO_ROOT=$(git -C "$REPO_PATH" rev-parse --show-toplevel)
if [ -z "$REPO_ROOT" ]; then
    error "Failed to determine the repository root."
fi

# Get the list of conflicting files
CONFLICT_FILES=$(git -C "$REPO_ROOT" diff --name-only --diff-filter=U)

# Check if there are any conflicting files
if [ -z "$CONFLICT_FILES" ]; then
    echo "No conflicting files found."
    exit 0
fi

# Path to the overview file
OVERVIEW_FILE="$OUTPUT_DIR/overview.txt"

# Output the path to the repository root to the overview file
echo -e "$REPO_ROOT" > "$OVERVIEW_FILE"

# Counter for unique filenames
COUNTER=1

# Process each conflicting file
while IFS= read -r FILE; do
    # Path to the file in the repository as provided by the user
    ABS_FILE_PATH="$ORIGINAL_REPO_PATH/$FILE"

    # Check if the file exists in the working directory
    ABS_FILE_ABSOLUTE_PATH="$REPO_ROOT/$FILE"
    if [ ! -e "$ABS_FILE_ABSOLUTE_PATH" ]; then
        echo "Warning: File '$ABS_FILE_ABSOLUTE_PATH' does not exist in the working directory."
        continue
    fi

    # Create temporary files for base, ours, and theirs
    BASE_FILE="$OUTPUT_DIR/base_${COUNTER}_$(basename "$FILE")"
    OURS_FILE="$OUTPUT_DIR/ours_${COUNTER}_$(basename "$FILE")"
    THEIRS_FILE="$OUTPUT_DIR/theirs_${COUNTER}_$(basename "$FILE")"
    MERGED_FILE="$OUTPUT_DIR/merged_${COUNTER}_$(basename "$FILE")"

    # Extract the base, ours, and theirs versions using git
    # :1: refers to the base version
    # :2: refers to "ours" (current branch)
    # :3: refers to "theirs" (merging branch)
    git -C "$REPO_ROOT" show ":1:$FILE" > "$BASE_FILE" || true
    git -C "$REPO_ROOT" show ":2:$FILE" > "$OURS_FILE" || true
    git -C "$REPO_ROOT" show ":3:$FILE" > "$THEIRS_FILE" || true
    cp "$ABS_FILE_ABSOLUTE_PATH" "$MERGED_FILE" || echo "Warning: Cannot copy merged file for '$FILE'."

    # Check if any of the base, ours, or theirs files are missing or empty
    if [ ! -s "$BASE_FILE" ] || [ ! -s "$OURS_FILE" ] || [ ! -s "$THEIRS_FILE" ]; then
        echo "Warning: One or more stages for '$FILE' do not exist. Skipping."
        continue
    fi

    # Append the information to the overview file
    echo -e "${FILE}\t$(basename "$BASE_FILE")\t$(basename "$OURS_FILE")\t$(basename "$THEIRS_FILE")" >> "$OVERVIEW_FILE"

    # Increment the counter
    COUNTER=$((COUNTER + 1))
done <<< "$CONFLICT_FILES"

# Inform the user
echo "CONFLICT-OVERVIEW: $OVERVIEW_FILE"
