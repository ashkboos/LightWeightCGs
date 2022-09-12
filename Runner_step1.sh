#!/bin/bash
i=0
folder="$2"
mkdir $folder
while IFS= read -r line; do
  if [ $i -ne 0 ]; then
    coord="$(echo $line | cut -d',' -f2)"
    mkdir $folder/$coord
    mkdir $folder/$coord/opal
    mkdir $folder/$coord/merge
    mkdir $folder/$coord/wala
    echo -n $i"- Generating graphs for "$coord" package: "
    echo -n " opal ..."
    java -Xmx7000m -Xms7000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --opal "$line" $folder/$coord/opal > $folder/$coord/opal/log 2>&1
    echo -n ", wala ..."
    java -Xmx7000m -Xms7000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --wala "$line"  $folder/$coord/wala > $folder/$coord/wala/log 2>&1
    echo ", merge ..."
    java -Xmx7000m -Xms7000m -jar runnable/LightWeightCGs-1.0-SNAPSHOT-with-dependencies.jar --merge "$line" $folder/$coord/merge > $folder/$coord/merge/log 2>&1
  fi
  ((i = i + 1))
done <"$1"

#How to run => $1: input file, $2: output folder $3: algorithm
#bash Runner_step1.sh results/inputMvnData/highly.connected10.resolved.csv results/outputStats/cgs
