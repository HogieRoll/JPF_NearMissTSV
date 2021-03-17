#!/bin/bash
if [ ! -e "jpf-core" ]; then
    git clone https://github.com/javapathfinder/jpf-core
fi

for f in src/input/*.java; do
    filename=$(basename -- "$f")
    filename="${filename%.*}"
    printf "target = $filename\nlistener = .listener.CoverageTool,.listener.MemoizationTool" > "src/input/$filename.jpf"
done
cp src/listeners/*.java jpf-core/src/main/gov/nasa/jpf/listener/
cp src/input/*.java jpf-core/src/examples/
cd jpf-core
gradle compile
gradle buildJars
cd ..
