/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Splits a file with several GDT sets into one message per set. A set starts with a line for field 8000, so
 * the reader has to look one line ahead; that line waits here until the next call.
 */
public class GDTSetSplitter {
    private final BufferedReader reader;
    private String pending;

    public GDTSetSplitter(BufferedReader reader) {
        this.reader = reader;
    }

    /** @return the lines of the next set, each ended with CR LF, or null at the end of the input */
    public String next() throws IOException {
        StringBuilder set = new StringBuilder();
        String line = pending != null ? pending : reader.readLine();
        pending = null;

        while (line != null) {
            if (line.isEmpty() || line.equals("")) {
                // blank lines and an old end-of-file mark belong to no set
            } else if (startsSet(line) && set.length() > 0) {
                pending = line;
                break;
            } else {
                set.append(line).append("\r\n");
            }
            line = reader.readLine();
        }

        return set.length() == 0 ? null : set.toString();
    }

    static boolean startsSet(String line) {
        return line.length() >= 7 && line.regionMatches(3, GDTParser.FIELD_SET_TYPE, 0, 4);
    }
}
