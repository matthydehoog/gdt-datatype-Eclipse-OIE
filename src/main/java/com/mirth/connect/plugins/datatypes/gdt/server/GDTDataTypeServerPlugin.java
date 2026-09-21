/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt.server;

import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptorFactory;
import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.plugins.datatypes.gdt.GDTDataTypeDelegate;

public class GDTDataTypeServerPlugin extends DataTypeServerPlugin {
    private DataTypeDelegate dataTypeDelegate = new GDTDataTypeDelegate();

    @Override
    public String getPluginPointName() {
        return dataTypeDelegate.getName();
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public BatchAdaptorFactory getBatchAdaptorFactory(SourceConnector sourceConnector, SerializerProperties properties) {
        return new GDTBatchAdaptorFactory(sourceConnector, properties);
    }

    @Override
    protected DataTypeDelegate getDataTypeDelegate() {
        return dataTypeDelegate;
    }
}
