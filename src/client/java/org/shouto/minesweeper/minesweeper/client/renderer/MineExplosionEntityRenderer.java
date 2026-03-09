package org.shouto.minesweeper.minesweeper.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.shouto.minesweeper.minesweeper.client.model.MineExplosionGeoModel;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

import java.util.HashMap;
import java.util.Map;

public class MineExplosionEntityRenderer extends GeoEntityRenderer<MineExplosionEntity, MineExplosionEntityRenderer.MineExplosionRenderState> {
    public MineExplosionEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new MineExplosionGeoModel());
        this.shadowRadius = 0.0F;
        this.withScale(1.8F);
    }

    @Override
    public MineExplosionRenderState createRenderState(MineExplosionEntity animatable, Void relatedObject) {
        return new MineExplosionRenderState(animatable.getOrCreateFallbackManager());
    }

    @Override
    public void captureDefaultRenderState(
            MineExplosionEntity animatable,
            Void relatedObject,
            MineExplosionRenderState renderState,
            float partialTick
    ) {
        super.captureDefaultRenderState(animatable, relatedObject, renderState, partialTick);

        // GeckoLib 5.4.1 puede dejar ANIMATABLE_MANAGER en null para esta entidad MISC.
        // Forzamos manager + tiempo de entidad para evitar crash y mantener animacion progresiva.
        renderState.addGeckolibData(DataTickets.ANIMATABLE_MANAGER, animatable.getOrCreateFallbackManager());
        renderState.addGeckolibData(DataTickets.ANIMATABLE_INSTANCE_ID, getInstanceId(animatable, relatedObject));
        renderState.addGeckolibData(DataTickets.TICK, (double) animatable.tickCount + partialTick);
    }

    public static final class MineExplosionRenderState extends EntityRenderState implements GeoRenderState {
        private final AnimatableManager<?> fallbackManager;
        private final Map<DataTicket<?>, Object> geckoData = new HashMap<>();

        public MineExplosionRenderState(AnimatableManager<?> fallbackManager) {
            this.fallbackManager = fallbackManager;
            this.geckoData.put(DataTickets.ANIMATABLE_MANAGER, fallbackManager);
        }

        @Override
        public Map<DataTicket<?>, Object> getDataMap() {
            return geckoData;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <D> D getGeckolibData(DataTicket<D> dataTicket) {
            if (dataTicket == DataTickets.ANIMATABLE_MANAGER || DataTickets.ANIMATABLE_MANAGER.equals(dataTicket)) {
                return (D) this.fallbackManager;
            }

            Object data = this.geckoData.get(dataTicket);
            return (D) data;
        }
    }
}
