package io.github.saamcosta.buildbro.world;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoofPlanTest {
	private static final Box FIVE_BY_SEVEN = Box.of(0, 0, 0, 6, 0, 4);

	private static Set<String> columns(List<RoofPlan.Piece> pieces, RoofPlan.Part... parts) {
		Set<String> columns = new HashSet<>();
		for (RoofPlan.Piece piece : pieces) {
			if (parts.length == 0 || List.of(parts).contains(piece.part())) {
				columns.add(piece.x() + "," + piece.z());
			}
		}
		return columns;
	}

	@Test
	void gableCoversEveryColumnWithNoGap() {
		List<RoofPlan.Piece> pieces = RoofPlan.plan(FIVE_BY_SEVEN, 10, RoofPlan.Style.GABLE, true, 1);
		Set<String> covered = columns(pieces, RoofPlan.Part.STAIR, RoofPlan.Part.RIDGE);
		for (int x = -1; x <= 7; x++) {
			for (int z = -1; z <= 5; z++) {
				assertTrue(covered.contains(x + "," + z), "gap at " + x + "," + z);
			}
		}
		// 7 across (5 + 2 overhang): layers 0..2 of stairs, a slab ridge on layer 3
		assertEquals(13, pieces.stream().mapToInt(RoofPlan.Piece::y).max().orElseThrow());
		assertTrue(pieces.stream().filter(p -> p.part() == RoofPlan.Part.RIDGE).allMatch(p -> p.y() == 13 && p.z() == 2));
	}

	@Test
	void gableStairsRiseTowardsTheRidge() {
		for (RoofPlan.Piece piece : RoofPlan.plan(FIVE_BY_SEVEN, 10, RoofPlan.Style.GABLE, true, 1)) {
			if (piece.part() == RoofPlan.Part.STAIR) {
				assertEquals(piece.z() < 2 ? RoofPlan.Rise.SOUTH : RoofPlan.Rise.NORTH, piece.rise(), piece.toString());
			}
		}
	}

	@Test
	void gableEndsAreClosedInsideTheWalls() {
		List<RoofPlan.Piece> pieces = RoofPlan.plan(FIVE_BY_SEVEN, 10, RoofPlan.Style.GABLE, true, 1);
		Set<String> gable = new HashSet<>();
		for (RoofPlan.Piece piece : pieces) {
			if (piece.part() == RoofPlan.Part.GABLE_WALL) {
				assertTrue(piece.x() == 0 || piece.x() == 6, "gable only at the end walls: " + piece);
				assertTrue(piece.z() >= 0 && piece.z() <= 4, "gable inside the walls: " + piece);
				gable.add(piece.x() + "," + piece.y() + "," + piece.z());
			}
		}
		// Under the stairs at each end: layer 0 z=0..4, layer 1 z=1..3, layer 2 z=2
		assertEquals(2 * (5 + 3 + 1), gable.size());
	}

	@Test
	void evenWidthGableMeetsWithoutRidge() {
		List<RoofPlan.Piece> pieces = RoofPlan.plan(Box.of(0, 0, 0, 5, 0, 3), 10, RoofPlan.Style.GABLE, true, 0);
		assertTrue(pieces.stream().noneMatch(p -> p.part() == RoofPlan.Part.RIDGE));
		assertEquals(11, pieces.stream().mapToInt(RoofPlan.Piece::y).max().orElseThrow());
	}

	@Test
	void hipCoversEveryColumnOnce() {
		List<RoofPlan.Piece> pieces = RoofPlan.plan(FIVE_BY_SEVEN, 10, RoofPlan.Style.HIP, true, 0);
		Set<String> covered = columns(pieces);
		assertEquals(7 * 5, covered.size());
		assertEquals(7 * 5, pieces.size(), "no column gets two blocks");
	}

	@Test
	void flatIsOneLayerWithOverhang() {
		List<RoofPlan.Piece> pieces = RoofPlan.plan(FIVE_BY_SEVEN, 10, RoofPlan.Style.FLAT, true, 1);
		assertEquals(9 * 7, pieces.size());
		assertTrue(pieces.stream().allMatch(p -> p.y() == 10));
	}

	@Test
	void ridgeAlongZSwapsTheAxes() {
		List<RoofPlan.Piece> pieces = RoofPlan.plan(Box.of(0, 0, 0, 4, 0, 8), 5, RoofPlan.Style.GABLE, false, 0);
		for (RoofPlan.Piece piece : pieces) {
			if (piece.part() == RoofPlan.Part.STAIR) {
				assertEquals(piece.x() < 2 ? RoofPlan.Rise.EAST : RoofPlan.Rise.WEST, piece.rise(), piece.toString());
			}
		}
	}
}
