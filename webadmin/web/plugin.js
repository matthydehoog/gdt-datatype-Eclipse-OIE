// GDT data type - properties panel for the web administrator (equivalent of the Swing
// DataTypeClientPlugin properties). Same shape as the built-in EDI/X12 data type plugin.
const PKG = "com.mirth.connect.plugins.datatypes.gdt";

const bool = (key, label, def, hint) => ({ key, label, type: "checkbox", default: def, hint });
const opt = (key, label, options, def, hint) => ({ key, label, type: "select", options, default: def, hint });
const code = (key, label, def, hint) => ({ key, label, type: "code", default: def, hint });

const BATCH_SCRIPT_HINT =
  "JavaScript that splits the batch and returns the next message. Has access to 'reader' (a Java BufferedReader); return null/empty to signal end of input. Only used when Process Batch is enabled in the connector.";

const DEF = {
  name: "GDT",
  label: "GDT",
  order: 72,
  propertiesClass: `${PKG}.GDTDataTypeProperties`,
  groups: [
    {
      key: "serializationProperties",
      label: "Serialization",
      class: `${PKG}.GDTSerializationProperties`,
      fields: [
        bool("strict", "Strict Parsing", false, "If checked, a GDT message is rejected when a line length (the first three digits) or the set length (field 8100) is wrong, or when a set does not start with field 8000. Many devices get these wrong, so by default they are ignored."),
        bool("fieldNames", "Field Names", true, "If checked, the name of each field from the GDT specification is added to the XML as an attribute, for example <F3101 name=\"Patient name\">. The names are ignored when converting XML to GDT."),
        bool("calculateSetLength", "Calculate Set Length", true, "If checked, field 8100 (set length) is calculated when converting XML to GDT, and added after field 8000 when the XML has none. If not checked, 8100 is written as it is in the XML. The length of every line (the first three digits) is always calculated."),
        opt(
          "lineEnding",
          "Line Ending",
          [
            { value: "CRLF", label: "CRLF" },
            { value: "LF", label: "LF" },
            { value: "CR", label: "CR" }
          ],
          "CRLF",
          "What ends a line when XML is converted to GDT. The specification asks for CRLF. When reading GDT, CRLF, LF and CR are all accepted."
        )
      ]
    },
    {
      key: "batchProperties",
      label: "Batch",
      class: `${PKG}.GDTBatchProperties`,
      fields: [
        opt(
          "splitType",
          "Split Batch By",
          [
            { value: "Set", label: "Set" },
            { value: "JavaScript", label: "JavaScript" }
          ],
          "Set",
          "Method for splitting the batch message. Set: every GDT set (starting at field 8000) is one message. Only used when Process Batch is enabled in the connector."
        ),
        code("batchScript", "JavaScript", null, BATCH_SCRIPT_HINT)
      ]
    }
  ]
};

DEF.defaults = (version) => {
  const props = { "@class": DEF.propertiesClass, "@version": version };
  for (const group of DEF.groups) {
    const obj = { "@class": group.class, "@version": version };
    for (const f of group.fields) obj[f.key] = f.default ?? null;
    props[group.key] = obj;
  }
  return props;
};

export function register(platform) {
  platform.registerDataType(DEF.name, DEF);
}
