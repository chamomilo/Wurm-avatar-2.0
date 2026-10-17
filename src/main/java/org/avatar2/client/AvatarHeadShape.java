package org.avatar2.client;

import com.wurmonline.client.renderer.model.collada.animation.Joint;
import com.wurmonline.client.renderer.model.collada.importer.ColladaModel;
import com.wurmonline.client.renderer.model.collada.math.Matrix4f;

/** Immutable bind-space head mapping. The crown stays at the chosen standing height. */
final class AvatarHeadShape {
    final float x, y, z, lower, upper;

    AvatarHeadShape(ColladaModel model, float crown, float height) {
        Joint head = model.getJoint("head"), neck = model.getJoint("neck_01");
        if (head == null || neck == null) throw new IllegalArgumentException("Missing Avatar head landmarks");
        Matrix4f h = head.getOriginalBindMatrix(), n = neck.getOriginalBindMatrix();
        x = h.m03; y = crown; z = h.m23;
        lower = n.m13;
        upper = h.m13;
        if (!(crown > upper && upper > lower && upper - lower > height * .02f))
            throw new IllegalArgumentException("Invalid Avatar head/neck landmarks");
    }

    private AvatarHeadShape(AvatarHeadShape basis, float crown) {
        x=basis.x; y=crown; z=basis.z; lower=basis.lower; upper=basis.upper;
    }

    AvatarHeadShape atCrown(float crown) { return crown==y?this:new AvatarHeadShape(this,crown); }

    static float scale(float value) { return 1 + (value - .5f) * .16f; }

    void deform(float value, float[] vertices, float[] normals) {
        if (value == .5f) return;
        double amount = scale(value) - 1, range = upper - lower;
        for (int i = 0; i < vertices.length; i += 3) {
            double t = (vertices[i + 1] - lower) / range;
            if (t <= 0) continue;
            double mask = t >= 1 ? 1 : t*t*t*(10 + t*(-15 + 6*t));
            double derivative = t >= 1 ? 0 : 30*t*t*(1-t)*(1-t) / range;
            double a = 1 + amount * mask, dx = vertices[i]-x, dy = vertices[i+1]-y, dz = vertices[i+2]-z;
            double j01 = amount*derivative*dx, j11 = a + amount*derivative*dy, j21 = amount*derivative*dz;
            if (j11 <= .5) throw new IllegalStateException("Head mapping lost orientation");
            vertices[i] = (float)(x + a*dx);
            vertices[i+1] = (float)(y + a*dy);
            vertices[i+2] = (float)(z + a*dz);
            // Inverse transpose of the exact smooth mapping; preserve UV-split shading.
            double nx = normals[i]/a, nz = normals[i+2]/a;
            double ny = (normals[i+1] - j01*nx - j21*nz)/j11;
            double length = Math.sqrt(nx*nx + ny*ny + nz*nz);
            normals[i]=(float)(nx/length); normals[i+1]=(float)(ny/length); normals[i+2]=(float)(nz/length);
        }
    }

    float[] delta(float value, Matrix4f rest) {
        float amount = scale(value)-1;
        return new float[]{amount*(rest.m03-x), amount*(rest.m13-y), amount*(rest.m23-z)};
    }
}
