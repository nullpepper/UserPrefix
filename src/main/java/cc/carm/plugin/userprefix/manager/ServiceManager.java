package cc.carm.plugin.userprefix.manager;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.platform.PlayerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 服务管理器，旨在于LuckPerms互联，调用其原始接口。
 */
public class ServiceManager {

    public static User getUser(Player player) {
        return getAPI().getUser(player);
    }

    /**
     * 同步获取已加载到LuckPerms内存中的用户数据。
     * <br> 若该用户（含离线玩家）尚未被加载，则返回 null。
     *
     * @param uuid 玩家UUID
     * @return 用户数据，未加载时为 null
     */
    @Nullable
    public static User getUser(UUID uuid) {
        return getService().getUserManager().getUser(uuid);
    }

    /**
     * 异步加载指定UUID的用户数据（含离线玩家），
     * 加载完成后该用户会常驻于LuckPerms的内存缓存中，可通过 {@link #getUser(UUID)} 同步获取。
     *
     * @param uuid 玩家UUID
     * @return 异步加载结果
     */
    public static CompletableFuture<User> loadUser(UUID uuid) {
        return getService().getUserManager().loadUser(uuid);
    }

    public static LuckPerms getService() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            return provider.getProvider();
        } else {
            return LuckPermsProvider.get();
        }
    }

    public static PlayerAdapter<Player> getAPI() {
        return getService().getPlayerAdapter(Player.class);
    }


    /**
     * 通过LuckPermsAPI判断玩家是否有对应的权限
     *
     * @param user       用户
     * @param permission 权限
     * @return true / false
     */
    public static boolean hasPermission(User user, String permission) {
        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    public static boolean hasPermission(Player player, String permission) {
        return hasPermission(getUser(player), permission);
    }

}
