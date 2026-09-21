/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt.server;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;

import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchMessageException;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReceiver;
import com.mirth.connect.plugins.datatypes.gdt.GDTBatchProperties;
import com.mirth.connect.plugins.datatypes.gdt.GDTSetSplitter;
import com.mirth.connect.plugins.datatypes.gdt.GDTBatchProperties.SplitType;
import com.mirth.connect.server.controllers.ContextFactoryController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.ScriptController;
import com.mirth.connect.server.message.DebuggableBatchAdaptor;
import com.mirth.connect.server.message.DebuggableBatchAdaptorFactory;
import com.mirth.connect.server.userutil.SourceMap;
import com.mirth.connect.server.util.CompiledScriptCache;
import com.mirth.connect.server.util.javascript.JavaScriptExecutorException;
import com.mirth.connect.server.util.javascript.JavaScriptScopeUtil;
import com.mirth.connect.server.util.javascript.JavaScriptTask;
import com.mirth.connect.server.util.javascript.JavaScriptUtil;
import com.mirth.connect.server.util.javascript.MirthContextFactory;

public class GDTBatchAdaptor extends DebuggableBatchAdaptor {
    private Logger logger = LogManager.getLogger(this.getClass());
    private ContextFactoryController contextFactoryController = ControllerFactory.getFactory().createContextFactoryController();
    private BufferedReader bufferedReader;
    private GDTSetSplitter setSplitter;

    public GDTBatchAdaptor(DebuggableBatchAdaptorFactory factory, SourceConnector sourceConnector, BatchRawMessage batchRawMessage) {
        super(factory, sourceConnector, batchRawMessage);
    }

    @Override
    public void cleanup() throws BatchMessageException {}

    @Override
    protected String getNextMessage(int batchSequenceId) throws Exception {
        if (batchRawMessage.getBatchMessageSource() instanceof BatchMessageReader) {
            if (batchSequenceId == 1) {
                BatchMessageReader batchMessageReader = (BatchMessageReader) batchRawMessage.getBatchMessageSource();
                bufferedReader = new BufferedReader(batchMessageReader.getReader());
                setSplitter = new GDTSetSplitter(bufferedReader);
            }
            return getMessageFromReader();
        } else if (batchRawMessage.getBatchMessageSource() instanceof BatchMessageReceiver) {
            return getMessageFromReceiver((BatchMessageReceiver) batchRawMessage.getBatchMessageSource());
        }

        return null;
    }

    private String getMessageFromReceiver(BatchMessageReceiver batchMessageReceiver) throws Exception {
        byte[] bytes = null;

        if (batchMessageReceiver.canRead()) {
            try {
                bytes = batchMessageReceiver.readBytes();
            } finally {
                batchMessageReceiver.readCompleted();
            }

            if (bytes != null) {
                return batchMessageReceiver.getStringFromBytes(bytes);
            }
        }
        return null;
    }

    private String getMessageFromReader() throws Exception {
        GDTBatchProperties batchProperties = (GDTBatchProperties) getBatchProperties();
        SplitType splitType = batchProperties.getSplitType();
        if (splitType == SplitType.Set) {
            return setSplitter.next();
        } else if (splitType == SplitType.JavaScript) {
            if (StringUtils.isEmpty(batchProperties.getBatchScript())) {
                throw new BatchMessageException("No batch script was set.");
            }
           

            try {
                final String batchScriptId = ScriptController.getScriptId(ScriptController.BATCH_SCRIPT_KEY, sourceConnector.getChannelId());
                final Boolean debug = ((DebuggableBatchAdaptorFactory) getFactory()).isDebug();
                MirthContextFactory contextFactory = getContextFactoryAndRecompile(contextFactoryController, debug, batchScriptId, batchProperties.getBatchScript());

                triggerDebug(debug);

                String result = JavaScriptUtil.execute(new JavaScriptTask<String>(contextFactory, "GDT Batch Adaptor", sourceConnector) {
                    @Override
                    public String doCall() throws Exception {
                        Script compiledScript = CompiledScriptCache.getInstance().getCompiledScript(batchScriptId);

                        if (compiledScript == null) {
                            logger.error("Batch script could not be found in cache");
                            return null;
                        } else {
                            Logger scriptLogger = LogManager.getLogger(ScriptController.BATCH_SCRIPT_KEY.toLowerCase());

                            try {
                                Scriptable scope = JavaScriptScopeUtil.getBatchProcessorScope(getContextFactory(), scriptLogger, sourceConnector.getChannelId(), sourceConnector.getChannel().getName(), getScopeObjects(bufferedReader));
                                return (String) Context.jsToJava(executeScript(compiledScript, scope), String.class);
                            } finally {
                                Context.exit();
                            }
                        }
                    }
                });

                if (StringUtils.isEmpty(result)) {
                    return null;
                } else {
                    return result;
                }
            } catch (JavaScriptExecutorException e) {
                logger.error(e.getCause());
            } catch (Throwable e) {
                logger.error(e);
            }
        } else {
            throw new BatchMessageException("No valid batch splitting method configured");
        }

        return null;
    }

    private Map<String, Object> getScopeObjects(Reader in) {
        Map<String, Object> scopeObjects = new HashMap<String, Object>();

        // Provide the reader in the scope
        scopeObjects.put("reader", in);

        scopeObjects.put("sourceMap", new SourceMap(Collections.unmodifiableMap(batchRawMessage.getSourceMap())));

        return scopeObjects;
    }
}
