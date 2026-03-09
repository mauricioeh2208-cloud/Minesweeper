package org.shouto.minesweeper.minesweeper.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlagBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(6.0, 0.0, 6.0, 10.0, 2.0, 10.0),
            Block.box(7.0, 2.0, 7.0, 9.0, 9.0, 9.0),
            Block.box(6.5, 9.0, 6.5, 9.5, 16.0, 9.5),
            Block.box(-4.0, 8.0, 7.4, 7.0, 16.0, 8.6)
    );

    public FlagBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
