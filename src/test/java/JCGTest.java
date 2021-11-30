import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.CallPreservationStrategy;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraphConstructor;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.data.opal.MavenArtifactDownloader;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.data.opal.exceptions.OPALException;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.merge.CallGraphUtils;
import evaluation.StatCounter;
import evaluation.StitchingEdgeTest;
import it.unimi.dsi.fastutil.Pair;

public class JCGTest {


    @Test
    public void func() throws OPALException, MissingArtifactException {
        var coord = MavenCoordinate.fromString("com.oracle.oci.sdk:oci-java-sdk-filestorage:1.23.0", "jar");
        final var file = new MavenArtifactDownloader(coord).downloadArtifact("jar");

        final var opalCG = new OPALCallGraphConstructor().construct(file, CGAlgorithm.AllocationSiteBasedPointsTo);
        final var cg = new PartialCallGraphConstructor().construct(opalCG, CallPreservationStrategy.INCLUDING_ALL_SUBTYPES);
        final var rcg = ExtendedRevisionJavaCallGraph.extendedBuilder()
            .graph(cg.graph)
            .product(coord.getProduct())
            .version(coord.getVersionConstraint())
            .classHierarchy(cg.classHierarchy)
            .nodeCount(cg.nodeCount)
            .build();
        System.out.println();
    }

    @Test
    public void testCFNE1()
        throws OPALException, IOException {

        final var testCates =
            new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("jcg")).getPath());
        final Map<String, Map<String, List<Pair<String, String>>>> result = new HashMap<>();
        for (final var testCase : Objects.requireNonNull(testCates.listFiles())) {
            for (final var bin : Objects.requireNonNull(testCase.listFiles())) {
                if (bin.getName().contains("bin")) {
                    final var opal = getRCG(bin, testCase.getName(), "");
                    final var thisTest =
                        Pair.of(ExtendedRevisionJavaCallGraph.toLocalDirectedGraph(opal),
                            opal.mapOfFullURIStrings().entrySet().stream().collect(
                                Collectors.toMap(e -> Long.valueOf(e.getKey()), Map.Entry::getValue)));

                    for (final var packag : Objects.requireNonNull(bin.listFiles())) {

                        List<ExtendedRevisionJavaCallGraph> rcgs = new ArrayList<>();
                        for (final var classfile : Objects.requireNonNull(packag.listFiles())) {
                            if (!classfile.getName().contains(" ")) {
                                rcgs.add(getRCG(classfile,
                                    classfile.getName().replace("$", "").replace(" ", ""),
                                    ""));
                            }
                        }

                        final var cgMerger = new CGMerger(rcgs);
                        List<Pair<DirectedGraph, Map<Long, String>>> mergedRCGs = new ArrayList<>();
                        for (final var rcg : rcgs) {
                            mergedRCGs
                                .add(Pair.of(cgMerger.mergeWithCHA(rcg), cgMerger.getAllUris()));
                        }
                        result.put(testCase.getName(), compareMergeOPAL(mergedRCGs, thisTest));
                    }
                }
            }
        }
        StatCounter.writeToCSV(buildOverallCsv(result), "jcgEdges.csv");

    }


    public static ExtendedRevisionJavaCallGraph getRCG(final File file, final String product,
                                                   final String version) throws OPALException {
        var opalCG = new OPALCallGraphConstructor().construct(file, CGAlgorithm.CHA);
        var cg = new PartialCallGraphConstructor().construct(opalCG, CallPreservationStrategy.ONLY_STATIC_CALLSITES);
        return ExtendedRevisionJavaCallGraph.extendedBuilder()
            .graph(cg.graph)
            .forge("mvn")
            .product(product)
            .version(version)
            .classHierarchy(cg.classHierarchy)
            .nodeCount(cg.nodeCount)
            .build();
    }
    public static Map<String, List<Pair<String, String>>> compareMergeOPAL(
        final List<Pair<DirectedGraph, Map<Long, String>>> merges,
        final Pair<DirectedGraph, Map<Long, String>> opal) {
        final var mergePairs =
            merges.stream().flatMap(directedGraphMapPair -> StitchingEdgeTest.convertToNodePairs(directedGraphMapPair).stream()).collect(Collectors.toList());
        final var opalPairs = StitchingEdgeTest.convertToNodePairs(opal);

        return Map.of("merge", mergePairs, "opal", opalPairs,
            "opal - merge", diff(opalPairs, mergePairs));
    }


    public static ArrayList<Pair<String, String>> diff(final List<Pair<String, String>> firstEdges,
                                                       final List<Pair<String, String>> secondEdges) {
        final var temp1 = new ArrayList<>(firstEdges);
        final var temp2 = new ArrayList<>(secondEdges);
        temp1.removeAll(temp2);
        return temp1;
    }


    private List<String[]> buildOverallCsv(
        final Map<String, Map<String, List<Pair<String, String>>>> testCases) {
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
                                final Map<String, List<Pair<String, String>>> edges,
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

    private String getSize(final Map<String, List<Pair<String, String>>> edges, final String key) {
        if (edges != null) {
            return String.valueOf(edges.get(key).size());
        }
        return "";
    }

    private String geteEdgeContent(final Map<String, List<Pair<String, String>>> edges,
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
