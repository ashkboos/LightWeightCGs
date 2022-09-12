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
import static evaluation.CGEvaluator.getDepsOnly;

import data.ResultCG;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.OPALPartialCallGraphConstructor;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.maven.utils.MavenUtilities;
import eu.fasten.core.merge.CGMerger;
import evaluation.CGEvaluator;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
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

    @NotNull
    public static Map<String, List<Pair<String, String>>> convertOpalAndMergeToNodePairs(
        @NotNull final ResultCG merge, @NotNull final ResultCG opal) {
        final var mergePairs = convertToNodePairs(merge);
        final var opalPairs = convertToNodePairs(opal);

        return Map.of("merge", mergePairs, "opal", opalPairs);
    }

    @NotNull
    public static List<Pair<String, String>> convertToNodePairs(@NotNull final ResultCG cg) {
        List<Pair<String, String>> result = new ArrayList<>();
        for (final var edge : cg.dg.edgeSet()) {
            final var firstMethod = FastenURI.create(cg.uris.get(edge.firstLong())).getEntity();
            final var secondMethod = FastenURI.create(cg.uris.get(edge.secondLong())).getEntity();
            result.add(ObjectObjectImmutablePair.of(firstMethod, secondMethod));
        }
        return result;
    }

    @NotNull
    static List<String[]> buildOverallCsv(@NotNull final Map<String, Map<String, List<String>>> edges) {

        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeader());
        dataLines.addAll(getScopeEdges(edges));
        return dataLines;

    }

    @NotNull
    private static List<String[]> getScopeEdges(
        @NotNull final Map<String, Map<String, List<String>>> edges) {
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
                opal.getOrDefault(source, new ArrayList<>()),
                merge.getOrDefault(source, new ArrayList<>())));
            counter++;

        }

        return result;
    }

    @NotNull
    private static String[] getContent(int counter,
                                       final String source,
                                       @NotNull final List<String> opal,
                                       @NotNull final List<String> merge) {

        return new String[] {
            /* num */ String.valueOf(counter),
            /* source */ source,
            /* opal */ opal.stream().sorted().collect(Collectors.joining(",\n")),
            /* count */ String.valueOf(opal.size()),
            /* merge */ merge.stream().sorted().collect(Collectors.joining(",\n")),
            /* count */ String.valueOf(merge.size())};
    }

    @NotNull
    private static String[] getHeader() {
        return new String[] {
            "num", "source", "opal",
            "count", "merge",
            "count"};
    }

    @NotNull
    public static Map<String, Map<String, List<String>>> groupBySource(
        @NotNull final Map<String, List<Pair<String, String>>> edges) {

        final Map<String, Map<String, List<String>>> result = new HashMap<>();

        for (final var generator : edges.entrySet()) {
            final Map<String, List<String>> edgesOfSource = new HashMap<>();

            for (Pair<String, String> edge : generator.getValue()) {
                edgesOfSource.computeIfAbsent(edge.left(), s -> new ArrayList<>())
                    .add(edge.right());
            }
            result.put(generator.getKey(), edgesOfSource);
        }
        return result;
    }

    @NotNull
    private static ResultCG merge(@NotNull final List<PartialJavaCallGraph> deps) {

        final var cgMerger = new CGMerger(deps);
        return new ResultCG(cgMerger.mergeAllDeps(), cgMerger.getAllUris());
    }

    @NotNull
    private static List<PartialJavaCallGraph> getDepsCG(@NotNull final Map.Entry<MavenCoordinate,
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


    @NotNull
    private static ResultCG getOpalCG(@NotNull final Map.Entry<MavenCoordinate,
        List<MavenCoordinate>> row)
        throws IOException, OPALException {

        final var tempDir = FilesUtils.downloadToDir(row.getValue());
        System.out.println("################# \n downloaded jars to:" + tempDir);

        final var depFiles =
            FilesUtils.downloadToDir(getDepsOnly(row.getValue()));
        final var rootFile = FilesUtils.download(row.getValue().get(0));
        System.out.printf("\n##################### \n DepSet %s downloaded to %s dep files %s .." +
            ".", row.getValue(), rootFile.getAbsolutePath(), Arrays.toString(depFiles));

        final var opalCG = new OPALCallGraphConstructor().construct(new File[] {rootFile}, tempDir, CGAlgorithm.CHA);

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