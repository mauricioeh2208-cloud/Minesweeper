package org.shouto.minesweeper.minesweeper.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.shouto.minesweeper.minesweeper.Minesweeper;
import org.shouto.minesweeper.minesweeper.block.FlagBlock;
import org.shouto.minesweeper.minesweeper.block.FlagCrateBlock;
import org.shouto.minesweeper.minesweeper.block.MineBlock;
import org.shouto.minesweeper.minesweeper.block.MineExplosionFxBlock;
import org.shouto.minesweeper.minesweeper.block.MineOpenBlock;

import java.util.function.Function;

public final class MinesweeperBlocks {
    public static final Block MINE_BLOCK = register(
            "mine_block",
            MineBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );
    public static final Block MINE_OPEN_BLOCK = register(
            "mine_open_block",
            MineOpenBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.8F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
    );
    public static final Block FLAG_BLOCK = register(
            "flag_block",
            FlagBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.5F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
    );
    public static final Block FLAG_CRATE_BLOCK = register(
            "flag_crate_block",
            FlagCrateBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(1.2F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
    );
    public static final Block MINE_EXPLOSION_FX_BLOCK = register(
            "mine_explosion_fx_block",
            MineExplosionFxBlock::new,
            BlockBehaviour.Properties.of()
                    .strength(0.0F)
                    .sound(SoundType.WOOL)
                    .replaceable()
                    .noOcclusion()
                    .instabreak()
    );
    public static final Block HIDDEN_BLOCK_1 = register(
            "hidden_block_1",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block HIDDEN_BLOCK_2 = register(
            "hidden_block_2",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block NUMBER_BLOCK_0 = register(
            "number_block_0",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block NUMBER_BLOCK_1 = register(
            "number_block_1",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block NUMBER_BLOCK_2 = register(
            "number_block_2",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block NUMBER_BLOCK_3 = register(
            "number_block_3",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block NUMBER_BLOCK_4 = register(
            "number_block_4",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );
    public static final Block NUMBER_BLOCK_5 = register(
            "number_block_5",
            Block::new,
            BlockBehaviour.Properties.of()
                    .strength(1.0F)
                    .sound(SoundType.STONE)
    );

    public static final Block[] NUMBER_BLOCKS = new Block[]{
            NUMBER_BLOCK_0,
            NUMBER_BLOCK_1,
            NUMBER_BLOCK_2,
            NUMBER_BLOCK_3,
            NUMBER_BLOCK_4,
            NUMBER_BLOCK_5
    };

    private MinesweeperBlocks() {
    }

    private static Block register(
            String name,
            Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties properties
    ) {
        Identifier id = Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, name);
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        Block block = factory.apply(properties.setId(key));
        return Registry.register(
                BuiltInRegistries.BLOCK,
                id,
                block
        );
    }

    public static void initialize() {
    }
}
