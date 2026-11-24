"""UI texts: every string in both languages, the same placeholders, and never an em dash."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / "res"
LANGUAGES = ("values", "values-pt")


def read_strings(folder):
    return {e.get("name"): e.text or "" for e in ET.parse(RES / folder / "strings.xml").getroot() if e.tag == "string"}


class StringsTest(unittest.TestCase):
    def test_both_languages_have_the_same_strings(self):
        english, portuguese = (read_strings(f) for f in LANGUAGES)
        self.assertEqual(set(english), set(portuguese))

    def test_placeholders_match(self):
        english, portuguese = (read_strings(f) for f in LANGUAGES)
        for name, text in english.items():
            self.assertEqual(sorted(re.findall(r"%\d\$[sd]", text)), sorted(re.findall(r"%\d\$[sd]", portuguese[name])),
                             name)

    def test_no_em_dash(self):
        for folder in LANGUAGES:
            for name, text in read_strings(folder).items():
                self.assertNotIn("—", text, f"{folder}/{name}")


if __name__ == "__main__":
    unittest.main()
