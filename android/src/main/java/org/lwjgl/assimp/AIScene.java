package org.lwjgl.assimp;

import org.lwjgl.PointerBuffer;

import java.util.List;

/**
 * Android replacement for the LWJGL assimp scene, populated by
 * {@link GlbImporter}. mMeshes()/mTextures() hand out synthetic handles
 * resolved by {@code AI*.create(long)}.
 */
public class AIScene {
  final List<AIMesh> meshes;
  final List<AITexture> textures;

  AIScene(final List<AIMesh> meshes, final List<AITexture> textures) {
    this.meshes = meshes;
    this.textures = textures;
  }

  public int mNumMeshes() {
    return this.meshes.size();
  }

  public PointerBuffer mMeshes() {
    final PointerBuffer out = PointerBuffer.allocateDirect(this.meshes.size());
    for(final AIMesh mesh : this.meshes) {
      out.put(mesh.address());
    }
    out.position(0);
    return out;
  }

  public int mNumTextures() {
    return this.textures.size();
  }

  public PointerBuffer mTextures() {
    final PointerBuffer out = PointerBuffer.allocateDirect(this.textures.size());
    for(final AITexture texture : this.textures) {
      out.put(texture.address());
    }
    out.position(0);
    return out;
  }
}
