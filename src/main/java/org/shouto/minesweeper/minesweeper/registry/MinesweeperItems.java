package org.shouto.minesweeper.minesweeper.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.shouto.minesweeper.minesweeper.Minesweeper;
import org.shouto.minesweeper.minesweeper.item.BanderaItem;
import org.shouto.minesweeper.minesweeper.item.DesactivadorMinaItem;
import org.shouto.minesweeper.minesweeper.item.InteraccionItem;

public final class MinesweeperItems {
    public static final Item MINE_BLOCK_ITEM = registerBlockItem("mine_block", MinesweeperBlocks.MINE_BLOCK);
    public static final Item MINE_OPEN_BLOCK_ITEM = registerBlockItem("mine_open_block", MinesweeperBlocks.MINE_OPEN_BLOCK);
    public static final Item FLAG_BLOCK_ITEM = registerBlockItem("flag_block", MinesweeperBlocks.FLAG_BLOCK);
    public static final Item FLAG_CRATE_BLOCK_ITEM = registerBlockItem("flag_crate_block", MinesweeperBlocks.FLAG_CRATE_BLOCK);
    public static final Item HIDDEN_BLOCK_1_ITEM = registerBlockItem("hidden_block_1", MinesweeperBlocks.HIDDEN_BLOCK_1);
    public static final Item HIDDEN_BLOCK_2_ITEM = registerBlockItem("hidden_block_2", MinesweeperBlocks.HIDDEN_BLOCK_2);
    public static final Item NUMBER_BLOCK_0_ITEM = registerBlockItem("number_block_0", MinesweeperBlocks.NUMBER_BLOCK_0);
    public static final Item NUMBER_BLOCK_1_ITEM = registerBlockItem("number_block_1", MinesweeperBlocks.NUMBER_BLOCK_1);
    public static final Item NUMBER_BLOCK_2_ITEM = registerBlockItem("number_block_2", MinesweeperBlocks.NUMBER_BLOCK_2);
    public static final Item NUMBER_BLOCK_3_ITEM = registerBlockItem("number_block_3", MinesweeperBlocks.NUMBER_BLOCK_3);
    public static final Item NUMBER_BLOCK_4_ITEM = registerBlockItem("number_block_4", MinesweeperBlocks.NUMBER_BLOCK_4);
    public static final Item NUMBER_BLOCK_5_ITEM = registerBlockItem("number_block_5", MinesweeperBlocks.NUMBER_BLOCK_5);

    public static final Item TOTEM_INMORTALIDAD = registerSimpleItem("totem_inmortalidad");
    public static final Item DESACTIVADOR_MINA = registerItem("desactivador_mina", DesactivadorMinaItem::new);
    public static final Item INTERACCION = registerItem("interaccion", InteraccionItem::new);
    public static final Item BANDERA = registerItem("bandera", BanderaItem::new);

    private MinesweeperItems() {
    }

    private static Item registerBlockItem(String name, Block block) {
        Identifier id = Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = new BlockItem(block, new Item.Properties().setId(key));
        return Registry.register(
                BuiltInRegistries.ITEM,
                id,
                item
        );
    }

    private static Item registerSimpleItem(String name) {
        return registerItem(name, Item::new);
    }

    private static Item registerItem(String name, java.util.function.Function<Item.Properties, Item> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item item = factory.apply(new Item.Properties().setId(key));
        return Registry.register(BuiltInRegistries.ITEM, id, item);
    }

    public static void initialize() {
    }
}
