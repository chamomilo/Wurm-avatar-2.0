package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.importer.TriangleMesh;
import org.junit.Test;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static org.junit.Assert.assertEquals;

public final class AvatarAnimationBridgeTest {
    @Test
    public void fourInfluenceVertexDoesNotChangeFollowingVertex() {
        TriangleMesh mesh = new TriangleMesh("weight-regression");
        mesh.setVertexBuffer(FloatBuffer.allocate(6));
        mesh.setWeightBuffer(FloatBuffer.wrap(new float[] {
                0.8f, 0.6f, 0.4f, 0.2f,
                0.4f, 0.3f, 0.2f, 0.1f
        }));
        mesh.setBoneBuffer(ShortBuffer.wrap(new short[] {
                0, 1, 2, 3,
                4, 5, 6, 7
        }));

        assertEquals(1, AvatarAnimationBridge.normalizeSkinWeights(mesh));
        assertEquals(0.4f, mesh.getWeightBuffer().get(0), 0.000001f);
        assertEquals(0.3f, mesh.getWeightBuffer().get(1), 0.000001f);
        assertEquals(0.2f, mesh.getWeightBuffer().get(2), 0.000001f);
        assertEquals(0.1f, mesh.getWeightBuffer().get(3), 0.000001f);
        assertEquals(0.4f, mesh.getWeightBuffer().get(4), 0.000001f);
        assertEquals(0.3f, mesh.getWeightBuffer().get(5), 0.000001f);
        assertEquals(0.2f, mesh.getWeightBuffer().get(6), 0.000001f);
        assertEquals(0.1f, mesh.getWeightBuffer().get(7), 0.000001f);
    }
}
