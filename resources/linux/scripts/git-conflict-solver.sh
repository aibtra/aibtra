#!/bin/bash

if [[ $# -lt 1 ]]; then
    echo "Usage: $(basename "$0") <git_repo_root>"
    exit 1
fi

git_repo_root="$1"

overview_path=""
script_dir="$(dirname "$0")"
script_path="$script_dir/lib/app/git-compose-conflict-overview.sh"
executable_path="$script_dir/bin/aibtra"

while IFS= read -r line; do
    echo "$line"
    if [[ "$line" == CONFLICT-OVERVIEW:* ]]; then
        overview_path="${line#CONFLICT-OVERVIEW: }"
    fi
done < <("$script_path" "$git_repo_root")

if [[ -n "$overview_path" ]]; then
    "$executable_path" --resolver "$overview_path"
else
    echo -e "\e[31mSomething went wrong. No overview path found.\e[0m"
    read -r -p "Press Enter to continue"
fi
