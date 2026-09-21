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
    public void aFieldWithALineBreakIsRefused() {
        assertFails(serializer(), false, "<GDT><set><F8000>6310</F8000><F6228>a\nb</F6228></set></GDT>", "line break");
    }

    @Test
    public void aFieldThatIsTooLongIsRefused() throws Exception {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i < 991; i++) {
            value.append('x');
        }
        assertFails(serializer(), false, "<GDT><set><F8000>6310</F8000><F6228>" + value + "</F6228></set></GDT>", "Field 6228 is too long");
        // 990 fits
        String ok = serializer().fromXML("<GDT><set><F8000>6310</F8000><F6228>" + value.substring(1) + "</F6228></set></GDT>");
        assertTrue(ok.contains("999" + "6228"));
    }

    @Test
    public void unknownElementsAreRefusedWithAHint() {
        assertFails(serializer(), false, "<GDT><set><PID>1</PID></set></GDT>", "F and its four digit number");
        assertFails(serializer(), false, "<GDT><F3101>x</F3101></GDT>", "a set is an element named set");
        assertFails(serializer(), false, "<GDT><set><F3101><b>x</b></F3101></set></GDT>", "can only contain text");
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
