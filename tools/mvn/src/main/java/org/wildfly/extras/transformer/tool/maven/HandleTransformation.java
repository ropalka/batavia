/*
 * Copyright 2020 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extras.transformer.tool.maven;

import org.wildfly.extras.transformer.TransformerBuilder;
import org.wildfly.extras.transformer.TransformerFactory;
import org.wildfly.extras.transformer.ArchiveTransformer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HandleTransformation
 *
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 * @author Scott Marlow
 */
final class HandleTransformation {

    private static final Logger LOG = Logger.getLogger(HandleTransformation.class.getName());

    static final String COPY_ONLY_REFERENCE = "jakarta-copy-only.properties";

    private static final Set<String> DEFAULT_COPY_ONLY_EXTENSIONS = loadExtensions(
            HandleTransformation.class.getResourceAsStream("/" + COPY_ONLY_REFERENCE));

    private static Set<String> loadExtensions(InputStream is) {
        if (is == null) return Collections.emptySet();
        Properties props = new Properties();
        try (is) {
            props.load(is);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to load " + COPY_ONLY_REFERENCE, e);
        }
        return props.stringPropertyNames();
    }

    private static Set<String> getCopyOnlyExtensions(String configsDir) {
        if (configsDir != null) {
            File override = new File(configsDir, COPY_ONLY_REFERENCE);
            if (override.isFile()) {
                try {
                    return loadExtensions(Files.newInputStream(override.toPath()));
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "Failed to load " + override.getAbsolutePath() + ", using defaults", e);
                }
            }
        }
        return DEFAULT_COPY_ONLY_EXTENSIONS;
    }

    /**
     * Transform the files contained under the folder path specified.
     *
     * @param folder represents a filesystem path that contains files/subfolders to be transformed.
     */
    static void transformDirectory(final File folder, final File targetFolder, final String configsDir, final boolean verbose, final boolean overwrite, boolean invert) throws IOException {
        transformDirectory(folder, targetFolder, configsDir, verbose, overwrite, invert, Collections.emptySet());
    }

    /**
     * Transform the files contained under the folder path specified.
     *
     * @param folder represents a filesystem path that contains files/subfolders to be transformed.
     */
    static void transformDirectory(final File folder, final File targetFolder, final String configsDir, final boolean verbose, final boolean overwrite, boolean invert, final
                                   Set<String> ignored) throws IOException {
        final File[] files = folder.listFiles();
        if (files == null) {
            LOG.log(Level.WARNING, "No files found in directory: {0}", folder.getAbsolutePath());
            return;
        }
        LOG.log(Level.FINE, "Transforming directory contents: {0} -> {1} ({2} entries)",
                new Object[]{folder.getAbsolutePath(), targetFolder.getAbsolutePath(), files.length});
        for (File sourceFile : files) {
            File targetFile = new File(targetFolder, sourceFile.getName());

            if (sourceFile.isDirectory()) {
                transformDirectory(sourceFile, new File(targetFolder, sourceFile.getName()), configsDir, verbose, overwrite, invert, ignored);
            } else {
                if (targetFile.exists()) {
                    if (overwrite) {
                        LOG.log(Level.FINE, "Overwriting existing file: {0}", targetFile.getAbsolutePath());
                        targetFile.delete();
                        transformFile(sourceFile, new File(targetFolder, sourceFile.getName()), configsDir, verbose, invert, ignored);
                    } else {
                        LOG.log(Level.FINE, "Skipping existing file (overwrite=false): {0}", targetFile.getAbsolutePath());
                    }
                } else {
                    transformFile(sourceFile, new File(targetFolder, sourceFile.getName()), configsDir, verbose, invert, ignored);
                }
            }
        }
    }

    static void transformFile(final File sourceFile, final File targetFile, final String configsDir, final boolean verbose, final boolean invert) throws IOException {
        transformFile(sourceFile, targetFile, configsDir, verbose, invert, Collections.emptySet());
    }

    static void transformFile(final File sourceFile, final File targetFile, final String configsDir, final boolean verbose, final boolean invert, Set<String> ignored) throws IOException {
        if (!sourceFile.exists()) {
            throw new IllegalArgumentException("input file " + sourceFile.getName() + " does not exist");
        }
        // Check if this is an ignored file
        final String path = sourceFile.getPath();
        for (String ignore : ignored) {
            if (path.endsWith(ignore)) {
                LOG.log(Level.FINE, "Ignoring file: {0}", path);
                return;
            }
        }
        // Copy file types listed in jakarta-copy-only.properties directly without attempting transformation
        final String name = sourceFile.getName();
        final int dot = name.lastIndexOf('.');
        if (dot >= 0 && getCopyOnlyExtensions(configsDir).contains(name.substring(dot))) {
            Files.createDirectories(targetFile.toPath().getParent());
            LOG.log(Level.FINE, "Copying as-is (excluded extension): {0}", path);
            Files.copy(sourceFile.toPath(), targetFile.toPath());
            return;
        }
        TransformerBuilder builder = TransformerFactory.getInstance().newTransformer();
        if (configsDir != null) {
            builder.setConfigsDir(configsDir);
        }
        builder.setVerbose(verbose);
        builder.setInvert(invert);
        ArchiveTransformer transformer = builder.build();
        if (!sourceFile.isDirectory()) {
            Files.createDirectories(targetFile.toPath().getParent());
        }
        LOG.log(Level.FINE, "Transforming: {0} -> {1}",
                new Object[]{sourceFile.getAbsolutePath(), targetFile.getAbsolutePath()});
        try {
            transformer.transform(sourceFile, targetFile);
        } catch (RuntimeException e) {
            if (verbose) {
                LOG.log(Level.WARNING, "Transformation not supported for " + sourceFile.getAbsolutePath() + ", copying as-is", e);
            } else {
                LOG.log(Level.INFO, "Transformation not supported for {0}, copying as-is", sourceFile.getAbsolutePath());
            }
            Files.copy(sourceFile.toPath(), targetFile.toPath());
        }
    }

}
