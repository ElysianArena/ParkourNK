package cn.daoge.parkour.config;

import cn.nukkit.utils.Config;

import java.util.Map;

public class Lang {
    private final Config config;

    public Lang(Config config) {
        this.config = config;
    }

    public String text(String key, Object... replacements) {
        String text = config.getString(key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return text.replace('&', '§');
    }

    public String message(String key, Object... replacements) {
        return text("prefix") + text(key, replacements);
    }
}
