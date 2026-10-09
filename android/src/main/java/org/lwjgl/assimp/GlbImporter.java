package org.lwjgl.assimp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal GLB parser covering what the engine's model loaders use:
 * POSITION/NORMAL/TEXCOORD_0 (float) and COLOR_0 (float or normalized u16)
 * vertex attributes, u8/u16/u32 indices grouped into triangles, and images
 * embedded via bufferView. Each glTF primitive becomes one AIMesh, matching
 * assimp's model. Node transforms are not applied — the same as an
 * aiImportFile call without post-processing flags.
 */
final class GlbImporter {
  private GlbImporter() { }

  private static final int CHUNK_JSON = 0x4E4F534A;
  private static final int CHUNK_BIN = 0x004E4942;

  static AIScene load(final Path file) throws IOException {
    try {
      return doLoad(file);
    } catch(final JSONException e) {
      throw new IOException("Malformed GLB: " + file, e);
    }
  }

  private static AIScene doLoad(final Path file) throws IOException, JSONException {
    final ByteBuffer glb = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
    if(glb.remaining() < 12 || glb.getInt(0) != 0x46546C67) { // "glTF"
      throw new IOException("Not a GLB file: " + file);
    }

    JSONObject json = null;
    ByteBuffer bin = null;
    int offset = 12;
    while(offset + 8 <= glb.limit()) {
      final int length = glb.getInt(offset);
      final int type = glb.getInt(offset + 4);
      if(type == CHUNK_JSON) {
        json = new JSONObject(new String(glb.array(), offset + 8, length));
      } else if(type == CHUNK_BIN) {
        final ByteBuffer slice = glb.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        slice.position(offset + 8);
        slice.limit(offset + 8 + length);
        bin = slice.slice().order(ByteOrder.LITTLE_ENDIAN);
      }
      offset += 8 + length + (length % 4 != 0 ? 4 - length % 4 : 0);
    }

    if(json == null) {
      throw new IOException("GLB has no JSON chunk: " + file);
    }

    final List<AITexture> textures = new ArrayList<>();
    final JSONArray images = json.optJSONArray("images");
    for(int i = 0; images != null && i < images.length(); i++) {
      final JSONObject image = images.getJSONObject(i);
      if(image.has("bufferView")) {
        textures.add(new AITexture(bufferViewBytes(json, bin, image.getInt("bufferView"))));
      }
    }

    final List<AIMesh> meshes = new ArrayList<>();
    final JSONArray jsonMeshes = json.optJSONArray("meshes");
    for(int i = 0; jsonMeshes != null && i < jsonMeshes.length(); i++) {
      final JSONArray primitives = jsonMeshes.getJSONObject(i).getJSONArray("primitives");
      for(int p = 0; p < primitives.length(); p++) {
        meshes.add(primitive(json, bin, primitives.getJSONObject(p)));
      }
    }

    return new AIScene(meshes, textures);
  }

  private static AIMesh primitive(final JSONObject json, final ByteBuffer bin, final JSONObject primitive) throws JSONException {
    final JSONObject attributes = primitive.getJSONObject("attributes");
    final float[] positions = floats(json, bin, attributes.getInt("POSITION"), 3);
    final int vertexCount = positions.length / 3;

    float[] normals = vec(json, bin, attributes.optInt("NORMAL", -1), 3);
    if(normals == null) {
      normals = new float[vertexCount * 3];
      for(int i = 0; i < vertexCount; i++) {
        normals[i * 3 + 1] = 1.0f;
      }
    }

    float[] uvs = vec(json, bin, attributes.optInt("TEXCOORD_0", -1), 2);
    if(uvs == null) {
      uvs = new float[vertexCount * 3];
    } else {
      // AIVector3D is 3-component; expand xy -> xyz
      final float[] expanded = new float[vertexCount * 3];
      for(int i = 0; i < vertexCount; i++) {
        expanded[i * 3] = uvs[i * 2];
        expanded[i * 3 + 1] = uvs[i * 2 + 1];
      }
      uvs = expanded;
    }

    float[] colours = colours(json, bin, attributes.optInt("COLOR_0", -1));
    if(colours == null) {
      colours = new float[vertexCount * 4];
      java.util.Arrays.fill(colours, 1.0f);
    }

    final int[] indices = indices(json, bin, primitive.getInt("indices"));
    return new AIMesh(positions, normals, colours, uvs, indices);
  }

  /** Float attribute of N components per element, or null if accessor absent. */
  private static float[] vec(final JSONObject json, final ByteBuffer bin, final int accessorIndex, final int components) throws JSONException {
    if(accessorIndex < 0) {
      return null;
    }
    return floats(json, bin, accessorIndex, components);
  }

  /** Colour attribute normalized to [0,1] float RGBA. */
  private static float[] colours(final JSONObject json, final ByteBuffer bin, final int accessorIndex) throws JSONException {
    if(accessorIndex < 0) {
      return null;
    }

    final JSONObject accessor = json.getJSONArray("accessors").getJSONObject(accessorIndex);
    final int components = accessor.getString("type").equals("VEC4") ? 4 : 3;
    final int count = accessor.getInt("count");
    final float[] out = new float[count * 4];

    if(accessor.getInt("componentType") == 5126) {
      final float[] raw = floats(json, bin, accessorIndex, components);
      for(int i = 0; i < count; i++) {
        out[i * 4] = raw[i * components];
        out[i * 4 + 1] = raw[i * components + 1];
        out[i * 4 + 2] = raw[i * components + 2];
        out[i * 4 + 3] = components == 4 ? raw[i * components + 3] : 1.0f;
      }
    } else { // 5123 u16 normalized
      final ByteBuffer data = accessorData(json, bin, accessorIndex, accessor);
      final int stride = stride(json, accessor, 2 * components);
      int pos = data.position() + accessor.optInt("byteOffset", 0);
      for(int i = 0; i < count; i++) {
        final int base = pos + i * stride;
        out[i * 4] = (data.getShort(base) & 0xffff) / 65535.0f;
        out[i * 4 + 1] = (data.getShort(base + 2) & 0xffff) / 65535.0f;
        out[i * 4 + 2] = (data.getShort(base + 4) & 0xffff) / 65535.0f;
        out[i * 4 + 3] = components == 4 ? (data.getShort(base + 6) & 0xffff) / 65535.0f : 1.0f;
      }
    }
    return out;
  }

  private static int[] indices(final JSONObject json, final ByteBuffer bin, final int accessorIndex) throws JSONException {
    final JSONObject accessor = json.getJSONArray("accessors").getJSONObject(accessorIndex);
    final int count = accessor.getInt("count");
    final int componentType = accessor.getInt("componentType");
    final ByteBuffer data = accessorData(json, bin, accessorIndex, accessor);
    final int size = componentType == 5121 || componentType == 5120 ? 1 : componentType == 5123 || componentType == 5122 ? 2 : 4;
    final int stride = stride(json, accessor, size);

    final int[] out = new int[count];
    int pos = data.position() + accessor.optInt("byteOffset", 0);
    for(int i = 0; i < count; i++) {
      final int base = pos + i * stride;
      out[i] = switch(componentType) {
        case 5121 -> data.get(base) & 0xff;
        case 5120 -> data.get(base);
        case 5123 -> data.getShort(base) & 0xffff;
        case 5122 -> data.getShort(base);
        default -> data.getInt(base);
      };
    }
    return out;
  }

  private static float[] floats(final JSONObject json, final ByteBuffer bin, final int accessorIndex, final int components) throws JSONException {
    final JSONObject accessor = json.getJSONArray("accessors").getJSONObject(accessorIndex);
    if(accessor.getInt("componentType") != 5126) {
      throw new IllegalArgumentException("Expected float accessor, got componentType " + accessor.getInt("componentType"));
    }

    final int count = accessor.getInt("count");
    final ByteBuffer data = accessorData(json, bin, accessorIndex, accessor);
    final int stride = stride(json, accessor, 4 * components);
    final float[] out = new float[count * components];
    int pos = data.position() + accessor.optInt("byteOffset", 0);
    for(int i = 0; i < count; i++) {
      final int base = pos + i * stride;
      for(int c = 0; c < components; c++) {
        out[i * components + c] = data.getFloat(base + c * 4);
      }
    }
    return out;
  }

  /** Bytes of a bufferView (for embedded images). */
  private static ByteBuffer bufferViewBytes(final JSONObject json, final ByteBuffer bin, final int viewIndex) throws JSONException {
    final JSONObject view = json.getJSONArray("bufferViews").getJSONObject(viewIndex);
    final ByteBuffer slice = bin.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    final int start = view.optInt("byteOffset", 0);
    slice.position(start);
    slice.limit(start + view.getInt("byteLength"));
    return slice.slice().order(ByteOrder.LITTLE_ENDIAN);
  }

  /** BufferView region an accessor reads from (positioned at its start). */
  private static ByteBuffer accessorData(final JSONObject json, final ByteBuffer bin, final int accessorIndex, final JSONObject accessor) throws JSONException {
    final JSONObject view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"));
    final ByteBuffer slice = bin.duplicate().order(ByteOrder.LITTLE_ENDIAN);
    slice.position(view.optInt("byteOffset", 0));
    return slice.slice().order(ByteOrder.LITTLE_ENDIAN);
  }

  private static int stride(final JSONObject json, final JSONObject accessor, final int packedSize) throws JSONException {
    final JSONObject view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"));
    return view.optInt("byteStride", packedSize);
  }
}
