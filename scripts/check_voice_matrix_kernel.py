#!/usr/bin/env python3
"""Compile the production arithmetic loop; check bit parity and time head shapes."""
import pathlib
import subprocess
import tempfile

root = pathlib.Path(__file__).resolve().parents[1]
source = (root / "app/src/debug/cpp/lami_voice_matrix.cpp").read_text()
begin = source.index("    // Interleave independent rows")
end = source.index("    env->ReleaseFloatArrayElements(hidden", begin)
kernel = source[begin:end]
program = r"""
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <random>
#include <vector>
void optimized(const float* weights, const float* input, float* scores, int rows) {
KERNEL
}
void reference(const float* weights, const float* input, float* scores, int rows) {
    for (int r = 0; r < rows; ++r) {
        float s = 0;
        for (int j = 0; j < 1024; ++j) s += weights[r * 1024 + j] * input[j];
        scores[r] = s;
    }
}
using Fn = void (*)(const float*, const float*, float*, int);
double bench(Fn fn, const float* w, const float* x, float* y, int rows) {
    auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < 100; ++i) fn(w, x, y, rows);
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now()-start).count()/100;
}
int main() {
    std::mt19937 rng(42);
    std::uniform_real_distribution<float> dist(-1, 1);
    for (int rows : {1, 3, 4, 5, 7, 8, 2048, 3072}) {
        std::vector<float> w(rows*1024), x(1024), a(rows), b(rows);
        for (int trial = 0; trial < 8; ++trial) {
            for (auto& v : w) v = dist(rng);
            for (auto& v : x) v = dist(rng);
            reference(w.data(), x.data(), a.data(), rows);
            optimized(w.data(), x.data(), b.data(), rows);
            if (std::memcmp(a.data(), b.data(), rows*sizeof(float))) return 1;
        }
        if (rows >= 2048) {
            std::vector<double> oldMs, newMs;
            for (int trial = 0; trial < 7; ++trial) {
                if (trial % 2) {
                    newMs.push_back(bench(optimized,w.data(),x.data(),b.data(),rows));
                    oldMs.push_back(bench(reference,w.data(),x.data(),a.data(),rows));
                } else {
                    oldMs.push_back(bench(reference,w.data(),x.data(),a.data(),rows));
                    newMs.push_back(bench(optimized,w.data(),x.data(),b.data(),rows));
                }
            }
            std::sort(oldMs.begin(),oldMs.end()); std::sort(newMs.begin(),newMs.end());
            std::printf("rows=%d reference_ms=%.4f optimized_ms=%.4f speedup=%.2fx\n",
                        rows,oldMs[3],newMs[3],oldMs[3]/newMs[3]);
        }
    }
    std::puts("PASS: exact float bits for all shapes and seeds");
}
""".replace("KERNEL", kernel)
with tempfile.TemporaryDirectory() as tmp:
    path = pathlib.Path(tmp)
    (path / "check.cpp").write_text(program)
    subprocess.run(["c++", "-std=c++17", "-O3", "-ffp-contract=off",
                    "-fno-fast-math", str(path / "check.cpp"), "-o", str(path / "check")], check=True)
    subprocess.run([str(path / "check")], check=True)
