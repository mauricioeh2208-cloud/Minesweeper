package org.shouto.minesweeper.minesweeper.client.model;

import net.minecraft.resources.Identifier;
import org.shouto.minesweeper.minesweeper.Minesweeper;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public class MineExplosionGeoModel extends GeoModel<MineExplosionEntity> {
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(
            Minesweeper.MOD_ID,
            "explosion_model_animation_gecko"
    );
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(
            Minesweeper.MOD_ID,
            "explosion_model_geckolib"
    );
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            Minesweeper.MOD_ID,
            "textures/entity/explosion_texture_gecko.png"
    );

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(MineExplosionEntity animatable) {
        return ANIMATION;
    }
}
