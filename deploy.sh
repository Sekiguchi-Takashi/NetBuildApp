#!/data/data/com.termux/files/usr/bin/bash
cd "$(dirname "$0")" || exit 1
REPO=NetBuildApp
OWNER=Sekiguchi-Takashi
MSG="${1:-update}"
TOKEN=$(git config --global github.token)
if [ -z "$TOKEN" ]; then
  printf 'github.token が未設定です\n'
  exit 1
fi
curl -s -o /dev/null -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"$REPO\",\"private\":true}"
if [ ! -d .git ]; then
  git init -b main
fi
git remote remove origin 2>/dev/null
git remote add origin "https://$TOKEN@github.com/$OWNER/$REPO.git"
git add -A
git commit -m "$MSG" || printf 'コミット対象なし\n'
git push -u origin main
printf 'push 完了: %s\n' "$REPO"
