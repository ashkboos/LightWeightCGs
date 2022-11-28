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

package jcg;

import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.PartialJavaCallGraph;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

public class JCGFormat {


    public static JSONObject convertERCGTOJCG(final PartialJavaCallGraph ercg) {

        if (ercg.isCallGraphEmpty()) {
            return new JSONObject();
        }
        return getJCGJSON(groupByCallSite(getAdjacencyList(ercg)));
    }


    private static JSONObject getJCGJSON(
        final Map<String, Map<Map<Object, Object>, List<String>>> cgWithCallSites) {

        final var result = new JSONObject();
        final var reachableMethods = new JSONArray();

        for (final var source : cgWithCallSites.keySet()) {

            final var method = new JSONObject();
            method.put("method", getMethodJSON(source));
            final var callSites = new JSONArray();
            for (final var targetsOfACallSite : cgWithCallSites.get(source).entrySet()) {
                for (final var callSite : targetsOfACallSite.getKey().entrySet()) {
                    final var callSiteJson = new JSONObject();
                    final var cs = (HashMap<String, Object>) callSite.getValue();
                    final var pc = (Integer) callSite.getKey();
                    callSiteJson.put("declaredTarget", getMethodJSON((String) cs.get("receiver"),
                        targetsOfACallSite.getValue().get(0)));
                    callSiteJson.put("line", cs.get("line"));
                    callSiteJson.put("pc", pc);
                    final var targets = new JSONArray();
                    for (final var target : targetsOfACallSite.getValue()) {
                        targets.put(getMethodJSON(target));
                    }
                    callSiteJson.put("targets", targets);
                    callSites.put(callSiteJson);
                }
            }
            method.put("callSites", callSites);

            reachableMethods.put(method);
        }
        result.put("reachableMethods", reachableMethods);
        return result;

    }

    private static <E> void putToJsonArray(final JSONArray array, final String key,
                                           final E content) {
        array.put(new JSONObject() {
            {
                put(key, content);
            }
        });
    }


    private static JSONObject getMethodJSON(final String type, final String uri) {
        return getMethodJSON(
            type + "." + StringUtils.substringAfter(StringUtils.substringBefore(uri, "("),
                "."));
    }


    private static JSONObject getMethodJSON(final String uri) {

        final var method = decodeMethod(uri);
        final var result = new JSONObject();

        final var params = new JSONArray();
        for (var param : StringUtils.substringBetween(method, "(", ")").split("[,]")) {
            if (!param.isEmpty()) {
                params.put(toJVMType(dereletivize(uri, param)));
            }
        }

        var name = decodeMethod(StringUtils.substringBetween(method, ".", "("));
        if (name.equals("<init>")) {
            name = "<clinit>";
        }
        if (name.equals(method.split("[.]")[0])) {
            name = "<init>";
        }

        result.put("name", name);
        result.put("parameterTypes", params);
        result.put("returnType", toJVMType(dereletivize(uri,
            StringUtils.substringAfterLast(method, ")"))));
        result.put("declaringClass", toJVMType(uri + "/" + method.split("[.]")[0]));

        return result;

    }


    private static String dereletivize(String type, String param) {
        return !param.contains("/") && !param.contains(".") ? type + "/" + param : param;
    }

    private static String decodeMethod(final String methodSignature) {
        String result = methodSignature;
        while (result.contains("%")) {
            result = URLDecoder.decode(result, StandardCharsets.UTF_8);
        }
        return result;
    }


    private static String toJVMType(final String type) {

        String result = type.replace(".", "/"), brakets = "";

        if (result.isEmpty()) {
            return result;
        }

        if (result.contains("[")) {
            brakets = "[".repeat(StringUtils.countMatches(result, "["));
            result = result.replaceAll("\\[", "").replaceAll("\\]", "");
        }
        if (result.startsWith("/")) {
            result = result.substring(1);
        }

        result = convertJavaTypes(result);
        if (result.contains("/")) {
            result = "L" + result + ";";
        }

        return brakets + result;

    }

    private static String convertJavaTypes(final String type) {

        final var WraperTypes = Map.of(
            "java/lang/ByteType", "B",
            "java/lang/ShortType", "S",
            "java/lang/IntegerType", "I",
            "java/lang/LongType", "J",
            "java/lang/FloatType", "F",
            "java/lang/DoubleType", "D",
            "java/lang/BooleanType", "Z",
            "java/lang/CharType", "C",
            "java/lang/VoidType", "V");

        return WraperTypes.getOrDefault(type, type);
    }


    private static Map<String, Map<Map<Object, Object>, List<String>>> groupByCallSite(
        final Map<String, List<Pair<String, Map<Object, Object>>>> cg) {

        final Map<String, Map<Map<Object, Object>, List<String>>> result = new HashMap<>();

        for (final var source : cg.keySet()) {
            final Map<Map<Object, Object>, List<String>> callSites = new HashMap<>();
            for (final var target : cg.get(source)) {
                final var line = target.getRight();
                callSites.put(line,
                    Stream.concat(callSites.getOrDefault(line, new ArrayList<>()).stream(),
                        Stream.of(target.getLeft())).collect(Collectors.toList()));
            }
            result.put(source, callSites);
        }

        return result;
    }


    private static String getSignature(final String rawEntity) {
        return rawEntity.substring(rawEntity.indexOf(".") + 1);
    }


    private static Map<String, List<Pair<String, Map<Object, Object>>>> getAdjacencyList(
        final PartialJavaCallGraph ercg) {

        final Map<String, List<Pair<String, Map<Object, Object>>>> result = new HashMap<>();

        final var methods = ercg.mapOfAllMethods();
        for (final var internalCall : ercg.getGraph().getCallSites().entrySet()) {
            putCall(result, methods, internalCall);
        }

        return result;
    }

    private static void putCall(final Map<String, List<Pair<String, Map<Object, Object>>>> result,
                                final Map<Long, JavaNode> methods,
                                final Map.Entry<LongLongPair, Map<Object, Object>> call) {

        final var source = methods.get(call.getKey().leftLong()).getSignature();
        final var targets = result.getOrDefault(source, new ArrayList<>());

        targets.add(MutablePair.of(methods.get(call.getKey().secondLong()).getSignature(),
            call.getValue()));

        result.put(source, targets);
    }
}
