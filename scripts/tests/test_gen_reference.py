import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


spec = importlib.util.spec_from_file_location("gen_reference", Path(__file__).parents[1] / "gen-reference.py")
reference = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reference)


class PropertyTypeTest(unittest.TestCase):
    def test_enum_constants_are_rendered_as_configuration_values(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            class_file = root / "module/build/classes/java/main/example/Properties$Mode.class"
            class_file.parent.mkdir(parents=True)
            class_file.touch()
            output = ("public final class example.Properties$Mode extends java.lang.Enum<example.Properties$Mode> {\n"
                      "  public static final example.Properties$Mode FIRST_VALUE;\n"
                      "  public static final example.Properties$Mode SECOND;\n}\n")
            with patch.object(reference, "ROOT", root), patch.object(reference.subprocess, "run") as run:
                run.return_value.stdout = output
                self.assertEqual("first-value / second", reference.property_type("example.Properties$Mode", "module"))

    def test_other_types_keep_short_type(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(reference, "ROOT", Path(directory)):
            self.assertEqual("List<String>", reference.property_type("java.util.List<java.lang.String>", "module"))
            self.assertEqual("", reference.property_type(None, "module"))


if __name__ == "__main__":
    unittest.main()
