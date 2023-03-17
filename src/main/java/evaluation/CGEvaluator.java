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

import static jcg.StitchingEdgeTest.convertOpalAndMergeToNodePairs;
import static jcg.StitchingEdgeTest.groupBySource;

import ch.qos.logback.classic.Level;
import data.ResultCG;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CSVUtils;
import util.FilesUtils;
import util.InputDataUtils;

public class CGEvaluator {

    private static final int warmUp = 10;
    private static final int iterations = 10;

    private static final Logger logger = LoggerFactory.getLogger(CGEvaluator.class);
    public static final boolean includeJava = false;

    public static void main(String[] args) {
//        configLogs();
        printMemInfo();
        switch (args[0]) {
            case "--inputDemography":
                InputDemography.inputDemography(args[1], args[2]);
                break;

            case "--resolve":
                InputDataUtils.resolveDependencies(args[1], args[2]);
                break;
            case "--splitInput":
                InputDataUtils.splitInput(args[1], args[2], args[3]);
                break;

            case "--wholeProgram":
                evaluateAndWriteToFile(args[1], args[2],
                    new GeneratorEvaluator(warmUp, iterations, includeJava)::evaluateGenerator);
                break;
            case "--merge":
                evaluateAndWriteToFile(args[1], args[2],
                    new MergeEvaluator(warmUp, iterations, includeJava)::evaluateMerge);
                break;

            case "--analyzeOutDir":
                analyzeOutDir(args[1], args[2], args[3], args[4]);
                break;
        }
    }

    private static void analyzeOutDir(final String rootPath,
                                      final String outPath,
                                      final String generatorFolder,
                                      final String mergerFolder) {
        logger.info("Start analyzing directory...");
        final var statCounter = new StatCounter();
        AtomicInteger counter = new AtomicInteger(0);

        final var input = CSVUtils.readResolvedCSV(rootPath + "/input.csv");
        Arrays.stream(Objects.requireNonNull(new File(rootPath).listFiles()))
            .forEach(pkgDir -> {
                if (pkgDir.getName().contains("DS_Store") || pkgDir.getName().equals("input.csv") ) {
                    return;
                }
                final var rootCoord = MavenCoordinate.fromString(pkgDir.getName(), "jar");
                final var generatorDir = FilesUtils.getDir(pkgDir, generatorFolder);
                final var mergeDir = FilesUtils.getDir(pkgDir, mergerFolder);
                StatCounterDeserializer.updateFromFile(rootCoord, generatorDir,
                    mergeDir, statCounter);

                final var opalCG = getResultCG(generatorDir);
                if (opalCG.isEmpty()) {
                    return;
                }
                final var mergedCG = getResultCG(mergeDir);
                if (mergedCG.isEmpty()) {
                    return;
                }
                statCounter.addAccuracy(rootCoord, calcPrecisionRecall(groupBySource(
                    convertOpalAndMergeToNodePairs(mergedCG, opalCG))));
                logger.info("pckg number :" + counter.getAndAdd(1) + ":" + pkgDir.getName());
                System.gc();
            });

        statCounter.concludeMerge(outPath);
        statCounter.concludeGenerator(outPath);
        statCounter.concludeLogs(outPath);
        statCounter.concludeAll(input, outPath);

    }

    private static void evaluateAndWriteToFile(final String row, final String outPath,
                                               final BiFunction<String, List<MavenCoordinate>, ResultCG> generateFunc) {
        var coords = CSVUtils.getCoordinatesFromRow(row);
        coords = new ArrayList<>(new LinkedHashSet<>(coords));
        final var cg = generateFunc.apply(outPath, coords);
        FilesUtils.writeCGToFile(outPath, cg);
    }

    private static void printMemInfo() {
        long heapSize = Runtime.getRuntime().totalMemory();//gave to jvm
        long heapMaxSize = Runtime.getRuntime().maxMemory();//max that is configured
        long heapFreeSize = Runtime.getRuntime().freeMemory();//diff between free and total and
        // add the difference
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

    private static ResultCG getResultCG(final File dir) {
        ResultCG opalCG = new ResultCG();
        try {
            opalCG = FilesUtils.readCG(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return opalCG;
    }


    static Set<StatCounter.SourceStats> calcPrecisionRecall(final Map<String, Map<String,
        Set<String>>> edges) {
        final var opal = edges.get("opal");
        final var merge = edges.get("merge");

        final var allSources = new ObjectOpenHashSet<String>();
        allSources.addAll(opal.keySet());
        allSources.addAll(merge.keySet());

        final Set<StatCounter.SourceStats> result = ConcurrentHashMap.newKeySet();
        allSources.parallelStream().forEach(source -> {

            final var opalTargets =
                new ObjectOpenHashSet<>(opal.getOrDefault(source, new ObjectOpenHashSet<>()));
            final var mergeTargets =
                new ObjectOpenHashSet<>(merge.getOrDefault(source, new ObjectOpenHashSet<>()));
            final var intersect = intersect(opalTargets, mergeTargets);
            final var mergeSize = mergeTargets.size();
            final var opalSize = opalTargets.size();
            final var intersectSize = intersect.size();

            final var precision = mergeSize == 0 ? 1 : (double) intersectSize / mergeSize;
            final var recall = opalSize == 0 ? 1 : (double) intersectSize / opalSize;

            result.add(new StatCounter.SourceStats(source, precision, recall, opalSize, mergeSize
                , intersectSize));
        });
        return result;
    }

    private static Set<String> intersect(final Set<String> first,
                                         final Set<String> second) {
        final var temp1 = new ObjectArrayList<>(first);
        final var temp2 = new ObjectArrayList<>(second);
        return temp1.stream().distinct().filter(temp2::contains).collect(Collectors.toSet());
    }


    public static DirectedGraph toLocalDirectedGraph(final PartialJavaCallGraph rcg) {
        DirectedGraph dcg = new MergedDirectedGraph();
        final var internals = rcg.mapOfFullURIStrings();
        rcg.getGraph().getCallSites().entrySet().parallelStream()
            .filter(intInt -> internals.containsKey(intInt.getKey().firstLong())
                && internals.containsKey(intInt.getKey().secondLong())).forEach(intInt -> {
                final var source = intInt.getKey().firstLong();
                final var target = intInt.getKey().secondLong();
                synchronized (dcg) {
                    dcg.addVertex(source);
                    dcg.addVertex(target);
                    dcg.addEdge(source, target);
                }
            });
        return dcg;
    }


}