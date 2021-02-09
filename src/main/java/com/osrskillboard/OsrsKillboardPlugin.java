package com.osrskillboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.kit.KitType;
import net.runelite.client.account.AccountSession;
import net.runelite.client.account.SessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PlayerLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@PluginDescriptor(
		name = "OsrsKillboard",
		description = "Tracks loot from killing players",
		tags = {"drops", "pvp"},
		enabledByDefault = true
)
public class OsrsKillboardPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private OsrsKillboardConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SessionManager sessionManager;

	private OsrsKillboardPanel panel;
	private NavigationButton navButton;

	@Provides
	OsrsKillboardConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(OsrsKillboardConfig.class);
	}

	@Getter(AccessLevel.PACKAGE)
	private OsrsKillboardClient osrsKillboardClient;

	private static Collection<ItemStack> stack(Collection<ItemStack> items) {
		final List<ItemStack> list = new ArrayList<>();

		for (final ItemStack item : items) {
			int quantity = 0;
			for (final ItemStack i : list) {
				if (i.getId() == item.getId()) {
					quantity = i.getQuantity();
					list.remove(i);
					break;
				}
			}
			if (quantity > 0) {
				list.add(new ItemStack(item.getId(), item.getQuantity() + quantity, item.getLocation()));
			} else {
				list.add(item);
			}
		}

		return list;
	}

	@Override
	protected void startUp() {
		log.info("OsrsKillboard started!");
		osrsKillboardClient = new OsrsKillboardClient();

		panel = new OsrsKillboardPanel(this, itemManager, config);
		spriteManager.getSpriteAsync(SpriteID.PLAYER_KILLER_SKULL, 0, panel::loadHeaderIcon);

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "panel_icon.png");

		navButton = NavigationButton.builder()
				.tooltip("OsrsKillboard")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() {
		log.info("OsrsKillboard stopped!");

		clientToolbar.removeNavigation(navButton);
		osrsKillboardClient = null;
	}

	@Subscribe
	public void onPlayerLootReceived(final PlayerLootReceived playerLootReceived) throws IOException {
		final Player victim = playerLootReceived.getPlayer();
		final Collection<ItemStack> items = playerLootReceived.getItems();
		final String name = victim.getName();
		final int combat = victim.getCombatLevel();
		final OsrsKillboardItem[] entries = buildEntries(stack(items));
		SwingUtilities.invokeLater(() -> panel.add(name, combat, entries));

		osrsKillboardClient = new OsrsKillboardClient();
		JsonObject killJson = buildKillJson(victim, entries);

		osrsKillboardClient.submit(client, victim.getName(), killJson);
	}

	private JsonObject buildKillJson(Player victim, OsrsKillboardItem[] lootItems) {
		JsonObject killJson = new JsonObject();

		Player pker = client.getLocalPlayer();

		// Kill Info
		killJson.addProperty("time", Instant.now().toString());
		killJson.addProperty("world", client.getWorld());
		killJson.addProperty("worldType", client.getWorldType().toString());
		killJson.addProperty("localLocation", pker.getLocalLocation().toString());
		killJson.addProperty("worldLocation", pker.getWorldLocation().toString());

		// Pker Info
		killJson.addProperty("pkerName", pker.getName());
		killJson.addProperty("pkerAccountType", client.getAccountType().toString());
		killJson.addProperty("pkerCombatLevel", pker.getCombatLevel());
		killJson.add("pkerItemsEquipped", getEquippedGearForPlayer(pker));
		killJson.addProperty("pkerIsSkulled", isPlayerSkulled(pker));
		killJson.add("pkerCombatStats", getPlayerSkillsList());

		// Victim Info
		killJson.addProperty("victimName", victim.getName());
		killJson.addProperty("victimCombatLevel", victim.getCombatLevel());
		killJson.add("victimItemsEquipped", getEquippedGearForPlayer(victim));
		killJson.addProperty("victimIsSkulled", isPlayerSkulled(victim));

		// Misc
		killJson.add("loot", getLootAsJson(lootItems));
		killJson.addProperty("lootValue", getLootValue(lootItems));
		killJson.addProperty("victimIsFriend", victim.isFriend());
		killJson.addProperty("victimIsClanMember", victim.isFriendsChatMember());

		return killJson;
	}

	private boolean isPlayerSkulled(Player player) {
		boolean isSkulled = false;
		if (player.getSkullIcon() != null) {
			isSkulled = true;
		}

		return isSkulled;
	}

	private JsonObject getPlayerSkillsList() {
		JsonObject skillJson = new JsonObject();

		skillJson.addProperty("attack", client.getRealSkillLevel(Skill.ATTACK));
		skillJson.addProperty("strength", client.getRealSkillLevel(Skill.STRENGTH));
		skillJson.addProperty("defence", client.getRealSkillLevel(Skill.DEFENCE));
		skillJson.addProperty("hitpoints", client.getRealSkillLevel(Skill.HITPOINTS));
		skillJson.addProperty("prayer", client.getRealSkillLevel(Skill.PRAYER));
		skillJson.addProperty("magic", client.getRealSkillLevel(Skill.MAGIC));
		skillJson.addProperty("ranged", client.getRealSkillLevel(Skill.RANGED));

		return skillJson;
	}

	private JsonArray getLootAsJson(OsrsKillboardItem[] lootItems) {
		JsonArray lootArray = new JsonArray();

		for (OsrsKillboardItem item : lootItems) {
			lootArray.add(buildLootItem(item));
		}

		return lootArray;
	}

	private JsonObject buildLootItem(OsrsKillboardItem item) {
		JsonObject itemJson = new JsonObject();

		itemJson.addProperty("id", item.getId());
		itemJson.addProperty("name", item.getName());
		itemJson.addProperty("qty", item.getQuantity());
		itemJson.addProperty("price", item.getGePrice());

		return itemJson;
	}

	private int getLootValue(OsrsKillboardItem[] lootItems) {
		int lootValue = 0;

		for (OsrsKillboardItem item : lootItems) {
			lootValue += item.getGePrice();
		}

		return lootValue;
	}

	private OsrsKillboardItem buildOsrsKillboardItem(int itemId, int quantity) {
		final ItemComposition itemComposition = itemManager.getItemComposition(itemId);
		final int realItemId = itemComposition.getNote() != -1 ? itemComposition.getLinkedNoteId() : itemId;
		final long price = (long) itemManager.getItemPrice(realItemId) * (long) quantity;
		return new OsrsKillboardItem(itemId, itemComposition.getName(), quantity, price);
	}

	private JsonObject getEquippedGearForPlayer(Player player) {
		ArrayList<Integer> equipmentIds = new ArrayList<>();

		PlayerComposition playerComposition = player.getPlayerComposition();
		equipmentIds.add(playerComposition.getEquipmentId(KitType.HEAD));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.TORSO));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.LEGS));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.BOOTS));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.HANDS));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.SHIELD));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.WEAPON));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.AMULET));
		equipmentIds.add(playerComposition.getEquipmentId(KitType.CAPE));

		ArrayList<OsrsKillboardItem> realItemList = new ArrayList<>();

		for (int equipmentId : equipmentIds) {
			OsrsKillboardItem newItem = buildOsrsKillboardItem(equipmentId, 1);
			realItemList.add(newItem);
		}

		OsrsKillboardItem[] equipmentArrayList = realItemList.toArray(new OsrsKillboardItem[realItemList.size()]);

		return getEquippedGearAsJson(equipmentArrayList);
	}

	private JsonObject getEquippedGearAsJson(OsrsKillboardItem[] equippedItems) {
		final String[] equipmentSlots = {"Head", "Torso", "Legs", "Boots", "Hands", "Shield", "Weapon", "Amulet", "Cape"};

		JsonObject equipment = new JsonObject();
		for (int i = 0; i < equipmentSlots.length; i++) {
			equipment.add(equipmentSlots[i], buildLootItem(equippedItems[i]));
		}

		return equipment;
	}

	private OsrsKillboardItem[] buildEntries(final Collection<ItemStack> itemStacks) {
		return itemStacks.stream()
				.map(itemStack -> buildOsrsKillboardItem(itemStack.getId(), itemStack.getQuantity()))
				.toArray(OsrsKillboardItem[]::new);
	}
}
