package jcg;/*
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

import static eu.fasten.core.data.CallPreservationStrategy.INCLUDING_ALL_SUBTYPES;
import static eu.fasten.core.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static util.CGUtils.generatePCG;

import data.ResultCG;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.merge.CallGraphUtils;
import eu.fasten.core.merge.CGMerger;
import evaluation.CGEvaluator;
import it.unimi.dsi.fastutil.Pair;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import util.CGUtils;
import util.CSVUtils;

public class JCGTest {

    @Test
    public void testCFNE1()
        throws OPALException {

        final var testCates =
            new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("jcg")).getPath());
        final Map<String, Map<String, Set<Pair<String, String>>>> result = new HashMap<>();
        for (final var testCase : Objects.requireNonNull(testCates.listFiles())) {
            for (final var bin : Objects.requireNonNull(testCase.listFiles())) {
                if (bin.getName().contains("bin")) {
                    final var testCaseName = testCase.getName();
                    final var coord = new MavenCoordinate(testCaseName, "", "", "");
                    final var opal = CGUtils.generatePCG(new File[] {bin}, coord,
                        String.valueOf(CGAlgorithm.CHA),
                        INCLUDING_ALL_SUBTYPES, Constants.opalGenerator);
                    final ResultCG thisTest =
                        new ResultCG(CGEvaluator.toLocalDirectedGraph(opal),
                            opal.mapOfFullURIStrings().entrySet().stream().collect(
                                Collectors.toMap(e -> Long.valueOf(e.getKey()),
                                    Map.Entry::getValue)));

                    for (final var packag : Objects.requireNonNull(bin.listFiles())) {

                        List<PartialJavaCallGraph> rcgs = new ArrayList<>();
                        for (final var classfile : Objects.requireNonNull(packag.listFiles())) {
                            if (!classfile.getName().contains(" ")) {
                                final var product =
                                    classfile.getName().replace("$", "").replace(" ", "");
                                rcgs.add(CGUtils.generatePCG(new File[] {classfile},
                                    MavenCoordinate.fromString("a:b:c", "jar"),
                                    String.valueOf(CGAlgorithm.CHA),
                                    ONLY_STATIC_CALLSITES, Constants.opalGenerator));
                            }
                        }

                        final var cgMerger = new CGMerger(rcgs);
                        List<ResultCG> mergedRCGs = new ArrayList<>();
                        for (final var rcg : rcgs) {
                            final var res =
                                new ResultCG(cgMerger.mergeWithCHA(rcg), cgMerger.getAllUris());
                            mergedRCGs.add(res);
                        }
                        result.put(testCaseName, compareMergeOPAL(mergedRCGs, thisTest));
                    }
                }
            }
        }
        CSVUtils.writeToCSV(buildOverallCsv(result), "jcgEdges.csv");

    }


    public static Map<String, Set<Pair<String, String>>> compareMergeOPAL(
        final List<ResultCG> merges,
        final ResultCG opal) {
        final var mergePairs =
            merges.stream().flatMap(
                directedGraphMapPair -> StitchingEdgeTest.convertToNodePairs(directedGraphMapPair)
                    .stream()).collect(Collectors.toSet());
        final var opalPairs = StitchingEdgeTest.convertToNodePairs(opal);

        return Map.of("merge", mergePairs, "opal", opalPairs,
            "opal - merge", diff(opalPairs, mergePairs));
    }


    public static Set<Pair<String, String>> diff(final Set<Pair<String, String>> firstEdges,
                                                       final Set<Pair<String, String>> secondEdges) {
        final var temp1 = new HashSet<>(firstEdges);
        final var temp2 = new HashSet<>(secondEdges);
        temp1.removeAll(temp2);
        return temp1;
    }


    private List<String[]> buildOverallCsv(
        final Map<String, Map<String, Set<Pair<String, String>>>> testCases) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(getHeader());
        int counter = 0;
        for (final var testCase : testCases.entrySet()) {
            dataLines.add(getContent(counter, testCase.getValue(), testCase.getKey()));
            counter++;
        }
        return dataLines;
    }


    private String[] getContent(final int counter,
                                final Map<String, Set<Pair<String, String>>> edges,
                                final String testCaseName) {
        return new String[] {
            /* number */ String.valueOf(counter),
            /* testCase */ testCaseName,
            /* merge */ geteEdgeContent(edges, "merge"),
            /* mergeNum */ getSize(edges, "merge"),
            /* opal */ geteEdgeContent(edges, "opal"),
            /* opalNum */ getSize(edges, "opal"),
        };
    }


    private String getSize(final Map<String, Set<Pair<String, String>>> edges,
                           final String key) {
        if (edges != null) {
            return String.valueOf(edges.get(key).size());
        }
        return "";
    }


    private String geteEdgeContent(final Map<String, Set<Pair<String, String>>> edges,
                                   final String scope) {
        return CallGraphUtils.toStringEdges(edges.get(scope).stream()
            .map(stringStringPair -> org.apache.commons.lang3.tuple.Pair.of(stringStringPair.left(),
                stringStringPair.right())).collect(
                Collectors.toList()));
    }


    private String[] getHeader() {
        return new String[] {
            "num", "testCase", "merge", "mergeNum", "opal", "opalNum"
        };
    }

}
