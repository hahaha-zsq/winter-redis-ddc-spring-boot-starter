#!/bin/bash

# 切换到脚本所在目录的上一级（即pom.xml所在目录）
cd "$(dirname "$0")/.."

# 检查pom.xml是否存在
if [ ! -f pom.xml ]; then
    echo "未找到pom.xml，请在项目根目录下运行此脚本。"
    exit 1
fi

# 读取用户输入的版本号
read -p "请输入发布的版本号（如0.0.2）: " VERSION

if [ -z "$VERSION" ]; then
    echo "版本号不能为空，发布终止。"
    exit 1
fi

# 修改project标签下的版本号
echo "更新pom.xml中的版本号为$VERSION..."
sed -i '' '
    /<groupId>io.github.hahaha-zsq<\/groupId>/{
        n
        s/[[:space:]]*<version>.*<\/version>[[:space:]]*$/    <version>'"$VERSION"'<\/version>/
    }
' pom.xml

# 提交更改
echo "提交更改..."
git add pom.xml
git commit -m "发布版本 $VERSION"

# 推送到远程仓库（master分支）
echo "推送到远程仓库..."
git push origin master

# 检查tag是否已存在
if git rev-parse "v$VERSION" >/dev/null 2>&1; then
    echo "标签v$VERSION已存在，跳过创建。"
else
    # 创建标签
    echo "创建标签v$VERSION..."
    git tag v$VERSION
    # 推送标签
    echo "推送标签..."
    git push origin v$VERSION
fi

echo "发布流程已启动，请查看GitHub Actions运行状态。"
echo "GitHub Actions将自动构建并发布到Maven中央仓库。"
echo "发布后，可以在 https://central.sonatype.com/ 查看发布状态。"
echo "通常需要几分钟到几小时不等，新版本才会在Maven中央仓库中可用。" 