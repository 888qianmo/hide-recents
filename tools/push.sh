#!/usr/bin/env bash
# 推送到 GitHub（仓库不存在时自动创建）。
#
#   GITHUB_USER=你的用户名 GITHUB_TOKEN=ghp_xxx bash tools/push.sh
#
# 可选：
#   REPO=hide-recents   仓库名
#   PRIVATE=1           建私有仓库
#
# token 用 classic PAT 勾 repo（创建 + 推送都需要）；只用 fine-grained token 时
# 得先在网页建好空仓库，再直接 git push。
set -euo pipefail

: "${GITHUB_USER:?请设置 GITHUB_USER}"
: "${GITHUB_TOKEN:?请设置 GITHUB_TOKEN（classic PAT，勾 repo）}"
REPO="${REPO:-hide-recents}"
PRIVATE="${PRIVATE:-0}"

cd "$(dirname "$0")/.."
[ -d .git ] || { echo "当前目录不是 git 仓库"; exit 1; }

python3 - "$GITHUB_USER" "$GITHUB_TOKEN" "$REPO" "$PRIVATE" <<'PY'
import json, sys, urllib.request, urllib.error
user, token, repo, private = sys.argv[1:5]
body = json.dumps({
    "name": repo,
    "private": private == "1",
    "description": "LSPosed 模块：隐藏后台卡片（system_server 过滤 + PUI 风格界面）",
}).encode()
req = urllib.request.Request(
    "https://api.github.com/user/repos", data=body, method="POST",
    headers={"Authorization": "Bearer " + token,
             "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
try:
    r = urllib.request.urlopen(req, timeout=25)
    print("created:", json.load(r)["html_url"])
except urllib.error.HTTPError as e:
    msg = e.read().decode("utf-8", "replace")
    if e.code == 422 and "already exists" in msg:
        print("仓库已存在，继续推送")
    else:
        sys.exit("创建失败 %s %s" % (e.code, msg[:300]))
PY

git push "https://${GITHUB_USER}:${GITHUB_TOKEN}@github.com/${GITHUB_USER}/${REPO}.git" main:main
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/${GITHUB_USER}/${REPO}.git"
echo "完成: https://github.com/${GITHUB_USER}/${REPO}"
