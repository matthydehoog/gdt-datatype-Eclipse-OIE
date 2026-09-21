/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads GDT (Geraete-Daten-Traeger, the QMS interface for medical devices) into sets of fields.
 *
 * A GDT line is LLLFFFFcontent followed by CR LF: LLL is the length of the whole line in three digits
 * (the three digits, the four digit field number, the content and CR LF), FFFF is the field number. A set
 * starts with field 8000 (set type) and, by the specification, has field 8100 with the length of the whole set
 * in five digits. A file can hold several sets.
 */
public final class GDTParser {
    /** Length of a line without its content: 3 digits + 4 digit field number + CR LF. */
    public static final int LINE_OVERHEAD = 3 + 4 + 2;
    /** Longest line that fits in three digits. */
    public static final int MAX_LINE_LENGTH = 999;

    public static final String FIELD_SET_TYPE = "8000";
    public static final String FIELD_SET_LENGTH = "8100";

    private GDTParser() {}

    public static final class SyntaxException extends Exception {
        private static final long serialVersionUID = 1L;

        public SyntaxException(String message) {
            super(message);
        }
    }

    public static final class Field {
        public final String id;
        public final String value;

        public Field(String id, String value) {
            this.id = id;
            this.value = value;
        }
    }

    public static final class FieldSet {
        public final List<Field> fields = new ArrayList<Field>();

        /** Value of the first field with this number, or null. */
        public String value(String id) {
            for (Field field : fields) {
                if (field.id.equals(id)) {
                    return field.value;
                }
            }
            return null;
        }

        public String type() {
            return value(FIELD_SET_TYPE);
        }
    }

    /** Length of the line that holds this content: what the first three digits must say. */
    public static int lineLength(String content) {
        return LINE_OVERHEAD + content.length();
    }

    /**
     * @param strict reject a wrong line length, a wrong set length (8100) and content before the first
     *            8000. Without it these are ignored: many devices get them wrong.
     */
    public static List<FieldSet> parse(String text, boolean strict) throws SyntaxException {
        List<FieldSet> sets = new ArrayList<FieldSet>();
        if (text == null) {
            return sets;
        }
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }

        FieldSet current = null;
        int setLength = 0;
        int lineNumber = 0;
        int i = 0;
        int n = text.length();

        while (i < n) {
            int end = i;
            while (end < n && text.charAt(end) != '\r' && text.charAt(end) != '\n') {
                end++;
            }
            String line = text.substring(i, end);
            i = end;
            if (i < n) {
                // CR LF, LF and CR all end a line
                i += text.charAt(i) == '\r' && i + 1 < n && text.charAt(i + 1) == '\n' ? 2 : 1;
            }
            lineNumber++;

            // An old DOS end-of-file mark, or nothing at all
            if (line.isEmpty() || line.equals("")) {
                continue;
            }

            if (line.length() < 7 || !digits(line, 0, 7)) {
                throw new SyntaxException("Line " + lineNumber + " is not a GDT line (three digits for the length, four for the field number, then the content): " + preview(line));
            }
            String id = line.substring(3, 7);
            String value = line.substring(7);

            if (strict) {
                int declared = Integer.parseInt(line.substring(0, 3));
                if (declared != lineLength(value)) {
                    throw new SyntaxException("Line " + lineNumber + " (field " + id + "): the length says " + line.substring(0, 3) + " but the line is " + lineLength(value) + " long");
                }
            }

            if (id.equals(FIELD_SET_TYPE)) {
                if (strict) {
                    checkSetLength(current, setLength);
                }
                current = new FieldSet();
                sets.add(current);
                setLength = 0;
            } else if (current == null) {
                if (strict) {
                    throw new SyntaxException("Line " + lineNumber + " (field " + id + "): a set must start with field 8000");
                }
                current = new FieldSet();
                sets.add(current);
            }
            current.fields.add(new Field(id, value));
            setLength += lineLength(value);
        }

        if (strict) {
            checkSetLength(current, setLength);
        }
        return sets;
    }

    private static void checkSetLength(FieldSet set, int actual) throws SyntaxException {
        if (set == null) {
            return;
        }
        String declared = set.value(FIELD_SET_LENGTH);
        if (declared == null) {
            throw new SyntaxException("Set " + set.type() + " has no field 8100 (set length)");
        }
        if (!declared.trim().matches("\\d+") || Integer.parseInt(declared.trim()) != actual) {
            throw new SyntaxException("Set " + set.type() + ": field 8100 says " + declared + " but the set is " + actual + " long");
        }
    }

    private static boolean digits(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    private static String preview(String line) {
        return line.length() > 40 ? line.substring(0, 40) + "..." : line;
    }
}
