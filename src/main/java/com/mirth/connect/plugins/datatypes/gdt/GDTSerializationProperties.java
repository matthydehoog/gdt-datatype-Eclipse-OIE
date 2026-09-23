/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.datatype.DataTypePropertyDescriptor;
import com.mirth.connect.model.datatype.PropertyEditorType;
import com.mirth.connect.model.datatype.SerializationProperties;

/**
 * Settings of the GDT data type.
 *
 * The engine's XStream deserializer does not run the constructor, so a property that is added in a later
 * version is missing (null, or false) in a saved channel. The getters fall back to the defaults, and the
 * properties that default to true are Booleans for that reason.
 */
public class GDTSerializationProperties extends SerializationProperties {

    /** What ends a line when XML is converted to GDT. The specification asks for CR LF. */
    public enum LineEnding {
        CRLF("\r\n"), LF("\n"), CR("\r");

        private final String characters;

        LineEnding(String characters) {
            this.characters = characters;
        }

        public String characters() {
            return characters;
        }
    }

    private LineEnding lineEnding = LineEnding.CRLF;
    private Boolean calculateSetLength = Boolean.TRUE;
    private Boolean fieldNames = Boolean.TRUE;
    private boolean strict = false;
    private boolean groupResults = false;
    private boolean groupCategories = false;
    private boolean joinResultsText = false;

    public GDTSerializationProperties() {

    }

    public GDTSerializationProperties(GDTSerializationProperties properties) {
        this.lineEnding = properties.getLineEnding();
        this.calculateSetLength = properties.isCalculateSetLength();
        this.fieldNames = properties.isFieldNames();
        this.strict = properties.isStrict();
        this.groupResults = properties.isGroupResults();
        this.groupCategories = properties.isGroupCategories();
        this.joinResultsText = properties.isJoinResultsText();
    }

    @Override
    public Map<String, DataTypePropertyDescriptor> getPropertyDescriptors() {
        Map<String, DataTypePropertyDescriptor> properties = new LinkedHashMap<String, DataTypePropertyDescriptor>();

        properties.put("strict", new DataTypePropertyDescriptor(isStrict(), "Strict Parsing", "If checked, a GDT message is rejected when a line length (the first three digits) or the set length (field 8100) is wrong, or when a set does not start with field 8000. Many devices get these wrong, so by default they are ignored.", PropertyEditorType.BOOLEAN));
        properties.put("fieldNames", new DataTypePropertyDescriptor(isFieldNames(), "Field Names", "If checked, the name of each field from the GDT specification is added to the XML as an attribute, for example <F3101 name=\"Patient name\">. The names are ignored when converting XML to GDT.", PropertyEditorType.BOOLEAN));
        properties.put("calculateSetLength", new DataTypePropertyDescriptor(isCalculateSetLength(), "Calculate Set Length", "If checked, field 8100 (set length) is calculated when converting XML to GDT, and added after field 8000 when the XML has none. If not checked, 8100 is written as it is in the XML. The length of every line (the first three digits) is always calculated.", PropertyEditorType.BOOLEAN));
        properties.put("lineEnding", new DataTypePropertyDescriptor(getLineEnding(), "Line Ending", "What ends a line when XML is converted to GDT. The specification asks for CRLF. When reading GDT, CRLF, LF and CR are all accepted.", PropertyEditorType.OPTION, LineEnding.values()));
        properties.put("groupResults", new DataTypePropertyDescriptor(isGroupResults(), "Group Result Fields", "If checked, a repeated field 8410 (Test ID) and the fields 8411 (Test name), 8420 (Result value) and 8421 (Unit) that follow it are nested together in one <result> element, instead of being siblings of the set, and a run of them is wrapped in one <results> element. A new 8410 ends a <result> and starts the next one; any other field ends the whole <results> run. Converting XML to GDT flattens <results> back into the same fields in the same order, so the GDT message does not change.", PropertyEditorType.BOOLEAN));
        properties.put("groupCategories", new DataTypePropertyDescriptor(isGroupCategories(), "Group Categories", "If checked, an open category (a field 6330, 6332, ..., 6398 with the category name, followed by 6331, 6333, ..., 6399 with its content, for example OrderID) becomes <category name=\"OrderID\">356218126</category> instead of the two F#### fields, and a run of them is wrapped in one <categories> element. Converting XML to GDT turns <categories> back into the same fields, numbered from 6330 again.", PropertyEditorType.BOOLEAN));
        properties.put("joinResultsText", new DataTypePropertyDescriptor(isJoinResultsText(), "Join Results Text", "If checked, several field 8480 (Results text) in a row are joined into one, in the order they appear, with nothing added between them. Some devices split a long text over several 8480 fields instead of using the four digit length that a long line can have.", PropertyEditorType.BOOLEAN));

        return properties;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        if (properties != null) {
            if (properties.get("strict") != null) {
                this.strict = (Boolean) properties.get("strict");
            }
            if (properties.get("fieldNames") != null) {
                this.fieldNames = (Boolean) properties.get("fieldNames");
            }
            if (properties.get("calculateSetLength") != null) {
                this.calculateSetLength = (Boolean) properties.get("calculateSetLength");
            }
            if (properties.get("lineEnding") != null) {
                this.lineEnding = (LineEnding) properties.get("lineEnding");
            }
            if (properties.get("groupResults") != null) {
                this.groupResults = (Boolean) properties.get("groupResults");
            }
            if (properties.get("groupCategories") != null) {
                this.groupCategories = (Boolean) properties.get("groupCategories");
            }
            if (properties.get("joinResultsText") != null) {
                this.joinResultsText = (Boolean) properties.get("joinResultsText");
            }
        }
    }

    public LineEnding getLineEnding() {
        return lineEnding == null ? LineEnding.CRLF : lineEnding;
    }

    public void setLineEnding(LineEnding lineEnding) {
        this.lineEnding = lineEnding;
    }

    public boolean isCalculateSetLength() {
        return calculateSetLength == null || calculateSetLength;
    }

    public void setCalculateSetLength(boolean calculateSetLength) {
        this.calculateSetLength = calculateSetLength;
    }

    public boolean isFieldNames() {
        return fieldNames == null || fieldNames;
    }

    public void setFieldNames(boolean fieldNames) {
        this.fieldNames = fieldNames;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public boolean isGroupResults() {
        return groupResults;
    }

    public void setGroupResults(boolean groupResults) {
        this.groupResults = groupResults;
    }

    public boolean isGroupCategories() {
        return groupCategories;
    }

    public void setGroupCategories(boolean groupCategories) {
        this.groupCategories = groupCategories;
    }

    public boolean isJoinResultsText() {
        return joinResultsText;
    }

    public void setJoinResultsText(boolean joinResultsText) {
        this.joinResultsText = joinResultsText;
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {}
    @Override public void migrate3_2_0(DonkeyElement element) {}
    @Override public void migrate3_3_0(DonkeyElement element) {}
    @Override public void migrate3_4_0(DonkeyElement element) {}
    @Override public void migrate3_5_0(DonkeyElement element) {}
    @Override public void migrate3_6_0(DonkeyElement element) {}
    @Override public void migrate3_7_0(DonkeyElement element) {}
    @Override public void migrate3_9_0(DonkeyElement element) {}
    @Override public void migrate3_11_0(DonkeyElement element) {}
    @Override public void migrate3_11_1(DonkeyElement element) {}
    @Override public void migrate3_12_0(DonkeyElement element) {}
    // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purgedProperties = new HashMap<String, Object>();
        purgedProperties.put("strict", strict);
        purgedProperties.put("fieldNames", isFieldNames());
        purgedProperties.put("calculateSetLength", isCalculateSetLength());
        purgedProperties.put("lineEnding", getLineEnding());
        purgedProperties.put("groupResults", isGroupResults());
        purgedProperties.put("groupCategories", isGroupCategories());
        purgedProperties.put("joinResultsText", isJoinResultsText());
        return purgedProperties;
    }
}
