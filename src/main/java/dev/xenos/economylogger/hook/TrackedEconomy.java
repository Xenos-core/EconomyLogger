package dev.xenos.economylogger.hook;

import dev.xenos.economylogger.track.Recorder;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;

import java.util.List;

public final class TrackedEconomy implements Economy {

    public static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private static final EconomyResponse NO_ECONOMY =
            new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "No economy plugin is installed");

    private final EconomyHook hook;
    private final Recorder recorder;

    public TrackedEconomy(EconomyHook hook, Recorder recorder) {
        this.hook = hook;
        this.recorder = recorder;
    }

    private Economy d() { return hook.delegate(); }

    public static boolean inTrackedCall() { return DEPTH.get()[0] > 0; }

    private interface Call { EconomyResponse run(Economy e); }

    private EconomyResponse tracked(OfflinePlayer p, String name, String world, boolean deposit, double amount, Call call) {
        Economy e = d();
        if (e == null) return NO_ECONOMY;
        int[] depth = DEPTH.get();
        depth[0]++;
        EconomyResponse r;
        try {
            r = call.run(e);
        } finally {
            depth[0]--;
        }
        if (depth[0] == 0 && hook.active()) {
            try {
                boolean ok = r != null && r.transactionSuccess();
                double amt = r != null && ok && r.amount > 0 ? r.amount : amount;
                double bal = r != null && ok ? r.balance : Double.NaN;
                recorder.record(p, name, deposit, amt, bal, ok, recorder.caller(), world, null);
            } catch (Throwable t) {
                hook.error(t);
            }
        }
        return r;
    }

    @Override public EconomyResponse withdrawPlayer(String player, double amount) { return tracked(null, player, null, false, amount, e -> e.withdrawPlayer(player, amount)); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) { return tracked(player, null, null, false, amount, e -> e.withdrawPlayer(player, amount)); }
    @Override public EconomyResponse withdrawPlayer(String player, String world, double amount) { return tracked(null, player, world, false, amount, e -> e.withdrawPlayer(player, world, amount)); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) { return tracked(player, null, world, false, amount, e -> e.withdrawPlayer(player, world, amount)); }
    @Override public EconomyResponse depositPlayer(String player, double amount) { return tracked(null, player, null, true, amount, e -> e.depositPlayer(player, amount)); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) { return tracked(player, null, null, true, amount, e -> e.depositPlayer(player, amount)); }
    @Override public EconomyResponse depositPlayer(String player, String world, double amount) { return tracked(null, player, world, true, amount, e -> e.depositPlayer(player, world, amount)); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) { return tracked(player, null, world, true, amount, e -> e.depositPlayer(player, world, amount)); }

    @Override public boolean isEnabled() { Economy e = d(); return e != null && e.isEnabled(); }
    @Override public String getName() { Economy e = d(); return e == null ? "EconomyLogger (no economy)" : e.getName(); }
    @Override public boolean hasBankSupport() { Economy e = d(); return e != null && e.hasBankSupport(); }
    @Override public int fractionalDigits() { Economy e = d(); return e == null ? 2 : e.fractionalDigits(); }
    @Override public String format(double amount) { Economy e = d(); return e == null ? String.valueOf(amount) : e.format(amount); }
    @Override public String currencyNamePlural() { Economy e = d(); return e == null ? "" : e.currencyNamePlural(); }
    @Override public String currencyNameSingular() { Economy e = d(); return e == null ? "" : e.currencyNameSingular(); }
    @Override public boolean hasAccount(String p) { Economy e = d(); return e != null && e.hasAccount(p); }
    @Override public boolean hasAccount(OfflinePlayer p) { Economy e = d(); return e != null && e.hasAccount(p); }
    @Override public boolean hasAccount(String p, String w) { Economy e = d(); return e != null && e.hasAccount(p, w); }
    @Override public boolean hasAccount(OfflinePlayer p, String w) { Economy e = d(); return e != null && e.hasAccount(p, w); }
    @Override public double getBalance(String p) { Economy e = d(); return e == null ? 0 : e.getBalance(p); }
    @Override public double getBalance(OfflinePlayer p) { Economy e = d(); return e == null ? 0 : e.getBalance(p); }
    @Override public double getBalance(String p, String w) { Economy e = d(); return e == null ? 0 : e.getBalance(p, w); }
    @Override public double getBalance(OfflinePlayer p, String w) { Economy e = d(); return e == null ? 0 : e.getBalance(p, w); }
    @Override public boolean has(String p, double a) { Economy e = d(); return e != null && e.has(p, a); }
    @Override public boolean has(OfflinePlayer p, double a) { Economy e = d(); return e != null && e.has(p, a); }
    @Override public boolean has(String p, String w, double a) { Economy e = d(); return e != null && e.has(p, w, a); }
    @Override public boolean has(OfflinePlayer p, String w, double a) { Economy e = d(); return e != null && e.has(p, w, a); }
    @Override public EconomyResponse createBank(String n, String p) { Economy e = d(); return e == null ? NO_ECONOMY : e.createBank(n, p); }
    @Override public EconomyResponse createBank(String n, OfflinePlayer p) { Economy e = d(); return e == null ? NO_ECONOMY : e.createBank(n, p); }
    @Override public EconomyResponse deleteBank(String n) { Economy e = d(); return e == null ? NO_ECONOMY : e.deleteBank(n); }
    @Override public EconomyResponse bankBalance(String n) { Economy e = d(); return e == null ? NO_ECONOMY : e.bankBalance(n); }
    @Override public EconomyResponse bankHas(String n, double a) { Economy e = d(); return e == null ? NO_ECONOMY : e.bankHas(n, a); }
    @Override public EconomyResponse bankWithdraw(String n, double a) { Economy e = d(); return e == null ? NO_ECONOMY : e.bankWithdraw(n, a); }
    @Override public EconomyResponse bankDeposit(String n, double a) { Economy e = d(); return e == null ? NO_ECONOMY : e.bankDeposit(n, a); }
    @Override public EconomyResponse isBankOwner(String n, String p) { Economy e = d(); return e == null ? NO_ECONOMY : e.isBankOwner(n, p); }
    @Override public EconomyResponse isBankOwner(String n, OfflinePlayer p) { Economy e = d(); return e == null ? NO_ECONOMY : e.isBankOwner(n, p); }
    @Override public EconomyResponse isBankMember(String n, String p) { Economy e = d(); return e == null ? NO_ECONOMY : e.isBankMember(n, p); }
    @Override public EconomyResponse isBankMember(String n, OfflinePlayer p) { Economy e = d(); return e == null ? NO_ECONOMY : e.isBankMember(n, p); }
    @Override public List<String> getBanks() { Economy e = d(); return e == null ? List.of() : e.getBanks(); }
    @Override public boolean createPlayerAccount(String p) { Economy e = d(); return e != null && e.createPlayerAccount(p); }
    @Override public boolean createPlayerAccount(OfflinePlayer p) { Economy e = d(); return e != null && e.createPlayerAccount(p); }
    @Override public boolean createPlayerAccount(String p, String w) { Economy e = d(); return e != null && e.createPlayerAccount(p, w); }
    @Override public boolean createPlayerAccount(OfflinePlayer p, String w) { Economy e = d(); return e != null && e.createPlayerAccount(p, w); }
}
