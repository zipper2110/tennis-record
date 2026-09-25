#!/bin/bash
# Collects the corresponding source of the pinned BtbN FFmpeg build in
# distribution/windows/native-dependencies.json.
#
# The script uses the stage selection of BtbN generate.sh and the pinned
# download commands of each stage, so it collects the same sources as the build.
#
# Usage: collect-btbn-ffmpeg-sources.sh [--list] <manifest> <output-dir>
#   --list  Print the stages and their pinned sources. Do not download.
#
# Requirements: bash 4, git, jq, xz, tar, svn and autoreconf (for some stages).
set -euo pipefail

LIST_ONLY=0
if [[ "${1:-}" == "--list" ]]; then
    LIST_ONLY=1
    shift
fi
if [[ $# -ne 2 ]]; then
    echo "Usage: $0 [--list] <manifest> <output-dir>" >&2
    exit 2
fi

MANIFEST="$(realpath "$1")"
OUT="$(realpath -m "$2")"
jq_field() { jq -er ".ffmpeg.$1" "$MANIFEST"; }

BUILD_REPO="$(jq_field buildRepository)"
BUILD_COMMIT="$(jq_field buildCommit)"
BUILD_TARGET="$(jq_field buildTarget)"
BUILD_VARIANT="$(jq_field buildVariant)"
mapfile -t BUILD_ADDINS < <(jq -er '.ffmpeg.buildAddins[]' "$MANIFEST")
SOURCE_REPO="$(jq_field sourceRepository)"
SOURCE_COMMIT="$(jq_field sourceCommit)"
VERSION="$(jq_field version)"

WORK="$(mktemp -d)"
trap 'rm -rf -- "$WORK"' EXIT

mini_clone() {
    git init -q "$3"
    git -C "$3" remote add origin "$1"
    git -C "$3" fetch -q --depth=1 origin "$2"
    git -C "$3" -c advice.detachedHead=false checkout -q FETCH_HEAD
}

mini_clone "$BUILD_REPO" "$BUILD_COMMIT" "$WORK/btbn"

# The stage download commands call these helpers from the BtbN base image.
mkdir -p "$WORK/bin"
for helper in git-mini-clone retry-tool check-wget; do
    install -m 755 "$WORK/btbn/images/base/${helper}.sh" "$WORK/bin/${helper}"
done
export PATH="$WORK/bin:$PATH"

cd "$WORK/btbn"
# Load the functions of generate.sh. Stop before it writes the Dockerfile.
sed -e '/^export TODF=/,$d' -e '/^cd "\$(dirname "\$0")"/d' generate.sh > "$WORK/generate-functions.sh"
set +eu
source "$WORK/generate-functions.sh" "$BUILD_TARGET" "$BUILD_VARIANT" "${BUILD_ADDINS[@]}"
set -eu
rm -f Dockerfile Dockerfile.*

ENTRYSCRIPT="$(ls -1d scripts.d/* | tail -n 1)"
STAGE_SCRIPTS=()
for DEP in $(get_stagedeps_recursive "$ENTRYSCRIPT"); do
    STAGE="$(resolvestage "$DEP")"
    if [[ -d "$STAGE" ]]; then
        STAGE_SCRIPTS+=( "$STAGE"/??-*.sh )
    else
        STAGE_SCRIPTS+=( "$STAGE" )
    fi
done

# Prints the download commands of an enabled stage. Prints nothing for a
# disabled stage or a stage without a download.
stage_download() {
    (
        SELF="$1"
        STAGENAME="$(basename "$1" .sh)"
        source util/dl_functions.sh
        source "$1"
        ffbuild_enabled || exit 0
        ffbuild_dockerdl
    )
}

mkdir -p "$OUT/stages"
INDEX="$OUT/SOURCES.txt"
{
    echo "Corresponding source of FFmpeg $VERSION"
    echo "BtbN build: $BUILD_REPO $BUILD_COMMIT"
    echo "Build: $BUILD_TARGET-$BUILD_VARIANT ${BUILD_ADDINS[*]}"
    echo "FFmpeg: $SOURCE_REPO $SOURCE_COMMIT"
    echo
    echo "Stage archives in stages/ and the download command of each stage:"
} > "$INDEX"

for SCRIPT in "${STAGE_SCRIPTS[@]}"; do
    STAGENAME="$(basename "$SCRIPT" .sh)"
    STG="$(stage_download "$SCRIPT")"
    [[ -z "$STG" ]] && continue
    printf '\n[%s]\n%s\n' "$STAGENAME" "$STG" >> "$INDEX"
    if [[ $LIST_ONLY -eq 1 ]]; then
        echo "$STAGENAME: $(tr '\n' ' ' <<< "$STG")"
        continue
    fi
    echo "Collecting $STAGENAME" >&2
    STAGEDIR="$WORK/stage-$STAGENAME"
    mkdir -p "$STAGEDIR"
    ( cd "$STAGEDIR" && eval "set -e; $STG" )
    tar -C "$STAGEDIR" --exclude=.git --exclude=.svn -cJf "$OUT/stages/$STAGENAME.tar.xz" .
    rm -rf -- "$STAGEDIR"
done

if [[ $LIST_ONLY -eq 1 ]]; then
    exit 0
fi

echo "Collecting FFmpeg and the BtbN build scripts" >&2
tar -C "$WORK" --exclude=.git -cJf "$OUT/FFmpeg-Builds-$BUILD_COMMIT.tar.xz" btbn
mini_clone "$SOURCE_REPO" "$SOURCE_COMMIT" "$WORK/ffmpeg"
tar -C "$WORK" --exclude=.git -cJf "$OUT/FFmpeg-$SOURCE_COMMIT.tar.xz" ffmpeg
