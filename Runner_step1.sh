#!/bin/bash
i=0
mkdir $2
while IFS= read -r line; do
  if [ $i -ne 0 ]; then
    coord="$(echo $line | cut -d',' -f2)"
    mkdir $2/$coord
    mkdir $2/$coord/opal
    mkdir $2/$coord/merge
    echo "Generating graphs for "$coord" package..."
    java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --opal "$line" $2/$coord/opal -Xmx14g >$2/$coord/opal/log
    java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --merge "$line" $2/$coord/merge -Xmx14g >$2/$coord/merge/log
  fi
  ((i = i + 1))
done <"$1"

#How to run => $1: input file, $2: output folder
#bash Runner_step1.sh results/inputMvnData/highly.connected10.resolved.csv results/outputStats/cgs
