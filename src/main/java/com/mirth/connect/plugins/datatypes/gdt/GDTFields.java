/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.gdt;

import java.util.HashMap;
import java.util.Map;

/** Names of the fields and set types of GDT 2.1 (QMS, 5/2001), for the XML attributes and the message tree. */
public final class GDTFields {
    private static final Map<String, String> FIELDS = new HashMap<String, String>();
    private static final Map<String, String> SET_TYPES = new HashMap<String, String>();

    static {
        String[][] fields = { { "0102", "Software responsible (firm)" }, { "0103", "Software" }, { "0132", "Release state of software" }, { "3000", "Patient number / patient label" }, { "3100", "Prefix / additional name of patient" }, { "3101", "Patient name" }, { "3102", "Patient first name" }, { "3103", "Patient birth date" }, { "3104", "Patient title" }, { "3105", "Patient insurance number" }, { "3106", "Patient residence" }, { "3107", "Patient street" }, { "3108", "Insurance status" }, { "3110", "Patient sex" }, { "3622", "Patient height (cm)" }, { "3623", "Patient weight (kg)" }, { "3628", "First language of patient" }, { "6200", "Date test data were saved" }, { "6201", "Time test data were saved" }, { "6205", "Current diagnosis" }, { "6220", "Results" }, { "6221", "Third party results" }, { "6226", "Number of following lines of 6228" }, { "6227", "Comments" }, { "6228", "Result table text, formatted" }, { "6302", "Attribute for (archive) file" }, { "6303", "File format" }, { "6304", "Information about the content of the file" }, { "6305", "Reference to the file" }, { "8000", "Set type" },
                { "8100", "Set length" }, { "8315", "Receiver GDT-ID" }, { "8316", "Sender GDT-ID" }, { "8402", "Device and method specific field" }, { "8410", "Test ID" }, { "8411", "Test name" }, { "8418", "Test status" }, { "8420", "Result value" }, { "8421", "Unit" }, { "8428", "Test material ID" }, { "8429", "Test material index" }, { "8430", "Test material name" }, { "8431", "Test material specification" }, { "8432", "Reading date" }, { "8437", "Unit(s) for data stream" }, { "8438", "Data stream" }, { "8439", "Reading time" }, { "8460", "Standard value text" }, { "8461", "Standard value, lower threshold" }, { "8462", "Standard value, upper threshold" }, { "8470", "Test notes" }, { "8480", "Results text" }, { "8990", "Signature" }, { "9206", "Character set used" }, { "9218", "GDT version" } };
        for (String[] f : fields) {
            FIELDS.put(f[0], f[1]);
        }

        // The set table of 6310 (section 3.4) numbers the file fields 6202, 6203, 6204 and 6205, where the field
        // table (section 4) has 6302 to 6305. Name 6202 to 6204 as well, for devices that follow the set table;
        // 6205 stays "Current diagnosis", as in the field table and the set table's own earlier line.
        // A new number here also steers the reading of four digit lengths (GDTParser.isLongLine), see GDTFieldsTest.
        FIELDS.put("6202", "Attribute for (archive) file");
        FIELDS.put("6203", "File format");
        FIELDS.put("6204", "Information about the content of the file");

        // Fields that GDT 3.0 (QMS, 10/2013, section 4, the English edition) adds to GDT 2.1. Practice systems and
        // devices send them, also in files that say 02.10 in 9218. A field that both versions have keeps its
        // GDT 2.1 name above. Each of these takes part in the four digit heuristic too, see GDTFieldsTest.
        String[][] gdt30 = { { "0201", "(N)BSNR: Establishment number" }, { "0202", "Name of the payer" }, { "0212", "LANR (lifetime physician number)" }, { "0950", "Central pharmaceutical number for permanent medication" }, { "0957", "Administration form for permanent medication" },
                { "3112", "Postal code of the patient" }, { "3113", "Hometown of the patient" }, { "3114", "Residence country code" }, { "3116", "Health-insurance area" }, { "3119", "Health-insurance number (electronic health card) of the patient" },
                { "3618", "Cellphone number of the patient" }, { "3619", "Email address of the patient" }, { "3626", "Phone number of the patient" }, { "3649", "Start of permanent diagnosis" }, { "3650", "Permanent diagnosis" }, { "3651", "Start of permanent medication" }, { "3652", "Permanent medicament" }, { "3654", "Risk factor" }, { "3656", "Allergies" }, { "3658", "Accidents" }, { "3660", "Surgeries" }, { "3662", "Anamnesis" }, { "3664", "Number of deliveries" }, { "3666", "Number of children" }, { "3668", "Number of pregnancies" }, { "3670", "Permanent therapy" }, { "3672", "Follow-up appointment" }, { "3673", "Permanent diagnosis ICD-Code" }, { "3674", "Diagnostic confidence for permanent diagnosis" }, { "3675", "Body side localization for permanent diagnosis" }, { "3676", "Diagnosis explanation for permanent diagnosis" }, { "3677", "Diagnosis derogation for permanent diagnosis" },
                { "3700", "Label of the basic-diagnostic category" }, { "3701", "Content of the basic-diagnostic category" },
                { "4104", "No. of contracted health insurance" }, { "4106", "Payer billing area" }, { "4109", "Day of last reading of the health-insurance card in the quarter" }, { "4110", "Valid-to date" }, { "4111", "Health-insurance number" }, { "4112", "Insurance status" }, { "4113", "Status addition / DMP-labeling" }, { "4121", "Schedule of fees" }, { "4122", "Billing area" },
                { "4200", "Desired date" }, { "4202", "Accident, consequences of accident" }, { "4203", "Treatment according to \u00a7 116b SGB V" }, { "4204", "Restricted entitlement according to \u00a7 18 Abs. 3a SGB V" }, { "4205", "Assignment" }, { "4207", "(Suspected) diagnosis" }, { "4208", "Findings / medication" }, { "4209", "Assignment/diagnosis/suspicion" }, { "4217", "(N)BSNR: Establishment number of the initiator" }, { "4218", "(N)BSNR: Establishment number of the referring person" }, { "4219", "Referral from other physician" }, { "4220", "Referral to" }, { "4221", "Type of treatment" }, { "4229", "Exceptional medical indication" }, { "4231", "Follow-up exam of a known infection" }, { "4237", "Hospital name" }, { "4241", "LANR (lifetime physician number) of the initiator" }, { "4242", "LANR (lifetime physician number) of the referring person" },
                { "6001", "ICD code" }, { "6003", "Diagnostic confidence" }, { "6004", "Body side localization" }, { "6006", "Diagnosis explanation" }, { "6008", "Statement of facts for a diagnosis derogation" }, { "6206", "Central pharmaceutical number" }, { "6210", "Medication prescribed" }, { "6211", "Medication without prescription" }, { "6214", "Number of packages (factor)" }, { "6218", "Information about intake" }, { "6329", "Content of the file, BASE64 coded" },
                { "6406", "Free of charge" }, { "6407", "Noctu" }, { "6408", "BVG" }, { "6409", "Accident" }, { "6431", "Aut idem" },
                { "8200", "Object identifier" }, { "8201", "End of object" }, { "8202", "End of record" }, { "8310", "Request identifier" }, { "8314", "Request UID" },
                { "8403", "Schedule of fees" }, { "8404", "Subcategory to field 8402" }, { "8405", "Information about patient" }, { "8407", "Gender" }, { "8412", "Test OID" }, { "8413", "Test/device ID" }, { "8425", "Budget-free" }, { "8433", "Time of collection" },
                { "8501", "Urgency" }, { "8504", "Intake of medication at the time of sample collection" }, { "8510", "Pregnancy" }, { "8511", "Gestation length (weeks, days)" }, { "8512", "1st day of cycle" },
                { "8601", "Name of invoice recipient" }, { "8602", "Title, first name of invoice recipient" }, { "8606", "City of residence of the invoice recipient" }, { "8607", "Street of residence of the invoice recipient" }, { "8608", "Comment/reference number" }, { "8609", "Type of billing" }, { "8610", "Private charges" }, { "8615", "Principal" },
                { "9103", "Date of creation" }, { "9152", "Counter URL" }, { "9153", "Description URL" }, { "9154", "URL" } };
        for (String[] f : gdt30) {
            if (FIELDS.containsKey(f[0])) {
                throw new IllegalStateException("GDT 3.0 field " + f[0] + " is already in the GDT 2.1 table");
            }
            FIELDS.put(f[0], f[1]);
        }

        // 6330, 6332, ..., 6398 name an open category and 6331, 6333, ..., 6399 hold its content
        for (int id = 6330; id <= 6398; id += 2) {
            FIELDS.put(String.valueOf(id), "Name of open category");
            FIELDS.put(String.valueOf(id + 1), "Content of open category");
        }

        SET_TYPES.put("6300", "Root data request");
        SET_TYPES.put("6301", "Root data transfer");
        SET_TYPES.put("6302", "New test request");
        SET_TYPES.put("6310", "Test data transfer");
        SET_TYPES.put("6311", "Test data display");
    }

    private GDTFields() {}

    /** Name of a field, or null when it is not in the specification. */
    public static String fieldName(String id) {
        return FIELDS.get(id);
    }

    /** Name of a set type (the content of field 8000), or null. */
    public static String setTypeName(String type) {
        return type == null ? null : SET_TYPES.get(type.trim());
    }
}
