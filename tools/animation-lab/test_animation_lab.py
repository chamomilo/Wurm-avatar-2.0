"""Offline tests for the companion. SPDX: LGPL-3.0-only."""
import math
import unittest
from animation_lab import derivative, restorable


class MathTests(unittest.TestCase):
    def test_axis_response_around_y(self):
        angle = math.radians(3)
        def rotation(theta):
            c, s = math.cos(theta), math.sin(theta)
            return [c,0,s,0, 0,1,0,0, -s,0,c,0, 0,0,0,1]
        result = derivative(rotation(angle), rotation(-angle), rotation(0), angle)
        self.assertEqual(result["positionPerRadian"], [0,0,0])
        self.assertAlmostEqual(result["rotationAxisPerRadian"][0], 0)
        self.assertAlmostEqual(result["rotationAxisPerRadian"][1], math.sin(angle)/angle)
        self.assertAlmostEqual(result["rotationAxisPerRadian"][2], 0)

    def test_restore_payload_is_copied_without_stale_revision(self):
        original = {"revision":5, "preview":{"clip":"walk"}, "corrections":{}, "pose":{}}
        saved = restorable(original)
        saved["preview"]["clip"] = "run"
        self.assertEqual(original["preview"]["clip"], "walk")
        self.assertNotIn("revision", saved)


if __name__ == "__main__":
    unittest.main()
