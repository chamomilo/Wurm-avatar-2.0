package com.wurmonline.client.resources;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.jar.JarFile;

/** Native resource paths backed by read-only installed hair/texture entries. */
public final class HairFixtureUrl extends ResourceUrl {
    private final File graphics;
    private final String path;
    public HairFixtureUrl(File graphics,String path){super(graphics.getAbsolutePath()+":"+path);this.graphics=graphics;this.path=path;}
    public ResourceUrl derive(String relative){return new HairFixtureUrl(graphics,path.substring(0,path.lastIndexOf('/')+1)+relative);}
    public ResourceUrl changeFilePath(String replacement){return new HairFixtureUrl(graphics,replacement);}
    public InputStream openStream()throws IOException{return new URL("jar:"+graphics.toURI().toURL()+"!/"+path).openStream();}
    public boolean exists(){try(JarFile jar=new JarFile(graphics)){return jar.getJarEntry(path)!=null;}catch(IOException e){return false;}}
    public String getFilePath(){return path;}
    public Map<String,String> getOverrides(){return Collections.emptyMap();}
    public long getSize(){try(JarFile jar=new JarFile(graphics)){return jar.getJarEntry(path).getSize();}catch(Exception e){return 0;}}
    public boolean equals(Object other){return other instanceof HairFixtureUrl&&toString().equals(other.toString());}
    public String toString(){return graphics.getAbsolutePath()+":"+path;}
}
