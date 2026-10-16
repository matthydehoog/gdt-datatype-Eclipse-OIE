# Development guide

This guide is for people who build, change or release the GDT Data Type. For using the data type in a channel, see the [README](../README.md).

## Contents

- [Repository layout](#repository-layout)
- [Building](#building)
- [How the plugin is put together](#how-the-plugin-is-put-together)
- [Message flow](#message-flow)
- [Reading GDT: GDTParser](#reading-gdt-gdtparser)
- [GDT to XML: GDTReader](#gdt-to-xml-gdtreader)
- [XML to GDT: GDTXMLHandler](#xml-to-gdt-gdtxmlhandler)
- [Field names: GDTFields](#field-names-gdtfields)
- [Batch processing](#batch-processing)
- [Properties and saved channels](#properties-and-saved-channels)
- [The web administrator](#the-web-administrator)
- [Testing](#testing)
- [Trying it in OIE](#trying-it-in-oie)
- [Continuous integration](#continuous-integration)
- [Releasing](#releasing)
- [Conventions](#conventions)

## Repository layout

| Path | What it is |
| --- | --- |
| `src/main/java/.../gdt/` | Shared classes: serializer, parser, reader, XML handler, properties, field names. Goes into `datatype-gdt-shared.jar`. |
| `src/main/java/.../gdt/server/` | Server-only classes: server plugin and batch adaptor. `datatype-gdt-server.jar`. |
| `src/main/java/.../gdt/client/` | Swing Administrator classes: client plugin and code templates. `datatype-gdt-client.jar`. |
| `src/main/resources/plugin.xml` | The extension descriptor the engine reads. |
| `src/test/` | JUnit 4 tests (`GDTSerializerTest`). |
| `webadmin/` | The properties panel for the OIE web administrator (`plugin.json`, `web/plugin.js`). |
| `examples/` | Sample GDT files and the XML the plugin makes of one of them. |
| `assembly.xml` | How the extension zip is laid out. |
| `oie.json` | The manifest for the OIE Community Store catalog. |
| `.github/` | Build, release, catalog and Jira workflows and their scripts. |

`...` is `com/mirth/connect/plugins/datatypes`. The package must be in the `com.mirth.connect` family: the engine's XStream allow-list only accepts classes from there when it reads a channel.

The structure follows the engine's built-in EDI/X12 data type; several classes say so in their header.

## Building

### Prerequisites

- **JDK 17** (the engine's own Java). The code targets Java 11.
- **Maven 3.8** or later.
- **The OIE engine jars** in your local Maven repository, version `oie.version` from `pom.xml` (now 4.6.0). They are not in Maven Central. The script the CI uses installs them from the engine's GitHub release, checked against its published SHA-256:

```bash
bash .github/scripts/install-oie-jars.sh 4.6.0
```

It needs `curl`, `tar`, `sha256sum` and `mvn` (Git Bash on Windows has them). It installs:

| Maven artifact (`com.mirth.connect`) | File in the engine release |
| --- | --- |
| `server-api` | `server-lib/mirth-server.jar` |
| `donkey-server` | `server-lib/donkey/donkey-server.jar` |
| `donkey-model` | `server-lib/donkey/donkey-model.jar` |
| `client` | `client-lib/mirth-client.jar` |
| `client-core` | `client-lib/mirth-client-core.jar` |

### Build

```bash
mvn clean package
```

The result is `target/datatype-gdt-<version>.zip`. `package` runs the tests first; add `-DskipTests` to skip them, or `-o` to build offline.

### What is in the zip

`maven-jar-plugin` makes three jars out of one source tree, by package; `assembly.xml` puts them in the zip:

```
datatype-gdt/
├── plugin.xml
├── datatype-gdt-shared.jar   gdt/*.class
├── datatype-gdt-server.jar   gdt/server/**
├── datatype-gdt-client.jar   gdt/client/**
└── webadmin/                 the web administrator panel
```

The folder name `datatype-gdt` must equal the `path` attribute in `plugin.xml`: the engine and the web administrator build URLs from it. The plugin has no dependencies of its own; everything it uses is on the engine's classpath.

## How the plugin is put together

| Plugin point | Class | Purpose |
| --- | --- | --- |
| `DataTypeDelegate` | `GDTDataTypeDelegate` | Name (`GDT`), serializer, default properties; text, not binary; the transformer works on XML. |
| `DataTypeServerPlugin` | `server.GDTDataTypeServerPlugin` | Registers the data type on the server, plus the batch adaptor. |
| `DataTypeClientPlugin` | `client.GDTDataTypeClientPlugin` | Registers the data type in the Swing Administrator. |
| `DataTypeCodeTemplatePlugin` | `client.GDTDataTypeCodeTemplatePlugin` | The standard *Convert GDT to XML / XML to GDT* entries in the reference list. |
| `IMessageSerializer` | `GDTSerializer` | GDT to XML, XML to GDT, metadata. |
| `DataTypeProperties` | `GDTDataTypeProperties` | Holds `GDTSerializationProperties` and `GDTBatchProperties`. |
| `MessageVocabulary` | `GDTVocabulary` | Descriptions in the message tree: field names, and *Set*, *Results*, *Result*, *Categories*, *Category*. |

The work is done by four classes that know nothing about the engine apart from SAX and the engine's `AbstractXMLReader`, so they can be tested on their own:

| Class | Role |
| --- | --- |
| `GDTParser` | Text to sets of fields. |
| `GDTReader` | Sets of fields to SAX events, so to XML. |
| `GDTXMLHandler` | SAX events back to GDT lines, with the lengths calculated. |
| `GDTFields` | The names of the fields and set types of GDT 2.1. |
| `GDTSetSplitter` | Splits a file into sets for batch processing. |

## Message flow

```
inbound GDT text
   ├─ populateMetaData()   GDTParser (lenient) → source 8316, type 8000, version 9218 of the first set
   └─ toXML()              GDTReader → SAX → XMLPrettyPrinter → XML for the transformer
                              ↓ transformer (E4X on msg)
outbound XML
   └─ fromXML()            strip pretty-print whitespace → SAX parser (DOCTYPE refused) → GDTXMLHandler → GDT text
```

- `isSerializationRequired` is `false` and `transformWithoutSerializing` returns `null`, so without filter or transformer steps a message passes through unchanged, as in the EDI/X12 data type.
- `populateMetaData` never throws: a message that cannot be read only logs a warning, and `toXML` reports the real error.
- `fromXML` sets the SAX feature `disallow-doctype-decl`, so XML with a DOCTYPE is refused before entities can be resolved.
- Whitespace between tags of pretty-printed XML is removed first (two patterns, as in EDI/X12); whitespace inside a value is kept.

## Reading GDT: GDTParser

`GDTParser.parse(text, strict)` returns a list of `FieldSet`s, each a list of `Field(id, value)` in message order.

A GDT line is `LLLFFFFvalue` + CR LF: `LLL` is the length of the whole line, `FFFF` the field number. A set starts with field `8000` and has `8100`, the length of the set. The constants are in `GDTParser` (`LINE_OVERHEAD` = 9, `MAX_LINE_LENGTH` = 999, `MAX_LONG_LINE_LENGTH` = 9999).

Real devices break the specification. The parser is lenient by default, and only `strict` rejects:

| Devices write | Lenient | Strict |
| --- | --- | --- |
| A wrong line length | Ignored | Error |
| A value with line breaks: following lines without length and field number | Appended to the field before, joined with `\n` | Error (not a GDT line) |
| A four digit length (`1342`) for a long line | Read as four digits when that is the only sensible reading (below) | Not tried: the line is read with three digits, and its length check fails |
| `8100` with six digits, or a wrong value | Read as it is | Error when it does not match the set |
| Fields before the first `8000` | Kept, as a set of their own | Error |
| Text that is not a GDT line before any field | Error | Error |

A byte order mark at the start, blank lines between sets and an old DOS end-of-file mark are skipped.

**The four digit heuristic** (`isLongLine`, lenient mode only) is the subtle part. `13428420text` can be read as length 134, field `2842`, or as length 1342, field `8420`. The four digit reading is chosen only when all of these hold:

1. the first four digits are at least 1000;
2. the three digit reading does not give the right length;
3. the three digit field is **not** in `GDTFields` and the four digit field **is**.

So the list of field names in `GDTFields` takes part in parsing. Adding a name there can change how an odd line is read; run the tests after such a change.

**Lengths.** `contentLength` counts a line break inside a value as two characters (CR LF), whichever line ending is written, as the specification does. `lineLength` adds the overhead; `physicalLength` adds one for a fourth length digit. Lengths are characters, which are bytes in the single-byte character sets GDT uses; the connector decodes the bytes, not the data type.

## GDT to XML: GDTReader

`GDTReader` (an `AbstractXMLReader`) parses with `GDTParser` and emits:

```xml
<GDT>
  <set type="6310" name="Test data transfer">
    <F8000 name="Set type">6310</F8000>
    ...
```

- Every field is an element `F` + four digits, in message order; repeats stay repeats.
- `name` attributes come from `GDTFields` when *Field Names* is on.
- Lengths are not in the XML: they are calculated again on the way back.
- Control characters other than tab and line break are dropped (XML cannot hold them).

Three optional groupings change only the XML, never the GDT. Each is off by default, so channels that existed before keep the same XML:

| Setting | XML |
| --- | --- |
| `groupResults` | A run of `8410` (Test ID) with the `8411`, `8420`, `8421` that follow becomes `<results><result>…</result></results>`. A new `8410` starts the next `result`; any other field ends the run. |
| `groupCategories` | Open categories, a name field `6330`, `6332` … `6398` followed by its content field `6331` … `6399`, become `<categories><category name="…">content</category></categories>`. |
| `joinResultsText` | Consecutive `8480` (Results text) fields are joined into one, with nothing added between them. |

The element names are constants in `GDTReader` (`SET`, `RESULTS`, `RESULT`, `CATEGORIES`, `CATEGORY`); `GDTVocabulary` and `GDTXMLHandler` use the same constants.

## XML to GDT: GDTXMLHandler

`GDTXMLHandler` (a SAX `DefaultHandler`) collects the fields of each `set` and writes them when the set ends:

- Elements matching `F####` (either case of `F`) are fields; any other element where a field is expected is refused with a hint.
- `results`/`result` are flattened back into their fields, in order.
- `categories`/`category` become a name and a content field, numbered from `6330` again. More categories than the numbers allow is an error. Which number a category had in the original message is not kept.
- Every line gets its length. A line longer than 999 gets a four digit length; a value that does not fit in 9999 is refused, with the maximum in the message.
- `8100` is calculated and added right behind `8000` when missing, unless *Calculate Set Length* is off; then it is written as it is in the XML (or not at all).
- A line break in a value is written as a new line, with the chosen *Line Ending*.
- Attributes in the XML (`name`, `type`) are ignored.

The round trip GDT → XML → GDT gives the same message, with the lengths corrected. Several tests check exactly that.

## Field names: GDTFields

`GDTFields` holds two static tables from the GDT 2.1 interface description (QMS, 5/2001): field numbers and set types with their English names. They are used for:

- the `name` attributes in the XML;
- the message tree descriptions (`GDTVocabulary`);
- the four digit heuristic in `GDTParser` (see above).

Fields that are not in the table are read and written like any other; they just have no name.

## Batch processing

With *Process Batch Files* on in the source connector, the engine asks `GDTDataTypeServerPlugin.getBatchAdaptorFactory` for an adaptor. `GDTBatchAdaptor` either:

- **Set**: uses `GDTSetSplitter`, which reads line by line and starts a new message at every line for field `8000`. It has to look one line ahead; that line waits in the splitter until the next call. Content before the first set stays in the first message. Each message ends its lines with CR LF.
- **JavaScript**: runs the user's batch script with `reader`, modelled on the engine's other batch adaptors.

## Properties and saved channels

Channels are stored with XStream, which **does not run constructors**. A property added in a later version is missing (`null` for objects, the type's default for primitives) in a channel saved before. The rules that follow:

- Properties that default to `true` are `Boolean` objects, and their getters return `true` when the field is `null` (`calculateSetLength`, `fieldNames`).
- Properties that default to `false` can be plain `boolean`: a missing value is `false` anyway (`strict`, `groupResults`, `groupCategories`, `joinResultsText`).
- Enum getters return the default when `null` (`lineEnding`).

### Adding a property

1. Add the field and a null-safe getter to `GDTSerializationProperties` (or `GDTBatchProperties`).
2. Add it to `getPropertyDescriptors()` (label and tooltip in the Swing Administrator) and `setProperties()`.
3. Add it to `webadmin/web/plugin.js` with the same key, label, default and tooltip.
4. Document it in the README's settings table.
5. Choose the default so that existing channels behave as before.
6. Test that a channel saved with the previous version still deploys.

## The web administrator

`webadmin/plugin.json` registers the panel and declares `GDTVocabulary` for the message tree (`vocabularies`, read by Web Support 1.1.0 and later; the class must stay in the shared jar with its `(String version, String type)` constructor, and `getDataType()` must return `GDT`); `webadmin/web/plugin.js` describes the fields of both property groups (serialization and batch) with their keys, defaults and tooltips, and builds the default property objects. It is a static copy of `getPropertyDescriptors()`, so keep both in sync by hand. After installing a new version, a hard refresh (Ctrl+F5) loads the new panel.

## Testing

```bash
mvn test
```

All 69 tests are in `GDTSerializerTest` (JUnit 4). They need no running engine and no network. They are grouped by subject:

| Subject | Examples |
| --- | --- |
| Parser | line endings, several sets, wrong lengths lenient and strict, `8100` checks, blank lines and end-of-file marks |
| GDT to XML | sets and fields, repeated fields, field names on and off, escaping, control characters |
| Groupings | `groupResults`, `groupCategories`, `joinResultsText`: off by default, nesting, runs, round trips, refused elements |
| XML to GDT | round trips, lengths corrected, `8100` added or left alone, line endings, a message built from scratch, pretty-printed XML |
| Long values | values with line breaks, four digit lengths, values that are too long, `8100` with six digits |
| Real devices | a NOX T3 sleep-study set (Noxturnal) that survives the trip through XML |
| Metadata, splitter, vocabulary, delegate | sender/type/version, garbage input, one message per set |

Write new tests in the same style: a name that says what should happen, and a small GDT text built with the test's `line(field, value)` helper.

## Trying it in OIE

1. `mvn clean package`.
2. Install `target/datatype-gdt-<version>.zip` on a **test** server (*Settings > Extensions > Install Extension*) and restart the service. Restart the Swing Administrator; in the web administrator, refresh with Ctrl+F5.
3. Make a channel with a File Reader (inbound data type **GDT**) and use the files in `examples/`. Set the File Reader's encoding to match field `9206` (`IBM437` or `ISO-8859-1`).
4. Check the XML in the message browser and the GDT that a File Writer with outbound data type GDT writes.

If the installer refuses because the extension already exists, uninstall the old version first and restart.

## Continuous integration

`.github/workflows/build.yml` runs on every push to `main`, every pull request and every `v*` tag:

1. Java 17 (Temurin) and a Maven cache keyed on the engine version and `pom.xml`.
2. `install-oie-jars.sh` when the engine jars are not cached. `OIE_VERSION` in the workflow must equal `oie.version` in `pom.xml`.
3. `check-versions.sh`: the version in `pom.xml`, `plugin.xml`, `oie.json` and `webadmin/plugin.json` must be the same, and on a tag equal to the tag.
4. `mvn -B package`, and the zip is kept as a build artifact.
5. On a tag: `release.sh` creates the release, then the `catalog` job calls `catalog-pr.yml`.

Dependabot (`.github/dependabot.yml`) proposes updates weekly for Maven and monthly for GitHub Actions. It ignores the engine jars and the libraries the engine ships (`log4j-api`, `commons-lang3`, `rhino`): those must match the engine, not the newest version.

Other workflows:

| Workflow | Does |
| --- | --- |
| `catalog-pr.yml` | Files a pull request to the [OIE Community Store catalog](https://github.com/gibson9583/oie-community-catalog) from the fork `<owner>/oie-community-catalog`. Needs the secret `CATALOG_TOKEN` (a classic PAT with `public_repo`); skipped without it. |
| `jira-issue.yml` | Creates a Jira issue for every new GitHub issue and comments its key. Needs the variables `JIRA_BASE_URL`, `JIRA_EMAIL`, `JIRA_PROJECT_KEY` and the secret `JIRA_API_TOKEN`. |

## Releasing

1. **Branch and pull request.** Reference the issue (`Closes #n`); the build must be green.
2. **Version.** Set the new version in `pom.xml`, `src/main/resources/plugin.xml` (`pluginVersion`), `oie.json` and `webadmin/plugin.json`. The build fails when they differ.
3. **Engine versions.** `<mirthVersion>` in `plugin.xml` lists every OIE version the extension may load on (now `4.6.0,4.7.0`). OIE only loads an extension whose list contains its **exact** version, so a new OIE release needs a new plugin release.
4. **Release notes (optional).** To write them yourself, create the release on GitHub first, without a zip. Otherwise `release.sh` writes short notes with the install steps and the SHA-256, plus GitHub's generated notes.
5. **Tag** the merged commit on `main` and push the tag:

   ```bash
   git tag v1.0.7
   git push origin v1.0.7
   ```

   The build creates the release with `datatype-gdt-<version>.zip` and its `.sha256`, or adds them to the release you wrote. A release that already has a zip is left alone: a new build gives a different file, whose checksum would not match.
6. **Catalog.** Check the pull request the catalog job filed, and close earlier ones of this plugin that are still open.
7. **Jira.** Closing a GitHub issue does not close its Jira issue: set it to *Done* yourself.
8. **Documentation.** Update the README when behaviour or settings change.

`release.sh` can be tried locally with `DRY_RUN=1 bash .github/scripts/release.sh v1.0.7` after a build.

## Conventions

- Every source file starts with the MPL 2.0 header; files based on the engine's EDI/X12 data type also credit it.
- Java 11 language level; no dependencies outside the engine's classpath.
- Keep the round trip exact: the XML may present a message differently (names, groupings), but XML to GDT must give the same fields in the same order.
- New behaviour that changes the XML is off by default, so existing channels keep working.
- Error messages say what is wrong and how to fix it, for example *"Field 8480 is too long … Split it over several fields."*
- Comments explain *why*; keep them short.
