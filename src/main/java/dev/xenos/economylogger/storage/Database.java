package dev.xenos.economylogger.storage;

import dev.xenos.economylogger.model.*;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Database {

    private sealed interface Op permits Insert, Link, Items {}
    private record Insert(TxRecord r) implements Op {}
    private record Link(long id, UUID counter, String counterName, long linkId) implements Op {}
    private record Items(long txId, List<TxItem> items) implements Op {}

    private final File file;
    private final Logger log;
    private final int batchSize;
    private final long flushMillis;
    private final long retentionMillis;
    private final BlockingQueue<Op> queue;
    private final AtomicLong ids = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(r -> daemon(r, "EconomyLogger-Reader"));

    private Connection writeConn;
    private Connection readConn;
    private Thread writer;
    private volatile boolean running;
    private long lastPurge;

    public Database(File file, Logger log, int batchSize, long flushMillis, int queueLimit, int retentionDays) {
        this.file = file;
        this.log = log;
        this.batchSize = Math.max(1, batchSize);
        this.flushMillis = Math.max(50, flushMillis);
        this.retentionMillis = retentionDays <= 0 ? 0 : retentionDays * 86_400_000L;
        this.queue = new LinkedBlockingQueue<>(Math.max(1000, queueLimit));
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }

    public void open() throws SQLException {
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException ignored) { }
        file.getParentFile().mkdirs();
        String url = "jdbc:sqlite:" + file.getAbsolutePath();
        writeConn = DriverManager.getConnection(url);
        try (Statement s = writeConn.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=NORMAL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS el_tx (" +
                    "id INTEGER PRIMARY KEY, ts INTEGER NOT NULL, player TEXT, player_name TEXT, kind INTEGER NOT NULL," +
                    "amount REAL NOT NULL, balance REAL, success INTEGER NOT NULL DEFAULT 1, plugin TEXT," +
                    "counter_uuid TEXT, counter_name TEXT, link_id INTEGER, context TEXT, world TEXT)");
            s.execute("CREATE INDEX IF NOT EXISTS el_tx_player_ts ON el_tx(player, ts)");
            s.execute("CREATE INDEX IF NOT EXISTS el_tx_ts ON el_tx(ts)");
            s.execute("CREATE INDEX IF NOT EXISTS el_tx_name ON el_tx(player_name COLLATE NOCASE)");
            s.execute("CREATE TABLE IF NOT EXISTS el_items (tx_id INTEGER NOT NULL, delta INTEGER NOT NULL, item BLOB, label TEXT)");
            s.execute("CREATE INDEX IF NOT EXISTS el_items_tx ON el_items(tx_id)");
            try (ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(id), 0) FROM el_tx")) {
                ids.set(rs.next() ? rs.getLong(1) : 0);
            }
        }
        writeConn.setAutoCommit(false);
        readConn = DriverManager.getConnection(url);
        try (Statement s = readConn.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA query_only=true");
        }
        running = true;
        writer = daemon(this::writeLoop, "EconomyLogger-Writer");
        writer.start();
    }

    public long nextId() { return ids.incrementAndGet(); }

    public long written() { return written.get(); }
    public long dropped() { return dropped.get(); }
    public int queued() { return queue.size(); }

    public void insert(TxRecord r) { offer(new Insert(r)); }
    public void link(long id, UUID counter, String counterName, long linkId) { offer(new Link(id, counter, counterName, linkId)); }
    public void items(long txId, List<TxItem> items) { if (!items.isEmpty()) offer(new Items(txId, items)); }

    private void offer(Op op) {
        if (!running || !queue.offer(op)) {
            if (dropped.getAndIncrement() % 1000 == 0) log.warning("Write queue is full, dropping records (is the disk stalled?)");
        }
    }

    private void writeLoop() {
        List<Op> batch = new ArrayList<>(batchSize);
        while (running || !queue.isEmpty()) {
            try {
                Op first = queue.poll(flushMillis, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);
                    flush(batch);
                    batch.clear();
                }
                maybePurge();
            } catch (InterruptedException e) {
                if (!running) break;
            } catch (Throwable t) {
                log.log(Level.SEVERE, "Failed to write " + batch.size() + " economy records", t);
                batch.clear();
                try { writeConn.rollback(); } catch (SQLException ignored) { }
            }
        }
        batch.clear();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            try { flush(batch); } catch (SQLException e) { log.log(Level.SEVERE, "Failed final flush", e); }
        }
    }

    private void flush(List<Op> batch) throws SQLException {
        try (PreparedStatement ins = writeConn.prepareStatement(
                "INSERT OR IGNORE INTO el_tx (id, ts, player, player_name, kind, amount, balance, success, plugin, counter_uuid, counter_name, link_id, context, world) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
             PreparedStatement link = writeConn.prepareStatement(
                     "UPDATE el_tx SET counter_uuid = COALESCE(counter_uuid, ?), counter_name = COALESCE(counter_name, ?), link_id = COALESCE(link_id, ?) WHERE id = ?");
             PreparedStatement item = writeConn.prepareStatement(
                     "INSERT INTO el_items (tx_id, delta, item, label) VALUES (?,?,?,?)")) {
            int inserts = 0;
            for (Op op : batch) {
                switch (op) {
                    case Insert(TxRecord r) -> {
                        ins.setLong(1, r.id);
                        ins.setLong(2, r.time);
                        ins.setString(3, r.player == null ? null : r.player.toString());
                        ins.setString(4, r.playerName);
                        ins.setInt(5, r.kind);
                        ins.setDouble(6, r.amount);
                        if (Double.isNaN(r.balance)) ins.setNull(7, Types.REAL); else ins.setDouble(7, r.balance);
                        ins.setInt(8, r.success ? 1 : 0);
                        ins.setString(9, r.plugin);
                        ins.setString(10, r.counterUuid == null ? null : r.counterUuid.toString());
                        ins.setString(11, r.counterName);
                        if (r.linkId == 0) ins.setNull(12, Types.INTEGER); else ins.setLong(12, r.linkId);
                        ins.setString(13, r.context);
                        ins.setString(14, r.world);
                        ins.addBatch();
                        inserts++;
                    }
                    case Link l -> {
                        if (inserts > 0) { ins.executeBatch(); inserts = 0; }
                        link.setString(1, l.counter() == null ? null : l.counter().toString());
                        link.setString(2, l.counterName());
                        link.setLong(3, l.linkId());
                        link.setLong(4, l.id());
                        link.executeUpdate();
                    }
                    case Items it -> {
                        for (TxItem i : it.items()) {
                            item.setLong(1, it.txId());
                            item.setInt(2, i.delta());
                            item.setBytes(3, i.data());
                            item.setString(4, i.label());
                            item.addBatch();
                        }
                    }
                }
            }
            if (inserts > 0) ins.executeBatch();
            item.executeBatch();
            writeConn.commit();
            written.addAndGet(batch.size());
        }
    }

    private void maybePurge() throws SQLException {
        if (retentionMillis == 0) return;
        long now = System.currentTimeMillis();
        if (now - lastPurge < 3_600_000L) return;
        lastPurge = now;
        long cutoff = now - retentionMillis;
        try (PreparedStatement a = writeConn.prepareStatement("DELETE FROM el_items WHERE tx_id IN (SELECT id FROM el_tx WHERE ts < ?)");
             PreparedStatement b = writeConn.prepareStatement("DELETE FROM el_tx WHERE ts < ?")) {
            a.setLong(1, cutoff);
            a.executeUpdate();
            b.setLong(1, cutoff);
            int n = b.executeUpdate();
            writeConn.commit();
            if (n > 0) log.info("Purged " + n + " records older than retention");
        }
    }

    public void close() {
        running = false;
        if (writer != null) {
            writer.interrupt();
            try { writer.join(10_000); } catch (InterruptedException ignored) { }
        }
        reader.shutdown();
        try { reader.awaitTermination(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
        try { if (writeConn != null) writeConn.close(); } catch (SQLException ignored) { }
        try { if (readConn != null) readConn.close(); } catch (SQLException ignored) { }
    }

    public <T> CompletableFuture<T> read(SqlTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run(readConn);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, reader);
    }

    @FunctionalInterface
    public interface SqlTask<T> { T run(Connection c) throws SQLException; }

    public CompletableFuture<Page> page(Query q) {
        return read(c -> {
            StringBuilder where = new StringBuilder(" WHERE t.player = ?");
            List<Object> args = new ArrayList<>();
            args.add(q.player().toString());
            long since = q.period().since();
            if (since > 0) { where.append(" AND t.ts >= ?"); args.add(since); }
            switch (q.filter()) {
                case INCOME -> where.append(" AND t.kind = 0");
                case SPEND -> where.append(" AND t.kind = 1");
                case PAYMENTS -> where.append(" AND t.counter_name IS NOT NULL");
                case ITEMS -> where.append(" AND EXISTS (SELECT 1 FROM el_items i WHERE i.tx_id = t.id)");
                default -> { }
            }
            if (q.plugin() != null) { where.append(" AND t.plugin = ?"); args.add(q.plugin()); }

            int total;
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM el_tx t" + where)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) { total = rs.next() ? rs.getInt(1) : 0; }
            }
            List<LogEntry> entries = new ArrayList<>();
            Map<Long, Integer> index = new HashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t.*, (SELECT COUNT(*) FROM el_items i WHERE i.tx_id = t.id) AS ic FROM el_tx t" + where +
                            " ORDER BY t.ts DESC, t.id DESC LIMIT ? OFFSET ?")) {
                List<Object> a = new ArrayList<>(args);
                a.add(q.pageSize());
                a.add(q.page() * q.pageSize());
                bind(ps, a);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LogEntry e = entry(rs, new ArrayList<>());
                        index.put(e.id(), entries.size());
                        entries.add(e);
                    }
                }
            }
            loadPreviews(c, entries, index);
            Summary summary = summary(c, q.player(), since);
            return new Page(q, entries, total, summary, plugins(c, q.player()));
        });
    }

    private void loadPreviews(Connection c, List<LogEntry> entries, Map<Long, Integer> index) throws SQLException {
        List<Long> withItems = entries.stream().filter(e -> e.itemCount() > 0).map(LogEntry::id).toList();
        if (withItems.isEmpty()) return;
        String in = String.join(",", Collections.nCopies(withItems.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement("SELECT tx_id, delta, item, label FROM el_items WHERE tx_id IN (" + in + ") ORDER BY rowid")) {
            bind(ps, new ArrayList<>(withItems));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LogEntry e = entries.get(index.get(rs.getLong(1)));
                    if (e.preview().size() < 12) e.preview().add(new TxItem(rs.getInt(2), rs.getBytes(3), rs.getString(4)));
                }
            }
        }
    }

    private Summary summary(Connection c, UUID player, long since) throws SQLException {
        List<Summary.Source> sources = new ArrayList<>();
        double in = 0, out = 0;
        int count = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT plugin, SUM(CASE WHEN kind = 0 THEN amount ELSE 0 END), SUM(CASE WHEN kind = 1 THEN amount ELSE 0 END)," +
                        " SUM(CASE WHEN kind = 0 THEN 1 ELSE 0 END), SUM(CASE WHEN kind = 1 THEN 1 ELSE 0 END)" +
                        " FROM el_tx WHERE player = ? AND ts >= ? GROUP BY plugin ORDER BY SUM(amount) DESC")) {
            ps.setString(1, player.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Summary.Source s = new Summary.Source(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getInt(4), rs.getInt(5));
                    in += s.income();
                    out += s.spent();
                    count += s.inCount() + s.outCount();
                    sources.add(s);
                }
            }
        }
        List<Summary.Partner> partners = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT counter_uuid, MAX(counter_name), SUM(CASE WHEN kind = 0 THEN amount ELSE 0 END), SUM(CASE WHEN kind = 1 THEN amount ELSE 0 END)" +
                        " FROM el_tx WHERE player = ? AND ts >= ? AND counter_name IS NOT NULL" +
                        " GROUP BY COALESCE(counter_uuid, counter_name) ORDER BY SUM(amount) DESC LIMIT 24")) {
            ps.setString(1, player.toString());
            ps.setLong(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) partners.add(new Summary.Partner(uuid(rs.getString(1)), rs.getString(2), rs.getDouble(3), rs.getDouble(4)));
            }
        }
        return new Summary(in, out, count, sources, partners);
    }

    private List<String> plugins(Connection c, UUID player) throws SQLException {
        List<String> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT plugin FROM el_tx WHERE player = ? AND plugin IS NOT NULL ORDER BY plugin")) {
            ps.setString(1, player.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(rs.getString(1)); }
        }
        return list;
    }

    public CompletableFuture<List<TxItem>> items(long txId) {
        return read(c -> {
            List<TxItem> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT delta, item, label FROM el_items WHERE tx_id = ? ORDER BY delta DESC, rowid")) {
                ps.setLong(1, txId);
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) list.add(new TxItem(rs.getInt(1), rs.getBytes(2), rs.getString(3))); }
            }
            return list;
        });
    }

    public CompletableFuture<Map.Entry<UUID, String>> findPlayer(String name) {
        return read(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT player, player_name FROM el_tx WHERE player_name = ? COLLATE NOCASE AND player IS NOT NULL ORDER BY ts DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Map.entry(UUID.fromString(rs.getString(1)), rs.getString(2)) : null;
                }
            }
        });
    }

    public CompletableFuture<long[]> stats() {
        return read(c -> {
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM el_tx")) {
                return new long[]{rs.next() ? rs.getLong(1) : 0, file.length() + new File(file.getPath() + "-wal").length()};
            }
        });
    }

    private static LogEntry entry(ResultSet rs, List<TxItem> preview) throws SQLException {
        double bal = rs.getDouble("balance");
        if (rs.wasNull()) bal = Double.NaN;
        return new LogEntry(rs.getLong("id"), rs.getLong("ts"), uuid(rs.getString("player")), rs.getString("player_name"),
                (byte) rs.getInt("kind"), rs.getDouble("amount"), bal, rs.getString("plugin"),
                uuid(rs.getString("counter_uuid")), rs.getString("counter_name"), rs.getString("context"),
                rs.getString("world"), rs.getInt("ic"), preview);
    }

    private static UUID uuid(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) {
            Object o = args.get(i);
            if (o instanceof Long l) ps.setLong(i + 1, l);
            else if (o instanceof Integer n) ps.setInt(i + 1, n);
            else ps.setString(i + 1, (String) o);
        }
    }
}
