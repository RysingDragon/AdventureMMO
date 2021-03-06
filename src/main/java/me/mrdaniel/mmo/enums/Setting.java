package me.mrdaniel.mmo.enums;

public enum Setting {
	
	EFFECTS("Effects", 0),
	SCOREBOARD("Scoreboard", 1),
	SCOREBOARDPERMANENT("ScoreboardPermanent", 2);
	
	public String name;
	public int id;
	
	Setting(String name, int id) {
		this.name = name; this.id = id;
	}

	public static Setting match(String name) {
		for (Setting setting : Setting.values()) if (setting.name.equalsIgnoreCase(name)) return setting;
		return null;
	}

	public static Setting get(int id) {
		for (Setting s : Setting.values()) if (s.id == id) return s;
		return null;
	}
}