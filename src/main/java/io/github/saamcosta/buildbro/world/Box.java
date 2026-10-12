package io.github.saamcosta.buildbro.world;

/** An axis-aligned box of blocks, corners included. */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
	public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
		return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
				Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
	}

	public int sizeX() {
		return maxX - minX + 1;
	}

	public int sizeY() {
		return maxY - minY + 1;
	}

	public int sizeZ() {
		return maxZ - minZ + 1;
	}

	public long volume() {
		return (long) sizeX() * sizeY() * sizeZ();
	}

	/** True for blocks on any face of the box. */
	public boolean isShell(int x, int y, int z) {
		return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
	}

	@Override
	public String toString() {
		return "(" + minX + " " + minY + " " + minZ + ")..(" + maxX + " " + maxY + " " + maxZ + ")";
	}
}
