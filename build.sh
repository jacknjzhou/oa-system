#!/usr/bin/env bash
# 在云同步目录（Synology Drive / OneDrive / NFS 等）里直接跑 `docker compose up --build` 会卡死：
# BuildKit 打包构建上下文时，虚化文件（File Provider placeholder）的 lseek(SEEK_HOLE) 会
# 触发 NAS 下载，网络一抖就无限挂起（表现为 "transferring context: 27B" 卡几十分钟）。
#
# 本脚本：把代码 rsync 到本地 APFS 镜像（默认 /tmp/oa-build），从那里构建镜像，
# 再回到仓库目录 `docker compose up -d`（不带 --build，复用已构建镜像）。
#
# 用法:
#   ./build.sh            # 构建 backend+frontend 镜像并启动栈
#   ./build.sh backend    # 只重建后端
#   ./build.sh frontend   # 只重建前端
#   ./build.sh down       # 停止栈（不删数据卷）
#   OA_BUILD_DIR=/path ./build.sh   # 自定义镜像目录（必须是非云同步的本地盘）
set -euo pipefail
cd "$(dirname "$0")"

BUILD_DIR="${OA_BUILD_DIR:-/tmp/oa-build}"
TARGET="${1:-all}"

sync_and_build() {
  local svc=$1
  local src="backend" ctx="oa-system-backend:latest"
  [ "$svc" = "frontend" ] && { src="frontend"; ctx="oa-system-frontend:latest"; }

  echo "==> 同步 $src/ → $BUILD_DIR/$src/（APFS 镜像）"
  mkdir -p "$BUILD_DIR/$src"
  case "$src" in
    backend)
      rsync -a --delete --exclude target --exclude data.sql "$src/" "$BUILD_DIR/$src/" ;;
    frontend)
      rsync -a --delete --exclude node_modules --exclude dist "$src/" "$BUILD_DIR/$src/" ;;
  esac

  echo "==> 构建 $ctx"
  (cd "$BUILD_DIR/$src" && docker build -q -t "$ctx" .)
}

case "$TARGET" in
  down)
    docker compose down
    ;;
  up)
    docker compose up -d
    ;;
  backend|frontend)
    sync_and_build "$TARGET"
    docker compose up -d --force-recreate "$TARGET"
    ;;
  all)
    sync_and_build backend
    sync_and_build frontend
    docker compose up -d
    ;;
  *)
    echo "用法: $0 [all|backend|frontend|up|down]" >&2
    exit 1
    ;;
esac

echo "==> 完成："
docker compose ps --format 'table {{.Name}}\t{{.Status}}'
