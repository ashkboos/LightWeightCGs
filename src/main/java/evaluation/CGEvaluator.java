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

import static eu.fasten.core.data.CallPreservationStrategy.INCLUDING_ALL_SUBTYPES;
import static eu.fasten.core.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static jcg.StitchingEdgeTest.convertOpalAndMergeToNodePairs;
import static jcg.StitchingEdgeTest.groupBySource;

import ch.qos.logback.classic.Level;
import com.ibm.wala.ipa.callgraph.CallGraph;
import data.ResultCG;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraph;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.merge.CGMerger;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import me.tongfei.progressbar.ProgressBar;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CGUtils;
import util.CSVUtils;
import util.FilesUtils;

public class CGEvaluator {

    private static final int warmUp = 0;
    private static final int iterations = 2;

    private static final Logger logger = LoggerFactory.getLogger(CGEvaluator.class);

    public static void main(String[] args) {
        configLogs();
        printMemInfo();
        switch (args[0]) {
            case "--inputDemography":
                InputDemography.inputDemography(args[1], args[2]);
                break;

            case "--resolve":
                resolveDependencies(args[1], args[2]);
                break;
            case "--splitInput":
                splitInput(args[1], args[2], args[3]);
                break;

            case "--opal":
                generateAndWriteToFile(args[1], args[2], CGEvaluator::generateAndWriteOpal);
                break;
            case "--merge":
                generateAndWriteToFile(args[1], args[2], CGEvaluator::generateAndWriteMerge);
                break;
            case "--wala":
                generateAndWriteToFile(args[1], args[2], CGEvaluator::generateAndWriteWala);
                break;

            case "--analyzeOutDir":
                analyzeOutDir(args[1], args[2], args[3], args[4]);
                break;
        }
    }

    private static void analyzeOutDir(final String rootPath, final String outPath,
                                      final String generatorFolder,
                                      final String mergerFolder) {
        logger.info("Start analyzing directory...");
        final var statCounter = new StatCounter();
        final var depTree = new ConcurrentHashMap<MavenCoordinate, List<MavenCoordinate>>();
        AtomicInteger counter = new AtomicInteger(0);

        Arrays.stream(Objects.requireNonNull(new File(rootPath).listFiles())).parallel()
            .forEach(pkgDir -> {
                final var generatorDir = FilesUtils.getDir(pkgDir, generatorFolder);
                final var mergeDir = FilesUtils.getDir(pkgDir, mergerFolder);
                final var depEntry =
                    StatCounterDeserializer.updateFromFile(generatorDir, mergeDir, statCounter);

                depTree.put(depEntry.root, depEntry.deps);

                final var opalCG = getResultCG(generatorDir);
                if (opalCG.isEmpty()) {
                    return;
                }
                final var mergedCG = getResultCG(mergeDir);
                if (mergedCG.isEmpty()) {
                    return;
                }

                statCounter.addAccuracy(depEntry.root, calcPrecisionRecall(groupBySource(
                    convertOpalAndMergeToNodePairs(mergedCG, opalCG))));
                logger.info("pckg number :" + counter.getAndAdd(1));
                System.gc();
            });

        statCounter.concludeMerge(outPath);
        statCounter.concludeGenerator(depTree, outPath);
        statCounter.concludeLogs(outPath);
        statCounter.concludeAll(depTree, outPath);

    }

    private static void generateAndWriteToFile(final String row, final String outPath,
                                               final BiFunction<String, List<MavenCoordinate>, ResultCG> generateFunc) {
        final var coords = CSVUtils.getCoordinatesFromRow(row);
        final var cg = generateFunc.apply(outPath, coords);
        FilesUtils.writeCGToFile(outPath, cg);
    }

    private static ResultCG generateAndWriteMerge(final String path,
                                                  final List<MavenCoordinate> coords) {
        StatCounter statCounter = new StatCounter();
        String generator = Constants.opalGenerator;
        if (path.contains("wala")) {
            generator = Constants.walaGenerator;
        }
        final ResultCG cg =
            createCGPoolAndMergeDepSet(coords, statCounter, new HashMap<>(), generator);
        statCounter.concludeMerge(path);
        return cg;
    }

    private static ResultCG generateAndWriteOpal(final String outPath,
                                                 final List<MavenCoordinate> coords) {
        StatCounter statCounter = new StatCounter();
        final ResultCG cg = generateForOPALAndMeasureTime(statCounter, coords);
        statCounter.concludeGenerator(Map.of(coords.get(0), coords), outPath);
        return cg;
    }

    private static ResultCG generateAndWriteWala(final String outPath,
                                                 final List<MavenCoordinate> coords) {
        StatCounter statCounter = new StatCounter();
        final ResultCG cg = generateForWalaAndMeasureTime(statCounter, coords);
        statCounter.concludeGenerator(Map.of(coords.get(0), coords), outPath);
        return cg;
    }

    private static ResultCG generateForWalaAndMeasureTime(final StatCounter statCounter,
                                                          final List<MavenCoordinate> depSet) {
        ResultCG result = new ResultCG();

        final var currentVP = depSet.get(0);
        try {
            final var tempJar = FilesUtils.downloadToJar(depSet);
            logger.info("\n##################### \n Deps of {} downloaded to {}, Wala is " +
                "generating ...", currentVP.getCoordinate(), tempJar.getAbsolutePath());

            final var times = new ArrayList<Long>();
            CallGraph callgraph = null;

            for (int i = 0; i < warmUp + iterations; i++) {
                if (i > warmUp) {
                    final long startTime = System.currentTimeMillis();
                    callgraph = CallGraphConstructor.generateCallGraph(tempJar.getAbsolutePath(),
                        Algorithm.CHA);
                    times.add(System.currentTimeMillis() - startTime);
                }
            }

            final var pcg =
                PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge,
                    currentVP.getProduct(), currentVP.getVersionConstraint(), -1,
                    Constants.walaGenerator);

            WalaResultAnalyzer.wrap(callgraph, pcg, INCLUDING_ALL_SUBTYPES);

            FilesUtils.forceDelete(tempJar);

            result = new ResultCG(toLocalDirectedGraph(pcg), toMapOfLongAndUri(pcg));

            statCounter.addWala(currentVP,
                (long) times.stream().mapToDouble(a -> a).average().orElse(0), result.dg);

        } catch (Exception e) {
            logger.warn("Exception occurred while generating CG for WALA!", e);
            statCounter.addWala(currentVP, new StatCounter.GeneratorStats(0L,
                new StatCounter.GraphStats()));
        }

        return result;

    }

    private static void printMemInfo() {
        long heapSize = Runtime.getRuntime().totalMemory();
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        long heapFreeSize = Runtime.getRuntime().freeMemory();
        logger.info("heap size is: " + toMB(heapSize) + "mb");
        logger.info("max heap size is: " + toMB(heapMaxSize) + "mb");
        logger.info("free heap size is: " + toMB(heapFreeSize) + "mb");
    }

    private static long toMB(long heapFreeSize) {
        return (heapFreeSize / 1024) / 1024;
    }

    private static void configLogs() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
            .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
        System.setProperty("org.jline.terminal.dumb", "true");
    }

    private static void resolveDependencies(final String input, final String output) {
        final var resolvedData =
            resolveAll(CSVUtils.dropTheHeader(CSVUtils.readCSVColumn(input, 0)));
        CSVUtils.writeToCSV(CSVUtils.buildDataCSVofResolvedCoords(resolvedData), output);
        logger.info("Wrote resolved data into file successfully!");
    }

    private static void splitInput(final String inputPath, final String chunkNum,
                                   final String resultPath) {
        final var data = CSVUtils.readResolvedCSV(inputPath);
        final var chunks = splitToChunks(data, Integer.parseInt(chunkNum));
        for (int i = 0; i < chunks.size(); i++) {
            final var part = chunks.get(i);
            CSVUtils.writeToCSV(
                CSVUtils.buildDataCSVofResolvedCoords(part), resultPath + "/chunk.p" + i + ".csv");
        }
        logger.info("Wrote data chunks into file successfully!");
    }


    private static List<Map<MavenCoordinate, List<MavenCoordinate>>> splitToChunks(
        final Map<MavenCoordinate, List<MavenCoordinate>> data, final int chunkNum) {
        final List<Map<MavenCoordinate, List<MavenCoordinate>>> result = new ArrayList<>();
        final var chunkSize = data.size() / chunkNum;
        int counter = 0;
        Map<MavenCoordinate, List<MavenCoordinate>> chunk = new HashMap<>();
        for (final var entry : data.entrySet()) {
            chunk.put(entry.getKey(), entry.getValue());
            counter++;
            if (counter % chunkSize == 0 && (data.size() - counter) > chunkNum) {
                result.add(chunk);
                chunk = new HashMap<>();
            } else if (data.size() == counter) {
                result.add(chunk);
            }
        }
        return result;
    }


    private static ResultCG getResultCG(final File dir) {
        ResultCG opalCG = new ResultCG();
        try {
            opalCG = FilesUtils.readCG(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return opalCG;
    }

    static ResultCG createCGPoolAndMergeDepSet(
        final List<MavenCoordinate> depSet, StatCounter statCounter,
        final Map<MavenCoordinate, PartialJavaCallGraph> cgPool, String generator) {

        for (final var dep : depSet) {
            if (!cgPool.containsKey(dep)) {
                addToCGPoolAndMeasureTime(statCounter, cgPool, dep, generator);
            }
        }

        logger.info("artifact: {}, dependencies: {}", depSet.get(0).getCoordinate(),
            depSet.stream().map(MavenCoordinate::getCoordinate).collect(Collectors.toList()));

        return mergeDepSetAndMeasureTime(cgPool, statCounter, depSet);
    }


    static List<StatCounter.SourceStats> calcPrecisionRecall(final Map<String, Map<String,
        List<String>>> edges) {
        final var opal = edges.get("opal");
        final var merge = edges.get("merge");

        final var allSources = new HashSet<String>();
        allSources.addAll(opal.keySet());
        allSources.addAll(merge.keySet());

        final List<StatCounter.SourceStats> result = new ArrayList<>();
        for (final var source : allSources) {

            final var opalTargets = new HashSet<>(opal.getOrDefault(source, new ArrayList<>()));
            final var mergeTargets = new HashSet<>(merge.getOrDefault(source, new ArrayList<>()));
            final var intersect = intersect(opalTargets, mergeTargets);
            final var mergeSize = mergeTargets.size();
            final var opalSize = opalTargets.size();
            final var intersectSize = intersect.size();

            final var precision = mergeSize == 0 ? 1 : (double) intersectSize / mergeSize;
            final var recall = opalSize == 0 ? 1 : (double) intersectSize / opalSize;

            result.add(new StatCounter.SourceStats(source, precision, recall, opalSize, mergeSize
                , intersectSize));
        }
        return result;
    }

    private static Set<String> intersect(final Set<String> first,
                                         final Set<String> second) {
        final var temp1 = new ArrayList<>(first);
        final var temp2 = new ArrayList<>(second);
        return temp1.stream().distinct().filter(temp2::contains).collect(Collectors.toSet());
    }


    public static Map<MavenCoordinate, List<MavenCoordinate>> resolveAll(
        final List<String> dataSet) {

        ProgressBar pb = new ProgressBar("Resolving", dataSet.size());
        pb.start();

        final var result = new HashMap<MavenCoordinate, List<MavenCoordinate>>();
        for (final var coord : dataSet) {
            final var deps = resolve(coord);
            if (deps.isEmpty()) {
                continue;
            }
            result.put(
                MavenCoordinate.fromString(coord, deps.get(0).getPackaging().getClassifier()),
                convertToFastenCoordinates(deps)
            );
            pb.step();
        }
        pb.stop();
        return result;
    }

    private static ResultCG mergeDepSetAndMeasureTime(
        final Map<MavenCoordinate, PartialJavaCallGraph> cgPool, StatCounter statCounter,
        final List<MavenCoordinate> depSet) {
        final var pcgs = depSet.stream().map(cgPool::get)
            .filter(Objects::nonNull).collect(Collectors.toList());

        final long startTimeUch = System.currentTimeMillis();
        final var cgMerger = new CGMerger(pcgs);
        statCounter.addUCH(depSet.get(0), System.currentTimeMillis() - startTimeUch);

        logger.info("\n ###############\n Merging {}:", depSet.get(0).getCoordinate());

        DirectedGraph dg = null;
        final var times = new ArrayList<Long>();
        for (int i = 0; i < warmUp + iterations; i++) {
            if (i > warmUp) {
                final long startTime = System.currentTimeMillis();
                dg = cgMerger.mergeAllDeps();
                times.add(System.currentTimeMillis() - startTime);
            }
        }

        statCounter.addMerge(depSet.get(0), depSet.get(0), depSet,
            (long) times.stream().mapToDouble(a -> a).average().orElse(0),
            new StatCounter.GraphStats(dg));

        return new ResultCG(dg, cgMerger.getAllUris());

    }

    private static List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> resolve(
        final String row) {
        List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> result =
            new ArrayList<>();
        try {
            result = Maven.resolver().resolve(row).withTransitivity()
                .asList(org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate.class);
        } catch (Exception e) {
            logger.warn("Exception occurred while resolving {}, {}", row, e);
        }
        return result;
    }

    private static List<MavenCoordinate> convertToFastenCoordinates(
        final List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> revisions) {
        return revisions.stream().map(CGEvaluator::convertToFastenCoordinate)
            .collect(Collectors.toList());
    }

    static ResultCG generateForOPALAndMeasureTime(
        final StatCounter statCounter,
        final List<MavenCoordinate> depSet) {

        ResultCG result = new ResultCG();

        try {
            final var depFiles =
                FilesUtils.downloadToDir(getDepsOnly(depSet));
            final var rootFile = FilesUtils.download(depSet.get(0));
            logger.info("\n##################### \n DepSet {} downloaded to {}, Opal is " +
                "generating, dep files {} ...", depSet, rootFile.getAbsolutePath(), depFiles);

            final var opalCons = new OPALCallGraphConstructor();
            final var times = new ArrayList<Long>();
            OPALCallGraph oCG = null;

            for (int i = 0; i < warmUp + iterations; i++) {
                if (i > warmUp) {
                    final long startTime = System.currentTimeMillis();
                    oCG = opalCons.construct(new File[] {rootFile}, depFiles, CGAlgorithm.CHA);
                    times.add(System.currentTimeMillis() - startTime);
                }
            }
            FilesUtils.forceDelete(depFiles);
            FilesUtils.forceDelete(rootFile);

            result = toDirectedCGAndUris(oCG, depSet.get(0));
            statCounter.addOPAL(depSet.get(0),
                (long) times.stream().mapToDouble(a -> a).average().orElse(0), result.dg);

        } catch (Exception e) {
            logger.warn("Exception occurred while generating CG for OPAL!", e);
            statCounter.addOPAL(depSet.get(0), new StatCounter.GeneratorStats(0L,
                new StatCounter.GraphStats()));
        }

        return result;

    }


    public static List<MavenCoordinate> getDepsOnly(final List<MavenCoordinate> depSet) {
        List<MavenCoordinate> result = new ArrayList<>();
        for (int i = 0; i < depSet.size(); i++) {
            if (i == 0) {
                continue;
            }
            result.add(depSet.get(i));
        }
        return result;
    }


    private static ResultCG toDirectedCGAndUris(
        final OPALCallGraph oCG, MavenCoordinate coordinate) {
        final var rcg = CGUtils.convertOpalCGToFastenCG(coordinate, INCLUDING_ALL_SUBTYPES, oCG);
        final var dcg = toLocalDirectedGraph(rcg);
        return new ResultCG(dcg, toMapOfLongAndUri(rcg));
    }

    private static Map<Long, String> toMapOfLongAndUri(final PartialJavaCallGraph rcg) {
        return rcg.mapOfFullURIStrings().entrySet().stream()
            .collect(Collectors.toMap(e -> Long.valueOf(e.getKey()), Map.Entry::getValue));
    }


    public static DirectedGraph toLocalDirectedGraph(final PartialJavaCallGraph rcg) {
        DirectedGraph dcg = new MergedDirectedGraph();
        final var internals = rcg.mapOfFullURIStrings();
        for (final var intInt : rcg.getGraph().getCallSites().entrySet()) {
            if (internals.containsKey(intInt.getKey().firstInt())
                && internals.containsKey(intInt.getKey().secondInt())) {
                final var source = (long) intInt.getKey().firstInt();
                final var target = (long) intInt.getKey().secondInt();
                dcg.addVertex(source);
                dcg.addVertex(target);
                dcg.addEdge(source, target);
            }
        }
        return dcg;
    }

    private static void addToCGPoolAndMeasureTime(final StatCounter statCounter,
                                                  final Map<MavenCoordinate, PartialJavaCallGraph> cgPool,
                                                  final MavenCoordinate dep,
                                                  final String generator) {
        if (cgPool.containsKey(dep)) {
            statCounter.addExistingToCGPool(dep);
            return;
        }
        try {
            logger.info("\n ##############\n Adding {} to cg pool!", dep.getCoordinate());
            final var file = FilesUtils.download(dep);

            final long startTime = System.currentTimeMillis();
            final var rcg =
                CGUtils.generateCGFromFile(file, dep, CGAlgorithm.CHA, ONLY_STATIC_CALLSITES,
                    generator);
            statCounter.addNewCGtoPool(dep, System.currentTimeMillis() - startTime,
                new StatCounter.GraphStats(rcg));

            FilesUtils.forceDelete(file);
            cgPool.put(dep, rcg);
        } catch (Exception e) {
            logger.warn("Exception during adding dep {} to cg pool!", dep.getCoordinate(), e);
            statCounter.addNewCGtoPool(dep, 0, new StatCounter.GraphStats());
        }
    }


    private static MavenCoordinate convertToFastenCoordinate(
        final org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate revision) {
        return new MavenCoordinate(revision.getGroupId(),
            revision.getArtifactId(), revision.getVersion(),
            revision.getPackaging().getClassifier());
    }
}