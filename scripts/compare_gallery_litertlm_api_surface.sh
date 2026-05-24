#!/usr/bin/env bash
set -u

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="${1:-/tmp/lami-gallery-apks/ai-edge-gallery-sm8750.apk}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="${OUT_DIR:-artifacts/litertlm_api_surface_compare/$TIMESTAMP}"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle/caches/modules-2/files-2.1}"

TARGET_CLASSES=(
  "com.google.ai.edge.litertlm.Engine"
  "com.google.ai.edge.litertlm.EngineConfig"
  "com.google.ai.edge.litertlm.Backend"
  "com.google.ai.edge.litertlm.Backend\$NPU"
  "com.google.ai.edge.litertlm.Backend\$CPU"
  "com.google.ai.edge.litertlm.Backend\$GPU"
  "com.google.ai.edge.litertlm.LiteRtLmJni"
)

cd "$ROOT_DIR" || exit 1
mkdir -p "$OUT_DIR"

log() {
  printf '[litertlm-api-surface] %s\n' "$*"
}

find_aar() {
  local version="$1"
  find "$GRADLE_CACHE/com.google.ai.edge.litertlm/litertlm-android/$version" \
    -name "litertlm-android-$version.aar" -type f 2>/dev/null | head -n 1
}

extract_aar() {
  local version="$1"
  local aar="$2"
  local out="$OUT_DIR/aar_$version"
  mkdir -p "$out"
  if [ -f "$aar" ]; then
    unzip -q -o "$aar" -d "$out" 2>/dev/null || true
  fi
  printf '%s\n' "$out"
}

javap_one() {
  local jar="$1"
  local class_name="$2"
  local out="$3"
  if [ -f "$jar" ] && command -v javap >/dev/null 2>&1; then
    javap -classpath "$jar" -p -s "$class_name" >"$out" 2>&1 || true
  else
    printf 'javap unavailable or classes.jar missing\n' >"$out"
  fi
}

javap_code_one() {
  local jar="$1"
  local class_name="$2"
  local out="$3"
  if [ -f "$jar" ] && command -v javap >/dev/null 2>&1; then
    javap -classpath "$jar" -p -s -c "$class_name" >"$out" 2>&1 || true
  else
    printf 'javap unavailable or classes.jar missing\n' >"$out"
  fi
}

sanitize_class_name() {
  printf '%s' "$1" | sed 's/[.$]/_/g'
}

if [ ! -f "$APK_PATH" ]; then
  log "missing APK: $APK_PATH"
  printf 'missing APK: %s\n' "$APK_PATH" >"$OUT_DIR/error.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

if ! command -v python3 >/dev/null 2>&1; then
  log "python3 not found; cannot parse dex"
  printf 'python3 not found\n' >"$OUT_DIR/error.txt"
  printf '%s\n' "$OUT_DIR"
  exit 0
fi

log "apk=$APK_PATH"
log "out=$OUT_DIR"

{
  printf 'apk=%s\n' "$APK_PATH"
  if command -v sha256sum >/dev/null 2>&1; then
    printf 'apk_sha256=%s\n' "$(sha256sum "$APK_PATH" | awk '{print $1}')"
  fi
  if command -v aapt >/dev/null 2>&1; then
    aapt dump badging "$APK_PATH" 2>/dev/null | head -n 20
  elif [ -x "$HOME/Android/Sdk/build-tools/36.1.0/aapt" ]; then
    "$HOME/Android/Sdk/build-tools/36.1.0/aapt" dump badging "$APK_PATH" 2>/dev/null | head -n 20
  fi
} >"$OUT_DIR/apk_metadata.txt"

mkdir -p "$OUT_DIR/gallery_dex"
unzip -q -o "$APK_PATH" 'classes*.dex' -d "$OUT_DIR/gallery_dex" 2>/dev/null || true

python3 - "$OUT_DIR" "$OUT_DIR/gallery_dex" <<'PY'
import os
import struct
import sys

out_dir = sys.argv[1]
dex_dir = sys.argv[2]

target_prefix = "Lcom/google/ai/edge/litertlm/"
target_exact = {
    "Lcom/google/ai/edge/litertlm/Engine;",
    "Lcom/google/ai/edge/litertlm/EngineConfig;",
    "Lcom/google/ai/edge/litertlm/Backend;",
    "Lcom/google/ai/edge/litertlm/Backend$NPU;",
    "Lcom/google/ai/edge/litertlm/Backend$CPU;",
    "Lcom/google/ai/edge/litertlm/Backend$GPU;",
    "Lcom/google/ai/edge/litertlm/LiteRtLmJni;",
}

def uleb(data, off):
    result = 0
    shift = 0
    start = off
    while True:
        b = data[off]
        off += 1
        result |= (b & 0x7f) << shift
        if (b & 0x80) == 0:
            break
        shift += 7
    return result, off, off - start

def u16(data, off):
    return struct.unpack_from("<H", data, off)[0]

def u32(data, off):
    return struct.unpack_from("<I", data, off)[0]

def read_string(data, off):
    _, off, _ = uleb(data, off)
    end = data.find(b"\x00", off)
    if end < 0:
        end = len(data)
    return data[off:end].decode("utf-8", "replace")

def access_flags(v):
    flags = [
        (0x1, "public"),
        (0x2, "private"),
        (0x4, "protected"),
        (0x8, "static"),
        (0x10, "final"),
        (0x20, "synchronized"),
        (0x40, "volatile"),
        (0x80, "transient"),
        (0x100, "native"),
        (0x200, "interface"),
        (0x400, "abstract"),
        (0x800, "strict"),
        (0x1000, "synthetic"),
        (0x4000, "enum"),
        (0x10000, "constructor"),
        (0x20000, "declared_synchronized"),
    ]
    names = [name for bit, name in flags if v & bit]
    return "|".join(names) if names else "-"

def parse_dex(path):
    data = open(path, "rb").read()
    if not data.startswith(b"dex\n"):
        return []
    string_ids_size = u32(data, 56)
    string_ids_off = u32(data, 60)
    type_ids_size = u32(data, 64)
    type_ids_off = u32(data, 68)
    proto_ids_size = u32(data, 72)
    proto_ids_off = u32(data, 76)
    field_ids_size = u32(data, 80)
    field_ids_off = u32(data, 84)
    method_ids_size = u32(data, 88)
    method_ids_off = u32(data, 92)
    class_defs_size = u32(data, 96)
    class_defs_off = u32(data, 100)

    string_offsets = [u32(data, string_ids_off + i * 4) for i in range(string_ids_size)]
    strings = [read_string(data, off) for off in string_offsets]
    type_ids = [u32(data, type_ids_off + i * 4) for i in range(type_ids_size)]

    def type_desc(idx):
        if idx == 0xFFFFFFFF:
            return "-"
        return strings[type_ids[idx]]

    proto_ids = []
    for i in range(proto_ids_size):
        off = proto_ids_off + i * 12
        shorty_idx = u32(data, off)
        return_type_idx = u32(data, off + 4)
        parameters_off = u32(data, off + 8)
        params = []
        if parameters_off:
            size = u32(data, parameters_off)
            params = [type_desc(u16(data, parameters_off + 4 + j * 2)) for j in range(size)]
        proto_ids.append((strings[shorty_idx], type_desc(return_type_idx), params))

    def proto_desc(idx):
        _, ret, params = proto_ids[idx]
        return "(" + "".join(params) + ")" + ret

    field_ids = []
    for i in range(field_ids_size):
        off = field_ids_off + i * 8
        class_idx = u16(data, off)
        type_idx = u16(data, off + 2)
        name_idx = u32(data, off + 4)
        field_ids.append((type_desc(class_idx), type_desc(type_idx), strings[name_idx]))

    method_ids = []
    for i in range(method_ids_size):
        off = method_ids_off + i * 8
        class_idx = u16(data, off)
        proto_idx = u16(data, off + 2)
        name_idx = u32(data, off + 4)
        method_ids.append((type_desc(class_idx), strings[name_idx], proto_desc(proto_idx)))

    rows = []
    for i in range(class_defs_size):
        off = class_defs_off + i * 32
        class_idx = u32(data, off)
        class_access = u32(data, off + 4)
        superclass_idx = u32(data, off + 8)
        class_data_off = u32(data, off + 24)
        class_desc = type_desc(class_idx)
        if not class_desc.startswith(target_prefix):
            continue
        rows.append(("class", class_desc, access_flags(class_access), "-", type_desc(superclass_idx), "-", os.path.basename(path)))
        if not class_data_off:
            continue
        cur = class_data_off
        static_fields_size, cur, _ = uleb(data, cur)
        instance_fields_size, cur, _ = uleb(data, cur)
        direct_methods_size, cur, _ = uleb(data, cur)
        virtual_methods_size, cur, _ = uleb(data, cur)
        for field_count in (static_fields_size, instance_fields_size):
            field_idx = 0
            for _ in range(field_count):
                diff, cur, _ = uleb(data, cur)
                access, cur, _ = uleb(data, cur)
                field_idx += diff
                if field_idx < len(field_ids):
                    owner, typ, name = field_ids[field_idx]
                    rows.append(("field", class_desc, access_flags(access), name, typ, owner, os.path.basename(path)))
        for method_count in (direct_methods_size, virtual_methods_size):
            method_idx = 0
            for _ in range(method_count):
                diff, cur, _ = uleb(data, cur)
                access, cur, _ = uleb(data, cur)
                code_off, cur, _ = uleb(data, cur)
                method_idx += diff
                if method_idx < len(method_ids):
                    owner, name, proto = method_ids[method_idx]
                    rows.append(("method", class_desc, access_flags(access), name, proto, "code_off=%s" % code_off, os.path.basename(path)))
    return rows

all_rows = []
for name in sorted(os.listdir(dex_dir)):
    if name.endswith(".dex"):
        all_rows.extend(parse_dex(os.path.join(dex_dir, name)))

surface = os.path.join(out_dir, "gallery_dex_litertlm_surface.tsv")
with open(surface, "w", encoding="utf-8") as f:
    f.write("kind\tclass\taccess\tname\tdescriptor\textra\tdex\n")
    for row in all_rows:
        f.write("\t".join(row) + "\n")

classes = sorted({row[1] for row in all_rows if row[0] == "class"})
with open(os.path.join(out_dir, "gallery_litertlm_classes.txt"), "w", encoding="utf-8") as f:
    for cls in classes:
        f.write(cls + "\n")

for cls in sorted(target_exact):
    safe = cls[1:-1].replace("/", ".").replace("$", "_")
    out = os.path.join(out_dir, "gallery_%s.txt" % safe)
    with open(out, "w", encoding="utf-8") as f:
        f.write("kind\taccess\tname\tdescriptor\textra\tdex\n")
        for row in all_rows:
            if row[1] == cls:
                f.write("\t".join((row[0], row[2], row[3], row[4], row[5], row[6])) + "\n")

with open(os.path.join(out_dir, "gallery_native_create_engine.txt"), "w", encoding="utf-8") as f:
    for row in all_rows:
        if row[1] == "Lcom/google/ai/edge/litertlm/LiteRtLmJni;" and row[3] == "nativeCreateEngine":
            f.write("\t".join(row) + "\n")
PY

for version in 0.10.0 0.11.0; do
  aar="$(find_aar "$version")"
  aar_out="$(extract_aar "$version" "$aar")"
  jar="$aar_out/classes.jar"
  {
    printf 'version=%s\n' "$version"
    printf 'aar=%s\n' "${aar:-missing}"
    printf 'classes_jar=%s\n' "$jar"
    printf '\n[litertlm classes]\n'
    if [ -f "$jar" ]; then
      jar tf "$jar" | grep '^com/google/ai/edge/litertlm/.*\.class$' | sort || true
    else
      printf '<missing>\n'
    fi
  } >"$OUT_DIR/aar_${version}_class_list.txt"

  for class_name in "${TARGET_CLASSES[@]}"; do
    safe="$(sanitize_class_name "$class_name")"
    javap_one "$jar" "$class_name" "$OUT_DIR/aar_${version}_${safe}_javap.txt"
    if [ "$class_name" = "com.google.ai.edge.litertlm.Engine" ] || [ "$class_name" = "com.google.ai.edge.litertlm.LiteRtLmJni" ]; then
      javap_code_one "$jar" "$class_name" "$OUT_DIR/aar_${version}_${safe}_javap_code.txt"
    fi
  done
done

{
  printf '# LiteRT-LM API surface comparison\n\n'
  printf 'artifact_dir=%s\n' "$OUT_DIR"
  printf 'gallery_apk=%s\n' "$APK_PATH"
  printf '\n## Gallery LiteRtLmJni.nativeCreateEngine\n\n'
  cat "$OUT_DIR/gallery_native_create_engine.txt" 2>/dev/null || true
  for version in 0.10.0 0.11.0; do
    printf '\n## Maven %s LiteRtLmJni.nativeCreateEngine\n\n' "$version"
    file="$OUT_DIR/aar_${version}_com_google_ai_edge_litertlm_LiteRtLmJni_javap.txt"
    if [ -f "$file" ]; then
      awk '
        /nativeCreateEngine/ { print; want=1; next }
        want && /descriptor:/ { print; want=0; next }
      ' "$file"
    fi
  done
  printf '\n## Gallery EngineConfig constructors\n\n'
  awk -F '\t' '$1 == "method" && $3 == "<init>" { print }' "$OUT_DIR/gallery_com.google.ai.edge.litertlm.EngineConfig.txt" 2>/dev/null || true
  for version in 0.10.0 0.11.0; do
    printf '\n## Maven %s EngineConfig constructors\n\n' "$version"
    file="$OUT_DIR/aar_${version}_com_google_ai_edge_litertlm_EngineConfig_javap.txt"
    grep -A1 'EngineConfig(' "$file" 2>/dev/null || true
  done
  printf '\n## Gallery Engine initialize\n\n'
  awk -F '\t' '$1 == "method" && $3 == "initialize" { print }' "$OUT_DIR/gallery_com.google.ai.edge.litertlm.Engine.txt" 2>/dev/null || true
  for version in 0.10.0 0.11.0; do
    printf '\n## Maven %s Engine initialize\n\n' "$version"
    file="$OUT_DIR/aar_${version}_com_google_ai_edge_litertlm_Engine_javap.txt"
    grep -A1 'initialize' "$file" 2>/dev/null || true
  done
  printf '\n## Gallery Backend.NPU\n\n'
  cat "$OUT_DIR/gallery_com.google.ai.edge.litertlm.Backend_NPU.txt" 2>/dev/null || true
  for version in 0.10.0 0.11.0; do
    printf '\n## Maven %s Backend.NPU\n\n' "$version"
    cat "$OUT_DIR/aar_${version}_com_google_ai_edge_litertlm_Backend_NPU_javap.txt" 2>/dev/null || true
  done
} >"$OUT_DIR/summary.txt"

log "wrote $OUT_DIR"
printf '%s\n' "$OUT_DIR"

exit 0
