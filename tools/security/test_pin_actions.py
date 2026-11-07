import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("pin_actions", Path(__file__).with_name("pin-actions.py"))
pin = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pin)

A = "a" * 40
B = "b" * 40


def fake(repo, ref):
    return {("actions/checkout", "v4"): f"{A}\trefs/tags/v4\n",
            ("gradle/actions", "v4"): f"{A}\trefs/tags/v4\n{B}\trefs/tags/v4^{{}}\n"}.get((repo, ref), "")


class PinTest(unittest.TestCase):
    def test_rewrites_tags_keeps_comment_and_subpath(self):
        src = "      - uses: actions/checkout@v4\n        with:\n          x: 1\n      - uses: gradle/actions/setup-gradle@v4\n"
        new, ch = pin.rewrite(src, fake)
        self.assertIn(f"uses: actions/checkout@{A} # v4\n", new)
        self.assertIn(f"uses: gradle/actions/setup-gradle@{B} # v4\n", new)     # annotated tag: the peeled commit wins
        self.assertEqual(len(ch), 2)

    def test_leaves_pinned_and_local_alone(self):
        src = f"- uses: actions/checkout@{A} # v4\n- uses: ./local-action\n"
        new, ch = pin.rewrite(src, fake)
        self.assertEqual((new, ch), (src, []))

    def test_unknown_tag_aborts(self):
        with self.assertRaises(SystemExit):
            pin.rewrite("- uses: actions/nope@v9\n", fake)


if __name__ == "__main__":
    unittest.main()
