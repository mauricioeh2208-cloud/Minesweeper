package org.shouto.minesweeper.minesweeper.entity;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class MineExplosionEntity extends Entity implements GeoEntity {
    private static final String TAG_LIFE_TICKS = "life_ticks";
    private static final String EXPLOSION_CONTROLLER = "explosion_controller";
    public static final int ANIMATION_DURATION_TICKS = 24;
    private static final int DEFAULT_LIFE_TICKS = ANIMATION_DURATION_TICKS;
    private static final RawAnimation EXPLOSION_ANIMATION = RawAnimation.begin().thenPlay("animacion");

    private AnimatableInstanceCache animatableCache;
    private AnimatableManager<MineExplosionEntity> fallbackManager;
    private int lifeTicks = DEFAULT_LIFE_TICKS;

    public MineExplosionEntity(EntityType<? extends MineExplosionEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
        this.setNoGravity(true);
        this.animatableCache = GeckoLibUtil.createInstanceCache(this);
    }

    public static MineExplosionEntity spawn(ServerLevel level, Vec3 position, int lifeTicks) {
        MineExplosionEntity explosion = new MineExplosionEntity(
                org.shouto.minesweeper.minesweeper.registry.MinesweeperEntities.MINE_EXPLOSION_ENTITY,
                level
        );
        explosion.setPos(position.x, position.y, position.z);
        explosion.setLifeTicks(lifeTicks);
        level.addFreshEntity(explosion);
        return explosion;
    }

    public void setLifeTicks(int ticks) {
        this.lifeTicks = Math.max(1, Math.min(ANIMATION_DURATION_TICKS, ticks));
    }

    @Override
    public void tick() {
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);
        this.setNoGravity(true);

        if (--this.lifeTicks <= 0) {
            this.discard();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Pose getPose() {
        return Pose.STANDING;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.lifeTicks = input.getIntOr(TAG_LIFE_TICKS, DEFAULT_LIFE_TICKS);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt(TAG_LIFE_TICKS, this.lifeTicks);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(EXPLOSION_CONTROLLER, test -> test.setAndContinue(EXPLOSION_ANIMATION)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        if (this.animatableCache == null) {
            this.animatableCache = GeckoLibUtil.createInstanceCache(this);
        }
        return this.animatableCache;
    }

    public AnimatableManager<MineExplosionEntity> getOrCreateFallbackManager() {
        if (this.fallbackManager == null) {
            this.fallbackManager = new AnimatableManager<>(this);
            this.fallbackManager.addController(
                    new AnimationController<>(EXPLOSION_CONTROLLER, test -> test.setAndContinue(EXPLOSION_ANIMATION))
            );
        }
        return this.fallbackManager;
    }
}
