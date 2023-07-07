#!/usr/bin/env bash
set -e
export TERM=ansi
export AWS_ACCESS_KEY_ID=foobar
export AWS_SECRET_ACCESS_KEY=foobar
export AWS_DEFAULT_REGION=eu-west-2

aws --endpoint-url=http://localhost:4566 opensearch create-domain --domain-name os01

echo "OpenSearch configured. Please wait until 'cluster on http://127.0.0.1:xxxxx is ready"
