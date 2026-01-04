package com.stonesniper.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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

    private boolean hasDelivered = false;

    public StoneSniper() {
        super(Categories.Misc, "stone-sniper", "Automatically snipes high-paying stone orders on DonutSMP.");
    }

    @Override
    public void onActivate() {
        hasDelivered = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.currentScreen == null) return;
        if (!(mc.currentScreen instanceof GenericContainerScreen)) return;

        GenericContainerScreen screen = (GenericContainerScreen) mc.currentScreen;
        String title = screen.getTitle().getString();

        if (!title.toLowerCase().contains("stone") && !title.toLowerCase().contains("order")) {
            return;
        }

        clickEmptyMap(screen);

        if (autoDeliver.get() && !hasDelivered) {
            findAndDeliverStone(screen);
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
                    info("Total Payment: " + formatPayment(orderInfo.totalPayment) + " (" + orderInfo.amountNeeded + " stones × " + formatPayment(orderInfo.totalPayment / orderInfo.amountNeeded) + " each)");
                    info("Progress: $" + formatPayment(orderInfo.currentPaid) + "/" + formatPayment(orderInfo.totalPayment) + " Paid");
                    info("Delivered: " + orderInfo.delivered + "/" + orderInfo.amountNeeded);
                    
                    if (orderInfo.currentPaid < orderInfo.totalPayment) {
                        mc.interactionManager.clickSlot(
                            screen.getScreenHandler().syncId,
                            i,
                            0,
                            SlotActionType.PICKUP,
                            mc.player
                        );
                        
                        hasDelivered = true;
                        dumpStoneToGui(screen);
                        return;
                    } else {
                        info("Order already fully paid!");
                    }
                }
            }
        }
    }

    private OrderInfo parseStoneOrder(ItemStack stack) {
        try {
            String name = stack.getName().getString();
            var lore = stack.getTooltip(mc.player, 
                mc.options.advancedItemTooltips ? 
                net.minecraft.client.item.TooltipContext.Default.ADVANCED : 
                net.minecraft.client.item.TooltipContext.Default.BASIC);
            
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

    private void dumpStoneToGui(GenericContainerScreen screen) {
        info("Dumping stone from inventory...");
        
        int stoneCount = 0;
        
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            
            if (stack.getItem() == Items.STONE || stack.getItem() == Items.COBBLESTONE) {
                stoneCount += stack.getCount();
                
                mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
                );
            }
        }
        
        if (stoneCount > 0) {
            info("Delivered " + stoneCount + " stone!");
        } else {
            warning("No stone found in inventory!");
        }
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
            } else {
                return Integer.parseInt(lowerText);
            }
        } catch (Exception e) {
            return 0;
        }
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

    private static class OrderInfo {
        int amountNeeded = 0;
        int delivered = 0;
        int totalPayment = 0;
        int currentPaid = 0;
    }
}
