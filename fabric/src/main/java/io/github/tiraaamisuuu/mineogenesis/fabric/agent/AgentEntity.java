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
import net.minecraft.item.Items;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.entity.ItemEntity;
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
    private final SimpleInventory inventory = new SimpleInventory(1);
    private Runnable interactionTick;

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
    public AgentInput currentInput() { return input; }
    public void setInteractionTick(Runnable tick) { interactionTick = tick; }
    public int collectedLogs() {
        var stack = inventory.getStack(0);
        return stack.isOf(Items.OAK_LOG) ? stack.getCount() : 0;
    }

    /** The trial, not a proximity search, supplies its owned loot entity. */
    public void collectOwnedLog(ItemEntity item) {
        if (!item.getStack().isOf(Items.OAK_LOG)) return;
        int before = item.getStack().getCount();
        ItemStack remaining = inventory.addStack(item.getStack().copy());
        int taken = before - remaining.getCount();
        if (taken <= 0) return;
        sendPickup(item, taken);
        if (remaining.isEmpty()) item.discard();
        else item.setStack(remaining);
    }

    @Override
    public void tickMovement() {
        if (!dataTracker.get(RUNNING)) {
            setVelocity(Vec3d.ZERO);
            sidewaysSpeed = forwardSpeed = 0;
            jumping = false;
        } else if (!getWorld().isClient) {
            setYaw(MathHelper.wrapDegrees(getYaw() + input.yawDelta()));
            setPitch(MathHelper.clamp(getPitch() + input.pitchDelta(), -90, 90));
            headYaw = bodyYaw = getYaw();
            setMovementSpeed((float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
            forwardSpeed = input.forward();
            sidewaysSpeed = input.strafe();
            jumping = input.jump();
        }
        // Keep vanilla interpolation so observers can receive the final paused pose.
        super.tickMovement();
        if (!dataTracker.get(RUNNING)) setVelocity(Vec3d.ZERO);
        else if (!getWorld().isClient && interactionTick != null) interactionTick.run();
    }

    @Override
    public void travel(Vec3d movementInput) {
        // Gate physics on both sides without suppressing network interpolation.
        if (!dataTracker.get(RUNNING)) {
            setVelocity(Vec3d.ZERO);
            return;
        }
        super.travel(movementInput);
    }

    // Full player equipment is outside this checkpoint; the acquisition slot is separate.
    @Override public Iterable<ItemStack> getArmorItems() { return List.of(); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) { }
    @Override public Arm getMainArm() { return Arm.RIGHT; }
}
