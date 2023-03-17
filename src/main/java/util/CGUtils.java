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

package util;

import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import eu.fasten.analyzer.javacgopal.data.CGAlgorithm;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraph;
import eu.fasten.analyzer.javacgopal.data.OPALCallGraphConstructor;
import eu.fasten.analyzer.javacgopal.data.OPALPartialCallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm;
import eu.fasten.analyzer.javacgwala.data.callgraph.CallGraphConstructor;
import eu.fasten.analyzer.javacgwala.data.callgraph.PartialCallGraphGenerator;
import eu.fasten.analyzer.javacgwala.data.callgraph.analyzer.WalaResultAnalyzer;
import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.JavaGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.opal.MavenCoordinate;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CGUtils {
    private static final Logger logger = LoggerFactory.getLogger(CGUtils.class);
    public static String ALG = "CHA";

    public static PartialJavaCallGraph generatePCG(final File[] depSet,
                                                   final MavenCoordinate mavenCoordinate,
                                                   final String algorithm,
                                                   final CallPreservationStrategy callPreservationStrategy,
                                                   final String generator) {
        return generatePCG(depSet, mavenCoordinate, algorithm,
            callPreservationStrategy, generator,false);
    }
    public static PartialJavaCallGraph generatePCG(final File[] depSet,
                                                   final MavenCoordinate mavenCoordinate,
                                                   final String algorithm,
                                                   final CallPreservationStrategy callPreservationStrategy,
                                                   final String generator,
                                                   final boolean partial) {
        logger.info("Generating CG for {} {} {} {} {}", mavenCoordinate.getCoordinate(),
            algorithm, callPreservationStrategy.toString(), generator, depSet);
        if (generator.equals(Constants.opalGenerator)) {
            final OPALCallGraph opalCG;
            final var opalAlg = CGAlgorithm.valueOf(algorithm);
            if (depSet.length > 1) {
                opalCG = new OPALCallGraphConstructor().construct(new File[] {depSet[0]},
                    Arrays.copyOfRange(depSet, 1, depSet.length), opalAlg);
            } else {
                opalCG = new OPALCallGraphConstructor().construct(depSet[0], opalAlg);
            }
            return convertOpalCGToFastenCG(mavenCoordinate, callPreservationStrategy, opalCG, partial);
        } else {
            final CallGraph callgraph;
            try {
                callgraph = CallGraphConstructor.generateCallGraph(depSet[0].getAbsolutePath(),
                    Algorithm.valueOf(algorithm));
            } catch (IOException | ClassHierarchyException | CancelException e) {
                throw new RuntimeException(e);
            }

            final var pcg =
                PartialCallGraphGenerator.generateEmptyPCG(Constants.mvnForge,
                    mavenCoordinate.getProduct(), mavenCoordinate.getVersionConstraint(), -1,
                    Constants.walaGenerator);

            WalaResultAnalyzer.wrap(callgraph, pcg, callPreservationStrategy);

            if (partial) {
                pcg.setSourceCallSites();
                pcg.setGraph(new JavaGraph());
            }

            return pcg;
        }
    }

    public static PartialJavaCallGraph convertOpalCGToFastenCG(
        final MavenCoordinate mavenCoordinate,
        final CallPreservationStrategy callPreservationStrategy,
        final OPALCallGraph opalCG, boolean partial) {
        final var partialCallGraph =
            new OPALPartialCallGraphConstructor().construct(opalCG, callPreservationStrategy);

        final var pcg =
            new PartialJavaCallGraph(Constants.mvnForge, mavenCoordinate.getProduct(),
                mavenCoordinate.getVersionConstraint(), -1,
                Constants.opalGenerator,
                partialCallGraph.classHierarchy,
                partialCallGraph.graph);

        if (partial) {
            pcg.setSourceCallSites();
            pcg.setGraph(new JavaGraph());
        }

        return pcg;
    }


}
