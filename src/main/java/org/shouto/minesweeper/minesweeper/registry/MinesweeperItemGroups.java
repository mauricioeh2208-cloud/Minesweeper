package org.shouto.minesweeper.minesweeper.registry;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.shouto.minesweeper.minesweeper.Minesweeper;

public final class MinesweeperItemGroups {
    public static final CreativeModeTab MINESWEEPER_TAB = Registry.register(
            BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "tab"),
            FabricItemGroup.builder()
                    .title(Component.translatable("itemGroup.minesweeper.tab"))
                    .icon(() -> new ItemStack(MinesweeperItems.MINE_BLOCK_ITEM))
                    .displayItems((parameters, output) -> {
                        output.accept(MinesweeperItems.MINE_BLOCK_ITEM);
                        output.accept(MinesweeperItems.MINE_OPEN_BLOCK_ITEM);
                        output.accept(MinesweeperItems.FLAG_BLOCK_ITEM);
                        output.accept(MinesweeperItems.FLAG_CRATE_BLOCK_ITEM);
                        output.accept(MinesweeperItems.HIDDEN_BLOCK_1_ITEM);
                        output.accept(MinesweeperItems.HIDDEN_BLOCK_2_ITEM);
                        output.accept(MinesweeperItems.NUMBER_BLOCK_0_ITEM);
                        output.accept(MinesweeperItems.NUMBER_BLOCK_1_ITEM);
                        output.accept(MinesweeperItems.NUMBER_BLOCK_2_ITEM);
                        output.accept(MinesweeperItems.NUMBER_BLOCK_3_ITEM);
                        output.accept(MinesweeperItems.NUMBER_BLOCK_4_ITEM);
                        output.accept(MinesweeperItems.NUMBER_BLOCK_5_ITEM);
                        output.accept(MinesweeperItems.INTERACCION);
                        output.accept(MinesweeperItems.DESACTIVADOR_MINA);
                        output.accept(MinesweeperItems.TOTEM_INMORTALIDAD);
                        output.accept(MinesweeperItems.BANDERA);
                    })
                    .build()
    );

    private MinesweeperItemGroups() {
    }

    public static void initialize() {
    }
}
