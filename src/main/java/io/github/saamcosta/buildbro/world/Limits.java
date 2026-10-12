package io.github.saamcosta.buildbro.world;

/**
 * @param maxBlocks most blocks one request may change
 * @param radius    most blocks (horizontally, on either axis) a change may be from where the player asked
 */
public record Limits(int maxBlocks, int radius) {
}
