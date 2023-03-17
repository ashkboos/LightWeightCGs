package evaluation;

import static eu.fasten.core.utils.TypeToJarMapper.createTypeUriToCoordMap;
import static util.FilesUtils.JAVA_8_COORD;

import com.google.common.collect.BiMap;
import data.ResultCG;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CGUtils;
import util.FilesUtils;

public class GeneratorEvaluator {
    private static final Logger logger = LoggerFactory.getLogger(GeneratorEvaluator.class);
    private final int warmUp;
    private final int iterations;
    private final boolean includeJava;

    public GeneratorEvaluator(int warmUp, int iterations, boolean includeJava) {
        this.warmUp = warmUp;
        this.iterations = iterations;
        this.includeJava = includeJava;
    }

    public ResultCG evaluateGenerator(final String outPath,
                                      final List<MavenCoordinate> coords) {
        StatCounter statCounter = new StatCounter();
        final var generator = extractGeneratorFromPath(outPath);
        if (includeJava) {
            coords.add(JAVA_8_COORD);
        }
        final ResultCG cg = generateAndCatch(statCounter, coords, generator);
        statCounter.concludeGenerator(outPath);
        return cg;
    }

    static String extractGeneratorFromPath(String outPath) {
        String generator = Constants.opalGenerator;
        if (outPath.toLowerCase().contains("wala")) {
            generator = Constants.walaGenerator;
        }
        return generator;
    }

    public ResultCG generateAndCatch(final StatCounter statCounter,
                                     final List<MavenCoordinate> depSet,
                                     final String generator) {
        ResultCG result = new ResultCG();

        final var currentVP = depSet.get(0);
        try {
            result = generateMeasureTimeAndConvert(statCounter, depSet, currentVP, generator);
        } catch (Exception e) {
            logger.warn("Exception occurred while generating CG for {}!", generator, e);
            statCounter.addGenerator(currentVP, new StatCounter.GeneratorStats(0L,
                new StatCounter.GraphStats()));
        }
        return result;
    }

     public ResultCG generateMeasureTimeAndConvert(StatCounter statCounter,
                                                  final List<MavenCoordinate> depSet,
                                                  final MavenCoordinate currentVP,
                                                  final String generator) {

        final var files = FilesUtils.download(depSet);
        final var typeCoordMap = createTypeUriToCoordMap(files);
        final var pcg = generatePCGAndMeasureTime(statCounter, currentVP, generator,
            files.stream().map(Pair::getValue).collect(Collectors.toList()));
        return new ResultCG(CGEvaluator.toLocalDirectedGraph(pcg),
            toMapOfLongAndUri(pcg.mapOfFullURIStrings(typeCoordMap)));
    }

    public static Map<Long, String> toMapOfLongAndUri(
        final BiMap<Long, String> integerStringBiMap) {
        final Long2ObjectOpenHashMap<String> result = new Long2ObjectOpenHashMap<>();
        for (final var node : integerStringBiMap.entrySet()) {
            result.put(node.getKey().longValue(), node.getValue());
        }
        return result;
    }


    private PartialJavaCallGraph generatePCGAndMeasureTime(StatCounter statCounter,
                                                           final MavenCoordinate currentVP,
                                                           final String generator,
                                                           final List<File> depJars) {

        final var jarOfAll = FilesUtils.jar(depJars);

        logger.info("jar file in: {}", jarOfAll.getAbsolutePath());
        final var time = measureGenerationTime(jarOfAll, generator);
        final PartialJavaCallGraph pcg =
            CGUtils.generatePCG(new File[] {jarOfAll}, currentVP, CGUtils.ALG,
                CallPreservationStrategy.INCLUDING_ALL_SUBTYPES, generator);
        statCounter.addGenerator(currentVP, time, pcg);
        try {
            FilesUtils.forceDelete(jarOfAll);
        } catch (IOException e) {
            logger.warn("Could not remove jar with dependencies!");
        }
        return pcg;
    }

    public long measureGenerationTime(final File tempJar, final String generator) {
        final var path = tempJar.getAbsolutePath();
        final Executable executable;
        logger.info("measuring time of {}", generator);
        if (generator.equals(Constants.opalGenerator)) {
            final var constructor = new OPALCallGraphConstructor();
            executable = () -> constructor.construct(tempJar, CGAlgorithm.valueOf(CGUtils.ALG));
        } else {
            executable = () -> CallGraphConstructor.generateCallGraph(path, Algorithm.valueOf(
                CGUtils.ALG));
        }
        return measureTime(executable, warmUp, iterations);
    }

    public static long measureTime(final Executable toRun, final int warmUp, final int iterations) {
        final var result = new ArrayList<Long>();
        for (int i = 0; i < warmUp + iterations; i++) {
                final long startTime = System.currentTimeMillis();
                try {
                    toRun.execute();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            if (i > warmUp) {
                result.add(System.currentTimeMillis() - startTime);
            }
        }
        return (long) result.stream().mapToDouble(a -> a).average().orElse(0);
    }

}
