package link.oulipo.drilipo;

import com.google.common.base.CharMatcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// https://github.com/mouse-reeve/mastodon/blob/e00be1cc2a9819607e31f2bd916cef04bf289065/app/lib/oulipo/oulipo.rb
public class Oulipo {
    public static final Pattern FIFTH_GLYPH_REGEX = Pattern.compile("[eèéêëēėęǝɛ]", Pattern.CASE_INSENSITIVE);
    public static final Pattern URL_REGEX = Pattern.compile("https?:\\/\\/[^\\s\\\\]+");
    public static final Pattern MENTION_REGEX = Pattern.compile("@[a-z0-9-]{1,15}");
    public static final Pattern EMOJI_REGEX = Pattern.compile("\\B:[a-zA-Z\\d_]+:\\B");

    private static final Pattern[] SKIP_VALIDATION_REGEXES = {URL_REGEX, MENTION_REGEX, EMOJI_REGEX};

    public static String tootText(String text) {
        for (Pattern p : SKIP_VALIDATION_REGEXES) {
            text = p.matcher(text).replaceAll("");
        }
        return text;
    }

    public static boolean isLipogrammatic(String text) {
        String tootText = tootText(text);
        return !(CharMatcher.whitespace().matchesAllOf(tootText) ||
                FIFTH_GLYPH_REGEX.matcher(tootText).find());
    }
}
