package org.shouto.minesweeper.minesweeper.client;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.shouto.minesweeper.minesweeper.client.renderer.MineExplosionEntityRenderer;
import org.shouto.minesweeper.minesweeper.registry.MinesweeperEntities;

public final class MinesweeperClientEntities {
    private MinesweeperClientEntities() {
    }

    public static void initialize() {
        EntityRendererRegistry.register(MinesweeperEntities.MINE_EXPLOSION_ENTITY, MineExplosionEntityRenderer::new);
    }
}
