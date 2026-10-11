#!/bin/bash
# 把本仓库推送到 GitHub。
# 前置：已完成 gh 登录（gh auth login --web --git-protocol ssh）
#
# 用法：
#   bash tools/gh_push.sh                    # 默认公开仓库 gym-smart-management-system
#   bash tools/gh_push.sh --private          # 建私有仓库
#   bash tools/gh_push.sh my-repo-name       # 自定义仓库名

set -euo pipefail

GH="/Users/a1/.homebrew/bin/gh"
REPO="gym-smart-management-system"
VISIBILITY="--public"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --private) VISIBILITY="--private"; shift ;;
    *)         REPO="$1"; shift ;;
  esac
done

echo "==> 1/4 检查 GitHub 登录状态"
if ! "$GH" auth status >/dev/null 2>&1; then
  echo "未登录。请先执行："
  echo "    $GH auth login --web --git-protocol ssh"
  exit 1
fi
OWNER=$("$GH" api user --jq '.login')
echo "    已登录为：$OWNER"

echo "==> 2/4 在 GitHub 上创建仓库 $OWNER/$REPO"
if "$GH" repo view "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "    仓库已存在，跳过创建"
else
  "$GH" repo create "$REPO" $VISIBILITY --source=. --remote=origin --push=false
  echo "    已创建（$VISIBILITY）"
fi

echo "==> 3/4 确认 origin 使用 SSH"
"$GH" repo set-default "$OWNER/$REPO" >/dev/null 2>&1 || true
git remote remove origin 2>/dev/null || true
git remote add origin "git@github.com:$OWNER/$REPO.git"
echo "    origin = git@github.com:$OWNER/$REPO.git"

echo "==> 4/4 推送"
BRANCH=$(git branch --show-current)
git push -u origin "$BRANCH"

echo
echo "完成：$("$GH" repo view "$OWNER/$REPO" --json url --jq '.url')"
