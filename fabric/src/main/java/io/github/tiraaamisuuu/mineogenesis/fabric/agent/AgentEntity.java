package io.github.tiraaamisuuu.mineogenesis.fabric.agent;

import io.github.tiraaamisuuu.mineogenesis.core.AgentInput;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Transient humanoid body. Deliberately has no mob goals, navigation or policy. */
public final class AgentEntity extends LivingEntity {
    private static final TrackedData<Boolean> RUNNING = DataTracker.registerData(
            AgentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private AgentInput input = AgentInput.IDLE;

    public AgentEntity(EntityType<? extends AgentEntity> type, World world) {
        super(type, world);
        // A1 tests actuators, not survival. The world still continues while paused.
        setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.1)
                .add(EntityAttributes.GENERIC_STEP_HEIGHT, 0.6);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(RUNNING, false);
    }

    public void setRunning(boolean running) {
        dataTracker.set(RUNNING, running);
        if (!running) {
            input = AgentInput.IDLE;
            setVelocity(Vec3d.ZERO);
            sidewaysSpeed = forwardSpeed = 0;
            jumping = false;
            velocityModified = true;
        }
    }

    public void setInput(AgentInput input) { this.input = input; }

    @Override
    public void tickMovement() {
        // Gate physics on BOTH sides, so a paused body does not fall on the client.
        if (!dataTracker.get(RUNNING)) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        if (!getWorld().isClient) {
            setYaw(MathHelper.wrapDegrees(getYaw() + input.yawDelta()));
            headYaw = bodyYaw = getYaw();
            setMovementSpeed((float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
            forwardSpeed = input.forward();
            sidewaysSpeed = input.strafe();
            jumping = input.jump();
        }
        super.tickMovement();
    }

    // Equipment/inventory semantics are intentionally outside this proof of concept.
    @Override public Iterable<ItemStack> getArmorItems() { return List.of(); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) { }
    @Override public Arm getMainArm() { return Arm.RIGHT; }
}
