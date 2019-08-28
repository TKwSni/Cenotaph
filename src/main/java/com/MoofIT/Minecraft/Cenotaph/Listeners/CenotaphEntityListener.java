package com.MoofIT.Minecraft.Cenotaph.Listeners;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Chest.Type;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import com.MoofIT.Minecraft.Cenotaph.Cenotaph;
import com.MoofIT.Minecraft.Cenotaph.CenotaphSettings;
import com.MoofIT.Minecraft.Cenotaph.CenotaphUtil;
import com.MoofIT.Minecraft.Cenotaph.TombBlock;
import com.MoofIT.Minecraft.Cenotaph.WorldGuardWrapper;
import net.milkbowl.vault.economy.EconomyResponse;

public class CenotaphEntityListener implements Listener {
	private Cenotaph plugin;
	private HashSet<Material> blockNoReplaceList = new HashSet<Material>();

	public CenotaphEntityListener(Cenotaph instance) {
		this.plugin = instance;
		initNoReplaceList();
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.isCancelled()) return;
		if (!(event.getEntity() instanceof Player))return;

		Player player = (Player)event.getEntity();
		// Add them to the list if they're about to die
		if (player.getHealth() - event.getDamage() <= 0) {
			Cenotaph.deathCause.put(player.getName(), event);
		}
	}

	@EventHandler
	public void onEntityExplode(EntityExplodeEvent event) {
		if (event.isCancelled()) return;
		if (!CenotaphSettings.creeperProtection() && !CenotaphSettings.tntProtection()) return;
		for (Block block : event.blockList()) {
			TombBlock tBlock = Cenotaph.tombBlockList.get(block.getLocation());
			if (tBlock != null) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityDeath(EntityDeathEvent event) {
		if (!(event.getEntity() instanceof Player)) return;
		Player p = (Player)event.getEntity();
		World world = p.getWorld();

		if (!p.hasPermission("cenotaph.use")) return;

		if (event.getDrops().size() == 0) {
			plugin.sendMessage(p, "Inv empty.");
			return;
		}

		if (CenotaphSettings.disabledWorlds().contains(world.getName())) {
			plugin.sendMessage(p,"Cenotaph disabled in " + world.getName() + ". Inv dropped.");
			return;
		}


		// Get the current player location.
		Location loc = p.getLocation();
		Block block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

		// If we run into something we don't want to destroy, go one up.
		if (blockNoReplaceList.contains(block.getType())) {
			block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
		}

		//Don't create the chest if it or its sign would be in the void
		if (CenotaphSettings.voidCheck() && ((CenotaphSettings.cenotaphSign() && block.getY() > p.getWorld().getMaxHeight() - 1) || (!CenotaphSettings.cenotaphSign() && block.getY() > p.getWorld().getMaxHeight()) || p.getLocation().getY() < 1)) {
			plugin.sendMessage(p, "Chest would be in the Void. Inv dropped.");
			return;
		}

		//WorldGuard support
		if (Cenotaph.worldguardEnabled){
			//plugin.getLogger().info("Checking WorlGuard...");
			if (!WorldGuardWrapper.canBuild(p)){
				plugin.sendMessage(p, "In a WorldGuard protected area. Inv dropped.");
				return;
			}
		}
		
		if (Cenotaph.economyEnabled) {
			//Check balance
			if (!p.hasPermission("cenotaph.nocost") && CenotaphSettings.cenotaphCost() > 0){
				if (Cenotaph.econ.getBalance(p) < CenotaphSettings.cenotaphCost()){
					plugin.sendMessage(p, "Not enough money! Inv dropped.");
					return;
				}
			}
		}

		// Check if the player has a chest.
		int pChestCount = 0;
		int pSignCount = 0;
		for (ItemStack item : event.getDrops()) {
			if (item == null) continue;
			if (item.getType() == Material.CHEST) pChestCount += item.getAmount();

			for(Material mat: Tag.SIGNS.getValues()) {
				if(item.getType() == mat)
					pSignCount += item.getAmount();
			}
		}

		if (pChestCount == 0 && !p.hasPermission("cenotaph.freechest")) {
			plugin.sendMessage(p, "No chest! Inv dropped.");
			return;
		}

		// Check if we can replace the block.
		block = findPlace(block,false);
		if ( block == null ) {
			plugin.sendMessage(p, "No room to place chest. Inv dropped.");
			return;
		}

		// Check if there is a nearby chest
		if (CenotaphSettings.noInterfere() && checkChest(block)) {
			plugin.sendMessage(p, "Existing chest interfering with chest placement. Inv dropped.");
			return;
		}

		int removeChestCount = 1;
		int removeSignCount = 0;

		// Do the check for a large chest block here so we can check for interference
		Block lBlock = findLarge(block);

		// Set the current block to a chest, init some variables for later use.
		block.setType(Material.CHEST);
		// We're running into issues with 1.3 where we can't cast to a Chest :(
		BlockState state = block.getState();
		if (!(state instanceof Chest)) {
			plugin.sendMessage(p, "Could not access chest. Inv dropped.");
			return;
		}
		Chest sChest = (Chest)state;
		Chest lChest = null;
		int slot = 0;
		int maxSlot = sChest.getInventory().getSize();
		BlockFace relativeFace = BlockFace.NORTH;
		// Check if they need a large chest.
		if (event.getDrops().size() > maxSlot) {
			// If they are allowed spawn a large chest to catch their entire inventory.
			if (lBlock != null && p.hasPermission("cenotaph.large")) {
				removeChestCount = 2;
				// Check if the player has enough chests
				if (pChestCount >= removeChestCount || p.hasPermission("cenotaph.freechest")) {
					lBlock.setType(Material.CHEST);
					// This fun stuff is required post-1.13 when they made chests not snap together. 
					org.bukkit.block.data.type.Chest blockChestData = (org.bukkit.block.data.type.Chest) block.getBlockData();
					org.bukkit.block.data.type.Chest lBlockChestData = (org.bukkit.block.data.type.Chest) lBlock.getBlockData();
					relativeFace = block.getFace(lBlock);
					if (relativeFace.equals(BlockFace.WEST)) {
						blockChestData.setFacing(BlockFace.SOUTH);
						lBlockChestData.setFacing(BlockFace.SOUTH);
						blockChestData.setType(Type.LEFT);
						lBlockChestData.setType(Type.RIGHT);
					} else if (relativeFace.equals(BlockFace.EAST)) {
						//Chests face North by default so Eastwards lBlock doesn't need the chest faced.
						blockChestData.setType(Type.LEFT);
						lBlockChestData.setType(Type.RIGHT);
					} else if (relativeFace.equals(BlockFace.SOUTH)) {
						blockChestData.setFacing(BlockFace.EAST);
						lBlockChestData.setFacing(BlockFace.EAST);
						blockChestData.setType(Type.LEFT);
						lBlockChestData.setType(Type.RIGHT);
					} else if (relativeFace.equals(BlockFace.NORTH)) {
						blockChestData.setFacing(BlockFace.WEST);
						lBlockChestData.setFacing(BlockFace.WEST);
						blockChestData.setType(Type.LEFT);
						lBlockChestData.setType(Type.RIGHT);							
					}
					block.setBlockData(blockChestData,true);
					lBlock.setBlockData(lBlockChestData,true);
					
					lChest = (Chest)lBlock.getState();
					maxSlot = maxSlot * 2;
				} else {
					removeChestCount = 1;
				}
			}
		}

		// Don't remove any chests if they get a free one.
		if (p.hasPermission("cenotaph.freechest"))
			removeChestCount = 0;

		// Check if we have signs enabled, if the player can use signs, and if the player has a sign or gets a free sign
		Block sBlock = null;
		if (CenotaphSettings.cenotaphSign() && p.hasPermission("cenotaph.sign") &&
			(pSignCount > 0 || p.hasPermission("cenotaph.freesign"))) {
			// Find a place to put the sign, then place the sign.
			sBlock = sChest.getWorld().getBlockAt(sChest.getX(), sChest.getY() + 1, sChest.getZ());
			if (canReplace(sBlock.getType())) {
				createSign(sBlock, p, relativeFace);
				removeSignCount += 1;
			} else if (lChest != null) {
				sBlock = lChest.getWorld().getBlockAt(lChest.getX(), lChest.getY() + 1, lChest.getZ());
				if (canReplace(sBlock.getType())) {
					createSign(sBlock, p, relativeFace);
					removeSignCount += 1;
				}
			}
		}
		// Don't remove a sign if they get a free one
		if (p.hasPermission("cenotaph.freesign"))
			removeSignCount -= 1;

		// Create a TombBlock for this tombstone
		TombBlock tBlock = new TombBlock(sChest.getBlock(), (lChest != null) ? lChest.getBlock() : null, sBlock, (System.currentTimeMillis() / 1000), p.getLevel() + 1, p.getUniqueId());

		// Add tombstone to list
		Cenotaph.tombList.offer(tBlock);

		// Add tombstone blocks to tombBlockList
		Cenotaph.tombBlockList.put(tBlock.getBlock().getLocation(), tBlock);
		if (tBlock.getLBlock() != null) Cenotaph.tombBlockList.put(tBlock.getLBlock().getLocation(), tBlock);
		if (tBlock.getSign() != null) Cenotaph.tombBlockList.put(tBlock.getSign().getLocation(), tBlock);

		// Add tombstone to player lookup list
		ArrayList<TombBlock> pList = Cenotaph.playerTombList.get(p.getName());
		if (pList == null) {
			pList = new ArrayList<TombBlock>();
			Cenotaph.playerTombList.put(p.getName(), pList);
		}
		pList.add(tBlock);

		plugin.saveCenotaphList(p.getWorld().getName());

		// Next get the players inventory using the getDrops() method.
		for (Iterator<ItemStack> iter = event.getDrops().listIterator(); iter.hasNext();) {
			ItemStack item = iter.next();
			if (item == null) continue;
			// Take the chest(s)
			if (removeChestCount > 0 && item.getType() == Material.CHEST) {
				if (item.getAmount() >= removeChestCount) {
					item.setAmount(item.getAmount() - removeChestCount);
					removeChestCount = 0;
				} else {
					removeChestCount -= item.getAmount();
					item.setAmount(0);
				}
				if (item.getAmount() == 0) {
					iter.remove();
					continue;
				}
			}


			for(Material mat: Tag.SIGNS.getValues()) {
				if(item.getType() == mat)
					pSignCount += item.getAmount();
			}

			// Take a sign
			if (removeSignCount > 0 && Tag.SIGNS.isTagged( item.getType() )){
				item.setAmount(item.getAmount() - 1);
				removeSignCount -= 1;
				if (item.getAmount() == 0) {
					iter.remove();
					continue;
				}
			}

			// Add items to chest if not full.
			if (slot < maxSlot) {
				if (slot >= sChest.getInventory().getSize()) {
					if (lChest == null) continue;
					lChest.getInventory().setItem(slot % sChest.getInventory().getSize(), item);
				} else {
					sChest.getInventory().setItem(slot, item);
				}
				iter.remove();
				slot++;
			} else if (removeChestCount == 0) break;
		}

		int breakTime = (CenotaphSettings.levelBasedRemoval() ? Math.min(p.getLevel() + 1 * CenotaphSettings.levelBasedTime(), CenotaphSettings.cenotaphRemoveTime()) : CenotaphSettings.cenotaphRemoveTime());
		String msg = "Inv stored. ";
		if (event.getDrops().size() > 0) msg += ChatColor.YELLOW + "Overflow: " + ChatColor.WHITE + event.getDrops().size() + " ";
		msg += ChatColor.YELLOW + "Security: ";
		if (CenotaphSettings.securityEnable()) {
			msg += ChatColor.WHITE + "Enabled ";					
			if (CenotaphSettings.securityRemove()) msg += ChatColor.YELLOW + "SecTime: " + ChatColor.WHITE + CenotaphUtil.convertTime(CenotaphSettings.securityTimeOut()) + " ";
		}
		else msg += "None ";
		msg += ChatColor.YELLOW + "BreakTime: " + ChatColor.WHITE + (CenotaphSettings.cenotaphRemove() ? CenotaphUtil.convertTime(breakTime) : "Inf") + " ";
		if (CenotaphSettings.removeWhenEmpty() || CenotaphSettings.keepUntilEmpty()) {
			msg += ChatColor.YELLOW + "BreakOverride: " + ChatColor.WHITE;
			if (CenotaphSettings.removeWhenEmpty()) msg += "Break on empty";
			if (CenotaphSettings.removeWhenEmpty() && CenotaphSettings.keepUntilEmpty()) msg += " & ";
			if (CenotaphSettings.keepUntilEmpty()) msg += "Keep until empty";
		}
		plugin.sendMessage(p, msg);
		
		//Subtract money
		if (!p.hasPermission("cenotaph.nocost") && CenotaphSettings.cenotaphCost() > 0){
			EconomyResponse r = Cenotaph.econ.withdrawPlayer(Bukkit.getOfflinePlayer(p.getUniqueId()), CenotaphSettings.cenotaphCost());
			if (r.transactionSuccess()){
				plugin.sendMessage(p, CenotaphSettings.cenotaphCost() + " " + Cenotaph.econ.currencyNamePlural() + " has been taken from your account.");
			}
		}
	}

	private void createSign(Block signBlock, Player p, BlockFace bf) {
		String date = new SimpleDateFormat(CenotaphSettings.dateFormat()).format(new Date());
		String time = new SimpleDateFormat(CenotaphSettings.timeFormat()).format(new Date());
		String name = p.getName();
		String reason = "Unknown";

		EntityDamageEvent dmg = Cenotaph.deathCause.get(name);
		if (dmg != null) {
			Cenotaph.deathCause.remove(name);
			reason = getCause(dmg);
		}

		signBlock.setType(Material.OAK_SIGN);
		//Lets make the sign appear to look downwards towards the foot of the long chests.
		org.bukkit.block.data.type.Sign sBlockData = (org.bukkit.block.data.type.Sign) signBlock.getBlockData();
		sBlockData.setRotation(bf);
		signBlock.setBlockData(sBlockData);
		
		final Sign sign = (Sign)signBlock.getState();

		for (int x = 0; x < 4; x++) {
			String line = CenotaphUtil.signMessage[x];
			line = line.replace("{name}", name);
			line = line.replace("{date}", date);
			line = line.replace("{time}", time);
			line = line.replace("{reason}", reason);

			if (line.length() > 15) line = line.substring(0, 15);
			sign.setLine(x, line);
		}

		plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
			public void run() {
				sign.update();
			}
		});
	}

	private void initNoReplaceList() {
		for(Material mat: Tag.SLABS.getValues()) {
			blockNoReplaceList.add(mat);
		}
		blockNoReplaceList.add(Material.TORCH);
		blockNoReplaceList.add(Material.REDSTONE_WIRE);
		for(Material mat: Tag.RAILS.getValues()) {
			blockNoReplaceList.add(mat);
		}
		blockNoReplaceList.add(Material.STONE_PRESSURE_PLATE);
		for(Material mat:Tag.WOODEN_PRESSURE_PLATES.getValues()) {
			blockNoReplaceList.add(mat);
		}
		blockNoReplaceList.add(Material.REDSTONE_TORCH);
		blockNoReplaceList.add(Material.CAKE);
	}

	private String getCause(EntityDamageEvent dmg) {
		try {
			switch (dmg.getCause()) {
				case ENTITY_ATTACK:
				{
					EntityDamageByEntityEvent event = (EntityDamageByEntityEvent)dmg;
					Entity e = event.getDamager();
					if (e == null) {
						return "Dispenser";
					} else if (e instanceof Player) {
						return ((Player) e).getDisplayName();
					} else {
						return e.getName();
					}
				}
				case CONTACT:
					return "Cactus";
				case SUFFOCATION:
					return "Suffocation";
				case FALL:
					return "Fall";
				case FIRE:
					return "Fire";
				case FIRE_TICK:
					return "Burning";
				case LAVA:
					return "Lava";
				case DROWNING:
					return "Drowning";
				case BLOCK_EXPLOSION:
					return "Explosion";
				case ENTITY_EXPLOSION:
				{
					try {
						EntityDamageByEntityEvent event = (EntityDamageByEntityEvent)dmg;
						Entity e = event.getDamager();
						if (e instanceof TNTPrimed) return "TNT";
						else if (e instanceof Fireball) return "Ghast";
						else return "Creeper";
					} catch (Exception e) {
						return "Explosion";
					}
				}
				case VOID:
					return "The Void";
				case LIGHTNING:
					return "Lightning";
				default:
					return "Unknown";
			}
		} catch (NullPointerException e) {
			Cenotaph.log.severe("[Cenotaph] Error processing death cause: " + dmg.getCause().toString());
			e.printStackTrace();
			return ChatColor.RED + "ERROR" + ChatColor.BLACK;
		}
	}

	/**
	 * Find a block near the base block to place the tombstone
	 * @param base
	 * @return
	 */
	Block findPlace(Block base, Boolean CardinalSearch) {
		if (canReplace(base.getType())) return base;
		int baseX = base.getX();
		int baseY = base.getY();
		int baseZ = base.getZ();
		World w = base.getWorld();

		if (CardinalSearch) {
			Block b;
			b = w.getBlockAt(baseX - 1, baseY, baseZ);
			if (canReplace(b.getType())) return b;

			b = w.getBlockAt(baseX + 1, baseY, baseZ);
			if (canReplace(b.getType())) return b;

			b = w.getBlockAt(baseX, baseY, baseZ - 1);
			if (canReplace(b.getType())) return b;

			b = w.getBlockAt(baseX, baseY, baseZ + 1);
			if (canReplace(b.getType())) return b;

			b = w.getBlockAt(baseX, baseY, baseZ);
			if (canReplace(b.getType())) return b;

			return null;
		}

		for (int x = baseX - 1; x < baseX + 1; x++) {
			for (int z = baseZ - 1; z < baseZ + 1; z++) {
				Block b = w.getBlockAt(x, baseY, z);
				if (canReplace(b.getType())) return b;
			}
		}

		if(CenotaphSettings.oneBlockUpCheck()) {
			//Check block one up, in case of Carpeting/
			for (int x = baseX - 1; x < baseX + 1; x++) {
				for (int z = baseZ - 1; z < baseZ + 1; z++) {
					Block b = w.getBlockAt(x, baseY + 1, z);
					if (canReplace(b.getType())) return b;
				}
			}
		}

		return null;
	}

	Boolean canReplace(Material mat) {
		return (mat == Material.AIR ||
				mat == Material.ACACIA_SAPLING ||
				mat == Material.BIRCH_SAPLING ||
				mat == Material.OAK_SAPLING ||
				mat == Material.DARK_OAK_SAPLING ||
				mat == Material.JUNGLE_SAPLING ||
				mat == Material.SPRUCE_SAPLING ||
				mat == Material.WATER ||
				mat == Material.LAVA ||
				mat == Material.SUNFLOWER ||
				mat == Material.POPPY ||
				mat == Material.BROWN_MUSHROOM ||
				mat == Material.RED_MUSHROOM ||
				mat == Material.FIRE ||
				mat == Material.WHEAT ||
				mat == Material.SNOW ||
				mat == Material.SUGAR_CANE ||
				mat == Material.GRAVEL ||
				mat == Material.SAND);
	}

	Block findLarge(Block base) {
		// Check all 4 sides for air.
		Block exp;
		exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
		if (canReplace(exp.getType()) && (!CenotaphSettings.noInterfere() || !checkChest(exp))) return exp;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
		if (canReplace(exp.getType()) && (!CenotaphSettings.noInterfere() || !checkChest(exp))) return exp;
		exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
		if (canReplace(exp.getType()) && (!CenotaphSettings.noInterfere() || !checkChest(exp))) return exp;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
		if (canReplace(exp.getType()) && (!CenotaphSettings.noInterfere() || !checkChest(exp))) return exp;
		return null;
	}

	boolean checkChest(Block base) {
		// Check all 4 sides for a chest.
		Block exp;
		exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
		if (exp.getType() == Material.CHEST) return true;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
		if (exp.getType() == Material.CHEST) return true;
		exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
		if (exp.getType() == Material.CHEST) return true;
		exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
		if (exp.getType() == Material.CHEST) return true;
		return false;
	}

}
