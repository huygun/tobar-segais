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

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ContentServlet extends HttpServlet {

    /**
     * Some bundles can use links that are relative to {@code PLUGINS_ROOT} while others use relative links.
     */
    public static final String PLUGINS_ROOT = "/PLUGINS_ROOT/";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null) {
            path = req.getServletPath();
        }
        int index = path.indexOf(PLUGINS_ROOT);
        if (index != -1) {
            path = path.substring(index + PLUGINS_ROOT.length() - 1);
        }
        ServletContext ctx = getServletContext();
        Map<String, String> bundles = (Map<String, String>) ctx.getAttribute("bundles");
        Map<String, String> redirects = (Map<String, String>) ctx.getAttribute("redirects");
        Map<String, String> aliases = (Map<String, String>) ctx.getAttribute("aliases");
        for (index = path.indexOf('/'); index != -1; index = path.indexOf('/', index + 1)) {
            if (index == 0) {
                // there is no bundle with an empty name
                continue;
            }
            String key = path.substring(0, index);
            if (key.startsWith("/")) {
                key = key.substring(1);
            }
            if (redirects.containsKey(key)) {
                resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
                final String permanentKey = StringUtils.removeEnd(StringUtils.removeStart(redirects.get(key), "/"), "/");
                resp.setHeader("Location",
                        req.getContextPath() + req.getServletPath() + "/" + permanentKey + path.substring(index));
                resp.flushBuffer();
                return;
            }
            if (aliases.containsKey(key)) {
                resp.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                final String temporaryKey = StringUtils.removeEnd(StringUtils.removeStart(aliases.get(key), "/"),"/");
                resp.setHeader("Location",
                        req.getContextPath() + req.getServletPath() + "/" + temporaryKey + path.substring(index));
                resp.flushBuffer();
                return;
            }
            if (bundles.containsKey(key)) {
                key = bundles.get(key);
            }
            URL resource = ctx.getResource(ServletContextListenerImpl.BUNDLE_PATH + "/" + key + ".jar");
            if (resource == null) {
                continue;
            }
            URL jarResource = new URL("jar:" + resource + "!/");
            URLConnection connection = jarResource.openConnection();
            if (!(connection instanceof JarURLConnection)) {
                continue;
            }
            JarURLConnection jarConnection = (JarURLConnection) connection;
            JarFile jarFile = jarConnection.getJarFile();
            try {
                int endOfFileName = path.indexOf('#', index);
                endOfFileName = endOfFileName == -1 ? path.length() : endOfFileName;
                String fileName = path.substring(index + 1, endOfFileName);
                JarEntry jarEntry = jarFile.getJarEntry(fileName);
                if (jarEntry == null) {
                    continue;
                }
                long size = jarEntry.getSize();
                if (size > 0 && size < Integer.MAX_VALUE) {
                    resp.setContentLength((int) size);
                }
                String mimeType = ctx.getMimeType(fileName);
                resp.setContentType(mimeType);
                String cacheControl = ServletContextListenerImpl.getInitParameter(ctx, "cache-control.mime." + mimeType);
                if (cacheControl == null) {
                    int slash = mimeType.indexOf('/');
                    if (slash != -1) {
                        cacheControl = ServletContextListenerImpl.getInitParameter(ctx, "cache-control.mime." + mimeType.substring(0, slash) + "/*");
                    }
                }
                if (cacheControl == null) {
                    cacheControl = ServletContextListenerImpl.getInitParameter(ctx, "cache-control.default");
                }
                if (StringUtils.isNotBlank(cacheControl)) {
                    resp.setHeader("Cache-Control", cacheControl);
                }
                InputStream in = null;
                OutputStream out = resp.getOutputStream();
                try {
                    in = jarFile.getInputStream(jarEntry);
                    IOUtils.copy(in, out);
                } finally {
                    IOUtils.closeQuietly(in);
                    out.close();
                }
                return;
            } finally {
                //jarFile.close();
            }
        }
        resp.sendError(404);
    }

    @Override
    protected long getLastModified(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null) {
            path = req.getServletPath();
        }
        int index = path.indexOf(PLUGINS_ROOT);
        if (index != -1) {
            path = path.substring(index + PLUGINS_ROOT.length() - 1);
        }
        Map<String, String> bundles = (Map<String, String>) getServletContext().getAttribute("bundles");
        try {
            for (index = path.indexOf('/'); index != -1; index = path.indexOf('/', index + 1)) {
                String key = path.substring(0, index);
                if (key.startsWith("/")) {
                    key = key.substring(1);
                }
                if (bundles.containsKey(key)) {
                    key = bundles.get(key);
                }
                URL resource =
                        getServletContext().getResource(ServletContextListenerImpl.BUNDLE_PATH + "/" + key + ".jar");
                if (resource == null) {
                    continue;
                }
                URL jarResource = new URL("jar:" + resource + "!/");
                URLConnection connection = jarResource.openConnection();
                if (!(connection instanceof JarURLConnection)) {
                    continue;
                }
                JarURLConnection jarConnection = (JarURLConnection) connection;
                JarFile jarFile = jarConnection.getJarFile();
                try {
                    int endOfFileName = path.indexOf('#', index);
                    endOfFileName = endOfFileName == -1 ? path.length() : endOfFileName;
                    String fileName = path.substring(index + 1, endOfFileName);
                    JarEntry jarEntry = jarFile.getJarEntry(fileName);
                    if (jarEntry == null) {
                        continue;
                    }
                    return jarEntry.getTime();
                } finally {
                    //jarFile.close();
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return -1;
    }
}
