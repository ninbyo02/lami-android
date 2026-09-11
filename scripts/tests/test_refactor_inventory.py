from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path

MODULE = Path(__file__).resolve().parents[1] / "refactor_inventory.py"
spec = importlib.util.spec_from_file_location("refactor_inventory_under_test", MODULE)
refactor = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(refactor)


class RefactorInventoryTest(unittest.TestCase):
    def test_mask_ignores_comment_and_string_fake_functions(self):
        source = '// fun fake(a: Int) {}\nval x = "fun nope(a: Int) {}"\nfun real() {}\n'
        masked = refactor.mask_kotlin(source)
        self.assertNotIn("fake", masked)
        self.assertNotIn("nope", masked)
        self.assertIn("real", masked)

    def test_parameter_counter_handles_function_types(self):
        params = "state: State, onRun: (Int, String) -> Unit, enabled: Boolean"
        self.assertEqual(refactor.count_top_level_parameters(params), 3)

    def test_scan_selects_unique_small_risk_one_candidate(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "app/src/main/java/example/Sample.kt"
            source.parent.mkdir(parents=True)
            arguments = ",\n".join(f"    p{i}: Int" for i in range(13))
            source.write_text(f"@Composable\nfun Sample(\n{arguments}\n) {{}}\n")
            old_root = refactor.ROOT
            refactor.ROOT = root
            try:
                contract = {
                    "version": 1,
                    "source_roots": ["app/src/main/java"],
                    "exclude_globs": [],
                    "thresholds": {"max_kotlin_file_lines": 3000, "max_function_lines": 320,
                                   "max_composable_parameters": 12, "max_remember_state_per_file": 32,
                                   "max_job_state_per_ui_file": 0},
                    "risk3_path_fragments": [], "risk2_path_fragments": []
                }
                result = refactor.scan(contract, 1)
            finally:
                refactor.ROOT = old_root
            self.assertEqual(result["selected_candidate"]["symbol"], "Sample")
            self.assertEqual(result["selected_candidate"]["value"], 13)


if __name__ == "__main__":
    unittest.main()
