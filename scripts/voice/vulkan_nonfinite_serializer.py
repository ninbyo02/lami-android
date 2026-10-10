"""Opt-in export workaround for FlatBuffers union JSON non-finite float parsing.

Only changes the JSON spelling; the schema still stores IEEE double scalars.
No installed ExecuTorch files are modified.
"""
import importlib.resources as resources
import json
import math
import tempfile
from pathlib import Path


def install_quoted_nonfinite_serializer():
    import executorch.backends.vulkan.serialization as package
    import executorch.backends.vulkan.serialization.vulkan_graph_serialize as serializer
    from executorch.exir._serialize._dataclass import _DataclassEncoder
    from executorch.exir._serialize._flatbuffer import _flatc_compile

    def quoted(value):
        if isinstance(value, float) and not math.isfinite(value):
            return "nan" if math.isnan(value) else ("-inf" if value < 0 else "inf")
        if isinstance(value, dict):
            return {key: quoted(item) for key, item in value.items()}
        if isinstance(value, list):
            return [quoted(item) for item in value]
        return value

    def convert(graph):
        data = quoted(json.loads(json.dumps(graph, cls=_DataclassEncoder)))
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            schema = root / "schema.fbs"
            schema.write_bytes(resources.files(package).joinpath("schema.fbs").read_bytes())
            source = root / "schema.json"
            source.write_text(json.dumps(data, allow_nan=False))
            _flatc_compile(directory, str(schema), str(source))
            return (root / "schema.bin").read_bytes()

    serializer.convert_to_flatbuffer = convert
