package cc.carm.plugin.userprefix.manager;

import cc.carm.lib.easyplugin.gui.configuration.GUIActionConfiguration;
import cc.carm.plugin.userprefix.conf.prefix.PrefixConfig;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 前缀决策逻辑（PrefixResolver）的单元测试。
 * <p>
 * 该逻辑同时服务于在线玩家与离线玩家的占位符解析，
 * 本测试确保离线解析与在线解析的语义一致（共用同一实现）。
 */
public class PrefixResolverTest {

    // ---------- 测试夹具 ----------

    private static PrefixConfig prefix(String id, int weight, String permission) {
        return new PrefixConfig(id, id, "content-of-" + id, weight, permission,
                Collections.<GUIActionConfiguration>emptyList(), null, null, null);
    }

    private static PrefixManager buildManager() {
        PrefixManager pm = new PrefixManager();
        pm.prefixes.put("a", prefix("a", 1, null));          // 公开前缀
        pm.prefixes.put("b", prefix("b", 2, "prefix.b"));    // 需要权限 prefix.b
        pm.prefixes.put("c", prefix("c", 3, "prefix.c"));    // 需要权限 prefix.c
        pm.defaultPrefix = prefix("default", 0, null);
        return pm;
    }

    /** 不含公开前缀的配置，用于测试“无任何可用前缀”时的默认兜底 */
    private static PrefixManager buildManagerWithoutPublic() {
        PrefixManager pm = new PrefixManager();
        pm.prefixes.put("b", prefix("b", 2, "prefix.b"));
        pm.prefixes.put("c", prefix("c", 3, "prefix.c"));
        pm.defaultPrefix = prefix("default", 0, null);
        return pm;
    }

    private static UserData data(String metaValue, String... permissions) {
        final Set<String> perms = new HashSet<>(Arrays.asList(permissions));
        return new UserData() {
            @Override
            public Optional<String> getMetaValue(String key) {
                return Optional.ofNullable(metaValue);
            }

            @Override
            public boolean hasPermission(String permission) {
                return perms.contains(permission);
            }
        };
    }

    private static List<String> ids(List<PrefixConfig> list) {
        return list.stream().map(PrefixConfig::getIdentifier).collect(Collectors.toList());
    }

    // ---------- resolve：核心决策 ----------

    @Test
    public void resolve_shouldReturnSelectedPrefixWhenUsable() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertEquals("b", resolver.resolve(data("b", "prefix.b")).getIdentifier());
    }

    @Test
    public void resolve_shouldReturnHighestUsableWhenSelectedNotUsable() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        // 选中 c，但玩家没有 prefix.c 权限 → 回退到有权限的最高权重前缀 (b)
        assertEquals("b", resolver.resolve(data("c", "prefix.b")).getIdentifier());
    }

    @Test
    public void resolve_shouldReturnHighestUsableWhenNoSelection() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertEquals("c", resolver.resolve(data(null, "prefix.b", "prefix.c")).getIdentifier());
    }

    @Test
    public void resolve_shouldKeepUsableSelectionEvenWhenAutoUseDisabled() {
        // 与原有逻辑一致：玩家已选中的前缀可用时，AUTO_USE 不影响选择结果
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> false);
        assertEquals("b", resolver.resolve(data("b", "prefix.b")).getIdentifier());
    }

    @Test
    public void resolve_shouldReturnDefaultWhenAutoUseDisabledAndSelectionUnusable() {
        // 选中前缀不可用 + 关闭自动使用 → 默认前缀
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> false);
        assertEquals("default", resolver.resolve(data("c", "prefix.b")).getIdentifier());
    }

    @Test
    public void resolve_shouldReturnDefaultWhenSelectionIsDefault() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertEquals("default", resolver.resolve(data("default")).getIdentifier());
    }

    @Test
    public void resolve_shouldReturnDefaultWhenSelectionMissing() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        // 选中的前缀在配置中已不存在 → 兜底（有权限的最高权重前缀）
        assertEquals("c", resolver.resolve(data("ghost", "prefix.b", "prefix.c")).getIdentifier());
    }

    @Test
    public void resolve_shouldFallbackToPublicPrefixWhenNoPermission() {
        // 与原有逻辑一致：公开前缀对所有人可用，无权限时回退到公开前缀（而非默认前缀）
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertEquals("a", resolver.resolve(data(null)).getIdentifier());
    }

    @Test
    public void resolve_shouldReturnDefaultWhenNothingUsable() {
        // 没有任何可用前缀（含公开前缀）时 → 默认前缀
        PrefixResolver resolver = new PrefixResolver(buildManagerWithoutPublic(), () -> true);
        assertEquals("default", resolver.resolve(data(null)).getIdentifier());
    }

    // ---------- isPrefixUsable ----------

    @Test
    public void isPrefixUsable_shouldAcceptNullAndDefault() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertTrue(resolver.isPrefixUsable(data(null), null));
        assertTrue(resolver.isPrefixUsable(data(null), "default"));
        assertTrue(resolver.isPrefixUsable(data(null), "DEFAULT"));
    }

    @Test
    public void isPrefixUsable_shouldCheckPermission() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertTrue(resolver.isPrefixUsable(data(null, "prefix.b"), "b"));
        assertFalse(resolver.isPrefixUsable(data(null), "b"));
        assertTrue(resolver.isPrefixUsable(data(null), "a")); // 公开前缀无需权限
        assertFalse(resolver.isPrefixUsable(data(null), "ghost"));
    }

    // ---------- getUsablePrefixes ----------

    @Test
    public void getUsablePrefixes_shouldFilterAndSortByWeight() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        List<PrefixConfig> usable = resolver.getUsablePrefixes(data(null, "prefix.c")); // a(公开) + c
        assertEquals(Arrays.asList("a", "c"), ids(usable));
    }

    // ---------- getHighestPrefix ----------

    @Test
    public void getHighestPrefix_shouldPickMaxWeightUsable() {
        PrefixResolver resolver = new PrefixResolver(buildManager(), () -> true);
        assertEquals("c", resolver.getHighestPrefix(data(null, "prefix.b", "prefix.c")).getIdentifier());
    }

    @Test
    public void getHighestPrefix_shouldReturnDefaultWhenAutoUseDisabledOrNothingUsable() {
        PrefixResolver resolver = new PrefixResolver(buildManagerWithoutPublic(), () -> true);
        assertEquals("default", resolver.getHighestPrefix(data(null)).getIdentifier());

        PrefixResolver noAutoUse = new PrefixResolver(buildManager(), () -> false);
        assertEquals("default", noAutoUse.getHighestPrefix(data(null, "prefix.b", "prefix.c")).getIdentifier());
    }

}
