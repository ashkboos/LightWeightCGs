#!/bin/bash
i=0
mkdir src/main/java/evaluation/results/outputStats/cgs
while IFS= read -r line; do
  if [ $i -ne 0 ]; then
    mkdir src/main/java/evaluation/results/outputStats/cgs/$i
    mkdir src/main/java/evaluation/results/outputStats/cgs/$i/opal
    mkdir src/main/java/evaluation/results/outputStats/cgs/$i/merge
    echo "Generating graphs for "$i"th package..."
    java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --opal "$line" src/main/java/evaluation/results/outputStats/cgs/$i/opal -Xmx16g >src/main/java/evaluation/results/outputStats/cgs/$i/opal/log
    java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --merge "$line" src/main/java/evaluation/results/outputStats/cgs/$i/merge -Xmx16g >src/main/java/evaluation/results/outputStats/cgs/$i/merge/log
  fi
  ((i = i + 1))
done <"$1"
mkdir src/main/java/evaluation/results/outputStats/overAll
java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --fromFiles src/main/java/evaluation/results/outputStats/cgs src/main/java/evaluation/results/outputStats/overAll src/main/java/evaluation/results/inputMvnData/highly.connected3.resolved.csv -Xmx100g
