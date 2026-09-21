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
 *
 * Real devices do more than the specification allows, and this parser reads what they write unless it is asked
 * to be strict:
 * <ul>
 * <li>a wrong length is ignored;</li>
 * <li>a value with line breaks in it (a report text, say) is spread over several lines, and only the first one
 * has the length and the field number. The lines that follow do not look like GDT lines (they do not begin with
 * seven digits) and belong to the field before them. Such a value has a line break as \n in it;</li>
 * <li>a value longer than 990 characters has a four digit length (1342 instead of 134), which is read as the
 * length when the three digit reading would give a field number that does not exist and the four digit reading
 * one that does;</li>
 * <li>field 8100 can have more than five digits.</li>
 * </ul>
 */
public final class GDTParser {
    /** Length of a line without its content: 3 digits + 4 digit field number + CR LF. */
    public static final int LINE_OVERHEAD = 3 + 4 + 2;
    /** Longest line of the specification: three digits. */
    public static final int MAX_LINE_LENGTH = 999;
    /** Longest line that is written, with a four digit length. */
    public static final int MAX_LONG_LINE_LENGTH = 9999;

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

    /** Number of characters a value takes on a line: a line break inside it (\n) counts as CR LF. */
    public static int contentLength(String value) {
        int length = value.length();
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') {
                length++;
            }
        }
        return length;
    }

    /** Length of the line that holds this value: what the digits in front of it must say. */
    public static int lineLength(String value) {
        return LINE_OVERHEAD + contentLength(value);
    }

    /**
     * What the line really takes, and so what its length digits and the set length (8100) must say: the length
     * of the specification, plus one when it needs a fourth digit.
     */
    public static int physicalLength(String value) {
        int length = lineLength(value);
        return length > MAX_LINE_LENGTH ? length + 1 : length;
    }

    /**
     * @param strict reject what the specification does not allow: a wrong line length or set length (8100), a
     *            line that is not a GDT line, content before the first 8000. Without it these are read as well
     *            as possible, because many devices get them wrong.
     */
    public static List<FieldSet> parse(String text, boolean strict) throws SyntaxException {
        List<FieldSet> sets = new ArrayList<FieldSet>();
        if (text == null) {
            return sets;
        }
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        List<String> lines = split(text);

        FieldSet current = null;
        int setLength = 0;

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int lineNumber = index + 1;

            // An old DOS end-of-file mark
            if (line.equals("")) {
                continue;
            }

            if (line.isEmpty()) {
                // A blank line is part of a value when text follows that is not a new field; otherwise it is nothing
                int next = index;
                while (next < lines.size() && lines.get(next).isEmpty()) {
                    next++;
                }
                if (!strict && current != null && next < lines.size() && !looksLikeField(lines.get(next)) && !lines.get(next).equals("")) {
                    appendToLast(current, "");
                }
                continue;
            }

            if (!looksLikeField(line)) {
                if (strict || current == null) {
                    throw new SyntaxException("Line " + lineNumber + " is not a GDT line (three digits for the length, four for the field number, then the content): " + preview(line));
                }
                appendToLast(current, line);
                continue;
            }

            String id = line.substring(3, 7);
            String value = line.substring(7);

            if (!strict && isLongLine(line, id, value)) {
                id = line.substring(4, 8);
                value = line.substring(8);
            }

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

    /** The lines of the text; CR LF, LF and CR all end a line. */
    private static List<String> split(String text) {
        List<String> lines = new ArrayList<String>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int end = i;
            while (end < n && text.charAt(end) != '\r' && text.charAt(end) != '\n') {
                end++;
            }
            lines.add(text.substring(i, end));
            i = end;
            if (i < n) {
                i += text.charAt(i) == '\r' && i + 1 < n && text.charAt(i + 1) == '\n' ? 2 : 1;
            }
        }
        return lines;
    }

    static boolean looksLikeField(String line) {
        return line.length() >= 7 && digits(line, 0, 7);
    }

    /**
     * A line with a four digit length: 1342 8420 text instead of 134 2842 0text. It is only read that way when
     * the length is at least 1000, the line cannot be right with three digits, and the four digit reading gives
     * a field of the specification where the three digit reading gives one that is not.
     */
    private static boolean isLongLine(String line, String id3, String value3) {
        if (line.length() < 8 || !digits(line, 0, 8) || Integer.parseInt(line.substring(0, 4)) < 1000) {
            return false;
        }
        if (Integer.parseInt(line.substring(0, 3)) == lineLength(value3)) {
            return false;
        }
        return GDTFields.fieldName(id3) == null && GDTFields.fieldName(line.substring(4, 8)) != null;
    }

    private static void appendToLast(FieldSet set, String line) {
        int last = set.fields.size() - 1;
        Field field = set.fields.get(last);
        set.fields.set(last, new Field(field.id, field.value + "\n" + line));
    }

    private static void checkSetLength(FieldSet set, int actual) throws SyntaxException {
        if (set == null) {
            return;
        }
        String declared = set.value(FIELD_SET_LENGTH);
        if (declared == null) {
            throw new SyntaxException("Set " + set.type() + " has no field 8100 (set length)");
        }
        if (!declared.trim().matches("\\d+") || Long.parseLong(declared.trim()) != actual) {
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
