package com.MoofIT.Minecraft.Cenotaph;

import java.util.ArrayList;

import org.bukkit.Location;
import org.bukkit.block.Block;

public class CenotaphUtil {
	
	public static boolean isTombBlock(Block block) {
		TombBlock tBlock = Cenotaph.tombBlockList.get(block.getLocation());
		if (tBlock == null)
			return false;
		else 
			return true;
	}
	
	public static TombBlock getTombBlock(Block block) {
		TombBlock tBlock = Cenotaph.tombBlockList.get(block.getLocation());
		if (tBlock == null)
			return null;
		else 
			return tBlock;
	}
	
	public static TombBlock getBlockByIndex(String playerName,String index) {
		ArrayList<TombBlock> pList = Cenotaph.playerTombList.get(playerName);
		int slot = 0;

		if (pList == null) return null;

		try {
			slot = Integer.parseInt(index);
		} catch (NumberFormatException e) {
			slot = pList.size();
		}
		slot -= 1;

		if (slot < 0 || slot >= pList.size()) return null;

		return pList.get(slot);
	}
	
	public static String[] signMessage = {
		CenotaphSettings.signLine1(),
		CenotaphSettings.signLine2(),
		CenotaphSettings.signLine3(),
		CenotaphSettings.signLine4()
	};

	/**
	 * Converts a rotation to a cardinal direction name.
	 * Author: sk89q - Original function from CommandBook plugin
	 * @param rot
	 * @return
	 */
	public static String getDirection(double rot) {
		if (0 <= rot && rot < 22.5) {
			return "North";
		} else if (22.5 <= rot && rot < 67.5) {
			return "Northeast";
		} else if (67.5 <= rot && rot < 112.5) {
			return "East";
		} else if (112.5 <= rot && rot < 157.5) {
			return "Southeast";
		} else if (157.5 <= rot && rot < 202.5) {
			return "South";
		} else if (202.5 <= rot && rot < 247.5) {
			return "Southwest";
		} else if (247.5 <= rot && rot < 292.5) {
			return "West";
		} else if (292.5 <= rot && rot < 337.5) {
			return "Northwest";
		} else if (337.5 <= rot && rot < 360.0) {
			return "North";
		} else {
			return null;
		}
	}
	
	public static String convertTime(int s) {
		String formatted = Integer.toString(s);
		if (s >= 86400) {
			formatted = String.format("%dd %d:%02d:%02d", s/86400, (s%86400)/3600, (s%3600)/60, s%60);
		}
		else if (s >= 3600) {
			formatted = String.format("%d:%02d:%02d", s/3600, (s%3600)/60, (s%60));
		}
		else if (s > 60) {
			formatted = String.format("%02d:%02d", s/60, s%60);
		}
		else if (s == 60) {
			formatted = "01:00";
		}
		else if (s < 60) {
			formatted = String.format("%02d:%02d", 00, s%60);
		}
		return formatted;
	}
	
	/**
	 * Gets the Yaw from one location to another in relation to North.
	 *
	 */
	public static double getYawTo(Location from, Location to) {
		final int distX = to.getBlockX() - from.getBlockX();
		final int distZ = to.getBlockZ() - from.getBlockZ();
		double degrees = Math.toDegrees(Math.atan2(-distX, distZ));
		degrees += 180;
		return degrees;
	}
	
	

}
