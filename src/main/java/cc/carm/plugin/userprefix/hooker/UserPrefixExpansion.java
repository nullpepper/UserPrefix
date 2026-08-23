package cc.carm.plugin.userprefix.hooker;

import cc.carm.lib.easyplugin.papi.EasyPlaceholder;
import cc.carm.lib.easyplugin.papi.expansion.SubExpansion;
import cc.carm.lib.easyplugin.papi.handler.PlaceholderHandler;
import cc.carm.plugin.userprefix.UserPrefixAPI;
import cc.carm.plugin.userprefix.conf.prefix.PrefixConfig;
import cc.carm.plugin.userprefix.manager.UserManager;
import net.luckperms.api.model.user.User;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

public class UserPrefixExpansion extends EasyPlaceholder {

    public UserPrefixExpansion(@NotNull JavaPlugin plugin, @NotNull String rootIdentifier) {
        super(plugin, rootIdentifier);

        handle("version", (player, args) -> getVersion());

        handle("identifier", handlePrefix(PrefixConfig::getIdentifier), "id");
        handle("prefix", handlePrefixView((prefix, viewer) ->
                viewer.isOnline() ? prefix.getContent((Player) viewer) : prefix.getContentForOffline(viewer)));
        handle("name", handlePrefix(PrefixConfig::getName));
        handle("description", handlePrefix(p -> String.join("\n", p.getDescription())));
        handle("weight", handlePrefix(PrefixConfig::getWeight));
        handle("amount", (player, args) -> {
            if (player == null) return "Loading...";
            if (player.isOnline()) return UserPrefixAPI.getUserManager().getUsablePrefixes((Player) player).size() + 1;
            return handleOfflineUser(player, user -> UserPrefixAPI.getUserManager().getUsablePrefixes(user).size() + 1);
        });
        handle("has", (player, args) -> {
            if (args.length < 1) return "参数不足";
            PrefixConfig prefix = UserPrefixAPI.getPrefixManager().getPrefix(args[0]);
            if (prefix == null) return "该前缀不存在";
            if (player == null) return "Loading...";
            if (player.isOnline()) return prefix.checkPermission((Player) player);
            return handleOfflineUser(player, prefix::checkPermission);
        }, Collections.singletonList("<前缀ID>"));

    }

    /**
     * 处理不依赖查看者的前缀信息占位符（identifier/name/description/weight）。
     * 在线玩家走原有逻辑，离线玩家走离线解析（缓存命中即返回，未命中触发加载）。
     */
    public PlaceholderHandler handlePrefix(Function<PrefixConfig, Object> handler) {
        return (player, args) -> resolvePrefixValue(player, handler);
    }

    /**
     * 处理需要查看者上下文的前缀信息占位符（如 prefix 内容中包含的其他占位符）。
     * 在线玩家走原有逻辑，离线玩家走离线解析。
     */
    public PlaceholderHandler handlePrefixView(BiFunction<PrefixConfig, OfflinePlayer, Object> handler) {
        return (player, args) -> resolvePrefixValue(player, prefix -> handler.apply(prefix, player));
    }

    public PlaceholderHandler handlePrefix(BiFunction<PrefixConfig, Player, Object> handler) {
        return handlePlayer((player, args) -> handler.apply(UserPrefixAPI.getUserManager().getPrefix(player), player));
    }

    /**
     * This is required or else PlaceholderAPI will unregister the Expansion on reload
     */
    @Override
    public boolean persist() {
        return true;
    }

    public PlaceholderHandler handlePlayer(BiFunction<Player, String[], Object> handler) {
        return (player, args) -> {
            if (player == null || !player.isOnline()) return "Loading...";
            return handler.apply((Player) player, args);
        };
    }

    public PlaceholderHandler handlePlayer(Function<Player, Object> handler) {
        return handlePlayer((player, args) -> handler.apply(player));
    }

    /**
     * 统一的前缀解析入口：在线玩家走原有逻辑；离线玩家缓存命中即返回，未命中则触发异步加载并返回 Loading...。
     *
     * @param player  占位符请求的目标玩家（可能为 null 或离线）
     * @param handler 前缀处理器
     * @return 解析结果
     */
    protected Object resolvePrefixValue(@Nullable OfflinePlayer player, @NotNull Function<PrefixConfig, Object> handler) {
        if (player == null) return "Loading...";
        if (player.isOnline()) {
            return handler.apply(UserPrefixAPI.getUserManager().getPrefix((Player) player));
        }
        return handleOfflinePrefix(player, handler);
    }

    /**
     * 离线玩家的前缀占位符解析：
     * 缓存命中或LuckPerms内存命中 → 立即返回真实值；
     * 冷数据 → 同步等待异步加载完成（上限 {@link UserManager#OFFLINE_LOAD_TIMEOUT}），
     * 拿到结果立即返回真实称号；超时才回退 Loading...（后台加载继续，后续查询即命中）。
     *
     * @param player  离线玩家
     * @param handler 前缀处理器
     * @return 解析结果
     */
    protected Object handleOfflinePrefix(@NotNull OfflinePlayer player, @NotNull Function<PrefixConfig, Object> handler) {
        try {
            PrefixConfig prefix = UserPrefixAPI.getUserManager().loadOfflinePrefix(player.getUniqueId())
                    .get(UserManager.OFFLINE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
            return handler.apply(prefix);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Loading...";
        } catch (Exception e) {
            return "Loading...";
        }
    }

    /**
     * 离线玩家的用户数据占位符解析（amount/has）：
     * LuckPerms内存已有该用户 → 立即计算；冷数据 → 同步等待加载完成（上限 {@link UserManager#OFFLINE_LOAD_TIMEOUT}），
     * 超时回退 Loading...。
     *
     * @param player  离线玩家
     * @param handler 用户数据处理器
     * @return 解析结果
     */
    protected Object handleOfflineUser(@NotNull OfflinePlayer player, @NotNull Function<User, Object> handler) {
        try {
            User user = UserPrefixAPI.getUserManager().loadOfflineUser(player.getUniqueId())
                    .get(UserManager.OFFLINE_LOAD_TIMEOUT, TimeUnit.MILLISECONDS);
            return handler.apply(user);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Loading...";
        } catch (Exception e) {
            return "Loading...";
        }
    }

    @Override
    public String onErrorParams(@Nullable OfflinePlayer player) {
        return "参数不足";
    }

    @Override
    public String onException(@Nullable OfflinePlayer player, @NotNull SubExpansion<?> expansion,
                              @NotNull Exception exception) {
        exception.printStackTrace();
        return "参数错误";
    }

}
