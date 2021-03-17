#!/bin/bash
echo $1
cp -f $1 src/input/
. build.sh
filename=$(basename -- "$1")
filename="${filename%.*}"
echo $filename
java -jar jpf-core/build/RunJPF.jar src/input/$filename.jpf
