package cc.carm.plugin.userprefix.manager;

import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * 玩家数据门面，用于屏蔽在线玩家（Bukkit Player）与离线玩家（LuckPerms User）的数据读取差异，
 * 使前缀决策逻辑（{@link PrefixResolver}）可以以同一套逻辑服务两种场景。
 */
public interface UserData {

    /**
     * 读取玩家的一条Meta数据。
     *
     * @param key Meta键
     * @return Meta值，不存在时为空
     */
    Optional<String> getMetaValue(@NotNull String key);

    /**
     * 判断玩家是否拥有指定权限。
     *
     * @param permission 权限节点
     * @return true / false
     */
    boolean hasPermission(@NotNull String permission);

    /**
     * 适配在线玩家（经由LuckPerms的PlayerAdapter读取，与在线占位符原有行为完全一致）。
     *
     * @param player 在线玩家
     * @return 数据门面
     */
    static UserData of(@NotNull Player player) {
        return new UserData() {
            @Override
            public Optional<String> getMetaValue(@NotNull String key) {
                return ServiceManager.getAPI().getMetaData(player).getMetaValue(key, String::valueOf);
            }

            @Override
            public boolean hasPermission(@NotNull String permission) {
                return ServiceManager.hasPermission(player, permission);
            }
        };
    }

    /**
     * 适配离线玩家（经由LuckPerms User数据读取）。
     *
     * @param user LuckPerms用户数据（可来自异步加载）
     * @return 数据门面
     */
    static UserData of(@NotNull User user) {
        return new UserData() {
            @Override
            public Optional<String> getMetaValue(@NotNull String key) {
                return user.getCachedData().getMetaData().getMetaValue(key, String::valueOf);
            }

            @Override
            public boolean hasPermission(@NotNull String permission) {
                return ServiceManager.hasPermission(user, permission);
            }
        };
    }

}
