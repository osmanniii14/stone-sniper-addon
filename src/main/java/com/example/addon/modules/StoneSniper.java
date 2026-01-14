package com.example.addon.modules; 

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class StoneSniper extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minTotalPayment = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-total-payment")
        .description("Minimum total payment to accept stone orders.")
        .defaultValue(5000)
        .min(0)
        .sliderMax(50000)
        .build()
    );

    private final Setting<Boolean> autoDeliver = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-deliver")
        .description("Automatically deliver stone when high-paying order is found.")
        .defaultValue(true)
        .build()
    );

    private final Setting<DeliveryMode> deliveryMode = sgGeneral.add(new EnumSetting.Builder<DeliveryMode>()
        .name("delivery-mode")
        .description("How to deliver stone.")
        .defaultValue(DeliveryMode.Normal)
        .build()
    );

    private final Setting<Integer> deliverySpeed = sgGeneral.add(new IntSetting.Builder()
        .name("delivery-speed")
        .description("How many stacks to deliver per tick (higher = faster).")
        .defaultValue(3)
        .min(1)
        .max(9)
        .sliderMax(9)
        .build()
    );

    private final Setting<Integer> emptyCheckDelay = sgGeneral.add(new IntSetting.Builder()
        .name("empty-check-delay")
        .description("Seconds to wait before confirming inventory is empty.")
        .defaultValue(5)
        .min(1)
        .max(10)
        .sliderMax(10)
        .build()
    );

    private boolean hasDelivered = false;
    private long lastStoneFoundTime = 0;
    private boolean isDelivering = false;

    public StoneSniper() {
        super(Categories.Misc, "stone-sniper", "Automatically snipes high-paying stone orders on DonutSMP.");
    }

    @Override
    public void onActivate() {
        hasDelivered = false;
        lastStoneFoundTime = 0;
        isDelivering = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.currentScreen == null) {
            isDelivering = false;
            return;
        }
        if (!(mc.currentScreen instanceof GenericContainerScreen)) {
            isDelivering = false;
            return;
        }

        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        String title = screen.getTitle().getString();

        if (!title.toLowerCase().contains("stone") && !title.toLowerCase().contains("order")) {
            return;
        }

        clickEmptyMap(screen);

        if (autoDeliver.get()) {
            if (!isDelivering && !hasDelivered) {
                findAndDeliverStone(screen);
            } else if (isDelivering) {
                continueDumping(screen);
            }
        }
    }

    private void clickEmptyMap(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            
            if (stack.getItem() == Items.MAP) {
                Text name = stack.getName();
                String nameStr = name.getString().toLowerCase();
                
                if (nameStr.contains("empty") || nameStr.contains("refresh") || nameStr.contains("update")) {
                    mc.interactionManager.clickSlot(
                        screen.getScreenHandler().syncId,
                        i,
                        0,
                        SlotActionType.PICKUP,
                        mc.player
                    );
                    return;
                }
            }
        }
    }

    private void findAndDeliverStone(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            ItemStack stack = screen.getScreenHandler().getSlot(i).getStack();
            
            if (stack.getItem() == Items.STONE || stack.getItem() == Items.COBBLESTONE) {
                OrderInfo orderInfo = parseStoneOrder(stack);
                
                if (orderInfo != null && orderInfo.totalPayment >= minTotalPayment.get()) {
                    info("Found high-paying stone order!");
                    info("Total Payment: " + formatPayment(orderInfo.totalPayment));
                    info("Progress: $" + formatPayment(orderInfo.currentPaid) + "/" + formatPayment(orderInfo.totalPayment));
                    info("Delivered: " + orderInfo.delivered + "/" + orderInfo.amountNeeded);
                    
                    if (orderInfo.currentPaid < orderInfo.totalPayment) {
                        mc.interactionManager.clickSlot(
                            screen.getScreenHandler().syncId,
                            i,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                        );
                        
                        isDelivering = true;
                        lastStoneFoundTime = System.currentTimeMillis();
                        return;
                    } else {
                        info("Order already fully paid!");
                    }
                }
            }
        }
    }

    private void continueDumping(GenericContainerScreen screen) {
        boolean foundStone = false;
        int delivered = 0;

        for (int i = 0; i < mc.player.getInventory().size() && delivered < deliverySpeed.get(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            
            boolean shouldDeliver = false;
            
            if (deliveryMode.get() == DeliveryMode.Normal) {
                shouldDeliver = stack.getItem() == Items.STONE || stack.getItem() == Items.COBBLESTONE;
            } else if (deliveryMode.get() == DeliveryMode.Shulkerbox) {
                shouldDeliver = isShulkerBox(stack) || stack.getItem() == Items.STONE || stack.getItem() == Items.COBBLESTONE;
            }
            
            if (shouldDeliver) {
                foundStone = true;
                lastStoneFoundTime = System.currentTimeMillis();
                
                mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
                delivered++;
            }
        }

        long timeSinceLastStone = System.currentTimeMillis() - lastStoneFoundTime;
        long delayMs = emptyCheckDelay.get() * 1000L;

        if (!foundStone && timeSinceLastStone > delayMs) {
            info("No more stone found for " + emptyCheckDelay.get() + " seconds. Stopping delivery.");
            isDelivering = false;
            hasDelivered = true;
        } else if (foundStone) {
            info("Delivering stone... (" + delivered + " stacks this tick)");
        }
    }

    private boolean isShulkerBox(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.SHULKER_BOX ||
               item == Items.WHITE_SHULKER_BOX ||
               item == Items.ORANGE_SHULKER_BOX ||
               item == Items.MAGENTA_SHULKER_BOX ||
               item == Items.LIGHT_BLUE_SHULKER_BOX ||
               item == Items.YELLOW_SHULKER_BOX ||
               item == Items.LIME_SHULKER_BOX ||
               item == Items.PINK_SHULKER_BOX ||
               item == Items.GRAY_SHULKER_BOX ||
               item == Items.LIGHT_GRAY_SHULKER_BOX ||
               item == Items.CYAN_SHULKER_BOX ||
               item == Items.PURPLE_SHULKER_BOX ||
               item == Items.BLUE_SHULKER_BOX ||
               item == Items.BROWN_SHULKER_BOX ||
               item == Items.GREEN_SHULKER_BOX ||
               item == Items.RED_SHULKER_BOX ||
               item == Items.BLACK_SHULKER_BOX;
    }

    private OrderInfo parseStoneOrder(ItemStack stack) {
        try {
            var lore = stack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
            
            OrderInfo info = new OrderInfo();
            int pricePerStone = 0;
            
            for (Text line : lore) {
                String lineStr = line.getString();
                
                if (lineStr.contains("Each")) {
                    pricePerStone = parsePaymentAmount(lineStr);
                }
                
                if (lineStr.contains("Delivered")) {
                    String[] parts = lineStr.split("/");
                    if (parts.length >= 2) {
                        String deliveredStr = parts[0].replaceAll("[^0-9]", "");
                        if (!deliveredStr.isEmpty()) {
                            info.delivered = Integer.parseInt(deliveredStr);
                        }
                        
                        String[] amountParts = parts[1].split(" ");
                        String amountStr = amountParts[0].replaceAll("[^0-9]", "");
                        if (!amountStr.isEmpty()) {
                            info.amountNeeded = Integer.parseInt(amountStr);
                        }
                    }
                }
                
                if (lineStr.contains("Paid")) {
                    if (lineStr.contains("/")) {
                        String[] parts = lineStr.split("/");
                        if (parts.length >= 2) {
                            String paymentPart = parts[1].replace("Paid", "").trim();
                            int parsed = parsePaymentAmount(paymentPart);
                            if (parsed > 0) {
                                info.totalPayment = parsed;
                            }
                        }
                    }
                    
                    if (lineStr.startsWith("$")) {
                        String currentPaidStr = lineStr.substring(1).split("/")[0].trim();
                        int currentPaid = parsePaymentAmount(currentPaidStr);
                        info.currentPaid = currentPaid;
                    }
                }
            }
            
            if (info.totalPayment == 0 && pricePerStone > 0 && info.amountNeeded > 0) {
                info.totalPayment = pricePerStone * info.amountNeeded;
            }
            
            if (info.totalPayment > 0) {
                return info;
            }
            
        } catch (Exception e) {
            error("Error parsing stone order: " + e.getMessage());
        }
        
        return null;
    }

    private int parsePaymentAmount(String text) {
        try {
            String lowerText = text.toLowerCase().replaceAll("[^0-9km.]", "");
            
            if (lowerText.contains("m")) {
                String numStr = lowerText.replace("m", "");
                double value = Double.parseDouble(numStr);
                return (int) (value * 1_000_000);
            } else if (lowerText.contains("k")) {
                String numStr = lowerText.replace("k", "");
                double value = Double.parseDouble(numStr);
                return (int) (value * 1_000);
            } else if (!lowerText.isEmpty()) {
                return Integer.parseInt(lowerText);
            }
        } catch (Exception e) {
        }
        return 0;
    }
    
    private String formatPayment(int amount) {
        if (amount >= 1_000_000) {
            return String.format("%.1fm", amount / 1_000_000.0);
        } else if (amount >= 1_000) {
            return String.format("%.0fk", amount / 1_000.0);
        } else {
            return String.valueOf(amount);
        }
    }

    public enum DeliveryMode {
        Normal,
        Shulkerbox
    }

    private static class OrderInfo {
        int amountNeeded = 0;
        int delivered = 0;
        int totalPayment = 0;
        int currentPaid = 0;
    }
}
