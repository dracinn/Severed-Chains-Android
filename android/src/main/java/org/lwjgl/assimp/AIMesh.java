package org.lwjgl.assimp;

/**
 * Android replacement for the LWJGL assimp mesh — one glTF primitive.
 * Attribute arrays come from {@link GlbImporter}; missing attributes are
 * filled with sane defaults so loaders never see nulls.
 */
public class AIMesh {
  private final long address;
  private final float[] vertices;
  private final float[] normals;
  private final float[] colours;
  private final float[] uvs;
  private final int[] indices;

  AIMesh(final float[] vertices, final float[] normals, final float[] colours, final float[] uvs, final int[] indices) {
    this.address = AIHandles.register(this);
    this.vertices = vertices;
    this.normals = normals;
    this.colours = colours;
    this.uvs = uvs;
    this.indices = indices;
  }

  long address() {
    return this.address;
  }

  public static AIMesh create(final long address) {
    return AIHandles.get(address);
  }

  public AIFace.Buffer mFaces() {
    return new AIFace.Buffer(this.indices);
  }

  public AIVector3D.Buffer mVertices() {
    return new AIVector3D.Buffer(this.vertices);
  }

  public AIVector3D.Buffer mNormals() {
    return new AIVector3D.Buffer(this.normals);
  }

  public AIColor4D.Buffer mColors(final int set) {
    return new AIColor4D.Buffer(this.colours);
  }

  public AIVector3D.Buffer mTextureCoords(final int set) {
    return new AIVector3D.Buffer(this.uvs);
  }
}
