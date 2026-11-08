package com.marcofotos;

import com.marcofotos.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;

public class MarcoFotoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final VoxelShape N = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape S = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape W = Block.box(14, 0, 0, 16, 16, 16);
    private static final VoxelShape E = Block.box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape U = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape D = Block.box(0, 14, 0, 16, 16, 16);

    public MarcoFotoBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState st = defaultBlockState().setValue(FACING, ctx.getClickedFace());
        return st.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? st : null;
    }

    @Override
    public boolean canSurvive(BlockState st, LevelReader level, BlockPos pos) {
        Direction f = st.getValue(FACING);
        BlockPos support = pos.relative(f.getOpposite());
        return level.getBlockState(support).isFaceSturdy(level, support, f);
    }

    @Override
    public BlockState updateShape(BlockState st, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == st.getValue(FACING).getOpposite() && !st.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(st, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        switch (st.getValue(FACING)) {
            case SOUTH: return S;
            case WEST: return W;
            case EAST: return E;
            case UP: return U;
            case DOWN: return D;
            default: return N;
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState st) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new MarcoFotoBlockEntity(pos, st);
    }

    @Override
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openBlockScreen(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState st, LootParams.Builder builder) {
        ItemStack stack = new ItemStack(this);
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof MarcoFotoBlockEntity m && m.data.hasMedia()) {
            BlockItem.setBlockEntityData(stack, ModRegistry.MARCO_BE.get(), m.saveWithoutMetadata());
        }
        return List.of(stack);
    }
}
