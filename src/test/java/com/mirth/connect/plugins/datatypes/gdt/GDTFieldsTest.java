/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.mirth.connect.model.datatype.SerializerProperties;

public class GDTFieldsTest {

    /** Every field of the field table of GDT 2.1 (QMS, 5/2001, section 4), apart from the open categories. */
    private static final String[] SPECIFICATION = { "0102", "0103", "0132", "3000", "3100", "3101", "3102", "3103", "3104", "3105", "3106", "3107", "3108", "3110", "3622", "3623", "3628", "6200", "6201", "6205", "6220", "6221", "6226", "6227", "6228", "6302", "6303", "6304", "6305", "8000", "8100", "8315", "8316", "8402", "8410", "8411", "8418", "8420", "8421", "8428", "8429", "8430", "8431", "8432", "8437", "8438", "8439", "8460", "8461", "8462", "8470", "8480", "8990", "9206", "9218" };

    @Test
    public void everyFieldOfTheSpecificationHasAName() {
        for (String id : SPECIFICATION) {
            assertNotNull("field " + id, GDTFields.fieldName(id));
        }
        for (int id = 6330; id <= 6398; id += 2) {
            assertEquals("Name of open category", GDTFields.fieldName(String.valueOf(id)));
            assertEquals("Content of open category", GDTFields.fieldName(String.valueOf(id + 1)));
        }
    }

    @Test
    public void theFileFieldsOfTheSetTableHaveTheNamesOfTheFieldTable() {
        assertEquals(GDTFields.fieldName("6302"), GDTFields.fieldName("6202"));
        assertEquals(GDTFields.fieldName("6303"), GDTFields.fieldName("6203"));
        assertEquals(GDTFields.fieldName("6304"), GDTFields.fieldName("6204"));
        assertEquals("Current diagnosis", GDTFields.fieldName("6205"));
    }

    @Test
    public void onlyTheSpecificationIsNamed() {
        List<String> extra = new ArrayList<String>();
        List<String> known = new ArrayList<String>(Arrays.asList(SPECIFICATION));
        known.addAll(Arrays.asList("6202", "6203", "6204"));
        for (int i = 0; i <= 9999; i++) {
            String id = String.format("%04d", i);
            boolean category = i >= 6330 && i <= 6399;
            if (GDTFields.fieldName(id) != null && !category && !known.contains(id)) {
                extra.add(id);
            }
        }
        assertEquals("named fields that are not in GDT 2.1", new ArrayList<String>(), extra);
        assertNull(GDTFields.fieldName("2842"));
    }

    /**
     * A line with a four digit length is only read as such when its three digit reading gives an unknown field
     * (GDTParser.isLongLine). So a field number added to the table can stop a long line of another field from
     * being read: 1003 6220 ... reads as length 100 and field 3622, which is known. This lists every such pair,
     * so that adding a field shows what it changes.
     */
    @Test
    public void fourDigitLengthsThatTheTableMakesAmbiguous() {
        TreeSet<String> ambiguous = new TreeSet<String>();
        for (int i = 0; i <= 9999; i++) {
            String id = String.format("%04d", i);
            if (GDTFields.fieldName(id) == null) {
                continue;
            }
            for (char d = '0'; d <= '9'; d++) {
                if (GDTFields.fieldName(d + id.substring(0, 3)) != null) {
                    ambiguous.add(id + " with a length ending in " + d);
                }
            }
        }
        assertEquals(new TreeSet<String>(Arrays.asList(
                "3622 with a length ending in 6", "3623 with a length ending in 6", "3628 with a length ending in 6",
                "6220 with a length ending in 3", "6221 with a length ending in 3", "6226 with a length ending in 3",
                "6227 with a length ending in 3", "6228 with a length ending in 3")), ambiguous);
    }

    @Test
    public void everyFieldOfTheExampleHasAName() throws Exception {
        String gdt = new String(Files.readAllBytes(Paths.get("examples", "test-data-transfer.gdt")), StandardCharsets.ISO_8859_1);
        String xml = new GDTSerializer(new SerializerProperties(new GDTSerializationProperties(), null, null)).toXML(gdt);
        NodeList all = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("*");
        int fields = 0;
        for (int i = 0; i < all.getLength(); i++) {
            Element e = (Element) all.item(i);
            if (e.getTagName().matches("F[0-9]{4}")) {
                fields++;
                assertTrue(e.getTagName() + " has no name", e.hasAttribute("name"));
            }
        }
        assertTrue(fields > 20);
    }
}
