# GDT Data Type for Eclipse OIE

A **GDT data type** for [Eclipse Open Integration Engine](https://openintegrationengine.org/) (tested against **4.6.0**), modelled on the built-in *EDI / X12* data type. GDT (*Geräte-Daten-Träger*, "device data carrier") is the interface of the QMS (Qualitätsring Medizinische Software) between medical measuring devices (ECG, spirometer, blood pressure monitor, ...) and the computer system of a practice. Once installed, "GDT" shows up next to HL7 v2.x, EDI / X12 and the other data types in the Swing client and the web administrator.

> Community extension. It is not part of, or endorsed by, the Eclipse OIE project, nor by the QMS.

## What you get

- The **GDT** data type for source and destination connectors, with a properties panel in the **Swing client** and the **web administrator**.
- Reads GDT (version 2.1 of the interface description) into **XML** and writes XML back to GDT. Sets, fields that occur more than once (`6220`, `8410`, ...), several sets in one file and all line endings (CRLF, LF, CR) are handled.
- **Line lengths are calculated for you** when XML is written as GDT, and so is field `8100` (the length of the set). A transformer can build a set from scratch without counting characters.
- **Lenient by default.** Many devices get the length digits wrong (the sample files of the specification itself do), so a wrong length is ignored. *Strict Parsing* rejects it.
- **Metadata** for the message list and searches: source = sender GDT-ID (`8316`), type = set type (`8000`, e.g. `6310`), version = GDT version (`9218`).
- **Message tree descriptions** with the names of the fields of the specification.
- **Batch** processing: a file with several sets can be split into one message per set.

## Install

1. Download `datatype-gdt-<version>.zip` from the [Releases](../../releases) page (or build it, see below).
2. Settings -> Extensions -> **Install Extension**, choose the zip, restart the engine.
3. Restart the Swing client. In the web administrator do a hard refresh (Ctrl+F5).
4. In a channel, open the source connector's **Set Data Types** and choose **GDT**.

If the installer refuses because the extension already exists, uninstall the old version first and restart.

## The XML

A GDT set is a list of lines. Each line is `LLLFFFFvalue`: three digits with the length of the line (including these digits, the field number and the CR LF), the four digit field number, and the value.

```
01380006300
014810000085
0178315PRAX_EDP
0178316LUFU_SYS
014921802.10
01030000
```

becomes

```xml
<GDT>
  <set type="6300" name="Root data request">
    <F8000 name="Set type">6300</F8000>
    <F8100 name="Set length">00085</F8100>
    <F8315 name="Receiver GDT-ID">PRAX_EDP</F8315>
    <F8316 name="Sender GDT-ID">LUFU_SYS</F8316>
    <F9218 name="GDT version">02.10</F9218>
    <F3000 name="Patient number / patient label">0</F3000>
  </set>
</GDT>
```

(The engine gives the XML on one line; it is indented here.) A file with several sets gives several `set` elements.

- A field is an element named **`F`** and the four digit field number: `F3101` for the patient name.
- The order of the fields is the order of the message. A field that occurs more than once, such as `6220`, is repeated.
- The `name` attributes come from the specification and can be switched off (*Field Names*). Attributes are ignored when the XML is turned back into GDT, and so are `type` and the length digits.
- The value of a field is exactly what is in the message, spaces included. That matters for `6228`, the formatted result table.
- Control characters (below space, apart from a tab) cannot be in XML and are left out.

The example files in [examples](examples) are the real output of the plugin.

## Settings

| Setting | Meaning | Default |
|---|---|---|
| Strict Parsing | Reject a message with a wrong line length, a wrong set length (`8100`) or a set that does not start with `8000` | off |
| Field Names | Add the name of each field as an attribute in the XML | on |
| Calculate Set Length | Calculate `8100` when XML is written as GDT, and add it behind `8000` when missing. Off: write it as it is in the XML | on |
| Line Ending | What ends a line when XML is written as GDT: CRLF, LF or CR. The specification asks for CRLF | CRLF |
| Split Batch By (Batch) | *Set*: every set is one message. *JavaScript*: your own splitter. Only used when *Process Batch Files* is on in the connector | Set |

The length of every line is always calculated, and it counts a CR LF whichever line ending is written, as the specification does. Lengths are counted in characters, which are bytes in the single byte character sets that GDT uses.

## Using it in a channel

Read a file that a device left in a directory (File Reader, inbound data type **GDT**):

```javascript
var set = msg['set'][0];                            // the first set
var patient = set['F3101'].toString() + ', ' + set['F3102'].toString();
var test = set['F8402'].toString();                 // for example EKG01

// fields that occur more than once
for each (var line in set['F6220']) {
    logger.info(line.toString());
}
```

To write GDT, let the outbound data type of the destination be **GDT** and give the transformer XML to write. You only fill in the values:

```xml
<GDT>
  <set>
    <F8000>6301</F8000>
    <F8315>LUFU_SYS</F8315>
    <F8316>PRAX_EDP</F8316>
    <F9218>02.10</F9218>
    <F3000>${patientNumber}</F3000>
    <F3101>${lastName}</F3101>
    <F3102>${firstName}</F3102>
    <F3103>${birthDate}</F3103>
  </set>
</GDT>
```

The line lengths and `8100` are added when the message is written. A value cannot contain a line break; use several fields (for example `6228`) instead. A field can hold at most 990 characters.

Which set answers which:

| Set | Direction | Meaning |
|---|---|---|
| 6300 | device -> practice system | Root data request. Must be answered with a 6301 |
| 6301 | practice system -> device | Root data transfer (the patient) |
| 6302 | practice system -> device | New test request (`8402` says which test, e.g. `EKG01`) |
| 6310 | device -> practice system | Test data transfer (the results) |
| 6311 | practice system -> device | Test data display |

## Character sets

Field `9206` says how the file is encoded: `1` = 7 bit, `2` = IBM code page 437 (the default of the specification), `3` = ISO 8859-1 (ANSI, code page 1252). The data type does not decode the bytes; the **connector** does. Set the encoding of the File Reader (and the File Writer) to match, for example `IBM437` or `ISO-8859-1`. With a wrong encoding, umlauts and accents come out wrong.

## Not included

- The **serial interface** (appendix A of the specification: blocks with a CRC-16 and ACK/NAK). This data type reads and writes the *lines* of GDT; use it with a File Reader / File Writer, or with any connector that delivers the whole message.
- **Checks of the contents** of fields (dates, the values of `3110`, the lengths in the field table). Only the structure of the lines and sets is checked, and only in strict mode.
- GDT versions other than the 2.1 interface description. Fields that are not in it are read and written like any other; they just have no name.

## Build

1. Install the engine jars into your local Maven repository under the coordinates `pom.xml` expects (`com.mirth.connect:server-api`, `donkey-server`, `donkey-model`, `client`, `client-core`, version = `oie.version`), taken from `<OIE_HOME>/server-lib` and `<OIE_HOME>/client-lib`.
2. Build with the JDK that comes with the engine:

   ```bash
   mvn clean package
   ```

   This produces `target/datatype-gdt-<version>.zip`:

   ```
   datatype-gdt/
   ├── plugin.xml
   ├── datatype-gdt-shared.jar     (serializer, properties: used by server and clients)
   ├── datatype-gdt-server.jar     (batch adaptor)
   ├── datatype-gdt-client.jar     (Swing plugin)
   └── webadmin/                   (web administrator panel: plugin.json + web/plugin.js)
   ```

## Design notes (for developers)

- **Package name.** The classes live in `com.mirth.connect.plugins.datatypes.gdt`. The engine's XStream allow-list only accepts classes from the `com.mirth.connect.*` family when it reads a channel.
- **Folder name = `path`.** The `path` attribute in `plugin.xml` (`datatype-gdt`) must equal the extension folder name.
- **Null-safe properties.** The engine does not run constructors when it reads a saved channel, so a setting that is added later is null there. The getters of `GDTSerializationProperties` fall back to the defaults, and settings that default to *on* are `Boolean` objects for that reason.
- **Two halves.** `GDTParser` turns text into sets and `GDTReader` turns those into SAX events (GDT to XML); `GDTXMLHandler` collects SAX events and writes the lines with their lengths (XML to GDT). The reader and the handler know nothing about the engine beyond `AbstractXMLReader`, which is why the parsing can be tested on its own.

## License

[Mozilla Public License 2.0](LICENSE). The structure of this data type follows the EDI / X12 data type of Open Integration Engine (Mirth Connect, Copyright (c) Mirth Corporation), which is MPL 2.0 as well.
