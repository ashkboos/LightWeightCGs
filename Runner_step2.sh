#!/bin/bash

mkdir $2
mkdir $2/opal
mkdir $2/wala
java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --analyzeOutDir $1 $2/opal opal mergeOPAL -Xmx200000m
java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --analyzeOutDir $1 $2/wala wala mergeWALA -Xmx200000m
java -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --inputDemography $3 $2/inputStats.csv

python3 stats.py $2 > $2/stats.txt

#How to run => $1: call graphs folder, $2: result folder, $3: full input data file e.g.
#bash Runner_step2.sh results/outputStats/cgs results/outputStats/overAll results/inputMvnData/highly.connected10.resolved.csv

