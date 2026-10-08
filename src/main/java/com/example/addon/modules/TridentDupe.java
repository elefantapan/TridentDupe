package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TridentDupe extends Module {
    // Coded by Killet Laztec & Ionar :3 (Ported to 26.2/26.3)
    
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    
    private final Setting<Double> delay = sgGeneral.add(new DoubleSetting.Builder()
        .name("dupe-delay")
        .description("Delay between each dupe cycle. Unlikely to need increasing.")
        .defaultValue(0)
        .build()
    );

    private final Setting<Double> chargeDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("charge-delay")
        .description("Delay between trident charge and throw. Increase if experiencing issues/lag.")
        .defaultValue(5)
        .build()
    );

    private final Setting<Boolean> dropTridents = sgGeneral.add(new BoolSetting.Builder()
        .name("dropTridents")
        .description("Drops tridents in your last hotbar slot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> durabilityManagement = sgGeneral.add(new BoolSetting.Builder()
        .name("durabilityManagement")
        .description("(More AFKable) Attempts to dupe the highest durability trident in your hotbar.")
        .defaultValue(true)
        .build()
    );

    private boolean cancel = true;
    private final List<Pair<Long, Runnable>> scheduledTasks = new ArrayList<>();
    private final List<Pair<Double, Runnable>> scheduledTasks2 = new ArrayList<>();

    public TridentDupe() {
        super(AddonTemplate.CATEGORY, "trident-dupe", "Dupes tridents in first hotbar slot. / / Killet / / Laztec / / Ionar");
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null) return;

        // Anpassat för paketstrukturen i 26.2/26.3
        if (event.packet instanceof PlayerMoveC2SPacket 
            || event.packet instanceof CloseHandledScreenC2SPacket) {
            return;
        }

        if (!(event.packet instanceof ClickSlotC2SPacket) && !(event.packet instanceof PlayerActionC2SPacket)) {
            return;
        }
        
        if (!cancel) return;

        event.cancel();
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        scheduledTasks.clear();
        scheduledTasks2.clear();
        cancel = true;
        dupe();
    }

    @Override
    public void onDeactivate() {
        cancel = false;
        scheduledTasks.clear();
        scheduledTasks2.clear();
    }

    private void dupe() {
        if (mc.player == null || mc.interactionManager == null) return;

        int lowestHotbarSlot = 0;
        int lowestHotbarDamage = 1000;
        
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TRIDENT || stack.getItem() == Items.BOW) {
                int currentHotbarDamage = stack.getDamage();
                if (lowestHotbarDamage > currentHotbarDamage) { 
                    lowestHotbarSlot = i; 
                    lowestHotbarDamage = currentHotbarDamage;
                }
            }
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        cancel = true;

        int finalLowestHotbarSlot = lowestHotbarSlot;
        scheduleTask(() -> {
            if (mc.player == null || mc.interactionManager == null || mc.getNetworkHandler() == null) return;
            cancel = false;

            int syncId = mc.player.currentScreenHandler.syncId;

            if (durabilityManagement.get()) {
                if (finalLowestHotbarSlot != 0) {
                    mc.interactionManager.clickSlot(syncId, 44, 0, SlotActionType.SWAP, mc.player);
                    if (dropTridents.get()) {
                        mc.interactionManager.clickSlot(syncId, 44, 0, SlotActionType.THROW, mc.player);
                    }
                    mc.interactionManager.clickSlot(syncId, (36 + finalLowestHotbarSlot), 0, SlotActionType.SWAP, mc.player);
                }
            }

            mc.interactionManager.clickSlot(syncId, 36, 0, SlotActionType.SWAP, mc.player);

            // Uppdaterat paketanrop för att matcha Mojangs paketsignatur i 26.2/26.3
            PlayerActionC2SPacket packet2 = new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, 
                BlockPos.ORIGIN, 
                Direction.DOWN
            );
            mc.getNetworkHandler().sendPacket(packet2);

            if (dropTridents.get()) {
                mc.interactionManager.clickSlot(syncId, 44, 0, SlotActionType.THROW, mc.player);
            }

            cancel = true;
            scheduleTask2(this::dupe, delay.get() * 100);
        }, chargeDelay.get() * 100);
    }

    public void scheduleTask(Runnable task, double tridentThrowTime) {
        long executeTime = System.currentTimeMillis() + (long) tridentThrowTime;
        scheduledTasks.add(new Pair<>(executeTime, task));
    }

    public void scheduleTask2(Runnable task, double delayMillis) {
        double executeTime = System.currentTimeMillis() + delayMillis;
        scheduledTasks2.add(new Pair<>(executeTime, task));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long currentTime = System.currentTimeMillis();
        
        Iterator<Pair<Long, Runnable>> iterator = scheduledTasks.iterator();
        while (iterator.hasNext()) {
            Pair<Long, Runnable> entry = iterator.next();
            if (entry.getLeft() <= currentTime) {
                entry.getRight().run();
                iterator.remove();
            }
        }

        Iterator<Pair<Double, Runnable>> iterator2 = scheduledTasks2.iterator();
        while (iterator2.hasNext()) {
            Pair<Double, Runnable> entry = iterator2.next();
            if (entry.getLeft() <= currentTime) {
                entry.getRight().run();
                iterator2.remove();
            }
        }
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        toggle();
    }

    @EventHandler
    private void onScreenOpen(OpenScreenEvent event) {
        if (event.screen instanceof DisconnectedScreen) {
            toggle();
        }
    }
}
