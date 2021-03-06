package me.mrdaniel.mmo.listeners;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.item.DurabilityData;
import org.spongepowered.api.data.type.HandTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.action.FishingEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.entity.TameEntityEvent;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.item.ItemTypes;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.entity.Hotbar;
import org.spongepowered.api.item.inventory.property.SlotIndex;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import me.mrdaniel.mmo.Main;
import me.mrdaniel.mmo.enums.Ability;
import me.mrdaniel.mmo.enums.RepairStore;
import me.mrdaniel.mmo.enums.SkillType;
import me.mrdaniel.mmo.io.players.MMOPlayer;
import me.mrdaniel.mmo.skills.SkillAction;
import me.mrdaniel.mmo.utils.ItemInfo;
import me.mrdaniel.mmo.utils.ItemUtils;

public class PlayerListener {
	
	public PlayerListener() {
		delays = new ArrayList<String>();
	}
	private ArrayList<String> delays;
	
	@Listener(order = Order.LAST)
	public void onInteractRight(InteractBlockEvent.Secondary e, @Root Player p) {
		if (e.isCancelled()) { return; }
		BlockSnapshot bss = e.getTargetBlock();
		BlockState bs = bss.getState();
		
		Optional<Location<World>> locOpt = bss.getLocation();
		if (locOpt.isPresent()) {
			Location<World> loc = locOpt.get();
			
			if (delays.contains(p.getName())) { return; }
			
			if (bs.getType() == BlockTypes.GOLD_BLOCK) {
				if (p.getItemInHand(HandTypes.MAIN_HAND).isPresent() && p.getItemInHand(HandTypes.MAIN_HAND).get() != ItemTypes.NONE) {
					ItemStack hand = p.getItemInHand(HandTypes.MAIN_HAND).get();
					if (!RepairStore.getInstance().items.containsKey(hand.getItem().getType())) { return; }
					
					ItemInfo ir = RepairStore.getInstance().items.get(hand.getItem().getType());
					
					e.setCancelled(true);
					
					delays.add(p.getName());
					Main.getInstance().getGame().getScheduler().createTaskBuilder().delay(100, TimeUnit.MILLISECONDS).execute(()-> { if (delays.contains(p.getName())) { delays.remove(p.getName()); } }).submit(Main.getInstance());
					
					MMOPlayer mmop = Main.getInstance().getMMOPlayerDatabase().getOrCreatePlayer(p.getUniqueId());
					p.setItemInHand(HandTypes.MAIN_HAND, null);
					Location<World> dropLoc = new Location<World>(loc.getExtent(), loc.getX(), loc.getY()+1, loc.getZ());
					
					double maxItems = ir.amount;
					int level = mmop.getSkills().getSkill(SkillType.SALVAGE).level;
					double percent = Main.getInstance().getValueStore().getAbility(Ability.SALVAGE).getSecond().getValue(level);
					if (percent > 100) { percent = 100; }
					int amount = (int) (maxItems * (percent/100.0));
					if (amount < 1) { amount = 1; }
					
					ItemStack item = ItemUtils.build(ir.type, amount, 0);
					ItemUtils.drop(item, dropLoc);
					mmop.process(new SkillAction(SkillType.SALVAGE, ir.exp));
				}
				else { p.sendMessage(Main.getInstance().getConfig().PREFIX.concat(Text.of(TextColors.GREEN, "Click the Gold Block with an item to salvage it"))); }
			}
			else if (bs.getType() == BlockTypes.IRON_BLOCK) {
				if (p.getItemInHand(HandTypes.MAIN_HAND).isPresent() && p.getItemInHand(HandTypes.MAIN_HAND).get() != ItemTypes.NONE) {
					ItemStack hand = p.getItemInHand(HandTypes.MAIN_HAND).get();
					if (!RepairStore.getInstance().items.containsKey(hand.getItem().getType())) { return; }
					
					ItemInfo ir = RepairStore.getInstance().items.get(hand.getItem().getType());
					if (!hand.get(Keys.ITEM_DURABILITY).isPresent()) { return; }
					if (hand.get(Keys.ITEM_DURABILITY).get() >= ir.maxDura-1) { return; }
					e.setCancelled(true);
					
					delays.add(p.getName());
					Main.getInstance().getGame().getScheduler().createTaskBuilder().delay(100, TimeUnit.MILLISECONDS).execute(()-> { if (delays.contains(p.getName())) { delays.remove(p.getName()); } }).submit(Main.getInstance());
					
					MMOPlayer mmop = Main.getInstance().getMMOPlayerDatabase().getOrCreatePlayer(p.getUniqueId());
					
					for (int i = 0; i < 9; i++) {
						Optional<ItemStack> itemOpt = p.getInventory().query(Hotbar.class).query(new SlotIndex(i)).peek();
						if (itemOpt.isPresent() && itemOpt.get().getItem().getType() == ir.type) {
							ItemStack item = itemOpt.get();
							
							// If we have only one of the item, then clear the slot. Otherwise, reduce the quantity by one.
							Inventory inventorySlot = p.getInventory().query(Hotbar.class).query(new SlotIndex(i));
							if (item.getQuantity() > 1) {
								item.setQuantity(item.getQuantity() - 1);
								inventorySlot.set(item);
							}
							else {
								inventorySlot.clear();
							}
							
							DurabilityData data = hand.getOrCreate(DurabilityData.class).get();
							
							int extra = (int) ((Main.getInstance().getValueStore().getAbility(Ability.REPAIR).getSecond().getValue(mmop.getSkills().getSkill(SkillType.REPAIR).level)/100.0)*ir.maxDura);
							int newDura = data.durability().get() + extra;
							if (newDura > ir.maxDura) { newDura = ir.maxDura; }
							hand.offer(Keys.ITEM_DURABILITY, newDura);
							p.setItemInHand(HandTypes.MAIN_HAND, hand);
							mmop.process(new SkillAction(SkillType.REPAIR, ir.exp/2));
							return;
						}
					}
				}
			}
		}
	}

	@Listener(order = Order.LAST)
	public void onFishing(FishingEvent.Stop e, @Root Player p) {
		if (e.isCancelled()) { return; }
		if (e.getItemStackTransaction() != null) {
			if (e.getItemStackTransaction().get(0).getFinal() != null && e.getItemStackTransaction().get(0).getFinal().getType() != ItemTypes.NONE) {
				MMOPlayer mmop = Main.getInstance().getMMOPlayerDatabase().getOrCreatePlayer(p.getUniqueId());
				mmop.process(new SkillAction(SkillType.FISHING, Main.getInstance().getValueStore().getFishing()));
				Drops.getInstance().FishingTreasure(p, mmop);
			}
		}
	}

	@Listener(order = Order.LAST)
	public void onTaming(TameEntityEvent e, @Root Player p) {
		if (e.isCancelled()) { return; }
		MMOPlayer mmop = Main.getInstance().getMMOPlayerDatabase().getOrCreatePlayer(p.getUniqueId());
		mmop.process(new SkillAction(SkillType.TAMING, Main.getInstance().getValueStore().getTaming()));
	}

	@Listener
	public void onPlayerJoin(ClientConnectionEvent.Join e) {
		MMOPlayer mmop = Main.getInstance().getMMOPlayerDatabase().getOrCreatePlayer(e.getTargetEntity().getUniqueId());
		mmop.updateTop(e.getTargetEntity().getName());
	}

	@Listener
	public void onPlayerQuit(ClientConnectionEvent.Disconnect e) {
		MMOPlayer mmop = Main.getInstance().getMMOPlayerDatabase().getOrCreatePlayer(e.getTargetEntity().getUniqueId());
		mmop.save();
		mmop.updateTop(e.getTargetEntity().getName());
		Main.getInstance().getMMOPlayerDatabase().unload(UUID.fromString(mmop.getUUID()));
	}
}