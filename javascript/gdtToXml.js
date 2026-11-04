/**
 * Reads GDT 2.1 text into the XML of the GDT data type, in plain JavaScript (E4X), for channels
 * that cannot install the plugin. Use it in a transformer whose inbound data type is Raw:
 *
 *     msg = gdtToXml(msg);
 *     msg = gdtToXml(msg, { groupResults: true, groupCategories: true, joinResultsText: false });
 *
 * The result is the plugin's XML with Field Names off: <GDT><set type="6310"><F8000>..</F8000>...</set></GDT>.
 * Options (each defaults to true; the plugin's settings default to off):
 *   groupResults:    a field 8410 and the 8411, 8420 and 8421 that follow it in one <result>,
 *                    and a run of them in one <results> (Group Result Fields)
 *   groupCategories: an open category (name field 6330, 6332, ... 6398 followed by its content field)
 *                    as <category name="...">content</category>, a run of them in one <categories> (Group Categories)
 *   joinResultsText: several field 8480 in a row joined into one (Join Results Text)
 *
 * Like the plugin (not strict): a line that does not start with seven digits continues the value of the
 * field before it (joined with a line break), wrong line lengths are ignored, and fields before the first
 * 8000 form a set without a type. A line of 998 characters or more is read with a four digit length;
 * the plugin decides that with its field table, so only a long line with a wrong length can differ.
 * Reading only: there is no XML to GDT, no strict mode and no field names.
 */
function gdtToXml(text, options) {
	options = options || {};
	var groupResults = options.groupResults !== false;
	var groupCategories = options.groupCategories !== false;
	var joinResultsText = options.joinResultsText !== false;

	// Characters XML cannot hold (control characters apart from tab and line break) are left out
	function clean(value) {
		var s = String(value), out = '';
		for (var i = 0; i < s.length; i++) {
			var c = s.charCodeAt(i);
			if (c == 9 || c == 10 || (c >= 32 && c != 0xFFFE && c != 0xFFFF)) {
				out += s.charAt(i);
			}
		}
		return out;
	}
	function looksLikeField(line) {
		return /^\d{7}/.test(line);
	}

	// 1. Lines -> sets of [id, value] fields
	text = String(text);
	if (text.charCodeAt(0) == 0xFEFF) { // byte order mark
		text = text.substring(1);
	}
	var lines = text.split(/\r\n|\r|\n/);
	var sets = [];
	var current = null;
	for (var i = 0; i < lines.length; i++) {
		var line = lines[i];
		if (line == '\u001A') { // an old DOS end-of-file mark
			continue;
		}
		if (line === '') {
			// A blank line is part of a value when text follows that is not a new field
			var next = i;
			while (next < lines.length && lines[next] === '') {
				next++;
			}
			if (current != null && next < lines.length && !looksLikeField(lines[next]) && lines[next] != '\u001A') {
				current.fields[current.fields.length - 1][1] += '\n';
			}
			continue;
		}
		if (!looksLikeField(line)) {
			if (current == null) {
				throw 'gdtToXml: line ' + (i + 1) + ' is not a GDT line: ' + line.substring(0, 40);
			}
			current.fields[current.fields.length - 1][1] += '\n' + line;
			continue;
		}
		var id = line.substring(3, 7);
		var value = line.substring(7);
		if (line.length + 2 > 999 && /^\d{8}/.test(line) && Number(line.substring(0, 4)) >= 1000) {
			id = line.substring(4, 8); // four digit length
			value = line.substring(8);
		}
		if (id == '8000' || current == null) {
			current = { type: id == '8000' ? value : null, fields: [] };
			sets.push(current);
		}
		current.fields.push([id, value]);
	}

	// 2. Sets -> XML. appendChild adds a copy in Rhino, so continue with the live child it returns
	function add(parent, child) {
		parent.appendChild(child);
		var children = parent.elements();
		return children[children.length() - 1];
	}
	function field(id, value) {
		var element = <field/>;
		element.setName('F' + id);
		element.appendChild(clean(value));
		return element;
	}

	var gdt = <GDT/>;
	for (var s = 0; s < sets.length; s++) {
		var set = add(gdt, <set/>);
		if (sets[s].type != null) {
			set.@type = clean(String(sets[s].type).trim());
		}

		var fields = sets[s].fields;
		if (joinResultsText) {
			var joined = [];
			for (var f = 0; f < fields.length; f++) {
				var last = joined[joined.length - 1];
				if (last && last[0] == '8480' && fields[f][0] == '8480') {
					last[1] += fields[f][1];
				} else {
					joined.push([fields[f][0], fields[f][1]]);
				}
			}
			fields = joined;
		}

		var results = null, result = null, categories = null, category = null, awaitingContentId = null;
		for (var f = 0; f < fields.length; f++) {
			var id = fields[f][0], value = fields[f][1], n = Number(id);
			var isCategoryContent = groupCategories && id == awaitingContentId;
			var isCategoryName = groupCategories && n >= 6330 && n <= 6398 && n % 2 == 0;
			awaitingContentId = null;
			if (!isCategoryContent && !isCategoryName) {
				categories = null;
			}

			if (groupResults) {
				var startsResult = id == '8410';
				var continuesResult = result != null && (id == '8411' || id == '8420' || id == '8421');
				if (!startsResult && !continuesResult) {
					result = null;
					results = null;
				}
				if (startsResult) {
					if (results == null) {
						results = add(set, <results/>);
					}
					result = add(results, <result/>);
				}
			}

			if (isCategoryName) {
				if (categories == null) {
					categories = add(set, <categories/>);
				}
				category = add(categories, <category name={clean(value)}/>);
				awaitingContentId = String(n + 1);
			} else if (isCategoryContent) {
				category.appendChild(clean(value));
			} else {
				add(result != null ? result : set, field(id, value));
			}
		}
	}
	return gdt;
}
