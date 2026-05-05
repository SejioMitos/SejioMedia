package com.mediascreen.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

public class DisplayFrameBlockEntity extends BlockEntity {

	private String youtubeUrl = "";
	private boolean isPlaying = false;
	private Direction facing = Direction.NORTH;

	public DisplayFrameBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.DISPLAY_FRAME, pos, state);
	}

	public String getYoutubeUrl() { return youtubeUrl; }

	public void setYoutubeUrl(String url) {
		this.youtubeUrl = url;
		markDirty();
		if (world != null && !world.isClient) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
	}

	public boolean isPlaying() { return isPlaying; }

	public void setPlaying(boolean playing) {
		this.isPlaying = playing;
		markDirty();
		if (world != null) {
			BlockState state = world.getBlockState(pos);
			world.setBlockState(pos, state.with(DisplayFrameBlock.PLAYING, playing));
		}
	}

	public Direction getFacing() { return facing; }
	public void setFacing(Direction facing) { this.facing = facing; }

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.writeNbt(nbt, registryLookup);
		nbt.putString("youtubeUrl", youtubeUrl);
		nbt.putBoolean("isPlaying", isPlaying);
		nbt.putString("facing", facing.getName());
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
		super.readNbt(nbt, registryLookup);
		youtubeUrl = nbt.getString("youtubeUrl");
		isPlaying = nbt.getBoolean("isPlaying");
		String facingName = nbt.getString("facing");
		Direction dir = Direction.byName(facingName);
		facing = dir != null ? dir : Direction.NORTH;
	}

	@Nullable
	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}

	@Override
	public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
		return createNbt(registryLookup);
	}
}