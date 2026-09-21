/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.List;

import org.openintegrationengine.engine.plugins.datatypes.AbstractXMLReader;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.mirth.connect.plugins.datatypes.gdt.GDTParser.Field;
import com.mirth.connect.plugins.datatypes.gdt.GDTParser.FieldSet;

/**
 * Turns a GDT message into SAX events. A set is an element <code>set</code>, a field an element named
 * F and its four digit number:
 *
 * <pre>
 * &lt;GDT&gt;
 *   &lt;set type="6310" name="Test data transfer"&gt;
 *     &lt;F8000 name="Set type"&gt;6310&lt;/F8000&gt;
 *     &lt;F3101 name="Patient name"&gt;Samplesmith&lt;/F3101&gt;
 *     ...
 * </pre>
 *
 * The line and set lengths are not in the XML; they are calculated again when the XML is turned into GDT.
 * Fields that occur more than once (6228, 8410, ...) stay in the order of the message.
 */
public class GDTReader extends AbstractXMLReader {
    public static final String ROOT = "GDT";
    public static final String SET = "set";

    private final boolean strict;
    private final boolean fieldNames;

    public GDTReader(boolean strict, boolean fieldNames) {
        this.strict = strict;
        this.fieldNames = fieldNames;
    }

    @Override
    public void parse(InputSource input) throws SAXException, IOException {
        ensureHandlerSet();

        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(input.getCharacterStream());
        char[] buffer = new char[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            sb.append(buffer, 0, read);
        }

        List<FieldSet> sets;
        try {
            sets = GDTParser.parse(sb.toString(), strict);
        } catch (GDTParser.SyntaxException e) {
            throw new SAXException(e.getMessage(), e);
        }
        if (sets.isEmpty()) {
            throw new SAXException("Unable to parse, the message contains no GDT lines");
        }

        ContentHandler handler = getContentHandler();
        handler.startDocument();
        handler.startElement("", ROOT, "", getEmptyAttributes());

        for (FieldSet set : sets) {
            AttributesImpl attributes = getEmptyAttributes();
            String type = set.type();
            if (type != null) {
                attributes.addAttribute("", "type", "", "", clean(type.trim()));
                String name = fieldNames ? GDTFields.setTypeName(type) : null;
                if (name != null) {
                    attributes.addAttribute("", "name", "", "", name);
                }
            }
            handler.startElement("", SET, "", attributes);

            for (Field field : set.fields) {
                String elementName = "F" + field.id;
                AttributesImpl fieldAttributes = getEmptyAttributes();
                String name = fieldNames ? GDTFields.fieldName(field.id) : null;
                if (name != null) {
                    fieldAttributes.addAttribute("", "name", "", "", name);
                }
                handler.startElement("", elementName, "", fieldAttributes);
                String value = clean(field.value);
                if (!value.isEmpty()) {
                    handler.characters(value.toCharArray(), 0, value.length());
                }
                handler.endElement("", elementName, "");
            }

            handler.endElement("", SET, "");
        }

        handler.endElement("", ROOT, "");
        handler.endDocument();
    }

    /** Leaves out the characters that XML cannot hold (control characters other than a tab). */
    static String clean(String value) {
        StringBuilder sb = null;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = c == '\t' || c == '\n' || (c >= 0x20 && c != 0xFFFE && c != 0xFFFF);
            if (!ok && sb == null) {
                sb = new StringBuilder(value.length()).append(value, 0, i);
            } else if (ok && sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? value : sb.toString();
    }
}
