#!/bin/bash
TARGET=../../nextflow-website/assets
LATEST=$TARGET/docs/latest/
VERSION=$TARGET/docs/v0.12.3/

mkdir -p $TARGET/docs/latest/
mkdir -p $TARGET/docs/v0.12.3/

rsync -r _build/html/* $LATEST
rsync -r _build/html/* $VERSION

( cd ../../nextflow-website; jbake)
