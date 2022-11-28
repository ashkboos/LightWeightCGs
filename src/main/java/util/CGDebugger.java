package util;

import static eu.fasten.core.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static java.lang.System.exit;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.fasten.core.data.ClassHierarchy;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.data.opal.exceptions.MissingArtifactException;
import eu.fasten.core.merge.CGMerger;
import eu.fasten.core.merge.CallGraphUtils;
import evaluation.CGEvaluator;
import evaluation.GeneratorEvaluator;
import evaluation.StatCounter;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CGDebugger {

    public static final Scanner CONSOLE = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println(
            "1- find call sites: 2- check parents and children: 3- find defined methods of type " +
                "4- find targets of source Uri in whole program");
        int analysis = CONSOLE.nextInt();
        if (analysis == 1) {
            System.out.println("Enter method Uri:");
            final var uri = CONSOLE.next();
            searchForCallSite(uri);
            exit(0);
        }

        if (analysis == 4) {
            generateAndFindTargets();
        }

        System.out.println("dependency set: ");
        final var depSet = CGDebugger.CONSOLE.next();
        final var deps = parseDepset(depSet);
        final var pcgs = toPCGs(deps);
        final var cgMerger = new CGMerger(pcgs);
        final var ch = cgMerger.getClassHierarchy();
        System.out.println("2- check parents and children: 3- find defined methods of type 4- " +
            "merge 5- get merged targets of source");
        DirectedGraph cg = new MergedDirectedGraph();
        BiMap<Long, String> uris = HashBiMap.create();
        while (CONSOLE.hasNextInt()) {
            analysis = CONSOLE.nextInt();
            if (analysis == 2) {
                checkParentsAndChildren(ch);
            }
            if (analysis == 3) {
                checkDefinedMethods(ch);
            }
            if (analysis == 4) {
                cg = cgMerger.mergeAllDeps();
                uris = cgMerger.getAllUris();
            }
            if (analysis == 5) {
                checkTargetsOfASource(cg, uris);
            }
            System.out.println(
                "2- check parents and children: 3- find defined methods of types 4- " +
                    "merge 5- get merged targets of source");
        }
    }

    private static void generateAndFindTargets() {

        System.out.println("Enter dependency set:");
        final var coords = CONSOLE.next();
        List<MavenCoordinate> deps = new ArrayList<>();
        for (String coord : coords.split(";")) {
            deps.add(MavenCoordinate.fromString(coord, "jar"));
        }
        final var generator = getGenerator();
        final var result =
            new GeneratorEvaluator(0, 0, true).generateMeasureTimeAndConvert(new StatCounter(), deps,
                deps.get(0), generator);
        while (true) {
            System.out.println("Enter method Uri:");
            final var uri = CONSOLE.next();
            final var sourceId = result.uris.entrySet().stream()
                .filter(longStringEntry -> longStringEntry.getValue().equals(uri)).findAny().get()
                .getKey();
            final var targetIds = result.dg.outgoingEdgesOf(sourceId);
            for (LongLongPair targetId : targetIds) {
                System.out.println(result.uris.get(targetId.secondLong()));
            }

        }
    }

    private static void checkTargetsOfASource(final DirectedGraph cg,
                                              final BiMap<Long, String> uris) {
        if (cg.nodes().isEmpty()) {
            System.out.println("Please merge first.");
            return;
        }
        System.out.println("Source method uri that you are looking for: ");
        final var uri = CGDebugger.CONSOLE.next();
        final var sourceId = uris.inverse().get(uri);
        for (final var edge : cg.outgoingEdgesOf(sourceId)) {
            System.out.println(uris.get(edge.secondLong()));
        }
    }

    private static void checkDefinedMethods(final ClassHierarchy ch) {
        System.out.println("type uri that you are looking for: ");
        final var uris = CGDebugger.CONSOLE.next();
        for (String uri : uris.split(",")) {
            System.out.println(uri + " :");
            System.out.println("defined methods: ");
            System.out.println(ch.getDefinedMethods().get(uri));
            System.out.println("abstract methods: ");
            System.out.println(ch.getAbstractMethods().get(uri));
        }

    }

    private static void checkParentsAndChildren(final ClassHierarchy ch) {
        System.out.println("type uri that you are looking for: ");
        final var uri = CGDebugger.CONSOLE.next();
        System.out.println("parents: ");
        var parents = ch.getUniversalParents().get(uri);
        if (parents == null) {
            parents = List.of("");
        }
        System.out.println(String.join(",", parents));
        System.out.println("children: ");
        var children = ch.getUniversalChildren().get(uri);
        if (children == null) {
            children = Set.of("");
        }
        System.out.println(String.join(",", children));
    }

    private static List<PartialJavaCallGraph> toPCGs(final List<MavenCoordinate> deps) {

        final var pcgs = new ConcurrentLinkedQueue<PartialJavaCallGraph>();
        final var generator = getGenerator();
        deps.parallelStream().forEach(dep -> {
            try {
                final var file = FilesUtils.download(dep);
                pcgs.add(CGUtils.generatePCG(new File[] {file}, dep, CGEvaluator.ALG,
                    ONLY_STATIC_CALLSITES, generator));
            } catch (MissingArtifactException ignored) {
            }
        });
        return new ArrayList<>(pcgs);
    }

    private static List<MavenCoordinate> parseDepset(String depSet) {
        final List<MavenCoordinate> deps = new ArrayList<>();
        for (final var coordString : depSet.split(";")) {
            deps.add(coord(coordString));
        }
        return deps;
    }

    private static void searchForCallSite(String s) {

        final var uri = FastenURI.create(s);
        final var coordString = uri.getProduct().concat(":").concat(uri.getVersion());
        final MavenCoordinate coord = coord(coordString);

        final String generator = getGenerator();
        final var file = FilesUtils.download(coord);

        System.out.println("Generating ...");
        final var rcg =
            CGUtils.generatePCG(new File[] {file}, coord, CGEvaluator.ALG, ONLY_STATIC_CALLSITES,
                generator);
        final var id = findId(uri, rcg);
        if (id == -1) {
            System.out.println("Didn't find the uri");
            exit(0);
        }
        findCallSites(rcg, id);
        try {
            CallGraphUtils.writeToFile("graph", JSONUtils.toJSONString(rcg));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println();
    }

    private static String getGenerator() {
        System.out.println("Generator:");
        return CGDebugger.CONSOLE.next();
    }

    private static MavenCoordinate coord(String coordString) {
        return MavenCoordinate.fromString(coordString, "jar");
    }

    private static void findCallSites(final PartialJavaCallGraph rcg, final Long id) {
        final var methods = rcg.mapOfAllMethods();
        for (final var callsite : rcg.getCallSites().entrySet()) {
            if (callsite.getKey().leftLong() == id) {
                System.out.println(methods.get(callsite.getKey().firstLong()).getSignature() +
                " -> " + methods.get(callsite.getKey().secondLong()).getSignature());
                System.out.println(callsite);
            }
        }
    }

    private static Long findId(final FastenURI uri, final PartialJavaCallGraph rcg) {
        for (final var node : rcg.mapOfFullURIStrings().entrySet()) {
            if (node.getValue().equals(uri.toString())) {
                return node.getKey();
            }
        }
        return -1L;
    }
}
