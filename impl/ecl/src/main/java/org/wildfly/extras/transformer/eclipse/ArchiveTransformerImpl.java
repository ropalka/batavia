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
package org.wildfly.extras.transformer.eclipse;

import static org.slf4j.helpers.NOPLogger.NOP_LOGGER;

import org.eclipse.transformer.TransformOptions;
import org.eclipse.transformer.action.Changes;
import org.eclipse.transformer.action.ContainerChanges;
import org.eclipse.transformer.Transformer;
import org.wildfly.extras.transformer.ArchiveTransformer;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:ropalka@redhat.com">Richard Opálka</a>
 */
final class ArchiveTransformerImpl extends ArchiveTransformer {

    private static final Logger LOG = Logger.getLogger(ArchiveTransformerImpl.class.getName());

    ArchiveTransformerImpl(final File configsDir, final boolean verbose, final boolean invert) {
        super(configsDir, verbose, invert);
    }

    @Override
    public boolean transform(final File inJarFile, final File outJarFile) {
        boolean transformed;
        try {
            LOG.log(Level.FINE, "Transforming input [{0}] to output [{1}] (isDirectory: {2}, verbose: {3}, invert: {4})",
                    new Object[]{inJarFile.getAbsolutePath(), outJarFile.getAbsolutePath(), inJarFile.isDirectory(), verbose, invert});
            TransformOptions transformOptions = new BataviaTransformOptions(configsDir, verbose, invert, inJarFile, outJarFile);
            transformed = transform(transformOptions, !verbose);
            LOG.log(Level.FINE, "Transformation result for [{0}]: transformed={1}", new Object[]{inJarFile.getName(), transformed});
        } catch (Exception ex) {
            LOG.log(Level.FINE, "Transformation failed for input [" + inJarFile.getAbsolutePath() + "]", ex);
            throw new RuntimeException(ex);
        }
        return transformed;
    }

    private boolean transform(TransformOptions transformOptions, boolean silent) throws IOException {
        Transformer jTrans;
        if (silent) {
            jTrans = new Transformer(NOP_LOGGER, transformOptions);
        } else {
            jTrans = new Transformer(transformOptions);
        }

        Transformer.ResultCode rc = jTrans.run();
        LOG.log(Level.FINE, "Eclipse Transformer result code: {0}", rc);
        if (rc != Transformer.ResultCode.SUCCESS_RC) {
            throw new IOException("Error occurred during transformation. Error code " + rc);
        }
        Changes changes = jTrans.getLastActiveChanges();
        if (changes != null) {
            LOG.log(Level.FINE, "Changes: input=[{0}] output=[{1}] changed={2} renamed={3} contentChanged={4} elapsed={5}ms",
                    new Object[]{changes.getInputResourceName(), changes.getOutputResourceName(),
                    changes.isChanged(), changes.isRenamed(), changes.isContentChanged(), changes.getElapsedMillis()});
            if (changes instanceof ContainerChanges) {
                ContainerChanges cc = (ContainerChanges) changes;
                LOG.log(Level.FINE, "Container details: resources={0} selected={1} accepted={2} changed={3} renamed={4} contentChanged={5} failed={6} duplicated={7}",
                        new Object[]{cc.getAllResources(), cc.getAllSelected(), cc.getAllAccepted(),
                        cc.getAllChanged(), cc.getAllRenamed(), cc.getAllContentChanged(),
                        cc.getAllFailed(), cc.getAllDuplicated()});
                for (Map.Entry<String, int[]> entry : cc.getChangedByAction().entrySet()) {
                    LOG.log(Level.FINE, "  Action [{0}]: changed={1}", new Object[]{entry.getKey(), entry.getValue()[0]});
                }
            }
            return changes.isChanged();
        }
        LOG.warning("No changes information available from transformer");
        return false;
    }

    @Override
    public boolean canTransformIndividualClassFile() {
        return true;
    }

}
