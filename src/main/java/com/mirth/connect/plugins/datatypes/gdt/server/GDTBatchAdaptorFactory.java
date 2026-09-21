/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt.server;

import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptor;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.server.message.DebuggableBatchAdaptorFactory;

public class GDTBatchAdaptorFactory extends DebuggableBatchAdaptorFactory {
	public GDTBatchAdaptorFactory(SourceConnector sourceConnector, SerializerProperties serializerProperties) {
        super(sourceConnector, serializerProperties);
    }

    @Override
    public BatchAdaptor createBatchAdaptor(BatchRawMessage batchRawMessage) {
        GDTBatchAdaptor batchAdaptor = new GDTBatchAdaptor(this, sourceConnector, batchRawMessage);

        batchAdaptor.setBatchProperties(batchProperties);

        return batchAdaptor;
    }
}
