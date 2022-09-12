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

import eu.fasten.analyzer.javacgopal.Main;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnalyzeJCGTests {

    private static final Logger logger = LoggerFactory.getLogger(AnalyzeJCGTests.class);

    public static void main(String[] args) {
        switch (args[0]) {
            case "--oneTest":
                generateSingleFeature(new File(args[1]), args[2]);
                break;
            case "--allTests":
                generateAllFeatures(new File(args[1]), args[2]);
                break;
        }
    }
    public static void generateSingleFeature(@NotNull final File testCaseDirectory,
                                             final String algorithm) {
        final String main = extractMain(testCaseDirectory);
        generateOpal(testCaseDirectory, main, algorithm, "cg/opalV3");
        merge(testCaseDirectory, main, algorithm, algorithm, "cg/mergeV3");
    }

    public static void generateAllFeatures(@NotNull final File testCasesDirectory, final String algorithm) {
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
            generateOpal(langFeature, main, algorithm, "cg/opalV3");

            if (!merge(langFeature, main, algorithm, algorithm, "cg/mergeV3")) {
                singleClass++;
            }
        }
        logger.info("There was #{} single class language features we couldn't merge! ", singleClass);
    }

    private static String extractMain(@NotNull final File langFeature) {
        final String conf;
        try {
            conf = new String(Files.readAllBytes(
                Paths.get(langFeature.getAbsolutePath().replace(".jar_split", "").concat(".conf"))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    public static void generateOpal(@NotNull final File langFeature, final String mainClass,
                                    final String algorithm,
                                    final String output) {
        final var fileName = langFeature.getName().replace(".class", "");
        final var resultGraphPath = langFeature.getAbsolutePath() + "/" + output;
        final var cgCommand =
            new String[] {"-g", "-a", langFeature.getAbsolutePath(), "-n", mainClass, "-ga",
                algorithm, "-m", "FILE", "-o", resultGraphPath};
        final var convertCommand = new String[] {"-c", "-i", resultGraphPath + "_" + fileName, "-f",
            "JCG", "-o", langFeature.getAbsolutePath() + "/" + output + "Jcg"};
        logger.info("CG Command: {}", Arrays.toString(cgCommand).replace(",", " "));
        Main.main(cgCommand);
        logger.info("Convert Command: {}", Arrays.toString(convertCommand).replace(",", " "));
        Main.main(convertCommand);

    }

    public static boolean merge(@NotNull final File langFeature, @NotNull final String main, final String genAlg,
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

    @NotNull
    private static String[] createArtDep(@NotNull final String main, @NotNull final File[] files) {

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

    private static void compute(@NotNull final File langFeature, final String main, final String output,
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

}
