package uk.co.playerready.pulsenetwork;

import java.util.Locale;
import java.util.UUID;

/** Builds a {@link NameStyle.Identity} from LuckPerms-style meta. Only known role tags and known style ids survive. */
public final class IdentityResolver {
    public static final String ROLE_KEY = "pulse.role", STYLE_KEY = "pulse.namestyle";
    /** Source of meta values (LuckPerms resolves group meta by weight, highest wins). */
    public interface Meta { String value(UUID id, String key); }
    private IdentityResolver() {}

    public static NameStyle.Identity resolve(UUID id, String name, Meta meta, Integer level, NameStyle.Tags tags) {
        String role = clean(meta.value(id, ROLE_KEY)), style = clean(meta.value(id, STYLE_KEY));
        return new NameStyle.Identity(name, tags.has(role) ? role : null, NameStyle.hasStyle(style) ? style : null,
                level != null && level > 0 && level < 10_000 ? level : null);
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT); }
}
