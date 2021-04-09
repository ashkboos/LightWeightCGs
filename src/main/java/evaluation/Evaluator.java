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

package evaluation;

import static evaluation.StitchingEdgeTest.compareMergeOPAL;
import static evaluation.StitchingEdgeTest.groupBySource;

import ch.qos.logback.classic.Level;

import eu.fasten.analyzer.javacgopal.Main;

import eu.fasten.analyzer.javacgopal.data.MavenCoordinate;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;


import eu.fasten.analyzer.javacgopal.data.CallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.PartialCallGraph;
import eu.fasten.core.data.ExtendedRevisionJavaCallGraph;
import eu.fasten.core.merge.LocalMerger;
import eu.fasten.analyzer.javacgopal.data.exceptions.OPALException;
import me.tongfei.progressbar.ProgressBar;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jooq.tools.csv.CSVReader;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Evaluator {

    private static final int warmUp = 10;
    private static final int iterations = 10;

    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);

    public static void main(String[] args)
        throws IOException, NoSuchFieldException, IllegalAccessException, OPALException {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory
            .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);

        System.setProperty("org.jline.terminal.dumb", "true");
        if (args[0].equals("--oneTest")) {
            generateSingleFeature(new File(args[1]));
        } else if (args[0].equals("--allTests")) {
            generateAllFeatures(new File(args[1]));
        } else if (args[0].equals("--compare")) {
            measureAllStats(readResolvedDataCSV(args[1]), args[2], Integer.parseInt(args[3]));
        } else if (args[0].equals("--resolve")) {
            final var resolvedData = resolveAll(dropTheHeader(readCSVColumn(args[1], 0)));
            StatCounter.writeToCSV(buildDataCSV(resolvedData), args[2]);
            logger.info("Wrote resolved data into file successfully!");
        }
    }

    public static void generateAllFeatures(final File testCasesDirectory) throws IOException {
        final var splitJars = testCasesDirectory.listFiles(f -> f.getPath().endsWith("_split"));
        var counter = 0;
        assert splitJars != null;
        final var tot = splitJars.length;
        int singleClass = 0;
        for (final var langFeature : splitJars) {
            new File(langFeature.getAbsolutePath() + "/cg").mkdir();
            counter += 1;
            logger.info("\n Processing {} -> {}", langFeature.getName(), counter + "/" + tot);
            final String main = extractMain(langFeature);
            generateOpal(langFeature, main, "CHA", "cg/opalV3");

            if (!merge(langFeature, main, "CHA", "CHA", "cg/mergeV3")) {
                singleClass++;
            }
        }
        logger
            .info("There was #{} single class language features we couldn't merge! ", singleClass);
    }

    public static void generateSingleFeature(final File testCaseDirectory) throws IOException {
        final String main = extractMain(testCaseDirectory);
        generateOpal(testCaseDirectory, main, "RTA", "cg/opalV3");
        merge(testCaseDirectory, main, "RTA", "CHA", "cg/mergeV3");
    }

    private static void measureAllStats(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
        final String outPath,
        final int threshold)
        throws IOException, NoSuchFieldException, IllegalAccessException, OPALException {

        final var statCounter = new StatCounter();

        final var uniquesCommons = countCommonCoordinates(resolvedData);
        logger.info("#{} redundant packages and #{} unique packages", uniquesCommons.getRight(),
            uniquesCommons.getLeft());

        if (uniquesCommons.getRight() < threshold) {
            logger.info("Number of redundant packages is less than threshold #{}", threshold);

        } else {
            runOPALandMerge(resolvedData, statCounter);

            statCounter.concludeMerge(resolvedData, outPath);
            statCounter.concludeAll(resolvedData, outPath);
            logger.info("Wrote results of evaluation into file successfully!");
        }
    }

    private static void runOPALandMerge(final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData,
                                        final StatCounter statCounter) {
        final Map<MavenCoordinate, Set<MavenCoordinate>> remainedDependents = getDependents(resolvedData);
        final Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> cgPool = new HashMap<>();
        ProgressBar pb = new ProgressBar("Measuring stats", resolvedData.entrySet().size());
        pb.start();

        for (final var row : resolvedData.entrySet()) {
            final var toMerge = row.getKey();
            for (final var dep : resolvedData.get(toMerge)) {
                if (!cgPool.containsKey(dep)) {
                    addToCGPool(statCounter, cgPool, dep);
                }
            }

            logger.info("artifact: {}, dependencies: {}", toMerge.getCoordinate(),
                resolvedData.get(toMerge).stream().map(MavenCoordinate::getCoordinate).collect(Collectors.toList()));

            final var merge = mergeArtifact(cgPool, statCounter, resolvedData.get(toMerge),
                toMerge);
            pb.step();
            for (final var dep : resolvedData.get(toMerge)) {
                if ( remainedDependents.get(dep) != null) {
                    if (remainedDependents.get(dep).isEmpty()) {
                        remainedDependents.get(dep).remove(toMerge);
                        cgPool.remove(dep);
                        System.gc();
                    }
                }
            }
            final var opal = generateForOPAL(statCounter, row);
            if(opal!= null && merge != null ) {
                statCounter.addAccuracy(toMerge,
                    calcPrecisionRecall(
                        removeVersions(groupBySource(compareMergeOPAL(merge, opal)))));
            }
            System.gc();
        }
        pb.stop();

    }
    private static List<StatCounter.SourceStats> calcPrecisionRecall(Map<String, Map<String, List<String>>> edges){
        final var opal = edges.get("opalInternals");
        final var merge = edges.get("mergeInternals");
        final var allSources = new HashSet<String>();
        allSources.addAll(opal.keySet());
        allSources.addAll(merge.keySet());
        List<StatCounter.SourceStats> result = new ArrayList<>();
        for (final var source : allSources) {

            final var opalTargets = new HashSet<>(opal.getOrDefault(source,
                new ArrayList<>()));
            final var mergeTargets = new HashSet<>(merge.getOrDefault(source, new ArrayList<>()));
            final var intersect = intersect(opalTargets, mergeTargets);
            final var mergeSize = mergeTargets.size();
            final var opalSize = opalTargets.size();
            final var intersectSize = intersect.size();

            final var precision = mergeSize == 0 ? 1 : (double) intersectSize/mergeSize;
            final var recall = opalSize == 0 ? 1 : (double) intersectSize/opalSize;

            result.add(new StatCounter.SourceStats(source, precision, recall, opalSize, mergeSize
                ,intersectSize));
        }
        return result;
    }

    private static Map<String, Map<String, List<String>>> removeVersions(Map<String, Map<String, List<String>>> edges) {
        for (var targets : edges.get("mergeInternals").entrySet()) {
            final List<String> removedVersion = new ArrayList<>();
            for (String target : targets.getValue()) {
                removedVersion.add(target.replace(target.split("/")[2],"").replace("//",""));
            }
            targets.setValue(removedVersion);
        }
        return edges;
    }

    private static Set<String> intersect(final Set<String> first,
                                         final Set<String> second) {
        final var temp1 = new ArrayList<>(first);
        final var temp2 = new ArrayList<>(second);
        return temp1.stream()
            .distinct()
            .filter(temp2::contains)
            .collect(Collectors.toSet());
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

    private static List<String[]> buildDataCSV(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {"number", "coordinate", "dependencies"});
        int counter = 0;
        for (final var coorDeps : resolvedData.entrySet()) {
            final var coord = coorDeps.getKey();
            dataLines.add(new String[] {
                /* number */ String.valueOf(counter),
                /* coordinate */ coord.getCoordinate(),
                /* dependencies */ StatCounter.toString(coorDeps.getValue())});
            counter++;
        }
        return dataLines;
    }

    private static Pair<Integer, Integer> countCommonCoordinates(
        final Map<MavenCoordinate, List<MavenCoordinate>> resolvedData) {
        final HashSet<MavenCoordinate> uniqueCoords = new HashSet<>();
        int counter = 0;
        for (final var coordList : resolvedData.values()) {
            for (final var coord : coordList) {
                if (uniqueCoords.contains(coord)) {
                    counter++;
                } else {
                    uniqueCoords.add(coord);
                }
            }
        }
        return ImmutablePair.of(uniqueCoords.size(), counter);
    }

    public static List<String> dropTheHeader(final List<String> csv) {
        csv.remove(0);
        return csv;
    }

    public static List<String> readCSVColumn(final String inputPath,
                                              final int columnNum) throws IOException {
        List<String> result = new ArrayList<>();
        try (var csvReader = new CSVReader(new FileReader(inputPath))) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {
                result.add(values[columnNum]);
            }
        }
        return result;
    }

    public static Map<MavenCoordinate, List<MavenCoordinate>> resolveAll(
        final List<String> dataSet) {

        ProgressBar pb = new ProgressBar("Resolving", dataSet.size());
        pb.start();

        final var result = new HashMap<MavenCoordinate, List<MavenCoordinate>>();
        for (final var coord1 : dataSet) {
            final var deps1 = resolve(coord1);
            if (deps1.isPresent()) {
                if(!hasScala(deps1)) {
                    if(deps1.get().size() >1){
                    deps1.ifPresent(mavenCoordinates -> result
                        .put(MavenCoordinate
                                .fromString(coord1,
                                    mavenCoordinates.get(0).getPackaging().getClassifier()),
                            convertToFastenCoordinates(mavenCoordinates)));
                    }
                }
            }

            if (result.size() == 100) {
                break;
            }
            pb.step();
        }
        pb.stop();
        return result;
    }

    private static boolean hasScala(
        Optional<List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate>> deps) {

        for (final var coord : deps.get()) {
            if (coord.getArtifactId().contains("scala") || coord.getGroupId().contains("scala")) {
                return true;
            }
        }
        return false;
    }


    private static List<ExtendedRevisionJavaCallGraph> mergeArtifact(final Map<MavenCoordinate,
        ExtendedRevisionJavaCallGraph> cgPool,
                                      final StatCounter statCounter,
                                      final List<MavenCoordinate> deps,
                                      final MavenCoordinate artifact) {
        final List<ExtendedRevisionJavaCallGraph> result = new ArrayList<>();
        final var rcgs = deps.stream().map(cgPool::get)
            .filter(Objects::nonNull) // get rid of null values
            .collect(Collectors.toList());
        final long startTimeUch = System.currentTimeMillis();
        final var cgMerger =  new LocalMerger(rcgs);
        statCounter.addUCH(artifact,System.currentTimeMillis() - startTimeUch);

        for (final var dep : deps) {
            if (cgPool.get(dep) != null) {
                logger.info("\n ###############\n Merging {}:", dep.getCoordinate());
                ExtendedRevisionJavaCallGraph rcg = null;
                final var times = new ArrayList<Long>();
                for (int i = 0; i < warmUp + iterations ; i++) {
                    if (i > warmUp) {
                        final long startTime = System.currentTimeMillis();
                        rcg =
                            cgMerger.mergeWithCHA(cgPool.get(dep));
                        times.add(System.currentTimeMillis() - startTime);
                    }
                }

                statCounter
                    .addMerge(artifact, dep, skipRevision(dep, deps),
                        (long) times.stream().mapToDouble(a -> a).average().getAsDouble(),
                        new StatCounter.GraphStats(rcg));

                result.add(rcg);
            }else {
                statCounter
                    .addMerge(artifact, dep, skipRevision(dep, deps),
                        0,
                        new StatCounter.GraphStats());
            }
        }
        return result;
    }

    private static Optional<List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate>> resolve(
        final String row) {
        try {
            return Optional.of(Maven.resolver().resolve(row).withTransitivity()
                .asList(org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate.class));
        } catch (Exception e) {
            logger.warn("Exception occurred while resolving {}, {}", row, e);
            return Optional.empty();
        }
    }

    private static List<MavenCoordinate> skipRevision(
        final MavenCoordinate revision,
        final List<MavenCoordinate> revisions) {

        final List<MavenCoordinate> coords = new ArrayList<>();

        for (final var coord : revisions) {
            if (!revision.equals(coord)) {
                coords.add(coord);
            }
        }
        return coords;
    }

    private static List<eu.fasten.analyzer.javacgopal.data.MavenCoordinate> convertToFastenCoordinates(
        final List<org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate> revisions) {
        return revisions.stream().map(Evaluator::convertToFastenCoordinate)
            .collect(Collectors.toList());
    }


    private static ExtendedRevisionJavaCallGraph generateForOPAL(StatCounter statCounter,
                                        Map.Entry<MavenCoordinate, List<MavenCoordinate>> row) {
        ExtendedRevisionJavaCallGraph rcg = null;
        try {
                final var tempDir = downloadToDir(row.getValue());
                logger.info("\n##################### \n Deps of {} downloaded to {}, Opal is " +
                        "generating ...", row.getValue().get(0).getCoordinate(),
                    tempDir.getAbsolutePath());
            CallGraphConstructor opalCg = null;

            final var times = new ArrayList<Long>();
            for (int i = 0; i < warmUp + iterations ; i++) {
                if (i > warmUp) {
                    final long startTime = System.currentTimeMillis();
                    opalCg = new CallGraphConstructor(tempDir, "", "CHA");
                    times.add(System.currentTimeMillis() - startTime);
                }
            }

                final var cg = new PartialCallGraph(opalCg, false);
                rcg = ExtendedRevisionJavaCallGraph.extendedBuilder()
                    .graph(cg.getGraph())
                    .classHierarchy(cg.getClassHierarchy())
                    .nodeCount(cg.getNodeCount())
                    .build();

            FileUtils.deleteDirectory(tempDir);

                statCounter.addOPAL(row.getKey(),
                    (long) times.stream().mapToDouble(a -> a).average().getAsDouble(), rcg);

            System.gc();
        } catch (Exception e) {
            logger.warn("Exception occurred found!", e);
            statCounter.addOPAL(row.getKey(), new StatCounter.OpalStats(0l,
                new StatCounter.GraphStats()));
        }
        return rcg;

    }

    public static File downloadToDir(final List<MavenCoordinate> mavenCoordinates)
        throws IOException {

        final var resultDir = Files.createTempDirectory("fasten").toAbsolutePath();

        for (final var coord : mavenCoordinates) {
            File coordinateFile = new File("");
            try {
                coordinateFile = new MavenCoordinate.MavenResolver().downloadArtifact(coord, "jar");
            } catch (Exception e) {
                logger.warn("File not found!");
            }
            Files.copy(coordinateFile.toPath(),
                Paths.get(resultDir + "/" + coord.getCoordinate() + ".jar"),
                StandardCopyOption.REPLACE_EXISTING);
        }
        return resultDir.toFile();
    }

    private static Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> buildUpCGPool(
        final Map<MavenCoordinate, List<MavenCoordinate>> dataSet, final StatCounter statCounter,
        final Integer uniqueCoords) {

        ProgressBar pb = new ProgressBar("Creating CGPool", uniqueCoords);
        pb.start();

        Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> result = new HashMap<>();
        for (final var row : dataSet.entrySet()) {
            for (final var coord : row.getValue()) {
                addToCGPool(statCounter, result, coord);
                pb.step();
            }

        }
        pb.stop();
        return result;
    }

    private static void addToCGPool(final StatCounter statCounter,
                                    final Map<MavenCoordinate, ExtendedRevisionJavaCallGraph> cgPool,
                                    final MavenCoordinate dep) {
        if (!cgPool.containsKey(dep)) {
            try {
                logger.info("\n ##############\n Adding {} to cg pool!",
                    dep.getCoordinate());
                final var file = new MavenCoordinate.MavenResolver().downloadArtifact(dep, "jar");

                final long startTime = System.currentTimeMillis();
                final var opalCG = new CallGraphConstructor(file, "", "CHA");
                final var cg = new PartialCallGraph(opalCG, true);
                final var rcg = ExtendedRevisionJavaCallGraph.extendedBuilder()
                    .graph(cg.getGraph())
                    .product(dep.getProduct())
                    .version(dep.getVersionConstraint())
                    .classHierarchy(cg.getClassHierarchy())
                    .nodeCount(cg.getNodeCount())
                    .build();

                    statCounter.addNewCGtoPool(dep,
                        System.currentTimeMillis() - startTime, new StatCounter.GraphStats(rcg));

                    file.delete();


                cgPool.put(dep, rcg);
            } catch (Exception e) {
                logger.warn("Exception occurred found!", e);
                statCounter.addNewCGtoPool(dep, 0, new StatCounter.GraphStats());
            }

        } else {
            statCounter.addExistingToCGPool(dep);
        }
    }

    private static MavenCoordinate convertToFastenCoordinate(
        final org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate revision) {
        return new MavenCoordinate(revision.getGroupId(),
            revision.getArtifactId(), revision.getVersion(),
            revision.getPackaging().getClassifier());
    }

    private static String extractMain(final File langFeature) throws IOException {
        final var conf = new String(Files.readAllBytes(

            Paths.get(langFeature.getAbsolutePath().replace(".jar_split", "").concat(".conf"))));
        final var jsObject = new JSONObject(conf);

        String main = "";
        if (jsObject.has("main")) {
            main = jsObject.getString("main");
        }

        if (main != null) {
            main = main.replace("\"", "");
        }
        return main;
    }

    public static void generateOpal(final File langFeature, final String mainClass,
                                    final String algorithm,
                                    final String output) {
        final var fileName = langFeature.getName().replace(".class", "");
        final var resultGraphPath = langFeature.getAbsolutePath() + "/" + output;
        final var cgCommand =
            new String[] {"-g", "-a", langFeature.getAbsolutePath(), "-n", mainClass, "-ga",
                algorithm, "-m", "FILE", "-o", resultGraphPath};
        final var convertCommand = new String[] {"-c", "-i", resultGraphPath + "_" + fileName, "-f",
            "JCG",
            "-o",
            langFeature.getAbsolutePath() + "/" + output + "Jcg"};
        logger.info("CG Command: {}", Arrays.toString(cgCommand).replace(",", " "));
        Main.main(cgCommand);
        logger.info("Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);

    }

    public static boolean merge(final File langFeature, final String main, final String genAlg,
                                final String mergeAlg,
                                final String output) {

        final var files = langFeature.listFiles(file -> file.getPath().endsWith(".class"));
        assert files != null;
        if (files.length > 1) {
            compute(langFeature, main, output, createArtDep(main, files), genAlg, mergeAlg);
            return true;
        } else {
            logger.info("No dependency, no merge for {}", langFeature.getAbsolutePath());
            return false;
        }
    }

    private static String[] createArtDep(final String main, final File[] files) {

        String art = "";
        final StringBuilder deps = new StringBuilder();
        for (final File file : files) {
            if (!main.isEmpty()) {
                if (file.getName().equals(main.split("[.]")[1] + ".class")) {
                    art = file.getAbsolutePath();
                } else {
                    deps.append(file.getAbsolutePath()).append(",");
                }
            } else {
                if (file.getName().contains("Demo.class")) {
                    art = file.getAbsolutePath();
                } else {
                    deps.append(file.getAbsolutePath()).append(",");
                }
            }

        }
        return new String[] {"-a", art, "-d", deps.toString().replaceAll(".$", "")};
    }

    private static void compute(final File langFeature, final String main, final String output,
                                final String[] artDeps, final String genAlg,
                                final String mergeAlg) {
        var mergeCommand = new String[] {"-s", "-ma", mergeAlg, "-ga", genAlg, "-n", main, "-o",
            langFeature.getAbsolutePath() + "/" + output};
        mergeCommand = ArrayUtils.addAll(mergeCommand, artDeps);

        logger.info("Merge Command: {}", Arrays.toString(mergeCommand).replace(",", " "));
        Main.main(mergeCommand);

        StringBuilder input = new StringBuilder();
        final var files = new File(langFeature.getAbsolutePath() + "/cg")
            .listFiles(
                file -> file.getName().startsWith("mergeV3") && !file.getName().endsWith("Demo"));

        assert files != null;
        if (files.length > 1) {
            for (int i = 0; i < files.length; i++) {
                if (i == files.length - 1) {
                    input.append(files[i].getAbsolutePath());
                } else {
                    input.append(files[i].getAbsolutePath()).append(",");
                }
            }
        }
        final var convertCommand = new String[] {"-c", "-i", input.toString(), "-f", "JCG", "-o",
            langFeature.getAbsolutePath() + "/" + output + "Jcg"};

        logger.info("Merge Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);
    }

    public static Map<MavenCoordinate, List<MavenCoordinate>> readResolvedDataCSV(
        final String inputPath) throws IOException {

        Map<MavenCoordinate, List<MavenCoordinate>> result = new HashMap<>();

        try (var csvReader = new CSVReader(new FileReader(inputPath), ',', '\'', 1)) {
            String[] values;
            while ((values = csvReader.readNext()) != null) {

                final var coords = getCoordsList(values[2]);
                result.put(coords.get(0), coords);
            }
        }
        return result;
    }

    private static List<MavenCoordinate> getCoordsList(final String coords) {
        List<MavenCoordinate> result = new ArrayList<>();

        var coordinates = coords.split(";");
        for (final var coord : coordinates) {
            result.add(MavenCoordinate.fromString(coord, "jar"));
        }
        return result;
    }
}