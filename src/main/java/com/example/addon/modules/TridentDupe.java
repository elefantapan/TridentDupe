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
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TridentDupe extends Module {
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
        .description("Attempts to dupe the highest durability trident in your hotbar.")
        .defaultValue(true)
        .build()
    );

    private boolean cancel = true;
    private final List<ScheduledTask> scheduledTasks = new ArrayList<>();
    private final List<ScheduledTask> scheduledTasks2 = new ArrayList<>();

    public TridentDupe() {
        super(
            AddonTemplate.CATEGORY,
            "trident-dupe",
            "Dupes tridents in the first hotbar slot."
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null) return;

        if (event.packet instanceof ServerboundPlayerActionPacket
            || event.packet instanceof ServerboundContainerClosePacket) {
            return;
        }

        if (!(event.packet instanceof ServerboundContainerClickPacket)) {
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
        if (mc.player == null || mc.gameMode == null) return;

        int lowestHotbarSlot = 0;
        int lowestHotbarDamage = Integer.MAX_VALUE;

        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getItem(i);

            if (stack.is(Items.TRIDENT) || stack.is(Items.BOW)) {
                int currentHotbarDamage = stack.getDamageValue();

                if (currentHotbarDamage < lowestHotbarDamage) {
                    lowestHotbarSlot = i;
                    lowestHotbarDamage = currentHotbarDamage;
                }
            }
        }

        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        cancel = true;

        int finalLowestHotbarSlot = lowestHotbarSlot;

        scheduleTask(() -> {
            if (mc.player == null || mc.gameMode == null) return;

            cancel = false;

            int syncId = mc.player.containerMenu.containerId;

            if (durabilityManagement.get() && finalLowestHotbarSlot != 0) {
                mc.gameMode.handleInventoryMouseClick(
                    syncId,
                    44,
                    0,
                    ClickType.SWAP,
                    mc.player
                );

                if (dropTridents.get()) {
                    mc.gameMode.handleInventoryMouseClick(
                        syncId,
                        44,
                        0,
                        ClickType.THROW,
                        mc.player
                    );
                }

                mc.gameMode.handleInventoryMouseClick(
                    syncId,
                    36 + finalLowestHotbarSlot,
                    0,
                    ClickType.SWAP,
                    mc.player
                );
            }

            mc.gameMode.handleInventoryMouseClick(
                syncId,
                36,
                0,
                ClickType.SWAP,
                mc.player
            );

            ServerboundPlayerActionPacket packet = new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
                BlockPos.ZERO,
                Direction.DOWN
            );

            mc.getConnection().send(packet);

            if (dropTridents.get()) {
                mc.gameMode.handleInventoryMouseClick(
                    syncId,
                    44,
                    0,
                    ClickType.THROW,
                    mc.player
                );
            }

            cancel = true;

            scheduleTask2(
                this::dupe,
                delay.get() * 100
            );
        }, chargeDelay.get() * 100);
    }

    private void scheduleTask(Runnable task, double delayMillis) {
        scheduledTasks.add(
            new ScheduledTask(
                System.currentTimeMillis() + (long) delayMillis,
                task
            )
        );
    }

    private void scheduleTask2(Runnable task, double delayMillis) {
        scheduledTasks2.add(
            new ScheduledTask(
                System.currentTimeMillis() + (long) delayMillis,
                task
            )
        );
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        long currentTime = System.currentTimeMillis();

        Iterator<ScheduledTask> iterator = scheduledTasks.iterator();

        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();

            if (task.executeTime <= currentTime) {
                task.task.run();
                iterator.remove();
            }
        }

        Iterator<ScheduledTask> iterator2 = scheduledTasks2.iterator();

        while (iterator2.hasNext()) {
            ScheduledTask task = iterator2.next();

            if (task.executeTime <= currentTime) {
                task.task.run();
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

    private static class ScheduledTask {
        private final long executeTime;
        private final Runnable task;

        private ScheduledTask(long executeTime, Runnable task) {
            this.executeTime = executeTime;
            this.task = task;
        }
    }
}
