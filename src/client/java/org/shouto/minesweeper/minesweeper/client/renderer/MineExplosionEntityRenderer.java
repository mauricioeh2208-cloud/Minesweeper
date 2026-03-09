package org.shouto.minesweeper.minesweeper.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.shouto.minesweeper.minesweeper.client.model.MineExplosionGeoModel;
import org.shouto.minesweeper.minesweeper.entity.MineExplosionEntity;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MineExplosionEntityRenderer extends GeoEntityRenderer<MineExplosionEntity, MineExplosionEntityRenderer.MineExplosionRenderState> {
    private static final Pattern FRAME_BONE_PATTERN = Pattern.compile("(?i)frame[^0-9]*(\\d+)");
    private static final int TICKS_PER_FRAME = 5;

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

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<MineExplosionRenderState> renderPassInfo, BoneSnapshots snapshots) {
        List<String> frameBones = collectFrameBones(renderPassInfo);
        if (frameBones.isEmpty()) {
            return;
        }

        int totalCycleTicks = Math.max(1, frameBones.size() * TICKS_PER_FRAME);
        int tick = (int) ((System.currentTimeMillis() / 50L) % totalCycleTicks);
        int visibleFrame = Math.max(0, Math.min(frameBones.size() - 1, tick / TICKS_PER_FRAME));

        for (int i = 0; i < frameBones.size(); i++) {
            final boolean visible = i == visibleFrame;
            snapshots.ifPresent(frameBones.get(i), snapshot -> setFrameVisibility(snapshot, visible));
        }
    }

    private static List<String> collectFrameBones(RenderPassInfo<MineExplosionRenderState> renderPassInfo) {
        List<String> frameBones = new ArrayList<>();
        List<String> allBones = new ArrayList<>(renderPassInfo.model().boneLookup().get().keySet());
        for (String boneName : allBones) {
            if (FRAME_BONE_PATTERN.matcher(boneName).find()) {
                frameBones.add(boneName);
            }
        }

        if (frameBones.isEmpty()) {
            for (String boneName : allBones) {
                if (!"model".equalsIgnoreCase(boneName)) {
                    frameBones.add(boneName);
                }
            }
        }

        frameBones.sort((a, b) -> Integer.compare(extractFrameNumber(a), extractFrameNumber(b)));
        return frameBones;
    }

    private static int extractFrameNumber(String boneName) {
        Matcher matcher = FRAME_BONE_PATTERN.matcher(boneName);
        if (!matcher.find()) {
            return Integer.MAX_VALUE;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static void setFrameVisibility(BoneSnapshot snapshot, boolean visible) {
        if (visible) {
            snapshot.setScale(1.0F, 1.0F, 1.0F).skipRender(false).skipChildrenRender(false);
        } else {
            snapshot.setScale(0.0F, 0.0F, 0.0F).skipRender(true).skipChildrenRender(true);
        }
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
