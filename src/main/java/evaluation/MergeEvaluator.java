package evaluation;

import static eu.fasten.core.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static evaluation.GeneratorEvaluator.extractGeneratorFromPath;
import static evaluation.GeneratorEvaluator.measureTime;
import static util.FilesUtils.JAVA_8_COORD;

import data.ResultCG;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.merge.CGMerger;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CGUtils;
import util.FilesUtils;

public class MergeEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(MergeEvaluator.class);
    public static final String RT_JAR_JSON = "-rt-jar.json";
    private final int warmUp;
    private final int iterations;
    private final boolean includeJava;

    public MergeEvaluator(int warmUp, int iterations, boolean includeJava) {
        this.warmUp = warmUp;
        this.iterations = iterations;
        this.includeJava = includeJava;
    }

    public ResultCG evaluateMerge(final String outPath,
                                  final List<MavenCoordinate> coords) {
        StatCounter statCounter = new StatCounter();
        final var generator = extractGeneratorFromPath(outPath);
        if (includeJava) {
            coords.add(JAVA_8_COORD);
        }
        final ResultCG cg =
            createCGPoolAndMergeDepSet(coords, statCounter, new HashMap<>(), generator);
        statCounter.concludeMerge(outPath);
        return cg;
    }

    public ResultCG createCGPoolAndMergeDepSet(
        final List<MavenCoordinate> depSet, StatCounter statCounter,
        final Map<MavenCoordinate, PartialJavaCallGraph> cgPool, final String generator) {

        createCGPoolAndMeasure(depSet, statCounter, cgPool, generator);
        return mergeDepSetAndMeasureTime(statCounter, depSet, depSet.stream().map(cgPool::get)
            .filter(Objects::nonNull).collect(Collectors.toList()));
    }

    private void createCGPoolAndMeasure(final List<MavenCoordinate> depSet, StatCounter statCounter,
                                        Map<MavenCoordinate, PartialJavaCallGraph> cgPool,
                                        final String generator) {
        for (final var dep : depSet) {
            if (!cgPool.containsKey(dep)) {
                addToCGPoolAndMeasureTime(statCounter, cgPool, dep, generator);
            }
        }
    }

    private void addToCGPoolAndMeasureTime(StatCounter statCounter,
                                           Map<MavenCoordinate, PartialJavaCallGraph> cgPool,
                                           final MavenCoordinate dep,
                                           final String generator) {
        if (cgPool.containsKey(dep)) {
            statCounter.addExistingToCGPool(dep);
            return;
        }
        try {
            final var rcg = generateAndMeasure(statCounter, dep, generator);
            cgPool.put(dep, rcg);
        } catch (Exception e) {
            logger.warn("Exception during adding dep {} to cg pool!", dep.getCoordinate(), e);
            statCounter.addNewCGtoPool(dep, 0, new StatCounter.GraphStats());
        }
    }

    private PartialJavaCallGraph generateAndMeasure(StatCounter statCounter,
                                                    final MavenCoordinate coordinate,
                                                    final String generator) {
        final var file = FilesUtils.download(coordinate);
        final var rcg =
            CGUtils.generatePCG(new File[] {file}, coordinate, CGEvaluator.ALG,
                ONLY_STATIC_CALLSITES, generator, true);

        statCounter.addNewCGtoPool(coordinate,
            new GeneratorEvaluator(warmUp, iterations, false).measureGenerationTime(file,
                generator), new StatCounter.GraphStats(rcg));

        return rcg;
    }

    private ResultCG mergeDepSetAndMeasureTime(StatCounter statCounter,
                                               final List<MavenCoordinate> depSet,
                                               final List<PartialJavaCallGraph> deps) {

        measureUCH(statCounter, depSet, deps);
        final var cgMerger = new CGMerger(deps);
        return new ResultCG(measureMerge(statCounter, depSet, cgMerger), cgMerger.getAllUris());

    }

    private DirectedGraph measureMerge(StatCounter statCounter,
                                       final List<MavenCoordinate> depSet,
                                       final CGMerger cgMerger) {
        final var dg = cgMerger.mergeAllDeps();
        statCounter.addMerge(depSet.get(0), depSet,
            measureTime(cgMerger::mergeAllDeps, warmUp, iterations),
            new StatCounter.GraphStats(dg));
        return dg;
    }

    private void measureUCH(StatCounter statCounter, final List<MavenCoordinate> depSet,
                            final List<PartialJavaCallGraph> deps) {
        final var uchTime = measureTime(() -> new CGMerger(deps), warmUp, iterations);
        statCounter.addUCH(depSet.get(0), uchTime);
    }

}
