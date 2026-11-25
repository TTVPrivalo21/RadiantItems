package dev.privalo.items.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Optional integrations resolved by reflection, so the plugin compiles and runs without
 * Vault or PlaceholderAPI on the classpath.
 */
public final class Hooks {

    private final Logger logger;
    private Object economy;
    private Method has;
    private Method withdraw;
    private Method deposit;
    private Method papi;

    public Hooks(Logger logger) {
        this.logger = logger;
    }

    public void load() {
        economy = null;
        has = withdraw = deposit = papi = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                Class<?> type = Class.forName("net.milkbowl.vault.economy.Economy");
                RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(type);
                if (rsp != null) {
                    economy = rsp.getProvider();
                    has = type.getMethod("has", OfflinePlayer.class, double.class);
                    withdraw = type.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                    deposit = type.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                    logger.info("Vault detectado: costes en dinero activados.");
                }
            } catch (ReflectiveOperationException | LinkageError ex) {
                economy = null;
                logger.warning("No se pudo enlazar con Vault: " + ex.getMessage());
            }
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI").getMethod("setPlaceholders", OfflinePlayer.class, String.class);
                logger.info("PlaceholderAPI detectado: placeholders externos activados.");
            } catch (ReflectiveOperationException | LinkageError ex) {
                papi = null;
                logger.warning("No se pudo enlazar con PlaceholderAPI: " + ex.getMessage());
            }
        }
    }

    public boolean economy() {
        return economy != null;
    }

    public boolean placeholderApi() {
        return papi != null;
    }

    public boolean hasMoney(Player player, double amount) {
        if (economy == null) {
            return false;
        }
        try {
            return (boolean) has.invoke(economy, player, amount);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("Vault has() falló: " + ex.getMessage());
            return false;
        }
    }

    public boolean withdraw(Player player, double amount) {
        return transaction(withdraw, player, amount);
    }

    public boolean deposit(Player player, double amount) {
        return transaction(deposit, player, amount);
    }

    private boolean transaction(Method method, Player player, double amount) {
        if (economy == null) {
            return false;
        }
        try {
            Object response = method.invoke(economy, player, amount);
            Method ok = response.getClass().getMethod("transactionSuccess");
            return (boolean) ok.invoke(response);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("Transacción de Vault falló: " + ex.getMessage());
            return false;
        }
    }

    public String placeholders(Player player, String text) {
        if (papi == null || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            return (String) papi.invoke(null, player, text);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return text;
        }
    }
}
