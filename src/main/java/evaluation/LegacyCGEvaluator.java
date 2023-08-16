package evaluation;

import static jcg.StitchingEdgeTest.convertOpalAndMergeToNodePairs;
import static jcg.StitchingEdgeTest.groupBySource;

import data.ResultCG;
import eu.fasten.analyzer.javacgwala.core.RevisionCallGraph;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.utils.FastenUriUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.tongfei.progressbar.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HelperScripts;

public class LegacyCGEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(LegacyCGEvaluator.class);

    public static void measureAll(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final String outPath,
        final int threshold) {

        final var statCounter = new StatCounter();

        final var uniquesCommons = HelperScripts.countCommonCoordinates(resolvedData);
        logger.info("#{} redundant packages and #{} unique packages, #{} all packages",
            uniquesCommons.get(1), uniquesCommons.get(0), uniquesCommons.get(2));

        if (uniquesCommons.get(1) < threshold) {
            logger.info("Number of redundant packages is less than threshold #{}", threshold);

        } else {
            runOPALandMerge(resolvedData, statCounter, outPath);

            statCounter.concludeMerge(outPath);
            statCounter.concludeGenerator(outPath);
            statCounter.concludeAll(resolvedData, outPath);
            logger.info("Wrote results of evaluation into file successfully!");
        }
    }

    private static void runOPALandMerge(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final StatCounter statCounter, final String path) {
        final Map<MavenCoordinate, Set<MavenCoordinate>> remainedDependents =
            getDependents(resolvedData);
        final Map<MavenCoordinate, PartialJavaCallGraph> cgPool = new HashMap<>();
        ProgressBar pb = new ProgressBar("Measuring stats", resolvedData.entrySet().size());
        pb.start();

        for (final var row : resolvedData.entrySet()) {
            final var toMerge = row.getKey();
            final ResultCG merge =
                new MergeEvaluator(1, 2, false, path).createCGPoolAndMergeDepSet(resolvedData.get(toMerge),
                    statCounter, cgPool,
                    Constants.opalGenerator);
            pb.step();
            for (final var dep : resolvedData.get(toMerge)) {
                if (remainedDependents.get(dep) != null) {
                    if (remainedDependents.get(dep).isEmpty()) {
                        remainedDependents.get(dep).remove(toMerge);
                        cgPool.remove(dep);
                        System.gc();
                    }
                }
            }
            final ResultCG opal = new GeneratorEvaluator(1, 2, false).generateAndCatch(statCounter,
                row.getValue(), Constants.opalGenerator);
            if (opal != null) {
                statCounter.addAccuracy(toMerge,
                    CGEvaluator.calcPrecisionRecall(groupBySource(
                        convertOpalAndMergeToNodePairs(merge, opal))));
            }
            System.gc();
        }
        pb.stop();

    }


    private static Map<MavenCoordinate, Set<MavenCoordinate>> getDependents(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final Map<MavenCoordinate, Set<MavenCoordinate>> result = new HashMap<>();
        for (final var entry : resolvedData.entrySet()) {
            for (final var dep : entry.getValue()) {
                final var dependents = result.getOrDefault(dep, new HashSet<>());
                dependents.add(MavenCoordinate.fromString(entry.getKey().getCoordinate(), "jar"));
                result.put(MavenCoordinate.fromString(dep.getCoordinate(), "jar"), dependents);
            }
        }
        return result;
    }

    private static ResultCG toDirectedCGAndUris(final RevisionCallGraph oCG) {
        final var result = new ResultCG();
        for (final var intInt : oCG.getGraph().getInternalCalls()) {
            final var source = (long) intInt.get(0);
            final var target = (long) intInt.get(1);
            result.dg.addVertex(source);
            result.dg.addVertex(target);
            result.dg.addEdge(source, target);
        }
        for (final var type : oCG.getClassHierarchy().entrySet()) {
            for (final var nodeEntry : type.getValue().getMethods().entrySet()) {
                final var fullUri = FastenUriUtils.generateFullFastenUri(Constants.mvnForge,
                    oCG.product, oCG.version, nodeEntry.getValue().toString());
                result.uris.put(Long.valueOf(nodeEntry.getKey()), fullUri);
            }
        }
        return result;
    }
}
