#!/bin/bash
# LocalStack の起動が終わったときに実行される（/etc/localstack/init/ready.d に置いたスクリプト）。
# 記録の画像を保存するバケットを作る。すでにあれば何もしない（コンテナを作り直しても動くように）
set -euo pipefail

bucket=${S3_BUCKET:-fitlog-images}
if awslocal s3api head-bucket --bucket "$bucket" 2>/dev/null; then
  echo "バケット ${bucket} はすでにあります"
else
  awslocal s3 mb "s3://${bucket}"
  echo "バケット ${bucket} を作りました"
fi
