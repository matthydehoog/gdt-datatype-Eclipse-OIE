/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 *
 * Modelled on the EDI/X12 data type of Open Integration Engine (Mirth Connect),
 * Copyright (c) Mirth Corporation.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import com.mirth.connect.donkey.model.message.MessageSerializer;
import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.model.converters.XMLPrettyPrinter;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.model.util.DefaultMetaData;
import com.mirth.connect.plugins.datatypes.gdt.GDTParser.FieldSet;
import com.mirth.connect.util.ErrorMessageBuilder;

public class GDTSerializer implements IMessageSerializer {
    private Logger logger = LogManager.getLogger(this.getClass());

    private final GDTSerializationProperties serializationProperties;

    // Removes the whitespace between tags of pretty-printed XML, like the EDI/X12 data type does.
    private static Pattern prettyPattern1 = Pattern.compile("\\s*<([^/][^>]*)>");
    private static Pattern prettyPattern2 = Pattern.compile("<([^>]*/|/[^>]*)>\\s*");

    public GDTSerializer(SerializerProperties properties) {
        GDTSerializationProperties p = null;
        if (properties != null) {
            p = (GDTSerializationProperties) properties.getSerializationProperties();
        }
        serializationProperties = p != null ? p : new GDTSerializationProperties();
    }

    @Override
    public boolean isSerializationRequired(boolean toXml) {
        return false;
    }

    @Override
    public String transformWithoutSerializing(String message, MessageSerializer outboundSerializer) throws MessageSerializerException {
        return null;
    }

    @Override
    public String fromXML(String source) throws MessageSerializerException {
        XMLReader xr;
        try {
            xr = XMLReaderFactory.createXMLReader();
            xr.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (SAXException e) {
            throw new MessageSerializerException("Error converting XML to GDT", e, ErrorMessageBuilder.buildErrorMessage(this.getClass().getSimpleName(), "Error converting XML to GDT", e));
        }

        GDTXMLHandler handler = new GDTXMLHandler(serializationProperties);
        xr.setContentHandler(handler);
        xr.setErrorHandler(handler);

        try {
            xr.parse(new InputSource(new StringReader(prettyPattern2.matcher(prettyPattern1.matcher(source).replaceAll("<$1>")).replaceAll("<$1>"))));
        } catch (Exception e) {
            throw new MessageSerializerException("Error converting XML to GDT", e, ErrorMessageBuilder.buildErrorMessage(this.getClass().getSimpleName(), "Error converting XML to GDT", e));
        }

        return handler.getOutput();
    }

    @Override
    public String toXML(String source) throws MessageSerializerException {
        try {
            GDTReader reader = new GDTReader(serializationProperties);
            StringWriter stringWriter = new StringWriter();
            XMLPrettyPrinter serializer = new XMLPrettyPrinter(stringWriter);
            serializer.setEncodeEntities(true);
            reader.setContentHandler(serializer);
            reader.parse(new InputSource(new StringReader(source)));
            return stringWriter.toString();
        } catch (Exception e) {
            throw new MessageSerializerException("Error converting GDT to XML", e, ErrorMessageBuilder.buildErrorMessage(this.getClass().getSimpleName(), "Error converting GDT to XML", e));
        }
    }

    private static void put(Map<String, Object> map, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            map.put(key, value.trim());
        }
    }

    @Override
    public Map<String, Object> getMetaDataFromMessage(String message) {
        Map<String, Object> map = new HashMap<String, Object>();
        populateMetaData(message, map);
        return map;
    }

    /**
     * Connector map variables with fields of the first set, so that a filter, a router or a file name does not
     * have to parse the message: <code>$('gdtTest')</code>, <code>${gdtReceiver}${gdtSender}.gdt</code>. A field
     * that is missing or empty gives no variable.
     */
    static final String[][] MAP_VARIABLES = { { "gdtPatientNumber", "3000" }, { "gdtTest", "8402" }, { "gdtSetType", "8000" }, { "gdtSender", "8316" }, { "gdtReceiver", "8315" }, { "gdtTestDate", "6200" }, { "gdtTestTime", "6201" } };

    /**
     * Source is the sender (field 8316), type the set type (field 8000, for example 6310) and version the
     * GDT version (field 9218, for example 02.10). All three come from the first set.
     *
     * The engine passes the connector map here (FilterTransformerExecutor, before the filter runs), so the
     * variables of {@link #MAP_VARIABLES} go into the connector map as well.
     */
    @Override
    public void populateMetaData(String message, Map<String, Object> map) {
        try {
            List<FieldSet> sets = GDTParser.parse(message, false);
            if (sets.isEmpty()) {
                return;
            }
            FieldSet first = sets.get(0);

            put(map, DefaultMetaData.SOURCE_VARIABLE_MAPPING, first.value("8316"));
            put(map, DefaultMetaData.TYPE_VARIABLE_MAPPING, first.type());
            put(map, DefaultMetaData.VERSION_VARIABLE_MAPPING, first.value("9218"));
            for (String[] variable : MAP_VARIABLES) {
                put(map, variable[0], first.value(variable[1]));
            }
        } catch (Exception e) {
            logger.warn("Error populating GDT metadata: " + e.getMessage());
        }
    }

    @Override
    public String toJSON(String message) throws MessageSerializerException {
        return null;
    }

    @Override
    public String fromJSON(String message) throws MessageSerializerException {
        return null;
    }
}
