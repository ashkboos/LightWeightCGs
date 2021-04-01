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

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatCounter {

    private static final Logger logger = LoggerFactory.getLogger(StatCounter.class);

    private final Map<MavenCoordinate, OpalStats> opalStats;
    private final Map<MavenCoordinate, CGPoolStats> cgPoolStats;
    private final Map<MavenCoordinate, List<MergeTimer>> mergeStats;
    private final Map<MavenCoordinate, Long> UCHTime;
    private final Map<MavenCoordinate, List<SourceStats>> accuracy;


    public StatCounter() {
        UCHTime = new HashMap<>();
        cgPoolStats = new HashMap<>();
        opalStats = new HashMap<>();
        mergeStats = new HashMap<>();
        accuracy = new HashMap<>();
    }

    public void addAccuracy(MavenCoordinate toMerge,
                            List<SourceStats> acc) {
        this.accuracy.put(toMerge, acc);
    }

    public static class SourceStats {
        final private String source;
        final private double precision;
        final private double recall;
        final private int OPAL;
        final private int merge;
        final private int intersect;

        public SourceStats(String source, double precision, double recall, int OPAL,
                           int merge, int intersect) {
            this.source = source;
            this.precision = precision;
            this.recall = recall;
            this.OPAL = OPAL;
            this.merge = merge;
            this.intersect = intersect;
        }
    }

    public static class MergeTimer {
        final private MavenCoordinate artifact;
        final private List<MavenCoordinate> deps;
        final private Long time;
        final private GraphStats mergeStats;

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
        final private Integer internalNodes, externalNodes, resolvedNodes, internalEdges,
            externalEdges, resolvedEdges;

        public GraphStats(final ExtendedRevisionJavaCallGraph rcg) {
            this.internalEdges = rcg.getGraph().getInternalCalls().size();
            this.externalEdges = rcg.getGraph().getExternalCalls().size();
            this.resolvedEdges = rcg.getGraph().getResolvedCalls().size();
            this.internalNodes = countMethods(rcg
                .getClassHierarchy().get(JavaScope.internalTypes)
                .values());
            this.externalNodes = countMethods(rcg
                .getClassHierarchy().get(JavaScope.externalTypes)
                .values());
            this.resolvedNodes = countMethods(rcg
                .getClassHierarchy().get(JavaScope.resolvedTypes)
                .values());
        }

        public GraphStats() {
            this.internalEdges = 0;
            this.externalEdges = 0;
            this.resolvedEdges = 0;
            this.internalNodes = 0;
            this.externalNodes = 0;
            this.resolvedNodes = 0;
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

    public static class OpalStats {
        final private Long time;
        final private GraphStats opalGraphStats;

        public OpalStats(final Long time, final GraphStats opalGraphStats) {
            this.time = time;
            this.opalGraphStats = opalGraphStats;
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

        public void addOccurrence() {
            occurrence++;
        }
    }

    public void addNewCGtoPool(final MavenCoordinate coord, final long time,
                               final GraphStats rcg) {

        cgPoolStats.put(coord,
            new CGPoolStats(time, 1, rcg)
        );
    }

    public void addExistingToCGPool(final MavenCoordinate coord) {
        cgPoolStats.get(coord).addOccurrence();
    }

    public void addOPAL(final MavenCoordinate coord,
                        final OpalStats os) {
        if (opalStats.containsKey(coord)) {
            logger.warn("The coordinate was already generated by OPAL {}", coord);
        }else {
            opalStats.put(coord, os);
        }
    }

    public void addOPAL(final MavenCoordinate coord, final long time,
                        final ExtendedRevisionJavaCallGraph rcg) {
        if (opalStats.containsKey(coord)) {
            logger.warn("The coordinate was already generated by OPAL {}", coord);
        }
        opalStats.put(coord, new OpalStats(time, new GraphStats(rcg)));
    }
    public void addUCH(final MavenCoordinate coord, final Long time){
        UCHTime.put(coord, time);
    }

    public void addMerge(final MavenCoordinate rootCoord,
                         final MavenCoordinate artifact,
                         final List<MavenCoordinate> deps,
                         final long time,
                         final GraphStats cgStats) {
        final var merge = mergeStats.getOrDefault(rootCoord, new ArrayList<>());
        merge.add(new MergeTimer(artifact, deps, time, cgStats));
        mergeStats.put(rootCoord, merge);
    }

    public void concludeMerge(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                              final String resultPath)
        throws IOException{

        writeToCSV(buildCGPoolCSV(), resultPath + "/CGPool.csv");
        writeToCSV(buildMergeCSV(), resultPath + "/Merge.csv");
    }

    public void concludeAll(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                            final String resultPath)
        throws IOException, NoSuchFieldException, IllegalAccessException {

        writeToCSV(buildOpalCSV(resolvedData), resultPath + "/resultOpal.csv");
        writeToCSV(buildOverallCsv(resolvedData), resultPath + "/Overall.csv");
        writeToCSV(buildAccuracyCsv(resolvedData), resultPath + "/accuracy.csv");
    }

    private List<String[]> buildAccuracyCsv(Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeaderOf("Accuracy"));

        int counter = 0;
        for (final var coordAcc : this.accuracy.entrySet()) {
            final var coord = coordAcc.getKey();
            final var accValue = coordAcc.getValue();
            dataLines.addAll(getContentOfAcc(resolvedData, counter, coord, accValue));
        }
        return dataLines;
    }

    private List<String[]> getContentOfAcc(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData, int counter, final MavenCoordinate coord,
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

    private List<String[]> buildOpalCSV(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {

        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeaderOf("Opal"));

        int counter = 0;
        for (final var opal : opalStats.entrySet()) {
            final var coord = opal.getKey();
            final var opalStats = opal.getValue();
            dataLines.add(getContentOfOpal(resolvedData, counter, coord, opalStats));
            counter++;
        }
        return dataLines;
    }

    private String[] getContentOfOpal(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final int counter, final MavenCoordinate coord,
        final OpalStats opalStats) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* opalTime */ String.valueOf(opalStats.time),
            /* internalNodes */ String.valueOf(opalStats.opalGraphStats.internalNodes),
            /* externalNodes */ String.valueOf(opalStats.opalGraphStats.externalNodes),
            /* internalEdges */ String.valueOf(opalStats.opalGraphStats.internalEdges),
            /* externalEdges */ String.valueOf(opalStats.opalGraphStats.externalEdges),
            /* dependencies */ toString(resolvedData.get(coord))
        };
    }

    private List<String[]> buildCGPoolCSV() {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeaderOf("CGPool"));

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
        dataLines.add(getHeaderOf("Merge"));
        int counter = 0;

        for (final var coordMerge : mergeStats.entrySet()) {
            final var rootCoord = coordMerge.getKey();
            for (final var merge : coordMerge.getValue()) {
                dataLines.add(getMergeContent(counter, rootCoord, merge));
                counter++;
            }
        }
        return dataLines;
    }

    private List<String[]> buildOverallCsv(
        final Map<MavenCoordinate, List<MavenCoordinate>> depTree)
        throws NoSuchFieldException, IllegalAccessException {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeaderOf("Overall"));
        int counter = 0;
        for (final var coorDeps : depTree.entrySet()) {
            final var coord = coorDeps.getKey();
            dataLines.add(getOverallContent(depTree, counter, coord, opalStats.get(coord)));
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
            /* internalNodes */ String.valueOf(cgPoolStats.cgPoolGraphStats.internalNodes),
            /* externalNodes */ String.valueOf(cgPoolStats.cgPoolGraphStats.externalNodes),
            /* internalEdges */ String.valueOf(cgPoolStats.cgPoolGraphStats.internalEdges),
            /* externalEdges */ String.valueOf(cgPoolStats.cgPoolGraphStats.externalEdges)
        };
    }

    private String[] getOverallContent(final Map<MavenCoordinate, List<MavenCoordinate>> depTree,
                                       final int counter,
                                       final MavenCoordinate coord,
                                       final OpalStats opalStats)
        throws NoSuchFieldException, IllegalAccessException {
        final var mergePool = calculateTotalMergeTime(depTree, coord);
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* opalTime */ String.valueOf(opalStats.time),
            /* totalMergeTime */ String.valueOf(mergePool.getLeft() + mergePool.getRight()),
            /* cgPool */ String.valueOf(mergePool.getRight()),
            /* mergeTime */ String.valueOf(mergePool.getLeft()),
            /* UCHTime */ String.valueOf(UCHTime.get(coord)),
            /* opalInternalNodes */ String.valueOf(opalStats.opalGraphStats.internalNodes),
            /* opalExternalNodes */ String.valueOf(opalStats.opalGraphStats.externalNodes),
            /* opalInternalEdges */ String.valueOf(opalStats.opalGraphStats.internalEdges),
            /* opalExternalEdges */ String.valueOf(opalStats.opalGraphStats.externalEdges),
            /* mergeInternalNodes */ calculateNumberOf("internal", "Nodes", coord, depTree),
            /* mergeExternalNodes */ calculateNumberOf("external", "Nodes", coord, depTree),
            /* mergeInternalEdges */ calculateNumberOf("internal", "Edges", coord, depTree),
            /* mergeExternalEdges */ calculateNumberOf("external", "Edges", coord, depTree),
            /* mergeResolvedNodes */ calculateNumberOf("resolved", "Nodes", coord, depTree),
            /* mergeResolvedEdges */ calculateNumberOf("resolved", "Edges", coord, depTree),
            /* dependencies */ toString(depTree.get(coord))};
    }

    private String[] getHeaderOf(final String CSVName) {
        if (CSVName.equals("Overall")) {
            return new String[] {"number", "coordinate", "opalTime",
                "totalMergeTime", "cgPool", "mergeTime", "UCHTime",
                "opalInternalNodes", "opalExternalNodes",
                "opalInternalEdges", "opalExternalEdges",
                "mergeInternalNodes", "mergeExternalNodes",
                "mergeInternalEdges", "mergeExternalEdges",
                "mergeResolvedNodes", "mergeResolvedEdges",
                "dependencies"};

        } else if (CSVName.equals("Opal")) {
            return new String[] {"number", "coordinate", "opalTime",
                "internalNodes", "externalNodes",
                "internalEdges", "externalEdges",
                "dependencies"};

        } else if (CSVName.equals("CGPool")) {
            return new String[] {"number", "coordinate", "occurrence", "isolatedRevisionTime",
                "internalNodes", "externalNodes",
                "internalEdges", "externalEdges"
            };

        } else if (CSVName.equals("Accuracy")) {
            return new String[] {"number", "coordinate", "source", "precision", "recall",
                "OPAL", "Merge", "intersection", "dependencies"};
        }

        //Merge
        return new String[] {"number", "rootCoordinate", "artifact", "mergeTime",
            "internalNodes", "externalNodes",
            "internalEdges", "externalEdges",
            "resolvedNodes", "resolvedEdges",
            "dependencies"};
    }

    private String[] getMergeContent(final int counter, final MavenCoordinate rootCoord,
                                     final MergeTimer merge) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* rootCoordinate */ rootCoord.getCoordinate(),
            /* artifact */ String.valueOf(merge.artifact.getCoordinate()),
            /* mergeTime */ String.valueOf(merge.time),
            /* internalNodes */ String.valueOf(merge.mergeStats.internalNodes),
            /* externalNodes */ String.valueOf(merge.mergeStats.externalNodes),
            /* internalEdges */ String.valueOf(merge.mergeStats.internalEdges),
            /* externalEdges */ String.valueOf(merge.mergeStats.externalEdges),
            /* resolvedNodes */ String.valueOf(merge.mergeStats.resolvedNodes),
            /* resolvedEdges */ String.valueOf(merge.mergeStats.resolvedEdges),
            /* dependencies */ toString(merge.deps)};
    }

    private String calculateNumberOf(final String scope, final String nodesOrEdges,
                                     final MavenCoordinate coord,
                                     final Map<MavenCoordinate, List<MavenCoordinate>> depTree)
        throws NoSuchFieldException, IllegalAccessException {
        int allNodes = 0;

        if (scope.equals("resolved")) {
            for (final var singleMerge : mergeStats.get(coord)) {
                allNodes = allNodes + singleMerge.mergeStats.getField(scope + nodesOrEdges);
            }
        } else {
            for (final var depCoord : depTree.get(coord)) {
                allNodes =
                    allNodes +
                        cgPoolStats.get(depCoord).cgPoolGraphStats.getField(scope + nodesOrEdges);
            }
        }

        return String.valueOf(allNodes);
    }

    public static String toString(final List<MavenCoordinate> coords) {
        return coords.stream()
            .map(MavenCoordinate::getCoordinate)
            .collect(Collectors.joining(";"));
    }

    private Pair<Long,Long> calculateTotalMergeTime(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final MavenCoordinate coord) {

        long cgPoolTotalTime = 0;
        long mergeTotalTime = 0;

        for (final var depCoord : resolvedData.get(coord)) {
            cgPoolTotalTime = cgPoolTotalTime + cgPoolStats.get(depCoord).time;
        }
        for (final var merge : mergeStats.get(coord)) {
            mergeTotalTime = mergeTotalTime + merge.time;
        }

        return ImmutablePair.of(mergeTotalTime, cgPoolTotalTime);

    }

    public static String convertToCSV(final String[] data) {
        return Stream.of(data)
            .map(StatCounter::escapeSpecialCharacters)
            .collect(Collectors.joining(","));
    }

    public static void writeToCSV(final List<String[]> data,
                           final String resutPath) throws IOException {
        File csvOutputFile = new File(resutPath);
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            data.stream()
                .map(StatCounter::convertToCSV)
                .forEach(pw::println);
            for (String[] datum : data) {
                for (String s : datum) {
                    if (s.contains("/org.apache.http.impl.io/HttpRequestWriter.writeHeadLine(/org" +
                        ".apache.http/HttpRequest)/java.lang/VoidType")) {
                        System.out.println();
                    }
                }
            }
        }
    }

    public static String escapeSpecialCharacters(String data) {
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

}
