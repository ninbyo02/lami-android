import unittest
import json
import tempfile
from pathlib import Path
from host_cpu_reference_cases import PROMPTS, EXPECTED, score, load_case_set


class CpuReferenceCaseTest(unittest.TestCase):
    def result(self):
        return {"scenarioId": "fresh", "backend": "HOST_CPU_REFERENCE", "status": "success", "turns": [
            {"index": i, "prompt": p, "status": "success", "sanitizedOutput": answer}
            for i, (p, answer) in enumerate(zip(PROMPTS, EXPECTED), 1)]}

    def test_case_set_validation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "cases.json"
            for value in [{}, {"turns": []}, {"turns": [None]}, {"turns": [{"prompt": "x", "expected": " "}]}]:
                path.write_text(json.dumps(value))
                with self.assertRaises(ValueError): load_case_set(path)
            path.write_text(json.dumps({"turns": [{"prompt":"x", "expected":"y"}]*7}))
            with self.assertRaises(ValueError): load_case_set(path)
            path.write_text(json.dumps({"turns": [{"prompt":"姓は？", "expected":"佐藤"}]}))
            self.assertEqual(load_case_set(path), (["姓は？"], ["佐藤"]))

    def test_custom_case_set_is_not_scored_as_default_math(self):
        run = {"scenarioId":"fresh", "backend":"HOST_CPU_REFERENCE", "status":"success", "turns":[
            {"index":1, "prompt":"型番は？", "status":"success", "sanitizedOutput":"Z1"}]}
        self.assertEqual(score(run, "fresh", "HOST_CPU_REFERENCE", ["型番は？"], ["Z31"])["quality"], "failed")
        run["turns"][0]["sanitizedOutput"] = "Ｚ３１。"
        self.assertEqual(score(run, "fresh", "HOST_CPU_REFERENCE", ["型番は？"], ["Z31"])["quality"], "passed")

    def test_correct_answers(self):
        self.assertEqual(score(self.result(), "fresh", "HOST_CPU_REFERENCE")["quality"], "passed")

    def test_stale_or_wrong_backend(self):
        for field, value in [("scenarioId", "stale"), ("backend", "GPU")]:
            run = self.result(); run[field] = value
            self.assertEqual(score(run, "fresh", "HOST_CPU_REFERENCE")["execution"], "failed")

    def test_changed_or_reordered_prompt(self):
        for change in ["prompt", "index"]:
            run = self.result(); run["turns"][0][change] = "wrong"
            self.assertEqual(score(run, "fresh", "HOST_CPU_REFERENCE")["execution"], "failed")

    def test_missing_invalid_or_extra_turn(self):
        for turns in [[], [None, None], self.result()["turns"]*2]:
            run = self.result(); run["turns"] = turns
            self.assertEqual(score(run, "fresh", "HOST_CPU_REFERENCE")["execution"], "failed")

    def test_execution_and_quality_are_separate(self):
        run = self.result(); run["turns"][0]["sanitizedOutput"] = "142"
        result = score(run, "fresh", "HOST_CPU_REFERENCE")
        self.assertEqual(result["execution"], "passed")
        self.assertEqual(result["quality"], "failed")

    def test_missing_model_is_not_inference_or_quality_pass(self):
        run = self.result(); run.update(status="failure", reason="model_resolution_failed:model_file_not_found", turns=[])
        result = score(run, "fresh", "HOST_CPU_REFERENCE")
        self.assertEqual(result["execution"], "failed")
        self.assertEqual(result["quality"], "not_evaluated")

    def test_blank_and_failed_turn(self):
        for status, text in [("failure", "42"), ("success", " ")]:
            run = self.result(); run["turns"][0].update(status=status, sanitizedOutput=text)
            self.assertEqual(score(run, "fresh", "HOST_CPU_REFERENCE")["execution"], "failed")


if __name__ == "__main__":
    unittest.main()
