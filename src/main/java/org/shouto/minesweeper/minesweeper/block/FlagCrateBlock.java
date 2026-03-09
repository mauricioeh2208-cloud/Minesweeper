package org.shouto.minesweeper.minesweeper.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlagCrateBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(-2.0, 0.0, 0.0, 18.0, 2.0, 16.0),
            Block.box(-2.0, 2.0, 0.0, 0.0, 11.0, 16.0),
            Block.box(16.0, 2.0, 0.0, 18.0, 11.0, 16.0),
            Block.box(0.0, 2.0, 0.0, 16.0, 11.0, 2.0),
            Block.box(0.0, 2.0, 14.0, 16.0, 11.0, 16.0),
            Block.box(-1.5, 11.0, 1.5, 17.5, 20.0, 14.5)
    );

    public FlagCrateBlock(BlockBehaviour.Properties properties) {
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
