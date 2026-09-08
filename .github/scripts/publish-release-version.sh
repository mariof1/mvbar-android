#!/usr/bin/env bash
set -euo pipefail

: "${RELEASE_VERSION:?Release version is required}"
: "${GITHUB_REF_NAME:?Release branch is required}"
branch="$GITHUB_REF_NAME"
tag="v${RELEASE_VERSION}"
git check-ref-format "refs/heads/$branch"
git check-ref-format "refs/tags/$tag"
base_commit="$(git rev-parse HEAD)"
base_version="$(git show "$base_commit:version.properties")"

git config user.name "mariof1"
git config user.email "32400371+mariof1@users.noreply.github.com"
git add -- version.properties
git commit -m "chore: bump Android version to $RELEASE_VERSION"
release_commit="$(git rev-parse HEAD)"
# Keep the tag on the exact source + version that produced the verified APKs.
# Only the version commit is rebased onto newer branch work below.
git tag -a "$tag" "$release_commit" -m "Android release $RELEASE_VERSION"

for attempt in 1 2 3; do
  git fetch origin "refs/heads/$branch"
  remote_head="$(git rev-parse FETCH_HEAD)"
  remote_tag="$(git ls-remote origin "refs/tags/$tag^{}" | cut -f1)"
  if [[ "$remote_tag" == "$release_commit" ]]; then
    # An earlier atomic push may have succeeded despite a lost response.
    exit 0
  fi
  if [[ -n "$remote_tag" ]]; then
    echo "::error::Release tag $tag was published by another run. It will not be overwritten."
    exit 1
  fi
  if ! git merge-base --is-ancestor "$base_commit" "$remote_head"; then
    echo "::error::Release branch history changed. Build again from the current branch."
    exit 1
  fi
  if [[ "$(git show "$remote_head:version.properties")" != "$base_version" ]]; then
    echo "::error::Another release changed version.properties during the build. Build again with a fresh version."
    exit 1
  fi
  if ! git -c rebase.updateRefs=false rebase --onto "$remote_head" "$base_commit" "$release_commit"; then
    git rebase --abort
    exit 1
  fi
  # Either both refs publish, or neither does. Never force-push branch history.
  if git push --atomic origin "HEAD:refs/heads/$branch" "refs/tags/$tag:refs/tags/$tag"; then
    exit 0
  fi
  echo "Release push attempt $attempt failed; checking the latest remote state."
done
echo "::error::Could not publish release refs after three attempts."
exit 1
