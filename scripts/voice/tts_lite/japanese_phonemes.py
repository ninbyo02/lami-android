"""Preserve long vowels while reusing the pinned Melo Japanese phonemizer.

The upstream rule for ー is ':' without leading whitespace. Its split(' ')[1:]
drops that rule's output entirely. Feed a literal colon through the same converter,
then resolve each marker to the preceding vowel, including across token groups.
"""
from threading import RLock
from unittest.mock import patch

LEGACY = "melo_legacy"
LONG_VOWELS = "melo_long_vowels_v1"
PROFILES = (LEGACY, LONG_VOWELS)
_LOCK = RLock()


def resolve_long_markers(phones):
    result = []
    for phone in phones:
        if phone == ":":
            previous = result[-1].rstrip(":") if result else ""
            if previous not in ("a", "i", "u", "e", "o", "N"):
                raise ValueError("A Japanese long mark must follow a vowel or nasal")
            phone = previous
        result.append(phone)
    return result


def phonemize(frontend, normalized_text, profile=LEGACY):
    if profile not in PROFILES:
        raise ValueError(f"Unsupported Japanese phoneme profile: {profile}")
    with _LOCK:
        if profile == LEGACY:
            return frontend.g2p(normalized_text)
        original = frontend.kata2phoneme
        # A literal colon survives the original converter's unknown-character
        # branch. Its slot is replaced with a valid JP vowel before ID conversion.
        with patch.object(frontend, "kata2phoneme", side_effect=lambda text: original(text.replace("ー", ":"))):
            phones, tones, word2ph = frontend.g2p(normalized_text)
        phones = resolve_long_markers(phones)
    if len(phones) != len(tones) or sum(word2ph) != len(phones):
        raise ValueError("Japanese phone/token alignment mismatch")
    return phones, tones, word2ph
