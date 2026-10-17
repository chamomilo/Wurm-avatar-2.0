package org.avatar2.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeometryProbeTest {
    @Test public void neutralRestoresExactInputAndTorsoDoesNotMoveHeadOrFeet() {
        float[] base = {.1f, 0, .1f, .1f, .65f, .1f, .1f, 1, .1f};
        float[] normal = {0, 0, 1, 0, 0, 1, 0, 0, 1};
        float[] vertices = base.clone(), normals = normal.clone();
        GeometryProbe probe = new GeometryProbe(1, 1);
        probe.deform(base, normal, vertices, normals, 0, 1);
        assertTrue(vertices[3] > base[3]); assertTrue(vertices[5] > base[5]);
        for (int i : new int[]{0, 1, 2, 6, 7, 8}) assertEquals(base[i], vertices[i], 0);
        float[] first = vertices.clone();
        for (int n = 0; n < 20; n++) {
            probe.deform(base, normal, vertices, normals, 0, 1);
            assertArrayEquals(first, vertices, 0);
        }
        GeometryProbe.NEUTRAL.deform(base, normal, vertices, normals, 0, 1);
        assertArrayEquals(base, vertices, 0); assertArrayEquals(normal, normals, 0);
    }

    @Test public void normalsFollowTheActualDeformationJacobian() {
        GeometryProbe probe = new GeometryProbe(-1, 1);
        float[] origin = {.07f, .62f, .11f};
        float[] normal = {0, 0, 1};
        float[] p = new float[3], n = new float[3];
        probe.deform(origin, normal, p, n, 0, 1);
        float epsilon = .0001f;
        for (int axis = 0; axis < 2; axis++) {
            float[] next = origin.clone(); next[axis] += epsilon;
            float[] q = new float[3];
            probe.deform(next, normal, q, new float[3], 0, 1);
            double dot = 0; for (int i = 0; i < 3; i++) dot += (q[i] - p[i])/epsilon*n[i];
            assertEquals(0, dot, .002);
        }
        assertEquals(1, n[0]*n[0] + n[1]*n[1] + n[2]*n[2], .00001);
    }

    @Test public void nonFiniteAndOutOfRangeCommandsAreRejected() {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, -1.01f, 1.01f}) {
            try { new GeometryProbe(bad, 0); fail(); } catch (IllegalArgumentException expected) { }
        }
    }
}
