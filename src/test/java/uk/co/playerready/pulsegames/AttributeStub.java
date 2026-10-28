package uk.co.playerready.pulsegames;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;

import java.lang.reflect.Proxy;

/**
 * A stand-in {@link Attribute}. The real constants (and Mockito, which initialises the interface) need a running
 * server's registry, which unit tests do not have; a proxy never initialises the interface.
 */
public final class AttributeStub {
    private AttributeStub() {}

    public static Attribute named(String key) {
        return (Attribute) Proxy.newProxyInstance(Attribute.class.getClassLoader(), new Class<?>[]{Attribute.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getKey" -> new NamespacedKey("minecraft", key);
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "Attribute[" + key + "]";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
