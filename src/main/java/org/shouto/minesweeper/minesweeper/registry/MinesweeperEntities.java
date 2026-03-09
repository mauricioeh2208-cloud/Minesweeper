package org.shouto.minesweeper.minesweeper.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import org.shouto.minesweeper.minesweeper.Minesweeper;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;

public final class MinesweeperEntities {
    public static final EntityType<MineExplosionEntity> MINE_EXPLOSION_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "mine_explosion_entity"),
            EntityType.Builder
                    .of(MineExplosionEntity::new, MobCategory.MISC)
                    .sized(2.6F, 2.6F)
                    .eyeHeight(0.1F)
                    .clientTrackingRange(64)
                    .updateInterval(1)
                    .noSummon()
                    .noSave()
                    .build(ResourceKey.create(
                            Registries.ENTITY_TYPE,
                            Identifier.fromNamespaceAndPath(Minesweeper.MOD_ID, "mine_explosion_entity")
                    ))
    );

    private MinesweeperEntities() {
    }

    public static void initialize() {
    }
}
