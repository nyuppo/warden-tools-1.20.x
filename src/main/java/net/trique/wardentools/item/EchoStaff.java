package net.trique.wardentools.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

public class EchoStaff extends Item {
    private final Multimap<EntityAttribute, EntityAttributeModifier> attributeModifiers;

    public EchoStaff(Settings settings) {
        super(settings);

        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> builder = ImmutableMultimap.builder();
        builder.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 1.0f, EntityAttributeModifier.Operation.ADDITION));
        builder.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", -1.0f, EntityAttributeModifier.Operation.ADDITION));
        this.attributeModifiers = builder.build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        if(slot == EquipmentSlot.MAINHAND) {
            return attributeModifiers;
        }

        return super.getAttributeModifiers(slot);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return super.use(world, user, hand);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.BLOCK;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) {
        return 30;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        super.usageTick(world, user, stack, remainingUseTicks);

        if(getMaxUseTime(stack) - remainingUseTicks == 1) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 3.0f, 1.0f);
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if(!world.isClient) {
            spawnSonicBoom(world, user);

            if(user instanceof PlayerEntity player) {
                player.getItemCooldownManager().set(this, 20 * 5);
                stack.damage(1, user, x -> x.sendToolBreakStatus(Hand.MAIN_HAND));
            }
        }

        return super.finishUsing(stack, world, user);
    }

    private void spawnSonicBoom(World world, LivingEntity user) {
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0f, 1.0f);

        float heightOffset = 1.6f;
        int distance = 16;
        Vec3d target = user.getPos().add(user.getRotationVector().multiply(distance));
        Vec3d source = user.getPos().add(0.0, heightOffset, 0.0);
        Vec3d offsetToTarget = target.subtract(source);
        Vec3d normalized = offsetToTarget.normalize();

        Set<Entity> hit = new HashSet<>();
        for (int particleIndex = 1; particleIndex < MathHelper.floor(offsetToTarget.length()) + 7; ++particleIndex) {
            Vec3d particlePos = source.add(normalized.multiply(particleIndex));
            ((ServerWorld)world).spawnParticles(ParticleTypes.SONIC_BOOM, particlePos.x, particlePos.y, particlePos.z, 1, 0.0, 0.0, 0.0, 0.0);

            hit.addAll(world.getEntitiesByClass(LivingEntity.class, new Box(new BlockPos((int)particlePos.getX(), (int)particlePos.getY(), (int)particlePos.getZ())).expand(2), it -> !(it instanceof WolfEntity)));
        }

        hit.remove(user);

        for (Entity hitTarget : hit) {
            if(hitTarget instanceof LivingEntity living) {
                living.damage(world.getDamageSources().sonicBoom(user), 10.0f);
                double vertical = 0.5 * (1.0 - living.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
                double horizontal = 2.5 * (1.0 - living.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
                living.addVelocity(normalized.getX() * horizontal, normalized.getY() * vertical, normalized.getZ() * horizontal);
            }
        }
    }
}
