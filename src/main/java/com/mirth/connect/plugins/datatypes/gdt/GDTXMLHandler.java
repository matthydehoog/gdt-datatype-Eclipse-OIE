/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import com.mirth.connect.plugins.datatypes.gdt.GDTParser.Field;

/**
 * Turns the XML produced by {@link GDTReader} (or a transformer) back into GDT. Every line gets its length
 * calculated. Field 8100 (set length) is calculated too, unless that is switched off; when the XML has no
 * 8100 it is added behind 8000, since a set needs one.
 *
 * The lengths are those of the specification: a line counts its three length digits, the four digit field
 * number, the content and CR LF, whichever line ending is written (a line break inside a value counts as CR LF
 * as well). A line that needs a fourth digit for its length counts that digit too, and so does field 8100 when it
 * has more than five digits. They are counted in characters, which are bytes in the single byte character sets
 * that GDT uses.
 *
 * A set can also hold {@link GDTReader#GROUP} elements (when {@code groupTests} produced them): their F####
 * children are read as fields of the set, in the order they appear, exactly as if they had not been nested.
 *
 * A set can also hold {@link GDTReader#CATEGORIES} elements (when {@code groupCategories} produced them):
 * each {@link GDTReader#CATEGORY} child becomes two fields, the category's name (its {@code name} attribute)
 * and its content (its text), numbered from 6330 again in the order the categories appear; which field had
 * which number in the original message is not kept.
 *
 * Either grouping is only a presentation in the XML; it does not change the GDT message.
 */
public class GDTXMLHandler extends DefaultHandler {
    private static final Pattern FIELD_NAME = Pattern.compile("[Ff](\\d{4})");

    private final GDTSerializationProperties properties;
    private final StringBuilder output = new StringBuilder();

    private int depth = 0;
    private List<Field> fields;
    private boolean inGroup;
    private boolean inCategories;
    private int nextCategoryId;
    private String fieldId;
    private String categoryName;
    private StringBuilder text = new StringBuilder();

    public GDTXMLHandler(GDTSerializationProperties properties) {
        this.properties = properties;
    }

    @Override
    public void startElement(String uri, String name, String qName, Attributes atts) throws SAXException {
        depth++;

        if (depth == 2) {
            if (!name.equals(GDTReader.SET)) {
                throw new SAXException("Unexpected element " + name + ": a set is an element named " + GDTReader.SET);
            }
            fields = new ArrayList<Field>();
            nextCategoryId = GDTReader.CATEGORY_FIRST_ID;
        } else if (depth == 3) {
            inGroup = name.equals(GDTReader.GROUP);
            inCategories = name.equals(GDTReader.CATEGORIES);
            if (!inGroup && !inCategories) {
                fieldId = matchFieldName(name, "in a set");
                text.setLength(0);
            }
        } else if (depth == 4 && inGroup) {
            fieldId = matchFieldName(name, "in " + GDTReader.GROUP);
            text.setLength(0);
        } else if (depth == 4 && inCategories) {
            if (!name.equals(GDTReader.CATEGORY)) {
                throw new SAXException("Unexpected element " + name + " in " + GDTReader.CATEGORIES + ": a category is an element named " + GDTReader.CATEGORY);
            }
            categoryName = atts.getValue("name");
            if (categoryName == null) {
                throw new SAXException("A " + GDTReader.CATEGORY + " needs a name attribute");
            }
            text.setLength(0);
        } else if (depth > 3) {
            String label = inCategories ? "Category " + categoryName : "Field F" + fieldId;
            throw new SAXException(label + " can only contain text, not the element " + name);
        }
    }

    private String matchFieldName(String name, String where) throws SAXException {
        Matcher m = FIELD_NAME.matcher(name);
        if (!m.matches()) {
            throw new SAXException("Unexpected element " + name + " " + where + ": a field is an element named F and its four digit number, for example F3101");
        }
        return m.group(1);
    }

    private boolean atField() {
        return (depth == 3 && !inGroup && !inCategories) || (depth == 4 && inGroup);
    }

    private boolean atCategory() {
        return depth == 4 && inCategories;
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (atField() || atCategory()) {
            text.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String name, String qName) throws SAXException {
        if (atField()) {
            fields.add(new Field(fieldId, normalizedText()));
        } else if (atCategory()) {
            if (nextCategoryId > GDTReader.CATEGORY_LAST_ID) {
                throw new SAXException("Too many categories: GDT only has fields " + GDTReader.CATEGORY_FIRST_ID + " to " + (GDTReader.CATEGORY_LAST_ID + 1) + " for them.");
            }
            fields.add(new Field(String.format("%04d", nextCategoryId), categoryName));
            fields.add(new Field(String.format("%04d", nextCategoryId + 1), normalizedText()));
            nextCategoryId += 2;
        } else if (depth == 3 && inGroup) {
            inGroup = false;
        } else if (depth == 3 && inCategories) {
            inCategories = false;
        } else if (depth == 2) {
            writeSet();
        }
        depth--;
    }

    /** A line break in a value is written as a line ending; in here it is one \n. */
    private String normalizedText() {
        return text.toString().replace("\r\n", "\n").replace('\r', '\n');
    }

    public String getOutput() {
        return output.toString();
    }

    private void writeSet() throws SAXException {
        List<Field> set = new ArrayList<Field>(fields);

        if (properties.isCalculateSetLength()) {
            int lengthIndex = -1;
            for (int i = 0; i < set.size(); i++) {
                if (set.get(i).id.equals(GDTParser.FIELD_SET_LENGTH)) {
                    lengthIndex = i;
                    break;
                }
            }
            if (lengthIndex < 0 && !set.isEmpty() && set.get(0).id.equals(GDTParser.FIELD_SET_TYPE)) {
                set.add(1, new Field(GDTParser.FIELD_SET_LENGTH, "00000"));
                lengthIndex = 1;
            }
            if (lengthIndex >= 0) {
                // The length field is part of what it counts. It has five digits, or more when the set is longer.
                int others = 0;
                for (int i = 0; i < set.size(); i++) {
                    if (i != lengthIndex) {
                        others += GDTParser.physicalLength(set.get(i).value);
                    }
                }
                int digits = 5;
                int total = others + GDTParser.LINE_OVERHEAD + digits;
                while (String.valueOf(total).length() > digits) {
                    digits++;
                    total = others + GDTParser.LINE_OVERHEAD + digits;
                }
                set.set(lengthIndex, new Field(GDTParser.FIELD_SET_LENGTH, String.format("%0" + digits + "d", total)));
            }
        }

        String ending = properties.getLineEnding().characters();
        for (Field field : set) {
            int length = GDTParser.physicalLength(field.value);
            if (GDTParser.lineLength(field.value) > GDTParser.MAX_LONG_LINE_LENGTH - 1) {
                throw new SAXException("Field " + field.id + " is too long: a GDT line is at most " + GDTParser.MAX_LONG_LINE_LENGTH + " long, so the content can have at most " + (GDTParser.MAX_LONG_LINE_LENGTH - GDTParser.LINE_OVERHEAD) + " characters (it has " + GDTParser.contentLength(field.value) + "). Split it over several fields.");
            }
            // The specification has three digits. Real devices write four for a value that is longer, and so do we.
            String digits = length > GDTParser.MAX_LINE_LENGTH ? String.format("%04d", length) : String.format("%03d", length);
            output.append(digits).append(field.id).append(field.value.replace("\n", ending)).append(ending);
        }
    }
}
