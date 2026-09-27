package io.github.ninbyo02.lami.ui.screens.home;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

public final class StandaloneSentencePieceJni {
    public native long[] countPair(byte[] model, byte[] input, byte[] output);
    public static void main(String[] args) throws Exception {
        System.load(Path.of(args[0]).toAbsolutePath().toString());
        byte[] model;
        if (args[1].endsWith(".litertlm")) {
            Class<?> reader = Class.forName("io.github.ninbyo02.lami.ui.screens.home.LitertLmSentencePieceSection");
            model = (byte[]) reader.getMethod("read", java.io.File.class)
                .invoke(reader.getField("INSTANCE").get(null), Path.of(args[1]).toFile());
        } else {
            model = Files.readAllBytes(Path.of(args[1]));
        }
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(model));
        if (!hash.equals(args[3])) throw new AssertionError("fixture tokenizer mismatch");
        var counter = new StandaloneSentencePieceJni();
        int checked = 0;
        for (String line : Files.readAllLines(Path.of(args[2]), StandardCharsets.UTF_8)) {
            String[] values = line.split("\t", -1);
            byte[] text = Base64.getDecoder().decode(values[0]);
            int expected = Integer.parseInt(values[1]);
            long[] actual = counter.countPair(model, text, text);
            if (actual.length != 4 || actual[0] != expected || actual[1] != expected || actual[2] < 0 || actual[3] < 0)
                throw new AssertionError("count mismatch at case " + checked);
            checked++;
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                counter.countPair(new byte[]{0}, new byte[0], new byte[0]);
                throw new AssertionError("malformed model accepted");
            } catch (IllegalStateException expected) { }
            try {
                counter.countPair(model, new byte[4 * 1024 * 1024 + 1], new byte[0]);
                throw new AssertionError("oversized text accepted");
            } catch (IllegalStateException expected) { }
            long[] afterFailure = counter.countPair(model, new byte[0], new byte[0]);
            if (afterFailure[0] != 0 || afterFailure[1] != 0) throw new AssertionError("reuse after failure");
        }
        System.out.println("JNI_CASES_PASSED=" + checked + "; invalid/oversized/recovery cycles=3");
    }
}
