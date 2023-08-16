package evaluation;

import static eu.fasten.core.data.CallPreservationStrategy.ONLY_STATIC_CALLSITES;
import static evaluation.GeneratorEvaluator.extractGeneratorFromPath;
import static evaluation.GeneratorEvaluator.measureTime;
import static util.FilesUtils.JAVA_8_COORD;

import data.SerializationInfo;
import data.ResultCG;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.JSONUtils;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import eu.fasten.core.merge.CGMerger;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.junit.jupiter.api.function.Executable;
import org.openjdk.jol.info.GraphLayout;
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
    private final String path;

    public MergeEvaluator(int warmUp, int iterations, boolean includeJava, String path) {
        this.warmUp = warmUp;
        this.iterations = iterations;
        this.includeJava = includeJava;
        this.path = path;
    }

    public ResultCG evaluateMerge(final List<MavenCoordinate> coords) {
        StatCounter statCounter = new StatCounter();
        final var generator = extractGeneratorFromPath(path);
        if (includeJava) {
            coords.add(JAVA_8_COORD);
        }
        final ResultCG cg =
            createCGPoolAndMergeDepSet(coords, statCounter, new HashMap<>(), generator);
        statCounter.concludeMerge(path);
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
            statCounter.addNewCGtoPool(dep, 0, new StatCounter.GraphStats(), -1);
        }
    }

    private SerializationInfo measureFileCachingOperations(final PartialJavaCallGraph rcg) {
        final var pcgDir = path + File.separator + FilesUtils.PCG_DIR;
        final var pcgFileName = rcg.product + ":" + rcg.version + ".json";
        final var cgPath = pcgDir + File.separator + pcgFileName;

        final Executable serialize = () -> JSONUtils.toJSONString(rcg);
        final var serTime = measureTime(serialize, warmUp, iterations);

        final var serializedCG = JSONUtils.toJSONString(rcg);
        final Executable write = () -> FilesUtils.writeCGToFile(cgPath, serializedCG);
        final var writeTime = measureTime(write, warmUp, iterations);

        final Executable read = () -> readCGToString(pcgDir, pcgFileName);
        final var readTime = measureTime(read, warmUp, iterations);

        final var cg = readCGToString(pcgDir, pcgFileName);

        long deserTime = 0;
        if (!cg.equals("")) {
            final Executable deserialize = () -> getPartialJavaCallGraph(cg);
            deserTime = measureTime(deserialize, warmUp, iterations);
        }

        final long cgSize = getCGByteSize(cg);

        return new SerializationInfo(serTime, writeTime, readTime, deserTime, cgSize);
    }

    private int getCGByteSize(final String cg) {
        return cg.getBytes(StandardCharsets.UTF_8).length;
    }

    private PartialJavaCallGraph getPartialJavaCallGraph(final String cg) {
        return new PartialJavaCallGraph(new JSONObject(cg));
    }

    private String readCGToString(final String pcgDir, final String cgFileName) {
        final var opalFile = FilesUtils.getFile(new File(pcgDir), cgFileName);
        if (opalFile != null && opalFile.length != 0) {
            try {
                return Files.readString(opalFile[0].toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return "";
    }

    private PartialJavaCallGraph generateAndMeasure(StatCounter statCounter,
                                                    final MavenCoordinate coordinate,
                                                    final String generator) {
        final var file = FilesUtils.download(coordinate);
        final var rcg =
            CGUtils.generatePCG(new File[] {file}, coordinate, CGUtils.ALG,
                ONLY_STATIC_CALLSITES, generator, true);

        final var cgMemSize = calcMemInMB(rcg);

        statCounter.addNewCGtoPool(coordinate,
            new GeneratorEvaluator(warmUp, iterations, false).measureGenerationTime(file,
                generator), new StatCounter.GraphStats(rcg), cgMemSize);

        return rcg;
    }

    private double calcMemInMB(Object obj) {
        List<Long> sizes = new ArrayList<>();
        for (int i = 0; i < iterations; i++) {
            final GraphLayout graphLayout = GraphLayout.parseInstance(obj);
            sizes.add(graphLayout.totalSize());
        }
        final var avgSize = sizes.stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
        return avgSize / (1024 * 1024);
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
        final var mergerSize = calcMemInMB(cgMerger);
        statCounter.addMerge(depSet.get(0),
            measureTime(cgMerger::mergeAllDeps, warmUp, iterations),
            new StatCounter.GraphStats(dg), mergerSize);
        return dg;
    }

    private void measureUCH(StatCounter statCounter, final List<MavenCoordinate> depSet,
                            final List<PartialJavaCallGraph> deps) {
        final var uchTime = measureTime(() -> new CGMerger(deps), warmUp, iterations);
        statCounter.addUCH(depSet.get(0), uchTime);
    }

}
