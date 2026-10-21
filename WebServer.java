package sentinel;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Thin HTTP demo adapter; aggregation, severity policy, and persistence use the CLI's Java classes. */
public final class WebServer {
    private final AlertEngine engine = new AlertEngine(Duration.ofSeconds(60), Clock.systemUTC());
    private final HistoryStore history;
    private final Connection reader;
    private final ArrayDeque<String> notifications = new ArrayDeque<>();
    private long events, emitted, saved, lastDemo;
    private static final Map<String,String> MESSAGES = Map.of(
        "DATABASE", "Database connection pool exhausted", "MEMORY", "Heap usage above 85 percent",
        "DISK", "Disk usage above 90 percent", "CPU", "CPU usage above 90 percent",
        "NETWORK", "Network timeout contacting upstream", "APPLICATION", "Worker request failed");
    private WebServer(Path db) throws Exception {
        history = new HistoryStore(db);
        reader = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        try (Statement s = reader.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM events")) {
            r.next(); saved = r.getLong(1);
        }
    }
    private void ingest(String category, String component) throws Exception {
        LogEvent event = new LogEvent(Instant.now(), "web-01", component, "ERROR", MESSAGES.get(category));
        AlertEngine.Change change = engine.accept(event);
        history.save(event, change); events++; saved++;
        if (change.emit()) {
            emitted++;
            String message = change.alert().severity()+" | "+category+" | count="+change.alert().count()+" | "+event.message();
            notifications.addFirst(message);
            if (notifications.size() > 30) notifications.removeLast();
            System.out.println("ALERT " + message);
        }
    }
    private static String q(String value) {
        if(value == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for(char c : value.toCharArray()) switch(c) {
            case '"' -> b.append("\\\""); case '\\' -> b.append("\\\\");
            case '\n' -> b.append("\\n"); case '\r' -> b.append("\\r"); case '\t' -> b.append("\\t");
            default -> { if(c < 32) b.append(String.format("\\u%04x",(int)c)); else b.append(c); }
        }
        return b.append('"').toString();
    }
    private String state() throws Exception {
        List<AlertEngine.Alert> active = engine.snapshot();
        Severity overall = active.stream().map(AlertEngine.Alert::severity).max(Comparator.naturalOrder()).orElse(null);
        StringBuilder b = new StringBuilder("{\"events\":"+events+",\"emitted\":"+emitted+",\"saved\":"+saved+
            ",\"suppressed\":"+(events-emitted)+",\"severity\":"+q(overall==null?"QUIET":overall.name())+",\"alerts\":[");
        boolean comma=false;
        for(AlertEngine.Alert a:active.stream().limit(20).toList()) {
            if(comma)b.append(',');comma=true;
            b.append("{\"server\":").append(q(a.key().server())).append(",\"category\":").append(q(a.category()))
                .append(",\"severity\":").append(q(a.severity().name())).append(",\"count\":").append(a.count())
                .append(",\"message\":").append(q(a.key().message())).append('}');
        }
        b.append("],\"notifications\":[");comma=false;
        for(String n:notifications){if(comma)b.append(',');comma=true;b.append(q(n));}
        b.append("],\"history\":[");comma=false;
        try(Statement s=reader.createStatement();ResultSet r=s.executeQuery("SELECT id,log_time,category,severity,message FROM events ORDER BY id DESC LIMIT 20")) {
            while(r.next()) {
                if(comma)b.append(',');comma=true;
                b.append("{\"id\":").append(r.getLong("id")).append(",\"time\":").append(q(r.getString("log_time")))
                    .append(",\"category\":").append(q(r.getString("category"))).append(",\"severity\":").append(q(r.getString("severity")))
                    .append(",\"message\":").append(q(r.getString("message"))).append('}');
            }
        }
        return b.append("]}").toString();
    }
    private static void send(HttpExchange x,int code,String type,String text) throws java.io.IOException {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.getResponseHeaders().set("X-Frame-Options","DENY");
        x.getResponseHeaders().set("Content-Security-Policy","default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'");
        x.sendResponseHeaders(code,bytes.length);
        try(var out=x.getResponseBody()){out.write(bytes);}
    }
    private synchronized void handle(HttpExchange x) throws java.io.IOException {
        try {
            String path=x.getRequestURI().getPath(), method=x.getRequestMethod();
            if(path.equals("/health") && method.equals("GET")){send(x,200,"application/json","{\"status\":\"ok\"}");return;}
            if(path.equals("/") && method.equals("GET")) {
                try(var in=WebServer.class.getResourceAsStream("/index.html")) {
                    if(in==null)throw new IllegalStateException("Dashboard asset missing");
                    send(x,200,"text/html",new String(in.readAllBytes(),StandardCharsets.UTF_8));
                }return;
            }
            if(path.equals("/api/state") && method.equals("GET")){send(x,200,"application/json",state());return;}
            if((path.equals("/api/demo")||path.equals("/api/event")) && method.equals("POST")) {
                if(saved>=100000){send(x,429,"application/json","{\"error\":\"Demo storage limit reached\"}");return;}
                byte[] input=x.getRequestBody().readNBytes(129);
                if(input.length>128){send(x,413,"application/json","{\"error\":\"Request too large\"}");return;}
                if(path.equals("/api/demo")) {
                    if(System.nanoTime()-lastDemo<2_000_000_000L){send(x,429,"application/json","{\"error\":\"Wait two seconds between demos\"}");return;}
                    String component="database-demo-"+UUID.randomUUID();
                    for(int i=0;i<10;i++)ingest("DATABASE",component);
                    lastDemo=System.nanoTime();
                }else{
                    String category=new String(input,StandardCharsets.UTF_8).trim();
                    if(!MESSAGES.containsKey(category)){send(x,400,"application/json","{\"error\":\"Unknown category\"}");return;}
                    ingest(category,category.toLowerCase(Locale.ROOT));
                }
                send(x,200,"application/json",state());return;
            }
            send(x,404,"application/json","{\"error\":\"Not found\"}");
        } catch(Exception e) {
            System.err.println("HTTP request failed: "+e.getMessage());
            send(x,500,"application/json","{\"error\":\"Unable to process request\"}");
        } finally {x.close();}
    }
    public static void main(String[] args) throws Exception {
        int port=Integer.parseInt(System.getenv().getOrDefault("PORT","8080"));
        Path db=Path.of(System.getenv().getOrDefault("SENTINEL_DB","data/sentinel-web.db"));
        WebServer app=new WebServer(db);
        HttpServer server=HttpServer.create(new InetSocketAddress("0.0.0.0",port),32);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        server.setExecutor(pool);server.createContext("/",app::handle);
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            server.stop(1);pool.shutdown();
            synchronized(app){try{app.reader.close();app.history.close();}catch(Exception ignored){}}
        }));
        server.start();System.out.println("SENTINEL Java dashboard listening on port "+port);
    }
}
