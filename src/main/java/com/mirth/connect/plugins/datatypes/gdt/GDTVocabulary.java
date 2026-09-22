/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import com.mirth.connect.model.util.MessageVocabulary;

/** Descriptions for the message tree of the administrator: the fields of GDT 2.1 by their id (F3101, ...). */
public class GDTVocabulary extends MessageVocabulary {

    public GDTVocabulary(String version, String type) {
        super(version, type);
    }

    @Override
    public String getDescription(String elementId) {
        if (elementId == null) {
            return "";
        }
        if (elementId.equals(GDTReader.SET)) {
            return "Set";
        }
        if (elementId.equals(GDTReader.GROUP)) {
            return "Test";
        }
        if (elementId.equals(GDTReader.CATEGORIES)) {
            return "Categories";
        }
        if (elementId.equals(GDTReader.CATEGORY)) {
            return "Category";
        }
        if (elementId.length() == 5 && (elementId.charAt(0) == 'F' || elementId.charAt(0) == 'f')) {
            String name = GDTFields.fieldName(elementId.substring(1));
            return name == null ? "" : name;
        }
        return "";
    }

    @Override
    public String getDataType() {
        return GDTDataTypeDelegate.NAME;
    }
}
