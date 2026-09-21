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
 * number, the content and CR LF, whichever line ending is written. They are counted in characters, which are
 * bytes in the single byte character sets that GDT uses.
 */
public class GDTXMLHandler extends DefaultHandler {
    private static final Pattern FIELD_NAME = Pattern.compile("[Ff](\\d{4})");

    private final GDTSerializationProperties properties;
    private final StringBuilder output = new StringBuilder();

    private int depth = 0;
    private List<Field> fields;
    private String fieldId;
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
        } else if (depth == 3) {
            Matcher m = FIELD_NAME.matcher(name);
            if (!m.matches()) {
                throw new SAXException("Unexpected element " + name + " in a set: a field is an element named F and its four digit number, for example F3101");
            }
            fieldId = m.group(1);
            text.setLength(0);
        } else if (depth > 3) {
            throw new SAXException("Field F" + fieldId + " can only contain text, not the element " + name);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (depth == 3) {
            text.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String name, String qName) throws SAXException {
        if (depth == 3) {
            String value = text.toString();
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw new SAXException("Field " + fieldId + " contains a line break; a GDT field is one line. Use several fields (for example 6228) instead.");
            }
            fields.add(new Field(fieldId, value));
        } else if (depth == 2) {
            writeSet();
        }
        depth--;
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
                // The length field is part of what it counts, and it always has five digits.
                int total = 0;
                for (int i = 0; i < set.size(); i++) {
                    total += i == lengthIndex ? GDTParser.lineLength("00000") : GDTParser.lineLength(set.get(i).value);
                }
                if (total > 99999) {
                    throw new SAXException("The set is " + total + " long; field 8100 can hold at most 99999");
                }
                set.set(lengthIndex, new Field(GDTParser.FIELD_SET_LENGTH, String.format("%05d", total)));
            }
        }

        String ending = properties.getLineEnding().characters();
        for (Field field : set) {
            int length = GDTParser.lineLength(field.value);
            if (length > GDTParser.MAX_LINE_LENGTH) {
                throw new SAXException("Field " + field.id + " is too long: a GDT line is at most " + GDTParser.MAX_LINE_LENGTH + " long, so the content can have at most " + (GDTParser.MAX_LINE_LENGTH - GDTParser.LINE_OVERHEAD) + " characters (it has " + field.value.length() + "). Split it over several fields.");
            }
            output.append(String.format("%03d", length)).append(field.id).append(field.value).append(ending);
        }
    }
}
