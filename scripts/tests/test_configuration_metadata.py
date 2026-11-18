"""Implementation-contributed mode hints remain additive without duplicating API properties."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("reference_metadata", Path(__file__).parents[1] / "gen-reference.py")
reference = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(reference)


class ConfigurationMetadataTest(unittest.TestCase):
    def metadata(self, root, name, data):
        path = root / name / reference.METADATA
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data))
        return {"dir": name, "path": ":libs:" + name}

    def test_merges_backend_values_while_api_declaration_remains_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            api = self.metadata(root, "api", {"groups": [{"name": "invoice-watcher.fixture"}],
                "properties": [{"name": "invoice-watcher.fixture.mode", "type": "java.lang.String",
                                "description": "Required. Implementation name."}]})
            first = self.metadata(root, "first", {"hints": [{"name": "invoice-watcher.fixture.mode",
                                 "values": [{"value": "first"}]}]})
            future = self.metadata(root, "future", {"hints": [{"name": "invoice-watcher.fixture.mode",
                                  "values": [{"value": "future"}]}]})
            before = (root / "api" / reference.METADATA).read_text()
            with patch.object(reference, "ROOT", root):
                result = reference.configuration_page([api, first, future])
                self.assertEqual(result, reference.configuration_page([future, first, api]))
                self.assertIn("`first` / `future`", result)
                self.assertEqual(1, result.count("## `invoice-watcher.fixture`"))
                self.assertNotIn("`future`", reference.configuration_page([api, first]))
            self.assertEqual(before, (root / "api" / reference.METADATA).read_text())

    def test_rejects_conflicting_declarations(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def declaration(kind):
                return {"groups": [{"name": "invoice-watcher.fixture"}],
                        "properties": [{"name": "invoice-watcher.fixture.mode", "type": kind,
                                        "description": "Required. Mode."}]}
            first = self.metadata(root, "one", declaration("java.lang.String"))
            second = self.metadata(root, "two", declaration("java.lang.Boolean"))
            with patch.object(reference, "ROOT", root):
                with self.assertRaisesRegex(SystemExit, "conflicting metadata.*mode.*type"):
                    reference.configuration_page([first, second])
