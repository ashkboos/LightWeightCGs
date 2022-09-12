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

import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CSVUtils;
import util.FilesUtils;

public class StatCounter {

    private static final Logger logger = LoggerFactory.getLogger(StatCounter.class);


    private final Map<MavenCoordinate, GeneratorStats> opalStats;

    private final Map<MavenCoordinate, GeneratorStats> walaStats;

    private final Map<MavenCoordinate, CGPoolStats> cgPoolStats;

    private final Map<MavenCoordinate, List<MergeTimer>> mergeStats;

    private final Map<MavenCoordinate, Long> UCHTime;

    private final Map<MavenCoordinate, List<SourceStats>> accuracy;

    private final Map<String, Pair<String, String>> logs;


    public StatCounter() {
        UCHTime = new ConcurrentHashMap<>();
        cgPoolStats = new ConcurrentHashMap<>();
        opalStats = new ConcurrentHashMap<>();
        walaStats = new ConcurrentHashMap<>();
        mergeStats = new ConcurrentHashMap<>();
        accuracy = new ConcurrentHashMap<>();
        logs = new ConcurrentHashMap<>();
    }

    public synchronized void addAccuracy(final MavenCoordinate toMerge,
                                         final List<SourceStats> acc) {
        this.accuracy.put(toMerge, acc);
    }

    public synchronized void addLog(final File[] opalLog, final File[] mergeLog,
                                    final String coord) {
        String opalLogString = "", mergeLoString = "";

        if (opalLog != null) {
            opalLogString = FilesUtils.readFromLast(opalLog[0], 20);
        }
        if (mergeLog != null) {
            mergeLoString = FilesUtils.readFromLast(mergeLog[0], 20);
        }
        this.logs.put(coord, ImmutablePair.of(opalLogString, mergeLoString));
    }

    public static class SourceStats {
        final private String source;
        final private double precision;
        final private double recall;
        final private int OPAL;
        final private int merge;
        final private int intersect;

        public SourceStats(final String source, final double precision, final double recall,
                           final int OPAL,
                           final int merge, final int intersect) {
            this.source = source;
            this.precision = precision;
            this.recall = recall;
            this.OPAL = OPAL;
            this.merge = merge;
            this.intersect = intersect;
        }
    }

    public static class MergeTimer {
        public final MavenCoordinate artifact;
        public final List<MavenCoordinate> deps;
        public final Long time;
        public final GraphStats mergeStats;

        public MergeTimer(final MavenCoordinate artifact,
                          final List<MavenCoordinate> deps, final Long time,
                          final GraphStats mergeStats) {
            this.artifact = artifact;
            this.deps = deps;
            this.time = time;
            this.mergeStats = mergeStats;
        }
    }

    public static class GraphStats {
        public final Integer nodes;
        public final Integer edges;


        public GraphStats(final DirectedGraph dg) {
            if (dg != null) {
                this.nodes = dg.nodes().size();
                this.edges = dg.edgeSet().size();
            } else {
                this.nodes = 0;
                this.edges = 0;
            }
        }

        public GraphStats(final PartialJavaCallGraph rcg) {
            if (rcg != null) {
                this.nodes = rcg.getNodeCount();
                this.edges = rcg.getGraph().getCallSites().size();
            } else {
                this.nodes = 0;
                this.edges = 0;
            }
        }

        public GraphStats() {
            this.nodes = 0;
            this.edges = 0;
        }

        public GraphStats(int nodes, int edges) {
            this.nodes = nodes;
            this.edges = edges;
        }


        private int countMethods(final Collection<JavaType> types) {
            int result = 0;
            for (final var type : types) {
                result = result + type.getMethods().size();
            }
            return result;
        }

        public int getField(final String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
            Field field = this.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (Integer) field.get(this);
        }
    }

    public static class GeneratorStats {
        final private Long time;
        final private GraphStats graphStats;

        public GeneratorStats(final Long time, final GraphStats graphStats) {
            this.time = time;
            this.graphStats = graphStats;
        }
    }

    public static class CGPoolStats {

        final private Long time;
        private Integer occurrence;
        final private GraphStats cgPoolGraphStats;

        public CGPoolStats(final Long time, final Integer occurrence,
                           final GraphStats cgPoolGraphStats) {

            this.time = time;
            this.occurrence = occurrence;
            this.cgPoolGraphStats = cgPoolGraphStats;
        }

        public synchronized void addOccurrence() {
            occurrence++;
        }
    }

    public synchronized void addNewCGtoPool(final MavenCoordinate coord, final long time,
                                            final GraphStats rcg) {

        cgPoolStats.put(coord,
            new CGPoolStats(time, 1, rcg)
        );
    }

    public synchronized void addExistingToCGPool(final MavenCoordinate coord) {
        cgPoolStats.get(coord).addOccurrence();
    }

    public synchronized void addOPAL(final MavenCoordinate coord, final GeneratorStats os) {
        addGenerator(coord, os, opalStats);
    }

    public synchronized void addOPAL(final MavenCoordinate coord, final long time,
                                     final DirectedGraph dg) {
        this.addOPAL(coord, new GeneratorStats(time, new GraphStats(dg)));
    }

    private void addGenerator(final MavenCoordinate coord, final GeneratorStats os,
                              final Map<MavenCoordinate, GeneratorStats> generator) {
        if (generator.containsKey(coord)) {
            logger.warn("The coordinate was already generated {}", coord);
        } else {
            generator.put(coord, os);
        }
    }

    public void addWala(final MavenCoordinate coord, final long time, final DirectedGraph dg) {
        this.addWala(coord, new GeneratorStats(time, new GraphStats(dg)));
    }

    public synchronized void addWala(final MavenCoordinate coord, final GeneratorStats os) {
        addGenerator(coord, os, walaStats);
    }

    public synchronized void addUCH(final MavenCoordinate coord, final Long time) {
        UCHTime.put(coord, time);
    }

    public synchronized void addMerge(final MavenCoordinate rootCoord,
                                      final MavenCoordinate artifact,
                                      final List<MavenCoordinate> deps,
                                      final long time, final GraphStats cgStats) {
        final var merge = mergeStats.getOrDefault(rootCoord, new ArrayList<>());
        merge.add(new MergeTimer(artifact, deps, time, cgStats));
        mergeStats.put(rootCoord, merge);
    }

    public void concludeMerge(final String resultPath) {

        CSVUtils.writeToCSV(buildCGPoolCSV(), resultPath + "/CGPool.csv");
        CSVUtils.writeToCSV(buildMergeCSV(), resultPath + "/Merge.csv");
    }

    public void concludeOpal(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                             final String resultPath) {

        CSVUtils.writeToCSV(
            buildGeneratorCSV(resolvedData, opalStats), resultPath + "/resultOpal.csv");
    }

    public void concludeWala(Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                             String resultPath) {
        CSVUtils.writeToCSV(
            buildGeneratorCSV(resolvedData, walaStats), resultPath + "/resultWala.csv");
    }

    public void concludeLogs(final String outPath) {
        CSVUtils.writeToCSV(buildLogCsv(), outPath + "/Logs.csv");
    }


    private List<String[]> buildLogCsv() {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(CSVUtils.getHeaderOf("Log"));
        int counter = 0;
        for (final var coorLogs : this.logs.entrySet()) {
            dataLines.add(getLogContent(counter, coorLogs));
            counter++;
        }
        return dataLines;
    }


    private String[] getLogContent(int counter, Map.Entry<String, Pair<String, String>> coorLogs) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coorLogs.getKey(),
            /* opalLog */ coorLogs.getValue().getLeft(),
            /* mergeLog */ coorLogs.getValue().getRight()
        };
    }

    public void concludeAll(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                            final String resultPath) {

        CSVUtils.writeToCSV(buildOverallCsv(resolvedData), resultPath + "/Overall.csv");
        CSVUtils.writeToCSV(buildAccuracyCsv(resolvedData), resultPath + "/accuracy.csv");
    }


    private List<String[]> buildAccuracyCsv(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(CSVUtils.getHeaderOf("Accuracy"));

        int counter = 0;
        for (final var coordAcc : this.accuracy.entrySet()) {
            final var coord = coordAcc.getKey();
            final var accValue = coordAcc.getValue();
            dataLines.addAll(getContentOfAcc(resolvedData, counter, coord, accValue));
        }
        return dataLines;
    }


    private List<String[]> getContentOfAcc(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData, int counter,
        final MavenCoordinate coord,
        final List<SourceStats> sourceStats) {
        List<String[]> result = new ArrayList<>();
        for (final var sourceStat : sourceStats) {
            result.add(new String[] {
                /* number */ String.valueOf(counter),
                /* coordinate */ coord.getCoordinate(),
                /* source */ String.valueOf(sourceStat.source),
                /* precision */ String.valueOf(sourceStat.precision),
                /* recall */ String.valueOf(sourceStat.recall),
                /* emptyOPAL */ String.valueOf(sourceStat.OPAL),
                /* emptyMerge */ String.valueOf(sourceStat.merge),
                /* emptyBoth */ String.valueOf(sourceStat.intersect),
                /* dependencies */ toString(resolvedData.get(coord))
            });
            counter++;
        }
        return result;
    }


    private List<String[]> buildGeneratorCSV(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final Map<MavenCoordinate, GeneratorStats> generatorStats) {

        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(CSVUtils.getHeaderOf("Generator"));

        int counter = 0;
        for (var coordinateStats : generatorStats.entrySet()) {
            var stats = coordinateStats.getValue();
            dataLines.add(
                getContentOfGenerator(resolvedData, counter, coordinateStats.getKey(), stats));
            counter++;
        }
        return dataLines;
    }


    private String[] getContentOfGenerator(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final int counter, MavenCoordinate coord,
        final GeneratorStats generatorStats) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* time */ String.valueOf(generatorStats.time),
            /* nodes */ String.valueOf(generatorStats.graphStats.nodes),
            /* edges */ String.valueOf(generatorStats.graphStats.edges),
            /* dependencies */ toString(resolvedData.get(coord))
        };
    }


    private List<String[]> buildCGPoolCSV() {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(CSVUtils.getHeaderOf("CGPool"));

        int counter = 0;
        for (final var coordRep : cgPoolStats.entrySet()) {
            final var coord = coordRep.getKey();
            dataLines.add(getContentOfCGPool(counter, coordRep.getValue().occurrence, coord,
                coordRep.getValue()));
            counter++;
        }
        return dataLines;
    }


    private List<String[]> buildMergeCSV() {

        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(CSVUtils.getHeaderOf("Merge"));
        int counter = 0;

        for (final var coordMerge : mergeStats.entrySet()) {
            final var rootCoord = coordMerge.getKey();
            for (final var merge : coordMerge.getValue()) {
                dataLines.add(
                    CSVUtils.getMergeContent(counter, rootCoord, merge, UCHTime.get(rootCoord)));
                counter++;
            }
        }
        return dataLines;
    }


    private List<String[]> buildOverallCsv(
        final Map<MavenCoordinate, List<MavenCoordinate>> depTree) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(CSVUtils.getHeaderOf("Overall"));
        int counter = 0;
        for (final var coorDeps : depTree.entrySet()) {
            final var coord = coorDeps.getKey();
            dataLines.add(
                getOverallContent(depTree, counter, coord, opalStats.getOrDefault(coord, null)));
            counter++;
        }
        return dataLines;
    }


    private String[] getContentOfCGPool(final int counter,
                                        final int occurrence,
                                        final MavenCoordinate coord,
                                        final CGPoolStats cgPoolStats) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* occurrence */ String.valueOf(occurrence),
            /* isolatedRevisionTime */ String.valueOf(cgPoolStats.time),
            /* nodes */ String.valueOf(cgPoolStats.cgPoolGraphStats.nodes),
            /* edges */ String.valueOf(cgPoolStats.cgPoolGraphStats.edges)
        };
    }


    private String[] getOverallContent(final Map<MavenCoordinate, List<MavenCoordinate>> depTree,
                                       final int counter,
                                       final MavenCoordinate coord,
                                       final GeneratorStats generatorStats) {
        final var mergePool = calculateTotalMergeTime(depTree, coord);
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* opalTime */ generatorStats == null ? "" : String.valueOf(generatorStats.time),
            /* totalMergeTime */ String.valueOf(mergePool.getLeft() + mergePool.getRight()),
            /* cgPool */ String.valueOf(mergePool.getRight()),
            /* mergeTime */ String.valueOf(mergePool.getLeft()),
            /* UCHTime */ String.valueOf(UCHTime.get(coord)),
            /* opalNodes */ generatorStats == null ? "-1" :
            String.valueOf(generatorStats.graphStats.nodes),
            /* opalEdges */ generatorStats == null ? "-1" :
            String.valueOf(generatorStats.graphStats.edges),
            /* mergeNodes */ calculateNumberOf("nodes", coord),
            /* mergeEdges */ calculateNumberOf("edges", coord),
            /* dependencies */ toString(depTree.get(coord))};
    }


    private String calculateNumberOf(final String nodesOrEdges,
                                     final MavenCoordinate coord) {
        int allNodes = 0;

        if (mergeStats.containsKey(coord)) {
            for (final var singleMerge : mergeStats.get(coord)) {
                try {
                    allNodes = allNodes + singleMerge.mergeStats.getField(nodesOrEdges);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            return "-1";
        }

        return String.valueOf(allNodes);
    }

    public static String toString(final List<MavenCoordinate> coords) {
        return coords.stream()
            .map(MavenCoordinate::getCoordinate)
            .collect(Collectors.joining(";"));
    }


    private Pair<Long, Long> calculateTotalMergeTime(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final MavenCoordinate coord) {

        long cgPoolTotalTime = 0;
        long mergeTotalTime = 0;

        try {
            for (final var depCoord : resolvedData.get(coord)) {
                if (cgPoolStats.get(depCoord) != null) {
                    if (cgPoolStats.get(depCoord).time != null) {
                        cgPoolTotalTime = cgPoolTotalTime + cgPoolStats.get(depCoord).time;
                    }
                }
            }
            for (final var merge : mergeStats.get(coord)) {
                mergeTotalTime = mergeTotalTime + merge.time;
            }
        } catch (Exception e) {
            logger.error("Exception occurred", e);
        }
        return ImmutablePair.of(mergeTotalTime, cgPoolTotalTime);

    }

}
