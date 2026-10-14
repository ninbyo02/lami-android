import copy
import unittest
from check_resident_cp_aot_contract import validate

def graph():
    ins = [
        ("serving_default_embeddings", [1, 1, 1024]),
        ("serving_default_input_ids", [1]),
        ("serving_default_mask", [1, 1, 1, 32]),
    ]
    outs = [("serving_default_hidden_output", [1, 1024])]
    for i in range(5):
        for kind in ("k", "v"):
            base = f"serving_default_kv_cache_{kind}_{i}"
            ins.append((base, [1, 32, 8, 128]))
            outs.append((base+"_output", [1, 32, 8, 128]))
    def tensors(entries):
        return [{"info": {"name": name, "dimensions": shape}} for name, shape in entries]
    return {"info": {"graphs": [{"info": {"graphInputs": tensors(ins), "graphOutputs": tensors(outs)}}]}}

class ContractTest(unittest.TestCase):
    def test_matching(self):
        self.assertEqual(validate(graph()), [])
    def test_old_logits_model_rejected(self):
        model = graph()
        model["info"]["graphs"][0]["info"]["graphOutputs"][0]["info"] = {
            "name": "serving_default_logits_output", "dimensions": [15, 2048]}
        self.assertTrue(any("hidden_output" in issue for issue in validate(model)))
    def test_wrong_kv_shape_rejected(self):
        model = graph()
        model["info"]["graphs"][0]["info"]["graphInputs"][3]["info"]["dimensions"] = [1, 16, 8, 128]
        self.assertTrue(validate(model))

if __name__ == "__main__":
    unittest.main()
