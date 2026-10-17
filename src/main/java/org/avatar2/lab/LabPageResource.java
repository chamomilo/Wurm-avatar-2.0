package org.avatar2.lab;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/** The shared Javassist loader can load classes without exposing JAR resources. */
final class LabPageResource {
    static final String RESOURCE="/org/avatar2/lab/dashboard.html";
    private LabPageResource() { }

    static byte[] read(Class<?> anchor,Path installedJar) throws IOException {
        try(InputStream input=anchor.getResourceAsStream(RESOURCE)) {
            if(input!=null)return bytes(input);
        }
        Path archive=installedJar.toAbsolutePath().normalize();
        if(Files.isRegularFile(archive)) {
            // Read into memory and close both streams: do not keep the installed
            // JAR locked on Windows for the lifetime of the HTTP server.
            try(ZipFile zip=new ZipFile(archive.toFile())) {
                ZipEntry entry=zip.getEntry(RESOURCE.substring(1));
                if(entry!=null&&!entry.isDirectory())try(InputStream input=zip.getInputStream(entry)) {
                    return bytes(input);
                }
            }
        }
        throw new IOException("Missing dashboard: "+RESOURCE+" (classpath and "+archive+")");
    }

    private static byte[] bytes(InputStream input) throws IOException {
        ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=input.read(buffer))!=-1) {
            if(output.size()+count>1024*1024)throw new IOException("Dashboard exceeds 1 MiB");
            output.write(buffer,0,count);
        }
        return output.toByteArray();
    }
}
