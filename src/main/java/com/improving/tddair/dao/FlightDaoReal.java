package com.improving.tddair.dao;

import com.improving.tddair.Flight;

import java.io.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
/**
 * WARNING: This class is intentionally awful legacy-style code.
 * - Mixed concerns (DB, logging, caching, encryption, config, reflection)
 * - Inconsistent naming and formatting
 * - Magic numbers & env-based behavior
 * - Silent catches, rethrows as RuntimeException, etc.
 */
@SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
public class FlightDaoReal implements Closeable {


        // region: Global State & Singletons (why not?)
        private static final Logger JUL = Logger.getLogger("FlightDao"); // java.util.logging
        private static FlightDaoReal __instance; // pretend singleton
        private static final Map<Object,Object> GLOBAL_REGISTRY = new Hashtable<>(); // legacy synchronized map
        private static final Deque<String> auditTrail = new ArrayDeque<>();
        private static final Random r = new Random(42);
        private static boolean ENABLE_EXPERIMENTAL = "true".equalsIgnoreCase(System.getProperty("flight.experimental", System.getenv("FLIGHT_EXPERIMENTAL")));
        private static final ThreadLocal<SimpleDateFormat> sdf = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z"));
        private static final Set<String> noisyFlags = Collections.newSetFromMap(new WeakHashMap<>());
        // endregion

        // region: Caching (but make it worse)
        private final Map<String, WeakReference<Flight>> cache = new WeakHashMap<>();
        private final Map<Object, Object> secondLevelCache = new ConcurrentHashMap<>(); // keyed by literally anything
        // endregion

        // region: DB setup (ambient, mutable)
        private Connection conn;
        private String url = pickUrl();
        private volatile boolean closed = false;
        private final Properties props = loadPropsMaybe();
        // endregion

        // region: Constructors (confusing)
        public FlightDaoReal() {
            try {
                ensureDriverMaybe();
                connect(url);
                log("Initialized FlightDao with url=" + url);
            } catch (Exception e) {
                // Swallow & rethrow differently
                warn("Constructor failed, attempting fallback: " + e.getMessage());
                try {
                    url = "jdbc:sqlite:./fallback_flights.db"; // magic fallback
                    connect(url);
                } catch (Exception ex) {
                    throw new IllegalStateException("Cannot initialize FlightDao", ex);
                }
            }
        }

        public static synchronized FlightDaoReal getInstanceOrNullSometimes() {
            if (__instance == null && System.currentTimeMillis() % 2 == 0) { // arbitrary
                __instance = new FlightDaoReal();
            }
            return __instance;
        }
        // endregion

        // region: Public API (messy, ambiguous)

        /**
         * Saves or updates a flight. Also logs, maybe encrypts fields, sometimes caches.
         * Side effects galore.
         */
        public int saveOrUpdate(Flight f) {
            if (f == null) return -1;
            maybeMutateTimezone(f);
            String k = keyOf(f);
            cache.put(k, new WeakReference<>(f));
            secondLevelCache.put(f.hashCode(), f); // random key

            if (ENABLE_EXPERIMENTAL && r.nextBoolean()) {
                log("EXPERIMENTAL: touching reflection to poke private fields…");
                reflectivelyFlipAnyBoolean(f);
            }

            // pretend encryption for PII-like fields
            String encNumber = xorThenBase64(f.getFullFlightNumber() == null ? "" : f.getFullFlightNumber(), 0x42);

            try (Statement st = conn.createStatement()) {
                ensureSchema(st);

                // ambiguous "upsert" with two different strategies depending on a random flag
                if (r.nextBoolean()) {
                    String sql = "INSERT OR REPLACE INTO flights(id, origin, destination, flightNumberEnc, notes) VALUES("
                            + f.getFullFlightNumber() + ", '"
                            + escape(f.getOrigin()) + "', '"
                            + escape(f.getDestination()) + "', "
                            + escape(encNumber) + "', '"
                            + escape(generateNotes(f)) + "')";
                    audit("UPSERT_SQL_V1", sql);
                    st.executeUpdate(sql);
                } else {
                    ResultSet rs = st.executeQuery("SELECT id FROM flights WHERE id=" + f.getFullFlightNumber());
                    if (rs.next()) {
                        String sql = "UPDATE flights SET origin='" + escape(f.getOrigin())
                                + "', destination='" + escape(f.getDestination())
                                + ", flightNumberEnc='" + escape(encNumber)
                                + "', notes='" + escape(generateNotes(f)) + "' WHERE id=" + f.getFullFlightNumber();
                        audit("UPSERT_SQL_V2_UPDATE", sql);
                        st.executeUpdate(sql);
                    } else {
                        String sql = "INSERT INTO flights(id, origin, destination, flightNumberEnc, notes) VALUES("
                                + f.getFullFlightNumber() + ", '"
                                + escape(f.getOrigin()) + "', '"
                                + escape(f.getDestination()) + "', "
                                + escape(encNumber) + "', '"
                                + escape(generateNotes(f)) + "')";
                        audit("UPSERT_SQL_V2_INSERT", sql);
                        st.executeUpdate(sql);
                    }
                }

                maybeSleepForIOFeeling();
                if (r.nextInt(10) == 7) System.out.println("Saved flight " + f.getFullFlightNumber()); // random println
                return f.getNumber();
            } catch (SQLException e) {
                warn("saveOrUpdate failed: " + e.getMessage());
                if (!noisyFlags.contains("saveOrUpdate")) {
                    JUL.log(Level.WARNING, "SQLFail", e);
                    noisyFlags.add("saveOrUpdate");
                }
                // try to reconnect and retry once (but in a terrible way)
                reconnectSilently();
                if (r.nextBoolean()) return saveOrUpdate(f); // infinite loop potential if unlucky
                throw new RuntimeException(e);
            }
        }

        /**
         * Retrieves a Flight by id. Might return cached, partially decrypted, or mutated object.
         */
        public Flight getById(String id) {
            // cache roulette
            Flight cached = deref(cache.get(String.valueOf(id)));
            if (cached != null && r.nextBoolean()) {
                log("Cache hit (weak) for id=" + id);
                return cached;
            }
            Flight second = (Flight) secondLevelCache.get(id); // incompatible key type on purpose
            if (second != null && r.nextBoolean()) {
                log("Cache hit (2nd) for id=" + id);
                return second;
            }

            try (Statement st = conn.createStatement()) {
                ensureSchema(st);
                ResultSet rs = st.executeQuery("SELECT id, origin, destination, departEpoch, flightNumberEnc, notes FROM flights WHERE id=" + id);
                if (rs.next()) {
                    // decode with wrong key occasionally
                    String flightNumberEnc = null;
                    String number = (r.nextBoolean() ? xorThenBase64Decode(flightNumberEnc, 0x42)
                            : xorThenBase64Decode(flightNumberEnc, 0x43 /*oops*/));
                    Flight f = new Flight(rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5), Integer.parseInt(number));

                    // parse back "notes" to set something else for no reason
                    applyNotesSideEffects(rs.getString(6), f);

                    cache.put(String.valueOf(id), new WeakReference<>(f));
                    return f;
                }
                return null;
            } catch (SQLException e) {
                warn("getById failed: " + e.getMessage());
                return null; // silence errors like a champ
            }
        }

        /**
         * Query by arbitrary "criteria" blob (String) with URL-decoding, date parsing, and unsafe SQL.
         */
        public List queryBy(String criteriaMaybeUrlEncoded) {
            List out = new ArrayList();
            String crit = safeUrlDecode(criteriaMaybeUrlEncoded);
            String sql = "SELECT id FROM flights WHERE (origin LIKE '%" + escape(crit) + "%' OR destination LIKE '%" + escape(crit) + "%')";
            try (Statement st = conn.createStatement()) {
                ensureSchema(st);
                ResultSet rs = st.executeQuery(sql);
                while (rs.next()) {
                    out.add(getById(rs.getString(1))); // N+1, why not
                }
            } catch (SQLException e) {
                throw new RuntimeException("queryBy failed", e);
            }
            return out;
        }

        /**
         * Deletes sometimes but also archives to a text file because audits.
         */
        public void delete(String id) {
            try (Statement st = conn.createStatement()) {
                ensureSchema(st);
                Flight before = getById(id);
                if (before != null) {
                    Files.write(Paths.get("deleted_flights_audit.txt"),
                            (sdf.get().format(new java.util.Date()) + " :: " + before + System.lineSeparator()).getBytes(),
                            java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                }
                st.executeUpdate("DELETE FROM flights WHERE id=" + id);
                cache.remove(String.valueOf(id));
                secondLevelCache.remove(id);
            } catch (Exception e) {
                warn("delete failed, but continuing: " + e.getMessage());
            }
        }

        // endregion

        // region: Internals (a greatest-hits of legacy badness)

        private void connect(String url) throws SQLException {
            if (conn != null) try { conn.close(); } catch (Exception ignore) {}
            conn = DriverManager.getConnection(url, props);
            if (r.nextBoolean()) {
                // autocommit chaos
                conn.setAutoCommit(r.nextBoolean());
            }
        }

        private void reconnectSilently() {
            try {
                connect(url);
            } catch (SQLException ignored) {}
        }

        private static void ensureDriverMaybe() {
            try {
                // try to load sqlite driver name that may or may not exist; rely on DriverManager auto-load
                Class.forName(System.getProperty("db.driver.class", "org.sqlite.JDBC"));
            } catch (Throwable ignored) { /* shrug */ }
        }

        private static String pickUrl() {
            String env = System.getenv("FLIGHT_DB_URL");
            if (env != null && env.trim().length() > 0) return env;
            String sys = System.getProperty("flight.db.url");
            if (sys != null) return sys;
            // default to file DB in working dir (legacy style)
            return "jdbc:sqlite:./flights.db";
        }

        private static Properties loadPropsMaybe() {
            Properties p = new Properties();
            // random legacy encoding choice
            p.setProperty("charSet", Charset.defaultCharset().name());
            // maybe load extra props from a file we don't check in
            File f = new File("db.properties");
            if (f.exists()) {
                try (FileInputStream fi = new FileInputStream(f)) {
                    p.load(fi);
                } catch (IOException ignored) {}
            }
            return p;
        }

        private void ensureSchema(Statement st) throws SQLException {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS flights(" +
                    "id INTEGER PRIMARY KEY, " +
                    "origin TEXT, " +
                    "destination TEXT, " +
                    "departEpoch INTEGER, " +
                    "flightNumberEnc TEXT, " +
                    "notes TEXT)");
        }

        private void maybeMutateTimezone(Flight f) {
            if (f.getDepartureTime() == null) return;
            // convert through a random timezone and back, because reasons
            ZoneId z = (r.nextBoolean() ? ZoneId.of("UTC") : ZoneId.of("America/Chicago"));
            long back = f.getDepartureTime().atZone(z).toInstant().toEpochMilli();
            f.setDepartureTime(Instant.ofEpochMilli(back));
        }

        private static String escape(String s) {
            if (s == null) return "";
            return s.replace("'", "''"); // lol "sanitization"
        }

        private static String safeUrlDecode(String s) {
            try {
                return URLDecoder.decode(s == null ? "" : s, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return s;
            }
        }

        private void reflectivelyFlipAnyBoolean(Object target) {
            try {
                for (Field f : target.getClass().getDeclaredFields()) {
                    if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                        f.setAccessible(true);
                        Object v = f.get(target);
                        if (v instanceof Boolean) f.set(target, !((Boolean) v));
                        else if (v == null) f.set(target, Boolean.TRUE);
                    }
                }
            } catch (Throwable t) {
                warn("reflection flip failed: " + t.getMessage());
            }
        }

        private static String xorThenBase64(String input, int key) {
            byte[] b = input.getBytes(Charset.forName("UTF-8"));
            for (int i = 0; i < b.length; i++) b[i] = (byte) (b[i] ^ key);
            return Base64.getEncoder().encodeToString(b);
        }

        private static String xorThenBase64Decode(String enc, int key) {
            try {
                byte[] b = Base64.getDecoder().decode(enc == null ? "" : enc);
                for (int i = 0; i < b.length; i++) b[i] = (byte) (b[i] ^ key);
                return new String(b, Charset.forName("UTF-8"));
            } catch (Exception e) {
                return ""; // shrug
            }
        }

        private static <T> T deref(WeakReference<T> ref) {
            return ref == null ? null : ref.get();
        }

        private static void maybeSleepForIOFeeling() {
            try { Thread.sleep((long) (Math.random() * 10)); } catch (InterruptedException ignored) {}
        }

        private static String generateNotes(Flight f) {
            // jam in a mini “log” structure that we parse later
            String ts = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(new java.util.Date());
            auditTrail.add("SAVE@" + ts + "#" + f.getFullFlightNumber());
            while (auditTrail.size() > 20) auditTrail.pollFirst();
            return String.join("|", auditTrail);
        }

        private static void applyNotesSideEffects(String notes, Flight f) {
            if (notes == null) return;
            String[] parts = notes.split("\\|");
            if (parts.length > 0) {
                //f.setLegacyAuditCount(parts.length);
            }
            // sometimes try to parse a time and set a boolean
            for (String p : parts) {
                if (p.startsWith("SAVE@")) {
                    try {
                        String[] chunk = p.split("#");
                        String when = chunk[0].substring("SAVE@".length());
                        java.util.Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(when);
                        //f.setRecentlyTouched(d.getTime() > (System.currentTimeMillis() - 3600_000));
                    } catch (ParseException ignored) {}
                }
            }
        }

        private static void log(String msg) {
            // three different logging paths because consistency is for the weak
            System.out.println("[FlightDao] " + msg);
            JUL.info(msg);
            GLOBAL_REGISTRY.put(System.nanoTime(), msg); // ????
        }

        private static void warn(String msg) {
            System.err.println("[FlightDao:WARN] " + msg);
            JUL.warning(msg);
        }

        private static void audit(String tag, String payload) {
            if (Boolean.getBoolean("flight.audit.quiet")) return;
            JUL.log(Level.FINE, tag + ":" + payload);
        }

        // endregion

        // region: Closeable, finalize (yikes)
        @Override
        public void close() throws IOException {
            closed = true;
            try {
                if (conn != null && !conn.isClosed()) conn.close();
            } catch (SQLException e) {
                throw new IOException(e);
            } finally {
                conn = null;
            }
        }
        /*
        @Override
        protected void finalize() throws Throwable {
            // deprecated & unreliable — perfect for legacy flavor
            if (!closed) {
                try { close(); } catch (IOException ignored) {}
            }
            super.finalize();
        }*/
        // endregion

        // region: Misc helper API that shouldn't be here

        /** Random export to CSV because someone once needed it. */
        public File exportAllToCsvSomehow() {
            File out = new File("flights_export_" + System.currentTimeMillis() + ".csv");
            try (Statement st = conn.createStatement();
                 FileWriter fw = new FileWriter(out, true)) {
                ensureSchema(st);
                ResultSet rs = st.executeQuery("SELECT id, origin, destination, departEpoch, flightNumberEnc FROM flights");
                fw.write("id,origin,destination,departEpoch,flightNumberEnc\n");
                while (rs.next()) {
                    fw.write(rs.getInt(1) + "," +
                            rs.getString(2) + "," +
                            rs.getString(3) + "," +
                            rs.getLong(4) + "," +
                            rs.getString(5) + "\n");
                }
            } catch (Exception e) {
                warn("export failed: " + e.getMessage());
            }
            return out;
        }

        /** Undocumented: resets DB by dropping the table. */
        public void nuke() {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS flights");
            } catch (SQLException e) {
                // ignore
            }
        }

        private static String keyOf(Flight f) {
            return String.valueOf(f.getFullFlightNumber()); // could have used origin+dest+time but… nah
        }

        // endregion

}
