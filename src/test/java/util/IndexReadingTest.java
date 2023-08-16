package util;

import static eu.fasten.analyzer.javacgwala.data.callgraph.Algorithm.CHA;
import static org.codehaus.plexus.PlexusConstants.SCANNING_INDEX;
import static util.FilesUtils.JAVA_8_COORD;

import eu.fasten.core.data.CallPreservationStrategy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.opal.MavenArtifactDownloader;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.IndexDataReader;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphLayout;
import org.openjdk.jol.vm.VM;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class IndexReadingTest {

    @Test
    public void test() {

        System.out.println(VM.current().details());
        final var pcg = CGUtils.generatePCG(new File[] {FilesUtils.getRTJar()},
            JAVA_8_COORD, CHA.label, CallPreservationStrategy.ONLY_STATIC_CALLSITES,
            Constants.opalGenerator);
        for (int i = 0; i < 5; i++) {
            System.out.println(getInstanceSize(pcg));
        }
        System.out.println("optimizing for merge: ");
        pcg.setSourceCallSitesToOptimizeMerge();
        for (int i = 0; i < 5; i++) {
            System.out.println(getInstanceSize(pcg));
        }
    }


    private double getInstanceSize(Object obj) {
        final GraphLayout graphLayout = GraphLayout.parseInstance(obj);
        return (double) graphLayout.totalSize() / (1024 * 1024);
    }

    @Test
    public void testT() {
        try ( //
              var fis = new FileInputStream(
                  new File("/Users/mehdi/Downloads/nexus-maven-repository-index.524.gz")); //
              var bis = new BufferedInputStream(fis)) {

            var reader = new IndexDataReader(bis);
            IndexingContext context = setupPlexusContext();
            reader.readIndex(new IndexDataReader.IndexDataReadVisitor() {
                @Override
                public void visitDocument(Document doc) {
                    ArtifactInfo ai = IndexUtils.constructArtifactInfo(doc, context);
                    if (ai == null) {
                        return;
                    }
                    if (ai.getGroupId().equals("de.mediathekview")) {
                        if (ai.getArtifactId().equals("MServer")) {
                            if (ai.getVersion().equals("3.1.60")) {
                                System.out.println(ai);
                                System.out.println("\n##################\n");
                            }
                        }
                    }
                    System.out.println();
//                    if (ai.getGroupId().equals("org.yamcs")){
//                        if (ai.getArtifactId().equals("yamcs-api")) {
//                            if (ai.getVersion().equals("5.5.3")) {
//                                System.out.println(ai);
//                                System.out.println("\n##################\n");
//                            }
//                        }
//                    }


                }
            }, context);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private IndexingContext setupPlexusContext() {
        PlexusContainer plexusContainer;
        List<IndexCreator> indexers;
        try {
            var pc = new DefaultPlexusContainer();
            var config = new DefaultContainerConfiguration();
            config.setClassWorld(pc.getClassWorld());
            config.setClassPathScanning(SCANNING_INDEX);

            plexusContainer = new DefaultPlexusContainer(config);

            indexers = new ArrayList<IndexCreator>();
            for (Object component : plexusContainer.lookupList(IndexCreator.class)) {
                indexers.add((IndexCreator) component);
            }

        } catch (PlexusContainerException e) {
            throw new RuntimeException("Cannot construct PlexusContainer for MavenCrawler.", e);
        } catch (ComponentLookupException e) {
            throw new RuntimeException("Cannot add IndexCreators for MavenCrawler.", e);
        }

        var context = (IndexingContext) Proxy.newProxyInstance( //
            getClass().getClassLoader(), //
            new Class[] {IndexingContext.class}, //
            new MyInvocationHandler(indexers));
        return context;
    }


    public static class MyInvocationHandler implements InvocationHandler {

        private List<IndexCreator> indexers;

        public MyInvocationHandler(List<IndexCreator> indexers) {
            this.indexers = indexers;
        }

        public List<IndexCreator> getIndexCreators() {
            return indexers;
        }

        public Object invoke(final Object proxy, final Method method, final Object[] args)
            throws Throwable {
            try {
                final Method localMethod =
                    getClass().getMethod(method.getName(), method.getParameterTypes());
                return localMethod.invoke(this, args);
            } catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException(
                    "Method " + method.getName() + "() is not supported");
            } catch (IllegalAccessException e) {
                throw new UnsupportedOperationException(
                    "Method " + method.getName() + "() is not supported");
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    public static void main(String[] args) {
        final var repo = "https://repo.maven.apache.org/maven2/";
        final var groupId = "de.mediathekview";
        final var artifactId = "MServer";
        final var version = "3.1.60";
        final var urlStr =  repo + groupId.replace('.', '/') + "/" + artifactId + "/" + version;

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                org.jsoup.nodes.Document document = Jsoup.parse(conn.getInputStream(), null, "");
                Elements links = document.select("a[href]");

                for (Element link : links) {
                    String fileName = link.attr("href");
                    if (fileName.endsWith("/")) {  // Skip directories
                        continue;
                    }
                    System.out.println(fileName);
                }
            } else {
                System.out.println("Request failed with response code: " + responseCode);
            }
            conn.disconnect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

