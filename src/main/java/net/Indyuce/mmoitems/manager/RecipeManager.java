package net.Indyuce.mmoitems.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmokingRecipe;

import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.item.template.MMOItemTemplate;
import net.Indyuce.mmoitems.api.recipe.MMORecipeChoice;
import net.Indyuce.mmoitems.api.recipe.workbench.CustomRecipe;

public class RecipeManager {
	private final Set<CustomRecipe> craftingRecipes = new HashSet<>();
	private final Set<LoadedRecipe> loadedRecipes = new HashSet<>();

	public RecipeManager() {
		reload();
	}

	public void reload() {
		clearCustomRecipes();

		for (Type type : MMOItems.plugin.getTypes().getAll()) {
			FileConfiguration config = type.getConfigFile().getConfig();
			for (MMOItemTemplate template : MMOItems.plugin.getTemplates().getTemplates(type))
				if (config.contains(template.getId() + ".crafting"))
					try {
						ConfigurationSection section = config.getConfigurationSection(template.getId() + ".crafting");

						if (section.contains("shaped"))
							section.getConfigurationSection("shaped").getKeys(false)
									.forEach(recipe -> registerShapedRecipe(type, template.getId(), section.getStringList("shaped." + recipe)));
						if (section.contains("shapeless"))
							section.getConfigurationSection("shapeless").getKeys(false)
									.forEach(recipe -> registerShapelessRecipe(type, template.getId(), section.getStringList("shapeless." + recipe)));
						if (section.contains("furnace"))
							section.getConfigurationSection("furnace").getKeys(false)
									.forEach(recipe -> registerBurningRecipe(BurningRecipeType.FURNACE, type, template.getId(),
											new BurningRecipeInformation(section.getConfigurationSection("furnace." + recipe)), recipe));
						if (section.contains("blast"))
							section.getConfigurationSection("blast").getKeys(false)
									.forEach(recipe -> registerBurningRecipe(BurningRecipeType.BLAST, type, template.getId(),
											new BurningRecipeInformation(section.getConfigurationSection("blast." + recipe)), recipe));
						if (section.contains("smoker"))
							section.getConfigurationSection("smoker").getKeys(false)
									.forEach(recipe -> registerBurningRecipe(BurningRecipeType.SMOKER, type, template.getId(),
											new BurningRecipeInformation(section.getConfigurationSection("smoker." + recipe)), recipe));
						if (section.contains("campfire"))
							section.getConfigurationSection("campfire").getKeys(false)
									.forEach(recipe -> registerBurningRecipe(BurningRecipeType.CAMPFIRE, type, template.getId(),
											new BurningRecipeInformation(section.getConfigurationSection("campfire." + recipe)), recipe));
					} catch (IllegalArgumentException exception) {
						MMOItems.plugin.getLogger().log(Level.WARNING,
								"Could not load recipe of '" + template.getId() + "': " + exception.getMessage());
					}
		}

		sortRecipes();
		Bukkit.getScheduler().runTask(MMOItems.plugin, () -> getLoadedRecipes().forEach(recipe -> Bukkit.addRecipe(recipe.getRecipe())));
	}

	public void registerBurningRecipe(BurningRecipeType recipeType, Type type, String id, BurningRecipeInformation info, String recipeId) {
		NamespacedKey key = getRecipeKey(type, id, recipeType.getPath(), recipeId);
		Recipe recipe = recipeType.provideRecipe(key, MMOItems.plugin.getItem(type, id), toBukkit(info.getChoice()), info.getExp(),
				info.getBurnTime());
		registerRecipe(key, recipe);
	}

	public void registerShapedRecipe(Type type, String id, List<String> list) {
		registerRecipe(new CustomRecipe(MMOItems.plugin.getMMOItem(type, id).newBuilder().buildNBT(), list, false));
	}

	public void registerShapelessRecipe(Type type, String id, List<String> list) {
		registerRecipe(new CustomRecipe(MMOItems.plugin.getMMOItem(type, id).newBuilder().buildNBT(), list, true));
	}

	/*
	 * TODO When Bukkit changes their 'RecipeChoice.ExactChoice' API we can
	 * remove the suppressed warnings, but right now it works despite being
	 * marked as deprecated. It is just draft API and probably subject to
	 * change.
	 */
	@SuppressWarnings("deprecation")
	public RecipeChoice toBukkit(MMORecipeChoice choice) {
		return choice.isVanilla() ? new RecipeChoice.MaterialChoice(choice.getItem().getType()) : new RecipeChoice.ExactChoice(choice.getItem());
	}

	public void registerRecipe(NamespacedKey key, Recipe recipe) {
		loadedRecipes.add(new LoadedRecipe(key, recipe));
	}

	public void registerRecipe(CustomRecipe recipe) {
		if (!recipe.isEmpty())
			craftingRecipes.add(recipe);
	}

	public Set<LoadedRecipe> getLoadedRecipes() {
		return loadedRecipes;
	}

	public Set<CustomRecipe> getCustomRecipes() {
		return craftingRecipes;
	}

	public Set<NamespacedKey> getNamespacedKeys() {
		return loadedRecipes.stream().map(recipe -> recipe.getKey()).collect(Collectors.toSet());
	}

	public void sortRecipes() {
		List<CustomRecipe> temporary = new ArrayList<>();
		temporary.addAll(craftingRecipes);
		craftingRecipes.clear();
		craftingRecipes.addAll(temporary.stream().sorted().collect(Collectors.toList()));
	}

	public void clearCustomRecipes() {
		craftingRecipes.clear();
	}

	public NamespacedKey getRecipeKey(Type type, String id, String recipeType, String number) {
		return new NamespacedKey(MMOItems.plugin, recipeType + "_" + type.getId() + "_" + id + "_" + number);
	}

	public void reloadRecipes() {
		Bukkit.getScheduler().runTask(MMOItems.plugin, () -> {

			Iterator<Recipe> iterator = Bukkit.recipeIterator();
			while (iterator.hasNext()) {
				Recipe recipe = iterator.next();
				if (recipe instanceof Keyed && ((Keyed) recipe).getKey().getNamespace().equals("mmoitems"))
					iterator.remove();
			}

			loadedRecipes.clear();
			reload();
		});
	}

	/***
	 * Parses an ItemStack from a string. Can be used to both get a vanilla
	 * material or an MMOItem
	 */
	public ItemStack parseStack(String parse) {
		ItemStack stack = null;
		String[] split = parse.split("\\:");
		String input = split[0];

		if (input.contains(".")) {
			String[] typeId = input.split("\\.");
			String typeFormat = typeId[0].toUpperCase().replace("-", "_").replace(" ", "_");
			Validate.isTrue(MMOItems.plugin.getTypes().has(typeFormat), "Could not find type " + typeFormat);

			MMOItem mmo = MMOItems.plugin.getMMOItem(MMOItems.plugin.getTypes().get(typeFormat), typeId[1]);
			if (mmo != null)
				stack = mmo.newBuilder().build();
		} else {
			Material mat = Material.AIR;
			try {
				mat = Material.valueOf(input.toUpperCase().replace("-", "_").replace(" ", "_"));
			} catch (IllegalArgumentException e) {
				MMOItems.plugin.getLogger().warning("Couldn't parse material from '" + parse + "'!");
			}

			if (mat != Material.AIR)
				stack = new ItemStack(mat);
		}

		try {
			if (stack != null && split.length > 1)
				stack.setAmount(Integer.parseInt(split[1]));
		} catch (NumberFormatException e) {
			MMOItems.plugin.getLogger().warning("Couldn't parse amount from '" + parse + "'!");
		}

		return stack;
	}

	/**
	 * Easier control of furnace, smoker, campfire and blast recipes so there is
	 * no need to have four time the same method to register this type of recipe
	 * 
	 * @author cympe
	 */
	public enum BurningRecipeType {
		FURNACE((key, result, source, experience, cookTime) -> new FurnaceRecipe(key, result, source, experience, cookTime)),
		SMOKER((key, result, source, experience, cookTime) -> new SmokingRecipe(key, result, source, experience, cookTime)),
		CAMPFIRE((key, result, source, experience, cookTime) -> new CampfireRecipe(key, result, source, experience, cookTime)),
		BLAST((key, result, source, experience, cookTime) -> new BlastingRecipe(key, result, source, experience, cookTime));

		private final RecipeProvider provider;

		private BurningRecipeType(RecipeProvider provider) {
			this.provider = provider;
		}

		public Recipe provideRecipe(NamespacedKey key, ItemStack result, RecipeChoice source, float experience, int cookTime) {
			return provider.provide(key, result, source, experience, cookTime);
		}

		public String getPath() {
			return name().toLowerCase();
		}
	}

	@FunctionalInterface
	public interface RecipeProvider {
		Recipe provide(NamespacedKey key, ItemStack result, RecipeChoice source, float experience, int cookTime);
	}

	/*
	 * used because spigot API does not let us access namespaced key of a Recipe
	 * instance.
	 */
	public class LoadedRecipe {
		private final Recipe recipe;
		private final NamespacedKey key;

		public LoadedRecipe(NamespacedKey key, Recipe recipe) {
			this.recipe = recipe;
			this.key = key;
		}

		public NamespacedKey getKey() {
			return key;
		}

		public Recipe getRecipe() {
			return recipe;
		}
	}

	/*
	 * blast furnace, smoker, campfire and furnace recipes have extra parameters
	 */
	public class BurningRecipeInformation {
		private final MMORecipeChoice choice;
		private final float exp;
		private final int burnTime;

		protected BurningRecipeInformation(ConfigurationSection config) {
			choice = new MMORecipeChoice(config.getString("item"));
			exp = (float) config.getDouble("exp", 0.35);
			burnTime = config.getInt("time", 200);
		}

		public int getBurnTime() {
			return burnTime;
		}

		public MMORecipeChoice getChoice() {
			return choice;
		}

		public float getExp() {
			return exp;
		}
	}
}