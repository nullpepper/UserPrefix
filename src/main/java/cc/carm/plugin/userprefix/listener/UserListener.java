package cc.carm.plugin.userprefix.listener;

import cc.carm.plugin.userprefix.UserPrefixAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class UserListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // 玩家上线，使离线期间缓存的前缀数据失效，确保使用最新数据
        UserPrefixAPI.getUserManager().invalidateOfflineCache(event.getPlayer().getUniqueId());
        UserPrefixAPI.getUserManager().initPlayer(event.getPlayer());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        UserPrefixAPI.getUserManager().unloadPlayer(event.getPlayer());
    }

}
