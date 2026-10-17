package com.wurmonline.client.resources;

import java.io.*;
import java.util.*;

/** Canonical Avatar URL backed by the same workspace runtime bytes, for native Studio QA. */
public final class AgeSkinFixtureUrl extends ResourceUrl {
    private final File root;
    private final String path;
    public AgeSkinFixtureUrl(File root, String path) { super(root.getAbsolutePath()+":"+path);this.root=root;this.path=path; }
    private File file() { return new File(root,"assets/runtime/"+path.substring("avatar2/".length())); }
    public ResourceUrl derive(String relative) { return new AgeSkinFixtureUrl(root,path.substring(0,path.lastIndexOf('/')+1)+relative); }
    public ResourceUrl changeFilePath(String replacement) { return new AgeSkinFixtureUrl(root,replacement); }
    public InputStream openStream() throws IOException { return new FileInputStream(file()); }
    public boolean exists() { return file().isFile(); }
    public String getFilePath() { return path; }
    public Map<String,String> getOverrides() { return Collections.emptyMap(); }
    public long getSize() { return file().length(); }
    public boolean equals(Object other) { return other instanceof AgeSkinFixtureUrl&&toString().equals(other.toString()); }
    public String toString() { return root.getAbsolutePath()+":"+path; }
}
