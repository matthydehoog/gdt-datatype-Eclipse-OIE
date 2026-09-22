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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 *
 * When {@code groupTests} is on, a field 8410 (Test ID) and the 8411 (Test name), 8420 (Result value) and
 * 8421 (Unit) that follow it are nested in one <code>test</code> element instead of being siblings of the
 * set, since a GDT message can hold many of these one after another (one per measured value):
 *
 * <pre>
 * &lt;test&gt;
 *   &lt;F8410 name="Test ID"&gt;SYSMXTG&lt;/F8410&gt;
 *   &lt;F8411 name="Test name"&gt;Systole max day phase&lt;/F8411&gt;
 *   &lt;F8420 name="Result value"&gt;142&lt;/F8420&gt;
 *   &lt;F8421 name="Unit"&gt;mmHg&lt;/F8421&gt;
 * &lt;/test&gt;
 * </pre>
 *
 * A new 8410, or any field that is not one of these four, ends the group. This does not change the GDT
 * message: converting the XML back flattens <code>test</code> into the same fields in the same order.
 *
 * When {@code groupCategories} is on, an open category (a field 6330, 6332, ..., 6398 with the category's
 * name, followed right away by 6331, 6333, ..., 6399 with its content) becomes a <code>category</code>
 * element, its name an attribute and its content the text, and a run of them is wrapped in one
 * <code>categories</code> element:
 *
 * <pre>
 * &lt;categories&gt;
 *   &lt;category name="OrderID"&gt;356218126&lt;/category&gt;
 * &lt;/categories&gt;
 * </pre>
 *
 * A name field without the content field right behind it becomes an empty category. Converting the XML
 * back turns <code>categories</code> into fields numbered from 6330 again; which field had which number
 * in the original message is not kept, since the numbers do not mean anything by themselves.
 */
public class GDTReader extends AbstractXMLReader {
    public static final String ROOT = "GDT";
    public static final String SET = "set";
    public static final String GROUP = "test";
    public static final String CATEGORIES = "categories";
    public static final String CATEGORY = "category";

    private static final String GROUP_START_FIELD = "8410";
    private static final Set<String> GROUP_FIELDS = new HashSet<String>(Arrays.asList("8411", "8420", "8421"));

    /** The category name fields are the even numbers from 6330 to 6398; the content field follows right after. */
    static final int CATEGORY_FIRST_ID = 6330;
    static final int CATEGORY_LAST_ID = 6398;

    private final boolean strict;
    private final boolean fieldNames;
    private final boolean groupTests;
    private final boolean groupCategories;

    public GDTReader(boolean strict, boolean fieldNames) {
        this(strict, fieldNames, false, false);
    }

    public GDTReader(boolean strict, boolean fieldNames, boolean groupTests) {
        this(strict, fieldNames, groupTests, false);
    }

    public GDTReader(boolean strict, boolean fieldNames, boolean groupTests, boolean groupCategories) {
        this.strict = strict;
        this.fieldNames = fieldNames;
        this.groupTests = groupTests;
        this.groupCategories = groupCategories;
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

            boolean inGroup = false;
            boolean inCategories = false;
            String awaitingCategoryContentId = null;

            for (Field field : set.fields) {
                String id = field.id;

                boolean isCategoryContent = groupCategories && id.equals(awaitingCategoryContentId);
                if (awaitingCategoryContentId != null && !isCategoryContent) {
                    handler.endElement("", CATEGORY, "");
                    awaitingCategoryContentId = null;
                }
                boolean isCategoryName = groupCategories && isCategoryNameField(id);
                if (inCategories && !isCategoryContent && !isCategoryName) {
                    handler.endElement("", CATEGORIES, "");
                    inCategories = false;
                }

                if (groupTests) {
                    boolean startsGroup = id.equals(GROUP_START_FIELD);
                    boolean continuesGroup = inGroup && GROUP_FIELDS.contains(id);
                    if (inGroup && !startsGroup && !continuesGroup) {
                        handler.endElement("", GROUP, "");
                        inGroup = false;
                    }
                    if (startsGroup) {
                        if (inGroup) {
                            handler.endElement("", GROUP, "");
                        }
                        handler.startElement("", GROUP, "", getEmptyAttributes());
                        inGroup = true;
                    }
                }

                if (isCategoryName) {
                    if (!inCategories) {
                        handler.startElement("", CATEGORIES, "", getEmptyAttributes());
                        inCategories = true;
                    }
                    AttributesImpl categoryAttributes = getEmptyAttributes();
                    categoryAttributes.addAttribute("", "name", "", "", clean(field.value));
                    handler.startElement("", CATEGORY, "", categoryAttributes);
                    awaitingCategoryContentId = String.format("%04d", Integer.parseInt(id) + 1);
                } else if (isCategoryContent) {
                    String value = clean(field.value);
                    if (!value.isEmpty()) {
                        handler.characters(value.toCharArray(), 0, value.length());
                    }
                    handler.endElement("", CATEGORY, "");
                    awaitingCategoryContentId = null;
                } else {
                    writeField(handler, field);
                }
            }
            if (awaitingCategoryContentId != null) {
                handler.endElement("", CATEGORY, "");
            }
            if (inCategories) {
                handler.endElement("", CATEGORIES, "");
            }
            if (inGroup) {
                handler.endElement("", GROUP, "");
            }

            handler.endElement("", SET, "");
        }

        handler.endElement("", ROOT, "");
        handler.endDocument();
    }

    private void writeField(ContentHandler handler, Field field) throws SAXException {
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

    /** Whether a field number is a category name (6330, 6332, ..., 6398): its content follows right after. */
    static boolean isCategoryNameField(String id) {
        if (id.length() != 4) {
            return false;
        }
        int n;
        try {
            n = Integer.parseInt(id);
        } catch (NumberFormatException e) {
            return false;
        }
        return n >= CATEGORY_FIRST_ID && n <= CATEGORY_LAST_ID && n % 2 == 0;
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
