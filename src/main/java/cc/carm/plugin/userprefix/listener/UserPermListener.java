package cc.carm.plugin.userprefix.listener;

import cc.carm.plugin.userprefix.Main;
import cc.carm.plugin.userprefix.UserPrefixAPI;
import cc.carm.plugin.userprefix.ui.PrefixSelectGUI;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class UserPermListener {

    public static void process(UserDataRecalculateEvent event) {
        // 权限/数据重算（含离线场景），使离线前缀缓存失效
        UserPrefixAPI.getUserManager().invalidateOfflineCache(event.getUser().getUniqueId());

        Player player = Bukkit.getPlayer(event.getUser().getUniqueId());
        if (player == null) return;
        UserPrefixAPI.getUserManager().checkPrefix(player, true);
        if (PrefixSelectGUI.openingUsers.contains(player)) {
            Main.getInstance().getFoliaScheduler().runOnEntity(player, true, () -> {
                // 玩家权限更新，同步关闭其GUI，以令其重新打开刷新自己的前缀。
                player.closeInventory();
                PrefixSelectGUI.removeOpening(player);
            });
        }
    }

}
