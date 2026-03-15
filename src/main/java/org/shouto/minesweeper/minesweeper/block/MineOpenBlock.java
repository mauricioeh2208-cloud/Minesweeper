package org.shouto.minesweeper.minesweeper.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MineOpenBlock extends Block {
    public static final BooleanProperty TRIGGERED = BooleanProperty.create("triggered");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3.0, 0.0, 3.0, 13.0, 2.0, 13.0),
            Block.box(5.0, 2.0, 8.0, 11.0, 3.0, 11.0),
            Block.box(5.0, 3.0, 5.0, 11.0, 4.0, 8.0),
            Block.box(5.0, 0.0, 1.0, 11.0, 2.0, 4.0),
            Block.box(1.0, 0.0, 5.0, 4.0, 2.0, 11.0),
            Block.box(12.0, 0.0, 5.0, 15.0, 2.0, 11.0)
    );

    public MineOpenBlock(BlockBehaviour.Properties settings) {
        super(settings);
        registerDefaultState(this.stateDefinition.any().setValue(TRIGGERED, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TRIGGERED);
    }
}
