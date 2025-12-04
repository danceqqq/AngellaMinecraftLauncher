package com.angella.mixins;

import com.angella.events.DeathEventHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    
    @Inject(method = "onDeath(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("TAIL"))
    private void onPlayerDeath(net.minecraft.entity.damage.DamageSource damageSource, CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        Text deathMessage = player.getDamageTracker().getDeathMessage();
        if (deathMessage != null) {
            DeathEventHandler.onPlayerDeath(player, deathMessage);
        }
    }
}

