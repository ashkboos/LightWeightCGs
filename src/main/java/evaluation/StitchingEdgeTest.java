package evaluation;

import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.merge.CGMerger;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
        final Pair<DirectedGraph, Map<Long, String>> merge,
        final Pair<DirectedGraph, Map<Long, String>> opal) {
        final var mergePairs = convertToNodePairs(merge);
        final var opalPairs = convertToNodePairs(opal);

        return Map.of("merge", mergePairs, "opal", opalPairs);
    }

    public static List<Pair<String, String>> convertToNodePairs(
        Pair<DirectedGraph, Map<Long, String>> opal) {
        List<Pair<String, String>> result = new ArrayList<>();
        for (LongLongPair edge : opal.left().edgeSet()) {
            final var firstMethod =
                FastenURI.create(opal.right().get(edge.firstLong())).getEntity();
            final var secondMethod =
                FastenURI.create(opal.right().get(edge.secondLong())).getEntity();
            result.add(ObjectObjectImmutablePair.of(firstMethod, secondMethod));
        }
        return result;
    }

    static List<String[]> buildOverallCsv(final Map<String, Map<String, List<String>>> edges) {

            final List<String[]> dataLines = new ArrayList<>();
            dataLines.add(getHeader());
            dataLines.addAll(getScopeEdges(edges));
            return dataLines;

    }

    private static List<String[]> getScopeEdges(final Map<String, Map<String, List<String>>> edges) {
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

    private static String[] getContent(int counter,
                                       final String source,
                                       final List<String> opal,
                                       final List<String> merge) {

        return new String[] {
            /* num */ String.valueOf(counter),
            /* source */ source,
            /* opal */ opal.stream().sorted().collect(Collectors.joining( ",\n" )),
            /* count */ String.valueOf(opal.size()),
            /* merge */ merge.stream().sorted().collect( Collectors.joining( ",\n" )),
            /* count */ String.valueOf(merge.size())};
    }

    private static String[] getHeader() {
        return new String[] {
            "num", "source", "opal",
            "count", "merge",
            "count" };
    }

    static Map<String, Map<String, List<String>>> groupBySource(
        final Map<String, List<Pair<String, String>>> edges) {

        final Map<String, Map<String, List<String>>> result = new HashMap<>();

        for (Map.Entry<String, List<Pair<String, String>>> scope : edges.entrySet()) {
            final Map<String, List<String>> edgesOfSource = new HashMap<>();

            for (Pair<String, String> edge : scope.getValue()) {
                edgesOfSource.computeIfAbsent(edge.left(), s -> new ArrayList<>()).add(edge.right());
            }
            result.put(scope.getKey(), edgesOfSource);
        }
        return result;
    }

    private static Pair<DirectedGraph, Map<Long, String>> merge(final List<ExtendedRevisionJavaCallGraph> deps) {

        final var cgMerger = new CGMerger(deps);
        return Pair.of(cgMerger.mergeAllDeps(), cgMerger.getAllUris());
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



    private static Pair<DirectedGraph, Map<Long, String>> getOpalCG(final Map.Entry<MavenCoordinate,
        List<MavenCoordinate>> row)
        throws IOException, OPALException {
        final var tempDir = Evaluator.downloadToDir(row.getValue());
        System.out.println("################# \n downloaded jars to:" +tempDir.getAbsolutePath());
        final var cg = new PartialCallGraph(new CallGraphConstructor(tempDir, "", "CHA"));

        final var ercg = ExtendedRevisionJavaCallGraph.extendedBuilder()
            .graph(cg.getGraph())
            .classHierarchy(cg.getClassHierarchy())
            .nodeCount(cg.getNodeCount())
            .build();
        final var map = ercg.mapOfFullURIStrings().entrySet().stream()
            .collect(Collectors.toMap(e -> Long.valueOf(e.getKey()), Map.Entry::getValue));
        return Pair.of(ExtendedRevisionJavaCallGraph.toLocalDirectedGraph(ercg), map);
    }

}