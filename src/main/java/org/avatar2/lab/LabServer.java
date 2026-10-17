package org.avatar2.lab;

import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

/** Disabled until explicitly started. Same-host, authenticated, no CORS. */
final class LabServer implements AutoCloseable {
    private final HttpServer http;
    private final ExecutorService workers;
    private final String token,origin;
    private final byte[] dashboard;
    private Path session;
    LabServer() throws IOException {
        this(Paths.get("mods","avatar2","avatar2-"+org.avatar2.client.Avatar2Mod.VERSION+".jar"));
    }
    LabServer(Path installedJar) throws IOException {
        // Fail at lab-on, not on the first browser request. The page is immutable
        // for this session and has the same JAR fallback as other Wurm resources.
        dashboard=LabPageResource.read(LabServer.class,installedJar);
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);token=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8);
        origin="http://127.0.0.1:"+http.getAddress().getPort();
        workers=new ThreadPoolExecutor(2,4,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16),r->{Thread t=new Thread(r,"Avatar2 Lab HTTP");t.setDaemon(true);return t;});
        http.setExecutor(workers);http.createContext("/",this::handle);
    }
    void start() throws IOException {
        try{
            session=Files.createTempFile("avatar2-lab-session-",".json");
            Files.write(session,LabJson.write(LabJson.map("protocol",1,"url",origin,"token",token,"createdMillis",System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8));
            http.start();
        }catch(IOException error){close();throw error;}
    }
    String address(){return origin+" (session: "+session+")";}
    void open() throws IOException {Desktop.getDesktop().browse(URI.create(origin+"/#"+token));}
    @Override public void close(){http.stop(0);workers.shutdownNow();if(session!=null)try{Files.deleteIfExists(session);}catch(IOException ignored){} }
    private void handle(HttpExchange exchange) throws IOException {
        try{
            Headers headers=exchange.getResponseHeaders();headers.set("Cache-Control","no-store");headers.set("X-Content-Type-Options","nosniff");headers.set("Referrer-Policy","no-referrer");
            headers.set("Content-Security-Policy","default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src 'self' blob:; frame-ancestors 'none'; base-uri 'none'");
            String host=exchange.getRequestHeaders().getFirst("Host"),sentOrigin=exchange.getRequestHeaders().getFirst("Origin");
            if(!origin.substring(7).equals(host)||sentOrigin!=null&&!origin.equals(sentOrigin)){send(exchange,403,LabJson.map("error","Host/origin rejected"));return;}
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
            if(path.equals("/")&&method.equals("GET")){
                sendBytes(exchange,200,"text/html; charset=utf-8",dashboard);return;
            }
            String auth=exchange.getRequestHeaders().getFirst("Authorization");
            if(auth==null||!MessageDigest.isEqual(("Bearer "+token).getBytes(StandardCharsets.UTF_8),auth.getBytes(StandardCharsets.UTF_8))){send(exchange,401,LabJson.map("error","Bearer token required"));return;}
            if(path.equals("/state")&&method.equals("GET")){AnimationLab.contact();send(exchange,200,AnimationLab.state());}
            else if(path.equals("/command")&&method.equals("POST")){
                if(!"application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"))){send(exchange,415,LabJson.map("error","Content-Type must be application/json"));return;}
                send(exchange,200,AnimationLab.submit(LabJson.object(LabJson.parse(read(exchange)))));
            }else if(path.equals("/capture")&&method.equals("POST")){
                AnimationLab.contact();LabCapture.requested.set(true);send(exchange,202,LabJson.map("status","requested"));
            }else if(path.equals("/frame.png")&&method.equals("GET")){
                LabCapture.CapturedFrame frame=LabCapture.latest;
                if(frame==null)send(exchange,409,LabJson.map("error","Request /capture and wait for a rendered frame"));else {
                    exchange.getResponseHeaders().set("X-Avatar2-Revision",String.valueOf(frame.metadata.get("revision")));
                    exchange.getResponseHeaders().set("X-Avatar2-Sequence",String.valueOf(frame.metadata.get("sequence")));
                    sendBytes(exchange,200,"image/png",frame.png);
                }
            }else send(exchange,404,LabJson.map("error","Unknown endpoint or method"));
        }catch(ConcurrentModificationException error){send(exchange,409,LabJson.map("error",error.getMessage()));}
        catch(IllegalArgumentException error){send(exchange,400,LabJson.map("error",error.getMessage()));}
        catch(IllegalStateException error){send(exchange,409,LabJson.map("error",error.getMessage()));}
        catch(Exception error){send(exchange,500,LabJson.map("error",error.toString()));}
        finally{exchange.close();}
    }
    private static String read(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream result=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int count;
        try(InputStream in=exchange.getRequestBody()){
            while((count=in.read(bytes))!=-1){if(result.size()+count>262144)throw new IllegalArgumentException("Command exceeds 256 KiB");result.write(bytes,0,count);}
        }return new String(result.toByteArray(),StandardCharsets.UTF_8);
    }
    private static void send(HttpExchange exchange,int code,Object data) throws IOException {sendBytes(exchange,code,"application/json; charset=utf-8",LabJson.write(data).getBytes(StandardCharsets.UTF_8));}
    private static void sendBytes(HttpExchange exchange,int code,String type,byte[] bytes) throws IOException {exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);}
}
