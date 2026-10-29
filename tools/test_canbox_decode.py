import unittest

from canbox_decode import (Census, FrameParser, describe, hiworld_checksum, raise_checksum)

# Real captured Raise frames (forum / XDA dumps). Checksum = ((type+len+sum(data))&FF)^FF.
HANGUP = bytes.fromhex("2E 20 02 0A 02 D1")
XDA_21 = bytes.fromhex("2E 21 05 00 10 00 00 00 C9")
XDA_22 = bytes.fromhex("2E 22 04 01 01 01 01 D5")
XDA_23 = bytes.fromhex("2E 23 04 01 01 01 01 D4")


def raise_frame(t, data):
    body = bytes([t, len(data)]) + bytes(data)
    return bytes([0x2E]) + body + bytes([raise_checksum(body)])


def hiworld_frame(t, data):  # synthetic, built from the smartgauges formula (not a real capture)
    body = bytes([len(data), t]) + bytes(data)
    return bytes([0x5A, 0xA5]) + body + bytes([hiworld_checksum(body)])


class RaiseTests(unittest.TestCase):
    def test_checksum_matches_real_captures(self):
        for raw in (HANGUP, XDA_21, XDA_22, XDA_23):
            self.assertEqual(raise_checksum(raw[1:-1]), raw[-1])

    def test_parses_real_frames_back_to_back(self):
        frames = FrameParser("raise").feed(HANGUP + XDA_21 + XDA_22 + XDA_23)
        self.assertEqual([f.type for f in frames], [0x20, 0x21, 0x22, 0x23])
        self.assertEqual(frames[0].data, bytes([0x0A, 0x02]))

    def test_byte_at_a_time(self):
        p = FrameParser("raise")
        out = []
        for b in HANGUP + XDA_22:
            out += p.feed(bytes([b]))
        self.assertEqual(len(out), 2)

    def test_resync_after_garbage_and_bad_checksum(self):
        corrupt = bytearray(XDA_22)
        corrupt[-1] ^= 0x01
        p = FrameParser("raise")
        frames = p.feed(b"\x00\xff\x13" + bytes(corrupt) + XDA_23)
        self.assertEqual([f.type for f in frames], [0x23])
        self.assertGreaterEqual(p.bad, 1)

    def test_huge_length_byte_does_not_stall(self):
        p = FrameParser("raise")
        frames = p.feed(bytes([0x2E, 0x41, 0xFF]) + HANGUP)
        self.assertEqual([f.type for f in frames], [0x20])

    def test_ack_bytes_between_frames_are_skipped(self):
        frames = FrameParser("raise").feed(HANGUP + b"\xff" + XDA_22)
        self.assertEqual(len(frames), 2)

    def test_describe_swc_and_vw_vehicle_info(self):
        self.assertIn("key=0x0A", describe(FrameParser("raise").feed(HANGUP)[0]))
        info = raise_frame(0x41, [0x02, 0x0F, 0xA0, 0x30, 0x39, 0x05, 0x78, 0x00, 0xFA, 0x00, 0x01, 0x02, 0x00])
        line = describe(FrameParser("raise").feed(info)[0])
        self.assertIn("rpm=4000", line)
        self.assertIn("speed=123.45", line)  # 0x3039 = 12345 -> 123.45
        self.assertIn("V=14.00", line)       # 0x0578 = 1400 -> 14.00


class HiworldTests(unittest.TestCase):
    def test_roundtrip(self):
        raw = hiworld_frame(0x12, [0, 0, 0x80, 0, 0, 0, 0])
        frames = FrameParser("hiworld").feed(b"\x99" + raw + raw)
        self.assertEqual(len(frames), 2)
        self.assertEqual(frames[0].type, 0x12)

    def test_resync_on_bad_checksum(self):
        raw = bytearray(hiworld_frame(0x12, [1, 2, 3]))
        raw[-1] ^= 0xFF
        good = hiworld_frame(0x11, [9])
        frames = FrameParser("hiworld").feed(bytes(raw) + good)
        self.assertEqual([f.type for f in frames], [0x11])


class CensusTests(unittest.TestCase):
    def test_counts_and_distinct(self):
        clock = iter(range(100))
        p = FrameParser("raise", clock=lambda: next(clock))
        c = Census()
        for f in p.feed(XDA_22 + XDA_22 + XDA_23):
            c.add(f)
        self.assertEqual(c.count[0x22], 2)
        self.assertEqual(len(c.payloads[0x22]), 1)
        self.assertIn("0x22", c.report("raise"))


if __name__ == "__main__":
    unittest.main()
