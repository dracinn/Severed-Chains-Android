package org.lwjgl.stb;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * STBRectPack replacement. Skyline/bottom-left packer - the layout does not
 * need to match stb's output, only produce valid non-overlapping rects.
 */
public final class STBRectPack {
  private STBRectPack() { }

  public static void stbrp_init_target(final STBRPContext context, final int width, final int height, final STBRPNode.Buffer nodes) {
    context.width = width;
    context.height = height;
  }

  private static int heightAt(final List<int[]> skyline, final int x) {
    int y = 0;
    for(final int[] node : skyline) {
      if(node[0] <= x) {
        y = node[1];
      } else {
        break;
      }
    }
    return y;
  }

  public static int stbrp_pack_rects(final STBRPContext context, final STBRPRect.Buffer rects) {
    // Skyline nodes: sorted list of (x, top) contour breakpoints
    List<int[]> skyline = new ArrayList<>();
    skyline.add(new int[] {0, 0});

    final List<STBRPRect> sorted = new ArrayList<>();
    for(int i = 0; i < rects.limit(); i++) {
      sorted.add(rects.get(i));
    }
    sorted.sort(Comparator.comparingInt((STBRPRect r) -> r.h()).reversed());

    boolean allPacked = true;

    for(final STBRPRect rect : sorted) {
      final int w = rect.w();
      final int h = rect.h();

      int bestX = -1;
      int bestY = Integer.MAX_VALUE;

      for(int i = 0; i < skyline.size(); i++) {
        final int x = skyline.get(i)[0];
        if(x + w > context.width) {
          continue;
        }

        // Placement height = max contour height across [x, x+w)
        int y = 0;
        int j = i;
        int remaining = w;
        while(remaining > 0) {
          y = Math.max(y, skyline.get(j)[1]);
          final int nextX = j + 1 < skyline.size() ? skyline.get(j + 1)[0] : context.width;
          remaining -= nextX - skyline.get(j)[0];
          j++;
        }

        if(y + h <= context.height && y < bestY) {
          bestY = y;
          bestX = x;
        }
      }

      if(bestX < 0) {
        rect.setPacked(false);
        allPacked = false;
        continue;
      }

      rect.x(bestX);
      rect.y(bestY);
      rect.setPacked(true);

      // Update skyline: drop nodes under the rect, insert raised segment
      final int rightEdge = bestX + w;
      final int carryY = heightAt(skyline, rightEdge);
      final int newTop = bestY + h;

      final List<int[]> updated = new ArrayList<>();
      for(final int[] node : skyline) {
        if(node[0] < bestX || node[0] >= rightEdge) {
          updated.add(node);
        }
      }
      updated.add(new int[] {bestX, newTop});
      if(rightEdge < context.width) {
        updated.add(new int[] {rightEdge, carryY});
      }
      updated.sort(Comparator.comparingInt(n -> n[0]));

      // Dedupe same-x nodes (later insertion wins) and merge equal heights
      final List<int[]> merged = new ArrayList<>();
      for(final int[] node : updated) {
        if(!merged.isEmpty() && merged.get(merged.size() - 1)[0] == node[0]) {
          merged.set(merged.size() - 1, node);
        } else {
          merged.add(node);
        }
      }
      for(int i = merged.size() - 1; i > 0; i--) {
        if(merged.get(i - 1)[1] == merged.get(i)[1]) {
          merged.remove(i);
        }
      }
      skyline = merged;
    }

    return allPacked ? 1 : 0;
  }
}
