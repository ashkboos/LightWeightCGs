/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util;

import static eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm.CHA;
import static util.CSVUtils.buildDataCSVofResolvedCoords;
import static util.FilesUtils.JAVA_8_COORD;

import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.merge.CallGraphUtils;
import evaluation.LegacyCGEvaluator;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelperScripts {
    private static final Logger logger = LoggerFactory.getLogger(HelperScripts.class);

    public static void main(String[] args) {
        switch (args[0]) {
            case "--countPackages":
                // Prints how many unique and duplicate coordinates are in the resolved file
                // input[1]: resolved csv file
                countPackages(CSVUtils.readResolvedCSV(args[1]));
                break;
            case "--distinctDepsets":
                // Prints duplicate records in two given resolved csvs
                // args[1]: first resolved data csv
                // args[2]: second resolved data csv
                printDuplicates(CSVUtils.readResolvedCSV(args[1]),
                    CSVUtils.readResolvedCSV(args[2]));
                break;
            case "--removeParentpkcs":
                // It removes the packages with only one dependency
                // args[1]: resolved csv file full path
                // args[2]: result file full path
                removeRowsWithOnlyOneDepAndWriteIt(args[1], args[2]);
                break;
            case "--oomCounter":
                // To calculate how many output files the experiments generated. This can be an
                // indication of memory errors. However, the proper solution is to run
                // experiments and output the logs of stdout and stderr instead of this script
                // args[1]: full path to the root directory of the experiment
                // args[2]: full path to the output file to write the results in
                countNoOutputProjects(args[1], args[2]);
                break;
            case "--compareCGPoolAndInput":
                // To calculate how many coordinates are failed in the partial generation of the
                // experiment this writes the coordinates that are present
                // in the resolved input file but not in the cg pool csv
                // args[1]: full path to the cg pool csv file
                // args[2]: full path to the input coordinates file
                // args[3]: full path to the output file to write the results in
                compareCGPoolAndInput(args[1], args[2], args[3]);
                break;
            case "--compare":
                LegacyCGEvaluator.measureAll(CSVUtils.readResolvedCSV(args[1]), args[2],
                    Integer.parseInt(args[3]));
                break;
            // "src/main/resources/wala-rt-jar.json"
            // "src/main/resources/opal-rt-jar.json"
            case "--generateRTJAR":
                generateCGForGeneratorAndStore(args[1], args[2]);
                break;
        }

    }

    private static void compareCGPoolAndInput(final String poolPath,
                                              final String inputPath,
                                              final String outputPath) {
        final var cgPool = CSVUtils.readCSV(poolPath);
        final var input = CSVUtils.readResolvedCSV(inputPath);
        Set<MavenCoordinate> poolCoords = new HashSet<>();
        if (!cgPool.isEmpty()) {
            for (final var cg : cgPool) {
                poolCoords.add(MavenCoordinate.fromString(cg.get("coordinate"), "jar"));
            }
        }
        final Set<MavenCoordinate> unsuccessfuls = new HashSet<>();
        for (final var row : input.entrySet()) {
            for (MavenCoordinate inputCoord : row.getValue()) {
                if (!poolCoords.contains(inputCoord)) {
                    unsuccessfuls.add(inputCoord);
                }
            }
        }
        CSVUtils.writeToCSV(buildUnsuccessfulCSV(unsuccessfuls), outputPath);
    }


    private static List<String[]> buildUnsuccessfulCSV(
        final Set<MavenCoordinate> unsucessfulls) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {"number", "coordinate"});
        int counter = 0;
        for (final var row : unsucessfulls) {
            dataLines.add(new String[] {
                /* number */ String.valueOf(counter),
                /* folder */ row.getCoordinate()
            });
            counter++;
        }
        return dataLines;
    }

    private static void countNoOutputProjects(final String rootPath,
                                              final String outPath) {
        Map<String, List<Boolean>> result = new HashMap<>();
        for (File pckg : Objects.requireNonNull(new File(rootPath).listFiles())) {
            final var opal = FilesUtils.getFile(pckg, "opal")[0];
            final var merge = FilesUtils.getFile(pckg, "merge")[0];
            final var opalPath = opal.getAbsolutePath();
            final var opalExists = new File(opalPath + "/resultOpal.csv").exists();
            final var opalCgExists = new File(opalPath + "/cg.json").exists();
            final var mergePath = merge.getAbsolutePath();
            final var mergeExists = new File(mergePath + "/Merge.csv").exists();
            final var CGPoolExists = new File(mergePath + "/CGPool.csv").exists();
            final var mergeCgExists = new File(mergePath + "/cg.json").exists();

            result.put(pckg.getName(), List.of(opalExists, opalCgExists, mergeExists,
                CGPoolExists, mergeCgExists));
        }
        CSVUtils.writeToCSV(buildCSV(result), outPath);
    }


    private static List<String[]> buildCSV(final Map<String, List<Boolean>> resolvedData) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {"number", "folder", "opalOutput", "opalCG", "mergeOutput",
            "CGPool", "mergeCG"});
        int counter = 0;
        for (final var row : resolvedData.entrySet()) {
            dataLines.add(new String[] {
                /* number */ String.valueOf(counter),
                /* folder */ row.getKey(),
                /* opalOutput */ row.getValue().get(0) ? "1" : "0",
                /* opalCG */ row.getValue().get(1) ? "1" : "0",
                /* mergeOutput */ row.getValue().get(2) ? "1" : "0",
                /* CGPool */ row.getValue().get(3) ? "1" : "0",
                /* mergeCG */ row.getValue().get(4) ? "1" : "0"
            });
            counter++;
        }
        return dataLines;
    }

    private static void removeRowsWithOnlyOneDepAndWriteIt(final String inputPath,
                                                           final String resultPath) {
        final var data = CSVUtils.readResolvedCSV(inputPath);
        final var multiDeps = removeOnlyOneDepRecords(data);
        CSVUtils.writeToCSV(buildDataCSVofResolvedCoords(multiDeps), resultPath);
    }

    private static void printDuplicates(
        final Map<MavenCoordinate, List<MavenCoordinate>> data1,
        final Map<MavenCoordinate, List<MavenCoordinate>> data2) {
        System.out.println("data1 size: " + data1.size());
        System.out.println("data2 size: " + data2.size());
        for (MavenCoordinate mavenCoordinate : data1.keySet()) {
            if (data2.containsKey(mavenCoordinate)) {
                System.out.println("duplicate coordinate: " + mavenCoordinate.getCoordinate());
            }
            data2.put(mavenCoordinate, data1.get(mavenCoordinate));
        }
        System.out.println("overall size : " + data2.size());
    }

    private static void countPackages(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final var uniquesCommons = countCommonCoordinates(resolvedData);
        System.out.printf("#%s redundant packages and #%s unique packages, #%s all" +
            " packages%n", uniquesCommons.get(1), uniquesCommons.get(0), uniquesCommons.get(2));
    }


    public static List<Integer> countCommonCoordinates(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final HashSet<MavenCoordinate> uniqueCoords = new HashSet<>();
        int all = 0;
        int counter = 0;
        for (final var coordList : resolvedData.values()) {
            for (final var coord : coordList) {
                if (uniqueCoords.contains(coord)) {
                    counter++;
                } else {
                    uniqueCoords.add(coord);
                }
                all++;
            }
        }
        return new ArrayList<>(List.of(uniqueCoords.size(), counter, all));
    }

    public static Map<MavenCoordinate, List<MavenCoordinate>> removeOnlyOneDepRecords(
        final Map<MavenCoordinate, List<MavenCoordinate>> data) {
        Map<MavenCoordinate, List<MavenCoordinate>> result = new HashMap<>();
        logger.info("Original data size is: {}", data.size());
        for (final var row : data.entrySet()) {
            if (row.getValue().size() > 1) {
                result.put(row.getKey(), row.getValue());
            }
        }
        logger.info("After one dep removal data size is: {}", result.size());
        return result;
    }

    private static void generateCGForGeneratorAndStore(final String generator, final String path) {

        final var pcg = CGUtils.generatePCG(new File[] {new FilesUtils().getRTJar()},
            JAVA_8_COORD, CHA.label, CallPreservationStrategy.ONLY_STATIC_CALLSITES, generator);

        final var jsonCG = JSONUtils.toJSONString(pcg);

        try {
            CallGraphUtils.writeToFile(path, jsonCG);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
