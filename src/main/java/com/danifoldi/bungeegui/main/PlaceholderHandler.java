package com.danifoldi.bungeegui.main;

import com.danifoldi.bungeegui.util.NumberUtil;
import com.danifoldi.bungeegui.util.ProxyversionUtil;
import de.myzelyam.api.vanish.BungeeVanishAPI;
import net.luckperms.api.LuckPermsProvider;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Singleton
public class PlaceholderHandler {
    private final Map<String, Function<ProxiedPlayer, String>> builtinPlaceholders = new HashMap<>();
    private final Map<String, Function<ProxiedPlayer, String>> placeholders = new HashMap<>();
    private final ConcurrentMap<ServerInfo, ServerPing> latestPing = new ConcurrentHashMap<>();
    private final ProxyServer proxyServer;
    private final PluginManager pluginManager;
    private final BungeeGuiPlugin plugin;

    @Inject
    public PlaceholderHandler(final @NotNull ProxyServer proxyServer,
                              final @NotNull PluginManager pluginManager,
                              final @NotNull BungeeGuiPlugin plugin) {
        this.proxyServer = proxyServer;
        this.pluginManager = pluginManager;
        this.plugin = plugin;
    }

    void register(String name, Function<ProxiedPlayer, String> placeholder) {
        placeholders.putIfAbsent(name, placeholder);
    }

    void unregister(String name) {
        placeholders.remove(name);
    }

    void registerBuiltin(String name, Function<ProxiedPlayer, String> placeholder) {
        builtinPlaceholders.putIfAbsent(name, placeholder);
    }

    void unregisterBuiltin(String name) {
        builtinPlaceholders.remove(name);
    }

    String parse(ProxiedPlayer player, String text) {
        String result = text;

        for (Map.Entry<String, Function<ProxiedPlayer, String>> placeholder: builtinPlaceholders.entrySet()) {
            final String value = placeholder.getValue().apply(player);
            result = result.replace("%" + placeholder.getKey() + "%", value == null ? "" : value);
        }

        for (Map.Entry<String, Function<ProxiedPlayer, String>> placeholder: placeholders.entrySet()) {
            final String value = placeholder.getValue().apply(player);
            result = result.replace("%" + placeholder.getKey() + "%", value == null ? "" : value);
        }

        return result;
    }

    void unregisterAll() {
        builtinPlaceholders.clear();
        placeholders.clear();
    }

    void registerBuiltins() {
        proxyServer.getScheduler().schedule(plugin, () -> {
            for (ServerInfo server: proxyServer.getServersCopy().values()) {
                server.ping((ping, error) -> {
                    if (error != null) {
                        plugin.getLogger().info("Could not ping server " + server.getName());
                        return;
                    }
                    latestPing.put(server, ping);
                });
            }
        }, 1, 5, TimeUnit.SECONDS);

        registerBuiltin("", player -> "%");

        registerBuiltin("ram_used", player -> NumberUtil.formatDecimal((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024d * 1024d)));
        registerBuiltin("ram_total", player -> NumberUtil.formatDecimal((Runtime.getRuntime().totalMemory()) / (1024d * 1024d)));

        registerBuiltin("proxyname", player -> proxyServer.getName());
        registerBuiltin("bungeegui", player -> plugin.getDescription().getName() + " " + plugin.getDescription().getVersion());
        registerBuiltin("version", player -> ProxyversionUtil.find(player.getPendingConnection().getVersion()).getVersion());
        registerBuiltin("max", player -> String.valueOf(proxyServer.getConfig().getPlayerLimit()));
        registerBuiltin("online", player -> String.valueOf(proxyServer.getOnlineCount()));
        registerBuiltin("online_visible", player -> {
            int count = proxyServer.getOnlineCount();
            if (pluginManager.getPlugin("PremiumVanish") != null) {
                count -= BungeeVanishAPI.getInvisiblePlayers().size();
            }
            return String.valueOf(count);
        });
        registerBuiltin("plugincount", player -> String.valueOf(pluginManager.getPlugins().size()));
        registerBuiltin("displayname", ProxiedPlayer::getDisplayName);
        registerBuiltin("name", ProxiedPlayer::getName);
        registerBuiltin("locale", player -> player.getLocale().getDisplayName());
        registerBuiltin("ping", player -> String.valueOf(player.getPing()));
        registerBuiltin("vanished", player -> {
            if (pluginManager.getPlugin("PremiumVanish") != null) {
                return BungeeVanishAPI.isInvisible(player) ? "Yes" : "No";
            } else {
                return "No";
            }
        });
        registerBuiltin("servername", player -> player.getServer().getInfo().getName());
        registerBuiltin("servermotd", player -> player.getServer().getInfo().getMotd());
        registerBuiltin("luckperms_prefix", player -> {
            try {
                return LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getPrefix();
            } catch (IllegalStateException | NullPointerException e) {
                return "";
            }
        });
        registerBuiltin("luckperms_suffix", player -> {
            try {
                return LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getSuffix();
            } catch (IllegalStateException | NullPointerException e) {
                return "";
            }
        });
        registerBuiltin("luckperms_group", player -> {
            try {
                return LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId()).getCachedData().getMetaData().getPrimaryGroup();
            } catch (IllegalStateException | NullPointerException e) {
                return "";
            }
        });

        for (Map.Entry<String, ServerInfo> server: proxyServer.getServersCopy().entrySet()) {
            registerBuiltin("online_visible@" + server.getKey(), player -> {
                ServerPing ping = latestPing.get(server.getValue());
                if (ping == null) {
                    return "0";
                }
                int count = ping.getPlayers().getOnline();
                if (pluginManager.getPlugin("PremiumVanish") != null) {
                    count -= BungeeVanishAPI.getInvisiblePlayers().stream().filter(u -> proxyServer.getPlayer(u).getServer().getInfo() == server.getValue()).count();
                }
                return String.valueOf(count);
            });
            registerBuiltin("online@" + server.getKey(), player -> {
                ServerPing ping = latestPing.get(server.getValue());
                if (ping == null) {
                    return "0";
                }
                return String.valueOf(ping.getPlayers().getOnline());
            });
            registerBuiltin("max@" + server.getKey(), player -> {
                ServerPing ping = latestPing.get(server.getValue());
                if (ping == null) {
                    return "0";
                }
                return String.valueOf(ping.getPlayers().getMax());
            });
            registerBuiltin("version@" + server.getKey(), player -> {
                ServerPing ping = latestPing.get(server.getValue());
                if (ping == null) {
                    return "-";
                }
                return String.valueOf(ping.getVersion().getName());
            });
            registerBuiltin("canaccess@" + server.getKey(), player -> server.getValue().canAccess(player) ? "Yes" : "No");
            registerBuiltin("restricted@" + server.getKey(), player -> server.getValue().isRestricted() ? "Yes" : "No");
            registerBuiltin("name@" + server.getKey(), player -> server.getValue().getName());
            registerBuiltin("motd@" + server.getKey(), player -> server.getValue().getMotd());
        }

        for (Plugin plugin: pluginManager.getPlugins()) {
            final String name = plugin.getDescription().getName();
            registerBuiltin("plugindescription@" + name, player -> plugin.getDescription().getDescription());
            registerBuiltin("pluginmain@" + name, player -> plugin.getDescription().getMain());
            registerBuiltin("pluginversion@" + name, player -> plugin.getDescription().getVersion());
            registerBuiltin("pluginauthor@" + name, player -> plugin.getDescription().getAuthor());
            registerBuiltin("plugindepends@" + name, player -> String.join(", ", plugin.getDescription().getDepends()));
            registerBuiltin("pluginsoftdepends@" + name, player -> String.join(", ", plugin.getDescription().getSoftDepends()));
        }
    }
}
