package evaluation;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.analyzer.javacgopal.data.exceptions.MissingArtifactException;
import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.merge.CallGraphUtils;
import eu.fasten.core.merge.LocalMerger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


public class StitchingEdgeTest {
    private static Map<MavenCoordinate, List<MavenCoordinate>> resolvedData;

    @BeforeAll
    public static void setUp() throws IOException {
        resolvedData = Evaluator.readResolvedDataCSV("src/main/java/evaluation/results" +
            "/inputMvnData/highly.connected1.resolved.csv");
    }

    @Test
    public void edgeTest() throws OPALException, IOException, MissingArtifactException {

        for (final var row : resolvedData.entrySet()) {
            final var opal = getOpalCG(row);
            final var deps = getDepsCG(row);


            StatCounter.writeToCSV(buildOverallCsv(groupBySource(compareMergeOPAL(merge(deps), opal))),
                 row.getKey().getCoordinate() + "Edges.csv");
        }

    }

    public static Map<String, List<Pair<String, String>>> compareMergeOPAL(
        final List<ExtendedRevisionJavaCallGraph> merges,
        ExtendedRevisionJavaCallGraph opal) {
        final var mergeInternals =
            augmentInternals(merges).stream().sorted().collect(Collectors.toList());
        final var mergeExternals =
            augmentExternals(merges).stream().sorted().collect(Collectors.toList());
        final var opalInternals =
            CallGraphUtils.convertToNodePairs(opal).get("internalTypes");
        final var opalExternals =
            CallGraphUtils.convertToNodePairs(opal).get("externalTypes");

        return Map.of("mergeInternals", mergeInternals,
            "mergeExternals", mergeExternals,
            "opalInternals", opalInternals,
            "opalExternals", opalExternals
        );

    }

    private static List<Pair<String, String>> augmentInternals(
        final List<ExtendedRevisionJavaCallGraph> rcgs) {
        List<Pair<String, String>> internals = new ArrayList<>();
        for (final var rcg : rcgs) {
            internals.addAll(CallGraphUtils.convertToNodePairs(rcg)
                .get("resolvedTypes"));
        }

        return internals;
    }

    private static List<Pair<String, String>> augmentExternals(
        final List<ExtendedRevisionJavaCallGraph> rcgs) {
        List<Pair<String, String>> externals = new ArrayList<>();
        for (final var rcg : rcgs) {
            externals.addAll(CallGraphUtils.convertToNodePairs(rcg)
                .get("internalTypes"));
            externals.addAll(CallGraphUtils.convertToNodePairs(rcg)
                .get("externalTypes"));
        }

        return externals;
    }



    static List<String[]> buildOverallCsv(final Map<String, Map<String, List<String>>> edges) {

            final List<String[]> dataLines = new ArrayList<>();
            dataLines.add(getHeader());
            dataLines.addAll(getScopeEdges(edges));
            return dataLines;

    }

    private static List<String[]> getScopeEdges(final Map<String, Map<String, List<String>>> edges) {
        final List<String[]> result = new ArrayList<>();

        final var opalInternals = edges.get("opalInternals");
        final var opalExternals = edges.get("opalExternals");
        final var mergeInternal = edges.get("mergeInternals");
        final var mergeExternals = edges.get("mergeExternals");
        int counter = 0;
        final var allSources = new HashSet<String>();
        allSources.addAll(opalInternals.keySet());
        allSources.addAll(opalExternals.keySet());
        allSources.addAll(mergeInternal.keySet());
        allSources.addAll(mergeExternals.keySet());

        for (final var source : allSources) {

            result.add(getContent(counter,
                source,
                opalInternals.getOrDefault(source, new ArrayList<>()),
                mergeInternal.getOrDefault(source, new ArrayList<>()),
                opalExternals.getOrDefault(source, new ArrayList<>()),
                mergeExternals.getOrDefault(source, new ArrayList<>())
            ));
            counter++;

        }

        return result;
    }

    private static String[] getContent(int counter,
                                       final String source,
                                       final List<String> opalInternal,
                                       final List<String> mergeInternal,
                                       final List<String> opalExternal,
                                       final List<String> mergeExternal) {

        return new String[] {
            /* num */ String.valueOf(counter),
            /* source */ source,
            /* opalInternal */ opalInternal.stream().sorted().collect(Collectors.joining( ",\n" )),
            /* count */ String.valueOf(opalInternal.size()),
            /* mergeInternal */ mergeInternal.stream().sorted().collect( Collectors.joining( ",\n" )),
            /* count */ String.valueOf(mergeInternal.size()),
            /* opalExternal */ opalExternal.stream().sorted().collect(Collectors.joining( ",\n" )),
            /* count */ String.valueOf(opalExternal.size()),
            /* mergeExternal */ mergeExternal.stream().sorted().collect( Collectors.joining( ",\n" )),
            /* count */ String.valueOf(mergeExternal.size())
        };
    }

    private static String[] getHeader() {
        return new String[] {
            "num", "source", "opalInternal",
            "count", "mergeInternal",
            "count", "opalExternal", "count", "mergeExternal", "count"
        };
    }

    static Map<String, Map<String, List<String>>> groupBySource(
        final Map<String, List<Pair<String, String>>> edges) {

        final Map<String, Map<String, List<String>>> result = new HashMap<>();

        for (Map.Entry<String, List<Pair<String, String>>> scope : edges.entrySet()) {
            Map<String, List<String>> edgesOfSource = new HashMap<>();

            for (Pair<String, String> edge : scope.getValue()) {
                edgesOfSource.computeIfAbsent(edge.getLeft(), s -> new ArrayList<>()).add(edge.getRight());
            }
            result.put(scope.getKey(), edgesOfSource);
        }
        return result;
    }

    private static List<ExtendedRevisionJavaCallGraph> merge(final List<ExtendedRevisionJavaCallGraph> deps) {

        final var cgMerger = new LocalMerger(deps);
        List<ExtendedRevisionJavaCallGraph> mergedRCGs = new ArrayList<>();
        for (final var rcg : deps) {
            mergedRCGs
                .add(cgMerger.mergeWithCHA(rcg));
        }
        return mergedRCGs;
    }

    private static List<ExtendedRevisionJavaCallGraph> getDepsCG(final Map.Entry<MavenCoordinate,
        List<MavenCoordinate>> row)
        throws OPALException, MissingArtifactException {
        final List<ExtendedRevisionJavaCallGraph> result = new ArrayList<>();

        for (final var dep : row.getValue()) {
            final var file = new MavenCoordinate.MavenResolver().downloadArtifact(dep, "jar");
            System.out.println("################# \n downloaded jar to:" + file.getAbsolutePath());

            final var opalCG = new CallGraphConstructor(file, "", "CHA");
            final var cg = new PartialCallGraph(opalCG);
            final var rcg = ExtendedRevisionJavaCallGraph.extendedBuilder()
                .graph(cg.getGraph())
                .product(dep.getProduct())
                .version(dep.getVersionConstraint())
                .classHierarchy(cg.getClassHierarchy())
                .nodeCount(cg.getNodeCount())
                .build();

            result.add(rcg);
        }
        return result;
    }



    private static ExtendedRevisionJavaCallGraph getOpalCG(final Map.Entry<MavenCoordinate,
        List<MavenCoordinate>> row)
        throws IOException, OPALException {
        final var tempDir = Evaluator.downloadToDir(row.getValue());
        System.out.println("################# \n downloaded jars to:" +tempDir.getAbsolutePath());
        final var cg = new PartialCallGraph(new CallGraphConstructor(tempDir, "", "CHA"));

        return ExtendedRevisionJavaCallGraph.extendedBuilder()
            .graph(cg.getGraph())
            .classHierarchy(cg.getClassHierarchy())
            .nodeCount(cg.getNodeCount())
            .build();
    }

}