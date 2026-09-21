/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt.client;

import org.syntax.jedit.tokenmarker.TokenMarker;

import com.mirth.connect.model.attachments.AttachmentHandlerType;
import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.model.util.MessageVocabulary;
import com.mirth.connect.plugins.DataTypeClientPlugin;
import com.mirth.connect.plugins.datatypes.gdt.GDTDataTypeDelegate;
import com.mirth.connect.plugins.datatypes.gdt.GDTVocabulary;

public class GDTDataTypeClientPlugin extends DataTypeClientPlugin {
    private DataTypeDelegate dataTypeDelegate = new GDTDataTypeDelegate();

    public GDTDataTypeClientPlugin(String name) {
        super(name);
    }

    @Override
    public String getDisplayName() {
        return "GDT";
    }

    @Override
    public AttachmentHandlerType getDefaultAttachmentHandlerType() {
        return null;
    }

    @Override
    public TokenMarker getTokenMarker() {
        return null;
    }

    @Override
    public Class<? extends MessageVocabulary> getVocabulary() {
        return GDTVocabulary.class;
    }

    @Override
    public String getTemplateString(byte[] content) throws Exception {
        return null;
    }

    @Override
    public int getMinTreeLevel() {
        return 2;
    }

    @Override
    public String getPluginPointName() {
        return dataTypeDelegate.getName();
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void reset() {}

    @Override
    protected DataTypeDelegate getDataTypeDelegate() {
        return dataTypeDelegate;
    }
}
