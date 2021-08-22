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
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Map<String, Pair<String, String>> logs;


    public StatCounter() {
        UCHTime = new ConcurrentHashMap<>();
        cgPoolStats = new ConcurrentHashMap<>();
        opalStats = new ConcurrentHashMap<>();
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
         opalLogString = readFromLast(opalLog[0], 20);
        }
        if (mergeLog != null){
            mergeLoString = readFromLast(mergeLog[0], 20);
        }
        this.logs.put(coord, ImmutablePair.of(opalLogString, mergeLoString));
    }

    public String readFromLast(final File file, final int lines){
        List<String> result = new ArrayList<>();
        int readLines = 0;
        StringBuilder builder = new StringBuilder();
        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(file, "r");
            long fileLength = file.length() - 1;
            // Set the pointer at the last of the file
            randomAccessFile.seek(fileLength);
            for(long pointer = fileLength; pointer >= 0; pointer--){
                randomAccessFile.seek(pointer);
                char c;
                // read from the last one char at the time
                c = (char)randomAccessFile.read();
                // break when end of the line
                if(c == '\n'){
                    readLines++;
                    if(readLines == lines)
                        break;
                }
                builder.append(c);
            }
            // Since line is read from the last so it
            // is in reverse so use reverse method to make it right
            builder.reverse();
            result.add(builder.toString());
//            System.out.println("Line - " + builder.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }finally{
            if(randomAccessFile != null){
                try {
                    randomAccessFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        Collections.reverse(result);
        return String.join("\n", result);
    }

    public static class SourceStats {
//        final private String source;
        final private double precision;
        final private double recall;
//        final private int OPAL;
//        final private int merge;
//        final private int intersect;

        public SourceStats(final String source, final double precision, final double recall,
                           final int OPAL,
                           final int merge, final int intersect) {
//            this.source = source;
            this.precision = precision;
            this.recall = recall;
//            this.OPAL = OPAL;
//            this.merge = merge;
//            this.intersect = intersect;
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
        final private Integer nodes, edges;


        public GraphStats(final DirectedGraph rcg) {
            if (rcg != null) {
                this.nodes = rcg.nodes().size();
                this.edges = rcg.edgeSet().size();
            }else {
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

    public synchronized void addOPAL(final MavenCoordinate coord,
                        final OpalStats os) {
        if (opalStats.containsKey(coord)) {
            logger.warn("The coordinate was already generated by OPAL {}", coord);
        }else {
            opalStats.put(coord, os);
        }
    }

    public synchronized void addOPAL(MavenCoordinate coord, final long time,
                        final DirectedGraph rcg) {
        if (opalStats.containsKey(coord)) {
            logger.warn("The coordinate was already generated by OPAL {}", coord);
        }
        opalStats.put(coord, new OpalStats(time, new GraphStats(rcg)));
    }
    public synchronized void addUCH(final MavenCoordinate coord, final Long time){
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

    public void concludeMerge(final String resultPath)
        throws IOException{

        writeToCSV(buildCGPoolCSV(), resultPath + "/CGPool.csv");
        writeToCSV(buildMergeCSV(), resultPath + "/Merge.csv");
    }
    public void concludeOpal(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                            final String resultPath) throws IOException{

        writeToCSV(buildOpalCSV(resolvedData), resultPath + "/resultOpal.csv");
    }

    public void concludeLogs(final String outPath)
        throws IOException, NoSuchFieldException, IllegalAccessException {
        writeToCSV(buildLogCsv(), outPath + "/Logs.csv");
    }

    private List<String[]> buildLogCsv() {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeaderOf("Log"));
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
                            final String resultPath)
        throws IOException, NoSuchFieldException, IllegalAccessException {

        writeToCSV(buildOverallCsv(resolvedData), resultPath + "/Overall.csv");
        writeToCSV(buildAccuracyCsv(resolvedData), resultPath + "/accuracy.csv");
    }

    private List<String[]> buildAccuracyCsv(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
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
//                /* source */ String.valueOf(sourceStat.source),
                /* precision */ String.valueOf(sourceStat.precision),
                /* recall */ String.valueOf(sourceStat.recall),
//                /* emptyOPAL */ String.valueOf(sourceStat.OPAL),
//                /* emptyMerge */ String.valueOf(sourceStat.merge),
//                /* emptyBoth */ String.valueOf(sourceStat.intersect),
                /* dependencies */ toString(resolvedData.get(coord))
            });
            counter++;
        }
        return result;
    }

    private List<String[]> buildOpalCSV(
        Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {

        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeaderOf("Opal"));

        int counter = 0;
        for (var opal : opalStats.entrySet()) {
            var opalStats = opal.getValue();
            dataLines.add(getContentOfOpal(resolvedData, counter, opal.getKey(), opalStats));
            counter++;
        }
        return dataLines;
    }

    private String[] getContentOfOpal(
        Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final int counter, MavenCoordinate coord,
        OpalStats opalStats) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* opalTime */ String.valueOf(opalStats.time),
            /* nodes */ String.valueOf(opalStats.opalGraphStats.nodes),
            /* edges */ String.valueOf(opalStats.opalGraphStats.edges),
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
                                       final OpalStats opalStats)
        throws NoSuchFieldException, IllegalAccessException {
        final var mergePool = calculateTotalMergeTime(depTree, coord);
        return new String[] {
            /* number */ String.valueOf(counter),
            /* coordinate */ coord.getCoordinate(),
            /* opalTime */ opalStats == null ? "" : String.valueOf(opalStats.time),
            /* totalMergeTime */ String.valueOf(mergePool.getLeft() + mergePool.getRight()),
            /* cgPool */ String.valueOf(mergePool.getRight()),
            /* mergeTime */ String.valueOf(mergePool.getLeft()),
            /* UCHTime */ String.valueOf(UCHTime.get(coord)),
            /* opalNodes */ opalStats == null ? "-1" :
            String.valueOf(opalStats.opalGraphStats.nodes),
            /* opalEdges */ opalStats == null ? "-1" :
            String.valueOf(opalStats.opalGraphStats.edges),
            /* mergeNodes */ calculateNumberOf("nodes", coord, depTree),
            /* mergeEdges */ calculateNumberOf("edges", coord, depTree),
            /* dependencies */ toString(depTree.get(coord))};
    }

    private String[] getHeaderOf(final String CSVName) {
        if (CSVName.equals("Overall")) {
            return new String[] {"number", "coordinate", "opalTime",
                "totalMergeTime", "cgPool", "mergeTime", "UCHTime",
                "opalNodes", "opalEdges", "mergeNodes", "mergeEdges",
                "dependencies"};

        } else if (CSVName.equals("Opal")) {
            return new String[] {"number", "coordinate", "opalTime",
                "nodes", "edges", "dependencies"};

        } else if (CSVName.equals("CGPool")) {
            return new String[] {"number", "coordinate", "occurrence", "isolatedRevisionTime",
                "nodes", "edges"};

        } else if (CSVName.equals("Accuracy")) {
            return new String[] {"number", "coordinate", "source", "precision", "recall",
                "OPAL", "Merge", "intersection", "dependencies"};

        } else if (CSVName.equals("Log")) {
            return new String[] {"number", "coordinate", "opalLog", "mergeLog"};
        }

        //Merge
        return new String[] {"number", "rootCoordinate", "artifact", "mergeTime", "uchTime",
            "nodes", "edges", "dependencies"};
    }

    private String[] getMergeContent(final int counter, final MavenCoordinate rootCoord,
                                     final MergeTimer merge) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* rootCoordinate */ rootCoord.getCoordinate(),
            /* artifact */ String.valueOf(merge.artifact.getCoordinate()),
            /* mergeTime */ String.valueOf(merge.time),
            /* uchTime */ String.valueOf(this.UCHTime.get(rootCoord)),
            /* nodes */ String.valueOf(merge.mergeStats.nodes),
            /* edges */ String.valueOf(merge.mergeStats.edges),
            /* dependencies */ toString(merge.deps)};
    }

    private String calculateNumberOf(final String nodesOrEdges,
                                     final MavenCoordinate coord,
                                     final Map<MavenCoordinate, List<MavenCoordinate>> depTree)
        throws NoSuchFieldException, IllegalAccessException {
        int allNodes = 0;

        if (mergeStats.containsKey(coord)) {
            for (final var singleMerge : mergeStats.get(coord)) {
                allNodes = allNodes + singleMerge.mergeStats.getField(nodesOrEdges);
            }
        }else {
            return "-1";
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
        }
        catch (Exception e){
            logger.error("Exception occurred", e);
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
