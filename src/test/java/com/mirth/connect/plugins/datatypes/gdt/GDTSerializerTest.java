/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.mirth.connect.donkey.model.message.MessageSerializerException;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.model.util.DefaultMetaData;
import com.mirth.connect.plugins.datatypes.gdt.GDTSerializationProperties.LineEnding;

public class GDTSerializerTest {

    /** One GDT line with the right length in front. */
    private static String line(String id, String value) {
        return String.format("%03d", GDTParser.lineLength(value)) + id + value + "\r\n";
    }

    /** A complete set: the fields as "3101Name" (four digit field number, then the value), 8100 calculated behind 8000. */
    private static String set(String... fields) {
        StringBuilder body = new StringBuilder();
        int total = GDTParser.lineLength("00000");
        for (int i = 0; i < fields.length; i++) {
            String value = fields[i].substring(4);
            total += GDTParser.lineLength(value);
        }
        for (int i = 0; i < fields.length; i++) {
            body.append(line(fields[i].substring(0, 4), fields[i].substring(4)));
            if (i == 0) {
                body.append(line("8100", String.format("%05d", total)));
            }
        }
        return body.toString();
    }

    private static final String[] TEST_DATA_TRANSFER = { "80006310", "8315PRAX_EDP", "8316LZBD_SYS", "921802.00", "300002345", "3101Samplesmith", "3102John", "310301101945", "31101", "362278", "362379", "8402BDM01", "620023101998", "6220This is a two-line", "6220result of 24h-blood pressure test", "6227Comments to a long-term-blood pressure test", "8410SYSMXTG", "8411Systole max day phase", "8420142", "8421mmHg", "843223101998", "8439163400" };

    /** Several measured values in a row, each its own 8410/8411/8420 triple, as a sleep study device writes them. */
    private static final String[] MULTI_TEST_TRANSFER = { "80006310", "8410Height", "8411Height", "8420180,0", "8410Snore Index", "8411Snore Index", "842010,3" };

    private static GDTSerializer serializer(GDTSerializationProperties p) {
        return new GDTSerializer(new SerializerProperties(p, null, null));
    }

    private static GDTSerializer serializer() {
        return serializer(new GDTSerializationProperties());
    }

    private static Element xml(String xml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
        return doc.getDocumentElement();
    }

    private static Element child(Element parent, String name, int index) {
        NodeList list = parent.getElementsByTagName(name);
        return (Element) list.item(index);
    }

    private static void assertFails(GDTSerializer s, boolean toXml, String input, String messagePart) {
        try {
            String ignored = toXml ? s.toXML(input) : s.fromXML(input);
            fail("expected an error, got " + ignored);
        } catch (MessageSerializerException e) {
            String all = String.valueOf(e.getMessage()) + " " + String.valueOf(e.getCause() == null ? "" : e.getCause().getMessage());
            assertTrue(all, all.contains(messagePart));
        }
    }

    // ---- parser

    @Test
    public void parserReadsTheLines() throws Exception {
        List<GDTParser.FieldSet> sets = GDTParser.parse(set("80006301", "3101Schmidt", "3102Karl"), true);

        assertEquals(1, sets.size());
        assertEquals("6301", sets.get(0).type());
        assertEquals("Schmidt", sets.get(0).value("3101"));
        assertEquals(4, sets.get(0).fields.size());
    }

    @Test
    public void lineEndingsAreCrLfLfOrCr() throws Exception {
        for (String ending : new String[] { "\r\n", "\n", "\r" }) {
            List<GDTParser.FieldSet> sets = GDTParser.parse("01380006310" + ending + "0143101Jan" + ending, false);
            assertEquals(ending, 2, sets.get(0).fields.size());
            assertEquals("Jan", sets.get(0).value("3101"));
        }
    }

    @Test
    public void severalSetsInOneFile() throws Exception {
        List<GDTParser.FieldSet> sets = GDTParser.parse(set("80006310", "3101A") + set("80006310", "3101B"), true);

        assertEquals(2, sets.size());
        assertEquals("A", sets.get(0).value("3101"));
        assertEquals("B", sets.get(1).value("3101"));
    }

    @Test
    public void parserDoesNotTrimTheContent() throws Exception {
        assertEquals("  a  ", GDTParser.parse(line("8000", "6310") + line("6228", "  a  "), false).get(0).value("6228"));
    }

    @Test
    public void aLineThatIsNotGdtIsAnError() {
        try {
            GDTParser.parse("hello world\r\n", false);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("Line 1 is not a GDT line"));
        }
    }

    @Test
    public void wrongLengthsAreIgnoredUnlessStrict() throws Exception {
        // The length says 999, the set length is wrong too: the sample files of the specification are like this
        String text = "99980006310\r\n0148100" + "00001\r\n0143101Jan\r\n";

        assertEquals("Jan", GDTParser.parse(text, false).get(0).value("3101"));
        try {
            GDTParser.parse(text, true);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("the length says 999"));
        }
    }

    @Test
    public void strictChecksTheSetLength() throws Exception {
        String good = set("80006310", "3101Jan");
        assertEquals(1, GDTParser.parse(good, true).size());

        String bad = good.replaceFirst("8100[0-9]{5}", "810000099");
        assertFalse(good.equals(bad));
        try {
            GDTParser.parse(bad, true);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("field 8100 says"));
        }
    }

    @Test
    public void strictNeedsField8100AndA8000First() throws Exception {
        try {
            GDTParser.parse(line("8000", "6310") + line("3101", "Jan"), true);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("has no field 8100"));
        }
        try {
            GDTParser.parse(line("3101", "Jan"), true);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("must start with field 8000"));
        }
        // not strict: a set is made anyway
        assertEquals(1, GDTParser.parse(line("3101", "Jan"), false).size());
    }

    @Test
    public void blankLinesAndAnEndOfFileMarkAreSkipped() throws Exception {
        List<GDTParser.FieldSet> sets = GDTParser.parse("\r\n" + line("8000", "6310") + "\r\n" + line("3101", "Jan") + "", false);

        assertEquals(1, sets.size());
        assertEquals(2, sets.get(0).fields.size());
    }

    // ---- GDT to XML

    @Test
    public void xmlHasSetsAndFields() throws Exception {
        String result = serializer().toXML(set(TEST_DATA_TRANSFER));
        Element root = xml(result);

        assertEquals("GDT", root.getTagName());
        Element s = child(root, "set", 0);
        assertEquals("6310", s.getAttribute("type"));
        assertEquals("Test data transfer", s.getAttribute("name"));
        assertEquals("6310", child(s, "F8000", 0).getTextContent());
        assertEquals("Patient name", child(s, "F3101", 0).getAttribute("name"));
        assertEquals("Samplesmith", child(s, "F3101", 0).getTextContent());
        assertEquals("BDM01", child(s, "F8402", 0).getTextContent());
    }

    @Test
    public void repeatedFieldsStayInOrder() throws Exception {
        Element root = xml(serializer().toXML(set(TEST_DATA_TRANSFER)));

        NodeList results = root.getElementsByTagName("F6220");
        assertEquals(2, results.getLength());
        assertEquals("This is a two-line", results.item(0).getTextContent());
        assertEquals("result of 24h-blood pressure test", results.item(1).getTextContent());
    }

    // ---- grouping the 8410/8411/8420/8421 fields of one result, under one results element

    private static GDTSerializationProperties groupResultsProperties() {
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setGroupResults(true);
        return p;
    }

    @Test
    public void groupResultsIsOffByDefault() throws Exception {
        Element root = xml(serializer().toXML(set(TEST_DATA_TRANSFER)));

        assertEquals(0, root.getElementsByTagName("results").getLength());
        assertEquals(0, root.getElementsByTagName("result").getLength());
        assertEquals("set", child(root, "F8410", 0).getParentNode().getNodeName());
    }

    @Test
    public void groupResultsNestsTheFieldsOfOneResult() throws Exception {
        Element root = xml(serializer(groupResultsProperties()).toXML(set(TEST_DATA_TRANSFER)));

        NodeList resultsList = root.getElementsByTagName("results");
        assertEquals(1, resultsList.getLength());
        Element results = (Element) resultsList.item(0);
        NodeList resultList = results.getElementsByTagName("result");
        assertEquals(1, resultList.getLength());
        Element result = (Element) resultList.item(0);
        assertEquals("SYSMXTG", child(result, "F8410", 0).getTextContent());
        assertEquals("Systole max day phase", child(result, "F8411", 0).getTextContent());
        assertEquals("142", child(result, "F8420", 0).getTextContent());
        assertEquals("mmHg", child(result, "F8421", 0).getTextContent());
        // 8432 and 8439 are not part of the result: they end the results run
        assertEquals("set", child(root, "F8432", 0).getParentNode().getNodeName());
        assertEquals("set", child(root, "F8439", 0).getParentNode().getNodeName());
    }

    @Test
    public void groupResultsWrapsARunOfResultsInOneElement() throws Exception {
        Element root = xml(serializer(groupResultsProperties()).toXML(set(MULTI_TEST_TRANSFER)));

        assertEquals(1, root.getElementsByTagName("results").getLength());
        NodeList resultList = root.getElementsByTagName("result");
        assertEquals(2, resultList.getLength());
        Element height = (Element) resultList.item(0);
        assertEquals("Height", child(height, "F8410", 0).getTextContent());
        assertEquals("Height", child(height, "F8411", 0).getTextContent());
        assertEquals("180,0", child(height, "F8420", 0).getTextContent());
        Element snoreIndex = (Element) resultList.item(1);
        assertEquals("Snore Index", child(snoreIndex, "F8410", 0).getTextContent());
        assertEquals("Snore Index", child(snoreIndex, "F8411", 0).getTextContent());
        assertEquals("10,3", child(snoreIndex, "F8420", 0).getTextContent());
    }

    @Test
    public void groupResultsClosesTheRunOnAnUnrelatedFieldAndStartsAnewAfterIt() throws Exception {
        String[] fields = { "80006310", "8410Height", "8411Height", "8420180,0", "3101Samplesmith", "8410Weight", "8411Weight", "842078,0" };
        Element root = xml(serializer(groupResultsProperties()).toXML(set(fields)));

        NodeList resultsList = root.getElementsByTagName("results");
        assertEquals(2, resultsList.getLength());
        assertEquals(1, ((Element) resultsList.item(0)).getElementsByTagName("result").getLength());
        assertEquals(1, ((Element) resultsList.item(1)).getElementsByTagName("result").getLength());
        assertEquals("set", child(root, "F3101", 0).getParentNode().getNodeName());
    }

    @Test
    public void groupResultsHandlesARealDeviceSet() throws Exception {
        Element root = xml(serializer(groupResultsProperties()).toXML(deviceSet()));

        assertEquals(1, root.getElementsByTagName("results").getLength());
        NodeList resultList = root.getElementsByTagName("result");
        assertEquals(2, resultList.getLength());
        Element analysisCriteria = (Element) resultList.item(0);
        assertEquals("Analysis Criteria", child(analysisCriteria, "F8410", 0).getTextContent());
        assertEquals(10, child(analysisCriteria, "F8420", 0).getTextContent().split("\n").length);
        assertEquals(0, analysisCriteria.getElementsByTagName("F8411").getLength());
        Element excludedTime = (Element) resultList.item(1);
        assertEquals("Excluded Time (m)", child(excludedTime, "F8410", 0).getTextContent());
        assertEquals("0,0", child(excludedTime, "F8420", 0).getTextContent());
        assertEquals("m", child(excludedTime, "F8421", 0).getTextContent());
        // 8316, before the first result, is not part of any group
        assertEquals("set", child(root, "F8316", 0).getParentNode().getNodeName());
    }

    @Test
    public void groupResultsRoundTripGivesTheSameMessage() throws Exception {
        GDTSerializer s = serializer(groupResultsProperties());

        assertEquals(set(TEST_DATA_TRANSFER), s.fromXML(s.toXML(set(TEST_DATA_TRANSFER))));
        assertEquals(set(MULTI_TEST_TRANSFER), s.fromXML(s.toXML(set(MULTI_TEST_TRANSFER))));

        // deviceSet() declares a wrong set length (8100) on purpose, which gets recalculated on the way
        // back (see aDeviceSetSurvivesTheTripThroughXml), so compare the fields rather than the raw text
        GDTParser.FieldSet back = GDTParser.parse(s.fromXML(s.toXML(deviceSet())), false).get(0);
        GDTParser.FieldSet original = GDTParser.parse(deviceSet(), false).get(0);
        assertEquals(original.fields.size(), back.fields.size());
        assertEquals(original.value("8316"), back.value("8316"));
        assertEquals(original.value("8420"), back.value("8420"));
        assertEquals("Excluded Time (m)", back.fields.get(5).value);
        assertEquals("m", back.fields.get(7).value);
    }

    @Test
    public void aTransformerCanNestFieldsInAResultsGroup() throws Exception {
        String gdt = serializer().fromXML("<GDT><set><F8000>6310</F8000><results><result><F8410>SNORE</F8410><F8411>Snore Index</F8411><F8420>10,3</F8420></result><result><F8410>Height</F8410><F8420>180,0</F8420></result></results><F8432>23101998</F8432></set></GDT>");

        List<GDTParser.Field> fields = GDTParser.parse(gdt, true).get(0).fields;
        assertEquals("8410", fields.get(2).id);
        assertEquals("SNORE", fields.get(2).value);
        assertEquals("8411", fields.get(3).id);
        assertEquals("Snore Index", fields.get(3).value);
        assertEquals("8420", fields.get(4).id);
        assertEquals("10,3", fields.get(4).value);
        assertEquals("8410", fields.get(5).id);
        assertEquals("Height", fields.get(5).value);
        assertEquals("8420", fields.get(6).id);
        assertEquals("180,0", fields.get(6).value);
        assertEquals("8432", fields.get(7).id);
    }

    @Test
    public void unexpectedElementInAResultsGroupIsRefused() {
        assertFails(serializer(), false, "<GDT><set><results><PID>1</PID></results></set></GDT>", "in results");
    }

    @Test
    public void unexpectedElementInAResultIsRefused() {
        assertFails(serializer(), false, "<GDT><set><results><result><PID>1</PID></result></results></set></GDT>", "in result");
    }

    @Test
    public void vocabularyDescribesResultsAndResult() {
        GDTVocabulary v = new GDTVocabulary("2.1", "6310");
        assertEquals("Results", v.getDescription("results"));
        assertEquals("Result", v.getDescription("result"));
    }

    // ---- grouping open categories (6330/6331, ...), for example OrderID

    private static final String[] ORDER_ID_TRANSFER = { "80006310", "6330OrderID", "6331356218126" };
    private static final String[] MULTI_CATEGORY_TRANSFER = { "80006310", "6330OrderID", "6331356218126", "6332Ward", "6333Cardiology" };

    private static GDTSerializationProperties groupCategoriesProperties() {
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setGroupCategories(true);
        return p;
    }

    @Test
    public void groupCategoriesIsOffByDefault() throws Exception {
        Element root = xml(serializer().toXML(set(ORDER_ID_TRANSFER)));

        assertEquals(0, root.getElementsByTagName("categories").getLength());
        assertEquals("OrderID", child(root, "F6330", 0).getTextContent());
        assertEquals("356218126", child(root, "F6331", 0).getTextContent());
    }

    @Test
    public void groupCategoriesWrapsOneOpenCategory() throws Exception {
        Element root = xml(serializer(groupCategoriesProperties()).toXML(set(ORDER_ID_TRANSFER)));

        NodeList categoriesList = root.getElementsByTagName("categories");
        assertEquals(1, categoriesList.getLength());
        Element categories = (Element) categoriesList.item(0);
        NodeList categoryList = categories.getElementsByTagName("category");
        assertEquals(1, categoryList.getLength());
        Element category = (Element) categoryList.item(0);
        assertEquals("OrderID", category.getAttribute("name"));
        assertEquals("356218126", category.getTextContent());
        // no F6330/F6331 fields are left
        assertEquals(0, root.getElementsByTagName("F6330").getLength());
        assertEquals(0, root.getElementsByTagName("F6331").getLength());
    }

    @Test
    public void groupCategoriesWrapsARunOfCategoriesInOneElement() throws Exception {
        Element root = xml(serializer(groupCategoriesProperties()).toXML(set(MULTI_CATEGORY_TRANSFER)));

        assertEquals(1, root.getElementsByTagName("categories").getLength());
        NodeList categoryList = root.getElementsByTagName("category");
        assertEquals(2, categoryList.getLength());
        assertEquals("OrderID", ((Element) categoryList.item(0)).getAttribute("name"));
        assertEquals("356218126", categoryList.item(0).getTextContent());
        assertEquals("Ward", ((Element) categoryList.item(1)).getAttribute("name"));
        assertEquals("Cardiology", categoryList.item(1).getTextContent());
    }

    @Test
    public void groupCategoriesClosesOnAnUnrelatedFieldAndStartsAnewAfterIt() throws Exception {
        String[] fields = { "80006310", "6330OrderID", "6331356218126", "3101Samplesmith", "6332Ward", "6333Cardiology" };
        Element root = xml(serializer(groupCategoriesProperties()).toXML(set(fields)));

        NodeList categoriesList = root.getElementsByTagName("categories");
        assertEquals(2, categoriesList.getLength());
        assertEquals("set", child(root, "F3101", 0).getParentNode().getNodeName());
    }

    @Test
    public void groupCategoriesAcceptsANameWithoutItsContentField() throws Exception {
        String[] fields = { "80006310", "6330OrderID", "3101Samplesmith" };
        Element root = xml(serializer(groupCategoriesProperties()).toXML(set(fields)));

        Element category = child(root, "category", 0);
        assertEquals("OrderID", category.getAttribute("name"));
        assertEquals("", category.getTextContent());
        assertEquals("set", child(root, "F3101", 0).getParentNode().getNodeName());
    }

    @Test
    public void groupCategoriesRoundTripKeepsTheNamesAndContent() throws Exception {
        GDTSerializer s = serializer(groupCategoriesProperties());

        GDTParser.FieldSet back = GDTParser.parse(s.fromXML(s.toXML(set(MULTI_CATEGORY_TRANSFER))), true).get(0);
        assertEquals("OrderID", back.value("6330"));
        assertEquals("356218126", back.value("6331"));
        assertEquals("Ward", back.value("6332"));
        assertEquals("Cardiology", back.value("6333"));
    }

    @Test
    public void aTransformerCanNestFieldsInACategoriesGroup() throws Exception {
        String gdt = serializer().fromXML("<GDT><set><F8000>6310</F8000><categories><category name=\"OrderID\">356218126</category></categories><F3101>Samplesmith</F3101></set></GDT>");

        List<GDTParser.Field> fields = GDTParser.parse(gdt, true).get(0).fields;
        assertEquals("6330", fields.get(2).id);
        assertEquals("OrderID", fields.get(2).value);
        assertEquals("6331", fields.get(3).id);
        assertEquals("356218126", fields.get(3).value);
        assertEquals("3101", fields.get(4).id);
    }

    @Test
    public void tooManyCategoriesAreRefused() {
        StringBuilder xml = new StringBuilder("<GDT><set><F8000>6310</F8000><categories>");
        for (int i = 0; i < 36; i++) {
            xml.append("<category name=\"c").append(i).append("\">v</category>");
        }
        xml.append("</categories></set></GDT>");

        assertFails(serializer(), false, xml.toString(), "Too many categories");
    }

    @Test
    public void categoryWithoutANameAttributeIsRefused() {
        assertFails(serializer(), false, "<GDT><set><categories><category>x</category></categories></set></GDT>", "needs a name attribute");
    }

    @Test
    public void unexpectedElementInACategoriesGroupIsRefused() {
        assertFails(serializer(), false, "<GDT><set><categories><PID>1</PID></categories></set></GDT>", "in categories");
    }

    @Test
    public void vocabularyDescribesCategoriesAndCategory() {
        GDTVocabulary v = new GDTVocabulary("2.1", "6310");
        assertEquals("Categories", v.getDescription("categories"));
        assertEquals("Category", v.getDescription("category"));
    }

    // ---- joining a field 8480 (Results text) split over several lines

    /** As a device writes a long results text: split over two 8480 fields instead of using a four digit length. */
    private static final String[] SPLIT_RESULTS_TEXT = { "80006310", "8480Neusflow signaal matig, analyse uitgevoerd op banden RIP sig", "8480naal." };

    private static GDTSerializationProperties joinResultsTextProperties() {
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setJoinResultsText(true);
        return p;
    }

    @Test
    public void joinResultsTextIsOffByDefault() throws Exception {
        Element root = xml(serializer().toXML(set(SPLIT_RESULTS_TEXT)));

        assertEquals(2, root.getElementsByTagName("F8480").getLength());
    }

    @Test
    public void joinResultsTextJoinsConsecutiveFields() throws Exception {
        Element root = xml(serializer(joinResultsTextProperties()).toXML(set(SPLIT_RESULTS_TEXT)));

        NodeList fields = root.getElementsByTagName("F8480");
        assertEquals(1, fields.getLength());
        assertEquals("Neusflow signaal matig, analyse uitgevoerd op banden RIP signaal.", fields.item(0).getTextContent());
    }

    @Test
    public void joinResultsTextDoesNotJoinAcrossAnotherField() throws Exception {
        String[] fields = { "80006310", "8480First part", "3101Samplesmith", "8480Second part" };
        Element root = xml(serializer(joinResultsTextProperties()).toXML(set(fields)));

        NodeList text = root.getElementsByTagName("F8480");
        assertEquals(2, text.getLength());
        assertEquals("First part", text.item(0).getTextContent());
        assertEquals("Second part", text.item(1).getTextContent());
    }

    @Test
    public void joinResultsTextRoundTripGivesTheSameValue() throws Exception {
        GDTSerializer s = serializer(joinResultsTextProperties());

        GDTParser.FieldSet back = GDTParser.parse(s.fromXML(s.toXML(set(SPLIT_RESULTS_TEXT))), true).get(0);
        assertEquals("Neusflow signaal matig, analyse uitgevoerd op banden RIP signaal.", back.value("8480"));
    }

    @Test
    public void severalSetsBecomeSeveralElements() throws Exception {
        Element root = xml(serializer().toXML(set("80006310", "3101A") + set("80006301", "3101B")));

        assertEquals(2, root.getElementsByTagName("set").getLength());
        assertEquals("6301", child(root, "set", 1).getAttribute("type"));
    }

    @Test
    public void fieldNamesCanBeSwitchedOff() throws Exception {
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setFieldNames(false);
        String result = serializer(p).toXML(set("80006310", "3101Jan"));

        assertFalse(result, result.contains("name="));
        assertEquals("Jan", child(xml(result), "F3101", 0).getTextContent());
    }

    @Test
    public void unknownFieldsHaveNoName() throws Exception {
        Element field = child(xml(serializer().toXML(set("80006310", "9999x"))), "F9999", 0);

        assertEquals("x", field.getTextContent());
        assertFalse(field.hasAttribute("name"));
    }

    @Test
    public void specialCharactersAreEscaped() throws Exception {
        Element root = xml(serializer().toXML(set("80006310", "6227a < b & \"c\" > d")));

        assertEquals("a < b & \"c\" > d", child(root, "F6227", 0).getTextContent());
    }

    @Test
    public void controlCharactersAreLeftOut() throws Exception {
        Element root = xml(serializer().toXML(line("8000", "6310") + line("6227", "abc")));

        assertEquals("abc", child(root, "F6227", 0).getTextContent());
    }

    @Test
    public void emptyOrGarbageInputIsAnError() {
        assertFails(serializer(), true, "", "no GDT lines");
        assertFails(serializer(), true, "this is not gdt", "not a GDT line");
    }

    // ---- XML to GDT

    @Test
    public void roundTripGivesTheSameMessage() throws Exception {
        String gdt = set(TEST_DATA_TRANSFER);

        assertEquals(gdt, serializer().fromXML(serializer().toXML(gdt)));
    }

    @Test
    public void roundTripKeepsSpacesAndUmlauts() throws Exception {
        String gdt = set("80006310", "6228    day phase    night phase  ", "6228Ps[mmHg]     143", "6227Müller & Söhne é");

        assertEquals(gdt, serializer().fromXML(serializer().toXML(gdt)));
    }

    @Test
    public void roundTripOfSeveralSets() throws Exception {
        String gdt = set("80006310", "3101A", "6228x") + set("80006301", "3101B");

        assertEquals(gdt, serializer().fromXML(serializer().toXML(gdt)));
    }

    @Test
    public void wrongLengthsAreCorrectedOnTheWayBack() throws Exception {
        // A set as the specification prints it: the line length of the name is one too low, 8100 is a guess
        String sample = "01380006301\r\n014810000173\r\n0178315EKG_TYP1\r\n0193101Samplesmith\r\n0143102John\r\n";

        String back = serializer().fromXML(serializer().toXML(sample));

        assertEquals(GDTParser.parse(back, true).size(), 1);
        assertTrue(back, back.contains("0203101Samplesmith\r\n"));
        assertEquals("Samplesmith", GDTParser.parse(back, true).get(0).value("3101"));
    }

    @Test
    public void lengthsInTheXmlAreIgnored() throws Exception {
        String xmlText = "<GDT><set><F8000>6310</F8000><F8100>99999</F8100><F3101>Jan</F3101></set></GDT>";
        String gdt = serializer().fromXML(xmlText);

        assertEquals(1, GDTParser.parse(gdt, true).size());
        assertEquals(set("80006310", "3101Jan"), gdt);
    }

    @Test
    public void field8100IsAddedWhenMissing() throws Exception {
        String gdt = serializer().fromXML("<GDT><set><F8000>6310</F8000><F3101>Jan</F3101></set></GDT>");

        assertEquals(set("80006310", "3101Jan"), gdt);
    }

    @Test
    public void field8100IsLeftAloneWhenCalculationIsOff() throws Exception {
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setCalculateSetLength(false);
        String gdt = serializer(p).fromXML("<GDT><set><F8000>6310</F8000><F8100>12345</F8100><F3101>Jan</F3101></set></GDT>");

        assertEquals(line("8000", "6310") + line("8100", "12345") + line("3101", "Jan"), gdt);
        // and nothing is added
        assertEquals(line("8000", "6310") + line("3101", "Jan"), serializer(p).fromXML("<GDT><set><F8000>6310</F8000><F3101>Jan</F3101></set></GDT>"));
    }

    @Test
    public void lineEndingCanBeChanged() throws Exception {
        GDTSerializationProperties p = new GDTSerializationProperties();
        p.setLineEnding(LineEnding.LF);
        String gdt = serializer(p).fromXML("<GDT><set><F8000>6310</F8000><F3101>Jan</F3101></set></GDT>");

        assertFalse(gdt.contains("\r"));
        assertEquals(3, gdt.split("\n").length);
        // the lengths still count CR LF, as the specification says
        assertTrue(gdt, gdt.startsWith("01380006310\n"));
    }

    @Test
    public void aTransformerCanBuildAMessageFromScratch() throws Exception {
        String gdt = serializer().fromXML("<GDT><set><F8000>6302</F8000><F8315>EKG_TYP1</F8315><F8316>PRAX_EDP</F8316><F9218>02.10</F9218><F3000>2345</F3000><F3101>Schmidt</F3101><F3102>Karl</F3102><F8402>EKG01</F8402></set></GDT>");

        GDTParser.FieldSet set = GDTParser.parse(gdt, true).get(0);
        assertEquals("6302", set.type());
        assertEquals("EKG01", set.value("8402"));
    }

    @Test
    public void prettyPrintedXmlWorks() throws Exception {
        String pretty = "<GDT>\n  <set type=\"6310\">\n    <F8000>6310</F8000>\n    <F6228>  spaced  </F6228>\n    <F3101>Jan</F3101>\n  </set>\n</GDT>\n";
        String gdt = serializer().fromXML(pretty);

        GDTParser.FieldSet set = GDTParser.parse(gdt, true).get(0);
        assertEquals("  spaced  ", set.value("6228"));
        assertEquals("Jan", set.value("3101"));
    }

    @Test
    public void aValueWithLineBreaksBecomesSeveralLinesAndComesBack() throws Exception {
        String gdt = serializer().fromXML("<GDT><set><F8000>6310</F8000><F6228>one\ntwo\n\nfour</F6228><F3101>Jan</F3101></set></GDT>");

        // one field, spread over four physical lines; the length counts every CR LF
        assertTrue(gdt, gdt.contains("025" + "6228one\r\ntwo\r\n\r\nfour\r\n"));
        GDTParser.FieldSet set = GDTParser.parse(gdt, false).get(0);
        assertEquals("one\ntwo\n\nfour", set.value("6228"));
        assertEquals("Jan", set.value("3101"));
        assertEquals(gdt, serializer().fromXML(serializer().toXML(gdt)));
    }

    @Test
    public void aLongValueGetsAFourDigitLength() throws Exception {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 991; i++) {
            value.append('x');
        }
        String gdt = serializer().fromXML("<GDT><set><F8000>6310</F8000><F8420>" + value + "</F8420></set></GDT>");

        // the line is 1001 long: three digits would be 1000, and the fourth digit is part of the line
        assertTrue(gdt.contains("1001" + "8420" + value + "\r\n"));
        assertEquals(value.toString(), GDTParser.parse(gdt, false).get(0).value("8420"));
    }

    @Test
    public void aValueThatIsMuchTooLongIsRefused() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 9991; i++) {
            value.append('x');
        }
        assertFails(serializer(), false, "<GDT><set><F8000>6310</F8000><F6228>" + value + "</F6228></set></GDT>", "Field 6228 is too long");
    }

    @Test
    public void setLengthWithMoreThanFiveDigits() throws Exception {
        StringBuilder xml = new StringBuilder("<GDT><set><F8000>6310</F8000>");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 900; i++) {
            text.append('y');
        }
        for (int i = 0; i < 130; i++) {
            xml.append("<F6228>").append(text).append("</F6228>");
        }
        xml.append("</set></GDT>");

        String gdt = serializer().fromXML(xml.toString());
        String length = GDTParser.parse(gdt, false).get(0).value("8100");

        assertEquals(6, length.length());
        // the length says what the set really is, itself included, so that a strict reader accepts it
        assertEquals(1, GDTParser.parse(gdt, true).size());
        assertEquals(gdt.length(), Integer.parseInt(length));
    }

    @Test
    public void unknownElementsAreRefusedWithAHint() {
        assertFails(serializer(), false, "<GDT><set><PID>1</PID></set></GDT>", "F and its four digit number");
        assertFails(serializer(), false, "<GDT><F3101>x</F3101></GDT>", "a set is an element named set");
        assertFails(serializer(), false, "<GDT><set><F3101><b>x</b></F3101></set></GDT>", "can only contain text");
    }

    // ---- what real devices write

    /** A set as a sleep-study device writes it: a field of many lines with a four digit length that is one too low. */
    private static String deviceSet() {
        StringBuilder longValue = new StringBuilder("Position changes when at least 5 seconds of continuous position is found.");
        for (int i = 0; i < 9; i++) {
            longValue.append("\r\n").append("Movement is detected when the activity signal exceeds a threshold of 0,2 for a minimum of 1 seconds and more text to make it long enough ").append(i);
        }
        String value = longValue.toString();
        int declared = 4 + 4 + value.length() + 2 - 1;
        return line("8000", "6310") + "0158100138144\r\n" + line("8316", "NOX_T3") + line("8410", "Analysis Criteria") + declared + "8420" + value + "\r\n" + line("8410", "Excluded Time (m)") + line("8420", "0,0") + line("8421", "m");
    }

    @Test
    public void aFieldOfSeveralLinesWithAFourDigitLengthIsOneField() throws Exception {
        GDTParser.FieldSet set = GDTParser.parse(deviceSet(), false).get(0);

        assertEquals("NOX_T3", set.value("8316"));
        assertEquals("138144", set.value("8100"));
        // the fields before and after the long one are intact
        assertEquals(8, set.fields.size());
        GDTParser.Field longField = set.fields.get(4);
        assertEquals("8420", longField.id);
        assertTrue(longField.value.startsWith("Position changes"));
        assertEquals(10, longField.value.split("\n").length);
        assertEquals("Excluded Time (m)", set.fields.get(5).value);
        assertEquals("m", set.fields.get(7).value);
    }

    @Test
    public void aDeviceSetSurvivesTheTripThroughXml() throws Exception {
        String xml = serializer().toXML(deviceSet());
        Element root = xml(xml);

        assertEquals(10, child(root, "F8420", 0).getTextContent().split("\n").length);
        GDTParser.FieldSet back = GDTParser.parse(serializer().fromXML(xml), false).get(0);
        assertEquals(GDTParser.parse(deviceSet(), false).get(0).value("8420"), back.value("8420"));
        assertEquals(8, back.fields.size());
    }

    @Test
    public void strictRejectsWhatTheSpecificationDoesNotAllow() {
        try {
            GDTParser.parse(deviceSet(), true);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("length says"));
        }
        try {
            GDTParser.parse(line("8000", "6310") + "some text that is not a GDT line\r\n", true);
            fail();
        } catch (GDTParser.SyntaxException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("is not a GDT line"));
        }
    }

    @Test
    public void aNormalLongLineIsNotTakenForAFourDigitLength() throws Exception {
        // 190 characters: the length is 199 and the field number 8420, and 1998 must not become the length
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 190; i++) {
            value.append('z');
        }
        GDTParser.FieldSet set = GDTParser.parse(line("8000", "6310") + line("8420", value.toString()), false).get(0);

        assertEquals("8420", set.fields.get(1).id);
        assertEquals(value.toString(), set.fields.get(1).value);
    }

    @Test
    public void aFieldNumberThatIsNotInTheSpecificationStaysAsItIs() throws Exception {
        String longUnknown = line("8000", "6310") + "0291234" + "value";
        // no four digit reading: the field is 1234 whatever the length says
        GDTParser.FieldSet set = GDTParser.parse(longUnknown + "\r\n", false).get(0);

        assertEquals("1234", set.fields.get(1).id);
    }

    // ---- metadata

    @Test
    public void metaDataIsSenderTypeAndVersion() {
        Map<String, Object> map = new HashMap<String, Object>();
        serializer().populateMetaData(set("80006310", "8316LZBD_SYS", "921802.10"), map);

        assertEquals("LZBD_SYS", map.get(DefaultMetaData.SOURCE_VARIABLE_MAPPING));
        assertEquals("6310", map.get(DefaultMetaData.TYPE_VARIABLE_MAPPING));
        assertEquals("02.10", map.get(DefaultMetaData.VERSION_VARIABLE_MAPPING));
    }

    @Test
    public void metaDataOfGarbageIsEmptyAndDoesNotThrow() {
        Map<String, Object> map = new HashMap<String, Object>();
        serializer().populateMetaData("not gdt", map);

        assertTrue(map.isEmpty());
    }

    // ---- batch

    @Test
    public void splitterGivesOneMessagePerSet() throws Exception {
        String file = set("80006310", "3101A") + "\r\n" + set("80006310", "3101B") + set("80006301", "3101C");
        GDTSetSplitter splitter = new GDTSetSplitter(new BufferedReader(new StringReader(file.replace("\r\n", "\n"))));

        String first = splitter.next();
        String second = splitter.next();
        String third = splitter.next();

        assertEquals(set("80006310", "3101A"), first);
        assertEquals(set("80006310", "3101B"), second);
        assertEquals(set("80006301", "3101C"), third);
        assertNull(splitter.next());
        assertNull(splitter.next());
    }

    @Test
    public void splitterOfAnEmptyFile() throws Exception {
        assertNull(new GDTSetSplitter(new BufferedReader(new StringReader(""))).next());
        assertNull(new GDTSetSplitter(new BufferedReader(new StringReader("\r\n\r\n"))).next());
    }

    @Test
    public void splitterKeepsContentBeforeTheFirstSetInTheFirstMessage() throws Exception {
        GDTSetSplitter splitter = new GDTSetSplitter(new BufferedReader(new StringReader(line("3101", "Jan") + line("8000", "6310"))));

        assertEquals(line("3101", "Jan"), splitter.next());
        assertEquals(line("8000", "6310"), splitter.next());
    }

    // ---- vocabulary and delegate

    @Test
    public void vocabularyKnowsTheFields() {
        GDTVocabulary v = new GDTVocabulary("2.1", "6310");

        assertEquals("Patient name", v.getDescription("F3101"));
        assertEquals("Name of open category", v.getDescription("F6340"));
        assertEquals("Content of open category", v.getDescription("F6341"));
        assertEquals("", v.getDescription("F9999"));
        assertEquals("Set", v.getDescription("set"));
        assertEquals("GDT", v.getDataType());
    }

    @Test
    public void delegateDescribesTheDataType() {
        GDTDataTypeDelegate d = new GDTDataTypeDelegate();

        assertEquals("GDT", d.getName());
        assertFalse(d.isBinary());
        assertNotNull(d.getDefaultProperties());
        assertTrue(d.getSerializer(d.getDefaultProperties().getSerializerProperties()) instanceof GDTSerializer);
    }
}
