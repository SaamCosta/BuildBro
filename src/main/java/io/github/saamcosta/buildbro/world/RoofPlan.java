package io.github.saamcosta.buildbro.world;

import java.util.ArrayList;
import java.util.List;

/**
 * Where each block of a roof goes, without touching the game, so the geometry can be tested.
 * The footprint is the outer edge of the walls; the eaves stick out by {@code overhang}.
 */
public final class RoofPlan {
	public enum Style {
		GABLE,
		HIP,
		FLAT
	}

	/** Compass direction a stair rises towards. */
	public enum Rise {
		NORTH,
		SOUTH,
		EAST,
		WEST
	}

	public enum Part {
		/** A stair; {@link Piece#rise()} says which way it goes up. */
		STAIR,
		/** A bottom slab on the ridge line, or the whole flat roof. */
		RIDGE,
		/** A full block closing the triangle at a gable end, flush with the wall. */
		GABLE_WALL
	}

	public record Piece(int x, int y, int z, Part part, Rise rise) {
	}

	private RoofPlan() {
	}

	/**
	 * @param ridgeAlongX for gables: true when the ridge runs east-west (slopes face north and south)
	 */
	public static List<Piece> plan(Box footprint, int baseY, Style style, boolean ridgeAlongX, int overhang) {
		int xLo = footprint.minX() - overhang;
		int xHi = footprint.maxX() + overhang;
		int zLo = footprint.minZ() - overhang;
		int zHi = footprint.maxZ() + overhang;
		List<Piece> pieces = new ArrayList<>();
		switch (style) {
			case FLAT -> {
				for (int x = xLo; x <= xHi; x++) {
					for (int z = zLo; z <= zHi; z++) {
						pieces.add(new Piece(x, baseY, z, Part.RIDGE, null));
					}
				}
			}
			case GABLE -> gable(pieces, footprint, baseY, ridgeAlongX, xLo, xHi, zLo, zHi);
			case HIP -> hip(pieces, baseY, xLo, xHi, zLo, zHi);
		}
		return pieces;
	}

	private static void gable(List<Piece> pieces, Box footprint, int baseY, boolean ridgeAlongX, int xLo, int xHi, int zLo, int zHi) {
		// Work in (along, across): "along" follows the ridge, "across" goes from one eave to the other.
		int alongLo = ridgeAlongX ? xLo : zLo;
		int alongHi = ridgeAlongX ? xHi : zHi;
		int acrossLo = ridgeAlongX ? zLo : xLo;
		int acrossHi = ridgeAlongX ? zHi : xHi;
		int wallAcrossLo = ridgeAlongX ? footprint.minZ() : footprint.minX();
		int wallAcrossHi = ridgeAlongX ? footprint.maxZ() : footprint.maxX();
		int[] gableEnds = ridgeAlongX
				? new int[] {footprint.minX(), footprint.maxX()}
				: new int[] {footprint.minZ(), footprint.maxZ()};
		Rise upFromLo = ridgeAlongX ? Rise.SOUTH : Rise.EAST;
		Rise upFromHi = ridgeAlongX ? Rise.NORTH : Rise.WEST;

		for (int layer = 0; ; layer++) {
			int lo = acrossLo + layer;
			int hi = acrossHi - layer;
			int y = baseY + layer;
			if (lo > hi) {
				break;
			}
			for (int along = alongLo; along <= alongHi; along++) {
				if (lo == hi) {
					pieces.add(piece(ridgeAlongX, along, y, lo, Part.RIDGE, null));
				} else {
					pieces.add(piece(ridgeAlongX, along, y, lo, Part.STAIR, upFromLo));
					pieces.add(piece(ridgeAlongX, along, y, hi, Part.STAIR, upFromHi));
				}
			}
			// Close the triangle under this layer at both gable ends, inside the walls only.
			for (int end : gableEnds) {
				for (int across = Math.max(lo + 1, wallAcrossLo); across <= Math.min(hi - 1, wallAcrossHi); across++) {
					pieces.add(piece(ridgeAlongX, end, y, across, Part.GABLE_WALL, null));
				}
			}
			if (lo == hi) {
				break;
			}
		}
	}

	private static void hip(List<Piece> pieces, int baseY, int xLo, int xHi, int zLo, int zHi) {
		for (int layer = 0; ; layer++) {
			int x0 = xLo + layer;
			int x1 = xHi - layer;
			int z0 = zLo + layer;
			int z1 = zHi - layer;
			int y = baseY + layer;
			if (x0 > x1 || z0 > z1) {
				break;
			}
			if (x0 == x1 || z0 == z1) {
				for (int x = x0; x <= x1; x++) {
					for (int z = z0; z <= z1; z++) {
						pieces.add(new Piece(x, y, z, Part.RIDGE, null));
					}
				}
				break;
			}
			// North and south edges own the corners; the game turns corner stairs into outer corners.
			for (int x = x0; x <= x1; x++) {
				pieces.add(new Piece(x, y, z0, Part.STAIR, Rise.SOUTH));
				pieces.add(new Piece(x, y, z1, Part.STAIR, Rise.NORTH));
			}
			for (int z = z0 + 1; z <= z1 - 1; z++) {
				pieces.add(new Piece(x0, y, z, Part.STAIR, Rise.EAST));
				pieces.add(new Piece(x1, y, z, Part.STAIR, Rise.WEST));
			}
		}
	}

	private static Piece piece(boolean ridgeAlongX, int along, int y, int across, Part part, Rise rise) {
		return ridgeAlongX ? new Piece(along, y, across, part, rise) : new Piece(across, y, along, part, rise);
	}
}
