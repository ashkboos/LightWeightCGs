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

package jcg;

import static eu.fasten.core.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static util.InputDataUtils.getDepsOnly;

import data.ResultCG;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.OPALPartialCallGraphConstructor;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import evaluation.CGEvaluator;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import util.CSVUtils;
import util.FilesUtils;


public class StitchingEdgeTest {
    private static Map<MavenCoordinate, List<MavenCoordinate>> resolvedData;

    @BeforeAll
    public static void setUp() throws IOException {
        resolvedData = CSVUtils.readResolvedCSV("src/main/java/evaluation/results" +
            "/inputMvnData/highly.connected1.resolved.csv");
    }

    @Test
    public void edgeTest() throws OPALException, IOException, MissingArtifactException {

        for (final var row : resolvedData.entrySet()) {
            final ResultCG opal = getOpalCG(row);
            final var deps = getDepsCG(row);

            CSVUtils.writeToCSV(buildOverallCsv(groupBySource(
                    convertOpalAndMergeToNodePairs(merge(deps), opal))),
                row.getKey().getCoordinate() + "Edges.csv");
        }

    }


    public static Map<String, Set<Pair<String, String>>> convertOpalAndMergeToNodePairs(
        final ResultCG merge, final ResultCG opal) {
        final var result = new Object2ObjectOpenHashMap<String, Set<Pair<String, String>>>();
        result.put("merge", convertToNodePairs(merge));
        result.put("opal", convertToNodePairs(opal));
        return result;
    }


    public static Set<Pair<String, String>> convertToNodePairs(final ResultCG cg) {
        Set<Pair<String, String>> result = ConcurrentHashMap.newKeySet();
        cg.dg.edgeSet().parallelStream().forEach(edge -> {
            final var firstMethod = cg.uris.get(edge.firstLong());
            final var secondMethod = cg.uris.get(edge.secondLong());
            if (firstMethod.contains("scala") || secondMethod.contains("scala")) {
                return;
            }
            result.add(ObjectObjectImmutablePair.of(firstMethod, secondMethod));
        });
        return result;
    }


    static List<String[]> buildOverallCsv(final Map<String, Map<String, Set<String>>> edges) {

        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeader());
        dataLines.addAll(getScopeEdges(edges));
        return dataLines;

    }


    private static List<String[]> getScopeEdges(
        final Map<String, Map<String, Set<String>>> edges) {
        final List<String[]> result = new ArrayList<>();

        final var opal = edges.get("opal");
        final var merge = edges.get("merge");
        int counter = 0;
        final var allSources = new HashSet<String>();
        allSources.addAll(opal.keySet());
        allSources.addAll(merge.keySet());

        for (final var source : allSources) {

            result.add(getContent(counter,
                source,
                opal.getOrDefault(source, new HashSet<>()),
                merge.getOrDefault(source, new HashSet<>())));
            counter++;

        }

        return result;
    }


    private static String[] getContent(int counter,
                                       final String source,
                                       final Set<String> opal,
                                       final Set<String> merge) {

        return new String[] {
            /* num */ String.valueOf(counter),
            /* source */ source,
            /* opal */ opal.stream().sorted().collect(Collectors.joining(",\n")),
            /* count */ String.valueOf(opal.size()),
            /* merge */ merge.stream().sorted().collect(Collectors.joining(",\n")),
            /* count */ String.valueOf(merge.size())};
    }


    private static String[] getHeader() {
        return new String[] {
            "num", "source", "opal",
            "count", "merge",
            "count"};
    }


    public static Map<String, Map<String, Set<String>>> groupBySource(
        final Map<String, Set<Pair<String, String>>> edges) {

        final Map<String, Map<String, Set<String>>> result = new Object2ObjectOpenHashMap<>();

        for (final var generator : edges.entrySet()) {
            final Map<String, Set<String>> edgesOfSource = new ConcurrentHashMap<>();

            generator.getValue().parallelStream().forEach(edge -> {
                edgesOfSource.computeIfAbsent(edge.left(), s -> ConcurrentHashMap.newKeySet())
                    .add(edge.right());
            });
            result.put(generator.getKey(), edgesOfSource);
        }
        return result;
    }


    private static ResultCG merge(final List<PartialJavaCallGraph> deps) {

        final var cgMerger = new CGMerger(deps);
        return new ResultCG(cgMerger.mergeAllDeps(), cgMerger.getAllUris());
    }


    private static List<PartialJavaCallGraph> getDepsCG(final Map.Entry<MavenCoordinate,
        List<MavenCoordinate>> row)
        throws OPALException, MissingArtifactException {
        final List<PartialJavaCallGraph> result = new ArrayList<>();

        for (final var dep : row.getValue()) {
            final var file =
                new MavenArtifactDownloader(dep).downloadArtifact(
                    MavenUtilities.MAVEN_CENTRAL_REPO);

            System.out.println("################# \n downloaded jar to:" + file.getAbsolutePath());

            final var opalCG = new OPALCallGraphConstructor().construct(file, CGAlgorithm.CHA);

            final var partialCallGraph = new OPALPartialCallGraphConstructor().construct(opalCG,
                ONLY_STATIC_CALLSITES);

            final var rcg = new PartialJavaCallGraph(Constants.mvnForge, dep.getProduct(),
                dep.getVersionConstraint(), -1,
                Constants.opalGenerator,
                partialCallGraph.classHierarchy,
                partialCallGraph.graph);

            result.add(rcg);
        }
        return result;
    }


    private static ResultCG getOpalCG(final Map.Entry<MavenCoordinate,
        List<MavenCoordinate>> row)
        throws IOException, OPALException {

        final var filesUtils = new FilesUtils();
        final var tempDir = filesUtils.downloadToDir(row.getValue());
        System.out.println("################# \n downloaded jars to:" + tempDir);

        final var depFiles =
            filesUtils.downloadToDir(getDepsOnly(row.getValue()));
        final var rootFile = filesUtils.download(row.getValue().get(0));
        System.out.printf("\n##################### \n DepSet %s downloaded to %s dep files %s .." +
            ".", row.getValue(), rootFile.getAbsolutePath(), Arrays.toString(depFiles));

        final var opalCG = new OPALCallGraphConstructor().construct(new File[] {rootFile}, tempDir,
            CGAlgorithm.CHA);

        final var partialCallGraph = new OPALPartialCallGraphConstructor().construct(opalCG,
            ONLY_STATIC_CALLSITES);

        final var rcg = new PartialJavaCallGraph(Constants.mvnForge, "",
            "", -1,
            Constants.opalGenerator,
            partialCallGraph.classHierarchy,
            partialCallGraph.graph);

        final var map = rcg.mapOfFullURIStrings().entrySet().stream()
            .collect(Collectors.toMap(e -> Long.valueOf(e.getKey()), Map.Entry::getValue));
        return new ResultCG(CGEvaluator.toLocalDirectedGraph(rcg), map);
    }

}