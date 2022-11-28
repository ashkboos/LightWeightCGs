#!/bin/bash

i=0
folder="$2"
mkdir $folder
cp $1 $2/input.csv
while IFS= read -r line; do
  if [ $i -ne 0 ]; then
    coord="$(echo $line | cut -d',' -f2)"
    mkdir $folder/$coord
    mkdir $folder/$coord/opal
    mkdir $folder/$coord/mergeOPAL
    mkdir $folder/$coord/wala
    mkdir $folder/$coord/mergeWALA
    echo $i"- analyzing "$coord": "
#    ((j=j%4)); ((j++==0)) && wait
    java -Xmx12000m -Xms12000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --wholeProgram "$line" $folder/$coord/opal > $folder/$coord/opal/log 2>&1 &
    java -Xmx12000m -Xms12000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --wholeProgram "$line" $folder/$coord/wala > $folder/$coord/wala/log 2>&1 &
    java -Xmx12000m -Xms12000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --merge "$line" $folder/$coord/mergeOPAL > $folder/$coord/mergeOPAL/log 2>&1 &
    java -Xmx12000m -Xms12000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --merge "$line" $folder/$coord/mergeWALA > $folder/$coord/mergeWALA/log 2>&1 &
  fi
  ((i = i + 1))
done <"$1"

#How to run => $1: input file, $2: output folder $3: algorithm
#bash Runner_step1.sh results/inputMvnData/highly.connected10.resolved.csv results/outputStats/cgs


