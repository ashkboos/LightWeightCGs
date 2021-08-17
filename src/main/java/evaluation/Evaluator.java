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

package evaluation;

import static evaluation.StitchingEdgeTest.compareMergeOPAL;
import static evaluation.StitchingEdgeTest.groupBySource;

import ch.qos.logback.classic.Level;

import eu.fasten.analyzer.javacgopal.Main;

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.opal.MavenCoordinate;

import eu.fasten.core.data.utils.DirectedGraphDeserializer;
import eu.fasten.core.data.utils.DirectedGraphSerializer;
import eu.fasten.core.merge.CallGraphUtils;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;


import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.merge.CGMerger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jooq.tools.csv.CSVReader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Evaluator {

    private static final int warmUp = 10;
    private static final int iterations = 10;

    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);

    public static void main(String[] args)
        throws IOException, NoSuchFieldException, IllegalAccessException {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
            .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);

        System.setProperty("org.jline.terminal.dumb", "true");
        switch (args[0]) {
            case "--oneTest":
                generateSingleFeature(new File(args[1]), args[2]);
                break;
            case "--allTests":
                generateAllFeatures(new File(args[1]), args[2]);
                break;
            case "--compare":
                measureAll(readResolvedCSV(args[1]), args[2], Integer.parseInt(args[3]), args[4]);
                break;
            case "--resolve":
                resolveDependencies(args[1], args[2]);
                break;
            case "--opal":
                writeOpalToFolder(args[1], args[2], args[3]);
                break;
            case "--merge":
                writeMergeToFolder(args[1], args[2], args[3]);
                break;
            case "--fromFiles":
                analyzeDir(args[1], args[2]);
                break;
            case "--splitInput":
                splitInput(args[1], args[2], args[3]);
                break;
            case "--inputDemography":
                inputDemography(args[1], args[2]);
                break;
        }
    }

    private static void resolveDependencies(String input, String output) throws IOException {
        final var resolvedData = resolveAll(dropTheHeader(readCSVColumn(input, 0)));
        StatCounter.writeToCSV(buildDataCSV(resolvedData), output);
        logger.info("Wrote resolved data into file successfully!");
    }

    private static void splitInput(final String inputPath, final String chunkNum,
                                   final String resultPath) throws IOException {
        final var data = readResolvedCSV(inputPath);
        final var multiDeps = removeOnlyOnedeps(data);
        final var splitted = splitToChunks(multiDeps, chunkNum);
        for (int i = 0; i < splitted.size(); i++) {
            final var part = splitted.get(i);
            StatCounter.writeToCSV(buildDataCSV(part), resultPath + "/chunk.p" + i + ".csv");
        }
        logger.info("Wrote resolved data into file successfully!");
    }

    public static void inputDemography(final String inputPath, final String outputPath)
        throws IOException {
        final var data = readResolvedCSV(inputPath);
        Map<String, List<Integer>> result = new HashMap<>();
        int counter = 0;
        for (final var entry : data.entrySet()) {
            int fileCounter = 0;
            int withoutDeps = 0;
            List<MavenCoordinate> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                MavenCoordinate coord = value.get(i);
                try {
                    final var file = new MavenCoordinate.MavenResolver().downloadArtifact(coord, "jar");
                    ZipInputStream is =
                        new ZipInputStream(new FileInputStream(file.getAbsolutePath()));
                    ZipEntry ze;
                    while ((ze = is.getNextEntry()) != null) {
                        if (!ze.isDirectory()) {
                            fileCounter++;
                        }
                    }
                    if (i==0) {
                        withoutDeps = fileCounter;
                    }
                    is.close();
                    file.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println("\n\n ################################ Coord number : "+ counter);
            counter ++;
            result.put(entry.getKey().getCoordinate(), Arrays.asList(entry.getValue().size(),
                withoutDeps, fileCounter));
        }
        StatCounter.writeToCSV(InputDemography.buildCSV(result), outputPath);
    }

    private static List<Map<MavenCoordinate, List<MavenCoordinate>>> splitToChunks(Map<MavenCoordinate, List<MavenCoordinate>> multiDeps, String chunkNum) {
        List<Map<MavenCoordinate, List<MavenCoordinate>>> result = new ArrayList<>();
        final var chunkNumInt = Integer.parseInt(chunkNum);
        final var chunkSize = multiDeps.size()/chunkNumInt;
        int counter = 0;
        Map<MavenCoordinate, List<MavenCoordinate>> chunk = new HashMap<>();
        for (final var entry : multiDeps.entrySet()) {
            chunk.put(entry.getKey(), entry.getValue());
            counter ++;
            if (counter%chunkSize == 0 && (multiDeps.size() - counter) > chunkNumInt) {
                result.add(chunk);
                chunk = new HashMap<>();
            }else if (multiDeps.size() == counter){
                result.add(chunk);
            }
        }
        return result;
    }

    private static Map<MavenCoordinate, List<MavenCoordinate>> removeOnlyOnedeps(Map<MavenCoordinate,
        List<MavenCoordinate>> data) {
        Map<MavenCoordinate, List<MavenCoordinate>> result = new HashMap<>();
        logger.info("Original data size is: {}", data.size());
        for (final var row : data.entrySet()) {
            if (row.getValue().size()>1) {
                result.put(row.getKey(), row.getValue());
            }
        }
        logger.info("After one dep removal data size is: {}", result.size());
        return result;
    }

    private static void analyzeDir(final String rootPath, final String outPath)
        throws IOException, NoSuchFieldException, IllegalAccessException {
        logger.info("Start analyzing directory...");
        final var statCounter = new StatCounter();
        final Map<MavenCoordinate, List<MavenCoordinate>> depTree = new HashMap<>();
        final int[] counter = {0};
        Arrays.stream(Objects.requireNonNull(new File(rootPath).listFiles())).parallel().forEach(pckg -> {
            final var opal = getFile(pckg, "opal")[0];
            final var merge = getFile(pckg, "merge")[0];
            Pair<DirectedGraph, Map<Long, String>> opalCG = null;
            try {
                opalCG = getOpalCG(opal);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            final var depEntry = updateStatCounter(merge, opal, statCounter);
            if (depEntry != null) {
                depTree.put(depEntry.left(), depEntry.right());
            }
            Pair<DirectedGraph, Map<Long, String>> mergedCG = null;
            if (opalCG != null) {
                try {
                    mergedCG = getMergedCGs(merge);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            logger.info("opal and merge are in memory!");
            MavenCoordinate coord;
            if(mergedCG != null) {
                if (depEntry != null) {
                    coord = depEntry.left();
                }else {
                    coord = MavenCoordinate.fromString("", "jar");
                }
                statCounter.addAccuracy(coord,
                    calcPrecisionRecall(groupBySource(compareMergeOPAL(mergedCG, opalCG))));
            }
            System.gc();
            logger.info("pckg number :{}", counter[0]);
            counter[0]++;
        });
        statCounter.concludeMerge(outPath);
        statCounter.concludeOpal(depTree, outPath);
        statCounter.concludeLogs(outPath);
        statCounter.concludeAll(depTree, outPath);
        System.gc();

    }

    private static Pair<MavenCoordinate, List<MavenCoordinate>> updateStatCounter(final File merge,
                                                                               final File opal,
                                                                                  final StatCounter statCounter) {

        final var resultOpal = getCSV(opal.getAbsolutePath()+"/resultOpal.csv");
        final var opalLog = getFile(opal, "log");
        final var cgPool = getCSV(merge.getAbsolutePath()+ "/CGPool.csv");
        final var resultMerge = getCSV(merge.getAbsolutePath()+"/Merge.csv");
        final var mergeLog = getFile(merge, "log");
        Pair<MavenCoordinate, List<MavenCoordinate>> depEntry = null;
        if (resultOpal.isPresent()) {
            if (!resultOpal.get().isEmpty()) {
                depEntry = addOpalToStatCounter(resultOpal.get().get(0), statCounter);
            }
        }
        if (resultMerge.isPresent()) {
            if (!resultMerge.get().isEmpty()) {
                if (depEntry == null) {
                    depEntry = addMergeToStatCounter(resultMerge.get(), statCounter);
                } else {
                    final Set<MavenCoordinate> temp = new HashSet<>(depEntry.right());
                    final var mergeDeps = addMergeToStatCounter(resultMerge.get(), statCounter);
                    temp.addAll(mergeDeps.right());
                    depEntry = Pair.of(mergeDeps.left(), new ArrayList<>(temp));
                }
            }
        }
        if (cgPool.isPresent()) {
            if (!cgPool.get().isEmpty()) {
                addCGPoolToStatCounter(cgPool.get(), statCounter);
            }
        }
        statCounter.addLog(opalLog, mergeLog, opal.getPath());
        return depEntry;
    }


    private static void addCGPoolToStatCounter(final List<Map<String, String>> cgPool,
                                               StatCounter statCounter) {
        for (final var cg : cgPool) {
            statCounter.addNewCGtoPool(MavenCoordinate.fromString(cg.get("coordinate"), "jar"),
                Long.parseLong(cg.get("isolatedRevisionTime")),
                    new StatCounter.GraphStats(Integer.parseInt(cg.get("nodes")),
                        Integer.parseInt(cg.get("edges"))));
        }
    }

    private static Pair<MavenCoordinate, List<MavenCoordinate>> addMergeToStatCounter(final List<Map<String, String>> resultMerge,
                                              StatCounter statCounter) {
        MavenCoordinate coord = null;
        Set<MavenCoordinate> depSet = new HashSet<>();
        for (final var merge : resultMerge) {
            List<MavenCoordinate> deps = new ArrayList<>();
            for (final var dep : merge.get("dependencies").split(";")) {
                deps.add(MavenCoordinate.fromString(dep, "jar"));
            }
            depSet.addAll(deps);
            coord = MavenCoordinate.fromString(merge.get("rootCoordinate"), "jar");
            statCounter.addMerge(coord,
                MavenCoordinate.fromString(merge.get("artifact"), "jar"),
                deps, Long.parseLong(merge.get("mergeTime")),
                new StatCounter.GraphStats(Integer.parseInt(merge.get("nodes")),
                    Integer.parseInt(merge.get("edges"))));

            statCounter.addUCH(coord, Long.parseLong(merge.get("uchTime")));
        }
        return Pair.of(coord, new ArrayList<>(depSet));
    }

    private static Pair<MavenCoordinate, List<MavenCoordinate>> addOpalToStatCounter(final Map<String, String> resultOpal,
                                                                                    StatCounter statCounter) {
        final var coord = MavenCoordinate.fromString(resultOpal.get("coordinate"), "jar");
            final var opalStats = new StatCounter.OpalStats(Long.parseLong(resultOpal.get("opalTime")),
                new StatCounter.GraphStats(Integer.parseInt(resultOpal.get("nodes")),
                    Integer.parseInt(resultOpal.get("edges"))));

            statCounter.addOPAL(coord, opalStats);
        List<MavenCoordinate> deps = new ArrayList<>();
        for (final var dep : resultOpal.get("dependencies").split(";")) {
            deps.add(MavenCoordinate.fromString(dep, "jar"));
        }
        return Pair.of(coord, deps);
    }

    private static Optional<List<Map<String, String>>> getCSV(final String inputPath) {
        final List<Map<String, String>> result = new ArrayList<>();
            if (new File(inputPath).exists()) {
                try (var csvReader = new CSVReader(new FileReader(inputPath), ',', '\'')) {
                    String[] values;
                    boolean firstRow = true;
                    final List<String> header = new ArrayList<>();
                    while ((values = csvReader.readNext()) != null) {
                        Map<String, String> row = new HashMap<>();
                        for (int i = 0; i < values.length; i++) {
                            String value = values[i];
                            if (firstRow) {
                                header.add(value);
                            } else {
                                row.put(header.get(i), value);
                            }
                        }
                        if (!firstRow) {
                            result.add(row);
                        }
                        firstRow = false;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return Optional.of(result);
            }else {
                return Optional.empty();
            }
    }


    private static Pair<DirectedGraph, Map<Long, String>> getOpalCG(final File opalDir) throws FileNotFoundException {
        final var opalFile = getFile(opalDir, "cg.json");
        if (opalFile != null && opalFile.length!=0) {
            return getRCG(opalFile[0]);
        }
        return null;
    }

    private static File[] getFile(final File opalDir, final String fileName) {
        return opalDir.listFiles((dir, name) -> name.toLowerCase().equals(
            fileName));
    }

    private static Pair<DirectedGraph, Map<Long, String>> getMergedCGs(final File mergeFiles) throws FileNotFoundException {
        final var files = mergeFiles.listFiles((dir, name) -> name.toLowerCase().endsWith(
            "json"));
        if (files != null) {
            return getRCG(files[0]);
        }
        return null;
    }

    private static Pair<DirectedGraph, Map<Long, String>> getRCG(final File serializedCGFile)
        throws FileNotFoundException {
        final JSONTokener tokener = new JSONTokener(new FileReader(serializedCGFile));
        final var deserializer = new DirectedGraphDeserializer();
        return deserializer.jsonToGraph(new JSONObject(tokener).toString());
    }

    private static void writeMergeToFolder(final String row, final String path,
                                           final String algorithm) throws IOException {
        final var statCounter = new StatCounter();
        final var coords = getCoordinates(row);
        final var rcg = mergeRecord(Map.of(coords.getKey(), coords.getValue()), statCounter,
            new HashMap<>(), coords.getKey(), algorithm);
            final var ser = new DirectedGraphSerializer();
            CallGraphUtils.writeToFile(path, ser.graphToJson(rcg.first(), rcg.second()),
                "/cg.json");
        statCounter.concludeMerge(path);
    }

    private static void writeOpalToFolder(final String row, final String path,
                                          final String algorithm) throws IOException {
        final var statCounter = new StatCounter();
        final var coords = getCoordinates(row);
        final var rcg = generateForOPAL(statCounter, coords, algorithm);
        final var ser = new DirectedGraphSerializer();
        if (rcg != null) {
            CallGraphUtils.writeToFile(path+"/cg.json", ser.graphToJson(rcg.first(), rcg.second()), "");
        }
        statCounter.concludeOpal(Map.of(coords.getKey(), coords.getValue()), path);
    }

    private static Map.Entry<MavenCoordinate, List<MavenCoordinate>> getCoordinates(String row) {
        final var splitedRow = row.split(",");
        List<MavenCoordinate> deps = new ArrayList<>();
        for (String coord : splitedRow[2].split(";")) {
            deps.add(MavenCoordinate.fromString(coord,"jar"));
        }
        return Map.entry(MavenCoordinate.fromString(splitedRow[1], "jar"), deps);
    }

    public static void generateAllFeatures(final File testCasesDirectory, final String algorithm) throws IOException {
        final var splitJars = testCasesDirectory.listFiles(f -> f.getPath().endsWith("_split"));
        var counter = 0;
        assert splitJars != null;
        final var tot = splitJars.length;
        int singleClass = 0;
        for (final var langFeature : splitJars) {
            new File(langFeature.getAbsolutePath() + "/cg").mkdir();
            counter += 1;
            logger.info("\n Processing {} -> {}", langFeature.getName(), counter + "/" + tot);
            final String main = extractMain(langFeature);
            generateOpal(langFeature, main, algorithm, "cg/opalV3");

            if (!merge(langFeature, main, algorithm, algorithm, "cg/mergeV3")) {
                singleClass++;
            }
        }
        logger
            .info("There was #{} single class language features we couldn't merge! ", singleClass);
    }

    public static void generateSingleFeature(final File testCaseDirectory,
                                             final String algorithm) throws IOException {
        final String main = extractMain(testCaseDirectory);
        generateOpal(testCaseDirectory, main, algorithm, "cg/opalV3");
        merge(testCaseDirectory, main, algorithm, algorithm, "cg/mergeV3");
    }

    private static void measureAll(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final String outPath,
        final int threshold, final String algorithm)
        throws IOException, NoSuchFieldException, IllegalAccessException {

        final var statCounter = new StatCounter();

        final var uniquesCommons = countCommonCoordinates(resolvedData);
        logger.info("#{} redundant packages and #{} unique packages", uniquesCommons.right(),
            uniquesCommons.left());

        if (uniquesCommons.right() < threshold) {
            logger.info("Number of redundant packages is less than threshold #{}", threshold);

        } else {
            runOPALandMerge(resolvedData, statCounter, algorithm);

            statCounter.concludeMerge(outPath);
            statCounter.concludeOpal(resolvedData, outPath);
            statCounter.concludeAll(resolvedData, outPath);
            logger.info("Wrote results of evaluation into file successfully!");
        }
    }

    private static void runOPALandMerge(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                                        final StatCounter statCounter, final String algorithm) {
        final Map<MavenCoordinate, Set<MavenCoordinate>> remainedDependents = getDependents(resolvedData);
        final Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> cgPool = new HashMap<>();
        ProgressBar pb = new ProgressBar("Measuring stats", resolvedData.entrySet().size());
        pb.start();

        for (final var row : resolvedData.entrySet()) {
            final var toMerge = row.getKey();
            final Pair<DirectedGraph, Map<Long, String>> merge =
                mergeRecord(resolvedData, statCounter, cgPool, toMerge, algorithm);
            pb.step();
            for (final var dep : resolvedData.get(toMerge)) {
                if ( remainedDependents.get(dep) != null) {
                    if (remainedDependents.get(dep).isEmpty()) {
                        remainedDependents.get(dep).remove(toMerge);
                        cgPool.remove(dep);
                        System.gc();
                    }
                }
            }
            final var opal = generateForOPAL(statCounter, row, algorithm);
            if(opal!= null && merge != null ) {
                statCounter.addAccuracy(toMerge,
                    calcPrecisionRecall(groupBySource(compareMergeOPAL(merge, opal))));
            }
            System.gc();
        }
        pb.stop();

    }

    private static Pair<DirectedGraph, Map<Long, String>> mergeRecord(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData, StatCounter statCounter,
        final Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> cgPool,
        final MavenCoordinate toMerge, final String algorithm) {
        for (final var dep : resolvedData.get(toMerge)) {
            if (!cgPool.containsKey(dep)) {
                addToCGPool(statCounter, cgPool, dep, algorithm);
            }
        }

        logger.info("artifact: {}, dependencies: {}", toMerge.getCoordinate(),
            resolvedData.get(toMerge).stream().map(MavenCoordinate::getCoordinate).collect(
                Collectors.toList()));

        return mergeArtifact(cgPool, statCounter, resolvedData.get(toMerge), toMerge);
    }

    private static List<StatCounter.SourceStats> calcPrecisionRecall(final Map<String, Map<String,
        List<String>>> edges){
        final var opal = edges.get("opal");
        final var merge = edges.get("merge");
        final var allSources = new HashSet<String>();
        allSources.addAll(opal.keySet());
        allSources.addAll(merge.keySet());
        final List<StatCounter.SourceStats> result = new ArrayList<>();
        for (final var source : allSources) {

            final var opalTargets = new HashSet<>(opal.getOrDefault(source,
                new ArrayList<>()));
            final var mergeTargets = new HashSet<>(merge.getOrDefault(source, new ArrayList<>()));
            final var intersect = intersect(opalTargets, mergeTargets);
            final var mergeSize = mergeTargets.size();
            final var opalSize = opalTargets.size();
            final var intersectSize = intersect.size();

            final var precision = mergeSize == 0 ? 1 : (double) intersectSize/mergeSize;
            final var recall = opalSize == 0 ? 1 : (double) intersectSize/opalSize;

            result.add(new StatCounter.SourceStats(source, precision, recall, opalSize, mergeSize
                ,intersectSize));
        }
        return result;
    }

    private static Map<String, Map<String, List<String>>> removeVersions(final Map<String,
        Map<String, List<String>>> edges) {
        for (var targets : edges.get("merge").entrySet()) {
            final List<String> removedVersion = new ArrayList<>();
            for (String target : targets.getValue()) {
                removedVersion.add(target.replace(target.split("/")[2],"").replace("//",""));
            }
            targets.setValue(removedVersion);
        }
        return edges;
    }

    private static Set<String> intersect(final Set<String> first,
                                         final Set<String> second) {
        final var temp1 = new ArrayList<>(first);
        final var temp2 = new ArrayList<>(second);
        return temp1.stream()
            .distinct()
            .filter(temp2::contains)
            .collect(Collectors.toSet());
    }

    private static Map<MavenCoordinate, Set<MavenCoordinate>> getDependents(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final Map<MavenCoordinate, Set<MavenCoordinate>> result = new HashMap<>();
        for (final var entry : resolvedData.entrySet()) {
            for (final var dep : entry.getValue()) {
                final var dependents = result.getOrDefault(dep, new HashSet<>());
                dependents.add(MavenCoordinate.fromString(entry.getKey().getCoordinate(), "jar"));
                result.put(MavenCoordinate.fromString(dep.getCoordinate(), "jar"), dependents);
            }
        }
        return result;
    }

    private static List<String[]> buildDataCSV(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {"number", "coordinate", "dependencies"});
        int counter = 0;
        for (final var coorDeps : resolvedData.entrySet()) {
            final var coord = coorDeps.getKey();
            dataLines.add(new String[] {
                /* number */ String.valueOf(counter),
                /* coordinate */ coord.getCoordinate(),
                /* dependencies */ StatCounter.toString(coorDeps.getValue())});
            counter++;
        }
        return dataLines;
    }

    private static Pair<Integer, Integer> countCommonCoordinates(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final HashSet<MavenCoordinate> uniqueCoords = new HashSet<>();
        int counter = 0;
        for (final var coordList : resolvedData.values()) {
            for (final var coord : coordList) {
                if (uniqueCoords.contains(coord)) {
                    counter++;
                } else {
                    uniqueCoords.add(coord);
                }
            }
        }
        return IntIntImmutablePair.of(uniqueCoords.size(), counter);
    }

    public static List<String> dropTheHeader(final List<String> csv) {
        csv.remove(0);
        return csv;
    }

    public static List<String> readCSVColumn(final String inputPath,
                                              final int columnNum) throws IOException {
        List<String> result = new ArrayList<>();
        try (var csvReader = new CSVReader(new FileReader(inputPath))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                result.add(values[columnNum]);
            }
        }
        return result;
    }

    public static Map<MavenCoordinate, List<MavenCoordinate>> resolveAll(
        final List<String> dataSet) {

        ProgressBar pb = new ProgressBar("Resolving", dataSet.size());
        pb.start();

        final var result = new HashMap<MavenCoordinate, List<MavenCoordinate>>();
        for (final var coord1 : dataSet) {
            final var deps1 = resolve(coord1);
            if (deps1.isPresent()) {
//                if(!hasScala(deps1)) {
//                    if(deps1.get().size() >1){
                    deps1.ifPresent(mavenCoordinates -> result
                        .put(MavenCoordinate
                                .fromString(coord1,
                                    mavenCoordinates.get(0).getPackaging().getClassifier()),
                            convertToFastenCoordinates(mavenCoordinates)));
//                    }
//                }
            }

//            if (result.size() == 100) {
//                break;
//            }
            pb.step();
        }
        pb.stop();
        return result;
    }

    private static boolean hasScala(
        Optional<List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate>> deps) {

        for (final var coord : deps.get()) {
            if (coord.getArtifactId().contains("scala") || coord.getGroupId().contains("scala")) {
                return true;
            }
        }
        return false;
    }


    private static Pair<DirectedGraph, Map<Long, String>> mergeArtifact(final Map<MavenCoordinate,
        ExtendedRevisionJavaCallGraph> cgPool,
                                      final StatCounter statCounter,
                                      final List<MavenCoordinate> deps,
                                      final MavenCoordinate artifact) {
        Pair<DirectedGraph, Map<Long, String>> result  = null;
        final var rcgs = deps.stream().map(cgPool::get)
            .filter(Objects::nonNull) // get rid of null values
            .collect(Collectors.toList());
        final long startTimeUch = System.currentTimeMillis();
        final var cgMerger =  new CGMerger(rcgs);
        statCounter.addUCH(artifact,System.currentTimeMillis() - startTimeUch);

            if (cgPool.get(artifact) != null) {
                logger.info("\n ###############\n Merging {}:", artifact.getCoordinate());
                DirectedGraph rcg = null;
                final var times = new ArrayList<Long>();
                for (int i = 0; i < warmUp + iterations ; i++) {
                    if (i > warmUp) {
                        final long startTime = System.currentTimeMillis();
                        rcg = cgMerger.mergeAllDeps();
                        times.add(System.currentTimeMillis() - startTime);
                    }
                }

                statCounter.addMerge(artifact, artifact, deps,
                        (long) times.stream().mapToDouble(a -> a).average().getAsDouble(),
                        new StatCounter.GraphStats(rcg));

                result = Pair.of(rcg, cgMerger.getAllUris());
            }else {
                statCounter
                    .addMerge(artifact, artifact, deps,
                        0, new StatCounter.GraphStats());
            }
        return result;
    }

    private static Optional<List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate>> resolve(
        final String row) {
        try {
            return Optional.of(Maven.resolver().resolve(row).withTransitivity()
                .asList(org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate.class));
        } catch (Exception e) {
            logger.warn("Exception occurred while resolving {}, {}", row, e);
            return Optional.empty();
        }
    }

    private static List<MavenCoordinate> skipRevision(
        final MavenCoordinate revision,
        final List<MavenCoordinate> revisions) {

        final List<MavenCoordinate> coords = new ArrayList<>();

        for (final var coord : revisions) {
            if (!revision.equals(coord)) {
                coords.add(coord);
            }
        }
        return coords;
    }

    private static List<MavenCoordinate> convertToFastenCoordinates(
        final List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> revisions) {
        return revisions.stream().map(Evaluator::convertToFastenCoordinate)
            .collect(Collectors.toList());
    }


    private static Pair<DirectedGraph, Map<Long, String>> generateForOPAL(StatCounter statCounter,
                                        Map.Entry<MavenCoordinate, List<MavenCoordinate>> row,
                                                                          String algorithm) {
        ExtendedRevisionJavaCallGraph rcg = null;
        DirectedGraph dcg = new MergedDirectedGraph();

        try {
                final var tempDir = downloadToDir(row.getValue());
                logger.info("\n##################### \n Deps of {} downloaded to {}, Opal is " +
                        "generating ...", row.getValue().get(0).getCoordinate(),
                    tempDir.getAbsolutePath());
            CallGraphConstructor opalCg = null;

            final var times = new ArrayList<Long>();
            for (int i = 0; i < warmUp + iterations ; i++) {
                if (i > warmUp) {
                    final long startTime = System.currentTimeMillis();
                    opalCg = new CallGraphConstructor(tempDir, "", algorithm);
                    times.add(System.currentTimeMillis() - startTime);
                }
            }

                final var cg = new PartialCallGraph(opalCg, false);
                rcg = ExtendedRevisionJavaCallGraph.extendedBuilder()
                    .graph(cg.getGraph())
                    .classHierarchy(cg.getClassHierarchy())
                    .nodeCount(cg.getNodeCount())
                    .build();

            FileUtils.deleteDirectory(tempDir);
            final var externals = rcg.externalNodeIdToTypeMap();
            for (final var intInt  : rcg.getGraph().getCallSites().entrySet()) {
                if (!externals.containsKey(intInt.getKey().firstInt())
                && !externals.containsKey(intInt.getKey().secondInt())) {
                    final var source = (long) intInt.getKey().firstInt();
                    final var target = (long) intInt.getKey().secondInt();
                    dcg.addVertex(source);
                    dcg.addVertex(target);
                    dcg.addEdge(source, target);
                }
            }
                statCounter.addOPAL(row.getKey(),
                    (long) times.stream().mapToDouble(a -> a).average().getAsDouble(), dcg);

            System.gc();
        } catch (Exception e) {
            logger.warn("Exception occurred found!", e);
            statCounter.addOPAL(row.getKey(), new StatCounter.OpalStats(0l,
                new StatCounter.GraphStats()));
        }
        Map<Long, String> map = new HashMap<>();
        if (rcg != null) {
            map = rcg.mapOfFullURIStrings().entrySet().stream()
                .collect(Collectors.toMap(e -> Long.valueOf(e.getKey()), Map.Entry::getValue));
            return Pair.of(dcg, map);
        }else {
            return null;
        }

    }

    public static File downloadToDir(final List<MavenCoordinate> mavenCoordinates)
        throws IOException {

        final var resultDir = Files.createTempDirectory("fasten").toAbsolutePath();

        for (final var coord : mavenCoordinates) {
            File coordinateFile = new File("");
            try {
                coordinateFile = new MavenCoordinate.MavenResolver().downloadArtifact(coord, "jar");
            } catch (Exception e) {
                logger.warn("File not found!");
            }
            Files.copy(coordinateFile.toPath(),
                Paths.get(resultDir + "/" + coord.getCoordinate() + ".jar"),
                StandardCopyOption.REPLACE_EXISTING);
        }
        return resultDir.toFile();
    }

    private static Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> buildUpCGPool(
        final Map<MavenCoordinate, List<MavenCoordinate>> dataSet, final StatCounter statCounter,
        final Integer uniqueCoords, final String algorithm) {

        ProgressBar pb = new ProgressBar("Creating CGPool", uniqueCoords);
        pb.start();

        Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> result = new HashMap<>();
        for (final var row : dataSet.entrySet()) {
            for (final var coord : row.getValue()) {
                addToCGPool(statCounter, result, coord, algorithm);
                pb.step();
            }

        }
        pb.stop();
        return result;
    }

    private static void addToCGPool(final StatCounter statCounter,
                                    final Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> cgPool,
                                    final MavenCoordinate dep,
                                    final String algorithm) {
        if (!cgPool.containsKey(dep)) {
            try {
                logger.info("\n ##############\n Adding {} to cg pool!",
                    dep.getCoordinate());
                final var file = new MavenCoordinate.MavenResolver().downloadArtifact(dep, "jar");

                final long startTime = System.currentTimeMillis();
                final var opalCG = new CallGraphConstructor(file, "", algorithm);
                final var cg = new PartialCallGraph(opalCG, true);
                final var rcg = ExtendedRevisionJavaCallGraph.extendedBuilder()
                    .graph(cg.getGraph())
                    .product(dep.getProduct())
                    .version(dep.getVersionConstraint())
                    .classHierarchy(cg.getClassHierarchy())
                    .nodeCount(cg.getNodeCount())
                    .build();

                    statCounter.addNewCGtoPool(dep,
                        System.currentTimeMillis() - startTime,
                        new StatCounter.GraphStats(ExtendedRevisionJavaCallGraph.toLocalDirectedGraph(rcg)));

                    file.delete();


                cgPool.put(dep, rcg);
            } catch (Exception e) {
                logger.warn("Exception occurred found!", e);
                statCounter.addNewCGtoPool(dep, 0, new StatCounter.GraphStats());
            }

        } else {
            statCounter.addExistingToCGPool(dep);
        }
    }

    private static MavenCoordinate convertToFastenCoordinate(
        final org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate revision) {
        return new MavenCoordinate(revision.getGroupId(),
            revision.getArtifactId(), revision.getVersion(),
            revision.getPackaging().getClassifier());
    }

    private static String extractMain(final File langFeature) throws IOException {
        final var conf = new String(Files.readAllBytes(

            Paths.get(langFeature.getAbsolutePath().replace(".jar_split", "").concat(".conf"))));
        final var jsObject = new JSONObject(conf);

        String main = "";
        if (jsObject.has("main")) {
            main = jsObject.getString("main");
        }

        if (main != null) {
            main = main.replace("\"", "");
        }
        return main;
    }

    public static void generateOpal(final File langFeature, final String mainClass,
                                    final String algorithm,
                                    final String output) {
        final var fileName = langFeature.getName().replace(".class", "");
        final var resultGraphPath = langFeature.getAbsolutePath() + "/" + output;
        final var cgCommand =
            new String[] {"-g", "-a", langFeature.getAbsolutePath(), "-n", mainClass, "-ga",
                algorithm, "-m", "FILE", "-o", resultGraphPath};
        final var convertCommand = new String[] {"-c", "-i", resultGraphPath + "_" + fileName, "-f",
            "JCG",
            "-o",
            langFeature.getAbsolutePath() + "/" + output + "Jcg"};
        logger.info("CG Command: {}", Arrays.toString(cgCommand).replace(",", " "));
        Main.main(cgCommand);
        logger.info("Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);

    }

    public static boolean merge(final File langFeature, final String main, final String genAlg,
                                final String mergeAlg,
                                final String output) {

        final var files = langFeature.listFiles(file -> file.getPath().endsWith(".class"));
        assert files != null;
        if (files.length > 1) {
            compute(langFeature, main, output, createArtDep(main, files), genAlg, mergeAlg);
            return true;
        } else {
            logger.info("No dependency, no merge for {}", langFeature.getAbsolutePath());
            return false;
        }
    }

    private static String[] createArtDep(final String main, final File[] files) {

        String art = "";
        final StringBuilder deps = new StringBuilder();
        for (final File file : files) {
            if (!main.isEmpty()) {
                if (file.getName().equals(main.split("[.]")[1] + ".class")) {
                    art = file.getAbsolutePath();
                } else {
                    deps.append(file.getAbsolutePath()).append(",");
                }
            } else {
                if (file.getName().contains("Demo.class")) {
                    art = file.getAbsolutePath();
                } else {
                    deps.append(file.getAbsolutePath()).append(",");
                }
            }

        }
        return new String[] {"-a", art, "-d", deps.toString().replaceAll(".$", "")};
    }

    private static void compute(final File langFeature, final String main, final String output,
                                final String[] artDeps, final String genAlg,
                                final String mergeAlg) {
        var mergeCommand = new String[] {"-s", "-ma", mergeAlg, "-ga", genAlg, "-n", main, "-o",
            langFeature.getAbsolutePath() + "/" + output};
        mergeCommand = ArrayUtils.addAll(mergeCommand, artDeps);

        logger.info("Merge Command: {}", Arrays.toString(mergeCommand).replace(",", " "));
        Main.main(mergeCommand);

        StringBuilder input = new StringBuilder();
        final var files = new File(langFeature.getAbsolutePath() + "/cg")
            .listFiles(
                file -> file.getName().startsWith("mergeV3") && !file.getName().endsWith("Demo"));

        assert files != null;
        if (files.length > 1) {
            for (int i = 0; i < files.length; i++) {
                if (i == files.length - 1) {
                    input.append(files[i].getAbsolutePath());
                } else {
                    input.append(files[i].getAbsolutePath()).append(",");
                }
            }
        }
        final var convertCommand = new String[] {"-c", "-i", input.toString(), "-f", "JCG", "-o",
            langFeature.getAbsolutePath() + "/" + output + "Jcg"};

        logger.info("Merge Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);
    }

    public static Map<MavenCoordinate, List<MavenCoordinate>> readResolvedCSV(
        final String inputPath) throws IOException {

        Map<MavenCoordinate, List<MavenCoordinate>> result = new HashMap<>();

        try (var csvReader = new CSVReader(new FileReader(inputPath), ',', '\'', 1)) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {

                final var coords = getCoordsList(values[2]);
                result.put(coords.get(0), coords);
            }
        }
        return result;
    }

    private static List<MavenCoordinate> getCoordsList(final String coords) {
        List<MavenCoordinate> result = new ArrayList<>();

        var coordinates = coords.split(";");
        for (final var coord : coordinates) {
            result.add(MavenCoordinate.fromString(coord, "jar"));
        }
        return result;
    }



}