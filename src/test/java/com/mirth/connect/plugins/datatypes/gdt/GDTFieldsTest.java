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

    /** The fields that the field table of GDT 3.0 (QMS, 10/2013, section 4) adds to the one of GDT 2.1. */
    private static final String[] GDT30 = { "0201", "0202", "0212", "0950", "0957", "3112", "3113", "3114", "3116", "3119", "3618", "3619", "3626", "3649", "3650", "3651", "3652", "3654", "3656", "3658", "3660", "3662", "3664", "3666", "3668", "3670", "3672", "3673", "3674", "3675", "3676", "3677", "3700", "3701", "4104", "4106", "4109", "4110", "4111", "4112", "4113", "4121", "4122", "4200", "4202", "4203", "4204", "4205", "4207", "4208", "4209", "4217", "4218", "4219", "4220", "4221", "4229", "4231", "4237", "4241", "4242", "6001", "6003", "6004", "6006", "6008", "6206", "6210", "6211", "6214", "6218", "6329", "6406", "6407", "6408", "6409", "6431", "8200", "8201", "8202", "8310", "8314", "8403", "8404", "8405", "8407", "8412", "8413", "8425", "8433", "8501", "8504", "8510", "8511", "8512", "8601", "8602", "8606", "8607", "8608", "8609", "8610", "8615", "9103", "9152", "9153", "9154" };

    @Test
    public void everyFieldOfTheSpecificationHasAName() {
        for (String id : SPECIFICATION) {
            assertNotNull("field " + id, GDTFields.fieldName(id));
        }
        for (String id : GDT30) {
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
    public void aFieldOfBothVersionsKeepsItsGdt21Name() {
        assertEquals("Results text", GDTFields.fieldName("8480"));
        assertEquals("Reading time", GDTFields.fieldName("8439"));
        assertEquals("Time of collection", GDTFields.fieldName("8433"));
        assertEquals("Postal code of the patient", GDTFields.fieldName("3112"));
    }

    @Test
    public void onlyTheSpecificationIsNamed() {
        List<String> extra = new ArrayList<String>();
        List<String> known = new ArrayList<String>(Arrays.asList(SPECIFICATION));
        known.addAll(Arrays.asList("6202", "6203", "6204"));
        known.addAll(Arrays.asList(GDT30));
        for (int i = 0; i <= 9999; i++) {
            String id = String.format("%04d", i);
            boolean category = i >= 6330 && i <= 6399;
            if (GDTFields.fieldName(id) != null && !category && !known.contains(id)) {
                extra.add(id);
            }
        }
        assertEquals("named fields that are not in GDT 2.1 or 3.0", new ArrayList<String>(), extra);
        assertNull(GDTFields.fieldName("2842"));
    }

    /**
     * A line with a four digit length is only read as such when its three digit reading gives an unknown field
     * (GDTParser.isLongLine). So a field number added to the table can stop a long line of another field from
     * being read: 1003 6220 ... reads as length 100 and field 3622, which is known. These tests list every such
     * pair, so that adding a field shows what it changes.
     */
    @Test
    public void fourDigitLengthsThatTheTableMakesAmbiguousForGdt21Fields() {
        // 6220 to 6228 (3622 is known) and 3622 to 3628 (6362 is an open category) since the first version.
        // 3100 to 3108 since the GDT 3.0 fields (8310): harmless, these are names and addresses of at most 30
        // characters, which never get a four digit length.
        assertEquals(pairs(
                pairs('3', "6220", "6221", "6226", "6227", "6228"),
                pairs('6', "3622", "3623", "3628"),
                pairs('8', "3100", "3101", "3102", "3103", "3104", "3105", "3106", "3107", "3108")),
                ambiguous(Arrays.asList(SPECIFICATION)));
    }

    /**
     * The GDT 3.0 fields were not read with a four digit length at all before they were in the table (the four
     * digit reading has to give a known field). Now they are, apart from the final digit listed here: 36xx and
     * 37xx with 6 (636x and 637x are open categories), 41xx and 42xx with 8 (841x and 842x are results).
     */
    @Test
    public void fourDigitLengthsThatTheTableMakesAmbiguousForGdt30Fields() {
        List<String> with6 = new ArrayList<String>();
        List<String> with8 = new ArrayList<String>();
        for (String id : GDT30) {
            if (id.startsWith("36") || id.startsWith("37")) {
                with6.add(id);
            } else if (id.startsWith("41") || id.startsWith("420") || id.startsWith("421")) {
                with8.add(id);
            }
        }
        assertEquals(pairs(pairs('6', with6.toArray(new String[0])), pairs('8', with8.toArray(new String[0]))),
                ambiguous(Arrays.asList(GDT30)));
    }

    private static TreeSet<String> ambiguous(List<String> ids) {
        TreeSet<String> ambiguous = new TreeSet<String>();
        for (String id : ids) {
            for (char d = '0'; d <= '9'; d++) {
                if (GDTFields.fieldName(d + id.substring(0, 3)) != null) {
                    ambiguous.add(id + " with a length ending in " + d);
                }
            }
        }
        return ambiguous;
    }

    private static TreeSet<String> pairs(char d, String... ids) {
        TreeSet<String> pairs = new TreeSet<String>();
        for (String id : ids) {
            pairs.add(id + " with a length ending in " + d);
        }
        return pairs;
    }

    @SafeVarargs
    private static TreeSet<String> pairs(TreeSet<String>... sets) {
        TreeSet<String> all = new TreeSet<String>();
        for (TreeSet<String> s : sets) {
            all.addAll(s);
        }
        return all;
    }

    /** A long text in a GDT 3.0 field is read with its four digit length, as one known field. */
    @Test
    public void aLongGdt30TextIsReadWithItsFourDigitLength() throws Exception {
        StringBuilder text = new StringBuilder();
        while (text.length() < 1100) {
            text.append("Asthma since childhood, no further allergies known. ");
        }
        String value = text.substring(0, 1100);
        int length = 4 + 4 + value.length() + 2; // 1110: the three digit reading gives field 0366, which is not known
        String gdt = "01380006310\r\n014810000000\r\n" + length + "3662" + value + "\r\n";
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setCalculateSetLength(false);
        String xml = new GDTSerializer(new SerializerProperties(p, null, null)).toXML(gdt);
        NodeList anamnesis = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getElementsByTagName("F3662");
        assertEquals(1, anamnesis.getLength());
        assertEquals(value, anamnesis.item(0).getTextContent());
        assertEquals("Anamnesis", ((Element) anamnesis.item(0)).getAttribute("name"));
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
