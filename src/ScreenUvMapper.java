package com.mediascreen.client;

import com.mediascreen.block.DisplayFrameBlock;
import com.mediascreen.block.DisplayFrameBlockEntity;
import com.mediascreen.block.MediaScreenBlock;
import com.mediascreen.block.MediaScreenBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

final class ScreenUvMapper {
	private static final UvRange FULL = new UvRange(0.0f, 1.0f);
	private static final UvRange LEFT_HALF = new UvRange(0.0f, 0.5f);
	private static final UvRange RIGHT_HALF = new UvRange(0.5f, 1.0f);

	private ScreenUvMapper() {}

	static UvRange forDisplayFrame(DisplayFrameBlockEntity entity, Direction facing) {
		World world = entity.getWorld();
		if (world == null) return FULL;

		BlockPos pos = entity.getPos();
		Direction screenRight = facing.rotateYCounterclockwise();
		Direction screenLeft = facing.rotateYClockwise();
		boolean hasLeftNeighbor = isMatchingDisplayFrame(world, pos.offset(screenLeft), facing);
		boolean hasRightNeighbor = isMatchingDisplayFrame(world, pos.offset(screenRight), facing);

		return selectRange(hasLeftNeighbor, hasRightNeighbor);
	}

	static UvRange forMediaScreen(MediaScreenBlockEntity entity, Direction facing) {
		World world = entity.getWorld();
		if (world == null) return FULL;

		BlockPos pos = entity.getPos();
		Direction screenRight = facing.rotateYCounterclockwise();
		Direction screenLeft = facing.rotateYClockwise();
		boolean hasLeftNeighbor = isMatchingMediaScreen(world, pos.offset(screenLeft), facing);
		boolean hasRightNeighbor = isMatchingMediaScreen(world, pos.offset(screenRight), facing);

		return selectRange(hasLeftNeighbor, hasRightNeighbor);
	}

	private static UvRange selectRange(boolean hasLeftNeighbor, boolean hasRightNeighbor) {
		if (hasRightNeighbor && !hasLeftNeighbor) return LEFT_HALF;
		if (hasLeftNeighbor && !hasRightNeighbor) return RIGHT_HALF;
		return FULL;
	}

	private static boolean isMatchingDisplayFrame(World world, BlockPos pos, Direction facing) {
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof DisplayFrameBlock) || !state.contains(DisplayFrameBlock.FACING)) {
			return false;
		}
		if (state.get(DisplayFrameBlock.FACING) != facing) {
			return false;
		}

		BlockEntity blockEntity = world.getBlockEntity(pos);
		return blockEntity instanceof DisplayFrameBlockEntity displayFrame && displayFrame.isPlaying();
	}

	private static boolean isMatchingMediaScreen(World world, BlockPos pos, Direction facing) {
		BlockState state = world.getBlockState(pos);
		if (!(state.getBlock() instanceof MediaScreenBlock) || !state.contains(MediaScreenBlock.FACING)) {
			return false;
		}
		if (state.get(MediaScreenBlock.FACING) != facing) {
			return false;
		}

		BlockEntity blockEntity = world.getBlockEntity(pos);
		return blockEntity instanceof MediaScreenBlockEntity mediaScreen && mediaScreen.isPlaying();
	}

	record UvRange(float minU, float maxU) {}
}
