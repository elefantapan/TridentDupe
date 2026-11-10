
package your.addon.modules;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class AnvilQuickCombine extends Module {

    public AnvilQuickCombine() {
        super(Categories.Misc, "anvil-quick-combine",
            "Moves your selected hotbar item into an anvil and takes the result.");
    }

    @Override
    public void onActivate() {
        try {
            if (mc.player == null || mc.gameMode == null)
                return;

            // Must have an anvil open.
            if (!(mc.player.containerMenu instanceof AnvilMenu menu))
                return;

            int selected = mc.player.getInventory().getSelectedSlot();
            int hotbarSlot = 30 + selected;

            ItemStack source = mc.player.getInventory().getItem(selected);

            if (source.isEmpty()) {
                warning("Selected hotbar slot is empty.");
                return;
            }

            // Require one item to make the replacement predictable.
            if (source.getCount() != 1) {
                warning("Selected stack must contain exactly one item.");
                return;
            }

            // Check whether the current input can be safely swapped.
            ItemStack existing = menu.getSlot(0).getItem();

            if (!existing.isEmpty() && existing.getCount() != 1) {
                warning("First anvil slot contains a stack.");
                return;
            }

            int syncId = menu.containerId;

            // 1. Pick up selected hotbar item.
            click(syncId, hotbarSlot);

            // 2. Replace first anvil input.
            click(syncId, 0);

            // 3. Return displaced item to the original hotbar slot.
            click(syncId, hotbarSlot);

            // 4. Take the output.
            if (!menu.getSlot(2).getItem().isEmpty()) {
                click(syncId, 2);
            }

        } finally {
            // This is a one-shot module.
            toggle();
        }
    }

    private void click(int syncId, int slot) {
        mc.gameMode.handleContainerInput(
            syncId,
            slot,
            0,
            ContainerInput.PICKUP,
            mc.player
        );
    }
}
