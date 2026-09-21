/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.util.HashMap;
import java.util.Map;

/** Names of the fields and set types of GDT 2.1 (QMS, 5/2001), for the XML attributes and the message tree. */
public final class GDTFields {
    private static final Map<String, String> FIELDS = new HashMap<String, String>();
    private static final Map<String, String> SET_TYPES = new HashMap<String, String>();

    static {
        String[][] fields = { { "0102", "Software responsible (firm)" }, { "0103", "Software" }, { "0132", "Release state of software" }, { "3000", "Patient number / patient label" }, { "3100", "Prefix / additional name of patient" }, { "3101", "Patient name" }, { "3102", "Patient first name" }, { "3103", "Patient birth date" }, { "3104", "Patient title" }, { "3105", "Patient insurance number" }, { "3106", "Patient residence" }, { "3107", "Patient street" }, { "3108", "Insurance status" }, { "3110", "Patient sex" }, { "3622", "Patient height (cm)" }, { "3623", "Patient weight (kg)" }, { "3628", "First language of patient" }, { "6200", "Date test data were saved" }, { "6201", "Time test data were saved" }, { "6205", "Current diagnosis" }, { "6220", "Results" }, { "6221", "Third party results" }, { "6226", "Number of following lines of 6228" }, { "6227", "Comments" }, { "6228", "Result table text, formatted" }, { "6302", "Attribute for (archive) file" }, { "6303", "File format" }, { "6304", "Information about the content of the file" }, { "6305", "Reference to the file" }, { "8000", "Set type" },
                { "8100", "Set length" }, { "8315", "Receiver GDT-ID" }, { "8316", "Sender GDT-ID" }, { "8402", "Device and method specific field" }, { "8410", "Test ID" }, { "8411", "Test name" }, { "8418", "Test status" }, { "8420", "Result value" }, { "8421", "Unit" }, { "8428", "Test material ID" }, { "8429", "Test material index" }, { "8430", "Test material name" }, { "8431", "Test material specification" }, { "8432", "Reading date" }, { "8437", "Unit(s) for data stream" }, { "8438", "Data stream" }, { "8439", "Reading time" }, { "8460", "Standard value text" }, { "8461", "Standard value, lower threshold" }, { "8462", "Standard value, upper threshold" }, { "8470", "Test notes" }, { "8480", "Results text" }, { "8990", "Signature" }, { "9206", "Character set used" }, { "9218", "GDT version" } };
        for (String[] f : fields) {
            FIELDS.put(f[0], f[1]);
        }

        // 6330, 6332, ..., 6398 name an open category and 6331, 6333, ..., 6399 hold its content
        for (int id = 6330; id <= 6398; id += 2) {
            FIELDS.put(String.valueOf(id), "Name of open category");
            FIELDS.put(String.valueOf(id + 1), "Content of open category");
        }

        SET_TYPES.put("6300", "Root data request");
        SET_TYPES.put("6301", "Root data transfer");
        SET_TYPES.put("6302", "New test request");
        SET_TYPES.put("6310", "Test data transfer");
        SET_TYPES.put("6311", "Test data display");
    }

    private GDTFields() {}

    /** Name of a field, or null when it is not in the specification. */
    public static String fieldName(String id) {
        return FIELDS.get(id);
    }

    /** Name of a set type (the content of field 8000), or null. */
    public static String setTypeName(String type) {
        return type == null ? null : SET_TYPES.get(type.trim());
    }
}
