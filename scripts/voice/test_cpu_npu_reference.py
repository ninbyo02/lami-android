import unittest
from check_cpu_npu_reference import PROMPTS, EXPECTED, score


class ComparisonTest(unittest.TestCase):
    def result(self):
        return {"scenarioId": "fresh", "backend": "NPU", "status": "success", "turns": [
            {"index": i, "prompt": p, "status": "success", "sanitizedOutput": answer}
            for i, (p, answer) in enumerate(zip(PROMPTS, EXPECTED), 1)]}

    def test_correct_answers(self):
        self.assertEqual(score(self.result(), "fresh", "NPU")["quality"], "passed")

    def test_stale_or_wrong_backend(self):
        for field, value in [("scenarioId", "stale"), ("backend", "GPU")]:
            run = self.result(); run[field] = value
            self.assertEqual(score(run, "fresh", "NPU")["execution"], "failed")

    def test_changed_or_reordered_prompt(self):
        for change in ["prompt", "index"]:
            run = self.result(); run["turns"][0][change] = "wrong"
            self.assertEqual(score(run, "fresh", "NPU")["execution"], "failed")

    def test_missing_invalid_or_extra_turn(self):
        for turns in [[], [None, None], self.result()["turns"]*2]:
            run = self.result(); run["turns"] = turns
            self.assertEqual(score(run, "fresh", "NPU")["execution"], "failed")

    def test_execution_and_quality_are_separate(self):
        run = self.result(); run["turns"][0]["sanitizedOutput"] = "142"
        result = score(run, "fresh", "NPU")
        self.assertEqual(result["execution"], "passed")
        self.assertEqual(result["quality"], "failed")

    def test_missing_model_is_not_inference_or_quality_pass(self):
        run = self.result(); run.update(status="failure", reason="model_resolution_failed:model_file_not_found", turns=[])
        result = score(run, "fresh", "NPU")
        self.assertEqual(result["execution"], "failed")
        self.assertEqual(result["quality"], "not_evaluated")

    def test_blank_and_failed_turn(self):
        for status, text in [("failure", "42"), ("success", " ")]:
            run = self.result(); run["turns"][0].update(status=status, sanitizedOutput=text)
            self.assertEqual(score(run, "fresh", "NPU")["execution"], "failed")


if __name__ == "__main__":
    unittest.main()
