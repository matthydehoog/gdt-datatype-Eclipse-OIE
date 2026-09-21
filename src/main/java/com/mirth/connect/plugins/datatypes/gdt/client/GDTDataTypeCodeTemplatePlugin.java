/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt.client;

import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.plugins.DataTypeCodeTemplatePlugin;
import com.mirth.connect.plugins.datatypes.gdt.GDTDataTypeDelegate;

public class GDTDataTypeCodeTemplatePlugin extends DataTypeCodeTemplatePlugin {

    public GDTDataTypeCodeTemplatePlugin(String name) {
        super(name);
    }

    @Override
    protected DataTypeDelegate getDataTypeDelegate() {
        return new GDTDataTypeDelegate();
    }

    @Override
    protected String getDisplayName() {
        return "GDT";
    }
}
