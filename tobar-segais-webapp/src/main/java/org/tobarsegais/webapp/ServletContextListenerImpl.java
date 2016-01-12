/*
 * Copyright 2011 Stephen Connolly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tobarsegais.webapp;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.jsoup.Jsoup;
import org.tobarsegais.webapp.data.Extension;
import org.tobarsegais.webapp.data.Index;
import org.tobarsegais.webapp.data.IndexEntry;
import org.tobarsegais.webapp.data.Plugin;
import org.tobarsegais.webapp.data.Toc;
import org.tobarsegais.webapp.data.TocEntry;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Loads all the bundles.
 */
public class ServletContextListenerImpl implements ServletContextListener {

    public static final String BUNDLE_PATH = "/WEB-INF/bundles";

    public static final Version LUCENE_VERSON = Version.LUCENE_36;

    public void contextInitialized(ServletContextEvent sce) {
        ServletContext application = sce.getServletContext();
        Map<String, String> bundles = new HashMap<String, String>();
        Map<String, String> redirects = new HashMap<String, String>();
        Map<String, String> aliases = new HashMap<String, String>();
        Map<String, Toc> contents = new LinkedHashMap<String, Toc>();
        List<IndexEntry> keywords = new ArrayList<IndexEntry>();
        Directory index = new RAMDirectory();
        Analyzer analyzer = new StandardAnalyzer(LUCENE_VERSON);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(LUCENE_VERSON, analyzer);
        IndexWriter indexWriter;
        try {
            indexWriter = new IndexWriter(index, indexWriterConfig);
        } catch (IOException e) {
            application.log("Cannot create search index. Search will be unavailable.", e);
            indexWriter = null;
        }
        final Set<String> paths = (Set<String>) application.getResourcePaths(BUNDLE_PATH);
        if (paths == null) {
            application.log(String.format("Could not find any bundles at %s", BUNDLE_PATH));
        } else {
            for (String path : paths) {
                if (path.endsWith(".jar")) {
                    String key = path.substring("/WEB-INF/bundles/".length(), path.lastIndexOf(".jar"));
                    application.log("Parsing " + path);
                    URLConnection connection = null;
                    try {
                        URL url = new URL("jar:" + application.getResource(path) + "!/");
                        connection = url.openConnection();
                        if (!(connection instanceof JarURLConnection)) {
                            application.log(path + " is not a jar file, ignoring");
                            continue;
                        }
                        JarURLConnection jarConnection = (JarURLConnection) connection;
                        JarFile jarFile = jarConnection.getJarFile();
                        Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            String symbolicName = manifest.getMainAttributes().getValue("Bundle-SymbolicName");
                            if (symbolicName != null) {
                                int i = symbolicName.indexOf(';');
                                if (i != -1) {
                                    symbolicName = symbolicName.substring(0, i);
                                }
                                bundles.put(symbolicName, key);
                                key = symbolicName;
                            }
                        }

                        JarEntry pluginEntry = jarFile.getJarEntry("plugin.xml");
                        if (pluginEntry == null) {
                            application.log(path + " does not contain a plugin.xml file, ignoring");
                            continue;
                        }
                        Plugin plugin = Plugin.read(jarFile.getInputStream(pluginEntry));

                        Extension tocExtension = plugin.getExtension("org.eclipse.help.toc");
                        if (tocExtension == null || tocExtension.getFile("toc") == null) {
                            application.log(path + " does not contain a 'org.eclipse.help.toc' extension, ignoring");
                            continue;
                        }
                        JarEntry tocEntry = jarFile.getJarEntry(tocExtension.getFile("toc"));
                        if (tocEntry == null) {
                            application.log(path + " is missing the referenced toc: " + tocExtension.getFile("toc")
                                    + ", ignoring");
                            continue;
                        }
                        Toc toc;
                        try {
                            toc = Toc.read(jarFile.getInputStream(tocEntry));
                        } catch (IllegalStateException e) {
                            application.log("Could not parse " + path + " due to " + e.getMessage(), e);
                            continue;
                        }
                        contents.put(key, toc);

                        Extension indexExtension = plugin.getExtension("org.eclipse.help.index");
                        if (indexExtension != null && indexExtension.getFile("index") != null) {
                            JarEntry indexEntry = jarFile.getJarEntry(indexExtension.getFile("index"));
                            if (indexEntry != null) {
                                try {
                                    keywords.addAll(Index.read(key, jarFile.getInputStream(indexEntry)).getChildren());
                                } catch (IllegalStateException e) {
                                    application.log("Could not parse " + path + " due to " + e.getMessage(), e);
                                }
                            } else {
                                application.log(path + " is missing the referenced index: " + indexExtension
                                        .getFile("index"));
                            }

                        }
                        application.log(path + " successfully parsed and added as " + key);
                        if (indexWriter != null) {
                            application.log("Indexing content of " + path);
                            Set<String> files = new HashSet<String>();
                            Stack<Iterator<? extends TocEntry>> stack = new Stack<Iterator<? extends TocEntry>>();
                            stack.push(Collections.singleton(toc).iterator());
                            while (!stack.empty()) {
                                Iterator<? extends TocEntry> cur = stack.pop();
                                if (cur.hasNext()) {
                                    TocEntry entry = cur.next();
                                    stack.push(cur);
                                    if (!entry.getChildren().isEmpty()) {
                                        stack.push(entry.getChildren().iterator());
                                    }
                                    String file = entry.getHref();
                                    if (file == null) {
                                        continue;
                                    }
                                    int hashIndex = file.indexOf('#');
                                    if (hashIndex != -1) {
                                        file = file.substring(0, hashIndex);
                                    }
                                    if (files.contains(file)) {
                                        // already indexed
                                        // todo work out whether to just pull the section
                                        continue;
                                    }
                                    Document document = new Document();
                                    document.add(
                                            new Field("title", entry.getLabel(), Field.Store.YES,
                                                    Field.Index.ANALYZED));
                                    document.add(new Field("href", key + "/" + entry.getHref(), Field.Store.YES,
                                            Field.Index.NO));
                                    JarEntry docEntry = jarFile.getJarEntry(file);
                                    if (docEntry == null) {
                                        // ignore missing file
                                        continue;
                                    }
                                    InputStream inputStream = null;
                                    try {
                                        inputStream = jarFile.getInputStream(docEntry);
                                        org.jsoup.nodes.Document docDoc = Jsoup.parse(IOUtils.toString(inputStream));
                                        document.add(new Field("contents", docDoc.body().text(), Field.Store.NO,
                                                Field.Index.ANALYZED));
                                        indexWriter.addDocument(document);
                                    } finally {
                                        IOUtils.closeQuietly(inputStream);
                                    }
                                }
                            }
                        }
                    } catch (XMLStreamException e) {
                        application.log("Could not parse " + path + " due to " + e.getMessage(), e);
                    } catch (MalformedURLException e) {
                        application.log("Could not parse " + path + " due to " + e.getMessage(), e);
                    } catch (IOException e) {
                        application.log("Could not parse " + path + " due to " + e.getMessage(), e);
                    } finally {
                        if (connection instanceof HttpURLConnection) {
                            // should never be the case, but we should try to be sure
                            ((HttpURLConnection) connection).disconnect();
                        }
                    }
                } else if ("/WEB-INF/bundles/permanent-redirect.properties".equals(path)) {
                    final Properties properties = new Properties();
                    try {
                        InputStream stream = application.getResourceAsStream(path);
                        try {
                            properties.load(stream);
                        } finally {
                            IOUtils.closeQuietly(stream);
                        }
                    } catch (IOException e) {
                        application.log("Cannot read permanent redirects.", e);
                    }
                    for (String key : properties.stringPropertyNames()) {
                        final String value = properties.getProperty(key);
                        if (StringUtils.isNotBlank(value)) {
                            final String src = StringUtils.removeEnd(StringUtils.removeStart(key, "/"), "/");
                            final String dst = StringUtils.removeEnd(StringUtils.removeStart(value, "/"), "/");
                            application
                                    .log(String.format("Adding HTTP/301 (permanent) from bundle %s to %s", src, dst));
                            redirects.put(src, dst);
                        }
                    }
                } else if ("/WEB-INF/bundles/temporary-redirect.properties".equals(path)) {
                    final Properties properties = new Properties();
                    try {
                        InputStream stream = application.getResourceAsStream(path);
                        try {
                            properties.load(stream);
                        } finally {
                            IOUtils.closeQuietly(stream);
                        }
                    } catch (IOException e) {
                        application.log("Cannot read temporary redirects.", e);
                    }
                    for (String key : properties.stringPropertyNames()) {
                        final String value = properties.getProperty(key);
                        if (StringUtils.isNotBlank(value)) {
                            final String src = StringUtils.removeEnd(StringUtils.removeStart(key, "/"), "/");
                            final String dst = StringUtils.removeEnd(StringUtils.removeStart(value, "/"), "/");
                            application
                                    .log(String.format("Adding HTTP/302 (temporary) from bundle %s to %s", src, dst));
                            aliases.put(src, dst);
                        }
                    }
                } else if ("/WEB-INF/bundles/sequence.lst".equals(path)) {
                    List<Pattern> sequence = new ArrayList<Pattern>();
                    try {
                        InputStream stream = application.getResourceAsStream(path);
                        try {
                            for (String line: IOUtils.readLines(stream, "UTF-8")) {
                                int hash = line.indexOf('#');
                                if (hash != -1) line = line.substring(0, hash);
                                if (line.startsWith("#") || StringUtils.isBlank(line)) {
                                    continue;
                                }
                                try {
                                    sequence.add(Pattern.compile(line));
                                } catch (PatternSyntaxException e) {
                                    application.log("Ignoring malformed regex: " + line, e);
                                }
                            }
                        } finally {
                            IOUtils.closeQuietly(stream);
                        }
                    } catch (IOException e) {
                        application.log("Cannot read sequence.lst.", e);
                    }
                    application.setAttribute("sequence", Collections.unmodifiableList(sequence));
                }
            }
        }
        if (indexWriter != null) {
            try {
                indexWriter.close();
            } catch (IOException e) {
                application.log("Cannot create search index. Search will be unavailable.", e);
            }
            application.setAttribute("index", index);
        }

        application.setAttribute("toc", Collections.unmodifiableMap(contents));
        application.setAttribute("keywords", new Index(keywords));
        application.setAttribute("bundles", Collections.unmodifiableMap(bundles));
        application.setAttribute("redirects", Collections.unmodifiableMap(redirects));
        application.setAttribute("aliases", Collections.unmodifiableMap(aliases));
        application.setAttribute("analyzer", analyzer);
        application.setAttribute("contentsQueryParser", new QueryParser(LUCENE_VERSON, "contents", analyzer));
        Properties properties = new Properties();
        try {
            // start with the global defaults
            InputStream stream = getClass().getResourceAsStream("default-context-param.properties"); 
            try {
                properties.load(stream);
            } finally {
                IOUtils.closeQuietly(stream);
            }
        } catch (IOException e) {
            application.log("Cannot read default-context-param.properties.", e);
        }
        try {
            // now add the webapp defaults
            InputStream stream = application.getResourceAsStream("/WEB-INF/default-context-param.properties");
            if (stream != null) {
                try {
                    properties.load(stream);
                } finally {
                    IOUtils.closeQuietly(stream);
                }
            }
        } catch (IOException e) {
            application.log("Cannot read default-context-param.properties.", e);
        }
        for (String key : properties.stringPropertyNames()) {
            final String value = properties.getProperty(key);
            if (StringUtils.isBlank(value)) {
                application.removeAttribute("context-param." + key);
            } else {
                application.setAttribute("context-param." + key, value);
            }
        }
        // now come the actual values from web.xml
        for (String name: Collections.list((Enumeration<String>)application.getInitParameterNames())) {
            application.setAttribute("context-param." + name, application.getInitParameter(name));
        }
        properties = new Properties();
        try {
            // finally we let anyone bundling 
            InputStream stream = application.getResourceAsStream("/WEB-INF/override-context-param.properties");
            if (stream != null) {
                try {
                    properties.load(stream);
                } finally {
                    IOUtils.closeQuietly(stream);
                }
            }
        } catch (IOException e) {
            application.log("Cannot read override-context-param.properties.", e);
        }
        for (String key : properties.stringPropertyNames()) {
            final String value = properties.getProperty(key);
            if (StringUtils.isBlank(value)) {
                application.removeAttribute("context-param." + key);
            } else {
                application.setAttribute("context-param." + key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Toc> getTablesOfContents(ServletContext application) {
        return (Map<String, Toc>) application.getAttribute("toc");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, String> getBundles(ServletContext application) {
        return (Map<String, String>) application.getAttribute("bundles");
    }

    @SuppressWarnings("unchecked")
    public static Index getKeywordsIndex(ServletContext application) {
        return (Index) application.getAttribute("keywords");
    }

    @SuppressWarnings("unchecked")
    public static Directory getDirectory(ServletContext application) {
        return (Directory) application.getAttribute("index");
    }

    @SuppressWarnings("unchecked")
    public static Analyzer getAnalyzer(ServletContext application) {
        return (Analyzer) application.getAttribute("analyzer");
    }
    
    @SuppressWarnings("unchecked")
    public static List<Pattern> getSequence(ServletContext application) {
        return (List<Pattern>) application.getAttribute("sequence");
    }
    
    public static int getSequenceOrder(ServletContext application, String key) {
        int i = 0;
        for (Pattern p : getSequence(application)) {
            if (p.matcher(key).matches()) return i;
            i++;
        }
        return i;
    }
    
    @SuppressWarnings("unchecked")
    public static String getInitParameter(ServletContext application, String name) {
        return (String) application.getAttribute("context-param." + name);
    }

    @SuppressWarnings("unchecked")
    public static QueryParser getContentsQueryParser(ServletContext application) {
        return (QueryParser) application.getAttribute("contentsQueryParser");
    }

    public void contextDestroyed(ServletContextEvent sce) {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
