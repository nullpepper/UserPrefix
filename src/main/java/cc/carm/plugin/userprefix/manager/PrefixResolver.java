package cc.carm.plugin.userprefix.manager;

import cc.carm.plugin.userprefix.conf.prefix.PrefixConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * 前缀决策逻辑，同时服务于在线玩家与离线玩家的占位符解析，
 * 保证离线占位符与在线占位符的解析语义完全一致。
 * <p>
 * 决策规则（与原有 {@link UserManager#getPrefix(org.bukkit.entity.Player)} 保持一致）：
 * <ol>
 *     <li>玩家当前选中的前缀存在且可用 → 使用该前缀；</li>
 *     <li>否则若开启了自动使用 → 使用权重最高的可用前缀；</li>
 *     <li>否则 → 使用默认前缀。</li>
 * </ol>
 */
public class PrefixResolver {

    protected final @NotNull PrefixManager prefixManager;
    protected final @NotNull BooleanSupplier autoUse;

    public PrefixResolver(@NotNull PrefixManager prefixManager, @NotNull BooleanSupplier autoUse) {
        this.prefixManager = prefixManager;
        this.autoUse = autoUse;
    }

    /**
     * 解析玩家的最终可用前缀。
     *
     * @param data 玩家数据门面（在线或离线）
     * @return 前缀配置
     */
    @NotNull
    public PrefixConfig resolve(@NotNull UserData data) {
        String identifier = data.getMetaValue(UserManager.META_KEY).orElse(null);
        if (identifier == null || !isPrefixUsable(data, identifier)) {
            return getHighestPrefix(data);
        } else {
            PrefixConfig prefix = prefixManager.getPrefix(identifier);
            return prefix == null ? prefixManager.getDefaultPrefix() : prefix;
        }
    }

    /**
     * 判断一个前缀对某玩家是否可用。
     *
     * @param data             玩家数据门面
     * @param prefixIdentifier 前缀标识
     * @return 若前缀标识不存在，则返回false；若前缀为默认前缀，或该前缀无权限，或玩家有该前缀的权限，则返回true。
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isPrefixUsable(@NotNull UserData data, @Nullable String prefixIdentifier) {
        if (prefixIdentifier == null || prefixIdentifier.equalsIgnoreCase("default")) return true;
        PrefixConfig prefix = prefixManager.getPrefix(prefixIdentifier);
        return prefix != null && (prefix.isPublic() || data.hasPermission(prefix.getPermission()));
    }

    /**
     * 得到玩家所有可用的前缀。
     *
     * @param data 玩家数据门面
     * @return 可用前缀列表（按权重升序）
     */
    @NotNull
    public List<PrefixConfig> getUsablePrefixes(@NotNull UserData data) {
        return prefixManager.getPrefixes().values().stream()
                .filter(prefix -> prefix.isPublic() || data.hasPermission(prefix.getPermission()))
                .sorted(Comparator.comparingInt(PrefixConfig::getWeight))
                .collect(Collectors.toList());
    }

    /**
     * 得到玩家可使用的最高权重的前缀。
     * 注意：若配置文件中关闭了 “autoUsePrefix” ，则会返回默认前缀。
     *
     * @param data 玩家数据门面
     * @return 前缀配置
     */
    @NotNull
    public PrefixConfig getHighestPrefix(@NotNull UserData data) {
        if (!autoUse.getAsBoolean()) {
            return prefixManager.getDefaultPrefix();
        }
        return getUsablePrefixes(data).stream()
                .max(Comparator.comparingInt(PrefixConfig::getWeight))
                .orElseGet(prefixManager::getDefaultPrefix);
    }

}
