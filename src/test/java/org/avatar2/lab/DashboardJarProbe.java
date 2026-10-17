package org.avatar2.lab;

import javassist.ClassPool;
import org.gotti.wurmunlimited.modloader.DefrostingClassLoader;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.*;

/** Regression: packaged page served through Wurm's real shared classloader. */
public final class DashboardJarProbe {
    public static void main(String[] args) throws Exception {
        Path jar=Paths.get(args[0]);String resource="org/avatar2/lab/dashboard.html";
        ClassPool pool=new ClassPool(true);pool.appendClassPath(jar.toString());
        try(DefrostingClassLoader loader=new DefrostingClassLoader(pool)) {
            Class<?> serverType=loader.loadClass("org.avatar2.lab.LabServer");
            if(serverType.getClassLoader()!=loader)throw new AssertionError("Wrong test loader");
            if(serverType.getResourceAsStream("/"+resource)!=null)throw new AssertionError("Parent resources would mask the Wurm regression");
            // The canonical updater gate verifies its complete module/artwork;
            // this probe owns only the Avatar dashboard regression.
            Constructor<?> constructor=serverType.getDeclaredConstructor();constructor.setAccessible(true);
            Object server=constructor.newInstance();
            Method start=method(serverType,"start"),address=method(serverType,"address"),close=method(serverType,"close");
            try {
                start.invoke(server);String url=((String)address.invoke(server)).split(" ")[0];
                HttpURLConnection connection=(HttpURLConnection)new URL(url+"/").openConnection();connection.setReadTimeout(3000);connection.setConnectTimeout(3000);
                try {
                    if(connection.getResponseCode()!=200)throw new AssertionError("Dashboard HTTP status: "+connection.getResponseCode());
                    if(!connection.getContentType().startsWith("text/html"))throw new AssertionError("Not HTML");
                    byte[] served;try(InputStream input=connection.getInputStream()){served=read(input);}
                    try(ZipFile zip=new ZipFile(jar.toFile());InputStream input=zip.getInputStream(zip.getEntry(resource))) {
                        if(!java.util.Arrays.equals(served,read(input)))throw new AssertionError("Different dashboard bytes");
                    }
                    if(!new String(served,StandardCharsets.UTF_8).contains("Animation Lab"))throw new AssertionError("Wrong page");
                    System.out.println("AVATAR2_DASHBOARD_OK Wurm shared loader hides resource; JAR fallback serves HTTP 200 HTML");
                }finally{connection.disconnect();}
            }finally{close.invoke(server);}
        }
    }
    private static Method method(Class<?> type,String name) throws Exception {Method method=type.getDeclaredMethod(name);method.setAccessible(true);return method;}
    private static byte[] read(InputStream input) throws IOException {ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);return output.toByteArray();}
}
