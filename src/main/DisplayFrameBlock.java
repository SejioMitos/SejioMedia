package com.mediascreen.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class DisplayFrameBlock extends HorizontalFacingBlock implements BlockEntityProvider {

	public static final MapCodec<DisplayFrameBlock> CODEC = createCodec(DisplayFrameBlock::new);
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final BooleanProperty PLAYING = BooleanProperty.of("playing");
	public static Consumer<DisplayFrameBlockEntity> openScreenClient;
	private static final VoxelShape SHAPE_NORTH = VoxelShapes.cuboid(-0.21875, 0.0, 0.5, 1.28125, 0.875, 0.5625);
	private static final VoxelShape SHAPE_EAST = VoxelShapes.cuboid(0.4375, 0.0, -0.21875, 0.5, 0.875, 1.28125);
	private static final VoxelShape SHAPE_SOUTH = VoxelShapes.cuboid(-0.28125, 0.0, 0.4375, 1.21875, 0.875, 0.5);
	private static final VoxelShape SHAPE_WEST = VoxelShapes.cuboid(0.5, 0.0, -0.28125, 0.5625, 0.875, 1.21875);

    public DisplayFrameBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends HorizontalFacingBlock> getCodec() {
        return CODEC;
    }

@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING);
		builder.add(PLAYING);
	}

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof DisplayFrameBlockEntity dfe) {
                dfe.setFacing(state.get(FACING));
            }
        }
    }

@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new DisplayFrameBlockEntity(pos, state);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world.isClient && openScreenClient != null) {
			BlockEntity be = world.getBlockEntity(pos);
			if (be instanceof DisplayFrameBlockEntity dfe) {
				openScreenClient.accept(dfe);
			}
		}
		return ActionResult.SUCCESS;
	}

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getShapeForState(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getShapeForState(state);
    }

	private static VoxelShape getShapeForState(BlockState state) {
		return switch (state.get(FACING)) {
			case EAST -> SHAPE_EAST;
			case SOUTH -> SHAPE_SOUTH;
			case WEST -> SHAPE_WEST;
			default -> SHAPE_NORTH;
		};
	}

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
